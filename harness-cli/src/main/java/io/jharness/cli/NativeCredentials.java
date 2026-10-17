package io.jharness.cli;

import com.sun.jna.*;
import com.sun.jna.ptr.*;
import io.jharness.api.Values;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Isolated native vault worker. Secrets enter on stdin, never command arguments or files. */
public final class NativeCredentials {
    private NativeCredentials() {}

    public static void main(String[] args) {
        try {
            byte[] input = System.in.readNBytes(65537);
            if (input.length > 65536) throw new IllegalArgumentException();
            Object parsed = Json.parse(new String(input, StandardCharsets.UTF_8));
            if (!(parsed instanceof List<?> operations) || operations.size() > 32)
                throw new IllegalArgumentException();
            String result = "";
            String os = System.getProperty("os.name");
            try (Vault vault = os.startsWith("Mac") ? new Keychain() : new SecretService()) {
                for (var raw : operations) {
                    var op = Values.object(raw);
                    String action = (String) op.get("action");
                    String target = (String) op.get("target");
                    String value = (String) op.get("value");
                    if (!Set.of("read", "write", "delete", "probe").contains(action)
                            || target == null
                            || !target.startsWith("J-Harness/")
                            || target.length() > 512
                            || target.indexOf('\0') >= 0
                            || value == null
                            || value.length() > 1024
                            || value.indexOf('\0') >= 0) throw new IllegalArgumentException();
                    result = vault.run(action, target, value);
                }
            }
            System.out.print(
                    Base64.getEncoder().encodeToString(result.getBytes(StandardCharsets.UTF_8)));
        } catch (Throwable failure) {
            // Native/service errors may contain secret material. Never print them.
            System.exit(7);
        }
    }

    private interface Vault extends AutoCloseable {
        String run(String action, String target, String value);

        @Override
        default void close() {}
    }

    private static final class Keychain implements Vault {
        private final NativeLibrary security = NativeLibrary.getInstance("Security");
        private final NativeLibrary core = NativeLibrary.getInstance("CoreFoundation");

        Keychain() {
            check(call("SecKeychainSetUserInteractionAllowed", 0));
        }

        private int call(String name, Object... args) {
            return security.getFunction(name).invokeInt(args);
        }

        private void check(int status) {
            if (status != 0) throw new IllegalStateException("Native vault unavailable");
        }

        @Override
        public String run(String action, String target, String value) {
            if (action.equals("probe")) {
                var keychain = new PointerByReference();
                check(call("SecKeychainCopyDefault", keychain));
                try {
                    var status = new IntByReference();
                    check(call("SecKeychainGetStatus", keychain.getValue(), status));
                    if ((status.getValue() & 1) == 0) throw new IllegalStateException();
                } finally {
                    release(keychain.getValue());
                }
                return "available";
            }
            byte[] service = "J-Harness".getBytes(StandardCharsets.UTF_8);
            byte[] account = target.getBytes(StandardCharsets.UTF_8);
            var length = new IntByReference();
            var data = new PointerByReference();
            var item = new PointerByReference();
            int found =
                    call(
                            "SecKeychainFindGenericPassword",
                            null,
                            service.length,
                            service,
                            account.length,
                            account,
                            length,
                            data,
                            item);
            if (found != 0 && found != -25300) check(found);
            try {
                if (action.equals("read")) {
                    if (found == -25300) return "";
                    if (length.getValue() < 0 || length.getValue() > 1024)
                        throw new IllegalStateException();
                    return new String(
                            data.getValue().getByteArray(0, length.getValue()),
                            StandardCharsets.UTF_8);
                }
                if (action.equals("delete")) {
                    if (found == 0) check(call("SecKeychainItemDelete", item.getValue()));
                    return "";
                }
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                try {
                    if (found == 0)
                        check(
                                call(
                                        "SecKeychainItemModifyAttributesAndData",
                                        item.getValue(),
                                        null,
                                        bytes.length,
                                        bytes));
                    else
                        check(
                                call(
                                        "SecKeychainAddGenericPassword",
                                        null,
                                        service.length,
                                        service,
                                        account.length,
                                        account,
                                        bytes.length,
                                        bytes,
                                        null));
                } finally {
                    Arrays.fill(bytes, (byte) 0);
                }
                return "";
            } finally {
                if (data.getValue() != null)
                    call("SecKeychainItemFreeContent", null, data.getValue());
                release(item.getValue());
            }
        }

        private void release(Pointer pointer) {
            if (pointer != null) core.getFunction("CFRelease").invokeVoid(new Object[] {pointer});
        }
    }

    private static final class SecretService implements Vault {
        private final NativeLibrary secret = NativeLibrary.getInstance("secret-1");
        private final NativeLibrary glib = NativeLibrary.getInstance("glib-2.0");
        private final NativeLibrary object = NativeLibrary.getInstance("gobject-2.0");
        private final Pointer schema;

        SecretService() {
            // Dynamic schema avoids coupling to private/padded C struct layout.
            schema =
                    secret.getFunction("secret_schema_new")
                            .invokePointer(
                                    new Object[] {"io.jharness.Credential", 0, "target", 0, null});
            if (schema == null) throw new IllegalStateException();
        }

        @Override
        public String run(String action, String target, String value) {
            var error = new PointerByReference();
            Pointer service =
                    secret.getFunction("secret_service_get_sync")
                            .invokePointer(new Object[] {2, null, error});
            Pointer collection = null;
            try {
                check(error);
                if (service == null) throw new IllegalStateException();
                collection =
                        secret.getFunction("secret_collection_for_alias_sync")
                                .invokePointer(new Object[] {service, "default", 0, null, error});
                check(error);
                if (collection == null
                        || secret.getFunction("secret_collection_get_locked")
                                        .invokeInt(new Object[] {collection})
                                != 0) throw new IllegalStateException();
            } finally {
                if (collection != null)
                    object.getFunction("g_object_unref").invokeVoid(new Object[] {collection});
                if (service != null)
                    object.getFunction("g_object_unref").invokeVoid(new Object[] {service});
            }
            if (action.equals("probe")) return "available";
            if (action.equals("read")) {
                Pointer password =
                        secret.getFunction("secret_password_lookup_sync")
                                .invokePointer(
                                        new Object[] {schema, null, error, "target", target, null});
                try {
                    check(error);
                    if (password == null) return "";
                    long length = password.indexOf(0, (byte) 0);
                    if (length < 0 || length > 1024) throw new IllegalStateException();
                    return password.getString(0, "UTF-8");
                } finally {
                    if (password != null)
                        secret.getFunction("secret_password_free")
                                .invokeVoid(new Object[] {password});
                }
            }
            if (action.equals("write")) {
                int success =
                        secret.getFunction("secret_password_store_sync")
                                .invokeInt(
                                        new Object[] {
                                            schema,
                                            "default",
                                            "J-Harness model connection",
                                            value,
                                            null,
                                            error,
                                            "target",
                                            target,
                                            null
                                        });
                check(error);
                if (success == 0) throw new IllegalStateException();
            } else {
                secret.getFunction("secret_password_clear_sync")
                        .invokeInt(new Object[] {schema, null, error, "target", target, null});
                check(error);
            }
            return "";
        }

        private void check(PointerByReference error) {
            if (error.getValue() != null) {
                glib.getFunction("g_error_free").invokeVoid(new Object[] {error.getValue()});
                throw new IllegalStateException("Native vault unavailable");
            }
        }

        @Override
        public void close() {
            secret.getFunction("secret_schema_unref").invokeVoid(new Object[] {schema});
        }
    }
}

package io.jharness.testkit;

import java.nio.file.*;

public final class M3CommandFixture {
    private M3CommandFixture() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !java.util.Set.of("block", "echo").contains(args[0]))
            throw new IllegalArgumentException("Expected block or echo");
        if (args[0].equals("block")) {
            Files.writeString(Path.of("started"), "ready");
            Thread.sleep(30000);
        } else System.out.println("ok");
    }
}

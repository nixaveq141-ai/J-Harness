package io.jharness.process;

import io.jharness.api.*;
import io.jharness.workspace.UnifiedDiff;
import java.io.IOException;
import java.util.*;

/**
 * Reads only; a preview is advisory and never substitutes for policy/version guards at execution.
 */
public final class LocalPlanPreviewer implements PlanPreviewer {
    private final FileSystemView files;
    private final DataSanitizer sanitizer;

    public LocalPlanPreviewer(FileSystemView files, DataSanitizer sanitizer) {
        this.files = Objects.requireNonNull(files);
        this.sanitizer = Objects.requireNonNull(sanitizer);
    }

    @Override
    public Map<String, Object> preview(ToolInvocation call, CancellationToken cancellation) {
        cancellation.throwIfCancelled();
        if (!Set.of("write_file", "edit_file").contains(call.tool()))
            return Map.of(
                    "tool",
                    call.tool(),
                    "previewable",
                    false,
                    "executed",
                    false,
                    "reason",
                    "无法预览：任意命令或未支持工具的实际副作用不可预测");
        try {
            ExecPath path = new ExecPath((String) call.arguments().get("path"));
            String version = files.versionOf(path);
            String before = version.equals("MISSING") ? "" : files.readText(path);
            String after;
            if (call.tool().equals("write_file")) {
                if (!version.equals("MISSING")
                        && !Boolean.TRUE.equals(call.arguments().get("overwrite")))
                    throw new HarnessException(
                            "FILE_EXISTS", "Preview conflicts with overwrite rule");
                after = (String) call.arguments().get("content");
            } else {
                if (version.equals("MISSING"))
                    throw new HarnessException(
                            "FILE_NOT_FOUND", "Cannot preview missing edit target");
                String old = (String) call.arguments().get("oldText"),
                        replacement = (String) call.arguments().get("newText");
                if (old == null || old.isEmpty())
                    throw new HarnessException("INVALID_EDIT", "Empty oldText");
                int count = 0;
                for (int at = 0; (at = before.indexOf(old, at)) >= 0; at += old.length()) count++;
                if (count
                        != ((Number) call.arguments().getOrDefault("expectedOccurrences", 1))
                                .intValue())
                    throw new HarnessException("EDIT_MATCH_CONFLICT", "Preview match conflict");
                after = before.replace(old, replacement);
            }
            if (after == null || after.length() > 1024 * 1024)
                throw new HarnessException("FILE_LIMIT", "Preview size limit");
            if (call.arguments().get("expectedVersion") instanceof String expected
                    && !expected.equals(version))
                throw new HarnessException("FILE_CONFLICT", "Preview version conflict");
            if (!files.versionOf(path).equals(version))
                throw new HarnessException("FILE_CONFLICT", "File changed during preview");
            cancellation.throwIfCancelled();
            String diff = UnifiedDiff.between(path.value(), before, after);
            return sanitizer.sanitize(
                    Map.of(
                            "tool",
                            call.tool(),
                            "previewable",
                            true,
                            "executed",
                            false,
                            "path",
                            path.value(),
                            "beforeVersion",
                            version,
                            "diff",
                            diff.length() > 8192 ? diff.substring(0, 8192) : diff,
                            "truncated",
                            diff.length() > 8192,
                            "advisory",
                            true));
        } catch (IOException ex) {
            throw new HarnessException("PREVIEW_IO", "Preview read failed", ex);
        }
    }
}

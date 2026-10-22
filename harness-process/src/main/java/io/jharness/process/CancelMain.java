package io.jharness.process;

import java.nio.file.Path;

/** M3 control entrypoint; the complete jharness command parser is delivered in M4. */
public final class CancelMain {
    private CancelMain() {}

    public static void main(String[] args) {
        if (args.length != 4
                || !args[0].equals("--state-dir")
                || !args[2].equals("--run")
                || args[1].isBlank()
                || args[3].isBlank()) {
            System.err.println("Usage: CancelMain --state-dir <directory> --run <runId>");
            System.exit(5);
        }
        LocalRunControl.CancelResult result;
        try {
            result = LocalRunControl.cancel(Path.of(args[1]), args[3]);
        } catch (IllegalArgumentException ex) {
            result = LocalRunControl.CancelResult.INVALID_STATE;
        } catch (RuntimeException ex) {
            result = LocalRunControl.CancelResult.UNREACHABLE;
        }
        System.out.println(result.name());
        System.exit(result.exitCode());
    }
}

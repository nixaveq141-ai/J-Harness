package io.jharness.process;

import java.nio.file.Path;

/** M3 control entrypoint; the complete jharness command parser is delivered in M4. */
public final class CancelMain {
    private CancelMain() {}

    public static void main(String[] args) {
        if (args.length != 4 || !args[0].equals("--state-dir") || !args[2].equals("--run")) {
            System.err.println("Usage: CancelMain --state-dir <directory> --run <runId>");
            System.exit(5);
        }
        LocalRunControl.CancelResult result = LocalRunControl.cancel(Path.of(args[1]), args[3]);
        System.out.println(result.name());
        System.exit(result.exitCode());
    }
}

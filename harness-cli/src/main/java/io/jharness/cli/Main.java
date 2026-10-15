package io.jharness.cli;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        var charset =
                System.console() == null ? StandardCharsets.UTF_8 : System.console().charset();
        var out = new java.io.PrintStream(System.out, true, charset);
        var err = new java.io.PrintStream(System.err, true, charset);
        System.exit(new CliApplication(System.getenv()).execute(args, System.in, out, err));
    }
}

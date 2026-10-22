package io.jharness.process;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class ProcessProbe {
    private ProcessProbe() {}

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        switch (args[0]) {
            case "cancel-invalid-path" ->
                    CancelMain.main(
                            new String[] {
                                "--state-dir",
                                "private-path\0value",
                                "--run",
                                UUID.randomUUID().toString()
                            });
            case "echo" -> {
                System.out.print(args[1]);
                System.err.print("diagnostic");
                System.exit(Integer.parseInt(args[2]));
            }
            case "env" -> {
                System.out.println(System.getenv("JHARNESS_TEST_SECRET"));
                System.out.println(System.getenv("JHARNESS_APPROVED_VALUE"));
            }
            case "stdin" -> System.out.println(System.in.read());
            case "terminal-prefix" -> {
                System.out.print("ordinary terminal tail\n");
                System.out.print("very-pr");
            }
            case "terminal-log" -> {
                for (int i = 0; i < 25000; i++) {
                    System.out.print("line very-pr");
                    System.out.flush();
                    System.out.print("ivate-value end\n");
                }
                System.out.print("ordinary final tail");
            }
            case "block" -> {
                if (args.length > 1) {
                    var self = ProcessHandle.current();
                    Files.writeString(
                            Path.of(args[1]),
                            self.pid() + "\n" + self.info().startInstant().orElseThrow());
                }
                Thread.sleep(30000);
            }
            case "flood" -> {
                while (true) {
                    System.out.println("x".repeat(128));
                    System.err.println("e".repeat(128));
                }
            }
            case "tree" -> {
                Process child =
                        new ProcessBuilder(
                                        java(),
                                        "-cp",
                                        System.getProperty("java.class.path"),
                                        ProcessProbe.class.getName(),
                                        "block",
                                        args[1] + ".child")
                                .start();
                Files.writeString(Path.of(args[1]), String.valueOf(child.pid()));
                Thread.sleep(30000);
            }
            default -> throw new IllegalArgumentException();
        }
    }

    public static String java() {
        return Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")
                .toString();
    }

    public static List<String> unicodeEcho(Path root, String text, int exit) throws Exception {
        if (!System.getProperty("os.name").startsWith("Windows"))
            return command("echo", text, Integer.toString(exit));
        // Java 21's Windows launcher uses CP_ACP for argv. A managed native executable receives
        // Unicode argv independently of the OS code page, without bypassing the ARGV shell guard.
        Path source = root.resolve("unicode-argv.cs"), receiver = root.resolve("unicode-argv.exe");
        Files.writeString(
                source,
                """
                using System;
                using System.Text;
                public static class UnicodeArgv {
                    public static int Main(string[] args) {
                        Console.OutputEncoding = new UTF8Encoding(false);
                        Console.Out.Write(args[0]);
                        Console.Error.Write("diagnostic");
                        return int.Parse(args[1]);
                    }
                }
                """);
        Path compiler =
                Path.of(
                        System.getenv("SystemRoot"),
                        "Microsoft.NET",
                        "Framework64",
                        "v4.0.30319",
                        "csc.exe");
        Process compile =
                new ProcessBuilder(
                                compiler.toString(),
                                "/nologo",
                                "/target:exe",
                                "/out:" + receiver,
                                source.toString())
                        .redirectErrorStream(true)
                        .start();
        try {
            if (!compile.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)
                    || compile.exitValue() != 0)
                throw new IOException("Unicode receiver compilation failed");
        } finally {
            compile.destroyForcibly();
        }
        return List.of(receiver.toString(), text, Integer.toString(exit));
    }

    public static List<String> command(String... args) {
        List<String> command =
                new ArrayList<>(
                        List.of(
                                java(),
                                "-cp",
                                System.getProperty("java.class.path"),
                                ProcessProbe.class.getName()));
        command.addAll(List.of(args));
        return command;
    }
}

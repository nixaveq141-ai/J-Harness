package io.jharness.process;

import java.nio.file.*;
import java.util.*;

public final class ProcessProbe {
    private ProcessProbe() {}

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
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
            case "block" -> Thread.sleep(30000);
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
                                        "block")
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

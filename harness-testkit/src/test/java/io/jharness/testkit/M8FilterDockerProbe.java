package io.jharness.testkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jharness.api.*;
import io.jharness.ecosystem.*;
import io.jharness.workspace.WorkspaceSearchFilter;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Explicit real-Docker acceptance; never runs as part of default Maven tests. */
public final class M8FilterDockerProbe {
    private M8FilterDockerProbe() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("DOCKER IMAGE BASE JAR OUTPUT");
        Path base = Path.of(args[2]).toAbsolutePath().resolve("filter-" + UUID.randomUUID());
        var configurations =
                List.of(
                        new WorkspaceSearchFilter(
                                true,
                                true,
                                List.of(
                                        "!hidden.txt",
                                        "!target/",
                                        "!cache/",
                                        "cache/*",
                                        "!cache/keep.txt")),
                        new WorkspaceSearchFilter(false, false, List.of("/visible.txt")));
        var cases = new ArrayList<Map<String, Object>>();
        var owners = new ArrayList<String>();
        var transport = new NativeCommandTransport();
        for (int i = 0; i < configurations.size(); i++) {
            var filter = configurations.get(i);
            Path localRoot = base.resolve(i + "/local"), dockerRoot = base.resolve(i + "/docker");
            for (Path root : List.of(localRoot, dockerRoot)) {
                Files.createDirectories(root);
                for (String file :
                        List.of(
                                "visible.txt",
                                "hidden.txt",
                                "cache/keep.txt",
                                "cache/drop.txt",
                                "target/out.txt",
                                "中文 空格/nested.txt",
                                ".git/secret.txt",
                                ".jharness/secret.txt")) {
                    Files.createDirectories(root.resolve(file).getParent());
                    Files.writeString(root.resolve(file), "needle " + file);
                }
                Files.writeString(root.resolve(".gitignore"), "hidden.txt\ncache/\n");
            }
            Map<String, Object> localResult, dockerResult;
            try (var local =
                            new LocalExecutionEnvironment(
                                    "filter-local", localRoot, List.of(), filter);
                    var docker =
                            new DockerExecutionEnvironment(
                                    args[0],
                                    args[1],
                                    dockerRoot,
                                    Path.of(args[3]),
                                    transport,
                                    filter)) {
                owners.add(docker.id());
                localResult = inspect(local, filter);
                dockerResult = inspect(docker, filter);
                if (!localResult.equals(dockerResult))
                    throw new IllegalStateException("Filter worlds differ");
            }
            var expected =
                    i == 0
                            ? List.of(
                                    "cache/keep.txt",
                                    "hidden.txt",
                                    "target/out.txt",
                                    "visible.txt",
                                    "中文 空格/nested.txt")
                            : List.of(
                                    "cache/drop.txt",
                                    "cache/keep.txt",
                                    "hidden.txt",
                                    "target/out.txt",
                                    "中文 空格/nested.txt");
            if (!expected.equals(localResult.get("paths")))
                throw new IllegalStateException("Unexpected filtered paths");
            cases.add(
                    Map.of(
                            "configuration",
                            filter.configuration(),
                            "local",
                            localResult,
                            "docker",
                            dockerResult));
        }
        for (String owner : owners) {
            var check =
                    transport.call(
                            List.of(args[0], "inspect", owner),
                            new byte[0],
                            Duration.ofSeconds(5),
                            CancellationToken.NONE);
            if (check.uncertain() || check.exitCode() == 0)
                throw new IllegalStateException("Container not confirmed removed");
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("milestone", "M8.1");
        report.put("result", "SUCCESS");
        report.put("actualDocker", true);
        report.put("image", args[1]);
        report.put("cases", cases);
        report.put("cleanupVerified", true);
        report.put("evidenceDirectory", base.toString());
        report.put("capturedAt", Instant.now().toString());
        report.put(
                "jarSha256",
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(Files.readAllBytes(Path.of(args[3])))));
        new ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValue(Path.of(args[4]).toFile(), report);
        System.out.println("M8.1 actual Docker filtering probe SUCCESS");
    }

    private static Map<String, Object> inspect(
            ExecutionEnvironment environment, WorkspaceSearchFilter filter) throws Exception {
        if (!filter.configuration().equals(environment.descriptor().get("searchFilter")))
            throw new IllegalStateException("Filter binding missing");
        var files = (WorkspaceFiles) environment.files();
        var paths =
                ((List<?>) files.search("**/*.txt", 100, CancellationToken.NONE).get("paths"))
                        .stream().map(String.class::cast).sorted().toList();
        if (!paths.equals(
                ((List<?>)
                                files.isolatedView()
                                        .search("**/*.txt", 100, CancellationToken.NONE)
                                        .get("paths"))
                        .stream().map(String.class::cast).sorted().toList()))
            throw new IllegalStateException("Child filter differs");
        var listed =
                ((List<?>)
                                files.list(new ExecPath("."), 4, 100, CancellationToken.NONE)
                                        .get("entries"))
                        .stream()
                                .map(Values::object)
                                .map(e -> (String) e.get("path"))
                                .filter(p -> p.endsWith(".txt"))
                                .sorted()
                                .toList();
        if (!listed.equals(paths)) throw new IllegalStateException("List/search differ");
        var requested = List.of(new ExecPath("cache/drop.txt"), new ExecPath("cache/keep.txt"));
        var grep =
                ((List<?>)
                                files.grep("needle", requested, 10, CancellationToken.NONE)
                                        .get("matches"))
                        .stream()
                                .map(Values::object)
                                .map(e -> (String) e.get("path"))
                                .sorted()
                                .toList();
        if (!grep.equals(paths.stream().filter(p -> p.startsWith("cache/")).toList()))
            throw new IllegalStateException("Grep differs");
        if (!files.readText(new ExecPath("cache/drop.txt")).contains("needle"))
            throw new IllegalStateException("Filter changed read authorization");
        for (String denied : List.of(".git/secret.txt", ".jharness/secret.txt", "../outside.txt")) {
            try {
                files.readText(new ExecPath(denied));
                throw new IllegalStateException("Unsafe path accepted");
            } catch (HarnessException ex) {
                if (!ex.code().equals("PATH_DENIED")) throw ex;
            }
        }
        return Map.of(
                "paths",
                paths,
                "listMatchesSearch",
                true,
                "grepPaths",
                grep,
                "isolatedViewMatches",
                true,
                "explicitReadRetained",
                true,
                "metadataAndTraversalDenied",
                true);
    }
}

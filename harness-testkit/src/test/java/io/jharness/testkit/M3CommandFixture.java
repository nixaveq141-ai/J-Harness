package io.jharness.testkit;

import java.nio.file.*;

public final class M3CommandFixture {
    private M3CommandFixture() {}

    public static void main(String[] args) throws Exception {
        if (args[0].equals("block")) {
            Files.writeString(Path.of("started"), "ready");
            Thread.sleep(30000);
        } else System.out.println("ok");
    }
}

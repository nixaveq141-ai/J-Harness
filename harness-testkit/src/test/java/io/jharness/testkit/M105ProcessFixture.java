package io.jharness.testkit;

/** Keyless timeout fixture: no children or filesystem changes. */
public final class M105ProcessFixture {
    private M105ProcessFixture() {}

    public static void main(String[] args) throws InterruptedException {
        Thread.sleep(10000);
    }
}

package edu.itc.se.lab01.task4;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

// Simulated download: sleeps instead of using the network.
public class FakeDownload implements Callable<String> {
    private final String url;
    private final long delayMs;
    private final boolean fail;

    public FakeDownload(String url, long delayMs, boolean fail) {
        this.url = url;
        this.delayMs = delayMs;
        this.fail = fail;
    }

    public String url() { return url; }

    @Override public String call() throws IOException, InterruptedException {
        Thread.sleep(delayMs);
        if (fail) throw new IOException("boom");
        int length = ThreadLocalRandom.current().nextInt(1_000, 100_000);
        return url + " -> " + length;
    }
}

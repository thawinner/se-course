package edu.itc.se.lab01.task4;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Main {
    static final long LIMIT = 4_000_000;
    static final int CHUNKS = 16;

    static final int DOWNLOADS = 500;
    static final int FAIL_EVERY = 50;   // downloads 50, 100, ... fail
    static final int SLOW_ID = 1;       // takes 5 s; waited on first

    public static void main(String[] args) throws Exception {
        primes();
        System.out.println();
        downloads("fixed(20) platform threads", Executors.newFixedThreadPool(20));
        downloads("virtual threads", Executors.newVirtualThreadPerTaskExecutor());
        System.out.println();
        completableFutures();
    }

    // CPU-bound: primes below 4,000,000
    static void primes() throws InterruptedException, ExecutionException {
        long t0 = System.nanoTime();
        long single = PrimeCounter.countPrimes(0, LIMIT);
        System.out.printf("single-threaded: %d primes in %d ms%n", single, ms(t0));

        int cores = Runtime.getRuntime().availableProcessors();
        t0 = System.nanoTime();
        try (ExecutorService pool = Executors.newFixedThreadPool(cores)) {
            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < CHUNKS; i++) {
                long from = i * LIMIT / CHUNKS;
                long to = (i + 1) * LIMIT / CHUNKS;
                futures.add(pool.submit(new PrimeCounter(from, to)));
            }
            long total = 0;
            for (Future<Long> f : futures) total += f.get();
            System.out.printf("pool (%d threads): %d primes in %d ms  (same result: %b)%n",
                    cores, total, ms(t0), total == single);
        }
    }

    // I/O-bound: 500 downloads with failures and a timeout
    static void downloads(String label, ExecutorService executor) throws InterruptedException {
        long t0 = System.nanoTime();
        int ok = 0, failed = 0, timedOut = 0;
        Throwable firstFailure = null;

        try (executor) {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 1; i <= DOWNLOADS; i++) futures.add(executor.submit(newDownload(i)));

            for (Future<String> f : futures) {
                try {
                    f.get(1, TimeUnit.SECONDS);
                    ok++;
                } catch (ExecutionException e) {
                    failed++;
                    if (firstFailure == null) firstFailure = e.getCause();   // the original IOException
                } catch (TimeoutException e) {
                    f.cancel(true);                    // stop the slow task
                    timedOut++;
                }
            }
        }

        System.out.printf("downloads on %-26s: ok=%d failed=%d timedOut=%d in %d ms   (failures: %s)%n",
                label, ok, failed, timedOut, ms(t0), firstFailure);
    }

    static FakeDownload newDownload(int i) {
        return new FakeDownload("https://example.com/file-" + i,
                i == SLOW_ID ? 5_000 : 200,
                i % FAIL_EVERY == 0);
    }

    // Bonus: the same downloads with CompletableFuture
    static void completableFutures() throws InterruptedException {
        long t0 = System.nanoTime();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<CompletableFuture<String>> all = new ArrayList<>();
            for (int i = 1; i <= DOWNLOADS; i++) {
                FakeDownload d = newDownload(i);
                all.add(CompletableFuture
                        .supplyAsync(() -> callUnchecked(d), executor)
                        .orTimeout(1, TimeUnit.SECONDS)
                        // turn a failure into a fallback string
                        .handle((result, ex) -> ex == null
                                ? result
                                : "FALLBACK " + d.url() + " (" + reason(ex) + ")"));
            }
            CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).join();

            int ok = 0, failed = 0, timedOut = 0;
            for (CompletableFuture<String> cf : all) {
                String s = cf.join();
                if (!s.startsWith("FALLBACK")) ok++;
                else if (s.endsWith("(timed out)")) timedOut++;
                else failed++;
            }
            System.out.printf("CompletableFuture + virtual threads : ok=%d failed=%d timedOut=%d in %d ms%n",
                    ok, failed, timedOut, ms(t0));
            System.out.println("  example fallbacks: " + all.get(SLOW_ID - 1).join()
                    + " | " + all.get(FAIL_EVERY - 1).join());
        } finally {
            // orTimeout() does not stop the slow task, so interrupt it.
            executor.shutdownNow();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS))
                System.out.println("warning: executor did not terminate");
        }
    }

    // A Supplier cannot throw checked exceptions.
    static String callUnchecked(FakeDownload d) {
        try {
            return d.call();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    static String reason(Throwable ex) {
        while (ex instanceof CompletionException && ex.getCause() != null) ex = ex.getCause();
        return ex instanceof TimeoutException ? "timed out" : ex.toString();
    }

    static long ms(long startNanos) { return (System.nanoTime() - startNanos) / 1_000_000; }
}

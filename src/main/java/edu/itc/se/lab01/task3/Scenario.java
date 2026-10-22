package edu.itc.se.lab01.task3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

// Shared by Main, Main2 and NotifyIfExperiment.
final class Scenario {
    static final int PRODUCERS = 2;
    static final int ORDERS_PER_PRODUCER = 50;
    static final int CONSUMERS = 3;
    static final int CAPACITY = 5;
    static final int EXPECTED = PRODUCERS * ORDERS_PER_PRODUCER;

    record Result(int processed, int distinct, int pills, boolean finished,
                  Throwable error, long ms) {
        boolean ok() {
            return finished && error == null
                    && processed == EXPECTED && distinct == EXPECTED && pills == CONSUMERS;
        }
    }

    private Scenario() {}

    // Gives up after timeoutMs, so a hanging buffer is reported instead of freezing.
    static Result run(OrderQueue queue, boolean verbose, long timeoutMs) throws InterruptedException {
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger pills = new AtomicInteger();
        Set<Integer> seenIds = ConcurrentHashMap.newKeySet();
        AtomicReference<Throwable> error = new AtomicReference<>();

        long start = System.nanoTime();

        List<Thread> producers = new ArrayList<>();
        for (int p = 0; p < PRODUCERS; p++) {
            int firstId = p * ORDERS_PER_PRODUCER + 1;               // 1..50, 51..100
            producers.add(newThread(new Producer(queue, firstId, ORDERS_PER_PRODUCER),
                    "producer-" + (p + 1), error));
        }
        List<Thread> consumers = new ArrayList<>();
        for (int c = 0; c < CONSUMERS; c++) {
            consumers.add(newThread(new Consumer(queue, processed, pills, seenIds, verbose),
                    "consumer-" + (c + 1), error));
        }

        // One poison pill per consumer, after all producers are done.
        Thread pillSender = newThread(() -> {
            try {
                for (Thread p : producers) p.join();
                for (int i = 0; i < CONSUMERS; i++) queue.put(Order.POISON);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "pill-sender", error);

        producers.forEach(Thread::start);
        consumers.forEach(Thread::start);
        pillSender.start();

        List<Thread> all = new ArrayList<>(producers);
        all.addAll(consumers);
        all.add(pillSender);
        long deadline = System.currentTimeMillis() + timeoutMs;
        boolean finished = true;
        for (Thread t : all) {
            t.join(Math.max(1, deadline - System.currentTimeMillis()));
            if (t.isAlive()) finished = false;
        }

        long ms = (System.nanoTime() - start) / 1_000_000;
        return new Result(processed.get(), seenIds.size(), pills.get(), finished, error.get(), ms);
    }

    // Daemon, so a stuck thread cannot keep the JVM alive.
    private static Thread newThread(Runnable task, String name, AtomicReference<Throwable> error) {
        Thread t = new Thread(task, name);
        t.setDaemon(true);
        t.setUncaughtExceptionHandler((th, ex) -> error.compareAndSet(null, ex));
        return t;
    }
}

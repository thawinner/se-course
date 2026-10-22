package edu.itc.se.lab01.task3;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class Consumer implements Runnable {
    private final OrderQueue queue;
    private final AtomicInteger processed;
    private final AtomicInteger pills;
    private final Set<Integer> seenIds;      // to check no order is processed twice
    private final boolean verbose;

    public Consumer(OrderQueue queue, AtomicInteger processed, AtomicInteger pills,
                    Set<Integer> seenIds, boolean verbose) {
        this.queue = queue;
        this.processed = processed;
        this.pills = pills;
        this.seenIds = seenIds;
        this.verbose = verbose;
    }

    @Override public void run() {
        var rnd = ThreadLocalRandom.current();
        String me = Thread.currentThread().getName();
        try {
            while (true) {
                Order order = queue.take();
                if (order.isPoison()) {
                    pills.incrementAndGet();
                    if (verbose) System.out.println(me + " received poison pill, stopping");
                    return;
                }
                Thread.sleep(rnd.nextInt(5, 31));                      // "process" 5–30 ms
                if (!seenIds.add(order.id()))
                    throw new IllegalStateException("order processed twice: " + order);
                processed.incrementAndGet();
                if (verbose) System.out.println(me + " processed " + order);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

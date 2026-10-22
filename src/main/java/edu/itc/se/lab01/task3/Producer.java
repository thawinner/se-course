package edu.itc.se.lab01.task3;

import java.util.concurrent.ThreadLocalRandom;

// Poison pills are added by Scenario after all producers finish.
public class Producer implements Runnable {
    private final OrderQueue queue;
    private final int firstId;
    private final int count;

    public Producer(OrderQueue queue, int firstId, int count) {
        this.queue = queue;
        this.firstId = firstId;
        this.count = count;
    }

    @Override public void run() {
        var rnd = ThreadLocalRandom.current();
        try {
            for (int id = firstId; id < firstId + count; id++) {
                Thread.sleep(rnd.nextInt(0, 21));
                queue.put(new Order(id, "item-" + id, rnd.nextInt(1, 6)));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

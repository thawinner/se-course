package edu.itc.se.lab01.task3;

import java.util.ArrayDeque;

// Step 7: BoundedBuffer with if + notify() instead of while + notifyAll(), run 20 times.
public class NotifyIfExperiment {

    static final class BrokenBuffer implements OrderQueue {
        private final ArrayDeque<Order> items = new ArrayDeque<>();
        private final int capacity;

        BrokenBuffer(int capacity) { this.capacity = capacity; }

        @Override public synchronized void put(Order order) throws InterruptedException {
            if (items.size() == capacity) wait();   // BUG: if instead of while
            items.addLast(order);
            if (items.size() > capacity)
                throw new IllegalStateException("buffer exceeded capacity: size=" + items.size());
            notify();                                // BUG: notify instead of notifyAll
        }

        @Override public synchronized Order take() throws InterruptedException {
            if (items.isEmpty()) wait();             // BUG
            Order order = items.removeFirst();
            notify();                                // BUG
            return order;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        final int runs = 20;
        int ok = 0, hung = 0, crashed = 0, wrong = 0;
        for (int run = 1; run <= runs; run++) {
            Scenario.Result r = Scenario.run(new BrokenBuffer(Scenario.CAPACITY), false, 3_000);
            String outcome;
            if (r.error() != null) {
                crashed++;
                outcome = "CRASHED: " + r.error();
            } else if (!r.finished()) {
                hung++;
                outcome = "HUNG (threads still waiting after 3 s), processed=" + r.processed();
            } else if (!r.ok()) {
                wrong++;
                outcome = "WRONG: processed=" + r.processed() + " pills=" + r.pills();
            } else {
                ok++;
                outcome = "ok";
            }
            System.out.printf("run %2d: %s%n", run, outcome);
        }
        System.out.printf("%nnotify() + if over %d runs: ok=%d hung=%d crashed=%d wrong=%d%n",
                runs, ok, hung, crashed, wrong);
    }
}

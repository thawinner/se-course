package edu.itc.se.lab01.task3;

import java.util.ArrayDeque;

// wait() is in a while loop because the condition must be re-checked after waking;
// notifyAll() so the waiting thread that can make progress is always woken.
public class BoundedBuffer implements OrderQueue {
    private final ArrayDeque<Order> items = new ArrayDeque<>();
    private final int capacity;

    public BoundedBuffer(int capacity) { this.capacity = capacity; }

    @Override public synchronized void put(Order order) throws InterruptedException {
        while (items.size() == capacity) wait();   // wait while full
        items.addLast(order);
        check(items.size() <= capacity, "buffer exceeded capacity " + capacity);
        notifyAll();                               // wake waiting consumers
    }

    @Override public synchronized Order take() throws InterruptedException {
        while (items.isEmpty()) wait();            // wait while empty
        check(!items.isEmpty(), "take() on an empty buffer");
        Order order = items.removeFirst();
        notifyAll();                               // wake waiting producers
        return order;
    }

    // Always-on check ('assert' only runs with -ea).
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

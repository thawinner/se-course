package edu.itc.se.lab01.task2;

import java.util.concurrent.atomic.AtomicLong;

// No lock: compare-and-swap retries if another thread changed the value first.
public class AtomicCounter implements Counter {
    private final AtomicLong count = new AtomicLong();

    @Override public void increment() { count.incrementAndGet(); }
    @Override public long get()       { return count.get(); }
}

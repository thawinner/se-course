package edu.itc.se.lab01.task2;

import java.util.concurrent.atomic.LongAdder;

// Faster under contention: with AtomicLong all threads retry CAS on ONE variable;
// LongAdder gives colliding threads separate cells, and sum() adds them up.
public class LongAdderCounter implements Counter {
    private final LongAdder count = new LongAdder();

    @Override public void increment() { count.increment(); }
    @Override public long get()       { return count.sum(); }
}

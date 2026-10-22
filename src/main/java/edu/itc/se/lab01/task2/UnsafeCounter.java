package edu.itc.se.lab01.task2;

// Broken on purpose: count++ is read, add, write, so concurrent increments get lost.
public class UnsafeCounter implements Counter {
    private long count;

    @Override public void increment() { count++; }
    @Override public long get()       { return count; }
}

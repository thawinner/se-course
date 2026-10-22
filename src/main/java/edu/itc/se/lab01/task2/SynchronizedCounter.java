package edu.itc.se.lab01.task2;

// One thread at a time. get() is synchronized too, so it sees the latest value.
public class SynchronizedCounter implements Counter {
    private long count;

    @Override public synchronized void increment() { count++; }
    @Override public synchronized long get()       { return count; }
}

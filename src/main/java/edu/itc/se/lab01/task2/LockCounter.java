package edu.itc.se.lab01.task2;

import java.util.concurrent.locks.ReentrantLock;

// unlock() is in finally so the lock is always released, even on an exception.
public class LockCounter implements Counter {
    private final ReentrantLock lock = new ReentrantLock();
    private long count;

    @Override public void increment() {
        lock.lock();
        try {
            count++;
        } finally {
            lock.unlock();
        }
    }

    @Override public long get() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }
}

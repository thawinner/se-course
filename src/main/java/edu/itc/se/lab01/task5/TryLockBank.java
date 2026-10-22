package edu.itc.se.lab01.task5;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

// Fix B: if both locks are not free, release, back off randomly and retry.
// The second lock does not wait: waiting while holding the first lock is
// hold-and-wait and was much slower (see README).
public class TryLockBank {
    static final int WORKERS = 8;
    static final int TRANSFERS_PER_WORKER = 100_000;

    static final class Account {
        final int id;
        final ReentrantLock lock = new ReentrantLock();
        private long balance;
        Account(int id, long balance) { this.id = id; this.balance = balance; }
        long balance() { return balance; }
    }

    final Account[] accounts = new Account[4];
    final LongAdder retries = new LongAdder();

    TryLockBank() {
        for (int i = 0; i < accounts.length; i++) accounts[i] = new Account(i, 1_000);
    }

    void transfer(Account from, Account to, long amount) throws InterruptedException {
        var rnd = ThreadLocalRandom.current();
        while (true) {
            if (from.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                try {
                    if (to.lock.tryLock()) {
                        try {
                            if (from.balance >= amount) { from.balance -= amount; to.balance += amount; }
                            return;
                        } finally {
                            to.lock.unlock();
                        }
                    }
                } finally {
                    from.lock.unlock();   // always release before backing off
                }
            }
            retries.increment();
            Thread.sleep(Duration.ofNanos(rnd.nextLong(10_000, 1_000_000)));   // random back-off
        }
    }

    long total() {
        long sum = 0;
        for (Account a : accounts) {
            a.lock.lock();
            try { sum += a.balance; } finally { a.lock.unlock(); }
        }
        return sum;
    }

    public static void main(String[] args) throws InterruptedException {
        Watchdog.start();
        TryLockBank bank = new TryLockBank();
        LongAdder transfers = new LongAdder();

        long t0 = System.nanoTime();
        Thread[] workers = new Thread[WORKERS];
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                try {
                    for (int i = 0; i < TRANSFERS_PER_WORKER; i++) {
                        int[] pair = OrderedBank.pickTwo(bank.accounts.length, rnd);
                        bank.transfer(bank.accounts[pair[0]], bank.accounts[pair[1]], rnd.nextInt(1, 50));
                        transfers.increment();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "transfer-" + w);
            workers[w].start();
        }
        for (Thread t : workers) t.join();

        System.out.printf("transfers=%d retries=%d total=%d (expected 4000) in %d ms%n",
                transfers.sum(), bank.retries.sum(), bank.total(), (System.nanoTime() - t0) / 1_000_000);
    }
}

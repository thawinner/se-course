package edu.itc.se.lab01.task5;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

import edu.itc.se.lab01.task5.Bank.Account;

// Fix A: always lock the smaller id first, so a circular wait cannot form.
public class OrderedBank {
    static final int WORKERS = 8;
    static final int TRANSFERS_PER_WORKER = 100_000;

    static void transfer(Account from, Account to, long amount) {
        Account first  = from.id < to.id ? from : to;
        Account second = from.id < to.id ? to : from;
        synchronized (first) {
            synchronized (second) {
                if (from.balance() >= amount) { from.withdraw(amount); to.deposit(amount); }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Watchdog.start();
        Account[] accounts = new Account[4];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new Account(i, 1_000);
        LongAdder transfers = new LongAdder();

        long t0 = System.nanoTime();
        Thread[] workers = new Thread[WORKERS];
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                for (int i = 0; i < TRANSFERS_PER_WORKER; i++) {
                    int[] pair = pickTwo(accounts.length, rnd);
                    transfer(accounts[pair[0]], accounts[pair[1]], rnd.nextInt(1, 50));
                    transfers.increment();
                }
            }, "transfer-" + w);
            workers[w].start();
        }
        for (Thread t : workers) t.join();

        long total = 0; for (Account a : accounts) total += a.balance();
        System.out.printf("transfers=%d total=%d (expected 4000) in %d ms%n",
                transfers.sum(), total, (System.nanoTime() - t0) / 1_000_000);
    }

    // Two different random indexes in [0, n).
    static int[] pickTwo(int n, ThreadLocalRandom rnd) {
        int a = rnd.nextInt(n);
        int b = rnd.nextInt(n - 1);
        if (b >= a) b++;
        return new int[] {a, b};
    }
}

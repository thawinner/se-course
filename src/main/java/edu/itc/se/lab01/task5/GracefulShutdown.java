package edu.itc.se.lab01.task5;

import java.util.concurrent.ThreadLocalRandom;

// Workers stop by themselves when interrupted; main interrupts them after 2 s.
public class GracefulShutdown {
    static final int WORKERS = 8;
    static final long RUN_MS = 2_000;

    public static void main(String[] args) throws InterruptedException {
        TryLockBank bank = new TryLockBank();

        Thread[] workers = new Thread[WORKERS];
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                long done = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    int[] pair = OrderedBank.pickTwo(bank.accounts.length, rnd);
                    try {
                        bank.transfer(bank.accounts[pair[0]], bank.accounts[pair[1]], rnd.nextInt(1, 50));
                        done++;
                    } catch (InterruptedException e) {
                        // catching it clears the flag, so set it again
                        Thread.currentThread().interrupt();
                    }
                }
                System.out.printf("%s stopped after %d transfers (interrupt flag=%b)%n",
                        Thread.currentThread().getName(), done, Thread.currentThread().isInterrupted());
            }, "transfer-" + w);
            workers[w].start();
        }

        Thread.sleep(RUN_MS);   // run time, not used for ordering
        System.out.printf("[main] interrupting %d workers after %d ms%n", WORKERS, RUN_MS);
        for (Thread t : workers) t.interrupt();

        boolean allStopped = true;
        for (Thread t : workers) {
            t.join(1_000);
            if (t.isAlive()) {
                allStopped = false;
                System.out.println(t.getName() + " is STILL ALIVE after join(1000)");
            }
        }
        System.out.printf("all workers stopped=%b total=%d (expected 4000)%n", allStopped, bank.total());
    }
}

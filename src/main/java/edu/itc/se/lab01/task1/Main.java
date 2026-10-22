package edu.itc.se.lab01.task1;

import java.util.List;

/*
 * start(): each countdown runs on a NEW thread and start() returns at once, so
 *   "all launched" prints first, the countdowns interleave, and join() makes
 *   "all landed" print last. All threads end TERMINATED.
 * run() (--sequential): a normal method call on the main thread, so the countdowns
 *   run one after another before "all launched". gemini and mercury print "main"
 *   (the current thread), artemis prints nothing (run() on a virtual thread does
 *   nothing), and all threads stay NEW because they were never started.
 */
public class Main {
    public static void main(String[] args) throws InterruptedException {
        boolean sequential = args.length > 0 && args[0].equals("--sequential");

        Thread apollo  = new Countdown("apollo");                                          // 1. subclass
        Thread gemini  = new Thread(new CountdownTask(), "gemini");                        // 2. Runnable
        Thread mercury = new Thread(() -> new CountdownTask().run(), "mercury");           // 3. lambda
        Thread artemis = Thread.ofVirtual().name("artemis").unstarted(new CountdownTask()); // 4. virtual

        List<Thread> rockets = List.of(apollo, gemini, mercury, artemis);

        for (Thread t : rockets) {
            if (sequential) t.run();
            else            t.start();
        }
        System.out.println("Mission control: all launched");

        for (Thread t : rockets) t.join();
        System.out.println("Mission control: all landed");

        for (Thread t : rockets) {
            System.out.printf("%-8s virtual=%-5b state=%s%n",
                    t.getName(), t.isVirtual(), t.getState());
        }
    }
}

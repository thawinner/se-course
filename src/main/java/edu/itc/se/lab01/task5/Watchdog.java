package edu.itc.se.lab01.task5;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

// Daemon thread that prints deadlocked threads every second.
final class Watchdog {
    private Watchdog() {}

    static Thread start() {
        return Thread.ofPlatform().name("watchdog").daemon(true).start(() -> {
            ThreadMXBean mx = ManagementFactory.getThreadMXBean();
            String lastReport = "";
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    continue;
                }
                long[] ids = mx.findDeadlockedThreads();   // null if none
                if (ids == null) continue;
                String names = Arrays.stream(mx.getThreadInfo(ids))
                        .filter(Objects::nonNull)
                        .map(ThreadInfo::getThreadName)
                        .sorted()
                        .collect(Collectors.joining(", "));
                if (!names.equals(lastReport)) {           // report each deadlock once
                    System.out.println("[watchdog] DEADLOCK detected between: " + names);
                    lastReport = names;
                }
            }
        });
    }
}

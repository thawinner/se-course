package edu.itc.se.lab01.task3;

// Usage: Main (one run) or Main 20 (20 quiet runs).
public class Main {
    public static void main(String[] args) throws InterruptedException {
        int runs = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        runAll("BoundedBuffer", runs, () -> new BoundedBuffer(Scenario.CAPACITY));
    }

    interface QueueFactory { OrderQueue create(); }

    static void runAll(String label, int runs, QueueFactory factory) throws InterruptedException {
        boolean verbose = runs == 1;
        int okRuns = 0;
        for (int run = 1; run <= runs; run++) {
            Scenario.Result r = Scenario.run(factory.create(), verbose, 10_000);
            if (!r.ok()) {
                throw new IllegalStateException(label + " run " + run + " failed: " + r);
            }
            okRuns++;
            System.out.printf("%sTotal processed: %d (expected %d) in %d ms%n",
                    runs > 1 ? "[" + label + " run " + run + "] " : "",
                    r.processed(), Scenario.EXPECTED, r.ms());
        }
        if (runs > 1) System.out.printf("%s: %d/%d runs correct%n", label, okRuns, runs);
    }
}

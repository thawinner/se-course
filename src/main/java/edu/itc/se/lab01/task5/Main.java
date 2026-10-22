package edu.itc.se.lab01.task5;

// The broken Bank is run on its own because it hangs.
public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("# OrderedBank");
        OrderedBank.main(args);
        System.out.println("\n# TryLockBank");
        TryLockBank.main(args);
        System.out.println("\n# graceful shutdown");
        GracefulShutdown.main(args);
    }
}

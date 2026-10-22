package edu.itc.se.lab01.task3;

// Same as Main, but with ArrayBlockingQueue. Usage: Main2 or Main2 20.
public class Main2 {
    public static void main(String[] args) throws InterruptedException {
        int runs = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        Main.runAll("ArrayBlockingQueue", runs, () -> new BlockingOrderQueue(Scenario.CAPACITY));
    }
}

package edu.itc.se.lab01.task3;

// Producer and Consumer only use this interface, so any buffer can plug in.
public interface OrderQueue {
    void put(Order order) throws InterruptedException;   // blocks while full
    Order take() throws InterruptedException;            // blocks while empty
}

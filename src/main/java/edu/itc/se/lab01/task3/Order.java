package edu.itc.se.lab01.task3;

public record Order(int id, String item, int qty) {

    // Poison pill: tells a consumer to stop.
    static final Order POISON = new Order(-1, "poison", 0);

    boolean isPoison() { return id == -1; }
}

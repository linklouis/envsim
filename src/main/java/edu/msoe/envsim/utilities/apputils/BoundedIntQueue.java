package edu.msoe.envsim.utilities.apputils;

import java.util.NoSuchElementException;

public class BoundedIntQueue {
    private final int[] data;
    private int head = 0; // Index of the oldest element
    private int size = 0; // Current number of elements

    public BoundedIntQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0");
        }
        this.data = new int[capacity];
    }

    public void push(int value) {
        int tail = (head + size) % data.length;
        data[tail] = value;

        if (size == data.length) {
            // Buffer is full; advance head to drop the overwritten oldest element
            head = (head + 1) % data.length;
        } else {
            size++;
        }
    }

    public int pop() {
        if (size == 0) {
            throw new NoSuchElementException("Queue is empty");
        }
        int oldest = data[head];
        head = (head + 1) % data.length;
        size--;
        return oldest;
    }

    public int peek() {
        if (size == 0) {
            throw new NoSuchElementException("Queue is empty");
        }
        return data[head];
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int[] array() {
        return data;
    }
}




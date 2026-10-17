package com.fw.main.utils;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

public class LockFreeAppendList<E> implements Iterable<E> {
    private static class Node<E> {
        final E item;
        final AtomicReference<Node<E>> next = new AtomicReference<>(null);

        Node(E item) {
            this.item = item;
        }
    }

    private final Node<E> head = new Node<>(null);
    private final AtomicReference<Node<E>> tail = new AtomicReference<>(head);

    public void add(E value) {
        Node<E> newNode = new Node<>(value);
        while (true) {
            Node<E> curTail = tail.get();
            Node<E> tailNext = curTail.next.get();

            if (curTail == tail.get()) {
                if (tailNext != null) {
                    tail.compareAndSet(curTail, tailNext);
                } else {
                    if (curTail.next.compareAndSet(null, newNode)) {
                        tail.compareAndSet(curTail, newNode);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public Iterator<E> iterator() {
        return new Iterator<>() {
            private Node<E> curr = head.next.get();

            @Override
            public boolean hasNext() {
                return curr != null;
            }

            @Override
            public E next() {
                E item = curr.item;
                curr = curr.next.get();
                return item;
            }
        };
    }
}
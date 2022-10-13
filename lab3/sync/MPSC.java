package sync;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class MPSC<E> {
    private int bottom = 0;
    private AtomicReferenceArray<Object> buffer;
    private AtomicInteger top = new AtomicInteger(0);

    public MPSC(int capacity) {
        buffer = new AtomicReferenceArray<>(capacity);
    }

    public void enq(E x) {
        // NOTE: Lock-free, but not wait-free.
        // It is gauranteed to be infallible.
        while (true) {
            int localTop = top.get();

            // NOTE: Waiting for the dequeuing
            // thread to reset the buffer.
            while (localTop >= buffer.length())
                localTop = top.get();

            // NOTE: Competing to enqueue at the
            // index where localTop is, otherwise retry.
            if (top.compareAndSet(localTop, localTop + 1)) {
                // POTENTIAL: Problem here.
                // Consumer can just deq here.
                // Ignore for now, since I have to
                // implement linked list instead.
                buffer.set(localTop, x);
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public E deq() {
        int localTop = top.get();

        if (localTop == 0) {
            return null;
        }

        if (bottom >= localTop) {
            // NOTE: Attempt to reset the buffer
            // if success return null. Otherwise,
            // other threads must have enqueue
            // some items, proceed to dequeue it.
            if (top.compareAndSet(localTop, 0)) {
                bottom = 0;
                return null;
            }
        }

        E localItem = (E) buffer.get(bottom);

        if (localItem != null) {
            bottom += 1;
            return localItem;
        } else {
            return null;
        }
    }

}
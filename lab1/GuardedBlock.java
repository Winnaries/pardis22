/**
 * 
 * Write a short program in which one thread increments an integer 1,000,000
 * times, and a second thread prints the integer -- without waiting for it to
 * finish.
 * 
 * Modify the program to use a condition variable to signal completion of the
 * incrementing task by the first thread before the second thread prints the
 * value.
 * 
 */

public class GuardedBlock {

    public static void main(String[] args) {
        Counter c = new Counter(); 
        Thread pthread = new Thread(new PrintThread(c));
        Thread ithread = new Thread(new IncrementThread(c));
        
        pthread.start(); 
        ithread.start(); 

        try {
            pthread.join(); 
            ithread.join(); 
        } catch (InterruptedException e) {}
    }

}

class PrintThread implements Runnable {
    Counter counter;

    public PrintThread(Counter counter) {
        this.counter = counter; 
    }
    
    public void run() {
        counter.block(); 
        System.out.printf("Current counting value is %d\n", counter.value());
    }
    
}

class IncrementThread implements Runnable {
    Counter counter; 

    public IncrementThread(Counter counter) {
        this.counter = counter; 
    }
    
    public void run() {
        for (int i = 0; i < 1000000; i += 1) {
            counter.increment(); 
        }

        counter.finish(); 
    }

}

class Counter {
    static int current = 0; 
    static boolean finished = false; 
    
    public int increment() {
        return current++; 
    }

    public int value() {
        return current; 
    }

    public synchronized void finish() {
        finished = true; 
        notifyAll(); 
    }

    public synchronized void block() {
        while (!finished) {
            try {
                wait();
            } catch (InterruptedException e) {}
        }
    }
}
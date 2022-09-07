/**
 * In the producer-consumer problem we have a producer thread wishing to pass
 * data items one at a time to a consumer thread using a shared, fixed-size
 * buffer of size, say, 100. Implement a ProducerConsumer class using
 * synchronized(), wait(), and notify() in Java, and use it to pass a 1000000
 * items sequence of integer values from one thread (the producer) to a second
 * thread (the consumer).
 * 
 * A (counting) semaphore is a shared variable with two atomic operations
 * signal() and wait(). Semaphores are used to govern access to some shared
 * resource. A non-negative value n of the semaphore indicates that there are n
 * amounts of resource available. Similarly, a negative value of the semaphore
 * indicates the number of threads waiting for a resource. signal() increments
 * the value of the semaphore by 1 to indicate that a resource has been made
 * available. This causes a waiting thread to be activated, if the value of the
 * semaphore just prior to executing signal() is negative. wait() decrements the
 * value of the semaphore by 1. If the value of the semaphore is now negative,
 * the calling thread is suspended. Implement a counting semaphore using
 * synchronized(), wait(), and notify() in Java, and test it on a suitably
 * simple example.
 * 
 * In the Dining Philosophers Problem there are five philosophers sitting around
 * a round table, as shown in the figure below. Each philosopher has a plate and
 * a chopstick shared with the left- and right hand neighbour. Each philosopher
 * alternately thinks and eats. To eat, a philosopher needs two chopsticks.
 * Using some of the mechanisms introduced in the lab, implement a solution to
 * the Dining Philosophers Problem that ensures that each philosopher who some
 * time tries to eat eventually does so. Your solution should work for an
 * arbitrary number of philosophers. The solution should be deadlock-free, i.e.
 * not be able to reach a configuration that cannot progress, and
 * starvation-free, i.e. in any infinite run, all philosophers get to eat
 * infinitely often. Argue carefully why your solution meets these requirements.
 * [See also exercise 1 in Herlihy and Shavit. There are a number of solutions
 * to this problem on the web and in textbooks. Try to come up with a solution
 * on your own, and declare if you have resorted to an external source for
 * input.]
 */

public class SemaphoreExample {
    
    public static void main(String[] args) {
        ProducerConsumer pc = new ProducerConsumer(100);
        Thread producer = new Thread(new Producer(pc));
        Thread consumer0 = new Thread(new Consumer(pc)); 

        producer.start(); 
        consumer0.start();
        
        try {
            producer.join(); 
            consumer0.join(); 
        } catch (InterruptedException e) {}
    }

}

class Producer implements Runnable {
    ProducerConsumer pc; 
    
    public Producer(ProducerConsumer pc) {
        this.pc = pc; 
    }
    
    public void run() {
        for (int i = 0; i < 1000000; i += 1) {
            pc.push(i);
        }
    }

}

class Consumer implements Runnable {
    ProducerConsumer pc; 
    
    public Consumer(ProducerConsumer pc) {
        this.pc = pc; 
    }

    public void run() {
        int count = 0; 
        while (count < 1000000) {
            pc.pop(); 
            count += 1; 
        }
    }

}

class ProducerConsumer {
    
    int maxSize; 
    int[] buffer; 
    CountingSemaphore count; 
    CountingSemaphore available;
    
    public ProducerConsumer(int size) {
        this.maxSize = size; 
        this.buffer = new int[size];
        this.count = new CountingSemaphore(); 
        this.available = new CountingSemaphore(size); 
    }

    public void push(int value) {
        int index = maxSize - available.block() - 1;
        buffer[index] = value; 
        count.signal(); 
    }

    public int pop() {
        int index = count.block();
        int temp = buffer[index >= 0 ? index : 0]; 
        available.signal(); 
        return temp; 
    }

}

class CountingSemaphore {
    
    private int count; 

    public CountingSemaphore() {
        this.count = 0; 
    }

    public CountingSemaphore(int initial) {
        this.count = initial; 
    }

    public synchronized int value() {
        return count;
    }

    public synchronized void signal() {
        count += 1; 
        if (count <= 1) {
            notify();
        }
    }

    public synchronized int block() {
        count -= 1; 

        if (count < 0) {
            try {
                wait(); 
            } catch (InterruptedException e) {
            }
        }

        return count; 
    }

}
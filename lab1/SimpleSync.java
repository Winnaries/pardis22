/**
 * Figure out how to use the Java API to extract the number of physical threads
 * available on the CPU you are running on.
 * 
 * Write a short program in which n threads for increasing n, say n = 2, 6, 12,
 * increment a shared integer repeatedly, without proper synchronisation,
 * 1,000,000 times, printing the resulting value at the end of the program. Run
 * the program on a multicore system and attempt to exercise the potential race
 * in the program.
 * 
 * Modify the program to use "synchronized" to ensure that increments on the
 * shared variable are atomic.
 */

public class SimpleSync implements Runnable {

    public void run() { 
        for (int j = 0; j < 1000000; j += 1) {
            Counter.increment(); 
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        int n = Integer.parseInt(args[0]); 
        int cores = Runtime.getRuntime().availableProcessors(); 
        System.out.printf("Running on %d cores computer...\n", cores);
        
        Thread[] threads = new Thread[n]; 

        for (int i = 0; i < n; i += 1) {
            threads[i] = new Thread(new SimpleSync()); 
            threads[i].start(); 
        }

        for (int i = 0; i < n; i += 1) {
            threads[i].join(); 
        }
        
        System.out.printf("Increasing value result is %d\n", Counter.value);
    }

}

class Counter {
    
    static int value = 0; 

    public synchronized static int increment() {
        return value++; 
    }

}
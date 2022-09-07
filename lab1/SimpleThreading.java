/**
 * 
 * Write a short program that prints "Hello world" from an additional thread
 * using the Java Thread API.
 * 
 * Modify the program to print "Hello world" five times, once from each of five
 * different threads. Ensure that the strings are not interleaved in the output.
 * 
 * Modify the printed string to include the thread number; ensure that all
 * threads have a unique thread number.
 * 
 */

class FirstExercise implements Runnable {

    public synchronized void run() {
        for (int i = 0; i < 5; i += 1) {
            System.out.printf("%d\tHello world\n", Thread.currentThread().getId());
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 5; i += 1) {
            (new Thread(new FirstExercise())).start(); 
        }
    }

}
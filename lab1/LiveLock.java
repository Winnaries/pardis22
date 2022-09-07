//The ThreadMXBean interface provides the findMonitorDeadlockedThreads() and findDeadlockedThreads() methods to find deadlocks in the running application.
import java.lang.management.*;

public class LiveLock {
    private boolean[] flag = new boolean[2];
    public static void main(String[] args) {
        LiveLock liveLock = new LiveLock();
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                liveLock.thread1();
            }
        });
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                liveLock.thread2();
            }
        });
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        System.out.println(threadBean.findDeadlockedThreads());
        t1.setName("Thread 1");
        t2.setName("Thread 2");
        t1.start();
        t2.start();
        System.out.println("Both threads started");
    }

    private void thread1() {
        while (true) {
            flag[0] = true;
            while (flag[1]) {
                if (flag[0]) {
                    flag[0] = false;
                }
            }
            //critical section
            System.out.println("Thread 1");
            flag[0] = false;
        }
    }

    private void thread2() {
        while (true) {
            flag[1] = true;
            while (flag[0]) {
                if (flag[1]) {
                    flag[1] = false;
                }
            }
            //critical section
            System.out.println("Thread 2");
            flag[1] = false;
        }
    }

    public void lock() {
        int i = Thread.currentThread().getName().equals("Thread 1") ? 1 : 0;
        int j = 1 - i;
        flag[i] = true;
        while (flag[j]) {
            flag[i] = false;
            while (flag[j]) {
            }
            flag[i] = true;
        }
    }
    public void unlock() {
        int i = Thread.currentThread().getName().equals("Thread 1") ? 1 : 0;
        flag[i] = false;
    }
}

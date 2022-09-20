package lab2;

import java.util.List; 
import java.util.ArrayList; 
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

class MergeSortTask implements Callable<Boolean> {
    int l, r;
    int m = -1;  
    Integer[] array; 

    public MergeSortTask(Integer[] array, int l, int r) {
        this.array = array; 
        this.l = l; 
        this.r = r; 
    }

    public MergeSortTask(Integer[] array, int l, int r, boolean isMerge) {
        this.array = array;
        this.l = l;
        this.r = r;
        
        if (isMerge) {
            this.m = (l + r) / 2;
        }
    }

    public Boolean call() {
        if (m == -1) MergeSort.sort(array, l, r);
        else MergeSort.merge(array, l, m, r);
        return true; 
    }
}

class MergeSortES {

    public static void main(String[] args) {
        int n = Integer.parseInt(args[0]); 
        int nThreads = Integer.parseInt(args[1]);
        int nearestPowerTwo = MergeSortUtils.nextPowerOfTwo(nThreads); 
        int depth = (int) Math.round(Math.log(nearestPowerTwo) / Math.log(2)); 

        // CREATE ROOT LEVEL
        int[][] left = new int[depth + 1][];
        int[][] right = new int[depth + 1][];

        // FILL ROOT LEVEL
        left[0] = new int[1];
        right[0] = new int[1];  
        left[0][0] = 0; 
        right[0][0] = n - 1; 

        for (int i = 1; i < depth + 1; i += 1) {
            int length = 1 << i; 
            int prev = i - 1; 

            // CREATE NEXT LEVEL
            left[i] = new int[length]; 
            right[i] = new int[length];
            
            // TRAVERSE PREVIOUS LEVEL
            for (int j = 0; j < left[prev].length; j += 1) {
                int l = left[prev][j];
                int r = right[prev][j];
                int m = (l + r) / 2; 

                // LEFT HALF
                left[i][2 * j] = l; 
                right[i][2 * j] = m; 

                // RIGHT HALF
                left[i][2 * j + 1] = m + 1; 
                right[i][2 * j + 1] = r; 
            }
        }

        // GENERATE INTEGER ARRAY
        Integer[] array = MergeSortUtils.generate(n); 
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);
        ArrayList<MergeSortTask> tasks = new ArrayList<>(nearestPowerTwo);

        // START TIMER
        long start = System.nanoTime();

        // SCHEDULES SEQUENTIAL TASKS
        for (int i = 0; i < left[depth].length; i += 1) {
            tasks.add(new MergeSortTask(array, left[depth][i], right[depth][i]));
        }

        List<Future<Boolean>> futures = null;

        try {
            futures = pool.invokeAll(tasks);

            // MERGE RESULTS
            for (int i = depth - 1; i >= 0; i -= 1) {
                // SCHEDULE NEXT TASKS
                tasks.clear();
                for (int j = 0; j < left[i].length; j += 1) {
                    tasks.add(new MergeSortTask(array, left[i][j], right[i][j], true));
                }

                // WAIT FOR PREVIOUS EXEC
                for (Future<Boolean> f : futures) f.get(); 

                // RUN NEXT TASKS
                futures = pool.invokeAll(tasks);
            }
            
            // MERGE LAST RESULTS
            if (futures != null) {
                for (Future<Boolean> f : futures) f.get();
            }
        } catch (Exception e) {}

        // STOP TIMER
        System.out.printf("Total %d ms elapsed\n", (System.nanoTime() - start) / 1000000);

        // CORRECTNESS TEST 
        System.exit(MergeSortUtils.test(array) ? 0 : 1); 
    }

}
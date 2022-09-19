package lab2;

import java.util.List; 
import java.util.ArrayList;
import java.util.concurrent.*;

class MergeSortTaskSlow implements Callable<Boolean> {
    int l, m, r; 
    Integer[] array; 

    public MergeSortTaskSlow(Integer[] array, int l, int r) {
        this.l = l; 
        this.r = r; 
        this.m = (l + r) / 2; 
        this.array = array; 
    }

    public int left() {
        return l;
    }

    public int right() {
        return r; 
    }

    public int middle() {
        return m; 
    }

    public Boolean call() { 
        MergeSort.merge(array, l, m, r);
        return true; 
    }

}

class MergeSortScheduler {
    ArrayList<ArrayList<MergeSortTaskSlow>> tasks = new ArrayList<ArrayList<MergeSortTaskSlow>>(); 

    public MergeSortScheduler(Integer[] array, int l, int r) {
        ArrayList<MergeSortTaskSlow> list = new ArrayList<MergeSortTaskSlow>(); 
        list.add(new MergeSortTaskSlow(array, l, r)); 
        tasks.add(list); 

        int traversed = 0;
        while (traversed < tasks.size()) {
            ArrayList<MergeSortTaskSlow> next = new ArrayList<MergeSortTaskSlow>();
            ArrayList<MergeSortTaskSlow> prev = tasks.get(traversed);
            int n = prev.size();

            for (int i = 0; i < n; i += 1) {
                MergeSortTaskSlow parent = prev.get(i);
                if (parent.left() < parent.middle())
                    next.add(new MergeSortTaskSlow(parent.array, parent.left(), parent.middle()));
                if (parent.middle() + 1 < parent.right())
                    next.add(new MergeSortTaskSlow(parent.array, parent.middle() + 1, parent.right()));
            }
            
            if (!next.isEmpty()) tasks.add(next);
            traversed += 1;
        }
    }
}

class MergeSortOldES {
    
    public static void main(String[] args) {
        int nItems = Integer.parseInt(args[0]); 
        int nThreads = Integer.parseInt(args[1]);
        Integer[] array = MergeSortUtils.generate(nItems); 
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);
        MergeSortScheduler scheduler = new MergeSortScheduler(array, 0, nItems - 1);
        
        
        long start = System.nanoTime();

        // -- START --

        for (int i = scheduler.tasks.size() - 1; i >= 0; i -= 1) {
            try {
                List<Future<Boolean>> futures = pool.invokeAll(scheduler.tasks.get(i)); 
                for (Future<Boolean> f : futures) f.get(); 
            } catch (Exception e) {}
        }
        
        // -- END --

        System.out.printf("Total %d ms elapsed\n", (System.nanoTime() - start) / 1000000);
        
        if (MergeSortUtils.test(array)) {
            System.out.println("Test passed");
            System.exit(0);
        } else {
            System.out.println("Test failed");
            System.exit(1); 
        }
    }

}
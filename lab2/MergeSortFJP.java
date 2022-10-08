package lab2;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

class ForkMergeSort extends RecursiveAction {
    int l, m, r; 
    Integer[] array; 

    private static final int THESHOLD = 100; 

    public ForkMergeSort(Integer[] array, int l, int r) {
        this.array = array; 
        this.l = l; 
        this.r = r; 
        this.m = (l + r) / 2;
    }

    public void compute() {
        if (r - l + 1 < ForkMergeSort.THESHOLD) {
            MergeSort.sort(array, l, r); 
        } else {
            ForkMergeSort child = new ForkMergeSort(array, l, m); 
            ForkMergeSort own = new ForkMergeSort(array, m + 1, r);
            child.fork(); 
            own.compute(); 
            child.join(); 
            MergeSort.merge(array, l, m, r); 
        }
    }
    
}

public class MergeSortFJP {
    
    public static void main(String[] args) {
        int nItems = Integer.parseInt(args[0]);
        int nThreads = Integer.parseInt(args[1]);
        Integer[] array = MergeSortUtils.generate(nItems); 
        ForkMergeSort task = new ForkMergeSort(array, 0, nItems - 1);
        ForkJoinPool pool = new ForkJoinPool(nThreads); 

        // START TIMER
        long start = System.nanoTime();

        // START SORTING
        pool.invoke(task); 

        // STOP TIMER
        System.out.printf("Total %d ms elapsed\n", (System.nanoTime() - start) / 1000000);

        // CORRECTNESS TEST
        System.exit(MergeSortUtils.test(array) ? 0 : 1);
    }

}

package lab2; 

import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.ArrayList;
import java.util.List; 
import java.util.stream.Stream; 

class MergeSortCollector {
    private Integer[] array = null; 
    private ArrayList<Integer> holders = new ArrayList<Integer>(); 

    public Integer[] sorted() {
        if (array == null) {
            Integer[] temp = holders.toArray(new Integer[0]);
            MergeSort.sort(temp, 0, temp.length - 1);
            this.array = temp; 
        }

        return array; 
    }

    public void accept(Integer x) {
        holders.add(x); 
    }

    public void combine(MergeSortCollector other) {
        Integer[] left = this.sorted(); 
        Integer[] right = other.sorted(); 

        int a = left.length; 
        int b = right.length; 

        Integer[] merged = new Integer[a + b];
        
        int x = 0, y = 0, k = 0;

        while (x < a && y < b) {
            if (left[x] < right[y]) {
                merged[k] = left[x];
                x += 1;
            } else {
                merged[k] = right[y];
                y += 1;
            }

            k += 1;
        }

        while (x < a) {
            merged[k] = left[x];
            x += 1;
            k += 1;
        }

        while (y < b) {
            merged[k] = right[y];
            y += 1;
            k += 1;
        }

        this.array = merged; 
    }

}

class MergeSortStream {

    public static void main(String[] args) {
        System.out.println("Cores: " + Runtime.getRuntime().availableProcessors());

        // GENERATE SHUFFLED INTEGER ARRAY
        int nItems = Integer.parseInt(args[0]); 
        int nThreads = Integer.parseInt(args[1]); 
        Integer[] array = MergeSortUtils.generate(nItems); 
        ForkJoinPool pool = new ForkJoinPool(nThreads); 
        List<Integer> list = Arrays.asList(array);

        try {
            // CUSTOM THREAD POOL TRICK WITH FJP
            pool.submit(() -> {
                // CREATE PARALLEL STREAM
                Stream<Integer> stream = list.parallelStream(); 

                // START TIMER
                long start = System.nanoTime();

                // RUN MERGE SORT IN PARALLEL
                MergeSortCollector msCollect = stream.collect(
                    MergeSortCollector::new, 
                    MergeSortCollector::accept, 
                    MergeSortCollector::combine
                );

                // STOP TIMER
                System.out.printf("Total %d ms elapsed\n", (System.nanoTime() - start) / 1000000);
                
                // CORRECTNESS TEST
                System.exit(MergeSortUtils.test(msCollect.sorted()) ? 0 : 1);
            }).get();
        } catch (Exception e) {}
    }

}
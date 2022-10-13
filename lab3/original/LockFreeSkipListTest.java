package original; 

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import common.Config;

public class LockFreeSkipListTest {

    static class Task implements Callable<Boolean> {
        int id;
        Integer[] ops;
        Integer[] values;

        LockFreeSkipList<Integer> skiplist;

        public Task(int id, LockFreeSkipList<Integer> skiplist, Config config) {
            ops = new Integer[config.opsPerThread];
            values = new Integer[config.opsPerThread];
            int[] stats = new int[3];
            this.skiplist = skiplist;
            this.id = id;

            int ndist = config.probs.length;
            outer: for (int i = 0; i < config.opsPerThread; i += 1) {
                double opsSample = config.rng.nextDouble();
                values[i] = config.testDist.getSample();

                for (int j = 0; j < ndist - 1; j += 1) {
                    if (opsSample < config.probs[j]) {
                        ops[i] = j;
                        stats[j] += 1;
                        continue outer;
                    }
                }

                ops[i] = ndist - 1;
                stats[ndist - 1] += 1;
            }

            System.out.printf("%2d: %7d contains, %7d add, %7d remove\n",
                    id, stats[0], stats[1], stats[2]);
        }

        public Boolean call() {
            for (int i = 0; i < ops.length; i += 1) {
                if (ops[i] == 0)
                    skiplist.contains(values[i]);
                else if (ops[i] == 1)
                    skiplist.add(values[i]);
                else if (ops[i] == 2)
                    skiplist.remove(values[i]);
                else
                    throw new Error("Unexpected operation " + ops[i]);
            }

            return true;
        }
    }

    public static void main(String[] args) {
        Config config = new Config(args); 
        config.print(); 

        LockFreeSkipList<Integer> skiplist = new LockFreeSkipList<Integer>();

        int success = 0;
        for (int i = 0; i < config.nitems; i += 1) {
            if (skiplist.add(config.prepDist.getSample()))
                success++;
        }

        System.out.printf("-1: %7d items\n", success);

        List<Future<Boolean>> futures = null;
        ArrayList<Task> tasks = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(config.nthreads);

        for (int i = 0; i < config.nthreads; i += 1) {
            tasks.add(new Task(i, skiplist, config));
        }

        long start = System.nanoTime();

        try {
            futures = pool.invokeAll(tasks.subList(0, config.nthreads - 1));
            tasks.get(config.nthreads - 1).call();
            for (Future<Boolean> f : futures)
                f.get();
        } catch (Exception e) {
        }

        System.out.println();
        System.out.println("Time elapsed: " + (System.nanoTime() - start) / 1000000 + " ms");
        pool.shutdownNow();
    }

}
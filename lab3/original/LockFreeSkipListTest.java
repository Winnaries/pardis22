import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class LockFreeSkipListTest {

    static final int MIN = 0;
    static final int MAX = 10_000_000;
    static final int LENGTH = 10_000_000;

    static final double[] CUMULATIVE_PROB = { 0.8, 0.9, 1.0 };

    static class Task implements Callable<Boolean> {
        int id;
        Integer[] ops;
        Integer[] values;

        LockFreeSkipList<Integer> skiplist;

        public Task(int id, LockFreeSkipList<Integer> skiplist, int nops, Random rng, Population population) {
            ops = new Integer[nops];
            values = new Integer[nops];
            int[] stats = new int[3];
            this.skiplist = skiplist;
            this.id = id;

            int ndist = CUMULATIVE_PROB.length;
            outer: for (int i = 0; i < nops; i += 1) {
                double opsSample = rng.nextDouble();
                values[i] = population.getSample();

                for (int j = 0; j < ndist - 1; j += 1) {
                    if (opsSample < CUMULATIVE_PROB[j]) {
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
        boolean isUniform = args[0].equalsIgnoreCase("uniform");
        int nthreads = Integer.parseInt(args[1]);
        int nitems = Integer.parseInt(args[2]);
        int seed = Integer.parseInt(args[3]);
        int opsPerThread = nitems / nthreads;

        Random rng = new Random(seed);
        LockFreeSkipList<Integer> skiplist = new LockFreeSkipList<Integer>();

        // NOTE: Have to vary the seed because otherwise
        // the random number will happens in the same order. 
        Population prefill = isUniform
                ? new UniformPopulation(seed * 2, MIN, MAX)
                : new NormalPopulation(seed * 2, MIN, MAX, 0f, 1f);
        Population population = isUniform
                ? new UniformPopulation(seed * 3, MIN, MAX)
                : new NormalPopulation(seed * 3, MIN, MAX, 0f, 1f);

        // NOTE: Insert 10 millions random integers
        // before running the test. 
        int success = 0;
        for (int i = 0; i < LENGTH; i += 1) {
            if (skiplist.add(prefill.getSample()))
                success++;
        }

        System.out.printf("-1: %7d items\n", success);

        // NOTE: Pre-allocated neccessary data 
        // structure for parallel execution. 
        List<Future<Boolean>> futures = null;
        ArrayList<Task> tasks = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(nthreads);

        for (int i = 0; i < nthreads; i += 1) {
            tasks.add(new Task(i, skiplist, opsPerThread, rng, population));
        }

        long start = System.nanoTime();

        try {
            futures = pool.invokeAll(tasks.subList(0, nthreads - 1));
            tasks.get(nthreads - 1).call();
            for (Future<Boolean> f : futures)
                f.get();
        } catch (Exception e) {
        }

        System.out.println();
        System.out.println("Time elapsed: " + (System.nanoTime() - start) / 1000000 + " ms");
        pool.shutdownNow();
    }

}
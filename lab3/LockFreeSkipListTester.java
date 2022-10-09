import java.util.Random;

public class LockFreeSkipListTester {

    static final double[] CUMULATIVE_PROB = { 0.0, 0.5, 1.0 }; // { CONTAINS, ADD, REMOVE }

    static class Task implements Runnable {
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

        public void run() {
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
        }

    }

    public static void main(String[] args) {
        boolean isUniform = args[0].equalsIgnoreCase("uniform");
        int nthreads = Integer.parseInt(args[1]);
        int seed = Integer.parseInt(args[2]);
        int opsPerThread = 1_000_000 / nthreads;

        Random rng = new Random(seed);
        Thread[] threads = new Thread[nthreads];
        LockFreeSkipList<Integer> skiplist = new LockFreeSkipList<Integer>();

        Population population = isUniform 
            ? new UniformPopulation(seed * 2, 0, 100)
            : new NormalPopulation(seed * 2, 0, 100, 0f, 1f);

        for (int i = 0; i < nthreads; i += 1) {
            threads[i] = new Thread(new Task(i, skiplist, opsPerThread, rng, population));
        }

        long start = System.nanoTime();

        for (int i = 0; i < nthreads - 1; i += 1) {
            threads[i].start();
        }

        threads[nthreads - 1].run();

        try {
            for (int i = 0; i < nthreads - 1; i += 1) {
                threads[i].join();
            }
        } catch (Exception e) {
        }

        System.out.println("Time elapsed: " + (System.nanoTime() - start) / 1000000 + " ms");
    }

}

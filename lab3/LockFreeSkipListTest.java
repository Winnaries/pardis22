import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class LockFreeSkipListTest {

    static final int MIN = 0;
    static final int MAX = 10000;
    static final int LENGTH = 10000;

    // static final int MIN = 0;
    // static final int MAX = 10_000_000;
    // static final int LENGTH = 10_000_000;

    static final double[] CUMULATIVE_PROB = { 0.0, 0.5, 1.0 };

    static class Task implements Callable<Boolean> {
        int id;
        Integer[] ops;
        Integer[] values;

        // global variables, shared across thread
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

        // create the data structure
        Random rng = new Random(seed);
        LockFreeSkipListRecordBook<Integer> book = new LockFreeSkipListRecordBook<Integer>();
        LockFreeSkipList<Integer> skiplist = new LockFreeSkipList<Integer>();

        // tell the skiplist to record ops history
        skiplist.recordOps(book);

        // pre-populate the skiplist
        Population prefill = isUniform
                ? new UniformPopulation(seed * 2, MIN, MAX)
                : new NormalPopulation(seed * 2, MIN, MAX, 0f, 1f);

        int success = 0;
        for (int i = 0; i < LENGTH; i += 1) {
            if (skiplist.add(prefill.getSample()))
                success++;
        }

        // log number of successfully added items
        System.out.printf("-1: %7d items\n", success);

        // create thread pool
        List<Future<Boolean>> futures = null;
        ArrayList<Task> tasks = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(nthreads);

        // operation value distribution
        Population population = isUniform
                ? new UniformPopulation(seed * 3, 0, 100)
                : new NormalPopulation(seed * 3, 0, 100, 0f, 1f);

        for (int i = 0; i < nthreads; i += 1) {
            tasks.add(new Task(i, skiplist, opsPerThread, rng, population));
        }

        // start timer
        long start = System.nanoTime();

        try {
            // perform parallely
            futures = pool.invokeAll(tasks);
            // futures = pool.invokeAll(tasks.subList(0, nthreads - 1));
            // tasks.get(nthreads - 1).call();
            for (Future<Boolean> f : futures)
                f.get();
        } catch (Exception e) {
        }

        // log elapsed time
        System.out.println(); 
        System.out.println("Time elapsed: " + (System.nanoTime() - start) / 1000000 + " ms");
        pool.shutdownNow();
        book.finished();
        // book.print();

        if (LockFreeSkipListValidator.isSeqCst(book, MIN, MAX)) {
            System.out.println("The history is sequentially consistent");
        } else {
            System.out.println("The history is NOT sequentially consistent");
        }
    }

}

class LockFreeSkipListRecord<T> {
    T v;
    int op;
    boolean r;
    long id, ts;

    static final String[] OPERATION = {
            "CONTAIN",
            "ADD",
            "REMOVE",
    };

    public LockFreeSkipListRecord(int op, T v, boolean r) {
        this.op = op;
        this.v = v;
        this.r = r;
        ts = System.nanoTime();
        id = Thread.currentThread().getId();
    }

    public String operationName() {
        return LockFreeSkipListRecord.OPERATION[op];
    }
}

class LockFreeSkipListRecordBook<T> {
    ArrayList<LockFreeSkipListRecord<T>> records = new ArrayList<LockFreeSkipListRecord<T>>();

    public void record(int op, T v, boolean r) {
        records.add(new LockFreeSkipListRecord<T>(op, v, r));
    }

    public void finished() {
        records.sort((a, b) -> a.ts < b.ts ? -1 : 1);
    }

    public void print(T filter) {
        System.out.println();
        int count = 0;

        for (LockFreeSkipListRecord<T> r : records) {
            count += 1;
            if (filter != null && r.v != filter) continue; 
            System.out.printf("%6d (%12d): %2d - %7s %7d %5b\n", count - 1, r.ts, r.id, r.operationName(), r.v, r.r);
        }

        System.out.println();
        System.out.println("Total " + records.size() + " operations.");
    }

}

class LockFreeSkipListValidator {

    static final String[] OPERATION = {
            "CONTAIN",
            "ADD",
            "REMOVE",
    };

    public static boolean isSeqCst(LockFreeSkipListRecordBook<Integer> book, int min, int max) {
        assert max >= min;
        int length = max - min;
        int count = 0;
        int[] latestOf = new int[length];
        int[] latestSeenAt = new int[length];

        for (LockFreeSkipListRecord<Integer> r : book.records) {
            int index = r.v - min;
            int latest = latestOf[index];
            int latestSeenIndex = latestSeenAt[index];
            count++;

            if (r.op == 0) { // CONTAIN
                if (latest == 0 && !r.r)
                    continue;
                else if (latest == 1 && r.r)
                    continue;
                else if (latest == 2 && !r.r)
                    continue;
            } else if (r.op == 1) { // ADD
                if (latest == 0 && r.r) {
                    latestOf[index] = r.op;
                    latestSeenAt[index] = count - 1;
                    continue;
                } else if (latest == 1 && !r.r)
                    continue;
                else if (latest == 2 && r.r) {
                    latestOf[index] = r.op;
                    latestSeenAt[index] = count - 1;
                    continue;
                }
            } else if (r.op == 2) { // REMOVE
                if (latest == 0 && !r.r)
                    continue;
                else if (latest == 1 && r.r) {
                    latestOf[index] = r.op;
                    latestSeenAt[index] = count - 1;
                    continue;
                } else if (latest == 2 && !r.r)
                    continue;
            } else {
                throw new Error("Unexpected operation " + r.op);
            }

            book.print(r.v);
            System.out.println();
            System.out.printf("Violate sequential consistency at %d %s where previous operation is %s at index %d\n",
                    count - 1,
                    OPERATION[r.op],
                    OPERATION[latest],
                    latestSeenIndex);
            System.out.println();

            return false;
        }

        return true;
    }

}
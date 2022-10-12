import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class LockFreeSkipListTest {

    static final int MIN = 0;
    static final int MAX = 100;
    static final int LENGTH = 100;

    static final double[] CUMULATIVE_PROB = { 0.0, 0.5, 1.0 };

    static class MPSCTask implements Runnable {
        volatile boolean finished = false; 
        LockFreeSkipListRecordBook<Integer> book; 

        public MPSCTask(LockFreeSkipListRecordBook<Integer> book) {
            this.book = book; 
            this.finished = false; 
        }

        public void run() {
            while (!this.finished) book.submit();
            while (book.submit()) {}
        }
    }

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
        // NOTE: We steal one thread from the total pool
        // for the logging work through mpsc. 
        boolean isUniform = args[0].equalsIgnoreCase("uniform");
        int nthreads = Integer.parseInt(args[1]) - 1;
        int nitems = Integer.parseInt(args[2]);
        int seed = Integer.parseInt(args[3]);
        int opsPerThread = nitems / nthreads;

        Random rng = new Random(seed);
        LockFreeSkipList<Integer> skiplist = new LockFreeSkipList<Integer>();
        LockFreeSkipListRecordBook<Integer> book = skiplist.book; 

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
        ArrayList<Task> tasks = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(nthreads + 1);

        for (int i = 0; i < nthreads; i += 1) {
            tasks.add(new Task(i, skiplist, opsPerThread, rng, population));
        }

        long start = System.nanoTime();

        try {
            MPSCTask task = new MPSCTask(book); 
            Future<?> special = pool.submit(task);
            List<Future<Boolean>> futures = pool.invokeAll(tasks);

            // NOTE: Waiting for all the threads to finished. 
            for (Future<Boolean> f : futures)
                f.get();

            // NOTE: Signal the MPSC thread that the operation 
            // has finished and is currently waiting for you. 
            task.finished = true;
            special.get(); 
        } catch (Exception e) {
        }

        System.out.println();
        System.out.println("Time elapsed: " + (System.nanoTime() - start) / 1000000 + " ms");
        System.out.println("Total ops: " + (skiplist.book.records.size()));

        if (LockFreeSkipListValidator.isLinearizable(skiplist.book, MIN, MAX)) {
            System.out.println("The history is sequentially consistent");
        } else {
            System.out.println("The history is NOT sequentially consistent");
        }

        pool.shutdownNow();
    }

}

class LockFreeSkipListRecord<T> {
    T v;
    int op, seq;
    boolean r;
    long id, ts, start;
    String note;

    static final String[] OPERATION = {
            "CONTAIN",
            "ADD",
            "REMOVE",
    };

    public LockFreeSkipListRecord(int seq, int op, T v, boolean r) {
        this.seq = seq;
        this.op = op;
        this.v = v;
        this.r = r;
        ts = System.nanoTime();
        id = Thread.currentThread().getId();
    }

    public LockFreeSkipListRecord(int seq, int op, T v, boolean r, long start, String note) {
        this.seq = seq;
        this.op = op;
        this.v = v;
        this.r = r;
        this.note = note;
        this.start = start;
        ts = System.nanoTime();
        id = Thread.currentThread().getId();
    }

    public String operationName() {
        return LockFreeSkipListRecord.OPERATION[op];
    }
}

class LockFreeSkipListRecordBook<T> {
    private volatile int seq = 0; 
    private MPSC<LockFreeSkipListRecord<T>> mpsc = new MPSC<>(1000);
    ArrayList<LockFreeSkipListRecord<T>> records = new ArrayList<LockFreeSkipListRecord<T>>();

    public void record(int op, T v, boolean r) {
        LockFreeSkipListRecord<T> rc = new LockFreeSkipListRecord<T>(0, op, v, r);
        rc.start = rc.ts;
        mpsc.enq(rc);
    }

    public void record(int op, T v, boolean r, long start, String note) {
        LockFreeSkipListRecord<T> rc = new LockFreeSkipListRecord<T>(0, op, v, r, start, note);
        mpsc.enq(rc);
    }

    public boolean submit() {
        LockFreeSkipListRecord<T> item = mpsc.deq(); 

        if (item != null) {
            System.err.println("");
            records.add(item); 
            item.seq = seq++; 
            return true; 
        }  
        
        return false; 
    }

    public void print(T filter) {
        System.out.println();
        long b = records.get(0).start;

        for (LockFreeSkipListRecord<T> r : records) {
            if (filter != null && r.v != filter)
                continue;
            System.out.printf("%6d (%9d - %9d): %2d - %7s %7d %5b - ", r.seq, r.start - b, r.ts - b, r.id,
                    r.operationName(),
                    r.v, r.r);
            if (r.note != null)
                System.out.println(r.note);
            else
                System.out.println();
        }

        System.out.println();
        System.out.println("Total " + records.size() + " operations.");
    }

    public void print(int from, int to) {
        System.out.println();
        long b = records.get(0).start;

        for (LockFreeSkipListRecord<T> r : records) {
            if (r.seq < from || r.seq >= to)
                continue;
            System.out.printf("%6d (%9d - %9d): %2d - %7s %7d %5b - ", r.seq, r.start - b, r.ts - b, r.id,
                    r.operationName(),
                    r.v, r.r);
            if (r.note != null)
                System.out.println(r.note);
            else
                System.out.println();
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

    public static boolean isLinearizable(LockFreeSkipListRecordBook<Integer> book, int min, int max) {
        return isLinearizable(book, min, max, true);
    }

    public static boolean isLinearizable(LockFreeSkipListRecordBook<Integer> book, int min, int max,
            boolean allowSpecialCase) {
        assert max >= min;

        int ghostChains = 0;
        int competingRemoves = 0;
        int violations = 0; 

        ArrayList<ArrayList<LockFreeSkipListRecord<Integer>>> histories = new ArrayList<>();

        for (int i = min; i <= max; i += 1) {
            ArrayList<LockFreeSkipListRecord<Integer>> initial = new ArrayList<>();
            initial.add(new LockFreeSkipListRecord<Integer>(0, 0, i, false));
            histories.add(initial);
        }

        for (LockFreeSkipListRecord<Integer> r : book.records) {
            ArrayList<LockFreeSkipListRecord<Integer>> history = histories.get(r.v - min);
            LockFreeSkipListRecord<Integer> latest = history.get(history.size() - 1);

            if (r.op == 0) { // CONTAIN
                if (latest.op == 0 && !r.r)
                    continue;
                else if (latest.op == 1 && r.r)
                    continue;
                else if (latest.op == 2 && !r.r)
                    continue;
            } else if (r.op == 1) { // ADD
                if (latest.op == 0 && r.r) {
                    history.add(r);
                    continue;
                } else if (latest.op == 1 && !r.r)
                    continue;
                else if (latest.op == 2 && r.r) {
                    history.add(r);
                    continue;
                }
            } else if (r.op == 2) { // REMOVE
                if (latest.op == 0 && !r.r)
                    continue;
                else if (latest.op == 1 && r.r) {
                    history.add(r);
                    continue;
                } else if (latest.op == 2 && !r.r)
                    continue;
            } else {
                throw new Error("Unexpected operation " + r.op);
            }

            // In the special cases, try to reorder.
            // 1. REMOVE-ADD-REMOVE : Competing Remove
            // 2. REMOVE-ADD-CONTAINS : Ghost Chain

            if (allowSpecialCase && latest.op == 1 && (r.op != 1)) {
                if (r.start < latest.ts) {
                    if (r.op == 0) {
                        ghostChains++;
                    } else {
                        competingRemoves++;
                    }

                    continue;
                }
            }

            violations += 1; 
        }

        System.out.println();
        System.out.println("" + (violations) + " violates sequential spec.");
        System.out.println("" + (ghostChains + competingRemoves) + " linearization occurs in another thread:");
        System.out.println(" - " + (ghostChains) + " are ghost chains.");
        System.out.println(" - " + (competingRemoves) + " are competing call to remove.");
        System.out.println();

        return true;
    }

}
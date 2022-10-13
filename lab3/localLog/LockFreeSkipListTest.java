import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class LockFreeSkipListTest {

    static final int MIN = 0;
    static final int MAX = 1000;
    static final int LENGTH = 1000;

    static final double[] CUMULATIVE_PROB = { 0.8, 0.9, 1.0 };

    static class Task implements Callable<Boolean> {
        int id;
        Integer[] ops;
        Integer[] values;
        LockFreeSkipListRecordBook<Integer> book;

        // global variables, shared across thread
        LockFreeSkipList<Integer> skiplist;

        public Task(int id, LockFreeSkipList<Integer> skiplist, int nops, Random rng, Population population,
                LockFreeSkipListRecordBook<Integer> book) {
            ops = new Integer[nops];
            values = new Integer[nops];
            int[] stats = new int[3];
            this.skiplist = skiplist;
            this.id = id;
            this.book = book;

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
                    skiplist.contains(values[i], book);
                else if (ops[i] == 1)
                    skiplist.add(values[i], book);
                else if (ops[i] == 2)
                    skiplist.remove(values[i], book);
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

        Population prefill = isUniform
                ? new UniformPopulation(seed * 2, MIN, MAX)
                : new NormalPopulation(seed * 2, MIN, MAX, 0f, 1f);
        Population population = isUniform
                ? new UniformPopulation(seed * 3, MIN, MAX)
                : new NormalPopulation(seed * 3, MIN, MAX, 0f, 1f);

        LockFreeSkipListRecordBook<Integer> dbook = new LockFreeSkipListRecordBook<>();

        int success = 0;
        for (int i = 0; i < LENGTH; i += 1) {
            if (skiplist.add(prefill.getSample(), dbook))
                success++;
        }

        System.out.printf("-1: %7d items\n", success);

        List<Future<Boolean>> futures = null;
        ArrayList<Task> tasks = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(nthreads);
        ArrayList<LockFreeSkipListRecordBook<Integer>> books = new ArrayList<>();

        for (int i = 0; i < nthreads; i += 1) {
            LockFreeSkipListRecordBook<Integer> nextBook = new LockFreeSkipListRecordBook<Integer>(); 
            Task nextTask = new Task(i, skiplist, opsPerThread, rng, population, nextBook); 
            books.add(nextBook);
            tasks.add(nextTask);
            
        }

        long start = System.nanoTime();

        try {
            futures = pool.invokeAll(tasks);
            for (Future<Boolean> f : futures)
                f.get();
        } catch (Exception e) {
        }

        System.out.println();
        System.out.println("Time elapsed: " + (System.nanoTime() - start) / 1000000 + " ms");

        for (int i = 0; i < nthreads; i += 1) {
            dbook.records.addAll(books.get(i).records);
        }

        dbook.finished();
        System.out.println("Total ops: " + (dbook.records.size()));

        if (LockFreeSkipListValidator.isLinearizable(dbook, MIN, MAX)) {
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
    volatile int seq = 0;
    ArrayList<LockFreeSkipListRecord<T>> records = new ArrayList<LockFreeSkipListRecord<T>>();

    public LockFreeSkipListRecord<T> record(int op, T v, boolean r) {
        LockFreeSkipListRecord<T> rc = new LockFreeSkipListRecord<T>(seq++, op, v, r);
        rc.start = rc.ts;
        records.add(rc);
        return rc;
    }

    public LockFreeSkipListRecord<T> record(int op, T v, boolean r, long start, String note) {
        LockFreeSkipListRecord<T> rc = new LockFreeSkipListRecord<T>(seq++, op, v, r, start, note);
        records.add(rc);
        return rc;
    }

    public void finished() {
        records.sort((a, b) -> a.ts < b.ts ? -1 : 1);
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
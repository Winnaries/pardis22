package mpsc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import common.Config;
import sync.MPSC;

public class LockFreeSkipListTest {

    static class MPSCTask implements Callable<ArrayList<LockFreeSkipListRecord<Integer>>> {
        volatile boolean finished = false;
        volatile LockFreeSkipListRecordBook<Integer> book;

        public MPSCTask(LockFreeSkipListRecordBook<Integer> book) {
            this.book = book;
            this.finished = false;
        }

        public ArrayList<LockFreeSkipListRecord<Integer>> call() {
            ArrayList<LockFreeSkipListRecord<Integer>> records = new ArrayList<>(); 
            
            while (!this.finished) {
                LockFreeSkipListRecord<Integer> temp = book.mpsc.deq(); 
                while (temp != null) {
                    System.err.println(""); 
                    records.add(temp);
                    temp = book.mpsc.deq(); 
                }
            }

            return records; 
        }
    }

    static class Task implements Callable<Boolean> {
        int id;
        Integer[] ops;
        Integer[] values;

        // global variables, shared across thread
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
        LockFreeSkipListRecordBook<Integer> book = skiplist.book;
        MPSCTask task = new MPSCTask(book);
        ArrayList<Task> tasks = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(config.nthreads + 1);
        Future<ArrayList<LockFreeSkipListRecord<Integer>>> special = pool.submit(task);

        int success = 0;
        for (int i = 0; i < config.nitems; i += 1) {
            if (skiplist.add(config.prepDist.getSample()))
                success++;
        }

        System.out.printf("-1: %7d items\n", success);


        for (int i = 0; i < config.nthreads; i += 1) {
            tasks.add(new Task(i, skiplist, config));
        }

        long start = System.nanoTime();

        try {
            List<Future<Boolean>> futures = pool.invokeAll(tasks);
            for (Future<Boolean> f : futures)
                f.get();
            System.out.println();
            System.out.println("Time elapsed: " + (System.nanoTime() - start) / 1000000 + " ms");
            task.finished = true;
            book.records.addAll(special.get());
        } catch (Exception e) {
        }

        book.finished();
        System.out.println("Total ops: " + (skiplist.book.records.size()));

        if (LockFreeSkipListValidator.isLinearizable(skiplist.book, config.min, config.max)) {
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
    public MPSC<LockFreeSkipListRecord<T>> mpsc = new MPSC<>(10000);
    public volatile ArrayList<LockFreeSkipListRecord<T>> records = new ArrayList<LockFreeSkipListRecord<T>>();

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
        if (item == null) return false; 
        records.add(item);
        return true;
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
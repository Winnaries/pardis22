package common;

import java.util.Random;

public class Config {

    public final int seed = 123; 
    public boolean isUniform; 
    public int nthreads, nitems, nops; 
    public int min = 0, max; 
    public int opsPerThread;
    public double[] probs = new double[3]; 

    public Random rng; 
    public Population prepDist; 
    public Population testDist;

    public Config(String[] args) {
        try {
            isUniform = args[0].equalsIgnoreCase("uniform");
            probs[0] = Double.parseDouble(args[1]);
            probs[1] = Double.parseDouble(args[2]) + probs[0];
            probs[2] = Double.parseDouble(args[3]) + probs[1];
            nthreads = Integer.parseInt(args[4]);
            max = Integer.parseInt(args[5]);
            nitems = Integer.parseInt(args[6]);
            nops = Integer.parseInt(args[7]);
            opsPerThread = nitems / nthreads;
            assert probs[2] == 1.0; 

            rng = new Random(seed); 

            prepDist = isUniform
                ? new UniformPopulation(seed * 2, min, max)
                : new NormalPopulation(seed * 2, min, max, 0f, 1f);
                
            testDist = isUniform
                ? new UniformPopulation(seed * 3, min, max)
                : new NormalPopulation(seed * 3, min, max, 0f, 1f);

        } catch (Exception e) {
            System.out.println(e);
            System.err.println("Error parsing arguments...");
            System.err.println();
            System.err.println("\tjava {impl}.LockFreeSkipListTest {population} {ops_ratio} {nthreads} {nops} {nitems}");
            System.err.println();
            System.err.println("where \t {impl}             is one of {original, mutex, local, mpsc}");
            System.err.println("      \t {population}       is one of {uniform, normal}");
            System.err.println("      \t {op_ratio}         is three space-separated double between [0,1] (must sum up to 1.0)");
            System.err.println("      \t {nthreads}         is natural number: 4, 16, 64, etc");
            System.err.println("      \t {max}              is the maximum possible value in the list (minimum is 0)");
            System.err.println("      \t {nitems}           is total amount of prepopulated item in the list");
            System.err.println("      \t {nops}             is total amount of operation, rounded to multiple of nthreads");
            System.exit(-1);
        }
    }

    public void print() {
        System.out.println("CONFIG"); 
        System.out.println(" - nthreads: " + nthreads); 
        System.out.println(" - seed: " + seed); 
        System.out.println(" - min: " + min); 
        System.out.println(" - max: " + max); 
        System.out.println(" - nitems: " + nitems); 
        System.out.println(" - nops: " + nops); 
        System.out.println(" - probs: " + probs[0] + " " + probs[1] + " " + probs[2]); 
        System.out.println(" - isUniform: " + isUniform); 
        System.out.println(" - opsPerThread: " + opsPerThread); 
        System.out.println(); 
    }

}

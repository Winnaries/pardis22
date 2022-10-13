package common; 

import java.util.Random;

public class UniformPopulation implements Population {
    Random rng; 
    int min, max; 
    int count = 0; 

    public UniformPopulation(int seed, int min, int max) {
        this.min = min; 
        this.max = max; 
        this.rng = new Random(seed);
    }

    public int getCount() {
        return count; 
    }

    public int getSample() {
        count += 1; 
        return (int)(rng.nextDouble() * (max - min) + min); 
    }
}

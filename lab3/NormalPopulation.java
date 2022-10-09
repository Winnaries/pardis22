import java.util.Random;

public class NormalPopulation implements Population {
    int min, max; 
    int count = 0; 
    double mean, var; 
    Random rng = new Random(); 

    public NormalPopulation(int seed, int min, int max, double mean, double var) {
        this.min = min;
        this.max = max;
        this.mean = mean; 
        this.var = var; 
        this.rng = new Random(); 
    }

    public int getCount() {
        return count; 
    }

    public int getSample() {
        count += 1; 
        double sample = mean + this.rng.nextGaussian() * Math.sqrt(var); 
        int temp = (int) ((max + min) / 2 + sample * (max - min) / 2 / 3);
        return temp >= max || temp <= min ? this.getSample() : temp; 
    }
}

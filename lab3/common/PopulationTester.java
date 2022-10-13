package common;

public class PopulationTester {

    // Dummy. 
    private PopulationTester() {
    }

    // Compute mean and variance with given sample size
    public static Stats computeStats(Population population, int sampleSize) {
        // Sample 'sampleSize' values from 'population', 
        // calculate mean and variance of the population.

        // Collect samples
        int samples[] = new int[sampleSize];
        for (int i = 0; i < sampleSize; i++)
            samples[i] = population.getSample();

        // Calculate the mean
        double mean = 0;
        for (int sample : samples)
            mean += sample;
        mean /= sampleSize;

        // Calculate the variance
        double variance = 0;
        for (int sample : samples)
            variance += (sample - mean) * (sample - mean);
        variance /= (sampleSize - 1);

        return new Stats(mean,variance);
    }

    // For returning statistics
    public static class Stats {
        public double mean, variance;
        public Stats(double mean, double variance) {
            this.mean = mean;
            this.variance = variance;
        }
    }
}

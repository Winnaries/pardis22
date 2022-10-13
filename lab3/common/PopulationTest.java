package common;

public class PopulationTest {
    public static void main(String [] args) {
        Population uniPop = new UniformPopulation(123, 0, 100);
        Population normPop = new NormalPopulation(123, 0, 100, 0, 1);

        PopulationTester.Stats uniStats = PopulationTester.computeStats(uniPop, 100);
        PopulationTester.Stats normStats = PopulationTester.computeStats(normPop, 100);

        System.out.printf("Uniform Population: mean=%.3f, variance=%.3f\n", uniStats.mean, uniStats.variance);
        System.out.printf("Normal Population:  mean=%.3f, variance=%.3f\n", normStats.mean, normStats.variance);
    }
}

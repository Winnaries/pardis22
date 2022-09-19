package lab2;

import java.util.Collections;
import java.util.Arrays; 
import java.util.List; 

public class MergeSortUtils {

    public static Integer[] generate(int n) {
        Integer[] array = new Integer[n];
        for (int i = 0; i < n; i += 1) {
            array[i] = i;
        }

        List<Integer> list = Arrays.asList(array);
        Collections.shuffle(list);

        list.toArray(array);
        return array; 
    }

    public static boolean test(Integer[] array) {
        int n = array.length; 
        for (int i = 0; i < n; i += 1) {
            if (array[i] != i) {
                return false; 
            }
        }

        return true; 
    }

    public static int nextPowerOfTwo(int x) {
        x = x - 1; 

        while ((x & x - 1) != 0) {
            x = x & x - 1; 
        }
        
        return x << 1; 
    }

}

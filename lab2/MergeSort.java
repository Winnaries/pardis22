package lab2;

class MergeSort {

    public static void sort(Integer[] array, int l, int r) {
        if (l < r) {
            int m = (l + r) / 2;
            MergeSort.sort(array, l, m);
            MergeSort.sort(array, m + 1, r);
            MergeSort.merge(array, l, m, r);
        }
    }

    public static void merge(Integer[] array, int l, int m, int r) {
        int a = m - l + 1, b = r - m; 

        Integer[] left = new Integer[a]; 
        Integer[] right = new Integer[b]; 
    
        for (int i = 0; i < a; i += 1)
            left[i] = array[l + i]; 
        for (int j = 0; j < b; j += 1) 
            right[j] = array[m + 1 + j];

        int x = 0, y = 0, k = l; 

        while (x < a && y < b) {
            if (left[x] < right[y]) {
                array[k] = left[x]; 
                x += 1;  
            } else {
                array[k] = right[y]; 
                y += 1; 
            }

            k += 1;
        }

        while (x < a) {
            array[k] = left[x]; 
            x += 1; 
            k += 1; 
        }

        while (y < b) {
            array[k] = right[y]; 
            y += 1; 
            k += 1; 
        }
    }
    
    public static void main(String[] args) {
        Integer n = Integer.parseInt(args[0]);
        Integer[] array = MergeSortUtils.generate(n);

        long start = System.nanoTime();
        MergeSort.sort(array, 0, n - 1); 
        System.out.printf("Total %d ms elapsed\n", (System.nanoTime() - start) / 1000000);

        if (!MergeSortUtils.test(array)) {
            throw new Error("Array is in an incorrect order.");
        }
    }

}
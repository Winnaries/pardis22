### How to run? 

```
CMD: java {impl}.LockFreeSkipListTest {population} {ops_ratio} {nthreads} {max} {nops}

WHERE:	 {impl}             is one of {original, mutex, local, mpsc}
      	 {population}       is one of {uniform, normal}
      	 {op_ratio}         is three space-separated double between [0,1] (must sum up to 1.0)
      	 {nthreads}         is natural number: 4, 16, 64, etc
      	 {max}              is the maximum possible value in the list (minimum is 0)
      	 {nops}             is total amount of operation, rounded to multiple of nthreads
```

### Contributors

- Nattawat Pornthisan
- Ng Shuen Jin
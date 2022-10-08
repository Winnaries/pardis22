import java.util.concurrent.atomic.AtomicIntegerArray;

class Dekker {
  public Dekker() {
    flag.set(0, 0);
    flag.set(1, 0);
    turn = 0;
  }

  public void Pmutex(int t) {
    int other;

    other = 1 - t;
    flag.set(t, 1);
    while (flag.get(other) == 1) {
      if (turn == other) {
        flag.set(t, 0);
        while (turn == other)
          ;
        flag.set(t, 1);
      }
    }
  }

  public void Vmutex(int t) {
    turn = 1 - t;
    flag.set(t, 0);
  }

  private volatile int turn;
  private AtomicIntegerArray flag = new AtomicIntegerArray(2);
}
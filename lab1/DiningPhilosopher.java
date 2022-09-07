public class DiningPhilosopher {
    
    public static void main(String[] args) {
        int n = Integer.parseInt(args[0]); 
        Thread[] philosophers = new Thread[n];
        Chopstick[] chopsticks = new Chopstick[n];
        
        for (int i = 0; i < n; i += 1) {
            chopsticks[i] = new Chopstick(i);
        }

        for (int j = 0; j < n; j += 1) {
            philosophers[j] = new Thread(new Philosopher(n, j, chopsticks));
            philosophers[j].start(); 
        }

        for (int k = 0; k < n; k += 1) {
            try {
                philosophers[k].join(); 
            } catch (InterruptedException e) {}
        }
    }

}

class Philosopher implements Runnable {

    int n; 
    int id;
    Chopstick[] chopsticks; 

    public Philosopher(int n, int id, Chopstick[] chopsticks) {
        this.n = n; 
        this.id = id; 
        this.chopsticks = chopsticks; 
    }

    public int[] getChopsticks() {
        int[] result = new int[2]; 
        result[0] = this.id; 
        result[1] = (this.id + 1) % this.n; 
        return result; 
    }

    public void say(String message) {
        System.out.printf("[#%d] %s\n", this.id, message);
    }

    public void eat() {
        try {
            Thread.sleep(((int) (Math.random() * 2000)));
            this.say("Finish eating");
        } catch (InterruptedException e) {
        }
    }

    public void think() {
        try {
            Thread.sleep(((int) (Math.random() * 2000)));
            this.say("Gets hungry");
        } catch (InterruptedException e) {
        }
    }
    
    public void run() {
        while (true) {
            this.think(); 

            // philosopher gets hungry...
            this.say("Gets hungry");
            int[] chopstickIndices = this.getChopsticks(); 
            Chopstick leftStick = this.chopsticks[chopstickIndices[0]];
            Chopstick rightStick = this.chopsticks[chopstickIndices[1]]; 
            
            // but only eat if left > right
            if (leftStick.id < rightStick.id) {
                continue; 
            }

            // philosopher takes...
            leftStick.take(); 
            this.say("Took left");
            rightStick.take(); 
            this.say("Took right");

            this.eat(); 

            // philosopher swap...
            int tempId = leftStick.id; 
            leftStick.id = rightStick.id; 
            rightStick.id = tempId; 
            this.say("Swap chopsticks");

            // philosopher puts down...
            leftStick.give(); 
            this.say("Give left");
            rightStick.give(); 
            this.say("Give right");
        }
    }

}

class Chopstick {
    
    int id; 
    int status = 1; 

    public Chopstick(int id) {
        this.id = id; 
    }

    public synchronized void take() {
        status -= 1; 

        if (status < 0) {
            try {
                wait();
            } catch (InterruptedException e) {}
        }
    }

    public synchronized void give() {
        status += 1; 
        notify();
    }

}
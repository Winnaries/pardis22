public class Synch extends Thread {
  int val;
  volatile boolean condition = true;
 
   public void run() {
     if (condition)
       while (condition) {
         val=val+1;
       }
   }
 
   public static void main(String[] args) throws Exception {
     Synch s=new Synch();
     s.start();
     Thread.sleep(1000);
     s.condition=false;
     System.out.println(s.val);
     System.out.println(s.val);
     s.join();
   }
}

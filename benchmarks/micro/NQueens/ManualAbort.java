import java.util.concurrent.atomic.AtomicInteger;

public class ManualAbort implements Config.Benchmark {
  private  AtomicInteger nSolutions;
  private int CUTOFF;
  private int[] A;
  private int size;

  public static void main(String[] args)  {
    int size = 14;
    if (args.length > 0)
      size = Integer.parseInt(args[0]);
    ManualAbort nq = new ManualAbort(size);
    Config.launch(nq);
  }

  private boolean verify() {
    return nSolutions.get() >= CUTOFF;
  }

  public ManualAbort(int size) {
    this.size = size;
    this.CUTOFF = (int) (0.02 * Config.solutions[size - 1]);
    System.out.println("Size = "+size+" Cutoff = "+this.CUTOFF);
  }

  public void speculativeSearch() {
    this.nSolutions = new AtomicInteger(0);
    this.A = new int[0];
    finish { 
      speculativeSearch(A, 0);
    } 
    boolean pass = verify();
    System.out.println("Success = "+pass);
  }

  private void speculativeSearch(int[] A, int depth) {
    // termination check on entry
    if(nSolutions.get() >= CUTOFF) {
      return;
    }
    if (size == depth) {
      if(nSolutions.incrementAndGet() >= CUTOFF) {
        //return;
      }
      return;
    }

    /* try each possible position for queen <depth> */
    for (int i=0; i < size; i++) {
      // cooperative termination check
      if(nSolutions.get() >= CUTOFF) {
        return;
      }
      /* allocate a temporary array and copy <a> into it */
      int[] B = new int[depth+1];
      System.arraycopy(A, 0, B, 0, depth);
      B[depth] = i;
      boolean status = Config.ok((depth +  1), B); 
      if (status) {
        async { speculativeSearch(B, depth+1); }
      }
    }
  }
}

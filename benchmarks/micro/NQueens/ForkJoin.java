import jsr166y.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ForkJoin implements Config.Benchmark {
  private  AtomicInteger nSolutions;
  private int CUTOFF;
  private int[] A;
  private int size;
  private int procs;
  private ForkJoinPool pool;

  public static void main(String[] args)  {
    int size = 14;
    final int procs = args.length>0? Integer.parseInt(args[0]) : 1;
    if (args.length > 1)
      size = Integer.parseInt(args[1]);
    ForkJoin nq = new ForkJoin(size, procs);
    Config.launch(nq);
  }

  private boolean verify() {
    return nSolutions.get() >= CUTOFF;
  }

  public ForkJoin(int size, int procs) {
    this.size = size;
    this.procs=procs;
    this.CUTOFF = (int) (0.02 * Config.solutions[size - 1]);
    System.out.println("Size = "+size+" Cutoff = "+this.CUTOFF);
  }

  public void speculativeSearch() {
    this.pool = new ForkJoinPool(procs);
    this.nSolutions = new AtomicInteger(0);
    this.A = new int[0];
    try {
      pool.invoke(new RecursiveAction() {
        @Override
        public void compute() {
          new Task(A, 0).fork();
          helpQuiesce();
        }
      });
    } catch(java.util.concurrent.CancellationException e) {
      System.out.println("Aborted");
    }
    boolean pass = verify();
    System.out.println("Success = "+pass);
  }

  private class Task extends RecursiveAction {
    private int[] A;
    private int depth;

    public Task(int[] a, int d) { A=a; depth=d; }
    @Override
    public void compute() {
      if (size == depth) {
        if(nSolutions.incrementAndGet() >= CUTOFF) {
          pool.shutdownNow();
        }
        return;
      }

      /* try each possible position for queen <depth> */
      for (int i=0; i < size; i++) {
        /* allocate a temporary array and copy <a> into it */
        int[] B = new int[depth+1];
        System.arraycopy(A, 0, B, 0, depth);
        B[depth] = i;
        boolean status = Config.ok((depth +  1), B);
        if (status) {
          new Task(B, depth+1).fork();
        }
      }
    }
  }
}

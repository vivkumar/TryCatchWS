import java.util.concurrent.atomic.AtomicInteger;

public class Sequential implements Config.Benchmark {
  private  AtomicInteger nSolutions;
  private int CUTOFF;
  private int[] A;
  private int size;

  public static void main(String[] args)  {
    int size = 14;
    if (args.length > 0)
      size = Integer.parseInt(args[0]);
    Sequential nq = new Sequential(size);
    Config.launch(nq);
  }

  private boolean verify() {
    return nSolutions.get() >= CUTOFF;
  }

  public Sequential(int size) {
    this.size = size;
    this.CUTOFF = (int) (0.02 * Config.solutions[size - 1]);
    System.out.println("Size = "+size+" Cutoff = "+this.CUTOFF);
  }

  public void speculativeSearch() {
    this.nSolutions = new AtomicInteger(0);
    this.A = new int[0];
    try { // remove in parallel implementation
      speculativeSearch(A, 0);
    } catch(java.util.concurrent.CancellationException e){} // remove in parallel implementation
    boolean pass = verify();
    System.out.println("Success = "+pass);
  }

  private void speculativeSearch(int[] A, int depth) {
    if (size == depth) {
      if(nSolutions.incrementAndGet() >= CUTOFF) {
        throw new java.util.concurrent.CancellationException("Found"); // remove in parallel implementation;
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
        speculativeSearch(B, depth+1);
      }
    }
  }
}

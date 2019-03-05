import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: Vivek Kumar
 */
public class Config {
  interface Benchmark {
    public void speculativeSearch();
    public boolean verify();
  }

  public static Complex[][] setup(int rows, int cols, int goalCount, final List<Complex> itemsToSearch) {
    System.out.println("Initializing..");
    Complex[][] array = new Complex[rows][cols];
    final MyRandom random = new MyRandom(1);
    // initialize array
    for(int i=0; i<rows; i++) { 
      array[i] = new Complex[cols];
      for(int j=0; j<cols; j++) {
        final double real = 1 + Math.abs(random.nextDouble());
        final double imag = 1 + Math.abs(random.nextDouble());
        array[i][j] = new Complex(real, imag);
      }
    }
    for(int i=0; i<goalCount; i++) {
      //find random row
      int r = random.nextInt() % rows;
      //find random col
      int c = random.nextInt() % cols;
      itemsToSearch.add(array[r][c]);
    }
    System.out.println("Total goal nodes = "+itemsToSearch.size());
    return array;
  }

  public static void launch(Benchmark benchmark) {
    int inner = 5;
    int outter = 3;

    for(int i=0;i <outter; i++) {
      if(i+1 == outter) {
        org.mmtk.plan.Plan.harnessBegin();
        org.jikesrvm.scheduler.RVMThread.perfEventStart();
      }
      for(int j=0; j<inner; j++) {
        System.out.println("========================== ITERATION ("+i+"."+j+") ==================================");
        final long startTime = System.currentTimeMillis();
        benchmark.speculativeSearch();
        final long time = System.currentTimeMillis() - startTime;
        final double secs = ((double)time) / 1000.0;
        System.out.println("PASS = "+benchmark.verify() + " Time: " + secs);
      }
    }
    org.jikesrvm.scheduler.RVMThread.perfEventStop();
    org.mmtk.plan.Plan.harnessEnd();
  }

  public static class Complex {
    private final double re;   // the real part
    private final double im;   // the imaginary part

    // create a new object with the given real and imaginary parts
    private Complex(final double real, final double imag) {
      re = real;
      im = imag;
    }

    @Override
    public boolean equals(final Object other) {
      if (other == null || getClass() != other.getClass()) {
        return false;
      }
      final Complex complex = (Complex) other;
      final int imDiff = Double.compare(complex.im, im);
      final int reDiff = Double.compare(complex.re, re);
      final int hashDiff = hashCode() - complex.hashCode();
      return reDiff == 0 && imDiff == 0 && hashDiff == 0;
    }
  }
}
/**
 * Author: Vivek Kumar
 *
 * Platform and Language independent (tested for C/C++ and Java)
 * fixed sequence random number generator.
 *
 * Code borrowed from Wikipedia for Linear congruential generator
 * https://en.wikipedia.org/wiki/Linear_congruential_generator
 *
 * Values for constants a, c, and m taken from there 
 * from the table as in C++11's minstd_rand.
 *
 * Changes to the original code:
 * 1) returning absolute value from rand. unsigned int
 *    not used as it isn't supported in Java and we want 
 *    to have portability across both C/C++ and Java.
 * 2) seed initialized to 1 if user passed 0
 * 
 * NOTE: This code is not thread-safe
 *
 **/

class MyRandom {
  private int seed;
  private static final int a = 16807;
  private static final int c = 0;
  private static final int m = 2147483647;
  public MyRandom(int s) {
    if(s==0) s=1;
    this.seed = s;
  }
  public MyRandom() {
    this.seed = 123456789;
  }
  public int nextInt() {
    seed = (a * seed + c) % m;
    return Math.abs(seed);
  }
  public double nextDouble() {
    return (nextInt() / Integer.MAX_VALUE); 
  }
}

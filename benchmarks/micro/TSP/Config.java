import java.util.concurrent.atomic.AtomicInteger;

/**
 * The code has been inspired from: "Branch and Bound Implementations for the Traveling Salesperson Problem, Part 2:
 * Single threaded solution with many inexpensive nodes". Richard Wiener. In Journal of Object Technology, Vol. 2, No.
 * 3, May-June 2003.
 *
 * @author Richard Wiener [Original Java version]
 * @author <a href="http://shams.web.rice.edu/">Shams Imam</a> (shams@rice.edu) [HJlib version]
 */
public class Config {

  interface Benchmark {
    public void speculativeSearch();
  }

  public static int SHORTEST_PATH_GOAL;
  private static final boolean verification = false; //use in sequential case only

  public static void launch(final Benchmark benchmark) {
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
        System.out.println("Time: " + secs);
      }
    }
    org.jikesrvm.scheduler.RVMThread.perfEventStop();
    org.mmtk.plan.Plan.harnessEnd();
  }

  // Commands
  public static int computeLowerBound(final int size, final byte[] cities, final boolean[] blocked, final int level, int numCities, int[] costMatrix) {
    int lowerBound = 0;
    if (size == 1) {
      for (int i = 1; i <= numCities; i++) {
        lowerBound += Config.minimum(i, blocked, numCities, costMatrix);
      }
    } else {
      // Obtain fixed portion of bound
      for (int i = 2; i <= size; i++) {
        blocked[cities[i]] = true;
        lowerBound += costMatrix[(cities[i - 1]) * (numCities + 1) + cities[i]];
      }
      blocked[1] = true;
      lowerBound += Config.minimum(cities[size], blocked, numCities, costMatrix);
      blocked[1] = false;
      blocked[cities[size]] = true;
      for (int i = 2; i <= numCities; i++) {
        if (!blocked[i]) {
          lowerBound += Config.minimum(i, blocked, numCities, costMatrix);
        }
      }
    }
    return lowerBound;
  }

  public static int minimum(final int index, final boolean[] blocked, final int numCities, int[] costMatrix) {
    int smallest = Integer.MAX_VALUE;
    for (int col = 1; col <= numCities; col++) {
      if (!blocked[col] && col != index && costMatrix[index*(numCities + 1) + col] < smallest) {
        smallest = costMatrix[index*(numCities + 1) + col];
      }
    }
    return smallest;
  }

  public static int[] initializeCostMatrix(int numCities) {
    int[] costMatrix = new int[(numCities + 1)*(numCities + 1)];

    final MyRandom random = new MyRandom(numCities * numCities);
    for (int row = 1; row <= numCities; row++) {
      for (int col = row + 1; col <= numCities; col++) {
        final int cost = random.nextInt()%100;
        //final int cost = (int) (Math.abs(random.nextDouble() * 100) + 1);
        costMatrix[row*(numCities + 1) + col] = cost;
        costMatrix[col*(numCities + 1) + row] = cost;
      }
    }
    return costMatrix;
  }

  private static byte[] citiesVisited;

  private static synchronized void lockedUpdateSolution(final int cost, final byte[] cities, AtomicInteger bestSolution) {
    if (cost < bestSolution.get()) {
      bestSolution.set(cost);
      citiesVisited = cities;
    }
  }

  public static void updateSolution(final int cost, final byte[] cities, AtomicInteger bestSolution) {
    if (cost < bestSolution.get()) {
      lockedUpdateSolution(cost, cities, bestSolution);
    }
  }

  public static boolean present(final byte city, final byte[] cities) {
    for (int i = 1; i <= cities.length - 1; i++) {
      if (cities[i] == city) {
        return true;
      }
    }
    return false;
  }

  public static String bestPath(int numCities) {
    String result = "";
    for (int i = 1; i < citiesVisited.length; i++) {
      result += citiesVisited[i] + " ";
    }
    if (citiesVisited.length == numCities) {
      for (byte i = 2; i <= numCities; i++) {
        if (!present(i, citiesVisited)) {
          result += i + " ";
          break;
        }
      }
      result += "1";
    } else {
      result += "";
    }
    return result;
  }

  public static void validate(AtomicInteger bestSolution, int numCities, int[] costMatrix) {
    System.out.println("Computed best tour cost = "+ bestSolution.get());
    final String solutionPath = bestPath(numCities);
    System.out.println("Computed path = "+ solutionPath);

    final String[] citiesVisitedStr = solutionPath.split("\\s+");
    final byte[] citiesVisited = new byte[citiesVisitedStr.length];
    for (int i1 = 0; i1 < citiesVisited.length; i1++) {
      citiesVisited[i1] = Byte.parseByte(citiesVisitedStr[i1]);
    }

    final boolean[] citiesTracker = new boolean[numCities + 1];
    citiesTracker[0] = true;

    int resultCost = 0;
    for (int i = 1, j = citiesVisited[0]; i < citiesVisited.length; i++) {
      final byte newCity = citiesVisited[i];
      resultCost += costMatrix[newCity*(numCities + 1) + j];

      citiesTracker[newCity] = true;
      j = newCity;
    }

    System.out.println("Verified best cost = "+ resultCost);
    if (resultCost != bestSolution.get()) {
      System.out.println("ERROR: Solution cost does not match computed cost!");
    }
    for (int i = 0; i < citiesTracker.length; i++) {
      if (!citiesTracker[i]) {
        System.out.println("ERROR: City " + i + " has not been visited in path: " + solutionPath);
      }
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


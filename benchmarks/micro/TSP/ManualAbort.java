import java.util.concurrent.atomic.AtomicInteger;

public class ManualAbort implements Config.Benchmark {
  public static void main(final String[] args) {
    int numCities = args.length>0?Integer.parseInt(args[0]) : 20;
    int goal = args.length>1?Integer.parseInt(args[1]) : 156;
    ManualAbort benchmark = new ManualAbort(numCities, goal);
    Config.launch(benchmark);
  }

  public void speculativeSearch() {
    bestSolution.set(Integer.MAX_VALUE - 1);
    // Create root node
    final byte[] cities = new byte[2];
    cities[1] = 1;
    final boolean[] blocked = new boolean[numCities + 1];
    final int lowerBound = Config.computeLowerBound(1, cities, blocked, 1, numCities, costMatrix);
    System.out.println("Root Lower Bound = "+ lowerBound);

    finish { 
      speculativeSearch(1, cities, blocked, 1, lowerBound);
    } 

    Config.validate(bestSolution, numCities, costMatrix);
  }

  private int numCities;
  private int[] costMatrix;
  private byte[] citiesVisited;
  private AtomicInteger bestSolution = new AtomicInteger();

  public ManualAbort(int nc, int goal) {
    numCities = nc;
    Config.SHORTEST_PATH_GOAL = goal;
    if(costMatrix == null) costMatrix = Config.initializeCostMatrix(numCities);
  }

  private void speculativeSearch(final int size, byte[] nextCities, final boolean[] blocked, final int level, final int lowerBound) {
    if (size == numCities - 1 && lowerBound < bestSolution.get()) {
      // update best known solution
      Config.updateSolution(lowerBound, nextCities, bestSolution);
      if(lowerBound == Config.SHORTEST_PATH_GOAL) {
        System.out.println("Aborting");
        return;
      }
    } else if (lowerBound >= bestSolution.get()) {
      // prune search
      return;
    }
    // termination check on entry
    if(bestSolution.get() == Config.SHORTEST_PATH_GOAL) {
      return;
    }
    final int newLevel = level + 1;
    for (byte city = 2; city <= numCities; city++) {
      // cooperative termination check
      if(bestSolution.get() == Config.SHORTEST_PATH_GOAL) {
        return;
      }
      if (!Config.present(city, nextCities)) {
        final byte[] newTour = new byte[size + 2];
        for (int index = 1; index <= size; index++) {
          newTour[index] = nextCities[index];
        }
        newTour[size + 1] = city;
        final int newSize = size + 1;
        final boolean[] newBlocked = new boolean[numCities + 1];
        final int newLowerBound = Config.computeLowerBound(newSize, newTour, newBlocked, newLevel, numCities, costMatrix);
        async { speculativeSearch(newSize, newTour, newBlocked, newLevel, newLowerBound); }
      }
    }
  }
}

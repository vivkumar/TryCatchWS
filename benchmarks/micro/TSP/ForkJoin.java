import java.util.concurrent.atomic.AtomicInteger;
import jsr166y.*;

public class ForkJoin implements Config.Benchmark {
  public static void main(final String[] args) {
    int procs = args.length>0?Integer.parseInt(args[0]) : 1;
    int numCities = args.length>1?Integer.parseInt(args[1]) : 20;
    int goal = args.length>2?Integer.parseInt(args[2]) : 156;
    ForkJoin benchmark = new ForkJoin(numCities, goal, procs);
    Config.launch(benchmark);
  }

  public void speculativeSearch() {
    this.pool = new ForkJoinPool(procs);
    try {
      pool.invoke(new RecursiveAction() {
        @Override
        public void compute() {
          bestSolution.set(Integer.MAX_VALUE - 1);
          // Create root node 
          final byte[] cities = new byte[2];
          cities[1] = 1;
          final boolean[] blocked = new boolean[numCities + 1];
          final int lowerBound = Config.computeLowerBound(1, cities, blocked, 1, numCities, costMatrix);
          System.out.println("Root Lower Bound = "+ lowerBound);
          new Task(1, cities, blocked, 1, lowerBound).fork();
          helpQuiesce();
        }
      });
    } catch(java.util.concurrent.CancellationException e) {
      System.out.println("Aborted");
    }
    Config.validate(bestSolution, numCities, costMatrix);
  }

  private ForkJoinPool pool;
  private int procs;
  private int numCities;
  private int[] costMatrix;
  private byte[] citiesVisited;
  private AtomicInteger bestSolution = new AtomicInteger();

  public ForkJoin(int nc, int goal, int procs) {
    this.procs=procs;
    numCities = nc;
    Config.SHORTEST_PATH_GOAL = goal;
    if(costMatrix == null) costMatrix = Config.initializeCostMatrix(numCities);
  }

  private class Task extends RecursiveAction {
    private int size;
    private byte[] nextCities;
    private boolean[] blocked;
    private int level;
    private int lowerBound;
    public Task(int s, byte[] n, boolean[] b, int l, int lb) {
      size=s;
      nextCities = n;
      blocked = b;
      level = l;
      lowerBound = lb;
    }
    @Override
    public void compute() {
      if (size == numCities - 1 && lowerBound < bestSolution.get()) {
        // update best known solution
        Config.updateSolution(lowerBound, nextCities, bestSolution);
        if(lowerBound == Config.SHORTEST_PATH_GOAL) {
          System.out.println("Aborting");
          pool.shutdownNow();
        }
      } else if (lowerBound >= bestSolution.get()) {
        // prune search
        return;
      }
      final int newLevel = level + 1;
      for (byte city = 2; city <= numCities; city++) {
        if (!Config.present(city, nextCities)) {
          final byte[] newTour = new byte[size + 2];
          for (int index = 1; index <= size; index++) {
            newTour[index] = nextCities[index];
          }
          newTour[size + 1] = city;
          final int newSize = size + 1;
          final boolean[] newBlocked = new boolean[numCities + 1];
          final int newLowerBound = Config.computeLowerBound(newSize, newTour, newBlocked, newLevel, numCities, costMatrix);
          new Task(newSize, newTour, newBlocked, newLevel, newLowerBound).fork();
        }
      }
    }
  }
}

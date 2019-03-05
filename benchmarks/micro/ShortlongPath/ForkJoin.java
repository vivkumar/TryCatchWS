import jsr166y.*;

public class ForkJoin implements Config.Benchmark {

  public ForkJoin(int procs) {
    this.procs = procs;
  }

  public static void main(final String[] args) {
    int procs = args.length>0?Integer.parseInt(args[0]):1;
    String type = args.length>1?args[1]:"T3";
    int goalRate = args.length>2?Integer.parseInt(args[2]):25000;
    ForkJoin benchmark = new ForkJoin(procs);
    System.out.println("Initializing...");
    Config.setup(type, goalRate);
    System.out.println("Args: ");
    Config.printArgs();
    Config.launch(benchmark);
  }

  public void speculativeSearch() {
    pool = new ForkJoinPool(procs);
    final Config.SPNode rootSPNode = Config.rootSPNode;
    resultShortDistance = Long.MAX_VALUE;
    resultLongDistance = Long.MIN_VALUE;
    try {
      pool.invoke(new RecursiveAction() {
        @Override
        public void compute() {
          Config.SPNode rootSPNode = Config.rootSPNode;
          new Task(rootSPNode, 0).fork();
          helpQuiesce();
        }
      });
    } catch(java.util.concurrent.CancellationException e) {
      System.out.println("Aborted");
    }
    System.out.println("Shortest Distance to Goal Node = "+resultShortDistance);
    System.out.println("Longest Distance to Goal Node = "+resultLongDistance);
  }

  private Long resultShortDistance;
  private Long resultLongDistance;
  private final Object lockObject = new Object();
  private ForkJoinPool pool;
  private int procs;

  private class Task extends RecursiveAction {
    private Config.SPNode treeSPNode;
    private long distance;
    public Task(Config.SPNode t, long d) { treeSPNode=t; distance=d; }
    @Override
    public void compute() {
      final int childCount = treeSPNode.numChildren();
      for (int i = 0; i < childCount; i++) {
        final Config.SPNode childSPNode = treeSPNode.child(i);
        final long childDistance = distance + treeSPNode.distance(i);
        if (Config.goalSPNodes.contains(childSPNode)) {
          boolean success = false;
          synchronized(lockObject) {
            if(childDistance < resultShortDistance) {
              resultShortDistance = childDistance;
              success = true;
            }
            if(childDistance > resultLongDistance) {
              resultLongDistance = childDistance;
              success = true;
            }
          }
          if(success) {
            if(resultShortDistance<= Config.SHORTEST_PATH_GOAL && resultLongDistance >= Config.LONGEST_PATH_GOAL) {
              pool.shutdownNow();
            }
            System.out.println(childDistance);
            return;
          }
        }
        new Task(childSPNode, childDistance).fork();
      }
    }
  }
}

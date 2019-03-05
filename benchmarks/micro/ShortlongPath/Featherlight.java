public class Featherlight implements Config.Benchmark {
  public Featherlight() {
  }

  public static void main(final String[] args) {
    String type = args.length>0?args[0]:"T3";
    int goalRate = args.length>1?Integer.parseInt(args[1]):25000;
    Featherlight benchmark = new Featherlight();
    System.out.println("Initializing...");
    Config.setup(type, goalRate);
    System.out.println("Args: ");
    Config.printArgs();
    Config.launch(benchmark);
  }

  public void speculativeSearch() {
    final Config.SPNode rootSPNode = Config.rootSPNode;
    resultShortDistance = Long.MAX_VALUE;
    resultLongDistance = Long.MIN_VALUE;
    finish_abort { 
      speculativeSearch(rootSPNode, 0);
    } 
    System.out.println("Shortest Distance to Goal Node = "+resultShortDistance);
    System.out.println("Longest Distance to Goal Node = "+resultLongDistance);
  }

  private Long resultShortDistance;
  private Long resultLongDistance;
  private final Object lockObject = new Object();

  void speculativeSearch(Config.SPNode treeSPNode, final long distance) {
    for (int i = 0; i < treeSPNode.numChildren(); i++) {
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
            System.out.println("Aborting");
            abort;
          }
          return;
        }
      }
      async { speculativeSearch(childSPNode, childDistance); }
    }
  }
}

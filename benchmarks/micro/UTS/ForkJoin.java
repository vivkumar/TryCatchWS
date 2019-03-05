import jsr166y.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ForkJoin implements Config.Benchmark {

  public ForkJoin(int procs) {
    this.procs=procs;
  }

  public static void main(final String[] args) {
    int procs = args.length>0 ? Integer.parseInt(args[0]) : 1;
    String type = args.length>1?args[1] : "T1";
    int maxHeight = args.length>2 ? Integer.parseInt(args[2]) : 10;
    double itemToFindLoc = args.length>3 ? Double.parseDouble(args[3]) : 0.65;
    ForkJoin benchmark = new ForkJoin(procs);
    System.out.println("Initializing...");
    Config.setup(type, maxHeight, itemToFindLoc);
    System.out.println("Args: ");
    Config.printArgs();
    Config.launch(benchmark);
  }

  static AtomicBoolean found = new AtomicBoolean();
  private int procs;
  private ForkJoinPool pool;

  public void speculativeSearch() {
    pool = new ForkJoinPool(procs);
    found.set(false);
    try {
      pool.invoke(new RecursiveAction() {
        @Override
        public void compute() {
          final Config.Node rootNode = Config.rootNode;
          new Task(rootNode).fork();
          helpQuiesce();
        }
      });
    } catch(java.util.concurrent.CancellationException e) {
      System.out.println("Aborted");
    }
    if (!found.get()) {
      System.out.println("ERROR: Did not find element!");
    }
    else {
      System.out.println("SUCCESS: Found Element");
    }
  }

  private class Task extends RecursiveAction {
    private Config.Node treeNode;
    public Task(Config.Node n) { treeNode = n; }
    @Override
    public void compute() {
      if (Config.foundElement(treeNode)) {
        found.set(true);
        pool.shutdownNow();
      }
      for (int i = 0; i < treeNode.numChildren(); i++) {
        new Task(treeNode.child(i)).fork();
      }
    }
  }    
}

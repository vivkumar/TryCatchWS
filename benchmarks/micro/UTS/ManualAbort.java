import java.util.concurrent.atomic.AtomicBoolean;

public class ManualAbort implements Config.Benchmark {

  public ManualAbort() {
  }

  public static void main(final String[] args) {
    String type = args.length>0?args[0] : "T1";
    int maxHeight = args.length>1 ? Integer.parseInt(args[1]) : 10;
    double itemToFindLoc = args.length>2 ? Double.parseDouble(args[2]) : 0.65;
    ManualAbort benchmark = new ManualAbort();
    System.out.println("Initializing...");
    Config.setup(type, maxHeight, itemToFindLoc);
    System.out.println("Args: ");
    Config.printArgs();
    Config.launch(benchmark);
  }

  static AtomicBoolean found = new AtomicBoolean();

  public void speculativeSearch() {
    final Config.Node rootNode = Config.rootNode;
    found.set(false);
    finish {
      speculativeSearch(rootNode);
    } 
    if (!found.get()) {
      System.out.println("ERROR: Did not find element!");
    }
    else {
      System.out.println("SUCCESS: Found Element");
    }
  }

  private void speculativeSearch(final Config.Node treeNode) {
    // termination check on entry
    if (found.get()) { 
      return; 
    }
    if (Config.foundElement(treeNode)) {
      found.set(true);
      return;
    }
    for (int i = 0; i < treeNode.numChildren(); i++) {
      // cooperative termination check
      if (found.get()) { 
        return; 
      }
      async { speculativeSearch(treeNode.child(i)); }
    }
  }
}

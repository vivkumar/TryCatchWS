import java.util.concurrent.atomic.AtomicBoolean;

public class Featherlight implements Config.Benchmark {

  public Featherlight() {
  }

  public static void main(final String[] args) {
    String type = args.length>0?args[0] : "T1";
    int maxHeight = args.length>1 ? Integer.parseInt(args[1]) : 10;
    double itemToFindLoc = args.length>2 ? Double.parseDouble(args[2]) : 0.65;
    Featherlight benchmark = new Featherlight();
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
    finish_abort {
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
    if (Config.foundElement(treeNode)) {
      found.set(true);
      abort;
    }
    for (int i = 0; i < treeNode.numChildren(); i++) {
      async { speculativeSearch(treeNode.child(i)); }
    }
  }
}

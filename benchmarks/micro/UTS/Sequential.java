import java.util.concurrent.atomic.AtomicBoolean;

public class Sequential implements Config.Benchmark {

  public Sequential() {
  }

  public static void main(final String[] args) {
    String type = args.length>0?args[0] : "T1";
    int maxHeight = args.length>1 ? Integer.parseInt(args[1]) : 10;
    double itemToFindLoc = args.length>2 ? Double.parseDouble(args[2]) : 0.65;
    Sequential benchmark = new Sequential();
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
    try { // remove in parallel implementation
      speculativeSearch(rootNode);
    } catch(java.util.concurrent.CancellationException e){} // remove in parallel implementation
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
      throw new java.util.concurrent.CancellationException("Found"); // remove in parallel implementation
    }
    for (int i = 0; i < treeNode.numChildren(); i++) {
      speculativeSearch(treeNode.child(i));
    }
  }
}

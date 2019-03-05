import java.util.PriorityQueue;
import java.util.Queue;

public class ManualAbort implements Config.Benchmark {

  public ManualAbort() { 
    this.solution = null;
  }

  public static void main(final String[] args) {
    ManualAbort benchmark = new ManualAbort();
    Config.boardInit();
    System.out.println("Args: ");
    Config.printArgs();
    Config.launch(benchmark);
  }

  protected Config.SearchBoard solution;

  public void speculativeSearch() {
    final Queue<Config.SearchBoard> queue = new PriorityQueue<Config.SearchBoard>();

    // Create root node
    queue.add(Config.initialSearchBoard);

    finish { 
      speculativeSearch(queue);
    } 

    final Config.SearchBoard solutionBoard = solution;
    if (solutionBoard != null) {
      System.out.println("Num Moves to Solution ="+ solutionBoard.numMoves);
    } else {
      System.out.println("ERROR: No solution found!");
    }
    Config.validate(solution);
    solution = null;
  }

  private void speculativeSearch(final Object loopQueue) {
    // termination check on entry
    if (solution != null) {
      return;
    }
    final Queue<Config.SearchBoard> queue = (Queue<Config.SearchBoard>) loopQueue;
    while (!queue.isEmpty()) {
      final Config.SearchBoard loopNode = queue.poll();
      if (loopNode.isSolution()) {
        solution= loopNode;
        System.out.println("Aborting");    
        return;
      }
      Config.processSudokuNode(queue, loopNode);
      if (!queue.isEmpty()) {
        final Queue<Config.SearchBoard>[] queues = Config.split(queue);
        for (int i=0; i<queues.length; i++) { 
          // cooperative termination check
          if (solution != null) {
            return;
          }
          async { speculativeSearch(queues[i]); }
        }
      }
    }
  }
}

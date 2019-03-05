import jsr166y.*;
import java.util.PriorityQueue;
import java.util.Queue;

public class ForkJoin implements Config.Benchmark {

  public ForkJoin(int procs) { 
    this.procs = procs;
    this.solution = null;
  }

  private ForkJoinPool pool;
  private int procs;

  public static void main(final String[] args) {
    int procs = args.length>0?Integer.parseInt(args[0]):1;
    ForkJoin benchmark = new ForkJoin(procs);
    Config.boardInit();
    System.out.println("Args: ");
    Config.printArgs();
    Config.launch(benchmark);
  }

  protected Config.SearchBoard solution;

  public void speculativeSearch() {
    pool = new ForkJoinPool(procs);
    try {
      pool.invoke(new RecursiveAction() {
        @Override
        public void compute() {
          final Queue<Config.SearchBoard> queue = new PriorityQueue<Config.SearchBoard>();
          // Create root node
          queue.add(Config.initialSearchBoard);
          new Task(queue).fork();
          helpQuiesce();
        }
      });
    } catch(java.util.concurrent.CancellationException e) {
      System.out.println("Aborted");
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

  private class Task extends RecursiveAction {
    private Queue<Config.SearchBoard> queue;
    public Task(Queue<Config.SearchBoard> q) { queue = q; }
    @Override
    public void compute() {
      while (!queue.isEmpty()) {
        final Config.SearchBoard loopNode = queue.poll();
        if (loopNode.isSolution()) {
          solution= loopNode;
          System.out.println("Aborting");    
          pool.shutdownNow();
        }
        Config.processSudokuNode(queue, loopNode);
        if (!queue.isEmpty()) {
          final Queue<Config.SearchBoard>[] queues = Config.split(queue);
          for (int i=0; i<queues.length; i++) { 
            new Task(queues[i]).fork(); 
          }
        }
      }
    }
  }
}

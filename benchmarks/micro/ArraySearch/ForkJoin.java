import jsr166y.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: Vivek Kumar
 */
public class ForkJoin implements Config.Benchmark {
  private final int rows, cols, goalCount;
  private static final AtomicInteger hits = new AtomicInteger();
  private static Config.Complex[][] array;
  private static final List<Config.Complex> itemsToSearch = new ArrayList<Config.Complex>();
  private ForkJoinPool pool;
  private int procs;

  private ForkJoin(int rows, int cols, int goalCount, int procs) {  
    this.rows = rows;
    this.cols = cols;
    this.goalCount = goalCount;
    this.procs = procs;
  }

  public boolean verify() {
    return (hits.get() == goalCount);
  }

  public void checkItem(int row, int col) {
    if(itemsToSearch.contains(array[row][col])) {
      if(hits.incrementAndGet() == goalCount) {
        pool.shutdownNow();
      }
    }
  }

  public void speculativeSearch() {
    hits.set(0);
    this.pool = new ForkJoinPool(procs);
    try {
      pool.invoke(new RecursiveAction() {
        @Override
        public void compute() {
          new Task(0, rows, 1).fork();
          helpQuiesce();
        }
      });
    } catch(java.util.concurrent.CancellationException e) {
      System.out.println("Aborted");
    }
  }

  private class Task extends RecursiveAction {
    final int lower, upper, stride;
    public Task(int l, int u, int s) {
      lower=l; upper=u; stride=s; 
    }
    @Override
    public void compute() {
      if (stride >> 2 < procs) {
        int var0 = lower + upper >> 1;
        int var1 = stride << 1;
        Task left = new Task(lower, var0, var1);
        Task right = new Task(var0, upper, var1);
        left.fork();
        right.compute();
      } else {
        for(int i=lower; i<upper; i++) {
          for(int j=0; j<cols; j++) {
            checkItem(i, j);
          }
        }
      }
    }
  }

  public static void main(final String[] args) {
    final int procs = args.length > 0 ? Integer.parseInt(args[0]) : 1;
    final int goalCount = args.length > 1 ? Integer.parseInt(args[1]) : 10;
    final int rows = args.length > 2 ? Integer.parseInt(args[2]) : 1000;
    final int cols = args.length > 3 ? Integer.parseInt(args[3]) : 20000;
    ForkJoin benchmark = new ForkJoin(rows, cols, goalCount, procs);
    array = Config.setup(rows, cols, goalCount, itemsToSearch);
    Config.launch(benchmark);
  }
}

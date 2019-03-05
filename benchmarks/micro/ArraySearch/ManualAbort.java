import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: Vivek Kumar
 */
public class ManualAbort implements Config.Benchmark {
  private final int rows, cols, goalCount;
  private static final AtomicInteger hits = new AtomicInteger();
  private static Config.Complex[][] array;
  private static final List<Config.Complex> itemsToSearch = new ArrayList<Config.Complex>();

  private ManualAbort(int rows, int cols, int goalCount) {  
    this.rows = rows;
    this.cols = cols;
    this.goalCount = goalCount;
  }

  public boolean verify() {
    return (hits.get() == goalCount);
  }

  public void checkItem(int row, int col) {
    if(itemsToSearch.contains(array[row][col])) {
      if(hits.incrementAndGet() == goalCount) {
        /*do nothing*/
      }
    }
  }

  public void speculativeSearch() {
    hits.set(0);
    finish {
      async {
        for(int i=0; i<rows; i++) {
          // termination check on entry
          if(!verify()) {
            for(int j=0; j<cols; j++) {
              // cooperative termination check
              if(verify()) break;
              checkItem(i, j);
            }
          }
        }
      }
    } 
  }

  public static void main(final String[] args) {
    final int goalCount = args.length > 0 ? Integer.parseInt(args[0]) : 10;
    final int rows = args.length > 1 ? Integer.parseInt(args[1]) : 1000;
    final int cols = args.length > 2 ? Integer.parseInt(args[2]) : 20000;
    ManualAbort benchmark = new ManualAbort(rows, cols, goalCount);
    array = Config.setup(rows, cols, goalCount, itemsToSearch);
    Config.launch(benchmark);
  }
}
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class Config {

  interface Benchmark {
    public void speculativeSearch();
  }

  private static final String DEFAULT_FILE_NAME = "./16x16-1.txt";

  protected static String fileName = DEFAULT_FILE_NAME;
  public static boolean count = false;

  public static AtomicInteger nodesGeneratedCounter = new AtomicInteger(0);
  public static AtomicInteger nodesVisitedCounter = new AtomicInteger(0);

  public static SearchBoard initialSearchBoard = null;

  public static void launch(final Benchmark benchmark) {
    int inner = 5;
    int outter = 3;

    for(int i=0;i <outter; i++) {
      if(i+1 == outter) {
        org.mmtk.plan.Plan.harnessBegin();
        org.jikesrvm.scheduler.RVMThread.perfEventStart();
      }
      for(int j=0; j<inner; j++) {
        System.out.println("========================== ITERATION ("+i+"."+j+") ==================================");
        final long startTime = System.currentTimeMillis();
        benchmark.speculativeSearch();
        final long time = System.currentTimeMillis() - startTime;
        final double secs = ((double)time) / 1000.0;
        System.out.println("Time: " + secs);
      }
    }
    org.jikesrvm.scheduler.RVMThread.perfEventStop();
    org.mmtk.plan.Plan.harnessEnd();

  }

  protected static void printArgs() {
    System.out.println("Input File Name="+ Config.fileName);
    System.out.println("Board Size="+ initialSearchBoard.boardSize);
    System.out.println("Unsolved entries="+ initialSearchBoard.numUnsolved);
    System.out.println("Count="+ count);
  }

  public static void boardInit() {
    final File inputFile = new File(fileName);

    if (!inputFile.exists()) {
      System.out.println("Couldn't open "+fileName+" file for reading");
      System.exit(1);
    }

    /* read input file and initialize */
    final byte[] validValues = read(fileName);
    initialSearchBoard = new SearchBoard(validValues);
    nodesGeneratedCounter.set(0);
    nodesGeneratedCounter.incrementAndGet();

    nodesVisitedCounter.set(0);
  }

  protected static void validate(final SearchBoard sudokuBoard) {

    if (count) {
      System.out.println("Num Nodes Generated="+ nodesGeneratedCounter.get());
      System.out.println("Num Nodes Visited="+ nodesVisitedCounter.get());
    }

    nodesGeneratedCounter.set(0);
    nodesGeneratedCounter.incrementAndGet();

    nodesVisitedCounter.set(0);

    if (sudokuBoard == null) {
      System.out.println("ERROR: No solution found!");
    } else if (!sudokuBoard.isSolution()) {
      System.out.println("ERROR: Computed board is not a valid solution!");
    }
  }

  /*
   * Reads a single sudoku puzzle from a file
   * using the format from
   * http://sudoku.cvs.sourceforge.net/viewvc/sudoku/puzzles/
   * but generalized to support larger board sizes
   */
  private static byte[] read(final String fileName) {
    final File file = new File(fileName);
    final StringBuilder contents = new StringBuilder();
    BufferedReader reader = null;
    int lineSize = -1;
    try {
      reader = new BufferedReader(new FileReader(file));
      String text = null;

      // repeat until all lines is read
      while ((text = reader.readLine()) != null) {
        //System.out.println("Reading.");
        if (text.length() > 0) {
          if (text.charAt(0) != '/') {
            lineSize = text.length();
            contents.append(text);
            contents.append(System.getProperty(
                "line.separator"));
          }
        }
      }
    } catch (final IOException e) {
      e.printStackTrace();
    } finally {
      try {
        if (reader != null) {
          reader.close();
        }
      } catch (final IOException e) {
        e.printStackTrace();
      }
    }

    // show file contents here
    // System.out.println(contents.toString());

    int noValues = 0;
    for (int i = 0; i < lineSize; i++) {
      if ((contents.charAt(i) == '|')
          || (contents.charAt(i) == '+')
          || (contents.charAt(i) == '-')
          || (contents.charAt(i) == ' ')
          || (contents.charAt(i) == '\n')
          || (contents.charAt(i) == '\r')) {
      } else {
        noValues++;
      }
    }
    final byte[] board = new byte[noValues * noValues];

    int crtPos = 0;
    for (int i = 0; i < contents.length(); i++) {

      if ((contents.charAt(i) == '|')
          || (contents.charAt(i) == '+')
          || (contents.charAt(i) == '-')
          || (contents.charAt(i) == ' ')
          || (contents.charAt(i) == '\n')
          || (contents.charAt(i) == '\r')) {
        // do nothing
      } else {

        switch (contents.charAt(i)) {
        case '.':
          board[crtPos++] = -1;
          break;
        case '0':
          board[crtPos++] = 0;
          break;
        case '1':
          board[crtPos++] = 1;
          break;
        case '2':
          board[crtPos++] = 2;
          break;
        case '3':
          board[crtPos++] = 3;
          break;
        case '4':
          board[crtPos++] = 4;
          break;
        case '5':
          board[crtPos++] = 5;
          break;
        case '6':
          board[crtPos++] = 6;
          break;
        case '7':
          board[crtPos++] = 7;
          break;
        case '8':
          board[crtPos++] = 8;
          break;
        case '9':
          board[crtPos++] = 9;
          break;
        case 'A':
        case 'a':
          board[crtPos++] = 10;
          break;
        case 'B':
        case 'b':
          board[crtPos++] = 11;
          break;
        case 'C':
        case 'c':
          board[crtPos++] = 12;
          break;
        case 'D':
        case 'd':
          board[crtPos++] = 13;
          break;
        case 'E':
        case 'e':
          board[crtPos++] = 14;
          break;
        case 'F':
        case 'f':
          board[crtPos++] = 15;
          break;
        case 'G':
        case 'g':
          board[crtPos++] = 16;
          break;
        case 'H':
        case 'h':
          board[crtPos++] = 17;
          break;
        case 'I':
        case 'i':
          board[crtPos++] = 18;
          break;
        case 'J':
        case 'j':
          board[crtPos++] = 19;
          break;
        case 'K':
        case 'k':
          board[crtPos++] = 20;
          break;
        case 'L':
        case 'l':
          board[crtPos++] = 21;
          break;
        case 'M':
        case 'm':
          board[crtPos++] = 22;
          break;
        case 'N':
        case 'n':
          board[crtPos++] = 23;
          break;
        case 'O':
        case 'o':
          board[crtPos++] = 24;
          break;
        case 'P':
        case 'p':
          board[crtPos++] = 25;
          break;
        case 'Q':
        case 'q':
          board[crtPos++] = 26;
          break;
        case 'R':
        case 'r':
          board[crtPos++] = 27;
          break;
        case 'S':
        case 's':
          board[crtPos++] = 28;
          break;
        case 'T':
        case 't':
          board[crtPos++] = 29;
          break;
        case 'U':
        case 'u':
          board[crtPos++] = 30;
          break;
        case 'V':
        case 'v':
          board[crtPos++] = 31;
          break;
        case 'W':
        case 'w':
          board[crtPos++] = 32;
          break;
        case 'X':
        case 'x':
          board[crtPos++] = 33;
          break;
        case 'Y':
        case 'y':
          board[crtPos++] = 34;
          break;
        case 'Z':
        case 'z':
          board[crtPos++] = 35;
          break;
        }

      }
    }
    return board;
  }

  /**
   * Source: http://java.dzone.com/articles/thursday-code-puzzler-9
   * <p/>
   * Splits a linked list up into two sub-lists, one for the front half and one for the back. If the number of
   * elements are odd, the extra element goes to the front list.
   *
   * @param list the LinkedList to be split
   * @return an array of the two sublists; the front half list is in index 0, the back half list is in index 1
   */
  protected static <T> Queue<T>[] split(final Queue<T> list) {
    final int splitSize = (list.size() / 2) + 1;
    @SuppressWarnings("unchecked")
    final Queue<T>[] retValue = new PriorityQueue[]{
        new PriorityQueue<T>(splitSize),
        new PriorityQueue<T>(splitSize)};

    while (!list.isEmpty()) {
      retValue[0].add(list.poll());
      if (!list.isEmpty()) {
        retValue[1].add(list.poll());
      }
    }

    return retValue;
  }

  public static void processSudokuNode(final Queue<SearchBoard> queue, final SearchBoard loopNode) {
    final List<SearchBoard> neighbors = loopNode.neighbors();
    for (final SearchBoard neighbor : neighbors) {
      queue.add(neighbor);
    }
  }

  static class SearchBoard implements Comparable<SearchBoard> {
    private static byte[] copyOf(final byte[] src) {
      final byte[] dest = new byte[src.length];
      for (int i = 0; i < dest.length; i++) {
        dest[i] = src[i];
      }
      return dest;
    }

    private static int countUnsolved(final byte[] board) {
      int count = 0;
      for (int i = 0; i < board.length; i++) {
        if (board[i] < 0) {
          count++;
        }
      }
      return count;
    }

    private static void setBoardValue(final byte[] boardData, final int boardSize, final int location, final byte entry) {
      if (boardData[location] >= 0) {
        System.out.println("ERROR: Attempt to set an already populated field!");
        throw new IllegalStateException("ERROR: Attempt to set an already populated field!");
      }
      boardData[location] = entry;
      for (int i = 0; i < boardData.length; i++) {
        final byte loopData = boardData[i];
        if (loopData < 0) {
          final byte[] possibleValues = possibleValues(boardData, boardSize, i);
          if (possibleValues.length == 1) {
            boardData[i] = possibleValues[0];
          }
        }
      }
    }

    private static byte[] possibleValues(final byte[] boardData, final int boardSize, final int location) {

      final boolean[] valueTakenFlags = computeValueTakenFlags(boardData, boardSize, location);

      int count = 0;
      for (final boolean possibleValueFlag : valueTakenFlags) {
        if (!possibleValueFlag) {
          count++;
        }
      }

      final byte[] resultData = new byte[count];
      for (int i = 0, j = 0; i < valueTakenFlags.length; i++) {
        final boolean valueTakenFlag = valueTakenFlags[i];
        if (!valueTakenFlag) {
          resultData[j] = (byte) i;
          j++;
        }
      }

      return resultData;
    }

    private static boolean[] computeValueTakenFlags(final byte[] boardData, final int boardSize, final int location) {
      final boolean[] valueTakenFlags = new boolean[boardSize];

      final int itemRowIndex = location / boardSize;
      final int itemColIndex = location % boardSize;
      final int miniBoardSize = (int) Math.sqrt(boardSize);
      {
        // first mark values from rows
        final int rowStartIndex = itemRowIndex;
        for (int i = rowStartIndex * boardSize, j = 0; j < boardSize; i++, j++) {
          final byte loopValue = boardData[i];
          if (loopValue >= 0) {
            valueTakenFlags[loopValue] = true;
          }
        }
      }
      {
        // next mark values from cols
        final int colStartIndex = itemColIndex;
        for (int i = 0, j = colStartIndex; i < boardSize; i++, j += boardSize) {
          final byte loopValue = boardData[j];
          if (loopValue >= 0) {
            valueTakenFlags[loopValue] = true;
          }
        }
      }
      {
        // finally mark values from boxes
        final int rowStartIndex = itemRowIndex - (itemRowIndex % miniBoardSize);
        final int colStartIndex = (itemColIndex / miniBoardSize) * miniBoardSize;
        for (int i = rowStartIndex, i1 = 0; i1 < miniBoardSize; i++, i1++) {
          for (int j = colStartIndex, j1 = 0; j1 < miniBoardSize; j++, j1++) {
            final int index = (i * boardSize) + j;
            final byte loopValue = boardData[index];
            if (loopValue >= 0) {
              valueTakenFlags[loopValue] = true;
            }
          }
        }
      }
      return valueTakenFlags;
    }

    protected final int numMoves;
    private final int numValues;
    public final int boardSize;
    private final byte[] board;
    public final int numUnsolved;

    public SearchBoard(final byte[] setIntegers) {
      this(0, setIntegers);
      for (int i = 0; i < setIntegers.length; i++) {
        if (setIntegers[i] >= boardSize) {
          System.err.println("ERROR: Value at position-" + i + " is " + setIntegers[i]);
        }
      }
    }

    public SearchBoard(final int numMoves, final byte[] setIntegers) {

      this.numMoves = numMoves;
      // compute noRows = noColumns = boardSize, knowing that: numValues = boardSize * boardSize (square board)
      this.numValues = setIntegers.length;
      this.boardSize = (int) Math.sqrt(numValues);
      this.board = setIntegers;
      this.numUnsolved = countUnsolved(this.board);
    }

    protected List<SearchBoard> neighbors() {

      final List<SearchBoard> resultList = new ArrayList<SearchBoard>();

      int indexToFocusOn = -1;
      for (int i = 0; i < board.length; i++) {
        if (board[i] < 0) {
          indexToFocusOn = i;
          break;
        }
      }

      if (indexToFocusOn >= 0) {
        final byte[] possibleValues = possibleValues(board, boardSize, indexToFocusOn);
        for (final byte possibleValue : possibleValues) {

          final byte[] newBoardData = copyOf(this.board);
          setBoardValue(newBoardData, boardSize, indexToFocusOn, possibleValue);
          final SearchBoard newBoard = new SearchBoard(this.numMoves + 1, newBoardData);

          resultList.add(newBoard);
          if (Config.count) {
            Config.nodesGeneratedCounter.incrementAndGet();
          }
        }

      }

      return resultList;
    }

    protected boolean isSolution() {
      if (Config.count) {
        Config.nodesVisitedCounter.incrementAndGet();
      }
      return (numUnsolved == 0);
    }

    @Override
    public String toString() {
      String s = toSmallString();
      final int rows = boardSize;
      for (int r = 0; r < rows; r++) {
        for (int c = 0; c < rows; c++) {
          final byte loopData = board[r * rows + c];
          if (loopData < 0) {
            s += String.format("%3s", ".");
          } else {
            s += String.format("%3d", loopData);
          }
        }
        s += "\n";
      }

      return s;
    }

    protected String toSmallString() {
      return "* State [moves=" + numMoves + ", unsolved=" + numUnsolved + "]: \n";
    }

    //@Override
    public int compareTo(final SearchBoard other) {
      return this.numUnsolved - other.numUnsolved;
    }
  }
}

/**
 * Author: Vivek Kumar
 *
 * Platform and Language independent (tested for C/C++ and Java)
 * fixed sequence random number generator.
 *
 * Code borrowed from Wikipedia for Linear congruential generator
 * https://en.wikipedia.org/wiki/Linear_congruential_generator
 *
 * Values for constants a, c, and m taken from there 
 * from the table as in C++11's minstd_rand.
 *
 * Changes to the original code:
 * 1) returning absolute value from rand. unsigned int
 *    not used as it isn't supported in Java and we want 
 *    to have portability across both C/C++ and Java.
 * 2) seed initialized to 1 if user passed 0
 * 
 * NOTE: This code is not thread-safe
 *
 **/

class MyRandom {
  private int seed;
  private static final int a = 16807;
  private static final int c = 0;
  private static final int m = 2147483647;
  public MyRandom(int s) {
    if(s==0) s=1;
    this.seed = s;
  }
  public MyRandom() {
    this.seed = 123456789;
  }
  public int nextInt() {
    seed = (a * seed + c) % m;
    return Math.abs(seed);
  }
  public double nextDouble() {
    return (nextInt() / Integer.MAX_VALUE); 
  }
}


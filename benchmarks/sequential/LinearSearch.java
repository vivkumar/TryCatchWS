import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Random;

/**
 * @author Shams Imam (shams@rice.edu)
 */
public class LinearSearch {

    public static void main(final String[] args) {
        LSConfiguration.parseArgs(args);
        System.out.println("Args: ");
        LSConfiguration.printArgs();
        System.out.println();

        int inner = 5;
        int outter = 3;
        final long start = System.nanoTime();
        for(int i=0;i <outter; i++) {
            if(i+1 == outter) {
                org.mmtk.plan.Plan.harnessBegin();
                org.jikesrvm.scheduler.RVMThread.perfEventStart();
            }
            for(int j=0; j<inner; j++) {
                System.out.println("========================== ITERATION ("+i+"."+j+") ==================================");
                final long startTime = System.currentTimeMillis();
                runIteration();
                final long time = System.currentTimeMillis() - startTime;
                final double secs = ((double)time) / 1000.0;
                System.out.println("Time: " + secs);
            }
        }
        org.jikesrvm.scheduler.RVMThread.perfEventStop();
        org.mmtk.plan.Plan.harnessEnd();
    }

    public static void loop_body(final Object[] arrayToSearch) {
        for (int j = 0; j < arrayToSearch.length; j++) {
            if (LSConfiguration.itemFoundPredicate(arrayToSearch, j)) {
                resultObject=true;
                throw new ItemFoundException("Found");
            }
        }
    }

    static Boolean resultObject;

    public static void runIteration() {
        final Object[][] dataArray = LSConfiguration.dataArray;
        resultObject = false; 
        try {
	      for(int i=0; i<dataArray.length; i++) {
            loop_body(dataArray[i], resultObject);
        }
        } catch(ItemFoundException e) { }
        if (!resultObject) {
            System.out.println("[ERROR] Did not find element!");
        }
        else {
            System.out.println("Test Passed!");
        }
    }
}

class LSConfiguration {

    protected static int numRows = 1000;
    protected static int numCols = 200000;
    protected static int blockSize = 10;
    protected static double itemToFindLoc = 0.69;
    //protected static double itemToFindLoc = 0.45;
    //protected static double itemToFindLoc = 0.93;
    protected static boolean debug = false;

    protected static Object[][] dataArray = null;
    private static Object itemToFind = null;

    protected static void parseArgs(final String[] args) {
        int i = 0;
        while (i < args.length) {
            final String loopOptionKey = args[i];

            if(loopOptionKey.equals("-r")) {
                i += 1;
                numRows = Math.max(10, Integer.parseInt(args[i]));
            }
            if(loopOptionKey.equals("-c")) {
                i += 1;
                numCols = Math.max(10, Integer.parseInt(args[i]));
            }
            if(loopOptionKey.equals("-n")) {
                i += 1;
                blockSize = Integer.parseInt(args[i]);
            }
            if(loopOptionKey.equals("-f")) {
                i += 1;
                itemToFindLoc = Math.min(1, Math.max(0, Double.parseDouble(args[i])));
            }
            i += 1;
        }

        if (numRows % blockSize != 0) {
            throw new IllegalArgumentException("Block size does not divide num rows!");
        }

        final long startTime = System.nanoTime();
        initialize();
        final long endTime = System.nanoTime();

        final double execTimeMillis = (endTime - startTime) / 1e6;
        System.out.println("Array Generation time: "+ execTimeMillis + "ms");
    }

    protected static void initialize() {

        if (dataArray != null) {
            return;
        }

        itemToFind = new Complex(0, 0);
        dataArray = new Object[numRows][];

        final Random random1 = new Random(numRows + numCols);
        dataArray[0] = new Object[numCols];
        for (int c = 0; c < numCols; c++) {
            final double real = 1 + Math.abs(random1.nextDouble());
            final double imag = 1 + Math.abs(random1.nextDouble());
            dataArray[0][c] = new Complex(real, imag);
        }

        for (int r = 1; r < numRows; r++) {
            final int rr = r;
            final Object[] loopRow = new Object[numCols];
            dataArray[rr] = loopRow;
            // copy
            System.arraycopy(dataArray[0], 0, loopRow, 0, numCols);
            // shuffle
            final Random random2 = new Random(rr);
            for (int c = 0; c < numCols; c += 100) {
                final int index = random2.nextInt(c + 1);
                final Object temp = loopRow[index];
                loopRow[index] = loopRow[c];
                loopRow[c] = temp;
            }
        }

        final int itemToFindIndex = (int) (itemToFindLoc * numRows * numCols);
        if (itemToFindIndex >= 0) {
            final int targetRow = (int) (itemToFindLoc * numRows);
            final int targetCol = (int) (itemToFindLoc * numCols);
            dataArray[targetRow][targetCol] = itemToFind;
            System.out.println("Result Index is: (" + targetRow + "," + targetCol + ")");
        }
    }

    protected static void printArgs() {
        System.out.println("Num rows: "+ numRows);
        System.out.println("Num cols: "+ numCols);
        System.out.println("Block Size: "+ blockSize);
        System.out.println("Result index percent: "+ itemToFindLoc);
        System.out.println("debug: "+ debug);
    }

    protected static boolean itemFoundPredicate(final Object[] arrayToSearch, final int index) {
        return arrayToSearch[index].equals(itemToFind);
    }

    private static class Complex {
        private final double re;   // the real part
        private final double im;   // the imaginary part

        // create a new object with the given real and imaginary parts
        private Complex(final double real, final double imag) {
            re = real;
            im = imag;
        }

        @Override
        public boolean equals(final Object other) {

            if (other == null || getClass() != other.getClass()) {
                return false;
            }

            final Complex complex = (Complex) other;
            final int imDiff = Double.compare(complex.im, im);
            final int reDiff = Double.compare(complex.re, re);
            final int hashDiff = hashCode() - complex.hashCode();

            return reDiff == 0 && imDiff == 0 && hashDiff == 0;
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            temp = Double.doubleToLongBits(re);
            result = (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(im);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }

        @Override
        public String toString() {
            if (im == 0) {
                return re + "";
            }
            if (re == 0) {
                return im + "i";
            }
            if (im < 0) {
                return re + " - " + (-im) + "i";
            }
            return re + " + " + im + "i";
        }
    }
}

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Random;

/**
 * @author Shams Imam (shams@rice.edu)
 */
public class DualLinearSearch {

    public static void main(final String[] args) {
        DLSConfiguration.parseArgs(args);
        System.out.println("Args: ");
        DLSConfiguration.printArgs();
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

    public static void loop_body(final Object[] arrayToSearch, final AtomicBoolean resultObject1, final AtomicBoolean resultObject2) {
        for (int j = 0; j < arrayToSearch.length; j++) {
            if (DLSConfiguration.item1FoundPredicate(arrayToSearch, j)) {
                resultObject1.set(true);
            }
            if (DLSConfiguration.item2FoundPredicate(arrayToSearch, j)) {
                resultObject2.set(true);
            }
        }
    }

    public static void runIteration() {
        final Object[][] dataArray = DLSConfiguration.dataArray;
        final AtomicBoolean resultObject1 = new AtomicBoolean();
        final AtomicBoolean resultObject2 = new AtomicBoolean();
        finish{
            async {
	        for(int i=0; i<dataArray.length; i++) {
                    loop_body(dataArray[i], resultObject1, resultObject2);
                }
            }
        }
        if (!resultObject1.get() || !resultObject2.get()) {
            System.out.println("[ERROR] Did not find element!");
        }
        else {
            System.out.println("Test Passed!");
        }
    }
}

class DLSConfiguration {

    protected static int numRows = 1000;
    protected static int numCols = 200000;
    protected static int blockSize = 10;
    protected static int granularity = 100;
    protected static double itemToFindLoc1 = 0.19; //-f1 0.19
    protected static double itemToFindLoc2 = 0.93; //-f2 0.93
    protected static boolean debug = false;

    protected static Object[][] dataArray = null;
    private static Object itemToFind1 = null;
    private static Object itemToFind2 = null;

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
            if(loopOptionKey.equals("-g")) {
                i += 1;
                granularity = Integer.parseInt(args[i]);
            }
            if(loopOptionKey.equals("-f1")) {
                i += 1;
                itemToFindLoc1 = Math.min(1, Math.max(0, Double.parseDouble(args[i])));
            }
            if(loopOptionKey.equals("-f2")) {
                i += 1;
                itemToFindLoc2 = Math.min(1, Math.max(0, Double.parseDouble(args[i])));
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

        itemToFind1 = new Complex(0, 0);
        itemToFind2 = new Complex(0, 0);
        dataArray = new Object[numRows][];

        final Random random1 = new Random(numRows + numCols + granularity);
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

        final int itemToFindIndex1 = (int) (itemToFindLoc1 * numRows * numCols);
        if (itemToFindIndex1 >= 0) {
            final int targetRow = (int) (itemToFindLoc1 * numRows);
            final int targetCol = (int) (itemToFindLoc1 * numCols);
            dataArray[targetRow][targetCol] = itemToFind1;
            System.out.println("Result1 Index is: (" + targetRow + "," + targetCol + ")");
        }
        final int itemToFindIndex2 = (int) (itemToFindLoc2 * numRows * numCols);
        if (itemToFindIndex2 >= 0) {
            final int targetRow = (int) (itemToFindLoc2 * numRows);
            final int targetCol = (int) (itemToFindLoc2 * numCols);
            dataArray[targetRow][targetCol] = itemToFind2;
            System.out.println("Result2 Index is: (" + targetRow + "," + targetCol + ")");
        }
    }

    protected static void printArgs() {
        System.out.println("Num rows: "+ numRows);
        System.out.println("Num cols: "+ numCols);
        System.out.println("Block Size: "+ blockSize);
        System.out.println("Result1 index percent: "+ itemToFindLoc1);
        System.out.println("Result2 index percent: "+ itemToFindLoc2);
        System.out.println("Granularity: "+ granularity);
        System.out.println("debug: "+ debug);
    }

    protected static boolean item1FoundPredicate(final Object[] arrayToSearch, final int index) {
        return arrayToSearch[index].equals(itemToFind1);
    }

    protected static boolean item2FoundPredicate(final Object[] arrayToSearch, final int index) {
        return arrayToSearch[index].equals(itemToFind2);
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

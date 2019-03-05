import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class Config {

  interface Benchmark {
    public void speculativeSearch();
  }

  // TREE TYPE AND SHAPE CONSTANTS
  protected static final int BIN = 0;                // TYPE: binomial tree
  protected static final int GEO = 1;                // TYPE: geometric tree
  protected static final int HYBRID = 2;                // TYPE: hybrid tree, start geometric, shift to binomial

  protected static final int LINEAR = 0;                // SHAPE: linearly decreasing geometric tree
  protected static final int EXPDEC = 1;                // SHAPE: exponentially decreasing geometric tree
  protected static final int CYCLIC = 2;                // SHAPE: cyclic geometric tree
  protected static final int FIXED = 3;                // SHAPE: fixed branching factor geometric tree

  protected static final int UNSETI = -1;                // sentinel for unset integer values
  protected static final double UNSETD = -1.0;            // sentinel for unset double values

  // misc constants
  protected static final double TWO_PI = 2.0 * Math.PI;
  protected static final int MAX_NUM_CHILDREN = 100;        // max number of children for BIN tree

  // UTS parameters and defaults
  protected static int treeType = GEO;            // UTS Type: Default = GEO
  protected static int shape_fn = LINEAR;            // GEOMETRIC TREE: shape function: Default = LINEAR
  protected static boolean debug = false;
  protected static boolean count = false;

  protected static String type = "T1";
  protected static int maxHeight = 10;
  protected static double itemToFindLoc = 0.65;

  protected static double b_0 = 4.0;            // branching factor for root node
  protected static int rootId = 0;            // RNG seed for root node
  protected static int nonLeafBF = 4;            // BINOMIAL TREE: branching factor for nonLeaf nodes
  protected static double nonLeafProb = 15.0 / 64.0;        // BINOMIAL TREE: probability a node is a nonLeaf
  protected static int gen_mx = 6;            // GEOMETRIC TREE: maximum number of generations
  protected static double shiftDepth = 0.5;            // HYBRID TREE: Depth fraction for shift from GEO to BIN

  protected static Node rootNode = null;
  private static Node itemToFind = null;

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

  protected static void setup(String _type, int _maxHeight, double _itemToFindLoc) {
    type = _type;
    maxHeight = _maxHeight;
    itemToFindLoc = _itemToFindLoc;

    final long startTime = System.nanoTime();
    initialize(type);
    final long endTime = System.nanoTime();

    final double execTimeMillis = (endTime - startTime) / 1e6;
    System.out.println("Tree Generation = "+ execTimeMillis);
  }

  private static void initialize(final String type) {

    if ("T1".equalsIgnoreCase(type)) {
      // T1="-t 1 -a 3 -d 10 -b 4 -r 19"
      rootId = 19;
      treeType = 1;
      shape_fn = 3;
      b_0 = 4;
      gen_mx = 10;
    } else if ("T1L".equalsIgnoreCase(type)) {
      // T1L="-t 1 -a 3 -d 13 -b 4 -r 29"
      rootId = 29;
      treeType = 1;
      shape_fn = 3;
      b_0 = 4;
      gen_mx = 13;
    } else if ("T2".equalsIgnoreCase(type)) {
      // T2="-t 1 -a 2 -d 16 -b 6 -r 502"
      rootId = 502;
      treeType = 1;
      shape_fn = 2;
      b_0 = 6;
      gen_mx = 16;
    } else if ("T3".equalsIgnoreCase(type)) {
      // T3="-t 0 -b 2000 -q 0.124875 -m 8 -r 42"
      rootId = 42;
      nonLeafProb = 0.124875;
      nonLeafBF = 8;
      treeType = 0;
      b_0 = 2000;
    } else if ("T4".equalsIgnoreCase(type)) {
      // T4="-t 2 -a 0 -d 16 -b 6 -r 1 -q 0.234375 -m 4"
      rootId = 1;
      nonLeafProb = 0.234375;
      nonLeafBF = 4;
      treeType = 2;
      shape_fn = 0;
      b_0 = 6;
      gen_mx = 16;
    } else if ("T5".equalsIgnoreCase(type)) {
      // T5="-t 1 -a 0 -d 20 -b 4 -r 34"
      rootId = 34;
      treeType = 1;
      shape_fn = 0;
      b_0 = 4;
      gen_mx = 20;
    } else {
      throw new IllegalStateException("Unsupported type: " + type);
    }

    gen_mx = maxHeight;

    rootNode = new Node(rootId);

    final int numNodes = countNodes(rootNode);
    System.out.println("Total nodes = "+numNodes);

    final int itemToFindIndex = (int) (numNodes * itemToFindLoc);
    if (itemToFindIndex >= 0) {
      itemToFind = findNode(rootNode, new AtomicInteger(itemToFindIndex));
    }
  }

  protected static void printArgs() {
    System.out.println("Type "+ type);
    System.out.println("Max Height "+ maxHeight);
    System.out.println("Root Id "+ rootId);
    System.out.println("Tree type "+ treeType);
    System.out.println("Result index percent "+ itemToFindLoc);
    System.out.println("Total nodes "+ countNodes(rootNode));
    if (itemToFind != null) {
      System.out.println("Target Height "+ itemToFind.height);
    }
    System.out.println("Count "+ count);
    System.out.println("debug "+ debug);
  }

  private static int countNodes(final Node node) {

    int result = 0;

    final int numChildren = node.numChildren();
    for (int n = 0; n < numChildren; n++) {
      final Node childNode = node.child(n);
      result += countNodes(childNode);
    }

    return 1 + result;
  }

  private static Node findNode(final Node node, final AtomicInteger counter) {

    counter.decrementAndGet();
    if (counter.get() == 0) {
      return node;
    }

    final int numChildren = node.numChildren();
    for (int n = 0; n < numChildren; n++) {
      final Node childNode = node.child(n);
      final Node loopNode = findNode(childNode, counter);
      if (loopNode != null) {
        return loopNode;
      }
    }

    return null;
  }

  protected static boolean foundElement(final Node nodeToSearch) {
    return nodeToSearch.equals(itemToFind);
  }
  static class Node {

    private static int numChildren(final int height, final int type, final Sha1Generator state) { // generic
      int nChildren;

    switch (Config.treeType) {
    case Config.BIN:
      nChildren = numChildren_bin(height, state);
      break;
    case Config.GEO:
      nChildren = numChildren_geo(height, state);
      break;
    case Config.HYBRID:
      if (height < Config.shiftDepth * Config.gen_mx) {
        nChildren = numChildren_geo(height, state);
      } else {
        nChildren = numChildren_bin(height, state);
      }
      break;
    default:
      throw new IllegalStateException("Node:numChildren(): Unknown tree type");
    }

    if (height == 0 && type == Config.BIN) {    // only BIN root can have more than MAX_NUM_CHILDREN
      final int rootBF = (int) Math.ceil(Config.b_0);
      if (nChildren > rootBF) {
        System.out.println("*** Number of children of root truncated from "
            + nChildren + " to " + rootBF);
        nChildren = rootBF;
      }
    } else {
      if (nChildren > Config.MAX_NUM_CHILDREN) {
        System.out.println("*** Number of children truncated from "
            + nChildren + " to " + Config.MAX_NUM_CHILDREN);
        nChildren = Config.MAX_NUM_CHILDREN;
      }
    }
    return nChildren;
    }

    private static int numChildren_bin(final int height, final Sha1Generator state) {            // Binomial: distribution is identical below root
      final int nc;
      if (height == 0) {
        nc = (int) Math.floor(Config.b_0);
      } else if (rng_toProb(state.rand()) < Config.nonLeafProb) {
        nc = Config.nonLeafBF;
      } else {
        nc = 0;
      }
      return nc;
    }

    private static int numChildren_geo(final int height, final Sha1Generator state) {            // Geometric: distribution controlled by shape and height
      double b_i = Config.b_0;
      if (height > 0) {
        switch (Config.shape_fn) {    // use shape function to compute target b_i
        case Config.EXPDEC:        // expected size polynomial in height
          b_i = Config.b_0 * Math.pow((double) height, -Math.log(Config.b_0) / Math.log((double) Config.gen_mx));
          break;
        case Config.CYCLIC:        // cyclic tree
          if (height > 5 * Config.gen_mx) {
            b_i = 0.0;
            break;
          }
          b_i = Math.pow(Config.b_0, Math.sin(Config.TWO_PI * (double) height / (double) Config.gen_mx));
          break;
        case Config.FIXED:        // identical distribution at all nodes up to max height
          b_i = (height < Config.gen_mx) ? Config.b_0 : 0;
          break;
        case Config.LINEAR:        // linear decrease in b_i
        default:
          b_i = Config.b_0 * (1.0 - ((double) height / (double) Config.gen_mx));
          break;
        }
      }
      final double p = 1.0 / (1.0 + b_i);            // probability corresponding to target b_i
      final int h = state.rand();
      final double u = rng_toProb(h);        // get uniform random number on [0,1)
      final int nChildren = (int) Math.floor(Math.log(1.0 - u) / Math.log(1.0 - p));
      // return max number of children at this cumulative probability
      return nChildren;
    }

    private static double rng_toProb(final int n) {         // convert a random number on [0,2^31) to one on [0.1)
      return ((n < 0) ? 0.0 : ((double) n) / 2147483648.0);
    }

    private static int childCount(final Sha1Generator state, final int type, final int height) {
      return height >= Config.maxHeight ? 0 : Math.max(1 + (height % 2), numChildren(height, type, state));
    }

    // Node State
    final Sha1Generator state;
    final int type;
    final int height;
    final int nChildren;
    final Node[] children;

    Node(final int rootID) {                    // root constructor: count the nodes as they are created
      this(new Sha1Generator(rootID), 0);
    }

    Node(final Node parent, final int spawn) {                // child constructor: count the nodes as they are created
      this(new Sha1Generator(parent.state, spawn), parent.height + 1);
    }

    Node(final Sha1Generator state, final int height) {
      this.state = state;
      this.type = Config.treeType;
      this.height = height;
      this.nChildren = childCount(state, type, height);
      this.children = new Node[this.nChildren];
      initializeChildren();
    }

    private void initializeChildren() {
      for (int c = 0; c < nChildren; c++) {
        children[c] = new Node(this, c);
      }
    }

    int numChildren() {
      return nChildren;
    }

    Node child(final int index) {
      return children[index];
    }

    int childType() {             // determine what kind of children this node will have
      switch (type) {
      case Config.BIN:
        return Config.BIN;
      case Config.GEO:
        return Config.GEO;
      case Config.HYBRID:
        if (height < Config.shiftDepth * Config.gen_mx) {
          return Config.GEO;
        } else {
          return Config.BIN;
        }
      default:
        throw new IllegalStateException("uts_get_childtype(): Unknown tree type");
      }
    }

    @Override
    public int hashCode() {
      int result = state.hashCode();
      result = 31 * result + type;
      result = 31 * result + height;
      result = 31 * result + nChildren;
      return result;
    }

    @Override
    public boolean equals(final Object other) {
      if (other == null || getClass() != other.getClass()) {
        return false;
      }

      final Node node = (Node) other;

      final boolean heightEqual = height == node.height;
      final boolean numChildrenEqual = nChildren == node.nChildren;
      final boolean typeEqual = type == node.type;
      final boolean stateEqual = state.equals(node.state);

      return (heightEqual && numChildrenEqual && typeEqual && stateEqual);
    }
  }
}

final class Sha1Compiler {
  // internal constants
  private final static int SHA1_DIGEST_SIZE = 20;
  private final static int SHA1_BLOCK_SIZE = 64;
  private final static int SHA1_MASK = SHA1_BLOCK_SIZE - 1;
  // internal rng state
  private int[] digest = new int[SHA1_DIGEST_SIZE / 4];    // 160 bit internal representation
  private int[] msgBlock = new int[SHA1_BLOCK_SIZE / 4];    // 64 byte internal working buffer
  private long count = 0;                    // 64 bit counter of bytes processed

  Sha1Compiler() {
    digest[0] = (int) 0x67452301l;
    digest[1] = (int) 0xefcdab89l;
    digest[2] = (int) 0x98badcfel;
    digest[3] = (int) 0x10325476l;
    digest[4] = (int) 0xc3d2e1f0l;
  }

  public final void hash(final byte[] data, final int length) {
    int bytePos = 0;                // byte position in data[]
    final int pos = (int) (count & SHA1_MASK);    // byte position in msgBlock
    int wordPos = pos >>> 2;            // word position in msgBlock
    int space = SHA1_BLOCK_SIZE - pos;    // bytes left in msgBlock
    int len = length;            // number of bytes left to process in data
    count += len;            // total number of bytes processed since begin
    while (len >= space) {
      for (; wordPos < (SHA1_BLOCK_SIZE >>> 2); bytePos += 4) {    // "int" aligned (byte)memory to (int)memory copy
        msgBlock[wordPos++] = (((int) data[bytePos] & 0xFF) << 24) | (((int) data[bytePos + 1] & 0xFF) << 16) | (((int) data[bytePos + 2] & 0xFF) << 8) | ((int) data[bytePos + 3] & 0xFF);
      }
      compile();
      len -= space;
      space = SHA1_BLOCK_SIZE;
      wordPos = 0;
    }
    for (; bytePos < length; bytePos += 4) {        // this is the "int" aligned (byte)memory to (int)memory copy
      msgBlock[wordPos++] = (((int) data[bytePos] & 0xFF) << 24) | (((int) data[bytePos + 1] & 0xFF) << 16)
          | (((int) data[bytePos + 2] & 0xFF) << 8) | (((int) data[bytePos + 3] & 0xFF));
    }
  }

  public final void digest(final byte[] output) {
    int i = (int) (count & SHA1_MASK);    // how many bytes already in msgBlock[]?
    msgBlock[i >> 2] &= (int) (0xffffff80l << 8 * (~i & 3));
    msgBlock[i >> 2] |= (int) (0x00000080l << 8 * (~i & 3));
    if (i > SHA1_BLOCK_SIZE - 9) {
      if (i < 60) {
        msgBlock[15] = 0;
      }
      compile();
      i = 0;
    } else {
      i = (i >> 2) + 1;
    }
    while (i < 14) {
      msgBlock[i++] = 0;
    }
    msgBlock[14] = (int) ((count >> 29));
    msgBlock[15] = (int) ((count << 3));
    compile(); // THIS call accounts for 50% of the program execution time...
    for (i = 0; i < SHA1_DIGEST_SIZE; ++i) {
      output[i] = (byte) (digest[i >> 2] >> (8 * (~i & 3)));
    }
  }

  private static int rotl32(final int x, final int n) {
    return ((x) << n) | ((x) >>> (32 - n));
  }

  private static int rotr32(final int x, final int n) {
    return ((x) >>> n) | ((x) << (32 - n));
  }

  private static int bswap_32(final int x) {
    return ((rotr32((x), 24) & (int) 0x00ff00ff) | (rotr32((x), 8) & (int) 0xff00ff00));
  }

  private static void bsw_32(final int[] p, final int n) {
    int _i = n;
    while (_i-- != 0) {
      p[_i] = bswap_32(p[_i]);
    }
  }

  private static int ch(final int x, final int y, final int z) {
    return ((z) ^ ((x) & ((y) ^ (z))));
  }

  private static int parity(final int x, final int y, final int z) {
    return ((x) ^ (y) ^ (z));
  }

  private static int maj(final int x, final int y, final int z) {
    return (((x) & (y)) | ((z) & ((x) ^ (y))));
  }

  private static int hf(final int[] w, final int i, final boolean hf_basic) {
    if (hf_basic) {
      return w[i];
    } else {
      final int x = i & 15;
      w[x] = rotl32(w[((i) + 13) & 15] ^ w[((i) + 8) & 15] ^ w[((i) + 2) & 15] ^ w[(i) & 15], 1);
      return w[x];
    }
  }

  private static void one_cycle(final int[] v, final int a, final int b, final int c,
      final int d, final int e, final String f, final int k, final int h) {

    if (f.equals("ch")) {
      v[e] += (rotr32(v[a], 27) + ch(v[b], v[c], v[d]) + k + h);
    } else if (f.equals("maj")) {
      v[e] += (rotr32(v[a], 27) + maj(v[b], v[c], v[d]) + k + h);
    } else if (f.equals("parity")) {
      v[e] += (rotr32(v[a], 27) + parity(v[b], v[c], v[d]) + k + h);
    } else {
      System.out.println("one_cycle(): error as unknown function type -->" + f);
      System.exit(-1);
    }
    v[b] = rotr32(v[b], 2);
  }

  private static void five_cycle(final int[] w, final boolean hf_basic, final int[] v,
      final String f, final int k, final int i) {
    one_cycle(v, 0, 1, 2, 3, 4, f, k, hf(w, i, hf_basic));
    one_cycle(v, 4, 0, 1, 2, 3, f, k, hf(w, i + 1, hf_basic));
    one_cycle(v, 3, 4, 0, 1, 2, f, k, hf(w, i + 2, hf_basic));
    one_cycle(v, 2, 3, 4, 0, 1, f, k, hf(w, i + 3, hf_basic));
    one_cycle(v, 1, 2, 3, 4, 0, f, k, hf(w, i + 4, hf_basic));
  }

  private void compile() {
    int v0, v1, v2, v3, v4;
    final int[] v = new int[5];
    System.arraycopy(digest, 0, v, 0, 5);

    five_cycle(msgBlock, true, v, "ch", (int) 0x5a827999, 0);
    five_cycle(msgBlock, true, v, "ch", (int) 0x5a827999, 5);
    five_cycle(msgBlock, true, v, "ch", (int) 0x5a827999, 10);
    one_cycle(v, 0, 1, 2, 3, 4, "ch", (int) 0x5a827999, hf(msgBlock, 15, true));

    one_cycle(v, 4, 0, 1, 2, 3, "ch", (int) 0x5a827999, hf(msgBlock, 16, false));
    one_cycle(v, 3, 4, 0, 1, 2, "ch", (int) 0x5a827999, hf(msgBlock, 17, false));
    one_cycle(v, 2, 3, 4, 0, 1, "ch", (int) 0x5a827999, hf(msgBlock, 18, false));
    one_cycle(v, 1, 2, 3, 4, 0, "ch", (int) 0x5a827999, hf(msgBlock, 19, false));

    five_cycle(msgBlock, false, v, "parity", (int) 0x6ed9eba1, 20);
    five_cycle(msgBlock, false, v, "parity", (int) 0x6ed9eba1, 25);
    five_cycle(msgBlock, false, v, "parity", (int) 0x6ed9eba1, 30);
    five_cycle(msgBlock, false, v, "parity", (int) 0x6ed9eba1, 35);

    five_cycle(msgBlock, false, v, "maj", (int) 0x8f1bbcdc, 40);
    five_cycle(msgBlock, false, v, "maj", (int) 0x8f1bbcdc, 45);
    five_cycle(msgBlock, false, v, "maj", (int) 0x8f1bbcdc, 50);
    five_cycle(msgBlock, false, v, "maj", (int) 0x8f1bbcdc, 55);

    five_cycle(msgBlock, false, v, "parity", (int) 0xca62c1d6, 60);
    five_cycle(msgBlock, false, v, "parity", (int) 0xca62c1d6, 65);
    five_cycle(msgBlock, false, v, "parity", (int) 0xca62c1d6, 70);
    five_cycle(msgBlock, false, v, "parity", (int) 0xca62c1d6, 75);
    digest[0] += v[0];
    digest[1] += v[1];
    digest[2] += v[2];
    digest[3] += v[3];
    digest[4] += v[4];
  }

  static String toHex(final int data) {
    String result = java.lang.Integer.toHexString(data);
    for (int i = result.length(); i < 8; i++) {
      result = "0" + result;
    }
    return result;
  }

  static String toHex(final long data) {
    String result = java.lang.Long.toHexString(data);
    for (int i = result.length(); i < 16; i++) {
      result = "0" + result;
    }
    return result;
  }

  private void showDigest() {
    for (int i = 0; i < 5; i++) {
      System.out.print(toHex(digest[i]) + " ");
    }
    System.out.println(" ");
  }

  private void showMsgBlock() {
    for (int i = 0; i < 16; i++) {
      System.out.print(toHex(msgBlock[i]) + " ");
    }
    System.out.println(" ");
  }
}

final class Sha1Generator {
  // internal constants
  private final static int POS_MASK = 0x7fffffff;
  private final static int LOWBYTE = 0xFF;
  private final static int SHA1_DIGEST_SIZE = 20;

  // internal rng state
  private final byte[] state = new byte[SHA1_DIGEST_SIZE];    // 160 bit output representation

  // new rng from seed
  public Sha1Generator(final int seedarg) {
    final byte[] seedstate = new byte[20];
    for (int i = 0; i < 16; i++) {
      seedstate[i] = 0;
    }
    seedstate[16] = (byte) (LOWBYTE & (seedarg >>> 24));
    seedstate[17] = (byte) (LOWBYTE & (seedarg >>> 16));
    seedstate[18] = (byte) (LOWBYTE & (seedarg >>> 8));
    seedstate[19] = (byte) (LOWBYTE & (seedarg));
    final Sha1Compiler sha1 = new Sha1Compiler();
    sha1.hash(seedstate, 20);
    sha1.digest(state);
  }

  // New rng from existing rng
  public Sha1Generator(final Sha1Generator parent, final int spawnnumber) {
    final byte[] seedstate = new byte[4];
    seedstate[0] = (byte) (LOWBYTE & (spawnnumber >>> 24));
    seedstate[1] = (byte) (LOWBYTE & (spawnnumber >>> 16));
    seedstate[2] = (byte) (LOWBYTE & (spawnnumber >>> 8));
    seedstate[3] = (byte) (LOWBYTE & (spawnnumber));
    final Sha1Compiler sha1 = new Sha1Compiler();
    sha1.hash(parent.state, 20);
    sha1.hash(seedstate, 4);
    sha1.digest(state);
  }

  // Return next random number
  public final int nextrand() {
    int d;
    final Sha1Compiler sha1 = new Sha1Compiler();
    sha1.hash(state, 20);
    sha1.digest(state);
    return POS_MASK & (((LOWBYTE & (int) state[16]) << 24) | ((LOWBYTE & (int) state[17]) << 16)
        | ((LOWBYTE & (int) state[18]) << 8) | ((LOWBYTE & (int) state[19])));
  }

  // return current random number (no advance)
  public final int rand() {
    int d;
    return POS_MASK & (((LOWBYTE & (int) state[16]) << 24) | ((LOWBYTE & (int) state[17]) << 16)
        | ((LOWBYTE & (int) state[18]) << 8) | ((LOWBYTE & (int) state[19])));
  }

  // describe the state of the RNG
  public String showstate() {
    final String[] hex = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F"};
    String sha1state = "SHA1 state=|";
    for (int i = 0; i < 20; i++) {
      sha1state += hex[((state[i] >> 4) & 0x0F)];
      sha1state += hex[((state[i] >> 0) & 0x0F)];
      sha1state += "|";
    }
    return sha1state;
  }

  // describe the RNG
  public String showtype() {
    return ("SHA-1 160 bits");
  }

  @Override
  public boolean equals(final Object o) {

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final Sha1Generator that = (Sha1Generator) o;
    return Arrays.equals(state, that.state);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(state);
  }
}


package io.github.flashlock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class DagTest {

  @Test
  void emptyGraph_hasSizeZero() {
    Dag<String> dag = Dag.<String>builder().build();
    assertEquals(0, dag.size());
  }

  @Test
  void singleNode_exposesPayloadAndZeroDegrees() {
    Dag.Builder<String> b = Dag.<String>builder();
    int id = b.add("solo");
    Dag<String> dag = b.build();
    assertEquals(1, dag.size());
    assertEquals("solo", dag.get(id));
    assertEquals(0, dag.inDegree(id));
    assertEquals(0, dag.outDegree(id));
  }

  @Test
  void linearChain_degreesAndAdjacency() {
    Dag.Builder<String> b = Dag.<String>builder();
    int a = b.add("a");
    int m = b.add("m");
    int z = b.add("z");
    b.connect(a, m).connect(m, z);
    Dag<String> dag = b.build();

    assertEquals(0, dag.inDegree(a));
    assertEquals(1, dag.inDegree(m));
    assertEquals(1, dag.inDegree(z));

    assertEquals(1, dag.outDegree(a));
    assertEquals(1, dag.outDegree(m));
    assertEquals(0, dag.outDegree(z));

    assertArrayEquals(new int[] {m}, dag.targets(a).toArray());
    assertArrayEquals(new int[] {z}, dag.targets(m).toArray());
    assertArrayEquals(new int[0], dag.targets(z).toArray());

    assertArrayEquals(new int[0], dag.sources(a).toArray());
    assertArrayEquals(new int[] {a}, dag.sources(m).toArray());
    assertArrayEquals(new int[] {m}, dag.sources(z).toArray());
  }

  @Test
  void diamond_dagBuilds() {
    Dag.Builder<Integer> b = Dag.<Integer>builder();
    int root = b.add(0);
    int left = b.add(1);
    int right = b.add(2);
    int sink = b.add(3);
    b.connect(root, left).connect(root, right).connect(left, sink).connect(right, sink);
    Dag<Integer> dag = b.build();
    assertEquals(2, dag.inDegree(sink));
    assertArrayEquals(new int[] {left, right}, dag.sources(sink).sorted().toArray());
  }

  @Test
  void iterator_yieldsPayloadsInIdOrder() {
    Dag.Builder<String> b = Dag.<String>builder();
    b.add("first");
    b.add("second");
    Dag<String> dag = b.build();
    List<String> payloads = new java.util.ArrayList<>();
    dag.forEach(payloads::add);
    assertEquals(List.of("first", "second"), payloads);
  }

  @Test
  void duplicateEdge_connectIsIdempotent() {
    Dag.Builder<String> b = Dag.<String>builder();
    int x = b.add("x");
    int y = b.add("y");
    b.connect(x, y).connect(x, y);
    Dag<String> dag = b.build();
    assertEquals(1, dag.outDegree(x));
    assertEquals(1, dag.inDegree(y));
  }

  @Test
  void selfLoop_throwsIllegalGraphException() {
    Dag.Builder<String> b = Dag.<String>builder();
    int n = b.add("n");
    assertThrows(IllegalGraphException.class, () -> b.connect(n, n));
  }

  @Test
  void directedCycle_throwsIllegalGraphExceptionOnBuild() {
    Dag.Builder<String> b = Dag.<String>builder();
    int u = b.add("u");
    int v = b.add("v");
    b.connect(u, v).connect(v, u);
    assertThrows(IllegalGraphException.class, b::build);
  }

  @Test
  void connect_withInvalidNodeId_throwsIndexOutOfBounds() {
    Dag.Builder<String> b = Dag.<String>builder();
    b.add("only");
    assertThrows(IndexOutOfBoundsException.class, () -> b.connect(0, 1));
  }

  @Test
  void get_withInvalidId_throwsIndexOutOfBounds() {
    Dag.Builder<String> b = Dag.<String>builder();
    b.add("x");
    Dag<String> dag = b.build();
    assertThrows(IndexOutOfBoundsException.class, () -> dag.get(1));
    assertThrows(IndexOutOfBoundsException.class, () -> dag.get(-1));
  }

  @Test
  void validateNode_rejectsOutOfRange() {
    assertThrows(IndexOutOfBoundsException.class, () -> Dag.validateNode(-1, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> Dag.validateNode(1, 1));
  }
}

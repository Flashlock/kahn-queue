package com.betts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DagTest {

  @Test
  void build_empty_hasSizeZero() {
    assertEquals(0, Dag.<String>builder().build().size());
  }

  @Test
  void add_returnsNewNodeId_forUseWithConnect() {
    Dag.Builder<String> b = Dag.builder();
    int idA = b.add("a");
    int idB = b.add("b");
    assertEquals(0, idA);
    assertEquals(1, idB);
    Dag<String> dag = b.build();
    assertEquals("a", dag.get(idA));
    assertEquals("b", dag.get(idB));
  }

  @Test
  void connect_invalidEndpoint_throwsIndexOutOfBounds() {
    var b = Dag.<String>builder();
    b.add("a");
    assertThrows(IndexOutOfBoundsException.class, () -> b.connect(-1, 0));
    assertThrows(IndexOutOfBoundsException.class, () -> b.connect(0, 2));
  }

  @Test
  void chain_edges_updateInDegrees() {
    var b = Dag.<String>builder();
    int a = b.add("a");
    int x = b.add("x");
    int y = b.add("y");
    b.connect(a, x).connect(x, y);
    Dag<String> dag = b.build();
    assertEquals(0, dag.inDegree(a));
    assertEquals(1, dag.inDegree(x));
    assertEquals(1, dag.inDegree(y));
  }

  @Test
  void duplicate_connect_doesNotIncreaseInDegree() {
    var b = Dag.<String>builder();
    int a = b.add("a");
    int x = b.add("x");
    b.connect(a, x).connect(a, x);
    assertEquals(1, b.build().inDegree(x));
  }

  @Test
  void get_inDegree_forEachChild_forEachParent_rejectBadId() {
    Dag.Builder<String> b = Dag.builder();
    b.add("only");
    Dag<String> dag = b.build();
    assertThrows(IndexOutOfBoundsException.class, () -> dag.get(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> dag.get(1));
    assertThrows(IndexOutOfBoundsException.class, () -> dag.inDegree(1));
    assertThrows(IndexOutOfBoundsException.class, () -> dag.forEachChild(1, i -> {}));
    assertThrows(IndexOutOfBoundsException.class, () -> dag.forEachParent(1, i -> {}));
  }

  @Test
  void forEachChild_and_forEachParent_visitNeighbors() {
    var b = Dag.<String>builder();
    int a = b.add("a");
    int x = b.add("x");
    int y = b.add("y");
    b.connect(a, x).connect(a, y);
    Dag<String> dag = b.build();

    List<Integer> out = new ArrayList<>();
    dag.forEachChild(a, out::add);
    assertEquals(List.of(x, y), out);

    List<Integer> in = new ArrayList<>();
    dag.forEachParent(x, in::add);
    assertEquals(List.of(a), in);
  }

  @Test
  void iterator_yieldsPayloadsInNodeOrder() {
    var b = Dag.<String>builder();
    b.add("first");
    b.add("second");
    Dag<String> dag = b.build();
    List<String> out = new ArrayList<>();
    for (String s : dag) {
      out.add(s);
    }
    assertEquals(List.of("first", "second"), out);
  }
}

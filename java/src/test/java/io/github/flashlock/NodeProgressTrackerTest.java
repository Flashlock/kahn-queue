package io.github.flashlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NodeProgressTrackerTest {

  @Test
  void emptyDag_initializesNoEntries_andProgressIsZero() {
    Dag<String> dag = Dag.<String>builder().build();
    NodeProgressTracker tracker = new NodeProgressTracker(dag);
    assertEquals(0f, tracker.progress(), 1e-6f);
  }

  @Test
  void constructor_initializesAllNodeIdsToZero() {
    Dag.Builder<String> b = Dag.<String>builder();
    int idA = b.add("a");
    int idB = b.add("b");
    int idC = b.add("c");
    Dag<String> dag = b.build();
    NodeProgressTracker tracker = new NodeProgressTracker(dag);
    assertEquals(0f, tracker.get(idA), 0f);
    assertEquals(0f, tracker.get(idB), 0f);
    assertEquals(0f, tracker.get(idC), 0f);
  }

  @Test
  void putAndGet_roundTrip() {
    Dag.Builder<String> b = Dag.<String>builder();
    int only = b.add("x");
    Dag<String> dag = b.build();
    NodeProgressTracker tracker = new NodeProgressTracker(dag);
    tracker.put(only, 0.5f);
    assertEquals(0.5f, tracker.get(only), 1e-6f);
  }

  @Test
  void put_rejectsValueBelowZero() {
    Dag.Builder<String> b = Dag.<String>builder();
    int id = b.add("x");
    Dag<String> dag = b.build();
    NodeProgressTracker tracker = new NodeProgressTracker(dag);
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> tracker.put(id, -0.01f));
    assertTrue(ex.getMessage().startsWith("Progress must be between 0 and 1."));
  }

  @Test
  void put_rejectsValueAboveOne() {
    Dag.Builder<String> b = Dag.<String>builder();
    int id = b.add("x");
    Dag<String> dag = b.build();
    NodeProgressTracker tracker = new NodeProgressTracker(dag);
    assertThrows(IllegalArgumentException.class, () -> tracker.put(id, 1.01f));
  }

  @Test
  void put_acceptsBoundaryValues() {
    Dag.Builder<String> b = Dag.<String>builder();
    int lo = b.add("lo");
    int hi = b.add("hi");
    Dag<String> dag = b.build();
    NodeProgressTracker tracker = new NodeProgressTracker(dag);
    tracker.put(lo, 0f);
    tracker.put(hi, 1f);
    assertEquals(0f, tracker.get(lo), 0f);
    assertEquals(1f, tracker.get(hi), 0f);
  }

  @Test
  void progress_isAverageAcrossNodes() {
    Dag.Builder<String> b = Dag.<String>builder();
    int n0 = b.add("a");
    int n1 = b.add("b");
    int n2 = b.add("c");
    int n3 = b.add("d");
    Dag<String> dag = b.build();
    NodeProgressTracker tracker = new NodeProgressTracker(dag);
    tracker.put(n0, 0f);
    tracker.put(n1, 0.5f);
    tracker.put(n2, 1f);
    tracker.put(n3, 0.25f);
    assertEquals(0.4375f, tracker.progress(), 1e-6f);
  }

  @Test
  void customMapSupplier_isPopulatedAndUsed() {
    Dag.Builder<String> b = Dag.<String>builder();
    int first = b.add("a");
    int second = b.add("b");
    Dag<String> dag = b.build();
    Map<Integer, Float> backing = new LinkedHashMap<>();
    NodeProgressTracker tracker = new NodeProgressTracker(dag, () -> backing);
    assertEquals(2, backing.size());
    assertEquals(0f, backing.get(first), 0f);
    assertEquals(0f, backing.get(second), 0f);
    tracker.put(first, 1f);
    tracker.put(second, 0f);
    assertEquals(0.5f, tracker.progress(), 1e-6f);
    assertEquals(1f, backing.get(first), 0f);
  }
}

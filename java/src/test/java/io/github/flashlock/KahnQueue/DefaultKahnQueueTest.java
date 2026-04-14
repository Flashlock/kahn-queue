package io.github.flashlock.KahnQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flashlock.Dag;
import io.github.flashlock.utils.StateMachine;
import java.lang.reflect.Field;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DefaultKahnQueueTest {

  @Test
  void emptyDag_readyIdsEmpty_andPopRejectsInvalidId() {
    Dag<String> dag = Dag.<String>builder().build();
    DefaultKahnQueue q = new DefaultKahnQueue(dag);
    assertTrue(q.readyIds().isEmpty());
    assertThrows(IndexOutOfBoundsException.class, () -> q.pop(0));
  }

  @Test
  void readyIds_containsOnlyZeroInDegreeNodes() {
    Dag.Builder<String> b = Dag.<String>builder();
    int root = b.add("root");
    int mid = b.add("mid");
    int leaf = b.add("leaf");
    b.connect(root, mid).connect(mid, leaf);
    Dag<String> dag = b.build();
    DefaultKahnQueue q = new DefaultKahnQueue(dag);
    assertEquals(Set.of(root), q.readyIds());
  }

  @Test
  void readyIds_twoIndependentRoots() {
    Dag.Builder<String> b = Dag.<String>builder();
    int a = b.add("a");
    int c = b.add("c");
    int join = b.add("join");
    b.connect(a, join).connect(c, join);
    Dag<String> dag = b.build();
    DefaultKahnQueue q = new DefaultKahnQueue(dag);
    assertEquals(Set.of(a, c), q.readyIds());
  }

  @Test
  void pop_throwsWhenNodeIsReadyNotActive() {
    Dag.Builder<String> b = Dag.<String>builder();
    int only = b.add("x");
    Dag<String> dag = b.build();
    DefaultKahnQueue q = new DefaultKahnQueue(dag);
    assertEquals(Set.of(only), q.readyIds());
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> q.pop(only));
    assertEquals("Pop failed. Node " + only + " is not active", ex.getMessage());
  }

  @Test
  void pop_throwsForOutOfRangeId() {
    Dag.Builder<String> b = Dag.<String>builder();
    b.add("x");
    Dag<String> dag = b.build();
    DefaultKahnQueue q = new DefaultKahnQueue(dag);
    assertThrows(IndexOutOfBoundsException.class, () -> q.pop(1));
  }

  @Test
  void prune_marksRootAndReachableDescendants() {
    Dag.Builder<String> b = Dag.<String>builder();
    int r = b.add("r");
    int m = b.add("m");
    int l = b.add("l");
    b.connect(r, m).connect(m, l);
    Dag<String> dag = b.build();
    DefaultKahnQueue q = new DefaultKahnQueue(dag);
    assertEquals(Set.of(r, m, l), q.prune(r));
  }

  @Test
  void prune_forkCollectsAllBranches() {
    Dag.Builder<String> b = Dag.<String>builder();
    int root = b.add("root");
    int left = b.add("left");
    int right = b.add("right");
    b.connect(root, left).connect(root, right);
    Dag<String> dag = b.build();
    DefaultKahnQueue q = new DefaultKahnQueue(dag);
    assertEquals(Set.of(root, left, right), q.prune(root));
  }

  @Test
  void prune_removesIdsFromReadySet() {
    Dag.Builder<String> b = Dag.<String>builder();
    int a = b.add("a");
    int c = b.add("c");
    int join = b.add("join");
    b.connect(a, join).connect(c, join);
    Dag<String> dag = b.build();
    DefaultKahnQueue q = new DefaultKahnQueue(dag);
    assertEquals(Set.of(a, c), q.readyIds());
    q.prune(a);
    assertEquals(Set.of(c), q.readyIds());
  }

  @Test
  void prune_secondCallThrows() {
    Dag.Builder<String> b = Dag.<String>builder();
    int r = b.add("r");
    Dag<String> dag = b.build();
    DefaultKahnQueue q = new DefaultKahnQueue(dag);
    assertEquals(Set.of(r), q.prune(r));
    assertTrue(q.readyIds().isEmpty());
    assertThrows(IllegalStateException.class, () -> q.prune(r));
  }

  @Test
  void kahnProgression_popActiveNode_returnsPromotedDependents() throws Exception {
    Dag.Builder<String> b = Dag.<String>builder();
    int root = b.add("root");
    int mid = b.add("mid");
    int leaf = b.add("leaf");
    b.connect(root, mid).connect(mid, leaf);
    Dag<String> dag = b.build();
    DefaultKahnQueue q = new DefaultKahnQueue(dag);

    forceNodeState(q, root, "ACTIVE");
    assertEquals(Set.of(mid), q.pop(root));
    assertEquals(Set.of(leaf), q.pop(mid));
    assertEquals(Set.of(), q.pop(leaf));
    assertTrue(q.readyIds().isEmpty());
  }

  @SuppressWarnings("unchecked")
  private static void forceNodeState(DefaultKahnQueue q, int id, String nodeStateName)
      throws Exception {
    Field nmField = DefaultKahnQueue.class.getDeclaredField("nodeMachines");
    nmField.setAccessible(true);
    Object[] machines = (Object[]) nmField.get(q);
    Object machine = machines[id];
    Field stateField = StateMachine.class.getDeclaredField("state");
    stateField.setAccessible(true);
    Class<? extends Enum<?>> nodeStateClass =
        (Class<? extends Enum<?>>)
            Class.forName("io.github.flashlock.KahnQueue.NodeState");
    Enum<?> state = Enum.valueOf((Class) nodeStateClass, nodeStateName);
    stateField.set(machine, state);
  }
}

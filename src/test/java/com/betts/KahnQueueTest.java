package com.betts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KahnQueueTest {

  @Test
  void create_emptyDag_hasNoActiveIds() {
    KahnQueue<String> q = KahnQueue.create(Dag.<String>builder().build());
    assertTrue(q.activeIds().isEmpty());
  }

  @Test
  void create_onlyZeroInDegreeNodesAreActive() {
    var b = Dag.<String>builder();
    int a = b.add("a");
    int x = b.add("x");
    b.connect(a, x);
    KahnQueue<String> q = KahnQueue.create(b.build());
    assertEquals(Set.of(a), q.activeIds());
  }

  @Test
  void create_unconnectedRoots_allActive() {
    var b = Dag.<String>builder();
    int r0 = b.add("r0");
    int r1 = b.add("r1");
    KahnQueue<String> q = KahnQueue.create(b.build());
    assertEquals(Set.of(r0, r1), q.activeIds());
  }

  @Test
  void pop_linearChain_activatesNextUntilDone() {
    var b = Dag.<String>builder();
    int a = b.add("a");
    int x = b.add("x");
    int y = b.add("y");
    b.connect(a, x).connect(x, y);
    KahnQueue<String> q = KahnQueue.create(b.build());

    assertEquals(List.of(x), q.pop(a));
    assertEquals(Set.of(x), q.activeIds());

    assertEquals(List.of(y), q.pop(x));
    assertEquals(Set.of(y), q.activeIds());

    assertEquals(List.of(), q.pop(y));
    assertTrue(q.activeIds().isEmpty());
  }

  @Test
  void pop_joinNode_activatesAfterBothParents() {
    var b = Dag.<String>builder();
    int a = b.add("a");
    int left = b.add("left");
    int right = b.add("right");
    int join = b.add("join");
    b.connect(a, left).connect(a, right).connect(left, join).connect(right, join);
    KahnQueue<String> q = KahnQueue.create(b.build());

    assertEquals(Set.of(a), q.activeIds());
    assertEquals(Set.of(left, right), new HashSet<>(q.pop(a)));
    assertEquals(Set.of(left, right), q.activeIds());

    q.pop(left);
    assertEquals(Set.of(right), q.activeIds());

    assertEquals(List.of(join), q.pop(right));
    assertEquals(Set.of(join), q.activeIds());
  }

  @Test
  void pop_notActive_throwsIllegalArgumentException() {
    var b = Dag.<String>builder();
    int a = b.add("a");
    int x = b.add("x");
    b.connect(a, x);
    KahnQueue<String> q = KahnQueue.create(b.build());

    assertThrows(IllegalArgumentException.class, () -> q.pop(x));
    assertEquals(Set.of(a), q.activeIds());
  }

  @Test
  void prune_fromStart_removesDownstreamFromScheduling() {
    var b = Dag.<String>builder();
    int a = b.add("a");
    int x = b.add("x");
    int y = b.add("y");
    b.connect(a, x).connect(x, y);
    KahnQueue<String> q = KahnQueue.create(b.build());

    assertEquals(Set.of(a, x, y), new HashSet<>(q.prune(a)));
    assertTrue(q.activeIds().isEmpty());
  }

  @Test
  void prune_midChain_upstreamStaysRunnable() {
    var b = Dag.<String>builder();
    int a = b.add("a");
    int x = b.add("x");
    int y = b.add("y");
    b.connect(a, x).connect(x, y);
    KahnQueue<String> q = KahnQueue.create(b.build());

    assertEquals(Set.of(x, y), new HashSet<>(q.prune(x)));
    assertEquals(Set.of(a), q.activeIds());

    assertEquals(List.of(), q.pop(a));
    assertTrue(q.activeIds().isEmpty());
  }
}

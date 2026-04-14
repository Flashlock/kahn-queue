package io.github.flashlock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flashlock.KahnQueue.DefaultKahnQueue;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class KahnSchedulerTest {

  @Test
  void run_invokesCallbackForEachReadyNode_andRunSkipsWhenFinished() {
    Dag.Builder<String> b = Dag.<String>builder();
    int a = b.add("a");
    int c = b.add("c");
    int join = b.add("join");
    b.connect(a, join).connect(c, join);
    Dag<String> dag = b.build();
    AtomicInteger calls = new AtomicInteger();
    KahnScheduler<String> sched =
        new KahnScheduler<>(dag, (id, s) -> calls.incrementAndGet());
    sched.run();
    assertEquals(2, calls.get());

    Dag.Builder<String> one = Dag.<String>builder();
    int only = one.add("x");
    Dag<String> dagOne = one.build();
    AtomicInteger calls2 = new AtomicInteger();
    KahnScheduler<String> finished =
        new KahnScheduler<>(dagOne, (id, s) -> calls2.incrementAndGet());
    finished.signalFailed(only);
    assertTrue(finished.isFinished());
    finished.run();
    assertEquals(0, calls2.get());
  }

  @Test
  void signalFailed_prunesDescendants_andExcludesRootFromPruned() {
    Dag.Builder<String> b = Dag.<String>builder();
    int r = b.add("r");
    int m = b.add("m");
    int l = b.add("l");
    b.connect(r, m).connect(m, l);
    Dag<String> dag = b.build();
    KahnScheduler<String> sched =
        new KahnScheduler<>(
            dag, (id, s) -> {}, () -> new DefaultKahnQueue(dag), LinkedHashSet::new);
    sched.signalFailed(r);
    KahnScheduler.DagResult result = sched.getResult();
    assertEquals(Set.of(r), result.failed());
    assertEquals(Set.of(m, l), result.pruned());
    assertTrue(sched.isFinished());
  }

  @Test
  void signalFailed_duplicateIgnored() {
    Dag.Builder<String> b = Dag.<String>builder();
    int r = b.add("r");
    int m = b.add("m");
    b.connect(r, m);
    Dag<String> dag = b.build();
    KahnScheduler<String> sched =
        new KahnScheduler<>(
            dag, (id, s) -> {}, () -> new DefaultKahnQueue(dag), LinkedHashSet::new);
    sched.signalFailed(r);
    sched.signalFailed(r);
    KahnScheduler.DagResult result = sched.getResult();
    assertEquals(Set.of(r), result.failed());
    assertEquals(Set.of(m), result.pruned());
  }

  @Test
  void signalComplete_firstPopMayFail_duplicateStillIgnored() {
    Dag.Builder<String> b = Dag.<String>builder();
    int only = b.add("x");
    Dag<String> dag = b.build();
    KahnScheduler<String> sched =
        new KahnScheduler<>(dag, (id, s) -> {});
    assertThrows(IllegalArgumentException.class, () -> sched.signalComplete(only));
    assertDoesNotThrow(() -> sched.signalComplete(only));
    assertEquals(Set.of(only), sched.getResult().completed());
  }

  @Test
  void getResult_returnsUnmodifiableCopies() {
    Dag.Builder<String> b = Dag.<String>builder();
    b.add("x");
    Dag<String> dag = b.build();
    KahnScheduler<String> sched =
        new KahnScheduler<>(
            dag, (id, s) -> {}, () -> new DefaultKahnQueue(dag), LinkedHashSet::new);
    sched.signalFailed(0);
    KahnScheduler.DagResult r = sched.getResult();
    assertThrows(UnsupportedOperationException.class, () -> r.completed().add(0));
    assertThrows(UnsupportedOperationException.class, () -> r.failed().add(0));
    assertThrows(UnsupportedOperationException.class, () -> r.pruned().add(0));
  }
}

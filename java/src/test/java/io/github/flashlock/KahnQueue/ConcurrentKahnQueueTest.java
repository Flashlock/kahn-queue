package io.github.flashlock.KahnQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.flashlock.Dag;
import io.github.flashlock.utils.StateMachine;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConcurrentKahnQueueTest {

  /** Inner loops replace @RepeatedTest so the runner reports one test per scenario. */
  private static final int CONCURRENCY_ROUNDS = 25;

  @Test
  void basics_readyIdsAndPopValidation() {
    Dag<String> empty = Dag.<String>builder().build();
    ConcurrentKahnQueue q0 = new ConcurrentKahnQueue(empty);
    assertTrue(q0.readyIds().isEmpty());
    assertThrows(IndexOutOfBoundsException.class, () -> q0.pop(0));

    Dag.Builder<String> chain = Dag.<String>builder();
    int root = chain.add("root");
    int mid = chain.add("mid");
    int leaf = chain.add("leaf");
    chain.connect(root, mid).connect(mid, leaf);
    Dag<String> dagChain = chain.build();
    ConcurrentKahnQueue qc = new ConcurrentKahnQueue(dagChain);
    assertEquals(Set.of(root), qc.readyIds());

    Dag.Builder<String> join = Dag.<String>builder();
    int a = join.add("a");
    int c = join.add("c");
    int jn = join.add("join");
    join.connect(a, jn).connect(c, jn);
    Dag<String> dagJoin = join.build();
    ConcurrentKahnQueue qj = new ConcurrentKahnQueue(dagJoin);
    assertEquals(Set.of(a, c), qj.readyIds());

    Dag.Builder<String> one = Dag.<String>builder();
    int only = one.add("x");
    Dag<String> dagOne = one.build();
    ConcurrentKahnQueue q1 = new ConcurrentKahnQueue(dagOne);
    assertEquals(Set.of(only), q1.readyIds());
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> q1.pop(only));
    assertTrue(ex.getMessage().contains("not active"), () -> "message: " + ex.getMessage());

    Dag.Builder<String> ob = Dag.<String>builder();
    ob.add("x");
    Dag<String> dagOb = ob.build();
    ConcurrentKahnQueue qo = new ConcurrentKahnQueue(dagOb);
    assertThrows(IndexOutOfBoundsException.class, () -> qo.pop(1));
  }

  @Test
  void prune_collectsReachableInLinearAndForkShapes() {
    Dag.Builder<String> b1 = Dag.<String>builder();
    int r = b1.add("r");
    int m = b1.add("m");
    int l = b1.add("l");
    b1.connect(r, m).connect(m, l);
    ConcurrentKahnQueue q1 = new ConcurrentKahnQueue(b1.build());
    assertEquals(Set.of(r, m, l), q1.prune(r));

    Dag.Builder<String> b2 = Dag.<String>builder();
    int root = b2.add("root");
    int left = b2.add("left");
    int right = b2.add("right");
    b2.connect(root, left).connect(root, right);
    ConcurrentKahnQueue q2 = new ConcurrentKahnQueue(b2.build());
    assertEquals(Set.of(root, left, right), q2.prune(root));
  }

  @Test
  void prune_updatesReadySet() {
    Dag.Builder<String> b = Dag.<String>builder();
    int a = b.add("a");
    int c = b.add("c");
    int join = b.add("join");
    b.connect(a, join).connect(c, join);
    Dag<String> dag = b.build();
    ConcurrentKahnQueue q = new ConcurrentKahnQueue(dag);
    assertEquals(Set.of(a, c), q.readyIds());
    q.prune(a);
    assertEquals(Set.of(c), q.readyIds());
  }

  @Test
  void readyIds_stableUnderSequentialRepeats() {
    Dag.Builder<String> b = Dag.<String>builder();
    int a = b.add("a");
    int c = b.add("c");
    int join = b.add("join");
    b.connect(a, join).connect(c, join);
    Dag<String> dag = b.build();
    ConcurrentKahnQueue q = new ConcurrentKahnQueue(dag);
    Set<Integer> snapshot = q.readyIds();
    for (int i = 0; i < 10_000; i++) {
      assertEquals(snapshot, q.readyIds());
    }
    assertReadyIdsStructuralInvariant(dag, q);
  }

  @Test
  void prune_secondCallThrows() {
    Dag.Builder<String> b = Dag.<String>builder();
    int r = b.add("r");
    Dag<String> dag = b.build();
    ConcurrentKahnQueue q = new ConcurrentKahnQueue(dag);
    assertEquals(Set.of(r), q.prune(r));
    assertTrue(q.readyIds().isEmpty());
    assertThrows(IllegalStateException.class, () -> q.prune(r));
    assertReadyIdsStructuralInvariant(dag, q);
  }

  @Test
  void kahnProgression_popActiveNode_returnsPromotedDependents() throws Exception {
    Dag.Builder<String> b = Dag.<String>builder();
    int root = b.add("root");
    int mid = b.add("mid");
    int leaf = b.add("leaf");
    b.connect(root, mid).connect(mid, leaf);
    Dag<String> dag = b.build();
    ConcurrentKahnQueue q = new ConcurrentKahnQueue(dag);
    forceNodeState(q, root, "ACTIVE");
    assertEquals(Set.of(mid), q.pop(root));
    assertEquals(Set.of(leaf), q.pop(mid));
    assertEquals(Set.of(), q.pop(leaf));
    assertTrue(q.readyIds().isEmpty());
    assertReadyIdsStructuralInvariant(dag, q);
  }

  @Test
  void concurrent_readyIdsStress_readsMatchSnapshot() throws Exception {
    for (int round = 0; round < CONCURRENCY_ROUNDS; round++) {
      runConcurrentReadyIdsStressOnce();
    }
  }

  @Test
  void concurrent_disjointPrune() throws Exception {
    for (int round = 0; round < CONCURRENCY_ROUNDS; round++) {
      runConcurrentDisjointPruneOnce();
    }
  }

  @Test
  void concurrent_popFailuresDoNotMutateReady() throws Exception {
    for (int round = 0; round < CONCURRENCY_ROUNDS; round++) {
      runConcurrentPopFailuresOnce();
    }
  }

  @Test
  void concurrent_sameIdPruneContention() throws Exception {
    for (int round = 0; round < CONCURRENCY_ROUNDS; round++) {
      runConcurrentSameIdPruneManyThreadsOnce();
    }
  }

  @Test
  void concurrent_overlappingPrune() throws Exception {
    for (int round = 0; round < CONCURRENCY_ROUNDS; round++) {
      runConcurrentOverlappingPruneOnce();
    }
  }

  @Test
  void pruneMutation_visibleToOtherThreadAfterFutureGet() throws Exception {
    Dag.Builder<String> b = Dag.<String>builder();
    int left = b.add("left");
    int right = b.add("right");
    Dag<String> dag = b.build();
    ConcurrentKahnQueue q = new ConcurrentKahnQueue(dag);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<Set<Integer>> done = pool.submit(() -> q.prune(left));
      assertEquals(Set.of(left), done.get(10, TimeUnit.SECONDS));
    } finally {
      pool.shutdown();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
    assertEquals(Set.of(right), q.readyIds());
    assertReadyIdsStructuralInvariant(dag, q);
  }

  private static void runConcurrentReadyIdsStressOnce() throws Exception {
    Dag.Builder<String> b = Dag.<String>builder();
    int x = b.add("x");
    int y = b.add("y");
    b.connect(x, y);
    Dag<String> dag = b.build();
    ConcurrentKahnQueue q = new ConcurrentKahnQueue(dag);
    Set<Integer> expected = Set.of(x);
    assertEquals(expected, q.readyIds());
    int threads = 8;
    int iterationsPerThread = 500;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        futures.add(
            pool.submit(
                () -> {
                  for (int i = 0; i < iterationsPerThread; i++) {
                    if (!expected.equals(q.readyIds())) {
                      throw new AssertionError("readyIds drifted during concurrent read");
                    }
                  }
                }));
      }
      for (Future<?> f : futures) {
        f.get();
      }
    } finally {
      pool.shutdown();
      assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
    }
    assertEquals(expected, q.readyIds());
    assertReadyIdsStructuralInvariant(dag, q);
  }

  private static void runConcurrentDisjointPruneOnce() throws Exception {
    Dag.Builder<String> b = Dag.<String>builder();
    int left = b.add("left");
    int right = b.add("right");
    Dag<String> dag = b.build();
    ConcurrentKahnQueue q = new ConcurrentKahnQueue(dag);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<Set<Integer>> f1 = pool.submit(() -> q.prune(left));
      Future<Set<Integer>> f2 = pool.submit(() -> q.prune(right));
      assertEquals(Set.of(left), f1.get());
      assertEquals(Set.of(right), f2.get());
    } finally {
      pool.shutdown();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
    assertTrue(q.readyIds().isEmpty());
    assertReadyIdsStructuralInvariant(dag, q);
  }

  private static void runConcurrentPopFailuresOnce() throws Exception {
    Dag.Builder<String> b = Dag.<String>builder();
    int only = b.add("x");
    Dag<String> dag = b.build();
    ConcurrentKahnQueue q = new ConcurrentKahnQueue(dag);
    Set<Integer> before = q.readyIds();
    int threads = 32;
    AtomicInteger illegalArgumentCount = new AtomicInteger();
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        futures.add(
            pool.submit(
                () -> {
                  try {
                    q.pop(only);
                  } catch (IllegalArgumentException e) {
                    illegalArgumentCount.incrementAndGet();
                  }
                }));
      }
      for (Future<?> f : futures) {
        f.get();
      }
    } finally {
      pool.shutdown();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
    assertEquals(threads, illegalArgumentCount.get());
    assertEquals(before, q.readyIds(), "pop failures must not mutate ready set");
    assertReadyIdsStructuralInvariant(dag, q);
  }

  private static void runConcurrentSameIdPruneManyThreadsOnce() throws Exception {
    Dag.Builder<String> b = Dag.<String>builder();
    int root = b.add("root");
    Dag<String> dag = b.build();
    ConcurrentKahnQueue q = new ConcurrentKahnQueue(dag);
    int threads = 8;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Future<Boolean>> futures = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    q.prune(root);
                    return true;
                  } catch (IllegalStateException e) {
                    return false;
                  }
                }));
      }
      start.countDown();
      int successes = 0;
      int illegalStates = 0;
      for (Future<Boolean> f : futures) {
        try {
          if (Boolean.TRUE.equals(f.get(30, TimeUnit.SECONDS))) {
            successes++;
          } else {
            illegalStates++;
          }
        } catch (ExecutionException e) {
          Throwable c = e.getCause();
          if (c instanceof IllegalStateException) {
            illegalStates++;
          } else {
            throw e;
          }
        }
      }
      assertEquals(1, successes);
      assertEquals(threads - 1, illegalStates);
      assertTrue(q.readyIds().isEmpty());
      assertReadyIdsStructuralInvariant(dag, q);
    } finally {
      pool.shutdown();
      assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
    }
  }

  private static void runConcurrentOverlappingPruneOnce() throws Exception {
    Dag.Builder<String> b = Dag.<String>builder();
    int r = b.add("r");
    int m = b.add("m");
    int l = b.add("l");
    b.connect(r, m).connect(m, l);
    Dag<String> dag = b.build();
    ConcurrentKahnQueue q = new ConcurrentKahnQueue(dag);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<Set<Integer>> f1 =
          pool.submit(
              () -> {
                start.await();
                return q.prune(r);
              });
      Future<Set<Integer>> f2 =
          pool.submit(
              () -> {
                start.await();
                return q.prune(m);
              });
      start.countDown();
      Set<Integer> s1 = getPruneResultOrIllegalState(f1);
      Set<Integer> s2 = getPruneResultOrIllegalState(f2);
      assertReadyIdsStructuralInvariant(dag, q);
      for (int id : q.readyIds()) {
        assertTrue(
            id == r || id == m || id == l,
            () -> "ready id must be a graph node: " + id);
      }
      if (s1 != null && s2 != null) {
        Set<Integer> union = new HashSet<>(s1);
        union.addAll(s2);
        assertEquals(
            Set.of(r, m, l),
            union,
            "if both prunes return a set, together they must cover the three-node chain");
      }
      if (s1 != null && s1.equals(Set.of(r, m, l))) {
        assertTrue(q.readyIds().isEmpty());
      }
      if (s2 != null && s2.equals(Set.of(r, m, l))) {
        assertTrue(q.readyIds().isEmpty());
      }
      boolean anyFullPrune =
          s1 != null && s1.equals(Set.of(r, m, l)) || s2 != null && s2.equals(Set.of(r, m, l));
      if (anyFullPrune) {
        assertTrue(q.readyIds().isEmpty(), "full prune must clear READY nodes");
      }
    } finally {
      pool.shutdown();
      assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
    }
  }

  private static void assertReadyIdsStructuralInvariant(Dag<?> dag, ConcurrentKahnQueue q) {
    for (int id : q.readyIds()) {
      Dag.validateNode(id, dag.size());
    }
  }

  private static Set<Integer> getPruneResultOrIllegalState(Future<Set<Integer>> future)
      throws Exception {
    try {
      return future.get(30, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      fail("timed out waiting for concurrent prune (possible deadlock)", e);
      return null;
    } catch (ExecutionException e) {
      Throwable c = e.getCause();
      if (c instanceof IllegalStateException) {
        return null;
      }
      throw e;
    }
  }

  @SuppressWarnings("unchecked")
  private static void forceNodeState(ConcurrentKahnQueue q, int id, String nodeStateName)
      throws Exception {
    Field nmField = ConcurrentKahnQueue.class.getDeclaredField("nodeMachines");
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

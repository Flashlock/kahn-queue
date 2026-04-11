package com.betts;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Schedules work over a {@link Dag}. Depends on the {@link Dag}
 * passed to {@link #create(Dag)} for the life of the queue.
 */
public class KahnQueue<T> {

  private final Dag<T> dag;
  private final AtomicInteger[] remainingDeps;
  private final Map<Integer, T> activeNodes = new ConcurrentHashMap<>();

  private KahnQueue(Dag<T> dag) {
    this.dag = dag;
    this.remainingDeps = new AtomicInteger[dag.size()];
    for (int i = 0; i < dag.size(); i++) {
      remainingDeps[i] = new AtomicInteger(dag.inDegree(i));
    }
  }

  /**
   * Creates a queue backed by {@code dag}.
   *
   * @throws IllegalGraphException if the graph is non-empty but has no source node (in-degree zero),
   *     e.g. a pure cycle, so nothing can be scheduled first
   */
  public static <T> KahnQueue<T> create(Dag<T> dag) {
    KahnQueue<T> queue = new KahnQueue<>(dag);

    for (int i = 0; i < dag.size(); i++) {
      if (dag.inDegree(i) == 0) {
        queue.activeNodes.put(i, dag.get(i));
      }
    }

    if (dag.size() > 0 && queue.activeNodes.isEmpty()) {
      throw new IllegalGraphException("No source node (in-degree zero); graph may contain a cycle");
    }

    return queue;
  }

  /**
   * Completes an active node; returns ids that became ready.
   *
   * @throws IllegalGraphException if {@code id} is not a valid node in {@code dag}
   * @throws IllegalArgumentException if {@code id} is not active
   */
  public List<Integer> pop(int id) {
    Dag.validateNode(id, dag.size());
    T removed = activeNodes.remove(id);
    if (removed == null) throw new IllegalArgumentException("Node " + id + " is not active");

    List<Integer> newlyActivated = new ArrayList<>();
    dag.targets(id).forEach(targetId -> {
      int remaining = remainingDeps[targetId].decrementAndGet();
      if(remaining == 0) {
        var data = dag.get(targetId);
        activeNodes.put(targetId, data);
        newlyActivated.add(targetId);
      }
    });

    return newlyActivated;
  }

  /**
   * Drops a node and it's downstream from scheduling; returns affected ids.
   *
   * @throws IllegalGraphException if {@code id} is not a valid node in {@code dag}
   */
  public Set<Integer> prune(int id) {
    Dag.validateNode(id, dag.size());
    Set<Integer> removed = new HashSet<>();
    Deque<Integer> stack = new ArrayDeque<>();
    stack.push(id);

    while (!stack.isEmpty()) {
      int node = stack.pop();
      if (activeNodes.remove(node) != null || remainingDeps[node].get() >= 0) {
        removed.add(node);
        dag.targets(node).forEach(stack::push);
        remainingDeps[node].set(-1); // mark as removed
      }
    }

    return removed;
  }

  /** Ids of nodes currently ready to run. */
  public Set<Integer> activeIds() {
    return activeNodes.keySet();
  }
}

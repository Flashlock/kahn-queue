package com.betts;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class DagScheduler<T> {
  public enum Signal {
    COMPLETE,
    FAIL
  }

  private final Dag<T> dag;
  private final KahnQueue<T> queue;
  private final BiConsumer<Integer, DagScheduler<T>> executeNode;

  // Thread-safe state tracking
  private final Set<Integer> completed = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final Set<Integer> failed = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final Set<Integer> pruned = Collections.newSetFromMap(new ConcurrentHashMap<>());

  public DagScheduler(Dag<T> dag, BiConsumer<Integer, DagScheduler<T>> executeNode) {
    this.dag = dag;
    this.queue = KahnQueue.create(dag);
    this.executeNode = executeNode;
  }

  /**
   * Entry point: Finds initial nodes with no dependencies and starts them.
   */
  public synchronized void run() {
    if (isFinished()) return;
    queue.activeIds().forEach(id -> executeNode.accept(id, this));
  }

  /**
   * The callback method for nodes to report their status.
   */
  public void signal(int nodeId, Signal signal) {
    switch (signal) {
      case COMPLETE -> {
        completed.add(nodeId);
        queue.pop(nodeId).forEach(id -> executeNode.accept(id, this));
      }
      case FAIL -> {
        failed.add(nodeId);
        var skipped = queue.prune(nodeId);
        skipped.remove(nodeId); // Don't count the failed node as pruned
        pruned.addAll(skipped);
      }
      default -> throw new IllegalArgumentException("Unsupported Signal: " + signal);
    }
  }

  public boolean isFinished() {
    return completed.size() + failed.size() + pruned.size() == dag.size();
  }

  public DagResult getResult() {
    return new DagResult(
        Set.copyOf(completed),
        Set.copyOf(failed),
        Set.copyOf(pruned)
    );
  }

  public record DagResult(Set<Integer> completed, Set<Integer> failed, Set<Integer> pruned) {}
}
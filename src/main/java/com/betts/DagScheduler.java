package com.betts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class DagScheduler<T> {
  public enum Signal {
    COMPLETE,
    FAIL
  }

  private final Dag<T> dag;
  private final KahnQueue<T> queue;
  private final Consumer<Integer> executeNode;

  // Thread-safe state tracking
  private final Set<Integer> completed = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final Set<Integer> failed = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final Set<Integer> pruned = Collections.newSetFromMap(new ConcurrentHashMap<>());

  private final List<Consumer<DagResult>> onComplete = new ArrayList<>();

  public DagScheduler(Dag<T> dag, Consumer<Integer> executeNode) {
    this.dag = dag;
    this.queue = KahnQueue.create(dag);
    this.executeNode = executeNode;
  }

  public void setOnComplete(Consumer<DagResult> onComplete) {
    this.onComplete.add(onComplete);
  }

  /**
   * Entry point: Finds initial nodes with no dependencies and starts them.
   */
  public synchronized void run() {
    if (isFinished()) return;
    queue.activeIds().forEach(executeNode);
  }

  /**
   * The callback method for nodes to report their status.
   */
  public synchronized void signal(int nodeId, Signal signal) {
    if (signal == Signal.COMPLETE) {
      completed.add(nodeId);
      queue.pop(nodeId).forEach(executeNode);
    } else {
      failed.add(nodeId);
      List<Integer> skipped = queue.prune(nodeId);
      skipped.remove(nodeId); // Don't count the failed node as pruned
      pruned.addAll(skipped);
    }

    if (isFinished()) {
      onAllNodesProcessed();
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

  protected void onAllNodesProcessed() {
    onComplete.forEach(action -> action.accept(getResult()));
  }

  public record DagResult(Set<Integer> completed, Set<Integer> failed, Set<Integer> pruned) {}
}
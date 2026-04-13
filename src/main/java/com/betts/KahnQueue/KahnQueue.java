package com.betts.KahnQueue;

import java.util.Set;

/**
 * Tracks which DAG nodes are runnable (dependencies satisfied, not pruned) and applies completion
 * or pruning.
 *
 * <p>Example:
 * <pre>{@code
 * KahnQueue q;
 * q.readyIds().forEach(id -> { ... });
 *
 * Set<Integer> promotedIds = q.pop(doneId);
 * promotedIds.forEach(id -> { ... });
 *
 * Set<Integer> prunedIds = q.prune(failedId);
 * }</pre>
 */
public interface KahnQueue {
  /**
   * Marks {@code id} completed and returns ids of nodes that became runnable as a result.
   */
  Set<Integer> pop(int id);

  /**
   * Marks {@code id} and its descendants pruned; returns every node id affected.
   */
  Set<Integer> prune(int id);

  /** Node ids currently runnable (zero unsatisfied predecessors, not pruned). */
  Set<Integer> readyIds();
}

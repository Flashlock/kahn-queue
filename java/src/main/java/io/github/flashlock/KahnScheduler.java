package io.github.flashlock;

import io.github.flashlock.KahnQueue.DefaultKahnQueue;
import io.github.flashlock.KahnQueue.KahnQueue;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Runs a DAG by handing ready node ids to {@code executeNode}. When work for a node finishes,
 * report the outcome from the callback, for example:
 *
 * <pre>{@code
 * (id, sched) -> {
 *   if (ok) {
 *     sched.signalComplete(id);
 *   } else {
 *     sched.signalFailed(id);
 *   }
 * }
 * }</pre>
 *
 * @param <T> node payload type
 */
public class KahnScheduler<T> {

  private final Dag<T> dag;
  private final KahnQueue queue;
  private final BiConsumer<Integer, KahnScheduler<T>> executeNode;

  private final Set<Integer> completed;
  private final Set<Integer> failed;
  private final Set<Integer> pruned;

  /**
   * Full constructor: {@code queueBacker} supplies the queue; {@code statsBacker} returns a new set
   * instance for result stats.
   */
  public KahnScheduler(
      Dag<T> dag,
      BiConsumer<Integer, KahnScheduler<T>> executeNode,
      Supplier<KahnQueue> queueBacker,
      Supplier<Set<Integer>> statsBacker
  ) {
    this.dag = dag;
    this.queue = queueBacker.get();
    this.completed = statsBacker.get();
    this.failed = statsBacker.get();
    this.pruned = statsBacker.get();
    this.executeNode = executeNode;
  }

  /** Convenience: {@link DefaultKahnQueue} and {@link LinkedHashSet} for result stats. */
  public KahnScheduler(Dag<T> dag, BiConsumer<Integer, KahnScheduler<T>> executeNode) {
    this(dag, executeNode, () -> new DefaultKahnQueue(dag), LinkedHashSet::new);
  }

  /** Invokes {@code executeNode} for each id the queue reports ready now; no-op if the run is already finished. */
  public void run() {
    if (isFinished()) {
      return;
    }
    queue.readyIds().forEach(id -> executeNode.accept(id, this));
  }

  /** Completes a node and schedules newly ready nodes. Duplicate completion is ignored. */
  public void signalComplete(int nodeId) {
    if (!completed.add(nodeId)) {
      return;
    }
    queue.pop(nodeId).forEach(cid -> executeNode.accept(cid, this));
  }

  /** Marks failure and prunes downstream nodes in the queue; duplicate failure is ignored. */
  public void signalFailed(int nodeId) {
    if (!failed.add(nodeId)) {
      return;
    }

    Set<Integer> removed = queue.prune(nodeId);

    removed.remove(nodeId); // root is failure, not pruned
    pruned.addAll(removed);
  }

  /** Whether every node is completed, failed, or pruned. */
  public boolean isFinished() {
    return completed.size() + failed.size() + pruned.size() == dag.size();
  }

  /** Copy of outcome sets (may still change until the run is finished). */
  public DagResult getResult() {
    return new DagResult(
        Set.copyOf(completed),
        Set.copyOf(failed),
        Set.copyOf(pruned));
  }

  /**
   * Outcomes: succeeded, explicitly failed roots, and nodes dropped by pruning after a failure.
   */
  public record DagResult(Set<Integer> completed, Set<Integer> failed, Set<Integer> pruned) {}
}

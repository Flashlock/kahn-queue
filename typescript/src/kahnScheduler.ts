import { KahnQueue } from "./kahnQueue/kahnQueue.js";
import type { Dag } from "./dag.js";

export type DagResult = {
  readonly completed: ReadonlySet<number>;
  readonly failed: ReadonlySet<number>;
  readonly pruned: ReadonlySet<number>;
};

/**
 * Runs a DAG by handing ready node ids to `executeNode`. When work for a node finishes,
 * report the outcome from the callback, for example:
 *
 * @example
 * ```ts
 * (id, sched) => {
 * if (ok) {
 * sched.signalComplete(id);
 * } else {
 * sched.signalFailed(id);
 * }
 * }
 * ```
 *
 * @typeParam T node payload type
 */
export class KahnScheduler<T> {
  readonly #dag: Dag<T>;
  readonly #queue: KahnQueue;
  readonly #executeNode: (id: number, sched: KahnScheduler<T>) => void;

  readonly #completed: Set<number> = new Set();
  readonly #failed: Set<number> = new Set();
  readonly #pruned: Set<number> = new Set();

  private constructor(dag: Dag<T>, executeNode: (id: number, sched: KahnScheduler<T>) => void) {
    this.#dag = dag;
    this.#queue = new KahnQueue(dag);
    this.#executeNode = executeNode;
  }

  /**
   * Full factory: mirrors the Java "full constructor" shape (queue + stats backers), but in
   * TypeScript we only expose one queue implementation.
   */
  static create<T>(
    dag: Dag<T>,
    executeNode: (id: number, sched: KahnScheduler<T>) => void,
  ): KahnScheduler<T> {
    return new KahnScheduler(dag, executeNode);
  }

  /** Invokes `executeNode` for each id the queue reports ready now; no-op if the run is already finished. */
  run(): void {
    if (this.isFinished()) return;
    for (const id of this.#queue.readyIds()) {
      this.#executeNode(id, this);
    }
  }

  /** Completes a node and schedules newly ready nodes. Duplicate completion is ignored. */
  signalComplete(nodeId: number): void {
    if (this.#completed.has(nodeId)) return;
    this.#completed.add(nodeId);
    for (const cid of this.#queue.pop(nodeId)) {
      this.#executeNode(cid, this);
    }
  }

  /** Marks failure and prunes downstream nodes in the queue; duplicate failure is ignored. */
  signalFailed(nodeId: number): void {
    if (this.#failed.has(nodeId)) return;
    this.#failed.add(nodeId);

    const removed = this.#queue.prune(nodeId);
    for (const id of removed) {
      if (id !== nodeId) {
        this.#pruned.add(id);
      }
    }
  }

  /** Whether every node is completed, failed, or pruned. */
  isFinished(): boolean {
    return this.#completed.size + this.#failed.size + this.#pruned.size === this.#dag.size;
  }

  /** Copy of outcome sets (may still change until the run is finished). */
  getResult(): DagResult {
    return Object.freeze({
      completed: new Set(this.#completed),
      failed: new Set(this.#failed),
      pruned: new Set(this.#pruned),
    });
  }
}

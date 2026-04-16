import { Dag } from "../dag.js";
import { NodeMachine } from "./nodeMachine.js";

/**
 * Tracks which DAG nodes are runnable (dependencies satisfied, not pruned) and applies completion
 * or pruning.
 *
 * @example
 * ```ts
 * const q = new KahnQueue(dag);
 * for (const id of q.peek()) {
 *   // ...
 * }
 *
 * const promotedIds = q.pop(doneId);
 * for (const id of promotedIds) {
 *   // ...
 * }
 *
 * const prunedIds = q.prune(failedId);
 * ```
 */
export class KahnQueue {
  readonly #dag: Dag<unknown>;
  readonly #nodeMachines: ReadonlyArray<NodeMachine>;

  /** Builds a queue whose readiness matches `dag`. */
  constructor(dag: Dag<unknown>) {
    this.#dag = dag;
    const machines: Array<NodeMachine> = new Array(dag.size);
    for (let i = 0; i < dag.size; i++) {
      machines[i] = NodeMachine.create(i, dag.inDegree(i));
    }
    this.#nodeMachines = machines;
  }

  /**
   * Marks `id` completed and returns ids of nodes that became runnable as a result.
   */
  pop(id: number): ReadonlySet<number> {
    Dag.validateNode(id, this.#dag.size);

    const machine = this.#nodeMachines[id]!;
    if (!machine.is("ACTIVE")) {
      throw new Error(`Pop failed. Node ${id} is not active`);
    }

    machine.transition("COMPLETE");

    const promoted = new Set<number>();
    for (const childId of this.#dag.targets(id)) {
      const child = this.#nodeMachines[childId]!;
      child.decrement();
      if (child.canTransition("ACTIVE")) {
        child.transition("ACTIVE");
        promoted.add(childId);
      }
    }
    return promoted;
  }

  /**
   * Marks `id` and its descendants pruned; returns every node id affected.
   */
  prune(id: number): ReadonlySet<number> {
    Dag.validateNode(id, this.#dag.size);

    const affected = new Set<number>();
    const stack: Array<number> = [id];

    while (stack.length > 0) {
      const curr = stack.pop()!;
      const machine = this.#nodeMachines[curr]!;
      machine.transition("PRUNED");
      affected.add(curr);
      for (const child of this.#dag.targets(curr)) stack.push(child);
    }

    return affected;
  }

  /** Node ids currently runnable (zero unsatisfied predecessors, not pruned). */
  peek(): ReadonlySet<number> {
    const ready = new Set<number>();
    for (const m of this.#nodeMachines) {
      if (m.is("READY")) ready.add(m.id);
    }
    return ready;
  }
}

import type { Dag } from "./dag.js";

/**
 * Per-node progress in `[0, 1]` for a DAG.
 */
export class NodeProgressTracker {
  readonly #tracker: Map<number, number>;

  /**
   * Full constructor: `trackerBacker` supplies the progress map.
   */
  constructor(dag: Dag<unknown>, trackerBacker: () => Map<number, number> = () => new Map()) {
    this.#tracker = trackerBacker();
    for (let i = 0; i < dag.size; i++) {
      this.#tracker.set(i, 0);
    }
  }

  /** Sets progress for `id` (values in `[0, 1]`). */
  put(id: number, value: number): void {
    if (value < 0 || value > 1) {
      throw new RangeError(`Progress must be between 0 and 1. ${id} : ${value}`);
    }
    this.#tracker.set(id, value);
  }

  /** Progress for `id`. */
  get(id: number): number {
    return this.#tracker.get(id) ?? 0;
  }

  /** Aggregate progress across nodes. */
  get progress(): number {
    if (this.#tracker.size === 0) return 0;

    let sum = 0;
    for (const v of this.#tracker.values()) {
      sum += v;
    }
    return sum / this.#tracker.size;
  }
}

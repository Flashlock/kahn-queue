import { IllegalGraphException } from "./illegalGraphException.js";

type Node<T> = { id: number; data: T };

/**
 * Immutable directed graph of nodes with integer ids and typed payloads. Obtain instances via the
 * static `Dag.builder()` factory; edges go from source to target.
 *
 * @typeParam T payload at each node
 */
export class Dag<T> implements Iterable<T> {
  readonly #nodes: readonly Node<T>[];
  readonly #adjacency: readonly ReadonlySet<number>[];
  readonly #reverseAdjacency: readonly ReadonlySet<number>[];

  private constructor(
    nodes: readonly Node<T>[],
    adjacency: readonly ReadonlySet<number>[],
    reverseAdjacency: readonly ReadonlySet<number>[],
  ) {
    this.#nodes = nodes;
    this.#adjacency = adjacency;
    this.#reverseAdjacency = reverseAdjacency;
  }

  /** Number of nodes (ids are `0 .. size-1`). */
  get size(): number {
    return this.#nodes.length;
  }

  /** Payload for node `id`. */
  get(id: number): T {
    Dag.validateNode(id, this.#nodes.length);
    return this.#nodes[id]!.data;
  }

  /** Count of incoming edges to `id`. */
  inDegree(id: number): number {
    Dag.validateNode(id, this.#nodes.length);
    return this.#reverseAdjacency[id]!.size;
  }

  /** Count of outgoing edges from `id`. */
  outDegree(id: number): number {
    Dag.validateNode(id, this.#nodes.length);
    return this.#adjacency[id]!.size;
  }

  /** Successor ids of `id`. */
  targets(id: number): readonly number[] {
    Dag.validateNode(id, this.#nodes.length);
    return Array.from(this.#adjacency[id]!);
  }

  /** Predecessor ids of `id`. */
  sources(id: number): readonly number[] {
    Dag.validateNode(id, this.#nodes.length);
    return Array.from(this.#reverseAdjacency[id]!);
  }

  /** Returns a new mutable `Builder`. */
  static builder<T>(): Dag.Builder<T> {
    return new Dag.Builder<T>();
  }

  /**
   * Ensures `id` is valid for a graph of `size` nodes.
   *
   * @throws RangeError if out of range
   */
  static validateNode(id: number, size: number): void {
    if (!Number.isInteger(id)) {
      throw new RangeError(`Invalid node id: ${String(id)}`);
    }
    if (id < 0 || id >= size) {
      throw new RangeError(`Invalid node id: ${id}`);
    }
  }

  /** Payloads in id order; use `targets` / `sources` for edge endpoints. */
  *[Symbol.iterator](): Iterator<T> {
    for (const node of this.#nodes) {
      yield node.data;
    }
  }
}

export namespace Dag {
  /**
   * Mutable graph builder.
   *
   * @typeParam T node payload type
   */
  export class Builder<T> {
    readonly #nodes: Node<T>[] = [];
    readonly #adjacency: Set<number>[] = [];
    readonly #reverseAdjacency: Set<number>[] = [];

    /** Adds a node; returns its id for passing to `connect`. */
    add(data: T): number {
      const id = this.#nodes.length;
      this.#nodes.push({ id, data });
      this.#adjacency.push(new Set());
      this.#reverseAdjacency.push(new Set());
      return id;
    }

    /**
     * Directed edge `source` → `target` (ignored if duplicate).
     *
     * @throws IllegalGraphException if `source === target`
     */
    connect(source: number, target: number): this {
      Dag.validateNode(source, this.#nodes.length);
      Dag.validateNode(target, this.#nodes.length);

      if (source === target) {
        throw new IllegalGraphException(`Self-loop not allowed: ${source}`);
      }

      const edges = this.#adjacency[source]!;
      if (!edges.has(target)) {
        edges.add(target);
        this.#reverseAdjacency[target]!.add(source);
      }
      return this;
    }

    /** Build the graph. */
    build(): Dag<T> {
      this.#cycleCheck();

      const nodes = Object.freeze([...this.#nodes]);
      const adjacency = Object.freeze(this.#adjacency.map((s) => Object.freeze(new Set(s))));
      const reverseAdjacency = Object.freeze(
        this.#reverseAdjacency.map((s) => Object.freeze(new Set(s))),
      );

      return new (Dag as any)(nodes, adjacency, reverseAdjacency);
    }

    #cycleCheck(): void {
      const n = this.#nodes.length;
      if (n === 0) return;

      const inDegree = this.#reverseAdjacency.map((s) => s.size);
      const ready: number[] = [];

      for (let i = 0; i < n; i++) {
        if (inDegree[i] === 0) ready.push(i);
      }

      let processed = 0;
      while (ready.length > 0) {
        const u = ready.shift()!;
        processed++;
        for (const v of this.#adjacency[u]!) {
          inDegree[v]--;
          if (inDegree[v] === 0) ready.push(v);
        }
      }

      if (processed !== n) {
        throw new IllegalGraphException("Graph contains a directed cycle");
      }
    }
  }
}

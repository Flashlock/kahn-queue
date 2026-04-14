import { Dag, IllegalGraphException } from "@flashlock/kahn-queue";
import type { Edge, Node } from "@xyflow/react";

export type BuiltDag = {
  dag: Dag<string>;
  /** `dag` node id `i` came from this React Flow node id. */
  indexToRfId: string[];
};

/**
 * Maps stable integer ids 0..n-1 (sorted by RF node id), then adds edges (source → target).
 */
export function buildDagFromFlow(nodes: Node[], edges: Edge[]): BuiltDag {
  if (nodes.length === 0) {
    return { dag: Dag.builder<string>().build(), indexToRfId: [] };
  }

  const ordered = [...nodes].sort((a, b) => a.id.localeCompare(b.id));
  const idToIndex = new Map<string, number>();
  const b = Dag.builder<string>();
  for (const n of ordered) {
    const label = (n.data as { label?: string } | undefined)?.label ?? n.id;
    const idx = b.add(String(label));
    idToIndex.set(n.id, idx);
  }

  for (const e of edges) {
    const s = idToIndex.get(e.source);
    const t = idToIndex.get(e.target);
    if (s === undefined || t === undefined) continue;
    try {
      b.connect(s, t);
    } catch (err) {
      if (err instanceof IllegalGraphException) {
        throw new Error(err.message);
      }
      throw err;
    }
  }

  try {
    const dag = b.build();
    return { dag, indexToRfId: ordered.map((n) => n.id) };
  } catch (err) {
    if (err instanceof IllegalGraphException) {
      throw new Error(err.message);
    }
    throw err;
  }
}

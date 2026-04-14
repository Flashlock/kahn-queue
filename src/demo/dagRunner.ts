import type { Dag } from "@flashlock/kahn-queue";

/**
 * Runs the DAG in Kahn layers: each wave is all nodes with in-degree zero among those not yet finished
 * (parallel “ready” set). Invokes `onWave` once per wave; await inside to animate highlights.
 */
export async function runDagInWaves(
  dag: Dag<string>,
  onWave: (ids: number[]) => Promise<void>,
): Promise<void> {
  const inDeg = Array.from({ length: dag.size }, (_, i) => dag.inDegree(i));
  const done = new Set<number>();

  while (done.size < dag.size) {
    const ready: number[] = [];
    for (let i = 0; i < dag.size; i++) {
      if (!done.has(i) && inDeg[i] === 0) ready.push(i);
    }
    if (ready.length === 0) {
      throw new Error("Scheduler stuck — cycle or inconsistent graph.");
    }
    await onWave(ready);
    for (const id of ready) {
      done.add(id);
      for (const t of dag.targets(id)) {
        inDeg[t]--;
      }
    }
  }
}

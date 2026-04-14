# kahn-queue (TypeScript)

## Getting started

- **TypeScript:** **~6** (`devDependencies` in `package.json`).
- **Tests:** `cd typescript` and `npm test` (Vitest).
- **Build:** `npm run build` (tsup → `dist/`).
- **Import:** `./dist/index.js` (after build), or your bundler’s alias to that entry.

## Pieces

| Piece | Role |
|--------|------|
| `Dag` / `Dag.Builder` | Immutable DAG: `Dag.builder()`, `add`, `connect`, `build()`. |
| `KahnScheduler` | Drives execution: `run`, `signalComplete` / `signalFailed`; `getResult()` returns `DagResult` (sets of ids). |
| `KahnQueue` | Single queue implementation backing the scheduler (see source for `pop` / `prune` / `readyIds`). |
| `IllegalGraphException` | Thrown for invalid graphs (e.g. self-loop or cycle at `build()`). |
| `NodeProgressTracker` | Optional per-node progress in `[0, 1]`; not required for scheduling. |

## Example

Single-threaded use: call `run()` once, then drive completion from your callback (e.g. Temporal-style workflow logic).

```ts
import { Dag, KahnScheduler } from "./dist/index.js";

const b = Dag.builder<string>();
const lint = b.add("lint");
const compile = b.add("compile");
const test = b.add("test");
b.connect(lint, compile).connect(compile, test);
const dag = b.build();

const sched = KahnScheduler.fromDag(dag, (id, s) => {
  try {
    runStep(dag.get(id));
    s.signalComplete(id);
  } catch {
    s.signalFailed(id);
  }
});
sched.run();
const result = sched.getResult();
```

`KahnScheduler.fromDag` / `create` wires a `KahnQueue` for you.

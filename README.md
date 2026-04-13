# KahnQueue
**License:** [The Unlicense](LICENSE) — public domain; copy or change it however you like, no attribution required.

This came from orchestrating **Temporal** workflows for **interdependent AI agents**—e.g. one agent can’t start until several upstream agents finish, while independent branches run **concurrently**. Same pattern **build systems** use: model work as a **dependency graph** and resolve order with **Kahn’s algorithm** (`KahnQueue`) instead of hard-coding a sequence.

## What it does

**Run async work in DAG dependency order.** Each ready node id is passed to your callback together with the `DagScheduler`. When work for that node finishes, call **`signalComplete(id)`** or **`signalFailed(id)`** on that scheduler. **`signalFailed`** prunes the node and its descendants in the queue; pruned ids appear in **`DagResult.pruned()`** (the failed root itself is not counted as pruned).

## Pieces

| Piece | Role |
|--------|------|
| `Dag<T>` | Immutable DAG: build with `Dag.builder()`, add nodes and `connect(source, target)`. |
| `DagScheduler<T>` | Drives execution: `run()`, then `signalComplete` / `signalFailed`; `getResult()` returns `DagResult` (completed / failed / pruned ids). |
| `KahnQueue` | Pluggable backing queue: **`DefaultKahnQueue`** (single-threaded updates) or **`ConcurrentKahnQueue`** (concurrent `pop` / `prune`). |
| `IllegalGraphException` | Thrown for invalid graphs (e.g. self-loop or cycle at `build()`). |
| `NodeProgressTracker` | Optional per-node progress in `[0, 1]` for UI; not required for scheduling. |

## Examples

`connect(source, target)` means *target* waits for *source*. Building the graph can throw `IllegalGraphException` (e.g. cycle or self-loop).

### Single-threaded (Temporal-style)

Same process runs the workflow logic: you kick the scheduler once, then block or await until the DAG run finishes—similar to a Temporal workflow awaiting activity futures.

```java
import com.betts.*;

var builder = Dag.<String>builder();
int lint = builder.add("lint");
int compile = builder.add("compile");
int test = builder.add("test");
builder.connect(lint, compile);
builder.connect(compile, test);
Dag<String> dag = builder.build();

var scheduler = new DagScheduler<>(dag, (id, sched) -> {
  try {
    runStep(dag.get(id)); // e.g. call a Temporal activity stub synchronously from the workflow
    sched.signalComplete(id);
  } catch (Throwable t) {
    sched.signalFailed(id);
  }
});
scheduler.run();
// Workflow.await(scheduler::isFinished);
DagScheduler.DagResult result = scheduler.getResult();
```

The two-arg `DagScheduler` constructor uses a single-threaded `DefaultKahnQueue` under the hood.

### Concurrent (thread pool)

Use the four-arg constructor with `ConcurrentKahnQueue` when ready nodes are executed on a pool; completions can arrive in any order. The last argument supplies the `Set` instances used for completed / failed / pruned ids—use a **concurrent** set (e.g. `ConcurrentHashMap::newKeySet`) when those sets are updated from multiple threads; the two-arg constructor uses `LinkedHashSet` and is intended for single-threaded scheduling.

```java
import com.betts.*;
import com.betts.KahnQueue.ConcurrentKahnQueue;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

ExecutorService pool = Executors.newFixedThreadPool(4);
try {
  var builder = Dag.<String>builder();
  int lint = builder.add("lint");
  int compile = builder.add("compile");
  int test = builder.add("test");
  builder.connect(lint, compile);
  builder.connect(compile, test);
  Dag<String> dag = builder.build();

  var scheduler = new DagScheduler<>(
      dag,
      (id, sched) -> pool.submit(() -> {
        try {
          runStep(dag.get(id));
          sched.signalComplete(id);
        } catch (Throwable t) {
          sched.signalFailed(id);
        }
      }),
      () -> new ConcurrentKahnQueue(dag),
      ConcurrentHashMap::newKeySet);

  scheduler.run();
  DagScheduler.DagResult result = scheduler.getResult();
} finally {
  pool.shutdown();
}
```

# KahnQueue
**License:** [The Unlicense](LICENSE) — public domain; copy or change it however you like, no attribution required.

This came from orchestrating **Temporal** workflows for **interdependent AI agents**—e.g. one agent can’t start until several upstream agents finish, while independent branches run **concurrently**. Same pattern **build systems** use: model work as a **dependency graph** and resolve order with **Kahn’s algorithm** (`KahnQueue`) instead of hard-coding a sequence.

## What it does

**Run async work in DAG dependency order.** Each ready node gets a callback; when work finishes you **`signal(id, COMPLETE)`** or **`FAIL`**. **`FAIL`** skips downstream nodes (**pruned** in the result).

## Pieces

`Dag<T>` (immutable graph + builder), `DagScheduler<T>` (orchestrates; owns a `KahnQueue` under the hood), `IllegalGraphException` for bad graphs (e.g. cycle). `NodeProgressTracker` is optional UI progress, not core scheduling.

## Example

```java
var builder = Dag.<String>builder();
builder.add("lint");
int compile = b.add("compile"), test = b.add("test");
builder.connect(compile, test);
Dag<String> dag = b.build();

var scheduler = new DagScheduler<>(dag, (id, s) -> pool.submit(() -> {
  try { 
    work(dag.get(id)); 
    s.signal(id, DagScheduler.Signal.COMPLETE); 
  }
  catch (Throwable t) { 
    s.signal(id, DagScheduler.Signal.FAIL); 
  }
}));
scheduler.run();
// await s.isFinished() — e.g. Temporal: Workflow.await(scheduler::isFinished)
var r = scheduler.getResult(); // completed / failed / pruned ids
```

`connect(source, target)` means *target* waits for *source*.

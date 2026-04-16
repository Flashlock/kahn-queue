# kahn-queue (Java)

[![Maven Central](https://img.shields.io/maven-central/v/io.github.flashlock/kahn-queue)](https://central.sonatype.com/artifact/io.github.flashlock/kahn-queue)
[![mvnrepository](https://img.shields.io/badge/mvnrepository-kahn--queue-437291?logo=apachemaven)](https://mvnrepository.com/artifact/io.github.flashlock/kahn-queue)

## Getting started

- **JDK:** **21** (LTS), declared in the toolchain in `build.gradle.kts`.
- **Gradle:** **9.2.0** via the wrapper in `gradle/wrapper/gradle-wrapper.properties`.
- **Tests:** From the repo root, `make test-java`, or `cd java` and `./gradlew test`.
- **Build JARs:** `make build-java` or `./gradlew assemble` (main, sources, Javadoc).

## Pieces

| Piece | Role |
|--------|------|
| `Dag<T>` | Immutable DAG: build with `Dag.builder()`, add nodes and `connect(source, target)`. |
| `KahnScheduler<T>` | Drives execution: `run()`, then `signalComplete` / `signalFailed`; `getResult()` returns `DagResult` (completed / failed / pruned ids). |
| `KahnQueue` | Pluggable backing queue: **`DefaultKahnQueue`** (single-threaded updates) or **`ConcurrentKahnQueue`** (concurrent `pop` / `prune`). |
| `IllegalGraphException` | Thrown for invalid graphs (e.g. self-loop or cycle at `build()`). |
| `NodeProgressTracker` | Optional per-node progress in `[0, 1]` for UI; not required for scheduling. |

## Examples

### Single-threaded (Temporal-style)

Same process runs the workflow logic: you kick the scheduler once, then block or await until the DAG run finishes—similar to a Temporal workflow awaiting activity futures.

```java
var builder = Dag.<String>builder();
int lint = builder.add("lint");
int compile = builder.add("compile");
int test = builder.add("test");

builder
  .connect(lint, compile)
  .connect(compile, test);

Dag<String> dag = builder.build();

var scheduler = new KahnScheduler<>(dag, (id, sched) -> {
  try {
    runStep(dag.get(id)); // e.g. Async.procedure(() -> run());
    sched.signalComplete(id);
  } catch (Throwable t) {
    sched.signalFailed(id);
  }
});
scheduler.run();

// Workflow.await(scheduler::isFinished);
KahnScheduler.DagResult result = scheduler.getResult();
```

The two-arg `KahnScheduler` constructor uses a single-threaded `DefaultKahnQueue` under the hood.

### Concurrent (thread pool)

Use the `ConcurrentKahnQueue`-backed scheduler when work runs concurrently. Ensure any shared result-tracking collections you provide are safe to update from multiple threads.

```java
import KahnQueue.io.github.flashlock.ConcurrentKahnQueue;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

ExecutorService pool = Executors.newFixedThreadPool(4);
try{
var builder = Dag.<String>builder();
int lint = builder.add("lint");
int compile = builder.add("compile");
int test = builder.add("test");

builder
  .connect(lint, compile)
  .connect(compile, test);

Dag<String> dag = builder.build();

var scheduler = new KahnScheduler<>(
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
    ConcurrentHashMap::newKeySet
  );

  scheduler.run();

KahnScheduler.DagResult result = scheduler.getResult();
}finally{
    pool.shutdown();
}
```

### Manual KahnQueue

```java
import io.github.flashlock.Dag;
import io.github.flashlock.KahnQueue.DefaultKahnQueue;

import java.util.ArrayDeque;

var builder = Dag.<String>builder();
int lint = builder.add("lint");
int compile = builder.add("compile");
int test = builder.add("test");

builder
  .connect(lint, compile)
  .connect(compile, test);

Dag<String> dag = builder.build();

var q = new DefaultKahnQueue(dag);

var ready = new ArrayDeque<>(q.peek());

while (!ready.isEmpty()) {
  int id = ready.removeFirst();

  // do work for `id` (e.g. runStep(dag.get(id)))

  q.pop(id).forEach(ready::add);

  // If a node fails, you can prune it (and its descendants):
  // q.prune(id);
}
```

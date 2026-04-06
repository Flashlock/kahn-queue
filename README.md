# kahn-queue

Small Java library: an immutable [`Dag`](src/main/java/com/betts/Dag.java) plus a [`KahnQueue`](src/main/java/com/betts/KahnQueue.java) for dependency-style scheduling (nodes become ready when their predecessors are finished or pruned).

Package: **`com.betts`** (matches the Gradle `group`).

**Author:** [Austin Betts](https://github.com/austinbetts)  
**License:** [The Unlicense](LICENSE) — public domain; copy or change it however you like, no attribution required.

## Build & test

```bash
./gradlew build
```

Requires a JDK (Gradle wrapper included).

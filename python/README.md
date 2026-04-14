# kahn-queue (Python)

## Getting started

- **Python:** **3.10+** recommended (venv + `requirements-dev.txt`; no version pinned in-repo).
- **Tests:** `make test-python`, or `cd python`, create `.venv`, install `requirements-dev.txt`, then `pytest`.

## Pieces

| Piece | Role |
|--------|------|
| `Dag` / `Dag.builder()` | Immutable DAG: `add`, `connect`, `build`. |
| `KahnScheduler` | Drives execution: `run`, `signal_complete` / `signal_failed`; `get_result()` returns `DagResult` (frozensets of ids). |
| `KahnQueue` / `DefaultKahnQueue` / `ConcurrentKahnQueue` | **`DefaultKahnQueue`** for single-threaded updates; **`ConcurrentKahnQueue`** when `pop` / `prune` run from many threads. |
| `IllegalGraphException` | Raised for invalid graphs (e.g. self-loop or cycle at `build()`). |
| `NodeProgressTracker` | Optional per-node progress in `[0, 1]`; not required for scheduling. |

## Examples

### Single-threaded (Temporal-style)

```python
from dag import Dag
from scheduler import KahnScheduler

b = Dag.builder()
lint = b.add("lint")
comp = b.add("compile")
test = b.add("test")
b.connect(lint, comp).connect(comp, test)
dag = b.build()

def execute_node(node_id: int, sched: KahnScheduler[str]) -> None:
    try:
        run_step(dag[node_id])
        sched.signal_complete(node_id)
    except Exception:
        sched.signal_failed(node_id)

sched = KahnScheduler(dag, execute_node)
sched.run()
result = sched.get_result()
```

The two-arg `KahnScheduler(dag, execute_node)` uses `DefaultKahnQueue` when `queue` is omitted.

### Concurrent (`ConcurrentKahnQueue`)

```python
from kahnQueue.concurrent_kahn_queue import ConcurrentKahnQueue
from scheduler import KahnScheduler

# … build dag, define execute_node as in the previous example …

sched = KahnScheduler(
    dag,
    execute_node,
    queue=ConcurrentKahnQueue(dag),
)
sched.run()
result = sched.get_result()
```

Use a thread-safe queue when `execute_node` is invoked from many threads; keep any extra shared result structures thread-safe as well.

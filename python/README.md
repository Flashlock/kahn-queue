# kahn-queue (Python)

[![PyPI](https://img.shields.io/pypi/v/kahn-queue?label=PyPI)](https://pypi.org/project/kahn-queue/)

## Getting started

- **Python:** **3.10+** recommended (venv + `requirements-dev.txt`; no version pinned in-repo).
- **Tests:** `make test-python`, or `cd python`, create `.venv`, install `requirements-dev.txt`, then `pytest`.
- **Imports:** Examples assume **`src`** is on `PYTHONPATH` (the test suite does this via `tests/conftest.py`). From the repo: `PYTHONPATH=src` or `pip install -e .` once packaging is configured.

## Pieces

| Piece | Role |
|--------|------|
| `Dag` / `Dag.builder()` | Immutable DAG: `add`, `connect`, `build()`. |
| `KahnScheduler` | `run()`, `signal_complete`, `signal_failed`; `is_finished` (property); `get_result()` → `DagResult`. |
| `DagResult` | `completed`, `failed`, `pruned` — each a `frozenset` of node ids. |
| `KahnQueue` / `DefaultKahnQueue` / `ConcurrentKahnQueue` | **`DefaultKahnQueue`** when `queue` is omitted; pass **`ConcurrentKahnQueue(dag)`** for concurrent `pop` / `prune`. |
| `IllegalGraphException` | Invalid graphs (self-loop, cycle at `build()`, etc.). |
| `NodeProgressTracker` | Optional per-node progress in `[0, 1]` (`tracker` module); not required for scheduling. |

## Examples

### Single threaded queue

```python
from dag import Dag
from scheduler import KahnScheduler

b = Dag.builder()
root = b.add("lint")
mid = b.add("compile")
leaf = b.add("test")
b.connect(root, mid).connect(mid, leaf)
dag = b.build()


def execute_node(node_id: int, sched: KahnScheduler[str]) -> None:
    try:
        run_step(dag[node_id])  # your step
        sched.signal_complete(node_id)
    except Exception:
        sched.signal_failed(node_id)

sched = KahnScheduler(dag, execute_node)
sched.run()
```

### Concurrent queue

```python
from dag import Dag
from kahnQueue.concurrent_kahn_queue import ConcurrentKahnQueue
from scheduler import KahnScheduler

b = Dag.builder()
a = b.add("a")
c = b.add("c")
jn = b.add("join")
b.connect(a, jn).connect(c, jn)
dag = b.build()


def execute_node(node_id: int, sched: KahnScheduler[str]) -> None:
    ...

sched = KahnScheduler(dag, execute_node, queue=ConcurrentKahnQueue(dag))
sched.run()
```

### Result

```python
result = sched.get_result()
# result.completed, result.failed, result.pruned — frozensets of ids
done = sched.is_finished
```

### Manual KahnQueue

```python
from dag import Dag
from kahnQueue.default_kahn_queue import DefaultKahnQueue

b = Dag.builder()
lint = b.add("lint")
compile = b.add("compile")
test = b.add("test")
b.connect(lint, compile).connect(compile, test)
dag = b.build()

q = DefaultKahnQueue(dag)

ready = list(q.peek())

while ready:
    id_ = ready.pop(0)

    # do work for `id_` (e.g. run_step(dag[id_]))

    promoted = q.pop(id_)
    ready.extend(promoted)

    # If a node fails, you can prune it (and its descendants):
    # q.prune(id_)
```

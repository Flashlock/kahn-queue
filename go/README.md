# kahn-queue (Go)

## Getting started

- **Go:** **1.26.2** in `go.mod`.
- **Tests:** `make test-go`, or `cd go` and `go test ./...`.

## Pieces

| Piece | Role |
|--------|------|
| `dag.Dag` / `dag.Builder` | Immutable DAG: `dag.NewBuilder`, `Add`, `Connect`, `Build`. |
| `kahnqueue.Scheduler` | Drives execution: `Run`, `SignalComplete` / `SignalFailed`; `Result` returns sorted id slices. |
| `kahnqueue.Queue` | Pluggable queue: **`NewDefault`** (single-goroutine) or **`NewQueue`** / **`NewConcurrent`** (safe for concurrent `Pop` / `Prune`). |
| `dag` errors (`ErrGraphHasCycles`, `ErrInvalidNode`, self-loop, …) | Invalid graphs at `Build` / `Connect`. |
| `kahnqueue.ProgressTracker` | Optional per-node progress in `[0, 1]`; not required for scheduling. |

## Example

`NewScheduler` uses **`NewQueue`** (concurrent-safe) by default. Call `SignalComplete` / `SignalFailed` from worker goroutines as work finishes; use **`WithQueue`** + **`NewDefault`** only if a single goroutine drives the scheduler (Temporal-style).

```go
import (
	"github.com/Flashlock/kahn-queue/go/dag"
	"github.com/Flashlock/kahn-queue/go/kahnqueue"
)

b := dag.NewBuilder[string]()
lint := b.Add("lint")
compile := b.Add("compile")
test := b.Add("test")
_ = b.Connect(lint, compile)
_ = b.Connect(compile, test)

d, err := b.Build()
if err != nil {
	panic(err)
}

sched := kahnqueue.NewScheduler(d, func(id int, s *kahnqueue.Scheduler[string]) {
	// runStep(d.Get(id)); then from this or another goroutine:
	s.SignalComplete(id)
})
sched.Run()
res := sched.Result()
_ = res
```

## Manual KahnQueue

```go
import (
	"github.com/Flashlock/kahn-queue/go/dag"
	"github.com/Flashlock/kahn-queue/go/kahnqueue"
)

b := dag.NewBuilder[string]()
lint := b.Add("lint")
compile := b.Add("compile")
test := b.Add("test")
_ = b.Connect(lint, compile)
_ = b.Connect(compile, test)

d, err := b.Build()
if err != nil {
	panic(err)
}

q := kahnqueue.NewDefault(d)

ready := append([]int(nil), q.ReadyIDs()...)

for len(ready) > 0 {
	id := ready[0]
	ready = ready[1:]

	// do work for `id` (e.g. runStep(d.Get(id)))

	promoted, err := q.Pop(id)
	if err != nil {
		panic(err)
	}
	ready = append(ready, promoted...)

	// If a node fails, you can prune it (and its descendants):
	// _, err = q.Prune(id)
}
```

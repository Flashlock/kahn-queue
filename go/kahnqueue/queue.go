package kahnqueue

import "github.com/Flashlock/kahn-queue/go/dag"

// Queue runs Kahn topological readiness over a [dag.Dag]. Implementations are safe for concurrent use.
//
// All slice returns are deterministically ordered: ids appear in ascending numeric order.
type Queue interface {
	// Pop marks id completed and returns ids that became runnable (sorted ascending).
	Pop(id int) (newlyReady []int, err error)
	// Prune marks id and its descendants pruned; returns affected ids (sorted ascending).
	Prune(id int) (affected []int, err error)
	// Peek returns node ids currently runnable (READY), sorted ascending.
	Peek() []int
}

// NewQueue builds a concurrent-safe [ConcurrentQueue] for d (recommended default).
func NewQueue[T comparable](d *dag.Dag[T]) *ConcurrentQueue[T] {
	return NewConcurrent(d)
}

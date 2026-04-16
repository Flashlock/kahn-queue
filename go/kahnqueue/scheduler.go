package kahnqueue

import (
	"sort"
	"sync"

	"github.com/Flashlock/kahn-queue/go/dag"
)

// ExecuteFunc runs work for a ready node id. Call [Scheduler.SignalComplete] or [Scheduler.SignalFailed] when done.
type ExecuteFunc[T comparable] func(nodeID int, sched *Scheduler[T])

// Scheduler runs a DAG by handing ready node ids to ExecuteFunc (mirrors Java KahnScheduler).
type Scheduler[T comparable] struct {
	mu sync.Mutex

	dag     *dag.Dag[T]
	queue   Queue
	execute ExecuteFunc[T]

	completed map[int]struct{}
	failed    map[int]struct{}
	pruned    map[int]struct{}
}

// SchedulerOption configures [NewScheduler].
type SchedulerOption[T comparable] func(*Scheduler[T])

// WithQueue injects a custom [Queue] factory (defaults to [NewQueue]).
func WithQueue[T comparable](newQueue func(d *dag.Dag[T]) Queue) SchedulerOption[T] {
	return func(s *Scheduler[T]) {
		s.queue = newQueue(s.dag)
	}
}

// NewScheduler constructs a scheduler. By default the queue is [NewQueue] (concurrent-safe).
func NewScheduler[T comparable](d *dag.Dag[T], exec ExecuteFunc[T], opts ...SchedulerOption[T]) *Scheduler[T] {
	s := &Scheduler[T]{
		dag:       d,
		execute:   exec,
		completed: make(map[int]struct{}),
		failed:    make(map[int]struct{}),
		pruned:    make(map[int]struct{}),
	}
	for _, o := range opts {
		o(s)
	}
	if s.queue == nil {
		s.queue = NewQueue(d)
	}
	return s
}

// Run invokes execute for each id the queue reports ready now; no-op if the run is already finished.
func (s *Scheduler[T]) Run() {
	s.mu.Lock()
	fin := s.isFinishedLocked()
	s.mu.Unlock()
	if fin {
		return
	}
	ids := s.queue.Peek()
	for _, id := range ids {
		s.execute(id, s)
	}
}

// SignalComplete completes a node and schedules newly ready nodes. Duplicate completion is ignored.
func (s *Scheduler[T]) SignalComplete(nodeID int) {
	s.mu.Lock()
	if _, ok := s.completed[nodeID]; ok {
		s.mu.Unlock()
		return
	}
	s.completed[nodeID] = struct{}{}
	s.mu.Unlock()

	newly, err := s.queue.Pop(nodeID)
	if err != nil {
		return
	}
	for _, cid := range newly {
		s.execute(cid, s)
	}
}

// SignalFailed marks failure and prunes downstream nodes; duplicate failure is ignored.
func (s *Scheduler[T]) SignalFailed(nodeID int) {
	s.mu.Lock()
	if _, ok := s.failed[nodeID]; ok {
		s.mu.Unlock()
		return
	}
	s.failed[nodeID] = struct{}{}
	s.mu.Unlock()

	removed, err := s.queue.Prune(nodeID)
	if err != nil {
		return
	}

	s.mu.Lock()
	defer s.mu.Unlock()
	for _, id := range removed {
		if id == nodeID {
			continue
		}
		s.pruned[id] = struct{}{}
	}
}

// IsFinished reports whether every node is completed, failed, or pruned.
func (s *Scheduler[T]) IsFinished() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.isFinishedLocked()
}

func (s *Scheduler[T]) isFinishedLocked() bool {
	n := s.dag.Size()
	return len(s.completed)+len(s.failed)+len(s.pruned) == n
}

// DagResult holds outcome ids in ascending order (deterministic).
type DagResult struct {
	Completed []int
	Failed    []int
	Pruned    []int
}

// Result returns copies of outcome sets as sorted slices.
func (s *Scheduler[T]) Result() DagResult {
	s.mu.Lock()
	defer s.mu.Unlock()
	return DagResult{
		Completed: sortedIntSetKeys(s.completed),
		Failed:    sortedIntSetKeys(s.failed),
		Pruned:    sortedIntSetKeys(s.pruned),
	}
}

func sortedIntSetKeys(m map[int]struct{}) []int {
	if len(m) == 0 {
		return nil
	}
	out := make([]int, 0, len(m))
	for k := range m {
		out = append(out, k)
	}
	sort.Ints(out)
	return out
}

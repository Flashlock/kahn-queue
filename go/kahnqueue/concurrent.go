package kahnqueue

import (
	"fmt"
	"sort"
	"sync"

	"github.com/Flashlock/kahn-queue/go/dag"
)

// ConcurrentQueue supports concurrent Pop and Prune with per-node mutexes. Peek may not be a consistent snapshot.
type ConcurrentQueue[T comparable] struct {
	dag      *dag.Dag[T]
	machines []*NodeMachine
	locks    []sync.Mutex
}

// NewConcurrent builds a concurrent queue for d.
func NewConcurrent[T comparable](d *dag.Dag[T]) *ConcurrentQueue[T] {
	n := d.Size()
	machines := make([]*NodeMachine, n)
	for i := 0; i < n; i++ {
		indeg, err := d.InDegree(i)
		if err != nil {
			panic(err)
		}
		machines[i] = NewNodeMachine(i, indeg)
	}
	return &ConcurrentQueue[T]{
		dag:      d,
		machines: machines,
		locks:    make([]sync.Mutex, n),
	}
}

// Pop marks id completed and returns ids that became runnable (sorted ascending).
func (q *ConcurrentQueue[T]) Pop(id int) ([]int, error) {
	if err := dag.ValidateNode(id, q.dag.Size()); err != nil {
		return nil, err
	}
	q.locks[id].Lock()
	m := q.machines[id]
	if !m.Is(NodeActive) {
		q.locks[id].Unlock()
		return nil, fmt.Errorf("Pop failed. Node %d is not active", id)
	}
	if err := m.Transition(NodeComplete); err != nil {
		q.locks[id].Unlock()
		return nil, err
	}
	targets, err := q.dag.Targets(id)
	if err != nil {
		q.locks[id].Unlock()
		return nil, err
	}
	var out []int
	for _, cid := range targets {
		q.locks[cid].Lock()
		child := q.machines[cid]
		if err := child.Decrement(); err != nil {
			q.locks[cid].Unlock()
			q.locks[id].Unlock()
			return nil, err
		}
		if child.CanTransition(NodeActive) {
			if err := child.Transition(NodeActive); err != nil {
				q.locks[cid].Unlock()
				q.locks[id].Unlock()
				return nil, err
			}
			out = append(out, cid)
		}
		q.locks[cid].Unlock()
	}
	q.locks[id].Unlock()
	return out, nil
}

// Prune marks id and its descendants pruned; returns affected ids (sorted ascending).
func (q *ConcurrentQueue[T]) Prune(id int) ([]int, error) {
	if err := dag.ValidateNode(id, q.dag.Size()); err != nil {
		return nil, err
	}
	stack := []int{id}
	seen := make([]bool, q.dag.Size())
	var affected []int
	for len(stack) > 0 {
		curr := stack[len(stack)-1]
		stack = stack[:len(stack)-1]
		if seen[curr] {
			continue
		}
		seen[curr] = true
		q.locks[curr].Lock()
		machine := q.machines[curr]
		if err := machine.Transition(NodePruned); err != nil {
			q.locks[curr].Unlock()
			return nil, err
		}
		affected = append(affected, curr)
		q.locks[curr].Unlock()
		targets, err := q.dag.Targets(curr)
		if err != nil {
			return nil, err
		}
		for _, t := range targets {
			if !seen[t] {
				stack = append(stack, t)
			}
		}
	}
	sort.Ints(affected)
	return affected, nil
}

// Peek returns node ids currently in READY, sorted ascending (index order).
func (q *ConcurrentQueue[T]) Peek() []int {
	n := q.dag.Size()
	out := make([]int, 0)
	for i := 0; i < n; i++ {
		if q.machines[i].Is(NodeReady) {
			out = append(out, i)
		}
	}
	return out
}

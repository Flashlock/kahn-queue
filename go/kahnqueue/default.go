package kahnqueue

import (
	"fmt"
	"sort"

	"github.com/Flashlock/kahn-queue/go/dag"
)

// DefaultQueue is a single-goroutine Kahn queue (no internal locking). Prefer [NewQueue] for concurrent use.
type DefaultQueue[T comparable] struct {
	dag      *dag.Dag[T]
	machines []*NodeMachine
}

// NewDefault builds a queue whose readiness matches d.
func NewDefault[T comparable](d *dag.Dag[T]) *DefaultQueue[T] {
	n := d.Size()
	machines := make([]*NodeMachine, n)
	for i := 0; i < n; i++ {
		indeg, err := d.InDegree(i)
		if err != nil {
			panic(err)
		}
		machines[i] = NewNodeMachine(i, indeg)
	}
	return &DefaultQueue[T]{dag: d, machines: machines}
}

// Pop marks id completed and returns ids that became runnable (sorted ascending).
func (q *DefaultQueue[T]) Pop(id int) ([]int, error) {
	if err := dag.ValidateNode(id, q.dag.Size()); err != nil {
		return nil, err
	}
	m := q.machines[id]
	if !m.Is(NodeActive) {
		return nil, fmt.Errorf("Pop failed. Node %d is not active", id)
	}
	if err := m.Transition(NodeComplete); err != nil {
		return nil, err
	}
	targets, err := q.dag.Targets(id)
	if err != nil {
		return nil, err
	}
	var out []int
	for _, cid := range targets {
		child := q.machines[cid]
		if err := child.Decrement(); err != nil {
			return nil, err
		}
		if child.CanTransition(NodeActive) {
			if err := child.Transition(NodeActive); err != nil {
				return nil, err
			}
			out = append(out, cid)
		}
	}
	return out, nil
}

// Prune marks id and its descendants pruned; returns every affected node id (sorted ascending).
func (q *DefaultQueue[T]) Prune(id int) ([]int, error) {
	if err := dag.ValidateNode(id, q.dag.Size()); err != nil {
		return nil, err
	}
	stack := []int{id}
	var affected []int
	seen := make([]bool, q.dag.Size())
	for len(stack) > 0 {
		curr := stack[len(stack)-1]
		stack = stack[:len(stack)-1]
		if seen[curr] {
			continue
		}
		seen[curr] = true
		machine := q.machines[curr]
		if err := machine.Transition(NodePruned); err != nil {
			return nil, err
		}
		affected = append(affected, curr)
		targets, err := q.dag.Targets(curr)
		if err != nil {
			return nil, err
		}
		for i := len(targets) - 1; i >= 0; i-- {
			t := targets[i]
			if !seen[t] {
				stack = append(stack, t)
			}
		}
	}
	sort.Ints(affected)
	return affected, nil
}

// ReadyIDs returns node ids currently runnable (READY), sorted ascending (index order).
func (q *DefaultQueue[T]) ReadyIDs() []int {
	n := q.dag.Size()
	out := make([]int, 0)
	for i := 0; i < n; i++ {
		if q.machines[i].Is(NodeReady) {
			out = append(out, i)
		}
	}
	return out
}

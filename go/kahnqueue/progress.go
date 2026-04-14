package kahnqueue

import (
	"fmt"
	"sync"

	"github.com/Flashlock/kahn-queue/go/dag"
)

// ProgressTracker stores per-node progress in [0, 1] by node id (index order; deterministic aggregation).
type ProgressTracker[T comparable] struct {
	mu     sync.Mutex
	values []float64
}

// NewProgressTracker initializes progress to 0 for every node in d.
func NewProgressTracker[T comparable](d *dag.Dag[T]) *ProgressTracker[T] {
	n := d.Size()
	return &ProgressTracker[T]{values: make([]float64, n)}
}

// Put sets progress for id (values must be in [0, 1]).
func (p *ProgressTracker[T]) Put(id int, value float64) error {
	if value < 0 || value > 1 {
		return fmt.Errorf("progress must be between 0 and 1: id=%d value=%f", id, value)
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	if err := dag.ValidateNode(id, len(p.values)); err != nil {
		return err
	}
	p.values[id] = value
	return nil
}

// Get returns progress for id.
func (p *ProgressTracker[T]) Get(id int) (float64, error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	if err := dag.ValidateNode(id, len(p.values)); err != nil {
		return 0, err
	}
	return p.values[id], nil
}

// Aggregate returns the mean progress across nodes in id order (deterministic).
func (p *ProgressTracker[T]) Aggregate() float64 {
	p.mu.Lock()
	defer p.mu.Unlock()
	if len(p.values) == 0 {
		return 0
	}
	var sum float64
	for _, v := range p.values {
		sum += v
	}
	return sum / float64(len(p.values))
}

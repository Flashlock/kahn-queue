package dag

import (
	"slices"
)

// Dag is an immutable directed acyclic graph: nodes 0..Size()-1 with typed payloads.
type Dag[T comparable] struct {
	nodes []T
	adj   [][]int // successors, sorted ascending per node
	rev   [][]int // predecessors, sorted ascending per node
}

// Size returns the number of nodes (ids are 0 .. Size()-1).
func (d *Dag[T]) Size() int { return len(d.nodes) }

// Get returns the payload for node id.
func (d *Dag[T]) Get(id int) (T, error) {
	var zero T
	if err := ValidateNode(id, d.Size()); err != nil {
		return zero, err
	}
	return d.nodes[id], nil
}

// InDegree returns the number of incoming edges to id.
func (d *Dag[T]) InDegree(id int) (int, error) {
	if err := ValidateNode(id, d.Size()); err != nil {
		return 0, err
	}
	return len(d.rev[id]), nil
}

// OutDegree returns the number of outgoing edges from id.
func (d *Dag[T]) OutDegree(id int) (int, error) {
	if err := ValidateNode(id, d.Size()); err != nil {
		return 0, err
	}
	return len(d.adj[id]), nil
}

// Targets returns successor ids of id in ascending order.
func (d *Dag[T]) Targets(id int) ([]int, error) {
	if err := ValidateNode(id, d.Size()); err != nil {
		return nil, err
	}
	return slices.Clone(d.adj[id]), nil
}

// Sources returns predecessor ids of id in ascending order.
func (d *Dag[T]) Sources(id int) ([]int, error) {
	if err := ValidateNode(id, d.Size()); err != nil {
		return nil, err
	}
	return slices.Clone(d.rev[id]), nil
}

// ValidateNode ensures id is valid for a graph of the given size.
func ValidateNode(id, size int) error {
	if id < 0 || id >= size {
		return ErrInvalidNode
	}
	return nil
}

package dag

import (
	"container/list"
	"sort"
)

// Builder constructs a [Dag] via [Builder.Add] and [Builder.Connect].
type Builder[T comparable] struct {
	nodes []T
	out   []map[int]struct{}
	in    []map[int]struct{}
}

// NewBuilder returns an empty mutable graph builder.
func NewBuilder[T comparable]() *Builder[T] {
	return &Builder[T]{}
}

// Add appends a node and returns its id.
func (b *Builder[T]) Add(data T) int {
	id := len(b.nodes)
	b.nodes = append(b.nodes, data)
	b.out = append(b.out, make(map[int]struct{}))
	b.in = append(b.in, make(map[int]struct{}))
	return id
}

// Connect adds a directed edge source → target (ignored if duplicate).
func (b *Builder[T]) Connect(source, target int) error {
	if err := ValidateNode(source, len(b.nodes)); err != nil {
		return err
	}
	if err := ValidateNode(target, len(b.nodes)); err != nil {
		return err
	}
	if source == target {
		return ErrSelfLoop(source)
	}
	if _, ok := b.out[source][target]; ok {
		return nil
	}
	b.out[source][target] = struct{}{}
	b.in[target][source] = struct{}{}
	return nil
}

// Build returns an immutable DAG after validating acyclicity.
func (b *Builder[T]) Build() (*Dag[T], error) {
	n := len(b.nodes)
	if n == 0 {
		return &Dag[T]{}, nil
	}
	if err := cycleCheck(b.out); err != nil {
		return nil, err
	}
	nodes := append([]T(nil), b.nodes...)
	adj := make([][]int, n)
	rev := make([][]int, n)
	for i := 0; i < n; i++ {
		adj[i] = sortedKeys(b.out[i])
		rev[i] = sortedKeys(b.in[i])
	}
	return &Dag[T]{nodes: nodes, adj: adj, rev: rev}, nil
}

func cycleCheck(out []map[int]struct{}) error {
	n := len(out)
	inDegree := make([]int, n)
	for u := 0; u < n; u++ {
		for v := range out[u] {
			inDegree[v]++
		}
	}
	ready := list.New()
	for i := 0; i < n; i++ {
		if inDegree[i] == 0 {
			ready.PushBack(i)
		}
	}
	processed := 0
	for ready.Len() > 0 {
		front := ready.Front()
		u := front.Value.(int)
		ready.Remove(front)
		processed++
		for v := range out[u] {
			inDegree[v]--
			if inDegree[v] == 0 {
				ready.PushBack(v)
			}
		}
	}
	if processed != n {
		return ErrGraphHasCycles
	}
	return nil
}

func sortedKeys(m map[int]struct{}) []int {
	if len(m) == 0 {
		return nil
	}
	keys := make([]int, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	sort.Ints(keys)
	return keys
}

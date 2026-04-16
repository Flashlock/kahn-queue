package kahnqueue

import (
	"context"
	"errors"
	"slices"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/Flashlock/kahn-queue/go/dag"
)

const concurrencyRounds = 25

func TestConcurrent_basics_peekAndPopValidation(t *testing.T) {
	empty, err := dag.NewBuilder[string]().Build()
	if err != nil {
		t.Fatal(err)
	}
	q0 := NewConcurrent(empty)
	if len(q0.Peek()) != 0 {
		t.Fatal("empty ready")
	}
	if _, err := q0.Pop(0); !errors.Is(err, dag.ErrInvalidNode) {
		t.Fatalf("pop empty %v", err)
	}

	chain := dag.NewBuilder[string]()
	root := chain.Add("root")
	mid := chain.Add("mid")
	leaf := chain.Add("leaf")
	if err := chain.Connect(root, mid); err != nil {
		t.Fatal(err)
	}
	if err := chain.Connect(mid, leaf); err != nil {
		t.Fatal(err)
	}
	dagChain, err := chain.Build()
	if err != nil {
		t.Fatal(err)
	}
	qc := NewConcurrent(dagChain)
	if !slices.Equal(qc.Peek(), []int{root}) {
		t.Fatalf("chain ready %v", qc.Peek())
	}

	join := dag.NewBuilder[string]()
	a := join.Add("a")
	c := join.Add("c")
	jn := join.Add("join")
	if err := join.Connect(a, jn); err != nil {
		t.Fatal(err)
	}
	if err := join.Connect(c, jn); err != nil {
		t.Fatal(err)
	}
	dagJoin, err := join.Build()
	if err != nil {
		t.Fatal(err)
	}
	qj := NewConcurrent(dagJoin)
	if !slices.Equal(qj.Peek(), []int{a, c}) {
		t.Fatalf("join ready %v", qj.Peek())
	}

	one := dag.NewBuilder[string]()
	only := one.Add("x")
	dagOne, err := one.Build()
	if err != nil {
		t.Fatal(err)
	}
	q1 := NewConcurrent(dagOne)
	if !slices.Equal(q1.Peek(), []int{only}) {
		t.Fatal()
	}
	_, err = q1.Pop(only)
	if err == nil {
		t.Fatal("expected pop error")
	}
	if err.Error() == "" {
		t.Fatal("message")
	}

	ob := dag.NewBuilder[string]()
	ob.Add("x")
	dagOb, err := ob.Build()
	if err != nil {
		t.Fatal(err)
	}
	qo := NewConcurrent(dagOb)
	if _, err := qo.Pop(1); !errors.Is(err, dag.ErrInvalidNode) {
		t.Fatalf("%v", err)
	}
}

func TestConcurrent_prune_collectsReachable(t *testing.T) {
	b1 := dag.NewBuilder[string]()
	r := b1.Add("r")
	m := b1.Add("m")
	l := b1.Add("l")
	if err := b1.Connect(r, m); err != nil {
		t.Fatal(err)
	}
	if err := b1.Connect(m, l); err != nil {
		t.Fatal(err)
	}
	d1, err := b1.Build()
	if err != nil {
		t.Fatal(err)
	}
	q1 := NewConcurrent(d1)
	got, err := q1.Prune(r)
	if err != nil {
		t.Fatal(err)
	}
	if !slices.Equal(got, []int{r, m, l}) {
		t.Fatalf("%v", got)
	}

	b2 := dag.NewBuilder[string]()
	root := b2.Add("root")
	left := b2.Add("left")
	right := b2.Add("right")
	if err := b2.Connect(root, left); err != nil {
		t.Fatal(err)
	}
	if err := b2.Connect(root, right); err != nil {
		t.Fatal(err)
	}
	d2, err := b2.Build()
	if err != nil {
		t.Fatal(err)
	}
	q2 := NewConcurrent(d2)
	got2, err := q2.Prune(root)
	if err != nil {
		t.Fatal(err)
	}
	if !slices.Equal(got2, []int{root, left, right}) {
		t.Fatalf("%v", got2)
	}
}

func TestConcurrent_prune_updatesReadySet(t *testing.T) {
	b := dag.NewBuilder[string]()
	a := b.Add("a")
	c := b.Add("c")
	join := b.Add("join")
	if err := b.Connect(a, join); err != nil {
		t.Fatal(err)
	}
	if err := b.Connect(c, join); err != nil {
		t.Fatal(err)
	}
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	q := NewConcurrent(d)
	if _, err := q.Prune(a); err != nil {
		t.Fatal(err)
	}
	if !slices.Equal(q.Peek(), []int{c}) {
		t.Fatalf("%v", q.Peek())
	}
}

func TestConcurrent_peek_stableUnderSequentialRepeats(t *testing.T) {
	b := dag.NewBuilder[string]()
	a := b.Add("a")
	c := b.Add("c")
	join := b.Add("join")
	if err := b.Connect(a, join); err != nil {
		t.Fatal(err)
	}
	if err := b.Connect(c, join); err != nil {
		t.Fatal(err)
	}
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	q := NewConcurrent(d)
	snapshot := q.Peek()
	for i := 0; i < 10000; i++ {
		if !slices.Equal(snapshot, q.Peek()) {
			t.Fatal("drift")
		}
	}
	assertPeekStructuralInvariant(t, d, q)
}

func TestConcurrent_prune_secondCallThrows(t *testing.T) {
	b := dag.NewBuilder[string]()
	r := b.Add("r")
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	q := NewConcurrent(d)
	got, err := q.Prune(r)
	if err != nil {
		t.Fatal(err)
	}
	if !slices.Equal(got, []int{r}) {
		t.Fatal()
	}
	if len(q.Peek()) != 0 {
		t.Fatal()
	}
	if _, err := q.Prune(r); err == nil {
		t.Fatal("expected error")
	}
	assertPeekStructuralInvariant(t, d, q)
}

func TestConcurrent_kahnProgression(t *testing.T) {
	b := dag.NewBuilder[string]()
	root := b.Add("root")
	mid := b.Add("mid")
	leaf := b.Add("leaf")
	if err := b.Connect(root, mid); err != nil {
		t.Fatal(err)
	}
	if err := b.Connect(mid, leaf); err != nil {
		t.Fatal(err)
	}
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	q := NewConcurrent(d)
	q.machines[root].forceState(NodeActive)
	got1, err := q.Pop(root)
	if err != nil {
		t.Fatal(err)
	}
	if !slices.Equal(got1, []int{mid}) {
		t.Fatalf("%v", got1)
	}
	q.machines[mid].forceState(NodeActive)
	got2, err := q.Pop(mid)
	if err != nil {
		t.Fatal(err)
	}
	if !slices.Equal(got2, []int{leaf}) {
		t.Fatalf("%v", got2)
	}
	q.machines[leaf].forceState(NodeActive)
	got3, err := q.Pop(leaf)
	if err != nil {
		t.Fatal(err)
	}
	if len(got3) != 0 {
		t.Fatalf("%v", got3)
	}
	if len(q.Peek()) != 0 {
		t.Fatal()
	}
	assertPeekStructuralInvariant(t, d, q)
}

func TestConcurrent_peekStress(t *testing.T) {
	for range concurrencyRounds {
		runConcurrentReadyIdsStressOnce(t)
	}
}

func TestConcurrent_disjointPrune(t *testing.T) {
	for range concurrencyRounds {
		runConcurrentDisjointPruneOnce(t)
	}
}

func TestConcurrent_popFailuresDoNotMutateReady(t *testing.T) {
	for range concurrencyRounds {
		runConcurrentPopFailuresOnce(t)
	}
}

func TestConcurrent_sameIdPruneContention(t *testing.T) {
	for range concurrencyRounds {
		runConcurrentSameIdPruneManyThreadsOnce(t)
	}
}

func TestConcurrent_overlappingPrune(t *testing.T) {
	for range concurrencyRounds {
		runConcurrentOverlappingPruneOnce(t)
	}
}

func TestConcurrent_pruneMutation_visibleAfterDone(t *testing.T) {
	b := dag.NewBuilder[string]()
	left := b.Add("left")
	right := b.Add("right")
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	q := NewConcurrent(d)
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	done := make(chan []int, 1)
	go func() {
		got, err := q.Prune(left)
		if err != nil {
			done <- nil
			return
		}
		done <- got
	}()
	var got []int
	select {
	case got = <-done:
	case <-ctx.Done():
		t.Fatal("timeout")
	}
	if !slices.Equal(got, []int{left}) {
		t.Fatalf("%v", got)
	}
	if !slices.Equal(q.Peek(), []int{right}) {
		t.Fatalf("%v", q.Peek())
	}
	assertPeekStructuralInvariant(t, d, q)
}

func runConcurrentReadyIdsStressOnce(t *testing.T) {
	t.Helper()
	b := dag.NewBuilder[string]()
	x := b.Add("x")
	y := b.Add("y")
	if err := b.Connect(x, y); err != nil {
		t.Fatal(err)
	}
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	q := NewConcurrent(d)
	expected := []int{x}
	if !slices.Equal(q.Peek(), expected) {
		t.Fatal()
	}
	const threads = 8
	const iterationsPerThread = 500
	var wg sync.WaitGroup
	var drift int32
	wg.Add(threads)
	for range threads {
		go func() {
			defer wg.Done()
			for i := 0; i < iterationsPerThread; i++ {
				if !slices.Equal(expected, q.Peek()) {
					atomic.StoreInt32(&drift, 1)
					return
				}
			}
		}()
	}
	wg.Wait()
	if atomic.LoadInt32(&drift) != 0 {
		t.Fatal("peek drifted during concurrent read")
	}
	if !slices.Equal(expected, q.Peek()) {
		t.Fatal()
	}
	assertPeekStructuralInvariant(t, d, q)
}

func runConcurrentDisjointPruneOnce(t *testing.T) {
	t.Helper()
	b := dag.NewBuilder[string]()
	left := b.Add("left")
	right := b.Add("right")
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	q := NewConcurrent(d)
	var wg sync.WaitGroup
	var got1, got2 []int
	var err1, err2 error
	wg.Add(2)
	go func() {
		defer wg.Done()
		got1, err1 = q.Prune(left)
	}()
	go func() {
		defer wg.Done()
		got2, err2 = q.Prune(right)
	}()
	wg.Wait()
	if err1 != nil {
		t.Fatal(err1)
	}
	if err2 != nil {
		t.Fatal(err2)
	}
	if !slices.Equal(got1, []int{left}) || !slices.Equal(got2, []int{right}) {
		t.Fatalf("%v %v", got1, got2)
	}
	if len(q.Peek()) != 0 {
		t.Fatal()
	}
	assertPeekStructuralInvariant(t, d, q)
}

func runConcurrentPopFailuresOnce(t *testing.T) {
	t.Helper()
	b := dag.NewBuilder[string]()
	only := b.Add("x")
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	q := NewConcurrent(d)
	before := q.Peek()
	const threads = 32
	var wg sync.WaitGroup
	var illegal int32
	wg.Add(threads)
	for range threads {
		go func() {
			defer wg.Done()
			_, err := q.Pop(only)
			if err != nil {
				atomic.AddInt32(&illegal, 1)
			}
		}()
	}
	wg.Wait()
	if int(atomic.LoadInt32(&illegal)) != threads {
		t.Fatalf("illegal count %d", illegal)
	}
	if !slices.Equal(before, q.Peek()) {
		t.Fatal("ready mutated")
	}
	assertPeekStructuralInvariant(t, d, q)
}

func runConcurrentSameIdPruneManyThreadsOnce(t *testing.T) {
	t.Helper()
	b := dag.NewBuilder[string]()
	root := b.Add("root")
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	q := NewConcurrent(d)
	const threads = 8
	var wg sync.WaitGroup
	var successes int32
	var illegalStates int32
	start := make(chan struct{})
	wg.Add(threads)
	for range threads {
		go func() {
			defer wg.Done()
			<-start
			_, err := q.Prune(root)
			if err == nil {
				atomic.AddInt32(&successes, 1)
			} else {
				atomic.AddInt32(&illegalStates, 1)
			}
		}()
	}
	close(start)
	wg.Wait()
	if successes != 1 {
		t.Fatalf("successes %d", successes)
	}
	if illegalStates != threads-1 {
		t.Fatalf("illegal %d", illegalStates)
	}
	if len(q.Peek()) != 0 {
		t.Fatal()
	}
	assertPeekStructuralInvariant(t, d, q)
}

func runConcurrentOverlappingPruneOnce(t *testing.T) {
	t.Helper()
	b := dag.NewBuilder[string]()
	r := b.Add("r")
	m := b.Add("m")
	l := b.Add("l")
	if err := b.Connect(r, m); err != nil {
		t.Fatal(err)
	}
	if err := b.Connect(m, l); err != nil {
		t.Fatal(err)
	}
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	q := NewConcurrent(d)
	start := make(chan struct{})
	var wg sync.WaitGroup
	var mu sync.Mutex
	var s1, s2 []int
	wg.Add(2)
	go func() {
		defer wg.Done()
		<-start
		got, err := q.Prune(r)
		mu.Lock()
		if err == nil {
			s1 = got
		}
		mu.Unlock()
	}()
	go func() {
		defer wg.Done()
		<-start
		got, err := q.Prune(m)
		mu.Lock()
		if err == nil {
			s2 = got
		}
		mu.Unlock()
	}()
	close(start)
	wg.Wait()
	assertPeekStructuralInvariant(t, d, q)
	for _, id := range q.Peek() {
		if id != r && id != m && id != l {
			t.Fatalf("bad ready id %d", id)
		}
	}
	full := []int{r, m, l}
	if s1 != nil && s2 != nil {
		union := append(append([]int{}, s1...), s2...)
		slices.Sort(union)
		union = slices.Compact(union)
		if !slices.Equal(union, full) {
			t.Fatalf("union %v", union)
		}
	}
	if s1 != nil && slices.Equal(s1, full) {
		if len(q.Peek()) != 0 {
			t.Fatal()
		}
	}
	if s2 != nil && slices.Equal(s2, full) {
		if len(q.Peek()) != 0 {
			t.Fatal()
		}
	}
	anyFull := (s1 != nil && slices.Equal(s1, full)) || (s2 != nil && slices.Equal(s2, full))
	if anyFull && len(q.Peek()) != 0 {
		t.Fatal("full prune should clear ready")
	}
}

func assertPeekStructuralInvariant[T comparable](t *testing.T, d *dag.Dag[T], q *ConcurrentQueue[T]) {
	t.Helper()
	for _, id := range q.Peek() {
		if err := dag.ValidateNode(id, d.Size()); err != nil {
			t.Fatalf("invalid id %d: %v", id, err)
		}
	}
}

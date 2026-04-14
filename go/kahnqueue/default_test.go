package kahnqueue

import (
	"errors"
	"slices"
	"testing"

	"github.com/Flashlock/kahn-queue/go/dag"
)

func TestDefault_emptyDag_readyIdsEmpty_andPopRejectsInvalidId(t *testing.T) {
	d, err := dag.NewBuilder[string]().Build()
	if err != nil {
		t.Fatal(err)
	}
	q := NewDefault(d)
	if len(q.ReadyIDs()) != 0 {
		t.Fatal("ready")
	}
	if _, err := q.Pop(0); !errors.Is(err, dag.ErrInvalidNode) {
		t.Fatalf("got %v", err)
	}
}

func TestDefault_readyIds_containsOnlyZeroInDegreeNodes(t *testing.T) {
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
	q := NewDefault(d)
	if !slices.Equal(q.ReadyIDs(), []int{root}) {
		t.Fatalf("got %v", q.ReadyIDs())
	}
}

func TestDefault_readyIds_twoIndependentRoots(t *testing.T) {
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
	q := NewDefault(d)
	if !slices.Equal(q.ReadyIDs(), []int{a, c}) {
		t.Fatalf("got %v", q.ReadyIDs())
	}
}

func TestDefault_pop_throwsWhenNodeIsReadyNotActive(t *testing.T) {
	b := dag.NewBuilder[string]()
	only := b.Add("x")
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	q := NewDefault(d)
	if !slices.Equal(q.ReadyIDs(), []int{only}) {
		t.Fatal()
	}
	_, err = q.Pop(only)
	if err == nil {
		t.Fatal("expected error")
	}
	want := "Pop failed. Node 0 is not active"
	if err.Error() != want {
		t.Fatalf("got %q want %q", err.Error(), want)
	}
}

func TestDefault_pop_throwsForOutOfRangeId(t *testing.T) {
	b := dag.NewBuilder[string]()
	b.Add("x")
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	q := NewDefault(d)
	if _, err := q.Pop(1); !errors.Is(err, dag.ErrInvalidNode) {
		t.Fatalf("got %v", err)
	}
}

func TestDefault_prune_marksRootAndReachableDescendants(t *testing.T) {
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
	q := NewDefault(d)
	got, err := q.Prune(r)
	if err != nil {
		t.Fatal(err)
	}
	want := []int{r, m, l}
	if !slices.Equal(got, want) {
		t.Fatalf("got %v want %v", got, want)
	}
}

func TestDefault_prune_forkCollectsAllBranches(t *testing.T) {
	b := dag.NewBuilder[string]()
	root := b.Add("root")
	left := b.Add("left")
	right := b.Add("right")
	if err := b.Connect(root, left); err != nil {
		t.Fatal(err)
	}
	if err := b.Connect(root, right); err != nil {
		t.Fatal(err)
	}
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	q := NewDefault(d)
	got, err := q.Prune(root)
	if err != nil {
		t.Fatal(err)
	}
	want := []int{root, left, right}
	if !slices.Equal(got, want) {
		t.Fatalf("got %v want %v", got, want)
	}
}

func TestDefault_prune_removesIdsFromReadySet(t *testing.T) {
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
	q := NewDefault(d)
	if _, err := q.Prune(a); err != nil {
		t.Fatal(err)
	}
	if !slices.Equal(q.ReadyIDs(), []int{c}) {
		t.Fatalf("got %v", q.ReadyIDs())
	}
}

func TestDefault_prune_secondCallThrows(t *testing.T) {
	b := dag.NewBuilder[string]()
	r := b.Add("r")
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	q := NewDefault(d)
	got, err := q.Prune(r)
	if err != nil {
		t.Fatal(err)
	}
	if !slices.Equal(got, []int{r}) {
		t.Fatal()
	}
	if len(q.ReadyIDs()) != 0 {
		t.Fatal("ready")
	}
	if _, err := q.Prune(r); err == nil {
		t.Fatal("expected error")
	}
}

func TestDefault_kahnProgression_popActiveNode_returnsPromotedDependents(t *testing.T) {
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
	q := NewDefault(d)
	q.machines[root].forceState(NodeActive)
	got1, err := q.Pop(root)
	if err != nil {
		t.Fatal(err)
	}
	if !slices.Equal(got1, []int{mid}) {
		t.Fatalf("got %v", got1)
	}
	q.machines[mid].forceState(NodeActive)
	got2, err := q.Pop(mid)
	if err != nil {
		t.Fatal(err)
	}
	if !slices.Equal(got2, []int{leaf}) {
		t.Fatalf("got %v", got2)
	}
	q.machines[leaf].forceState(NodeActive)
	got3, err := q.Pop(leaf)
	if err != nil {
		t.Fatal(err)
	}
	if len(got3) != 0 {
		t.Fatalf("got %v", got3)
	}
	if len(q.ReadyIDs()) != 0 {
		t.Fatalf("ready %v", q.ReadyIDs())
	}
}

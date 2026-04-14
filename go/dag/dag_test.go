package dag_test

import (
	"errors"
	"slices"
	"testing"

	"github.com/Flashlock/kahn-queue/go/dag"
)

func TestEmptyGraph_hasSizeZero(t *testing.T) {
	d, err := dag.NewBuilder[string]().Build()
	if err != nil {
		t.Fatal(err)
	}
	if d.Size() != 0 {
		t.Fatalf("size %d", d.Size())
	}
}

func TestSingleNode_exposesPayloadAndZeroDegrees(t *testing.T) {
	b := dag.NewBuilder[string]()
	id := b.Add("solo")
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	if d.Size() != 1 {
		t.Fatalf("size")
	}
	p, err := d.Get(id)
	if err != nil || p != "solo" {
		t.Fatalf("get: %v %q", err, p)
	}
	indeg, _ := d.InDegree(id)
	outdeg, _ := d.OutDegree(id)
	if indeg != 0 || outdeg != 0 {
		t.Fatalf("degrees %d %d", indeg, outdeg)
	}
}

func TestLinearChain_degreesAndAdjacency(t *testing.T) {
	b := dag.NewBuilder[string]()
	a := b.Add("a")
	m := b.Add("m")
	z := b.Add("z")
	if err := b.Connect(a, m); err != nil {
		t.Fatal(err)
	}
	if err := b.Connect(m, z); err != nil {
		t.Fatal(err)
	}
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	check := func(id, wantIn, wantOut int, wantT, wantS []int) {
		t.Helper()
		indeg, _ := d.InDegree(id)
		outdeg, _ := d.OutDegree(id)
		if indeg != wantIn || outdeg != wantOut {
			t.Fatalf("id %d: indeg %d outdeg %d", id, indeg, outdeg)
		}
		tt, _ := d.Targets(id)
		ss, _ := d.Sources(id)
		if !slices.Equal(tt, wantT) || !slices.Equal(ss, wantS) {
			t.Fatalf("id %d: targets %v sources %v", id, tt, ss)
		}
	}
	check(a, 0, 1, []int{m}, nil)
	check(m, 1, 1, []int{z}, []int{a})
	check(z, 1, 0, nil, []int{m})
}

func TestDiamond_dagBuilds(t *testing.T) {
	b := dag.NewBuilder[int]()
	root := b.Add(0)
	left := b.Add(1)
	right := b.Add(2)
	sink := b.Add(3)
	if err := b.Connect(root, left); err != nil {
		t.Fatal(err)
	}
	if err := b.Connect(root, right); err != nil {
		t.Fatal(err)
	}
	if err := b.Connect(left, sink); err != nil {
		t.Fatal(err)
	}
	if err := b.Connect(right, sink); err != nil {
		t.Fatal(err)
	}
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	indeg, _ := d.InDegree(sink)
	if indeg != 2 {
		t.Fatalf("indeg sink %d", indeg)
	}
	src, _ := d.Sources(sink)
	if !slices.Equal(src, []int{left, right}) {
		t.Fatalf("sources %v", src)
	}
}

func TestIterator_yieldsPayloadsInIdOrder(t *testing.T) {
	b := dag.NewBuilder[string]()
	b.Add("first")
	b.Add("second")
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	var payloads []string
	for i := 0; i < d.Size(); i++ {
		p, err := d.Get(i)
		if err != nil {
			t.Fatal(err)
		}
		payloads = append(payloads, p)
	}
	if !slices.Equal(payloads, []string{"first", "second"}) {
		t.Fatalf("got %v", payloads)
	}
}

func TestDuplicateEdge_connectIsIdempotent(t *testing.T) {
	b := dag.NewBuilder[string]()
	x := b.Add("x")
	y := b.Add("y")
	if err := b.Connect(x, y); err != nil {
		t.Fatal(err)
	}
	if err := b.Connect(x, y); err != nil {
		t.Fatal(err)
	}
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	ox, _ := d.OutDegree(x)
	iy, _ := d.InDegree(y)
	if ox != 1 || iy != 1 {
		t.Fatalf("ox %d iy %d", ox, iy)
	}
}

func TestSelfLoop_throwsIllegalGraphException(t *testing.T) {
	b := dag.NewBuilder[string]()
	n := b.Add("n")
	err := b.Connect(n, n)
	var ig *dag.IllegalGraphError
	if err == nil || !errors.As(err, &ig) {
		t.Fatalf("expected IllegalGraphError, got %v", err)
	}
}

func TestDirectedCycle_throwsIllegalGraphExceptionOnBuild(t *testing.T) {
	b := dag.NewBuilder[string]()
	u := b.Add("u")
	v := b.Add("v")
	if err := b.Connect(u, v); err != nil {
		t.Fatal(err)
	}
	if err := b.Connect(v, u); err != nil {
		t.Fatal(err)
	}
	_, err := b.Build()
	if !errors.Is(err, dag.ErrGraphHasCycles) {
		t.Fatalf("expected ErrGraphHasCycles, got %v", err)
	}
}

func TestConnect_withInvalidNodeId(t *testing.T) {
	b := dag.NewBuilder[string]()
	b.Add("only")
	err := b.Connect(0, 1)
	if !errors.Is(err, dag.ErrInvalidNode) {
		t.Fatalf("got %v", err)
	}
}

func TestGet_withInvalidId(t *testing.T) {
	b := dag.NewBuilder[string]()
	b.Add("x")
	d, err := b.Build()
	if err != nil {
		t.Fatal(err)
	}
	if _, err := d.Get(1); !errors.Is(err, dag.ErrInvalidNode) {
		t.Fatalf("got %v", err)
	}
	if _, err := d.Get(-1); !errors.Is(err, dag.ErrInvalidNode) {
		t.Fatalf("got %v", err)
	}
}

func TestValidateNode_rejectsOutOfRange(t *testing.T) {
	if err := dag.ValidateNode(-1, 1); !errors.Is(err, dag.ErrInvalidNode) {
		t.Fatal(err)
	}
	if err := dag.ValidateNode(1, 1); !errors.Is(err, dag.ErrInvalidNode) {
		t.Fatal(err)
	}
}

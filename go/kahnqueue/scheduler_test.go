package kahnqueue

import (
	"slices"
	"testing"

	"github.com/Flashlock/kahn-queue/go/dag"
)

func TestScheduler_emptyGraphIsFinished(t *testing.T) {
	d, err := dag.NewBuilder[string]().Build()
	if err != nil {
		t.Fatal(err)
	}
	s := NewScheduler(d, func(int, *Scheduler[string]) {})
	if !s.IsFinished() {
		t.Fatal("expected finished on empty dag")
	}
}

func TestScheduler_linearChain(t *testing.T) {
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
	sched := NewScheduler(d, func(id int, s *Scheduler[string]) {
		q.machines[id].forceState(NodeActive)
		s.SignalComplete(id)
	}, WithQueue(func(dd *dag.Dag[string]) Queue {
		if dd != d {
			t.Fatal("dag mismatch")
		}
		return q
	}))

	sched.Run()
	if !sched.IsFinished() {
		t.Fatal("expected finished")
	}
	res := sched.Result()
	if !slices.Equal(res.Completed, []int{r, m, l}) {
		t.Fatalf("completed %v", res.Completed)
	}
}

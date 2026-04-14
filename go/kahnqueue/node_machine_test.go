package kahnqueue

import (
	"testing"
)

func TestCreate_withZeroDependencies_startsReady(t *testing.T) {
	m := NewNodeMachine(123, 0)
	if m.ID != 123 || m.NumSources != 0 || !m.Is(NodeReady) {
		t.Fatalf("got id=%d num=%d state=%v", m.ID, m.NumSources, m.State())
	}
}

func TestCreate_withPositiveDependencies_staysQueuedUntilDecrementedToZero(t *testing.T) {
	m := NewNodeMachine(0, 2)
	if !m.Is(NodeQueued) || m.CanTransition(NodeReady) {
		t.Fatalf("expected queued")
	}
	if err := m.Decrement(); err != nil {
		t.Fatal(err)
	}
	if m.NumSources != 1 || !m.Is(NodeQueued) {
		t.Fatalf("after one decrement")
	}
	if err := m.Decrement(); err != nil {
		t.Fatal(err)
	}
	if m.NumSources != 0 || !m.Is(NodeReady) {
		t.Fatalf("after two decrements")
	}
}

func TestDecrement_throwsIfAlreadyZero(t *testing.T) {
	m := NewNodeMachine(0, 0)
	err := m.Decrement()
	if err == nil || err.Error() != "Attempting to decrement below zero" {
		t.Fatalf("got %v", err)
	}
}

func TestTransitions_followExpectedLifecycle(t *testing.T) {
	m := NewNodeMachine(0, 0)
	if !m.Is(NodeReady) {
		t.Fatal()
	}
	if !m.CanTransition(NodeActive) {
		t.Fatal()
	}
	if err := m.Transition(NodeActive); err != nil {
		t.Fatal(err)
	}
	if !m.CanTransition(NodeComplete) {
		t.Fatal()
	}
	if err := m.Transition(NodeComplete); err != nil {
		t.Fatal(err)
	}
	if m.CanTransition(NodePruned) {
		t.Fatal("complete should not allow prune in this graph")
	}
}

func TestPruned_isTerminal_fromAnyEarlierPhase(t *testing.T) {
	queued := NewNodeMachine(0, 1)
	if !queued.Is(NodeQueued) {
		t.Fatal()
	}
	if err := queued.Transition(NodePruned); err != nil {
		t.Fatal(err)
	}
	if err := queued.Transition(NodeReady); err == nil {
		t.Fatal("expected error from pruned")
	}

	ready := NewNodeMachine(0, 0)
	if err := ready.Transition(NodePruned); err != nil {
		t.Fatal(err)
	}
	if err := ready.Transition(NodeActive); err == nil {
		t.Fatal("expected error")
	}
}

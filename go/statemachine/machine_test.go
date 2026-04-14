package statemachine_test

import (
	"testing"

	"github.com/Flashlock/kahn-queue/go/statemachine"
)

type phase string

const (
	pA phase = "A"
	pB phase = "B"
	pC phase = "C"
)

func transitionsAB_BC() map[phase]map[phase]struct{} {
	return map[phase]map[phase]struct{}{
		pA: {pB: {}},
		pB: {pC: {}},
		pC: {},
	}
}

func TestStartsInInitialState(t *testing.T) {
	sm := statemachine.New(pA, transitionsAB_BC())
	if sm.State() != pA || !sm.Is(pA) {
		t.Fatalf("expected initial A")
	}
}

func TestCanTransition_trueWhenEdgeExists(t *testing.T) {
	sm := statemachine.New(pA, transitionsAB_BC())
	if !sm.CanTransition(pB) || sm.CanTransition(pC) {
		t.Fatalf("canTransition mismatch")
	}
}

func TestTransitionUpdatesStateWhenAllowed(t *testing.T) {
	sm := statemachine.New(pA, transitionsAB_BC())
	if err := sm.Transition(pB); err != nil {
		t.Fatal(err)
	}
	if sm.State() != pB || !sm.Is(pB) {
		t.Fatalf("expected B")
	}
}

func TestTransitionThrowsWhenDisallowed(t *testing.T) {
	sm := statemachine.New(pA, transitionsAB_BC())
	err := sm.Transition(pC)
	if err == nil {
		t.Fatal("expected error")
	}
	if got := err.Error(); got != "invalid transition: A → C" {
		t.Fatalf("message: got %q", got)
	}
}

func TestWhenCurrentStateMissingFromMap_cannotMove(t *testing.T) {
	onlyFromA := map[phase]map[phase]struct{}{
		pA: {pB: {}},
	}
	sm := statemachine.New(pB, onlyFromA)
	if sm.CanTransition(pA) {
		t.Fatal("expected false")
	}
	if err := sm.Transition(pA); err == nil {
		t.Fatal("expected error")
	}
}

func TestChainedTransitions_followMap(t *testing.T) {
	sm := statemachine.New(pA, transitionsAB_BC())
	if err := sm.Transition(pB); err != nil {
		t.Fatal(err)
	}
	if err := sm.Transition(pC); err != nil {
		t.Fatal(err)
	}
	if sm.State() != pC {
		t.Fatalf("expected C")
	}
	if sm.CanTransition(pA) {
		t.Fatal("expected no edge from C to A")
	}
}

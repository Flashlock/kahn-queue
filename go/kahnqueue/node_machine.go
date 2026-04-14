package kahnqueue

import (
	"errors"
	"fmt"

	"github.com/Flashlock/kahn-queue/go/statemachine"
)

// NodeMachine embeds a [statemachine.Machine] for node lifecycle plus Kahn predecessor counts.
type NodeMachine struct {
	*statemachine.Machine[NodeState]
	ID         int
	NumSources int
}

// NewNodeMachine creates a machine for id with numSources incoming edges.
func NewNodeMachine(id, numSources int) *NodeMachine {
	m := &NodeMachine{
		Machine:    statemachine.New(NodeQueued, nodeTransitionTable()),
		ID:         id,
		NumSources: numSources,
	}
	m.tryReady()
	return m
}

// CanTransition applies the Java NodeMachine guard, then the transition table.
func (m *NodeMachine) CanTransition(to NodeState) bool {
	if m.Is(NodeQueued) && m.NumSources > 0 && to == NodeReady {
		return false
	}
	return m.Machine.CanTransition(to)
}

// Transition moves to to if allowed (including NodeMachine-specific rules).
func (m *NodeMachine) Transition(to NodeState) error {
	if !m.CanTransition(to) {
		return fmt.Errorf("invalid transition: %v → %v", m.State(), to)
	}
	m.Machine.SetStateDirect(to)
	return nil
}

// Decrement reduces remaining unsatisfied predecessors; may transition to READY.
func (m *NodeMachine) Decrement() error {
	if m.NumSources == 0 {
		return errors.New("Attempting to decrement below zero")
	}
	m.NumSources--
	m.tryReady()
	return nil
}

func (m *NodeMachine) tryReady() {
	if m.CanTransition(NodeReady) {
		_ = m.Transition(NodeReady)
	}
}

func (m *NodeMachine) forceState(s NodeState) {
	m.Machine.SetStateDirect(s)
}

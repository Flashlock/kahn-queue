package statemachine

import (
	"fmt"
)

// Machine holds one state and transitions allowed from each state.
// S must be comparable so it can be used as a map key (enums, strings, etc.).
type Machine[S comparable] struct {
	state   S
	allowed map[S]map[S]struct{}
}

// New builds a machine starting at initial; allowed maps each state to states it may enter next.
func New[S comparable](initial S, allowed map[S]map[S]struct{}) *Machine[S] {
	return &Machine[S]{state: initial, allowed: allowed}
}

// State returns the current state.
func (m *Machine[S]) State() S { return m.state }

// Is reports whether the current state is s.
func (m *Machine[S]) Is(s S) bool { return m.state == s }

// CanTransition reports whether a transition to to is allowed from the current state.
func (m *Machine[S]) CanTransition(to S) bool {
	next, ok := m.allowed[m.state]
	if !ok {
		return false
	}
	_, ok = next[to]
	return ok
}

// Transition moves to to if allowed.
func (m *Machine[S]) Transition(to S) error {
	if !m.CanTransition(to) {
		return fmt.Errorf("invalid transition: %v → %v", m.state, to)
	}
	m.state = to
	return nil
}

// SetStateDirect sets the current state without validating transitions (tests only).
func (m *Machine[S]) SetStateDirect(s S) { m.state = s }

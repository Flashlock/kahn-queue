import enum

import pytest

from utils.state_machine import StateMachine


class Phase(enum.Enum):
    A = "A"
    B = "B"
    C = "C"


def transitions_ab_bc():
    return {Phase.A: {Phase.B}, Phase.B: {Phase.C}, Phase.C: set()}


def test_starts_in_initial_state():
    sm = StateMachine(Phase.A, transitions_ab_bc())
    assert sm.state == Phase.A
    assert sm.is_(Phase.A)


def test_can_transition_true_when_edge_exists():
    sm = StateMachine(Phase.A, transitions_ab_bc())
    assert sm.can_transition(Phase.B) is True
    assert sm.can_transition(Phase.C) is False


def test_transition_updates_state_when_allowed():
    sm = StateMachine(Phase.A, transitions_ab_bc())
    sm.transition(Phase.B)
    assert sm.state == Phase.B
    assert sm.is_(Phase.B)


def test_transition_throws_when_disallowed():
    sm = StateMachine(Phase.A, transitions_ab_bc())
    with pytest.raises(RuntimeError) as ex:
        sm.transition(Phase.C)
    assert str(ex.value) == "Invalid transition: Phase.A → Phase.C"


def test_when_current_state_missing_from_map_cannot_move():
    only_from_a = {Phase.A: {Phase.B}}
    sm = StateMachine(Phase.B, only_from_a)
    assert sm.can_transition(Phase.A) is False
    with pytest.raises(RuntimeError):
        sm.transition(Phase.A)


def test_chained_transitions_follow_map():
    sm = StateMachine(Phase.A, transitions_ab_bc())
    sm.transition(Phase.B)
    sm.transition(Phase.C)
    assert sm.state == Phase.C
    assert sm.can_transition(Phase.A) is False


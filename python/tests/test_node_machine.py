import pytest

from kahnQueue.node_machine import NodeMachine
from kahnQueue.node_state import NodeState


def test_create_with_zero_dependencies_starts_ready():
    m = NodeMachine.create(123, 0)
    assert m.id == 123
    assert m.num_sources == 0
    assert m.is_(NodeState.READY)


def test_create_with_positive_dependencies_stays_queued_until_decremented_to_zero():
    m = NodeMachine.create(0, 2)
    assert m.is_(NodeState.QUEUED)
    assert m.can_transition(NodeState.READY) is False

    m.decrement()
    assert m.num_sources == 1
    assert m.is_(NodeState.QUEUED)

    m.decrement()
    assert m.num_sources == 0
    assert m.is_(NodeState.READY)


def test_decrement_throws_if_already_zero():
    m = NodeMachine.create(0, 0)
    with pytest.raises(RuntimeError) as ex:
        m.decrement()
    assert str(ex.value) == "Attempting to decrement below zero"


def test_transitions_follow_expected_lifecycle():
    m = NodeMachine.create(0, 0)
    assert m.is_(NodeState.READY)

    assert m.can_transition(NodeState.ACTIVE)
    m.transition(NodeState.ACTIVE)
    assert m.can_transition(NodeState.COMPLETE)
    m.transition(NodeState.COMPLETE)
    assert m.can_transition(NodeState.PRUNED) is False


def test_pruned_is_terminal_from_any_earlier_phase():
    queued = NodeMachine.create(0, 1)
    assert queued.is_(NodeState.QUEUED)
    queued.transition(NodeState.PRUNED)
    with pytest.raises(RuntimeError):
        queued.transition(NodeState.READY)

    ready = NodeMachine.create(0, 0)
    assert ready.is_(NodeState.READY)
    ready.transition(NodeState.PRUNED)
    with pytest.raises(RuntimeError):
        ready.transition(NodeState.ACTIVE)


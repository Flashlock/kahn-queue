from __future__ import annotations

from dataclasses import dataclass
from typing import Final, Mapping, Set

from kahnQueue.node_state import NodeState
from utils.state_machine import StateMachine

NODE_TRANSITIONS: Final[Mapping[NodeState, Set[NodeState]]] = {
    NodeState.QUEUED: {NodeState.READY, NodeState.PRUNED},
    NodeState.READY: {NodeState.ACTIVE, NodeState.PRUNED},
    NodeState.ACTIVE: {NodeState.COMPLETE, NodeState.PRUNED},
    NodeState.COMPLETE: set(),
    NodeState.PRUNED: set(),
}


@dataclass
class NodeMachine(StateMachine[NodeState]):
    num_sources: int
    id: int

    @classmethod
    def create(cls, id: int, num_sources: int) -> NodeMachine:
        """Create a new NodeMachine and attempt initial transition to READY."""
        m = cls(
            _state=NodeState.QUEUED,
            _transitions=NODE_TRANSITIONS,
            num_sources=num_sources,
            id=id,
        )
        m._try_ready()
        return m

    def can_transition(self, to: NodeState) -> bool:
        if self.is_(NodeState.QUEUED) and self.num_sources > 0 and to == NodeState.READY:
            return False
        return super().can_transition(to)

    def decrement(self) -> None:
        """Decrement num_sources and transition to READY if possible."""
        if self.num_sources <= 0:
            raise RuntimeError("Attempting to decrement below zero")
        self.num_sources -= 1
        self._try_ready()

    def _try_ready(self) -> None:
        if self.can_transition(NodeState.READY):
            self.transition(NodeState.READY)
            
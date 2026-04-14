from __future__ import annotations

from dataclasses import dataclass
from typing import Generic, Mapping, Set, TypeVar

S = TypeVar("S")


@dataclass
class StateMachine(Generic[S]):
    """Holds one enum state; ``transition`` only succeeds when the move is allowed by the map
    passed to the constructor.
    """

    _state: S
    _transitions: Mapping[S, Set[S]]

    @property
    def state(self) -> S:
        """Current state."""
        return self._state

    def is_(self, s: S) -> bool:
        """Whether the current state is ``s``."""
        return self._state == s

    def can_transition(self, to: S) -> bool:
        """Whether a transition to ``to`` is allowed from the current state."""
        return to in self._transitions.get(self._state, set())

    def transition(self, to: S) -> None:
        """Moves to ``to`` if allowed.

        Raises:
            RuntimeError: if the transition is not allowed
        """
        if not self.can_transition(to):
            raise RuntimeError(f"Invalid transition: {self._state} \u2192 {to}")
        self._state = to
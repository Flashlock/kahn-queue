from __future__ import annotations
from typing import Protocol, runtime_checkable, Iterable

@runtime_checkable
class KahnQueue(Protocol):
    """Tracks which DAG nodes are runnable and applies completion or pruning."""

    def pop(self, id: int) -> Iterable[int]:
        """Marks ``id`` completed and returns ids of nodes that became runnable."""
        ...

    def prune(self, id: int) -> Iterable[int]:
        """Marks ``id`` and its descendants pruned; returns every affected node id."""
        ...

    def peek(self) -> Iterable[int]:
        """Node ids currently runnable (not yet active)."""
        ...
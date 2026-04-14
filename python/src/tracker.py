from __future__ import annotations

from typing import Dict
from dag import Dag, validate_node


class NodeProgressTracker:
    """Per-node progress in ``[0, 1]`` for a DAG."""

    def __init__(self, dag: Dag[object]) -> None:
        """Initializes progress for all nodes in the DAG to 0.0."""
        # Standard dicts are fine for thread-safe atomic updates in Python
        self._tracker: Dict[int, float] = {i: 0.0 for i in range(len(dag))}

    def __setitem__(self, node_id: int, value: float) -> None:
        if not 0.0 <= value <= 1.0:
            raise ValueError(f"Progress must be between 0 and 1. {node_id} : {value}")
        self._tracker[node_id] = float(value)

    def __getitem__(self, node_id: int) -> float:
        return self._tracker[node_id]

    @property
    def progress(self) -> float:
        """Aggregate progress across nodes."""
        if not self._tracker:
            return 0.0
        return sum(self._tracker.values()) / len(self._tracker)
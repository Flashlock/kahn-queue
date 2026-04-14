from __future__ import annotations

from enum import StrEnum, auto

class NodeState(StrEnum):
    QUEUED = auto()
    READY = auto()
    ACTIVE = auto()
    COMPLETE = auto()
    PRUNED = auto()

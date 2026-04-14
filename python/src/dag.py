from __future__ import annotations

from collections import deque
from dataclasses import dataclass
from typing import Deque, Generic, Iterable, Iterator, List, Set, Tuple, TypeVar

from exception import IllegalGraphException

T = TypeVar("T")


def validate_node(node_id: int, size: int) -> None:
    """Ensures ``node_id`` is valid for a graph of ``size`` nodes.

    Raises:
        IndexError: if out of range
    """
    if not 0 <= node_id < size:
        raise IndexError(f"Invalid node id: {node_id}")


@dataclass(frozen=True)
class Dag(Generic[T], Iterable[T]):
    """Immutable directed graph of nodes with integer ids and typed payloads.

    Obtain instances via the static :meth:`builder` factory; edges go from source to target.
    """

    _nodes: Tuple[T, ...]
    _adj: Tuple[Tuple[int, ...], ...]
    _rev: Tuple[Tuple[int, ...], ...]

    def __len__(self) -> int:
        """Number of nodes (ids are ``0 .. size()-1``)."""
        return len(self._nodes)

    def size(self) -> int:
        """Number of nodes."""
        return len(self)

    def __getitem__(self, node_id: int) -> T:
        """Payload for node ``node_id``."""
        validate_node(node_id, len(self))
        return self._nodes[node_id]

    def get(self, node_id: int) -> T:
        """Payload for node ``node_id``."""
        return self[node_id]

    def in_degree(self, node_id: int) -> int:
        """Count of incoming edges to ``node_id``."""
        validate_node(node_id, len(self))
        return len(self._rev[node_id])

    def out_degree(self, node_id: int) -> int:
        """Count of outgoing edges from ``node_id``."""
        validate_node(node_id, len(self))
        return len(self._adj[node_id])

    def targets(self, node_id: int) -> Iterable[int]:
        """Successor ids of ``node_id``."""
        validate_node(node_id, len(self))
        return self._adj[node_id]

    def sources(self, node_id: int) -> Iterable[int]:
        """Predecessor ids of ``node_id``."""
        validate_node(node_id, len(self))
        return self._rev[node_id]

    def __iter__(self) -> Iterator[T]:
        """Payloads in id order; use ``targets`` / ``sources`` for edge endpoints."""
        return iter(self._nodes)

    @staticmethod
    def validate_node(node_id: int, size: int) -> None:
        """Ensures ``node_id`` is valid for a graph of ``size`` nodes.

        Raises:
            IndexError: if out of range
        """
        validate_node(node_id, size)

    @classmethod
    def builder(cls) -> DagBuilder[T]:
        """Returns a new mutable ``Builder``."""
        return DagBuilder()


class DagBuilder(Generic[T]):
    """Mutable graph builder."""

    def __init__(self) -> None:
        self._nodes: List[T] = []
        self._adj: List[Set[int]] = []
        self._rev: List[Set[int]] = []

    def add(self, data: T) -> int:
        """Adds a node; returns its id for passing to ``connect``."""
        node_id = len(self._nodes)
        self._nodes.append(data)
        self._adj.append(set())
        self._rev.append(set())
        return node_id

    def connect(self, source: int, target: int) -> DagBuilder[T]:
        """Directed edge ``source`` → ``target`` (ignored if duplicate).

        Raises:
            IllegalGraphException: if ``source == target``
        """
        validate_node(source, len(self._nodes))
        validate_node(target, len(self._nodes))
        
        if source == target:
            raise IllegalGraphException(f"Self-loop not allowed: {source}")
            
        if target not in self._adj[source]:
            self._adj[source].add(target)
            self._rev[target].add(source)
        return self

    def build(self) -> Dag[T]:
        """Build the graph."""
        self._cycle_check()
        return Dag(
            _nodes=tuple(self._nodes),
            _adj=tuple(tuple(sorted(s)) for s in self._adj),
            _rev=tuple(tuple(sorted(s)) for s in self._rev),
        )

    def _cycle_check(self) -> None:
        n = len(self._nodes)
        if n == 0:
            return
            
        indeg = [len(self._rev[i]) for i in range(n)]
        ready: Deque[int] = deque(i for i, d in enumerate(indeg) if d == 0)
        processed = 0
        
        while ready:
            u = ready.popleft()
            processed += 1
            for v in self._adj[u]:
                indeg[v] -= 1
                if indeg[v] == 0:
                    ready.append(v)
                    
        if processed != n:
            raise IllegalGraphException("Graph contains a directed cycle")
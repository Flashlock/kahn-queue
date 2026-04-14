from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, FrozenSet, Generic, Set, TypeVar

from dag import Dag
from kahnQueue.default_kahn_queue import DefaultKahnQueue
from kahnQueue.kahn_queue import KahnQueue

T = TypeVar("T")


@dataclass(frozen=True)
class DagResult:
    completed: FrozenSet[int]
    failed: FrozenSet[int]
    pruned: FrozenSet[int]


class KahnScheduler(Generic[T]):
    """Runs a DAG by handing ready node ids to ``execute_node``. When work for a node finishes,
    report the outcome from the callback.
    """

    def __init__(
        self,
        dag: Dag[T],
        execute_node: Callable[[int, "KahnScheduler[T]"], None],
        queue: KahnQueue | None = None,
    ) -> None:
        """Initializes the scheduler. Use the ``queue`` argument to provide a 
        ConcurrentKahnQueue if running in a multi-threaded environment.
        """
        self._dag = dag
        self._execute_node = execute_node
        self._queue = queue or DefaultKahnQueue(dag)
        
        self._completed: Set[int] = set()
        self._failed: Set[int] = set()
        self._pruned: Set[int] = set()

    def run(self) -> None:
        """Invokes ``execute_node`` for each id the queue reports ready now."""
        if self.is_finished:
            return
        for node_id in self._queue.ready_ids():
            self._execute_node(node_id, self)

    def signal_complete(self, node_id: int) -> None:
        """Completes a node and schedules newly ready nodes. Duplicate completion is ignored."""
        if node_id in self._completed:
            return
        
        self._completed.add(node_id)
        for cid in self._queue.pop(node_id):
            self._execute_node(cid, self)

    def signal_failed(self, node_id: int) -> None:
        """Marks failure and prunes downstream nodes in the queue."""
        if node_id in self._failed:
            return
            
        self._failed.add(node_id)
        removed = set(self._queue.prune(node_id))
        removed.discard(node_id)  # root is failure, not pruned
        self._pruned.update(removed)

    @property
    def is_finished(self) -> bool:
        """Whether every node is completed, failed, or pruned."""
        return (len(self._completed) + len(self._failed) + len(self._pruned)) == len(self._dag)

    def get_result(self) -> DagResult:
        """Copy of outcome sets (may still change until the run is finished)."""
        return DagResult(
            completed=frozenset(self._completed),
            failed=frozenset(self._failed),
            pruned=frozenset(self._pruned),
        )
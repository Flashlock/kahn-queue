from __future__ import annotations

from threading import RLock
from typing import List

from dag import Dag, validate_node
from kahnQueue.node_machine import NodeMachine
from kahnQueue.node_state import NodeState
from kahnQueue.kahn_queue import KahnQueue


class ConcurrentKahnQueue(KahnQueue):
    """``KahnQueue`` for concurrent ``pop`` and ``prune`` calls. ``peek()`` may not
    reflect a consistent snapshot if other threads update the queue at the same time; coordinate
    externally if you need strict ordering or visibility. For single-threaded use, prefer
    ``DefaultKahnQueue``.
    """

    def __init__(self, dag: Dag[object]) -> None:
        """Builds a queue whose readiness matches ``dag``."""
        self._dag = dag
        # Each machine gets its own lock, mirroring the synchronized(nodeMachines[id]) pattern
        self._node_machines: List[NodeMachine] = [
            NodeMachine.create(i, dag.in_degree(i)) for i in range(dag.size())
        ]
        self._locks = [RLock() for _ in range(dag.size())]

    def pop(self, id: int) -> List[int]:
        validate_node(id, self._dag.size())
        
        with self._locks[id]:
            machine = self._node_machines[id]
            if not machine.is_(NodeState.ACTIVE):
                raise ValueError(f"Pop failed. Node {id} is not ACTIVE (state: {machine.state})")
            
            machine.transition(NodeState.COMPLETE)

            promoted: set[int] = set()
            for cid in self._dag.targets(id):
                with self._locks[cid]:
                    child = self._node_machines[cid]
                    child.decrement()
                    
                    if child.can_transition(NodeState.ACTIVE):
                        child.transition(NodeState.ACTIVE)
                        promoted.add(cid)
            return sorted(promoted)

    def prune(self, id: int) -> List[int]:
        validate_node(id, self._dag.size())
        
        affected: set[int] = set()
        stack: List[int] = [id]

        while stack:
            curr = stack.pop()
            
            with self._locks[curr]:
                machine = self._node_machines[curr]
                # Avoid redundant transitions if another thread pruned this branch
                if machine.is_(NodeState.PRUNED):
                    continue
                machine.transition(NodeState.PRUNED)
                affected.add(curr)
            
            # Adding targets outside the lock is safe as the DAG structure is immutable
            stack.extend(self._dag.targets(curr))
            
        return sorted(affected)

    def peek(self) -> List[int]:
        # Deterministic ordering for sequential callers; may not reflect a consistent snapshot
        # under concurrent updates.
        return [m.id for m in self._node_machines if m.is_(NodeState.READY)]
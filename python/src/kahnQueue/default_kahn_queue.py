from __future__ import annotations

from typing import List

from dag import Dag, validate_node
from kahnQueue.node_machine import NodeMachine
from kahnQueue.node_state import NodeState
from kahnQueue.kahn_queue import KahnQueue

class DefaultKahnQueue(KahnQueue):
    """Single-threaded implementation of Kahn's algorithm state tracking."""

    def __init__(self, dag: Dag[object]) -> None:
        self._dag = dag
        self._node_machines: List[NodeMachine] = [
            NodeMachine.create(i, dag.in_degree(i)) for i in range(dag.size())
        ]

    def pop(self, id: int) -> List[int]:
        validate_node(id, self._dag.size())
        machine = self._node_machines[id]
        
        if not machine.is_(NodeState.ACTIVE):
            raise ValueError(f"Pop failed. Node {id} is in state {machine.state}, not ACTIVE")
        
        machine.transition(NodeState.COMPLETE)

        promoted: List[int] = []
        for cid in self._dag.targets(id):
            child = self._node_machines[cid]
            child.decrement()
            # Encapsulated logic: if it's READY, move it to ACTIVE immediately
            # in a single-threaded queue.
            if child.is_(NodeState.READY):
                child.transition(NodeState.ACTIVE)
                promoted.append(cid)
        return promoted

    def prune(self, id: int) -> List[int]:
        validate_node(id, self._dag.size())
        affected: set[int] = set()
        stack: List[int] = [id]
        
        while stack:
            curr = stack.pop()
            machine = self._node_machines[curr]
            
            # Skip if already pruned to avoid cycles/redundancy
            if machine.is_(NodeState.PRUNED):
                continue
                
            machine.transition(NodeState.PRUNED)
            affected.add(curr)
            stack.extend(self._dag.targets(curr))
            
        return sorted(affected)

    def ready_ids(self) -> List[int]:
        # Deterministic: ids are scanned in ascending order.
        return [m.id for m in self._node_machines if m.is_(NodeState.READY)]
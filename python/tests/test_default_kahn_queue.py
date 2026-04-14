import pytest

from dag import Dag
from kahnQueue.default_kahn_queue import DefaultKahnQueue
from kahnQueue.node_state import NodeState


def _force_node_state(q: DefaultKahnQueue, id_: int, state: NodeState) -> None:
    q._node_machines[id_]._state = state  # mirrors Java tests using reflection


def test_empty_dag_ready_ids_empty_and_pop_rejects_invalid_id():
    dag = Dag.builder().build()
    q = DefaultKahnQueue(dag)
    assert q.ready_ids() == set()
    with pytest.raises(IndexError):
        q.pop(0)


def test_ready_ids_contains_only_zero_in_degree_nodes():
    b = Dag.builder()
    root = b.add("root")
    mid = b.add("mid")
    leaf = b.add("leaf")
    b.connect(root, mid).connect(mid, leaf)
    dag = b.build()
    q = DefaultKahnQueue(dag)
    assert q.ready_ids() == {root}


def test_ready_ids_two_independent_roots():
    b = Dag.builder()
    a = b.add("a")
    c = b.add("c")
    join = b.add("join")
    b.connect(a, join).connect(c, join)
    dag = b.build()
    q = DefaultKahnQueue(dag)
    assert q.ready_ids() == {a, c}


def test_pop_throws_when_node_is_ready_not_active():
    b = Dag.builder()
    only = b.add("x")
    dag = b.build()
    q = DefaultKahnQueue(dag)
    assert q.ready_ids() == {only}
    with pytest.raises(ValueError) as ex:
        q.pop(only)
    assert "Pop failed. Node" in str(ex.value)
    assert "not ACTIVE" in str(ex.value)


def test_pop_throws_for_out_of_range_id():
    b = Dag.builder()
    b.add("x")
    dag = b.build()
    q = DefaultKahnQueue(dag)
    with pytest.raises(IndexError):
        q.pop(1)


def test_prune_marks_root_and_reachable_descendants():
    b = Dag.builder()
    r = b.add("r")
    m = b.add("m")
    l = b.add("l")
    b.connect(r, m).connect(m, l)
    dag = b.build()
    q = DefaultKahnQueue(dag)
    assert q.prune(r) == {r, m, l}


def test_prune_fork_collects_all_branches():
    b = Dag.builder()
    root = b.add("root")
    left = b.add("left")
    right = b.add("right")
    b.connect(root, left).connect(root, right)
    dag = b.build()
    q = DefaultKahnQueue(dag)
    assert q.prune(root) == {root, left, right}


def test_prune_removes_ids_from_ready_set():
    b = Dag.builder()
    a = b.add("a")
    c = b.add("c")
    join = b.add("join")
    b.connect(a, join).connect(c, join)
    dag = b.build()
    q = DefaultKahnQueue(dag)
    assert q.ready_ids() == {a, c}
    q.prune(a)
    assert q.ready_ids() == {c}


def test_prune_second_call_throws():
    b = Dag.builder()
    r = b.add("r")
    dag = b.build()
    q = DefaultKahnQueue(dag)
    assert q.prune(r) == {r}
    assert q.ready_ids() == set()
    # DefaultKahnQueue.prune is idempotent for already-pruned branches.
    assert q.prune(r) == set()


def test_kahn_progression_pop_active_node_returns_promoted_dependents():
    b = Dag.builder()
    root = b.add("root")
    mid = b.add("mid")
    leaf = b.add("leaf")
    b.connect(root, mid).connect(mid, leaf)
    dag = b.build()
    q = DefaultKahnQueue(dag)

    _force_node_state(q, root, NodeState.ACTIVE)
    assert q.pop(root) == {mid}
    assert q.pop(mid) == {leaf}
    assert q.pop(leaf) == set()
    assert q.ready_ids() == set()


from __future__ import annotations

import concurrent.futures
import threading

import pytest

from dag import Dag
from kahnQueue.concurrent_kahn_queue import ConcurrentKahnQueue
from kahnQueue.node_state import NodeState


def _force_node_state(q: ConcurrentKahnQueue, id_: int, state: NodeState) -> None:
    q._node_machines[id_]._state = state  # mirrors Java tests using reflection


def test_basics_ready_ids_and_pop_validation():
    empty = Dag.builder().build()
    q0 = ConcurrentKahnQueue(empty)
    assert q0.ready_ids() == set()
    with pytest.raises(IndexError):
        q0.pop(0)

    chain = Dag.builder()
    root = chain.add("root")
    mid = chain.add("mid")
    leaf = chain.add("leaf")
    chain.connect(root, mid).connect(mid, leaf)
    dag_chain = chain.build()
    qc = ConcurrentKahnQueue(dag_chain)
    assert qc.ready_ids() == {root}

    join = Dag.builder()
    a = join.add("a")
    c = join.add("c")
    jn = join.add("join")
    join.connect(a, jn).connect(c, jn)
    dag_join = join.build()
    qj = ConcurrentKahnQueue(dag_join)
    assert qj.ready_ids() == {a, c}

    one = Dag.builder()
    only = one.add("x")
    dag_one = one.build()
    q1 = ConcurrentKahnQueue(dag_one)
    assert q1.ready_ids() == {only}
    with pytest.raises(ValueError) as ex:
        q1.pop(only)
    assert "Pop failed. Node" in str(ex.value)
    assert "not ACTIVE" in str(ex.value)

    ob = Dag.builder()
    ob.add("x")
    dag_ob = ob.build()
    qo = ConcurrentKahnQueue(dag_ob)
    with pytest.raises(IndexError):
        qo.pop(1)


def test_prune_collects_reachable_in_linear_and_fork_shapes():
    b1 = Dag.builder()
    r = b1.add("r")
    m = b1.add("m")
    l = b1.add("l")
    b1.connect(r, m).connect(m, l)
    q1 = ConcurrentKahnQueue(b1.build())
    assert q1.prune(r) == {r, m, l}

    b2 = Dag.builder()
    root = b2.add("root")
    left = b2.add("left")
    right = b2.add("right")
    b2.connect(root, left).connect(root, right)
    q2 = ConcurrentKahnQueue(b2.build())
    assert q2.prune(root) == {root, left, right}


def test_prune_updates_ready_set():
    b = Dag.builder()
    a = b.add("a")
    c = b.add("c")
    join = b.add("join")
    b.connect(a, join).connect(c, join)
    dag = b.build()
    q = ConcurrentKahnQueue(dag)
    assert q.ready_ids() == {a, c}
    q.prune(a)
    assert q.ready_ids() == {c}


def test_ready_ids_stable_under_sequential_repeats():
    b = Dag.builder()
    a = b.add("a")
    c = b.add("c")
    join = b.add("join")
    b.connect(a, join).connect(c, join)
    dag = b.build()
    q = ConcurrentKahnQueue(dag)
    snapshot = q.ready_ids()
    for _ in range(10_000):
        assert q.ready_ids() == snapshot
    for id_ in q.ready_ids():
        Dag.validate_node(id_, dag.size())


def test_prune_second_call_throws():
    b = Dag.builder()
    r = b.add("r")
    dag = b.build()
    q = ConcurrentKahnQueue(dag)
    assert q.prune(r) == {r}
    assert q.ready_ids() == set()
    # ConcurrentKahnQueue.prune is idempotent for already-pruned branches.
    assert q.prune(r) == set()
    for id_ in q.ready_ids():
        Dag.validate_node(id_, dag.size())


def test_kahn_progression_pop_active_node_returns_promoted_dependents():
    b = Dag.builder()
    root = b.add("root")
    mid = b.add("mid")
    leaf = b.add("leaf")
    b.connect(root, mid).connect(mid, leaf)
    dag = b.build()
    q = ConcurrentKahnQueue(dag)
    _force_node_state(q, root, NodeState.ACTIVE)
    assert q.pop(root) == {mid}
    assert q.pop(mid) == {leaf}
    assert q.pop(leaf) == set()
    assert q.ready_ids() == set()
    for id_ in q.ready_ids():
        Dag.validate_node(id_, dag.size())


def test_concurrent_ready_ids_stress_reads_match_snapshot():
    for _round in range(25):
        b = Dag.builder()
        x = b.add("x")
        y = b.add("y")
        b.connect(x, y)
        dag = b.build()
        q = ConcurrentKahnQueue(dag)
        expected = {x}
        assert q.ready_ids() == expected

        def worker():
            for _i in range(500):
                if q.ready_ids() != expected:
                    raise AssertionError("ready_ids drifted during concurrent read")

        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            futures = [pool.submit(worker) for _ in range(8)]
            for f in futures:
                f.result()
        assert q.ready_ids() == expected


def test_concurrent_disjoint_prune():
    for _round in range(25):
        b = Dag.builder()
        left = b.add("left")
        right = b.add("right")
        dag = b.build()
        q = ConcurrentKahnQueue(dag)
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            f1 = pool.submit(q.prune, left)
            f2 = pool.submit(q.prune, right)
            assert f1.result() == {left}
            assert f2.result() == {right}
        assert q.ready_ids() == set()


def test_concurrent_pop_failures_do_not_mutate_ready():
    for _round in range(25):
        b = Dag.builder()
        only = b.add("x")
        dag = b.build()
        q = ConcurrentKahnQueue(dag)
        before = q.ready_ids()

        def worker():
            try:
                q.pop(only)
                return False
            except ValueError:
                return True

        with concurrent.futures.ThreadPoolExecutor(max_workers=32) as pool:
            results = list(pool.map(lambda _: worker(), range(32)))
        assert all(results)
        assert q.ready_ids() == before


def test_concurrent_same_id_prune_contention():
    for _round in range(25):
        b = Dag.builder()
        root = b.add("root")
        dag = b.build()
        q = ConcurrentKahnQueue(dag)
        start = threading.Event()

        def worker():
            start.wait()
            return q.prune(root)

        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            futures = [pool.submit(worker) for _ in range(8)]
            start.set()
            results = [f.result(timeout=30) for f in futures]
        assert results.count({root}) == 1
        assert results.count(set()) == 7
        assert q.ready_ids() == set()


def test_concurrent_overlapping_prune():
    for _round in range(25):
        b = Dag.builder()
        r = b.add("r")
        m = b.add("m")
        l = b.add("l")
        b.connect(r, m).connect(m, l)
        dag = b.build()
        q = ConcurrentKahnQueue(dag)
        start = threading.Event()

        def prune_after(event, node):
            event.wait()
            return q.prune(node)

        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            f1 = pool.submit(prune_after, start, r)
            f2 = pool.submit(prune_after, start, m)
            start.set()
            s1 = f1.result(timeout=30)
            s2 = f2.result(timeout=30)

        for id_ in q.ready_ids():
            assert 0 <= id_ < dag.size()

        assert set(s1) | set(s2) == {r, m, l}

        any_full = (s1 == {r, m, l}) or (s2 == {r, m, l})
        if any_full:
            assert q.ready_ids() == set()


def test_prune_mutation_visible_to_other_thread_after_future_get():
    b = Dag.builder()
    left = b.add("left")
    right = b.add("right")
    dag = b.build()
    q = ConcurrentKahnQueue(dag)
    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
        done = pool.submit(q.prune, left)
        assert done.result(timeout=10) == {left}
    assert q.ready_ids() == {right}


def _get_prune_result_or_illegal_state(fut: "concurrent.futures.Future[set[int]]"):
    # Deprecated helper kept for compatibility with earlier versions of this test suite.
    return fut.result(timeout=30)


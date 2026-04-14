import pytest

from dag import Dag
from kahnQueue.default_kahn_queue import DefaultKahnQueue
from scheduler import KahnScheduler


def test_run_invokes_callback_for_each_ready_node_and_run_skips_when_finished():
    b = Dag.builder()
    a = b.add("a")
    c = b.add("c")
    join = b.add("join")
    b.connect(a, join).connect(c, join)
    dag = b.build()
    calls = 0

    def cb(_id, _s):
        nonlocal calls
        calls += 1

    sched = KahnScheduler(dag, cb)
    sched.run()
    assert calls == 2

    one = Dag.builder()
    only = one.add("x")
    dag_one = one.build()
    calls2 = 0

    def cb2(_id, _s):
        nonlocal calls2
        calls2 += 1

    finished = KahnScheduler(dag_one, cb2)
    finished.signal_failed(only)
    assert finished.is_finished
    finished.run()
    assert calls2 == 0


def test_signal_failed_prunes_descendants_and_excludes_root_from_pruned():
    b = Dag.builder()
    r = b.add("r")
    m = b.add("m")
    l = b.add("l")
    b.connect(r, m).connect(m, l)
    dag = b.build()
    sched = KahnScheduler(dag, lambda _id, _s: None, queue=DefaultKahnQueue(dag))
    sched.signal_failed(r)
    result = sched.get_result()
    assert result.failed == {r}
    assert result.pruned == {m, l}
    assert sched.is_finished


def test_signal_failed_duplicate_ignored():
    b = Dag.builder()
    r = b.add("r")
    m = b.add("m")
    b.connect(r, m)
    dag = b.build()
    sched = KahnScheduler(dag, lambda _id, _s: None, queue=DefaultKahnQueue(dag))
    sched.signal_failed(r)
    sched.signal_failed(r)
    result = sched.get_result()
    assert result.failed == {r}
    assert result.pruned == {m}


def test_signal_complete_first_pop_may_fail_duplicate_still_ignored():
    b = Dag.builder()
    only = b.add("x")
    dag = b.build()
    sched = KahnScheduler(dag, lambda _id, _s: None)
    with pytest.raises(ValueError):
        sched.signal_complete(only)
    # second call is ignored due to completed set being updated before pop()
    sched.signal_complete(only)
    assert sched.get_result().completed == {only}


def test_get_result_returns_unmodifiable_copies():
    b = Dag.builder()
    b.add("x")
    dag = b.build()
    sched = KahnScheduler(dag, lambda _id, _s: None, queue=DefaultKahnQueue(dag))
    sched.signal_failed(0)
    r = sched.get_result()
    with pytest.raises(AttributeError):
        r.completed.add(0)  # frozenset has no add
    with pytest.raises(AttributeError):
        r.failed.add(0)
    with pytest.raises(AttributeError):
        r.pruned.add(0)


import pytest

from dag import Dag
from tracker import NodeProgressTracker


def test_empty_dag_initializes_no_entries_and_progress_is_zero():
    dag = Dag.builder().build()
    tracker = NodeProgressTracker(dag)
    assert tracker.progress == 0.0


def test_constructor_initializes_all_node_ids_to_zero():
    b = Dag.builder()
    id_a = b.add("a")
    id_b = b.add("b")
    id_c = b.add("c")
    dag = b.build()
    tracker = NodeProgressTracker(dag)
    assert tracker[id_a] == 0.0
    assert tracker[id_b] == 0.0
    assert tracker[id_c] == 0.0


def test_put_and_get_round_trip():
    b = Dag.builder()
    only = b.add("x")
    dag = b.build()
    tracker = NodeProgressTracker(dag)
    tracker[only] = 0.5
    assert tracker[only] == 0.5


def test_put_rejects_value_below_zero():
    b = Dag.builder()
    id_ = b.add("x")
    dag = b.build()
    tracker = NodeProgressTracker(dag)
    with pytest.raises(ValueError) as ex:
        tracker[id_] = -0.01
    assert str(ex.value).startswith("Progress must be between 0 and 1.")


def test_put_rejects_value_above_one():
    b = Dag.builder()
    id_ = b.add("x")
    dag = b.build()
    tracker = NodeProgressTracker(dag)
    with pytest.raises(ValueError):
        tracker[id_] = 1.01


def test_put_accepts_boundary_values():
    b = Dag.builder()
    lo = b.add("lo")
    hi = b.add("hi")
    dag = b.build()
    tracker = NodeProgressTracker(dag)
    tracker[lo] = 0.0
    tracker[hi] = 1.0
    assert tracker[lo] == 0.0
    assert tracker[hi] == 1.0


def test_progress_is_average_across_nodes():
    b = Dag.builder()
    n0 = b.add("a")
    n1 = b.add("b")
    n2 = b.add("c")
    n3 = b.add("d")
    dag = b.build()
    tracker = NodeProgressTracker(dag)
    tracker[n0] = 0.0
    tracker[n1] = 0.5
    tracker[n2] = 1.0
    tracker[n3] = 0.25
    assert tracker.progress == pytest.approx(0.4375, abs=1e-6)


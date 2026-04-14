import pytest

from dag import Dag
from exception import IllegalGraphException


def test_empty_graph_has_size_zero():
    dag = Dag.builder().build()
    assert dag.size() == 0


def test_single_node_exposes_payload_and_zero_degrees():
    b = Dag.builder()
    id_ = b.add("solo")
    dag = b.build()
    assert dag.size() == 1
    assert dag.get(id_) == "solo"
    assert dag.in_degree(id_) == 0
    assert dag.out_degree(id_) == 0


def test_linear_chain_degrees_and_adjacency():
    b = Dag.builder()
    a = b.add("a")
    m = b.add("m")
    z = b.add("z")
    b.connect(a, m).connect(m, z)
    dag = b.build()

    assert dag.in_degree(a) == 0
    assert dag.in_degree(m) == 1
    assert dag.in_degree(z) == 1

    assert dag.out_degree(a) == 1
    assert dag.out_degree(m) == 1
    assert dag.out_degree(z) == 0

    assert list(dag.targets(a)) == [m]
    assert list(dag.targets(m)) == [z]
    assert list(dag.targets(z)) == []

    assert list(dag.sources(a)) == []
    assert list(dag.sources(m)) == [a]
    assert list(dag.sources(z)) == [m]


def test_diamond_dag_builds():
    b = Dag.builder()
    root = b.add(0)
    left = b.add(1)
    right = b.add(2)
    sink = b.add(3)
    b.connect(root, left).connect(root, right).connect(left, sink).connect(right, sink)
    dag = b.build()
    assert dag.in_degree(sink) == 2
    assert sorted(list(dag.sources(sink))) == [left, right]


def test_iterator_yields_payloads_in_id_order():
    b = Dag.builder()
    b.add("first")
    b.add("second")
    dag = b.build()
    assert list(dag) == ["first", "second"]


def test_duplicate_edge_connect_is_idempotent():
    b = Dag.builder()
    x = b.add("x")
    y = b.add("y")
    b.connect(x, y).connect(x, y)
    dag = b.build()
    assert dag.out_degree(x) == 1
    assert dag.in_degree(y) == 1


def test_self_loop_throws_illegal_graph_exception():
    b = Dag.builder()
    n = b.add("n")
    with pytest.raises(IllegalGraphException):
        b.connect(n, n)


def test_directed_cycle_throws_illegal_graph_exception_on_build():
    b = Dag.builder()
    u = b.add("u")
    v = b.add("v")
    b.connect(u, v).connect(v, u)
    with pytest.raises(IllegalGraphException):
        b.build()


def test_connect_with_invalid_node_id_throws_index_error():
    b = Dag.builder()
    b.add("only")
    with pytest.raises(IndexError):
        b.connect(0, 1)


def test_get_with_invalid_id_throws_index_error():
    b = Dag.builder()
    b.add("x")
    dag = b.build()
    with pytest.raises(IndexError):
        dag.get(1)
    with pytest.raises(IndexError):
        dag.get(-1)


def test_validate_node_rejects_out_of_range():
    with pytest.raises(IndexError):
        Dag.validate_node(-1, 1)
    with pytest.raises(IndexError):
        Dag.validate_node(1, 1)


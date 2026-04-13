package com.betts;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Immutable directed graph of nodes with integer ids and typed payloads. Obtain instances via the
 * static {@link #builder()} factory; edges go from source to target.
 *
 * @param <T> payload at each node
 */
public class Dag<T> implements Iterable<T> {

  private final Node<T>[] nodes;
  private final BitSet[] adjacency;
  private final BitSet[] reverseAdjacency;

  private Dag(Node<T>[] nodes, BitSet[] adjacency, BitSet[] reverseAdjacency) {
    this.nodes = nodes;
    this.adjacency = adjacency;
    this.reverseAdjacency = reverseAdjacency;
  }

  /** Number of nodes (ids are {@code 0 .. size()-1}). */
  public int size() {
    return nodes.length;
  }

  /** Payload for node {@code id}. */
  public T get(int id) {
    validateNode(id, nodes.length);
    return nodes[id].data;
  }

  /** Count of incoming edges to {@code id}. */
  public int inDegree(int id) {
    validateNode(id, nodes.length);
    return (int) sources(id).count();
  }

  /** Count of outgoing edges from {@code id}. */
  public int outDegree(int id) {
    validateNode(id, nodes.length);
    return (int) targets(id).count();
  }

  /** Successor ids of {@code id}. */
  public IntStream targets(int id) {
    validateNode(id, nodes.length);
    return adjacency[id].stream();
  }

  /** Predecessor ids of {@code id}. */
  public IntStream sources(int id) {
    validateNode(id, nodes.length);
    return reverseAdjacency[id].stream();
  }

  /** Returns a new mutable {@code Builder}. */
  public static <T> Builder<T> builder() {
    return new Builder<>();
  }

  /**
   * Ensures {@code id} is valid for a graph of {@code size} nodes.
   *
   * @throws IndexOutOfBoundsException if out of range
   */
  public static void validateNode(int id, int size) throws IndexOutOfBoundsException {
    if (id < 0 || id >= size) {
      throw new IndexOutOfBoundsException("Invalid node id: " + id);
    }
  }

  /** Payloads in id order; use {@code targets} / {@code sources} for edge endpoints. */
  @SuppressWarnings("NullableProblems")
  @Override
  public Iterator<T> iterator() {
    return new Iterator<>() {
      private int index = 0;

      @Override
      public boolean hasNext() {
        return index < nodes.length;
      }

      @Override
      public T next() {
        return nodes[index++].data;
      }
    };
  }

  /**
   * Mutable graph builder.
   *
   * @param <T> node payload type
   */
  public static final class Builder<T> {
    private final List<Node<T>> nodes = new ArrayList<>();
    private final List<BitSet> adjacency = new ArrayList<>();
    private final List<BitSet> reverseAdjacency = new ArrayList<>();

    private Builder() {}

    /** Adds a node; returns its id for passing to {@code connect}. */
    public int add(T data) {
      int id = nodes.size();

      nodes.add(new Node<>(id, data));
      adjacency.add(new BitSet());
      reverseAdjacency.add(new BitSet());

      return id;
    }

    /**
     * Directed edge {@code source} → {@code target} (ignored if duplicate).
     *
     * @throws IllegalGraphException if {@code source == target}
     */
    public Builder<T> connect(int source, int target) throws IllegalGraphException {
      validateNode(source, nodes.size());
      validateNode(target, nodes.size());
      if (source == target) {
        throw new IllegalGraphException("Self-loop not allowed: " + source);
      }

      BitSet edges = adjacency.get(source);

      if (!edges.get(target)) {
        edges.set(target);
        reverseAdjacency.get(target).set(source);
      }

      return this;
    }

    /** Build the graph. */
    @SuppressWarnings("unchecked")
    public Dag<T> build() throws IllegalGraphException {
      cycleCheck();

      int size = nodes.size();

      Node<T>[] nodeArray = nodes.toArray(new Node[0]);
      BitSet[] adjacencyArray = adjacency.toArray(new BitSet[0]);
      BitSet[] reverseAdjacencyArray = reverseAdjacency.toArray(new BitSet[0]);

      // defensive copy of BitSets (immutability)
      for (int i = 0; i < size; i++) {
        adjacencyArray[i] = (BitSet) adjacencyArray[i].clone();
        reverseAdjacencyArray[i] = (BitSet) reverseAdjacencyArray[i].clone();
      }

      return new Dag<>(nodeArray, adjacencyArray, reverseAdjacencyArray);
    }

    private void cycleCheck() throws IllegalGraphException {
      int n = nodes.size();
      if (n == 0) {
        return;
      }

      int[] inDegree = new int[n];
      for (int i = 0; i < n; i++) {
        inDegree[i] = reverseAdjacency.get(i).cardinality();
      }

      ArrayDeque<Integer> ready = new ArrayDeque<>();
      for (int i = 0; i < n; i++) {
        if (inDegree[i] == 0) {
          ready.addLast(i);
        }
      }

      int processed = 0;
      while (!ready.isEmpty()) {
        int u = ready.removeFirst();
        processed++;
        BitSet outs = adjacency.get(u);
        for (int v = outs.nextSetBit(0); v >= 0; v = outs.nextSetBit(v + 1)) {
          if (--inDegree[v] == 0) {
            ready.addLast(v);
          }
        }
      }

      if (processed != n) {
        throw new IllegalGraphException("Graph contains a directed cycle");
      }
    }
  }

  private record Node<T>(int id, T data) {}
}

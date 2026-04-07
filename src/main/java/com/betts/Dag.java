package com.betts;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Immutable directed graph: nodes have integer ids and payloads, edges point from source to target.
 * Build with {@link #builder()}
 */
public class Dag<T> implements Iterable<T> {

  private final Node<T>[] nodes;
  private final BitSet[] adjacency;
  private final BitSet[] reverseAdjacency;
  private final int[] inDegree;

  Dag(Node<T>[] nodes, BitSet[] adjacency, BitSet[] reverseAdjacency, int[] inDegree) {
    this.nodes = nodes;
    this.adjacency = adjacency;
    this.reverseAdjacency = reverseAdjacency;
    this.inDegree = inDegree;
  }

  /** Number of nodes in this graph. */
  public int size() {
    return nodes.length;
  }

  /** Payload stored for {@code id}. */
  public T get(int id) {
    validateNode(id, nodes.length);
    return nodes[id].data;
  }

  /** Incoming edge count for {@code id}. */
  public int inDegree(int id) {
    validateNode(id, nodes.length);
    return inDegree[id];
  }

  /** Visits each outgoing neighbor of {@code id}. */
  public void forEachChild(int id, IntConsumer consumer) {
    validateNode(id, nodes.length);
    BitSet bs = adjacency[id];
    for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
      consumer.accept(i);
    }
  }

  /** Visits each incoming neighbor of {@code id}. */
  public void forEachParent(int id, IntConsumer consumer) {
    validateNode(id, nodes.length);
    BitSet bs = reverseAdjacency[id];
    for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
      consumer.accept(i);
    }
  }

  /** Creates a {@link Builder}. */
  public static <T> Builder<T> builder() {
    return new Builder<>();
  }

  private static void validateNode(int id, int size) {
    if (id < 0 || id >= size) {
      throw new IndexOutOfBoundsException("Invalid node id: " + id);
    }
  }

  /** Iterates payloads; for adjacency, use {@link #forEachChild} / {@link #forEachParent}. */
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
   * Constructs a {@link Dag}. Safe for concurrent use: {@link #add}, {@link #connect}, and
   * {@link #build} may be invoked from multiple threads.
   */
  public static final class Builder<T> {
    private final List<Node<T>> nodes = new ArrayList<>();
    private final List<BitSet> adjacency = new ArrayList<>();
    private final List<BitSet> reverseAdjacency = new ArrayList<>();
    private final List<Integer> inDegree = new ArrayList<>();

    private Builder() {}

    /** Adds a node; returns its id for {@link #connect(int, int)}. */
    public synchronized int add(T data) {
      int id = nodes.size();

      nodes.add(new Node<>(id, data));
      adjacency.add(new BitSet());
      reverseAdjacency.add(new BitSet());
      inDegree.add(0);

      return id;
    }

    /**
     * Adds a directed edge; ids come from {@link #add(Object)}.
     *
     * @throws IllegalGraphException if {@code source} and {@code target} are the same (self-loop)
     */
    public synchronized Builder<T> connect(int source, int target) {
      validateNode(source, nodes.size());
      validateNode(target, nodes.size());
      if (source == target) {
        throw new IllegalGraphException("Self-loop not allowed: " + source);
      }

      BitSet edges = adjacency.get(source);

      if (!edges.get(target)) {
        edges.set(target);
        reverseAdjacency.get(target).set(source);
        inDegree.set(target, inDegree.get(target) + 1);
      }

      return this;
    }

    /** Returns an immutable {@link Dag}. */
    @SuppressWarnings("unchecked")
    public synchronized Dag<T> build() {
      int size = nodes.size();

      Node<T>[] nodeArray = nodes.toArray(new Node[0]);
      BitSet[] adjacencyArray = adjacency.toArray(new BitSet[0]);
      BitSet[] reverseAdjacencyArray = reverseAdjacency.toArray(new BitSet[0]);

      // defensive copy of BitSets (immutability)
      for (int i = 0; i < size; i++) {
        adjacencyArray[i] = (BitSet) adjacencyArray[i].clone();
        reverseAdjacencyArray[i] = (BitSet) reverseAdjacencyArray[i].clone();
      }

      int[] inDegreeArray = new int[size];
      for (int i = 0; i < size; i++) {
        inDegreeArray[i] = inDegree.get(i);
      }

      return new Dag<>(nodeArray, adjacencyArray, reverseAdjacencyArray, inDegreeArray);
    }
  }

  private record Node<T>(int id, T data) {}
}

package com.betts;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Per-node progress in {@code [0, 1]} for a DAG.
 */
public class NodeProgressTracker {
  private final Map<Integer, Float> tracker;

  /**
   * Full constructor: {@code trackerBacker} supplies the progress map.
   */
  public NodeProgressTracker(Dag<?> dag, Supplier<Map<Integer, Float>> trackerBacker) {
    this.tracker = trackerBacker.get();
    for (int i = 0; i < dag.size(); i++) {
      tracker.put(i, 0f);
    }
  }

  /** Convenience: {@link LinkedHashMap} for the map. */
  public NodeProgressTracker(Dag<?> dag) {
    this(dag, LinkedHashMap::new);
  }

  /** Sets progress for {@code id} (values in {@code [0, 1]}). */
  public void put(int id, float value) {
    if (value < 0 || value > 1) {
      throw new IllegalArgumentException(String.format("Progress must be between 0 and 1. %d : %f", id, value));
    }
    tracker.put(id, value);
  }

  /** Progress for {@code id}. */
  public float get(int id) {
    return tracker.get(id);
  }

  /** Aggregate progress across nodes. */
  public float progress() {
    return (float) tracker.values().stream()
        .mapToDouble(Float::doubleValue)
        .average()
        .orElse(0.0);
  }
}

package com.betts;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NodeProgressTracker {
  private final Map<Integer, Float> tracker = new ConcurrentHashMap<>();

  public NodeProgressTracker(List<Integer> nodeIds) {
    nodeIds.forEach(id -> tracker.put(id, 0f));
  }

  public void put(int id, float value) {
    if (value < 0 || value > 1) {
      throw new IllegalArgumentException(String.format("Progress must be between 0 and 1. %d : %f", id, value));
    }
    tracker.put(id, value);
  }

  public float get(int id) {
    return tracker.get(id);
  }

  public float progress() {
    return (float) tracker.values().stream()
      .mapToDouble(Float::doubleValue)
      .average()
      .orElse(0.0);
  }
}

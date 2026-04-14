import { describe, expect, test } from "vitest";

import { Dag, NodeProgressTracker } from "../src";

describe("NodeProgressTracker", () => {
  test("emptyDag_initializesNoEntries_andProgressIsZero", () => {
    const dag = Dag.builder<string>().build();
    const tracker = new NodeProgressTracker(dag);
    expect(tracker.progress).toBe(0);
  });

  test("constructor_initializesAllNodeIdsToZero", () => {
    const b = Dag.builder<string>();
    const idA = b.add("a");
    const idB = b.add("b");
    const idC = b.add("c");
    const dag = b.build();
    const tracker = new NodeProgressTracker(dag);
    expect(tracker.get(idA)).toBe(0);
    expect(tracker.get(idB)).toBe(0);
    expect(tracker.get(idC)).toBe(0);
  });

  test("putAndGet_roundTrip", () => {
    const b = Dag.builder<string>();
    const only = b.add("x");
    const dag = b.build();
    const tracker = new NodeProgressTracker(dag);
    tracker.put(only, 0.5);
    expect(tracker.get(only)).toBeCloseTo(0.5, 6);
  });

  test("put_rejectsValueBelowZero", () => {
    const b = Dag.builder<string>();
    const id = b.add("x");
    const dag = b.build();
    const tracker = new NodeProgressTracker(dag);
    expect(() => tracker.put(id, -0.01)).toThrowError(/Progress must be between 0 and 1\./);
  });

  test("put_rejectsValueAboveOne", () => {
    const b = Dag.builder<string>();
    const id = b.add("x");
    const dag = b.build();
    const tracker = new NodeProgressTracker(dag);
    expect(() => tracker.put(id, 1.01)).toThrow();
  });

  test("put_acceptsBoundaryValues", () => {
    const b = Dag.builder<string>();
    const lo = b.add("lo");
    const hi = b.add("hi");
    const dag = b.build();
    const tracker = new NodeProgressTracker(dag);
    tracker.put(lo, 0);
    tracker.put(hi, 1);
    expect(tracker.get(lo)).toBe(0);
    expect(tracker.get(hi)).toBe(1);
  });

  test("progress_isAverageAcrossNodes", () => {
    const b = Dag.builder<string>();
    const n0 = b.add("a");
    const n1 = b.add("b");
    const n2 = b.add("c");
    const n3 = b.add("d");
    const dag = b.build();
    const tracker = new NodeProgressTracker(dag);
    tracker.put(n0, 0);
    tracker.put(n1, 0.5);
    tracker.put(n2, 1);
    tracker.put(n3, 0.25);
    expect(tracker.progress).toBeCloseTo(0.4375, 6);
  });

  test("customMapSupplier_isPopulatedAndUsed", () => {
    const b = Dag.builder<string>();
    const first = b.add("a");
    const second = b.add("b");
    const dag = b.build();
    const backing = new Map<number, number>();
    const tracker = new NodeProgressTracker(dag, () => backing);
    expect(backing.size).toBe(2);
    expect(backing.get(first)).toBe(0);
    expect(backing.get(second)).toBe(0);
    tracker.put(first, 1);
    tracker.put(second, 0);
    expect(tracker.progress).toBeCloseTo(0.5, 6);
    expect(backing.get(first)).toBe(1);
  });
});

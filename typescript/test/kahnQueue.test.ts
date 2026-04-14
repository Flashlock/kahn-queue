import { describe, expect, test } from "vitest";

import { Dag, KahnQueue } from "../src";

describe("KahnQueue", () => {
  test("emptyDag_readyIdsEmpty_andPopRejectsInvalidId", () => {
    const dag = Dag.builder<string>().build();
    const q = new KahnQueue(dag);
    expect(q.readyIds().size).toBe(0);
    expect(() => q.pop(0)).toThrow(RangeError);
  });

  test("readyIds_containsOnlyZeroInDegreeNodes", () => {
    const b = Dag.builder<string>();
    const root = b.add("root");
    const mid = b.add("mid");
    const leaf = b.add("leaf");
    b.connect(root, mid).connect(mid, leaf);
    const dag = b.build();
    const q = new KahnQueue(dag);
    expect(q.readyIds()).toEqual(new Set([root]));
  });

  test("readyIds_twoIndependentRoots", () => {
    const b = Dag.builder<string>();
    const a = b.add("a");
    const c = b.add("c");
    const join = b.add("join");
    b.connect(a, join).connect(c, join);
    const dag = b.build();
    const q = new KahnQueue(dag);
    expect(q.readyIds()).toEqual(new Set([a, c]));
  });

  test("pop_throwsWhenNodeIsReadyNotActive", () => {
    const b = Dag.builder<string>();
    const only = b.add("x");
    const dag = b.build();
    const q = new KahnQueue(dag);
    expect(q.readyIds()).toEqual(new Set([only]));
    expect(() => q.pop(only)).toThrowError(`Pop failed. Node ${only} is not active`);
  });

  test("pop_throwsForOutOfRangeId", () => {
    const b = Dag.builder<string>();
    b.add("x");
    const dag = b.build();
    const q = new KahnQueue(dag);
    expect(() => q.pop(1)).toThrow(RangeError);
  });

  test("prune_marksRootAndReachableDescendants", () => {
    const b = Dag.builder<string>();
    const r = b.add("r");
    const m = b.add("m");
    const l = b.add("l");
    b.connect(r, m).connect(m, l);
    const dag = b.build();
    const q = new KahnQueue(dag);
    expect(q.prune(r)).toEqual(new Set([r, m, l]));
  });

  test("prune_forkCollectsAllBranches", () => {
    const b = Dag.builder<string>();
    const root = b.add("root");
    const left = b.add("left");
    const right = b.add("right");
    b.connect(root, left).connect(root, right);
    const dag = b.build();
    const q = new KahnQueue(dag);
    expect(q.prune(root)).toEqual(new Set([root, left, right]));
  });

  test("prune_removesIdsFromReadySet", () => {
    const b = Dag.builder<string>();
    const a = b.add("a");
    const c = b.add("c");
    const join = b.add("join");
    b.connect(a, join).connect(c, join);
    const dag = b.build();
    const q = new KahnQueue(dag);
    expect(q.readyIds()).toEqual(new Set([a, c]));
    q.prune(a);
    expect(q.readyIds()).toEqual(new Set([c]));
  });

  test("prune_secondCallThrows", () => {
    const b = Dag.builder<string>();
    const r = b.add("r");
    const dag = b.build();
    const q = new KahnQueue(dag);
    expect(q.prune(r)).toEqual(new Set([r]));
    expect(q.readyIds().size).toBe(0);
    expect(() => q.prune(r)).toThrow();
  });
});

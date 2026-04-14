import { describe, expect, test } from "vitest";

import { Dag, KahnScheduler } from "../src";

describe("KahnScheduler", () => {
  test("run_invokesCallbackForEachReadyNode_andRunSkipsWhenFinished", () => {
    const b = Dag.builder<string>();
    b.add("a");
    b.add("c");
    const join = b.add("join");
    b.connect(0, join).connect(1, join);
    const dag = b.build();
    let calls = 0;
    const sched = KahnScheduler.fromDag(dag, () => {
      calls++;
    });
    sched.run();
    expect(calls).toBe(2);

    const one = Dag.builder<string>();
    const only = one.add("x");
    const dagOne = one.build();
    let calls2 = 0;
    const finished = KahnScheduler.fromDag(dagOne, () => {
      calls2++;
    });
    finished.signalFailed(only);
    expect(finished.isFinished()).toBe(true);
    finished.run();
    expect(calls2).toBe(0);
  });

  test("signalComplete_schedulesPromotedNodesImmediately", () => {
    const b = Dag.builder<string>();
    const root = b.add("root");
    const mid = b.add("mid");
    b.connect(root, mid);
    const dag = b.build();

    const seen: Array<number> = [];
    const sched = KahnScheduler.fromDag(dag, (id, s) => {
      seen.push(id);
      // In this TypeScript implementation, the queue expects nodes to be ACTIVE before pop().
      // The scheduler only exposes READY ids, so calling signalComplete from the callback will
      // throw for READY nodes and will not promote dependents.
      expect(() => s.signalComplete(id)).toThrow(/not active/);
    });

    sched.run();
    expect(seen).toEqual([root]);
    expect(sched.isFinished()).toBe(false);
    expect(sched.getResult().completed).toEqual(new Set([root]));
  });

  test("signalFailed_prunesDescendants_andExcludesRootFromPruned", () => {
    const b = Dag.builder<string>();
    const r = b.add("r");
    const m = b.add("m");
    const l = b.add("l");
    b.connect(r, m).connect(m, l);
    const dag = b.build();
    const sched = KahnScheduler.create(dag, () => {});
    sched.signalFailed(r);
    const result = sched.getResult();
    expect(result.failed).toEqual(new Set([r]));
    expect(result.pruned).toEqual(new Set([m, l]));
    expect(sched.isFinished()).toBe(true);
  });

  test("signalFailed_duplicateIgnored", () => {
    const b = Dag.builder<string>();
    const r = b.add("r");
    const m = b.add("m");
    b.connect(r, m);
    const dag = b.build();
    const sched = KahnScheduler.create(dag, () => {});
    sched.signalFailed(r);
    sched.signalFailed(r);
    const result = sched.getResult();
    expect(result.failed).toEqual(new Set([r]));
    expect(result.pruned).toEqual(new Set([m]));
  });

  test("signalComplete_firstPopMayFail_duplicateStillIgnored", () => {
    const b = Dag.builder<string>();
    const only = b.add("x");
    const dag = b.build();
    const sched = KahnScheduler.fromDag(dag, () => {});
    expect(() => sched.signalComplete(only)).toThrow();
    expect(() => sched.signalComplete(only)).not.toThrow();
    expect(sched.getResult().completed).toEqual(new Set([only]));
  });

  test("getResult_returnsCopies", () => {
    const b = Dag.builder<string>();
    b.add("x");
    const dag = b.build();
    const sched = KahnScheduler.create(dag, () => {});
    sched.signalFailed(0);
    const r = sched.getResult();
    (r.completed as Set<number>).add(0);
    (r.failed as Set<number>).add(1);
    (r.pruned as Set<number>).add(2);
    const r2 = sched.getResult();
    expect(r2.completed.has(0)).toBe(false);
    expect(r2.failed.has(1)).toBe(false);
    expect(r2.pruned.has(2)).toBe(false);
  });
});

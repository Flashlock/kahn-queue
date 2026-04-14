import { describe, expect, test } from "vitest";

import { NodeMachine } from "../src/kahnQueue/nodeMachine.js";

describe("NodeMachine", () => {
  test("create_withZeroDependencies_startsReady", () => {
    const m = NodeMachine.create(123, 0);
    expect(m.id).toBe(123);
    expect(m.numSources).toBe(0);
    expect(m.is("READY")).toBe(true);
  });

  test("create_withPositiveDependencies_staysQueuedUntilDecrementedToZero", () => {
    const m = NodeMachine.create(0, 2);
    expect(m.is("QUEUED")).toBe(true);
    expect(m.canTransition("READY")).toBe(false);

    m.decrement();
    expect(m.numSources).toBe(1);
    expect(m.is("QUEUED")).toBe(true);

    m.decrement();
    expect(m.numSources).toBe(0);
    expect(m.is("READY")).toBe(true);
  });

  test("decrement_throwsIfAlreadyZero", () => {
    const m = NodeMachine.create(0, 0);
    expect(() => m.decrement()).toThrow("Attempting to decrement below zero");
  });

  test("transitions_followExpectedLifecycle", () => {
    const m = NodeMachine.create(0, 0);
    expect(m.is("READY")).toBe(true);

    expect(m.canTransition("ACTIVE")).toBe(true);
    m.transition("ACTIVE");
    expect(m.canTransition("COMPLETE")).toBe(true);
    m.transition("COMPLETE");
    expect(m.canTransition("PRUNED")).toBe(false);
  });

  test("pruned_isTerminal_fromQueuedOrReady", () => {
    const queued = NodeMachine.create(0, 1);
    expect(queued.is("QUEUED")).toBe(true);
    queued.transition("PRUNED");
    expect(() => queued.transition("READY")).toThrow(/Invalid transition/);

    const ready = NodeMachine.create(0, 0);
    expect(ready.is("READY")).toBe(true);
    ready.transition("PRUNED");
    expect(() => ready.transition("ACTIVE")).toThrow(/Invalid transition/);
  });
});

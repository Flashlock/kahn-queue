import { describe, expect, test } from "vitest";

import { StateMachine } from "../src/utils/stateMachine.js";

type Phase = "A" | "B" | "C";

function transitionsAB_BC(): Partial<Record<Phase, Iterable<Phase>>> {
  return {
    A: ["B"],
    B: ["C"],
    C: [],
  };
}

describe("StateMachine", () => {
  test("startsInInitialState", () => {
    const sm = new StateMachine<Phase>("A", transitionsAB_BC());
    expect(sm.state).toBe("A");
    expect(sm.is("A")).toBe(true);
  });

  test("canTransition_trueWhenEdgeExists", () => {
    const sm = new StateMachine<Phase>("A", transitionsAB_BC());
    expect(sm.canTransition("B")).toBe(true);
    expect(sm.canTransition("C")).toBe(false);
  });

  test("transition_updatesStateWhenAllowed", () => {
    const sm = new StateMachine<Phase>("A", transitionsAB_BC());
    sm.transition("B");
    expect(sm.state).toBe("B");
    expect(sm.is("B")).toBe(true);
  });

  test("transition_throwsWhenDisallowed", () => {
    const sm = new StateMachine<Phase>("A", transitionsAB_BC());
    expect(() => sm.transition("C")).toThrowError("Invalid transition: A → C");
  });

  test("whenCurrentStateMissingFromMap_cannotMove", () => {
    const onlyFromA: Partial<Record<Phase, Iterable<Phase>>> = { A: ["B"] };
    const sm = new StateMachine<Phase>("B", onlyFromA);
    expect(sm.canTransition("A")).toBe(false);
    expect(() => sm.transition("A")).toThrow();
  });

  test("chainedTransitions_followMap", () => {
    const sm = new StateMachine<Phase>("A", transitionsAB_BC());
    sm.transition("B");
    sm.transition("C");
    expect(sm.state).toBe("C");
    expect(sm.canTransition("A")).toBe(false);
  });
});

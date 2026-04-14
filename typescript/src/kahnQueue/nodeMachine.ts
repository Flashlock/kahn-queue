import { StateMachine } from "../utils/stateMachine.js";

export type NodeState = "QUEUED" | "READY" | "ACTIVE" | "COMPLETE" | "PRUNED";

const NODE_TRANSITIONS: Partial<Record<NodeState, NodeState[]>> = {
  QUEUED: ["READY", "PRUNED"],
  READY: ["ACTIVE", "PRUNED"],
  ACTIVE: ["COMPLETE", "PRUNED"],
  COMPLETE: [],
  PRUNED: [],
};

export class NodeMachine extends StateMachine<NodeState> {
  numSources: number;
  readonly id: number;

  private constructor(initialState: NodeState, id: number, numSources: number) {
    super(initialState, NODE_TRANSITIONS);
    this.id = id;
    this.numSources = numSources;
  }

  static create(id: number, numSources: number): NodeMachine {
    const m = new NodeMachine("QUEUED", id, numSources);
    m.tryReady();
    return m;
  }

  override canTransition(to: NodeState): boolean {
    if (this.is("QUEUED") && this.numSources > 0 && to === "READY") {
      return false;
    }
    return super.canTransition(to);
  }

  decrement(): void {
    if (this.numSources === 0) {
      throw new Error("Attempting to decrement below zero");
    }
    this.numSources--;
    this.tryReady();
  }

  tryReady(): void {
    if (this.canTransition("READY")) {
      this.transition("READY");
    }
  }
}

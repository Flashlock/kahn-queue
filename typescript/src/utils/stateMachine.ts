/**
 * Holds one state; {@link StateMachine.transition} only succeeds when the move is allowed by the map
 * passed to the constructor.
 *
 * @typeParam S state string union
 */
export class StateMachine<S extends string> {
  readonly #transitions: Partial<Record<S, Set<S>>>;
  #state: S;

  constructor(initialState: S, transitions: Partial<Record<S, Iterable<S>>>) {
    this.#state = initialState;
    this.#transitions = {};

    for (const [key, value] of Object.entries(transitions) as [S, Iterable<S>][]) {
      this.#transitions[key] = new Set(value);
    }
  }

  /** Current state. */
  get state(): S {
    return this.#state;
  }

  /** Whether the current state is `s`. */
  is(s: S): boolean {
    return this.#state === s;
  }

  /** Whether a transition to `to` is allowed from the current state. */
  canTransition(to: S): boolean {
    return this.#transitions[this.#state]?.has(to) ?? false;
  }

  /**
   * Moves to `to` if allowed.
   *
   * @throws Error if the transition is not allowed
   */
  transition(to: S): void {
    if (!this.canTransition(to)) {
      throw new Error(`Invalid transition: ${this.#state} → ${to}`);
    }
    this.#state = to;
  }
}

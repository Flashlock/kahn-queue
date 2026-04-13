package com.betts.utils;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Holds one enum state; {@link #transition} only succeeds when the move is allowed by the map
 * passed to the constructor.
 *
 * @param <S> state enum
 */
public class StateMachine<S extends Enum<S>> {

  private final Map<S, Set<S>> transitions;
  private S state;

  /** Starts in {@code initialState}; {@code transitions} maps each state to states it may enter next. */
  public StateMachine(S initialState, Map<S, Set<S>> transitions) {
    this.state = initialState;
    this.transitions = new EnumMap<>(transitions);
  }

  /** Current state. */
  public S state() {
    return state;
  }

  /** Whether a transition to {@code to} is allowed from the current state. */
  public boolean canTransition(S to) {
    Set<S> allowed = transitions.get(state);
    return allowed != null && allowed.contains(to);
  }

  /**
   * Moves to {@code to} if allowed.
   *
   * @throws IllegalStateException if the transition is not allowed
   */
  public void transition(S to) throws IllegalStateException {
    if (!canTransition(to)) {
      throw new IllegalStateException(
          "Invalid transition: " + state + " → " + to);
    }
    state = to;
  }

  /** Whether the current state is {@code s}. */
  public boolean is(S s) {
    return state == s;
  }
}

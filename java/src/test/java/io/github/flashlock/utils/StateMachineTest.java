package io.github.flashlock.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StateMachineTest {

  private enum Phase {
    A,
    B,
    C
  }

  @Test
  void startsInInitialState() {
    StateMachine<Phase> sm = new StateMachine<>(Phase.A, transitionsAB_BC());
    assertEquals(Phase.A, sm.state());
    assertTrue(sm.is(Phase.A));
  }

  @Test
  void canTransition_trueWhenEdgeExists() {
    StateMachine<Phase> sm = new StateMachine<>(Phase.A, transitionsAB_BC());
    assertTrue(sm.canTransition(Phase.B));
    assertFalse(sm.canTransition(Phase.C));
  }

  @Test
  void transition_updatesStateWhenAllowed() {
    StateMachine<Phase> sm = new StateMachine<>(Phase.A, transitionsAB_BC());
    sm.transition(Phase.B);
    assertEquals(Phase.B, sm.state());
    assertTrue(sm.is(Phase.B));
  }

  @Test
  void transition_throwsWhenDisallowed() {
    StateMachine<Phase> sm = new StateMachine<>(Phase.A, transitionsAB_BC());
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> sm.transition(Phase.C));
    assertEquals("Invalid transition: A → C", ex.getMessage());
  }

  @Test
  void whenCurrentStateMissingFromMap_cannotMove() {
    Map<Phase, Set<Phase>> onlyFromA = new EnumMap<>(Phase.class);
    onlyFromA.put(Phase.A, Set.of(Phase.B));
    StateMachine<Phase> sm = new StateMachine<>(Phase.B, onlyFromA);
    assertFalse(sm.canTransition(Phase.A));
    assertThrows(IllegalStateException.class, () -> sm.transition(Phase.A));
  }

  @Test
  void chainedTransitions_followMap() {
    StateMachine<Phase> sm = new StateMachine<>(Phase.A, transitionsAB_BC());
    sm.transition(Phase.B);
    sm.transition(Phase.C);
    assertEquals(Phase.C, sm.state());
    assertFalse(sm.canTransition(Phase.A));
  }

  private static Map<Phase, Set<Phase>> transitionsAB_BC() {
    Map<Phase, Set<Phase>> m = new EnumMap<>(Phase.class);
    m.put(Phase.A, Set.of(Phase.B));
    m.put(Phase.B, Set.of(Phase.C));
    m.put(Phase.C, Set.of());
    return m;
  }
}

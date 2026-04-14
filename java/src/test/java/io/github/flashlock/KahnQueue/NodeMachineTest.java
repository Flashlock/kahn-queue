package io.github.flashlock.KahnQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NodeMachineTest {

  @Test
  void create_withZeroDependencies_startsReady() {
    NodeMachine m = NodeMachine.create(123, 0);
    assertEquals(123, m.id);
    assertEquals(0, m.numSources);
    assertTrue(m.is(NodeState.READY));
  }

  @Test
  void create_withPositiveDependencies_staysQueuedUntilDecrementedToZero() {
    NodeMachine m = NodeMachine.create(0, 2);
    assertTrue(m.is(NodeState.QUEUED));
    assertFalse(m.canTransition(NodeState.READY));

    m.decrement();
    assertEquals(1, m.numSources);
    assertTrue(m.is(NodeState.QUEUED));

    m.decrement();
    assertEquals(0, m.numSources);
    assertTrue(m.is(NodeState.READY));
  }

  @Test
  void decrement_throwsIfAlreadyZero() {
    NodeMachine m = NodeMachine.create(0, 0);
    IllegalStateException ex = assertThrows(IllegalStateException.class, m::decrement);
    assertEquals("Attempting to decrement below zero", ex.getMessage());
  }

  @Test
  void transitions_followExpectedLifecycle() {
    NodeMachine m = NodeMachine.create(0, 0);
    assertTrue(m.is(NodeState.READY));

    assertTrue(m.canTransition(NodeState.ACTIVE));
    m.transition(NodeState.ACTIVE);
    assertTrue(m.canTransition(NodeState.COMPLETE));
    m.transition(NodeState.COMPLETE);
    assertFalse(m.canTransition(NodeState.PRUNED));
  }

  @Test
  void pruned_isTerminal_fromAnyEarlierPhase() {
    NodeMachine queued = NodeMachine.create(0, 1);
    assertTrue(queued.is(NodeState.QUEUED));
    queued.transition(NodeState.PRUNED);
    assertThrows(IllegalStateException.class, () -> queued.transition(NodeState.READY));

    NodeMachine ready = NodeMachine.create(0, 0);
    assertTrue(ready.is(NodeState.READY));
    ready.transition(NodeState.PRUNED);
    assertThrows(IllegalStateException.class, () -> ready.transition(NodeState.ACTIVE));
  }
}


package io.github.flashlock.KahnQueue;

import io.github.flashlock.utils.StateMachine;

import java.util.Map;
import java.util.Set;

final class NodeMachine extends StateMachine<NodeState> {
  private static final Map<NodeState, Set<NodeState>> NODE_TRANSITIONS = Map.of(
      NodeState.QUEUED, Set.of(NodeState.READY, NodeState.PRUNED),
      NodeState.READY, Set.of(NodeState.ACTIVE, NodeState.PRUNED),
      NodeState.ACTIVE, Set.of(NodeState.COMPLETE, NodeState.PRUNED),
      NodeState.COMPLETE, Set.of(),
      NodeState.PRUNED, Set.of()
  );

  int numSources;
  int id;

  private NodeMachine(NodeState initialState, Map<NodeState, Set<NodeState>> transitions) {
    super(initialState, transitions);
  }

  public static NodeMachine create(int id, int numSources) {
    var machine = new NodeMachine(NodeState.QUEUED, NODE_TRANSITIONS);
    machine.numSources = numSources;
    machine.id = id;
    machine.tryReady();
    return machine;
  }

  @Override
  public boolean canTransition(NodeState to) {
    if (is(NodeState.QUEUED) && numSources > 0 && to == NodeState.READY) return false;
    return super.canTransition(to);
  }

  public void decrement() {
    if (numSources == 0) {
      throw new IllegalStateException("Attempting to decrement below zero");
    }
    numSources--;
    tryReady();
  }

  private void tryReady() {
    if (canTransition(NodeState.READY)) {
      transition(NodeState.READY);
    }
  }
}

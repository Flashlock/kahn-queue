package com.betts.KahnQueue;

import com.betts.Dag;
import com.betts.utils.StateMachine;

import java.util.*;
import java.util.stream.Collectors;

/**
 * {@code KahnQueue} for concurrent {@code pop} and {@code prune} calls. {@code readyIds()} may not
 * reflect a consistent snapshot if other threads update the queue at the same time; coordinate
 * externally if you need strict ordering or visibility. For single-threaded use, prefer {@link
 * DefaultKahnQueue}.
 */
public class ConcurrentKahnQueue implements KahnQueue {

  private final Dag<?> dag;
  private final NodeMachine[] nodeMachines;

  /** Builds a queue whose readiness matches {@code dag}. */
  public ConcurrentKahnQueue(Dag<?> dag) {
    this.dag = dag;
    this.nodeMachines = new NodeMachine[dag.size()];
    for (int i = 0; i < dag.size(); i++) {
      nodeMachines[i] = NodeMachine.create(i, dag.inDegree(i));
    }
  }

  @Override
  public Set<Integer> pop(int id) {
    Dag.validateNode(id, dag.size());

    synchronized (nodeMachines[id]) {
      var machine = nodeMachines[id];

      if (!machine.is(NodeState.ACTIVE)) {
        throw new IllegalArgumentException("Pop failed. Node " + id + " is not active");
      }

      machine.transition(NodeState.COMPLETE);

      return dag.targets(id)
          .map(cid -> {
            synchronized (nodeMachines[cid]) {
              var child = nodeMachines[cid];

              child.decrement();

              if (child.canTransition(NodeState.ACTIVE)) {
                child.transition(NodeState.ACTIVE);
                return cid;
              }

              return -1;
            }
          })
          .filter(cid -> cid != -1)
          .boxed()
          .collect(Collectors.toSet());
    }
  }

  @Override
  public Set<Integer> prune(int id) {
    Dag.validateNode(id, dag.size());

    Set<Integer> affected = new HashSet<>();
    Deque<Integer> stack = new ArrayDeque<>();
    stack.push(id);

    while (!stack.isEmpty()) {
      int curr = stack.pop();

      synchronized (nodeMachines[curr]) {
        NodeMachine machine = nodeMachines[curr];
        machine.transition(NodeState.PRUNED);
        affected.add(curr);
      }

      dag.targets(curr).forEach(stack::push);
    }

    return affected;
  }

  @Override
  public Set<Integer> readyIds() {
    return Arrays.stream(nodeMachines)
        .filter(machine -> machine.is(NodeState.READY))
        .mapToInt(machine -> machine.id)
        .boxed()
        .collect(Collectors.toSet());
  }

  private enum NodeState {
    QUEUED,
    READY,
    ACTIVE,
    COMPLETE,
    PRUNED
  }

  private static final Map<NodeState, Set<NodeState>> NODE_TRANSITIONS = Map.of(
      NodeState.QUEUED, Set.of(NodeState.READY, NodeState.PRUNED),
      NodeState.READY, Set.of(NodeState.ACTIVE, NodeState.PRUNED),
      NodeState.ACTIVE, Set.of(NodeState.COMPLETE, NodeState.PRUNED),
      NodeState.COMPLETE, Set.of(),
      NodeState.PRUNED, Set.of()
  );

  private static final class NodeMachine extends StateMachine<NodeState> {
    int numDependencies;
    int id;

    private NodeMachine(NodeState initialState, Map<NodeState, Set<NodeState>> transitions) {
      super(initialState, transitions);
    }

    static NodeMachine create(int id, int numDependencies) {
      var machine = new NodeMachine(NodeState.QUEUED, NODE_TRANSITIONS);
      machine.numDependencies = numDependencies;
      machine.id = id;
      machine.tryReady();
      return machine;
    }

    @Override
    public boolean canTransition(NodeState to) {
      if (is(NodeState.QUEUED) && numDependencies > 0 && to == NodeState.READY) return false;
      return super.canTransition(to);
    }

    void decrement() {
      if (numDependencies == 0) {
        throw new IllegalStateException("Attempting to decrement below zero");
      }
      numDependencies--;
      tryReady();
    }

    void tryReady() {
      if (canTransition(NodeState.READY)) {
        transition(NodeState.READY);
      }
    }
  }
}

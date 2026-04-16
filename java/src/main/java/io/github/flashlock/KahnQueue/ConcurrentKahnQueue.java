package io.github.flashlock.KahnQueue;

import io.github.flashlock.Dag;

import java.util.*;
import java.util.stream.Collectors;

/**
 * {@code KahnQueue} for concurrent {@code pop} and {@code prune} calls. {@code peek()} may not
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
  public Set<Integer> peek() {
    return Arrays.stream(nodeMachines)
        .filter(machine -> machine.is(NodeState.READY))
        .mapToInt(machine -> machine.id)
        .boxed()
        .collect(Collectors.toSet());
  }
}

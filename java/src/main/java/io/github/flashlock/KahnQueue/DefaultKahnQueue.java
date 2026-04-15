package io.github.flashlock.KahnQueue;

import io.github.flashlock.Dag;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Single-threaded {@code KahnQueue}.
 * For concurrent updates, use {@link  ConcurrentKahnQueue}.
 */
public class DefaultKahnQueue implements KahnQueue {

  private final Dag<?> dag;
  private final NodeMachine[] nodeMachines;

  /** Builds a queue whose readiness matches {@code dag}. */
  public DefaultKahnQueue(Dag<?> dag) {
    this.dag = dag;
    this.nodeMachines = new NodeMachine[dag.size()];
    for (int i = 0; i < dag.size(); i++) {
      nodeMachines[i] = NodeMachine.create(i, dag.inDegree(i));
    }
  }

  @Override
  public Set<Integer> pop(int id) {
    Dag.validateNode(id, dag.size());

    var machine = nodeMachines[id];

    if (!machine.is(NodeState.ACTIVE)) {
      throw new IllegalArgumentException("Pop failed. Node " + id + " is not active");
    }

    machine.transition(NodeState.COMPLETE);

    return dag.targets(id)
        .map(
            cid -> {
              var child = nodeMachines[cid];

              child.decrement();

              if (child.canTransition(NodeState.ACTIVE)) {
                child.transition(NodeState.ACTIVE);
                return cid;
              }

              return -1;
            })
        .filter(cid -> cid != -1)
        .boxed()
        .collect(Collectors.toCollection(TreeSet::new));
  }

  @Override
  public Set<Integer> prune(int id) {
    Dag.validateNode(id, dag.size());

    Set<Integer> affected = new TreeSet<>();
    Deque<Integer> stack = new ArrayDeque<>();
    stack.push(id);

    while (!stack.isEmpty()) {
      int curr = stack.pop();

      NodeMachine machine = nodeMachines[curr];
      machine.transition(NodeState.PRUNED);
      affected.add(curr);

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
        .collect(Collectors.toCollection(TreeSet::new));
  }
}

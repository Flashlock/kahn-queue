package io.github.flashlock.KahnQueue;

enum NodeState {
  QUEUED,
  READY,
  ACTIVE,
  COMPLETE,
  PRUNED
}

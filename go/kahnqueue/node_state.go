package kahnqueue

// NodeState is the lifecycle state of a node in the queue.
type NodeState uint8

const (
	NodeQueued NodeState = iota
	NodeReady
	NodeActive
	NodeComplete
	NodePruned
)

func nodeTransitionTable() map[NodeState]map[NodeState]struct{} {
	return map[NodeState]map[NodeState]struct{}{
		NodeQueued:   {NodeReady: {}, NodePruned: {}},
		NodeReady:    {NodeActive: {}, NodePruned: {}},
		NodeActive:   {NodeComplete: {}, NodePruned: {}},
		NodeComplete: {},
		NodePruned:   {},
	}
}

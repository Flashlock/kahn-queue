package dag

import (
	"errors"
	"fmt"
)

// ErrInvalidNode is returned when a node id is out of range for the graph size.
var ErrInvalidNode = errors.New("invalid node id")

// ErrGraphHasCycles is returned when [Builder.Build] detects a directed cycle.
var ErrGraphHasCycles = errors.New("graph contains a directed cycle")

// IllegalGraphError indicates a structural problem such as a self-loop.
type IllegalGraphError struct {
	Msg string
}

func (e *IllegalGraphError) Error() string { return e.Msg }

// ErrSelfLoop wraps a self-edge attempt during connect.
func ErrSelfLoop(node int) error {
	return &IllegalGraphError{Msg: fmt.Sprintf("Self-loop not allowed: %d", node)}
}

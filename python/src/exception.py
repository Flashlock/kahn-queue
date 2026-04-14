"""Exceptions shared across the KahnQueue modules."""


class IllegalGraphException(ValueError):
    """Thrown when connecting a node to itself in ``Dag.Builder`` (self-loop)."""


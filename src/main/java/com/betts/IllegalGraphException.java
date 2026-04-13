package com.betts;

/**
 * Thrown when connecting a node to itself in {@code Dag.Builder} (self-loop).
 */
public final class IllegalGraphException extends IllegalArgumentException {

  /** Creates an exception with the given detail message. */
  public IllegalGraphException(String message) {
    super(message);
  }

  /** Creates an exception with the given detail message and cause. */
  public IllegalGraphException(String message, Throwable cause) {
    super(message, cause);
  }
}

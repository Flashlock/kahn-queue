package com.betts;

/** Thrown when a graph violates constraints required for the operation (e.g. cycle, invalid node id). */
public class IllegalGraphException extends RuntimeException {

  public IllegalGraphException(String message) {
    super(message);
  }

  public IllegalGraphException(String message, Throwable cause) {
    super(message, cause);
  }
}

/**
 * Thrown when connecting a node to itself in `Dag.Builder` (self-loop).
 */
export class IllegalGraphException extends Error {
  override readonly name = "IllegalGraphException";
  readonly cause?: unknown;

  /** Creates an exception with the given detail message. */
  constructor(message: string, options?: { cause?: unknown }) {
    super(message);
    this.cause = options?.cause;
  }
}

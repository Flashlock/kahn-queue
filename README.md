# kahn-queue

**License:** [MIT](LICENSE)

## Quick start

```bash
make help        # build / test / clean / deploy
make test        # run all language test suites
make build       # build all
make clean       # clean all
make deploy      # deploy/publish all (where configured; see make help)
```

## What is a KahnQueue?

A **KahnQueue** is a queue-like datastructure which facilitates topological iteration over a **DAG**.

This is highly relevant when work can be modeled as a **DAG** where each node represents some unit of work, and edges represent dependencies. Each implementation provides single threaded and multi-threaded support, allowing work to run as asynchronously as possible.

The goal of this project is to provide consistent multi-language support for a **KahnQueue**.

## ABI

- **readyNodes()**: Get a collection of nodes which are ready to be ran.
- **pop(someNode)**: Pops a node from the ready nodes, returns a collection of newly ready nodes.
- **prune(someNode)**: Removes a node and its subtree.

## Kahn-Utilities

Since the **KahnQueue** is commonly used in workflow systems, this project also provides some basic utilities around managing workflows.

- **NodeProgressTracker**: Track the progress of each node.
- **KahnScheduler**: A lightweight workflow engine, manages a **KahnQueue** internally to run node workflows.

## Inspiration

### Temporal Workflows
- Temporal is a single threaded environment, use default (single threaded) **KahnQueue**
- Create two workflows: Diagram and Node
- Create the **DAG** as a Diagram Activity
- In the Diagram workflow, use the **KahnScheduler** to schedule Node workflows

## Supported Languages

| Language | Status |
|----------|--------|
| [Java](java/README.md) | Implemented |
| [Go](go/README.md) | alpha |
| [Python](python/README.md) | Implemented |
| [TypeScript](typescript/README.md) | Implemented |

Add new ports as sibling directories with their native tooling; extend the Makefile when each lands.


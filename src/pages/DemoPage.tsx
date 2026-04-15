import { useCallback, useRef, useState } from "react";
import {
  Background,
  BackgroundVariant,
  Handle,
  Panel,
  Position,
  ReactFlow,
  ReactFlowProvider,
  addEdge,
  useEdgesState,
  useNodesState,
  type Connection,
  type Edge,
  type Node,
  type NodeProps,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { NodeProgressTracker } from "@flashlock/kahn-queue";
import { Seo } from "../components/Seo";
import { buildDagFromFlow } from "../demo/buildDagFromFlow";
import { runDagInWaves } from "../demo/dagRunner";
import "./DemoPage.css";

type DagData = { label: string; status: "idle" | "running" | "done"; progress: number };

/** Custom node: data payload + literal `"dag"` type tag (see NodeProps in @xyflow/react). */
type DagFlowNode = Node<DagData, "dag">;

function DagNode(props: NodeProps<DagFlowNode>) {
  const { data } = props;
  return (
    <>
      <Handle className="dag-handle" type="target" position={Position.Top} />
      <div className={`dag-node dag-node--${data.status}`}>
        <span className="dag-node-label">{data.label}</span>
        {data.status === "running" && (
          <div className="dag-node-progress" aria-hidden>
            <div className="dag-node-progress-bar" style={{ width: `${Math.round(data.progress * 100)}%` }} />
          </div>
        )}
      </div>
      <Handle className="dag-handle" type="source" position={Position.Bottom} />
    </>
  );
}

const nodeTypes = { dag: DagNode };

const initialNodes: DagFlowNode[] = [
  { id: "n1", type: "dag", position: { x: 140, y: 20 }, data: { label: "lint", status: "idle", progress: 0 } },
  { id: "n2", type: "dag", position: { x: 140, y: 140 }, data: { label: "compile", status: "idle", progress: 0 } },
  { id: "n3", type: "dag", position: { x: 140, y: 260 }, data: { label: "test", status: "idle", progress: 0 } },
];

const initialEdges: Edge[] = [
  { id: "e1", source: "n1", target: "n2" },
  { id: "e2", source: "n2", target: "n3" },
];

function setStatuses(nodes: DagFlowNode[], rfIds: string[], status: DagData["status"]): DagFlowNode[] {
  const set = new Set(rfIds);
  return nodes.map((n) => (set.has(n.id) ? { ...n, data: { ...n.data, status } } : n));
}

function allIdle(nodes: DagFlowNode[]): DagFlowNode[] {
  return nodes.map((n) => ({ ...n, data: { ...n.data, status: "idle", progress: 0 } }));
}

function setProgress(nodes: DagFlowNode[], rfIds: string[], progress: number): DagFlowNode[] {
  const set = new Set(rfIds);
  return nodes.map((n) => (set.has(n.id) ? { ...n, data: { ...n.data, progress } } : n));
}

function DemoPageInner() {
  const [nodes, setNodes, onNodesChange] = useNodesState<DagFlowNode>(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);
  const [log, setLog] = useState<string[]>([]);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [totalProgress, setTotalProgress] = useState(0);
  const idRef = useRef(4);
  const progressRef = useRef<NodeProgressTracker | null>(null);

  const onConnect = useCallback(
    (c: Connection) =>
      setEdges((eds) =>
        addEdge({ ...c, id: `e-${c.source}-${c.target}-${Date.now()}`, animated: true }, eds),
      ),
    [setEdges],
  );

  const addNode = useCallback(() => {
    const n = idRef.current++;
    const y = 40 + (n - 1) * 56;
    setNodes((ns) => [
      ...ns,
      {
        id: `n${n}`,
        type: "dag",
        position: { x: 40 + (n % 3) * 120, y },
        data: { label: `step ${n}`, status: "idle", progress: 0 },
      },
    ]);
  }, [setNodes]);

  const clearGraph = useCallback(() => {
    setNodes(initialNodes);
    setEdges(initialEdges);
    setLog([]);
    setError(null);
    idRef.current = 4;
    progressRef.current = null;
    setTotalProgress(0);
  }, [setNodes, setEdges]);

  const run = useCallback(async () => {
    setError(null);
    setLog([]);
    if (nodes.length === 0) {
      setError("Add at least one node.");
      return;
    }

    let built: ReturnType<typeof buildDagFromFlow>;
    try {
      built = buildDagFromFlow(nodes, edges);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      return;
    }

    if (built.dag.size === 0) {
      setError("Graph is empty.");
      return;
    }

    setRunning(true);
    setNodes((ns) => allIdle(ns));
    setTotalProgress(0);
    progressRef.current = new NodeProgressTracker(built.dag);

    let wave = 0;
    try {
      await runDagInWaves(built.dag, async (ids) => {
        wave++;
        const rfIds = ids.map((i) => built.indexToRfId[i]);
        const labels = ids.map((i) => built.dag.get(i)).join(", ");
        setLog((prev) => [...prev, `Wave ${wave}: ${labels}`]);
        setNodes((ns) => setStatuses(ns, rfIds, "running"));
        setNodes((ns) => setProgress(ns, rfIds, 0));

        const tracker = progressRef.current;
        const durationMs = 720;
        const tickMs = 60;
        const steps = Math.max(1, Math.floor(durationMs / tickMs));
        for (let s = 1; s <= steps; s++) {
          const p = s / steps;
          if (tracker) {
            for (const id of ids) tracker.put(id, p);
            setTotalProgress(tracker.progress);
          }
          setNodes((ns) => setProgress(ns, rfIds, p));
          await new Promise((r) => setTimeout(r, tickMs));
        }

        setNodes((ns) => setStatuses(ns, rfIds, "done"));
        if (tracker) {
          for (const id of ids) tracker.put(id, 1);
          setTotalProgress(tracker.progress);
        }
        setNodes((ns) => setProgress(ns, rfIds, 1));
        await new Promise((r) => setTimeout(r, 180));
      });
      setLog((prev) => [...prev, "Done — all nodes finished in Kahn order."]);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setRunning(false);
    }
  }, [nodes, edges, setNodes]);

  return (
    <div className="demo-page">
      <Seo
        title="Live demo"
        description="Interactive DAG playground: draw nodes and edges, run a Kahn-style scheduler, and watch parallel-ready waves on the canvas. Uses the TypeScript Dag from Kahn Queue."
      />
      <header className="demo-header">
        <p className="demo-eyebrow">Interactive</p>
        <h1>Scheduler playground</h1>
        <p className="demo-lead">
          Drag nodes, connect edges (direction matters), then run. Each <strong>wave</strong> is the set of nodes with no
          remaining predecessors—mimicking a Kahn ready queue. The TypeScript <code>Dag</code> enforces acyclic graphs on
          build.
        </p>
      </header>

      <div className="demo-shell">
        <div className="demo-flow-wrap">
          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            nodeTypes={nodeTypes}
            fitView
            minZoom={0.4}
            maxZoom={1.5}
            defaultEdgeOptions={{ animated: true }}
            proOptions={{ hideAttribution: true }}
          >
            <Background variant={BackgroundVariant.Dots} gap={18} size={1} color="rgba(148,163,184,0.25)" />
            <Panel position="top-left" className="demo-panel">
              <button type="button" className="demo-btn" onClick={addNode} disabled={running}>
                + Node
              </button>
              <button type="button" className="demo-btn" onClick={clearGraph} disabled={running}>
                Reset sample
              </button>
              <button type="button" className="demo-btn demo-btn-primary" onClick={run} disabled={running}>
                {running ? "Running…" : "Run scheduler"}
              </button>
            </Panel>
          </ReactFlow>
        </div>

        <aside className="demo-side">
          <h2>Wave log</h2>
          <p className="demo-side-hint">Parallel-ready nodes share a wave; the next wave starts when all current work is done.</p>
          <div className="demo-total-progress" aria-hidden>
            <div className="demo-total-progress-track">
              <div
                className="demo-total-progress-bar"
                style={{ width: `${Math.round(totalProgress * 100)}%` }}
              />
            </div>
            <div className="demo-total-progress-label">{Math.round(totalProgress * 100)}%</div>
          </div>
          <ul className="demo-log">
            {log.length === 0 ? <li>&nbsp;</li> : null}
            {log.map((line, i) => (
              <li key={`${i}-${line}`}>{line}</li>
            ))}
          </ul>
          {error && (
            <div className="demo-err" role="alert">
              {error}
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}

export function DemoPage() {
  return (
    <ReactFlowProvider>
      <DemoPageInner />
    </ReactFlowProvider>
  );
}

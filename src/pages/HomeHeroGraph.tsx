import { Dag } from "@flashlock/kahn-queue";
import { useEffect, useMemo, useRef, useState } from "react";
import { runDagInWaves } from "../demo/dagRunner";

type Status = "idle" | "running" | "done";

/** Carried through `Dag`, layout, and UI. */
export type HeroGraphPayload = {
  label: string;
  x: number;
  y: number;
  final?: boolean;
  status: Status;
};

function buildHeroDag(): Dag<HeroGraphPayload> {
  const b = Dag.builder<HeroGraphPayload>();
  const orchestrate = b.add({ label: "orchestrate", x: 59, y: 6, status: "idle" });
  const plan = b.add({ label: "plan", x: 92, y: 22, status: "idle" });
  const retrieve = b.add({ label: "retrieve", x: 66, y: 39, status: "idle" });
  const reason = b.add({ label: "reason", x: 79, y: 58, status: "idle" });
  const act = b.add({ label: "act", x: 73, y: 80, status: "idle" });
  const verify = b.add({ label: "verify", x: 30, y: 90, status: "idle" });
  const reflect = b.add({ label: "reflect", x: 10, y: 76, status: "idle" });
  const ship = b.add({ label: "ship", x: 50, y: 65, status: "idle", final: true });
  b.connect(reflect, verify)
    .connect(verify, ship)
    .connect(act, ship)
    .connect(reason, act)
    .connect(retrieve, ship)
    .connect(orchestrate, retrieve)
    .connect(plan, retrieve)
    .connect(orchestrate, plan);
  return b.build();
}

function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}

function snapshotNodes(dag: Dag<HeroGraphPayload>): HeroGraphPayload[] {
  return Array.from({ length: dag.size }, (_, i) => ({ ...dag.get(i) }));
}

export function HomeHeroGraph() {
  const dag = useMemo(() => buildHeroDag(), []);
  const [nodes, setNodes] = useState<HeroGraphPayload[]>(() => snapshotNodes(dag));
  const cancelledRef = useRef(false);

  useEffect(() => {
    if (typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      return;
    }

    cancelledRef.current = false;

    const loop = async () => {
      while (!cancelledRef.current) {
        setNodes(snapshotNodes(dag));
        await sleep(350);

        try {
          await runDagInWaves(dag, async (ids) => {
            if (cancelledRef.current) return;
            setNodes((prev) =>
              prev.map((node, i) => (ids.includes(i) ? { ...node, status: "running" } : node)),
            );
            await sleep(720);
            if (cancelledRef.current) return;
            setNodes((prev) =>
              prev.map((node, i) => (ids.includes(i) ? { ...node, status: "done" } : node)),
            );
            await sleep(180);
          });
        } catch {
          break;
        }

        if (cancelledRef.current) return;
        await sleep(900);
      }
    };

    void loop();

    return () => {
      cancelledRef.current = true;
    };
  }, [dag]);

  return (
    <div className="home-graph" aria-hidden>
      <svg className="home-graph-svg" viewBox="0 0 100 100" preserveAspectRatio="none">
        {Array.from({ length: dag.size }, (_, i) =>
          dag.targets(i).map((t) => {
            const from = nodes[i];
            const to = nodes[t];
            return (
              <line
                key={`${i}-${t}`}
                x1={from.x}
                y1={from.y}
                x2={to.x}
                y2={to.y}
                className="home-graph-edge"
              />
            );
          }),
        ).flat()}
      </svg>
      <div className="home-graph-nodes">
        {nodes.map((node) => (
          <div
            key={node.label}
            className={`dag-node dag-node--${node.status}${node.final ? " home-graph-node-final" : ""}`}
            style={{ left: `${node.x}%`, top: `${node.y}%` }}
          >
            <span className="dag-node-label">{node.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

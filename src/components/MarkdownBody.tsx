import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { Components } from "react-markdown";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { coldarkDark } from "react-syntax-highlighter/dist/esm/styles/prism";
import "./MarkdownBody.css";

type Props = {
  markdown: string;
};

function isBlockCode(className: string | undefined, raw: string): boolean {
  if (className && /language-[\w-]+/.test(className)) return true;
  return /\n/.test(raw);
}

function normalizeFencedCode(raw: string): string {
  // react-markdown often includes a trailing newline; some sources also prepend a stray newline/space.
  let text = raw.replace(/\n$/, "");
  if (text.startsWith("\n")) text = text.slice(1);
  if (text.startsWith(" ")) text = text.slice(1);
  return text;
}

/** Prism language ids — READMEs often use short tags like `ts`. */
const LANG_ALIASES: Record<string, string> = {
  ts: "typescript",
  tsx: "tsx",
  js: "javascript",
  jsx: "jsx",
  sh: "bash",
  shell: "bash",
  zsh: "bash",
  yml: "yaml",
  md: "markdown",
};

const mdComponents: Components = {
  pre({ children }) {
    return <>{children}</>;
  },
  code({ className, children }) {
    const text = normalizeFencedCode(String(children));

    if (!isBlockCode(className, text)) {
      return <code className={className}>{children}</code>;
    }

    const match = /language-([\w-]+)/.exec(className || "");
    const raw = match?.[1] ?? "text";
    const lang = LANG_ALIASES[raw] ?? raw;

    return (
      <SyntaxHighlighter
        className="md-syntax-block"
        style={coldarkDark}
        language={lang}
        PreTag="div"
        codeTagProps={{ className: "md-syntax-code" }}
        customStyle={{
          margin: "1.15rem 0",
          padding: "1.05rem 1.15rem",
          borderRadius: "0.7rem",
          fontSize: "0.84rem",
          lineHeight: 1.55,
          border: "1px solid rgba(56, 189, 248, 0.14)",
          boxShadow: "0 0 0 1px rgba(15, 23, 42, 0.5) inset, 0 8px 28px rgba(15, 23, 42, 0.45)",
        }}
      >
        {text}
      </SyntaxHighlighter>
    );
  },
};

export function MarkdownBody({ markdown }: Props) {
  return (
    <article className="md-root">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={mdComponents}>
        {markdown}
      </ReactMarkdown>
    </article>
  );
}

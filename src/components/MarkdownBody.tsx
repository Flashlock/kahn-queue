import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import "./MarkdownBody.css";

type Props = {
  markdown: string;
};

export function MarkdownBody({ markdown }: Props) {
  return (
    <article className="md-root">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{markdown}</ReactMarkdown>
    </article>
  );
}

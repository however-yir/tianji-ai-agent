import { isValidElement, useEffect, useMemo, useState } from "react";
import type { ComponentPropsWithoutRef, ReactNode } from "react";
import ReactMarkdown from "react-markdown";
import rehypeHighlight from "rehype-highlight";
import rehypeKatex from "rehype-katex";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import "highlight.js/styles/github.css";
import "katex/dist/katex.min.css";

function extractNodeText(node: ReactNode): string {
  if (typeof node === "string" || typeof node === "number") {
    return String(node);
  }
  if (Array.isArray(node)) {
    return node.map((item) => extractNodeText(item)).join("");
  }
  if (isValidElement<{ children?: ReactNode }>(node)) {
    return extractNodeText(node.props.children);
  }
  return "";
}

async function copyText(text: string): Promise<boolean> {
  if (!text) {
    return false;
  }
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

function MermaidBlock({ chart }: { chart: string }) {
  const [svg, setSvg] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    async function renderChart() {
      try {
        const mermaid = (await import("mermaid")).default;
        mermaid.initialize({
          startOnLoad: false,
          securityLevel: "loose",
          theme: "neutral",
          fontFamily: "Avenir Next, PingFang SC, Helvetica Neue, sans-serif",
        });
        const id = `mermaid-${Math.random().toString(16).slice(2)}`;
        const { svg: nextSvg } = await mermaid.render(id, chart);
        if (!active) {
          return;
        }
        setSvg(nextSvg);
        setError("");
      } catch {
        if (!active) {
          return;
        }
        setError("Mermaid 图表渲染失败");
      }
    }

    void renderChart();

    return () => {
      active = false;
    };
  }, [chart]);

  if (error) {
    return <div className="mermaid-error">{error}</div>;
  }

  if (!svg) {
    return <div className="mermaid-loading">正在渲染 Mermaid 图表...</div>;
  }

  return <div className="mermaid-block" dangerouslySetInnerHTML={{ __html: svg }} />;
}

function CodePanel({
  rawCode,
  children,
}: {
  rawCode: string;
  children: ReactNode;
}) {
  const [copied, setCopied] = useState(false);

  async function onCopyCode() {
    const success = await copyText(rawCode);
    if (success) {
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1400);
    }
  }

  return (
    <div className="md-pre-wrap">
      <button className="copy-code-btn" onClick={() => void onCopyCode()} type="button">
        {copied ? "已复制" : "复制代码"}
      </button>
      <pre>{children}</pre>
    </div>
  );
}

function MarkdownPre({ children }: ComponentPropsWithoutRef<"pre">) {
  const maybeCodeChild = children as ReactNode;
  if (isValidElement<{ className?: string; children?: ReactNode }>(maybeCodeChild)) {
    const className = maybeCodeChild.props.className || "";
    const rawCode = extractNodeText(maybeCodeChild.props.children).replace(/\n$/, "");
    if (className.includes("language-mermaid")) {
      return <MermaidBlock chart={rawCode} />;
    }
    return <CodePanel rawCode={rawCode}>{children}</CodePanel>;
  }

  return <pre>{children}</pre>;
}

export default function RichMarkdown({ content }: { content: string }) {
  const markdown = useMemo(() => content, [content]);

  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm, remarkMath]}
      rehypePlugins={[rehypeHighlight, rehypeKatex]}
      components={{
        pre(props) {
          return <MarkdownPre>{props.children}</MarkdownPre>;
        },
        code(props) {
          const className = props.className || "";
          if (className.startsWith("language-")) {
            return <code className={className}>{props.children}</code>;
          }
          return <code className="inline-code">{props.children}</code>;
        },
        a(props) {
          const href = props.href || "#";
          return (
            <a href={href} target="_blank" rel="noreferrer">
              {props.children}
            </a>
          );
        },
      }}
    >
      {markdown}
    </ReactMarkdown>
  );
}

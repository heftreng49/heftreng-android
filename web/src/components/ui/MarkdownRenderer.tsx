import ReactMarkdown from "react-markdown"
import remarkGfm     from "remark-gfm"

export function MarkdownRenderer({ content }: { content: string }) {
  return (
    <div className="prose prose-sm max-w-none markdown-body">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>
        {content}
      </ReactMarkdown>
      <style jsx global>{`
        .markdown-body { color: var(--on-bg); font-size: 14px; line-height: 1.7; }
        .markdown-body h1,.markdown-body h2,.markdown-body h3 {
          color: var(--on-bg); font-weight: 700; margin: 1.2em 0 0.5em;
        }
        .markdown-body h1 { font-size: 1.5em; }
        .markdown-body h2 { font-size: 1.25em; }
        .markdown-body h3 { font-size: 1.1em; }
        .markdown-body p  { margin: 0.75em 0; }
        .markdown-body a  { color: var(--primary); text-decoration: underline; }
        .markdown-body strong { color: var(--on-bg); font-weight: 700; }
        .markdown-body em { font-style: italic; }
        .markdown-body del { text-decoration: line-through; color: var(--muted); }
        .markdown-body code {
          background: var(--surface-var); border-radius: 4px;
          padding: 2px 6px; font-size: 0.85em; font-family: monospace;
          color: var(--primary-light);
        }
        .markdown-body pre {
          background: var(--surface-var); border-radius: 10px;
          padding: 12px 16px; overflow-x: auto; margin: 1em 0;
        }
        .markdown-body pre code { background: none; padding: 0; }
        .markdown-body blockquote {
          border-left: 3px solid var(--primary); margin: 1em 0;
          padding: 0.5em 1em; background: color-mix(in srgb,var(--primary) 8%,transparent);
          border-radius: 0 8px 8px 0; color: var(--on-surface);
        }
        .markdown-body ul, .markdown-body ol { padding-left: 1.5em; margin: 0.75em 0; }
        .markdown-body li { margin: 0.3em 0; }
        .markdown-body table { width: 100%; border-collapse: collapse; margin: 1em 0; font-size: 13px; }
        .markdown-body th {
          background: var(--surface-var); color: var(--on-bg);
          padding: 8px 12px; text-align: left; font-weight: 600;
          border: 1px solid var(--divider);
        }
        .markdown-body td {
          padding: 8px 12px; border: 1px solid var(--divider);
          color: var(--on-surface);
        }
        .markdown-body tr:nth-child(even) { background: color-mix(in srgb,var(--surface-var) 50%,transparent); }
        .markdown-body hr { border: none; border-top: 1px solid var(--divider); margin: 1.5em 0; }
      `}</style>
    </div>
  )
}

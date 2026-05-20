type JsonDiffViewerProps = {
  before?: unknown;
  after?: unknown;
};

function formatJson(value: unknown): string {
  if (value == null) {
    return 'null';
  }
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  return JSON.stringify(value, null, 2);
}

export function JsonDiffViewer({ before, after }: JsonDiffViewerProps) {
  return (
    <div className="grid gap-4 md:grid-cols-2">
      <section>
        <h3 className="mb-2 font-mono text-[11px] tracking-wider text-muted-foreground uppercase">Trước</h3>
        <pre className="max-h-72 overflow-auto rounded-md border border-border bg-secondary p-3 font-mono text-xs whitespace-pre-wrap text-ink">
          {formatJson(before)}
        </pre>
      </section>
      <section>
        <h3 className="mb-2 font-mono text-[11px] tracking-wider text-muted-foreground uppercase">Sau</h3>
        <pre className="max-h-72 overflow-auto rounded-md border border-border bg-secondary p-3 font-mono text-xs whitespace-pre-wrap text-ink">
          {formatJson(after)}
        </pre>
      </section>
    </div>
  );
}

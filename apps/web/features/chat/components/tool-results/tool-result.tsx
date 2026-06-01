import { renderToolResult } from './dispatch';

export function ToolResult({
  toolName,
  input,
  output,
}: {
  toolName: string;
  input: unknown;
  output: unknown;
}) {
  return renderToolResult({ toolName, input, output });
}

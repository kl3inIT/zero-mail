import { api } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type KnowledgeSnippet = components['schemas']['KnowledgeSnippetResponse'];
export type KnowledgeSnippetRequest = components['schemas']['KnowledgeSnippetRequest'];

function unwrap<T>(
  result: { data?: T; error?: unknown; response: Response },
  fallbackMessage: string,
): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw result.error ?? new Error(fallbackMessage);
  }
  return result.data;
}

export async function listKnowledgeSnippets(): Promise<KnowledgeSnippet[]> {
  const result = await api.GET('/api/knowledge-snippets', {});
  const data = unwrap(result, `/api/knowledge-snippets failed: ${result.response.status}`);
  return data.items ?? [];
}

export async function createKnowledgeSnippet(
  body: KnowledgeSnippetRequest,
): Promise<KnowledgeSnippet> {
  const result = await api.POST('/api/knowledge-snippets', { body });
  return unwrap(result, `/api/knowledge-snippets create failed: ${result.response.status}`);
}

export async function updateKnowledgeSnippet({
  id,
  body,
}: {
  id: string;
  body: KnowledgeSnippetRequest;
}): Promise<KnowledgeSnippet> {
  const result = await api.PUT('/api/knowledge-snippets/{snippetId}', {
    params: { path: { snippetId: id } },
    body,
  });
  return unwrap(result, `/api/knowledge-snippets/${id} update failed: ${result.response.status}`);
}

export async function deleteKnowledgeSnippet(id: string): Promise<void> {
  const result = await api.DELETE('/api/knowledge-snippets/{snippetId}', {
    params: { path: { snippetId: id } },
  });
  if (result.error || !result.response.ok) {
    throw (
      result.error ??
      new Error(`/api/knowledge-snippets/${id} delete failed: ${result.response.status}`)
    );
  }
}

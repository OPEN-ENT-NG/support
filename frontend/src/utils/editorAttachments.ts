import { EditorRef } from '@edifice.io/react/editor';
import { RefObject } from 'react';
import { TicketAttachment } from '~/models';
import { getWorkspaceDocumentProperties } from '~/services';

export interface EditorNode {
  type?: string;
  attrs?: {
    src?: string;
    documentId?: string;
    links?: { dataDocumentId: string; name: string }[];
  };
  content?: EditorNode[];
}

export function extractDocumentIds(nodes: EditorNode[]): string[] {
  return nodes.flatMap((node) => {
    const ids: string[] = [];

    if (node.type === 'custom-image' && node.attrs?.src) {
      const id = node.attrs.src.split('/').pop()?.split('?')[0].split('.')[0];
      if (id) ids.push(id);
    }

    if (node.type === 'attachments' && node.attrs?.links) {
      for (const link of node.attrs.links) {
        if (link.dataDocumentId) ids.push(link.dataDocumentId);
      }
    }

    if (node.content) ids.push(...extractDocumentIds(node.content));

    return ids;
  });
}

export async function buildAttachmentsFromEditor(
  editorRef: RefObject<EditorRef | null>,
): Promise<TicketAttachment[]> {
  const json = editorRef.current?.getContent('json');
  if (!json || typeof json === 'string' || !json.content) return [];

  const ids = extractDocumentIds(json.content as EditorNode[]);
  if (ids.length === 0) return [];

  const docs = await Promise.all(
    ids.map((id) => getWorkspaceDocumentProperties(id)),
  );

  return docs.map((doc) => ({
    id: doc._id,
    name: doc.name,
    size: 1,
  }));
}

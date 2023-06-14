package net.atos.entng.support;

public class WorkspaceAttachment extends Attachment
{
    public WorkspaceAttachment(Long bugTrackerId, String name)
    {
        super(bugTrackerId, name);
    }

    public WorkspaceAttachment(Long bugTrackerId, String name, String contentType, Integer size)
    {
        super(bugTrackerId, name, contentType, size);
    }

    public WorkspaceAttachment(Long bugTrackerId, String name, String documentId)
    {
        super(bugTrackerId, name, documentId, (String) null);
    }

    public WorkspaceAttachment(Long bugTrackerId, String name, String contentType, Integer size, String documentId)
    {
        super(bugTrackerId, name, contentType, size, documentId, null);
    }
}

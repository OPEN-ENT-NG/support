package net.atos.entng.support;

public class WorkspaceAttachment extends Attachment
{
    public WorkspaceAttachment(Integer bugTrackerId, String name)
    {
        super(bugTrackerId, name);
    }

    public WorkspaceAttachment(Integer bugTrackerId, String name, Integer size)
    {
        super(bugTrackerId, name, size);
    }

    public WorkspaceAttachment(Integer bugTrackerId, String name, String documentId)
    {
        super(bugTrackerId, name, documentId, null);
    }

    public WorkspaceAttachment(Integer bugTrackerId, String name, Integer size, String documentId)
    {
        super(bugTrackerId, name, size, documentId, null);
    }
}

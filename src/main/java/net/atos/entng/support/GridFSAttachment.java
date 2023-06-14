package net.atos.entng.support;

public class GridFSAttachment extends Attachment
{
    public GridFSAttachment(Long bugTrackerId, String name)
    {
        super(bugTrackerId, name);
    }

    public GridFSAttachment(Long bugTrackerId, String name, String contentType, Integer size)
    {
        super(bugTrackerId, name, contentType, size);
    }

    public GridFSAttachment(Long bugTrackerId, String name, String fileSystemId)
    {
        super(bugTrackerId, name, null, fileSystemId);
    }

    public GridFSAttachment(Long bugTrackerId, String name, String contentType, Integer size, String fileSystemId)
    {
        super(bugTrackerId, name, contentType, size, null, fileSystemId);
    }
}

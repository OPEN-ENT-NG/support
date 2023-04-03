package net.atos.entng.support;

public class Attachment
{
    public Integer bugTrackerId;
    public String name;
    public Integer size;

    // Only one of these two should be set. Attachments from the ENT should have a documentId, attachments from the bug tracker an fsId
    public String documentId;   // The id of the file in the documents collection
    public String fileSystemId; // The id of the file in the filesystem

    public Attachment(Integer bugTrackerId, String name)
    {
        this(bugTrackerId, name, null);
    }

    public Attachment(Integer bugTrackerId, String name, Integer size)
    {
        this(bugTrackerId, name, size, null, null);
    }

    public Attachment(Integer bugTrackerId, String name, String documentId, String fileSystemId)
    {
        this(bugTrackerId, name, null, documentId, fileSystemId);
    }

    public Attachment(Integer bugTrackerId, String name, Integer size, String documentId, String fileSystemId)
    {
        this.bugTrackerId = bugTrackerId;
        this.name = name;
        this.size = size;
        this.documentId = documentId;
        this.fileSystemId = fileSystemId;
    }
}

package net.atos.entng.support;

public class Attachment
{
    public Long bugTrackerId;

    public String name;
    public Integer size;
    public String contentType;
    public String created;

    // Only one of these two should be set. Attachments from the ENT should have a documentId, attachments from the bug tracker an fsId
    public String documentId;   // The id of the file in the documents collection
    public String fileSystemId; // The id of the file in the filesystem

    public Attachment(Long bugTrackerId, String name)
    {
        this(bugTrackerId, name, (String) null);
    }

    public Attachment(Long bugTrackerId, String name, String contentType)
    {
        this(bugTrackerId, name, contentType, (Integer) null);
    }

    public Attachment(Long bugTrackerId, String name, Integer size)
    {
        this(bugTrackerId, name, null, size, null, null);
    }

    public Attachment(Long bugTrackerId, String name, String contentType, Integer size)
    {
        this(bugTrackerId, name, contentType, size, null, null);
    }

    public Attachment(Long bugTrackerId, String name, String documentId, String fileSystemId)
    {
        this(bugTrackerId, name, null, null, documentId, fileSystemId);
    }

    public Attachment(Long bugTrackerId, String name, String contentType, Integer size, String documentId, String fileSystemId)
    {
        this(bugTrackerId, name, contentType, size, null, documentId, fileSystemId);
    }

    public Attachment(Long bugTrackerId, String name, String contentType, Integer size, String created, String documentId, String fileSystemId)
    {
        this.bugTrackerId = bugTrackerId;
        this.name = name;
        this.size = size;
        this.contentType = contentType;
        this.created = created;
        this.documentId = documentId;
        this.fileSystemId = fileSystemId;
    }

    public boolean equals(Attachment o)
    {
        if(o == null)
            return false;
		else if(this.bugTrackerId == null)
		{
			if(this.documentId != null && this.documentId.equals(o.documentId))
                return true;
			else if(this.fileSystemId != null && this.fileSystemId.equals(o.fileSystemId))
                return true;
            else
                return false;
		}
		else
            return this.bugTrackerId.equals(o.bugTrackerId);
    }

    @Override
    public boolean equals(Object o)
    {
        if(o instanceof Attachment)
            return this.equals((Attachment) o);
        else
            return super.equals(o);
    }
}

package net.atos.entng.support;

import org.entcore.common.utils.Id;
import org.entcore.common.utils.IdObject;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.ArrayList;

public class Issue implements IdObject
{
    public Id<Issue, Long> id;
    private JsonObject content;
    public List<Attachment> attachments = new ArrayList<Attachment>();

    public Issue(Long id)
    {
        this(id, null);
    }

    public Issue(Long id, JsonObject content)
    {
        this.id = new Id<Issue, Long>(id);
        this.setContent(content);
    }

    public Issue(Issue o)
    {
        this.id = o.id;
        this.setContent(o.getContent());
        this.attachments.addAll(o.attachments);
    }

    @Override
    public Id<Issue, Long> getId()
    {
        return this.id;
    }

    public void setContent(JsonObject content)
    {
        this.content = content;
    }

    public JsonObject getContent()
    {
        return this.content == null ? new JsonObject() : this.content;
    }

    public JsonObject toJsonObject()
    {
        JsonArray jsonAttachments = new JsonArray();

        for(Attachment a : this.attachments)
        {
            JsonObject att = new JsonObject()
                .put("id", a.bugTrackerId)
                .put("filename", a.name)
                .put("content_type", a.contentType)
                .put("size", a.size)
                .put("created_on", a.created)
                .put("document_id", a.documentId)
                .put("gridfs_id", a.fileSystemId);
            jsonAttachments.add(att);
        }

        return new JsonObject().put("id", this.id.get()).put("content", this.getContent()).put("attachments", jsonAttachments);
    }
}

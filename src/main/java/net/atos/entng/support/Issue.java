package net.atos.entng.support;

import org.entcore.common.utils.Id;
import org.entcore.common.utils.IdObject;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.ArrayList;

public class Issue implements IdObject
{
    public Id<Issue, Integer> id;
    private JsonObject content;
    public List<Attachment> attachments = new ArrayList<Attachment>();

    public Issue(Integer id)
    {
        this(id, null);
    }

    public Issue(Integer id, JsonObject content)
    {
        this.id = new Id<Issue, Integer>(id);
        this.setContent(content);
    }

    public Issue(Issue o)
    {
        this.id = o.id;
        this.setContent(o.getContent());
    }

    @Override
    public Id<Issue, Integer> getId()
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
            jsonAttachments.add(new JsonObject().put("id", a.bugTrackerId).put("document_id", a.documentId).put("gridfs_id", a.fileSystemId));

        return new JsonObject().put("id", this.id.get()).put("content", this.getContent()).put("attachments", jsonAttachments);
    }
}

package net.atos.entng.support;

import org.entcore.common.utils.Id;
import org.entcore.common.utils.IdObject;

import io.vertx.core.json.JsonObject;

public class Comment implements IdObject
{
    public Id<Comment, Integer> id;
    public String created;
    public String content;
    public String ownerName;

    public Comment(String content)
    {
        this(null, content, null, null);
    }

    @Override
    public Id<Comment, Integer> getId()
    {
        return this.id;
    }

    public Comment(Integer id, String content, String ownerName, String created)
    {
        this.id = new Id<Comment, Integer>(id);
        this.content = content;
        this.ownerName = ownerName;
        this.created = created;
    }

    public JsonObject toJsonObject()
    {
        return new JsonObject().put("id", this.id.get()).put("content", this.content).put("owner_name", this.ownerName).put("created", this.created);
    }
}
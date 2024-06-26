package net.atos.entng.support;

import org.entcore.common.utils.Id;
import org.entcore.common.utils.IdObject;

import io.vertx.core.json.JsonObject;

public class Comment implements IdObject
{
    public final Id<Comment, Long> id;
    public String created;
    public String content;
    public String ownerName;

    public Comment(String content)
    {
        this(null, content, null, null);
    }

    public Comment(String content, String ownerName)
    {
        this(null, content, ownerName, null);
    }

    @Override
    public Id<Comment, Long> getId()
    {
        return this.id;
    }

    public Comment(Long id, String content, String ownerName, String created)
    {
        this.id = new Id<Comment, Long>(id);
        this.content = content;
        this.ownerName = ownerName;
        this.created = created;
    }

    public Comment(Comment o)
    {
        if(o != null)
        {
            this.id = new Id<Comment, Long>(o.id.get());
            this.created = o.created;
            this.content = o.content;
            this.ownerName = o.ownerName;
        }
        else
            this.id = new Id<Comment, Long>(null);
    }


    public JsonObject toJsonObject()
    {
        return new JsonObject().put("id", this.id.get()).put("content", this.content).put("owner_name", this.ownerName).put("created", this.created);
    }
}
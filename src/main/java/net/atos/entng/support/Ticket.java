package net.atos.entng.support;

import net.atos.entng.support.enums.TicketStatus;

import org.entcore.common.utils.Id;
import org.entcore.common.utils.IdObject;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

import java.util.List;
import java.util.ArrayList;

public class Ticket implements IdObject
{
    public Id<Ticket, Integer> id;
    public TicketStatus status;

    public String subject;
    public String description;
    public String shortDescription;
    public String category;
    public String schoolId;
    public String ownerId;
    public String ownerName;
    public String created;
    public String modified;
    public String locale;

    public Integer escalationStatus;
    public String escalationDate;

    public List<Attachment> attachments = new ArrayList<Attachment>();
    public List<Comment> comments = new ArrayList<Comment>();

    public Ticket(Integer id)
    {
        this.id = new Id<Ticket, Integer>(id);
    }

    @Override
    public Id<Ticket, Integer> getId()
    {
        return this.id;
    }

    public JsonObject toJsonObject()
    {
        JsonArray jsonAttachments = new JsonArray();
        JsonArray jsonAttachmentsNames = new JsonArray();
        JsonArray jsonComments = new JsonArray();

        for(Attachment a : this.attachments)
        {
            jsonAttachments.add(a.documentId);
            jsonAttachmentsNames.add(a.name);
        }

        for(Comment m : this.comments)
            jsonComments.add(m.toJsonObject());

        String shortDesc = this.shortDescription != null ? this.shortDescription : this.description != null ? this.description.substring(0, this.description.length() < 101 ? this.description.length() : 101) : null;

        return new JsonObject()
                    .put("id", this.id.get())
                    .put("status", this.status != null ? this.status.status() : null)
                    .put("subject", this.subject)
                    .put("description", this.description)
                    .put("short_desc", shortDesc)
                    .put("category", this.category)
                    .put("school_id", this.schoolId)
                    .put("owner_id", this.ownerId)
                    .put("owner_name", this.ownerName)
                    .put("created", this.created)
                    .put("modified", this.modified)
                    .put("escalation_status", this.escalationStatus)
                    .put("escalation_date", this.escalationDate)
                    .put("attachments", jsonAttachments)
                    .put("attachmentsNames", jsonAttachmentsNames)
                    .put("comments", jsonComments);
    }
}

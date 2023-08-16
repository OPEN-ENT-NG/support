package net.atos.entng.support.model;

import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.Ticket;
import net.atos.entng.support.helpers.IModelHelper;

public class TicketModel implements IModel<TicketModel> {
    private int id;
    private String owner;
    private String subject;
    private String description;
    private String created;
    private String modified;
    private String category;
    private int status;
    private String schoolId;

    public TicketModel() {
    }

    public TicketModel(JsonObject jsonObject) {
        this.id = jsonObject.getInteger(Ticket.ID);
        this.owner = jsonObject.getString(Ticket.OWNER);
        this.subject = jsonObject.getString(Ticket.SUBJECT);
        this.description = jsonObject.getString("description");
        this.created = jsonObject.getString(Ticket.CREATION_DATE);
        this.modified = jsonObject.getString(Ticket.MODIFICATION_DATE);
        this.category = jsonObject.getString(Ticket.CATEGORY);
        this.status = jsonObject.getInteger(Ticket.STATUS);
        this.schoolId = jsonObject.getString(Ticket.SCHOOL_ID);
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, true, true);
    }

    public int getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public String getSubject() {
        return subject;
    }

    public String getDescription() {
        return description;
    }

    public String getCreated() {
        return created;
    }

    public String getModified() {
        return modified;
    }

    public String getCategory() {
        return category;
    }

    public int getStatus() {
        return status;
    }

    public String getSchoolId() {
        return schoolId;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}

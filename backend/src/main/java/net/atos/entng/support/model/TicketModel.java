package net.atos.entng.support.model;

import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.JiraTicket;
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
        this.id = jsonObject.getInteger(JiraTicket.ID);
        this.owner = jsonObject.getString(JiraTicket.OWNER);
        this.subject = jsonObject.getString(JiraTicket.SUBJECT);
        this.description = jsonObject.getString(JiraTicket.DESCRIPTION);
        this.created = jsonObject.getString(JiraTicket.CREATION_DATE);
        this.modified = jsonObject.getString(JiraTicket.MODIFICATION_DATE);
        this.category = jsonObject.getString(JiraTicket.CATEGORY);
        this.status = jsonObject.getInteger(JiraTicket.STATUS);
        this.schoolId = jsonObject.getString(JiraTicket.SCHOOL_ID);
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
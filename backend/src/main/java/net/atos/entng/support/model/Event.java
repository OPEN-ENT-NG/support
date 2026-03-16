package net.atos.entng.support.model;

import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.JiraTicket;
import net.atos.entng.support.helpers.IModelHelper;

public class Event implements IModel<Event>{
    private String username;
    private String event;
    private int status;
    private String eventDate;
    private String userId;
    private int eventType;
    private String schoolId;

    public Event(){
    }

    public Event(JsonObject jsonObject) {
        this.username = jsonObject.getString(JiraTicket.USERNAME);
        this.event = jsonObject.getString(JiraTicket.EVENT);
        this.status = jsonObject.getInteger(JiraTicket.STATUS);
        this.eventDate = jsonObject.getString(JiraTicket.EVENT_DATE);
        this.userId = jsonObject.getString(JiraTicket.USER_ID);
        this.eventType = jsonObject.getInteger(JiraTicket.EVENT_TYPE);
        this.schoolId = jsonObject.getString(JiraTicket.SCHOOL_ID);
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, true, true);
    }

    public String getUsername() {
        return username;
    }

    public String getEvent() {
        return event;
    }

    public int getStatus() {
        return status;
    }

    public String getEventDate() {
        return eventDate;
    }

    public String getUserId() {
        return userId;
    }

    public int getEventType() {
        return eventType;
    }

    public String getSchoolId() {
        return schoolId;
    }

}
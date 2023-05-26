package net.atos.entng.support.model;

import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.Ticket;
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
        this.username = jsonObject.getString(Ticket.USERNAME);
        this.event = jsonObject.getString(Ticket.EVENT);
        this.status = jsonObject.getInteger(Ticket.STATUS);
        this.eventDate = jsonObject.getString(Ticket.EVENT_DATE);
        this.userId = jsonObject.getString(Ticket.USER_ID);
        this.eventType = jsonObject.getInteger(Ticket.EVENT_TYPE);
        this.schoolId = jsonObject.getString(Ticket.SCHOOL_ID);
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

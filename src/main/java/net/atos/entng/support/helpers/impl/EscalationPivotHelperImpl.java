package net.atos.entng.support.helpers.impl;

import io.vertx.core.logging.LoggerFactory;
import net.atos.entng.support.enums.TicketStatus;
import net.atos.entng.support.helpers.EscalationPivotHelper;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;

import java.text.DateFormat;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Pattern;

public class EscalationPivotHelperImpl implements EscalationPivotHelper {

    private final Logger log = LoggerFactory.getLogger(EscalationPivotHelperImpl.class);

    public EscalationPivotHelperImpl() {

    }

    /**
     * Get ENT equivalent of pivot status
     * @param pivotStatus pivot status
     * @return ent status
     */
    @Override
    public int getStatusCorrespondence(JsonObject confStatus, String pivotStatus) {
        if( pivotStatus == null ) {
            return TicketStatus.NEW.status();
        }
        if (confStatus.getString(STATUS_NEW_FIELD ).equals(pivotStatus )) return TicketStatus.NEW.status();
        else if (confStatus.getString(STATUS_OPENED_FIELD ).equals(pivotStatus )) return TicketStatus.OPENED.status();
        else if (confStatus.getString(STATUS_RESOLVED_FIELD ).equals(pivotStatus )) return TicketStatus.RESOLVED.status();
        else if (confStatus.getString(STATUS_CLOSED_FIELD ).equals(pivotStatus )) return TicketStatus.CLOSED.status();
            else  return TicketStatus.NEW.status();
    }

    /**
     * Check if comment must be serialized
     * If it's '|' separated (at least 4 fields)
     * And first field is 14 number (AAAMMJJHHmmSS)
     * Then it must not be serialized
     * @param content Comment to check
     * @return true if the comment has to be serialized
     */
    private boolean hasToSerialize(String content) {
        String[] elements = content.split(Pattern.quote("|"));
        if(elements.length < 4) return true;
        String id = elements[0].trim();
        return ( !id.matches("[0-9]{14}") );
    }

    /**
     * Serialize comments : date | author | content
     * @param comments Json Array with comments to serialize
     * @return Json array with comments serialized
     */
    @Override
    public JsonArray serializeComments (final JsonArray comments) {
        JsonArray finalComments = new JsonArray();
        if(comments != null && comments.size() > 0) {
            for( Object o : comments) {
                if (!(o instanceof JsonObject)) continue;
                JsonObject comment = (JsonObject) o;
                String content = getDateFormatted(comment.getString("created"), true)
                        + " | " + comment.getString("owner_name")
                        + " | " + getDateFormatted(comment.getString("created"), false)
                        + " | " + comment.getString("content");
                String origContent = comment.getString("content");
                finalComments.add(hasToSerialize(origContent) ? content : origContent);
            }
        }
        return finalComments;
    }

    /**
     * Transform a comment from pivot format, to json
     * @param comment Original full '|' separated string
     * @return JsonFormat with correct metadata (owner and date)
     */
    private JsonObject unserializeComment(String comment) {
        try{
            String[] elements = comment.split(Pattern.quote("|"));
            if(elements.length < 2) {
                return null;
            }

            JsonObject jsonComment = new JsonObject();
            jsonComment.put("id", elements[0].trim());

            int start = 1;
            if(elements.length >= 4) {
                jsonComment.put("owner", elements[1].trim());
                jsonComment.put("created", elements[2].trim());
                start = 3;
            }
            StringBuilder content = new StringBuilder();
            for(int i = start; i<elements.length ; i++) {
                content.append(elements[i]);
                content.append("|");
            }
            content.deleteCharAt(content.length() - 1);
            jsonComment.put("content", content.toString());
            return jsonComment;
        } catch (NullPointerException e) {
            return null;
        }
    }

    /**
     * Format date from SQL format : yyyy-MM-dd'T'HH:mm:ss
     * to pivot comment id format : yyyyMMddHHmmss
     * or display format : yyyy-MM-dd HH:mm:ss
     * @param sqlDate date string to format
     * @param idStyle use id format if true
     * @return formatted date string
     */
    private String getDateFormatted (final String sqlDate, final boolean idStyle) {
        final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date d;
        try {
            d = df.parse(sqlDate);
        } catch (ParseException e) {
            log.error("Support : error when parsing date");
            e.printStackTrace();
            return "iderror";
        }
        Format formatter;
        if(idStyle) {
            formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        } else {
            formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }
        return formatter.format(d);
    }

    /**
     * Compare comments of ticket and bugtracker issue.
     * Add every comment to ticket not already existing
     * @param ticketComments comments of ENT ticket
     * @param issueComments comment of Bugtracker issue
     * @return comments that needs to be added in ticket
     */
    @Override
    public JsonArray compareComments(JsonArray ticketComments, JsonArray issueComments) {
        JsonArray commentsToAdd = new JsonArray();
        for(Object oi : issueComments)  {
            if( !(oi instanceof String) ) continue;
            String rawComment = (String)oi;
            JsonObject issueComment = unserializeComment(rawComment);
            String issueCommentId;

            if(issueComment != null && issueComment.containsKey("id")) {
                issueCommentId = issueComment.getString("id", "");
            } else {
                log.error("Support : Invalid comment : " + rawComment);
                continue;
            }

            boolean existing = false;
            for(Object ot : ticketComments) {
                if( !(ot instanceof JsonObject) ) continue;
                JsonObject ticketComment = (JsonObject)ot;
                String ticketCommentCreated = ticketComment.getString("created","").trim();
                String ticketCommentId = getDateFormatted(ticketCommentCreated, true);
                String ticketCommentContent = ticketComment.getString("content", "").trim();
                JsonObject ticketCommentPivotContent = unserializeComment(ticketCommentContent);

                String ticketCommentPivotId = "";
                if( ticketCommentPivotContent != null ) {
                    ticketCommentPivotId = ticketCommentPivotContent.getString("id");
                }
                if(issueCommentId.equals(ticketCommentId)
                        || issueCommentId.equals(ticketCommentPivotId)) {
                    existing = true;
                    break;
                }
            }
            if(!existing) {
                commentsToAdd.add(rawComment);
            }
        }
        return commentsToAdd;
    }
}

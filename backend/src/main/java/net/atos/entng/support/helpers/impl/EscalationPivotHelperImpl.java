package net.atos.entng.support.helpers.impl;

import io.vertx.core.logging.LoggerFactory;
import net.atos.entng.support.enums.TicketStatus;
import net.atos.entng.support.helpers.EscalationPivotHelper;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import org.apache.commons.text.StringEscapeUtils;

import java.text.DateFormat;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Pattern;

public class EscalationPivotHelperImpl implements EscalationPivotHelper {

    private final Logger log = LoggerFactory.getLogger(EscalationPivotHelperImpl.class);

    // A tag starts with a letter after '<' or '</', so plain text such as "3 < 5" is left untouched
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("</?[a-zA-Z][^>]*>");
    private static final String ATTACHMENTS_BLOCK_REGEX = "(?s)<div class=\"attachments\">.*?</div>";
    private static final String LINE_BREAK_REGEX = "(?i)<br\\s*/?>";
    private static final String BLOCK_END_REGEX = "(?i)</(p|div|li|h[1-6]|blockquote|pre)>";
    private static final String TRAILING_WHITESPACE_REGEX = "\\s+$";
    private static final char NON_BREAKING_SPACE = '\u00A0';

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
                String origContent = toPlainText(comment.getString("content"));
                String content = getDateFormatted(comment.getString("created"), true)
                        + " | " + comment.getString("owner_name")
                        + " | " + getDateFormatted(comment.getString("created"), false)
                        + " | " + origContent;
                finalComments.add(hasToSerialize(origContent) ? content : origContent);
            }
        }
        return finalComments;
    }

    /**
     * Convert an HTML comment (rich editor) to plain text, as expected by the pivot format.
     * Since the React rich editor, comments are stored as HTML, but they are sent to IWS inside
     * a mail made of "key=value" lines, which only carried plain text before.
     * Plain text content (no HTML tag) is returned unchanged.
     * @param content comment content, HTML or plain text
     * @return plain text content
     */
    private String toPlainText(final String content) {
        if (content == null || !HTML_TAG_PATTERN.matcher(content).find()) {
            return content;
        }
        String text = content
                .replaceAll(ATTACHMENTS_BLOCK_REGEX, "")
                .replaceAll(LINE_BREAK_REGEX, "\n")
                .replaceAll(BLOCK_END_REGEX, "\n");
        text = HTML_TAG_PATTERN.matcher(text).replaceAll("");
        return StringEscapeUtils.unescapeHtml4(text)
                .replace(NON_BREAKING_SPACE, ' ')
                .replaceAll(TRAILING_WHITESPACE_REGEX, "");
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
            if (!existing) {
                commentsToAdd.add(rawComment.replaceAll("\\r?\\n", "<br>"));
            }
        }
        return commentsToAdd;
    }
}

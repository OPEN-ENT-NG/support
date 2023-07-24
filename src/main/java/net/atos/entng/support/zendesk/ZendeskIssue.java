package net.atos.entng.support.zendesk;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;

import org.entcore.common.json.JSONAble;
import org.entcore.common.json.JSONIgnore;
import org.entcore.common.json.JSONRename;
import org.entcore.common.json.JSONInherit;
import org.entcore.common.json.JSONDefault;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.user.UserInfos;
import org.entcore.common.utils.Id;

import net.atos.entng.support.Issue;
import net.atos.entng.support.Ticket;
import net.atos.entng.support.enums.BugTracker;
import net.atos.entng.support.enums.TicketStatus;
import net.atos.entng.support.Attachment;

// cf. https://developer.zendesk.com/api-reference/ticketing/tickets/tickets/#create-ticket
@JSONInherit(field="id")
public class ZendeskIssue extends Issue implements JSONAble
{
    @JSONIgnore
    public static final ZendeskEscalationConf escalationConf = new ZendeskEscalationConf();
    @JSONIgnore
    public static final ZendeskIssue zendeskIssueTemplate = new ZendeskIssue((Long)null);

    // Does not come from Zendesk
    public static class ZendeskEscalationConf implements JSONAble
    {
        public Long user_name_field_id;
        public Long gestionnaire_name_field_id;
        public Long user_phone_field_id;
        public Long ticket_id_field_id;
        public Long school_name_field_id;
        public Long school_uai_field_id;
        @JSONDefault("fr")
        public String locale;
    }

    private static class Collaborator implements JSONAble
    {
        public Long id;
        public String name;
        public String email;
    }

    private static class CustomField<T> implements JSONAble
    {
        public Long id;
        public T value;

        public CustomField()
        {
            this(null, null);
        }

        public CustomField(Long id, T value)
        {
            this.id = id;
            this.value = value;
        }
    }

    private static class Follower implements JSONAble
    {
        public static enum FollowerAction { put, delete }

        public Long user_id;
        public String user_email;
        public String user_name;
        public FollowerAction action;
    }

    private static class CustomMetadata extends JsonObject implements JSONAble
    {
        // Fully customisable
    }

    private static enum Priority { urgent, high, normal, low }

    private static class Requester implements JSONAble
    {
        public Long locale_id;
        public String name;
        public String email;
    }

    public static enum ZendeskStatus
    {
        @JSONRename("new")
        NEW(TicketStatus.OPENED), // Le ticket, bien que nouveau dans Zendesk, a commencé à être traité du point de vue de l'ENT, car il a été transmis
        open(TicketStatus.OPENED),
        pending(TicketStatus.OPENED),
        hold(TicketStatus.OPENED),
        solved(TicketStatus.RESOLVED),
        closed(TicketStatus.CLOSED),
        deleted(TicketStatus.CLOSED);

        public final TicketStatus correspondingStatus;

        ZendeskStatus(TicketStatus corr)
        {
            this.correspondingStatus = corr;
        }

        public static ZendeskStatus fromString(String statusStr)
        {
            if("new".equals(statusStr))
                return ZendeskStatus.NEW;
            else
                return ZendeskStatus.valueOf(statusStr);
        }

        public static ZendeskStatus from(TicketStatus ticketStatus)
        {
            switch(ticketStatus)
            {
                case NEW:
                    return ZendeskStatus.NEW;
                case OPENED:
                    return ZendeskStatus.open;
                case RESOLVED:
                    return ZendeskStatus.solved;
                case CLOSED:
                    return ZendeskStatus.solved;
                default:
                    return null;
            }
        }
    }

    private static enum Type { problem, incident, question, task }

    public static class VoiceComment implements JSONAble
    {
        public String from;
        public String to;
        public String recording_url;
        public String started_at;
        public Long call_duration;
        public Long answered_by_id;
        public String transcription_text;
        public String location;
    }

    public static class SatisfactionRating implements JSONAble
    {
        public static enum SatisfactionScore { offered, unoffered, good, bad }
        public Long assignee_id;
        public String comment;
        public String created_at;
        public Long group_id;
        public Long id;
        public String reason;
        public Long reason_code;
        public Long reason_id;
        public Long requester_id;
        public SatisfactionScore score;
        public Long ticket_id;
        public String updated_at;
        public String url;
    }

    // NOT from Zendesk
    @JSONIgnore
    public List<ZendeskComment> comments;

    // Read only
    public Long custom_status_id;

    // Write only
    public String assignee_email;
    public List<Long> attribute_value_ids;
    public ZendeskComment comment;
    public List<Follower> email_ccs;
    public List<Follower> followers;
    public Long macro_id;
    public CustomMetadata metadata;
    public Requester requester;
    public Boolean safe_update;
    public String updated_stamp;
    public Long via_id;
    public VoiceComment voice_comment;

    // POST requests only
    public List<Collaborator> collaborators;
    public List<Long> macro_ids;
    public Long via_followup_source_id;

    public Boolean allow_attachments;
    public Boolean allow_channelback;
    public Long assignee_id;
    public Long brand_id;
    public List<Long> collaborator_ids;
    public String created_at;
    public List<CustomField<?>> custom_fields;
    public String description;
    public String due_at;
    public List<Long> email_cc_ids;
    public String external_id;
    public List<Long> follower_ids;
    public List<Long> followup_ids;
    public Long forum_topic_id;
    public Boolean from_messaging_channel;
    public Long group_id;
    public Boolean has_incidents;
    public Boolean is_public;
    public Long organization_id;
    public Priority priority;
    public Long problem_id;
    public String raw_subject;
    public String recipient;
    public Long requester_id;
    public SatisfactionRating satisfaction_rating;
    public List<Long> sharing_agreement_ids;
    public ZendeskStatus status;
    public String subject;
    public Long submitter_id;
    public List<String> tags;
    public Long ticket_form_id;
    public Type type;
    public String updated_at;
    public String url;
    public ZendeskVia via;

    public ZendeskIssue(Long id)
    {
        super(id, BugTracker.ZENDESK);
    }

    public ZendeskIssue(Long id, JsonObject content)
    {
        super(id, BugTracker.ZENDESK, content);
    }

    public ZendeskIssue(Issue o)
    {
        super(o);
        this.bugTracker = BugTracker.ZENDESK;
    }

    public ZendeskIssue(JsonObject o)
    {
        super((Long)null, BugTracker.ZENDESK);
        this.fromJson(o);
    }

    public static ZendeskIssue followUp(ZendeskIssue oldIssue)
    {
        ZendeskIssue newIssue = new ZendeskIssue(oldIssue);
        newIssue.status = ZendeskStatus.NEW;
        newIssue.via_followup_source_id = oldIssue.id.get();
        newIssue.comment = oldIssue.comments != null ? oldIssue.comments.get(0) : null;

        if(newIssue.comment == null)
            newIssue.comment = new ZendeskComment();

        return newIssue;
    }

    public static Future<ZendeskIssue> fromTicket(Ticket ticket, UserInfos gestionnaireInfos)
    {
        ZendeskIssue issue = new ZendeskIssue(zendeskIssueTemplate);
        Promise<ZendeskIssue> promise = Promise.promise();

        issue.subject = ticket.subject;
        issue.comment = new ZendeskComment(ticket.description);
        issue.comment.uploads = new ArrayList<String>();
        for(Attachment a : ticket.attachments)
            issue.comment.uploads.add(a.bugTrackerToken);

        if(issue.custom_fields == null)
            issue.custom_fields = new ArrayList<CustomField<?>>();

        if(escalationConf.user_name_field_id != null)
            issue.custom_fields.add(new CustomField<String>(escalationConf.user_name_field_id, ticket.ownerName));
        if(escalationConf.gestionnaire_name_field_id != null)
            issue.custom_fields.add(new CustomField<String>(escalationConf.gestionnaire_name_field_id, gestionnaireInfos.getUsername()));
        if(escalationConf.ticket_id_field_id != null)
            issue.custom_fields.add(new CustomField<Integer>(escalationConf.ticket_id_field_id, ticket.id.get()));

        String query = "MATCH (s:Structure {id: {schoolId}}), (u:User {id: {userId}}) RETURN s.name AS schoolName, s.UAI AS schoolUAI, u.mobile AS userMobile";
        JsonObject params = new JsonObject().put("schoolId", ticket.schoolId != null ? ticket.schoolId : "").put("userId", ticket.ownerId != null ? ticket.ownerId : "");

        Neo4j.getInstance().execute(query, params, new Handler<Message<JsonObject>>()
        {
			@Override
			public void handle(Message<JsonObject> r) {
				JsonArray res = r.body().getJsonArray("result");
                if(res != null && res.getJsonObject(0) != null)
                {
                    JsonObject result = res.getJsonObject(0);

                    if(escalationConf.user_phone_field_id != null)
                        issue.custom_fields.add(new CustomField<String>(escalationConf.user_phone_field_id, result.getString("userMobile")));
                    if(escalationConf.school_name_field_id != null)
                        issue.custom_fields.add(new CustomField<String>(escalationConf.school_name_field_id, result.getString("schoolName")));
                    if(escalationConf.school_uai_field_id != null)
                        issue.custom_fields.add(new CustomField<String>(escalationConf.school_uai_field_id, result.getString("schoolUAI")));
                }

                promise.complete(issue);
			}
		});

        return promise.future();
    }

    public Id<Ticket, Integer> getTicketId()
    {
        if(escalationConf.ticket_id_field_id != null)
            for(CustomField<?> f : this.custom_fields)
                if(escalationConf.ticket_id_field_id.equals(f.id))
                    return new Id<Ticket, Integer>(Integer.parseInt(f.value.toString()));

        return null;
    }

    @Override
    public void setContent(JsonObject content)
    {
        this.fromJson(content);
    }

    public JsonObject getContent()
    {
        return this.toJson();
    }
}

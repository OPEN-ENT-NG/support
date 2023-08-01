package net.atos.entng.support.zendesk;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import fr.wseduc.webutils.I18n;

import org.entcore.common.json.JSONAble;
import org.entcore.common.json.JSONIgnore;
import org.entcore.common.json.JSONRename;
import org.entcore.common.json.JSONDefault;
import org.entcore.common.json.JSONInherit;

import net.atos.entng.support.Comment;

// cf. https://developer.zendesk.com/api-reference/ticketing/tickets/ticket_comments/
@JSONInherit(field="id")
@JSONInherit(field="content", rename="html_body")
@JSONInherit(field="created", rename="created_at")
public class ZendeskComment extends Comment implements JSONAble
{
    public static enum ZendeskCommentType { Comment, VoiceComment }

    public List<ZendeskAttachment> attachments;
    public Long audit_id;
    public JsonObject metadata;
    public String plain_body;
    public ZendeskCommentType type;
    public Long author_id;
    public String body;
    @JSONRename("public")
    public Boolean Public;
    public List<String> uploads;
    public ZendeskVia via;

    public ZendeskComment()
    {
        super((String)null);
    }

    public ZendeskComment(String content)
    {
        super(content);
        this.type = ZendeskCommentType.Comment;
    }

    public ZendeskComment(String content, String ownerName)
    {
        super(content, ownerName);
        this.type = ZendeskCommentType.Comment;
    }

    public ZendeskComment(Comment c)
    {
        super(c);
        this.type = ZendeskCommentType.Comment;
    }

    @Override
    public JsonObject toJson()
    {
        String originalContent = this.content;

        if(this.content == null || "".equals(this.content.trim()))
            this.content = I18n.getInstance().translate("support.escalated.ticket.empty", new Locale(ZendeskIssue.escalationConf.locale));

            if(this.ownerName != null)
        {
            String authorHeader = I18n.getInstance().translate("support.escalated.ticket.author", new Locale(ZendeskIssue.escalationConf.locale));
            this.content = authorHeader + " : " + this.ownerName + "\n\n" + this.content;
        }

        JsonObject jo = JSONAble.super.toJson();
        this.content = originalContent;
        return jo;
    }
}
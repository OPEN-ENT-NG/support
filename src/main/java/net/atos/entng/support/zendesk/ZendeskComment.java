package net.atos.entng.support.zendesk;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

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

    public ZendeskComment(Comment c)
    {
        super(c);
        this.type = ZendeskCommentType.Comment;
    }
}
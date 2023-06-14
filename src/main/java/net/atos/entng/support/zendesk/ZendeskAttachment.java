package net.atos.entng.support.zendesk;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.Attachment;

import org.entcore.common.json.JSONAble;
import org.entcore.common.json.JSONIgnore;
import org.entcore.common.json.JSONRename;
import org.entcore.common.json.JSONDefault;
import org.entcore.common.json.JSONInherit;

// cf. https://developer.zendesk.com/api-reference/ticketing/tickets/ticket-attachments/

@JSONInherit(field="bugTrackerId", rename="id")
@JSONInherit(field="name", rename="file_name")
@JSONInherit(field="contentType", rename="content_type")
@JSONInherit(field="size")
public class ZendeskAttachment extends Attachment implements JSONAble
{
    private static enum MalwareScanResult { malware_found, malware_not_found, not_scanned, failed_to_scan }

    public String content_url;
    public Boolean deleted;
    public String height;
    public Boolean inline;
    public Boolean malware_access_override;
    public MalwareScanResult malware_scan_result;
    public String mapped_content_url;
    public List<ZendeskAttachment> thumbnails = new ArrayList<ZendeskAttachment>();
    public String url;
    public String width;

    public ZendeskAttachment()
    {
        super(null, null);
    }
}
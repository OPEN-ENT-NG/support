package net.atos.entng.support.zendesk;

import org.entcore.common.json.JSONAble;
import org.entcore.common.json.JSONDefault;
import org.entcore.common.json.JSONIgnore;

public class ZendeskEscalationConf implements JSONAble
{
    public Long user_name_field_id;
    public Long gestionnaire_name_field_id;
    public Long user_phone_field_id;
    public Long ticket_id_field_id;
    public Long school_name_field_id;
    public Long school_uai_field_id;
    public Long comment_author_user_id;
    @JSONDefault("fr")
    public String locale;

    @JSONIgnore
    private static final ZendeskEscalationConf instance = new ZendeskEscalationConf();
    public static ZendeskEscalationConf getInstance()
    {
        return instance;
    }

    private ZendeskEscalationConf()
    {

    }
}
package net.atos.entng.support.filters;

import fr.wseduc.webutils.http.Binding;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.Ticket;
import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;

import java.util.List;
import java.util.Map;

import static org.entcore.common.sql.Sql.parseId;

public class AccessIfMyStructureAdmin implements ResourcesProvider {
    /**
     * Authorize if user is the ticket's owner, or a local admin for the ticket's school_id
     */
    @Override
    public void authorize(final HttpServerRequest request, final Binding binding,
                          final UserInfos user, final Handler<Boolean> handler) {


        RequestUtils.bodyToJson(request, body -> {
            final List ids = body.getJsonArray(Ticket.IDS).getList();
            if (ids == null || ids.isEmpty()) {
                handler.handle(false);
                return;
            }
            request.pause();

            StringBuilder query = new StringBuilder("SELECT count(*) FROM support.tickets AS t");
            query.append(" WHERE t.id IN ").append(Sql.listPrepared(ids)).append(" ")
                    .append(" AND (t.owner = ?"); // Check if current user is the ticket's owner
            JsonArray values = new JsonArray(ids);
            values.add(user.getUserId());

            UserInfos.Function admin = null;

            Map<String, UserInfos.Function> functions = user.getFunctions();
            if (functions != null && (functions.containsKey(DefaultFunctions.ADMIN_LOCAL) || functions.containsKey(DefaultFunctions.SUPER_ADMIN))) {
                // If current user is a local admin, check that its scope contains the ticket's school_id
                UserInfos.Function adminLocal = functions.get(DefaultFunctions.ADMIN_LOCAL);
                // super_admin always are authorized
                if (adminLocal != null && adminLocal.getScope() != null && !adminLocal.getScope().isEmpty()) {
                    query.append(" OR t.school_id IN (");
                    for (String scope : adminLocal.getScope()) {
                        query.append("?,");
                        values.add(scope);
                    }
                    query.deleteCharAt(query.length() - 1);
                    query.append(")");
                }
            }

            query.append(")");
            admin = functions.get(DefaultFunctions.SUPER_ADMIN);


            final UserInfos.Function finalAdmin = admin;
            Sql.getInstance().prepared(query.toString(), values, new Handler<Message<JsonObject>>() {
                @Override
                public void handle(Message<JsonObject> message) {
                    request.resume();
                    Long count = SqlResult.countResult(message);

                    if (finalAdmin != null) {
                        handler.handle(true);
                    } else {
                        handler.handle(count != null && count > 0);
                    }
                }
            });
        });
    }
}

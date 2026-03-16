package net.atos.entng.support.filters;

import fr.wseduc.webutils.http.Binding;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import net.atos.entng.support.constants.JiraTicket;
import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;

import java.util.List;
import java.util.Map;


public class AdminOfTicketsStructure implements ResourcesProvider {
    /**
     * Authorize if user is the ticket's owner, or a local admin for the ticket's school_id
     */
    @Override
    public void authorize(final HttpServerRequest request, final Binding binding,
                          final UserInfos user, final Handler<Boolean> handler) {


            List<String> ids = request.params().getAll(JiraTicket.ID);
            if (ids.isEmpty()) {
                handler.handle(false);
                return;
            }
            request.pause();

            StringBuilder query = new StringBuilder("SELECT count(*) FROM support.tickets AS t");
            query.append(" WHERE t.id IN ").append(Sql.listPrepared(ids));
            JsonArray values = new JsonArray();
            ids.forEach(values::add);

            UserInfos.Function admin = null;

            Map<String, UserInfos.Function> functions = user.getFunctions();
            if (functions != null && (functions.containsKey(DefaultFunctions.ADMIN_LOCAL) || functions.containsKey(DefaultFunctions.SUPER_ADMIN))) {
                // If current user is a local admin, check that its scope contains the ticket's school_id
                UserInfos.Function adminLocal = functions.get(DefaultFunctions.ADMIN_LOCAL);
                // super_admin always are authorized
                if (adminLocal != null && adminLocal.getScope() != null && !adminLocal.getScope().isEmpty()) {
                    query.append(" AND t.school_id IN ").append(Sql.listPrepared(adminLocal.getScope()));
                    adminLocal.getScope().forEach(values::add);
                }
                admin = functions.get(DefaultFunctions.SUPER_ADMIN);
            }else {
                handler.handle(false);
                return;
            }



            final UserInfos.Function finalAdmin = admin;
            Sql.getInstance().prepared(query.toString(), values, message -> {
                request.resume();
                Long count = SqlResult.countResult(message);

                Boolean isSuperAdminOrResultEqualIdsSize = finalAdmin != null || count == ids.size();
                handler.handle(isSuperAdminOrResultEqualIdsSize);
            });
    }
}
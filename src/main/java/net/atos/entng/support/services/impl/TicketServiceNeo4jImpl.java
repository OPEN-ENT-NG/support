package net.atos.entng.support.services.impl;

import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;

import org.entcore.common.neo4j.Neo4j;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;


public class TicketServiceNeo4jImpl {

    public static void getUsersFromList(JsonArray listUserIds, Handler<Either<String, JsonArray>> handler) {
        Neo4j neo4j = Neo4j.getInstance();
        String query = "match(n:User) where n.id in {ids} return n.id, n.profiles;";
        JsonObject params = new JsonObject().put("ids", listUserIds);
        neo4j.execute(query, params, validResultHandler(handler));
    }

    /**
     * Get user and structure info for escalation
     * @param userId  neo4j user ID
     * @param structureId neo4j structure ID
     * @param handler handler to use data
     */
    public static void getUserEscalateInfo(String userId, String structureId, Handler<Either<String, JsonArray>> handler) {
        Neo4j neo4j = Neo4j.getInstance();
        StringBuilder query = new StringBuilder();
        query.append("match (u:User), (s:Structure) ")
            .append("where u.id = {userid} and s.id = {structid} ")
            .append("return u.id as userid, u.emailAcademy as useremail, ")
            .append("u.email as userpersoemail,")
            .append("s.id as structid, s.name as structname, s.UAI as structuai, s.academy as structacademy, ")
            .append("s.homePhone as userphone ")
            .append("order by u.id, s.id;");

        JsonObject params = new JsonObject().put("userid", userId).put("structid", structureId);

        neo4j.execute(query.toString(), params, validResultHandler(handler));
    }
}

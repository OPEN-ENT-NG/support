package net.atos.entng.support.services.impl;

import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;
import static org.entcore.common.neo4j.Neo4jResult.validUniqueResultHandler;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import net.atos.entng.support.constants.Ticket;
import net.atos.entng.support.helpers.PromiseHelper;
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

    public static Future<JsonArray> getSchoolFromList(JsonArray listSchoolIds) {
        Promise<JsonArray> promise = Promise.promise();
        Neo4j neo4j = Neo4j.getInstance();
        String query = "MATCH (s:Structure) WHERE s.id IN {ids} RETURN s.id, s.name;";
        JsonObject params = new JsonObject().put("ids", listSchoolIds);
        neo4j.execute(query, params, validResultHandler(PromiseHelper.handler(promise)));
        return promise.future();
    }

    public static Future<JsonObject> getSchoolWorkflowRights(String userId, String workflowWanted, String structureId) {
        Promise<JsonObject> promise = Promise.promise();
        Neo4j neo4j = Neo4j.getInstance();
        String query = "MATCH (u:User {id: {userId}})" +
                "OPTIONAL MATCH (u)-->(g:Group)-->(r:Role)-[:AUTHORIZE]->(w:WorkflowAction {displayName: {workflow}}), (g)-[:DEPENDS]->(s:Structure {id: {structureId}})" +
                " RETURN  DISTINCT w IS NOT NULL as canAccess";
        JsonObject params = new JsonObject().put(Ticket.USERID, userId)
                .put(Ticket.WORKFLOW, workflowWanted)
                .put(Ticket.STRUCTURE_ID, structureId);
        neo4j.execute(query, params, validUniqueResultHandler(PromiseHelper.handler(promise)));
        return promise.future();
    }

}

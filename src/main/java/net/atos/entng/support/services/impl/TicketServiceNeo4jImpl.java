package net.atos.entng.support.services.impl;

import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;

import org.entcore.common.neo4j.Neo4j;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class TicketServiceNeo4jImpl {

    private Neo4j neo4j = Neo4j.getInstance();

    public void getUsersFromList(JsonArray listUserIds, Handler<Either<String, JsonArray>> handler) {
        String query = "match(n:User) where n.id in {ids} return n.id, n.profiles;";

        JsonObject params = new JsonObject().put("ids", listUserIds);

        neo4j.execute(query, params, validResultHandler(handler));
    }

    public void getUserStructures(String userId, Handler<Either<String, JsonArray>> handler) {
        String query = "match (u:User)-[IN]->(p:ProfileGroup)-[DEPENDS]->(s:Structure) where u.id = {id} return distinct s.id as id, s.name as name order by s.name;";

        JsonObject params = new JsonObject().put("id", userId);

        neo4j.execute(query, params, validResultHandler(handler));
    }
}

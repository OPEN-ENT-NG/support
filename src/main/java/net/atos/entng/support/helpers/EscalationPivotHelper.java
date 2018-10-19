package net.atos.entng.support.helpers;

import io.vertx.core.json.JsonArray;

public interface EscalationPivotHelper {

    String STATUS_NEW = "Ouvert";
    String STATUS_OPENED = "En cours";
    String STATUS_RESOLVED = "Résolu";
    String STATUS_CLOSED = "Fermé";

    /**
     * Get ENT equivalent of pivot status
     * @param pivotStatus pivot status
     * @return ent status
     */
    int getStatusCorrespondence(String pivotStatus);

    /**
     * Serialize comments : date | author | content
     * @param comments Json Array with comments to serialize
     * @return Json array with comments serialized
     */
    JsonArray serializeComments (final JsonArray comments);

    /**
     * Compare comments of ticket and bugtracker issue.
     * Add every comment to ticket not already existing
     * @param ticketComments comments of ENT ticket
     * @param issueComments comment of Bugtracker issue
     * @return comments that needs to be added in ticket
     */
    JsonArray compareComments(JsonArray ticketComments, JsonArray issueComments);
}

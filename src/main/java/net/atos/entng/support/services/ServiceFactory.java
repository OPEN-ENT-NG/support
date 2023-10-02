package net.atos.entng.support.services;

import fr.wseduc.mongodb.MongoDb;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.EscalationServiceFactory;
import net.atos.entng.support.constants.Ticket;
import net.atos.entng.support.enums.BugTracker;
import net.atos.entng.support.services.impl.TicketServiceImpl;
import net.atos.entng.support.services.impl.TicketServiceSqlImpl;
import net.atos.entng.support.services.impl.UserServiceDirectoryImpl;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.sql.Sql;
import org.entcore.common.storage.Storage;

import static fr.wseduc.webutils.Server.getEventBus;

public class ServiceFactory {
    private final Vertx vertx;
    private final Storage storage;
    private final Neo4j neo4j;
    private final Sql sql;
    private final MongoDb mongoDb;
    private final JsonObject config;
    private final BugTracker bugTrackerType;
    private boolean escalationActivated;

    public ServiceFactory(Vertx vertx, Storage storage, Neo4j neo4j, Sql sql, MongoDb mongoDb, JsonObject config
            , BugTracker bugTrackerType) {
        this.vertx = vertx;
        this.storage = storage;
        this.neo4j = neo4j;
        this.sql = sql;
        this.mongoDb = mongoDb;
        this.config = config;
        this.bugTrackerType = bugTrackerType;
    }

    public TicketService ticketService() {
        return new TicketServiceImpl();
    }

    public TicketServiceSql ticketServiceSql() {
        return new TicketServiceSqlImpl(bugTrackerType);
    }

    public UserService userService() {
        return new UserServiceDirectoryImpl(getEventBus(vertx), neo4j);
    }

    public EscalationService escalationService() {
        return escalationActivated
                ? EscalationServiceFactory.makeEscalationService(bugTrackerType, vertx, config,
                this.ticketServiceSql(), this.userService(), storage)
                : null;
    }

    public Boolean isEscalationActivated() {
        return escalationActivated = config.getBoolean(Ticket.ACTIVATE_ESCALATION, false);
    }


    public Storage getStorage() {
        return storage;
    }
}

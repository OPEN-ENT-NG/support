package net.atos.entng.support.services;

import net.atos.entng.support.services.impl.TicketServiceImpl;
import net.atos.entng.support.services.impl.TicketServiceNeo4jImpl;

public class ServiceFactory {

    private final TicketServiceNeo4jImpl ticketServiceNeo4j;

    public ServiceFactory(TicketServiceNeo4jImpl ticketServiceNeo4j){
        this.ticketServiceNeo4j = ticketServiceNeo4j;
    }

    public TicketService ticketService(){
        return new TicketServiceImpl(ticketServiceNeo4j);
    }
}

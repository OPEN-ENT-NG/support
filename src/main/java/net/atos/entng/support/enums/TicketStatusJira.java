package net.atos.entng.support.enums;

import net.atos.entng.support.constants.Ticket;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum TicketStatusJira {
    NEW(Ticket.NEW, 1),
    OPEN(Ticket.OPEN, 2),
    RESOLVED(Ticket.RESOLVED, 3),
    CLOSED(Ticket.CLOSED, 4),
    WAITING(Ticket.WAITING, 5);

    private final Integer status;
    private final String name;
    private static Map<String, TicketStatusJira> ticketStatusJiraMap;

    TicketStatusJira(String name, Integer status) {
        this.name = name;
        this.status = status;
    }

    public Integer getStatus() {
        return this.status;
    }

    public String getName() {
        return this.name;
    }

    public static TicketStatusJira getTicketStatusJira(String name) {
        if (ticketStatusJiraMap == null) {
            ticketStatusJiraMap = Arrays.stream(values()).collect(Collectors.toMap(TicketStatusJira::getName, Function.identity()));
        }
        return ticketStatusJiraMap.get(name);
    }
}

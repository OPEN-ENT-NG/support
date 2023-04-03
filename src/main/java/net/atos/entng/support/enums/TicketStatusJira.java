package net.atos.entng.support.enums;

import net.atos.entng.support.constants.JiraTicket;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum TicketStatusJira {
    NEW(JiraTicket.NEW, 1),
    OPEN(JiraTicket.OPEN, 2),
    RESOLVED(JiraTicket.RESOLVED, 3),
    CLOSED(JiraTicket.CLOSED, 4);

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

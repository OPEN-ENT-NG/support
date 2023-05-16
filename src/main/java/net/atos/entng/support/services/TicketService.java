package net.atos.entng.support.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import net.atos.entng.support.model.I18nConfig;

public interface TicketService {
    /**
     * Get user profile from tickets
     *
     * @param ticketsList {JsonArray} list of tickets
     * @param i18nConfig  {I18nConfig} containing "domain and i18n}
     * @returns {Future<JsonArray>} result
     **/
    Future<JsonArray> getProfileFromTickets(JsonArray ticketsList, I18nConfig i18nConfig);

    /**
     * Get school from tickets
     *
     * @param ticketsList {JsonArray} list of tickets
     * @returns {Future<JsonArray>} result
     **/
    Future<JsonArray> getSchoolFromTickets(JsonArray ticketsList);
}

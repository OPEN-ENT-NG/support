package net.atos.entng.support.services;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.model.I18nConfig;
import org.entcore.common.user.UserInfos;

import java.util.List;

public interface TicketService {
    /**
     * Get user profile from tickets
     *
     * @param ticketsList {JsonArray} list of tickets
     * @param i18nConfig  {I18nConfig} containing "domain and i18n}
     * @return {Future<JsonArray>} result
     **/
    Future<JsonArray> getProfileFromTickets(JsonArray ticketsList, I18nConfig i18nConfig);

    /**
     * Get school from tickets
     *
     * @param ticketsList {JsonArray} list of tickets
     * @return {Future<JsonArray>} result
     **/
    Future<JsonArray> getSchoolFromTickets(JsonArray ticketsList);

    Future<JsonObject> getSchoolWorkflowRightFromUserId(String userId, String workflowWanted, String structureId);

    /**
     * Get "childrens" structures of a "parent" structure
     *
     * @param structureIds {List<String>} List of structureIds from which we want to retrieve the children
     * @return {Future<JsonObject>} JsonObject containing structure "childrens" and the "parent" structure
     **/
    Future<JsonObject> listStructureChildren(List<String> structureIds);

    /**
     * Sort structures by name
     *
     * @param structureIds {List<String>} List of structureIds you want to sort
     * @return {Future<JsonObject>} JsonObject containing structureIds sorted by name
     **/
    Future<JsonObject> sortSchoolByName(List<String> structureIds);

    CompositeFuture getSchoolAndProfileFromTicket(JsonArray tickets, I18nConfig i18nConfig);


    /**
     * Fill tickets category_label if non-existing
     *
     * @param locale {String} locale language
     * @return {Future<Long>} Number of updated tickets
     **/
    Future<Integer> fillCategoryLabel(String locale);
}

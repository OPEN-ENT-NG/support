package net.atos.entng.support.model;

import net.atos.entng.support.enums.Actions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class TransactionElement implements IModel<TransactionElement> {
    private Actions action;
    private String query;
    private JsonArray params;
    private JsonArray result;

    // Constructors

    public TransactionElement(JsonObject jsonObject) {
        throw new RuntimeException("Not implemented");
    }

    public TransactionElement(String query, JsonArray params) {
        this.query = query;
        this.params = params;
        this.action = Actions.PREPARED;
    }

    public TransactionElement(String query, JsonArray params, Actions action) {
        this.action = action;
        this.query = query;
        this.params = params;
    }


    // Getters

    public Actions getAction() {
        return this.action;
    }

    public String getQuery() {
        return this.query;
    }

    public JsonArray getParams() {
        return this.params;
    }

    public JsonArray getResult() {
        return this.result;
    }


    // Setters

    public TransactionElement setAction(Actions action) {
        this.action = action;
        return this;
    }

    public TransactionElement setQuery(String query) {
        this.query = query;
        return this;
    }

    public TransactionElement setParams(JsonArray params) {
        this.params = params;
        return this;
    }

    public TransactionElement setResult(JsonArray result) {
        this.result = result;
        return this;
    }

    // Functions

    @Override
    public JsonObject toJson() {
        return new JsonObject()
                .put("action", this.action.getValue())
                .put("statement", this.query)
                .put("values", this.params);
    }
}
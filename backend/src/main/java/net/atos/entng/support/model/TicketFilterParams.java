package net.atos.entng.support.model;

import io.vertx.core.json.JsonArray;

import java.util.List;

/**
 * Filter and pagination parameters for {@code TicketServiceSql#listFilteredTickets}.
 */
public class TicketFilterParams {

    private final Integer page;
    private final List<String> statuses;
    private final List<String> applicants;
    private final List<String> schoolIds;
    private final boolean allSchools;
    private final String sortBy;
    private final String order;
    private final Integer nbTicketsPerPage;
    private final String search;
    private final JsonArray orderedProfileIds;

    private TicketFilterParams(Builder builder) {
        this.page = builder.page;
        this.statuses = builder.statuses;
        this.applicants = builder.applicants;
        this.schoolIds = builder.schoolIds;
        this.allSchools = builder.allSchools;
        this.sortBy = builder.sortBy;
        this.order = builder.order;
        this.nbTicketsPerPage = builder.nbTicketsPerPage;
        this.search = builder.search;
        this.orderedProfileIds = builder.orderedProfileIds;
    }

    public Integer getPage() {
        return page;
    }

    public List<String> getStatuses() {
        return statuses;
    }

    public List<String> getApplicants() {
        return applicants;
    }

    public List<String> getSchoolIds() {
        return schoolIds;
    }

    public boolean isAllSchools() {
        return allSchools;
    }

    public String getSortBy() {
        return sortBy;
    }

    public String getOrder() {
        return order;
    }

    public Integer getNbTicketsPerPage() {
        return nbTicketsPerPage;
    }

    public String getSearch() {
        return search;
    }

    public JsonArray getOrderedProfileIds() {
        return orderedProfileIds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Integer page;
        private List<String> statuses;
        private List<String> applicants;
        private List<String> schoolIds;
        private boolean allSchools;
        private String sortBy;
        private String order;
        private Integer nbTicketsPerPage;
        private String search;
        private JsonArray orderedProfileIds;

        public Builder page(Integer page) {
            this.page = page;
            return this;
        }

        public Builder statuses(List<String> statuses) {
            this.statuses = statuses;
            return this;
        }

        public Builder applicants(List<String> applicants) {
            this.applicants = applicants;
            return this;
        }

        public Builder schoolIds(List<String> schoolIds) {
            this.schoolIds = schoolIds;
            return this;
        }

        public Builder allSchools(boolean allSchools) {
            this.allSchools = allSchools;
            return this;
        }

        public Builder sortBy(String sortBy) {
            this.sortBy = sortBy;
            return this;
        }

        public Builder order(String order) {
            this.order = order;
            return this;
        }

        public Builder nbTicketsPerPage(Integer nbTicketsPerPage) {
            this.nbTicketsPerPage = nbTicketsPerPage;
            return this;
        }

        public Builder search(String search) {
            this.search = search;
            return this;
        }

        public Builder orderedProfileIds(JsonArray orderedProfileIds) {
            this.orderedProfileIds = orderedProfileIds;
            return this;
        }

        public TicketFilterParams build() {
            return new TicketFilterParams(this);
        }

    }

}

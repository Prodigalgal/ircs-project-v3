package com.prodigalgal.ircs.content.people.domain;

public enum PeopleResource {

    ACTORS("actor", "actors", "actor_names"),
    DIRECTORS("director", "directors", "director_names");

    private final String entityName;
    private final String tableName;
    private final String flatColumnName;

    PeopleResource(
            String entityName,
            String tableName,
            String flatColumnName) {
        this.entityName = entityName;
        this.tableName = tableName;
        this.flatColumnName = flatColumnName;
    }

    public String entityName() {
        return entityName;
    }

    public String tableName() {
        return tableName;
    }

    public String flatColumnName() {
        return flatColumnName;
    }
}

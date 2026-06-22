package com.prodigalgal.ircs.content.people.domain;

public enum PeopleResource {

    ACTORS("actor", "actors", "video_actors", "unified_video_actors", "actor_id"),
    DIRECTORS("director", "directors", "video_directors", "unified_video_directors", "director_id");

    private final String entityName;
    private final String tableName;
    private final String rawJoinTableName;
    private final String unifiedJoinTableName;
    private final String joinColumnName;

    PeopleResource(
            String entityName,
            String tableName,
            String rawJoinTableName,
            String unifiedJoinTableName,
            String joinColumnName) {
        this.entityName = entityName;
        this.tableName = tableName;
        this.rawJoinTableName = rawJoinTableName;
        this.unifiedJoinTableName = unifiedJoinTableName;
        this.joinColumnName = joinColumnName;
    }

    public String entityName() {
        return entityName;
    }

    public String tableName() {
        return tableName;
    }

    public String rawJoinTableName() {
        return rawJoinTableName;
    }

    public String unifiedJoinTableName() {
        return unifiedJoinTableName;
    }

    public String joinColumnName() {
        return joinColumnName;
    }
}

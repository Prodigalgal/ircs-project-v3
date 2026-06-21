package com.prodigalgal.ircs.task;

final class TaskRuntimeFields {

    static final String MASTER_TASK_ID = "masterTaskId";
    static final String PAGE_TASK_ID = "pageTaskId";
    static final String PAGE_NUMBER = "pageNumber";
    static final String STATUS = "status";
    static final String UPDATED_AT = "updatedAt";
    static final String LAST_ERROR = "lastError";
    static final String CONTROL_REASON = "controlReason";
    static final String START_PAGE = "startPage";
    static final String TOTAL_PAGES = "totalPages";
    static final String TOTAL_ITEMS = "totalItems";

    static final String PAGE_SCHEDULED = "pageScheduled";
    static final String PAGE_DISCOVERED = "pageDiscovered";
    static final String PAGE_COMPLETED = "pageCompleted";
    static final String PAGE_SUCCEEDED = "pageSucceeded";
    static final String PAGE_FAILED = "pageFailed";

    static final String DETAIL_SCHEDULED = "detailScheduled";
    static final String DETAIL_COMPLETED = "detailCompleted";
    static final String DETAIL_SUCCEEDED = "detailSucceeded";
    static final String DETAIL_FAILED = "detailFailed";

    private TaskRuntimeFields() {
    }
}

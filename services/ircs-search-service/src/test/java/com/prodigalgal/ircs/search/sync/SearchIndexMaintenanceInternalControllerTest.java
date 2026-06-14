package com.prodigalgal.ircs.search.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SearchIndexMaintenanceResponse;
import com.prodigalgal.ircs.search.index.SearchIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class SearchIndexMaintenanceInternalControllerTest {

    private final SearchIndexService indexService = org.mockito.Mockito.mock(SearchIndexService.class);
    private final SearchSyncOutboxRepository outboxRepository =
            org.mockito.Mockito.mock(SearchSyncOutboxRepository.class);
    private final SearchInternalAccessPolicy accessPolicy = org.mockito.Mockito.mock(SearchInternalAccessPolicy.class);
    private final SearchIndexMaintenanceInternalController controller =
            new SearchIndexMaintenanceInternalController(indexService, outboxRepository, accessPolicy);

    @Test
    void hardResetChecksAccessDeletesSyncTasksAndDelegatesToIndexService() {
        when(outboxRepository.deleteByEntityType(SearchEntityType.UNIFIED_VIDEO)).thenReturn(3);
        when(indexService.hardReset(SearchEntityType.UNIFIED_VIDEO)).thenReturn(true);

        ResponseEntity<SearchIndexMaintenanceResponse> result = controller.hardReset(
                SearchEntityType.UNIFIED_VIDEO,
                "ops-service",
                "token",
                "search:sync");

        assertEquals(HttpStatus.ACCEPTED, result.getStatusCode());
        assertEquals(SearchEntityType.UNIFIED_VIDEO, result.getBody().entityType());
        assertEquals("hard-reset", result.getBody().operation());
        assertEquals(3, result.getBody().deletedSyncTasks());
        assertEquals(true, result.getBody().recreated());
        verify(accessPolicy).assertAccess("ops-service", "token", "search:sync");
        verify(outboxRepository).deleteByEntityType(SearchEntityType.UNIFIED_VIDEO);
        verify(indexService).hardReset(SearchEntityType.UNIFIED_VIDEO);
    }
}

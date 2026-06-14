package com.prodigalgal.ircs.search.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.search.SearchSyncOutboxWriter;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SearchSyncTaskBatchEnqueueRequest;
import com.prodigalgal.ircs.contracts.search.SearchSyncTaskEnqueueRequest;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SearchSyncTaskInternalControllerTest {

    private final SearchSyncOutboxWriter writer = org.mockito.Mockito.mock(SearchSyncOutboxWriter.class);
    private final SearchSyncTaskInternalController controller =
            new SearchSyncTaskInternalController(writer, new SearchInternalAccessPolicy(), 100);

    @Test
    void enqueuesSingleInternalRequest() {
        UUID id = UUID.randomUUID();
        when(writer.enqueue(id, SearchEntityType.RAW_VIDEO, SyncOperation.INDEX, "content-service", "trace-1"))
                .thenReturn(1);

        var response = controller.enqueue(
                new SearchSyncTaskEnqueueRequest(
                        id,
                        SearchEntityType.RAW_VIDEO,
                        SyncOperation.INDEX,
                        "content-service",
                        "trace-1"),
                null,
                null,
                null,
                null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().accepted()).isEqualTo(1);
        verify(writer).enqueue(id, SearchEntityType.RAW_VIDEO, SyncOperation.INDEX, "content-service", "trace-1");
    }

    @Test
    void enqueuesBatchInternalRequest() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(writer.enqueueBatch(ids, SearchEntityType.UNIFIED_VIDEO, SyncOperation.DELETE, "ops", "trace-2"))
                .thenReturn(2);

        var response = controller.enqueueBatch(
                new SearchSyncTaskBatchEnqueueRequest(
                        ids,
                        SearchEntityType.UNIFIED_VIDEO,
                        SyncOperation.DELETE,
                        null,
                        null),
                "ops",
                null,
                null,
                "trace-2");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().accepted()).isEqualTo(2);
    }
}

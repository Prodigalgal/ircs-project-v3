package com.prodigalgal.ircs.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.web.PageEnvelope;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class CatalogControllerTest {

    private final CatalogService catalogService = org.mockito.Mockito.mock(CatalogService.class);
    private final CatalogController controller = new CatalogController(catalogService);

    @Test
    void returnsStandardCategories() {
        StandardCategorySummary category = new StandardCategorySummary(
                UUID.randomUUID(),
                "Movie",
                "movie");
        when(catalogService.listStandardCategories()).thenReturn(List.of(category));

        assertEquals(List.of(category), controller.listCatalogStandardCategories());
        verify(catalogService).listStandardCategories();
    }

    @Test
    void returnsRawCategoryMappings() {
        RawCategorySummary category = new RawCategorySummary(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "dev-source",
                "1",
                "Movies",
                UUID.randomUUID(),
                "Movie",
                "movie");
        when(catalogService.listRawCategories()).thenReturn(List.of(category));

        assertEquals(List.of(category), controller.listCatalogRawCategories());
        verify(catalogService).listRawCategories();
    }

    @Test
    void returnsDataSources() {
        DataSourceSummary dataSource = new DataSourceSummary(
                UUID.randomUUID(),
                "dev-source",
                "https://example.invalid",
                "/list",
                "/detail/{id}");
        when(catalogService.listDataSources()).thenReturn(List.of(dataSource));

        assertEquals(List.of(dataSource), controller.listCatalogDataSources());
        verify(catalogService).listDataSources();
    }

    @Test
    void returnsDataSourcesPage() {
        CatalogPageRequest request = CatalogPageRequest.of(0, 50, List.of("name,asc"));
        DataSourceRead dataSource = new DataSourceRead(
                UUID.randomUUID(),
                "dev-source",
                "https://example.invalid",
                "/list",
                "{\"pg\":\"{page}\"}",
                "/detail",
                "{\"ids\":\"{id}\"}",
                "{\"title\":\"title\"}",
                Instant.parse("2026-06-06T00:00:00Z"),
                Instant.parse("2026-06-06T01:00:00Z"));
        CatalogPage<DataSourceRead> page = CatalogPage.of(List.of(dataSource), request, 1);
        when(catalogService.pageDataSources(request)).thenReturn(page);

        assertPageEquals(page, controller.pageDataSources(0, 50, List.of("name,asc")));
        verify(catalogService).pageDataSources(request);
    }

    @Test
    void returnsDataSourceDetail() {
        UUID id = UUID.randomUUID();
        DataSourceRead dataSource = new DataSourceRead(
                id,
                "dev-source",
                "https://example.invalid",
                "/list",
                null,
                "/detail",
                null,
                null,
                null,
                null);
        when(catalogService.findDataSource(id)).thenReturn(Optional.of(dataSource));

        assertEquals(ResponseEntity.ok(dataSource), controller.getDataSource(id));
        verify(catalogService).findDataSource(id);
    }

    @Test
    void returnsStandardCategoryPageAndDetail() {
        UUID id = UUID.randomUUID();
        CatalogPageRequest request = CatalogPageRequest.of(1, 10, List.of("name", "desc"));
        StandardCategoryRead category = new StandardCategoryRead(id, "Movie", "movie", null, null);
        CatalogPage<StandardCategoryRead> page = CatalogPage.of(List.of(category), request, 11);
        when(catalogService.pageStandardCategories(request, "mov")).thenReturn(page);
        when(catalogService.findStandardCategory(id)).thenReturn(Optional.of(category));

        assertPageEquals(page, controller.pageStandardCategories(1, 10, List.of("name", "desc"), "mov"));
        assertEquals(ResponseEntity.ok(category), controller.getStandardCategory(id));
        verify(catalogService).pageStandardCategories(request, "mov");
        verify(catalogService).findStandardCategory(id);
    }

    @Test
    void returnsStandardGenrePageAndDetail() {
        UUID id = UUID.randomUUID();
        CatalogPageRequest request = CatalogPageRequest.of(0, 20, List.of("name,asc"));
        StandardGenreRead genre = new StandardGenreRead(id, "Action");
        CatalogPage<StandardGenreRead> page = CatalogPage.of(List.of(genre), request, 1);
        when(catalogService.pageStandardGenres(request, "act")).thenReturn(page);
        when(catalogService.findStandardGenre(id)).thenReturn(Optional.of(genre));

        assertPageEquals(page, controller.pageStandardGenres(0, 20, List.of("name,asc"), "act"));
        assertEquals(ResponseEntity.ok(genre), controller.getStandardGenre(id));
        verify(catalogService).pageStandardGenres(request, "act");
        verify(catalogService).findStandardGenre(id);
    }

    @Test
    void returnsRawCategoryPageWithV1FiltersAndDetail() {
        UUID id = UUID.randomUUID();
        UUID dataSourceId = UUID.randomUUID();
        CatalogPageRequest request = CatalogPageRequest.of(2, 25, List.of("sourceName,desc"));
        RawCategoryQuery query = new RawCategoryQuery("动漫", dataSourceId, true);
        RawCategoryRead category = new RawCategoryRead(
                id,
                "1",
                "动漫",
                dataSourceId,
                "dev-source",
                null,
                null,
                null,
                null);
        CatalogPage<RawCategoryRead> page = CatalogPage.of(List.of(category), request, 51);
        when(catalogService.pageRawCategories(request, query)).thenReturn(page);
        when(catalogService.findRawCategory(id)).thenReturn(Optional.of(category));

        assertPageEquals(page, controller.pageRawCategories(
                2,
                25,
                List.of("sourceName,desc"),
                "动漫",
                dataSourceId,
                true));
        assertEquals(ResponseEntity.ok(category), controller.getRawCategory(id));
        verify(catalogService).pageRawCategories(request, query);
        verify(catalogService).findRawCategory(id);
    }

    private static <T> void assertPageEquals(CatalogPage<T> expected, PageEnvelope<T> actual) {
        assertEquals(expected.content(), actual.content());
        assertEquals(expected.number(), actual.page().number());
        assertEquals(expected.size(), actual.page().size());
        assertEquals(expected.totalElements(), actual.page().totalElements());
        assertEquals(expected.totalPages(), actual.page().totalPages());
        assertEquals(expected.first(), actual.page().first());
        assertEquals(expected.last(), actual.page().last());
        assertEquals(expected.numberOfElements(), actual.page().numberOfElements());
        assertEquals(expected.empty(), actual.page().empty());
    }
}

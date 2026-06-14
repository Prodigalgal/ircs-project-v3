package com.prodigalgal.ircs.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        assertEquals(page, controller.pageDataSources(0, 50, List.of("name,asc")));
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

        assertEquals(page, controller.pageStandardCategories(1, 10, List.of("name", "desc"), "mov"));
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

        assertEquals(page, controller.pageStandardGenres(0, 20, List.of("name,asc"), "act"));
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

        assertEquals(page, controller.pageRawCategories(
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

    @Test
    void returnsRawGenreAndRawAreaReadContracts() {
        CatalogPageRequest request = CatalogPageRequest.of(0, 20, List.of("sourceValue,asc"));
        RawValueQuery query = new RawValueQuery("动作", false);
        RawGenreRead genre = new RawGenreRead(UUID.randomUUID(), "动作片", UUID.randomUUID(), "动作");
        RawAreaRead area = new RawAreaRead(UUID.randomUUID(), "大陆", UUID.randomUUID(), "中国大陆", null, null);
        CatalogPage<RawGenreRead> genrePage = CatalogPage.of(List.of(genre), request, 1);
        CatalogPage<RawAreaRead> areaPage = CatalogPage.of(List.of(area), request, 1);
        when(catalogService.pageRawGenres(request, query)).thenReturn(genrePage);
        when(catalogService.pageRawAreas(request, query)).thenReturn(areaPage);
        when(catalogService.findRawArea(area.id())).thenReturn(Optional.of(area));

        assertEquals(genrePage, controller.pageRawGenres(0, 20, List.of("sourceValue,asc"), "动作", false));
        assertEquals(areaPage, controller.pageRawAreas(0, 20, List.of("sourceValue,asc"), "动作", false));
        assertEquals(ResponseEntity.ok(area), controller.getRawArea(area.id()));
        verify(catalogService).pageRawGenres(request, query);
        verify(catalogService).pageRawAreas(request, query);
        verify(catalogService).findRawArea(area.id());
    }

    @Test
    void returnsRawLanguagePageDetailAndTrace() {
        UUID id = UUID.randomUUID();
        CatalogPageRequest request = CatalogPageRequest.of(0, 20, List.of("sourceValue,asc"));
        RawValueQuery query = new RawValueQuery("粤语", true);
        RawLanguageRead language = new RawLanguageRead(id, "粤语中字", null, null, null, null);
        LanguageTraceRead trace = new LanguageTraceRead(
                UUID.randomUUID(),
                "Example",
                "abc-1",
                UUID.randomUUID(),
                "dev-source",
                "https://example.invalid/detail?ids=abc-1");
        CatalogPage<RawLanguageRead> page = CatalogPage.of(List.of(language), request, 1);
        when(catalogService.pageRawLanguages(request, query)).thenReturn(page);
        when(catalogService.findRawLanguage(id)).thenReturn(Optional.of(language));
        when(catalogService.traceRawLanguage(id)).thenReturn(List.of(trace));

        assertEquals(page, controller.pageRawLanguages(0, 20, List.of("sourceValue,asc"), "粤语", true));
        assertEquals(ResponseEntity.ok(language), controller.getRawLanguage(id));
        assertEquals(List.of(trace), controller.traceRawLanguage(id));
        verify(catalogService).pageRawLanguages(request, query);
        verify(catalogService).findRawLanguage(id);
        verify(catalogService).traceRawLanguage(id);
    }
}

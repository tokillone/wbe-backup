package com.licong.webbackup.controller;

import com.licong.webbackup.common.ApiResponse;
import com.licong.webbackup.dto.map.MapFilterResponse;
import com.licong.webbackup.dto.map.MapStatsResponse;
import com.licong.webbackup.service.MapVisualizationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MapVisualizationControllerCacheTest {

    @Test
    void publicReadOnlyResponsesExposeCacheControlAndEtag() {
        MapVisualizationService service = mock(MapVisualizationService.class);
        MapFilterResponse filters = MapFilterResponse.builder()
                .targetClasses(List.of())
                .categories(List.of())
                .categoriesByTargetClass(Map.of())
                .subcategoriesByCategory(Map.of())
                .biomarkersByCategorySubcategory(Map.of())
                .yearsBySelection(Map.of())
                .build();
        when(service.getFilters()).thenReturn(filters);
        MapVisualizationController controller = new MapVisualizationController(service);

        ResponseEntity<ApiResponse<MapFilterResponse>> first = controller.filters(null);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getHeaders().getETag()).isNotBlank();
        assertThat(first.getHeaders().getCacheControl())
                .contains("public")
                .contains("max-age=21600")
                .contains("must-revalidate");

        ResponseEntity<ApiResponse<MapFilterResponse>> notModified =
                controller.filters(first.getHeaders().getETag());

        assertThat(notModified.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(notModified.getBody()).isNull();
        assertThat(notModified.getHeaders().getETag()).isEqualTo(first.getHeaders().getETag());
    }

    @Test
    void statsUseShortPublicCachingAndClusterPostsAreNeverStored() {
        MapVisualizationService service = mock(MapVisualizationService.class);
        when(service.getStats(null, null, null, null, null, null))
                .thenReturn(MapStatsResponse.builder().regions(List.of()).points(List.of()).build());
        MapVisualizationController controller = new MapVisualizationController(service);

        ResponseEntity<ApiResponse<MapStatsResponse>> stats =
                controller.stats(null, null, null, null, null, null, null);

        assertThat(stats.getHeaders().getCacheControl())
                .contains("public")
                .contains("max-age=300");
        assertThat(stats.getHeaders().getFirst(HttpHeaders.ETAG)).isNotBlank();
        assertThat(controller.clusterDetail(null).getHeaders().getCacheControl()).isEqualTo("no-store");
    }
}

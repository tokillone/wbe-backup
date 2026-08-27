package com.licong.webbackup.controller;

import com.licong.webbackup.dto.HomeOverviewResponse;
import com.licong.webbackup.service.HomeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HomeControllerTest {

    @Test
    void publicOverviewUsesShortSharedCachePolicy() {
        HomeService service = mock(HomeService.class);
        HomeOverviewResponse overview = HomeOverviewResponse.builder()
                .biomarkerFrequencies(List.of())
                .targetCategoryOptions(List.of())
                .build();
        when(service.getOverview(null, null, null, null)).thenReturn(overview);

        ResponseEntity<?> response = new HomeController(service)
                .overview(null, null, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl())
                .isEqualTo("max-age=300, public, stale-while-revalidate=600");
    }
}

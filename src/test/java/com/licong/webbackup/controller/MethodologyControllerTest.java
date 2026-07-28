package com.licong.webbackup.controller;

import com.licong.webbackup.dto.methodology.MethodologyDataResponse;
import com.licong.webbackup.service.MethodologyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MethodologyControllerTest {

    private MethodologyService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(MethodologyService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MethodologyController(service)).build();
        when(service.getVersion()).thenReturn("abc123");
    }

    @Test
    void returnsPublicCacheHeadersAndRepresentationEtag() throws Exception {
        when(service.getOverview()).thenReturn(Map.of("rowCount", 22738));

        mockMvc.perform(get("/api/methodology/overview"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"abc123-overview\""))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(header().string("Cache-Control", containsString("max-age=3600")))
                .andExpect(jsonPath("$.data.rowCount").value(22738));
    }

    @Test
    void returnsNotModifiedWithoutLoadingTheLargePayload() throws Exception {
        mockMvc.perform(get("/api/methodology/records")
                        .header("If-None-Match", "\"abc123-records\""))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", "\"abc123-records\""))
                .andExpect(header().string("Cache-Control", containsString("public")));

        verify(service, never()).getRecords();
    }

    @Test
    void keepsTheLegacyCombinedDataEndpointCompatible() throws Exception {
        when(service.getData()).thenReturn(MethodologyDataResponse.builder()
                .meta(Map.of("rowCount", 22738))
                .records(List.of())
                .samplingMethods(List.of())
                .options(Map.of())
                .build());

        mockMvc.perform(get("/api/methodology/data"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"abc123-data\""))
                .andExpect(jsonPath("$.data.meta.rowCount").value(22738))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.samplingMethods").isArray())
                .andExpect(jsonPath("$.data.options").isMap());
    }
}

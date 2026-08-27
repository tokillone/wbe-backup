package com.licong.webbackup.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.licong.webbackup.dto.coremarker.CoreMarkerPriorityOverviewResponse;
import com.licong.webbackup.service.CoreMarkerPriorityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CoreMarkerPriorityControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CoreMarkerPriorityService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(CoreMarkerPriorityService.class);
        mockMvc = standaloneSetup(new CoreMarkerPriorityController(service)).build();
    }

    @Test
    void overviewDisablesBrowserCachingAndReturnsNotModifiedForMatchingEtag() throws Exception {
        JsonNode row = objectMapper.readTree("{\"id\":1,\"totalScore\":90}");
        JsonNode summary = objectMapper.readTree("{\"rowCount\":1}");
        when(service.getOverview()).thenReturn(new CoreMarkerPriorityOverviewResponse(List.of(row), summary));

        MvcResult firstResponse = mockMvc.perform(get("/api/core-marker-priority/overview"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.data.rows[0].totalScore").value(90))
                .andReturn();

        String etag = firstResponse.getResponse().getHeader(HttpHeaders.ETAG);
        MvcResult notModifiedResponse = mockMvc.perform(
                        get("/api/core-marker-priority/overview").header(HttpHeaders.IF_NONE_MATCH, etag)
                )
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag))
                .andReturn();
        assertThat(notModifiedResponse.getResponse().getHeaders(HttpHeaders.ETAG)).containsExactly(etag);
    }

    @Test
    void detailUsesAnIndependentEtag() throws Exception {
        JsonNode detail = objectMapper.readTree("{\"doiList\":\"10.1000/test\"}");
        when(service.getDetail("1")).thenReturn(detail);

        MvcResult firstResponse = mockMvc.perform(get("/api/core-marker-priority/details/1"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.data.doiList").value("10.1000/test"))
                .andReturn();

        String etag = firstResponse.getResponse().getHeader(HttpHeaders.ETAG);
        mockMvc.perform(get("/api/core-marker-priority/details/1").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());
    }
}

package com.licong.webbackup.controller;

import com.licong.webbackup.dto.sankey.Icd11SankeyCategoryResponse;
import com.licong.webbackup.dto.sankey.Icd11SankeyGraphResponse;
import com.licong.webbackup.service.Icd11SankeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Icd11SankeyControllerTest {

    private Icd11SankeyService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(Icd11SankeyService.class);
        when(service.cacheRevision()).thenReturn(42L);
        mockMvc = MockMvcBuilders.standaloneSetup(new Icd11SankeyController(service)).build();
    }

    @Test
    void exposesCategoriesOnProductionApiPathWithRevalidatingPublicCache() throws Exception {
        when(service.getCategories()).thenReturn(Icd11SankeyCategoryResponse.builder()
                .categories(List.of("A 消化道和代谢系统药物"))
                .defaultCategory("ALL")
                .build());

        mockMvc.perform(get("/api/icd11-sankey/categories"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(header().string("Cache-Control", containsString("no-cache")))
                .andExpect(jsonPath("$.data.defaultCategory").value("ALL"))
                .andExpect(jsonPath("$.data.categories[0]").value("A 消化道和代谢系统药物"));
    }

    @Test
    void graphV2HonorsConditionalRequestWithoutSerializingAnotherPayload() throws Exception {
        String category = "N 神经系统药物";
        when(service.getGraph(category)).thenReturn(Icd11SankeyGraphResponse.builder()
                .category(category)
                .nodes(List.of())
                .links(List.of())
                .paths(List.of())
                .level1Colors(Map.of())
                .stats(null)
                .build());

        String eTag = mockMvc.perform(get("/api/icd11-sankey/graph-v2")
                        .param("category", category))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value(category))
                .andReturn()
                .getResponse()
                .getHeader("ETag");

        mockMvc.perform(get("/api/icd11-sankey/graph-v2")
                        .param("category", category)
                        .header("If-None-Match", eTag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", eTag));

        verify(service, org.mockito.Mockito.times(2)).getGraph(category);
    }
}

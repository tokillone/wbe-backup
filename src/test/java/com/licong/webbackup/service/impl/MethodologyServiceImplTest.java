package com.licong.webbackup.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.licong.webbackup.dto.methodology.MethodologyOptionRow;
import com.licong.webbackup.dto.methodology.MethodologyRecordResponse;
import com.licong.webbackup.dto.methodology.MethodologySamplingMethodRow;
import com.licong.webbackup.mapper.MethodologyMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MethodologyServiceImplTest {

    @Test
    void assemblesDatabaseRowsIntoFrontendPayload() {
        MethodologyMapper mapper = mock(MethodologyMapper.class);
        when(mapper.findSourceChecksum()).thenReturn("abc123");
        when(mapper.findMetaJson()).thenReturn("{\"sourceName\":\"WBE汇总表.xlsx\",\"rowCount\":22738}");
        when(mapper.findAllRecords()).thenReturn(List.of(
                MethodologyRecordResponse.builder().doc("WBE0001").samplingStandard("时间比例复合采样").build()
        ));
        when(mapper.findSamplingMethods()).thenReturn(List.of(
                new MethodologySamplingMethodRow(
                        "时间比例复合采样",
                        "[\"复合采样\"]",
                        "[\"进水样\"]",
                        "[\"时间比例\"]",
                        "[\"24 h\"]",
                        "[\"不适用\"]",
                        "[\"已明确或不涉及\"]",
                        84,
                        6556)
        ));
        when(mapper.findOptions()).thenReturn(List.of(
                new MethodologyOptionRow("country", "China"),
                new MethodologyOptionRow("country", "Australia"),
                new MethodologyOptionRow("prescription", "处方药")
        ));

        var response = new MethodologyServiceImpl(mapper, new ObjectMapper()).getData();

        assertThat(response.getMeta()).containsEntry("rowCount", 22738);
        assertThat(response.getRecords()).singleElement().extracting("doc").isEqualTo("WBE0001");
        assertThat(response.getSamplingMethods()).singleElement().satisfies(method -> {
            assertThat(method.getSamplingClass()).containsExactly("复合采样");
            assertThat(method.getAuditSourceGroups()).isEqualTo(84);
        });
        assertThat(response.getOptions().get("country")).containsExactly("China", "Australia");
        assertThat(new MethodologyServiceImpl(mapper, new ObjectMapper()).getVersion()).isEqualTo("abc123");
    }
}

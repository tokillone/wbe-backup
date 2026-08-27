package com.licong.webbackup.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.licong.webbackup.dto.coremarker.CoreMarkerPriorityOverviewResponse;
import com.licong.webbackup.exception.BusinessException;
import com.licong.webbackup.repository.CoreMarkerPriorityRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoreMarkerPriorityServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsDatabaseOverviewWithoutChangingItsShape() throws Exception {
        CoreMarkerPriorityRepository repository = mock(CoreMarkerPriorityRepository.class);
        JsonNode row = objectMapper.readTree("{\"id\":1,\"biomarker\":\"Caffeine\",\"totalScore\":90}");
        JsonNode summary = objectMapper.readTree("{\"rowCount\":1,\"scoreVersion\":\"TEST\"}");
        when(repository.findOverview()).thenReturn(new CoreMarkerPriorityOverviewResponse(List.of(row), summary));

        CoreMarkerPriorityOverviewResponse result = new CoreMarkerPriorityServiceImpl(repository).getOverview();

        assertThat(result.rows()).containsExactly(row);
        assertThat(result.summary()).isEqualTo(summary);
    }

    @Test
    void returnsOneMarkerDetailFromDatabase() throws Exception {
        CoreMarkerPriorityRepository repository = mock(CoreMarkerPriorityRepository.class);
        JsonNode detail = objectMapper.readTree("{\"countryList\":\"China\",\"doiList\":\"10.1000/test\"}");
        when(repository.findDetail("1")).thenReturn(Optional.of(detail));

        JsonNode result = new CoreMarkerPriorityServiceImpl(repository).getDetail("1");

        assertThat(result).isEqualTo(detail);
    }

    @Test
    void reportsMissingMarkerAsNotFound() {
        CoreMarkerPriorityRepository repository = mock(CoreMarkerPriorityRepository.class);
        when(repository.findDetail("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new CoreMarkerPriorityServiceImpl(repository).getDetail("missing"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(404));
    }
}

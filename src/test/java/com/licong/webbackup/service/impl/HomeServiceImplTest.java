package com.licong.webbackup.service.impl;

import com.licong.webbackup.dto.BiomarkerFrequencyResponse;
import com.licong.webbackup.dto.HomeOverviewResponse;
import com.licong.webbackup.dto.HomeSubclassRow;
import com.licong.webbackup.dto.HomeTrendRow;
import com.licong.webbackup.mapper.HomeMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeServiceImplTest {

    @Test
    void batchesDetailsWithoutChangingPerCategoryResults() {
        HomeMapper mapper = mock(HomeMapper.class);
        BiomarkerFrequencyResponse first = biomarker("A");
        BiomarkerFrequencyResponse second = biomarker("B");
        when(mapper.findTopBiomarkerFrequencies("all", "all", 20, 1))
                .thenReturn(List.of(first, second));
        when(mapper.findCategorySubclasses("all", "all", List.of("A", "B")))
                .thenReturn(List.of(subclass("A", "A1", 4L), subclass("B", "B1", 2L)));
        when(mapper.findCategoryBiomarkers("all", "all", List.of("A", "B")))
                .thenReturn(List.of(trend("A", "A-marker", 3L), trend("B", "B-marker", 1L)));
        when(mapper.findTargetCategoryOptions()).thenReturn(List.of());

        HomeOverviewResponse response = new HomeServiceImpl(mapper)
                .getOverview(null, null, null, null);

        assertThat(response.getBiomarkerFrequencies()).containsExactly(first, second);
        assertThat(first.getSubclassOptions()).singleElement().satisfies(item -> {
            assertThat(item.getName()).isEqualTo("A1");
            assertThat(item.getFrequency()).isEqualTo(4L);
        });
        assertThat(first.getTrend()).singleElement().satisfies(item -> {
            assertThat(item.getPeriod()).isEqualTo("A-marker");
            assertThat(item.getFrequency()).isEqualTo(3L);
        });
        assertThat(second.getSubclassOptions()).extracting("name").containsExactly("B1");
        assertThat(second.getTrend()).extracting("period").containsExactly("B-marker");
        assertThat(response.getTargetCategoryOptions()).extracting("name").containsExactly("全部");

        verify(mapper).findCategorySubclasses("all", "all", List.of("A", "B"));
        verify(mapper).findCategoryBiomarkers("all", "all", List.of("A", "B"));
    }

    @Test
    void skipsBatchDetailQueriesWhenOverviewIsEmpty() {
        HomeMapper mapper = mock(HomeMapper.class);
        when(mapper.findTopBiomarkerFrequencies("drug", "all", 1, 1)).thenReturn(List.of());
        when(mapper.findTargetCategoryOptions()).thenReturn(List.of());

        HomeOverviewResponse response = new HomeServiceImpl(mapper)
                .getOverview(0, 0, "DRUG", "全部");

        assertThat(response.getBiomarkerFrequencies()).isEmpty();
        verify(mapper, never()).findCategorySubclasses(eq("drug"), eq("all"), anyList());
        verify(mapper, never()).findCategoryBiomarkers(eq("drug"), eq("all"), anyList());
    }

    private BiomarkerFrequencyResponse biomarker(String name) {
        BiomarkerFrequencyResponse response = new BiomarkerFrequencyResponse();
        response.setName(name);
        return response;
    }

    private HomeSubclassRow subclass(String category, String name, long frequency) {
        HomeSubclassRow row = new HomeSubclassRow();
        row.setCategoryName(category);
        row.setName(name);
        row.setFrequency(frequency);
        row.setBiomarkerCount(1L);
        return row;
    }

    private HomeTrendRow trend(String category, String period, long frequency) {
        HomeTrendRow row = new HomeTrendRow();
        row.setCategoryName(category);
        row.setPeriod(period);
        row.setSubclass("默认");
        row.setFrequency(frequency);
        return row;
    }
}

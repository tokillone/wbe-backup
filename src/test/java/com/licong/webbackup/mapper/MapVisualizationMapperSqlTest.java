package com.licong.webbackup.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MapVisualizationMapperSqlTest {

    @Test
    void topBiomarkersUseTheSameAggregateSourceAsMapBubbles() throws Exception {
        Method method = Arrays.stream(MapVisualizationMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("findTopBiomarkersForLocations"))
                .findFirst()
                .orElseThrow();
        Select select = method.getAnnotation(Select.class);
        String sql = String.join("\n", select.value());

        assertThat(sql)
                .contains("FROM map_pndl_stats")
                .contains("biomarker_key != 'ALL'")
                .contains("year_label = #{year}")
                .doesNotContain("JOIN record_site_bridge");
    }
}

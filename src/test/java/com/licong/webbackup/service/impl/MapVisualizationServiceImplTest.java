package com.licong.webbackup.service.impl;

import com.licong.webbackup.dto.map.MapFilterResponse;
import com.licong.webbackup.dto.map.MapFilterRow;
import com.licong.webbackup.dto.map.MapRegionStatResponse;
import com.licong.webbackup.dto.map.MapStatsResponse;
import com.licong.webbackup.mapper.MapVisualizationMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MapVisualizationServiceImplTest {

    @Test
    void filtersAlwaysExposeAllSubstanceCategory() {
        MapVisualizationMapper mapper = mock(MapVisualizationMapper.class);
        when(mapper.findFilterRows()).thenReturn(List.of(filterRow("烟草使用标志物")));

        MapVisualizationServiceImpl service = new MapVisualizationServiceImpl(mapper);
        MapFilterResponse filters = service.getFilters();

        assertThat(filters.getCategories()).startsWith("全部目标物质类别");
        assertThat(filters.getCategoriesByTargetClass().get("抗生素")).startsWith("全部目标物质类别");
        assertThat(filters.getDefaultSelection().getCategory()).isEqualTo("全部目标物质类别");
        assertThat(filters.getDefaultSelection().getSubcategory()).isEqualTo("全部小类");
    }

    @Test
    void allCategoryStatsUseExactAggregateWhenAvailable() {
        MapVisualizationMapper mapper = mock(MapVisualizationMapper.class);
        MapRegionStatResponse exact = stat(
                "city",
                "china|zhejiang|ningbo",
                "宁波市",
                "全部目标物质类别",
                "全部小类",
                "ALL",
                "18",
                5);
        when(mapper.findStats(
                eq("全部目标物质类别"),
                eq("ALL"),
                eq("全部小类"),
                eq("ALL"),
                eq("全部年份"),
                any()))
                .thenReturn(List.of(exact));

        MapVisualizationServiceImpl service = new MapVisualizationServiceImpl(mapper);
        MapStatsResponse stats = service.getStats("ALL", "全部目标物质类别", "全部小类", "ALL", "全部年份", "city");

        assertThat(stats.getRegions()).containsExactly(exact);
        assertThat(stats.getPoints()).containsExactly(exact);
        verify(mapper, never()).findStatsForAnyCategory(any(), any(), any(), any(), any(), any());
    }

    @Test
    void allCategoryStatsFallBackWhenExactAggregateHasZeroCounts() {
        MapVisualizationMapper mapper = mock(MapVisualizationMapper.class);
        MapRegionStatResponse zeroAggregate = stat(
                "city",
                "china|zhejiang|ningbo",
                "宁波市",
                "全部目标物质类别",
                "全部小类",
                "ALL",
                "0",
                0);
        zeroAggregate.setDoiCount(0L);
        zeroAggregate.setPointCount(0L);
        when(mapper.findStats(
                eq("全部目标物质类别"),
                eq("ALL"),
                eq("全部小类"),
                eq("ALL"),
                eq("全部年份"),
                any()))
                .thenReturn(List.of(zeroAggregate));
        when(mapper.findStatsForAnyCategory(
                eq("全部目标物质类别"),
                eq("ALL"),
                eq("全部小类"),
                eq("ALL"),
                eq("全部年份"),
                any()))
                .thenReturn(List.of(
                        stat("city", "china|zhejiang|ningbo", "宁波市", "烟草使用标志物", "尼古丁代谢物", "COTININE", "2022", "10", 3),
                        stat("city", "china|zhejiang|ningbo", "宁波市", "抗生素", "大环内酯", "AZITHROMYCIN", "2023", "20", 7)
                ));

        MapVisualizationServiceImpl service = new MapVisualizationServiceImpl(mapper);
        MapStatsResponse stats = service.getStats("ALL", "全部目标物质类别", "全部小类", "ALL", "全部年份", "city");

        assertThat(stats.getRegions()).hasSize(1);
        assertThat(stats.getRegions().get(0).getRecordCount()).isEqualTo(10L);
        assertThat(stats.getRegions().get(0).getPndlGeomeanMgD1000inh()).isEqualByComparingTo("17.000000");
    }

    @Test
    void allCategoryStatsFallBackToMergedConcreteCategories() {
        MapVisualizationMapper mapper = mock(MapVisualizationMapper.class);
        when(mapper.findStats(
                eq("全部目标物质类别"),
                eq("ALL"),
                eq("全部小类"),
                eq("ALL"),
                eq("全部年份"),
                any()))
                .thenReturn(List.of());
        when(mapper.findStatsForAnyCategory(
                eq("全部目标物质类别"),
                eq("ALL"),
                eq("全部小类"),
                eq("ALL"),
                eq("全部年份"),
                any()))
                .thenReturn(List.of(
                        stat("city", "china|zhejiang|ningbo", "宁波市", "烟草使用标志物", "尼古丁代谢物", "COTININE", "2022", "10", 3),
                        stat("city", "china|zhejiang|ningbo", "宁波市", "抗生素", "大环内酯", "AZITHROMYCIN", "2023", "20", 7)
                ));

        MapVisualizationServiceImpl service = new MapVisualizationServiceImpl(mapper);
        MapStatsResponse stats = service.getStats("ALL", "全部目标物质类别", "全部小类", "ALL", "全部年份", "city");

        assertThat(stats.getRegions()).hasSize(1);
        assertThat(stats.getPoints()).hasSize(1);
        assertThat(stats.getRegions().get(0).getCategory()).isEqualTo("全部目标物质类别");
        assertThat(stats.getRegions().get(0).getRecordCount()).isEqualTo(10L);
        assertThat(stats.getRegions().get(0).getPndlGeomeanMgD1000inh()).isEqualByComparingTo("17.000000");
    }

    @Test
    void allCategoryStatsAcceptDisplayAllShorthand() {
        MapVisualizationMapper mapper = mock(MapVisualizationMapper.class);
        when(mapper.findStats(
                eq("全部目标物质类别"),
                eq("ALL"),
                eq("全部小类"),
                eq("ALL"),
                eq("全部年份"),
                any()))
                .thenReturn(List.of());
        when(mapper.findStatsForAnyCategory(
                eq("全部目标物质类别"),
                eq("ALL"),
                eq("全部小类"),
                eq("ALL"),
                eq("全部年份"),
                any()))
                .thenReturn(List.of(
                        stat("city", "china|zhejiang|ningbo", "宁波市", "烟草使用标志物", "尼古丁代谢物", "COTININE", "2022", "10", 3)
                ));

        MapVisualizationServiceImpl service = new MapVisualizationServiceImpl(mapper);
        MapStatsResponse stats = service.getStats("全部", "全部", "全部", "全部", "全部", "city");

        assertThat(stats.getRegions()).hasSize(1);
        assertThat(stats.getRegions().get(0).getCategory()).isEqualTo("全部目标物质类别");
        assertThat(stats.getRegions().get(0).getSubcategory()).isEqualTo("全部小类");
        assertThat(stats.getRegions().get(0).getBiomarkerKey()).isEqualTo("ALL");
        assertThat(stats.getRegions().get(0).getYearLabel()).isEqualTo("全部年份");
    }

    @Test
    void allCategoryStatsFallBackToMergedConcreteYears() {
        MapVisualizationMapper mapper = mock(MapVisualizationMapper.class);
        when(mapper.findStats(
                eq("全部目标物质类别"),
                eq("ALL"),
                eq("全部小类"),
                eq("ALL"),
                eq("全部年份"),
                any()))
                .thenReturn(List.of());
        when(mapper.findStatsForAnyCategory(
                eq("全部目标物质类别"),
                eq("ALL"),
                eq("全部小类"),
                eq("ALL"),
                eq("全部年份"),
                any()))
                .thenReturn(List.of(
                        stat("city", "china|zhejiang|ningbo", "宁波市", "烟草使用标志物", "尼古丁代谢物", "COTININE", "2022", "10", 3),
                        stat("city", "china|zhejiang|ningbo", "宁波市", "抗生素", "大环内酯", "AZITHROMYCIN", "2023", "20", 7)
                ));

        MapVisualizationServiceImpl service = new MapVisualizationServiceImpl(mapper);
        MapStatsResponse stats = service.getStats("ALL", "全部目标物质类别", "全部小类", "ALL", "全部年份", "city");

        assertThat(stats.getRegions()).hasSize(1);
        assertThat(stats.getRegions().get(0).getRecordCount()).isEqualTo(10L);
        assertThat(stats.getRegions().get(0).getPndlSources()).isEqualTo("多类别合并显示");
    }

    @Test
    void allCategoryFallbackIgnoresIncompleteAllAggregates() {
        MapVisualizationMapper mapper = mock(MapVisualizationMapper.class);
        when(mapper.findStats(
                eq("全部目标物质类别"),
                eq("ALL"),
                eq("全部小类"),
                eq("ALL"),
                eq("全部年份"),
                any()))
                .thenReturn(List.of());
        when(mapper.findStatsForAnyCategory(
                eq("全部目标物质类别"),
                eq("ALL"),
                eq("全部小类"),
                eq("ALL"),
                eq("全部年份"),
                any()))
                .thenReturn(List.of(
                        stat("city", "china|zhejiang|ningbo", "宁波市", "烟草使用标志物", "全部小类", "ALL", "全部年份", "18", 5),
                        stat("city", "china|zhejiang|ningbo", "宁波市", "烟草使用标志物", "尼古丁代谢物", "COTININE", "2022", "10", 3),
                        stat("city", "china|zhejiang|ningbo", "宁波市", "烟草使用标志物", "尼古丁代谢物", "COTININE", "2023", "20", 7)
                ));

        MapVisualizationServiceImpl service = new MapVisualizationServiceImpl(mapper);
        MapStatsResponse stats = service.getStats("ALL", "全部目标物质类别", "全部小类", "ALL", "全部年份", "city");

        assertThat(stats.getRegions()).hasSize(1);
        assertThat(stats.getRegions().get(0).getRecordCount()).isEqualTo(10L);
        assertThat(stats.getRegions().get(0).getPndlGeomeanMgD1000inh()).isEqualByComparingTo("17.000000");
    }

    private MapFilterRow filterRow(String category) {
        MapFilterRow row = new MapFilterRow();
        row.setTargetClass("抗生素");
        row.setCategory(category);
        row.setSubcategory("全部小类");
        row.setBiomarkerKey("ALL");
        row.setBiomarkerLabel("全部 biomarker");
        row.setYearLabel("全部年份");
        return row;
    }

    private MapRegionStatResponse stat(
            String level,
            String geoKey,
            String name,
            String category,
            String subcategory,
            String biomarkerKey,
            String pndl,
            long records
    ) {
        MapRegionStatResponse row = new MapRegionStatResponse();
        row.setLevel(level);
        row.setGeoKey(geoKey);
        row.setDisplayName(name);
        row.setCountry("China");
        row.setProvince("Zhejiang");
        row.setCity(name);
        row.setLatitude(new BigDecimal("29.8683"));
        row.setLongitude(new BigDecimal("121.5440"));
        row.setCategory(category);
        row.setSubcategory(subcategory);
        row.setBiomarkerKey(biomarkerKey);
        row.setBiomarkerLabel(biomarkerKey);
        row.setYearLabel("全部年份");
        row.setPndlGeomeanMgD1000inh(new BigDecimal(pndl));
        row.setPndlMeanMgD1000inh(new BigDecimal(pndl));
        row.setPndlMinMgD1000inh(new BigDecimal(pndl));
        row.setPndlMaxMgD1000inh(new BigDecimal(pndl));
        row.setRecordCount(records);
        row.setDoiCount(1L);
        row.setYearCount(1L);
        row.setCityCount(1L);
        row.setPointCount(1L);
        return row;
    }

    private MapRegionStatResponse stat(
            String level,
            String geoKey,
            String name,
            String category,
            String subcategory,
            String biomarkerKey,
            String year,
            String pndl,
            long records
    ) {
        MapRegionStatResponse row = stat(level, geoKey, name, category, subcategory, biomarkerKey, pndl, records);
        row.setYearLabel(year);
        return row;
    }
}

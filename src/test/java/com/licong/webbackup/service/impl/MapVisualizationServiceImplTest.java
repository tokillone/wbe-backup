package com.licong.webbackup.service.impl;

import com.licong.webbackup.dto.map.MapFilterResponse;
import com.licong.webbackup.dto.map.MapFilterRow;
import com.licong.webbackup.dto.map.MapDetailResponse;
import com.licong.webbackup.dto.map.MapMetricObservationRow;
import com.licong.webbackup.dto.map.MapRegionStatResponse;
import com.licong.webbackup.dto.map.MapSourceRecordResponse;
import com.licong.webbackup.dto.map.MapStatsResponse;
import com.licong.webbackup.mapper.MapVisualizationMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MapVisualizationServiceImplTest {

    @Test
    void medianHandlesOddAndEvenValueCounts() {
        assertThat(MapVisualizationServiceImpl.median(List.of(
                new BigDecimal("9"), new BigDecimal("1"), new BigDecimal("5"))))
                .isEqualByComparingTo("5");
        assertThat(MapVisualizationServiceImpl.median(List.of(
                new BigDecimal("8"), new BigDecimal("2"), new BigDecimal("4"), new BigDecimal("6"))))
                .isEqualByComparingTo("5");
    }

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
        verify(mapper, never()).countStatsRows();
        verify(mapper, never()).countPositivePndlRows();
        verify(mapper, never()).countConvertiblePndlRows();
        verify(mapper, never()).countMappablePndlRows();
        verify(mapper, never()).countMappableGeoLocations();
    }

    @Test
    void statsKeepCountryAdmin1AndCityRowsUnderTheSameMarkerFilter() {
        MapVisualizationMapper mapper = mock(MapVisualizationMapper.class);
        List<MapRegionStatResponse> hierarchy = List.of(
                stat("country", "china", "中国", "抗生素", "大环内酯", "AZITHROMYCIN", "20", 10),
                stat("admin1", "china|zhejiang", "浙江省", "抗生素", "大环内酯", "AZITHROMYCIN", "18", 8),
                stat("city", "china|zhejiang|ningbo", "宁波市", "抗生素", "大环内酯", "AZITHROMYCIN", "16", 5)
        );
        when(mapper.findStats(
                eq("抗生素"), eq("药物类"), eq("大环内酯"), eq("AZITHROMYCIN"),
                eq("2023"), eq(List.of("country", "admin1", "city"))))
                .thenReturn(hierarchy);

        MapStatsResponse response = new MapVisualizationServiceImpl(mapper).getStats(
                "药物类", "抗生素", "大环内酯", "AZITHROMYCIN", "2023", null);

        assertThat(response.getRegions())
                .extracting(MapRegionStatResponse::getLevel)
                .containsExactly("country", "admin1", "city");
        assertThat(response.getRegions())
                .extracting(MapRegionStatResponse::getBiomarkerKey)
                .containsOnly("AZITHROMYCIN");
    }

    @Test
    void allCategoryStatsDoNotMergeConcreteRowsWhenExactAggregateHasZeroCounts() {
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

        assertThat(stats.getRegions()).containsExactly(zeroAggregate);
        verify(mapper, never()).findStatsForAnyCategory(any(), any(), any(), any(), any(), any());
    }

    @Test
    void allCategoryStatsStayEmptyInsteadOfSummingConcreteCategoryPointCounts() {
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

        assertThat(stats.getRegions()).isEmpty();
        assertThat(stats.getPoints()).isEmpty();
        verify(mapper, never()).findStatsForAnyCategory(any(), any(), any(), any(), any(), any());
    }

    @Test
    void allCategoryDisplayAllShorthandDoesNotSynthesizeMissingAggregate() {
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

        assertThat(stats.getRegions()).isEmpty();
        verify(mapper, never()).findStatsForAnyCategory(any(), any(), any(), any(), any(), any());
    }

    @Test
    void allCategoryStatsDoNotMergeConcreteYears() {
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

        assertThat(stats.getRegions()).isEmpty();
        verify(mapper, never()).findStatsForAnyCategory(any(), any(), any(), any(), any(), any());
    }

    @Test
    void incompleteConcreteRowsNeverReplaceMissingExactAggregate() {
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

        assertThat(stats.getRegions()).isEmpty();
        verify(mapper, never()).findStatsForAnyCategory(any(), any(), any(), any(), any(), any());
    }

    @Test
    void detailSummaryUsesCoverageCountsInsteadOfPndlOnlyCounts() {
        MapVisualizationMapper mapper = mock(MapVisualizationMapper.class);
        MapRegionStatResponse dalian = stat(
                "city",
                "china|liaoning|dalian",
                "大连市",
                "R05 咳嗽和感冒用药",
                "R05D 镇咳药",
                "125735",
                "7.41",
                191);
        dalian.setPointCount(19L);
        dalian.setDoiCount(1L);
        dalian.setPndlRecordCount(32L);
        dalian.setPndlPointCount(4L);
        dalian.setPndlDoiCount(1L);
        dalian.setPndlYearCount(8L);
        dalian.setBiomarkerCount(1L);
        when(mapper.findRegion(
                eq("city"),
                eq("china|liaoning|dalian"),
                any(), any(), any(), any(), any()))
                .thenReturn(dalian);

        MapVisualizationServiceImpl service = new MapVisualizationServiceImpl(mapper);
        MapDetailResponse detail = service.getDetail(
                "city",
                "china|liaoning|dalian",
                "R 呼吸系统药物",
                "R05 咳嗽和感冒用药",
                "R05D 镇咳药",
                "125735",
                "全部年份");

        assertThat(detail.getSummaryCards())
                .extracting(card -> card.getLabel() + "=" + card.getValue())
                .contains("点位数=19", "文献数=1", "记录数=191", "PNDL 年份数=8")
                .doesNotContain("点位数=4", "记录数=32");
    }

    @Test
    void detailKeepsCoverageSourcesWhenPndlIsUnavailable() {
        MapVisualizationMapper mapper = mock(MapVisualizationMapper.class);
        MapRegionStatResponse spain = stat(
                "country",
                "spain",
                "西班牙",
                "R05 咳嗽和感冒用药",
                "R05D 镇咳药",
                "125735",
                "1",
                3);
        spain.setPndlMedianMgD1000inh(null);
        spain.setCountry("Spain");
        spain.setPointCount(3L);
        spain.setDoiCount(1L);

        MapSourceRecordResponse source = new MapSourceRecordResponse();
        source.setMeasurementId(901L);
        source.setCountry("Spain");
        source.setCity("Barcelona");
        source.setConcentrationValue(new BigDecimal("26"));
        source.setConcentrationUnit("ng/L");

        when(mapper.findRegion(
                eq("country"),
                eq("spain"),
                any(), any(), any(), any(), any()))
                .thenReturn(spain);
        when(mapper.findSourceRecords(
                eq("country"),
                eq("spain"),
                any(), any(), any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(source));

        MapVisualizationServiceImpl service = new MapVisualizationServiceImpl(mapper);
        MapDetailResponse detail = service.getDetail(
                "country",
                "spain",
                "R 呼吸系统药物",
                "R05 咳嗽和感冒用药",
                "R05D 镇咳药",
                "125735",
                "全部年份");

        assertThat(detail.getRegion().getRecordCount()).isEqualTo(3L);
        assertThat(detail.getRegion().getPndlMedianMgD1000inh()).isNull();
        assertThat(detail.getSourceRecords()).singleElement().satisfies(record -> {
            assertThat(record.getPndlMgD1000inh()).isNull();
            assertThat(record.getConcentrationValue()).isEqualByComparingTo("26");
            assertThat(record.getConcentrationUnit()).isEqualTo("ng/L");
        });
    }

    @Test
    void cityDetailIncludesPndlComparisonsAndSameUnitAnnualMedianTrend() {
        MapVisualizationMapper mapper = mock(MapVisualizationMapper.class);
        MapRegionStatResponse ningbo = stat(
                "city", "china|zhejiang|ningbo", "宁波市",
                "抗生素", "大环内酯", "AZITHROMYCIN", "16", 5);
        ningbo.setParentGeoKey("china|zhejiang");
        ningbo.setCountry("China");
        when(mapper.findRegion(
                eq("city"), eq("china|zhejiang|ningbo"), eq("抗生素"), eq("药物类"),
                eq("大环内酯"), eq("AZITHROMYCIN"), eq("全部年份")))
                .thenReturn(ningbo);
        when(mapper.findRankingStats(
                eq("city"), eq("抗生素"), eq("药物类"), eq("大环内酯"),
                eq("AZITHROMYCIN"), eq("全部年份"), anyInt()))
                .thenReturn(List.of(ningbo));
        when(mapper.findComparisonStatsByScope(
                any(), any(), any(), eq("抗生素"), eq("药物类"), eq("大环内酯"),
                eq("AZITHROMYCIN"), eq("全部年份"), anyInt()))
                .thenAnswer(invocation -> {
                    String level = invocation.getArgument(0);
                    return "country".equals(level)
                            ? List.of(stat(
                                    "country", "china", "中国", "抗生素", "大环内酯",
                                    "AZITHROMYCIN", "20", 10))
                            : List.of(ningbo);
                });
        MapMetricObservationRow observation2022 = metricObservation(2022, "12");
        MapMetricObservationRow observation2023 = metricObservation(2023, "18");
        when(mapper.findMetricObservations(
                eq("city"), eq("china|zhejiang|ningbo"), eq("抗生素"), eq("药物类"),
                eq("大环内酯"), eq("AZITHROMYCIN")))
                .thenReturn(List.of(observation2022, observation2023));

        MapDetailResponse detail = new MapVisualizationServiceImpl(mapper).getDetail(
                "city", "china|zhejiang|ningbo", "药物类", "抗生素",
                "大环内酯", "AZITHROMYCIN", "全部年份");

        assertThat(detail.getRegion()).isSameAs(ningbo);
        assertThat(detail.getPndlRanking()).singleElement()
                .satisfies(row -> assertThat(row.getSelected()).isTrue());
        assertThat(detail.getPndlComparisons())
                .extracting(comparison -> comparison.getKey())
                .contains("city", "parent-city", "country");
        assertThat(detail.getTrendSeries()).singleElement().satisfies(series -> {
            assertThat(series.getMetricKey()).isEqualTo("pndl");
            assertThat(series.getUnit()).isEqualTo("mg/day/1000 inh");
            assertThat(series.getPoints())
                    .extracting(point -> point.getYear())
                    .containsExactly(2022, 2023);
        });
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

    private MapMetricObservationRow metricObservation(int year, String value) {
        MapMetricObservationRow row = new MapMetricObservationRow();
        row.setMetricKey("pndl");
        row.setMetricLabel("PNDL");
        row.setUnit("mg/day/1000 inh");
        row.setYear(year);
        row.setValue(new BigDecimal(value));
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
        row.setPndlMedianMgD1000inh(new BigDecimal(pndl));
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

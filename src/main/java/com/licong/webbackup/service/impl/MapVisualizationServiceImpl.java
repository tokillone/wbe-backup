package com.licong.webbackup.service.impl;

import com.licong.webbackup.dto.map.MapBiomarkerOptionResponse;
import com.licong.webbackup.dto.map.MapDetailResponse;
import com.licong.webbackup.dto.map.MapDiagnosticsResponse;
import com.licong.webbackup.dto.map.MapFilterResponse;
import com.licong.webbackup.dto.map.MapFilterRow;
import com.licong.webbackup.dto.map.MapFilterSelectionResponse;
import com.licong.webbackup.dto.map.MapLegendResponse;
import com.licong.webbackup.dto.map.MapRegionStatResponse;
import com.licong.webbackup.dto.map.MapStatsResponse;
import com.licong.webbackup.dto.map.MapSummaryResponse;
import com.licong.webbackup.mapper.MapVisualizationMapper;
import com.licong.webbackup.service.MapVisualizationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class MapVisualizationServiceImpl implements MapVisualizationService {

    private static final String ALL_SUBCATEGORY = "全部小类";
    private static final String ALL_BIOMARKERS = "ALL";
    private static final String ALL_YEARS = "全部年份";
    private static final String ALL_TARGET_CLASSES = "ALL";
    private static final List<String> DEFAULT_LEVELS = List.of("country", "admin1", "city");
    private static final Set<String> VALID_LEVELS = Set.of("country", "admin1", "city");
    private static final List<String> LEGEND_COLORS = List.of("#fff7bc", "#fec44f", "#fe9929", "#d95f0e", "#993404");
    private static final int DETAIL_SOURCE_LIMIT = 20;

    private final MapVisualizationMapper mapVisualizationMapper;

    public MapVisualizationServiceImpl(MapVisualizationMapper mapVisualizationMapper) {
        this.mapVisualizationMapper = mapVisualizationMapper;
    }

    @Override
    public MapFilterResponse getFilters() {
        List<MapFilterRow> rows = mapVisualizationMapper.findFilterRows();
        Map<String, LinkedHashSet<String>> categoriesByTargetClass = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> subcategoriesByCategory = new LinkedHashMap<>();
        Map<String, LinkedHashMap<String, MapBiomarkerOptionResponse>> biomarkersBySelection = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> yearsBySelection = new LinkedHashMap<>();

        for (MapFilterRow row : rows) {
            if (!StringUtils.hasText(row.getCategory())) {
                continue;
            }
            String targetClass = valueOr(row.getTargetClass(), "未分类");
            String category = row.getCategory();
            String subcategory = valueOr(row.getSubcategory(), ALL_SUBCATEGORY);
            String biomarkerKey = valueOr(row.getBiomarkerKey(), ALL_BIOMARKERS);
            String year = valueOr(row.getYearLabel(), ALL_YEARS);

            categoriesByTargetClass.computeIfAbsent(targetClass, ignored -> new LinkedHashSet<>()).add(category);
            subcategoriesByCategory.computeIfAbsent(category, ignored -> new LinkedHashSet<>()).add(subcategory);
            String categorySubcategoryKey = selectionKey(category, subcategory);
            biomarkersBySelection.computeIfAbsent(categorySubcategoryKey, ignored -> new LinkedHashMap<>())
                    .putIfAbsent(biomarkerKey, MapBiomarkerOptionResponse.builder()
                            .key(biomarkerKey)
                            .label(valueOr(row.getBiomarkerLabel(), "全部 biomarker"))
                            .cas(row.getBiomarkerCas())
                            .build());
            yearsBySelection.computeIfAbsent(selectionKey(category, subcategory, biomarkerKey), ignored -> new LinkedHashSet<>())
                    .add(year);
        }

        List<String> targetClasses = new ArrayList<>(categoriesByTargetClass.keySet());
        Map<String, List<String>> categoriesByTargetClassResponse = new LinkedHashMap<>();
        categoriesByTargetClass.forEach((key, value) -> categoriesByTargetClassResponse.put(key, new ArrayList<>(value)));
        List<String> categories = new ArrayList<>(subcategoriesByCategory.keySet());
        Map<String, List<String>> subcategoryResponse = new LinkedHashMap<>();
        subcategoriesByCategory.forEach((key, value) -> subcategoryResponse.put(key, new ArrayList<>(value)));
        Map<String, List<MapBiomarkerOptionResponse>> biomarkerResponse = new LinkedHashMap<>();
        biomarkersBySelection.forEach((key, value) -> biomarkerResponse.put(key, new ArrayList<>(value.values())));
        Map<String, List<String>> yearResponse = new LinkedHashMap<>();
        yearsBySelection.forEach((key, value) -> yearResponse.put(key, new ArrayList<>(value)));

        String defaultCategory = categories.isEmpty() ? "" : categories.get(0);
        String defaultSubcategory = subcategoryResponse.getOrDefault(defaultCategory, List.of(ALL_SUBCATEGORY))
                .stream()
                .filter(ALL_SUBCATEGORY::equals)
                .findFirst()
                .orElseGet(() -> subcategoryResponse.getOrDefault(defaultCategory, List.of(ALL_SUBCATEGORY)).get(0));
        List<MapBiomarkerOptionResponse> defaultBiomarkers =
                biomarkerResponse.getOrDefault(selectionKey(defaultCategory, defaultSubcategory), List.of());
        String defaultBiomarker = defaultBiomarkers.stream()
                .map(MapBiomarkerOptionResponse::getKey)
                .filter(ALL_BIOMARKERS::equals)
                .findFirst()
                .orElse(defaultBiomarkers.isEmpty() ? ALL_BIOMARKERS : defaultBiomarkers.get(0).getKey());
        List<String> defaultYears = yearResponse.getOrDefault(selectionKey(defaultCategory, defaultSubcategory, defaultBiomarker),
                List.of(ALL_YEARS));
        String defaultYear = defaultYears.stream()
                .filter(ALL_YEARS::equals)
                .findFirst()
                .orElse(defaultYears.get(0));

        return MapFilterResponse.builder()
                .targetClasses(targetClasses)
                .categories(categories)
                .categoriesByTargetClass(categoriesByTargetClassResponse)
                .subcategoriesByCategory(subcategoryResponse)
                .biomarkersByCategorySubcategory(biomarkerResponse)
                .yearsBySelection(yearResponse)
                .defaultSelection(MapFilterSelectionResponse.builder()
                        .targetClass(ALL_TARGET_CLASSES)
                        .category(defaultCategory)
                        .subcategory(defaultSubcategory)
                        .biomarkerKey(defaultBiomarker)
                        .year(defaultYear)
                        .build())
                .diagnostics(buildDiagnostics(rows.isEmpty() ? "地图筛选项为空，请确认聚合表已刷新且存在可映射的 PNDL 数据。" : null))
                .build();
    }

    @Override
    public MapStatsResponse getStats(String targetClass, String category, String subcategory, String biomarkerKey, String year, String levels) {
        MapFilterSelectionResponse selection = normalizeSelection(targetClass, category, subcategory, biomarkerKey, year);
        List<String> requestedLevels = normalizeLevels(levels);
        List<MapRegionStatResponse> regions = mapVisualizationMapper.findStats(
                selection.getCategory(),
                selection.getTargetClass(),
                selection.getSubcategory(),
                selection.getBiomarkerKey(),
                selection.getYear(),
                requestedLevels);
        List<MapRegionStatResponse> points = regions.stream()
                .filter(row -> row.getLatitude() != null && row.getLongitude() != null)
                .toList();

        return MapStatsResponse.builder()
                .legend(buildLegend(regions))
                .summary(buildSummary(regions, points))
                .regions(regions)
                .points(points)
                .diagnostics(buildDiagnostics(regions.isEmpty() ? "当前筛选没有可映射的 PNDL 聚合结果。" : null))
                .build();
    }

    @Override
    public MapDetailResponse getDetail(String level, String geoKey, String category, String subcategory,
                                       String biomarkerKey, String year) {
        String normalizedLevel = normalizeLevel(level);
        MapFilterSelectionResponse selection = normalizeSelection(null, category, subcategory, biomarkerKey, year);
        MapRegionStatResponse region = mapVisualizationMapper.findRegion(
                normalizedLevel,
                geoKey,
                selection.getCategory(),
                selection.getTargetClass(),
                selection.getSubcategory(),
                selection.getBiomarkerKey(),
                selection.getYear());
        return MapDetailResponse.builder()
                .region(region)
                .sources(mapVisualizationMapper.findSourceRecords(
                        normalizedLevel,
                        geoKey,
                        selection.getCategory(),
                        selection.getTargetClass(),
                        selection.getSubcategory(),
                        selection.getBiomarkerKey(),
                        selection.getYear(),
                        DETAIL_SOURCE_LIMIT))
                .build();
    }

    private MapFilterSelectionResponse normalizeSelection(String targetClass, String category, String subcategory, String biomarkerKey, String year) {
        if (StringUtils.hasText(category) && StringUtils.hasText(subcategory)
                && StringUtils.hasText(biomarkerKey) && StringUtils.hasText(year)) {
            return MapFilterSelectionResponse.builder()
                    .targetClass(StringUtils.hasText(targetClass) ? targetClass.trim() : ALL_TARGET_CLASSES)
                    .category(category.trim())
                    .subcategory(subcategory.trim())
                    .biomarkerKey(biomarkerKey.trim())
                    .year(year.trim())
                    .build();
        }
        return getFilters().getDefaultSelection();
    }

    private MapDiagnosticsResponse buildDiagnostics(String message) {
        long statsRows = mapVisualizationMapper.countStatsRows();
        long positivePndlRows = mapVisualizationMapper.countPositivePndlRows();
        long convertiblePndlRows = mapVisualizationMapper.countConvertiblePndlRows();
        long mappablePndlRows = mapVisualizationMapper.countMappablePndlRows();
        long geoLocations = mapVisualizationMapper.countMappableGeoLocations();
        String resolvedMessage = message;
        if (!StringUtils.hasText(resolvedMessage) && statsRows == 0) {
            if (positivePndlRows == 0) {
                resolvedMessage = "源数据中没有正数 PNDL 记录。";
            } else if (convertiblePndlRows == 0) {
                resolvedMessage = "源数据有 PNDL，但单位暂不能转换为 mg/day/1000 inh。";
            } else if (mappablePndlRows == 0) {
                resolvedMessage = "源数据有可转换 PNDL，但国家/省市未能匹配到地理维表。";
            } else if (geoLocations == 0) {
                resolvedMessage = "地理维表为空，请先初始化 geo_locations。";
            } else {
                resolvedMessage = "地图聚合表为空，请刷新 map_pndl_stats。";
            }
        }
        return MapDiagnosticsResponse.builder()
                .statsRowCount(statsRows)
                .positivePndlCount(positivePndlRows)
                .convertiblePndlCount(convertiblePndlRows)
                .mappablePndlCount(mappablePndlRows)
                .geoLocationCount(geoLocations)
                .message(resolvedMessage)
                .build();
    }

    private List<String> normalizeLevels(String levels) {
        if (!StringUtils.hasText(levels)) {
            return DEFAULT_LEVELS;
        }
        List<String> normalized = Arrays.stream(levels.split(","))
                .map(String::trim)
                .filter(VALID_LEVELS::contains)
                .distinct()
                .toList();
        return normalized.isEmpty() ? DEFAULT_LEVELS : normalized;
    }

    private String normalizeLevel(String level) {
        if (!VALID_LEVELS.contains(level)) {
            return "country";
        }
        return level;
    }

    private MapLegendResponse buildLegend(List<MapRegionStatResponse> regions) {
        List<BigDecimal> values = regions.stream()
                .map(MapRegionStatResponse::getPndlGeomeanMgD1000inh)
                .filter(Objects::nonNull)
                .filter(value -> value.compareTo(BigDecimal.ZERO) > 0)
                .toList();
        BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(null);
        BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(null);
        return MapLegendResponse.builder()
                .min(min)
                .max(max)
                .unit("mg/day/1000 inh")
                .colors(LEGEND_COLORS)
                .build();
    }

    private MapSummaryResponse buildSummary(List<MapRegionStatResponse> regions, List<MapRegionStatResponse> points) {
        return MapSummaryResponse.builder()
                .countryCount(regions.stream().filter(row -> "country".equals(row.getLevel())).count())
                .admin1Count(regions.stream().filter(row -> "admin1".equals(row.getLevel())).count())
                .cityCount(regions.stream().filter(row -> "city".equals(row.getLevel())).count())
                .pointCount(points.size())
                .recordCount(regions.stream().map(MapRegionStatResponse::getRecordCount).filter(Objects::nonNull).mapToLong(Long::longValue).sum())
                .doiCount(regions.stream().map(MapRegionStatResponse::getDoiCount).filter(Objects::nonNull).mapToLong(Long::longValue).sum())
                .build();
    }

    private String selectionKey(String... parts) {
        return String.join("|||", parts);
    }

    private String valueOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}

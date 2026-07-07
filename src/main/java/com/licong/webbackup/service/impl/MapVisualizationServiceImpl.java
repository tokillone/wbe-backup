package com.licong.webbackup.service.impl;

import com.licong.webbackup.dto.map.MapBiomarkerOptionResponse;
import com.licong.webbackup.dto.map.MapBreakdownItemResponse;
import com.licong.webbackup.dto.map.MapBreakdownRow;
import com.licong.webbackup.dto.map.MapClusterDetailRequest;
import com.licong.webbackup.dto.map.MapClusterLocationRequest;
import com.licong.webbackup.dto.map.MapDetailResponse;
import com.licong.webbackup.dto.map.MapDiagnosticsResponse;
import com.licong.webbackup.dto.map.MapFilterResponse;
import com.licong.webbackup.dto.map.MapFilterRow;
import com.licong.webbackup.dto.map.MapFilterSelectionResponse;
import com.licong.webbackup.dto.map.MapLegendResponse;
import com.licong.webbackup.dto.map.MapPndlRankingItemResponse;
import com.licong.webbackup.dto.map.MapRegionStatResponse;
import com.licong.webbackup.dto.map.MapSourceRecordResponse;
import com.licong.webbackup.dto.map.MapStatsResponse;
import com.licong.webbackup.dto.map.MapSummaryCardResponse;
import com.licong.webbackup.dto.map.MapSummaryResponse;
import com.licong.webbackup.dto.map.MapTopBiomarkerResponse;
import com.licong.webbackup.mapper.MapVisualizationMapper;
import com.licong.webbackup.service.MapVisualizationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
    private static final String ALL_CATEGORIES = "全部目标物质类别";
    private static final List<String> DEFAULT_LEVELS = List.of("country", "admin1", "city");
    private static final Set<String> VALID_LEVELS = Set.of("country", "admin1", "city");
    private static final List<String> LEGEND_COLORS = List.of("#fff7bc", "#fec44f", "#fe9929", "#d95f0e", "#993404");
    private static final int DETAIL_SOURCE_LIMIT = 20;
    private static final int CLUSTER_LOCATION_LIMIT = 120;
    private static final int FULL_DETAIL_LIMIT = 40;

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
        LinkedHashSet<String> allYears = new LinkedHashSet<>();

        for (MapFilterRow row : rows) {
            if (!StringUtils.hasText(row.getCategory())) {
                continue;
            }
            String targetClass = valueOr(row.getTargetClass(), "未分类");
            String category = row.getCategory();
            String subcategory = valueOr(row.getSubcategory(), ALL_SUBCATEGORY);
            String biomarkerKey = valueOr(row.getBiomarkerKey(), ALL_BIOMARKERS);
            String year = valueOr(row.getYearLabel(), ALL_YEARS);

            allYears.add(year);
            if (!ALL_TARGET_CLASSES.equals(targetClass)) {
                categoriesByTargetClass.computeIfAbsent(targetClass, ignored -> new LinkedHashSet<>()).add(category);
            }
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

        allYears.add(ALL_YEARS);
        categoriesByTargetClass.values().forEach(categories -> {
            categories.remove(ALL_CATEGORIES);
            categories.add(ALL_CATEGORIES);
        });
        subcategoriesByCategory.computeIfAbsent(ALL_CATEGORIES, ignored -> new LinkedHashSet<>()).add(ALL_SUBCATEGORY);
        biomarkersBySelection.computeIfAbsent(selectionKey(ALL_CATEGORIES, ALL_SUBCATEGORY), ignored -> new LinkedHashMap<>())
                .putIfAbsent(ALL_BIOMARKERS, MapBiomarkerOptionResponse.builder()
                        .key(ALL_BIOMARKERS)
                        .label("全部 biomarker")
                        .cas(null)
                        .build());
        yearsBySelection.computeIfAbsent(selectionKey(ALL_CATEGORIES, ALL_SUBCATEGORY, ALL_BIOMARKERS), ignored -> new LinkedHashSet<>())
                .addAll(allYears);

        List<String> targetClasses = new ArrayList<>(categoriesByTargetClass.keySet());
        Map<String, List<String>> categoriesByTargetClassResponse = new LinkedHashMap<>();
        categoriesByTargetClass.forEach((key, value) -> categoriesByTargetClassResponse.put(key, new ArrayList<>(value)));
        List<String> categories = new ArrayList<>(subcategoriesByCategory.keySet());
        moveAllCategoryToFront(categories);
        categoriesByTargetClassResponse.values().forEach(this::moveAllCategoryToFront);
        Map<String, List<String>> subcategoryResponse = new LinkedHashMap<>();
        subcategoriesByCategory.forEach((key, value) -> subcategoryResponse.put(key, new ArrayList<>(value)));
        Map<String, List<MapBiomarkerOptionResponse>> biomarkerResponse = new LinkedHashMap<>();
        biomarkersBySelection.forEach((key, value) -> biomarkerResponse.put(key, new ArrayList<>(value.values())));
        Map<String, List<String>> yearResponse = new LinkedHashMap<>();
        yearsBySelection.forEach((key, value) -> yearResponse.put(key, new ArrayList<>(value)));

        String defaultCategory = categories.contains(ALL_CATEGORIES)
                ? ALL_CATEGORIES
                : (categories.isEmpty() ? "" : categories.get(0));
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
        if (regions.isEmpty() && ALL_CATEGORIES.equals(selection.getCategory())) {
            regions = mergeAnyCategoryStats(mapVisualizationMapper.findStatsForAnyCategory(
                    selection.getTargetClass(),
                    selection.getSubcategory(),
                    selection.getBiomarkerKey(),
                    selection.getYear(),
                    requestedLevels), selection);
        }
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
    public MapDetailResponse getDetail(String level, String geoKey, String targetClass, String category,
                                       String subcategory, String biomarkerKey, String year) {
        String normalizedLevel = normalizeLevel(level);
        MapFilterSelectionResponse selection = normalizeSelection(targetClass, category, subcategory, biomarkerKey, year);
        MapRegionStatResponse region = mapVisualizationMapper.findRegion(
                normalizedLevel,
                geoKey,
                selection.getCategory(),
                selection.getTargetClass(),
                selection.getSubcategory(),
                selection.getBiomarkerKey(),
                selection.getYear());
        MapClusterLocationRequest location = clusterLocation(normalizedLevel, geoKey);
        if (region == null && ALL_CATEGORIES.equals(selection.getCategory())) {
            List<MapRegionStatResponse> merged = mergeAnyCategoryStats(
                    mapVisualizationMapper.findRegionsByKeysForAnyCategory(
                            selection.getTargetClass(),
                            selection.getSubcategory(),
                            selection.getBiomarkerKey(),
                            selection.getYear(),
                            List.of(location)),
                    selection);
            region = merged.isEmpty() ? null : merged.get(0);
        }
        List<MapSourceRecordResponse> sources = mapVisualizationMapper.findSourceRecords(
                normalizedLevel,
                geoKey,
                selection.getCategory(),
                selection.getTargetClass(),
                selection.getSubcategory(),
                selection.getBiomarkerKey(),
                selection.getYear(),
                DETAIL_SOURCE_LIMIT);
        List<MapRegionStatResponse> locations = region == null ? List.of() : List.of(region);
        return buildDetailResponse(region, locations, List.of(location), selection, sources, false);
    }

    @Override
    public MapDetailResponse getClusterDetail(MapClusterDetailRequest request) {
        MapFilterSelectionResponse selection = normalizeSelection(
                request == null ? null : request.getTargetClass(),
                request == null ? null : request.getCategory(),
                request == null ? null : request.getSubcategory(),
                request == null ? null : request.getBiomarkerKey(),
                request == null ? null : request.getYear());
        List<MapClusterLocationRequest> requestedLocations = normalizeClusterLocations(request);
        if (requestedLocations.isEmpty()) {
            return MapDetailResponse.builder()
                    .title("PNDL 聚合详情")
                    .subtitle("没有可用于查询的聚合位置")
                    .cluster(true)
                    .region(null)
                    .locations(List.of())
                    .summaryCards(List.of())
                    .topBiomarkers(List.of())
                    .pndlRanking(List.of())
                    .categoryBreakdown(List.of())
                    .sources(List.of())
                    .sourceRecords(List.of())
                    .build();
        }
        List<MapRegionStatResponse> regions = mapVisualizationMapper.findRegionsByKeys(
                selection.getCategory(),
                selection.getTargetClass(),
                selection.getSubcategory(),
                selection.getBiomarkerKey(),
                selection.getYear(),
                requestedLocations);
        if (regions.isEmpty() && ALL_CATEGORIES.equals(selection.getCategory())) {
            regions = mergeAnyCategoryStats(mapVisualizationMapper.findRegionsByKeysForAnyCategory(
                    selection.getTargetClass(),
                    selection.getSubcategory(),
                    selection.getBiomarkerKey(),
                    selection.getYear(),
                    requestedLocations), selection);
        }
        List<MapSourceRecordResponse> sources = collectSourceRecords(
                requestedLocations,
                selection,
                clampLimit(request == null ? null : request.getLimit(), DETAIL_SOURCE_LIMIT, FULL_DETAIL_LIMIT));
        return buildDetailResponse(null, regions, requestedLocations, selection, sources, true);
    }

    private MapDetailResponse buildDetailResponse(MapRegionStatResponse region,
                                                  List<MapRegionStatResponse> regions,
                                                  List<MapClusterLocationRequest> queryLocations,
                                                  MapFilterSelectionResponse selection,
                                                  List<MapSourceRecordResponse> sources,
                                                  boolean cluster) {
        List<MapRegionStatResponse> safeRegions = regions == null ? List.of() : regions;
        List<MapSourceRecordResponse> safeSources = sources == null ? List.of() : sources;
        List<MapClusterLocationRequest> safeQueryLocations = queryLocations == null ? List.of() : queryLocations;
        List<MapTopBiomarkerResponse> topBiomarkers = safeQueryLocations.isEmpty()
                ? List.of()
                : mapVisualizationMapper.findTopBiomarkersForLocations(
                        selection.getCategory(),
                        selection.getTargetClass(),
                        selection.getSubcategory(),
                        selection.getBiomarkerKey(),
                        selection.getYear(),
                        safeQueryLocations,
                        10);
        List<MapBreakdownItemResponse> categoryBreakdown = safeQueryLocations.isEmpty()
                ? List.of()
                : buildCategoryBreakdown(mapVisualizationMapper.findCategoryBreakdownForLocations(
                        selection.getCategory(),
                        selection.getTargetClass(),
                        selection.getYear(),
                        safeQueryLocations,
                        8));
        List<MapPndlRankingItemResponse> ranking = cluster
                ? buildClusterRanking(safeRegions)
                : buildRegionRanking(region, selection);
        return MapDetailResponse.builder()
                .title(cluster ? "PNDL 聚合详情" : (region == null ? "PNDL 详情" : region.getDisplayName()))
                .subtitle(cluster ? buildClusterSubtitle(safeRegions, selection) : buildRegionSubtitle(region, selection))
                .cluster(cluster)
                .region(region)
                .locations(safeRegions)
                .summaryCards(buildSummaryCards(region, safeRegions, cluster))
                .topBiomarkers(topBiomarkers)
                .pndlRanking(ranking)
                .categoryBreakdown(categoryBreakdown)
                .sources(safeSources)
                .sourceRecords(safeSources)
                .build();
    }

    private List<MapSourceRecordResponse> collectSourceRecords(List<MapClusterLocationRequest> locations,
                                                               MapFilterSelectionResponse selection,
                                                               int limit) {
        List<MapSourceRecordResponse> records = new ArrayList<>();
        for (MapClusterLocationRequest location : locations) {
            if (records.size() >= limit) {
                break;
            }
            records.addAll(mapVisualizationMapper.findSourceRecords(
                    location.getLevel(),
                    location.getGeoKey(),
                    selection.getCategory(),
                    selection.getTargetClass(),
                    selection.getSubcategory(),
                    selection.getBiomarkerKey(),
                    selection.getYear(),
                    limit - records.size()));
        }
        return records;
    }

    private List<MapSummaryCardResponse> buildSummaryCards(MapRegionStatResponse region,
                                                           List<MapRegionStatResponse> regions,
                                                           boolean cluster) {
        List<MapRegionStatResponse> rows = regions == null ? List.of() : regions;
        long recordCount = rows.stream().map(MapRegionStatResponse::getRecordCount).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        long doiCount = rows.stream().map(MapRegionStatResponse::getDoiCount).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        long pointCount = rows.stream().map(MapRegionStatResponse::getPointCount).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        long cityCount = rows.stream().map(MapRegionStatResponse::getCityCount).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        BigDecimal min = rows.stream().map(MapRegionStatResponse::getPndlMinMgD1000inh).filter(Objects::nonNull)
                .min(BigDecimal::compareTo).orElse(null);
        BigDecimal max = rows.stream().map(MapRegionStatResponse::getPndlMaxMgD1000inh).filter(Objects::nonNull)
                .max(BigDecimal::compareTo).orElse(null);
        List<MapSummaryCardResponse> cards = new ArrayList<>();
        if (cluster) {
            cards.add(summaryCard("位置数", String.valueOf(rows.size()), "聚合气泡包含的可映射位置"));
            cards.add(summaryCard("记录数", String.valueOf(recordCount), "当前筛选的 PNDL 记录"));
            cards.add(summaryCard("文献数", String.valueOf(doiCount), "去重 DOI 计数"));
            cards.add(summaryCard("PNDL 范围", rangeValue(min, max), "mg/day/1000 inh"));
            cards.add(summaryCard("点位数", String.valueOf(pointCount), "后端聚合点位"));
            cards.add(summaryCard("城市数", String.valueOf(cityCount), "涉及城市数量"));
            return cards;
        }
        cards.add(summaryCard("位置精度", locationPrecision(region), region == null ? "" : region.getGeoKey()));
        cards.add(summaryCard("PNDL 几何均值", decimalValue(region == null ? null : region.getPndlGeomeanMgD1000inh()), "mg/day/1000 inh"));
        cards.add(summaryCard("PNDL 范围", rangeValue(min, max), "mg/day/1000 inh"));
        cards.add(summaryCard("记录 / 文献", recordCount + " / " + doiCount, "当前筛选"));
        cards.add(summaryCard("年份数", String.valueOf(region == null || region.getYearCount() == null ? 0 : region.getYearCount()), "覆盖年份"));
        cards.add(summaryCard("点位数", String.valueOf(pointCount), "后端聚合点位"));
        return cards;
    }

    private List<MapPndlRankingItemResponse> buildRegionRanking(MapRegionStatResponse region,
                                                                MapFilterSelectionResponse selection) {
        if (region == null) {
            return List.of();
        }
        List<MapRegionStatResponse> rankingRows = mapVisualizationMapper.findRankingStats(
                region.getLevel(),
                selection.getCategory(),
                selection.getTargetClass(),
                selection.getSubcategory(),
                selection.getBiomarkerKey(),
                selection.getYear(),
                12);
        return toRankingItems(rankingRows, region.getLevel() + "|" + region.getGeoKey());
    }

    private List<MapPndlRankingItemResponse> buildClusterRanking(List<MapRegionStatResponse> regions) {
        List<MapRegionStatResponse> rankingRows = regions.stream()
                .sorted(Comparator
                        .comparing(MapRegionStatResponse::getPndlGeomeanMgD1000inh,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MapRegionStatResponse::getRecordCount,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MapRegionStatResponse::getDisplayName, Comparator.nullsLast(String::compareTo)))
                .limit(12)
                .toList();
        return toRankingItems(rankingRows, "");
    }

    private List<MapPndlRankingItemResponse> toRankingItems(List<MapRegionStatResponse> rows, String selectedRegionId) {
        List<MapPndlRankingItemResponse> items = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            MapRegionStatResponse row = rows.get(i);
            String regionId = row.getLevel() + "|" + row.getGeoKey();
            items.add(MapPndlRankingItemResponse.builder()
                    .rank(i + 1)
                    .level(row.getLevel())
                    .geoKey(row.getGeoKey())
                    .displayName(row.getDisplayName())
                    .pndlGeomeanMgD1000inh(row.getPndlGeomeanMgD1000inh())
                    .recordCount(row.getRecordCount())
                    .doiCount(row.getDoiCount())
                    .pointCount(row.getPointCount())
                    .yearCount(row.getYearCount())
                    .pndlSources(row.getPndlSources())
                    .selected(regionId.equals(selectedRegionId))
                    .build());
        }
        return items;
    }

    private List<MapBreakdownItemResponse> buildCategoryBreakdown(List<MapBreakdownRow> rows) {
        long total = rows.stream()
                .map(MapBreakdownRow::getRecordCount)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        if (total <= 0) {
            return List.of();
        }
        return rows.stream()
                .map(row -> MapBreakdownItemResponse.builder()
                        .label(row.getLabel())
                        .recordCount(row.getRecordCount())
                        .percentage(BigDecimal.valueOf(row.getRecordCount() == null ? 0 : row.getRecordCount())
                                .multiply(BigDecimal.valueOf(100))
                                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP))
                        .build())
                .toList();
    }

    private List<MapClusterLocationRequest> normalizeClusterLocations(MapClusterDetailRequest request) {
        if (request == null || request.getLocations() == null) {
            return List.of();
        }
        Map<String, MapClusterLocationRequest> locations = new LinkedHashMap<>();
        for (MapClusterLocationRequest location : request.getLocations()) {
            if (location == null || !StringUtils.hasText(location.getLevel()) || !StringUtils.hasText(location.getGeoKey())) {
                continue;
            }
            String level = normalizeLevel(location.getLevel().trim());
            String geoKey = location.getGeoKey().trim();
            locations.putIfAbsent(level + "|" + geoKey, clusterLocation(level, geoKey));
            if (locations.size() >= CLUSTER_LOCATION_LIMIT) {
                break;
            }
        }
        return new ArrayList<>(locations.values());
    }

    private MapClusterLocationRequest clusterLocation(String level, String geoKey) {
        MapClusterLocationRequest location = new MapClusterLocationRequest();
        location.setLevel(level);
        location.setGeoKey(geoKey);
        return location;
    }

    private MapSummaryCardResponse summaryCard(String label, String value, String note) {
        return MapSummaryCardResponse.builder()
                .label(label)
                .value(value)
                .note(note)
                .build();
    }

    private String buildClusterSubtitle(List<MapRegionStatResponse> regions, MapFilterSelectionResponse selection) {
        return regions.size() + " 个位置 · " + selection.getCategory() + " / " + selection.getSubcategory();
    }

    private String buildRegionSubtitle(MapRegionStatResponse region, MapFilterSelectionResponse selection) {
        return locationPrecision(region) + " · " + selection.getCategory() + " / " + selection.getSubcategory();
    }

    private String locationPrecision(MapRegionStatResponse region) {
        if (region == null || region.getLevel() == null) {
            return "位置未识别";
        }
        return switch (region.getLevel()) {
            case "city" -> "城市级位置";
            case "admin1" -> "省州级位置";
            default -> "国家级位置";
        };
    }

    private String rangeValue(BigDecimal min, BigDecimal max) {
        if (min == null || max == null) {
            return "无数据";
        }
        return decimalValue(min) + " - " + decimalValue(max);
    }

    private String decimalValue(BigDecimal value) {
        if (value == null) {
            return "无数据";
        }
        return value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private int clampLimit(Integer value, int fallback, int max) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return Math.min(value, max);
    }

    private List<MapRegionStatResponse> mergeAnyCategoryStats(List<MapRegionStatResponse> rows,
                                                              MapFilterSelectionResponse selection) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<String, List<MapRegionStatResponse>> grouped = rows.stream()
                .filter(row -> row.getLevel() != null && row.getGeoKey() != null)
                .collect(LinkedHashMap::new,
                        (map, row) -> map.computeIfAbsent(row.getLevel() + "|" + row.getGeoKey(), ignored -> new ArrayList<>()).add(row),
                        Map::putAll);
        return grouped.values().stream()
                .map(group -> mergeRegionGroup(group, selection))
                .sorted(Comparator
                        .comparing(MapRegionStatResponse::getLevel, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MapRegionStatResponse::getPndlGeomeanMgD1000inh,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MapRegionStatResponse::getDisplayName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private MapRegionStatResponse mergeRegionGroup(List<MapRegionStatResponse> group,
                                                   MapFilterSelectionResponse selection) {
        MapRegionStatResponse first = group.get(0);
        MapRegionStatResponse merged = new MapRegionStatResponse();
        merged.setLevel(first.getLevel());
        merged.setGeoKey(first.getGeoKey());
        merged.setParentGeoKey(first.getParentGeoKey());
        merged.setDisplayName(first.getDisplayName());
        merged.setCountry(first.getCountry());
        merged.setProvince(first.getProvince());
        merged.setCity(first.getCity());
        merged.setLatitude(first.getLatitude());
        merged.setLongitude(first.getLongitude());
        merged.setCategory(ALL_CATEGORIES);
        merged.setSubcategory(selection.getSubcategory());
        merged.setBiomarkerKey(selection.getBiomarkerKey());
        merged.setBiomarkerLabel(ALL_BIOMARKERS.equals(selection.getBiomarkerKey()) ? "全部 biomarker" : first.getBiomarkerLabel());
        merged.setBiomarkerCas(ALL_BIOMARKERS.equals(selection.getBiomarkerKey()) ? null : first.getBiomarkerCas());
        merged.setYearLabel(selection.getYear());
        merged.setPndlGeomeanMgD1000inh(weightedAverage(group, MapRegionStatResponse::getPndlGeomeanMgD1000inh));
        merged.setPndlMeanMgD1000inh(weightedAverage(group, MapRegionStatResponse::getPndlMeanMgD1000inh));
        merged.setPndlMinMgD1000inh(group.stream().map(MapRegionStatResponse::getPndlMinMgD1000inh).filter(Objects::nonNull)
                .min(BigDecimal::compareTo).orElse(null));
        merged.setPndlMaxMgD1000inh(group.stream().map(MapRegionStatResponse::getPndlMaxMgD1000inh).filter(Objects::nonNull)
                .max(BigDecimal::compareTo).orElse(null));
        merged.setRecordCount(sumLong(group, MapRegionStatResponse::getRecordCount));
        merged.setDoiCount(sumLong(group, MapRegionStatResponse::getDoiCount));
        merged.setYearCount(maxLong(group, MapRegionStatResponse::getYearCount));
        merged.setCityCount(sumLong(group, MapRegionStatResponse::getCityCount));
        merged.setPointCount(sumLong(group, MapRegionStatResponse::getPointCount));
        merged.setPndlSources("多类别合并显示");
        return merged;
    }

    private BigDecimal weightedAverage(List<MapRegionStatResponse> rows,
                                       java.util.function.Function<MapRegionStatResponse, BigDecimal> getter) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        long weightSum = 0;
        for (MapRegionStatResponse row : rows) {
            BigDecimal value = getter.apply(row);
            if (value == null) {
                continue;
            }
            long weight = row.getRecordCount() == null || row.getRecordCount() <= 0 ? 1L : row.getRecordCount();
            weightedSum = weightedSum.add(value.multiply(BigDecimal.valueOf(weight)));
            weightSum += weight;
        }
        return weightSum <= 0 ? null : weightedSum.divide(BigDecimal.valueOf(weightSum), 6, RoundingMode.HALF_UP);
    }

    private long sumLong(List<MapRegionStatResponse> rows,
                         java.util.function.Function<MapRegionStatResponse, Long> getter) {
        return rows.stream().map(getter).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
    }

    private long maxLong(List<MapRegionStatResponse> rows,
                         java.util.function.Function<MapRegionStatResponse, Long> getter) {
        return rows.stream().map(getter).filter(Objects::nonNull).mapToLong(Long::longValue).max().orElse(0L);
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

    private void moveAllCategoryToFront(List<String> categories) {
        if (categories.remove(ALL_CATEGORIES)) {
            categories.add(0, ALL_CATEGORIES);
        }
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

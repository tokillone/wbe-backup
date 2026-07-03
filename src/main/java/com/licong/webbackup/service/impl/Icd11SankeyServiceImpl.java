package com.licong.webbackup.service.impl;

import com.licong.webbackup.dto.sankey.Icd11SankeyCategoryResponse;
import com.licong.webbackup.dto.sankey.Icd11SankeyGraphResponse;
import com.licong.webbackup.dto.sankey.Icd11SankeyLinkResponse;
import com.licong.webbackup.dto.sankey.Icd11SankeyNodeResponse;
import com.licong.webbackup.dto.sankey.Icd11SankeyPathResponse;
import com.licong.webbackup.dto.sankey.Icd11SankeyPathRow;
import com.licong.webbackup.dto.sankey.Icd11SankeyStatsResponse;
import com.licong.webbackup.dto.sankey.Icd11SankeyTopItemResponse;
import com.licong.webbackup.mapper.Icd11SankeyMapper;
import com.licong.webbackup.service.Icd11SankeyService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class Icd11SankeyServiceImpl implements Icd11SankeyService {

    private static final int TOP_LIMIT = 8;
    private static final int SHARE_SCALE = 8;
    private static final List<String> LEVEL1_COLORS = List.of(
            "#5B8FD1",
            "#7FC8C2",
            "#D79A63",
            "#9A86C8",
            "#72A66A",
            "#C78086",
            "#6CA0A8",
            "#C9A45C",
            "#8F9CAA",
            "#B98D75",
            "#83A5D8",
            "#8BC4A0"
    );

    private final Icd11SankeyMapper icd11SankeyMapper;

    public Icd11SankeyServiceImpl(Icd11SankeyMapper icd11SankeyMapper) {
        this.icd11SankeyMapper = icd11SankeyMapper;
    }

    @Override
    public Icd11SankeyCategoryResponse getCategories() {
        List<String> categories = icd11SankeyMapper.findCategories();
        return Icd11SankeyCategoryResponse.builder()
                .categories(categories)
                .defaultCategory(categories.isEmpty() ? "" : categories.get(0))
                .build();
    }

    @Override
    public Icd11SankeyGraphResponse getGraph(String category) {
        String normalizedCategory = normalizeCategory(category);
        List<Icd11SankeyPathRow> rows = StringUtils.hasText(normalizedCategory)
                ? icd11SankeyMapper.findPathsByCategory(normalizedCategory)
                : List.of();
        return buildGraph(normalizedCategory, rows);
    }

    private Icd11SankeyGraphResponse buildGraph(String category, List<Icd11SankeyPathRow> rows) {
        LinkedHashMap<String, Icd11SankeyNodeResponse> nodes = new LinkedHashMap<>();
        LinkedHashMap<String, Icd11SankeyLinkResponse> links = new LinkedHashMap<>();
        LinkedHashMap<String, String> level1Colors = new LinkedHashMap<>();
        List<Icd11SankeyPathResponse> paths = new ArrayList<>();
        Map<String, BigDecimal> level1Weights = new LinkedHashMap<>();
        Map<String, BigDecimal> drugWeights = new LinkedHashMap<>();
        Map<String, BigDecimal> biomarkerWeights = new LinkedHashMap<>();

        List<AggregatedPath> aggregatedPaths = aggregatePaths(rows);
        BigDecimal totalWeight = aggregatedPaths.stream()
                .map(AggregatedPath::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (AggregatedPath path : aggregatedPaths) {
            BigDecimal weight = path.weight();
            String pathId = path.pathId();
            String level1 = path.level1();
            String level2 = path.level2();
            String drug = path.drug();
            String biomarker = path.biomarker();
            String color = level1Colors.computeIfAbsent(level1, ignored -> colorAt(level1Colors.size()));

            String level1Id = nodeId("level1", path.level1Code(), level1);
            String level2Id = nodeId("level2", path.level2Code(), level2);
            String drugId = nodeId("drug", null, drug);
            String biomarkerId = nodeId("biomarker", null, biomarker);
            List<String> nodeIds = List.of(level1Id, level2Id, drugId, biomarkerId);

            addNode(nodes, level1Id, level1, "level1", 0, weight, level1, color,
                    searchText(level1, path.level1Code(), "ICD11_Level1"));
            addNode(nodes, level2Id, level2, "level2", 1, weight, level1, color,
                    searchText(level2, path.level2Code(), "ICD11_Level2", path.diseaseEntitySearchText()));
            addNode(nodes, drugId, drug, "drug", 2, weight, level1, color,
                    searchText(drug, "药物", path.substanceCategorySearchText(), path.substanceSubclassSearchText()));
            addNode(nodes, biomarkerId, biomarker, "biomarker", 3, weight, level1, color,
                    searchText(biomarker, "生物标记物", path.biomarkerAliasSearchText(), path.biomarkerCasSearchText()));

            addLink(links, level1Id, level2Id, weight, level1, level1, level2,
                    "ICD11_Level1 → ICD11_Level2", pathId, color);
            addLink(links, level2Id, drugId, weight, level1, level2, drug,
                    "ICD11_Level2 → 药物", pathId, color);
            addLink(links, drugId, biomarkerId, weight, level1, drug, biomarker,
                    "药物 → 生物标记物", pathId, color);

            mergeWeight(level1Weights, level1, weight);
            mergeWeight(drugWeights, drug, weight);
            mergeWeight(biomarkerWeights, biomarker, weight);
            paths.add(Icd11SankeyPathResponse.builder()
                    .pathId(pathId)
                    .level1(level1)
                    .level2(level2)
                    .drug(drug)
                    .biomarker(biomarker)
                    .biomarkerAliases(path.biomarkerAliases())
                    .weight(weight)
                    .share(share(weight, totalWeight))
                    .nodeIds(nodeIds)
                    .build());
        }

        return Icd11SankeyGraphResponse.builder()
                .category(category)
                .nodes(new ArrayList<>(nodes.values()))
                .links(new ArrayList<>(links.values()))
                .paths(paths)
                .level1Colors(level1Colors)
                .stats(buildStats(nodes, paths.size(), totalWeight, level1Weights, drugWeights, biomarkerWeights))
                .build();
    }

    private List<AggregatedPath> aggregatePaths(List<Icd11SankeyPathRow> rows) {
        LinkedHashMap<String, AggregatedPath> aggregated = new LinkedHashMap<>();
        for (Icd11SankeyPathRow row : rows) {
            BigDecimal weight = positiveWeight(row.getLiteratureCount());
            String level1 = valueOr(row.getIcd11Level1Name(), "未标注 ICD11_Level1");
            String level2 = valueOr(row.getIcd11Level2Name(), "未标注 ICD11_Level2");
            String drug = valueOr(row.getDrugName(), "未标注药物");
            String biomarker = valueOr(row.getBiomarkerName(), drug);
            String key = String.join("||", level1, level2, drug, biomarker);
            AggregatedPath path = aggregated.computeIfAbsent(key, ignored -> new AggregatedPath(
                    "path::" + row.getSankeyPathId(),
                    row.getIcd11Level1Code(),
                    level1,
                    row.getIcd11Level2Code(),
                    level2,
                    drug,
                    biomarker
            ));
            path.add(row, weight);
        }
        return new ArrayList<>(aggregated.values());
    }

    private void addNode(LinkedHashMap<String, Icd11SankeyNodeResponse> nodes, String name, String displayName,
                         String kind, int depth, BigDecimal value, String level1, String color, String searchText) {
        Icd11SankeyNodeResponse node = nodes.get(name);
        if (node == null) {
            nodes.put(name, Icd11SankeyNodeResponse.builder()
                    .name(name)
                    .displayName(displayName)
                    .kind(kind)
                    .depth(depth)
                    .value(value)
                    .searchText(searchText)
                    .level1(level1)
                    .color(color)
                    .build());
            return;
        }
        node.setValue(node.getValue().add(value));
        if (StringUtils.hasText(searchText) && !node.getSearchText().contains(searchText)) {
            node.setSearchText(node.getSearchText() + " " + searchText);
        }
    }

    private void addLink(LinkedHashMap<String, Icd11SankeyLinkResponse> links, String source, String target,
                         BigDecimal value, String level1, String sourceLabel, String targetLabel,
                         String edgeType, String pathId, String color) {
        String linkId = source + "@@" + target + "@@" + edgeType + "@@" + level1;
        Icd11SankeyLinkResponse link = links.get(linkId);
        if (link == null) {
            link = Icd11SankeyLinkResponse.builder()
                    .linkId(linkId)
                    .source(source)
                    .target(target)
                    .value(BigDecimal.ZERO)
                    .level1(level1)
                    .sourceLabel(sourceLabel)
                    .targetLabel(targetLabel)
                    .edgeType(edgeType)
                    .pathIds(new ArrayList<>())
                    .color(color)
                    .build();
            links.put(linkId, link);
        }
        link.setValue(link.getValue().add(value));
        link.getPathIds().add(pathId);
    }

    private Icd11SankeyStatsResponse buildStats(LinkedHashMap<String, Icd11SankeyNodeResponse> nodes,
                                                int relations,
                                                BigDecimal totalWeight,
                                                Map<String, BigDecimal> level1Weights,
                                                Map<String, BigDecimal> drugWeights,
                                                Map<String, BigDecimal> biomarkerWeights) {
        Map<Integer, Long> depthCounts = nodes.values().stream()
                .collect(Collectors.groupingBy(Icd11SankeyNodeResponse::getDepth, Collectors.counting()));
        int maxNodes = depthCounts.values().stream().mapToInt(Long::intValue).max().orElse(0);
        return Icd11SankeyStatsResponse.builder()
                .totalWeight(totalWeight)
                .level1(depthCounts.getOrDefault(0, 0L).intValue())
                .level2(depthCounts.getOrDefault(1, 0L).intValue())
                .drug(depthCounts.getOrDefault(2, 0L).intValue())
                .biomarker(depthCounts.getOrDefault(3, 0L).intValue())
                .relations(relations)
                .maxNodes(maxNodes)
                .topLevel1(topItems(level1Weights, totalWeight))
                .topDrug(topItems(drugWeights, totalWeight))
                .topBiomarker(topItems(biomarkerWeights, totalWeight))
                .build();
    }

    private List<Icd11SankeyTopItemResponse> topItems(Map<String, BigDecimal> weights, BigDecimal totalWeight) {
        return weights.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<String, BigDecimal>, BigDecimal>comparing(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(TOP_LIMIT)
                .map(entry -> Icd11SankeyTopItemResponse.builder()
                        .name(entry.getKey())
                        .value(entry.getValue())
                        .share(share(entry.getValue(), totalWeight))
                        .build())
                .toList();
    }

    private String normalizeCategory(String category) {
        List<String> categories = icd11SankeyMapper.findCategories();
        if (categories.isEmpty()) {
            return "";
        }
        if (!StringUtils.hasText(category)) {
            return categories.get(0);
        }
        String trimmed = category.trim();
        return categories.contains(trimmed) ? trimmed : categories.get(0);
    }

    private String nodeId(String kind, String code, String displayName) {
        String key = StringUtils.hasText(code) ? code.trim() + "::" + displayName : displayName;
        return kind + "::" + key;
    }

    private String searchText(String... values) {
        Set<String> parts = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                parts.add(value.trim().toLowerCase());
            }
        }
        return String.join(" ", parts);
    }

    private List<String> aliasList(String alias) {
        if (!StringUtils.hasText(alias)) {
            return List.of();
        }
        return List.of(alias.trim());
    }

    private void mergeWeight(Map<String, BigDecimal> weights, String key, BigDecimal value) {
        weights.merge(key, value, BigDecimal::add);
    }

    private BigDecimal share(BigDecimal value, BigDecimal totalWeight) {
        if (totalWeight == null || totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return value.divide(totalWeight, SHARE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal positiveWeight(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return value;
    }

    private String colorAt(int index) {
        return LEVEL1_COLORS.get(index % LEVEL1_COLORS.size());
    }

    private String valueOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static class AggregatedPath {
        private final String pathId;
        private final String level1Code;
        private final String level1;
        private final String level2Code;
        private final String level2;
        private final String drug;
        private final String biomarker;
        private final LinkedHashSet<String> biomarkerAliases = new LinkedHashSet<>();
        private final LinkedHashSet<String> biomarkerAliasSearchText = new LinkedHashSet<>();
        private final LinkedHashSet<String> biomarkerCasSearchText = new LinkedHashSet<>();
        private final LinkedHashSet<String> diseaseEntitySearchText = new LinkedHashSet<>();
        private final LinkedHashSet<String> substanceCategorySearchText = new LinkedHashSet<>();
        private final LinkedHashSet<String> substanceSubclassSearchText = new LinkedHashSet<>();
        private BigDecimal weight = BigDecimal.ZERO;

        private AggregatedPath(String pathId, String level1Code, String level1, String level2Code, String level2,
                               String drug, String biomarker) {
            this.pathId = pathId;
            this.level1Code = level1Code;
            this.level1 = level1;
            this.level2Code = level2Code;
            this.level2 = level2;
            this.drug = drug;
            this.biomarker = biomarker;
        }

        private void add(Icd11SankeyPathRow row, BigDecimal rowWeight) {
            weight = weight.add(rowWeight);
            addValue(biomarkerAliases, row.getBiomarkerAlias());
            addValue(biomarkerAliasSearchText, row.getBiomarkerAlias());
            addValue(biomarkerCasSearchText, row.getBiomarkerCas());
            addValue(diseaseEntitySearchText, row.getDiseaseEntity());
            addValue(substanceCategorySearchText, row.getSubstanceCategory());
            addValue(substanceSubclassSearchText, row.getSubstanceSubclass());
        }

        private String pathId() {
            return pathId;
        }

        private String level1Code() {
            return level1Code;
        }

        private String level1() {
            return level1;
        }

        private String level2Code() {
            return level2Code;
        }

        private String level2() {
            return level2;
        }

        private String drug() {
            return drug;
        }

        private String biomarker() {
            return biomarker;
        }

        private BigDecimal weight() {
            return weight;
        }

        private List<String> biomarkerAliases() {
            return new ArrayList<>(biomarkerAliases);
        }

        private String biomarkerAliasSearchText() {
            return String.join(" ", biomarkerAliasSearchText);
        }

        private String biomarkerCasSearchText() {
            return String.join(" ", biomarkerCasSearchText);
        }

        private String diseaseEntitySearchText() {
            return String.join(" ", diseaseEntitySearchText);
        }

        private String substanceCategorySearchText() {
            return String.join(" ", substanceCategorySearchText);
        }

        private String substanceSubclassSearchText() {
            return String.join(" ", substanceSubclassSearchText);
        }

        private void addValue(LinkedHashSet<String> values, String value) {
            if (StringUtils.hasText(value)) {
                values.add(value.trim());
            }
        }
    }
}

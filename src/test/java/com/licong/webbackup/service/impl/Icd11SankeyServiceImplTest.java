package com.licong.webbackup.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.licong.webbackup.dto.sankey.Icd11SankeyGraphResponse;
import com.licong.webbackup.dto.sankey.Icd11SankeyPathRow;
import com.licong.webbackup.mapper.Icd11SankeyMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Icd11SankeyServiceImplTest {

    @Test
    void buildsCategoryGraphWithoutLiteratureDetails() throws Exception {
        Icd11SankeyMapper mapper = mock(Icd11SankeyMapper.class);
        when(mapper.findCategories()).thenReturn(List.of("A 消化道和代谢系统药物", "N 神经系统药物"));
        when(mapper.findPathsByCategory("A 消化道和代谢系统药物")).thenReturn(List.of(
                pathRow(1L, "A 消化道和代谢系统药物", "消化系统疾病", "胃或十二指肠溃疡", "西咪替丁", "西咪替丁", "Cimetidine", "2"),
                pathRow(2L, "A 消化道和代谢系统药物", "消化系统疾病", "胃或十二指肠溃疡", "雷尼替丁", "雷尼替丁", "Ranitidine", "3")
        ));

        Icd11SankeyServiceImpl service = new Icd11SankeyServiceImpl(mapper);
        Icd11SankeyGraphResponse graph = service.getGraph("A 消化道和代谢系统药物");

        assertThat(service.getCategories().getCategories()).hasSize(2);
        assertThat(service.getCategories().getDefaultCategory()).isEqualTo("ALL");
        assertThat(graph.getPaths()).hasSize(2);
        assertThat(graph.getLinks()).hasSize(6);
        assertThat(graph.getStats().getTotalWeight()).isEqualByComparingTo("5");
        assertThat(graph.getStats().getLevel3()).isEqualTo(1);
        assertThat(graph.getStats().getMappingRows()).isEqualTo(2);
        assertThat(graph.getStats().getRelations()).isEqualTo(2);
        assertThat(graph.getStats().getTopLevel3()).extracting("name").containsExactly("胃或十二指肠溃疡细分");
        assertThat(graph.getStats().getTopDrug()).extracting("name").containsExactly("雷尼替丁", "西咪替丁");
        assertThat(graph.getPaths().get(0).getLevel3()).isEqualTo("胃或十二指肠溃疡细分");
        assertThat(graph.getPaths().get(0).getShare()).isEqualByComparingTo("0.4");

        String json = new ObjectMapper().writeValueAsString(graph);
        assertThat(json).doesNotContain("doi", "literature", "sourceRow", "originalRow");
    }

    @Test
    void defaultsToAllTargetCategoriesWhenCategoryFilterIsAbsent() {
        Icd11SankeyMapper mapper = mock(Icd11SankeyMapper.class);
        when(mapper.findCategories()).thenReturn(List.of("A 消化道和代谢系统药物", "N 神经系统药物"));
        when(mapper.findAllPaths()).thenReturn(List.of(
                pathRow(1L, "A 消化道和代谢系统药物", "消化系统疾病", "胃或十二指肠溃疡",
                        "西咪替丁", "西咪替丁", "Cimetidine", "2"),
                pathRow(2L, "N 神经系统药物", "神经系统疾病", "疼痛疾患",
                        "加巴喷丁", "加巴喷丁", "Gabapentin", "3")
        ));

        Icd11SankeyGraphResponse graph = new Icd11SankeyServiceImpl(mapper).getGraph(null);

        assertThat(graph.getCategory()).isEqualTo("全部目标类别");
        assertThat(graph.getStats().getMappingRows()).isEqualTo(2);
        assertThat(graph.getStats().getRelations()).isEqualTo(2);
        assertThat(graph.getPaths()).extracting("drug").containsExactly("西咪替丁", "加巴喷丁");
    }

    @Test
    void fallsBackToDefaultCategoryForUnknownCategory() {
        Icd11SankeyMapper mapper = mock(Icd11SankeyMapper.class);
        when(mapper.findCategories()).thenReturn(List.of("A 消化道和代谢系统药物"));
        when(mapper.findPathsByCategory("A 消化道和代谢系统药物")).thenReturn(List.of(
                pathRow(1L, "A 消化道和代谢系统药物", "消化系统疾病", "胃或十二指肠溃疡", "西咪替丁", "西咪替丁", "Cimetidine", "2")
        ));

        Icd11SankeyServiceImpl service = new Icd11SankeyServiceImpl(mapper);

        assertThat(service.getGraph("不存在的类别").getCategory()).isEqualTo("A 消化道和代谢系统药物");
    }

    @Test
    void aggregatesVisibleDuplicatePathsAndMergesSameNamedBiomarkers() {
        Icd11SankeyMapper mapper = mock(Icd11SankeyMapper.class);
        when(mapper.findCategories()).thenReturn(List.of("A 消化道和代谢系统药物"));
        when(mapper.findPathsByCategory("A 消化道和代谢系统药物")).thenReturn(List.of(
                pathRow(11L, "A 消化道和代谢系统药物", "内分泌、营养或代谢疾病", "内分泌疾病",
                        "二甲双胍", "二甲双胍", "Metformin", "10", "1115-70-4; 657-24-9"),
                pathRow(10L, "A 消化道和代谢系统药物", "内分泌、营养或代谢疾病", "内分泌疾病",
                        "二甲双胍", "二甲双胍", "Metformin", "3", "657-24-9")
        ));

        Icd11SankeyServiceImpl service = new Icd11SankeyServiceImpl(mapper);
        Icd11SankeyGraphResponse graph = service.getGraph("A 消化道和代谢系统药物");

        assertThat(graph.getPaths()).hasSize(1);
        assertThat(graph.getPaths().get(0).getWeight()).isEqualByComparingTo("13");
        assertThat(graph.getPaths().get(0).getMappingRows()).isEqualTo(2);
        assertThat(graph.getPaths().get(0).getShare()).isEqualByComparingTo("1");
        assertThat(graph.getStats().getTotalWeight()).isEqualByComparingTo("13");
        assertThat(graph.getStats().getMappingRows()).isEqualTo(2);
        assertThat(graph.getStats().getRelations()).isEqualTo(1);
        assertThat(graph.getNodes())
                .filteredOn(node -> "biomarker".equals(node.getKind()) && "二甲双胍".equals(node.getDisplayName()))
                .singleElement()
                .satisfies(node -> {
                    assertThat(node.getName()).isEqualTo("biomarker::二甲双胍");
                    assertThat(node.getValue()).isEqualByComparingTo("13");
                    assertThat(node.getSearchText()).contains("1115-70-4", "657-24-9");
                });
    }

    @Test
    void buildsLevel2TerminalPathWithoutSyntheticLevel3() {
        Icd11SankeyMapper mapper = mock(Icd11SankeyMapper.class);
        when(mapper.findCategories()).thenReturn(List.of("C 心血管系统药物"));
        Icd11SankeyPathRow level2Row = pathRow(
                21L,
                "C 心血管系统药物",
                "循环系统疾病",
                "心律失常",
                "Flecainide",
                "氟卡尼",
                "Flecainide",
                "4"
        );
        level2Row.setIcd11Level3Code(null);
        level2Row.setIcd11Level3Name(null);
        level2Row.setMappingLevel("Level2");
        when(mapper.findPathsByCategory("C 心血管系统药物")).thenReturn(List.of(level2Row));

        Icd11SankeyGraphResponse graph = new Icd11SankeyServiceImpl(mapper)
                .getGraph("C 心血管系统药物");

        assertThat(graph.getPaths()).singleElement().satisfies(path -> {
            assertThat(path.getLevel3()).isNull();
            assertThat(path.getMappingLevel()).isEqualTo("Level2");
            assertThat(path.getNodeIds()).hasSize(4);
        });
        assertThat(graph.getNodes()).noneMatch(node -> "level3".equals(node.getKind()));
        assertThat(graph.getLinks()).extracting("edgeType")
                .containsExactlyInAnyOrder(
                        "ICD11_Level1 → ICD11_Level2",
                        "ICD11_Level2 → 药物",
                        "药物 → 生物标记物"
                );
        assertThat(graph.getStats().getLevel3()).isZero();
        assertThat(graph.getStats().getLevel2OnlyPaths()).isEqualTo(1);
        assertThat(graph.getStats().getLevel3Paths()).isZero();
        assertThat(graph.getStats().getLevel2OnlyWeight()).isEqualByComparingTo("4");
        assertThat(graph.getStats().getLevel3Weight()).isEqualByComparingTo("0");
        assertThat(graph.getStats().getTopLevel3()).isEmpty();
    }

    @Test
    void cachesReadModelsAndReloadsThemAfterSynchronizationInvalidation() {
        Icd11SankeyMapper mapper = mock(Icd11SankeyMapper.class);
        String category = "A 消化道和代谢系统药物";
        when(mapper.findCategories()).thenReturn(List.of(category));
        when(mapper.findPathsByCategory(category)).thenReturn(List.of(
                pathRow(31L, category, "消化系统疾病", "胃或十二指肠溃疡",
                        "西咪替丁", "西咪替丁", "Cimetidine", "2")
        ));
        Icd11SankeyServiceImpl service = new Icd11SankeyServiceImpl(mapper);

        Icd11SankeyGraphResponse first = service.getGraph(category);
        Icd11SankeyGraphResponse second = service.getGraph(category);

        assertThat(second).isSameAs(first);
        verify(mapper).findCategories();
        verify(mapper).findPathsByCategory(category);
        long previousRevision = service.cacheRevision();

        service.invalidateCache();
        Icd11SankeyGraphResponse reloaded = service.getGraph(category);

        assertThat(reloaded).isNotSameAs(first);
        assertThat(service.cacheRevision()).isGreaterThan(previousRevision);
        verify(mapper, times(2)).findCategories();
        verify(mapper, times(2)).findPathsByCategory(category);
    }

    private Icd11SankeyPathRow pathRow(Long id, String category, String level1, String level2,
                                       String drug, String biomarker, String alias, String weight) {
        return pathRow(id, category, level1, level2, drug, biomarker, alias, weight, null);
    }

    private Icd11SankeyPathRow pathRow(Long id, String category, String level1, String level2,
                                       String drug, String biomarker, String alias, String weight, String cas) {
        Icd11SankeyPathRow row = new Icd11SankeyPathRow();
        row.setSankeyPathId(id);
        row.setTargetCategory(category);
        row.setSubstanceCategory("A02 胃酸相关疾病用药");
        row.setSubstanceSubclass("A02BA H2受体拮抗剂");
        row.setDrugName(drug);
        row.setBiomarkerName(biomarker);
        row.setBiomarkerAlias(alias);
        row.setBiomarkerCas(cas);
        row.setIcd11Level1Code("13");
        row.setIcd11Level1Name(level1);
        row.setIcd11Level2Code("BlockL2-DA6");
        row.setIcd11Level2Name(level2);
        row.setIcd11Level3Code("Level3-DA60");
        row.setIcd11Level3Name(level2 + "细分");
        row.setMappingLevel("Level3");
        row.setLiteratureCount(new BigDecimal(weight));
        row.setDataRowCount(1L);
        return row;
    }
}

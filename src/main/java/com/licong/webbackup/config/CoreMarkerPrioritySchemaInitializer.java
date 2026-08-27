package com.licong.webbackup.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class CoreMarkerPrioritySchemaInitializer {

    private static final String DATASET_KEY = "default";
    private static final Set<String> MANAGED_LEGACY_VERSIONS = Set.of(
            "CORE-SHEET-POINT-LINK",
            "MIDRANK-WITHIN-GROUP-JENKS-V4"
    );
    private static final Set<String> MANAGED_LEGACY_HASHES = Set.of(
            "EA2EAF739C46"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public CoreMarkerPrioritySchemaInitializer(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @PostConstruct
    public void initialize() {
        ensureTables();
        transactionTemplate.executeWithoutResult(status -> synchronizeManagedDataset());
    }

    private void ensureTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS core_marker_priority_datasets (
                    dataset_key VARCHAR(64) PRIMARY KEY,
                    summary_json LONGTEXT NOT NULL,
                    source_hash VARCHAR(128),
                    source_modified_at VARCHAR(64),
                    managed_seed BOOLEAN NOT NULL DEFAULT FALSE,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='核心标记物优先级数据集元数据'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS core_marker_priority_records (
                    marker_id VARCHAR(64) PRIMARY KEY,
                    category VARCHAR(255),
                    target_type VARCHAR(255),
                    target_subtype VARCHAR(255),
                    target_fine VARCHAR(255),
                    biomarker VARCHAR(500),
                    tier VARCHAR(64),
                    total_score DECIMAL(10,2),
                    overall_rank INT,
                    overview_json LONGTEXT NOT NULL,
                    detail_json LONGTEXT NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_core_marker_category (category),
                    INDEX idx_core_marker_target (target_type, target_subtype),
                    INDEX idx_core_marker_tier_score (tier, total_score),
                    INDEX idx_core_marker_rank (overall_rank)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='核心标记物优先级派生结果'
                """);
        ensureColumn(
                "core_marker_priority_records",
                "target_fine",
                "VARCHAR(255) NULL COMMENT '目标物质细类'"
        );
        ensureColumn(
                "core_marker_priority_datasets",
                "managed_seed",
                "BOOLEAN NOT NULL DEFAULT FALSE COMMENT '应用托管数据集，允许随内置评分数据安全升级'"
        );
    }

    private void synchronizeManagedDataset() {
        SeedPayload seed = readSeedPayload();
        long recordCount = countRows("core_marker_priority_records");
        Map<String, Object> current = findDatasetForUpdate();
        if (current == null) {
            if (recordCount > 0) {
                log.warn(
                        "核心标记物初始化已跳过：存在 {} 条无数据集元数据的记录，为避免覆盖请人工核查",
                        recordCount
                );
                return;
            }
            writeManagedSeed(seed);
            return;
        }

        String currentHash = normalize(asString(current.get("source_hash")));
        String currentSummaryJson = asString(current.get("summary_json"));
        String currentVersion = scoreVersion(currentSummaryJson);
        boolean managedSeed = asBoolean(current.get("managed_seed"));
        boolean matchesBundledSeed = normalize(seed.sourceHash()).equals(currentHash);
        boolean recognizedManagedSeed = managedSeed
                || matchesBundledSeed
                || MANAGED_LEGACY_VERSIONS.contains(currentVersion)
                || MANAGED_LEGACY_HASHES.contains(currentHash);

        if (matchesBundledSeed && recordCount == seed.rowCount()) {
            if (!managedSeed) {
                jdbcTemplate.update("""
                        UPDATE core_marker_priority_datasets
                        SET managed_seed = TRUE
                        WHERE dataset_key = ?
                        """, DATASET_KEY);
                log.info("核心标记物数据集已标记为应用托管版本，后续可安全执行版本升级");
            }
            return;
        }

        if (!recognizedManagedSeed) {
            log.warn(
                    "核心标记物初始化已保留人工数据：records={}, version={}, sourceHash={}",
                    recordCount,
                    currentVersion,
                    currentHash
            );
            return;
        }

        jdbcTemplate.update("DELETE FROM core_marker_priority_records");
        jdbcTemplate.update(
                "DELETE FROM core_marker_priority_datasets WHERE dataset_key = ?",
                DATASET_KEY
        );
        writeManagedSeed(seed);
        log.info(
                "核心标记物数据库已从托管旧版本 {} / {} 升级为 {} / {}，共 {} 条",
                currentVersion,
                currentHash,
                seed.scoreVersion(),
                seed.sourceHash(),
                seed.rowCount()
        );
    }

    private void writeManagedSeed(SeedPayload seed) {
        int datasetInserted = jdbcTemplate.update("""
                INSERT IGNORE INTO core_marker_priority_datasets (
                    dataset_key, summary_json, source_hash, source_modified_at, managed_seed
                ) VALUES (?, ?, ?, ?, TRUE)
                """,
                DATASET_KEY,
                seed.summary().toString(),
                seed.sourceHash(),
                textOrNull(seed.summary(), "sourceModifiedAt")
        );
        if (datasetInserted == 0) {
            log.info("核心标记物初始化已由其他实例完成，本实例不再写入初始数据");
            return;
        }
        List<Object[]> batch = new ArrayList<>();
        for (JsonNode row : seed.rows()) {
            String markerId = row.path("id").asText();
            JsonNode detail = seed.details().path(markerId);
            JsonNode categoryScore = row.path("scoreByScope").path("category");
            batch.add(new Object[]{
                    markerId,
                    textOrNull(row, "category"),
                    textOrNull(row, "targetType"),
                    textOrNull(row, "targetSubtype"),
                    textOrNull(row, "targetFine"),
                    textOrNull(row, "biomarker"),
                    textOrNull(categoryScore, "tierLabel"),
                    decimalOrNull(categoryScore, "totalScore"),
                    integerOrNull(categoryScore, "rank"),
                    row.toString(),
                    detail.isMissingNode() ? "{}" : detail.toString()
            });
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO core_marker_priority_records (
                    marker_id, category, target_type, target_subtype, target_fine, biomarker, tier,
                    total_score, overall_rank, overview_json, detail_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, batch);
        log.info("核心标记物托管数据已写入数据库，共 {} 条", batch.size());
    }

    private SeedPayload readSeedPayload() {
        JsonNode overview = readResource("data/core-marker-priority-overview.json");
        JsonNode details = readResource("data/core-marker-priority-details.json");
        JsonNode summary = overview.path("summary");
        JsonNode rows = overview.path("rows");
        String sourceHash = textOrNull(summary, "sourceHash");
        String scoreVersion = textOrNull(summary, "scoreVersion");
        if (!rows.isArray() || rows.isEmpty() || sourceHash == null || scoreVersion == null) {
            throw new IllegalStateException("核心标记物托管数据缺少 rows、sourceHash 或 scoreVersion");
        }
        return new SeedPayload(summary, rows, details, sourceHash, scoreVersion, rows.size());
    }

    private Map<String, Object> findDatasetForUpdate() {
        try {
            return jdbcTemplate.queryForMap("""
                    SELECT summary_json, source_hash, managed_seed
                    FROM core_marker_priority_datasets
                    WHERE dataset_key = ?
                    FOR UPDATE
                    """, DATASET_KEY);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private String scoreVersion(String summaryJson) {
        if (summaryJson == null || summaryJson.isBlank()) {
            return "";
        }
        try {
            return objectMapper.readTree(summaryJson).path("scoreVersion").asText("");
        } catch (IOException exception) {
            return "";
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(asString(value));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private long countRows(String tableName) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return count == null ? 0 : count;
    }

    private void ensureColumn(String tableName, String columnName, String columnDefinition) {
        Integer columnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, Integer.class, tableName, columnName);
        if (columnCount != null && columnCount > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
    }

    private JsonNode readResource(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try {
            String json = resource.getContentAsString(StandardCharsets.UTF_8);
            return objectMapper.readTree(json);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取核心标记物初始数据：" + path, exception);
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isNumber() ? null : value.decimalValue();
    }

    private Integer integerOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isIntegralNumber() ? null : value.intValue();
    }

    private record SeedPayload(
            JsonNode summary,
            JsonNode rows,
            JsonNode details,
            String sourceHash,
            String scoreVersion,
            int rowCount
    ) {
    }
}

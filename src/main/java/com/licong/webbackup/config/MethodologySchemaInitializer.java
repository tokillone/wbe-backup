package com.licong.webbackup.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class MethodologySchemaInitializer {

    private static final String DATA_RESOURCE = "methodology/methodology-data.json";
    private static final String INITIALIZATION_LOCK = "wbe_methodology_seed";
    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public MethodologySchemaInitializer(JdbcTemplate jdbcTemplate,
                                        ObjectMapper objectMapper,
                                        PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @PostConstruct
    public void initialize() throws IOException {
        createSchema();
        if (findExistingChecksum() != null) {
            return;
        }

        Boolean lockAcquired = jdbcTemplate.queryForObject(
                "SELECT GET_LOCK(?, 30)", Boolean.class, INITIALIZATION_LOCK);
        if (!Boolean.TRUE.equals(lockAcquired)) {
            throw new IllegalStateException("无法取得方法学数据初始化锁");
        }
        try {
            if (findExistingChecksum() != null) {
                return;
            }
            assertDatasetTablesEmpty();

            byte[] source;
            try (InputStream inputStream = new ClassPathResource(DATA_RESOURCE).getInputStream()) {
                source = inputStream.readAllBytes();
            }
            JsonNode root = objectMapper.readTree(source);
            validateSeed(root);
            String checksum = DigestUtils.md5DigestAsHex(source);
            transactionTemplate.executeWithoutResult(status -> seedDataset(root, checksum));
        } finally {
            jdbcTemplate.queryForObject(
                    "SELECT RELEASE_LOCK(?)", Boolean.class, INITIALIZATION_LOCK);
        }
    }

    private String findExistingChecksum() {
        return jdbcTemplate.query(
                "SELECT source_checksum FROM methodology_dataset_meta WHERE dataset_id = 1",
                resultSet -> resultSet.next() ? resultSet.getString(1) : null);
    }

    private void assertDatasetTablesEmpty() {
        Long existingRows = jdbcTemplate.queryForObject("""
                SELECT
                    (SELECT COUNT(*) FROM methodology_records)
                  + (SELECT COUNT(*) FROM methodology_sampling_methods)
                  + (SELECT COUNT(*) FROM methodology_options)
                """, Long.class);
        if (existingRows != null && existingRows > 0) {
            throw new IllegalStateException(
                    "检测到未登记的方法学生产数据，自动初始化已停止以避免覆盖");
        }
    }

    private void validateSeed(JsonNode root) {
        JsonNode records = root.path("records");
        JsonNode methods = root.path("samplingMethods");
        JsonNode options = root.path("options");
        int declaredRows = root.path("meta").path("rowCount").asInt(-1);
        if (!records.isArray() || records.isEmpty() || declaredRows != records.size()
                || !methods.isArray() || methods.isEmpty()
                || !options.isObject() || options.isEmpty()) {
            throw new IllegalStateException("方法学种子数据结构或行数校验失败");
        }
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS methodology_dataset_meta (
                    dataset_id TINYINT PRIMARY KEY,
                    source_checksum CHAR(32) NOT NULL,
                    source_name VARCHAR(255) NOT NULL,
                    meta_json LONGTEXT NOT NULL,
                    row_count INT NOT NULL,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方法学核验数据集元信息'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS methodology_records (
                    record_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    doc_code VARCHAR(80) NOT NULL,
                    doi VARCHAR(300) NULL,
                    target_class VARCHAR(180) NOT NULL,
                    category VARCHAR(220) NOT NULL,
                    subcategory VARCHAR(220) NOT NULL,
                    drug VARCHAR(500) NOT NULL,
                    marker VARCHAR(500) NOT NULL,
                    prescription VARCHAR(80) NOT NULL,
                    sampling_raw TEXT NOT NULL,
                    sampling_standard VARCHAR(300) NOT NULL,
                    sampling_detail TEXT NOT NULL,
                    sampling_class VARCHAR(220) NOT NULL,
                    sample_object VARCHAR(220) NOT NULL,
                    proportion VARCHAR(220) NOT NULL,
                    duration VARCHAR(120) NOT NULL,
                    passive_sampler VARCHAR(180) NOT NULL,
                    station_status VARCHAR(180) NOT NULL,
                    analysis_raw TEXT NOT NULL,
                    analysis_group VARCHAR(180) NOT NULL,
                    country VARCHAR(120) NOT NULL,
                    INDEX idx_methodology_doc (doc_code),
                    INDEX idx_methodology_target (target_class),
                    INDEX idx_methodology_country (country),
                    INDEX idx_methodology_sampling (sampling_standard),
                    INDEX idx_methodology_analysis (analysis_group)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方法学核验逐行事实表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS methodology_sampling_methods (
                    standard VARCHAR(300) PRIMARY KEY,
                    display_order INT NOT NULL,
                    sampling_class_json TEXT NOT NULL,
                    sample_object_json TEXT NOT NULL,
                    proportion_json TEXT NOT NULL,
                    duration_json TEXT NOT NULL,
                    passive_sampler_json TEXT NOT NULL,
                    station_status_json TEXT NOT NULL,
                    audit_source_groups INT NOT NULL,
                    impact_rows INT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方法学标准采样方法审计维度'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS methodology_options (
                    option_type VARCHAR(80) NOT NULL,
                    display_order INT NOT NULL,
                    option_value VARCHAR(500) NOT NULL,
                    PRIMARY KEY (option_type, display_order),
                    INDEX idx_methodology_option_value (option_type, option_value(180))
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方法学筛选项及展示顺序'
                """);
    }

    private void seedDataset(JsonNode root, String checksum) {
        insertRecords(root.path("records"));
        insertSamplingMethods(root.path("samplingMethods"));
        insertOptions(root.path("options"));

        JsonNode meta = root.path("meta");
        jdbcTemplate.update("""
                        INSERT INTO methodology_dataset_meta (
                            dataset_id, source_checksum, source_name, meta_json, row_count
                        ) VALUES (1, ?, ?, ?, ?)
                        """,
                checksum,
                text(meta, "sourceName"),
                meta.toString(),
                meta.path("rowCount").asInt());
    }

    private void insertRecords(JsonNode records) {
        List<JsonNode> batch = new ArrayList<>(BATCH_SIZE);
        for (JsonNode record : records) {
            batch.add(record);
            if (batch.size() == BATCH_SIZE) {
                insertRecordBatch(batch);
                batch = new ArrayList<>(BATCH_SIZE);
            }
        }
        if (!batch.isEmpty()) {
            insertRecordBatch(batch);
        }
    }

    private void insertRecordBatch(List<JsonNode> records) {
        jdbcTemplate.batchUpdate("""
                INSERT INTO methodology_records (
                    doc_code, doi, target_class, category, subcategory, drug, marker, prescription,
                    sampling_raw, sampling_standard, sampling_detail, sampling_class, sample_object,
                    proportion, duration, passive_sampler, station_status, analysis_raw, analysis_group, country
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                JsonNode record = records.get(index);
                String[] fields = {
                        "doc", "doi", "targetClass", "category", "subcategory", "drug", "marker", "prescription",
                        "samplingRaw", "samplingStandard", "samplingDetail", "samplingClass", "sampleObject",
                        "proportion", "duration", "passiveSampler", "stationStatus", "analysisRaw", "analysisGroup", "country"
                };
                for (int fieldIndex = 0; fieldIndex < fields.length; fieldIndex++) {
                    statement.setString(fieldIndex + 1, text(record, fields[fieldIndex]));
                }
            }

            @Override
            public int getBatchSize() {
                return records.size();
            }
        });
    }

    private void insertSamplingMethods(JsonNode methods) {
        int order = 0;
        for (JsonNode method : methods) {
            jdbcTemplate.update("""
                            INSERT INTO methodology_sampling_methods (
                                standard, display_order, sampling_class_json, sample_object_json,
                                proportion_json, duration_json, passive_sampler_json, station_status_json,
                                audit_source_groups, impact_rows
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    text(method, "standard"),
                    order++,
                    method.path("samplingClass").toString(),
                    method.path("sampleObject").toString(),
                    method.path("proportion").toString(),
                    method.path("duration").toString(),
                    method.path("passiveSampler").toString(),
                    method.path("stationStatus").toString(),
                    method.path("auditSourceGroups").asInt(),
                    method.path("impactRows").asInt());
        }
    }

    private void insertOptions(JsonNode options) {
        Iterator<Map.Entry<String, JsonNode>> fields = options.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            int order = 0;
            for (JsonNode value : field.getValue()) {
                jdbcTemplate.update("""
                                INSERT INTO methodology_options (option_type, display_order, option_value)
                                VALUES (?, ?, ?)
                                """,
                        field.getKey(), order++, value.asText());
            }
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }
}

package com.licong.webbackup.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreMarkerPrioritySchemaInitializerTest {

    @Test
    void currentManagedDatasetIsNotRewritten() {
        JdbcTemplate jdbcTemplate = configuredJdbcTemplate(
                516L,
                Map.of(
                        "summary_json", "{\"scoreVersion\":\"MIDRANK-WITHIN-GROUP-JENKS-V5\"}",
                        "source_hash", "5F49DCBF2669",
                        "managed_seed", true
                )
        );

        initializer(jdbcTemplate).initialize();

        verify(jdbcTemplate, never()).update(eq("DELETE FROM core_marker_priority_records"));
        verify(jdbcTemplate, never()).batchUpdate(
                contains("INSERT INTO core_marker_priority_records"),
                anyList()
        );
    }

    @Test
    void matchingBundledDatasetIsMarkedAsManagedWithoutRewritingRows() {
        JdbcTemplate jdbcTemplate = configuredJdbcTemplate(
                516L,
                Map.of(
                        "summary_json", "{\"scoreVersion\":\"MIDRANK-WITHIN-GROUP-JENKS-V5\"}",
                        "source_hash", "5F49DCBF2669",
                        "managed_seed", false
                )
        );

        initializer(jdbcTemplate).initialize();

        verify(jdbcTemplate).update(
                contains("SET managed_seed = TRUE"),
                eq("default")
        );
        verify(jdbcTemplate, never()).update(eq("DELETE FROM core_marker_priority_records"));
    }

    @Test
    void legacyDatabaseSnapshotIsAtomicallyUpgradedToBundledDataset() {
        JdbcTemplate jdbcTemplate = configuredJdbcTemplate(
                516L,
                Map.of(
                        "summary_json", "{\"scoreVersion\":\"CORE-SHEET-POINT-LINK\"}",
                        "source_hash", "EA2EAF739C46",
                        "managed_seed", false
                )
        );
        when(jdbcTemplate.update(
                contains("INSERT IGNORE INTO core_marker_priority_datasets"),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(1);

        initializer(jdbcTemplate).initialize();

        verify(jdbcTemplate).update("DELETE FROM core_marker_priority_records");
        verify(jdbcTemplate).update(
                "DELETE FROM core_marker_priority_datasets WHERE dataset_key = ?",
                "default"
        );
        verify(jdbcTemplate).batchUpdate(
                contains("INSERT INTO core_marker_priority_records"),
                anyList()
        );
    }

    @Test
    void unknownManualOrPartialDatasetIsPreserved() {
        JdbcTemplate jdbcTemplate = configuredJdbcTemplate(
                0L,
                Map.of(
                        "summary_json", "{\"scoreVersion\":\"MANUAL-REVIEW-V1\"}",
                        "source_hash", "MANUAL123",
                        "managed_seed", false
                )
        );

        initializer(jdbcTemplate).initialize();

        verify(jdbcTemplate, never()).update(eq("DELETE FROM core_marker_priority_records"));
        verify(jdbcTemplate, never()).update(
                contains("INSERT IGNORE INTO core_marker_priority_datasets"),
                any(),
                any(),
                any(),
                any()
        );
    }

    private JdbcTemplate configuredJdbcTemplate(long recordCount, Map<String, Object> dataset) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                contains("information_schema.columns"),
                eq(Integer.class),
                any(),
                any()
        )).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM core_marker_priority_records",
                Long.class
        )).thenReturn(recordCount);
        when(jdbcTemplate.queryForMap(
                contains("FOR UPDATE"),
                eq("default")
        )).thenReturn(dataset);
        return jdbcTemplate;
    }

    private CoreMarkerPrioritySchemaInitializer initializer(JdbcTemplate jdbcTemplate) {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return new CoreMarkerPrioritySchemaInitializer(
                jdbcTemplate,
                new ObjectMapper(),
                transactionManager
        );
    }
}

package com.licong.webbackup.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.transaction.PlatformTransactionManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MethodologySchemaInitializerTest {

    @Test
    @SuppressWarnings("unchecked")
    void repeatedStartupPreservesExistingDatasetWithoutReadingOrReplacingSeedData() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(jdbcTemplate.query(
                eq("SELECT source_checksum FROM methodology_dataset_meta WHERE dataset_id = 1"),
                any(ResultSetExtractor.class)))
                .thenReturn("existing-production-checksum");

        MethodologySchemaInitializer initializer = new MethodologySchemaInitializer(
                jdbcTemplate, new ObjectMapper(), transactionManager);

        initializer.initialize();

        verify(jdbcTemplate, never()).queryForObject(
                eq("SELECT GET_LOCK(?, 30)"), eq(Boolean.class), anyString());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        verify(transactionManager, never()).getTransaction(any());
    }
}

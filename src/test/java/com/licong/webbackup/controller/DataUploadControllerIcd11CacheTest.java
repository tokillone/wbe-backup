package com.licong.webbackup.controller;

import com.licong.webbackup.common.SecuritySupport;
import com.licong.webbackup.dto.upload.DataUploadSyncResponse;
import com.licong.webbackup.entity.User;
import com.licong.webbackup.service.DataUploadService;
import com.licong.webbackup.service.Icd11SankeyService;
import com.licong.webbackup.service.SimplifiedDataUploadService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataUploadControllerIcd11CacheTest {

    @Test
    void invalidatesIcd11ReadModelsOnlyAfterSuccessfulSynchronization() {
        SecuritySupport securitySupport = mock(SecuritySupport.class);
        DataUploadService dataUploadService = mock(DataUploadService.class);
        Icd11SankeyService sankeyService = mock(Icd11SankeyService.class);
        SimplifiedDataUploadService simplifiedService = mock(SimplifiedDataUploadService.class);
        User user = mock(User.class);
        DataUploadSyncResponse response = DataUploadSyncResponse.builder().insertedRows(1).build();
        when(securitySupport.requireUser("token")).thenReturn(user);
        when(dataUploadService.sync(7L, user)).thenReturn(response);
        DataUploadController controller =
                new DataUploadController(securitySupport, dataUploadService, sankeyService, simplifiedService);

        controller.sync("token", 7L);

        InOrder ordered = inOrder(dataUploadService, sankeyService);
        ordered.verify(dataUploadService).sync(7L, user);
        ordered.verify(sankeyService).invalidateCache();
    }

    @Test
    void keepsCurrentCacheWhenSynchronizationFails() {
        SecuritySupport securitySupport = mock(SecuritySupport.class);
        DataUploadService dataUploadService = mock(DataUploadService.class);
        Icd11SankeyService sankeyService = mock(Icd11SankeyService.class);
        SimplifiedDataUploadService simplifiedService = mock(SimplifiedDataUploadService.class);
        User user = mock(User.class);
        when(securitySupport.requireUser("token")).thenReturn(user);
        when(dataUploadService.sync(8L, user)).thenThrow(new IllegalStateException("sync failed"));
        DataUploadController controller =
                new DataUploadController(securitySupport, dataUploadService, sankeyService, simplifiedService);

        assertThatThrownBy(() -> controller.sync("token", 8L))
                .isInstanceOf(IllegalStateException.class);

        verify(dataUploadService).recordSyncFailure(8L, user, "sync failed");
        verify(sankeyService, never()).invalidateCache();
    }
}

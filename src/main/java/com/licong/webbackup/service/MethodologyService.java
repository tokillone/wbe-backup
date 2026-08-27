package com.licong.webbackup.service;

import com.licong.webbackup.dto.methodology.MethodologyDataResponse;
import com.licong.webbackup.dto.methodology.MethodologyOptionsResponse;
import com.licong.webbackup.dto.methodology.MethodologyRecordResponse;

import java.util.List;
import java.util.Map;

public interface MethodologyService {
    String getVersion();

    Map<String, Object> getOverview();

    MethodologyOptionsResponse getOptions();

    List<MethodologyRecordResponse> getRecords();

    MethodologyDataResponse getData();
}

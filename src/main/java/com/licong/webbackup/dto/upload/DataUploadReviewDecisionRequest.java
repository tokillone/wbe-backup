package com.licong.webbackup.dto.upload;

import lombok.Data;

@Data
public class DataUploadReviewDecisionRequest {

    private boolean sourceCoverageConfirmed;
    private boolean traceabilityConfirmed;
    private boolean valuesAndUnitsConfirmed;
    private boolean siteLinkageConfirmed;
    private boolean icd11Confirmed;
    private boolean methodologyConfirmed;
    private boolean coreMarkerConfirmed;
    private boolean productionDiffConfirmed;
    private String note;

    public boolean allConfirmed() {
        return sourceCoverageConfirmed
                && traceabilityConfirmed
                && valuesAndUnitsConfirmed
                && siteLinkageConfirmed
                && icd11Confirmed
                && methodologyConfirmed
                && coreMarkerConfirmed
                && productionDiffConfirmed;
    }
}

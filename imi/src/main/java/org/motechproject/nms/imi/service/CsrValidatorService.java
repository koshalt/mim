package org.motechproject.nms.imi.service;

import org.motechproject.nms.kilkari.dto.CallSummaryRecordDto;

public interface CsrValidatorService {
    /**
     * Validates a call summary record. Throws InvalidCallSummaryRecord exception if something goes wrong.

     * NOTE: directly used in IT only
     *
     * @param record
     */
    void validateSummaryRecord(CallSummaryRecordDto record);
}

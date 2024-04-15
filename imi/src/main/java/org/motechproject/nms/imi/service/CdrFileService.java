package org.motechproject.nms.imi.service;

import org.motechproject.event.MotechEvent;
import org.motechproject.nms.imi.web.contract.CdrFileNotificationRequest;
import org.motechproject.nms.imi.web.contract.FileInfo;
import org.motechproject.nms.imi.web.contract.WhatsAppCdrFileNotificationRequest;
import org.motechproject.nms.imi.web.contract.WhatsAppFileNotificationRequest;

import java.io.File;
import java.util.List;

/**
 *
 */
public interface CdrFileService {

    /**
     * Verifies the checksum & record count provided in fileInfo match the checksum & record count of file
     * also verifies all csv rows are valid.
     *
     * @param fileInfo      file information provided about the file (ie: expected checksum & recordCount)
     * @param isDetailFile  true: processes a CDR file, otherwise processes a CSR file
     *
     */
    List<String> verifyChecksumAndCountAndCsv(FileInfo fileInfo, Boolean isDetailFile);

    List<String> verifyWhatsAppSMSChecksumAndCountAndCsv(FileInfo fileInfo);


    /**
     * Send aggregated detail records for processing as CallSummaryRecordDto in MOTECH events
     *
     * NOTE: only exposed here for ITs
     *
     * @param file      file to process
     * @return          a list of errors (failure) or an empty list (success)
     */
    void saveDetailRecords(File file);

    void saveWhatsAppOptSmsCsr(File file);

    void saveWhatsAppOptCsr(File file);


    /**
     * Send summary records for processing as CallSummaryRecordDto in MOTECH events
     *
     * NOTE: only exposed here for ITs
     *
     * @param file      file to process
     * @param lineCount number of CSR lines in the file
     * @return          a list of errors (failure) or an empty list (success)
     */
    void processCsrs(File file, int lineCount);

    void processWhatsAppOptSMSCsrs(File file, int lineCount);

    void processWhatsAppOptCsrs(File file, int lineCount);


    /**
     * Verify file exists, verify checksum & record count match. Then sends event to proceed to CDR processing
     * phase 2
     */
    void cdrProcessPhase1(CdrFileNotificationRequest request);

    void whatsAppSMSCdrProcessPhase1(WhatsAppFileNotificationRequest request);

    void whatsAppCdrProcessPhase1(WhatsAppCdrFileNotificationRequest request);


    /**
     * Aggregates multiple detail records provided my IMI into one summary record for each call in a given day.
     * Then sends a MOTECH PROCESS_SUMMARY_RECORD event for each summary record such that the summary record
     * process is distributed among all MOTECH nodes.
     *
     * NOTE: only exposed here for ITs. Normally called by the MOTECH event system (it's a @MotechListener)
     */
    List<String> cdrProcessPhase2(MotechEvent event);

    List<String> whatsAppSMSCdrProcessPhase2(MotechEvent event);

    List<String> whatsAppCdrProcessPhase2(MotechEvent event);

    /**
     * To be called during target file generation or triggered with Ops API. We expect ~3x the # of OBD requests/day of CDRs
     * coming back. For phase 1, ~360k CDR for 120k OBD/day. The minimum default retention is set to 5 days and can be
     * increased through config value in imi.properties - imi.cdr.retention.duration
     */
    void cleanOldCallRecords();
}

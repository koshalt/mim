package org.motechproject.nms.imi.service.impl;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.datanucleus.store.rdbms.query.ForwardQueryResult;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.motechproject.alerts.contract.AlertService;
import org.motechproject.alerts.domain.AlertStatus;
import org.motechproject.alerts.domain.AlertType;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.EventRelay;
import org.motechproject.event.listener.annotations.MotechListener;
import org.motechproject.mds.query.SqlQueryExecution;
import org.motechproject.metrics.service.Timer;
import org.motechproject.nms.imi.domain.CallDetailRecord;
import org.motechproject.nms.imi.domain.CallSummaryRecord;
import org.motechproject.nms.imi.domain.ChunkAuditRecord;
import org.motechproject.nms.imi.domain.FileAuditRecord;
import org.motechproject.nms.imi.domain.FileProcessedStatus;
import org.motechproject.nms.imi.domain.FileType;
import org.motechproject.nms.imi.exception.ChunkingException;
import org.motechproject.nms.imi.exception.ExecException;
import org.motechproject.nms.imi.exception.InternalException;
import org.motechproject.nms.imi.exception.InvalidCallRecordFileException;
import org.motechproject.nms.imi.repository.CallDetailRecordDataService;
import org.motechproject.nms.imi.repository.CallSummaryRecordDataService;
import org.motechproject.nms.imi.repository.ChunkAuditRecordDataService;
import org.motechproject.nms.imi.repository.FileAuditRecordDataService;
import org.motechproject.nms.imi.service.CdrFileService;
import org.motechproject.nms.imi.service.contract.CdrFileProcessedNotification;
import org.motechproject.nms.imi.web.contract.CdrFileNotificationRequest;
import org.motechproject.nms.imi.web.contract.FileInfo;
import org.motechproject.nms.kilkari.domain.CallRetry;
import org.motechproject.nms.kilkari.dto.CallSummaryRecordDto;
import org.motechproject.nms.kilkari.exception.InvalidCallRecordDataException;
import org.motechproject.nms.kilkari.service.CallRetryService;
import org.motechproject.nms.kilkari.service.CsrService;
import org.motechproject.nms.kilkari.service.CsrVerifierService;
import org.motechproject.server.config.SettingsFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.jdo.Query;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.min;
import static org.motechproject.nms.kilkari.utils.KilkariConstants.SQL_QUERY_LOG;
import static org.motechproject.nms.region.utils.LocationConstants.DATE_FORMAT_STRING;


/**
 * Implementation of the {@link CdrFileService} interface.
 */
@Service("cdrFileService")
public class CdrFileServiceImpl implements CdrFileService {

    public static final String DISPLAYING_THE_FIRST_N_ERRORS = "%s: %d errors - only displaying the first %d";
    private static final String DISTRIBUTED_CSR_PROCESSING = "imi.distributed_csr_processing";
    private static final String CSR_CHUNK_SIZE = "imi.csr_chunk_size";
    private static final int CSR_CHUNK_SIZE_DEFAULT = 1000;
    private static final Boolean DISTRIBUTED_CSR_PROCESSING_DEFAULT = false;
    private static final String CDR_FILE_NOTIFICATION_URL = "imi.cdr_file_notification_url";
    private static final String LOCAL_CDR_DIR = "imi.local_cdr_dir";
    private static final String CDR_CSR_RETENTION_DURATION = "imi.cdr_csr.retention.duration";
    private static final int MIN_CALL_DATA_RETENTION_DURATION_IN_DAYS = 5;
    private static final String CDR_CSR_CLEANUP_SUBJECT = "nms.imi.cdr_csr.cleanup";
    private static final String CDR_TABLE_NAME = "motech_data_services.nms_imi_cdrs";
    private static final String DELETE_CDR_TABLE = "DELETE FROM motech_data_services.nms_imi_cdrs where creationDate < now() - INTERVAL :interval DAY";
    private static final String DELETE_CSR_TABLE = "DELETE FROM motech_data_services.nms_imi_csrs where creationDate < now() - INTERVAL :interval DAY";
    private static final String NMS_IMI_KK_PROCESS_CSR = "nms.imi.kk.process_csr";
    private static final String NMS_IMI_PROCESS_CHUNK = "nms.imi.process_chunk";
    private static final String CDR_PHASE_2 = "nms.imi.kk.cdr_phase_2";
    private static final String CDR_PHASE_3 = "nms.imi.kk.cdr_phase_3";
    private static final String CDR_PHASE_4 = "nms.imi.kk.cdr_phase_4";
    private static final String CDR_PHASE_5 = "nms.imi.kk.cdr_phase_5";
    private static final String OBD_FILE_PARAM_KEY = "obdFile";
    private static final String CSR_FILE_PARAM_KEY = "csrFile";
    private static final String CSR_CHECKSUM_PARAM_KEY = "csrChecksum";
    private static final String CSR_COUNT_PARAM_KEY = "csrCount";
    private static final String CDR_FILE_PARAM_KEY = "cdrFile";
    private static final String CDR_CHECKSUM_PARAM_KEY = "cdrChecksum";
    private static final String CDR_COUNT_PARAM_KEY = "cdrCount";
    private static final String LOG_TEMPLATE = "Found %d records in table %s";
    private static final String CDR_DETAIL_FILE = "CDR Detail File";
    private static final String IGNORING_CSR_ROW = "Ignoring CSR error - %s(%d): %s";
    private static final String IGNORING_CDR_HDR = "Ignoring CDR Hhader error - %s: %s";
    private static final String IGNORING_CSR_HDR = "Ignoring CSR Hhader error - %s: %s";
    private static final String IGNORING_CDR_ROW = "Ignoring CDR error - %s(%d): %s";
    private static final String FILE_LINE_ERROR = "%s(%d): %s";
    private static final String UNABLE_TO_READ = "Unable to read %s: %s";
    private static final String UNABLE_TO_READ_HEADER = "Unable to read  header %s: %s";
    private static final int CDR_PROGRESS_REPORT_CHUNK = 10000;
    private static final int PARTITION_SIZE = 50000;
    private static final int PARTITION_SIZE_CSR = 10000;
    private static final String MAX_CDR_ERROR_COUNT = "imi.max_cdr_error_count";
    private static final String CSR_TABLE_NAME = "motech_data_services.nms_imi_csrs";
    private static final int MAX_CDR_ERROR_COUNT_DEFAULT = 100;
    private static final int MAX_CHAR_ALERT = 4500;
    private static final String INVALID_CDR_P4 = "The CDR should be readable & valid in Phase 4, this is an internal MOTECH error and must be investigated - ";
    private static final String INVALID_CSR_P5 = "The CSR should be readable & valid in Phase 5, this is an internal MOTECH error and must be investigated - ";
    private static final String INVALID_CDR_HEADER_P2 = "The CDR header should be valid in  Phase 2, this is an internal MOTECH error and must be investigated - ";
    private static final String INVALID_CDR_P2 = "The CDR should be readable & valid in Phase 2, this is an internal MOTECH error and must be investigated - ";
    private static final String INVALID_CSR_HEADER_P2 = "The CSR header should be valid in  Phase 2, this is an internal MOTECH error and must be investigated - ";
    private static final String INVALID_CSR_P2 = "The CSR should be readable & valid in Phase 2, this is an internal MOTECH error and must be investigated - ";
    private static final String COPY_ERROR = "Copy Error";
    private static final String ENTIRE_LINE_FMT = "%s [%s]";
    private static final String MOTECH_BUG = "!!!MOTECH BUG!!! Unexpected Exception in %s: %s";
    private static final String CSR_VERIFIER_CACHE_EVICT_MESSAGE = "nms.kk.cache.evict.csv_verifier";
    private static final String QUOTATION = "'";
    private static final String QUOTATION_COMMA = "', ";
    private static final String MOTECH_STRING = "'motech', ";
    private static final String MOTECH = "'motech'";
    private static final String CDR_LOG_STRING = "List of CDR's in {}";
    private static final String CSR_LOG_STRING = "List of CSR's in {}";

    private static final Logger LOGGER = LoggerFactory.getLogger(CdrFileServiceImpl.class);
    public static final double HALF = 0.5;
    public static final String FILE = "file";

    private SettingsFacade settingsFacade;
    private EventRelay eventRelay;
    private FileAuditRecordDataService fileAuditRecordDataService;
    private AlertService alertService;
    private CallDetailRecordDataService callDetailRecordDataService;
    private CallSummaryRecordDataService callSummaryRecordDataService;
    private CsrService csrService;
    private CallRetryService callRetryService;
    private CsrVerifierService csrVerifierService;
    private ChunkAuditRecordDataService chunkAuditRecordDataService;
    private PeriodFormatter periodFormatter;
    private String hostname;


    @Autowired
    public CdrFileServiceImpl(@Qualifier("imiSettings") // NO CHECKSTYLE More than 7 parameters
                              SettingsFacade settingsFacade, EventRelay eventRelay,
                              FileAuditRecordDataService fileAuditRecordDataService, AlertService alertService,
                              CallDetailRecordDataService callDetailRecordDataService,
                              CallSummaryRecordDataService callSummaryRecordDataService, CsrService csrService,
                              CsrVerifierService csrVerifierService, CallRetryService callRetryService,
                              ChunkAuditRecordDataService chunkAuditRecordDataService) {
        this.settingsFacade = settingsFacade;
        this.eventRelay = eventRelay;
        this.fileAuditRecordDataService = fileAuditRecordDataService;
        this.alertService = alertService;
        this.callDetailRecordDataService = callDetailRecordDataService;
        this.callSummaryRecordDataService = callSummaryRecordDataService;
        this.csrService = csrService;
        this.csrVerifierService = csrVerifierService;
        this.callRetryService = callRetryService;
        this.chunkAuditRecordDataService = chunkAuditRecordDataService;

        periodFormatter = new PeriodFormatterBuilder()
                .appendDays().appendSuffix("d")
                .appendHours().appendSuffix("h")
                .appendMinutes().appendSuffix("m")
                .appendSeconds().appendSuffix("s")
                .toFormatter();

        hostname = hostName();
    }


    private void alertAndAudit(String file, List<String> errors) {
        for (String error : errors) {
            alertAndAudit(file, error);
        }
    }


    private void alertAndAudit(String file, String error) {
        LOGGER.error(error);
        alertService.create(file, CDR_DETAIL_FILE, error, AlertType.CRITICAL, AlertStatus.NEW, 0, null);
        fileAuditRecordDataService.create(new FileAuditRecord(FileType.CDR_DETAIL_FILE, file, false, error,
                null, null));
    }


    private boolean sendNotificationRequest(CdrFileProcessedNotification cfpn) {
        String notificationUrl = settingsFacade.getProperty(CDR_FILE_NOTIFICATION_URL);
        LOGGER.debug("Sending {} to {}", cfpn, notificationUrl);

        ExponentialRetrySender sender = new ExponentialRetrySender(settingsFacade, alertService);

        HttpPost httpPost = new HttpPost(notificationUrl);
        ObjectMapper mapper = new ObjectMapper();

        try {
            String requestJson = mapper.writeValueAsString(cfpn);
            httpPost.setHeader("Content-type", "application/json");
            httpPost.setHeader("Accept", "application/json");
            httpPost.setEntity(new StringEntity(requestJson));
        } catch (IOException e) {
            throw new InternalException(String.format("Unable to create cdrFile notification request: %s",
                    e.getMessage()), e);
        }

        return sender.sendNotificationRequest(httpPost, HttpStatus.SC_OK, cfpn.getFileName(), "cdrFile Notification Request");
    }


    /**
     * Verifies the checksum & record count provided in fileInfo match the checksum & record count of file
     * also verifies all csv rows are valid.
     *
     * @param fileInfo  file information provided about the file (ie: expected checksum & recordCount)
     *
     * @return          a list of errors (failure) or an empty list (success)
     */
    @Override
    public List<String> verifyChecksumAndCountAndCsv(FileInfo fileInfo, Boolean isDetailFile) {
        File file = new File(localCdrDir(), fileInfo.getCdrFile());
        List<String> errors = new ArrayList<>();
        int lineNumber = 1;
        String thisChecksum = "";
        String fileName = file.getName();

        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis);
             BufferedReader reader = new BufferedReader(isr)) {

            String line = reader.readLine();

            try {
                if (isDetailFile) {
                    CdrHelper.validateHeader(line);
                } else {
                    CsrHelper.validateHeader(line);
                }
            } catch (IllegalArgumentException e) {
                String error = String.format("Unable to read header %s: %s", fileName, e.getMessage());
                errors.add(error);
            }

            while ((line = reader.readLine()) != null) {

                try {
                    // Parse the CSV line into a CDR or CSR (which we actually discard in this phase)
                    // This will trow IllegalArgumentException if the CSV is malformed
                    if (isDetailFile) {
                        CdrHelper.validateCsv(line);
                    } else {
                        CsrHelper.validateCsv(line);
                    }

                } catch (IllegalArgumentException e) {
                    errors.add(String.format(FILE_LINE_ERROR, fileName, lineNumber, e.getMessage()));
                }

                lineNumber++;

            }
            reader.close();
            isr.close();
            fis.close();

            thisChecksum = ChecksumHelper.checksum(file);


            if (!thisChecksum.equalsIgnoreCase(fileInfo.getChecksum())) {
                String error = String.format("Checksum mismatch for %s: provided checksum: %s, calculated checksum: %s",
                        fileName, fileInfo.getChecksum(), thisChecksum);
                errors.add(error);
            }

            if (lineNumber - 1 != fileInfo.getRecordsCount()) {
                String error = String.format("Record count mismatch, provided count: %d, actual count: %d",
                        fileInfo.getRecordsCount(), lineNumber - 1);
                errors.add(error);
            }

        } catch (IOException e) {
                String error = String.format(UNABLE_TO_READ, fileName, e.getMessage());
                errors.add(error);
            }
        return errors;
    }


    /**
     * Save detail records for reporting
     *
     * @param file      file to process
     * @return          a list of errors (failure) or an empty list (success)
     */
    @Override
    public void saveDetailRecords(File file) {
        int lineNumber = 1;
        int saveCount = 0;
        String fileName = file.getName();
        List<CallDetailRecord> callDetailRecords = new ArrayList<>();

        LOGGER.info("saveDetailRecords({})", fileName);

        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis);
             BufferedReader reader = new BufferedReader(isr)) {

            String line;

            try {
                line = reader.readLine();
                CdrHelper.validateHeader(line);
            } catch (IllegalArgumentException e) {
                //errors here should have been reported in Phase 2, let's just ignore them
                //todo remove following line to not over confuse ops?
                LOGGER.debug(String.format(IGNORING_CDR_HDR, fileName, e.getMessage()));
            }

            Timer timer = new Timer("cdr", "cdrs");
            while ((line = reader.readLine()) != null) {

                    CallDetailRecord cdr = CdrHelper.csvLineToCdr(line);

                    callDetailRecords.add(cdr);

            }
            Long updatedRecords = bulkUpdateCdr(callDetailRecords);
            LOGGER.debug("{} records updated in time : {}", updatedRecords, timer.time());

        } catch (IOException e) {
            String error = INVALID_CDR_P4 + String.format(UNABLE_TO_READ, fileName, e.getMessage());
            LOGGER.error(error);
            alertService.create(fileName, "Invalid CDR in Phase 4", error, AlertType.CRITICAL, AlertStatus.NEW, 0,
                    null);
        } catch (Exception e) {
            String msg = String.format(MOTECH_BUG, "P4 - saveDetailRecords", ExceptionUtils.getFullStackTrace(e));
            LOGGER.error(msg);
            alertService.create(fileName, "saveDetailRecords", msg.substring(0, min(msg.length(), MAX_CHAR_ALERT)),
                    AlertType.CRITICAL, AlertStatus.NEW, 0, null);
        }
    }


    private Long bulkUpdateCdr(List<CallDetailRecord> callDetailRecords){

        int count = 0;
        Long sqlCount = 0L;
        while (count < callDetailRecords.size()) {
            List<CallDetailRecord> updateObjectsPart = new ArrayList<>();
            while (updateObjectsPart.size() < PARTITION_SIZE && count < callDetailRecords.size()) {
                updateObjectsPart.add(callDetailRecords.get(count));
                count++;
            }

            sqlCount += cdrBulkInsert(updateObjectsPart);
            updateObjectsPart.clear();
        }
        return sqlCount;

    }

    private Long cdrBulkInsert(final List<CallDetailRecord> updateObjects) {
        Timer queryTimer = new Timer();

        @SuppressWarnings("unchecked")
        SqlQueryExecution<Long> queryExecution = new SqlQueryExecution<Long>() {

            @Override
            public String getSqlQuery() {
                String query = "INSERT IGNORE INTO nms_imi_cdrs (requestId, msisdn, callId, attemptNo, callStartTime," +
                        "callAnswerTime, callEndTime, callDurationInPulse, callStatus, languageLocationId, contentFile," +
                        " msgPlayStartTime, msgPlayEndTime, circleId,operatorId, priority, callDisconnectReason, weekId," +
                        " creationDate, modificationDate, modifiedBy, owner, creator)  " +
                        "values  " +
                        insertQuerySet(updateObjects);

                LOGGER.debug(SQL_QUERY_LOG, query);
                return query;
            }

            @Override
            public Long execute(Query query) {
                query.setClass(CallDetailRecord.class);
                return (Long) query.execute();
            }
        };

        Long updatedNo = callDetailRecordDataService.executeSQLQuery(queryExecution);
        LOGGER.debug(CDR_LOG_STRING, queryTimer.time());
        return updatedNo;
    }


    private String insertQuerySet(List<CallDetailRecord> callDetailRecords){

        StringBuilder stringBuilder = new StringBuilder();

        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern(DATE_FORMAT_STRING);
        DateTime dateTimeNow = new DateTime();

        int i = 0;
        for (CallDetailRecord callDetailRecord: callDetailRecords) {
            if (i != 0) {
                stringBuilder.append(", ");
            }
            stringBuilder.append("(");
            stringBuilder.append(QUOTATION + callDetailRecord.getRequestId()+ QUOTATION_COMMA);
            stringBuilder.append(callDetailRecord.getMsisdn()+ ", ");
            stringBuilder.append(QUOTATION + callDetailRecord.getCallId()+ QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callDetailRecord.getAttemptNo()+ QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callDetailRecord.getCallStartTime()+ QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callDetailRecord.getCallAnswerTime()+ QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callDetailRecord.getCallEndTime()+ QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callDetailRecord.getCallDurationInPulse()+ QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callDetailRecord.getCallStatus()+ QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callDetailRecord.getLanguageLocationId()+ QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callDetailRecord.getContentFile()+ QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callDetailRecord.getMsgPlayStartTime()+ QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callDetailRecord.getMsgPlayEndTime()+ QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callDetailRecord.getCircleId()+ QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callDetailRecord.getOperatorId()+ QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callDetailRecord.getPriority()+ QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callDetailRecord.getCallDisconnectReason()+ QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callDetailRecord.getWeekId()+ QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + dateTimeFormatter.print(dateTimeNow) + QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + dateTimeFormatter.print(dateTimeNow) + QUOTATION_COMMA);
            stringBuilder.append(MOTECH_STRING);
            stringBuilder.append(MOTECH_STRING);
            stringBuilder.append(MOTECH);
            stringBuilder.append(")");
            i++;
        }
        return stringBuilder.toString();

    }

    private boolean shouldDistributeCsrProcessing() {
        try {
            return Boolean.valueOf(settingsFacade.getProperty(DISTRIBUTED_CSR_PROCESSING));
        } catch (Exception e) {
            LOGGER.info("Unable to read {}, returning default value {}", DISTRIBUTED_CSR_PROCESSING,
                    DISTRIBUTED_CSR_PROCESSING_DEFAULT);
            return DISTRIBUTED_CSR_PROCESSING_DEFAULT;
        }
    }


    private int csrChunkSize() {
        try {
            return Integer.valueOf(settingsFacade.getProperty(CSR_CHUNK_SIZE));
        } catch (Exception e) {
            LOGGER.info("Unable to read {}, returning default value {}", CSR_CHUNK_SIZE, CSR_CHUNK_SIZE_DEFAULT);
            return CSR_CHUNK_SIZE_DEFAULT;
        }
    }


    private void processOneCsr(CallSummaryRecordDto csrDto, boolean distributed) {
        Map<String, Object> params = CallSummaryRecordDto.toParams(csrDto);
        MotechEvent motechEvent = new MotechEvent(NMS_IMI_KK_PROCESS_CSR, params);
        if (distributed) {
            eventRelay.sendEventMessage(motechEvent);
        } else {
            csrService.processCallSummaryRecord(motechEvent);
        }
    }


    private void dispatchChunk(String file, String name, List<CallSummaryRecordDto> csrDtos, int chunkCount,
                               int csrCount) {
        ObjectMapper mapper = new ObjectMapper();
        String chunk;
        try {
            chunk = mapper.writeValueAsString(csrDtos);
        } catch (IOException e) {
            throw new ChunkingException(String.format("Exception packaging CSR chunk: %s", e.getMessage()), e);
        }

        Map<String, Object> params = new HashMap<>();
        params.put(FILE, file);
        params.put("name", name);
        params.put("chunk", chunk);
        params.put("chunkCount", chunkCount);
        params.put("csrCount", csrCount);
        MotechEvent motechEvent = new MotechEvent(NMS_IMI_PROCESS_CHUNK, params);
        eventRelay.sendEventMessage(motechEvent);
    }


    private String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "###";
        }
    }


    private String reportChunkProcessingTime(final String file) {

        @SuppressWarnings("unchecked")
        SqlQueryExecution<String> queryExecution = new SqlQueryExecution<String>() {

            @Override
            public String getSqlQuery() {
                String query = "SELECT MIN(processingStart) as start, MAX(processingEnd) as end " +
                        "FROM nms_imi_chunk_audit_records WHERE file = :file";
                LOGGER.debug("SQL QUERY: {}", query);
                return query;
            }

            @Override
            public String execute(Query query) {

                Map params = new HashMap();
                params.put(FILE, file);
                ForwardQueryResult fqr = (ForwardQueryResult) query.executeWithMap(params);

                if (fqr.isEmpty()) {
                    throw new IllegalStateException("No row was returned!");
                }
                if (fqr.size() == 1) {
                    Timestamp tsStart = (Timestamp) ((Object[]) fqr.get(0))[0];
                    Timestamp tsEnd = (Timestamp) ((Object[]) fqr.get(0))[1];
                    DateTime dtStart = new DateTime(tsStart);
                    DateTime dtEnd = new DateTime(tsEnd);
                    Duration d = new Duration(dtStart, dtEnd);
                    return String.format("%s (%ds)", periodFormatter.print(d.toPeriod()), d.getStandardSeconds());
                }
                throw new IllegalStateException("More than one row returned!");
            }
        };

        return chunkAuditRecordDataService.executeSQLQuery(queryExecution);
    }


    private void reportIfAllChunksWereProcessed(final String file) {

        @SuppressWarnings("unchecked")
        SqlQueryExecution<List<ChunkAuditRecord>> queryExecution = new SqlQueryExecution<List<ChunkAuditRecord>>() {

            @Override
            public String getSqlQuery() {
                String query = "SELECT * FROM nms_imi_chunk_audit_records WHERE file = :file AND node IS NULL LIMIT 1";
                LOGGER.debug("SQL QUERY: {}", query);
                return query;
            }

            @Override
            public List<ChunkAuditRecord> execute(Query query) {

                query.setClass(CallRetry.class);

                Map params = new HashMap();
                params.put(FILE, file);
                ForwardQueryResult fqr = (ForwardQueryResult) query.executeWithMap(params);

                return (List<ChunkAuditRecord>) fqr;
            }
        };

        List<ChunkAuditRecord> records = chunkAuditRecordDataService.executeSQLQuery(queryExecution);

        if (records.size() == 0) {
            LOGGER.info("CSR processing complete - {} processed in {}.", file, reportChunkProcessingTime(file));
        }
    }


    private void upsertChunkAuditRecord(String file, String chunk, int csrCount) {
        ChunkAuditRecord record = chunkAuditRecordDataService.findByFileAndChunk(file, chunk);
        if (record == null) {
            chunkAuditRecordDataService.create(new ChunkAuditRecord(file, chunk, csrCount));
        } else {
            record.setCsrProcessed(0);
            record.setNode(null);
            record.setProcessingStart(null);
            record.setProcessingEnd(null);
            chunkAuditRecordDataService.update(record);
        }
    }


    private void updateChunkAuditRecord(String file, String chunk, int csrCount, DateTime processingStart,
                                        DateTime processingEnd, String timing) {
        ChunkAuditRecord record = chunkAuditRecordDataService.findByFileAndChunk(file, chunk);
        if (record != null) {
            record.setCsrProcessed(csrCount);
            record.setNode(hostname);
            record.setProcessingStart(processingStart);
            record.setProcessingEnd(processingEnd);
            record.setTiming(timing);
            chunkAuditRecordDataService.update(record);
        }
    }


    @MotechListener(subjects = { NMS_IMI_PROCESS_CHUNK })
    @Transactional
    public void processChunk(MotechEvent event) throws IOException {
        DateTime processingStart = DateTime.now();
        Timer timer = new Timer("csr", "csrs");
        String file = (String) event.getParameters().get(FILE);
        String name = (String) event.getParameters().get("name");
        String json = (String) event.getParameters().get("chunk");
        ObjectMapper mapper = new ObjectMapper();
        List<CallSummaryRecordDto> csrDtos;

        try {
            csrDtos = mapper.readValue(json, new TypeReference<List<CallSummaryRecordDto>>() { });

            LOGGER.debug("Processing {} ({} csrs)", name, csrDtos.size());

            for (CallSummaryRecordDto csrDto : csrDtos) {
                Map<String, Object> params = CallSummaryRecordDto.toParams(csrDto);
                MotechEvent motechEvent = new MotechEvent(NMS_IMI_KK_PROCESS_CSR, params);
                csrService.processCallSummaryRecord(motechEvent);
            }

            updateChunkAuditRecord(file, name, csrDtos.size(), processingStart, DateTime.now(),
                    timer.frequency(csrDtos.size()));
            reportIfAllChunksWereProcessed(file);

        } catch (Exception e) {
            String msg = String.format(MOTECH_BUG, "P5 - processChunk - " + name, ExceptionUtils.getFullStackTrace(e));
            LOGGER.error(msg);
            alertService.create(name, "processCsrs", msg.substring(0, min(msg.length(), MAX_CHAR_ALERT)),
                    AlertType.CRITICAL, AlertStatus.NEW, 0, null);
            // We want to fail this event to we get retried
            throw e;
        }

        LOGGER.info("Processed {} - {}", name, timer.frequency(csrDtos.size()));
    }


    private int calculateChunkCount(int count, int size) {
        if (count % size == 0) {
            return count / size;
        }

        return (int) (count / size) + 1;
    }


    /**
     * Send summary records for processing as CallSummaryRecordDto in MOTECH events or process them directly,
     * depending on the imi.distributed_csr_processing setting
     * Additionally stores a copy of the provided CSR in the CallSummaryRecord table, for reporting
     *
     * @param file      file to process
     * @return          a list of errors (failure) or an empty list (success)
     */
    @Override //NO CHECKSTYLE Cyclomatic Complexity
    public void processCsrs(File file, int lineCount) { //NOPMD NcssMethodCount
        int lineNumber = 1;
        int saveCount = 0;
        int processCount = 0;
        int chunkCount = 0;
        int chunkNumber = 0;
        String fileName = file.getName();
        List<CallSummaryRecord> callSummaryRecords = new ArrayList<>();

        LOGGER.info("processCsrs({}, {})", fileName, lineCount);

        boolean distributedProcessing = shouldDistributeCsrProcessing();
        String verb = distributedProcessing ? "distributed" : "enqueued";

        List<CallSummaryRecordDto> chunk = new ArrayList<>();
        int chunkSize = csrChunkSize();
        if (chunkSize > 1) {
            LOGGER.info("CSRs will be distributed in chunks of {} csrs", chunkSize);
            chunkCount = calculateChunkCount(lineCount, chunkSize);
            chunkNumber = 1;
            LOGGER.info("{} CSRs will be distributed in {} chunk(s)", lineCount, chunkCount);
            verb = "distributed";
        } else {
            LOGGER.info("CSR processing will be {}", distributedProcessing ? "distributed" : "local");
        }

        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis);
             BufferedReader reader = new BufferedReader(isr)) {

            String line;
            try {
                line = reader.readLine();
                CsrHelper.validateHeader(line);
            } catch (IllegalArgumentException e) {
                //errors here should have been reported in Phase 2, let's just ignore them
                //todo remove following line to not over confuse ops?
                LOGGER.debug(String.format(IGNORING_CSR_HDR, fileName, e.getMessage()));
            }

            Timer timer = new Timer("csr", "csrs");
            Timer chunkTimer = new Timer("chunk", "chunks");
            while ((line = reader.readLine()) != null) {
                try {

                    CallSummaryRecord csr = CsrHelper.csvLineToCsr(line);
                    callSummaryRecords.add(csr);

                    if (chunkSize > 1) {
                        chunk.add(csr.toDto());
                        if (chunk.size() >= chunkSize || lineNumber >= lineCount) {
                            String chunkName = String.format("Chunk%d/%d", chunkNumber, chunkCount);
                            dispatchChunk(fileName, chunkName, chunk, chunkCount, lineCount);
                            upsertChunkAuditRecord(fileName, chunkName, chunk.size());

                            LOGGER.info("Dispatched {} - {}", chunkName, chunkTimer.frequency(chunkNumber));

                            chunk = new ArrayList<>();
                            chunkNumber++;
                        }
                    } else {
                        processOneCsr(csr.toDto(), distributedProcessing);
                        processCount++;
                    }

                } catch (InvalidCallRecordDataException | IllegalArgumentException e) {
                    // All errors here should have been reported in Phase 2, let's just ignore them
                    //todo remove following line to not over confuse ops?
                    LOGGER.debug(String.format(IGNORING_CSR_ROW, fileName, lineNumber, e.getMessage()));
                }

                if (lineNumber % CDR_PROGRESS_REPORT_CHUNK == 0) {
                    LOGGER.debug("Read {}", timer.frequency(lineNumber));
                }

                if (processCount > 0 && processCount % CDR_PROGRESS_REPORT_CHUNK == 0) {
                    LOGGER.debug("Processed {}", timer.frequency(processCount));
                }

                lineNumber++;
            }
            Long updatedRecords = bulkUpdateCsr(callSummaryRecords);
            LOGGER.debug("{} records updated in time : {}", updatedRecords, timer.time());

            LOGGER.info(String.format("Read %s", timer.frequency(lineNumber - 1)));
            if (chunkSize <= 1) {
                LOGGER.info(String.format("Saved %d, %s %d", saveCount, verb, processCount));
            }

        } catch (IOException e) {
            String error = INVALID_CSR_P5 + String.format(UNABLE_TO_READ, fileName, e.getMessage());
            LOGGER.error(error);
            alertService.create(fileName, "Invalid CSR in Phase 5", error, AlertType.CRITICAL, AlertStatus.NEW, 0,
                    null);
        } catch (Exception e) {
            String msg = String.format(MOTECH_BUG, "P5 - processCsrs", ExceptionUtils.getFullStackTrace(e));
            LOGGER.error(msg);
            alertService.create(fileName, "processCsrs", msg.substring(0, min(msg.length(), MAX_CHAR_ALERT)),
                    AlertType.CRITICAL, AlertStatus.NEW, 0, null);
        }
    }


    private Long bulkUpdateCsr(List<CallSummaryRecord> callSummaryRecords){

        int count = 0;
        Long sqlCount = 0L;
        while (count < callSummaryRecords.size()) {
            List<CallSummaryRecord> updateObjectsPart = new ArrayList<>();
            while (updateObjectsPart.size() < PARTITION_SIZE_CSR && count < callSummaryRecords.size()) {
                updateObjectsPart.add(callSummaryRecords.get(count));
                count++;
            }

            sqlCount += csrBulkInsert(updateObjectsPart);
            updateObjectsPart.clear();
        }
        return sqlCount;

    }

    private Long csrBulkInsert(final List<CallSummaryRecord> updateObjects) {
        Timer queryTimer = new Timer();

        @SuppressWarnings("unchecked")
        SqlQueryExecution<Long> queryExecution = new SqlQueryExecution<Long>() {

            @Override
            public String getSqlQuery() {
                String query = "INSERT IGNORE INTO nms_imi_csrs (attempts, callFlowUrl, circle, cli, contentFileName," +
                        "creationDate, creator, finalStatus, languageLocationCode, modificationDate, modifiedBy," +
                        " msisdn, owner, priority, requestId, serviceId, statusCode, weekId)  " +
                        "values  " +
                        insertQuerySetCsr(updateObjects);

                LOGGER.debug(SQL_QUERY_LOG, query);
                return query;
            }

            @Override
            public Long execute(Query query) {
                query.setClass(CallDetailRecord.class);
                return (Long) query.execute();
            }
        };

        Long updatedNo = callSummaryRecordDataService.executeSQLQuery(queryExecution);
        LOGGER.debug(CSR_LOG_STRING, queryTimer.time());
        return updatedNo;
    }


    private String insertQuerySetCsr(List<CallSummaryRecord> callSummaryRecords){

        StringBuilder stringBuilder = new StringBuilder();

        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern(DATE_FORMAT_STRING);
        DateTime dateTimeNow = new DateTime();

        int i = 0;
        for (CallSummaryRecord callSummaryRecord: callSummaryRecords) {
            if (i != 0) {
                stringBuilder.append(", ");
            }
            stringBuilder.append("(");
            stringBuilder.append(callSummaryRecord.getAttempts() + ", ");
            stringBuilder.append(QUOTATION + callSummaryRecord.getCallFlowUrl() + QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callSummaryRecord.getCircle() + QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callSummaryRecord.getCli() + QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callSummaryRecord.getContentFileName() + QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + dateTimeFormatter.print(dateTimeNow) + QUOTATION_COMMA);
            stringBuilder.append(MOTECH_STRING);
            stringBuilder.append(callSummaryRecord.getFinalStatus() + ", ");
            stringBuilder.append(QUOTATION + callSummaryRecord.getLanguageLocationCode() + QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + dateTimeFormatter.print(dateTimeNow) + QUOTATION_COMMA);
            stringBuilder.append(MOTECH_STRING);
            stringBuilder.append(callSummaryRecord.getMsisdn() + ", ");
            stringBuilder.append(MOTECH_STRING);
            stringBuilder.append(callSummaryRecord.getPriority() + ", ");
            stringBuilder.append(QUOTATION + callSummaryRecord.getRequestId() + QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callSummaryRecord.getServiceId() + QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callSummaryRecord.getStatusCode()+ QUOTATION_COMMA);
            stringBuilder.append(QUOTATION + callSummaryRecord.getWeekId()+ QUOTATION);
            stringBuilder.append(")");
            i++;
        }
        return stringBuilder.toString();

    }


    private CdrFileNotificationRequest requestFromParams(Map<String, Object> params) {
        return new CdrFileNotificationRequest(
            (String) params.get(OBD_FILE_PARAM_KEY),
            new FileInfo(
                    (String) params.get(CSR_FILE_PARAM_KEY),
                    (String) params.get(CSR_CHECKSUM_PARAM_KEY),
                    (int) params.get(CSR_COUNT_PARAM_KEY)
            ),
        new FileInfo(
                (String) params.get(CDR_FILE_PARAM_KEY),
                (String) params.get(CDR_CHECKSUM_PARAM_KEY),
                (int) params.get(CDR_COUNT_PARAM_KEY)
            )
        );
    }


    private  Map<String, Object> paramsFromRequest(CdrFileNotificationRequest request) {
        Map<String, Object> params = new HashMap<>();
        params.put(OBD_FILE_PARAM_KEY, request.getFileName());
        params.put(CSR_FILE_PARAM_KEY, request.getCdrSummary().getCdrFile());
        params.put(CSR_CHECKSUM_PARAM_KEY, request.getCdrSummary().getChecksum());
        params.put(CSR_COUNT_PARAM_KEY, request.getCdrSummary().getRecordsCount());
        params.put(CDR_FILE_PARAM_KEY, request.getCdrDetail().getCdrFile());
        params.put(CDR_CHECKSUM_PARAM_KEY, request.getCdrDetail().getChecksum());
        params.put(CDR_COUNT_PARAM_KEY, request.getCdrDetail().getRecordsCount());
        return params;
    }


    private void sendPhaseEvent(String subject, CdrFileNotificationRequest request) {
        MotechEvent motechEvent = new MotechEvent(subject, paramsFromRequest(request));
        eventRelay.sendEventMessage(motechEvent);
    }


    private File localCdrDir() {
        return new File(settingsFacade.getProperty(LOCAL_CDR_DIR));
    }


    // Phase 1: verify the file exists, the csv is valid and its record count and checksum match the provided info
    //          while collecting a list of errors on the go.
    //          Does not proceed to phase 2 if any error occurred and returns an error
    @Override //NO CHECKSTYLE Cyclomatic Complexity
    public void cdrProcessPhase1(CdrFileNotificationRequest request) {

        LOGGER.info("CDR Processing - Phase 1 - Start");

        List<String> cdrErrors = verifyChecksumAndCountAndCsv(request.getCdrDetail(), true);
        alertAndAudit(request.getCdrDetail().getCdrFile(), cdrErrors);


        List<String> csrErrors = verifyChecksumAndCountAndCsv(request.getCdrSummary(), false);
        alertAndAudit(request.getCdrSummary().getCdrFile(), csrErrors);

        if (cdrErrors.size() > 0 || csrErrors.size() > 0) {

            List<String> returnedErrors = new ArrayList<>();

            int maxErrors = getMaxErrorCount();

            LOGGER.debug("Phase 1 - Error");

            if (cdrErrors.size() > 0) {
                List<String> maxCdrErrors = cdrErrors.subList(0, min(maxErrors, cdrErrors.size()));

                if (cdrErrors.size() > maxErrors) {
                    String error = String.format(DISPLAYING_THE_FIRST_N_ERRORS, request.getCdrDetail().getCdrFile(),
                            cdrErrors.size(), maxErrors);
                    LOGGER.error(error);
                    alertService.create(request.getCdrDetail().getCdrFile(), "Phase 1 - Too many errors in CDR", error,
                            AlertType.HIGH, AlertStatus.NEW, 0, null);
                    returnedErrors.add(error);
                }

                returnedErrors.addAll(maxCdrErrors);
            }

            if (csrErrors.size() > 0) {
                List<String> maxCsrErrors = csrErrors.subList(0, min(maxErrors, csrErrors.size()));

                if (csrErrors.size() > maxErrors) {
                    String error = String.format(DISPLAYING_THE_FIRST_N_ERRORS, request.getCdrSummary().getCdrFile(),
                            csrErrors.size(), maxErrors);
                    LOGGER.error(error);
                    alertService.create(request.getCdrSummary().getCdrFile(), "Phase 1 - Too many errors in CSR", error,
                            AlertType.HIGH, AlertStatus.NEW, 0, null);
                    returnedErrors.add(error);
                }

                returnedErrors.addAll(maxCsrErrors);
            }

            if (cdrErrors.size() > 0) {
                fileAuditRecordDataService.create(new FileAuditRecord(FileType.CDR_DETAIL_FILE,
                        request.getCdrDetail().getCdrFile(), false,
                        String.format("%d invalid CDR rows, see tomcat log", cdrErrors.size()), null, null));
            }

            if (csrErrors.size() > 0) {
                fileAuditRecordDataService.create(new FileAuditRecord(FileType.CDR_SUMMARY_FILE,
                        request.getCdrSummary().getCdrFile(), false,
                        String.format("%d invalid CSR rows, see tomcat log", csrErrors.size()), null, null));
            }

            throw new InvalidCallRecordFileException(returnedErrors);
        }

        // Send a MOTECH event to continue to phase 2 (without timing out the POST from IMI)
        LOGGER.info("Phase 1 - Sending Phase 2 event");
        sendPhaseEvent(CDR_PHASE_2, request);

        LOGGER.info("Phase 1 - Success");
    }


    // Runs the copy command stored in the imi.scp.from_command entry of the imi.properties file
    // Likely scp, but could be something else
    private void copyFile(File file) throws ExecException {
        LOGGER.debug("Copying {} from IMI...", file.getName());
        ScpHelper scpHelper = new ScpHelper(settingsFacade);
        scpHelper.scpCdrFromRemote(file.getName());
    }


    private List<String> verifyDetailFile(CdrFileNotificationRequest request) {
        List<String> errors = new ArrayList<>();
        File file = new File(localCdrDir(), request.getCdrDetail().getCdrFile());
        String fileName = file.getName();
        int lineNumber = 1;

        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis);
             BufferedReader reader = new BufferedReader(isr)) {

            String line;

            try {
                line = reader.readLine();
                CdrHelper.validateHeader(line);
            } catch (IllegalArgumentException e) {
                String error = INVALID_CDR_HEADER_P2 + String.format(UNABLE_TO_READ_HEADER, fileName, e.getMessage());
                errors.add(error);
                LOGGER.error(error);
                alertService.create(fileName, "Invalid CDR Header in Phase 2", error, AlertType.CRITICAL,
                        AlertStatus.NEW, 0, null);
                return errors;
            }

            while ((line = reader.readLine()) != null) {
                try {

                    CdrHelper.csvLineToCdrDto(line);

                } catch (InvalidCallRecordDataException e) {
                    String error = String.format(FILE_LINE_ERROR, fileName, lineNumber, e.getMessage());
                    LOGGER.debug(String.format(ENTIRE_LINE_FMT, error, line));
                    errors.add(error);
                }
                lineNumber++;
            }

        } catch (IOException e) {
            String error = INVALID_CDR_P2 + String.format(UNABLE_TO_READ, fileName, e.getMessage());
            errors.add(error);
            LOGGER.error(error);
            alertService.create(fileName, "Invalid CDR in Phase 2", error, AlertType.CRITICAL, AlertStatus.NEW, 0,
                    null);
            return errors;
        }

        return errors;

    }


    private List<String> verifySummaryFile(CdrFileNotificationRequest request) {
        List<String> errors = new ArrayList<>();
        File file = new File(localCdrDir(), request.getCdrSummary().getCdrFile());
        String fileName = file.getName();
        int lineNumber = 1;

        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis);
             BufferedReader reader = new BufferedReader(isr)) {

            String line;

            try {
                line = reader.readLine();
                CsrHelper.validateHeader(line);
            } catch (IllegalArgumentException e) {
                String error = INVALID_CSR_HEADER_P2 + String.format(UNABLE_TO_READ_HEADER,
                        fileName, e.getMessage());
                errors.add(error);
                LOGGER.error(error);
                alertService.create(fileName, "Invalid CSR Header in Phase 2", error, AlertType.CRITICAL,
                        AlertStatus.NEW, 0, null);
                return errors;
            }

            while ((line = reader.readLine()) != null) {
                try {

                    CallSummaryRecord csr = CsrHelper.csvLineToCsr(line);
                    CallSummaryRecordDto csrDto = csr.toDto();
                    csrVerifierService.verify(csrDto);

                } catch (InvalidCallRecordDataException e) {
                    String error = String.format(FILE_LINE_ERROR, fileName, lineNumber, e.getMessage());
                    LOGGER.debug(String.format(ENTIRE_LINE_FMT, error, line));
                    errors.add(error);
                }
                lineNumber++;
            }

        } catch (IOException e) {
            String error = INVALID_CSR_P2 + String.format(UNABLE_TO_READ, fileName, e.getMessage());
            errors.add(error);
            LOGGER.error(error);
            alertService.create(fileName, "Invalid File in Phase 2", error, AlertType.CRITICAL, AlertStatus.NEW, 0,
                    null);
            return errors;
        }

        return errors;
    }


    private int getMaxErrorCount() {
        try {
            return Integer.parseInt(settingsFacade.getProperty(MAX_CDR_ERROR_COUNT));
        } catch (NumberFormatException e) {
            return MAX_CDR_ERROR_COUNT_DEFAULT;
        }
    }


    // Phase 2: Verify files a little mode deeply than t=in phase 1 (ie: check csv field values are valid)
    //          Send list on invalid CDR/CSR rows in CdrFileProcessedNotification HTTP POST back to IMI and dispatches
    //          messages for Phase 3 & 4 processing to any node.
    @Override //NO CHECKSTYLE Cyclomatic Complexity
    @MotechListener(subjects = { CDR_PHASE_2 })
    @Transactional
    public List<String> cdrProcessPhase2(MotechEvent event) { //NOPMD NcssMethodCount

        LOGGER.info("Phase 2 - Start");

        CdrFileNotificationRequest request = requestFromParams(event.getParameters());

        LOGGER.debug("Phase 2 - cdrFileNotificationRequest: {}", request);

        //
        // Detail File
        //
        LOGGER.info("Phase 2 - copy detail File");
        File cdrFile = new File(localCdrDir(), request.getCdrDetail().getCdrFile());
        try {
            copyFile(cdrFile);
        } catch (ExecException e) {
            String error = String.format("Error copying CDR file %s: %s", cdrFile.getName(), e.getMessage());
            LOGGER.error(error);
            alertService.create(cdrFile.getName(), COPY_ERROR, error, AlertType.CRITICAL, AlertStatus.NEW, 0, null);
            return Arrays.asList(error);
        }
        LOGGER.info("Phase 2 - verify detail File");
        List<String> detailErrors = verifyDetailFile(request);

        //
        // Summary File
        //
        LOGGER.info("Phase 2 - copy detail File");
        File csrFile = new File(localCdrDir(), request.getCdrSummary().getCdrFile());
        try {
            copyFile(csrFile);
        } catch (ExecException e) {
            String error = String.format("Error copying CSR file %s: %s", csrFile.getName(), e.getMessage());
            LOGGER.error(error);
            alertService.create(csrFile.getName(), COPY_ERROR, error, AlertType.CRITICAL, AlertStatus.NEW, 0, null);
            // This is a monster error, let's not even bother talking about the potential errors in detailErrors
            return Arrays.asList(error);
        }
        LOGGER.info("Phase 2 - verifySummaryFile");
        List<String> summaryErrors = verifySummaryFile(request);


        List<String> returnedErrors = new ArrayList<>();
        FileProcessedStatus status = FileProcessedStatus.FILE_PROCESSED_SUCCESSFULLY;
        String failure = null;
        if (detailErrors.size() > 0 || summaryErrors.size() > 0) {

            int maxErrors = getMaxErrorCount();

            LOGGER.debug("Phase 2 - Error");
            status = FileProcessedStatus.FILE_ERROR_IN_FILE_FORMAT;

            if (detailErrors.size() > 0) {
                List<String> maxDetailErrors = detailErrors.subList(0, min(maxErrors, detailErrors.size()));

                if (detailErrors.size() > maxErrors) {
                    String error = String.format(DISPLAYING_THE_FIRST_N_ERRORS, cdrFile.getName(), detailErrors.size(),
                            maxErrors);
                    LOGGER.error(error);
                    alertService.create(request.getCdrDetail().getCdrFile(), "Too many errors in CDR", error,
                            AlertType.HIGH, AlertStatus.NEW, 0, null);
                    returnedErrors.add(error);
                }

                returnedErrors.addAll(maxDetailErrors);
            }

            if (summaryErrors.size() > 0) {
                List<String> maxSummaryErrors = summaryErrors.subList(0, min(maxErrors, summaryErrors.size()));

                if (summaryErrors.size() > maxErrors) {
                    String error = String.format(DISPLAYING_THE_FIRST_N_ERRORS, csrFile.getName(), summaryErrors.size(),
                            maxErrors);
                    LOGGER.error(error);
                    alertService.create(request.getCdrSummary().getCdrFile(), "Too many errors in CSR", error,
                            AlertType.HIGH, AlertStatus.NEW, 0, null);
                    returnedErrors.add(error);
                }

                returnedErrors.addAll(maxSummaryErrors);
            }

            failure = StringUtils.join(returnedErrors, ",");

            if (detailErrors.size() > 0) {
                fileAuditRecordDataService.create(new FileAuditRecord(FileType.CDR_DETAIL_FILE,
                        request.getCdrDetail().getCdrFile(), false,
                        String.format("%d invalid CDR rows, see tomcat log", detailErrors.size()), null, null));
            }

            if (summaryErrors.size() > 0) {
                fileAuditRecordDataService.create(new FileAuditRecord(FileType.CDR_SUMMARY_FILE,
                        request.getCdrSummary().getCdrFile(), false,
                        String.format("%d invalid CSR rows, see tomcat log", summaryErrors.size()), null, null));
            }
        } else {
            // record successful verification of detail & summary files
            fileAuditRecordDataService.create(new FileAuditRecord(FileType.CDR_DETAIL_FILE,
                    request.getCdrDetail().getCdrFile(), true, "Successfully verified",
                    request.getCdrDetail().getRecordsCount(), request.getCdrDetail().getChecksum()));
            fileAuditRecordDataService.create(new FileAuditRecord(FileType.CDR_SUMMARY_FILE,
                    request.getCdrSummary().getCdrFile(), true, "Successfully verified",
                    request.getCdrSummary().getRecordsCount(), request.getCdrSummary().getChecksum()));
        }


        LOGGER.info("Phase 2 - sendNotificationRequest");
        boolean notificationSuccess = sendNotificationRequest(new CdrFileProcessedNotification(status.getValue(),
                request.getFileName(), failure));
        boolean success = (status == FileProcessedStatus.FILE_PROCESSED_SUCCESSFULLY);
        String message = String.format("%s %s notification to IMI",
                notificationSuccess ? "Successfully sent" : "Error sending", success ? "success" : "failure");

        fileAuditRecordDataService.create(new FileAuditRecord(FileType.CDR_DETAIL_FILE,
                request.getCdrDetail().getCdrFile(), notificationSuccess, message,
                request.getCdrDetail().getRecordsCount(), request.getCdrDetail().getChecksum()));
        fileAuditRecordDataService.create(new FileAuditRecord(FileType.CDR_SUMMARY_FILE,
                request.getCdrSummary().getCdrFile(), notificationSuccess, message,
                request.getCdrSummary().getRecordsCount(), request.getCdrSummary().getChecksum()));


        //
        // Distribute Phase 3 & 4 & 5
        //

        // Delete old IMI CSR & IMI CDR & KK CSR
        LOGGER.info("Phase 2 - Sending Phase 3 event");
        sendPhaseEvent(CDR_PHASE_3, request);

        // Save CDRs
        LOGGER.info("Phase 2 - Sending Phase 4 event");
        sendPhaseEvent(CDR_PHASE_4, request);

        // Send CSRs for processing
        LOGGER.info("Phase 2 - Sending Phase 5 event");
        sendPhaseEvent(CDR_PHASE_5, request);

        LOGGER.info("Phase 2 - End");

        return returnedErrors;
    }


    // Phase 3:  Deletes old IMI CSR & IMI CDR & KK CSR
    @MotechListener(subjects = { CDR_PHASE_3 })
    @Transactional
    public void cdrProcessPhase3(MotechEvent event) {

        Timer timer = new Timer();
        LOGGER.info("Phase 3 - Start");
        LOGGER.info("Phase 3 - cleanOldCallRecords");
        cleanOldCallRecords();
        LOGGER.info("Phase 3 - End {}", timer.time());

    }


    // Phase 4:  Save CDRs for reporting
    @MotechListener(subjects = { CDR_PHASE_4 })
    @Transactional
    public void cdrProcessPhase4(MotechEvent event) {

        Timer timer = new Timer();
        LOGGER.info("Phase 4 - Start");

        CdrFileNotificationRequest request = requestFromParams(event.getParameters());

        // Copy detail file, if needed
        LOGGER.info("Phase 4 - copying CDR");
        File cdrFile = new File(localCdrDir(), request.getCdrDetail().getCdrFile());
        String cdrFileName = cdrFile.getName();
        try {
            copyFile(cdrFile);
        } catch (ExecException e) {
            String error = String.format("Error copying CDR file %s: %s", cdrFileName, e.getMessage());
            LOGGER.error(error);
            alertService.create(cdrFileName, COPY_ERROR, error, AlertType.CRITICAL, AlertStatus.NEW, 0, null);
            return;
        }

        LOGGER.info("Phase 4 - saveDetailRecords");
        saveDetailRecords(cdrFile);

        LOGGER.info("Phase 4 - End {}", timer.time());
    }


    // Phase 5: Sends CSR rows for processing on any node
    @MotechListener(subjects = { CDR_PHASE_5 })
    @Transactional
    public void cdrProcessPhase5(MotechEvent event) {

        Timer timer = new Timer();
        LOGGER.info("Phase 5 - Start");

        CdrFileNotificationRequest request = requestFromParams(event.getParameters());

        File csrFile = new File(localCdrDir(), request.getCdrSummary().getCdrFile());
        String csrFileName = csrFile.getName();

        // Copy summary file, if needed
        LOGGER.info("Phase 5 - copying CSR");
        try {
            copyFile(csrFile);
        } catch (ExecException e) {
            String error = String.format("Error copying CDR file %s: %s", csrFileName, e.getMessage());
            LOGGER.error(error);
            alertService.create(csrFileName, COPY_ERROR, error, AlertType.CRITICAL, AlertStatus.NEW, 0, null);
            return;
        }

        eventRelay.broadcastEventMessage(new MotechEvent(CSR_VERIFIER_CACHE_EVICT_MESSAGE));

        LOGGER.info("Phase 5 - processCsrs");
        processCsrs(csrFile, request.getCdrSummary().getRecordsCount());

        LOGGER.info("Phase 5 - End {}", timer.time());
    }


    @Override
    @MotechListener(subjects = { CDR_CSR_CLEANUP_SUBJECT })
    @Transactional
    public void cleanOldCallRecords() {
        LOGGER.info("cleanOldCallRecords() called");
        int cdrDuration = MIN_CALL_DATA_RETENTION_DURATION_IN_DAYS;

        try {
            int durationFromConfig = Integer.parseInt(settingsFacade.getProperty(CDR_CSR_RETENTION_DURATION));
            if (durationFromConfig < cdrDuration) {
                LOGGER.debug(String.format("Discarding retention property from config since it is less than MIN: %d",
                        cdrDuration));
            } else {
                cdrDuration = durationFromConfig;
                LOGGER.debug(String.format("Using retention property from config: %d", cdrDuration));
            }
        } catch (NumberFormatException ne) {
            LOGGER.debug(String.format("Unable to get property from config: %s", CDR_CSR_RETENTION_DURATION));
        }

        LOGGER.debug(String.format(LOG_TEMPLATE, callDetailRecordDataService.count(), CDR_TABLE_NAME));
        LOGGER.debug(String.format(LOG_TEMPLATE, callSummaryRecordDataService.count(), CSR_TABLE_NAME));

        deleteRecords(cdrDuration, true);
        deleteRecords(cdrDuration, false);

        LOGGER.debug(String.format(LOG_TEMPLATE, callDetailRecordDataService.count(), CDR_TABLE_NAME));
        LOGGER.debug(String.format(LOG_TEMPLATE, callSummaryRecordDataService.count(), CSR_TABLE_NAME));

        callRetryService.deleteOldRetryRecords(cdrDuration);

    }

    /**
     * Helper to clean out the CDR table with the given retention policy
     * @param retentionInDays min days to keep CDR for
     * @param cdrTable if true, delete the cdr table, otherwise delete the csr table
     */
    private void deleteRecords(final int retentionInDays, final boolean cdrTable) {
        @SuppressWarnings("unchecked")
        SqlQueryExecution<Long> queryExecution = new SqlQueryExecution<Long>() {

            @Override
            public String getSqlQuery() {
                String query = cdrTable ? DELETE_CDR_TABLE : DELETE_CSR_TABLE;
                LOGGER.debug("SQL QUERY: {}", query);
                return query;
            }

            @Override
            public Long execute(Query query) {

                Map params = new HashMap();
                params.put("interval", retentionInDays);
                return (Long) query.executeWithMap(params);
            }
        };

        // FYI: doesn't matter what data service we use since it is just used as a vehicle to execute the custom query
        Long rowCount = callDetailRecordDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("Deleted {} {} from the {} table", rowCount, rowCount == 1L ? "row" : "rows", cdrTable ? "CDR" : "CSR");

        // evict caches for the changes to be read again
        if (cdrTable) {
            callDetailRecordDataService.evictEntityCache(false);
        } else {
            callSummaryRecordDataService.evictEntityCache(false);
        }
    }
}

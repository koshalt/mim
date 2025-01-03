package org.motechproject.nms.imi.service.impl;


import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.datanucleus.store.rdbms.query.ForwardQueryResult;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.motechproject.alerts.contract.AlertService;
import org.motechproject.alerts.domain.AlertStatus;
import org.motechproject.alerts.domain.AlertType;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.annotations.MotechListener;
import org.motechproject.mds.query.SqlQueryExecution;
import org.motechproject.nms.imi.domain.*;
import org.motechproject.nms.imi.exception.ExecException;
import org.motechproject.nms.imi.exception.InternalException;
import org.motechproject.nms.imi.repository.FileAuditRecordDataService;
import org.motechproject.nms.imi.service.TargetFileService;
import org.motechproject.nms.imi.service.contract.TargetFileNotification;
import org.motechproject.nms.imi.web.contract.FileProcessedStatusRequest;
import org.motechproject.nms.kilkari.domain.*;
import org.motechproject.nms.kilkari.domain.WhatsAppOptSMS;
import org.motechproject.nms.kilkari.dto.SubscriptionDto;
import org.motechproject.nms.kilkari.repository.WhatsAppOptSMSDataService;
import org.motechproject.nms.kilkari.service.CallRetryService;
import org.motechproject.nms.kilkari.service.SubscriptionService;
import org.motechproject.nms.kilkari.service.SubscriptionTimeSlotService;
import org.motechproject.nms.kilkari.utils.KilkariConstants;
import org.motechproject.nms.props.domain.DayOfTheWeek;
import org.motechproject.nms.props.domain.RequestId;
import org.motechproject.scheduler.contract.RepeatingSchedulableJob;
import org.motechproject.scheduler.service.MotechSchedulerService;
import org.motechproject.server.config.SettingsFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.jdo.Query;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.*;

@Service("targetFileService")
public class TargetFileServiceImpl implements TargetFileService {
    private static final String LOCAL_OBD_DIR = "imi.local_obd_dir";
    private static final String LOCAL_OBDTEST_DIR = "imi.local_obdtest_dir";
    private static final String LOCAL_WHATSAPP_SMS_OBD_DIR = "imi.local_whatsapp_sms_obd_dir";
    private static final String LOCAL_OBD_DIR_WHATSAPP = "imi.local_obd_dir_whatsapp";
    private static final String TARGET_FILE_TIME = "imi.target_file_time";
    private static final String WHATSAPP_SMS_TARGET_FILE_TIME = "imi.whatsapp_sms_target_file_time";
    private static final String WHATSAPP_TARGET_FILE_TIME = "imi.whatsapp_target_file_time";
    private static final String MAX_QUERY_BLOCK = "imi.max_query_block";
    private static final String TARGET_FILE_SEC_INTERVAL = "imi.target_file_sec_interval";
    private static final String TARGET_FILE_NOTIFICATION_URL = "imi.target_file_notification_url";
    private static final String TARGET_FILE_NOTIFICATION_URL_WHATSAPP = "imi.target_file_notification_url_whatsapp";
    private static final String WHATSAPP_SMS_TARGET_FILE_NOTIFICATION_URL = "imi.whatsApp_sms_target_file_notification_url";
    public static final String HUNGAMA_TARGET_FILE_NOTIFICATION_URL = "imi.hungama.target_file_notification_url";
    private static final String TARGET_FILE_CALL_FLOW_URL = "imi.target_file_call_flow_url";
    private static final String TARGET_FILE_CALL_FLOW_URL_WHATSAPP = "imi.target_file_call_flow_url_wp";
    private static final String CONTENT_FILE_NAME_WHATSAPP_WELCOME_MESSAGE = "imi.content_file_name_whatsapp_welcome_message";
    private static final String CONTENT_FILE_NAME_WHATSAPP_DEACTIVATION_MESSAGE = "imi.content_file_name_whatsapp_deactivation_message";
    private static final String DEACTIVATION_REASONS_FOR_WHATSAPP = "imi.deactivation_reasons_for_whatsapp";
    private static final String NORMAL_PRIORITY = "0";
    private static final String HIGH_PRIORITY = "1";
    private static final int PARTITION_SIZE = 50000;
    private static final String IMI_FRESH_CHECK_DND = "imi.fresh_check_dnd";
    private static final String IMI_FRESH_NO_CHECK_DND = "imi.fresh_no_check_dnd";
    private static final String IMI_RETRY_CHECK_DND = "imi.retry_check_dnd";
    private static final String IMI_RETRY_NO_CHECK_DND = "imi.retry_no_check_dnd";
    private static final String SPECIFIC_STATE_ID = "imi.hungama.stateId";
    private static final String IMI_FRESH_CHECK_DND_JH = "imi.fresh_check_dnd_jh";
    private static final String IMI_FRESH_NO_CHECK_DND_JH = "imi.fresh_no_check_dnd_jh";
    private static final String IMI_RETRY_CHECK_DND_JH = "imi.retry_check_dnd_jh";
    private static final String IMI_RETRY_NO_CHECK_DND_JH = "imi.retry_no_check_dnd_jh";
    private static final String generateJhFile = "imi.obd_bifurcate";
    private static final String Jh = "JH";
    private static final String specific_non_Jh = "SPECIFIC_NON-JH";
    private static final String non_Jh = "NON-JH";

    private static final int PROGRESS_INTERVAL = 10000;
    private static final int WEEKS_1 = 72;
    private static final int WEEKS_2 = 48;

    private static final String GENERATE_TARGET_FILE_EVENT = "nms.obd.generate_target_file";
    private static final String GENERATE_WHATSAPP_SMS_TARGET_FILE_EVENT = "nms.obd.generate_whatsapp_sms_target_file";
    private static final String GENERATE_TARGET_FILE_EVENT_WHATSAPP = "nms.obd.generate_whatsapp_target_file";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormat.forPattern("yyyyMMddHHmmss");
    private static final String DEFAULT_CIRCLE = "99";  // https://applab.atlassian.net/browse/NIP-64
    public static final String WROTE = "Wrote {}";
    private static final int DAYS_IN_WEEK = 7;

    private SettingsFacade settingsFacade;
    private MotechSchedulerService schedulerService;
    private AlertService alertService;
    private SubscriptionService subscriptionService;
    private SubscriptionTimeSlotService subscriptionTimeSlotService;
    private CallRetryService callRetryService;
    private FileAuditRecordDataService fileAuditRecordDataService;
    private WhatsAppOptSMSDataService whatsAppOptSMSDataService;
    private static String freshCheckDND;
    private static String freshNoCheckDND;
    private static String retryCheckDND;
    private static String retryNoCheckDND;

    private static String freshCheckDNDJh;
    private static String freshNoCheckDNDJh;
    private static String retryCheckDNDJh;
    private static String retryNoCheckDNDJh;

    private static final Logger LOGGER = LoggerFactory.getLogger(TargetFileServiceImpl.class);

    /**
     * Use the MOTECH scheduler to setup a repeating job
     * The job will start today at the time stored in imi.target_file_time in imi.properties
     * It will repeat every imi.target_file_sec_interval seconds (default value is a day)
     */
    private void scheduleTargetFileGeneration() {
        //Calculate today's fire time
        DateTimeFormatter fmt = DateTimeFormat.forPattern("H:m");
        String timeProp = settingsFacade.getProperty(TARGET_FILE_TIME);
        DateTime time = fmt.parseDateTime(timeProp);
        DateTime today = DateTime.now()                     // This means today's date...
                .withHourOfDay(time.getHourOfDay())         // ...at the hour...
                .withMinuteOfHour(time.getMinuteOfHour())   // ...and minute specified in imi.properties
                .withSecondOfMinute(0)
                .withMillisOfSecond(0);

        //Second interval between events
        String intervalProp = settingsFacade.getProperty(TARGET_FILE_SEC_INTERVAL);
        Integer secInterval = Integer.parseInt(intervalProp);

        if (secInterval < 1) {
            LOGGER.warn("{} is set to less than 1 second, no repeating schedule will be set to automatically generate " +
                    "target files!", TARGET_FILE_SEC_INTERVAL);
            return;
        }

        LOGGER.debug(String.format("The %s message will be sent every %ss starting %s", GENERATE_TARGET_FILE_EVENT,
                secInterval.toString(), today.toString()));

        //Schedule repeating job
        MotechEvent event = new MotechEvent(GENERATE_TARGET_FILE_EVENT);
        RepeatingSchedulableJob job = new RepeatingSchedulableJob(event,          //MOTECH event
                null,           //repeatCount, null means infinity
                secInterval,    //repeatIntervalInSeconds
                today.toDate(), //startTime
                null,           //endTime, null means no end time
                true);          //ignorePastFiresAtStart

        schedulerService.safeScheduleRepeatingJob(job);
    }

    private void scheduleTargetFileGenerationWhatsApp() {
        //Calculate today's fire time
        LOGGER.debug("test - scheduleTargetFileGenerationWA ");
        DateTimeFormatter fmt = DateTimeFormat.forPattern("H:m");
        String timeProp = settingsFacade.getProperty(WHATSAPP_TARGET_FILE_TIME);
        DateTime time = fmt.parseDateTime(timeProp);
        DateTime today = DateTime.now()                     // This means today's date...
                .withHourOfDay(time.getHourOfDay())         // ...at the hour...
                .withMinuteOfHour(time.getMinuteOfHour())   // ...and minute specified in imi.properties
                .withSecondOfMinute(0)
                .withMillisOfSecond(0);

        //Second interval between events
        String intervalProp = settingsFacade.getProperty(TARGET_FILE_SEC_INTERVAL);
        Integer secInterval = Integer.parseInt(intervalProp);

        if (secInterval < 1) {
            LOGGER.warn("{} is set to less than 1 second, no repeating schedule will be set to automatically generate " +
                    "target files!", TARGET_FILE_SEC_INTERVAL);
            return;
        }

        LOGGER.debug(String.format("The %s message will be sent every %ss starting %s", GENERATE_TARGET_FILE_EVENT_WHATSAPP,
                secInterval.toString(), today.toString()));

        //Schedule repeating job
        LOGGER.debug("test - Schedule repeating job ");
        MotechEvent event = new MotechEvent(GENERATE_TARGET_FILE_EVENT_WHATSAPP);
        RepeatingSchedulableJob job = new RepeatingSchedulableJob(event,          //MOTECH event
                null,           //repeatCount, null means infinity
                secInterval,    //repeatIntervalInSeconds
                today.toDate(), //startTime
                null,           //endTime, null means no end time
                true);          //ignorePastFiresAtStart

        schedulerService.safeScheduleRepeatingJob(job);
    }

    @Autowired
    public TargetFileServiceImpl(@Qualifier("imiSettings") SettingsFacade settingsFacade,
                                 MotechSchedulerService schedulerService, AlertService alertService,
                                 SubscriptionService subscriptionService,
                                 CallRetryService callRetryService,
                                 FileAuditRecordDataService fileAuditRecordDataService,
                                 WhatsAppOptSMSDataService whatsAppOptSMSDataService,
                                 SubscriptionTimeSlotService subscriptionTimeSlotService) {
        this.schedulerService = schedulerService;
        this.settingsFacade = settingsFacade;
        this.alertService = alertService;
        this.subscriptionService = subscriptionService;
        this.callRetryService = callRetryService;
        this.fileAuditRecordDataService = fileAuditRecordDataService;
        this.whatsAppOptSMSDataService = whatsAppOptSMSDataService;
        this.subscriptionTimeSlotService = subscriptionTimeSlotService;

        scheduleTargetFileGeneration();
        scheduleWhatsAppTargetFileGeneration();

        freshCheckDND = settingsFacade.getProperty(IMI_FRESH_CHECK_DND);
        freshNoCheckDND = settingsFacade.getProperty(IMI_FRESH_NO_CHECK_DND);
        retryCheckDND = settingsFacade.getProperty(IMI_RETRY_CHECK_DND);
        retryNoCheckDND = settingsFacade.getProperty(IMI_RETRY_NO_CHECK_DND);

        freshCheckDNDJh = settingsFacade.getProperty(IMI_FRESH_CHECK_DND_JH);
        freshNoCheckDNDJh = settingsFacade.getProperty(IMI_FRESH_NO_CHECK_DND_JH);
        retryCheckDNDJh = settingsFacade.getProperty(IMI_RETRY_CHECK_DND_JH);
        retryNoCheckDNDJh = settingsFacade.getProperty(IMI_RETRY_NO_CHECK_DND_JH);
    }

    @Autowired
    public void TargetFileServiceImplWhatsApp(@Qualifier("imiSettings") SettingsFacade settingsFacade,
                                              MotechSchedulerService schedulerService, AlertService alertService,
                                              SubscriptionService subscriptionService,
                                              CallRetryService callRetryService,
                                              FileAuditRecordDataService fileAuditRecordDataService) {
        this.schedulerService = schedulerService;
        this.settingsFacade = settingsFacade;
        this.alertService = alertService;
        this.subscriptionService = subscriptionService;
        this.callRetryService = callRetryService;
        this.fileAuditRecordDataService = fileAuditRecordDataService;

        LOGGER.debug("test - TargetFileServiceImplWA ");
        scheduleTargetFileGenerationWhatsApp();

        freshCheckDND = settingsFacade.getProperty(IMI_FRESH_CHECK_DND);
        freshNoCheckDND = settingsFacade.getProperty(IMI_FRESH_NO_CHECK_DND);
        retryCheckDND = settingsFacade.getProperty(IMI_RETRY_CHECK_DND);
        retryNoCheckDND = settingsFacade.getProperty(IMI_RETRY_NO_CHECK_DND);

        freshCheckDNDJh = settingsFacade.getProperty(IMI_FRESH_CHECK_DND_JH);
        freshNoCheckDNDJh = settingsFacade.getProperty(IMI_FRESH_NO_CHECK_DND_JH);
        retryCheckDNDJh = settingsFacade.getProperty(IMI_RETRY_CHECK_DND_JH);
        retryNoCheckDNDJh = settingsFacade.getProperty(IMI_RETRY_NO_CHECK_DND_JH);
    }
    public String serviceIdFromOrigin(boolean freshCall, SubscriptionOrigin origin) {

        if (origin == SubscriptionOrigin.MCTS_IMPORT || origin == SubscriptionOrigin.RCH_IMPORT) {
            return freshCall ? freshCheckDND : retryCheckDND;
        }

        if (origin == SubscriptionOrigin.IVR) {
            return freshCall ? freshNoCheckDND : retryNoCheckDND;
        }

        throw new IllegalStateException("Unexpected SubscriptionOrigin value");
    }

    public String serviceIdFromOrigins(boolean freshCall, String origin) {

        if (origin.equalsIgnoreCase("MCTS_IMPORT") || origin.equalsIgnoreCase("RCH_IMPORT")) {
            return freshCall ? freshCheckDND : retryCheckDND;
        }

        if (origin.equalsIgnoreCase("IVR")) {
            return freshCall ? freshNoCheckDND : retryNoCheckDND;
        }

        throw new IllegalStateException("Unexpected SubscriptionOrigin value");
    }

    public String serviceIdFromOriginJh(boolean freshCall, SubscriptionOrigin origin) {

        if (origin == SubscriptionOrigin.MCTS_IMPORT || origin == SubscriptionOrigin.RCH_IMPORT) {
            return freshCall ? freshCheckDNDJh : retryCheckDNDJh;
        }

        if (origin == SubscriptionOrigin.IVR) {
            return freshCall ? freshNoCheckDNDJh : retryNoCheckDNDJh;
        }

        throw new IllegalStateException("Unexpected SubscriptionOrigin value");
    }

    public String serviceIdFromOriginsJh(boolean freshCall, String origin) {

        if (origin.equalsIgnoreCase("MCTS_IMPORT") || origin.equalsIgnoreCase("RCH_IMPORT")) {
            return freshCall ? freshCheckDNDJh : retryCheckDNDJh;
        }

        if (origin.equalsIgnoreCase("IVR")) {
            return freshCall ? freshNoCheckDNDJh : retryNoCheckDNDJh;
        }

        throw new IllegalStateException("Unexpected SubscriptionOrigin value");
    }

    private void scheduleWhatsAppTargetFileGeneration() {
        //Calculate today's fire time
        DateTimeFormatter fmt = DateTimeFormat.forPattern("H:m");
        String timeProp = settingsFacade.getProperty(WHATSAPP_SMS_TARGET_FILE_TIME);
        DateTime time = fmt.parseDateTime(timeProp);
        DateTime today = DateTime.now()                     // This means today's date...
                .withHourOfDay(time.getHourOfDay())         // ...at the hour...
                .withMinuteOfHour(time.getMinuteOfHour())   // ...and minute specified in imi.properties
                .withSecondOfMinute(0)
                .withMillisOfSecond(0);

        //Second interval between events
        String intervalProp = settingsFacade.getProperty(TARGET_FILE_SEC_INTERVAL);
        Integer secInterval = Integer.parseInt(intervalProp);

        if (secInterval < 1) {
            LOGGER.warn("{} is set to less than 1 second, no repeating schedule will be set to automatically generate " +
                    "target files!", TARGET_FILE_SEC_INTERVAL);
            return;
        }

        LOGGER.debug(String.format("The %s message will be sent every %ss starting %s", GENERATE_WHATSAPP_SMS_TARGET_FILE_EVENT,
                secInterval.toString(), today.toString()));

        //Schedule repeating job
        MotechEvent event = new MotechEvent(GENERATE_WHATSAPP_SMS_TARGET_FILE_EVENT);
        RepeatingSchedulableJob job = new RepeatingSchedulableJob(event,          //MOTECH event
                null,           //repeatCount, null means infinity
                secInterval,    //repeatIntervalInSeconds
                today.toDate(), //startTime
                null,           //endTime, null means no end time
                true);          //ignorePastFiresAtStart

        schedulerService.safeScheduleRepeatingJob(job);
    }



    private String targetFileName(String timestamp) {
        return String.format("OBD_NMS_%s.csv", timestamp);
    }

    private String wpTargetFileName(String timestamp) {
        return String.format("WP_OBD_NMS_%s.csv", timestamp);
    }
    // Helper method that makes the code a bit cleaner
    private void alert(String id, String name, String description) {
        alertService.create(id, name, description, AlertType.CRITICAL, AlertStatus.NEW, 0, null);
    }

    private String whatsAppSMStargetFileName(String timestamp) {
        return String.format("OBD_NMS_WASMS_%s.csv", timestamp);
    }


    private void writeHeader(OutputStreamWriter writer) throws IOException {
        /*
         * #1 RequestId
         *
         */
        writer.write("RequestId");
        writer.write(",");

        /*
         * #2 ServiceId
         *
         */
        writer.write("ServiceId");
        writer.write(",");

        /*
         * #3 Msisdn
         *
         */
        writer.write("Msisdn");
        writer.write(",");

        /*
         * #4 Cli
         *
         */
        writer.write("Cli");
        writer.write(",");

        /*
         * #5 Priority
         *
         */
        writer.write("Priority");
        writer.write(",");

        /*
         * #6 CallFlowURL
         *
         */
        writer.write("CallFlowURL");
        writer.write(",");

        /*
         * #7 ContentFileName
         *
         */
        writer.write("ContentFileName");
        writer.write(",");

        /*
         * #8 WeekId
         *
         */
        writer.write("WeekId");
        writer.write(",");

        /*
         * #9 LanguageLocationCode
         *
         */
        writer.write("LanguageLocationCode");
        writer.write(",");

        /*
         * #10 Circle
         *
         */
        writer.write("Circle");
        writer.write(",");

        /*
         * #11 subscription mode
         *
         */
        writer.write("SubscriptionOrigin");
        writer.write(",");
        /*
         * # WhatsApp OPT-IN Call need to be played at welcome prompt
         */
        writer.write("opt_in_call_eligibility");
        writer.write(",");

        writer.write("time_stamp1");
        writer.write(",");

        writer.write("time_stamp2");
        writer.write(",");

        writer.write("time_stamp3");

        writer.write("\n");
    }

    private File wpLocalObdDir() {
        return new File(settingsFacade.getProperty(LOCAL_OBD_DIR_WHATSAPP));
    }

    private void writeSubscriptionRow(String requestId, String serviceId, // NO CHECKSTYLE More than 7 parameters
                                      String msisdn, String priority, String callFlowUrl, String contentFileName,
                                      String weekId, String languageLocationCode, String circle,
                                      String subscriptionOrigin, OutputStreamWriter writer, Boolean needsWelcomeOptInForWP,
                                      String timestamp1, String timestamp2, String timestamp3)throws IOException {
        /*
         * #1 RequestId
         *
         * A unique Request id for each obd record
         */
        writer.write(requestId);
        writer.write(",");

        /*
         * #2 ServiceId
         *
         * Unique Id provided by IMImobile for a particular service
         */
        writer.write(serviceId);
        writer.write(",");

        /*
         * #3 Msisdn
         *
         * 10 digit number to be dialed out
         */
        writer.write(msisdn);
        writer.write(",");

        /*
         * #4 Cli
         *
         * 10 Digit number to be displayed as CLI for the call. If left blank, the default CLI of the service
         * shall be picked up.
         */
        writer.write(""); // No idea why/what that field is: let's write nothing
        writer.write(",");

        /*
         * #5 Priority
         *
         * Specifies the priority with which the call is to be made. By default value is 0.
         * Possible Values: 0-Default, 1-Medium Priority, 2-High Priority
         */
        writer.write(priority); //todo: priorities, what do we want to do?
        writer.write(",");

        /*
         * #6 CallFlowURL
         *
         * The URL of the VXML flow. If unspecified, default VXML URL specified for the service shall be picked up
         */
        writer.write(callFlowUrl);
        writer.write(",");

        /*
         * #7 ContentFileName
         *
         * Content file to be played
         */
        writer.write(contentFileName);
        writer.write(",");

        /*
         * #8 WeekId
         *
         * Week id of the messaged delivered in OBD
         */
        writer.write(weekId);
        writer.write(",");

        /*
         * #9 LanguageLocationCode
         *
         * To identify the language
         */
        writer.write(languageLocationCode);
        writer.write(",");

        /*
         * #10 Circle
         *
         * Circle of the beneficiary.
         */
        if (circle != null && !circle.isEmpty()) {
            writer.write(circle);
        } else {
            writer.write(DEFAULT_CIRCLE);
        }
        writer.write(",");

        /*
         * #11 subscription mode
         *
         * I for IVR origin, M for MCTS origin
         */
        writer.write(subscriptionOrigin);

        writer.write(",");

        writer.write((((contentFileName.equals("w1_1.wav") || contentFileName.equals("opt_in.wav"))  && needsWelcomeOptInForWP != null) ? needsWelcomeOptInForWP : Boolean.FALSE).toString());

        writer.write(",");

        if (timestamp1 != null) {
            writer.write(timestamp1);
        }
        writer.write(",");

        /*
         * #14 Extra Column 2
         *
         * Additional data from SubscriptionTimeSlot
         */
        if (timestamp2 != null) {
            writer.write(timestamp2);
        }
        writer.write(",");

        /*
         * #15 Extra Column 3
         *
         * Additional data from SubscriptionTimeSlot
         */
        if (timestamp3 != null) {
            writer.write(timestamp3);
        }

        writer.write("\n");
    }

    private HashMap<String, Integer> generateFreshCallsApi(DateTime timestamp, int maxQueryBlock, String callFlowUrl,
                                                           HashMap<String, OutputStreamWriter> wr, List<String> subscriptionIdsJh, boolean split) throws IOException {

        LOGGER.info("generateFreshCallsObd({})", timestamp);

        int skippedrecords = 0;

        DayOfTheWeek dow = DayOfTheWeek.fromDateTime(timestamp);
        HashMap<String, Integer> recordsMap = new HashMap<>();
        int recordsWritten = 0;
        int recordsWrittenSpecific = 0;
        int recordsWrittenJh = 0;
        Long offset = 0L;
        int weeks;

        List<Long> specificStateList = getSpecificStateList();

        List<SubscriptionTimeSlot> timeSlots = null;

        try {
            timeSlots = subscriptionTimeSlotService.findAllTimeSlots();

        }catch (Exception e){
            LOGGER.error("Error finding time slots for subscriptions", e);
        }
        if(timeSlots==null){
            LOGGER.warn("No time slots available, proceeding without time slot data.");
        }


        Map<String, SubscriptionTimeSlot> timeSlotMap = new HashMap<>();
        if(timeSlots!=null){
            for (SubscriptionTimeSlot timeSlot : timeSlots) {
                timeSlotMap.put(timeSlot.getSubscriptionId(), timeSlot);
            }}



    List<SubscriptionDto> subscriptions = findAllSubscriptionsForDayApi(dow, maxQueryBlock);

            LOGGER.info("Subs_block_size" + subscriptions.size());


            for (SubscriptionDto subscription : subscriptions) {
                offset = subscription.getId();

//                Subscriber subscriber = subscription.getSubscriber();
                Long stateID = subscription.getStateId();

                RequestId requestId = new RequestId(subscription.getSubscriptionId(), TIME_FORMATTER.print(timestamp));

                try {
//                    SubscriptionPack pack = subscription.getSubscriptionPack();
                    int daysIntoPack = Days.daysBetween(new DateTime(subscription.getStartDate()), timestamp).getDays();
//                    LOGGER.info("this is the weeks: {}",pack.getWeeks());
                    if(subscription.getSubscriptionPackIdOid()==1){
                        weeks =WEEKS_1;
                    }else {
                        weeks=WEEKS_2;
                    }
                    if (daysIntoPack == weeks * 7) {
                        //
                        // Do not add subscriptions on their last day to the fresh call list since we
                        // will try to fetch message for current +1 week, which wouldn't exist
                        // See https://applab.atlassian.net/browse/NMS-301
                        //
                        /*LOGGER.debug("Ignoring last day for subscription {} from fresh calls.",
                                subscription.getSubscriptionId());*/
                        skippedrecords++;
                        continue;
                    }

                    SubscriptionPackMessage msg = subscription.nextScheduledMessage(timestamp);
//                    LOGGER.info("this is the mesg we are getting : {}",msg);

                    SubscriptionTimeSlot timeSlot = timeSlotMap.get(subscription.getSubscriptionId());
                    String timeStamp1 = timeSlot != null ? timeSlot.getTimeStamp1() != null ? timeSlot.getTimeStamp1().toString() : "" : "";
                    String timeStamp2 = timeSlot != null ? timeSlot.getTimeStamp2() != null ? timeSlot.getTimeStamp2().toString() : "" : "";
                    String timeStamp3 = timeSlot != null ? timeSlot.getTimeStamp3() != null ? timeSlot.getTimeStamp3().toString() : "" : "";


//                    LOGGER.info("this is the calling number: {}",subscriber.getCallingNumber().toString());

                    if(split && subscriptionIdsJh.contains(subscription.getSubscriptionId())) {
                            writeSubscriptionRow(
                                    requestId.toString(),
                                    serviceIdFromOriginsJh(true, subscription.getOrigin()),
                                    subscription.getCallingNumber(),
                                    HIGH_PRIORITY, //todo: how do we choose a priority?
                                    callFlowUrl,
                                    msg.getMessageFileName(),
                                    msg.getWeekId(),
                      // we are happy with empty language and circle since they are optional
                                    subscription.getLanguageCode(),
                                    subscription.getCircleName(),
                                    getCode(subscription.getOrigin()),
                                    wr.get(Jh),
                                    subscription.isNeedsWelcomeOptInForWp(),
                                    timeStamp1,
                                    timeStamp2,
                                    timeStamp3);
                            recordsWrittenJh++;
                        }
                    else if(specificStateList.contains(stateID)){
                        recordsWrittenSpecific = getRecordsWrittenApi(callFlowUrl, wr, recordsWrittenSpecific, subscription,requestId,msg,  specific_non_Jh,timeStamp1,timeStamp2,timeStamp3);
                    }
                    else {
                        recordsWritten = getRecordsWrittenApi(callFlowUrl, wr, recordsWritten, subscription, requestId, msg, non_Jh,timeStamp1,timeStamp2,timeStamp3);

                    }
                } catch (IllegalStateException se) {
                    String message = se.toString();
                    alertService.create(subscription.getSubscriptionId(), "IllegalStateException", message,
                            AlertType.HIGH, AlertStatus.NEW, 0, null);
                    LOGGER.error(message,se);
                }
            }



        LOGGER.info(WROTE+non_Jh, recordsWritten);
        recordsMap.put(non_Jh, recordsWritten);
        recordsMap.put(specific_non_Jh, recordsWrittenSpecific);
        if(split){
            LOGGER.info(WROTE+Jh, recordsWrittenJh);
            recordsMap.put(Jh, recordsWrittenJh);
        }
        return recordsMap;
    }

    private HashMap<String, Integer> generateFreshCalls(DateTime timestamp, int maxQueryBlock, String callFlowUrl,
                                                        HashMap<String, OutputStreamWriter> wr, List<String> subscriptionIdsJh, boolean split) throws IOException {

        LOGGER.info("generateFreshCallsObd({})", timestamp);

        int skippedrecords = 0;

        DayOfTheWeek dow = DayOfTheWeek.fromDateTime(timestamp);
        HashMap<String, Integer> recordsMap = new HashMap<>();
        int recordsWritten = 0;
        int recordsWrittenSpecific = 0;
        int recordsWrittenJh = 0;
        Long offset = 0L;
        List<Long> specificStateList = getSpecificStateList();
        do {
            List<Subscription> subscriptions = subscriptionService.findActiveSubscriptionsForDay(dow, offset, maxQueryBlock);
            LOGGER.info("Subs_block_size"  + subscriptions.size());
            if (subscriptions.size() == 0) {
                break;
            }

            List<String> subscriptionIds = new ArrayList<>();
            for (Subscription subscription : subscriptions) {
                subscriptionIds.add(subscription.getSubscriptionId());
            }

            List<SubscriptionTimeSlot> timeSlots = null;

            try {
                timeSlots = subscriptionTimeSlotService.findTimeSlotsForSubscriptionsById(subscriptionIds);

            }catch (Exception e){
                LOGGER.error("Error finding time slots for subscriptions", e);
            }
            if(timeSlots==null){
                continue;
            }


            Map<String, SubscriptionTimeSlot> timeSlotMap = new HashMap<>();
            for (SubscriptionTimeSlot timeSlot : timeSlots) {
                timeSlotMap.put(timeSlot.getSubscriptionId(), timeSlot);
            }

            for (Subscription subscription : subscriptions) {
//                LOGGER.debug("Handling Subscription " + subscription.getId());
                offset = subscription.getId();

                Subscriber subscriber = subscription.getSubscriber();
                Long stateID = subscriber.getMother() == null ? subscriber.getChild().getState().getId() : subscriber.getMother().getState().getId();

                RequestId requestId = new RequestId(subscription.getSubscriptionId(), TIME_FORMATTER.print(timestamp));

                try {
                    SubscriptionPack pack = subscription.getSubscriptionPack();
                    int daysIntoPack = Days.daysBetween(subscription.getStartDate(), timestamp).getDays();
                    if (daysIntoPack == pack.getWeeks() * 7) {
                        //
                        // Do not add subscriptions on their last day to the fresh call list since we
                        // will try to fetch message for current +1 week, which wouldn't exist
                        // See https://applab.atlassian.net/browse/NMS-301
                        //
                        /*LOGGER.debug("Ignoring last day for subscription {} from fresh calls.",
                                subscription.getSubscriptionId());*/
                        skippedrecords++;
                        continue;
                    }

                    SubscriptionPackMessage msg = subscription.nextScheduledMessage(timestamp);

                    SubscriptionTimeSlot timeSlot = timeSlotMap.get(subscription.getSubscriptionId());
                    String timeStamp1 = timeSlot != null ? timeSlot.getTimeStamp1() != null ? timeSlot.getTimeStamp1().toString() : "" : "";
                    String timeStamp2 = timeSlot != null ? timeSlot.getTimeStamp2() != null ? timeSlot.getTimeStamp2().toString() : "" : "";
                    String timeStamp3 = timeSlot != null ? timeSlot.getTimeStamp3() != null ? timeSlot.getTimeStamp3().toString() : "" : "";



                    if(split && subscriptionIdsJh.contains(subscription.getSubscriptionId())) {
                        writeSubscriptionRow(
                                requestId.toString(),
                                serviceIdFromOriginJh(true, subscription.getOrigin()),
                                subscriber.getCallingNumber().toString(),
                                HIGH_PRIORITY, //todo: how do we choose a priority?
                                callFlowUrl,
                                msg.getMessageFileName(),
                                msg.getWeekId(),
                                // we are happy with empty language and circle since they are optional
                                subscriber.getLanguage() == null ? "" : subscriber.getLanguage().getCode(),
                                subscriber.getCircle() == null ? "" : subscriber.getCircle().getName(),
                                subscription.getOrigin().getCode(),
                                wr.get(Jh),
                                subscription.isNeedsWelcomeOptInForWP(),
                                timeStamp1,
                                timeStamp2,
                                timeStamp3);
                        recordsWrittenJh++;
                    }
                    else if(specificStateList.contains(stateID)){
                        recordsWrittenSpecific = getRecordsWritten(callFlowUrl, wr, recordsWrittenSpecific, subscription, subscriber, requestId, msg, specific_non_Jh,timeStamp1,timeStamp2,timeStamp3);
                    }
                    else {
                        recordsWritten = getRecordsWritten(callFlowUrl, wr, recordsWritten, subscription, subscriber, requestId, msg, non_Jh,timeStamp1,timeStamp2,timeStamp3);

                    }
                } catch (IllegalStateException se) {
                    String message = se.toString();
                    alertService.create(subscription.getSubscriptionId(), "IllegalStateException", message,
                            AlertType.HIGH, AlertStatus.NEW, 0, null);
                    LOGGER.error(message,se);
                }
            }

        } while (true);

        LOGGER.info(WROTE+non_Jh, recordsWritten);
        recordsMap.put(non_Jh, recordsWritten);
        recordsMap.put(specific_non_Jh, recordsWrittenSpecific);
        if(split){
            LOGGER.info(WROTE+Jh, recordsWrittenJh);
            recordsMap.put(Jh, recordsWrittenJh);
        }
        return recordsMap;
    }

    private int getRecordsWritten(String callFlowUrl, HashMap<String, OutputStreamWriter> wr, int recordsWrittenJh, Subscription subscription, Subscriber subscriber, RequestId requestId, SubscriptionPackMessage msg, String specific_non_jh,String timeStamp1,String timeStamp2,String timeStamp3) throws IOException {
        writeSubscriptionRow(
                requestId.toString(),
                serviceIdFromOrigin(true, subscription.getOrigin()),
                subscriber.getCallingNumber().toString(),
                NORMAL_PRIORITY, //todo: how do we choose a priority?
                callFlowUrl,
                msg.getMessageFileName(),
                msg.getWeekId(),
                // we are happy with empty language and circle since they are optional
                subscriber.getLanguage() == null ? "" : subscriber.getLanguage().getCode(),
                subscriber.getCircle() == null ? "" : subscriber.getCircle().getName(),
                subscription.getOrigin().getCode(),
                wr.get(specific_non_jh),
                subscription.isNeedsWelcomeOptInForWP(),
                timeStamp1,
                timeStamp2,
                timeStamp3);
        recordsWrittenJh++;
        return recordsWrittenJh;
    }

    public final String getCode(String code) {
        if (code.equalsIgnoreCase("IVR")) {
            return "I";
        } else if (code.equalsIgnoreCase("MCTS_IMPORT") ){
            return "M";
        } else {
            return "R";
        }
    }



    private int getRecordsWrittenApi(String callFlowUrl, HashMap<String, OutputStreamWriter> wr, int recordsWrittenJh, SubscriptionDto subscription,  RequestId requestId, SubscriptionPackMessage msg,  String specific_non_jh,String timeStamp1,String timeStamp2,String timeStamp3) throws IOException {
        writeSubscriptionRow(
                requestId.toString(),
                serviceIdFromOrigins(true, subscription.getOrigin()),
                subscription.getCallingNumber(),
                NORMAL_PRIORITY, //todo: how do we choose a priority?
                callFlowUrl,
                msg.getMessageFileName(),
                msg.getWeekId(),
                // we are happy with empty language and circle since they are optional
                subscription.getLanguageCode(),
                subscription.getCircleName(),
                getCode(subscription.getOrigin()),
                wr.get(specific_non_jh),
                subscription.isNeedsWelcomeOptInForWp(),
                timeStamp1,
                timeStamp2,
                timeStamp3);
        recordsWrittenJh++;
        return recordsWrittenJh;
    }

    private List<SubscriptionDto> findAllSubscriptionsForDayApi(DayOfTheWeek dow, int batchSize) {
        List<SubscriptionDto> allSubscriptions = new ArrayList<>();
        long offset = 0; // Start from the first record
        boolean hasMoreRecords = true;

        while (hasMoreRecords) {
            List<SubscriptionDto> batchSubscriptions = subscriptionService.findActiveSubscriptionsForDayApi(dow, offset, batchSize);

            if (batchSubscriptions.isEmpty()) {
                hasMoreRecords = false; // No more records to fetch
            } else {
                allSubscriptions.addAll(batchSubscriptions);
                offset += batchSize; // Increment offset for the next batch
            }

            LOGGER.info("Fetched {} records so far.", allSubscriptions.size());
        }

        LOGGER.info("Total subscriptions fetched: {}", allSubscriptions.size());
        return allSubscriptions;
    }

    private void writeWhatsAppHeader(OutputStreamWriter writer) throws IOException {
        /*
         * #1 CircleId
         *
         */
        writer.write("CircleId");
        writer.write(",");

        /*
         * #2 ContentFile
         *
         */
        writer.write("ContentFile");
        writer.write(",");

        /*
         * #3 LanguageLocationId
         *
         */
        writer.write("LanguageLocationId");
        writer.write(",");

        /*
         * #4 Msisdn
         *
         */
        writer.write("Msisdn");
        writer.write(",");

        /*
         * #5 RequestId
         *
         */
        writer.write("RequestId");
        writer.write("\n");


    }
    private void writeWhatsAppSMSRow(String circleId, String contentFile, String languageLocationId,
                                     String msisdn, String requestId, OutputStreamWriter writer) throws IOException {
        /*
         * #1 CircleId
         *
         */
        writer.write(circleId);
        writer.write(",");

        /*
         * #2 ContentFile
         *
         */
        writer.write(contentFile);
        writer.write(",");

        /*
         * #3 LanguageLocationId
         *
         */
        writer.write(languageLocationId);
        writer.write(",");

        /*
         * #4 Msisdn
         *
         */
        writer.write(msisdn);
        writer.write(",");


        /*
         * #5 RequestId
         *
         */
        writer.write(requestId);
        writer.write("\n");

    }


    private HashMap<String, Integer> generateRetryCalls(DateTime timestamp, int maxQueryBlock, String callFlowUrl,
                                                        HashMap<String, OutputStreamWriter> wr, List<String> subscriptionIdsJh, boolean split) throws IOException {

        LOGGER.info("generateRetryCallsObd({})", timestamp);
        int count = 0;
        int countJh = 0;
        int countSpecific = 0;
        HashMap<String, Integer> retryCount = new HashMap<>();
        List<Long> specificState = getSpecificStateList();

        for (String writer : wr.keySet()) {
            if (writer.equals(specific_non_Jh)) {
                Long offset = 0L;
                do {
                    List<CallRetry> callRetries = callRetryService.retrieveAllIVR(offset, maxQueryBlock, specificState);

                    LOGGER.info("Call_Retries" + callRetries.size());

                    if (callRetries.size() == 0) {
                        break;
                    }

                    List<String> subscriptionIds = new ArrayList<>();
                    for (CallRetry callRetry : callRetries) {
                        subscriptionIds.add(callRetry.getSubscriptionId());
                    }


                    List<SubscriptionTimeSlot> timeSlots = null;
                    try {
                        timeSlots = subscriptionTimeSlotService.findTimeSlotsForSubscriptionsById(subscriptionIds);
                    } catch (Exception e) {
                        LOGGER.error("Error finding time slots for subscriptions", e);
                    }

                    if (timeSlots == null) {
                        continue;
                    }

                    Map<String, SubscriptionTimeSlot> timeSlotMap = new HashMap<>();
                    for (SubscriptionTimeSlot timeSlot : timeSlots) {
                        timeSlotMap.put(timeSlot.getSubscriptionId(), timeSlot);
                    }


                    for (CallRetry callRetry : callRetries) {
                        offset = callRetry.getId();
                        RequestId requestId = new RequestId(callRetry.getSubscriptionId(), TIME_FORMATTER.print(timestamp));

                        SubscriptionTimeSlot timeSlot = timeSlotMap.get(callRetry.getSubscriptionId());
                        String timeStamp1 = timeSlot != null ? timeSlot.getTimeStamp1() != null ? timeSlot.getTimeStamp1().toString() : "" : "";
                        String timeStamp2 = timeSlot != null ? timeSlot.getTimeStamp2() != null ? timeSlot.getTimeStamp2().toString() : "" : "";
                        // String timeStamp3 = timeSlot != null ? timeSlot.getTimeStamp3() != null ? timeSlot.getTimeStamp3().toString() : "" : "";
                        String timeStamp3 = "";

                        writeSubscriptionRow(
                                requestId.toString(),
                                serviceIdFromOrigin(false, callRetry.getSubscriptionOrigin()),
                                callRetry.getMsisdn().toString(),
                                NORMAL_PRIORITY,
                                callFlowUrl,
                                callRetry.getContentFileName(),
                                callRetry.getWeekId(),
                                callRetry.getLanguageLocationCode(),
                                callRetry.getCircle(),
                                callRetry.getSubscriptionOrigin().getCode(),
                                wr.get(specific_non_Jh),
                                callRetry.isOpt_in_call_eligibility(),
                                timeStamp1,
                                timeStamp2,
                                timeStamp3);
                        countSpecific++;
                    }

                } while (true);
            } else if(writer.equals(non_Jh)){
                Long offset = 0L;
                do {
                    // All calls are rescheduled for the next day which means that we should query for all CallRetry records
                    List<CallRetry> callRetries = callRetryService.retrieveAllNonIVR(offset, maxQueryBlock,specificState);
                    LOGGER.info("Call_Retries" + callRetries.size());

                    if (callRetries.size() == 0) {
                        break;
                    }
                    List<String> subscriptionIds = new ArrayList<>();
                    for (CallRetry callRetry : callRetries) {
                        subscriptionIds.add(callRetry.getSubscriptionId());
                    }


                    List<SubscriptionTimeSlot> timeSlots = null;
                    try {
                        timeSlots = subscriptionTimeSlotService.findTimeSlotsForSubscriptionsById(subscriptionIds);
                    } catch (Exception e) {
                        LOGGER.error("Error finding time slots for subscriptions", e);
                    }

                    if (timeSlots == null) {
//                        LOGGER.warn("No time slots found for subscriptions: {}", subscriptionIds);
                        continue;
                    }

                    Map<String, SubscriptionTimeSlot> timeSlotMap = new HashMap<>();
                    for (SubscriptionTimeSlot timeSlot : timeSlots) {
                        timeSlotMap.put(timeSlot.getSubscriptionId(), timeSlot);
                    }


                    for (CallRetry callRetry : callRetries) {
                        offset = callRetry.getId();
                        RequestId requestId = new RequestId(callRetry.getSubscriptionId(), TIME_FORMATTER.print(timestamp));
                        SubscriptionTimeSlot timeSlot = timeSlotMap.get(callRetry.getSubscriptionId());
                        String timeStamp1 = timeSlot != null ? timeSlot.getTimeStamp1() != null ? timeSlot.getTimeStamp1().toString() : "" : "";
                        String timeStamp2 = timeSlot != null ? timeSlot.getTimeStamp2() != null ? timeSlot.getTimeStamp2().toString() : "" : "";
                       // String timeStamp3 = timeSlot != null ? timeSlot.getTimeStamp3() != null ? timeSlot.getTimeStamp3().toString() : "" : "";
                        String timeStamp3 = ""; // Only two slots for retry calls

                        if (split && subscriptionIdsJh.contains(callRetry.getSubscriptionId())) {
                            writeSubscriptionRow(
                                    requestId.toString(),
                                    serviceIdFromOriginJh(false, callRetry.getSubscriptionOrigin()),
                                    callRetry.getMsisdn().toString(),
                                    HIGH_PRIORITY,
                                    callFlowUrl,
                                    callRetry.getContentFileName(),
                                    callRetry.getWeekId(),
                                    callRetry.getLanguageLocationCode(),
                                    callRetry.getCircle(),
                                    callRetry.getSubscriptionOrigin().getCode(),
                                    wr.get(Jh),
                                    callRetry.isOpt_in_call_eligibility(),
                                    timeStamp1,
                                    timeStamp2,
                                    timeStamp3);
                            countJh++;
                        } else {
                            writeSubscriptionRow(
                                    requestId.toString(),
                                    serviceIdFromOrigin(false, callRetry.getSubscriptionOrigin()),
                                    callRetry.getMsisdn().toString(),
                                    NORMAL_PRIORITY,
                                    callFlowUrl,
                                    callRetry.getContentFileName(),
                                    callRetry.getWeekId(),
                                    callRetry.getLanguageLocationCode(),
                                    callRetry.getCircle(),
                                    callRetry.getSubscriptionOrigin().getCode(),
                                    wr.get(non_Jh),
                                    callRetry.isOpt_in_call_eligibility(),
                                    timeStamp1,
                                    timeStamp2,
                                    timeStamp3);
                            count++;
                        }
                    }

                } while (true);
            }
        }
       /* do {
            // All calls are rescheduled for the next day which means that we should query for all CallRetry records
            List<CallRetry> callRetries = callRetryService.retrieveAll(offset, maxQueryBlock);
            LOGGER.info("Call_Retries"+callRetries.size());

            if (callRetries.size() == 0) {
                break;
            }

            for (CallRetry callRetry : callRetries) {
                    offset = callRetry.getId();
                    RequestId requestId = new RequestId(callRetry.getSubscriptionId(), TIME_FORMATTER.print(timestamp));
                    Subscriber subscriber = subscriptionService.getSubscription(callRetry.getSubscriptionId()).getSubscriber();
                    Long stateCode = subscriber.getMother() == null ? subscriber.getChild().getState().getCode() : subscriber.getMother().getState().getCode();
                    if(split && subscriptionIdsJh.contains(callRetry.getSubscriptionId())) {
                        writeSubscriptionRow(
                                requestId.toString(),
                                serviceIdFromOriginJh(false, callRetry.getSubscriptionOrigin()),
                                callRetry.getMsisdn().toString(),
                                HIGH_PRIORITY,
                                callFlowUrl,
                                callRetry.getContentFileName(),
                                callRetry.getWeekId(),
                                callRetry.getLanguageLocationCode(),
                                callRetry.getCircle(),
                                callRetry.getSubscriptionOrigin().getCode(),
                                wr.get(Jh));
                        countJh++;
                    }
                    else if(specificState.contains(stateCode)){
                        writeSubscriptionRow(
                                requestId.toString(),
                                serviceIdFromOrigin(false, callRetry.getSubscriptionOrigin()),
                                callRetry.getMsisdn().toString(),
                                NORMAL_PRIORITY,
                                callFlowUrl,
                                callRetry.getContentFileName(),
                                callRetry.getWeekId(),
                                callRetry.getLanguageLocationCode(),
                                callRetry.getCircle(),
                                callRetry.getSubscriptionOrigin().getCode(),
                                wr.get(specific_non_Jh));
                        countSpecific++;
                    }
                    else {
                        writeSubscriptionRow(
                                requestId.toString(),
                                serviceIdFromOrigin(false, callRetry.getSubscriptionOrigin()),
                                callRetry.getMsisdn().toString(),
                                NORMAL_PRIORITY,
                                callFlowUrl,
                                callRetry.getContentFileName(),
                                callRetry.getWeekId(),
                                callRetry.getLanguageLocationCode(),
                                callRetry.getCircle(),
                                callRetry.getSubscriptionOrigin().getCode(),
                                wr.get(non_Jh));
                        count++;
                    }
                }

        } while (true);*/

        LOGGER.info(WROTE+non_Jh+"Retry", count);
        retryCount.put(non_Jh, count);
        retryCount.put(specific_non_Jh, countSpecific);
        if(split) {
            LOGGER.info(WROTE+Jh+"Retry", countJh);
            retryCount.put(Jh, countJh);
        }

        return retryCount;
    }


    private HashMap<String, Integer> generateRetryCallsApi(DateTime timestamp, int maxQueryBlock, String callFlowUrl,
                                   HashMap<String, OutputStreamWriter> wr, List<String> subscriptionIdsJh, boolean split) throws IOException {

        LOGGER.info("generateRetryCallsObd({})", timestamp);
        int count = 0;
        int countJh = 0;
        int countSpecific = 0;
        HashMap<String, Integer> retryCount = new HashMap<>();
        List<Long> specificState = getSpecificStateList();

        List<SubscriptionTimeSlot> timeSlots = null;
        try {
            timeSlots = subscriptionTimeSlotService.findAllTimeSlots();
        } catch (Exception e) {
            LOGGER.error("Error finding time slots for subscriptions", e);
        }

        if (timeSlots == null) {
            LOGGER.warn("No time slots available, proceeding without time slot data.");
        }

        Map<String, SubscriptionTimeSlot> timeSlotMap = new HashMap<>();
        if(timeSlots!=null){
            for (SubscriptionTimeSlot timeSlot : timeSlots) {
                timeSlotMap.put(timeSlot.getSubscriptionId(), timeSlot);
            }}

        for (String writer : wr.keySet()) {
            if (writer.equals(specific_non_Jh)) {
                Long offset = 0L;
                do {
                    List<CallRetry> callRetries = callRetryService.retrieveAllIVR(offset, maxQueryBlock, specificState);

                    LOGGER.info("Call_Retries" + callRetries.size());

                    if (callRetries.size() == 0) {
                        break;
                    }






                    for (CallRetry callRetry : callRetries) {
                        offset = callRetry.getId();
                        RequestId requestId = new RequestId(callRetry.getSubscriptionId(), TIME_FORMATTER.print(timestamp));

                        SubscriptionTimeSlot timeSlot = timeSlotMap.get(callRetry.getSubscriptionId());
                        String timeStamp1 = timeSlot != null ? timeSlot.getTimeStamp1() != null ? timeSlot.getTimeStamp1().toString() : "" : "";
                        String timeStamp2 = timeSlot != null ? timeSlot.getTimeStamp2() != null ? timeSlot.getTimeStamp2().toString() : "" : "";
                        String timeStamp3 = timeSlot != null ? timeSlot.getTimeStamp3() != null ? timeSlot.getTimeStamp3().toString() : "" : "";

                        writeSubscriptionRow(
                                requestId.toString(),
                                serviceIdFromOrigin(false, callRetry.getSubscriptionOrigin()),
                                callRetry.getMsisdn().toString(),
                                NORMAL_PRIORITY,
                                callFlowUrl,
                                callRetry.getContentFileName(),
                                callRetry.getWeekId(),
                                callRetry.getLanguageLocationCode(),
                                callRetry.getCircle(),
                                callRetry.getSubscriptionOrigin().getCode(),
                                wr.get(specific_non_Jh),
                                callRetry.isOpt_in_call_eligibility(),
                                timeStamp1,
                                timeStamp2,
                                timeStamp3);
                        countSpecific++;
                    }

                } while (true);
            } else if(writer.equals(non_Jh)){
                Long offset = 0L;
                do {
                    // All calls are rescheduled for the next day which means that we should query for all CallRetry records
                    List<CallRetry> callRetries = callRetryService.retrieveAllNonIVR(offset, maxQueryBlock,specificState);
                    LOGGER.info("Call_Retries" + callRetries.size());

                    if (callRetries.size() == 0) {
                        break;
                    }


                    for (CallRetry callRetry : callRetries) {
                        offset = callRetry.getId();
                        RequestId requestId = new RequestId(callRetry.getSubscriptionId(), TIME_FORMATTER.print(timestamp));
                        SubscriptionTimeSlot timeSlot = timeSlotMap.get(callRetry.getSubscriptionId());
                        String timeStamp1 = timeSlot != null ? timeSlot.getTimeStamp1() != null ? timeSlot.getTimeStamp1().toString() : "" : "";
                        String timeStamp2 = timeSlot != null ? timeSlot.getTimeStamp2() != null ? timeSlot.getTimeStamp2().toString() : "" : "";
                        String timeStamp3 = timeSlot != null ? timeSlot.getTimeStamp3() != null ? timeSlot.getTimeStamp3().toString() : "" : "";

                        if (split && subscriptionIdsJh.contains(callRetry.getSubscriptionId())) {
                            writeSubscriptionRow(
                                    requestId.toString(),
                                    serviceIdFromOriginJh(false, callRetry.getSubscriptionOrigin()),
                                    callRetry.getMsisdn().toString(),
                                    HIGH_PRIORITY,
                                    callFlowUrl,
                                    callRetry.getContentFileName(),
                                    callRetry.getWeekId(),
                                    callRetry.getLanguageLocationCode(),
                                    callRetry.getCircle(),
                                    callRetry.getSubscriptionOrigin().getCode(),
                                    wr.get(Jh),
                                    callRetry.isOpt_in_call_eligibility(),
                                    timeStamp1,
                                    timeStamp2,
                                    timeStamp3);
                            countJh++;
                        } else {
                            writeSubscriptionRow(
                                    requestId.toString(),
                                    serviceIdFromOrigin(false, callRetry.getSubscriptionOrigin()),
                                    callRetry.getMsisdn().toString(),
                                    NORMAL_PRIORITY,
                                    callFlowUrl,
                                    callRetry.getContentFileName(),
                                    callRetry.getWeekId(),
                                    callRetry.getLanguageLocationCode(),
                                    callRetry.getCircle(),
                                    callRetry.getSubscriptionOrigin().getCode(),
                                    wr.get(non_Jh),
                                    callRetry.isOpt_in_call_eligibility(),
                                    timeStamp1,
                                    timeStamp2,
                                    timeStamp3);
                            count++;
                        }
                    }

                } while (true);
            }
        }
       /* do {
            // All calls are rescheduled for the next day which means that we should query for all CallRetry records
            List<CallRetry> callRetries = callRetryService.retrieveAll(offset, maxQueryBlock);
            LOGGER.info("Call_Retries"+callRetries.size());

            if (callRetries.size() == 0) {
                break;
            }

            for (CallRetry callRetry : callRetries) {
                    offset = callRetry.getId();
                    RequestId requestId = new RequestId(callRetry.getSubscriptionId(), TIME_FORMATTER.print(timestamp));
                    Subscriber subscriber = subscriptionService.getSubscription(callRetry.getSubscriptionId()).getSubscriber();
                    Long stateCode = subscriber.getMother() == null ? subscriber.getChild().getState().getCode() : subscriber.getMother().getState().getCode();
                    if(split && subscriptionIdsJh.contains(callRetry.getSubscriptionId())) {
                        writeSubscriptionRow(
                                requestId.toString(),
                                serviceIdFromOriginJh(false, callRetry.getSubscriptionOrigin()),
                                callRetry.getMsisdn().toString(),
                                HIGH_PRIORITY,
                                callFlowUrl,
                                callRetry.getContentFileName(),
                                callRetry.getWeekId(),
                                callRetry.getLanguageLocationCode(),
                                callRetry.getCircle(),
                                callRetry.getSubscriptionOrigin().getCode(),
                                wr.get(Jh));
                        countJh++;
                    }
                    else if(specificState.contains(stateCode)){
                        writeSubscriptionRow(
                                requestId.toString(),
                                serviceIdFromOrigin(false, callRetry.getSubscriptionOrigin()),
                                callRetry.getMsisdn().toString(),
                                NORMAL_PRIORITY,
                                callFlowUrl,
                                callRetry.getContentFileName(),
                                callRetry.getWeekId(),
                                callRetry.getLanguageLocationCode(),
                                callRetry.getCircle(),
                                callRetry.getSubscriptionOrigin().getCode(),
                                wr.get(specific_non_Jh));
                        countSpecific++;
                    }
                    else {
                        writeSubscriptionRow(
                                requestId.toString(),
                                serviceIdFromOrigin(false, callRetry.getSubscriptionOrigin()),
                                callRetry.getMsisdn().toString(),
                                NORMAL_PRIORITY,
                                callFlowUrl,
                                callRetry.getContentFileName(),
                                callRetry.getWeekId(),
                                callRetry.getLanguageLocationCode(),
                                callRetry.getCircle(),
                                callRetry.getSubscriptionOrigin().getCode(),
                                wr.get(non_Jh));
                        count++;
                    }
                }

        } while (true);*/

        LOGGER.info(WROTE+non_Jh+"Retry", count);
        retryCount.put(non_Jh, count);
        retryCount.put(specific_non_Jh, countSpecific);
        if(split) {
            LOGGER.info(WROTE+Jh+"Retry", countJh);
            retryCount.put(Jh, countJh);
        }

        return retryCount;
    }

    private File localObdDir() {
        return new File(settingsFacade.getProperty(LOCAL_OBD_DIR));
    }

    private File localObdTestDir() {
        return new File(settingsFacade.getProperty(LOCAL_OBDTEST_DIR));
    }

    private List<Long> getSpecificStateList(){
        String locationProp = settingsFacade.getProperty(SPECIFIC_STATE_ID);
        if (StringUtils.isBlank(locationProp)) {

            return Collections.emptyList();
        }

        String[] locationParts = StringUtils.split(locationProp, ',');

        List<Long> stateIds = new ArrayList<>();
        for (String locationPart : locationParts) {
            stateIds.add(Long.valueOf(locationPart));
        }

        return stateIds;
    }


    private String contentFileNameWhatsappWelcomeMessage(){ return new String(settingsFacade.getProperty(CONTENT_FILE_NAME_WHATSAPP_WELCOME_MESSAGE)); }
    @Transactional
    public HashMap<String, TargetFileNotification> generateTargetFile(boolean split) {
        LOGGER.info("generateTargetFile()"+split);
        DateTime today = DateTime.now();
        String targetFileName = targetFileName(TIME_FORMATTER.print(today));
        String targetFileNameHungama = targetFileName(TIME_FORMATTER.print(today)+"IVR");
        File localTargetDir = localObdDir();
        String checksum;
        String checksumHungama;
        File targetFile = new File(localTargetDir, targetFileName);
        File targetFileHungama = new File(localTargetDir, targetFileNameHungama);

        HashMap<String, Integer> recordCount;
        HashMap<String, Integer> recordCountRetry;

        int maxQueryBlock = Integer.parseInt(settingsFacade.getProperty(MAX_QUERY_BLOCK));
        String callFlowUrl = settingsFacade.getProperty(TARGET_FILE_CALL_FLOW_URL);

        if (callFlowUrl == null) {
            //it's ok to have an empty call flow url - the spec says the default call flow will be used
            //whatever that is...
            callFlowUrl = "";
        }
        HashMap<String, OutputStreamWriter> wr = new HashMap<>();
        HashMap<String, TargetFileNotification> tfn = new HashMap<>();
        if(split){
            String targetFileNameJh = targetFileName(TIME_FORMATTER.print(today)+"JH");
            File localTargetDirJh = localObdDir();
            String checksumJh;
            File targetFileJh = new File(localTargetDirJh, targetFileNameJh);

            try {
                FileOutputStream fos = new FileOutputStream(targetFile);
                OutputStreamWriter writer = new OutputStreamWriter(fos);

                FileOutputStream fosH = new FileOutputStream(targetFileHungama);
                OutputStreamWriter writerH = new OutputStreamWriter(fosH);

                FileOutputStream fosJh = new FileOutputStream(targetFileJh);
                OutputStreamWriter writerJh = new OutputStreamWriter(fosJh);

                //Header
                writeHeader(writer);
                writeHeader(writerH);
                writeHeader(writerJh);

                List<String> subscriptionIdsJh = subscriptionService.findJhSubscriptionIds();
                LOGGER.info("JH-Subscriptions-Number"+subscriptionIdsJh.size());


                wr.put(Jh, writerJh);
                wr.put(non_Jh, writer);
                wr.put(specific_non_Jh, writerH);

                //Fresh calls
                recordCount = generateFreshCalls(today, maxQueryBlock, callFlowUrl, wr, subscriptionIdsJh, true);

                //Retry calls
                recordCountRetry = generateRetryCalls(today, maxQueryBlock, callFlowUrl, wr, subscriptionIdsJh, true);

                writer.close();
                fos.close();

                writerH.close();
                fosH.close();

                writerJh.close();
                fosJh.close();

                checksum = ChecksumHelper.checksum(targetFile);
                checksumJh = ChecksumHelper.checksum(targetFileJh);
                checksumHungama = ChecksumHelper.checksum(targetFileHungama);
                tfn.put(Jh, new TargetFileNotification(targetFileNameJh, checksumJh, recordCount.get(Jh)+recordCountRetry.get(Jh)));

            } catch (IOException e) {
                LOGGER.error(e.getMessage(),e);
                alert(targetFile.toString(), "targetFile", e.getMessage());
                fileAuditRecordDataService.create(new FileAuditRecord(FileType.TARGET_FILE, targetFile.getName(),
                        false, e.getMessage(), null, null));
                return null;
            }
        }
        else{
            try {
                FileOutputStream fos = new FileOutputStream(targetFile);
                OutputStreamWriter writer = new OutputStreamWriter(fos);

                FileOutputStream fosH = new FileOutputStream(targetFileHungama);
                OutputStreamWriter writerH = new OutputStreamWriter(fosH);

                //Header
                writeHeader(writer);
                writeHeader(writerH);

                wr.put(non_Jh, writer);
                wr.put(specific_non_Jh,writerH);

                //Fresh calls
                recordCount = generateFreshCalls(today, maxQueryBlock, callFlowUrl, wr, Collections.emptyList(), false);

                //Retry calls
                recordCountRetry = generateRetryCalls(today, maxQueryBlock, callFlowUrl, wr, Collections.emptyList(), false);

                writer.close();
                writerH.close();

                fos.close();
                fosH.close();

                checksum = ChecksumHelper.checksum(targetFile);
                checksumHungama = ChecksumHelper.checksum(targetFileHungama);

            } catch (IOException e) {
                LOGGER.error(e.getMessage(),e);
                alert(targetFile.toString(), "targetFile", e.getMessage());
                fileAuditRecordDataService.create(new FileAuditRecord(FileType.TARGET_FILE, targetFile.getName(),
                        false, e.getMessage(), null, null));
                return null;
            }
        }

        tfn.put(non_Jh, new TargetFileNotification(targetFileName, checksum, recordCount.get(non_Jh)+recordCountRetry.get(non_Jh)));
        tfn.put(specific_non_Jh, new TargetFileNotification(targetFileNameHungama,checksumHungama, recordCount.get(specific_non_Jh)+recordCountRetry.get(specific_non_Jh)));
        LOGGER.debug("TargetFileNotification = {}", tfn.toString());

        //audit the success
        for(TargetFileNotification t: tfn.values()) {
            fileAuditRecordDataService.create(new FileAuditRecord(FileType.TARGET_FILE, t.getFileName(), true,
                    null, t.getRecordsCount(), t.getChecksum()));
        }
        return tfn;
    }


    @Transactional
    public HashMap<String, TargetFileNotification> generateTargetFileApi(boolean split) {
        LOGGER.info("generateTargetFile()"+split);
        DateTime today = DateTime.now();
        String targetFileName = targetFileName(TIME_FORMATTER.print(today));
        String targetFileNameHungama = targetFileName(TIME_FORMATTER.print(today)+"IVR");
        File localTargetDir = localObdTestDir();
        String checksum;
        String checksumHungama;
        File targetFile = new File(localTargetDir, targetFileName);
        File targetFileHungama = new File(localTargetDir, targetFileNameHungama);

        HashMap<String, Integer> recordCount;
        HashMap<String, Integer> recordCountRetry;

        int maxQueryBlock = Integer.parseInt(settingsFacade.getProperty(MAX_QUERY_BLOCK));
        String callFlowUrl = settingsFacade.getProperty(TARGET_FILE_CALL_FLOW_URL);

        if (callFlowUrl == null) {
            //it's ok to have an empty call flow url - the spec says the default call flow will be used
            //whatever that is...
            callFlowUrl = "";
        }
        HashMap<String, OutputStreamWriter> wr = new HashMap<>();
        HashMap<String, TargetFileNotification> tfn = new HashMap<>();
        if(split){
            String targetFileNameJh = targetFileName(TIME_FORMATTER.print(today)+"JH");
            File localTargetDirJh = localObdTestDir();
            String checksumJh;
            File targetFileJh = new File(localTargetDirJh, targetFileNameJh);

            try {
                FileOutputStream fos = new FileOutputStream(targetFile);
                OutputStreamWriter writer = new OutputStreamWriter(fos);

                FileOutputStream fosH = new FileOutputStream(targetFileHungama);
                OutputStreamWriter writerH = new OutputStreamWriter(fosH);

                FileOutputStream fosJh = new FileOutputStream(targetFileJh);
                OutputStreamWriter writerJh = new OutputStreamWriter(fosJh);

                //Header
                writeHeader(writer);
                writeHeader(writerH);
                writeHeader(writerJh);

                List<String> subscriptionIdsJh = subscriptionService.findJhSubscriptionIds();
                LOGGER.info("JH-Subscriptions-Number"+subscriptionIdsJh.size());


                wr.put(Jh, writerJh);
                wr.put(non_Jh, writer);
                wr.put(specific_non_Jh, writerH);

                //Fresh calls
                recordCount = generateFreshCallsApi(today, maxQueryBlock, callFlowUrl, wr, subscriptionIdsJh, true);

                //Retry calls
                recordCountRetry = generateRetryCallsApi(today, maxQueryBlock, callFlowUrl, wr, subscriptionIdsJh, true);

                writer.close();
                fos.close();

                writerH.close();
                fosH.close();

                writerJh.close();
                fosJh.close();

                checksum = ChecksumHelper.checksum(targetFile);
                checksumJh = ChecksumHelper.checksum(targetFileJh);
                checksumHungama = ChecksumHelper.checksum(targetFileHungama);
                tfn.put(Jh, new TargetFileNotification(targetFileNameJh, checksumJh, recordCount.get(Jh)+recordCountRetry.get(Jh)));

            } catch (IOException e) {
                LOGGER.error(e.getMessage(),e);
                alert(targetFile.toString(), "targetFile", e.getMessage());
                fileAuditRecordDataService.create(new FileAuditRecord(FileType.TARGET_FILE, targetFile.getName(),
                        false, e.getMessage(), null, null));
                return null;
            }
        }
        else{
            try {
                FileOutputStream fos = new FileOutputStream(targetFile);
                OutputStreamWriter writer = new OutputStreamWriter(fos);

                FileOutputStream fosH = new FileOutputStream(targetFileHungama);
                OutputStreamWriter writerH = new OutputStreamWriter(fosH);

                //Header
                writeHeader(writer);
                writeHeader(writerH);

                wr.put(non_Jh, writer);
                wr.put(specific_non_Jh,writerH);

                //Fresh calls
                recordCount = generateFreshCallsApi(today, maxQueryBlock, callFlowUrl, wr, Collections.emptyList(), false);

                //Retry calls
                recordCountRetry = generateRetryCallsApi(today, maxQueryBlock, callFlowUrl, wr, Collections.emptyList(), false);

                writer.close();
                writerH.close();

                fos.close();
                fosH.close();

                checksum = ChecksumHelper.checksum(targetFile);
                checksumHungama = ChecksumHelper.checksum(targetFileHungama);

            } catch (IOException e) {
                LOGGER.error(e.getMessage(),e);
                alert(targetFile.toString(), "targetFile", e.getMessage());
                fileAuditRecordDataService.create(new FileAuditRecord(FileType.TARGET_FILE, targetFile.getName(),
                        false, e.getMessage(), null, null));
                return null;
            }
        }

        tfn.put(non_Jh, new TargetFileNotification(targetFileName, checksum, recordCount.get(non_Jh)+recordCountRetry.get(non_Jh)));
        tfn.put(specific_non_Jh, new TargetFileNotification(targetFileNameHungama,checksumHungama, recordCount.get(specific_non_Jh)+recordCountRetry.get(specific_non_Jh)));
        LOGGER.debug("TargetFileNotification = {}", tfn.toString());

        //audit the success
        for(TargetFileNotification t: tfn.values()) {
            fileAuditRecordDataService.create(new FileAuditRecord(FileType.TARGET_FILE, t.getFileName(), true,
                    null, t.getRecordsCount(), t.getChecksum()));
        }
        return tfn;
    }

    @Transactional
    public HashMap<String, TargetFileNotification> generateWhatsAppSMSTargetFile() {
        DateTime today = DateTime.now();
        LOGGER.debug(today.toString());
        String targetFileName = whatsAppSMStargetFileName(TIME_FORMATTER.print(today));
        LOGGER.debug("TargetFileName " + targetFileName);
        LOGGER.debug("Test-2: Calling localWhatsAppSMSOBDDir()");
        File localTargetDir = localWhatsAppSMSOBDDir();
        String checksum;
        File targetFile = new File(localTargetDir, targetFileName);
        Integer recordCount;
        HashMap<String, TargetFileNotification> tfn = new HashMap<>();

        try {
            LOGGER.debug("Test-3: Writing csv file for whatsApp sms target file");
            FileOutputStream fos = new FileOutputStream(targetFile);
            OutputStreamWriter writer = new OutputStreamWriter(fos);

            LOGGER.debug("Test-4: Writing header for the csv file for whatsApp sms target file");

            //WhatsApp SMS Header
            writeWhatsAppHeader(writer);

            LOGGER.debug("Test-5: Fetching data to be written in the csv file for whatsApp sms target file for date {}",LocalDate.now().minusDays(1));
            List<WhatsAppOptSMS> listOfWhatAppOptSmsToBeSent = getWhatAppSMSData(String.valueOf(LocalDate.now().minusDays(1)));
            LOGGER.debug("listOfWhatAppOptSmsToBeSent: {}", listOfWhatAppOptSmsToBeSent);
//            List<WhatsAppOptSMS> listOfWhatAppOptSmsToBeSent = new ArrayList<>();
            recordCount = listOfWhatAppOptSmsToBeSent.size();
            LOGGER.info("Row count in the target file "+ recordCount);

            LOGGER.debug("Test-6: Writing data in the csv file for whatsApp sms target file");
            //Writing Rows
            for(WhatsAppOptSMS row : listOfWhatAppOptSmsToBeSent){
                writeWhatsAppSMSRow(row.getCircleId(),
                        row.getContentFile(),
                        row.getLanguageLocationId(),
                        String.valueOf(row.getMsisdn()),
                        new RequestId(row.getRequestId(), TIME_FORMATTER.print(DateTime.now())).toString(),
                        writer);
            }

            writer.close();
            fos.close();

            checksum = ChecksumHelper.checksum(targetFile);

        } catch (IOException e) {
            LOGGER.error(e.getMessage(),e);
            alert(targetFile.toString(), "targetFile", e.getMessage());
            fileAuditRecordDataService.create(new FileAuditRecord(FileType.WHATSAPP_SMS_TARGET_FILE, targetFile.getName(),
                    false, e.getMessage(), null, null));
            return null;
        }

        tfn.put("whatsAppTargetFile", new TargetFileNotification(targetFileName, checksum, recordCount));
        LOGGER.debug("TargetFileNotification = {}", tfn.toString());

        //audit the success
        for(TargetFileNotification t: tfn.values()) {
            fileAuditRecordDataService.create(new FileAuditRecord(FileType.WHATSAPP_SMS_TARGET_FILE, t.getFileName(), true,
                    null, t.getRecordsCount(), t.getChecksum()));
        }
        return tfn;
    }


    public List<WhatsAppOptSMS> getWhatAppSMSData(String creationDate) {

        @SuppressWarnings("unchecked")
        SqlQueryExecution<List<WhatsAppOptSMS>> queryExecution = new SqlQueryExecution<List<WhatsAppOptSMS>>() {

            @Override
            public String getSqlQuery() {
                String query =  "Select id,circleId, contentFile, languageLocationId, msisdn, operatorId, requestId, messageId, smsSent, response, creationDate, modificationDate from nms_imi_waos where smsSent = false and date(creationDate) = :creationDate";
                LOGGER.debug(KilkariConstants.SQL_QUERY_LOG, query);
                return query;
            }

            @Override
            public List<WhatsAppOptSMS> execute(Query query) {

                query.setClass(WhatsAppOptSMS.class);

                Map params = new HashMap();
                params.put("creationDate", creationDate);
                ForwardQueryResult fqr = (ForwardQueryResult) query.executeWithMap(params);

                return (List<WhatsAppOptSMS>) fqr;
            }
        };

        List<WhatsAppOptSMS> whatsAppOptSMSData = whatsAppOptSMSDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("whatAppOptSMSData: {}", whatsAppOptSMSData);
        return whatsAppOptSMSData;
    }


    private void sendNotificationRequest(TargetFileNotification tfn) {
        String notificationUrl;
        if(tfn.getFileName().contains("IVR")){
            notificationUrl = settingsFacade.getProperty(HUNGAMA_TARGET_FILE_NOTIFICATION_URL);
        }else{
            notificationUrl = settingsFacade.getProperty(TARGET_FILE_NOTIFICATION_URL);
        }
        LOGGER.debug("Sending {} to {}", tfn, notificationUrl);


        ExponentialRetrySender sender = new ExponentialRetrySender(settingsFacade, alertService);

        HttpPost httpPost = new HttpPost(notificationUrl);
        ObjectMapper mapper = new ObjectMapper();

        try {
            String requestJson = mapper.writeValueAsString(tfn);
            httpPost.setHeader("Content-type", "application/json");
            httpPost.setHeader("Accept", "application/json");
            httpPost.setEntity(new StringEntity(requestJson));
        } catch (IOException e) {
            throw new InternalException(String.format("Unable to create targetFile notification request: %s",
                    e.getMessage()), e);
        }

        sender.sendNotificationRequest(httpPost, HttpStatus.SC_ACCEPTED, tfn.getFileName(), "targetFile Notification Request");
    }

    private void sendNotificationRequestWhatsApp(TargetFileNotification tfn) {
        String notificationUrl = settingsFacade.getProperty(TARGET_FILE_NOTIFICATION_URL_WHATSAPP);
        LOGGER.debug("Sending {} to {}", tfn, notificationUrl);

        ExponentialRetrySender sender = new ExponentialRetrySender(settingsFacade, alertService);

        HttpPost httpPost = new HttpPost(notificationUrl);
        ObjectMapper mapper = new ObjectMapper();

        try {
            String requestJson = mapper.writeValueAsString(tfn);
            httpPost.setHeader("Content-type", "application/json");
            httpPost.setHeader("Accept", "application/json");
            httpPost.setEntity(new StringEntity(requestJson));
        } catch (IOException e) {
            throw new InternalException(String.format("Unable to create targetFile notification request: %s",
                    e.getMessage()), e);
        }
        LOGGER.debug("test - httpPost object created");
        sender.sendNotificationRequestWhatsApp(httpPost, HttpStatus.SC_ACCEPTED, HttpStatus.SC_OK, tfn.getFileName(), "whatsapp targetFile Notification Request");
    }

    private void sendNotificationRequestWhatsAppSMS(TargetFileNotification tfn) {
        String notificationUrl = settingsFacade.getProperty(WHATSAPP_SMS_TARGET_FILE_NOTIFICATION_URL);
        LOGGER.debug("Sending {} to {}", tfn, notificationUrl);


        ExponentialRetrySender sender = new ExponentialRetrySender(settingsFacade, alertService);

        HttpPost httpPost = new HttpPost(notificationUrl);
        ObjectMapper mapper = new ObjectMapper();

        try {
            String requestJson = mapper.writeValueAsString(tfn);
            httpPost.setHeader("Content-type", "application/json");
            httpPost.setHeader("Accept", "application/json");
            httpPost.setEntity(new StringEntity(requestJson));
        } catch (IOException e) {
            throw new InternalException(String.format("Unable to create targetFile notification request: %s",
                    e.getMessage()), e);
        }

        sender.sendNotificationRequest(httpPost, HttpStatus.SC_ACCEPTED, tfn.getFileName(), "whatsApp sms targetFile Notification Request");
    }


    @MotechListener(subjects = { GENERATE_TARGET_FILE_EVENT })
    @Transactional
    public void generateTargetFile(MotechEvent event) {
        LOGGER.debug(event.toString());
            HashMap<String, TargetFileNotification> tfn = generateTargetFile(Boolean.valueOf(settingsFacade.getProperty(generateJhFile)));
            if (tfn != null) {
                // Copy the OBD file from the local imi.local_obd_dir to the remote imi.local_obd_dir network share
                ScpHelper scpHelper = new ScpHelper(settingsFacade);
                for(TargetFileNotification t: tfn.values()) {
                    try {
                        scpHelper.scpObdToRemote(t.getFileName());
                    } catch (ExecException e) {
                        String error = String.format("Error copying target file %s: %s", t.getFileName(),
                                e.getMessage());
                        LOGGER.error(error,e);
                        fileAuditRecordDataService.create(new FileAuditRecord(
                                FileType.TARGET_FILE,
                                t.getFileName(),
                                false,
                                error,
                                null,
                                null
                        ));
                        alert(t.getFileName(), "targetFileName", error);
                        return;
                    }

                    //notify the IVR system the file is ready
                    sendNotificationRequest(t);
                }

            }
    }

    private void writeHeaderWP(OutputStreamWriter writer) throws IOException{
        /*
         * #1 RequestId - external_id
         *
         */
        writer.write("external_id");
        writer.write(",");

        /*
         * #2 MobileNumber
         *
         */
        writer.write("urn");
        writer.write(",");

        /*
         * #3 Week_id
         *
         */
        writer.write("week_id");
        writer.write(",");

        /*
         * #4 ContentFileName
         *
         */
        writer.write("content_file_name");
        writer.write(",");

        /*
         * #5 Preferred_language - ISO Code:
         *
         */
        writer.write("preferred_language");
        writer.write(",");

        /*
         * #6 State_Code
         *
         */
        writer.write("state_code");

        writer.write("\n");
    }

    private void writeSubscriptionRowWP(String requestId, String urn , String weekId,String contentFileName,
                                        String preferredLanguage , String stateCode , OutputStreamWriter writer) throws IOException {
        LOGGER.debug("test writeSubscriptionRowWP : ");
        LOGGER.debug("test - requestId " + requestId + " urn " + urn + " weekId " + weekId + " contentFileName " + contentFileName);
        LOGGER.debug(" test - preferredLanguage " + preferredLanguage + " stateCode " + stateCode );

        /*
         * #1 RequestId
         *
         * A unique Request id for each obd record
         */
        writer.write(requestId);
        writer.write(",");

        /*
         * #2 urn - mobile number
         *
         *10 digit number to be dialed out
         */
        writer.write(urn);
        writer.write(",");

        /*
         * #3 weekId
         *
         * Week id of the messaged delivered in OBD
         */
        writer.write(weekId);
        writer.write(",");

        /*
         * #4 contentFileName
         *
         * Content file to be played
         */
        writer.write(contentFileName);
        writer.write(",");

        /*
         * #5 preferredLanguage
         *
         */
        writer.write(preferredLanguage);
        writer.write(",");

        /*
         * #6 stateCode
         *
         */
        writer.write(stateCode);

        writer.write("\n");
    }

    private Integer generateFreshCallsWP(DateTime timestamp, int maxQueryBlock,
                                         OutputStreamWriter wr) throws IOException{
        LOGGER.info("generateFreshCallsWPObd({})", timestamp);

        int skippedrecords = 0;

        DayOfTheWeek dow = DayOfTheWeek.fromDateTime(timestamp);
        int recordsWritten = 0;
        Long offset = 0L;
        Date date = timestamp.toDate();
        do {
            LOGGER.debug("test - findActiveSubscriptionsForDayWP ");
            LOGGER.debug("offset is " + offset);
            List<Subscription> subscriptions = subscriptionService.findActiveSubscriptionsForDayWP(dow, offset, maxQueryBlock, date);
            LOGGER.info("Subs_block_size "  + subscriptions.size());
            if (subscriptions.size() == 0) {
                break;
            }
                for (Subscription subscription : subscriptions) {
                    LOGGER.debug("Handling Subscription " + subscription.getId());
                    offset = subscription.getId();

                    Subscriber subscriber = subscription.getSubscriber();
                    RequestId requestId = new RequestId(subscription.getSubscriptionId(), TIME_FORMATTER.print(timestamp));
                    LOGGER.debug("test - subscriber is " + subscriber.getId());
                    try {
                        SubscriptionPack pack = subscription.getSubscriptionPack();
                        int daysIntoPack = Days.daysBetween(subscription.getStartDate(), timestamp).getDays();
                        if (daysIntoPack == pack.getWeeks() * 7) {
                            //
                            // Do not add subscriptions on their last day to the fresh call list since we
                            // will try to fetch message for current +1 week, which wouldn't exist
                            // See https://applab.atlassian.net/browse/NMS-301
                            //
                            LOGGER.debug("Ignoring last day for subscription {} from fresh calls.",
                                    subscription.getSubscriptionId());
                            skippedrecords++;
                            continue;
                        }

                        SubscriptionPackMessage msg = subscription.nextScheduledMessage(timestamp);
                        String stateCode;
                        if(subscriber.getMother() == null){
                            stateCode = subscriber.getChild().getState().getCode().toString();
                        }
                        else{
                            stateCode = subscriber.getMother().getState().getCode().toString();
                        }
                        String mobileNo = "91" + subscriber.getCallingNumber().toString();
                        writeSubscriptionRowWP(
                                requestId.toString() ,
                                mobileNo,
                                msg.getWeekId(),
                                msg.getMessageFileName(),
                                subscriber.getLanguage().getIsoCode(),
                                stateCode,
                                wr
                                );

                            recordsWritten++;

                    } catch (IllegalStateException se) {
                        String message = se.toString();
                        alertService.create(subscription.getSubscriptionId(), "IllegalStateException", message,
                                AlertType.HIGH, AlertStatus.NEW, 0, null);
                        LOGGER.error(message,se);
                    }
                }
                if(subscriptions.size()<maxQueryBlock){
                    LOGGER.debug("Records less than {} " , maxQueryBlock);
                    break;
                }
            }
        while (true);
        LOGGER.debug("test - " + recordsWritten);
        return recordsWritten;
    }

    private int generateWelcomeMessageWP(DateTime timestamp , int maxQueryBlock , OutputStreamWriter writer) throws  IOException{
        LOGGER.info("test generateWelcomeMessageWP({})", timestamp);

        int skippedrecords = 0;
        Date date = timestamp.toDate();
        DayOfTheWeek dow = DayOfTheWeek.fromDateTime(timestamp);
        int recordCountWelcome = 0;
        Long offset = 0L;

        do {

            LOGGER.debug("offset is " + offset);
            List<Subscription> subscriptions = subscriptionService.findWelcomeActiveSubscriptionsForDayWP(date, dow, offset, maxQueryBlock);
            LOGGER.info("Subs_block_size "  + subscriptions.size());
            if(subscriptions.size()==0){
                break;
            }
            for(Subscription subscription : subscriptions){
                Subscriber subscriber = subscription.getSubscriber();
                RequestId requestId = new RequestId(subscription.getSubscriptionId(), TIME_FORMATTER.print(timestamp));
                LOGGER.debug("test - subscriber is " + subscriber.getId());
                offset = subscription.getId();
                try {
                    SubscriptionPack pack = subscription.getSubscriptionPack();
                    int daysIntoPack = Days.daysBetween(subscription.getStartDate(), timestamp).getDays();
                    if (daysIntoPack == pack.getWeeks() * 7) {
                        //
                        // Do not add subscriptions on their last day to the fresh call list since we
                        // will try to fetch message for current +1 week, which wouldn't exist
                        // See https://applab.atlassian.net/browse/NMS-301
                        //
                        LOGGER.debug("Ignoring last day for subscription {} from fresh calls.",
                                subscription.getSubscriptionId());
                        skippedrecords++;
                        continue;
                    }

//                    SubscriptionPackMessage msg = subscription.nextScheduledMessage(timestamp);
                    String stateCode;
                    if(subscriber.getMother() == null){
                        stateCode = subscriber.getChild().getState().getCode().toString();
                    }
                    else{
                        stateCode = subscriber.getMother().getState().getCode().toString();
                    }
                    LOGGER.debug("test - calling number " + subscriber.getCallingNumber().toString());
                    LOGGER.debug("test - stateCode " + stateCode );
                    LOGGER.debug("test - contentFileNameWhatsappWelcomeMessage " + contentFileNameWhatsappWelcomeMessage());

                    String mobileNo = "91" + subscriber.getCallingNumber().toString();

                    writeSubscriptionRowWP(
                            requestId.toString() ,
                            mobileNo,
                            "w1_1",
                            contentFileNameWhatsappWelcomeMessage(),
                            subscriber.getLanguage().getIsoCode(),
                            stateCode,
                            writer
                    );


                    recordCountWelcome++;


                } catch (IllegalStateException se) {
                    String message = se.toString();
                    alertService.create(subscription.getSubscriptionId(), "IllegalStateException", message,
                            AlertType.HIGH, AlertStatus.NEW, 0, null);
                    LOGGER.error(message,se);
                }
            }
            if(subscriptions.size()<maxQueryBlock){
                LOGGER.debug("Records less than {} " , maxQueryBlock);
                break;
            }

        }while (true);
        LOGGER.debug("test - recordCountWelcome " + recordCountWelcome );
        return recordCountWelcome;
    }

    private int generateDeactivatedMessageWP(DateTime timestamp , int maxQueryBlock , OutputStreamWriter writer) throws  IOException{
        LOGGER.info("test generateDeactivatedMessageWP({})", timestamp);

        int skippedrecords = 0;

        Date date = timestamp.toDate();
        int recordCountDeactivated = 0;
        Long offset = 0L;

        String deactivationReasons = deactivationReasonsForWhatsapp();
        LOGGER.debug(" deactivationReasons are " + deactivationReasons);
        do {

            LOGGER.debug("offset is " + offset);
            List<Subscription> subscriptions = subscriptionService.findDeactivatedSubscriptionsForDayWP(date, offset, maxQueryBlock, deactivationReasons);
            LOGGER.info("Subs_block_size "  + subscriptions.size());
            if(subscriptions.size()==0){
                break;
            }
            for(Subscription subscription : subscriptions){
                Subscriber subscriber = subscription.getSubscriber();
                RequestId requestId = new RequestId(subscription.getSubscriptionId(), TIME_FORMATTER.print(timestamp));
                LOGGER.debug("subscriber id : " + subscriber.getId());
                offset = subscription.getId();
                try {
                    SubscriptionPack pack = subscription.getSubscriptionPack();
                    int daysIntoPack = Days.daysBetween(subscription.getStartDate(), timestamp).getDays();
                    if (daysIntoPack == pack.getWeeks() * 7) {
                        //
                        // Do not add subscriptions on their last day to the fresh call list since we
                        // will try to fetch message for current +1 week, which wouldn't exist
                        // See https://applab.atlassian.net/browse/NMS-301
                        //
                        LOGGER.debug("Ignoring last day for subscription {} from fresh calls.",
                                subscription.getSubscriptionId());
                        skippedrecords++;
                        continue;
                    }

//                    SubscriptionPackMessage msg = subscription.nextScheduledMessage(timestamp);
                    int currentWeek = daysIntoPack / DAYS_IN_WEEK + 1;
                    SubscriptionPackMessage msg = subscription.getMessageByWeekAndMessageId(currentWeek, 1);
                    String stateCode;
                    if(subscriber.getMother() == null){
                        stateCode = subscriber.getChild().getState().getCode().toString();
                    }
                    else{
                        stateCode = subscriber.getMother().getState().getCode().toString();
                    }

                    String mobileNo = "91" + subscriber.getCallingNumber().toString();

                    writeSubscriptionRowWP(
                            requestId.toString() ,
                            mobileNo,
                            msg.getWeekId(),
                            contentFileNameWhatsappDeactivatedMessage(),
                            subscriber.getLanguage().getIsoCode(),
                            stateCode,
                            writer
                    );

                    recordCountDeactivated++;

                } catch (IllegalStateException se) {
                    String message = se.toString();
                    alertService.create(subscription.getSubscriptionId(), "IllegalStateException", message,
                            AlertType.HIGH, AlertStatus.NEW, 0, null);
                    LOGGER.error(message,se);
                }
            }
            if(subscriptions.size()<maxQueryBlock){
                LOGGER.debug("Records less than {} " , maxQueryBlock);
                break;
            }
        }while (true);

        LOGGER.debug("test - recordCountDeactivated " + recordCountDeactivated );
        return recordCountDeactivated;
    }

    @MotechListener(subjects = {GENERATE_WHATSAPP_SMS_TARGET_FILE_EVENT})
    @Transactional
    public void generateWhatsAppTargetFile(MotechEvent event) {
        LOGGER.debug(event.toString());
        HashMap<String, TargetFileNotification> tfn = generateWhatsAppSMSTargetFile();
        copyWhatsappSMSTargetFiletoRemoteAndNotifyIVR(tfn);
    }

    public void copyWhatsappSMSTargetFiletoRemoteAndNotifyIVR(HashMap<String, TargetFileNotification> tfn){
        if (tfn != null) {
            // Copy the OBD file from the local imi.local_obd_dir to the remote imi.local_obd_dir network share
            ScpHelper scpHelper = new ScpHelper(settingsFacade);
            for(TargetFileNotification t: tfn.values()) {
                try {
                    scpHelper.scpWhatsAppToRemote(t.getFileName());
                } catch (ExecException e) {
                    String error = String.format("Error copying target file %s: %s", t.getFileName(),
                            e.getMessage());
                    LOGGER.error(error,e);
                    fileAuditRecordDataService.create(new FileAuditRecord(
                            FileType.WHATSAPP_SMS_TARGET_FILE,
                            t.getFileName(),
                            false,
                            error,
                            null,
                            null
                    ));
                    alert(t.getFileName(), "targetFileName", error);
                    return;
                }

                //notify the IVR system the file is ready
                LOGGER.debug("Test-8: Calling sendWhatsAppNotificationRequest");
                if(t.getRecordsCount() != 0){
                    sendNotificationRequestWhatsAppSMS(t);
                }
                else{
                    LOGGER.debug("Test-8: There is no records in the file to notify.");
                }

                LOGGER.debug("Test-9: Fetching the to update sms sent status");
                List<WhatsAppOptSMS> listOfWhatAppOptSmsToBeSent = getWhatAppSMSData(String.valueOf(LocalDate.now().minusDays(1)));
                LOGGER.debug("Test-10: Updating the smsSent status");
                Long updatedRecords = bulkUpdateWhatsAppOptSMS(listOfWhatAppOptSmsToBeSent);
                LOGGER.debug("{} whatsAppOptSMS records updated ", updatedRecords);

            }

        }
    }

    private Long bulkUpdateWhatsAppOptSMS(List<WhatsAppOptSMS> whatsAppOptSMSList){

        int count = 0;
        Long sqlCount = 0L;
        while (count < whatsAppOptSMSList.size()) {
            List<WhatsAppOptSMS> updateObjectsPart = new ArrayList<>();
            while (updateObjectsPart.size() < PARTITION_SIZE && count < whatsAppOptSMSList.size()) {
                updateObjectsPart.add(whatsAppOptSMSList.get(count));
                count++;
            }

            sqlCount += whatsAppOptSMSBulkUpdate(updateObjectsPart);
            updateObjectsPart.clear();
        }
        return sqlCount;

    }

    @Transactional
    private Long whatsAppOptSMSBulkUpdate(List<WhatsAppOptSMS> whatsAppOptSMSList) {
        for(WhatsAppOptSMS row : whatsAppOptSMSList){
            row.setSmsSent(true);
            whatsAppOptSMSDataService.update(row);
        }
        return (long) whatsAppOptSMSList.size();
    }




    /**
     * Log & audit the fact that IMI processed the OBD file (successfully or not)
     *
     * @param request file name & status
     */
    @Override
    public void handleFileProcessedStatusNotification(FileProcessedStatusRequest request) {
        fileAuditRecordDataService.create(new FileAuditRecord(
                FileType.TARGET_FILE,
                request.getFileName(),
                request.getFileProcessedStatus() == FileProcessedStatus.FILE_PROCESSED_SUCCESSFULLY,
                request.getFileProcessedStatus().getName(),
                null,
                null
        ));
        if (request.getFileProcessedStatus() != FileProcessedStatus.FILE_PROCESSED_SUCCESSFULLY) {
            LOGGER.error(request.toString());
            //todo: IT check if alert was created
            alert(request.getFileName(), "targetFileName",
                    String.format("Target File Processing Error: %s", request.getFailureReason()));
        }
    }

    /**
     * Log & audit the fact that IMI processed the OBD whatsapp file (successfully or not)
     *
     * @param request file name & status
     */
    @Override
    public void handleFileProcessedStatusNotificationWhatsApp(FileProcessedStatusRequest request) {
        fileAuditRecordDataService.create(new FileAuditRecord(
                FileType.WHATSAPP_TARGET_FILE,
                request.getFileName(),
                request.getFileProcessedStatus() == FileProcessedStatus.FILE_PROCESSED_SUCCESSFULLY,
                request.getFileProcessedStatus().getName(),
                null,
                null
        ));
        if (request.getFileProcessedStatus() != FileProcessedStatus.FILE_PROCESSED_SUCCESSFULLY) {
            LOGGER.error(request.toString());
            //todo: IT check if alert was created
            alert(request.getFileName(), "targetFileName",
                    String.format("Target File Processing Error: %s", request.getFailureReason()));
        }
    }


    @MotechListener(subjects = {GENERATE_TARGET_FILE_EVENT_WHATSAPP})
    @Transactional
    public void generateTargetFileWhatsApp(MotechEvent event) {
        LOGGER.debug(event.toString());
        LOGGER.debug("Scheduler is called for Whatsapp ");
        TargetFileNotification tfn = generateTargetFileWhatsApp();
        LOGGER.debug("test - target file generated ");
        if (tfn != null) {
                //notify the IVR system the file is ready
            LOGGER.debug("notifying IVR system file is ready");
            if(tfn.getRecordsCount() == 0){
                LOGGER.debug("There is no records in the file");
            }
            sendNotificationRequestWhatsApp(tfn);
        }
    }

    private File localWhatsAppSMSOBDDir() {
        return new File(settingsFacade.getProperty(LOCAL_WHATSAPP_SMS_OBD_DIR));
    }

    private String contentFileNameWhatsappDeactivatedMessage(){ return new String(settingsFacade.getProperty(CONTENT_FILE_NAME_WHATSAPP_DEACTIVATION_MESSAGE)); }

    private String deactivationReasonsForWhatsapp(){ return new String(settingsFacade.getProperty(DEACTIVATION_REASONS_FOR_WHATSAPP)); }


    @Transactional
    public TargetFileNotification generateTargetFileWhatsApp() {
        LOGGER.info("generateTargetFileWP()");
        DateTime today = DateTime.now();
        DateTime yesterday = today.minusDays(1);
        String targetFileName = wpTargetFileName(TIME_FORMATTER.print(today));
        File localTargetDir = wpLocalObdDir();
        String checksum;
        File targetFile = new File(localTargetDir, targetFileName);

        LOGGER.debug("test - targetFileName");

        Integer recordCount = 0;
        Integer recordCountWelcome = 0;
        Integer recordCountDeactivated = 0;

        int maxQueryBlock = Integer.parseInt(settingsFacade.getProperty(MAX_QUERY_BLOCK));

        try {
            FileOutputStream fos = new FileOutputStream(targetFile);
            OutputStreamWriter writer = new OutputStreamWriter(fos);

            //Header
            writeHeaderWP(writer);

            LOGGER.debug("test - generateFreshCallsWP ");
            //Fresh calls
            recordCount = generateFreshCallsWP(today, maxQueryBlock, writer );

            LOGGER.debug("test - generateWelcomeMessageWP");
            //welcome message
            recordCountWelcome = generateWelcomeMessageWP(today , maxQueryBlock , writer);

            LOGGER.debug("test - generate deactivated count");
            recordCountDeactivated = generateDeactivatedMessageWP(yesterday , maxQueryBlock , writer);

            writer.close();
            fos.close();

            checksum = ChecksumHelper.checksum(targetFile);

        } catch (IOException e) {
            LOGGER.error(e.getMessage(),e);
            alert(targetFile.toString(), "targetFile", e.getMessage());
            fileAuditRecordDataService.create(new FileAuditRecord(FileType.WHATSAPP_TARGET_FILE, targetFile.getName(),
                    false, e.getMessage(), null, null));
            return null;
        }

        TargetFileNotification tfn = new TargetFileNotification(targetFileName, checksum, recordCount+recordCountWelcome+recordCountDeactivated  ) ;
        LOGGER.debug("TargetFileNotification = {}", tfn.toString());

        //audit the success

        fileAuditRecordDataService.create(new FileAuditRecord(FileType.WHATSAPP_TARGET_FILE, tfn.getFileName(), true,
                null, tfn.getRecordsCount(), tfn.getChecksum()));

        // Copy the OBD file from the local imi.local_obd_dir to the remote imi.local_obd_dir network share
        ScpHelper scpHelper = new ScpHelper(settingsFacade);
        try {
            scpHelper.scpWhatsAppObdToRemote(tfn.getFileName());
        } catch (ExecException e) {
            String error = String.format("Error copying target file %s: %s", tfn.getFileName(),
                    e.getMessage());
            LOGGER.error(error,e);
            fileAuditRecordDataService.create(new FileAuditRecord(
                    FileType.WHATSAPP_TARGET_FILE,
                    tfn.getFileName(),
                    false,
                    error,
                    null,
                    null
            ));
            alert(tfn.getFileName(), "targetFileName", error);
            return null;
        }
        return tfn;
    }

    @Override
    public void handleWhatsAppSMSFileProcessedStatusNotification(FileProcessedStatusRequest request) {
        fileAuditRecordDataService.create(new FileAuditRecord(
                FileType.WHATSAPP_SMS_TARGET_FILE,
                request.getFileName(),
                request.getFileProcessedStatus() == FileProcessedStatus.FILE_PROCESSED_SUCCESSFULLY,
                request.getFileProcessedStatus().getName(),
                null,
                null
        ));
        if (request.getFileProcessedStatus() != FileProcessedStatus.FILE_PROCESSED_SUCCESSFULLY) {
            LOGGER.error(request.toString());
            //todo: IT check if alert was created
            alert(request.getFileName(), "whatsAppSMSTargetFileName",
                    String.format("WhatsApp SMS Target File Processing Error: %s", request.getFailureReason()));
        }
    }
}

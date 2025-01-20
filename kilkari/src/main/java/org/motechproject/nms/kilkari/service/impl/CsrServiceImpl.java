package org.motechproject.nms.kilkari.service.impl;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.motechproject.alerts.contract.AlertService;
import org.motechproject.alerts.domain.AlertStatus;
import org.motechproject.alerts.domain.AlertType;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.annotations.MotechListener;
import org.motechproject.metrics.service.Timer;
import org.motechproject.nms.kilkari.domain.*;
import org.motechproject.nms.kilkari.dto.CallSummaryRecordDto;
import org.motechproject.nms.kilkari.dto.WhatsAppOptCsrDto;
import org.motechproject.nms.kilkari.dto.WhatsAppOptSMSCsrDto;
import org.motechproject.nms.kilkari.exception.InvalidCallRecordDataException;
import org.motechproject.nms.kilkari.exception.NoSuchSubscriptionException;
import org.motechproject.nms.kilkari.repository.CallRetryDataService;
import org.motechproject.nms.kilkari.repository.DeactivatedBeneficiaryDataService;
import org.motechproject.nms.kilkari.repository.SubscriptionDataService;
import org.motechproject.nms.kilkari.repository.WhatsAppOptSMSDataService;
import org.motechproject.nms.kilkari.service.CsrService;
import org.motechproject.nms.kilkari.service.CsrVerifierService;
import org.motechproject.nms.kilkari.service.SubscriptionService;
import org.motechproject.nms.kilkari.utils.KilkariConstants;
import org.motechproject.nms.props.domain.DayOfTheWeek;
import org.motechproject.nms.props.domain.FinalCallStatus;
import org.motechproject.nms.props.domain.StatusCode;
import org.motechproject.nms.props.domain.WhatsAppOptInStatusCode;
import org.motechproject.server.config.SettingsFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static java.lang.Math.min;


@Service("csrService")
public class CsrServiceImpl implements CsrService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CsrServiceImpl.class);

    private SubscriptionDataService subscriptionDataService;
    private SubscriptionService subscriptionService;
    private CallRetryDataService callRetryDataService;
    private WhatsAppOptSMSDataService whatsAppOptSMSDataService;
    private AlertService alertService;
    private CsrVerifierService csrVerifierService;
    private DeactivatedBeneficiaryDataService deactivatedBeneficiaryDataService;

    @Autowired
    public CsrServiceImpl(SubscriptionDataService subscriptionDataService, SubscriptionService subscriptionService,
                          CallRetryDataService callRetryDataService, AlertService alertService,
                          CsrVerifierService csrVerifierService,
                          DeactivatedBeneficiaryDataService deactivatedBeneficiaryDataService,
                          WhatsAppOptSMSDataService whatsAppOptSMSDataService) {
        this.subscriptionDataService = subscriptionDataService;
        this.subscriptionService = subscriptionService;
        this.callRetryDataService = callRetryDataService;
        this.alertService = alertService;
        this.csrVerifierService = csrVerifierService;
        this.deactivatedBeneficiaryDataService = deactivatedBeneficiaryDataService;
        this.whatsAppOptSMSDataService = whatsAppOptSMSDataService;
    }


    private void completeSubscriptionIfNeeded(Subscription subscription, String contentFileName) {

        resetWelcomeFlagInSubscription(subscription);

        if (subscription.getStatus() == SubscriptionStatus.DEACTIVATED || !subscription.isLastPackMessage(contentFileName)) {
            // if subscription is deactivated or content was not the last message, do nothing
            return;
        }

        // Mark the subscription completed
        subscription.setStatus(SubscriptionStatus.COMPLETED);
        subscriptionDataService.update(subscription);
        SubscriptionServiceImpl.createDeactivatedUser(deactivatedBeneficiaryDataService, subscription, null, true);
    }

    private void updateSubscriptionServiceStatus(Subscription subscription, String contentFileName) {

        resetWelcomeFlagInSubscription(subscription);

        if (subscription.getStatus() == SubscriptionStatus.DEACTIVATED || !subscription.isLastPackMessage(contentFileName)) {
            // if subscription is deactivated or content was not the last message, do nothing
            return;
        }

        // Mark the subscription completed
        subscription.setStatus(SubscriptionStatus.COMPLETED);
        subscriptionDataService.update(subscription);
        SubscriptionServiceImpl.createDeactivatedUser(deactivatedBeneficiaryDataService, subscription, null, true);
    }

    private void switchIVRfromWhatsAppIfOpted(Subscription subscription, CallSummaryRecordDto callSummaryRecordDto, CallRetry callRetry){
        if (subscription.isNeedsWelcomeOptInForWP() && SubscriptionStatus.ACTIVE.equals(subscription.getStatus())){
            LOGGER.info("INSIDE processCallSummaryRecord -INSIDE switchIVRfromWhatsAppIfOpted TEND TO change status");
            switch (WhatsAppOptInStatusCode.valueOf(callSummaryRecordDto.getOpt_in_input())){
                case OPTED_FOR_WHATSAPP:
                    switchToWhatsApp(subscription);
                    if (callRetry != null) {
                            callRetryDataService.delete(callRetry);
                        }
                    break;
                case OPTED_FOR_IVR:
                    if (callRetry != null) {
                        callRetryDataService.delete(callRetry);
                    }
                    break;
                default:
                    doRescheduleOptIn(subscription, callSummaryRecordDto, callRetry);
            }
        }
    }

    private void switchToWhatsApp(Subscription subscription){
        LOGGER.info("INSIDE processCallSummaryRecord -INSIDE switchToWhatsApp TEND TO change status to IVR_AND_WHATSAPP");
        subscription.setWhatsAppSelfOptIn(true);
        subscription.setServiceStatus(ServiceStatus.IVR_AND_WHATSAPP);
        subscription.setWpStartDate(LocalDate.now().plusDays(1).toDateTimeAtStartOfDay());
        subscriptionDataService.update(subscription);
    }

    private void handleDndForSubscription(Subscription subscription) {

        if (subscription.getOrigin() == SubscriptionOrigin.IVR) {
            // Raise an alert since the user chose to subscribe voluntarily through IVR but we got a DND response
            String error = String.format("Subscription %s was rejected (DND) but its origin is IVR, not MCTS!",
                    subscription.getSubscriptionId());
            LOGGER.error(error);
            alertService.create(subscription.getSubscriptionId(), "subscription", error, AlertType.HIGH,
                    AlertStatus.NEW, 0, null);
            return;
        }

        subscriptionService.deactivateSubscription(subscription, DeactivationReason.DO_NOT_DISTURB);
    }


     private void doReschedule(Subscription subscription, CallRetry existingCallRetry, CallSummaryRecordDto csrDto) {

        boolean invalidNr = StatusCode.fromInt(csrDto.getStatusCode()).equals(StatusCode.OBD_FAILED_INVALIDNUMBER);
LOGGER.info("inside doreshedule method");
LOGGER.info("this is the data inside: {},{},{]",subscription,existingCallRetry, csrDto);
        if (existingCallRetry == null && SubscriptionStatus.ACTIVE.equals(subscription.getStatus())) {
            LOGGER.info("inside condition 1");
            // We've never retried this call, let's do it
            callRetryDataService.create(new CallRetry(
                    subscription.getSubscriptionId(),
                    subscription.getSubscriber().getCallingNumber(),
                    CallStage.RETRY_1,
                    csrDto.getContentFileName(),
                    csrDto.getWeekId(),
                    csrDto.getLanguageCode(),
                    csrDto.getCircleName(),
                    subscription.getOrigin(),
                    csrDto.getTargetFileTimeStamp(),
                    invalidNr ? 1 : 0,
                    csrDto.isOpt_in_call_eligibility()
                    )
            );
            return;
        }

        if ((subscription.getSubscriptionPack().retryCount() == 1) ||
                (existingCallRetry !=null && existingCallRetry.getCallStage() == CallStage.RETRY_LAST)) {
            LOGGER.info("inside condition 2");
            // This call should not be retried

            // Deactivate subscription for persistent invalid numbers
            // See https://github.com/motech-implementations/mim/issues/169
            if (existingCallRetry != null && existingCallRetry.getInvalidNumberCount() != null &&
                    existingCallRetry.getInvalidNumberCount() == subscription.getSubscriptionPack().retryCount()) {
                LOGGER.info("inside condition 3");
                subscription.setStatus(SubscriptionStatus.DEACTIVATED);
                subscription.setDeactivationReason(DeactivationReason.INVALID_NUMBER);
                subscriptionDataService.update(subscription);
                SubscriptionServiceImpl.createDeactivatedUser(deactivatedBeneficiaryDataService, subscription, DeactivationReason.INVALID_NUMBER, false);
            }
            if (existingCallRetry != null && existingCallRetry.isOpt_in_call_eligibility()
                    && existingCallRetry.getCallStage() == CallStage.RETRY_LAST && existingCallRetry.getWeekId().equals("w1_1")) {
                LOGGER.info("inside condition 4");
                boolean optInCall = existingCallRetry.getContentFileName().equals("opt_in.wav");
                if(!optInCall) {
                    LOGGER.info("inside condition 5");
                    /*callRetryDataService.delete(existingCallRetry);
                    callRetryDataService.create(new CallRetry(
                                    subscription.getSubscriptionId(),
                                    subscription.getSubscriber().getCallingNumber(),
                                    CallStage.RETRY_LAST,
                                    "opt_in.wav",
                                    csrDto.getWeekId(),
                                    csrDto.getLanguageCode(),
                                    csrDto.getCircleName(),
                                    subscription.getOrigin(),
                                    csrDto.getTargetFileTimeStamp(),
                                    0,
                                    csrDto.isOpt_in_call_eligibility()
                            )
                    );*/
                    existingCallRetry.setContentFileName("opt_in.wav");
                    callRetryDataService.update(existingCallRetry);
                } else {
                    LOGGER.info("inside condition 6");
                    completeSubscriptionIfNeeded(subscription, csrDto.getContentFileName());
                    callRetryDataService.delete(existingCallRetry);
                    LOGGER.info("subscription is : {}", subscription);
                    LOGGER.info("csrDto is : {}", csrDto);
                    LOGGER.info("whatsAppOptSMSDataService is  : {}", whatsAppOptSMSDataService);
                    // write message table logic here
                    whatsAppOptSMSDataService.create(new WhatsAppOptSMS(csrDto.getCircleName(),
                            "SMS_CONTENT",
                            csrDto.getLanguageCode(),
                            subscription.getSubscriber().getCallingNumber(),
                            "",
                            subscription.getSubscriptionId(),
                            false,
                            WhatsAppOptInResponse.NO_RESPONSE,
                            DateTime.now(),
                            DateTime.now()
                            ));
                }
                return;
            }
            if (existingCallRetry != null) {
                LOGGER.info("inside condition 7");
                callRetryDataService.delete(existingCallRetry);
            }

            // Does subscription need to be marked complete, even if we failed to send the last message?
            completeSubscriptionIfNeeded(subscription, csrDto.getContentFileName());

            return;
        }


        // This call should indeed be re-rescheduled
        if (existingCallRetry != null) {
            LOGGER.info("inside condition 8");
            existingCallRetry.setCallStage(existingCallRetry.getCallStage().nextStage());
            existingCallRetry.setInvalidNumberCount(existingCallRetry.getInvalidNumberCount() == null ? 0 :
                    (existingCallRetry.getInvalidNumberCount() + (invalidNr ? 1 : 0)));
            callRetryDataService.update(existingCallRetry);
        }

    }

    private void doRescheduleOptIn(Subscription subscription,CallSummaryRecordDto callSummaryRecordDto, CallRetry callRetry){
        LOGGER.info("INSIDE doRescheduleOptIn -before resheduling");
        boolean invalidNr = StatusCode.fromInt(callSummaryRecordDto.getStatusCode()).equals(StatusCode.OBD_FAILED_INVALIDNUMBER);
        CallRetry callRetry1 = callRetryDataService.findBySubscriptionId(subscription.getSubscriptionId());
        if ((callRetry1 !=null && callRetry.getCallStage() == CallStage.RETRY_LAST)) {
            // Only way flow reach here when opt-in call has no response
            callRetryDataService.delete(callRetry);
            // write insertion of message table logic here
            LOGGER.info("subscription is : {}", subscription);
            LOGGER.info("csrDto is : {}", callSummaryRecordDto);
            LOGGER.info("whatsAppOptSMSDataService is  : {}", whatsAppOptSMSDataService);
             whatsAppOptSMSDataService.create(new WhatsAppOptSMS(callSummaryRecordDto.getCircleName(),
                    "SMS_CONTENT",
                    callSummaryRecordDto.getLanguageCode(),
                    subscription.getSubscriber().getCallingNumber(),
                    "",
                    subscription.getSubscriptionId(),
                    false,
                    WhatsAppOptInResponse.NO_RESPONSE,
                    DateTime.now(),
                    DateTime.now()
            ));
            return;
        }
        if (callRetry1 == null && SubscriptionStatus.ACTIVE.equals(subscription.getStatus()) && subscription.isNeedsWelcomeOptInForWP()) {
            // A seperate OPT-IN needs to be sent in case of no response
            callRetryDataService.create(new CallRetry(
                            subscription.getSubscriptionId(),
                            subscription.getSubscriber().getCallingNumber(),
                            CallStage.RETRY_LAST,
                            "opt_in.wav",
                            callSummaryRecordDto.getWeekId(),
                            callSummaryRecordDto.getLanguageCode(),
                            callSummaryRecordDto.getCircleName(),
                            subscription.getOrigin(),
                            callSummaryRecordDto.getTargetFileTimeStamp(),
                            0,
                            callSummaryRecordDto.isOpt_in_call_eligibility()
                    )
            );
        }
    };


    @MotechListener(subjects = {KilkariConstants.NMS_IMI_KK_PROCESS_CSR_SUBJECT}) //NO CHECKSTYLE Cyclomatic Complexity
    @Transactional
    public void processCallSummaryRecord(MotechEvent event) { //NOPMD NcssMethodCount

        LOGGER.info("inside process call summary record");
        Timer timer = new Timer();
        String whatHappened = "##";

        String subscriptionId = "###INVALID###";
        try {
            CallSummaryRecordDto csrDto = CallSummaryRecordDto.fromParams(event.getParameters());
            LOGGER.info("this is csr dto : {}",csrDto);
            subscriptionId = csrDto.getSubscriptionId();
            LOGGER.info("this is the subscriptionId: {}",subscriptionId);
            csrVerifierService.verify(csrDto);

            Subscription subscription = subscriptionDataService.findBySubscriptionId(subscriptionId);
            if (subscription == null) {
                throw new NoSuchSubscriptionException(subscriptionId);
            }

            CallRetry callRetry = callRetryDataService.findBySubscriptionId(subscriptionId);
            LOGGER.info("this is call retry: {}",callRetry);
            LOGGER.info("this is the final status: {}",FinalCallStatus.fromInt(csrDto.getFinalStatus()));
            switch (FinalCallStatus.fromInt(csrDto.getFinalStatus())) {
                case SUCCESS:
                    completeSubscriptionIfNeeded(subscription, csrDto.getContentFileName());
                    if (callRetry != null) {

                        if (!callRetry.getContentFileName().equals("opt_in.wav")) {
                            callRetryDataService.delete(callRetry);
                        }
                    }
                    if(csrDto.isOpt_in_call_eligibility() && (csrDto.getContentFileName().equals(SubscriptionPackMessage.getWelcomeMessage().getMessageFileName())
                            || csrDto.getContentFileName().equals("opt_in.wav"))) {
                        switchIVRfromWhatsAppIfOpted(subscription, csrDto, callRetry);
                    }
                    whatHappened = "SU";
                    break;

                case FAILED:
                    String weekId = getWeekIdForSubscription(subscription.getStartDate());
                    //If there was a DOB/LMP update during RCH import, number of weeks into subscription would have changed.
                    //No need to reschedule this call. Exception for w1, because regardless of which week the subscription starts in, user
                    //always gets w1 message initially
                    LOGGER.info("this is the data: {},{}",csrDto.getWeekId(),weekId);




                    LOGGER.info("this is data in db for week: {}",subscription.getFirstMessageDayOfWeek());
                    LOGGER.info("this is data in csrdto: {}",DayOfTheWeek.getDayOfTheWeekFromTimestamp(csrDto.getTargetFileTimeStamp()));
                    LOGGER.info("this is the condition1: {}",!subscription.getFirstMessageDayOfWeek().equals(DayOfTheWeek.getDayOfTheWeekFromTimestamp(csrDto.getTargetFileTimeStamp())));
                    LOGGER.info("this is condition 2: {}", "1".equals(extractRouteNumber(csrDto.getServiceId())));
                    LOGGER.info("this is condition 2  and return : {}",extractRouteNumber(csrDto.getServiceId()));
                    LOGGER.info("this is service id: {}",csrDto.getServiceId());
                    if(!csrDto.getWeekId().equals("w1_1")&&!weekId.equals(csrDto.getWeekId())){
                        if(callRetry!=null){
                            LOGGER.info("inside call retry condition");
                            callRetryDataService.delete(callRetry);
                        }
                    }else if(callRetry == null && !subscription.getFirstMessageDayOfWeek().equals(DayOfTheWeek.getDayOfTheWeekFromTimestamp(csrDto.getTargetFileTimeStamp())) && "1".equals(extractRouteNumber(csrDto.getServiceId()))) {
                        LOGGER.info("inside fresh call condition after rch update");
                    }else if(callRetry == null && "2".equals(extractRouteNumber(csrDto.getServiceId()))){
                        LOGGER.info("inside retry call condition after rch update");
                    }else {
                        LOGGER.info("this is the data2: {}",csrDto.getTargetFileTimeStamp());
                        if (callRetry == null ||
                                !csrDto.getTargetFileTimeStamp().equals(callRetry.getTargetFiletimestamp())){
                            LOGGER.info("inside fresh or retryupdate else condition");
                            doReschedule(subscription, callRetry, csrDto);
                        }
                    }
                    whatHappened = "FA";
                    break;

                case REJECTED:
                    LOGGER.info("inside rejected");
                    handleDndForSubscription(subscription);
                    whatHappened = "RE";
                    break;

                default:
                    LOGGER.info("inside default");
                    String error = String.format("Invalid FinalCallStatus: %s", csrDto.getFinalStatus());
                    LOGGER.error(error);
                    alertService.create(subscriptionId, KilkariConstants.NMS_IMI_KK_PROCESS_CSR_SUBJECT, error, AlertType.CRITICAL,
                            AlertStatus.NEW, 0, null);
            }

        } catch (NoSuchSubscriptionException e) {
            String msg = String.format("No such subscription %s", e.getMessage());
            LOGGER.error(msg);
            alertService.create(subscriptionId, "Invalid CSR Data", msg, AlertType.HIGH, AlertStatus.NEW, 0, null);
            whatHappened = "ES";
        } catch (InvalidCallRecordDataException e) {
            String msg = String.format("Invalid CDR data for subscription %s: %s", subscriptionId, e.getMessage());
            LOGGER.error(msg);
            alertService.create(subscriptionId, "Invalid CSR Data", msg, AlertType.HIGH, AlertStatus.NEW, 0, null);
            whatHappened = "EI";
        } catch (Exception e) {
            String msg = String.format("MOTECH BUG *** Unexpected exception in processCallSummaryRecord() for " +
                    "subscription %s: %s", subscriptionId, ExceptionUtils.getFullStackTrace(e));
            LOGGER.error(msg);
            alertService.create(subscriptionId, KilkariConstants.NMS_IMI_KK_PROCESS_CSR_SUBJECT,
                    msg.substring(0, min(msg.length(), KilkariConstants.MAX_CHAR_ALERT)), AlertType.CRITICAL, AlertStatus.NEW, 0, null);
            whatHappened = "E!";
        }

        LOGGER.debug(String.format("processCallSummaryRecord %s %s %s", subscriptionId, whatHappened, timer.time()));
    }

    private String extractRouteNumber(String serviceId) {
        if (serviceId != null && serviceId.contains("Retryonroute")) {
            LOGGER.info("inside this1");
            int startIndex = serviceId.indexOf("Retryonroute") + "Retryonroute".length();
            if (startIndex < serviceId.length()) {
                LOGGER.info("inside this2");
                return String.valueOf(serviceId.charAt(startIndex));
            }
        }
        return "";
    }

    @MotechListener(subjects = {KilkariConstants.NMS_IMI_KK_WHATSAPP_SMS_PROCESS_CSR_SUBJECT}) //NO CHECKSTYLE Cyclomatic Complexity
    @Transactional
    public void processWhatsAppSMSCsr(MotechEvent event) { //NOPMD NcssMethodCount

        Timer timer = new Timer();
        String whatHappened = "##";

        String subscriptionId = "###INVALID###";
        try {
            LOGGER.debug("test 20 - WhatsAppOptSMSCsrDto.fromParams");
            WhatsAppOptSMSCsrDto csrDto = WhatsAppOptSMSCsrDto.fromParams(event.getParameters());
            subscriptionId = csrDto.getRequestId();
//            csrVerifierService.verify(csrDto);
            LOGGER.debug("test 21 - subscriptionDataService.findBySubscriptionIdAndStatus");
            Subscription subscription = subscriptionDataService.findBySubscriptionIdAndStatus(subscriptionId, SubscriptionStatus.ACTIVE);
            if (subscription == null) {
                throw new NoSuchSubscriptionException(subscriptionId);
            }
            LOGGER.debug("test 22 - updateSubscriptionServiceStatusForWhatsAppSMS");
            updateSubscriptionServiceStatusForWhatsAppSMS(subscription, csrDto.getResponse(), (List<Subscription>) event.getParameters().get("subscriptions"));

        } catch (NoSuchSubscriptionException e) {
            String msg = String.format("No such subscription %s", e.getMessage());
            LOGGER.error(msg);
            alertService.create(subscriptionId, "Invalid CSR Data", msg, AlertType.HIGH, AlertStatus.NEW, 0, null);
            whatHappened = "ES";
        } catch (Exception e) {
            String msg = String.format("MOTECH BUG *** Unexpected exception in processCallSummaryRecord() for " +
                    "subscription %s: %s", subscriptionId, ExceptionUtils.getFullStackTrace(e));
            LOGGER.error(msg);
            alertService.create(subscriptionId, KilkariConstants.NMS_IMI_KK_PROCESS_CSR_SUBJECT,
                    msg.substring(0, min(msg.length(), KilkariConstants.MAX_CHAR_ALERT)), AlertType.CRITICAL, AlertStatus.NEW, 0, null);
            whatHappened = "E!";
        }

        LOGGER.debug(String.format("processWhatsAppSMSCsr %s %s %s", subscriptionId, whatHappened, timer.time()));
    }

    @MotechListener(subjects = {KilkariConstants.NMS_IMI_KK_WHATSAPP_PROCESS_CSR_SUBJECT}) //NO CHECKSTYLE Cyclomatic Complexity
    @Transactional
    public void processWhatsAppCsr(MotechEvent event) { //NOPMD NcssMethodCount

        Timer timer = new Timer();
        String whatHappened = "##";

        String subscriptionId = "###INVALID###";
        try {
            LOGGER.debug("test 20 - WhatsAppOptCsrDto.fromParams");
            WhatsAppOptCsrDto csrDto = WhatsAppOptCsrDto.fromParams(event.getParameters());
            LOGGER.debug("csrDto: {}", csrDto);
            subscriptionId = csrDto.getExternalId();
//            csrVerifierService.verify(csrDto);
            LOGGER.debug("test 21 - subscriptionDataService.findBySubscriptionIdAndStatus");
            Subscription subscription = subscriptionDataService.findBySubscriptionIdAndStatus(subscriptionId, SubscriptionStatus.ACTIVE);
            LOGGER.debug("subscription: {}", subscription);
            if (subscription == null) {
                throw new NoSuchSubscriptionException(subscriptionId);
            }
            LOGGER.debug("test 22 - updateSubscriptionServiceStatusForWhatsApp");
            updateSubscriptionServiceStatusForWhatsApp(subscription, csrDto.getMessageStatus(), (List<Subscription>) event.getParameters().get("subscriptions"));
            LOGGER.debug("subscription: {}", subscription);
        } catch (NoSuchSubscriptionException e) {
            String msg = String.format("No such subscription %s", e.getMessage());
            LOGGER.error(msg);
            alertService.create(subscriptionId, "Invalid CSR Data", msg, AlertType.HIGH, AlertStatus.NEW, 0, null);
            whatHappened = "ES";
        } catch (Exception e) {
            String msg = String.format("MOTECH BUG *** Unexpected exception in processCallSummaryRecord() for " +
                    "subscription %s: %s", subscriptionId, ExceptionUtils.getFullStackTrace(e));
            LOGGER.error(msg);
            alertService.create(subscriptionId, KilkariConstants.NMS_IMI_KK_PROCESS_CSR_SUBJECT,
                    msg.substring(0, min(msg.length(), KilkariConstants.MAX_CHAR_ALERT)), AlertType.CRITICAL, AlertStatus.NEW, 0, null);
            whatHappened = "E!";
        }

        LOGGER.debug(String.format("processWhatsAppSMSCsr %s %s %s", subscriptionId, whatHappened, timer.time()));
    }

    private void updateSubscriptionServiceStatusForWhatsAppSMS(Subscription subscription, WhatsAppOptInResponse response, List<Subscription> subscriptions) {
        if(response == WhatsAppOptInResponse.OPTED){
            subscription.setServiceStatus(ServiceStatus.IVR_AND_WHATSAPP);
            subscription.setWhatsAppSelfOptIn(true);
            subscription.setWpStartDate(new DateTime().plusDays(1));
        }
        else if (response == WhatsAppOptInResponse.NOT_OPTED){
            subscription.setServiceStatus(ServiceStatus.IVR);
            subscription.setWhatsAppSelfOptIn(false);
        }
        subscriptions.add(subscription);
    }

    private void updateSubscriptionServiceStatusForWhatsApp(Subscription subscription, WhatsAppMessageStatus status, List<Subscription> subscriptions) {
        if( status == WhatsAppMessageStatus.SENT ){
            return;
        }
        //What if beneficiary phone no. got changed before updating the status.
        else if( status == WhatsAppMessageStatus.READ || status == WhatsAppMessageStatus.DELIVERED ){
            if(!subscription.isWhatsAppCdrStatus() && subscription.isWhatsAppSelfOptIn()){
                subscription.setWhatsAppCdrStatus(true);
                subscription.setServiceStatus(ServiceStatus.WHATSAPP);
            }
        }
        else {
            if(status == WhatsAppMessageStatus.UNDELIVERED){
                subscription.setWpDeactivationReason(WhatsAppDeactivationReason.MESSAGE_DELIVERY_FAILURE);
            }
            else{
                subscription.setWpDeactivationReason(WhatsAppDeactivationReason.UNSUBSCRIBED_BY_USER);
            }
            subscription.setServiceStatus(ServiceStatus.IVR);
            subscription.setWpEndDate(new DateTime());
        }
        subscriptions.add(subscription);
//        subscriptionDataService.update(subscription);
    }




    private void resetWelcomeFlagInSubscription(Subscription subscription) {

        if (subscription.getNeedsWelcomeMessageViaObd()) {
            subscription.setNeedsWelcomeMessageViaObd(false);
            subscriptionDataService.update(subscription);
        }
    }

    private String getWeekIdForSubscription(DateTime startDate) {
        int daysIntoSubscription = Days.daysBetween(startDate, DateTime.now()).getDays();
        int currentWeek = (daysIntoSubscription / 7) + 1;
        return String.format("w%d_%d", currentWeek, 1);
    }

}

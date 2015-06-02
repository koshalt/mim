package org.motechproject.nms.kilkari.it;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.motechproject.alerts.contract.AlertService;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.EventRelay;
import org.motechproject.mds.config.SettingsService;
import org.motechproject.nms.kilkari.domain.CallRetry;
import org.motechproject.nms.kilkari.domain.CallStage;
import org.motechproject.nms.kilkari.domain.CallSummaryRecord;
import org.motechproject.nms.kilkari.domain.DeactivationReason;
import org.motechproject.nms.kilkari.domain.Subscriber;
import org.motechproject.nms.kilkari.domain.Subscription;
import org.motechproject.nms.kilkari.domain.SubscriptionOrigin;
import org.motechproject.nms.kilkari.domain.SubscriptionPack;
import org.motechproject.nms.kilkari.domain.SubscriptionPackType;
import org.motechproject.nms.kilkari.domain.SubscriptionStatus;
import org.motechproject.nms.kilkari.dto.CallSummaryRecordDto;
import org.motechproject.nms.kilkari.repository.CallRetryDataService;
import org.motechproject.nms.kilkari.repository.CallSummaryRecordDataService;
import org.motechproject.nms.kilkari.repository.SubscriberDataService;
import org.motechproject.nms.kilkari.repository.SubscriptionDataService;
import org.motechproject.nms.kilkari.repository.SubscriptionPackDataService;
import org.motechproject.nms.kilkari.service.CsrService;
import org.motechproject.nms.kilkari.service.SubscriptionService;
import org.motechproject.nms.kilkari.utils.SubscriptionPackBuilder;
import org.motechproject.nms.props.domain.DayOfTheWeek;
import org.motechproject.nms.props.domain.FinalCallStatus;
import org.motechproject.nms.props.domain.RequestId;
import org.motechproject.nms.props.domain.StatusCode;
import org.motechproject.nms.region.domain.Circle;
import org.motechproject.nms.region.domain.District;
import org.motechproject.nms.region.domain.Language;
import org.motechproject.nms.region.domain.LanguageLocation;
import org.motechproject.nms.region.domain.State;
import org.motechproject.nms.region.repository.CircleDataService;
import org.motechproject.nms.region.repository.DistrictDataService;
import org.motechproject.nms.region.repository.LanguageDataService;
import org.motechproject.nms.region.repository.LanguageLocationDataService;
import org.motechproject.nms.region.repository.StateDataService;
import org.motechproject.testing.osgi.BasePaxIT;
import org.motechproject.testing.osgi.container.MotechNativeTestContainerFactory;
import org.ops4j.pax.exam.ExamFactory;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
@ExamFactory(MotechNativeTestContainerFactory.class)
public class CsrServiceBundleIT extends BasePaxIT {

    private static final String PROCESS_SUMMARY_RECORD_SUBJECT = "nms.imi.kk.process_summary_record";
    private static final String CSR_PARAM_KEY = "csr";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormat.forPattern("yyyyMMddHHmmss");

    @Inject
    EventRelay eventRelay;

    @Inject
    CsrService csrService;

    @Inject
    private SettingsService settingsService;

    @Inject
    private SubscriptionService subscriptionService;

    @Inject
    private SubscriptionDataService subscriptionDataService;

    @Inject
    private SubscriberDataService subscriberDataService;

    @Inject
    private LanguageDataService languageDataService;

    @Inject
    private CallRetryDataService callRetryDataService;

    @Inject
    private CallSummaryRecordDataService csrDataService;

    @Inject
    private AlertService alertService;

    @Inject
    private LanguageLocationDataService languageLocationDataService;

    @Inject
    private CircleDataService circleDataService;

    @Inject
    private StateDataService stateDataService;

    @Inject
    private DistrictDataService districtDataService;

    @Inject
    private SubscriptionPackDataService subscriptionPackDataService;

    @Before
    public void cleanupDatabase() {
        for (Subscription subscription: subscriptionDataService.retrieveAll()) {
            subscription.setStatus(SubscriptionStatus.COMPLETED);
            subscription.setEndDate(new DateTime().withDate(2011, 8, 1));

            subscriptionDataService.update(subscription);
        }

        subscriptionService.deleteAll();
        subscriberDataService.deleteAll();
        languageLocationDataService.deleteAll();
        languageDataService.deleteAll();
        districtDataService.deleteAll();
        stateDataService.deleteAll();
        circleDataService.deleteAll();
        callRetryDataService.deleteAll();
        createSubscriptionPacks();
        csrService.buildMessageDurationCache();
    }


    @Test
    public void testServicePresent() {
        assertTrue(csrService != null);
    }


    private Language makeLanguage() {
        Language language = languageDataService.findByName("Hindi");
        if (language != null) {
            return language;
        }
        return languageDataService.create(new Language("Hindi"));
    }

    private LanguageLocation makeLanguageLocation() {
        LanguageLocation languageLocation = languageLocationDataService.findByCode("99");
        if (languageLocation != null) {
            return languageLocation;
        }

        Language language = makeLanguage();
        Circle circle = makeCircle();

        languageLocation = new LanguageLocation("99", circle, language, false);
        languageLocation.getDistrictSet().add(makeDistrict());
        return languageLocationDataService.create(languageLocation);
    }

    private Circle makeCircle() {
        Circle circle = circleDataService.findByName("XX");
        if (circle != null) {
            return circle;
        }

        return circleDataService.create(new Circle("XX"));
    }

    private State makeState() {
        State state = stateDataService.findByCode(1l);
        if (state != null) {
            return state;
        }

        state = new State();
        state.setName("State 1");
        state.setCode(1L);

        return stateDataService.create(state);
    }

    private District makeDistrict() {
        District district = districtDataService.findById(1L);
        if (district != null) {
            return district;
        }

        district = new District();
        district.setName("District 1");
        district.setRegionalName("District 1");
        district.setCode(1L);
        district.setState(makeState());

        return districtDataService.create(district);
    }

    private Long makeNumber() {
        return (long) (Math.random() * 9000000000L) + 1000000000L;
    }

    private void createSubscriptionPacks() {
        if (subscriptionPackDataService.byName("childPack") == null) {
            subscriptionPackDataService.create(
                    SubscriptionPackBuilder.createSubscriptionPack(
                            "childPack",
                            SubscriptionPackType.CHILD,
                            SubscriptionPackBuilder.CHILD_PACK_WEEKS,
                            1));
        }
        if (subscriptionPackDataService.byName("pregnancyPack") == null) {
            subscriptionPackDataService.create(
                    SubscriptionPackBuilder.createSubscriptionPack(
                            "pregnancyPack",
                            SubscriptionPackType.PREGNANCY,
                            SubscriptionPackBuilder.PREGNANCY_PACK_WEEKS,
                            2));
        }
    }

    private Subscription makeSubscription(SubscriptionOrigin origin, DateTime startDate) {
        createSubscriptionPacks();
        Subscriber subscriber = subscriberDataService.create(new Subscriber(
                makeNumber(),
                makeLanguageLocation(),
                makeCircle()
        ));
        SubscriptionPack subscriptionPack = subscriptionService.getSubscriptionPack("childPack");
        Subscription subscription = new Subscription(subscriber, subscriptionPack, origin);
        subscription.setStartDate(startDate);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription = subscriptionService.create(subscription);
        getLogger().debug("Created subscription {}", subscription.toString());
        return subscription;
    }

    private Subscription makeSubscriptionForMotherPack(SubscriptionOrigin origin, DateTime startDate) {
        createSubscriptionPacks();
        Subscriber subscriber = subscriberDataService.create(new Subscriber(
                makeNumber(),
                makeLanguageLocation(),
                makeCircle()
        ));
        SubscriptionPack subscriptionPack = subscriptionService.getSubscriptionPack("pregnancyPack");
        Subscription subscription = new Subscription(subscriber, subscriptionPack, origin);
        subscription.setStartDate(startDate);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription = subscriptionService.create(subscription);
        getLogger().debug("Created subscription {}", subscription.toString());
        return subscription;
    }

    private Map<Integer, Integer> makeStatsMap(StatusCode statusCode, int count) {
        Map<Integer, Integer> map = new HashMap<>();
        map.put(statusCode.getValue(), count);
        return map;
    }


    @Test
    public void verifyServiceFunctional() {
        Subscription subscription = makeSubscription(SubscriptionOrigin.IVR, DateTime.now().minusDays(14));

        CallSummaryRecordDto csr = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_INVALIDNUMBER, 3),
                0,
                3
        );

        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr);
        MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);
    }

    //NMS_FT_175
    // Deactivate if user phone number does not exist
    // https://github.com/motech-implementations/mim/issues/169
    @Test
    public void verifyIssue169() {
        Subscription subscription = makeSubscription(SubscriptionOrigin.IVR, DateTime.now().minusDays(14));
        Subscriber subscriber = subscription.getSubscriber();

        LanguageLocation languageLocation;
        languageLocation = (LanguageLocation) subscriberDataService.getDetachedField(subscriber,
                "languageLocation");

        Circle circle;
        circle = (Circle) subscriberDataService.getDetachedField(subscriber, "circle");


        csrDataService.create(new CallSummaryRecord(
                new RequestId(subscription.getSubscriptionId(), "11112233445566").toString(),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_INVALIDNUMBER, 10),
                0,
                10,
                3
        ));

        callRetryDataService.create(new CallRetry(
                subscription.getSubscriptionId(),
                subscription.getSubscriber().getCallingNumber(),
                DayOfTheWeek.today(),
                CallStage.RETRY_LAST,
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                SubscriptionOrigin.MCTS_IMPORT
        ));

        CallSummaryRecordDto csr = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_INVALIDNUMBER, 3),
                0,
                3
        );

        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr);
        MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);

        subscription = subscriptionDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertEquals(SubscriptionStatus.DEACTIVATED, subscription.getStatus());
        assertEquals(DeactivationReason.INVALID_NUMBER, subscription.getDeactivationReason());
        CallRetry callRetry = callRetryDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertNull(callRetry);
    }

    @Test //NMS_FT_140
    public void verifyCallRetryDeletedWhenAllRetryFailed() {
        Subscription subscription = makeSubscription(SubscriptionOrigin.IVR, DateTime.now().minusDays(14));
        Subscriber subscriber = subscription.getSubscriber();

        LanguageLocation languageLocation;
        languageLocation = (LanguageLocation) subscriberDataService.getDetachedField(subscriber,
                "languageLocation");

        Circle circle;
        circle = (Circle) subscriberDataService.getDetachedField(subscriber, "circle");


        csrDataService.create(new CallSummaryRecord(
                new RequestId(subscription.getSubscriptionId(), "11112233445566").toString(),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_NOANSWER, 10),
                0,
                10,
                3
        ));

        callRetryDataService.create(new CallRetry(
                subscription.getSubscriptionId(),
                subscription.getSubscriber().getCallingNumber(),
                DayOfTheWeek.today(),
                CallStage.RETRY_LAST,
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                SubscriptionOrigin.MCTS_IMPORT
        ));

        CallSummaryRecordDto csr = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_NOANSWER, 3),
                0,
                3
        );

        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr);
        MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);

        CallRetry callRetry = callRetryDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertNull(callRetry);

    }

    @Test //NMS_FT_144
    public void verifyOBDRescheduledWhenFailedAsIVRDidNotAttempt() {
        Subscription subscription = makeSubscription(SubscriptionOrigin.IVR, DateTime.now().minusDays(14));

        csrDataService.create(new CallSummaryRecord(
                new RequestId(subscription.getSubscriptionId(), "11112233445566").toString(),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_NOATTEMPT, 1),
                0,
                10,
                3
        ));

        callRetryDataService.create(new CallRetry(
                subscription.getSubscriptionId(),
                subscription.getSubscriber().getCallingNumber(),
                DayOfTheWeek.today(),
                CallStage.RETRY_1,
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                SubscriptionOrigin.MCTS_IMPORT
        ));

        CallSummaryRecordDto csr = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_NOATTEMPT, 1),
                0,
                3
        );

        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr);
        MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);

        CallRetry callRetry = callRetryDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertNotNull(callRetry);
        assertTrue(CallStage.RETRY_2.equals(callRetry.getCallStage()));
    }

    @Test //NMS_FT_145
    public void verifyOBDRescheduledWhenFailedDueToUserNumberBusy() {
        Subscription subscription = makeSubscription(SubscriptionOrigin.IVR, DateTime.now().minusDays(14));

        csrDataService.create(new CallSummaryRecord(
                new RequestId(subscription.getSubscriptionId(), "11112233445566").toString(),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_BUSY, 1),
                0,
                10,
                3
        ));

        callRetryDataService.create(new CallRetry(
                subscription.getSubscriptionId(),
                subscription.getSubscriber().getCallingNumber(),
                DayOfTheWeek.today(),
                CallStage.RETRY_1,
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                SubscriptionOrigin.MCTS_IMPORT
        ));

        CallSummaryRecordDto csr = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_BUSY, 1),
                0,
                3
        );

        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr);
        MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);

        CallRetry callRetry = callRetryDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertNotNull(callRetry);
        assertTrue(CallStage.RETRY_2.equals(callRetry.getCallStage()));
    }

    @Test //NMS_FT_146
    public void verifyOBDRescheduledWhenFailedDueToUnansweredCall() {
        Subscription subscription = makeSubscription(SubscriptionOrigin.IVR, DateTime.now().minusDays(14));

        csrDataService.create(new CallSummaryRecord(
                new RequestId(subscription.getSubscriptionId(), "11112233445566").toString(),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_NOANSWER, 1),
                0,
                10,
                3
        ));

        callRetryDataService.create(new CallRetry(
                subscription.getSubscriptionId(),
                subscription.getSubscriber().getCallingNumber(),
                DayOfTheWeek.today(),
                CallStage.RETRY_1,
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                SubscriptionOrigin.MCTS_IMPORT
        ));

        CallSummaryRecordDto csr = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_NOANSWER, 1),
                0,
                3
        );

        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr);
        MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);

        CallRetry callRetry = callRetryDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertNotNull(callRetry);
        assertTrue(CallStage.RETRY_2.equals(callRetry.getCallStage()));
    }

    @Test //NMS_FT_147
    public void verifyOBDRescheduledWhenFailedDueToNumberSwitchedOff() {
        Subscription subscription = makeSubscription(SubscriptionOrigin.IVR, DateTime.now().minusDays(14));

        csrDataService.create(new CallSummaryRecord(
                new RequestId(subscription.getSubscriptionId(), "11112233445566").toString(),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_SWITCHEDOFF, 1),
                0,
                10,
                3
        ));

        callRetryDataService.create(new CallRetry(
                subscription.getSubscriptionId(),
                subscription.getSubscriber().getCallingNumber(),
                DayOfTheWeek.today(),
                CallStage.RETRY_1,
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                SubscriptionOrigin.MCTS_IMPORT
        ));

        CallSummaryRecordDto csr = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_SWITCHEDOFF, 1),
                0,
                3
        );

        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr);
        MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);

        CallRetry callRetry = callRetryDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertNotNull(callRetry);
        assertTrue(CallStage.RETRY_2.equals(callRetry.getCallStage()));
    }

    @Test //NMS_FT_149
    public void verifyOBDNotRetriedWhenRejectedDueToDNDForMCTSImport() {
        Subscription subscription = makeSubscription(SubscriptionOrigin.MCTS_IMPORT, DateTime.now().minusDays(14));

        csrDataService.create(new CallSummaryRecord(
                new RequestId(subscription.getSubscriptionId(), "11112233445566").toString(),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.REJECTED,
                makeStatsMap(StatusCode.OBD_DNIS_IN_DND, 1),
                0,
                10,
                3
        ));

        callRetryDataService.create(new CallRetry(
                subscription.getSubscriptionId(),
                subscription.getSubscriber().getCallingNumber(),
                DayOfTheWeek.today(),
                CallStage.RETRY_1,
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                SubscriptionOrigin.MCTS_IMPORT
        ));

        CallSummaryRecordDto csr = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.REJECTED,
                makeStatsMap(StatusCode.OBD_DNIS_IN_DND, 1),
                0,
                3
        );

        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr);
        MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);

        CallRetry callRetry = callRetryDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertNull(callRetry);

    }
    
    @Test //NMS_FT_141
    public void verifyOBDRescheduledForMotherPack() {
        Subscription subscription = makeSubscriptionForMotherPack(SubscriptionOrigin.IVR, DateTime.now().minusDays(14));
        
        csrDataService.create(new CallSummaryRecord(
                new RequestId(subscription.getSubscriptionId(), "11112233445566").toString(),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_BUSY, 1),
                0,
                10,
                3
        ));
        
        CallSummaryRecordDto csr = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_BUSY, 1),
                0,
                3
        );
        
        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr);
        MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);
        
        CallRetry callRetry = callRetryDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertNotNull(callRetry);
        assertTrue(CallStage.RETRY_1.equals(callRetry.getCallStage()));
    }
    
    @Test //NMS_FT_142
    public void verifyOBDNotRescheduledForMotherPackIfAlreadyRetried() {
        Subscription subscription = makeSubscriptionForMotherPack(SubscriptionOrigin.IVR, DateTime.now().minusDays(14));
        
        csrDataService.create(new CallSummaryRecord(
                new RequestId(subscription.getSubscriptionId(), "11112233445566").toString(),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_BUSY, 1),
                0,
                10,
                3
        ));
        
        callRetryDataService.create(new CallRetry(
                subscription.getSubscriptionId(),
                subscription.getSubscriber().getCallingNumber(),
                DayOfTheWeek.today(),
                CallStage.RETRY_1,
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                SubscriptionOrigin.MCTS_IMPORT
        ));
        
        CallSummaryRecordDto csr = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_BUSY, 1),
                0,
                3
        );
        
        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr);
        MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);
        
        CallRetry callRetry = callRetryDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertNull(callRetry);
    }
    
    @Test //NMS_FT_147
    public void verifyOBDRescheduledWhenFailedDueOtherReason() {
        Subscription subscription = makeSubscription(SubscriptionOrigin.IVR, DateTime.now().minusDays(14));
        
        csrDataService.create(new CallSummaryRecord(
                new RequestId(subscription.getSubscriptionId(), "11112233445566").toString(),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_OTHERS, 1),
                0,
                10,
                3
        ));
        
        callRetryDataService.create(new CallRetry(
                subscription.getSubscriptionId(),
                subscription.getSubscriber().getCallingNumber(),
                DayOfTheWeek.today(),
                CallStage.RETRY_1,
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                SubscriptionOrigin.MCTS_IMPORT
        ));
        
        CallSummaryRecordDto csr = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_OTHERS, 1),
                0,
                3
        );
        
        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr);
        MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);
        
        CallRetry callRetry = callRetryDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertNotNull(callRetry);
        assertTrue(CallStage.RETRY_2.equals(callRetry.getCallStage()));
    }
    
    @Test //NMS_FT_165
    public void verifyPregnancyPackMarkCompleted() {
        Subscription subscription = makeSubscriptionForMotherPack(SubscriptionOrigin.IVR, DateTime.now().minusDays(14));
        
        csrDataService.create(new CallSummaryRecord(
                new RequestId(subscription.getSubscriptionId(), "11112233445566").toString(),
                subscription.getSubscriber().getCallingNumber(),
                "w72_2.wav",
                "w72_2",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.SUCCESS,
                makeStatsMap(StatusCode.OBD_SUCCESS_CALL_CONNECTED, 1),
                0,
                10,
                3
        ));
        
        CallSummaryRecordDto csr = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w72_2.wav",
                "w72_2",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.SUCCESS,
                makeStatsMap(StatusCode.OBD_SUCCESS_CALL_CONNECTED, 1),
                0,
                3
        );
        
        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr);
        MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);
        
        subscription = subscriptionDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertEquals(SubscriptionStatus.COMPLETED, subscription.getStatus());
    }
    
    @Test //NMS_FT_166
    public void verifyChildPackMarkCompleted() {
        Subscription subscription = makeSubscription(SubscriptionOrigin.IVR, DateTime.now().minusDays(14));
        
        csrDataService.create(new CallSummaryRecord(
                new RequestId(subscription.getSubscriptionId(), "11112233445566").toString(),
                subscription.getSubscriber().getCallingNumber(),
                "w48_1.wav",
                "w48_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.SUCCESS,
                makeStatsMap(StatusCode.OBD_SUCCESS_CALL_CONNECTED, 1),
                0,
                10,
                3
        ));
        
        CallSummaryRecordDto csr = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w48_1.wav",
                "w48_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.SUCCESS,
                makeStatsMap(StatusCode.OBD_SUCCESS_CALL_CONNECTED, 1),
                0,
                3
        );
        
        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr);
        MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);
        
        subscription = subscriptionDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertEquals(SubscriptionStatus.COMPLETED, subscription.getStatus());
    }
    
    @Test //NMS_FT_167
    public void verifyPregnancyPackMarkCompletedAfterFirstRetry() {
        Subscription subscription = makeSubscriptionForMotherPack(SubscriptionOrigin.IVR, DateTime.now().minusDays(14));
        
        csrDataService.create(new CallSummaryRecord(
                new RequestId(subscription.getSubscriptionId(), "11112233445566").toString(),
                subscription.getSubscriber().getCallingNumber(),
                "w72_2.wav",
                "w72_2",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_OTHERS, 1),
                0,
                10,
                3
        ));
        
        callRetryDataService.create(new CallRetry(
                subscription.getSubscriptionId(),
                subscription.getSubscriber().getCallingNumber(),
                DayOfTheWeek.today(),
                CallStage.RETRY_1,
                "w72_2.wav",
                "w72_2",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                SubscriptionOrigin.MCTS_IMPORT
        ));
        
        CallSummaryRecordDto csr = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w72_2.wav",
                "w72_2",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_OTHERS, 1),
                0,
                3
        );
        
        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr);
        MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);
        
        subscription = subscriptionDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertEquals(SubscriptionStatus.COMPLETED, subscription.getStatus());
    }
    
    @Test //NMS_FT_168
    public void verifyChildPackMarkCompletedIfRetriesExhausted() {
        Subscription subscription = makeSubscription(SubscriptionOrigin.IVR, DateTime.now().minusDays(14));
        
        csrDataService.create(new CallSummaryRecord(
                new RequestId(subscription.getSubscriptionId(), "11112233445566").toString(),
                subscription.getSubscriber().getCallingNumber(),
                "w48_1.wav",
                "w48_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_OTHERS, 1),
                0,
                10,
                3
        ));
        
        callRetryDataService.create(new CallRetry(
                subscription.getSubscriptionId(),
                subscription.getSubscriber().getCallingNumber(),
                DayOfTheWeek.today(),
                CallStage.RETRY_LAST,
                "w48_1.wav",
                "w48_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                SubscriptionOrigin.MCTS_IMPORT
        ));
        
        CallSummaryRecordDto csr = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w48_1.wav",
                "w48_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_OTHERS, 1),
                0,
                3
        );
        
        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr);
        MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);
        
        subscription = subscriptionDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertEquals(SubscriptionStatus.COMPLETED, subscription.getStatus());
    }

    //Test to added in FT plan later
    //This test case verifies that subscription for child pack is not marked completed
    //if all reties are not exhausted.
    @Test
    public void verifyChildPackIsNotMarkedCompletedIfAllRetriesAreNotExhausted() {
        Subscription subscription = makeSubscription(SubscriptionOrigin.IVR, DateTime.now().minusDays(14));

        csrDataService.create(new CallSummaryRecord(
                new RequestId(subscription.getSubscriptionId(), "11112233445566").toString(),
                subscription.getSubscriber().getCallingNumber(),
                "w48_1.wav",
                "w48_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_OTHERS, 1),
                0,
                10,
                3
        ));

        callRetryDataService.create(new CallRetry(
                subscription.getSubscriptionId(),
                subscription.getSubscriber().getCallingNumber(),
                DayOfTheWeek.today(),
                CallStage.RETRY_1,
                "w48_1.wav",
                "w48_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                SubscriptionOrigin.MCTS_IMPORT
        ));

        CallSummaryRecordDto csr = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w48_1.wav",
                "w48_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_OTHERS, 1),
                0,
                3
        );

        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr);
        MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);

        subscription = subscriptionDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
        CallRetry callRetry = callRetryDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertNotNull(callRetry);
        assertTrue(CallStage.RETRY_2.equals(callRetry.getCallStage()));
    }

    @Test
    public void verifySubscriptionCompletion() {

        String timestamp = DateTime.now().toString(TIME_FORMATTER);

        CsrHelper helper = new CsrHelper(timestamp, subscriptionService, subscriberDataService,
                subscriptionPackDataService, languageDataService, languageLocationDataService, circleDataService,
                stateDataService, districtDataService);

        helper.makeRecords(1,3,0,0);

        for (CallSummaryRecordDto record : helper.getRecords()) {
            Map<String, Object> eventParams = new HashMap<>();
            eventParams.put(CSR_PARAM_KEY, record);
            MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
            csrService.processCallSummaryRecord(motechEvent);
        }

        List<Subscription> subscriptions = subscriptionDataService.findByStatus(SubscriptionStatus.COMPLETED);
        assertEquals(3, subscriptions.size());
    }

    //NMS_FT_176
    @Test
    public void verifySubscriptionForChildPackIsActiveIFAllRetriesAreNotFailedDueToInvalidNumber() {
        Subscription subscription = makeSubscription(SubscriptionOrigin.IVR, DateTime.now().minusDays(14));
        Subscriber subscriber = subscription.getSubscriber();

        csrDataService.create(new CallSummaryRecord(
                new RequestId(subscription.getSubscriptionId(), "11112233445566").toString(),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_INVALIDNUMBER, 1),
                0,
                10,
                3
        ));

        callRetryDataService.create(new CallRetry(
                subscription.getSubscriptionId(),
                subscription.getSubscriber().getCallingNumber(),
                DayOfTheWeek.today(),
                CallStage.RETRY_1,
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                SubscriptionOrigin.MCTS_IMPORT
        ));

        //first retry failed due to invalid number
        CallSummaryRecordDto csr = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_INVALIDNUMBER, 1),
                0,
                3
        );

        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr);
        MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);

        //subscription is still active
        subscription = subscriptionDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
        CallRetry callRetry = callRetryDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertNotNull(callRetry);
        assertTrue(CallStage.RETRY_2.equals(callRetry.getCallStage()));

        ////second retry failed due to number switched off
        CallSummaryRecordDto csr2 = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_SWITCHEDOFF, 1),
                0,
                3
        );
        eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr2);
        motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);

        //subscription is still active
        subscription = subscriptionDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
        CallRetry callRetry2 = callRetryDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertNotNull(callRetry2);
        assertTrue(CallStage.RETRY_LAST.equals(callRetry2.getCallStage()));

        //third retry failed due to invalid number
        CallSummaryRecordDto csr3 = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_INVALIDNUMBER, 1),
                0,
                3
        );
        eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr3);
        motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);

        //subscription should still be active as second retry was failed other than invalid number reason
        subscription = subscriptionDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());

    }

    //To be added in FT plan later
    //This test case verifies that subscription for mother pack is marked deactivated if all delivery
    // attempts(including retry) is failed due to user number invalid for a message
    @Test
    public void verifySubscriptionForMotherPackIsMarkDeactivatedIfAllRetiesFailedDueToInvalidNumber() {
        Subscription subscription = makeSubscriptionForMotherPack(SubscriptionOrigin.IVR, DateTime.now().minusDays(14));

        csrDataService.create(new CallSummaryRecord(
                new RequestId(subscription.getSubscriptionId(), "11112233445566").toString(),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_INVALIDNUMBER, 10),
                0,
                10,
                3
        ));

        callRetryDataService.create(new CallRetry(
                subscription.getSubscriptionId(),
                subscription.getSubscriber().getCallingNumber(),
                DayOfTheWeek.today(),
                CallStage.RETRY_1,
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                SubscriptionOrigin.MCTS_IMPORT
        ));

        CallSummaryRecordDto csr = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_INVALIDNUMBER, 3),
                0,
                3
        );

        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr);
        MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);

        subscription = subscriptionDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertEquals(SubscriptionStatus.DEACTIVATED, subscription.getStatus());
        assertEquals(DeactivationReason.INVALID_NUMBER, subscription.getDeactivationReason());
        CallRetry callRetry = callRetryDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertNull(callRetry);
    }

    //To be added in FT plan later
    //This test case verifies that subscription for mother pack is not marked deactivated if all delivery
    // attempts(including retry) is not failed due to user number invalid for a message
    @Test
    public void verifySubscriptionForMotherPackIsActiveIfAllRetiesAreNotFailedDueToInvalidNumber() {
        Subscription subscription = makeSubscriptionForMotherPack(SubscriptionOrigin.IVR, DateTime.now().minusDays(14));

        csrDataService.create(new CallSummaryRecord(
                new RequestId(subscription.getSubscriptionId(), "11112233445566").toString(),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_INVALIDNUMBER, 10),
                0,
                10,
                3
        ));

        callRetryDataService.create(new CallRetry(
                subscription.getSubscriptionId(),
                subscription.getSubscriber().getCallingNumber(),
                DayOfTheWeek.today(),
                CallStage.RETRY_1,
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                SubscriptionOrigin.MCTS_IMPORT
        ));

        //first retry due to number is switched off
        CallSummaryRecordDto csr = new CallSummaryRecordDto(
                new RequestId(subscription.getSubscriptionId(), "11112233445566"),
                subscription.getSubscriber().getCallingNumber(),
                "w1_1.wav",
                "w1_1",
                makeLanguageLocation().getCode(),
                makeCircle().getName(),
                FinalCallStatus.FAILED,
                makeStatsMap(StatusCode.OBD_FAILED_SWITCHEDOFF, 3),
                0,
                3
        );

        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(CSR_PARAM_KEY, csr);
        MotechEvent motechEvent = new MotechEvent(PROCESS_SUMMARY_RECORD_SUBJECT, eventParams);
        csrService.processCallSummaryRecord(motechEvent);

        subscription = subscriptionDataService.findBySubscriptionId(subscription.getSubscriptionId());
        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
    }

    //todo: verify multiple days' worth of summary record aggregation
    //todo: verify more stuff I can't think of now
}

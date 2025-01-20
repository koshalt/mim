package org.motechproject.nms.kilkari.service.impl;

import org.apache.commons.lang.StringUtils;
import org.datanucleus.store.rdbms.query.ForwardQueryResult;
import org.joda.time.DateTime;
import org.joda.time.Weeks;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.motechproject.commons.date.util.DateUtil;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.EventRelay;
import org.motechproject.event.listener.annotations.MotechListener;
import org.motechproject.mds.query.QueryExecution;
import org.motechproject.mds.query.QueryParams;
import org.motechproject.mds.query.SqlQueryExecution;
import org.motechproject.mds.util.InstanceSecurityRestriction;
import org.motechproject.metrics.service.Timer;
import org.motechproject.nms.kilkari.domain.*;
import org.motechproject.nms.kilkari.repository.*;
import org.motechproject.nms.kilkari.service.CsrVerifierService;
import org.motechproject.nms.kilkari.service.SubscriptionService;
import org.motechproject.nms.kilkari.utils.KilkariConstants;
import org.motechproject.nms.kilkari.utils.PhoneNumberHelper;
import org.motechproject.nms.props.domain.DayOfTheWeek;
import org.motechproject.nms.region.domain.*;
import org.motechproject.server.config.SettingsFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.jdo.Query;
import javax.validation.ConstraintViolationException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of the {@link SubscriptionService} interface.
 */
@Service("subscriptionService")
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionServiceImpl.class);

    private SettingsFacade settingsFacade;
    private SubscriberDataService subscriberDataService;
    private SubscriptionPackDataService subscriptionPackDataService;
    private SubscriptionDataService subscriptionDataService;
    private SubscriptionErrorDataService subscriptionErrorDataService;
    private CallRetryDataService callRetryDataService;
    private CsrVerifierService csrVerifierService;
    private EventRelay eventRelay;
    private boolean allowMctsSubscriptions;
    private BlockedMsisdnRecordDataService blockedMsisdnRecordDataService;
    private DeactivatedBeneficiaryDataService deactivatedBeneficiaryDataService;
    private SubscriberMsisdnTrackerDataService subscriberMsisdnTrackerDataService;
    public static AtomicBoolean isCapacityAvailable = new AtomicBoolean(true);
    private static final String HIGH_PRIORITY_BLOCK = "kilkari.highPriority.blockId";


    @Autowired
    public SubscriptionServiceImpl(@Qualifier("kilkariSettings") SettingsFacade settingsFacade, // NO CHECKSTYLE More than 7 parameters
                                   SubscriberDataService subscriberDataService,
                                   SubscriptionPackDataService subscriptionPackDataService,
                                   SubscriptionDataService subscriptionDataService,
                                   SubscriptionErrorDataService subscriptionErrorDataService,
                                   EventRelay eventRelay,
                                   CallRetryDataService callRetryDataService,
                                   CsrVerifierService csrVerifierService,
                                   BlockedMsisdnRecordDataService blockedMsisdnRecordDataService,
                                   DeactivatedBeneficiaryDataService deactivatedBeneficiaryDataService,
                                  //  ReactivatedBeneficiaryAuditDataService reactivatedBeneficiaryAuditDataService,
                                   SubscriberMsisdnTrackerDataService subscriberMsisdnTrackerDataService) {
        this.subscriberDataService = subscriberDataService;
        this.subscriptionPackDataService = subscriptionPackDataService;
        this.subscriptionDataService = subscriptionDataService;
        this.subscriptionErrorDataService = subscriptionErrorDataService;
        this.settingsFacade = settingsFacade;
        this.eventRelay = eventRelay;
        this.callRetryDataService = callRetryDataService;
        this.csrVerifierService = csrVerifierService;
        this.allowMctsSubscriptions = true;
        this.blockedMsisdnRecordDataService = blockedMsisdnRecordDataService;
        this.deactivatedBeneficiaryDataService = deactivatedBeneficiaryDataService;
   //     this.reactivatedBeneficiaryAuditDataService = reactivatedBeneficiaryAuditDataService;
        this.subscriberMsisdnTrackerDataService = subscriberMsisdnTrackerDataService;
    }

    public SubscriptionServiceImpl() {}

    @PostConstruct
    public Boolean acceptNewSubscriptionForBlockedMsisdn() {
        return  "true".equalsIgnoreCase(settingsFacade.getProperty(KilkariConstants.ACCEPT_NEW_SUBSCRIPTION_FOR_BLOCKED_MSISDN));
    }

    @Transactional
    public void purgeOldInvalidSubscriptions() {
        int weeksToKeepInvalidFLWs = Integer.parseInt(settingsFacade.getProperty(KilkariConstants.WEEKS_TO_KEEP_CLOSED_SUBSCRIPTIONS));
        final SubscriptionStatus completed = SubscriptionStatus.COMPLETED;
        final SubscriptionStatus deactivated = SubscriptionStatus.DEACTIVATED;
        final DateTime cutoff = DateTime.now().minusWeeks(weeksToKeepInvalidFLWs).withTimeAtStartOfDay();

        @SuppressWarnings("unchecked")
        QueryExecution<List<Subscription>> queryExecution = new QueryExecution<List<Subscription>>() {
            @Override
            public List<Subscription> execute(Query query, InstanceSecurityRestriction restriction) {

                query.setFilter("(status == completed || status == deactivated) && endDate < cutoff");
                query.declareParameters("org.motechproject.nms.kilkari.domain.SubscriptionStatus completed, " +
                        "org.motechproject.nms.kilkari.domain.SubscriptionStatus deactivated, " +
                        "org.joda.time.DateTime cutoff");


                return (List<Subscription>) query.execute(completed, deactivated, cutoff);
            }
        };

        List<Subscription> purgeList = subscriptionDataService.executeQuery(queryExecution);

        int purgedSubscribers = 0;
        int purgedSubscriptions = 0;
        for (Subscription subscription : purgeList) {
            String subscriptionId = subscription.getSubscriptionId();

            // If, for some reason, there is a retry record for that subscription, delete it too.
            CallRetry callRetry = callRetryDataService.findBySubscriptionId(subscription.getSubscriptionId());
            if (callRetry != null) {
                LOGGER.debug("Purging CallRetry record for subscription {}", subscription.getSubscriptionId());
                callRetryDataService.delete(callRetry);
            }

            LOGGER.debug("Purging subscription {}", subscription.getSubscriptionId());
            Subscriber subscriber = subscription.getSubscriber();
            subscriptionDataService.delete(subscription);

            // I need to load the subscriber since I deleted one of their subscription prior
            purgedSubscriptions++;
            if (subscriber.getSubscriptions().size() == 0) {
                LOGGER.debug("Purging subscriber for subscription {} as it was the last subscription for that subscriber",
                        subscriptionId);
                deleteSubscriber(subscriber.getId());
                purgedSubscribers++;
            }
        }

        LOGGER.info(String.format("Purged %s subscribers and %s subscriptions with status (%s or %s) and " + "endDate date before %s", purgedSubscribers, purgedSubscriptions, SubscriptionStatus.COMPLETED, SubscriptionStatus.DEACTIVATED, cutoff.toString()));
    }



    @Override
    public void completePastDueSubscriptions() {

        final DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        final DateTime currentTime = DateTime.now();
        final DateTime oldestPregnancyStart = currentTime.minusDays(KilkariConstants.PREGNANCY_PACK_LENGTH_DAYS).withTimeAtStartOfDay();
        final DateTime oldestChildStart = currentTime.minusDays(KilkariConstants.CHILD_PACK_LENGTH_DAYS).withTimeAtStartOfDay();

        LOGGER.debug("Completing active pregnancy susbscriptions older than {}", oldestPregnancyStart);
        LOGGER.debug("Completing active child susbscriptions older than {}", oldestChildStart);

        @SuppressWarnings("unchecked")
        SqlQueryExecution<Long> queryExecution = new SqlQueryExecution<Long>() {

            @Override
            public String getSqlQuery() {
                String query =
                        "UPDATE motech_data_services.nms_subscriptions AS s " +
                        "JOIN motech_data_services.nms_subscription_packs AS sp " +
                        "ON s.subscriptionPack_id_OID = sp.id " +
                        "SET s.status = " +
                        "(Case when s.status = 'HOLD' and s.activationDate is null THEN 'PACK_NOT_INITIATED_DUE_TO_ON_HOLD_DATA' " +
                        "ELSE 'COMPLETED' " +
                        "END), s.endDate = :currentTime, s.modificationDate = :currentTime " +
                        "WHERE " +
                        "(s.status = 'ACTIVE' OR s.status = 'PENDING_ACTIVATION' OR s.status = 'HOLD') AND " +
                        "((sp.type = 'PREGNANCY' AND s.startDate < :oldestPregnancyStart) " +
                        "OR " +
                        "(sp.type = 'CHILD' AND s.startDate < :oldestChildStart))";
                LOGGER.debug(KilkariConstants.SQL_QUERY_LOG, query);
                return query;
            }

            @Override
            public Long execute(Query query) {
                Map params = new HashMap();
                params.put("currentTime", currentTime.toString(dateTimeFormatter));
                params.put("oldestPregnancyStart", oldestPregnancyStart.toString(dateTimeFormatter));
                params.put("oldestChildStart", oldestChildStart.toString(dateTimeFormatter));
                return (Long) query.executeWithMap(params);
            }
        };

        Long rowCount = subscriptionDataService.executeSQLQuery(queryExecution);
        LOGGER.debug(String.format("Updated %d subscription(s) to COMPLETED", rowCount));
        subscriptionDataService.evictEntityCache(true); // no need to evict sub-entity classes
    }

    @Override
    public void deletePreconditionCheck(Subscription subscription) {
        int weeksToKeepInvalidFLWs = Integer.parseInt(settingsFacade.getProperty(KilkariConstants.WEEKS_TO_KEEP_CLOSED_SUBSCRIPTIONS));
        DateTime now = new DateTime();

        if (subscription.getStatus() != SubscriptionStatus.COMPLETED &&
                subscription.getStatus() != SubscriptionStatus.DEACTIVATED) {
            throw new IllegalStateException("Can not delete an open subscription");
        }

        if (subscription.getEndDate() == null) {
            throw new IllegalStateException("Subscription in closed state with null end date");
        }

        if (Math.abs(Weeks.weeksBetween(now, subscription.getEndDate()).getWeeks()) < weeksToKeepInvalidFLWs) {
            throw new IllegalStateException(String.format("Subscription must be closed for %s weeks before deleting",
                                            weeksToKeepInvalidFLWs));
        }
    }

    public String getBeneficiaryId(Subscriber subscriber, SubscriptionOrigin mode, SubscriptionPack subscriptionPack) {
        if (mode == SubscriptionOrigin.IVR) {
            return "";
        } else if (mode == SubscriptionOrigin.MCTS_IMPORT) {
            return (subscriptionPack.getType() == SubscriptionPackType.PREGNANCY) ? subscriber.getMother().getBeneficiaryId() : subscriber.getChild().getBeneficiaryId();
        } else {
            return (subscriptionPack.getType() == SubscriptionPackType.PREGNANCY) ? subscriber.getMother().getRchId() : subscriber.getChild().getRchId();
        }
    }

    @Override
    public Subscription createSubscription(Subscriber subscriber, long callingNumber, Language language,
                                           SubscriptionPack subscriptionPack, SubscriptionOrigin mode) {

        // call overload with null circle
        return createSubscription(subscriber, callingNumber, language, null, subscriptionPack, mode);
    }

    @Override // NO CHECKSTYLE Cyclomatic Complexity
    public Boolean activeSubscriptionByMsisdnMcts(Subscriber subscriber,Long msisdn, SubscriptionPackType packType, String motherBeneficiaryId, String childBeneficiaryId) {
        //List<Subscriber> subscribers = subscriberService.getSubscriber(msisdn);
        int subscriptionsSize = 0;
        if (packType == SubscriptionPackType.PREGNANCY) {
            //if (subscribers.size() != 0) {
//                for (Subscriber subscriber : subscribers
//                        ) {
                    List<Subscription> subscriptions = getActiveSubscriptionBySubscriber(subscriber);
                    subscriptionsSize = subscriptions.size();
                    if (subscriptionsSize != 0) {
                        if (subscriptionsSize == 1) {
                            if (subscriptions.get(0).getSubscriptionPack().getType().equals(SubscriptionPackType.CHILD)) {
                                return true;
                            } else {
                                return (subscriber.getMother() != null && subscriber.getMother().getBeneficiaryId() != null && !motherBeneficiaryId.equals(subscriber.getMother().getBeneficiaryId()));
                            }
                        } else {
                            return (subscriber.getMother() != null && !motherBeneficiaryId.equals(subscriber.getMother().getBeneficiaryId()));
                        }
                    }
                //}
//            } else {
//                return false;
//            }
        } else {
            //if (subscribers.size() != 0) {
//                for (Subscriber subscriber : subscribers
//                        ) {
                    List<Subscription> subscriptions = getActiveSubscriptionBySubscriber(subscriber);
                    subscriptionsSize = subscriptions.size();
                    if (subscriptionsSize != 0) {
                        if (subscriptionsSize == 1) {
                            if (SubscriptionPackType.PREGNANCY.equals(subscriptions.get(0).getSubscriptionPack().getType()) && subscriber.getMother() != null && motherBeneficiaryId != null && !motherBeneficiaryId.equals(subscriber.getMother().getBeneficiaryId())) {
                                return true;
                            } else if (subscriptions.get(0).getSubscriptionPack().getType().equals(SubscriptionPackType.PREGNANCY) && subscriber.getMother() != null && motherBeneficiaryId != null && motherBeneficiaryId.equals(subscriber.getMother().getBeneficiaryId())) {
                                return false;
                            } else {
                                return (subscriber.getChild() != null && subscriber.getChild().getBeneficiaryId() != null && !childBeneficiaryId.equals(subscriber.getChild().getBeneficiaryId()));
                            }
                        } else {
                            return ((subscriber.getChild() != null && !childBeneficiaryId.equals(subscriber.getChild().getBeneficiaryId())) || (subscriber.getMother() != null && !motherBeneficiaryId.equals(subscriber.getMother().getBeneficiaryId())));
                        }
                    }
//                }
//            } else {
//                return false;
//            }
        }
        return false;
    }

    @Override // NO CHECKSTYLE Cyclomatic Complexity
    public Boolean activeSubscriptionByMsisdnRch(List<Subscriber> subscribers,Long msisdn, SubscriptionPackType packType, String motherRchId, String childRchId) {
        //List<Subscriber> subscribers = subscriberDataService.getSubscriber(msisdn);
        int subscriptionsSize = 0;
        if (packType == SubscriptionPackType.PREGNANCY) {
            if (subscribers.size() != 0) {
                for (Subscriber subscriber : subscribers
                        ) {
                    List<Subscription> subscriptions = getActiveSubscriptionBySubscriber(subscriber);
                    subscriptionsSize = subscriptions.size();
                    if (subscriptionsSize != 0) {
                        if (subscriptionsSize == 1) {
                            if (SubscriptionPackType.CHILD.equals(subscriptions.get(0).getSubscriptionPack().getType())) {
                                return true;
                            } else {
                                if(motherRchId == null){
                                    return (subscriber.getMother() != null);
                                }
                                return (subscriber.getMother() != null && subscriber.getMother().getRchId() != null && !motherRchId.equals(subscriber.getMother().getRchId()));
                            }
                        } else {
                            return (subscriber.getMother() == null || !motherRchId.equals(subscriber.getMother().getRchId()));
                        }
                    }
                }
            } else {
                return false;
            }
        } else {
            if (subscribers.size() != 0) {
                for (Subscriber subscriber : subscribers
                        ) {
                    List<Subscription> subscriptions = getActiveSubscriptionBySubscriber(subscriber);
                    subscriptionsSize = subscriptions.size();
                    if (subscriptionsSize != 0) {
                        if (subscriptionsSize == 1) {
                            if (SubscriptionPackType.PREGNANCY.equals(subscriptions.get(0).getSubscriptionPack().getType()) && subscriber.getMother() != null && subscriber.getMother().getRchId() != null && (motherRchId == null || !motherRchId.equals(subscriber.getMother().getRchId()))) {
                                return true;
                            } else if (SubscriptionPackType.PREGNANCY.equals(subscriptions.get(0).getSubscriptionPack().getType()) && subscriber.getMother() != null && subscriber.getMother().getRchId() != null && motherRchId != null && motherRchId.equals(subscriber.getMother().getRchId())) {
                                return false;
                            } else {
                                return (subscriber.getChild() != null && subscriber.getChild().getRchId() != null && !childRchId.equals(subscriber.getChild().getRchId()));
                            }
                        } else {
                            return ((subscriber.getChild() != null && subscriber.getChild().getRchId() !=null &&   !childRchId.equals(subscriber.getChild().getRchId())) || (subscriber.getMother() != null && subscriber.getMother().getRchId() != null && !motherRchId.equals(subscriber.getMother().getRchId())));
                        }
                    }
                }
            } else {
                return false;
            }
        }
        return false;
    }

    @Override // NO CHECKSTYLE Cyclomatic Complexity
    public Subscription createSubscription(Subscriber subscriber, long callingNumber, Language language, Circle circle,
                                           SubscriptionPack subscriptionPack, SubscriptionOrigin mode) {
        try {
            long number = PhoneNumberHelper.truncateLongNumber(callingNumber);

            // Check if the callingNumber is in Weekly_Calls_Not_Answered_Msisdn_Records
            BlockedMsisdnRecord blockedMsisdnRecord = blockedMsisdnRecordDataService.findByNumber(callingNumber);
            if (!acceptNewSubscriptionForBlockedMsisdn()) {
                if (blockedMsisdnRecord != null) {
                    LOGGER.info("Can't create a Subscription as the number {} is deactivated due to Weekly Calls Not Answered", callingNumber);
                    String beneficiaryId = getBeneficiaryId(subscriber, mode, subscriptionPack);
                    subscriptionErrorDataService.create(new SubscriptionError(number, beneficiaryId,
                            SubscriptionRejectionReason.WEEKLY_CALLS_NOT_ANSWERED, subscriptionPack.getType(), "", mode));
                    return null;
                }
            }
            Subscriber sub;
            Subscription subscription;

            if (subscriber == null) {
                sub = subscriberDataService.create(new Subscriber(number, language, circle));
            } else {
                sub = subscriber;
            }

            if (acceptNewSubscriptionForBlockedMsisdn() && blockedMsisdnRecord != null) {
                Long motherID = 0L;

                if (sub.getMother() != null) {
                    motherID = sub.getMother().getId();
                } else if (sub.getChild() != null && sub.getChild().getMother() != null) {
                    motherID = sub.getChild().getMother().getId();
                }

                deleteBlockedMsisdn(motherID, null, callingNumber);
            }

            Language subscriberLanguage = sub.getLanguage();
            Circle subscriberCircle = sub.getCircle();

            if (subscriberLanguage == null && language != null) {
                sub.setLanguage(language);
                subscriberDataService.update(sub);
            }

            if (subscriberCircle == null && circle != null) {
                sub.setCircle(circle);
                subscriberDataService.update(sub);
            }

            subscription = (mode == SubscriptionOrigin.IVR) ?
                    createSubscriptionViaIvr(sub, subscriptionPack) :
                    createSubscriptionViaMcts(sub, subscriptionPack, mode);

            if (subscription != null) {
                sub.getSubscriptions().add(subscription);
                subscriberDataService.update(sub);
            }

            return subscription;
        } catch (ConstraintViolationException e) {
            LOGGER.error("2: List of constraints: {}", e.getConstraintViolations());
            throw e;
        }
    }

    private Subscription createSubscriptionViaIvr(Subscriber subscriber, SubscriptionPack pack) {
        Iterator<Subscription> subscriptionIterator = subscriber.getSubscriptions().iterator();
        Subscription existingSubscription;

        while (subscriptionIterator.hasNext()) {

            existingSubscription = subscriptionIterator.next();

            if (existingSubscription.getSubscriptionPack().equals(pack)) {
                if (existingSubscription.getStatus().equals(SubscriptionStatus.ACTIVE) ||
                        existingSubscription.getStatus().equals(SubscriptionStatus.PENDING_ACTIVATION)) {

                    // subscriber already has an active subscription to this pack, don't create a new one
                    // however if origin of existing subscription is MCTS then we want to update it to IVR
                    if (existingSubscription.getOrigin() == SubscriptionOrigin.MCTS_IMPORT) {
                        existingSubscription.setOrigin(SubscriptionOrigin.IVR);
                        subscriptionDataService.update(existingSubscription);
                    }

                    return null;
                }
            }
        }

        Subscription subscription = new Subscription(subscriber, pack, SubscriptionOrigin.IVR);
        subscription.setStartDate(DateTime.now().plusDays(1));
        subscription.setStatus(Subscription.getStatus(subscription, DateTime.now()));
        subscription.setWhatsAppCdrStatus(false);
        subscription.setNeedsWelcomeOptInForWP(false);
        subscription.setServiceStatus(ServiceStatus.IVR);
        subscription.setWhatsAppSelfOptIn(false);

        return subscriptionDataService.create(subscription);
    }

    /**
     * There's some rather complex logic for how MCTS subscribers get created and when more than one subscription is
     * allowed for an MSISDN.
     *
     * From #158:
     * MCTS data records with same MSISDN with one having DOB and other having only LMP shall be allowed to subscribe to
     * different packs.
     *
     * From #159:
     * Kilkari Service shall not allow multiple “Active” or “Pending Activation” subscriptions for the same pack on the
     * same MSISDN via bulk upload of MCTS Data or via IVR. In particular, subscription request with a particular
     * MSISDN, shall be rejected IF:
     *  - DOB (with or without LMP) is present in the MCTS record and there is an already “Active” subscription to Child
     *    Pack on this MSISDN
     *  - LMP (without DOB) is present in the MCTS record and there is an already “Active” or “Pending Activation”
     *    subscription to Pregnancy Pack on this MSISDN.
     */
    private Subscription createSubscriptionViaMcts(Subscriber subscriber, SubscriptionPack pack, SubscriptionOrigin importOrigin) {

        if (!enrollmentPreconditionCheck(subscriber, pack, importOrigin)) {
            LOGGER.info("PreCondition test passed");
            return null;
        }
        boolean WPstate = false;
        if (subscriber != null) {
            WPstate = getWhatsAppStateFilter().contains(subscriber.getMother() == null ? subscriber.getChild().getState().getCode() : subscriber.getMother().getState().getCode());
        }
        DateTime startDate = (pack.getType() == SubscriptionPackType.CHILD) ?
                subscriber.getDateOfBirth() : subscriber.getLastMenstrualPeriod().plusDays(KilkariConstants.THREE_MONTHS);

        Subscription subscription = new Subscription(subscriber, pack, importOrigin);
        subscription.setStartDate(startDate);
        subscription.setNeedsWelcomeMessageViaObd(true);
        subscription.setNeedsWelcomeOptInForWP(WPstate);
        subscription.setServiceStatus(ServiceStatus.IVR);
        subscription.setWhatsAppSelfOptIn(false);
        subscription.setWhatsAppCdrStatus(false);
        LOGGER.info("capacity"+isCapacityAvailable.get());
            if (isCapacityAvailable.get()) {
                subscription.setStatus(Subscription.getStatus(subscription, DateTime.now()));
            } else {
                LOGGER.debug("System at capacity, No new MCTS subscriptions allowed. Setting status to HOLD");
                subscription.setStatus(Subscription.getStatus(subscription, DateTime.now()).equals(SubscriptionStatus.PENDING_ACTIVATION) ? SubscriptionStatus.PENDING_ACTIVATION : SubscriptionStatus.HOLD);
            }

            LOGGER.info("Creating Subscription ()" + subscription.getSubscriptionId()+" with status "+subscription.getStatus().toString());
            return subscriptionDataService.create(subscription);
    }

    /**
     * Helper to evaluate if a subscriber is eligible to subscribe to a pack
     * @param subscriber subscriber to use
     * @param pack subscription pack to subscribe to
     * @return true if all field available and conditions satisfied
     */
    private boolean enrollmentPreconditionCheck(Subscriber subscriber, SubscriptionPack pack, SubscriptionOrigin importOrigin) {
        if (pack.getType() == SubscriptionPackType.CHILD) {

            if (subscriber.getDateOfBirth() == null) {
                return false;
            }

            if (Subscription.hasCompletedForStartDate(subscriber.getDateOfBirth(), DateTime.now(), pack)) {
                return false;
            }

            if (getActiveSubscription(subscriber, SubscriptionPackType.CHILD) != null) {
                // reject the subscription if it already exists
                logRejectedSubscription(subscriber.getCallingNumber(), (importOrigin == SubscriptionOrigin.MCTS_IMPORT) ? subscriber.getChild().getBeneficiaryId() : subscriber.getChild().getRchId(),
                        SubscriptionRejectionReason.ALREADY_SUBSCRIBED, SubscriptionPackType.CHILD, importOrigin);
                return false;
            }
        } else { // SubscriptionPackType.PREGNANCY

            if (subscriber.getLastMenstrualPeriod() == null) {
                return false;
            }

            if (Subscription.hasCompletedForStartDate(subscriber.getLastMenstrualPeriod().plusDays(KilkariConstants.THREE_MONTHS),
                    DateUtil.now(), pack)) {
                return false;
            }

            if (getActiveSubscription(subscriber, SubscriptionPackType.PREGNANCY) != null) {
                // reject the subscription if it already exists
                logRejectedSubscription(subscriber.getCallingNumber(), (importOrigin == SubscriptionOrigin.MCTS_IMPORT) ? subscriber.getMother().getBeneficiaryId() : subscriber.getMother().getRchId(),
                        SubscriptionRejectionReason.ALREADY_SUBSCRIBED, SubscriptionPackType.PREGNANCY, importOrigin);
                return false;
            }
        }

        return true;
    }

    /**
     * The return statement has been changed from boolean to integer to accomodate the HOLD to PENDING_ACTIVATION scenario.
     */
    private Integer enrollmentPreconditionCheckForUpkeep(Subscriber subscriber, SubscriptionPack pack, SubscriptionOrigin importOrigin) {
        if (pack.getType() == SubscriptionPackType.CHILD) {

            if (subscriber.getDateOfBirth() == null) {
                return 0;
            }

            if (Subscription.hasCompletedForStartDate(subscriber.getDateOfBirth(), DateTime.now(), pack)) {
                return 0;
            }

            if (getActiveSubscriptionForUpkeep(subscriber, SubscriptionPackType.CHILD) != null) {
                // reject the subscription if it already exists
                logRejectedSubscription(subscriber.getCallingNumber(), (importOrigin == SubscriptionOrigin.MCTS_IMPORT) ? subscriber.getChild().getBeneficiaryId() : subscriber.getChild().getRchId(),
                        SubscriptionRejectionReason.ALREADY_SUBSCRIBED, SubscriptionPackType.CHILD, importOrigin);
                return 0;
            }

            if (Subscription.notReadyForStartDate(subscriber.getDateOfBirth(), DateTime.now(), pack)) {
                return 1;
            }
        } else { // SubscriptionPackType.PREGNANCY

            if (subscriber.getLastMenstrualPeriod() == null) {
                return 0;
            }

            if (Subscription.hasCompletedForStartDate(subscriber.getLastMenstrualPeriod().plusDays(KilkariConstants.THREE_MONTHS),
                    DateUtil.now(), pack)) {
                return 0;
            }

            if (getActiveSubscriptionForUpkeep(subscriber, SubscriptionPackType.PREGNANCY) != null) {
                // reject the subscription if it already exists
                logRejectedSubscription(subscriber.getCallingNumber(), (importOrigin == SubscriptionOrigin.MCTS_IMPORT) ? subscriber.getMother().getBeneficiaryId() : subscriber.getMother().getRchId(),
                        SubscriptionRejectionReason.ALREADY_SUBSCRIBED, SubscriptionPackType.PREGNANCY, importOrigin);
                return 0;
            }

            if (Subscription.notReadyForStartDate(subscriber.getLastMenstrualPeriod().plusDays(KilkariConstants.THREE_MONTHS), DateUtil.now(), pack)) {
                return 1;
            }
        }

        return 2;
    }

    private void logRejectedSubscription(long callingNumber, String beneficiaryId, SubscriptionRejectionReason reason,
                                         SubscriptionPackType packType, SubscriptionOrigin importOrigin) {
        SubscriptionError error = new SubscriptionError(callingNumber, beneficiaryId, reason, packType, "Active subscription exists for same pack", importOrigin);
        subscriptionErrorDataService.create(error);
    }


    @Override
    public Subscription getActiveSubscription(Subscriber subscriber, SubscriptionPackType type) {
        if (subscriber != null && subscriber.getSubscriptions() != null) {
            Iterator<Subscription> subscriptionIterator = subscriber.getSubscriptions().iterator();
            Subscription existingSubscription;

            while (subscriptionIterator.hasNext()) {
                existingSubscription = subscriptionIterator.next();
                if (existingSubscription.getSubscriptionPack().getType() == type) {
                    if (type == SubscriptionPackType.PREGNANCY &&
                            (existingSubscription.getStatus() == SubscriptionStatus.ACTIVE ||
                                    existingSubscription.getStatus() == SubscriptionStatus.PENDING_ACTIVATION ||
                                    existingSubscription.getStatus() == SubscriptionStatus.HOLD)) {
                        return existingSubscription;
                    }
                    if (type == SubscriptionPackType.CHILD && (existingSubscription.getStatus() == SubscriptionStatus.ACTIVE ||
                            existingSubscription.getStatus() == SubscriptionStatus.PENDING_ACTIVATION ||
                            existingSubscription.getStatus() == SubscriptionStatus.HOLD)) {
                        return existingSubscription;
                    }
                }
            }
            return null;
        }
        return null;
    }

    private Subscription getActiveSubscriptionForUpkeep(Subscriber subscriber, SubscriptionPackType type) {
        if (subscriber != null && subscriber.getSubscriptions() != null) {
            Iterator<Subscription> subscriptionIterator = subscriber.getSubscriptions().iterator();
            Subscription existingSubscription;

            while (subscriptionIterator.hasNext()) {
                existingSubscription = subscriptionIterator.next();
                if (existingSubscription.getSubscriptionPack().getType() == type) {
                    if (type == SubscriptionPackType.PREGNANCY &&
                            (existingSubscription.getStatus() == SubscriptionStatus.ACTIVE ||
                                    existingSubscription.getStatus() == SubscriptionStatus.PENDING_ACTIVATION)) {
                        return existingSubscription;
                    }
                    if (type == SubscriptionPackType.CHILD && (existingSubscription.getStatus() == SubscriptionStatus.ACTIVE ||
                            existingSubscription.getStatus() == SubscriptionStatus.PENDING_ACTIVATION)) {
                        return existingSubscription;
                    }
                }
            }
            return null;
        }
        return null;
    }

    public Subscription getLatestDeactivatedSubscription(Subscriber subscriber, SubscriptionPackType type, boolean includeCompleted) {
        Iterator<Subscription> subscriptionIterator = subscriber.getSubscriptions().iterator();
        Subscription existingSubscription;
        List<Subscription> deactivatedSubscriptions = new ArrayList<>();

        while (subscriptionIterator.hasNext()) {
            existingSubscription = subscriptionIterator.next();
            if (existingSubscription.getSubscriptionPack().getType() == type) {
                if (type == SubscriptionPackType.PREGNANCY &&
                        ((existingSubscription.getStatus() == SubscriptionStatus.DEACTIVATED) ||
                        (includeCompleted && existingSubscription.getStatus() == SubscriptionStatus.COMPLETED))) {
                    deactivatedSubscriptions.add(existingSubscription);
                }
                if (type == SubscriptionPackType.CHILD && ((existingSubscription.getStatus() == SubscriptionStatus.DEACTIVATED) ||
                        (includeCompleted && existingSubscription.getStatus() == SubscriptionStatus.COMPLETED))) {
                    deactivatedSubscriptions.add(existingSubscription);
                }
            }
        }

        if (!deactivatedSubscriptions.isEmpty()) {
            Collections.sort(deactivatedSubscriptions, new Comparator<Subscription>() {
                public int compare(Subscription m1, Subscription m2) { //descending order
                    if (m2.getEndDate() == null && m1.getEndDate() != null) {
                        return (m2.getModificationDate())
                                .compareTo(m1.getEndDate());
                    } else if (m1.getEndDate() == null && m2.getEndDate() != null) {
                        return (m2.getEndDate())
                                .compareTo(m1.getModificationDate());
                    } else if (m1.getEndDate() == null && m2.getEndDate() == null) {
                        return (m2.getModificationDate())
                                .compareTo(m1.getModificationDate());
                    } else {
                        return (m2.getEndDate())
                                .compareTo(m1.getEndDate());
                    }
                }
            });
            return deactivatedSubscriptions.get(0);
        }

        return null;
    }

    @Override
    public List<Subscription> getActiveSubscriptionBySubscriber(Subscriber subscriber) {
        List<Subscription> subscriptions = new ArrayList<>();
        Subscription subscription1 = getActiveSubscription(subscriber, SubscriptionPackType.CHILD);
        Subscription subscription2 = getActiveSubscription(subscriber, SubscriptionPackType.PREGNANCY);
        if (subscription1 != null) {
            subscriptions.add(subscription1);
        }
        if (subscription2 != null) {
            subscriptions.add(subscription2);
        }

        return subscriptions;
    }

    @Override
    public Subscription getSubscription(String subscriptionId) {
        return subscriptionDataService.findBySubscriptionId(subscriptionId);
    }


    @Override
    public void updateStartDate(Subscription subscription, DateTime newReferenceDate) {
        try {
            if (subscription.getSubscriptionPack().getType() == SubscriptionPackType.PREGNANCY) {
                subscription.setStartDate(newReferenceDate.plusDays(KilkariConstants.THREE_MONTHS));
            } else { // CHILD pack
                subscription.setStartDate(newReferenceDate);
            }
            if (isCapacityAvailable.get()||(!isCapacityAvailable.get()&&subscription.getStatus()==SubscriptionStatus.ACTIVE)) {
                subscription.setStatus(Subscription.getStatus(subscription, DateTime.now()));
            }
            else {
                subscription.setStatus(SubscriptionStatus.HOLD);
            }
            if (subscription.getOrigin() == SubscriptionOrigin.IVR) {  // Start Date gets updated through MCTS
                subscription.setOrigin(SubscriptionOrigin.MCTS_IMPORT);
            }
            LOGGER.info("Updating subscription "+subscription.getSubscriptionId()+" with status "+subscription.getStatus().toString());
            subscriptionDataService.update(subscription);
        } catch (ConstraintViolationException e) {
            LOGGER.error("3: List of constraints: {}", e.getConstraintViolations());
            throw e;
        }
    }

    @Override
    public void activateSubscription(Subscription subscription) {
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionDataService.update(subscription);
    }

    @Override
    public Boolean activatePendingSubscriptionsUpTo(final DateTime upToDateTime, long maxActiveSubscriptions) {

        long currentActive = subscriptionDataService.countFindByStatus(SubscriptionStatus.ACTIVE);
        LOGGER.info("Found {} active subscriptions", currentActive);

        final long openSlots = maxActiveSubscriptions - subscriptionDataService.countFindByStatus(SubscriptionStatus.ACTIVE);
        if (openSlots < 1) {
            LOGGER.info("No open slots found for hold subscription activation. Slots: {}", openSlots);
            return false;
        }
        SqlQueryExecution sqe = new SqlQueryExecution() {

            @Override
            public String getSqlQuery() {
                String query = "UPDATE nms_subscriptions SET status='ACTIVE', activationDate = :now, " +
                                "modificationDate = :now WHERE status='PENDING_ACTIVATION' AND startDate < :upto limit :count";
                LOGGER.debug(KilkariConstants.SQL_QUERY_LOG, query);
                return query;
            }

            @Override
            public Object execute(Query query) {
                Map params = new HashMap();
                params.put("now", DateTime.now().toString(KilkariConstants.TIME_FORMATTER));
                params.put("upto", upToDateTime.toString(KilkariConstants.TIME_FORMATTER));
                params.put("count", openSlots);
                query.executeWithMap(params);
                return null;
            }
        };
        subscriptionDataService.executeSQLQuery(sqe);
        subscriptionDataService.evictEntityCache(true);
        return true;
    }

    /**
     * Toggle if we should let new MCTS subscriptions to be created based on config, broadcast message to other instances
     */
    @Override
    public void toggleMctsSubscriptionCreation(long maxActiveSubscriptions) {

        long currentActive = subscriptionDataService.countFindByStatus(SubscriptionStatus.ACTIVE);
        LOGGER.info("Found {} active subscriptions", currentActive);
        this.allowMctsSubscriptions = (currentActive < maxActiveSubscriptions);

        // broadcast flag to every other instance
        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put(KilkariConstants.TOGGLE_CAP_KEY, this.allowMctsSubscriptions);
        MotechEvent toggleEvent = new MotechEvent(KilkariConstants.TOGGLE_SUBSCRIPTION_CAPPING, eventParams);
        eventRelay.broadcastEventMessage(toggleEvent);
    }

    @Override
    @Transactional
    public long activateHoldSubscriptions(long maxActiveSubscriptions) {

        LOGGER.info("Activating hold subscriptions up to {}", maxActiveSubscriptions);

        long openSlots = maxActiveSubscriptions - subscriptionDataService.countFindByStatus(SubscriptionStatus.ACTIVE);
        if (!this.allowMctsSubscriptions || openSlots < 1) {
            LOGGER.info("No open slots found for hold subscription activation. Slots: {}", openSlots);
            return 0;
        }

        int activated = 0;
        LOGGER.info("Found {} open slots for hold-activation", openSlots);
        List<Subscription> holdSubscriptions = findHoldSubscriptions(openSlots);
        LOGGER.info("Found {} subscriptions to activate", holdSubscriptions.size());

        for (Subscription current : holdSubscriptions) {

            if (activateHoldSubscription(current)) {
                activated++;
            }
        }

        LOGGER.info("Activated {} subscriptions", activated);
        return activated;
    }

    private boolean activateHoldSubscription(Subscription currentSubscription) {

        Subscriber currentSubscriber = currentSubscription.getSubscriber();
        SubscriptionPack currentPack = currentSubscription.getSubscriptionPack();

        if (enrollmentPreconditionCheckForUpkeep(currentSubscriber, currentPack, currentSubscription.getOrigin()) == 2) { // Don't need a full check but it doesn't hurt
            currentSubscription.setStatus(SubscriptionStatus.ACTIVE);
            subscriptionDataService.update(currentSubscription);
            return true;
        } else if (enrollmentPreconditionCheckForUpkeep(currentSubscriber, currentPack, currentSubscription.getOrigin()) == 1) {
            currentSubscription.setStatus(SubscriptionStatus.PENDING_ACTIVATION);
            subscriptionDataService.update(currentSubscription);
            return true;
        } else {
            LOGGER.debug("We will not be activating this Hold subscription : {}", currentSubscription.getSubscriptionId());
            return false;
        }
    }

    @Override
    public void deleteBlockedMsisdn(Long motherId, Long oldCallingNumber, Long newCallingNumber) {
        // Check if the callingNumber is in Blocked Msisdn_Records
        BlockedMsisdnRecord blockedMsisdnRecord = blockedMsisdnRecordDataService.findByNumber(newCallingNumber);
        if (blockedMsisdnRecord != null) {
            LOGGER.info("Deleting msisdn {} from Blocked list.", newCallingNumber);
            blockedMsisdnRecordDataService.delete(blockedMsisdnRecord);
        }

        subscriberMsisdnTrackerDataService.create(new SubscriberMsisdnTracker(motherId, oldCallingNumber, newCallingNumber));

    }

    /**
     * This finds all the subscriptions that are on hold and returns the list by users having the most days left in the pack
     * first. This will skew the majority of mother packs to get activated first, but looks like we'd optimize for
     * targeting users as early as possible when competing for spots
     * @param resultSize max result size, usually reflects the number of open slots for activation
     * @return list of subscriptions
     */
    private List<Subscription> findHoldSubscriptions(final long resultSize) {

        @SuppressWarnings("unchecked")
        SqlQueryExecution<List<Subscription>> queryExecution = new SqlQueryExecution<List<Subscription>>() {

            // This query looks a little complex but it is basically structured that way to sort users by most messages left
            @Override
            public String getSqlQuery() {
                String query ="SELECT res1.id as id, res1.activationDate, res1.deactivationReason, res1.endDate, res1.firstMessageDayOfWeek, res1.needsWelcomeMessageViaObd, " +
                        "res1.origin, res1.secondMessageDayOfWeek, res1.startDate, res1.status, res1.subscriber_id_OID, res1.subscriptionId, res1.subscriptionPack_id_OID, " +
                        "res1.creationDate, res1.creator, res1.modificationDate, res1.modifiedBy, res1.owner, " +
                        "CASE " +
                        "WHEN res1.type = 'PREGNANCY' THEN DATE_ADD(res1.lastMenstrualPeriod, INTERVAL :pday DAY) " +
                        "ELSE DATE_ADD(res1.dateOfBirth, INTERVAL :cday DAY) " +
                        "END AS referenceDate, " +
                        "res1.type " +
                        "FROM " +
                        "(SELECT ss.id as id, ss.activationDate, ss.deactivationReason, ss.endDate, ss.firstMessageDayOfWeek, ss.needsWelcomeMessageViaObd, " +
                        "ss.origin, ss.secondMessageDayOfWeek, ss.startDate, ss.status, ss.subscriber_id_OID, ss.subscriptionId, ss.subscriptionPack_id_OID, " +
                        "ss.creationDate, ss.creator, ss.modificationDate, ss.modifiedBy, ss.owner, s.dateOfBirth, s.lastMenstrualPeriod, sp.type FROM nms_subscriptions AS ss " +
                        "JOIN nms_subscription_packs AS sp ON ss.subscriptionPack_id_OID = sp.id " +
                        "JOIN nms_subscribers AS s ON ss.subscriber_id_OID = s.id " +
                        "JOIN nms_mcts_mothers m ON s.mother_id_OID = m.id " +
                        "WHERE ss.status = 'HOLD' AND m.healthBlock_id_OID IN ( " + highPriorityBlocks() + " ) AND origin in ('MCTS_IMPORT', 'RCH_IMPORT')) AS res1 " +
                        "UNION " +
                         "SELECT res.id as id, res.activationDate, res.deactivationReason, res.endDate, res.firstMessageDayOfWeek, res.needsWelcomeMessageViaObd, " +
                                "res.origin, res.secondMessageDayOfWeek, res.startDate, res.status, res.subscriber_id_OID, res.subscriptionId, res.subscriptionPack_id_OID, " +
                                "res.creationDate, res.creator, res.modificationDate, res.modifiedBy, res.owner, " +
                                "CASE " +
                                    "WHEN res.type = 'PREGNANCY' THEN DATE_ADD(res.lastMenstrualPeriod, INTERVAL :pdays DAY) " +
                                    "ELSE DATE_ADD(res.dateOfBirth, INTERVAL :cdays DAY) " +
                                "END AS referenceDate, " +
                                "res.type " +
                                "FROM " +
                                    "(SELECT ss.id as id, ss.activationDate, ss.deactivationReason, ss.endDate, ss.firstMessageDayOfWeek, ss.needsWelcomeMessageViaObd, " +
                                    "ss.origin, ss.secondMessageDayOfWeek, ss.startDate, ss.status, ss.subscriber_id_OID, ss.subscriptionId, ss.subscriptionPack_id_OID, " +
                                    "ss.creationDate, ss.creator, ss.modificationDate, ss.modifiedBy, ss.owner, s.dateOfBirth, s.lastMenstrualPeriod, sp.type FROM nms_subscriptions AS ss " +
                                    "JOIN nms_subscription_packs AS sp ON ss.subscriptionPack_id_OID = sp.id " +
                                    "JOIN nms_subscribers AS s ON ss.subscriber_id_OID = s.id " +
                                    "WHERE ss.status = 'HOLD' AND origin in ('MCTS_IMPORT', 'RCH_IMPORT')) AS res " + // Origin is superfluous here since IVR doesn't go on hold
                                     "LIMIT :limit";
                LOGGER.debug(KilkariConstants.SQL_QUERY_LOG, query);
                return query;
            }

            @Override
            public List<Subscription> execute(Query query) {

                query.setClass(Subscription.class);

                Map<String, Object> params = new HashMap<>();
                params.put("pday", KilkariConstants.THREE_MONTHS + KilkariConstants.PREGNANCY_PACK_LENGTH_DAYS);
                params.put("cday", KilkariConstants.CHILD_PACK_LENGTH_DAYS);
                params.put("pdays", KilkariConstants.THREE_MONTHS + KilkariConstants.PREGNANCY_PACK_LENGTH_DAYS);
                params.put("cdays", KilkariConstants.CHILD_PACK_LENGTH_DAYS);
                params.put("limit", resultSize);
                ForwardQueryResult fqr = (ForwardQueryResult) query.executeWithMap(params);

                return (List<Subscription>) fqr;
            }
        };

        return subscriptionDataService.executeSQLQuery(queryExecution);
    }


    /**
     * Delete the CallRetry record corresponding to the given subscription     *
     * @param subscriptionId subscription to delete the CallRetry record fors
     */
    @Override
    public void deleteCallRetry(String subscriptionId) {
        CallRetry callRetry = callRetryDataService.findBySubscriptionId(subscriptionId);
        if (callRetry != null) {
            callRetryDataService.delete(callRetry);
        }
    }

    @Override
    public void updateCallRetry(String subscriptionId,Long msisdn) {
        CallRetry callRetry = callRetryDataService.findBySubscriptionId(subscriptionId);
        if (callRetry != null) {
            callRetry.setMsisdn(msisdn);
            callRetryDataService.update(callRetry);
        }
    }

    @Override
    public void deactivateSubscription(Subscription subscription, DeactivationReason reason) {
        if (subscription != null && (subscription.getStatus() == SubscriptionStatus.ACTIVE ||
                subscription.getStatus() == SubscriptionStatus.PENDING_ACTIVATION || subscription.getStatus() == SubscriptionStatus.HOLD)) {
            subscription.setStatus(SubscriptionStatus.DEACTIVATED);
            subscription.setDeactivationReason(reason);
            Subscription subscriptionDeativated = subscriptionDataService.update(subscription);
            LOGGER.info("Deactivated Subscription " + subscriptionDeativated.getSubscriptionId());

            SubscriptionServiceImpl.createDeactivatedUser(deactivatedBeneficiaryDataService, subscription, reason, false);

            LOGGER.debug("Created the deactivated user " + subscriptionDeativated.getSubscriptionId());

            // Let's not retry calling subscribers with deactivated subscriptions
            deleteCallRetry(subscription.getSubscriptionId());
        }
        // Else no-op
    }

    @Override
    @Cacheable(value = "pack", key = "'0-'.concat(#p0)")
    public SubscriptionPack getSubscriptionPack(SubscriptionPackType type) {
        return subscriptionPackDataService.byType(type);
    }


    @Override
    @Cacheable(value = "pack", key = "#p0.concat('-0')")
    public SubscriptionPack getSubscriptionPack(String name) {
        return subscriptionPackDataService.byName(name);
    }


//    @Override
//    public List<SubscriptionPack> getSubscriptionPacks() {
//        return subscriptionPackDataService.retrieveAll();
//    }


    public Subscription create(Subscription subscription) {
        return subscriptionDataService.create(subscription);
    }

    @Override
    public List<Subscription> findActiveSubscriptionsForDay(final DayOfTheWeek dow, final long offset,
                                                            final int rowCount) {
        Timer queryTimer = new Timer();

        @SuppressWarnings("unchecked")
        SqlQueryExecution<List<Subscription>> queryExecution = new SqlQueryExecution<List<Subscription>>() {

            @Override
            public String getSqlQuery() {
                String query =  "SELECT s.id as id, activationDate, deactivationReason, endDate, firstMessageDayOfWeek, " +
                                "s.needsWelcomeMessageViaObd, s.origin, s.secondMessageDayOfWeek, s.startDate, " +
                                "s.status, s.subscriber_id_OID, s.subscriptionId, s.subscriptionPack_id_OID, " +
                                "s.creationDate, s.creator, s.modificationDate, s.modifiedBy, s.owner, s.needsWelcomeOptInForWP " +
                                "FROM nms_subscriptions AS s " +
                                "INNER JOIN nms_subscription_packs AS p ON s.subscriptionPack_id_OID = p.id " +
                                "WHERE s.id > :offset AND " +
                                "(firstMessageDayOfWeek = :dow OR " +
                                "(secondMessageDayOfWeek = :dow AND p.messagesPerWeek = 2)) AND " +
                                "status = 'ACTIVE' AND "+
                                " (serviceStatus IN ('IVR', 'IVR_AND_WHATSAPP') OR serviceStatus IS NULL) "+
                                "ORDER BY s.id " +
                                "LIMIT :max";
                LOGGER.debug(KilkariConstants.SQL_QUERY_LOG, query);
                return query;
            }

            @Override
            public List<Subscription> execute(Query query) {

                query.setClass(Subscription.class);

                Map params = new HashMap();
                params.put("offset", offset);
                params.put("dow", dow.toString());
                params.put("max", rowCount);
                ForwardQueryResult fqr = (ForwardQueryResult) query.executeWithMap(params);

                return (List<Subscription>) fqr;
            }
        };

        List<Subscription> subscriptions = subscriptionDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("findActiveSubscriptionsForDay(dow={}, offset={}, rowCount={}) {}", dow, offset, rowCount, queryTimer.time());
        return subscriptions;
    }


    @Override
    public List<Subscription> findActiveSubscriptionsForDayWP(final DayOfTheWeek dow, final long offset,
                                                              final int rowCount, final Date date) {
        Timer queryTimer = new Timer();

        @SuppressWarnings("unchecked")
        SqlQueryExecution<List<Subscription>> queryExecution = new SqlQueryExecution<List<Subscription>>() {

            @Override
            public String getSqlQuery() {
                String query =  "SELECT s.id as id, activationDate, deactivationReason, endDate, firstMessageDayOfWeek, " +
                        "s.needsWelcomeMessageViaObd, s.origin, s.secondMessageDayOfWeek, s.startDate, " +
                        "s.status, s.subscriber_id_OID, s.subscriptionId, s.subscriptionPack_id_OID, " +
                        "s.creationDate, s.creator, s.modificationDate, s.modifiedBy, s.owner " +
                        "FROM nms_subscriptions AS s " +
                        "WHERE s.id > :offset AND " +
                        "firstMessageDayOfWeek = :dow AND " +
                        "status = 'ACTIVE' AND " +
                        "s.serviceStatus IN ('WHATSAPP', 'IVR_AND_WHATSAPP') AND " +
                        "s.needsWelcomeOptInForWP = true AND " +
                        "DATE(s.wpStartDate) != DATE(:date) " +
                        "ORDER BY s.id " +
                        "LIMIT :max";
                LOGGER.debug(KilkariConstants.SQL_QUERY_LOG, query);
                return query;
            }

            @Override
            public List<Subscription> execute(Query query) {

                query.setClass(Subscription.class);

                Map params = new HashMap();
                params.put("offset", offset);
                params.put("dow", dow.toString());
                params.put("max", rowCount);
                params.put("date", date);

                LOGGER.debug("Executing SQL Query: {}", query.toString());
                LOGGER.debug("Parameters: {}", params);
                ForwardQueryResult fqr = (ForwardQueryResult) query.executeWithMap(params);

                return (List<Subscription>) fqr;
            }
        };

        List<Subscription> subscriptions = subscriptionDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("findActiveSubscriptionsForDay(dow={}, offset={}, rowCount={}) {}", dow, offset, rowCount, queryTimer.time());
        return subscriptions;
    }


    @Override
    public List<Subscription> findWelcomeActiveSubscriptionsForDayWP(final Date date, final DayOfTheWeek dow, final long offset,
                                                              final int rowCount) {
        Timer queryTimer = new Timer();

        @SuppressWarnings("unchecked")
        SqlQueryExecution<List<Subscription>> queryExecution = new SqlQueryExecution<List<Subscription>>() {

            @Override
            public String getSqlQuery() {
                String query =  "SELECT s.id as id, activationDate, deactivationReason, endDate, firstMessageDayOfWeek, " +
                        "s.needsWelcomeMessageViaObd, s.origin, s.secondMessageDayOfWeek, s.startDate, " +
                        "s.status, s.subscriber_id_OID, s.subscriptionId, s.subscriptionPack_id_OID, " +
                        "s.creationDate, s.creator, s.modificationDate, s.modifiedBy, s.owner " +
                        "FROM nms_subscriptions AS s " +
                        "WHERE s.id > :offset AND " +
                        "DATE(s.wpStartDate) = DATE(:date) AND "+
                        "status = 'ACTIVE' AND " +
                        "s.serviceStatus IN ('WHATSAPP', 'IVR_AND_WHATSAPP') AND " +
                        "s.needsWelcomeOptInForWP = true " +
                        "ORDER BY s.id " +
                        "LIMIT :max";
                LOGGER.debug(KilkariConstants.SQL_QUERY_LOG, query);
                return query;
            }

            @Override
            public List<Subscription> execute(Query query) {

                query.setClass(Subscription.class);

                Map params = new HashMap();
                params.put("offset", offset);

                params.put("max", rowCount);
                params.put("date", date);

                LOGGER.debug("Executing SQL Query: {}", query.toString());
                LOGGER.debug("Parameters: {}", params);

                ForwardQueryResult fqr = (ForwardQueryResult) query.executeWithMap(params);

                return (List<Subscription>) fqr;
            }
        };

        List<Subscription> subscriptions = subscriptionDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("findWelcomeActiveSubscriptionsForDayWP (dow={}, offset={}, rowCount={}) {}", dow, offset, rowCount, queryTimer.time());
        return subscriptions;
    }

    @Override
    public List<Subscription> findDeactivatedSubscriptionsForDayWP(final Date date, final long offset,
                                                                     final int rowCount , final  String deactivationReasons) {
        Timer queryTimer = new Timer();

        @SuppressWarnings("unchecked")
        SqlQueryExecution<List<Subscription>> queryExecution = new SqlQueryExecution<List<Subscription>>() {

            @Override
            public String getSqlQuery() {
                String query =  "SELECT s.id as id, activationDate, deactivationReason, endDate, firstMessageDayOfWeek, " +
                        "s.needsWelcomeMessageViaObd, s.origin, s.secondMessageDayOfWeek, s.startDate, " +
                        "s.status, s.subscriber_id_OID, s.subscriptionId, s.subscriptionPack_id_OID, " +
                        "s.creationDate, s.creator, s.modificationDate, s.modifiedBy, s.owner " +
                        "FROM nms_subscriptions AS s " +
                        "WHERE s.id > :offset AND " +
                        "DATE(s.endDate) = DATE(:date) AND " +
                        "status = 'DEACTIVATED' AND " +
                        "s.serviceStatus IN ('WHATSAPP', 'IVR_AND_WHATSAPP') AND " +
                        "s.deactivationReason IN ( " + deactivationReasons + " ) " +
                        "ORDER BY s.id " +
                        "LIMIT :max";
                LOGGER.debug(KilkariConstants.SQL_QUERY_LOG, query);
                return query;
            }

            @Override
            public List<Subscription> execute(Query query) {

                query.setClass(Subscription.class);

                Map params = new HashMap();
                params.put("offset", offset);
                params.put("date", date);
                params.put("max", rowCount);
//                params.put("deactivationReasons" , deactivationReasons);
                LOGGER.debug("test - parameters {} " , params);
                ForwardQueryResult fqr = (ForwardQueryResult) query.executeWithMap(params);

                return (List<Subscription>) fqr;
            }
        };

        List<Subscription> subscriptions = subscriptionDataService.executeSQLQuery(queryExecution);
        LOGGER.debug("findDeactivatedSubscriptionsForDayWP (date={}, offset={}, rowCount={}) {}", date, offset, rowCount, queryTimer.time());

        return subscriptions;

    }

    @Override
    public List<String> findJhSubscriptionIds() {
        SqlQueryExecution<List<String>> queryExecution = new SqlQueryExecution<List<String>>() {
            @Override
            public String getSqlQuery() {
                String query = "select subscriptionId from (select subscriptionId from nms_mcts_mothers a LEFT JOIN " +
                        "nms_subscribers b on b.mother_id_oid=a.id LEFT JOIN nms_subscriptions c on c.subscriber_id_oid = b.id where rchId like 'JH%' " +
                        "and subscriptionPack_id_OID = 1 and IVR_SERVICE IS TRUE UNION ALL select subscriptionId from nms_mcts_children a LEFT JOIN " +
                        "nms_subscribers b on b.child_id_oid=a.id LEFT JOIN nms_subscriptions c on c.subscriber_id_oid = b.id " +
                        "where rchId like 'JH%' and subscriptionPack_id_OID = 2 and IVR_SERVICE IS TRUE AND (serviceStatus IN ('IVR', 'IVR_AND_WHATSAPP') OR serviceStatus IS NULL)) as  a;";
                LOGGER.debug(KilkariConstants.SQL_QUERY_LOG, query);
                return query;
            }

            @Override
            public List<String> execute(Query query) {
                List<String> ids = (List<String>) query.execute();
                return ids;
            }
        };
        List<String> subscriptionIds = subscriptionDataService.executeSQLQuery(queryExecution);
        return subscriptionIds;
    }



    public List<Subscription> findPendingSubscriptionsFromDate(DateTime startDate, int page, int pageSize) {
        return subscriptionDataService.findByStatusAndStartDate(SubscriptionStatus.PENDING_ACTIVATION, startDate, new QueryParams(page, pageSize));
    }


    @CacheEvict(value = {"pack" }, allEntries = true)
    public void broadcastCacheEvictMessage(SubscriptionPack pack) {
        MotechEvent motechEvent = new MotechEvent(KilkariConstants.PACK_CACHE_EVICT_MESSAGE_SUBJECT);
        eventRelay.broadcastEventMessage(motechEvent);
    }


    @MotechListener(subjects = { KilkariConstants.PACK_CACHE_EVICT_MESSAGE_SUBJECT })
    @CacheEvict(value = {"pack" }, allEntries = true)
    public void cacheEvict(MotechEvent event) {
        csrVerifierService.cacheEvict();
    }

    @MotechListener(subjects = { KilkariConstants.TOGGLE_SUBSCRIPTION_CAPPING })
    public void toggleActiveSubscriptionCapping(MotechEvent event) {
        LOGGER.info("Received message to toggle subscription capping");
        boolean value = (boolean) event.getParameters().get(KilkariConstants.TOGGLE_CAP_KEY);
        this.allowMctsSubscriptions = value;
        LOGGER.info("Set allow new mcts subscriptions to {}", value);
    }

//    @Override
//    public List<Subscription> retrieveAll() {
//        return subscriptionDataService.retrieveAll();
//    }

    @Override
    public Subscription getIVRSubscription(Set<Subscription> subscriptions, SubscriptionPackType packType) {
        for (Subscription subscription : subscriptions) {
            if (subscription.getSubscriptionPack().getType() == packType && subscription.getOrigin() == SubscriptionOrigin.IVR) {
                return subscription;
            }
        }
        return null;
    }

    public static void createDeactivatedUser(DeactivatedBeneficiaryDataService service, Subscription subscription, DeactivationReason reason, boolean completed) {
        Subscriber subscriber = subscription.getSubscriber();
        String externalId = null;

        if (subscription.getSubscriptionPack().getType() == SubscriptionPackType.CHILD) {
            MctsChild child = subscriber.getChild();
            if (child != null) {
                externalId = child.getBeneficiaryId();
                if (child.getRchId() != null) {
                    externalId = child.getRchId();
                }
            }
        } else {
            MctsMother mother = subscriber.getMother();
            if (mother != null) {
                externalId = mother.getBeneficiaryId();
                if (mother.getRchId() != null) {
                    externalId = mother.getRchId();
                }
            }
        }

        if (externalId != null) {
            DeactivatedBeneficiary deactivatedUser = new DeactivatedBeneficiary();
            deactivatedUser.setExternalId(externalId);
            deactivatedUser.setOrigin(subscription.getOrigin());
            deactivatedUser.setDeactivationReason(reason);
            deactivatedUser.setCompletedSubscription(completed);
            deactivatedUser.setDeactivationDate(DateTime.now());
            deactivatedUser.setServiceStartDate(subscription.getStartDate());
            service.create(deactivatedUser);
        }
    }

    @Override
    public void deleteSubscriber(Long id) {
        SqlQueryExecution<Long> queryExecution = new SqlQueryExecution<Long>() {

            @Override
            public String getSqlQuery() {
                return KilkariConstants.DELETE_SUBSCRIBER_BY_ID;
            }

            @Override
            public Long execute(Query query) {
                query.setClass(Subscriber.class);
                return (Long) query.execute(id);
            }
        };

        subscriberDataService.executeSQLQuery(queryExecution);

    }

    private Set<Long> getWhatsAppStateFilter(){
        Set<Long> states = new HashSet<>();
        String locationProp = settingsFacade.getProperty(KilkariConstants.WP_STATES);
        if (StringUtils.isBlank(locationProp)) {
            return states;
        }
        String[] locationParts = StringUtils.split(locationProp, ',');
        for (String locationPart : locationParts) {
            Long stateId = Long.valueOf(locationPart);
            states.add(stateId);
        }
        return states;
    }
    
    private String highPriorityBlocks(){
        if (settingsFacade.getProperty(HIGH_PRIORITY_BLOCK)==null || settingsFacade.getProperty(HIGH_PRIORITY_BLOCK).trim().isEmpty()){
            return "0";
        }
        return settingsFacade.getProperty(HIGH_PRIORITY_BLOCK);
    }

}

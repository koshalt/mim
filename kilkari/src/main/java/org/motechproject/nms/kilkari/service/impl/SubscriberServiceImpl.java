package org.motechproject.nms.kilkari.service.impl;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.datanucleus.store.rdbms.query.ForwardQueryResult;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.motechproject.mds.query.SqlQueryExecution;
import org.motechproject.nms.kilkari.domain.RejectionReasons;
import org.motechproject.nms.kilkari.domain.BlockedMsisdnRecord;
import org.motechproject.nms.kilkari.domain.DeactivationReason;
import org.motechproject.nms.kilkari.domain.DeactivationSubscriptionAuditRecord;
import org.motechproject.nms.kilkari.domain.MctsBeneficiary;
import org.motechproject.nms.kilkari.domain.MctsChild;
import org.motechproject.nms.kilkari.domain.MctsMother;
import org.motechproject.nms.kilkari.domain.Subscriber;
import org.motechproject.nms.kilkari.domain.Subscription;
import org.motechproject.nms.kilkari.domain.SubscriptionError;
import org.motechproject.nms.kilkari.domain.SubscriptionOrigin;
import org.motechproject.nms.kilkari.domain.SubscriptionPack;
import org.motechproject.nms.kilkari.domain.SubscriptionPackType;
import org.motechproject.nms.kilkari.domain.SubscriptionRejectionReason;
import org.motechproject.nms.kilkari.domain.SubscriptionStatus;
import org.motechproject.nms.kilkari.domain.AuditStatus;
import org.motechproject.nms.kilkari.domain.ReactivatedBeneficiaryAudit;
import org.motechproject.nms.kilkari.repository.*;
import org.motechproject.nms.kilkari.service.SubscriberService;
import org.motechproject.nms.kilkari.service.SubscriptionService;
import org.motechproject.nms.kilkari.utils.KilkariConstants;
import org.motechproject.nms.region.domain.Circle;
import org.motechproject.nms.region.domain.District;
import org.motechproject.nms.region.domain.Language;

import org.motechproject.nms.rejectionhandler.domain.ChildImportRejection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.jdo.Query;
import javax.validation.ConstraintViolationException;
import java.util.*;

import static org.motechproject.nms.kilkari.utils.RejectedObjectConverter.childRejectionMcts;
import static org.motechproject.nms.kilkari.utils.RejectedObjectConverter.childRejectionRch;
import static org.motechproject.nms.kilkari.utils.RejectedObjectConverter.convertMapToChild;
import static org.motechproject.nms.kilkari.utils.RejectedObjectConverter.convertMapToRchChild;


/**
 * Implementation of the {@link SubscriberService} interface.
 */
@Service("subscriberService")
public class SubscriberServiceImpl implements SubscriberService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriberServiceImpl.class);

    private SubscriberDataService subscriberDataService;
    private SubscriptionService subscriptionService;
    private SubscriptionDataService subscriptionDataService;
    private SubscriptionErrorDataService subscriptionErrorDataService;
    private SubscriptionPackDataService subscriptionPackDataService;
    private DeactivationSubscriptionAuditRecordDataService deactivationSubscriptionAuditRecordDataService;
    private BlockedMsisdnRecordDataService blockedMsisdnRecordDataService;
    private ReactivatedBeneficiaryAuditDataService reactivatedBeneficiaryAuditDataService;
    private MctsChildDataService mctsChildDataService;
    private MctsMotherDataService mctsMotherDataService;

    @Autowired
    public SubscriberServiceImpl(SubscriberDataService subscriberDataService, SubscriptionService subscriptionService,
                                 SubscriptionDataService subscriptionDataService,
                                 SubscriptionErrorDataService subscriptionErrorDataService,
                                 SubscriptionPackDataService subscriptionPackDataService,
                                 BlockedMsisdnRecordDataService blockedMsisdnRecordDataService,
                                 DeactivationSubscriptionAuditRecordDataService deactivationSubscriptionAuditRecordDataService,
                                 ReactivatedBeneficiaryAuditDataService reactivatedBeneficiaryAuditDataService,
                                 MctsChildDataService mctsChildDataService) {
        this.subscriberDataService = subscriberDataService;
        this.subscriptionService = subscriptionService;
        this.subscriptionDataService = subscriptionDataService;
        this.subscriptionErrorDataService = subscriptionErrorDataService;
        this.subscriptionPackDataService = subscriptionPackDataService;
        this.deactivationSubscriptionAuditRecordDataService = deactivationSubscriptionAuditRecordDataService;
        this.blockedMsisdnRecordDataService = blockedMsisdnRecordDataService;
        this.reactivatedBeneficiaryAuditDataService = reactivatedBeneficiaryAuditDataService;
        this.mctsChildDataService = mctsChildDataService;
    }

    @Override
    public List<Subscriber> getSubscriber(final long callingNumber) {

        SqlQueryExecution<List<Subscriber>> queryExecution = new SqlQueryExecution<List<Subscriber>>() {

            @Override
            public String getSqlQuery() {
                return KilkariConstants.SELECT_SUBSCRIBERS_BY_NUMBER;
            }

            @Override
            public List<Subscriber> execute(Query query) {
                query.setClass(Subscriber.class);
                return (List<Subscriber>) query.execute(callingNumber);
            }
        };

        return subscriberDataService.executeSQLQuery(queryExecution);
    }

    @Override
    public Subscriber getSubscriberByBeneficiary(final MctsBeneficiary beneficiary) {

        if(beneficiary == null){
            LOGGER.debug("beneficiary is null : " + beneficiary);
            return null;
        }
        SqlQueryExecution<Subscriber> queryExecution = new SqlQueryExecution<Subscriber>() {

            @Override
            public String getSqlQuery() {
                return "select *  from nms_subscribers where mother_id_OID = ? or child_id_OID = ?";
            }

            @Override
            public Subscriber execute(Query query) {
                query.setClass(Subscriber.class);
                Long id = beneficiary.getId();
                if(id == null){
                    LOGGER.debug("getSubscriberByBeneficiary id is null" + beneficiary.getRchId());
                    return null;
                }
                ForwardQueryResult fqr = (ForwardQueryResult) query.execute(id, id);
                if (fqr.isEmpty()) {
                    return null;
                }
                List<Subscriber> subscriberList = (List<Subscriber>) fqr;
                if(subscriberList==null || subscriberList.size()==0 || subscriberList.isEmpty()){
                    return null;
                }
                else if(subscriberList.size()==1){
                    return subscriberList.get(0);
                }
                else {
                    LOGGER.debug("More than one row returned for beneficiary mother-id/child-id :  {}" , beneficiary.getId());

                    //if we have multiple subscriber for same user valid subscriber will be decided on below scenario
                    //1. If we don't have any active subscription for any subscriber we will consider older subscriber as valid subscriber
                    //2. If we have one or more active subscriptions for multiple subscriber we will consider latest subscriber with active subscriptions.

                    int activeSubscribers = 0;
                    Subscriber subscriber = new Subscriber();
                    for(Subscriber subscriber1 : subscriberList) {
                        if (subscriber1.getActiveAndPendingSubscriptions() != null && !subscriber1.getActiveAndPendingSubscriptions().isEmpty()) {
                            subscriber = subscriber1;
                            activeSubscribers++;
                        }
                    }
                    if(activeSubscribers==0) return subscriberList.get(0);
                    else return subscriber;
                }
            }
        };

        return subscriberDataService.executeSQLQuery(queryExecution);

    }

    @Override
    public Subscriber create(Subscriber subscriber) {
        try {
            return subscriberDataService.create(subscriber);
        } catch (ConstraintViolationException e) {
            LOGGER.error("4: List of constraints: {}", e.getConstraintViolations());
            throw e;
        }
    }

    @Override
    @Transactional
    public void updateStartDate(Subscriber subscriber) {

        Subscriber retrievedSubscriber = subscriberDataService.update(subscriber);

        // update start dates for subscriptions if reference date (LMP/DOB) has changed
        Set<Subscription> subscriptions = retrievedSubscriber.getActiveAndPendingSubscriptions();
        Iterator<Subscription> subscriptionIterator = subscriptions.iterator();
        Subscription subscription;

        while (subscriptionIterator.hasNext()) {
            subscription = subscriptionIterator.next();

            if (subscription.getSubscriptionPack().getType() == SubscriptionPackType.PREGNANCY) {

                if (subscriber.getLastMenstrualPeriod() != null) { // Subscribers via IVR will not have LMP
                    subscriptionService.updateStartDate(subscription, subscriber.getLastMenstrualPeriod());
                }

            } else { // SubscriptionPackType.CHILD

                if (subscriber.getDateOfBirth() != null) {  // Subscribers via IVR will not have DOB
                    subscriptionService.updateStartDate(subscription, subscriber.getDateOfBirth());
                }
            }
        }
    }

    @Override
    public void updateMsisdnForSubscriber(Subscriber subscriber, MctsBeneficiary beneficiary, Long newMsisdn) {
        SubscriptionPackType packType;
        packType = (beneficiary instanceof MctsChild) ? SubscriptionPackType.CHILD : SubscriptionPackType.PREGNANCY;

        List<Subscriber> subscriberWithMsisdns = getSubscriber(newMsisdn);
        if (!subscriberWithMsisdns.isEmpty()) {
            // this number is in use
            for (Subscriber subscriberWithMsisdn : subscriberWithMsisdns) {
                if (subscriptionService.getActiveSubscription(subscriberWithMsisdn, packType) != null) {
                    // in fact, it's in use for this pack -- reject the subscription
                    throw new IllegalStateException(
                            String.format("Can't change subscriber's MSISDN (%s) because it is already in use for %s pack",
                                    newMsisdn, packType));
                }
            }
        }

        // do the update -- by creating a new Subscriber object and re-linking the beneficiary and subscription to it

        Subscriber newSubscriber = new Subscriber(newMsisdn, subscriber.getLanguage(), subscriber.getCircle());
        Subscription subscription = subscriptionService.getActiveSubscription(subscriber, packType);
        newSubscriber.getSubscriptions().add(subscription);
        subscription.setSubscriber(newSubscriber);
        subscriber.getSubscriptions().remove(subscription);

        if (packType == SubscriptionPackType.CHILD) {
            subscriber.setChild(null);
            newSubscriber.setChild((MctsChild) beneficiary);
            newSubscriber.setDateOfBirth(subscriber.getDateOfBirth());
        } else {
            subscriber.setMother(null);
            newSubscriber.setMother((MctsMother) beneficiary);
            newSubscriber.setLastMenstrualPeriod(subscriber.getLastMenstrualPeriod());
        }

        subscriberDataService.create(newSubscriber);
        subscriberDataService.update(subscriber);
        subscriptionDataService.update(subscription);
    }

    @Override // NO CHECKSTYLE Cyclomatic Complexity
    public Subscription updateMotherSubscriber(Long msisdn, MctsMother motherUpdate, DateTime lmp, Map<String, Object> record, String action,String name,DateTime motherDOB,LocalDate lastUpdatedDateNic) { //NOPMD NcssMethodCount
        District district = motherUpdate.getDistrict(); // district should never be null here since we validate upstream on setLocation
        Circle circle = district.getCircle();
        Language language = district.getLanguage();
        SubscriptionPack pack = subscriptionPackDataService.byType(SubscriptionPackType.PREGNANCY);
        String motherBeneficiaryId = motherUpdate.getBeneficiaryId();
        List<Subscriber> subscriberByMsisdns = getSubscriber(msisdn);
        Subscriber subscriberByMctsId = getSubscriberByBeneficiary(motherUpdate);

        if (subscriberByMctsId == null) {   // No existing subscriber(number) attached to mother MCTS id
            if (subscriberByMsisdns.isEmpty()) {   // No subscriber attached to the number
                // create subscriber, beneficiary, subscription and return
                return createSubscriber(msisdn, motherUpdate, lmp, pack, language, circle);
            } else { // subscriber (number) is already in use
                for (Subscriber subscriber : subscriberByMsisdns) {
                    if (subscriptionService.activeSubscriptionByMsisdnMcts(subscriber, msisdn, pack.getType(), motherBeneficiaryId, null)) {
                        LOGGER.debug("An active subscription is already present for this phone number.");
                        subscriptionErrorDataService.create(new SubscriptionError(msisdn, motherUpdate.getBeneficiaryId(), SubscriptionRejectionReason.MSISDN_ALREADY_SUBSCRIBED, pack.getType(), "Msisdn already has an active Subscription", SubscriptionOrigin.MCTS_IMPORT));
                        return null;
                    }
                    MctsMother mother = subscriber.getMother();
                    if (mother == null) {  // Check if it's an existing anonymous mother
                        Set<Subscription> subscriptions = subscriber.getAllSubscriptions();
                        Subscription motherSubscription = subscriptionService.getIVRSubscription(subscriptions, SubscriptionPackType.PREGNANCY);
                        if (motherSubscription != null) {
                            Subscription childSubscription = subscriptionService.getIVRSubscription(subscriptions, SubscriptionPackType.CHILD);
                            if (childSubscription == null) {
                                //update the anonymous mother with MCTS details
                                motherUpdate.setLastMenstrualPeriod(lmp);
                                subscriber.setLastMenstrualPeriod(lmp);
                                subscriber.setMother(motherUpdate);
                                subscriber.setModificationDate(DateTime.now());
                                return updateOrCreateSubscription(subscriber, motherSubscription, lmp, pack, language, circle, SubscriptionOrigin.MCTS_IMPORT, false);
                            } else {
                                // Both IVR mother and child exists. Create a new subscriber record for mother and set the same in existing motherSubscription
                                subscriberByMctsId = new Subscriber(msisdn, language);
                                subscriberByMctsId.setLastMenstrualPeriod(lmp);
                                motherUpdate.setLastMenstrualPeriod(lmp);
                                subscriberByMctsId.setMother(motherUpdate);
                                subscriberByMctsId = create(subscriberByMctsId);
                                motherSubscription.setSubscriber(subscriberByMctsId);

                                return updateOrCreateSubscription(subscriberByMctsId, motherSubscription, lmp, pack, language, circle, SubscriptionOrigin.MCTS_IMPORT, false);
                            }
                        }
                    }
                }
                return createSubscriber(msisdn, motherUpdate, lmp, pack, language, circle);
            }
        } else { // subscriberByBeneficiary != null aka. MCTS mother exists in motech
            if (subscriberByMsisdns.isEmpty()) {   //no subscriber attached to the new number
                // We got here because beneficiary's phone number changed
                subscriptionService.deleteBlockedMsisdn(motherUpdate.getId(), subscriberByMctsId.getCallingNumber(), msisdn);
                motherUpdate.setName(name);
                motherUpdate.setDateOfBirth(motherDOB);
                motherUpdate.setLastMenstrualPeriod(lmp);
                motherUpdate.setUpdatedDateNic(lastUpdatedDateNic);
                subscriberByMctsId.setCallingNumber(msisdn);
                Subscription subscription = subscriptionService.getActiveSubscription(subscriberByMctsId, pack.getType());
                subscriberByMctsId.setLastMenstrualPeriod(lmp);
                subscriberByMctsId.setModificationDate(DateTime.now());

                return updateOrCreateSubscription(subscriberByMctsId, subscription, lmp, pack, language, circle, SubscriptionOrigin.MCTS_IMPORT, false);
            } else {    // we have a subscriber by phone# and also one with the MCTS id
                for (Subscriber subscriber : subscriberByMsisdns) {
                    if (subscriberByMctsId.getId().equals(subscriber.getId())) {
                        Subscription subscription = subscriptionService.getActiveSubscription(subscriberByMctsId, pack.getType());
                        motherUpdate.setName(name);
                        motherUpdate.setDateOfBirth(motherDOB);
                        motherUpdate.setLastMenstrualPeriod(lmp);
                        motherUpdate.setUpdatedDateNic(lastUpdatedDateNic);
                        subscriberByMctsId.setLastMenstrualPeriod(lmp);
                        subscriberByMctsId.setModificationDate(DateTime.now());
                        return updateOrCreateSubscription(subscriberByMctsId, subscription, lmp, pack, language, circle, SubscriptionOrigin.MCTS_IMPORT, false);
                    }
                }
                subscriptionErrorDataService.create(new SubscriptionError(msisdn, motherUpdate.getBeneficiaryId(), SubscriptionRejectionReason.MSISDN_ALREADY_SUBSCRIBED, pack.getType(), "Unrelated Subscribers exists with this Msisdn and MctsId", SubscriptionOrigin.MCTS_IMPORT));
                return null;
            }
        }
    }

    @Override // NO CHECKSTYLE Cyclomatic Complexity
    public Subscription updateRchMotherSubscriber(Long msisdn, MctsMother motherUpdate, DateTime lmp, Long caseNo, Boolean deactivate, Map<String, Object> record, String action, String name,DateTime motherDOB,LocalDate lastUpdatedDateNic , DateTime motherRegistrationDate) { //NOPMD NcssMethodCount
        District district = motherUpdate.getDistrict(); // district should never be null here since we validate upstream on setLocation
        Circle circle = district.getCircle();
        Language language = district.getLanguage();
        SubscriptionPack pack = subscriptionPackDataService.byType(SubscriptionPackType.PREGNANCY);
        List<Subscriber> subscribersByMsisdn = getSubscriber(msisdn);
        Subscriber subscriberByRchId = getSubscriberByBeneficiary(motherUpdate);
        boolean greaterCase = false;

        if (subscriberByRchId == null) { // No existing subscriber(number) attached to mother RCH id
            if (subscribersByMsisdn.isEmpty()) {  // No subscriber attached to the number
                // Reject the record if it's aborted/stillbirth or death
                if (deactivate) {
                    subscriptionErrorDataService.create(new SubscriptionError(msisdn, motherUpdate.getRchId(), SubscriptionRejectionReason.ABORT_STILLBIRTH_DEATH, pack.getType(), "", SubscriptionOrigin.RCH_IMPORT));
                    return null;
                }
                // create subscriber, beneficiary, subscription and return
                Subscriber subscriberByMsisdn = new Subscriber(msisdn, language);
                subscriberByMsisdn.setLastMenstrualPeriod(lmp);
                motherUpdate.setLastMenstrualPeriod(lmp);
                subscriberByMsisdn.setMother(motherUpdate);
                subscriberByMsisdn.setCaseNo(caseNo);
                motherUpdate.setMaxCaseNo(caseNo);
                motherUpdate.setRegistrationDate(motherRegistrationDate);
                create(subscriberByMsisdn);
                return subscriptionService.createSubscription(subscriberByMsisdn, msisdn, language, circle, pack, SubscriptionOrigin.RCH_IMPORT);
            } else {  // subscriber (number) is already in use
                if (subscriptionService.activeSubscriptionByMsisdnRch(subscribersByMsisdn, msisdn, SubscriptionPackType.PREGNANCY, motherUpdate.getRchId(), null)) {
                    subscriptionErrorDataService.create(new SubscriptionError(msisdn, motherUpdate.getRchId(), SubscriptionRejectionReason.MSISDN_ALREADY_SUBSCRIBED, pack.getType(), "Subscriber exists with this Msisdn", SubscriptionOrigin.RCH_IMPORT));
                    return null;
                } else {
                    Subscriber subscriberByMsisdn = new Subscriber(msisdn, language);
                    subscriberByMsisdn.setLastMenstrualPeriod(lmp);
                    motherUpdate.setLastMenstrualPeriod(lmp);
                    subscriberByMsisdn.setMother(motherUpdate);
                    subscriberByMsisdn.setCaseNo(caseNo);
                    motherUpdate.setMaxCaseNo(caseNo);
                    motherUpdate.setRegistrationDate(motherRegistrationDate);
                    create(subscriberByMsisdn);
                    return subscriptionService.createSubscription(subscriberByMsisdn, msisdn, language, circle, pack, SubscriptionOrigin.RCH_IMPORT);
                }
            }
        } else { // subscriberByBeneficiary != null aka. RCH mother exists in motech
            if (subscribersByMsisdn.isEmpty()) {  //no subscriber attached to the new number
                // We got here because beneficiary's phone number changed
                if (subscriberByRchId.getCaseNo() == null) {
                    greaterCase = true;
                    subscriberByRchId.setCaseNo(caseNo);
                    motherUpdate.setMaxCaseNo(caseNo);
                } else if (subscriberByRchId.getCaseNo() > caseNo) {
                    subscriptionErrorDataService.create(new SubscriptionError(msisdn, motherUpdate.getRchId(), SubscriptionRejectionReason.INVALID_CASE_NO, pack.getType(), "Active subscription exists with different caseNo", SubscriptionOrigin.RCH_IMPORT));
                    return null;
                }
                subscriptionService.deleteBlockedMsisdn(motherUpdate.getId(), subscriberByRchId.getCallingNumber(), msisdn);
                subscriberByRchId.setCallingNumber(msisdn);
                Subscription subscription = subscriptionService.getActiveSubscription(subscriberByRchId, pack.getType());
                Subscription latestDeactivatedSubscription = subscriptionService.getLatestDeactivatedSubscription(subscriberByRchId, pack.getType(), true);
                if(caseNo > subscriberByRchId.getCaseNo()){
                    greaterCase = true;
                }

                if (subscription == null && latestDeactivatedSubscription != null && Objects.equals(subscriberByRchId.getCaseNo(), caseNo)) {
                    if (latestDeactivatedSubscription.getStatus().equals(SubscriptionStatus.DEACTIVATED) && (!(latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.LOW_LISTENERSHIP) || latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.WEEKLY_CALLS_NOT_ANSWERED)))) {
                        subscription = latestDeactivatedSubscription;
                    } else if (latestDeactivatedSubscription.getStatus().equals(SubscriptionStatus.COMPLETED)) {
                        subscriberByRchId.setLastMenstrualPeriod(lmp);
                        subscriberByRchId.setModificationDate(DateTime.now());
                        motherUpdate.setName(name);
                        motherUpdate.setDateOfBirth(motherDOB);
                        motherUpdate.setLastMenstrualPeriod(lmp);
                        motherUpdate.setUpdatedDateNic(lastUpdatedDateNic);
                        motherUpdate.setRegistrationDate(motherRegistrationDate);
                        return latestDeactivatedSubscription;
                    }
                }else {
                    subscriberByRchId.setCaseNo(caseNo);
                    motherUpdate.setMaxCaseNo(caseNo);
                }
                subscriberByRchId.setLastMenstrualPeriod(lmp);
                subscriberByRchId.setModificationDate(DateTime.now());
                motherUpdate.setName(name);
                motherUpdate.setDateOfBirth(motherDOB);
                motherUpdate.setLastMenstrualPeriod(lmp);
                motherUpdate.setUpdatedDateNic(lastUpdatedDateNic);
                motherUpdate.setRegistrationDate(motherRegistrationDate);

                if(subscription != null){
                    if ( !((subscriberByRchId.getLastMenstrualPeriod().getDayOfYear() == lmp.getDayOfYear()) && (subscriberByRchId.getLastMenstrualPeriod().getYear() == lmp.getYear()))) {
                        subscriptionService.deleteCallRetry(subscription.getSubscriptionId());
                    }else {
                        subscriptionService.updateCallRetry(subscription.getSubscriptionId(), msisdn);
                    }
                }
                return updateOrCreateSubscription(subscriberByRchId, subscription, lmp, pack, language, circle, SubscriptionOrigin.RCH_IMPORT, greaterCase);
            } else {  // we have a subscriber by phone# and also one with the RCH id
                if (subscriptionService.activeSubscriptionByMsisdnRch(subscribersByMsisdn,msisdn, SubscriptionPackType.PREGNANCY, motherUpdate.getRchId(), null)) {
                    subscriptionErrorDataService.create(new SubscriptionError(msisdn, motherUpdate.getRchId(), SubscriptionRejectionReason.MSISDN_ALREADY_SUBSCRIBED, pack.getType(), "Unrelated Subscribers exists with this Msisdn and RchId", SubscriptionOrigin.RCH_IMPORT));
                    return null;
                }
                for (Subscriber subscriber : subscribersByMsisdn) {
                    if ((subscriberByRchId.getId().equals(subscriber.getId())) && (subscriberByRchId.getCaseNo() == null || subscriberByRchId.getCaseNo() <= caseNo)) {
                        Subscription subscription = subscriptionService.getActiveSubscription(subscriberByRchId, pack.getType());
                        if ((subscription != null) && !((subscriberByRchId.getLastMenstrualPeriod().getDayOfYear() == lmp.getDayOfYear()) && (subscriberByRchId.getLastMenstrualPeriod().getYear() == lmp.getYear()))) {
                            subscriptionService.deleteCallRetry(subscription.getSubscriptionId());
                        }
                        if (subscriber.getCaseNo() != null && caseNo > subscriber.getCaseNo()) {
                            greaterCase = true;
                        }
                        Subscription latestDeactivatedSubscription = subscriptionService.getLatestDeactivatedSubscription(subscriberByRchId, pack.getType(), true);

                        if (subscription == null && latestDeactivatedSubscription != null && subscriber.getCaseNo() != null && Objects.equals(subscriberByRchId.getCaseNo(), caseNo)) {
                            SubscriptionStatus status = latestDeactivatedSubscription.getStatus();
                            DeactivationReason reason = latestDeactivatedSubscription.getDeactivationReason();

                            if (status.equals(SubscriptionStatus.DEACTIVATED) && DeactivationReason.INVALID_NUMBER.equals(reason) && !subscriberByRchId.getCallingNumber().equals(msisdn)) {
                                subscription = latestDeactivatedSubscription;

                            } else if ((status.equals(SubscriptionStatus.DEACTIVATED) && DeactivationReason.INVALID_NUMBER.equals(reason) && subscriberByRchId.getCallingNumber().equals(msisdn)) || status.equals(SubscriptionStatus.COMPLETED)) {

                                subscriberByRchId.setModificationDate(DateTime.now());
                                subscriberByRchId.setLastMenstrualPeriod(lmp);
                                motherUpdate.setName(name);
                                motherUpdate.setDateOfBirth(motherDOB);
                                motherUpdate.setLastMenstrualPeriod(lmp);
                                motherUpdate.setUpdatedDateNic(lastUpdatedDateNic);
                                return latestDeactivatedSubscription;

                            } else if (status.equals(SubscriptionStatus.DEACTIVATED) && ((!DeactivationReason.LOW_LISTENERSHIP.equals(reason) && !DeactivationReason.WEEKLY_CALLS_NOT_ANSWERED.equals(reason)))) {
                                subscription = latestDeactivatedSubscription;
                            }
                        } else {
                            motherUpdate.setMaxCaseNo(caseNo);
                        }

                        subscriberByRchId.setLastMenstrualPeriod(lmp);

                        motherUpdate.setName(name);
                        motherUpdate.setDateOfBirth(motherDOB);
                        motherUpdate.setLastMenstrualPeriod(lmp);
                        motherUpdate.setUpdatedDateNic(lastUpdatedDateNic);
                        subscriberByRchId.setCaseNo(caseNo);
                        subscriberByRchId.setModificationDate(DateTime.now());
                        return updateOrCreateSubscription(subscriberByRchId, subscription, lmp, pack, language, circle, SubscriptionOrigin.RCH_IMPORT, greaterCase);
                    }
                }

                    Subscription subscription = subscriptionService.getActiveSubscription(subscriberByRchId, pack.getType());
                    Subscription latestDeactivatedSubscription = subscriptionService.getLatestDeactivatedSubscription(subscriberByRchId, pack.getType(), true);

                    subscriberByRchId.setLastMenstrualPeriod(lmp);
                    motherUpdate.setName(name);
                    motherUpdate.setDateOfBirth(motherDOB);
                    motherUpdate.setLastMenstrualPeriod(lmp);
                    motherUpdate.setUpdatedDateNic(lastUpdatedDateNic);
                if (subscriberByRchId.getCaseNo() != null && caseNo > subscriberByRchId.getCaseNo()) {
                    greaterCase = true;
                }
                if (subscription == null && latestDeactivatedSubscription != null && Objects.equals(subscriberByRchId.getCaseNo(), caseNo)) {
                    SubscriptionStatus status = latestDeactivatedSubscription.getStatus();
                    DeactivationReason reason = latestDeactivatedSubscription.getDeactivationReason();

                    if (status.equals(SubscriptionStatus.COMPLETED) || (!DeactivationReason.LOW_LISTENERSHIP.equals(reason) && !DeactivationReason.WEEKLY_CALLS_NOT_ANSWERED.equals(reason))) {

                        if (status.equals(SubscriptionStatus.DEACTIVATED) && DeactivationReason.INVALID_NUMBER.equals(reason) && !subscriberByRchId.getCallingNumber().equals(msisdn)) {
                            subscription = latestDeactivatedSubscription;

                        } else if (status.equals(SubscriptionStatus.COMPLETED) || (DeactivationReason.INVALID_NUMBER.equals(reason) && subscriberByRchId.getCallingNumber().equals(msisdn)) ) {
                            subscriberByRchId.setModificationDate(DateTime.now());
                            return latestDeactivatedSubscription;
                        } else {
                            subscription = latestDeactivatedSubscription;
                        }
                    }
                }
                    subscriberByRchId.setCallingNumber(msisdn);
                    subscriberByRchId.setCaseNo(caseNo);
                    motherUpdate.setMaxCaseNo(caseNo);
                    subscriberByRchId.setModificationDate(DateTime.now());
                    if(subscription != null){subscriptionService.deleteCallRetry(subscription.getSubscriptionId());}

                    return updateOrCreateSubscription(subscriberByRchId, subscription, lmp, pack, language, circle, SubscriptionOrigin.RCH_IMPORT, greaterCase);
            }
        }
    }

    @Override // NO CHECKSTYLE Cyclomatic Complexity
    public ChildImportRejection updateChildSubscriber(Long msisdn, MctsChild childUpdate, DateTime dob, Map<String, Object> record, String action) { //NOPMD NcssMethodCount
        District district = childUpdate.getDistrict(); // district should never be null here since we validate upstream on setLocation
        Circle circle = district.getCircle();
        Language language = district.getLanguage();
        SubscriptionPack pack = subscriptionPackDataService.byType(SubscriptionPackType.CHILD);
        List<Subscriber> subscriberByMsisdns = getSubscriber(msisdn);
        Subscriber subscriberByMctsId = getSubscriberByBeneficiary(childUpdate);
        String motherBeneficiaryId = childUpdate.getMother() == null ? null : childUpdate.getMother().getBeneficiaryId();
        String childBeneficiaryId = childUpdate.getBeneficiaryId();
        Subscription finalSubscription = null;

        if (subscriberByMctsId == null) {   // No existing subscriber(number) attached to child MCTS id

            if (subscriberByMsisdns.isEmpty()) {   // No subscriber attached to new msisdn
                if (childUpdate.getMother() != null) {
                    Subscriber subscriberByMotherMctsId = getSubscriberByBeneficiary(childUpdate.getMother());
                    // If Mother of the child is already subscribed and has no other child attached to it, update msisdn in the same record
                    if (subscriberByMotherMctsId != null) {
                        if (subscriberByMotherMctsId.getChild() != null) {
                            return childRejectionMcts(convertMapToChild(record), false, RejectionReasons.MOBILE_NUMBER_ALREADY_SUBSCRIBED.toString(), action);
                        } else { // Update child and msisdn in same subscriber
                            subscriberByMotherMctsId.setChild(childUpdate);
                            subscriberByMotherMctsId.setDateOfBirth(dob);
                            subscriptionService.deleteBlockedMsisdn(childUpdate.getMother().getId(), subscriberByMotherMctsId.getCallingNumber(), msisdn);
                            subscriberByMotherMctsId.setCallingNumber(msisdn);
                            subscriberByMotherMctsId.setModificationDate(DateTime.now());
                            finalSubscription = subscriptionService.createSubscription(subscriberByMotherMctsId, msisdn, language, circle, pack, SubscriptionOrigin.MCTS_IMPORT);
                        }
                    } else {
                        // create subscriber, beneficiary, subscription and return
                        finalSubscription = createSubscriber(msisdn, childUpdate, dob, pack, language, circle);
                    }
                } else {
                    // create subscriber, beneficiary, subscription and return
                    finalSubscription = createSubscriber(msisdn, childUpdate, dob, pack, language, circle);
                }
            } else { // subscriber number is already in use
                for (Subscriber subscriber : subscriberByMsisdns) {
                    if (subscriptionService.activeSubscriptionByMsisdnMcts(subscriber,msisdn, pack.getType(), motherBeneficiaryId, childBeneficiaryId)) {
                        LOGGER.debug("An active subscription is already present for this phone number.");
                        return childRejectionMcts(convertMapToChild(record), false, RejectionReasons.MOBILE_NUMBER_ALREADY_SUBSCRIBED.toString(), action);
                    }

                    if (subscriber.getMother() != null) {
                        if (childUpdate.getMother() != null && childUpdate.getMother().equals(subscriber.getMother())) { // If existing mother is of child then update only if no other active child is present for same msisdn
                            subscriberByMctsId = subscriber;
                        } else {  // We got here because this record is of MCTS mother. Check if it has IVR child
                            Subscription childSubscription = splitSubscribers(subscriber, msisdn, childUpdate, dob, pack, language, circle);
                            if (childSubscription != null) {
                                finalSubscription = childSubscription;
                            }
                        }
                    } else { // Mother and child both are null. So must be an anonymous user. Check for IVR mother
                        Subscription motherSubscription = subscriptionService.getIVRSubscription(subscriber.getAllSubscriptions(), SubscriptionPackType.PREGNANCY);
                        if (motherSubscription == null) { // Update the Child anonymous user
                            subscriber.setDateOfBirth(dob);
                            subscriber.setChild(childUpdate);
                            subscriber.setMother(childUpdate.getMother());
                            subscriber.setModificationDate(DateTime.now());
                            Subscription childSubscription = subscriptionService.getActiveSubscription(subscriber, SubscriptionPackType.CHILD);
                            finalSubscription = updateOrCreateSubscription(subscriber, childSubscription, dob, pack, language, circle, SubscriptionOrigin.MCTS_IMPORT, false);
                        } else { // IVR mother. Check for IVR child. If not found, create a new subscriber
                            Subscription childSubscription = splitSubscribers(subscriber, msisdn, childUpdate, dob, pack, language, circle);
                            if (childSubscription != null) {
                                finalSubscription = childSubscription;
                            }
                        }
                    }
                }

                if (subscriberByMctsId != null) {
                    subscriberByMctsId.setDateOfBirth(dob);
                    subscriberByMctsId.setChild(childUpdate);
                    subscriberByMctsId.setModificationDate(DateTime.now());
                    finalSubscription = subscriptionService.createSubscription(subscriberByMctsId, msisdn, language, circle, pack, SubscriptionOrigin.MCTS_IMPORT);
                }
                if (finalSubscription == null) {
                    finalSubscription = createSubscriber(msisdn, childUpdate, dob, pack, language, circle);
                }
            }
        } else { // Found existing child beneficiary in our system

            if (subscriberByMsisdns.isEmpty() && childUpdate.getMother() != null) {   // no subscriber attached to the new number
                // We got here because beneficiary's phone number changed
                subscriptionService.deleteBlockedMsisdn(childUpdate.getMother().getId(), subscriberByMctsId.getCallingNumber(), msisdn);
                subscriberByMctsId.setCallingNumber(msisdn);
                if (subscriberByMctsId.getMother() == null) {
                    subscriberByMctsId.setMother(childUpdate.getMother());
                }
                Subscription subscription = subscriptionService.getActiveSubscription(subscriberByMctsId, pack.getType());
                subscriberByMctsId.setDateOfBirth(dob);
                subscriberByMctsId.setModificationDate(DateTime.now());
                finalSubscription = updateOrCreateSubscription(subscriberByMctsId, subscription, dob, pack, language, circle, SubscriptionOrigin.MCTS_IMPORT, false);
            } else if (subscriberByMsisdns.isEmpty() && childUpdate.getMother() == null) {
                subscriberByMctsId.setCallingNumber(msisdn);
                Subscription subscription = subscriptionService.getActiveSubscription(subscriberByMctsId, pack.getType());
                subscriberByMctsId.setDateOfBirth(dob);
                subscriberByMctsId.setModificationDate(DateTime.now());
                finalSubscription = updateOrCreateSubscription(subscriberByMctsId, subscription, dob, pack, language, circle, SubscriptionOrigin.MCTS_IMPORT, false);
            }
            else {    // we have a subscriber by phone# and also one with the MCTS id
                Boolean isSameSubscriber = true;
                for (Subscriber subscriber : subscriberByMsisdns) {
                    if (subscriberByMctsId.getId().equals(subscriber.getId())) {
                        Subscription subscription = subscriptionService.getActiveSubscription(subscriberByMctsId, pack.getType());
                        subscriberByMctsId.setDateOfBirth(dob);
                        if (subscriberByMctsId.getMother() == null) {
                            subscriberByMctsId.setMother(childUpdate.getMother());
                        }
                        subscriberByMctsId.setModificationDate(DateTime.now());
                        finalSubscription = updateOrCreateSubscription(subscriberByMctsId, subscription, dob, pack, language, circle, SubscriptionOrigin.MCTS_IMPORT, false);
                    } else {
                        //A different subscriber found with same mobile number
                        isSameSubscriber = false;
                    }
                }
                if (!isSameSubscriber) {
                    return childRejectionMcts(convertMapToChild(record), false, RejectionReasons.MOBILE_NUMBER_ALREADY_SUBSCRIBED.toString(), action);
                }
            }
        }

        liveBirthChildDeathCheck(finalSubscription, record);

        childRejectionMcts(convertMapToChild(record), true, null, action);
        return null;

    }

    private void liveBirthChildDeathCheck(Subscription subscription, Map<String, Object> record) {
        // a child subscription was created -- deactivate mother's pregnancy subscription if she has one
        Subscriber subscriber = subscription != null ? subscription.getSubscriber() : null;
        Subscription pregnancySubscription = subscriptionService.getActiveSubscription(subscriber,
                SubscriptionPackType.PREGNANCY);
        if (pregnancySubscription != null) {
            subscriptionService.deactivateSubscription(pregnancySubscription, DeactivationReason.LIVE_BIRTH);
        }

        Boolean death = (Boolean) record.get(KilkariConstants.DEATH);
        if ((death != null) && death) {
            subscriptionService.deactivateSubscription(subscription, DeactivationReason.CHILD_DEATH);
        }
    }

    private void liveBirthChildCheck(Subscription subscription) {
        // a child subscription was created -- deactivate mother's pregnancy subscription if she has one
        Subscriber subscriber = subscription != null ? subscription.getSubscriber() : null;
        Subscription pregnancySubscription = subscriptionService.getActiveSubscription(subscriber,
                SubscriptionPackType.PREGNANCY);
        if (pregnancySubscription != null) {
            subscriptionService.deactivateSubscription(pregnancySubscription, DeactivationReason.LIVE_BIRTH);
        }

    }

    @Override // NO CHECKSTYLE Cyclomatic Complexity
    public ChildImportRejection updateRchChildSubscriber(Long msisdn, MctsChild childUpdate, DateTime dob, Map<String, Object> record, String action) { //NOPMD NcssMethodCount
        District district = childUpdate.getDistrict(); // district should never be null here since we validate upstream on setLocation
        Circle circle = district.getCircle();
        Language language = district.getLanguage();
        SubscriptionPack pack = subscriptionPackDataService.byType(SubscriptionPackType.CHILD);
        List<Subscriber> subscribersByMsisdn = getSubscriber(msisdn);
        Subscriber subscriberByRchId = getSubscriberByBeneficiary(childUpdate);
        Subscription finalSubscription = null;
        String motherRchId;
        String name = childUpdate.getName();
        DateTime registrationDate ;
        if (childUpdate.getMother() != null) {
            motherRchId = childUpdate.getMother().getRchId();
        } else {
            motherRchId = null;
        }

        if (!subscribersByMsisdn.isEmpty() && subscriptionService.activeSubscriptionByMsisdnRch(subscribersByMsisdn, msisdn, SubscriptionPackType.CHILD, motherRchId, childUpdate.getRchId())) {
            return childRejectionRch(convertMapToRchChild(record), false, RejectionReasons.MOBILE_NUMBER_ALREADY_SUBSCRIBED.toString(), action);
        }

        if(subscriberByRchId==null && childUpdate.getMother() != null && childUpdate.getMother().getId() != null &&  getSubscriberListByMother(childUpdate.getMother().getId()) != null){
            Subscriber motherSubscriberByRchId = getSubscriberListByMother(childUpdate.getMother().getId());
            Subscription subscription = subscriptionService.getActiveSubscription(motherSubscriberByRchId, pack.getType());
            if(motherSubscriberByRchId.getChild()!=null && motherSubscriberByRchId.getChild().getRchId()!=null  && !Objects.equals(motherSubscriberByRchId.getChild().getRchId(), childUpdate.getRchId()) && subscription!=null){
                return childRejectionRch(convertMapToRchChild(record), false, RejectionReasons.ALREADY_SUBSCRIBED.toString(), action);
            }
        }

        if(subscriberByRchId!=null && childUpdate.getMother()!=null && childUpdate.getMother().getId() != null && getSubscriberListByMother(childUpdate.getMother().getId()) != null){
            Subscriber motherSubscriberByRchId = getSubscriberListByMother(childUpdate.getMother().getId());
            Subscription subscription = subscriptionService.getActiveSubscription(motherSubscriberByRchId, pack.getType());
            if(!Objects.equals(motherSubscriberByRchId.getId(), subscriberByRchId.getId()) && subscription!=null){
                return childRejectionRch(convertMapToRchChild(record), false, RejectionReasons.ALREADY_SUBSCRIBED.toString(), action);
            }
        }

        if (subscriberByRchId != null) { //subscriber exists with the provided RCH id
            if (subscribersByMsisdn.isEmpty()) { //no subscriber with provided msisdn
                //subscriber's number has changed
                //update msisdn in subscriber and delete msisdn from blocked list
                if(subscriberByRchId.getMother()==null && childUpdate.getMother()!= null){
                    Subscriber motherSubscriber = getSubscriberListByMother(childUpdate.getMother().getId());
                    Subscription motherSubscription = subscriptionService.getActiveSubscription(motherSubscriber,SubscriptionPackType.PREGNANCY);
                    liveBirthChildCheck(motherSubscription);
                }
                subscriptionService.deleteBlockedMsisdn(childUpdate.getId(), subscriberByRchId.getCallingNumber(), msisdn);
                subscriberByRchId.setCallingNumber(msisdn);
                if (subscriberByRchId.getMother() == null) {
                    subscriberByRchId.setMother(childUpdate.getMother());
                }
                Subscription subscription = subscriptionService.getActiveSubscription(subscriberByRchId, pack.getType());
                Subscription latestDeactivatedSubscription = subscriptionService.getLatestDeactivatedSubscription(subscriberByRchId, pack.getType(), true);

                if (subscription == null && latestDeactivatedSubscription != null) {
                    if (latestDeactivatedSubscription.getStatus().equals(SubscriptionStatus.COMPLETED) ||
                            latestDeactivatedSubscription.getStatus().equals(SubscriptionStatus.DEACTIVATED) &&
                                    !(latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.LOW_LISTENERSHIP) || latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.WEEKLY_CALLS_NOT_ANSWERED) || latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.INVALID_NUMBER)) ){
                        subscriberByRchId.setDateOfBirth(dob);
                        subscriberByRchId.setModificationDate(DateTime.now());
                        return null;
                }else if(latestDeactivatedSubscription.getStatus().equals(SubscriptionStatus.DEACTIVATED) && (latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.INVALID_NUMBER))) {
                    subscription = latestDeactivatedSubscription;
                     }
                }
                subscriberByRchId.setDateOfBirth(dob);
                subscriberByRchId.setModificationDate(DateTime.now());
                // Delete that record from retry table as beneficiary gets their mobile number update
                if(subscription != null){
                    if ((subscriberByRchId.getDateOfBirth().getDayOfYear() != dob.getDayOfYear())){
                        subscriptionService.deleteCallRetry(subscription.getSubscriptionId());
                    }else {
                        subscriptionService.updateCallRetry(subscription.getSubscriptionId(), msisdn);
                    }
                }
                finalSubscription = updateOrCreateSubscription(subscriberByRchId, subscription, dob, pack, language, circle, SubscriptionOrigin.RCH_IMPORT, false);
            } else {
                //subscriber found with provided msisdn
                if(subscriberByRchId.getMother()==null && childUpdate.getMother()!= null){
                    Boolean isSameSubscriber = true;
                    Subscriber motherSubscriber = getSubscriberListByMother(childUpdate.getMother().getId());
                    Subscription motherSubscription = subscriptionService.getActiveSubscription(motherSubscriber,SubscriptionPackType.PREGNANCY);
                    liveBirthChildCheck(motherSubscription);

                    for (Subscriber subscriber : subscribersByMsisdn) {

                        if (subscriber.getId().equals(subscriberByRchId.getId())) {
                            Subscription subscription = subscriptionService.getActiveSubscription(subscriberByRchId, pack.getType());
                            if (subscriberByRchId.getMother() == null) {
                                subscriberByRchId.setMother(childUpdate.getMother());
                            }
                            Subscription latestDeactivatedSubscription = subscriptionService.getLatestDeactivatedSubscription(subscriberByRchId, pack.getType(), true);
                            if (subscription == null && latestDeactivatedSubscription != null) {
                                if (latestDeactivatedSubscription.getStatus().equals(SubscriptionStatus.COMPLETED) ||
                                        ((latestDeactivatedSubscription.getStatus().equals(SubscriptionStatus.DEACTIVATED) && !(latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.LOW_LISTENERSHIP) || latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.WEEKLY_CALLS_NOT_ANSWERED))))) {
                                    subscriberByRchId.setDateOfBirth(dob);
                                    subscriberByRchId.setModificationDate(DateTime.now());
                                    return null;
                                }
                            }
                            subscriberByRchId.setDateOfBirth(dob);
                            subscriberByRchId.setModificationDate(DateTime.now());
                            finalSubscription = updateOrCreateSubscription(subscriberByRchId, subscription, dob, pack, language, circle, SubscriptionOrigin.RCH_IMPORT, false);
                        } else {
                            //A different subscriber found with same mobile number
                            isSameSubscriber = false;
                        }
                    }
                    if (!isSameSubscriber) {
                        if (subscriptionService.activeSubscriptionByMsisdnRch(subscribersByMsisdn, msisdn, SubscriptionPackType.CHILD, motherRchId, childUpdate.getRchId())) {
                            return childRejectionRch(convertMapToRchChild(record), false, RejectionReasons.MOBILE_NUMBER_ALREADY_SUBSCRIBED.toString(), action);
                        } else {
                            subscriberByRchId.setCallingNumber(msisdn);
                            if (subscriberByRchId.getMother() == null) {
                                subscriberByRchId.setMother(childUpdate.getMother());
                            }
                            Subscription subscription = subscriptionService.getActiveSubscription(subscriberByRchId, pack.getType());
                            Subscription latestDeactivatedSubscription = subscriptionService.getLatestDeactivatedSubscription(subscriberByRchId, pack.getType(), true);
                            if (subscription == null && latestDeactivatedSubscription != null) {
                                if (latestDeactivatedSubscription.getStatus().equals(SubscriptionStatus.COMPLETED) ||
                                        latestDeactivatedSubscription.getStatus().equals(SubscriptionStatus.DEACTIVATED) &&
                                                !(latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.LOW_LISTENERSHIP) || latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.WEEKLY_CALLS_NOT_ANSWERED) || latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.INVALID_NUMBER)) ){
                                    subscriberByRchId.setDateOfBirth(dob);
                                    subscriberByRchId.setModificationDate(DateTime.now());
                                    return null;
                                }else if(latestDeactivatedSubscription.getStatus().equals(SubscriptionStatus.DEACTIVATED) && (latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.INVALID_NUMBER))) {
                                    subscription = latestDeactivatedSubscription;
                                }
                            }
                            subscriberByRchId.setDateOfBirth(dob);
                            subscriberByRchId.setModificationDate(DateTime.now());
                            finalSubscription = updateOrCreateSubscription(subscriberByRchId, subscription, dob, pack, language, circle, SubscriptionOrigin.RCH_IMPORT, false);
                        }
                    }
                }
                else if(childUpdate.getMother()!=null){
                    Subscriber subscriber = getSubscriberListByMother(childUpdate.getMother().getId());
                    Subscription subscription = subscriptionService.getActiveSubscription(subscriber, pack.getType());
                    Subscription latestDeactivatedSubscription = subscriptionService.getLatestDeactivatedSubscription(subscriberByRchId, pack.getType(), true);

                    if (Objects.equals(subscriber.getId(), subscriberByRchId.getId()) && subscription == null && latestDeactivatedSubscription != null && !Objects.equals(subscriber.getCallingNumber(), msisdn) ) {
                        if(latestDeactivatedSubscription.getStatus().equals(SubscriptionStatus.DEACTIVATED) && !(latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.LOW_LISTENERSHIP) || latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.WEEKLY_CALLS_NOT_ANSWERED))) {
                            subscription = latestDeactivatedSubscription;
                        }
                    }
                    if ((!Objects.equals(subscriber.getCallingNumber(), msisdn) || (subscriber.getDateOfBirth() == null || subscriber.getDateOfBirth().getDayOfYear() != dob.getDayOfYear())) && subscription!=null){
                        subscriptionService.deleteCallRetry(subscription.getSubscriptionId());
                    }
                    subscriber.setCallingNumber(msisdn);
                    subscriber.setDateOfBirth(dob);
                    subscriber.setChild(childUpdate);
                    subscriber.setModificationDate(DateTime.now());
                    finalSubscription = updateOrCreateSubscription(subscriber, subscription, dob, pack, language, circle, SubscriptionOrigin.RCH_IMPORT, false);
                }
                else {
                    Boolean isSameSubscriber = true;
                    for (Subscriber subscriber : subscribersByMsisdn) {
                        if (subscriber.getId().equals(subscriberByRchId.getId())) {
                            Subscription subscription = subscriptionService.getActiveSubscription(subscriberByRchId, pack.getType());
                            if (subscriberByRchId.getMother() == null) {
                                subscriberByRchId.setMother(childUpdate.getMother());
                            }
                            Subscription latestDeactivatedSubscription = subscriptionService.getLatestDeactivatedSubscription(subscriberByRchId, pack.getType(), true);

                            if (subscription == null && latestDeactivatedSubscription != null) {
                                if (latestDeactivatedSubscription.getStatus().equals(SubscriptionStatus.COMPLETED) ||
                                        ((latestDeactivatedSubscription.getStatus().equals(SubscriptionStatus.DEACTIVATED) && !(latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.LOW_LISTENERSHIP) || latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.WEEKLY_CALLS_NOT_ANSWERED))))) {
                                    subscriberByRchId.setDateOfBirth(dob);
                                    subscriberByRchId.setModificationDate(DateTime.now());
                                    return null;
                                }
                            }
                            if ((subscriberByRchId.getDateOfBirth().getDayOfYear() != dob.getDayOfYear()) && subscription!=null){
                                subscriptionService.deleteCallRetry(subscription.getSubscriptionId());
                            }
                            subscriberByRchId.setDateOfBirth(dob);
                            subscriberByRchId.setModificationDate(DateTime.now());
                            finalSubscription = updateOrCreateSubscription(subscriberByRchId, subscription, dob, pack, language, circle, SubscriptionOrigin.RCH_IMPORT, false);
                        } else {
                            //A different subscriber found with same mobile number
                            isSameSubscriber = false;
                        }
                    }
                    if (!isSameSubscriber) {
                        if (subscriptionService.activeSubscriptionByMsisdnRch(subscribersByMsisdn, msisdn, SubscriptionPackType.CHILD, motherRchId, childUpdate.getRchId())) {
                            return childRejectionRch(convertMapToRchChild(record), false, RejectionReasons.MOBILE_NUMBER_ALREADY_SUBSCRIBED.toString(), action);
                        } else {
                            subscriberByRchId.setCallingNumber(msisdn);
                            if (subscriberByRchId.getMother() == null) {
                                subscriberByRchId.setMother(childUpdate.getMother());
                            }
                            Subscription subscription = subscriptionService.getActiveSubscription(subscriberByRchId, pack.getType());
                            Subscription latestDeactivatedSubscription = subscriptionService.getLatestDeactivatedSubscription(subscriberByRchId, pack.getType(), true);

                            if (subscription == null && latestDeactivatedSubscription != null) {
                                if (latestDeactivatedSubscription.getStatus().equals(SubscriptionStatus.COMPLETED) ||
                                        latestDeactivatedSubscription.getStatus().equals(SubscriptionStatus.DEACTIVATED) &&
                                                !(latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.LOW_LISTENERSHIP) || latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.WEEKLY_CALLS_NOT_ANSWERED) || latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.INVALID_NUMBER)) ){
                                    subscriberByRchId.setDateOfBirth(dob);
                                    subscriberByRchId.setModificationDate(DateTime.now());
                                    return null;
                                }
                                else if(latestDeactivatedSubscription.getStatus().equals(SubscriptionStatus.DEACTIVATED) && (latestDeactivatedSubscription.getDeactivationReason().equals(DeactivationReason.INVALID_NUMBER))) {
                                    subscription = latestDeactivatedSubscription;
                                }
                            }
                            subscriberByRchId.setDateOfBirth(dob);
                            subscriberByRchId.setModificationDate(DateTime.now());
                            finalSubscription = updateOrCreateSubscription(subscriberByRchId, subscription, dob, pack, language, circle, SubscriptionOrigin.RCH_IMPORT, false);
                        }
                    }
                }
            }
        } else { // no subscribers found with the provided RCH id

            if(childUpdate.getMother() != null && childUpdate.getMother().getId() != null &&  getSubscriberListByMother(childUpdate.getMother().getId()) != null){
                Subscriber motherSubscriberByRchId = getSubscriberListByMother(childUpdate.getMother().getId());
                Subscription subscription = subscriptionService.getActiveSubscription(motherSubscriberByRchId, pack.getType());
                if (subscription!=null)
                    // Mother is already having another active child registered
                    return childRejectionRch(convertMapToRchChild(record), false, RejectionReasons.ALREADY_SUBSCRIBED.toString(), action);
                motherSubscriberByRchId.setDateOfBirth(dob);
                motherSubscriberByRchId.setChild(childUpdate);
                motherSubscriberByRchId.setModificationDate(DateTime.now());
                motherSubscriberByRchId.setCallingNumber(msisdn);
                finalSubscription = updateOrCreateSubscription(motherSubscriberByRchId, subscription, dob, pack, language, circle, SubscriptionOrigin.RCH_IMPORT, false);
            }
            else {
                if (subscribersByMsisdn.isEmpty() && childUpdate.getMother() != null) { // no subscriber exists with provided msisdn
                    Subscriber subscriberByRchMotherId = getSubscriberByBeneficiary(childUpdate.getMother());
                    if (subscriberByRchMotherId == null) { // no subscriber exists with RCH mother id either
                        //create subscriber, beneficiary, subscription and return
                        Subscriber subscriber = new Subscriber(msisdn, language, circle);
                        subscriber.setDateOfBirth(dob);
                        subscriber.setMother(childUpdate.getMother());
                        subscriber.setChild(childUpdate);
                        create(subscriber);
                        finalSubscription = subscriptionService.createSubscription(subscriber, msisdn, language, pack, SubscriptionOrigin.RCH_IMPORT);
                    } else {
                        if (subscriberByRchMotherId.getChild() == null) {
                            //update subscriber with child
                            subscriberByRchMotherId.setChild(childUpdate);
                            subscriberByRchMotherId.setModificationDate(DateTime.now());
                            Subscription subscription = subscriptionService.getActiveSubscription(subscriberByRchMotherId, pack.getType());
                            finalSubscription = updateOrCreateSubscription(subscriberByRchMotherId, subscription, dob, pack, language, circle, SubscriptionOrigin.RCH_IMPORT, false);
                        } else {
                            return childRejectionRch(convertMapToRchChild(record), false, RejectionReasons.ALREADY_SUBSCRIBED.toString(), action);
                        }
                    }
                } else { //subscriber exists with provided msisdn
                    if (subscribersByMsisdn.size() == 1 && (childUpdate.getMother() != null) && (subscribersByMsisdn.get(0).getMother() != null)) {
                        //update subscriber with child
                        if (childUpdate.getMother().getRchId() != null && subscribersByMsisdn.get(0).getMother().getRchId() != null && childUpdate.getMother().getRchId().equals(subscribersByMsisdn.get(0).getMother().getRchId())) {
                            Subscriber subscriber = subscribersByMsisdn.get(0);
                            Subscription subscription = subscriptionService.getActiveSubscription(subscriber, pack.getType());
                            if (subscription != null && !((subscriber.getDateOfBirth().getDayOfYear() == dob.getDayOfYear()) && (subscriber.getDateOfBirth().getYear() == dob.getYear()))){
                                subscriptionService.deleteCallRetry(subscription.getSubscriptionId());
                            }
                            subscriber.setDateOfBirth(dob);
                            subscriber.setChild(childUpdate);
                            subscriber.setModificationDate(DateTime.now());
                            finalSubscription = updateOrCreateSubscription(subscriber, subscription, dob, pack, language, circle, SubscriptionOrigin.RCH_IMPORT, false);
                        } else {
                            if (subscriptionService.activeSubscriptionByMsisdnRch(subscribersByMsisdn, msisdn, SubscriptionPackType.CHILD, motherRchId, childUpdate.getRchId())) {
                                return childRejectionRch(convertMapToRchChild(record), false, RejectionReasons.MOBILE_NUMBER_ALREADY_SUBSCRIBED.toString(), action);
                            } else {
                                Subscriber subscriber = new Subscriber(msisdn, language, circle);
                                subscriber.setDateOfBirth(dob);
                                subscriber.setMother(childUpdate.getMother());
                                subscriber.setChild(childUpdate);
                                create(subscriber);
                                finalSubscription = subscriptionService.createSubscription(subscriber, msisdn, language, pack, SubscriptionOrigin.RCH_IMPORT);
                            }
                        }

                    } else if (subscribersByMsisdn.size() == 0 && childUpdate.getMother() == null) {
                        Subscriber subscriber = new Subscriber(msisdn, language, circle);
                        subscriber.setDateOfBirth(dob);
                        subscriber.setMother(childUpdate.getMother());
                        subscriber.setChild(childUpdate);
                        create(subscriber);
                        finalSubscription = subscriptionService.createSubscription(subscriber, msisdn, language, pack, SubscriptionOrigin.RCH_IMPORT);
                    } else {
                        if (subscriptionService.activeSubscriptionByMsisdnRch(subscribersByMsisdn, msisdn, SubscriptionPackType.CHILD, motherRchId, childUpdate.getRchId())) {
                            return childRejectionRch(convertMapToRchChild(record), false, RejectionReasons.MOBILE_NUMBER_ALREADY_SUBSCRIBED.toString(), action);
                        } else {
                            if (subscriptionService.activeSubscriptionByMsisdnRch(subscribersByMsisdn, msisdn, SubscriptionPackType.PREGNANCY, motherRchId, childUpdate.getRchId())) {
                                return childRejectionRch(convertMapToRchChild(record), false, RejectionReasons.MOBILE_NUMBER_ALREADY_SUBSCRIBED.toString(), action);
                            }
                            Subscriber subscriber = new Subscriber(msisdn, language, circle);
                            subscriber.setDateOfBirth(dob);
                            if (childUpdate.getMother() != null) {
                                subscriber.setMother(childUpdate.getMother());
                            }
                            subscriber.setChild(childUpdate);
                            create(subscriber);
                            finalSubscription = subscriptionService.createSubscription(subscriber, msisdn, language, pack, SubscriptionOrigin.RCH_IMPORT);
                        }
                    }
                }
            }
        }

        liveBirthChildDeathCheck(finalSubscription, record);
        return null;
    }

    public Subscription updateOrCreateSubscription(Subscriber subscriber, Subscription subscription, DateTime dateTime, SubscriptionPack pack, Language language, Circle circle, SubscriptionOrigin origin, Boolean greaterCaseNo) { // NO CHECKSTYLE Cyclomatic Complexity
        Subscription deactivatedSubscripion = subscriptionService.getLatestDeactivatedSubscription(subscriber, pack.getType(), false);
        DateTime startDate;
        DateTime currentDate = DateTime.now();
        long differenceInMillis ;
        long differenceInWeeks = 0;
        if(deactivatedSubscripion != null){
            startDate = deactivatedSubscripion.getStartDate();
            differenceInMillis = currentDate.getMillis() - startDate.getMillis();
            differenceInWeeks = differenceInMillis / (1000*60*60*24*7);
        }
        LOGGER.debug("Previous pack started " + differenceInWeeks + " weeks back.");
        if (subscription != null && ((SubscriptionStatus.ACTIVE == subscription.getStatus() || SubscriptionStatus.PENDING_ACTIVATION == subscription.getStatus() || SubscriptionStatus.HOLD == subscription.getStatus()))) {
            subscriptionService.updateStartDate(subscription, dateTime);
            return subscription;
        } else if (subscription != null && subscription.getDeactivationReason().equals(DeactivationReason.INVALID_NUMBER)) {
            return reactivateSubscription(subscriber, deactivatedSubscripion, dateTime);
        } else if (subscription == null && deactivatedSubscripion != null && pack.getType() == SubscriptionPackType.CHILD) {
            if (DeactivationReason.LOW_LISTENERSHIP == deactivatedSubscripion.getDeactivationReason() ||  DeactivationReason.WEEKLY_CALLS_NOT_ANSWERED == deactivatedSubscripion.getDeactivationReason()) {
                if(differenceInWeeks > 48){
                    return subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(), language, circle, pack, origin);
                }
                return reactivateSubscription(subscriber, deactivatedSubscripion, dateTime);
            } else {
                LOGGER.debug("Reactivation is not valid in this scenario.");
                return null;
            }
        } else if (subscription == null  && deactivatedSubscripion != null  && (DeactivationReason.LOW_LISTENERSHIP == deactivatedSubscripion.getDeactivationReason() ||  DeactivationReason.WEEKLY_CALLS_NOT_ANSWERED == deactivatedSubscripion.getDeactivationReason())) {
            if(greaterCaseNo){
                return subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(), language, circle, pack, origin);
            }
            if (!greaterCaseNo && differenceInWeeks < 60) {
                return reactivateSubscription(subscriber, deactivatedSubscripion, dateTime);
            } else {
                return deactivatedSubscripion;
            }
        } else if (subscription != null && !subscription.getDeactivationReason().equals(DeactivationReason.INVALID_NUMBER)){
            Set<Subscription> activeSubscriptions = subscriber.getActiveAndPendingSubscriptions();
            if (activeSubscriptions != null && !activeSubscriptions.isEmpty()) {
                for(Subscription sub : activeSubscriptions ){
                    sub.setModificationDate(DateTime.now());
                }
            }
            subscription.setModificationDate(DateTime.now());
            return subscription;
        } else {
            return subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(), language, circle, pack, origin);
        }
    }

    public Subscription reactivateSubscription(Subscriber subscriber, Subscription subscription, DateTime dateTime) {
        ReactivatedBeneficiaryAudit reactivatedBeneficiaryAudit = new ReactivatedBeneficiaryAudit();
        reactivatedBeneficiaryAudit.setDeactivationReason(subscription.getDeactivationReason());
        reactivatedBeneficiaryAudit.setSubscriptionPack(subscription.getSubscriptionPack());
        reactivatedBeneficiaryAudit.setExternalId(subscription.getSubscriptionId());
        reactivatedBeneficiaryAudit.setDeactivationDate(subscription.getEndDate());
        reactivatedBeneficiaryAudit.setOrigin(subscription.getOrigin());
        reactivatedBeneficiaryAudit.setServiceReactivationDate(DateTime.now());
        reactivatedBeneficiaryAuditDataService.create(reactivatedBeneficiaryAudit);
        if(SubscriptionServiceImpl.isCapacityAvailable.get()) subscription.setStatus(SubscriptionStatus.ACTIVE);
        else subscription.setStatus(SubscriptionStatus.HOLD);
        subscription.setDeactivationReason(null);
        subscriptionService.updateStartDate(subscription, dateTime);
        if (subscriber.getMother() != null) {
            subscriptionService.deleteBlockedMsisdn(subscriber.getMother().getId(), null, subscriber.getCallingNumber());
        } else {
            subscriptionService.deleteBlockedMsisdn(subscriber.getChild().getId(), null, subscriber.getCallingNumber());
        }
        return subscription;
    }

    public Subscription createSubscriber(Long msisdn, MctsBeneficiary beneficiary, DateTime dateTime, SubscriptionPack pack, Language language, Circle circle) {
        Subscriber subscriber = new Subscriber(msisdn, language , circle);
        if (pack.getType() == SubscriptionPackType.PREGNANCY) {
            MctsMother mother = (MctsMother) beneficiary;
            mother.setLastMenstrualPeriod(dateTime);
            subscriber.setLastMenstrualPeriod(dateTime);
            subscriber.setMother(mother);
        } else {
            MctsChild child = (MctsChild) beneficiary;
            child.setDateOfBirth(dateTime);
            subscriber.setDateOfBirth(dateTime);
            subscriber.setChild(child);
            subscriber.setMother(child.getMother());
        }
        create(subscriber);
        return subscriptionService.createSubscription(subscriber, msisdn, language, circle, pack, SubscriptionOrigin.MCTS_IMPORT);
    }

    public Subscription splitSubscribers(Subscriber subscriber, Long msisdn, MctsChild childUpdate, DateTime dob, SubscriptionPack pack, Language language, Circle circle) {
        Subscription childSubscription = subscriptionService.getIVRSubscription(subscriber.getAllSubscriptions(), SubscriptionPackType.CHILD);
        // If childSubscription is null We can go ahead and create a new record since no child is attached to this
        // else it's IVR mother and child.create a new subscriber and update the same in existing childSubscription
        if (childSubscription != null) {
            Subscriber newSubscriber = new Subscriber(msisdn, language);
            childUpdate.setDateOfBirth(dob);
            newSubscriber.setDateOfBirth(dob);
            newSubscriber.setChild(childUpdate);
            newSubscriber.setMother(childUpdate.getMother());
            newSubscriber = create(newSubscriber);
            childSubscription.setSubscriber(newSubscriber);
            if (childSubscription.getStatus() == SubscriptionStatus.ACTIVE) {
                subscriptionService.updateStartDate(childSubscription, dob);
                return subscriptionService.getActiveSubscription(newSubscriber, pack.getType());
            } else {
                return subscriptionService.createSubscription(newSubscriber, msisdn, language, circle, pack, SubscriptionOrigin.MCTS_IMPORT);
            }
        }
        return null;   //create a new record
    }

    public void deleteAllowed(Subscriber subscriber) {
        for (Subscription subscription : subscriber.getSubscriptions()) {
            subscriptionService.deletePreconditionCheck(subscription);
        }
    }

    @Override
    public void deactivateAllSubscriptionsForSubscriber(long callingNumber, DeactivationReason deactivationReason) {
        LOGGER.info("Receieved Release Number {} for Deactivation.", callingNumber);
        List<Subscriber> subscriberByMsisdns = this.getSubscriber(callingNumber);
        if (subscriberByMsisdns.isEmpty()) {
            LOGGER.info("Subscriber for msisdn {} is not found.", callingNumber);
            throw new IllegalArgumentException(String.format(KilkariConstants.SUBSCRIBER_NOT_FOUND, callingNumber));
        }
        LOGGER.info("Found Subscriber for msisdn {} .", callingNumber);
        int counter = 0;
        for (Subscriber subscriber : subscriberByMsisdns) {
            for (Subscription subscription : subscriber.getAllSubscriptions()) {
                if ((subscription.getStatus() == SubscriptionStatus.PENDING_ACTIVATION) || (subscription.getStatus() == SubscriptionStatus.ACTIVE) || (subscription.getStatus() == SubscriptionStatus.HOLD)) {
                    try {
                        LOGGER.info("Deactivating Subscription with Id {} for msisdn.", subscription.getSubscriptionId());
                        subscriptionService.deactivateSubscription(subscription, deactivationReason);
                        deactivationSubscriptionAuditRecordDataService.create(new DeactivationSubscriptionAuditRecord(subscription.getSubscriptionId(), subscriber.getId(), subscription.getOrigin(), callingNumber, subscription.getStatus(), AuditStatus.SUCCESS, ""));
                        counter++;
                    } catch (Exception e) {
                        String error = ExceptionUtils.getFullStackTrace(e);
                        String truncatedError;
                        LOGGER.error(String.format("Unexpected exception in deactivating subscription %s: %s", subscription.getSubscriptionId(), error));
                        if (error.length() > DeactivationSubscriptionAuditRecord.MAX_OUTCOME_LENGTH) {
                            truncatedError = error.substring(0, DeactivationSubscriptionAuditRecord.MAX_OUTCOME_LENGTH);
                        } else {
                            truncatedError = error;
                        }
                        deactivationSubscriptionAuditRecordDataService.create(new DeactivationSubscriptionAuditRecord(subscription.getSubscriptionId(), subscriber.getId(), subscription.getOrigin(), callingNumber, subscription.getStatus(), AuditStatus.FAILURE, truncatedError));
                        throw new IllegalStateException(e);
                    }
                }
            }
        }

        LOGGER.info("Deactivated {} Subscritions for msisdn {}.", counter, callingNumber);
        // Add callingNumber to WeeklyCallsNotAnsweredMsisdnRecord (tableName = "nms_blocked_msisdn")
        BlockedMsisdnRecord record = blockedMsisdnRecordDataService.findByNumber(callingNumber);
        //TODO: we can use createOrUpdate method of MotechDataService once the bug is fixed.
        if (record == null) {
            blockedMsisdnRecordDataService.create(new BlockedMsisdnRecord(callingNumber, deactivationReason));
            LOGGER.info("Added callingNumber {} to BlockedMsisdnRecord.", callingNumber);
        } else {
            record.setDeactivationReason(deactivationReason);
            blockedMsisdnRecordDataService.update(record);
            LOGGER.info("Updated existing BlockedMsisdnRecord for callingNumber {}", callingNumber);
        }
    }

    @Override
    public Subscriber getSubscriberListByMother(final long motherId) {
        LOGGER.debug("getSubscriberListByMother, motherId is {}" , motherId);
        SqlQueryExecution<List<Subscriber>> queryExecution = new SqlQueryExecution<List<Subscriber>>() {

            @Override
            public String getSqlQuery() {
                return KilkariConstants.SELECT_SUBSCRIBERS_BY_MOTHER_ID_OID;
            }

            @Override
            public List<Subscriber> execute(Query query) {
                query.setClass(Subscriber.class);
                ForwardQueryResult fqr = (ForwardQueryResult) query.execute(motherId);

                if (fqr.isEmpty()) {
                    return null;
                }
                    return (List<Subscriber>) fqr;
            }
        };

        List<Subscriber> subscriberList = subscriberDataService.executeSQLQuery(queryExecution);
        if(subscriberList==null || subscriberList.size()==0){
            return null;
        } else if(subscriberList.size()==1){
            return subscriberList.get(0);
        } else {
            LOGGER.debug("More than one subscriber returned for motherID : {} , found {} subscribers ", motherId , subscriberList.size()); ;

            //if we have multiple subscriber for same user valid subscriber will be decided on below scenario
            //1. If we don't have any active subscription for any subscriber we will consider older subscriber as valid subscriber
            //2. If we have one or more active subscriptions for multiple subscriber we will consider latest subscriber with active subscriptions.

            int activeSubscribers = 0;
            Subscriber subscriber = new Subscriber();
            for(Subscriber subscriber1 : subscriberList) {
                if (subscriber1.getActiveAndPendingSubscriptions() != null && !subscriber1.getActiveAndPendingSubscriptions().isEmpty()) {
                    subscriber = subscriber1;
                    activeSubscribers++;
                }
            }
            if(activeSubscribers==0) return subscriberList.get(0);
            else return subscriber;
        }
    }

}

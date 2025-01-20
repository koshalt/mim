package org.motechproject.nms.kilkari.service;

import org.joda.time.DateTime;
import org.motechproject.event.MotechEvent;
import org.motechproject.mds.annotations.InstanceLifecycleListener;
import org.motechproject.mds.annotations.InstanceLifecycleListenerType;
import org.motechproject.nms.kilkari.domain.DeactivationReason;
import org.motechproject.nms.kilkari.domain.Subscriber;
import org.motechproject.nms.kilkari.domain.Subscription;
import org.motechproject.nms.kilkari.domain.SubscriptionOrigin;
import org.motechproject.nms.kilkari.domain.SubscriptionPack;
import org.motechproject.nms.kilkari.domain.SubscriptionPackType;
import org.motechproject.nms.props.domain.DayOfTheWeek;
import org.motechproject.nms.region.domain.Circle;
import org.motechproject.nms.region.domain.Language;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Service interface for managing Kilkari subscriptions
 */
public interface SubscriptionService {

    /**
     * Used by MCTS import and overloaded
     * Create a new Kilkari subscription. Upon subscription creation, the start date for the subscription will be set
     * based on the subscriber's reference date (DOB or LMP) if it exists. No subscription will be created if the
     * reference date implies that the subscription would already have been completed, or if the beneficiary already
     * has a subscription to the specific pack.
     * @param subscriber Subscriber record of the beneficiary
     * @param callingNumber MSISDN of the beneficiary
     * @param language Language of the beneficiary
     * @param subscriptionPack The subscription pack (e.g. Pregnancy, Child) for which to subscribe this beneficiary
     * @param mode How the subscription originated -- via IVR or MCTS import
     * @return The created subscription, or null if no subscription was created
     */
    Subscription createSubscription(Subscriber subscriber, long callingNumber, Language language,
                                    SubscriptionPack subscriptionPack, SubscriptionOrigin mode);

    /**
     * Create a new Kilkari subscription. Upon subscription creation, the start date for the subscription will be set
     * based on the subscriber's reference date (DOB or LMP) if it exists. No subscription will be created if the
     * reference date implies that the subscription would already have been completed, or if the beneficiary already
     * has a subscription to the specific pack.
     * @param subscriber Subscriber record of the beneficiary
     * @param callingNumber MSISDN of the beneficiary
     * @param language Language of the beneficiary
     * @param circle Circle of the beneficiary
     * @param subscriptionPack The subscription pack (e.g. Pregnancy, Child) for which to subscribe this beneficiary
     * @param mode How the subscription originated -- via IVR or MCTS import
     * @return The created subscription, or null if no subscription was created
     */

    Subscription createSubscription(Subscriber subscriber, long callingNumber, Language language, Circle circle,
                                    SubscriptionPack subscriptionPack, SubscriptionOrigin mode);

    /**
     * Get the subscription for the specified subscription ID
     * @param subscriptionId ID of the subscription to return, must be a String representation of a UUID
     * @return Subscription with the specified ID, or null if none found
     */
    Subscription getSubscription(String subscriptionId);

    /**
     * Update the specified subscription, based on the new reference date (LMP or DOB) supplied. Called by
     * SubscriberService.update if the subscriber's LMP or DOB changes -- as a result, the subscription's start date
     * and/or status may change.
     * @param subscription The subscription to update
     * @param newReferenceDate The new reference date (LMP or DOB) from which to base the subscription start date
     */
    void updateStartDate(Subscription subscription, DateTime newReferenceDate);

    /**
     * Activate the specified subscription
     * @param subscription The subscription to activate
     */
    void activateSubscription(Subscription subscription);

    /**
     * Activate the all pending subscriptions up to (but not including) the given date/time
     * @param upToDateTime The date/time up to which all pending subscriptions will be activated
     */
    Boolean activatePendingSubscriptionsUpTo(final DateTime upToDateTime, long maxActiveSubscriptions);

    /**
     * Based on the maxAllowed active subscriptions in the system, toggle mcts creation to true or false
     * @param maxActiveSubscriptions max active subscriptions to use for toggle evaluation
     */
    void toggleMctsSubscriptionCreation(long maxActiveSubscriptions);

    /**
     * Activate Mcts subscriptions that are on hold, if there are open slots for activation
     * @param maxActiveSubscriptions to calculate open slots left
     * @return number of subscriptions activated
     */
    long activateHoldSubscriptions(long maxActiveSubscriptions);

    /**
     * Deactivate the specified subscription
     * @param subscription The subscription to deactivate
     * @param reason The reason for deactivation
     */
    void deactivateSubscription(Subscription subscription, DeactivationReason reason);

    void deleteCallRetry(String subscriptionId);

    void updateCallRetry(String subscriptionId,Long msisdn);

    void deleteBlockedMsisdn(Long motherId, Long oldCallingNumber, Long newCallingNumber);

    /**
     * Get the subscription pack with the specified type
     * @param type The type of the subscription pack to retrieve
     * @return Subscription pack with the specified type, or null if none exists
     */
    SubscriptionPack getSubscriptionPack(SubscriptionPackType type);

    /**
     * Get the subscription pack with the specified name
     * @param name The name of the subscription pack to retrieve
     * @return Subscription pack with the specified name, or null if none exists
     */
    SubscriptionPack getSubscriptionPack(String name);

    /**
     * Returns active or pending subscription for the specified pack type if the subscriber has one
     * @param subscriber The subscriber
     * @param type The type of subscription pack
     * @return The subscription if an active one exists, null otherwise
     */
    Subscription getActiveSubscription(Subscriber subscriber, SubscriptionPackType type);

    Subscription getLatestDeactivatedSubscription(Subscriber subscriber, SubscriptionPackType type, boolean includeCompleted);

    List<Subscription> getActiveSubscriptionBySubscriber(Subscriber subscriber);

    //List<SubscriptionPack> getSubscriptionPacks();

    /**
     * Create the specified subscription in the database. To be used only by test code.
     * @param subscription The subscription to create
     * @return The created subscription
     */
    Subscription create(Subscription subscription);

    /**
     * Get the list of subscriptions due for a message on the specified day. Used by the Kilkari outbound dialer to
     * create the list of message recipients for a given day.
     * @param dayOfTheWeek The day of the week for which to find subscriptions
     * @param offset The row number at which to start returning results
     * @param rowCount The maximum number of rows to return
     * @return The list of subscriptions due for a message
     */
    List<Subscription> findActiveSubscriptionsForDay(DayOfTheWeek dayOfTheWeek, long offset, int rowCount);

    List<String> findJhSubscriptionIds();

    List<Subscription> findActiveSubscriptionsForDayWP(DayOfTheWeek dayOfTheWeek, long offset, int rowCount , final Date date);

    List<Subscription> findWelcomeActiveSubscriptionsForDayWP(final Date date, final DayOfTheWeek dow, final long offset,
                                                                     final int rowCount);

    List<Subscription> findDeactivatedSubscriptionsForDayWP(final Date date, final long offset,
                                                              final int rowCount , final String deactivationReasons);

    /**
     * Get the list of pending subscriptions that starts after the specified date.
     * @param startDate The start date from which to find subscriptins
     * @param page The page for which to return results
     * @param pageSize The number of results to return per page
     * @return The list of pending subscriptions that starts after the specified date
     */
    List<Subscription> findPendingSubscriptionsFromDate(DateTime startDate, int page, int pageSize);

    /**
     * MotechEvent handler that responds to scheduler events.  Purges subscription and subscriber records that
     * are in a closed state and have been for more than kilkari.weeks_to_keep_closed_subscriptions weeks
     */
    void purgeOldInvalidSubscriptions();

    /**
     * This is used to complete active subscriptions that have extended past their scheduled message delivery length
     */
    void completePastDueSubscriptions();

    /**
     * Lifecycle listener that verifies a subscription can only be deleted if it is deactivated or completed
     * and has been in that state for 6 weeks
     *
     * @param subscription
     */
    @InstanceLifecycleListener(InstanceLifecycleListenerType.PRE_DELETE)
    void deletePreconditionCheck(Subscription subscription);

    /**
     * Lifecycle listener that broadcasts a cache evict message for the message pack cache
     *
     * @param pack
     */
    @InstanceLifecycleListener({ InstanceLifecycleListenerType.POST_CREATE, InstanceLifecycleListenerType.PRE_DELETE,
            InstanceLifecycleListenerType.PRE_STORE })
    void broadcastCacheEvictMessage(SubscriptionPack pack);

    /**
     *
     * Message pack cache evict
     *
     */
    void cacheEvict(MotechEvent event);

    /**
     * Broadcast listener to enable/disable subscription capping
     * @param event motech event to toggle capping
     */
    void toggleActiveSubscriptionCapping(MotechEvent event);

    /**
     *
     * Retrieves all subscriptions in the database
     *
     */
    //List<Subscription> retrieveAll();

    /**
     * Helper method that determines the subscription based on its origin and packType
     * @param subscriptions Set of subscriptions for which the packType and origin is to be checked
     * @param packType Type of the pack (Pregnancy/Child)
     * @return subscription with given type and IVR origin
     */
    Subscription getIVRSubscription(Set<Subscription> subscriptions, SubscriptionPackType packType);

    Boolean activeSubscriptionByMsisdnMcts(Subscriber subscriber, Long msisdn, SubscriptionPackType packType, String motherBeneficiaryId, String childBeneficiaryId);

    Boolean activeSubscriptionByMsisdnRch(List<Subscriber> subscribers, Long msisdn, SubscriptionPackType packType, String motherRchId, String childRchId);

    /**
     * deletes the subscriber by its id -- the mds default query is not working because of  inner join bug
     * @param subscriberId
     */
    void deleteSubscriber(Long subscriberId);

}

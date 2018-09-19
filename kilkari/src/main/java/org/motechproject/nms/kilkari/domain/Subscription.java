package org.motechproject.nms.kilkari.domain;

import org.codehaus.jackson.annotate.JsonBackReference;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;
import org.motechproject.mds.annotations.InstanceLifecycleListeners;
import org.motechproject.mds.domain.MdsEntity;
import org.motechproject.nms.props.domain.DayOfTheWeek;
import org.motechproject.nms.tracking.annotation.TrackClass;
import org.motechproject.nms.tracking.annotation.TrackField;

import javax.jdo.annotations.Column;
import javax.jdo.annotations.Index;
import javax.jdo.annotations.Unique;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

@Entity(maxFetchDepth = -1, tableName = "nms_subscriptions")
@Index(name = "status_endDate_composit_idx", members = { "status", "endDate" })
@TrackClass
@InstanceLifecycleListeners
public class Subscription extends MdsEntity {

    private static final int DAYS_IN_WEEK = 7;
    private static final int WEEKDAY1 = 0;
    private static final int WEEKDAY5 = 4;
    private static final int WEEKDAY7 = 6;
    private static final String WEEK_ID_FORMAT = "w%d_%d";

    @Field
    @Unique
    @Column(allowsNull = "false", length = 36)
    @NotNull
    private String subscriptionId;

    @Field
    @Column(allowsNull = "false")
    @NotNull
    @JsonBackReference
    private Subscriber subscriber;

    @Field
    @Column(allowsNull = "false")
    @NotNull
    private SubscriptionPack subscriptionPack;

    @Field
    @Column(allowsNull = "false")
    @NotNull
    @TrackField
    private SubscriptionStatus status;

    @Field
    @Column(allowsNull = "false")
    @NotNull
    private SubscriptionOrigin origin;

    @Field
    @TrackField
    private DateTime startDate; // the date from which the weekly message should be calculated (DOB or LMP+90 days)

    @Field
    private DateTime endDate; // the date that the subscription is completed or deactivated

    @Field
    private DateTime activationDate; // the date on which the subscription is activated -- may be different from the
                                     // start date, e.g. if a child subscription is created 5 weeks after the DOB

    @Field
    private DayOfTheWeek firstMessageDayOfWeek;

    @Field
    private DayOfTheWeek secondMessageDayOfWeek;

    @Field
    private DeactivationReason deactivationReason;

    @Field
    private boolean needsWelcomeMessageViaObd;

    public Subscription(Subscriber subscriber, SubscriptionPack subscriptionPack, SubscriptionOrigin origin) {
        this.subscriptionId = UUID.randomUUID().toString();
        this.subscriber = subscriber;
        this.subscriptionPack = subscriptionPack;
        this.origin = origin;
        if (origin == SubscriptionOrigin.MCTS_IMPORT) {
            needsWelcomeMessageViaObd = true;
        }
    }

    public String getSubscriptionId() { return subscriptionId; }

    public Subscriber getSubscriber() {
        return subscriber;
    }

    public void setSubscriber(Subscriber subscriber) {
        this.subscriber = subscriber;
    }

    public SubscriptionPack getSubscriptionPack() {
        return subscriptionPack;
    }

    public void setSubscriptionPack(SubscriptionPack subscriptionPack) {
        this.subscriptionPack = subscriptionPack;
    }

    public SubscriptionStatus getStatus() { return status; }

    public void setStatus(SubscriptionStatus status) {
        if ((status == SubscriptionStatus.ACTIVE) && (this.status != SubscriptionStatus.ACTIVE)) {
            // subscription is being activated
            this.activationDate = DateTime.now();
        }

        this.status = status;

        if (this.status == SubscriptionStatus.DEACTIVATED || this.status == SubscriptionStatus.COMPLETED) {
            setEndDate(new DateTime());
        } else {
            setEndDate(null);
        }
    }

    public void setReactivationStatus() {
        this.status = SubscriptionStatus.ACTIVE;
    }

    public SubscriptionOrigin getOrigin() { return origin; }

    public void setOrigin(SubscriptionOrigin origin) { this.origin = origin; }

    public DateTime getStartDate() { return startDate; }

    public void setStartDate(DateTime startDate) {
        this.startDate = startDate.withTimeAtStartOfDay();
        this.firstMessageDayOfWeek = DayOfTheWeek.fromInt(startDate.getDayOfWeek());
        this.secondMessageDayOfWeek = DayOfTheWeek.fromDateTime(startDate.plusDays(4));
    }

    public DateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(DateTime endDate) {
        this.endDate = endDate;
    }

    public DateTime getActivationDate() {
        return endDate;
    }

    public void setActivationDate(DateTime activationDate) {
        this.activationDate = activationDate;
    }

    public DayOfTheWeek getFirstMessageDayOfWeek() {
        return firstMessageDayOfWeek;
    }

    public DayOfTheWeek getSecondMessageDayOfWeek() {
        return secondMessageDayOfWeek;
    }

    public DeactivationReason getDeactivationReason() { return deactivationReason; }

    public void setDeactivationReason(DeactivationReason deactivationReason) {
        this.deactivationReason = deactivationReason;
    }

    public boolean getNeedsWelcomeMessageViaObd() {
        return needsWelcomeMessageViaObd;
    }

    public void setNeedsWelcomeMessageViaObd(boolean needsWelcomeMessageViaObd) {
        this.needsWelcomeMessageViaObd = needsWelcomeMessageViaObd;
    }

    public boolean needsWelcomeMessageViaInbox() {
        int daysSinceStart = Days.daysBetween(startDate, activationDate).getDays();

        if (subscriptionPack.getMessagesPerWeek() == 1) {
            if ((daysSinceStart >= WEEKDAY1) && (daysSinceStart <= WEEKDAY7)) {
                return true;
            }
        } else { // messages per week == 2
            if ((daysSinceStart >= WEEKDAY1) && (daysSinceStart < WEEKDAY5)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Helper for TargetFile generation to pick next message for subscription
     * @param date The date on which the message will be played
     * @return SubscriptionPackMessage with the details of the message to play
     */
    public SubscriptionPackMessage nextScheduledMessage(DateTime date) { //NO CHECKSTYLE CyclomaticComplexity
        // Validate the subscription is active or completed
        if (status != SubscriptionStatus.ACTIVE && status != SubscriptionStatus.COMPLETED) {
            throw new IllegalStateException(String.format("Subscription with ID %s is not active", subscriptionId));
        }

        // get days into pack
        int daysIntoPack = Days.daysBetween(startDate, date).getDays();
        if (daysIntoPack < 0) {
            // there is no message due
            throw new IllegalStateException(
                    String.format("Subscription with ID %s is not due for any scheduled message. Start date in the future", subscriptionId));
        }

        // If completed, send last message in pack for 7 day window after completion
        if (status == SubscriptionStatus.COMPLETED) {
            if (daysIntoPack < (subscriptionPack.getWeeks() * DAYS_IN_WEEK + DAYS_IN_WEEK)) {
                return getMessageByWeekAndMessageId(subscriptionPack.getWeeks(), subscriptionPack.getMessagesPerWeek());
            } else {
                throw new IllegalStateException("Trying to get last subscription message more than a week after the subscription has completed.");
            }
        }

        // If check for welcome message condition
        if (needsWelcomeMessageViaObd) {
            // Subscriber may have subscribed via MCTS/IVR and may not know what Kilkari is; play welcome message this week
            return SubscriptionPackMessage.getWelcomeMessage();
        }

        // get next message and week index
        int currentWeek = daysIntoPack / DAYS_IN_WEEK + 1;
        if (subscriptionPack.getMessagesPerWeek() == 1) {
            return getMessageByWeekAndMessageId(currentWeek, 1);
        } else {
            // messages per week == 2
            if (daysIntoPack % DAYS_IN_WEEK < 4) {
                // use this week's first message
                return getMessageByWeekAndMessageId(currentWeek, 1);
            } else {
                // use this week's second message
                return getMessageByWeekAndMessageId(currentWeek, 2);
            }
        }
    }

    public SubscriptionPackMessage getMessageByWeekAndMessageId(int week, int day) {

        String weekId = String.format(WEEK_ID_FORMAT, week, day);
        // TODO: This is inefficient but don't want to deal with performance yet. Worst case, we will iterate
        // over 72x2 messages every time. Ideally, we would do a select using lambdaj and the likes
        for (SubscriptionPackMessage currentMessage : subscriptionPack.getMessages()) {
            if (currentMessage.getWeekId().equals(weekId)) {
                return currentMessage;
            }
        }

        throw new IllegalStateException(String.format(
                "Subscription with ID %s has no message in pack for given week(%d) and day(%d). Check deployment",
                subscriptionId, week, day));
    }

    /**
     * Helper method to be called by the CDR processor to determine whether to mark subscription status as COMPLETED
     * @param date The date for which subscription status is to be evaluated
     * @return true if the subscription should be marked completed, false otherwise
     */
    public boolean hasCompleted(DateTime date) {
        return hasCompletedForStartDate(startDate, date, subscriptionPack);
    }

    public static boolean hasCompletedForStartDate(DateTime startDate, DateTime today, SubscriptionPack pack) {
        int totalDaysInPack = pack.getWeeks() * DAYS_IN_WEEK;
        int daysSinceStartDate = Days.daysBetween(startDate, today).getDays();

        return totalDaysInPack < daysSinceStartDate;
    }


    /**
     * Helper method which determines if the given contentFileName corresponds to the last message of this
     * subscription's message pack
     *
     * @param contentFileName
     * @return true if contentFileName is the last message for this subscription's message pack
     */
    public boolean isLastPackMessage(String contentFileName) {
        List<SubscriptionPackMessage> messages = subscriptionPack.getMessages();
        return messages.get(messages.size() - 1).getMessageFileName().equals(contentFileName);
    }

    /**
     * Helper method that determines the subscription status based on its start date, subscription pack length
     * and current status (deactivated subscriptions remain deactivated)
     * @param subscription subscription for which the status is to be determined
     * @param today today's date
     * @return status determined for the provided subscription
     */
    public static SubscriptionStatus getStatus(Subscription subscription, DateTime today) {
        if (subscription.getStatus() == SubscriptionStatus.DEACTIVATED) {
            return SubscriptionStatus.DEACTIVATED;
        } else {
            int daysInPack = subscription.getSubscriptionPack().getWeeks() * DAYS_IN_WEEK;
            DateTime startDate = subscription.getStartDate();
            DateTime completionDate = startDate.plusDays(daysInPack);

            if (today.isBefore(startDate)) {
                return SubscriptionStatus.PENDING_ACTIVATION;
            } else if (today.isEqual(startDate) || (today.isAfter(startDate) && today.isBefore(completionDate))) {
                return SubscriptionStatus.ACTIVE;
            } else {
                return SubscriptionStatus.COMPLETED;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Subscription that = (Subscription) o;

        return !(subscriptionId != null ? !subscriptionId
                .equals(that.subscriptionId) : that.subscriptionId != null);

    }

    @Override
    public int hashCode() {
        return subscriptionId != null ? subscriptionId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Subscription{" +
                "subscriptionId='" + subscriptionId + '\'' +
                //todo: put back subscriptionPack when the getDetachedField bug is fixed...
                ", status=" + status +
                ", origin=" + origin +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                '}';
    }
}

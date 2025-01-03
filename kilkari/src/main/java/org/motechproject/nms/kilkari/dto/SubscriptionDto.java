package org.motechproject.nms.kilkari.dto;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.motechproject.nms.kilkari.domain.SubscriptionPackMessage;

import java.util.Date;

public class SubscriptionDto {
    private final long id;
    private final Date activationDate;
    private final String deactivationReason;
    private final Date endDate;
    private final String firstMessageDayOfWeek;
    private final boolean needsWelcomeMessageViaObd;
    private final String origin;
    private final String secondMessageDayOfWeek;
    private final Date startDate;
    private final String status;
    private final long subscriberIdOid;
    private final String subscriptionId;
    private final long subscriptionPackIdOid;
    private final Date creationDate;
    private final String creator;
    private final Date modificationDate;
    private final String modifiedBy;
    private final String owner;
    private final boolean needsWelcomeOptInForWp;
    private final String callingNumber;
    private final String languageCode;
    private final String circleName;
    private final long stateId;
    private final int weeks;
    private final String subscriptionPackName;
    private final int messagesPerWeek;

    private SubscriptionDto(Builder builder) {
        this.id = builder.id;
        this.activationDate = builder.activationDate;
        this.deactivationReason = builder.deactivationReason;
        this.endDate = builder.endDate;
        this.firstMessageDayOfWeek = builder.firstMessageDayOfWeek;
        this.needsWelcomeMessageViaObd = builder.needsWelcomeMessageViaObd;
        this.origin = builder.origin;
        this.secondMessageDayOfWeek = builder.secondMessageDayOfWeek;
        this.startDate = builder.startDate;
        this.status = builder.status;
        this.subscriberIdOid = builder.subscriberIdOid;
        this.subscriptionId = builder.subscriptionId;
        this.subscriptionPackIdOid = builder.subscriptionPackIdOid;
        this.creationDate = builder.creationDate;
        this.creator = builder.creator;
        this.modificationDate = builder.modificationDate;
        this.modifiedBy = builder.modifiedBy;
        this.owner = builder.owner;
        this.needsWelcomeOptInForWp = builder.needsWelcomeOptInForWp;
        this.callingNumber = builder.callingNumber;
        this.languageCode = builder.languageCode;
        this.circleName = builder.circleName;
        this.stateId = builder.stateId;
        this.weeks = builder.weeks;
        this.subscriptionPackName = builder.subscriptionPackName;
        this.messagesPerWeek = builder.messagesPerWeek;
    }

    public long getId() {
        return id;
    }

    public Date getActivationDate() {
        return activationDate;
    }

    public String getDeactivationReason() {
        return deactivationReason;
    }

    public Date getEndDate() {
        return endDate;
    }

    public String getFirstMessageDayOfWeek() {
        return firstMessageDayOfWeek;
    }

    public boolean isNeedsWelcomeMessageViaObd() {
        return needsWelcomeMessageViaObd;
    }

    public String getOrigin() {
        return origin;
    }

    public String getSecondMessageDayOfWeek() {
        return secondMessageDayOfWeek;
    }

    public Date getStartDate() {
        return startDate;
    }

    public String getStatus() {
        return status;
    }

    public long getSubscriberIdOid() {
        return subscriberIdOid;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public long getSubscriptionPackIdOid() {
        return subscriptionPackIdOid;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public String getCreator() {
        return creator;
    }

    public Date getModificationDate() {
        return modificationDate;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public String getOwner() {
        return owner;
    }

    public boolean isNeedsWelcomeOptInForWp() {
        return needsWelcomeOptInForWp;
    }

    public String getCallingNumber() {
        return callingNumber;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public String getCircleName() {
        return circleName;
    }

    public long getStateId() {
        return stateId;
    }

    public int getWeeks() {
        return weeks;
    }

    public String getSubscriptionPackName() {
        return subscriptionPackName;
    }

    public int getMessagesPerWeek() {
        return messagesPerWeek;
    }


    public SubscriptionPackMessage nextScheduledMessage(DateTime date) {

        int daysIntoPack = Days.daysBetween(new DateTime(startDate), date).getDays();
        if (daysIntoPack < 0) {
            throw new IllegalStateException(
                    String.format("Subscription with ID %s is not due for any scheduled message. Start date in the future", subscriptionId));
        }

        if (needsWelcomeMessageViaObd) {
            return getWelcomeMessage();
        }

        int currentWeek = daysIntoPack / 7 + 1;

            return getMessageByWeekAndMessageId(currentWeek, 1);

    }

    public SubscriptionPackMessage getMessageByWeekAndMessageId(int week, int day) {
        String weekId = String.format("w%d_%d", week, day);
        String messageFileName = String.format("w%d_%d.wav", week, day);

        return new SubscriptionPackMessage(weekId, messageFileName, 120); // Using default 120 seconds duration
    }

    private SubscriptionPackMessage getWelcomeMessage() {
        return new SubscriptionPackMessage("w1_1", "w1_1.wav", 120);
    }

    public static class Builder {
        private long id;
        private Date activationDate;
        private String deactivationReason;
        private Date endDate;
        private String firstMessageDayOfWeek;
        private boolean needsWelcomeMessageViaObd;
        private String origin;
        private String secondMessageDayOfWeek;
        private Date startDate;
        private String status;
        private long subscriberIdOid;
        private String subscriptionId;
        private long subscriptionPackIdOid;
        private Date creationDate;
        private String creator;
        private Date modificationDate;
        private String modifiedBy;
        private String owner;
        private boolean needsWelcomeOptInForWp;
        private String callingNumber;
        private String languageCode;
        private String circleName;
        private long stateId;
        private int weeks;
        private String subscriptionPackName;
        private int messagesPerWeek;

        public Builder setId(long id) { this.id = id; return this; }
        public Builder setActivationDate(Date activationDate) { this.activationDate = activationDate; return this; }
        public Builder setDeactivationReason(String deactivationReason) { this.deactivationReason = deactivationReason; return this; }
        public Builder setEndDate(Date endDate) { this.endDate = endDate; return this; }
        public Builder setFirstMessageDayOfWeek(String firstMessageDayOfWeek) { this.firstMessageDayOfWeek = firstMessageDayOfWeek; return this; }
        public Builder setNeedsWelcomeMessageViaObd(boolean needsWelcomeMessageViaObd) { this.needsWelcomeMessageViaObd = needsWelcomeMessageViaObd; return this; }
        public Builder setOrigin(String origin) { this.origin = origin; return this; }
        public Builder setSecondMessageDayOfWeek(String secondMessageDayOfWeek) { this.secondMessageDayOfWeek = secondMessageDayOfWeek; return this; }
        public Builder setStartDate(Date startDate) { this.startDate = startDate; return this; }
        public Builder setStatus(String status) { this.status = status; return this; }
        public Builder setSubscriberIdOid(long subscriberIdOid) { this.subscriberIdOid = subscriberIdOid; return this; }
        public Builder setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; return this; }
        public Builder setSubscriptionPackIdOid(long subscriptionPackIdOid) { this.subscriptionPackIdOid = subscriptionPackIdOid; return this; }
        public Builder setCreationDate(Date creationDate) { this.creationDate = creationDate; return this; }
        public Builder setCreator(String creator) { this.creator = creator; return this; }
        public Builder setModificationDate(Date modificationDate) { this.modificationDate = modificationDate; return this; }
        public Builder setModifiedBy(String modifiedBy) { this.modifiedBy = modifiedBy; return this; }
        public Builder setOwner(String owner) { this.owner = owner; return this; }
        public Builder setNeedsWelcomeOptInForWp(boolean needsWelcomeOptInForWp) { this.needsWelcomeOptInForWp = needsWelcomeOptInForWp; return this; }
        public Builder setCallingNumber(String callingNumber) { this.callingNumber = callingNumber; return this; }
        public Builder setLanguageCode(String languageCode) { this.languageCode = languageCode; return this; }
        public Builder setCircleName(String circleName) { this.circleName = circleName; return this; }
        public Builder setStateId(long stateId) { this.stateId = stateId; return this; }
        public Builder setWeeks(int weeks) { this.weeks = weeks; return this; }
        public Builder setSubscriptionPackName(String subscriptionPackName) { this.subscriptionPackName = subscriptionPackName; return this; }
        public Builder setMessagesPerWeek(int messagesPerWeek) { this.messagesPerWeek = messagesPerWeek; return this; }



        public SubscriptionDto build() {
            return new SubscriptionDto(this);
        }
    }
}

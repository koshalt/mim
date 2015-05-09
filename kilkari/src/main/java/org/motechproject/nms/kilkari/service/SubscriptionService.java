package org.motechproject.nms.kilkari.service;

import org.motechproject.nms.kilkari.domain.DeactivationReason;
import org.motechproject.nms.kilkari.domain.Subscription;
import org.motechproject.nms.kilkari.domain.SubscriptionMode;
import org.motechproject.nms.kilkari.domain.SubscriptionPack;
import org.motechproject.nms.region.language.domain.Language;
import org.joda.time.LocalDate;

/**
 *
 */
public interface SubscriptionService {

    void createSubscriptionPacks();

    void createSubscription(long callingNumber, Language language, SubscriptionPack subscriptionPack,
                            SubscriptionMode mode);

    Subscription getSubscription(String subscriptionId);

    void updateStartDate(Subscription subscription, LocalDate newReferenceDate);

    void deactivateSubscription(Subscription subscription, DeactivationReason reason);

    SubscriptionPack getSubscriptionPack(String name);

}

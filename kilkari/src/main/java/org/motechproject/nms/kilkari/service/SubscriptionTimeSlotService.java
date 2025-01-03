package org.motechproject.nms.kilkari.service;

import org.motechproject.nms.kilkari.domain.SubscriptionTimeSlot;

import java.util.List;

public interface SubscriptionTimeSlotService {

    List<SubscriptionTimeSlot> findTimeSlotsForSubscriptionsById(List<String> subscriptionId);

     List<SubscriptionTimeSlot> findAllTimeSlots();

}

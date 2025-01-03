package org.motechproject.nms.kilkari.service.impl;

import org.datanucleus.store.rdbms.query.ForwardQueryResult;
import org.motechproject.mds.query.SqlQueryExecution;
import org.motechproject.nms.kilkari.domain.SubscriptionTimeSlot;
import org.motechproject.nms.kilkari.repository.SubscriptionTimeSlotDataService;
import org.motechproject.nms.kilkari.service.SubscriptionTimeSlotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.jdo.Query;
import java.util.*;

@Service("subscriptionTimeSlotService")
@Component
public class SubscriptionTimeSlotServiceImpl implements SubscriptionTimeSlotService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionTimeSlotServiceImpl.class);
    private final SubscriptionTimeSlotDataService subscriptionTimeSlotDataService;

    @Autowired
    public SubscriptionTimeSlotServiceImpl(SubscriptionTimeSlotDataService subscriptionTimeSlotDataService) {
        this.subscriptionTimeSlotDataService = subscriptionTimeSlotDataService;
    }

    @Override
    public List<SubscriptionTimeSlot> findTimeSlotsForSubscriptionsById(final List<String> subscriptionIds) {

        if (subscriptionIds == null || subscriptionIds.isEmpty()) {
            LOGGER.warn("No subscription IDs provided, returning empty list.");
            return new ArrayList<>();
        }

        StringBuilder idsString = new StringBuilder();
        int i = 0;
        while (i < subscriptionIds.size()) {
            idsString.append("'").append(subscriptionIds.get(i)).append("'");
            if (i < subscriptionIds.size() - 1) {
                idsString.append(",");
            }
            i++;
        }

        String query = "SELECT ts.subscription_id, ts.subscriptionId, ts.timeStamp1, ts.timeStamp2, ts.timeStamp3 " +
                "FROM nms_subscriptions_time_slot AS ts " +
                "WHERE ts.subscriptionId IN (" + idsString + ")";


        SqlQueryExecution<List<Object[]>> queryExecution = new SqlQueryExecution<List<Object[]>>() {

            @Override
            public String getSqlQuery() {
                return query;
            }

            @Override
            public List<Object[]> execute(Query query) {
                ForwardQueryResult fqr = (ForwardQueryResult) query.execute();

                List<Object[]> results = new ArrayList<>();
                if (fqr != null && !fqr.isEmpty()) {
                    for (Object result : fqr) {
                        results.add((Object[]) result);
                    }
                }

                return results;
            }
        };

        List<Object[]> rawResults = subscriptionTimeSlotDataService.executeSQLQuery(queryExecution);

        List<SubscriptionTimeSlot> subscriptionTimeSlots = new ArrayList<>();
        for (Object[] row : rawResults) {

            SubscriptionTimeSlot timeSlot = new SubscriptionTimeSlot();

            timeSlot.setSubscription_id(((Number) row[0]).longValue());
            timeSlot.setSubscriptionId((String) row[1]);
            timeSlot.setTimeStamp1((Integer) row[2]);
            timeSlot.setTimeStamp2((Integer) row[3]);
            timeSlot.setTimeStamp3((Integer) row[4]);
            subscriptionTimeSlots.add(timeSlot);
        }
        if(subscriptionIds.isEmpty()) {
            LOGGER.warn("No subscriptionTimeSlots found for fresh calls subscriptionIds:");
        }

        return subscriptionTimeSlots;
    }



    @Override
    public List<SubscriptionTimeSlot> findAllTimeSlots() {
        String query = "SELECT ts.subscription_id, ts.subscriptionId, ts.timeStamp1, ts.timeStamp2, ts.timeStamp3 " +
                "FROM nms_subscriptions_time_slot ts " +
                "LEFT JOIN nms_subscriptions s ON ts.subscription_id = s.id;";

        LOGGER.debug("Executing SQL query: {}", query);

        SqlQueryExecution<List<Object[]>> queryExecution = new SqlQueryExecution<List<Object[]>>() {

            @Override
            public String getSqlQuery() {
                return query;
            }

            @Override
            public List<Object[]> execute(Query query) {
                ForwardQueryResult fqr = (ForwardQueryResult) query.execute();
                List<Object[]> results = new ArrayList<>();

                if (fqr != null && !fqr.isEmpty()) {
                    for (Object result : fqr) {
                        results.add((Object[]) result);
                    }
                }
                return results;
            }
        };

        List<Object[]> rawResults = subscriptionTimeSlotDataService.executeSQLQuery(queryExecution);
        List<SubscriptionTimeSlot> subscriptionTimeSlots = new ArrayList<>();

        for (Object[] row : rawResults) {
            SubscriptionTimeSlot timeSlot = new SubscriptionTimeSlot();
            timeSlot.setSubscription_id(((Number) row[0]).longValue());
            timeSlot.setSubscriptionId((String) row[1]);
            timeSlot.setTimeStamp1((Integer) row[2]);
            timeSlot.setTimeStamp2((Integer) row[3]);
            timeSlot.setTimeStamp3((Integer) row[4]);
            subscriptionTimeSlots.add(timeSlot);
        }

        if (subscriptionTimeSlots.isEmpty()) {
            LOGGER.warn("No time slots found.");
        }

        return subscriptionTimeSlots;
    }
}

package org.motechproject.nms.testing.it.kilkari;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.motechproject.commons.date.util.DateUtil;
import org.motechproject.event.MotechEvent;
import org.motechproject.mds.ex.JdoListenerInvocationException;
import org.motechproject.nms.kilkari.domain.*;
import org.motechproject.nms.kilkari.dto.SubscriptionDto;
import org.motechproject.nms.kilkari.repository.CallRetryDataService;
import org.motechproject.nms.kilkari.repository.InboxCallDataDataService;
import org.motechproject.nms.kilkari.repository.InboxCallDetailRecordDataService;
import org.motechproject.nms.kilkari.repository.SubscriberDataService;
import org.motechproject.nms.kilkari.repository.SubscriptionDataService;
import org.motechproject.nms.kilkari.repository.SubscriptionPackDataService;
import org.motechproject.nms.kilkari.repository.SubscriptionPackMessageDataService;
import org.motechproject.nms.kilkari.service.InboxService;
import org.motechproject.nms.kilkari.service.SubscriberService;
import org.motechproject.nms.kilkari.service.SubscriptionService;
import org.motechproject.nms.kilkari.utils.KilkariConstants;
import org.motechproject.nms.props.domain.DayOfTheWeek;
import org.motechproject.nms.region.repository.CircleDataService;
import org.motechproject.nms.region.repository.DistrictDataService;
import org.motechproject.nms.region.repository.LanguageDataService;
import org.motechproject.nms.region.repository.StateDataService;
import org.motechproject.nms.region.service.DistrictService;
import org.motechproject.nms.region.service.LanguageService;
import org.motechproject.nms.testing.it.utils.RegionHelper;
import org.motechproject.nms.testing.it.utils.SubscriptionHelper;
import org.motechproject.nms.testing.service.TestingService;
import org.motechproject.nms.tracking.domain.ChangeLog;
import org.motechproject.nms.tracking.repository.ChangeLogDataService;
import org.motechproject.testing.osgi.BasePaxIT;
import org.motechproject.testing.osgi.container.MotechNativeTestContainerFactory;
import org.ops4j.pax.exam.ExamFactory;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.inject.Inject;
import java.io.Reader;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Verify that SubscriptionService is present & functional.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
@ExamFactory(MotechNativeTestContainerFactory.class)
public class SubscriptionServiceBundleIT extends BasePaxIT {

    @Inject
    SubscriberService subscriberService;
    @Inject
    SubscriptionService subscriptionService;
    @Inject
    InboxService inboxService;
    @Inject
    SubscriberDataService subscriberDataService;
    @Inject
    SubscriptionPackDataService subscriptionPackDataService;
    @Inject
    SubscriptionPackMessageDataService subscriptionPackMessageDataService;
    @Inject
    SubscriptionDataService subscriptionDataService;
    @Inject
    LanguageDataService languageDataService;
    @Inject
    LanguageService languageService;
    @Inject
    InboxCallDetailRecordDataService inboxCallDetailRecordDataService;
    @Inject
    InboxCallDataDataService inboxCallDataDataService;
    @Inject
    StateDataService stateDataService;
    @Inject
    DistrictDataService districtDataService;
    @Inject
    DistrictService districtService;
    @Inject
    CircleDataService circleDataService;
    @Inject
    TestingService testingService;
    @Inject
    CallRetryDataService callRetryDataService;
    @Inject
    ChangeLogDataService changeLogDataService;
    @Inject
    PlatformTransactionManager transactionManager;

    private RegionHelper rh;
    private SubscriptionHelper sh;


    @Before
    public void setupTestData() {
        testingService.clearDatabase();

        rh = new RegionHelper(languageDataService, languageService, circleDataService, stateDataService,
                districtDataService, districtService);

        sh = new SubscriptionHelper(subscriberService,subscriptionService, subscriberDataService, subscriptionPackDataService,
                languageDataService, languageService, circleDataService, stateDataService, districtDataService,
                districtService);

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        Subscriber subscriber1 = subscriberDataService.create(new Subscriber(1000000000L));
        subscriber1.setDateOfBirth(DateTime.now().minusDays(14));
        subscriberDataService.create(subscriber1);
        subscriptionService.createSubscription(subscriber1, subscriber1.getCallingNumber(), rh.hindiLanguage(),
                sh.childPack(), SubscriptionOrigin.MCTS_IMPORT);

        Subscriber subscriber2 = subscriberDataService.create(new Subscriber(2000000000L));
        subscriber2.setDateOfBirth(DateTime.now().minusDays(14));
        subscriptionService.createSubscription(subscriber2, subscriber2.getCallingNumber(), rh.hindiLanguage(),
                sh.childPack(), SubscriptionOrigin.MCTS_IMPORT);

        subscriber2.setLastMenstrualPeriod(DateTime.now().minusDays(103));
        subscriberDataService.create(subscriber2);
        subscriptionService.createSubscription(subscriber2, subscriber2.getCallingNumber(), rh.hindiLanguage(),
                sh.pregnancyPack(), SubscriptionOrigin.MCTS_IMPORT);
        transactionManager.commit(status);
    }

    @Test
    public void testServicePresent() throws Exception {
        assertNotNull(subscriptionService);
    }

    
    @Test
    public void testPurgeOldClosedSubscriptionsNothingToPurge() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // s1 & s2 should remain untouched
        List<Subscriber> subscriber = subscriberService.getSubscriber(1000000000L);
        assertFalse(subscriber.isEmpty());
        Set<Subscription> subscriptions = subscriber.get(0).getSubscriptions();
        assertEquals(1, subscriptions.size());


        Subscriber s2 = new Subscriber(1000000001L, rh.hindiLanguage());
        s2.setDateOfBirth(DateTime.now().minusDays(14));
        s2.setLastMenstrualPeriod(DateTime.now().minusDays(103));
        subscriberService.create(s2);

        subscriptionService.createSubscription(s2, s2.getCallingNumber(), rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        subscriptionService.createSubscription(s2, s2.getCallingNumber(), rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        subscriber = subscriberService.getSubscriber(1000000001L);
        assertEquals(1, subscriber.size());
        assertEquals(2, subscriber.get(0).getSubscriptions().size());

        subscriptionService.purgeOldInvalidSubscriptions();

        subscriber = subscriberService.getSubscriber(1000000000L);
        assertNotNull(subscriber.get(0));
        subscriptions = subscriber.get(0).getSubscriptions();
        assertEquals(1, subscriptions.size());

        subscriber = subscriberService.getSubscriber(1000000001L);
        assertEquals(1, subscriber.size());
        assertEquals(2, subscriber.get(0).getSubscriptions().size());

        transactionManager.commit(status);
    }

    @Test
    public void testPurgeOldClosedSubscriptionsSubscribersDeleted() {

        List<Subscriber> subscriber;
        Set<Subscription> subscriptions;

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // s3 & s4 should be deleted
        Subscriber s3 = new Subscriber(1000000002L, rh.hindiLanguage());
        s3.setDateOfBirth(DateTime.now().minusDays(14));
        subscriberService.create(s3);

        subscriptionService.createSubscription(s3, s3.getCallingNumber(), rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        s3 = subscriberService.getSubscriber(1000000002L).get(0);
        Subscription subscription = s3.getSubscriptions().iterator().next();
        subscription.setStatus(SubscriptionStatus.COMPLETED);
        subscription.setEndDate(new DateTime().withDate(2011, 8, 1));
        subscriptionDataService.update(subscription);

        subscriber = subscriberService.getSubscriber(1000000002L);
        assertNotNull(subscriber);
        subscriptions = subscriber.get(0).getSubscriptions();
        assertEquals(1, subscriptions.size());


        Subscriber s4 = new Subscriber(1000000003L, rh.hindiLanguage());
        s4.setDateOfBirth(DateTime.now().minusDays(14));
        s4.setLastMenstrualPeriod(DateTime.now().minusDays(103));
        subscriberService.create(s4);

        subscriptionService.createSubscription(s4, s4.getCallingNumber(), rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        subscriptionService.createSubscription(s4, s4.getCallingNumber(), rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        subscriber = subscriberService.getSubscriber(1000000003L);
        Iterator<Subscription> subscriptionIterator = subscriber.get(0).getSubscriptions().iterator();
        subscription = subscriptionIterator.next();
        subscription.setStatus(SubscriptionStatus.COMPLETED);
        subscription.setEndDate(new DateTime().withDate(2011, 8, 1));
        subscriptionDataService.update(subscription);
        subscription = subscriptionIterator.next();
        subscription.setStatus(SubscriptionStatus.DEACTIVATED);
        subscription.setEndDate(new DateTime().withDate(2011, 8, 1));
        subscriptionDataService.update(subscription);

        subscriber = subscriberService.getSubscriber(1000000003L);
        assertNotNull(subscriber);
        subscriptions = subscriber.get(0).getSubscriptions();
        assertEquals(2, subscriptions.size());

        transactionManager.commit(status);

        status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        subscriptionService.purgeOldInvalidSubscriptions();

        transactionManager.commit(status);

        status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        subscriber = subscriberService.getSubscriber(1000000002L);
//        assertNull(subscriber);
        assertTrue(subscriber.isEmpty());

        subscriber = subscriberService.getSubscriber(1000000003L);
//        assertNull(subscriber);
        assertTrue(subscriber.isEmpty());

        transactionManager.commit(status);
    }

    @Test
    public void testPurgeOldClosedSubscriptionsRemoveSubscriptionLeaveSubscriber() {

        List<Subscriber> subscriber;
        Set<Subscription> subscriptions;
        Iterator<Subscription> subscriptionIterator;
        Subscription subscription;

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // s5 & s6 should remain but with one less subscription
        Subscriber s5 = new Subscriber(1000000004L, rh.hindiLanguage());
        s5.setDateOfBirth(DateTime.now().minusDays(14));
        s5.setLastMenstrualPeriod(DateTime.now().minusDays(103));
        subscriberService.create(s5);

        subscriptionService.createSubscription(s5, s5.getCallingNumber(), rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        subscriptionService.createSubscription(s5, s5.getCallingNumber(), rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        subscriber = subscriberService.getSubscriber(1000000004L);
        subscriptionIterator = subscriber.get(0).getSubscriptions().iterator();
        subscription = subscriptionIterator.next();
        subscription.setStatus(SubscriptionStatus.COMPLETED);
        subscription.setEndDate(new DateTime().withDate(2011, 8, 1));
        subscriptionDataService.update(subscription);

        subscriber = subscriberService.getSubscriber(1000000004L);
        assertNotNull(subscriber);
        subscriptions = subscriber.get(0).getSubscriptions();
        assertEquals(2, subscriptions.size());

        Subscriber s6 = new Subscriber(1000000005L, rh.hindiLanguage());
        s6.setDateOfBirth(DateTime.now().minusDays(14));
        s6.setLastMenstrualPeriod(DateTime.now().minusDays(103));
        subscriberService.create(s6);

        subscriptionService.createSubscription(s6, s6.getCallingNumber(), rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        subscriptionService.createSubscription(s6, s6.getCallingNumber(), rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        subscriber = subscriberService.getSubscriber(1000000005L);
        subscriptionIterator = subscriber.get(0).getSubscriptions().iterator();
        subscription = subscriptionIterator.next();
        subscription.setStatus(SubscriptionStatus.DEACTIVATED);
        subscription.setEndDate(new DateTime().withDate(2011, 8, 1));
        subscriptionDataService.update(subscription);

        subscriber = subscriberService.getSubscriber(1000000005L);
        assertNotNull(subscriber);
        subscriptions = subscriber.get(0).getSubscriptions();
        assertEquals(2, subscriptions.size());

        transactionManager.commit(status);

        status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        subscriptionService.purgeOldInvalidSubscriptions();
        transactionManager.commit(status);

        status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        subscriber = subscriberService.getSubscriber(1000000004L);
        assertNotNull(subscriber);
        subscriptions = subscriber.get(0).getSubscriptions();
        assertEquals(1, subscriptions.size());

        subscriber = subscriberService.getSubscriber(1000000005L);
        assertNotNull(subscriber);
        subscriptions = subscriber.get(0).getSubscriptions();
        assertEquals(1, subscriptions.size());

        transactionManager.commit(status);
    }

    @Test
    public void testServiceFunctional() throws Exception {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        SubscriptionPack pack1 = subscriptionPackDataService.byName("pack1");
        SubscriptionPack pack2 = subscriptionPackDataService.byName("pack2");

        Subscriber subscriber = subscriberDataService.create(new Subscriber(1000000001L));
        subscriber.setDateOfBirth(DateTime.now().minusDays(14));
        subscriber.setLastMenstrualPeriod(DateTime.now().minusDays(103));
        subscriptionService.createSubscription(subscriber, 1000000001L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        subscriptionService.createSubscription(subscriber, 1000000001L, rh.hindiLanguage(),
                sh.pregnancyPack(), SubscriptionOrigin.MCTS_IMPORT);

        subscriber = subscriberService.getSubscriber(1000000001L).get(0);
        Set<Subscription> subscriptions = subscriber.getSubscriptions();

        transactionManager.commit(status);

        Set<String> packs = new HashSet<>();
        for (Subscription subscription : subscriptions) {
            packs.add(subscription.getSubscriptionPack().getName());
        }

        assertEquals(new HashSet<>(Arrays.asList("childPack", "pregnancyPack")), packs);

        long id = inboxService.addInboxCallDetails(new InboxCallDetailRecord(
                1111111111L,
                "OP",
                "AA",
                "1234567890123451234512345",
                new DateTime(123L),
                new DateTime(456L),
                1,
                1,
                1,
                new HashSet<>(Arrays.asList(
                        new InboxCallData(
                                UUID.randomUUID().toString(),
                                "48WeeksPack",
                                "xx",
                                "foo.wav",
                                new DateTime(100L),
                                new DateTime(200L)
                        ),
                        new InboxCallData(
                                UUID.randomUUID().toString(),
                                "76WeeksPack",
                                "xx",
                                "bar.wav",
                                new DateTime(300L),
                                new DateTime(400L)
                        )
                ))
        ));

        InboxCallDetailRecord inboxCallDetailRecordFromDatabase = inboxCallDetailRecordDataService.findById(id);


        assertEquals(1111111111L, (long) inboxCallDetailRecordFromDatabase.getCallingNumber());
    }

    @Test
    public void testSubscriptionPackCreation() throws Exception {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber s2 = new Subscriber(1000000001L, rh.hindiLanguage());
        s2.setDateOfBirth(DateTime.now().minusDays(14));
        s2.setLastMenstrualPeriod(DateTime.now().minusDays(103));
        subscriberService.create(s2);

        subscriptionService.createSubscription(s2, s2.getCallingNumber(), rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        subscriptionService.createSubscription(s2, s2.getCallingNumber(), rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        SubscriptionPack fortyEightWeekPack = subscriptionPackDataService.byName("childPack");
        assertEquals(48, fortyEightWeekPack.getMessages().size());

        SubscriptionPack seventyTwoWeekPack = subscriptionPackDataService.byName("pregnancyPack");
        assertEquals(144, seventyTwoWeekPack.getMessages().size());

        transactionManager.commit(status);
    }

    /* Ignored the test as it is trying to create subscriber wiithout DOB
     */
    @Ignore
    @Test
    public void testCreateSubscriptionNoSubscriber() throws Exception {
        // Just verify the db is clean
        List<Subscriber> subscriber = subscriberService.getSubscriber(1111111111L);
        assertTrue(subscriber.isEmpty());

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        subscriptionService.createSubscription(null, 1111111111L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        subscriber = subscriberService.getSubscriber(1111111111L);
        assertNotNull(subscriber);
        assertEquals(rh.hindiLanguage(), subscriber.get(0).getLanguage());
        assertEquals(1, subscriber.get(0).getSubscriptions().size());

        Subscription subscription = subscriber.get(0).getSubscriptions().iterator().next();
        assertEquals(sh.childPack(), subscription.getSubscriptionPack());

        transactionManager.commit(status);

    }

    @Test
    public void testCreateSubscriptionExistingSubscriberDifferentLanguage() throws Exception {

        // Just verify the db is clean
        List<Subscriber> s = subscriberService.getSubscriber(1111111111L);
        assertTrue(s.isEmpty());

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberDataService.create(new Subscriber(1111111111L));
        subscriber.setDateOfBirth(DateTime.now().minusDays(14));
        subscriptionService.createSubscription(subscriber, 1111111111L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        subscriber = subscriberService.getSubscriber(1111111111L).get(0);
        assertNotNull(subscriber);
        assertEquals(rh.hindiLanguage(), subscriber.getLanguage());
        assertEquals(1, subscriber.getSubscriptions().size());

        Subscription subscription = subscriber.getSubscriptions().iterator().next();
        assertEquals(sh.childPack(), subscription.getSubscriptionPack());

        transactionManager.commit(status);
    }


    @Test
    public void testCreateSubscriptionExistingSubscriberWithoutLanguage() throws Exception {

        // Just verify the db is clean
        List<Subscriber> s = subscriberService.getSubscriber(1111111111L);
        assertTrue(s.isEmpty());

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        subscriberService.create(new Subscriber(1111111111L));
        s = subscriberService.getSubscriber(1111111111L);
        assertNotNull(s);
        assertNull(s.get(0).getLanguage());

        subscriptionService.createSubscription(s.get(0), 1111111111L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        s = subscriberService.getSubscriber(1111111111L);
        assertNotNull(s);
        assertEquals(rh.hindiLanguage(), s.get(0).getLanguage());

        transactionManager.commit(status);
    }

    @Test
    public void testCreateSubscriptionViaMcts() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setDateOfBirth(DateTime.now().minusDays(14));
        subscriberDataService.create(mctsSubscriber);

        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        assertEquals(1, mctsSubscriber.getActiveAndPendingSubscriptions().size());

        transactionManager.commit(status);
    }


    @Test
    public void testCreateDuplicateChildSubscriptionViaMcts() {

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setDateOfBirth(DateTime.now().minusDays(14));
        mctsSubscriber.setChild(new MctsChild("123456789"));
        subscriberDataService.create(mctsSubscriber);

        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        assertEquals(1, mctsSubscriber.getActiveAndPendingSubscriptions().size());

        // attempt to create subscription to the same pack -- should fail
        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        assertEquals(1, mctsSubscriber.getActiveAndPendingSubscriptions().size());

        transactionManager.commit(status);
    }


    @Test
    public void testCreateDuplicatePregnancySubscriptionViaMcts() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now().minusDays(28));
        mctsSubscriber.setMother(new MctsMother("123456789"));
        subscriberDataService.create(mctsSubscriber);

        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        assertEquals(1, mctsSubscriber.getActiveAndPendingSubscriptions().size());

        // attempt to create subscription to the same pack -- should fail
        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        assertEquals(1, mctsSubscriber.getActiveAndPendingSubscriptions().size());

        transactionManager.commit(status);
    }


    @Test
    public void testCreateSecondPregnancySubscriptionAfterDeactivationViaMcts() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now().minusDays(28));
        subscriberDataService.create(mctsSubscriber);

        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        Subscription pregnancySubscription = mctsSubscriber.getActiveAndPendingSubscriptions().iterator().next();
        pregnancySubscription.setStatus(SubscriptionStatus.DEACTIVATED);
        subscriptionDataService.update(pregnancySubscription);

        // attempt to create subscription to the same pack -- should succeed
        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        assertEquals(1, mctsSubscriber.getActiveAndPendingSubscriptions().size());

        transactionManager.commit(status);
    }


    @Test
    public void testCreateSubscriptionsToDifferentPacksViaMcts() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setDateOfBirth(DateTime.now().minusDays(14));
        subscriberDataService.create(mctsSubscriber);

        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);

        // due to subscription rules detailed in #157, we need to clear out the DOB and set an LMP in order to
        // create a second subscription for this MCTS subscriber
        mctsSubscriber.setDateOfBirth(null);
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now().minusDays(100));
        subscriberDataService.update(mctsSubscriber);

        // attempt to create subscription to a different pack
        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        assertEquals(2, mctsSubscriber.getActiveAndPendingSubscriptions().size());

        transactionManager.commit(status);
    }

    @Test
    public void testChangeDOB() {
        DateTime now = DateTime.now();

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setDateOfBirth(now.minusDays(14));
        subscriberDataService.create(mctsSubscriber);

        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);

        Subscription subscription = mctsSubscriber.getSubscriptions().iterator().next();

        assertEquals(now.minusDays(14).withTimeAtStartOfDay(), subscription.getStartDate());
        assert(subscription.getStatus() == SubscriptionStatus.ACTIVE);

        mctsSubscriber.setDateOfBirth(now.minusDays(100));
        subscriberService.updateStartDate(mctsSubscriber);

        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        subscription = mctsSubscriber.getSubscriptions().iterator().next();

        assertEquals(now.minusDays(100).withTimeAtStartOfDay(), subscription.getStartDate());
        assert(subscription.getStatus() == SubscriptionStatus.ACTIVE);

        transactionManager.commit(status);
    }


    @Test
    public void testChangeLMP() {
        DateTime now = DateTime.now();

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setLastMenstrualPeriod(now.minusDays(180));

        subscriberDataService.create(mctsSubscriber);

        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);

        Subscription subscription = mctsSubscriber.getSubscriptions().iterator().next();

        assertEquals(now.minusDays(90).withTimeAtStartOfDay(), subscription.getStartDate());
        assert(subscription.getStatus() == SubscriptionStatus.ACTIVE);

        mctsSubscriber.setLastMenstrualPeriod(now.minusDays(270));
        subscriberService.updateStartDate(mctsSubscriber);

        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        subscription = mctsSubscriber.getSubscriptions().iterator().next();

        assertEquals(now.minusDays(180).withTimeAtStartOfDay(), subscription.getStartDate());
        assert(subscription.getStatus() == SubscriptionStatus.ACTIVE);

        mctsSubscriber.setLastMenstrualPeriod(now.minusDays(1000));
        subscriberService.updateStartDate(mctsSubscriber);

        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        subscription = mctsSubscriber.getSubscriptions().iterator().next();

        assertEquals(now.minusDays(910).withTimeAtStartOfDay(), subscription.getStartDate());
        assert(subscription.getStatus() == SubscriptionStatus.COMPLETED);

        transactionManager.commit(status);
    }


    @Test
    public void testGetNextMessageForSubscription() {
        DateTime now = DateTime.now();

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setLastMenstrualPeriod(now.minusDays(90)); //so the startDate should be today
        subscriberDataService.create(mctsSubscriber);
        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);

        Subscription subscription = mctsSubscriber.getSubscriptions().iterator().next();

        // initially, the welcome message should be played (welcome message and week 1 message are the same)
        SubscriptionPackMessage message = subscription.nextScheduledMessage(now);
        assertEquals("w1_1", message.getWeekId());

        subscription.setNeedsWelcomeMessageViaObd(false);
        subscriptionDataService.update(subscription);

        message = subscription.nextScheduledMessage(now.plusDays(7)); //one week
        assertEquals("w2_1", message.getWeekId());

        message = subscription.nextScheduledMessage(now.plusDays(74));
        assertEquals("w11_2", message.getWeekId());

        transactionManager.commit(status);
    }


    @Test
    public void testGetNextMessageForCompletedSubscription() {
        DateTime now = DateTime.now();

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setLastMenstrualPeriod(now.minusDays(90));
        subscriberDataService.create(mctsSubscriber);
        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh
                .pregnancyPack(), SubscriptionOrigin.MCTS_IMPORT);
        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);

        Subscription subscription = mctsSubscriber.getSubscriptions().iterator().next();
        subscription.setNeedsWelcomeMessageViaObd(false);
        subscriptionDataService.update(subscription);

        transactionManager.commit(status);

        exception.expect(IllegalStateException.class);
        SubscriptionPackMessage message = subscription.nextScheduledMessage(now.plusDays(1000));
    }


    @Test
    public void testActiveSubscriptionsForDay() {
        DateTime startDate = DateTime.now().minusDays((int) (Math.random() * 100));
        DayOfTheWeek startDay = DayOfTheWeek.fromInt(startDate.getDayOfWeek());

        Subscriber subscriber = subscriberDataService.create(new Subscriber(1111111111L));
        subscriber.setDateOfBirth(DateTime.now().minusDays(14));
        Subscription subscription = new Subscription(subscriber, sh.childPack(), SubscriptionOrigin.MCTS_IMPORT);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(startDate);
        subscriptionDataService.create(subscription);

        List<SubscriptionDto> subscriptions = subscriptionService.findActiveSubscriptionsForDay(startDay, 0, 10);
        assertTrue(subscriptions.size() > 0);
        for (SubscriptionDto s : subscriptions) {
            if (Long.parseLong(s.getCallingNumber()) == 1111111111L) {
                return;
            }
        }
        throw new IllegalStateException("Couldn't find our subscription by its start day!");
    }


    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testDeleteOpenSubscription() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setDateOfBirth(DateTime.now().minusDays(14));
        subscriberDataService.create(mctsSubscriber);

        Subscription subscription = subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(),
                sh.childPack(), SubscriptionOrigin.MCTS_IMPORT);

        transactionManager.commit(status);

        exception.expect(JdoListenerInvocationException.class);
        subscriptionDataService.delete(subscription);
    }


    @Test
    public void testDeleteRecentDeactivateSubscription() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setDateOfBirth(DateTime.now().minusDays(14));
        subscriberDataService.create(mctsSubscriber);

        Subscription subscription = subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(),
                sh.childPack(), SubscriptionOrigin.MCTS_IMPORT);
        subscription.setStatus(SubscriptionStatus.DEACTIVATED);
        subscriptionDataService.update(subscription);

        transactionManager.commit(status);

        exception.expect(JdoListenerInvocationException.class);
        subscriptionDataService.delete(subscription);
    }

    @Test
    public void testDeleteRecentCompletedSubscription() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setDateOfBirth(DateTime.now().minusDays(14));
        subscriberDataService.create(mctsSubscriber);

        Subscription subscription = subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(),
                sh.childPack(), SubscriptionOrigin.MCTS_IMPORT);
        subscription.setStatus(SubscriptionStatus.COMPLETED);
        subscriptionDataService.update(subscription);

        transactionManager.commit(status);

        exception.expect(JdoListenerInvocationException.class);
        subscriptionDataService.delete(subscription);
    }


    @Test
    public void testDeleteOldDeactivatedSubscription() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberService.getSubscriber(2000000000L).get(0);
        assertNotNull(subscriber);

        assertEquals(2, subscriber.getSubscriptions().size());

        Subscription subscription = subscriber.getSubscriptions().iterator().next();
        subscription.setStatus(SubscriptionStatus.DEACTIVATED);
        subscription.setEndDate(new DateTime().withDate(2011, 8, 1));
        subscriptionDataService.update(subscription);

        subscriptionDataService.delete(subscription);

        subscriber = subscriberService.getSubscriber(2000000000L).get(0);
        assertEquals(1, subscriber.getSubscriptions().size());

        transactionManager.commit(status);
    }

    @Test
    public void testDeleteOldCompletedSubscription() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberService.getSubscriber(2000000000L).get(0);
        assertNotNull(subscriber);

        assertEquals(2, subscriber.getSubscriptions().size());

        Subscription subscription = subscriber.getSubscriptions().iterator().next();
        subscription.setStatus(SubscriptionStatus.COMPLETED);
        subscription.setEndDate(new DateTime().withDate(2011, 8, 1));
        subscriptionDataService.update(subscription);

        subscriptionDataService.delete(subscription);

        subscriber = subscriberService.getSubscriber(2000000000L).get(0);
        assertEquals(1, subscriber.getSubscriptions().size());

        transactionManager.commit(status);
    }

    /**
     * Verify that we have no NPE for trying to deactivate null subscriptions
     * See https://github.com/motech-implementations/mim/pull/675
     */
    @Test
    public void testDeactivateNullSubscription() {
        subscriptionService.deactivateSubscription(null, DeactivationReason.MCTS_UPDATE);
    }

    /*
     * To verify that number of Messages per week shouldn't get configured if invalid value is provided.
     */
    @Test(expected=IllegalArgumentException.class)
    public void verifyFT180() {
            sh.childPack().setMessagesPerWeek(3);
    }

    /*
     * To verify LMP is changed successfully and new subscription created
     * when subscription already exist for pregnancyPack having status as "Completed".
     * Now Added one more assert for updated LMP to cover NMS_FT_134.
     * NMS_FT_134 description ::
     * To check pregnancyPack subscription is successfully created when subscription 
     * already exist for pregnancyPack with status as "Completed".
     */
    @Test
    public void verifyFT156() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now().minusDays(28));
        subscriberDataService.create(mctsSubscriber);

        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        
        // mark subscription as complete
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now().minusDays(602));
        subscriberService.updateStartDate(mctsSubscriber);

        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        DateTime oldLMP = mctsSubscriber.getLastMenstrualPeriod();
        assertEquals(1, mctsSubscriber.getSubscriptions().size()); // Completed subscription should be there
        assertEquals(0, mctsSubscriber.getActiveAndPendingSubscriptions().size()); // No active subscription
        assertEquals(SubscriptionStatus.COMPLETED, mctsSubscriber.getSubscriptions().iterator().next().getStatus());
        
        DateTime newLMP = DateTime.now().minusDays(100);
        mctsSubscriber.setLastMenstrualPeriod(newLMP);
        subscriberService.updateStartDate(mctsSubscriber);
        
        // attempt to create subscription to the same pack -- should succeed
        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        assertFalse(mctsSubscriber.getLastMenstrualPeriod().equals(oldLMP));
        assertEquals(mctsSubscriber.getLastMenstrualPeriod(), newLMP);
        assertEquals(2, mctsSubscriber.getSubscriptions().size());
        assertEquals(1, mctsSubscriber.getActiveAndPendingSubscriptions().size()); // One active subscription

        transactionManager.commit(status);
    }


    /*
     * To verify DOB is changed successfully and new subscription created
     * when subscription already exist for childPack having status as "Deactivated".
     * Now Added one more assert for updated DOB to cover NMS_FT_132.
     * NMS_FT_132 description ::
     * To check subscription for childPack is successfully created when subscription  
     * already exist for  childPack in state "Deactivated"
     */
    @Test
    public void verifyFT159() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setDateOfBirth(DateTime.now());
        subscriberDataService.create(mctsSubscriber);

        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        Subscription childSubscription = mctsSubscriber.getActiveAndPendingSubscriptions().iterator().next();
        childSubscription.setStatus(SubscriptionStatus.DEACTIVATED);
        subscriptionDataService.update(childSubscription);
        
        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        DateTime oldDob = mctsSubscriber.getDateOfBirth();
        DateTime newDob = DateTime.now().minusDays(100);
        mctsSubscriber.setDateOfBirth(newDob);
        subscriberService.updateStartDate(mctsSubscriber);

        // attempt to create subscription to the same pack -- should succeed
        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        assertFalse(mctsSubscriber.getDateOfBirth().equals(oldDob));
        assertEquals(mctsSubscriber.getDateOfBirth(), newDob);
        assertEquals(2, mctsSubscriber.getSubscriptions().size());
        assertEquals(1, mctsSubscriber.getActiveAndPendingSubscriptions().size());

        transactionManager.commit(status);
    }


    /*
     * To verify DOB is changed successfully and new subscription created
     * when subscription already exist for childPack having status as "Completed".
     * Now Added one more assert for updated DOB to cover NMS_FT_133.
     * NMS_FT_133 description ::
     * To check childPack subscription is successfully created when subscription 
     * already exist for childPack with status as "Completed".
     */
    @Test
    public void verifyFT160() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        DateTime currentTime = DateTime.now();
        mctsSubscriber.setDateOfBirth(currentTime);
        subscriberDataService.create(mctsSubscriber);

        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        
        // mark subscription as complete
        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        mctsSubscriber.setDateOfBirth(currentTime.minusDays(340));
        subscriberService.updateStartDate(mctsSubscriber);
        
        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        DateTime oldDob = mctsSubscriber.getDateOfBirth(); 
        assertEquals(1, mctsSubscriber.getSubscriptions().size());
        assertEquals(0, mctsSubscriber.getActiveAndPendingSubscriptions().size()); // No active subscription

        DateTime newDob = currentTime.minusDays(100);
        mctsSubscriber.setDateOfBirth(newDob);
        subscriberService.updateStartDate(mctsSubscriber);
        // attempt to create subscription to the same pack -- should succeed
        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        assertFalse(mctsSubscriber.getDateOfBirth().equals(oldDob));
        assertEquals(mctsSubscriber.getDateOfBirth(), newDob);
        assertEquals(2, mctsSubscriber.getSubscriptions().size());

        transactionManager.commit(status);
    }
    
    /*
	 * To verify that MSISDN greater than 10 digit should be accepted during 
	 * MCTS upload. subscriber should be created with last 10 digits of MSISDN.
	 */
    @Test
    public void verifyFT182() {
    	
    	//attempt to create subscriber and subscription having calling number more than 10 digit
    	subscriptionService.createSubscription(null, 991111111122L, rh.hindiLanguage(),
				sh.pregnancyPack(), SubscriptionOrigin.MCTS_IMPORT);
    	
        List<Subscriber> subscriber1 = subscriberService.getSubscriber(991111111122L);
        assertTrue(subscriber1.isEmpty());

        Subscriber subscriber2 = subscriberService.getSubscriber(1111111122L).get(0);
        assertNotNull(subscriber2);
    }

    /*
     * To verify that user's subscription should create in pending state
     */
    @Test
    public void verifyFT153() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = new Subscriber(9999911222L);
        subscriber.setLastMenstrualPeriod(DateTime.now().minusDays(80));
        subscriberService.create(subscriber);
        
        subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(), rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        
        subscriber = subscriberService.getSubscriber(9999911222L).get(0);
        Subscription subscription = subscriber.getSubscriptions().iterator().next();
        assertEquals(1, subscriber.getSubscriptions().size());
        assertEquals(SubscriptionStatus.PENDING_ACTIVATION, subscription.getStatus());

        transactionManager.commit(status);
    }

    /**
     * Verifies that child subscriptions that are past due (active + <pack length) don't get completed automatically
     */
    @Test
    public void verifyChildPastDueCompletionNoChange() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // create subscriber and subscription
        Subscriber subscriber = new Subscriber(9999911222L);
        subscriber.setDateOfBirth(DateTime.now().minusDays(14));
        subscriberService.create(subscriber);
        Subscription sub = subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(),
                rh.hindiLanguage(), sh.childPack(), SubscriptionOrigin.MCTS_IMPORT);

        // get and update subscription with old start date within expected range (<48wks for child pack)
        Long subscriptionId = sub.getId();
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartDate(DateTime.now().minusDays(48 * 7 - 1));
        subscriptionDataService.update(sub);

        // fetch and assert after update
        Subscription fetch = subscriptionDataService.findById(subscriptionId);
        assertEquals(SubscriptionStatus.ACTIVE, fetch.getStatus());

        // run update script
        subscriptionService.completePastDueSubscriptions();
        Subscription fetchUpdate = subscriptionDataService.findById(subscriptionId);
        assertEquals(SubscriptionStatus.ACTIVE, fetchUpdate.getStatus());

        transactionManager.commit(status);
    }


    /**
     * Verifies that changes to the subscriptionStatus or startDate fields are tracked
     * Ignored due to AssertionError
     */
    @Test
    @Ignore
    public void verifyTrackSubscriptionFieldChanges() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // create subscriber and subscription
        Subscriber subscriber = new Subscriber(9999911222L);
        subscriber.setDateOfBirth(DateTime.now().minusDays(14));
        subscriberService.create(subscriber);
        Subscription sub = subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(),
                rh.hindiLanguage(), sh.childPack(), SubscriptionOrigin.MCTS_IMPORT);
        DateTime startDate1 = sub.getStartDate();

        // update subscription with old start date within expected range (<72wks for pregnancy pack)
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartDate(DateTime.now().minusDays(72 * 7 - 1));
        sub = subscriptionDataService.update(sub);
        DateTime startDate2 = sub.getStartDate();
        transactionManager.commit(status);


        String sd1 = startDate1.toString();
        String sd2 = startDate2.toString();

        List<ChangeLog> changes = changeLogDataService.findByEntityNameAndInstanceId(Subscription.class.getName(), sub.getId());

        assertEquals(2, changes.size());

        String change = changes.get(0).getChange();
        String expectedChange = String.format("startDate(null, %s),status(null, PENDING_ACTIVATION)", sd1);
        assertEquals(expectedChange, change);

        change = changes.get(1).getChange();
        expectedChange = String.format("startDate(%s, %s),status(PENDING_ACTIVATION, ACTIVE)", sd1, sd2);
        assertEquals(expectedChange, change);
    }


    /**
     * Verifies that subscribers with calls to retry and deactivated subscriptions do not get called
     */
    @Test
    public void verifyNoRetryWithDeactivatedSubscription() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // create subscriber and subscription
        Subscriber subscriber = new Subscriber(9999911222L);
        subscriber.setDateOfBirth(DateTime.now().minusDays(14));
        subscriberService.create(subscriber);
        Subscription sub = subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(), rh.hindiLanguage(),
                sh.childPack(), SubscriptionOrigin.MCTS_IMPORT);

        Long subscriptionId = sub.getId();
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartDate(DateTime.now().minusDays(20 * 7));
        subscriptionDataService.update(sub);

        callRetryDataService.create(new CallRetry(sub.getSubscriptionId(), subscriber.getCallingNumber(),
                CallStage.RETRY_1, "w1_1", "w1_1", rh.hindiLanguage().getCode(), "xx", SubscriptionOrigin.MCTS_IMPORT, null,
                null, false));

        // fetch and assert after update
        Subscription fetch = subscriptionDataService.findById(subscriptionId);
        assertEquals(SubscriptionStatus.ACTIVE, fetch.getStatus());

        CallRetry callRetry = callRetryDataService.findBySubscriptionId(sub.getSubscriptionId());
        assertNotNull(callRetry);

        subscriptionService.deactivateSubscription(sub, DeactivationReason.DEACTIVATED_BY_USER);

        callRetry = callRetryDataService.findBySubscriptionId(sub.getSubscriptionId());
        assertNull(callRetry);

        transactionManager.commit(status);
    }

    /**
     * Verify that the new subscription created is set to hold status when service is full
     */
    @Test
    public void verifyMctsSubscriptionCreationOnHold() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        subscriptionService.toggleMctsSubscriptionCreation(0);
        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setDateOfBirth(DateTime.now().minusDays(14));
        subscriberDataService.create(mctsSubscriber);

        Subscription hold = subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);
        assertEquals(0, mctsSubscriber.getActiveAndPendingSubscriptions().size());

        Subscription holdFetch = subscriptionService.getSubscription(hold.getSubscriptionId());
        assertEquals(SubscriptionStatus.HOLD, holdFetch.getStatus());

        subscriptionService.toggleMctsSubscriptionCreation(100); // set this back to active
        transactionManager.commit(status);
    }

    /**
     * verify that sub1 is created as hold when service is full and sub2 is created as pending when not full
     * this simulates testing over multiple days
     */
    @Test
    public void verifySubscriptionCreationFullNotFull() {

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // Creation when service is full
        subscriptionService.toggleMctsSubscriptionCreation(0);
        Subscriber mctsSubscriber1 = new Subscriber(9999911122L);
        mctsSubscriber1.setDateOfBirth(DateTime.now().minusDays(14));
        subscriberDataService.create(mctsSubscriber1);

        Subscription hold = subscriptionService.createSubscription(mctsSubscriber1, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        mctsSubscriber1 = subscriberService.getSubscriber(9999911122L).get(0);
        assertEquals(0, mctsSubscriber1.getActiveAndPendingSubscriptions().size());

        Subscription holdFetch = subscriptionService.getSubscription(hold.getSubscriptionId());
        assertEquals(SubscriptionStatus.HOLD, holdFetch.getStatus());

        subscriptionService.toggleMctsSubscriptionCreation(100); // set this back to active

        // creation when service is not full (current + x days)
        Subscriber mctsSubscriber2 = new Subscriber(9999911123L);
        mctsSubscriber2.setDateOfBirth(DateTime.now().minusDays(14));
        subscriberDataService.create(mctsSubscriber2);

        subscriptionService.createSubscription(mctsSubscriber2, 9999911123L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        mctsSubscriber2 = subscriberService.getSubscriber(9999911123L).get(0);
        assertEquals(1, mctsSubscriber2.getActiveAndPendingSubscriptions().size());

        transactionManager.commit(status);
    }

    /**
     * verify that hold activation is disabled when mcts activation is set to false
     */
    @Test
    public void verifyHoldActivationNoSlots() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        subscriptionService.toggleMctsSubscriptionCreation(0);

        assertEquals(0, subscriptionService.activateHoldSubscriptions(0));
        subscriptionService.toggleMctsSubscriptionCreation(100); // set this back to active
        transactionManager.commit(status);
    }

    /**
     * Verify that no subscriptions are activated when open slots are full with mcts activation set to true (by default)
     */
    @Test
    public void verifyHoldActivationFull() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        assertEquals(0, subscriptionService.activateHoldSubscriptions(0));

        transactionManager.commit(status);
    }

    @Test
    public void verifyHoldActivationSuccessful() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        subscriptionService.toggleMctsSubscriptionCreation(0);

        // sub1
        Subscriber mctsSubscriber1 = new Subscriber(9999911122L);
        mctsSubscriber1.setDateOfBirth(DateTime.now().minusDays(14));
        subscriberDataService.create(mctsSubscriber1);

        // sub2
        Subscriber mctsSubscriber2 = new Subscriber(9999911123L);
        mctsSubscriber2.setDateOfBirth(DateTime.now().minusDays(14));
        subscriberDataService.create(mctsSubscriber2);

        // creation subscriptions
        Subscription hold1 = subscriptionService.createSubscription(mctsSubscriber1, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        Subscription hold2 = subscriptionService.createSubscription(mctsSubscriber2, 9999911123L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        // verify that they are on hold
        assertEquals(SubscriptionStatus.HOLD, hold1.getStatus());
        assertEquals(SubscriptionStatus.HOLD, hold2.getStatus());

        subscriptionService.toggleMctsSubscriptionCreation(10000); // set activation to active
        subscriptionService.activateHoldSubscriptions(10000);

        transactionManager.commit(status);

        // verify that the subscriptions on hold are set to active after the active subscriptions limit is removed
        assertEquals(SubscriptionStatus.ACTIVE, hold1.getStatus());
        assertEquals(SubscriptionStatus.ACTIVE, hold2.getStatus());

    }

    @Test
    public void testLmpChangeFromActiveToCompleted() throws Exception {
        DateTime now = DateTime.now();

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9439986187L);
        mctsSubscriber.setLastMenstrualPeriod(now.minusDays(180));

        subscriberDataService.create(mctsSubscriber);

        subscriptionService.createSubscription(mctsSubscriber, 9439986187L, rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        mctsSubscriber = subscriberService.getSubscriber(9439986187L).get(0);

        Subscription subscription = mctsSubscriber.getSubscriptions().iterator().next();

        assertEquals(now.minusDays(90).withTimeAtStartOfDay(), subscription.getStartDate());
        assert(subscription.getStatus() == SubscriptionStatus.ACTIVE);

        mctsSubscriber.setLastMenstrualPeriod(now.minusDays(650));
        subscriberService.updateStartDate(mctsSubscriber);

        mctsSubscriber = subscriberService.getSubscriber(9439986187L).get(0);
        subscription = mctsSubscriber.getSubscriptions().iterator().next();

        assertEquals(now.minusDays(560).withTimeAtStartOfDay(), subscription.getStartDate());
        assert(subscription.getStatus() == SubscriptionStatus.COMPLETED);

        transactionManager.commit(status);
    }


    @Test
    public void testMaxNoOfActiveKkSubscriberHasNoImpactOnAlreadyCreatedSubscriber() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        subscriptionService.toggleMctsSubscriptionCreation(4);

        // sub1
        Subscriber mctsSubscriber1 = new Subscriber(9999911122L);
        mctsSubscriber1.setDateOfBirth(DateTime.now().minusDays(14));
        subscriberDataService.create(mctsSubscriber1);

        Subscription hold1 = subscriptionService.createSubscription(mctsSubscriber1, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);


       // set active subscriptions to zero
        subscriptionService.toggleMctsSubscriptionCreation(0);

        // sub2
        Subscriber mctsSubscriber2 = new Subscriber(9999911123L);
        mctsSubscriber2.setDateOfBirth(DateTime.now().minusDays(14));
        subscriberDataService.create(mctsSubscriber2);

        // creation subscriptions

        Subscription hold2 = subscriptionService.createSubscription(mctsSubscriber2, 9999911123L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        // verify their status before removing the limit
        assertEquals(SubscriptionStatus.ACTIVE, hold1.getStatus());
        assertEquals(SubscriptionStatus.HOLD, hold2.getStatus());

        subscriptionService.toggleMctsSubscriptionCreation(10000); // set activation to active
        subscriptionService.activateHoldSubscriptions(10000);

        transactionManager.commit(status);

        // verify that sub2 is set to active after the active subscriptions limit is removed, and no change in active sub
        assertEquals(SubscriptionStatus.ACTIVE, hold1.getStatus());
        assertEquals(SubscriptionStatus.ACTIVE, hold2.getStatus());

    }

    /*Verify welcome message playing for week #1 for pregnancy pack*/
    @Test
    public void testWelcomeMessageFormotherSubscription() {
        DateTime now = DateTime.now();

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setLastMenstrualPeriod(now.minusDays(90)); //so the startDate should be today
        subscriberDataService.create(mctsSubscriber);
        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);

        Subscription subscription = mctsSubscriber.getSubscriptions().iterator().next();

        // initially, the welcome message should be played
        SubscriptionPackMessage message = subscription.nextScheduledMessage(now);
        assertEquals("w1_1", message.getWeekId());
    }

    /*Verify welcome message playing for week #1 for child pack*/

    @Test
    public void testWelcomeMessageForchildSubscription() {
        DateTime now = DateTime.now();

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setDateOfBirth(DateTime.now().minusDays(1)); //so the startDate should be today
        subscriberDataService.create(mctsSubscriber);
        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);

        Subscription subscription = mctsSubscriber.getSubscriptions().iterator().next();

        // initially, the welcome message should be played
        SubscriptionPackMessage message = subscription.nextScheduledMessage(now);
        assertEquals("w1_1", message.getWeekId());
    }

    @Test
    @Ignore
    public void verifyHoldToPendingActivation() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        subscriptionService.toggleMctsSubscriptionCreation(0);

        DateTime now = DateTime.now();



        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setLastMenstrualPeriod(now.minusDays(70)); //so the startDate should be today
        subscriberDataService.create(mctsSubscriber);
        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        mctsSubscriber = subscriberService.getSubscriber(9999911122L).get(0);

        Subscription subscription = mctsSubscriber.getSubscriptions().iterator().next();

        // verify that they are on hold
        assertEquals(SubscriptionStatus.HOLD, subscription.getStatus());


        subscriptionService.toggleMctsSubscriptionCreation(10000); // set activation to active
        subscriptionService.activateHoldSubscriptions(10000);

        transactionManager.commit(status);

        assertEquals(SubscriptionStatus.PENDING_ACTIVATION, subscription.getStatus());
    }


}

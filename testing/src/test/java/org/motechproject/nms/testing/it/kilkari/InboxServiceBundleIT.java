package org.motechproject.nms.testing.it.kilkari;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.motechproject.nms.kilkari.domain.Subscriber;
import org.motechproject.nms.kilkari.domain.Subscription;
import org.motechproject.nms.kilkari.domain.SubscriptionOrigin;
import org.motechproject.nms.kilkari.domain.SubscriptionPackMessage;
import org.motechproject.nms.kilkari.exception.NoInboxForSubscriptionException;
import org.motechproject.nms.kilkari.repository.SubscriberDataService;
import org.motechproject.nms.kilkari.repository.SubscriptionPackDataService;
import org.motechproject.nms.kilkari.repository.SubscriptionPackMessageDataService;
import org.motechproject.nms.kilkari.service.InboxService;
import org.motechproject.nms.kilkari.service.SubscriberService;
import org.motechproject.nms.kilkari.service.SubscriptionService;
import org.motechproject.nms.region.repository.CircleDataService;
import org.motechproject.nms.region.repository.DistrictDataService;
import org.motechproject.nms.region.repository.LanguageDataService;
import org.motechproject.nms.region.repository.StateDataService;
import org.motechproject.nms.region.service.DistrictService;
import org.motechproject.nms.region.service.LanguageService;
import org.motechproject.nms.testing.it.utils.RegionHelper;
import org.motechproject.nms.testing.it.utils.SubscriptionHelper;
import org.motechproject.nms.testing.service.TestingService;
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
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
@ExamFactory(MotechNativeTestContainerFactory.class)
public class InboxServiceBundleIT extends BasePaxIT {

	@Inject
	SubscriberService subscriberService;
	@Inject
	SubscriptionService subscriptionService;
	@Inject
	InboxService inboxService;
	@Inject
	SubscriptionPackDataService subscriptionPackDataService;
	@Inject
	LanguageDataService languageDataService;
	@Inject
	LanguageService languageService;
	@Inject
	StateDataService stateDataService;
	@Inject
	TestingService testingService;
    @Inject
    CircleDataService circleDataService;
	@Inject
	DistrictDataService districtDataService;
	@Inject
	DistrictService districtService;
    @Inject
    SubscriberDataService subscriberDataService;
    @Inject
    SubscriptionPackMessageDataService subscriptionPackMessageDataService;
	@Inject
	PlatformTransactionManager transactionManager;

    RegionHelper rh;
    SubscriptionHelper sh;

    @Before
    public void setUp() {
		testingService.clearDatabase();

		rh = new RegionHelper(languageDataService, languageService, circleDataService, stateDataService,
				districtDataService, districtService);

        sh = new SubscriptionHelper(subscriberService,subscriptionService, subscriberDataService, subscriptionPackDataService,
				languageDataService, languageService, circleDataService, stateDataService, districtDataService,
                districtService);
    }


	@Test
	public void testServicePresent() throws Exception {
		assertNotNull(inboxService);
	}


	/*
	 * To check NMS is able to make available a single message of current week in inbox when user is subscribed to
	 * Pregnancy Pack with 2 message per week configuration.
	 *
	 */
    @Ignore
	@Test
	public void verifyFT108() throws Exception {

		DateTime now = DateTime.now();

		// Configuration for second msg of the week
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        Subscriber subscriber = new Subscriber(1000000002L, rh.hindiLanguage());
		subscriber.setLastMenstrualPeriod(now.minusDays(94));
		subscriberService.create(subscriber);
        transactionManager.commit(status);

        status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(), rh.hindiLanguage(),
                sh.pregnancyPack(), SubscriptionOrigin.MCTS_IMPORT);

		subscriber = subscriberService.getSubscriber(subscriber.getCallingNumber()).get(0);
		Set<Subscription> subscriptions = subscriber.getAllSubscriptions();
		Subscription subscription = subscriptions.iterator().next();
		SubscriptionPackMessage msg = inboxService.getInboxMessage(subscription);
        transactionManager.commit(status);

		// second msg should be in inbox
		assertEquals(msg.getWeekId(), "w1_2");
		assertEquals(msg.getMessageFileName(), "w1_2.wav");

		// Configuration for first msg of the week
        status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        subscriber.setLastMenstrualPeriod(DateTime.now().minusDays(90));
		subscriberService.updateStartDate(subscriber);
        transactionManager.commit(status);

        status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        subscriber = subscriberService.getSubscriber(subscriber.getCallingNumber()).get(0);
		subscriptions = subscriber.getAllSubscriptions();
		subscription = subscriptions.iterator().next();
		msg = inboxService.getInboxMessage(subscription);
        transactionManager.commit(status);

		// first msg should be in inbox
		assertEquals(msg.getWeekId(), "w1_1");
		assertEquals(msg.getMessageFileName(), "w1_1.wav");

	}

	/*
	 *  To check NMS is able to make a message available for 7 days after user's subscription gets completed for
	 *  Pregnancy Pack.
	 */
    @Ignore
	@Test
	public void verifyFT121() throws Exception {
		DateTime now = DateTime.now();

		// Configuration for second msg of the week
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        Subscriber subscriber = new Subscriber(1000000002L, rh.hindiLanguage());
		subscriber.setLastMenstrualPeriod(now.minusDays(94));
		subscriberService.create(subscriber);
        transactionManager.commit(status);

        status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(), rh.hindiLanguage(),
				sh.pregnancyPack(), SubscriptionOrigin.MCTS_IMPORT);

		subscriber = subscriberService.getSubscriber(subscriber.getCallingNumber()).get(0);
		Set<Subscription> subscriptions = subscriber.getAllSubscriptions();
		Subscription subscription = subscriptions.iterator().next();
		SubscriptionPackMessage msg = inboxService.getInboxMessage(subscription);
        transactionManager.commit(status);

		// second msg should be in inbox
		assertEquals(msg.getWeekId(), "w1_2");
		assertEquals(msg.getMessageFileName(), "w1_2.wav");

		// Configuration for last msg of the week
        status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        subscriber.setLastMenstrualPeriod(now.minusDays(595));
		subscriberService.updateStartDate(subscriber);

		subscriber = subscriberService.getSubscriber(subscriber.getCallingNumber()).get(0);
		subscriptions = subscriber.getAllSubscriptions();
		subscription = subscriptions.iterator().next();
		msg = inboxService.getInboxMessage(subscription);
        transactionManager.commit(status);

		// last msg should be in inbox
		assertEquals(msg.getWeekId(), "w72_2");
		assertEquals(msg.getMessageFileName(), "w72_2.wav");

	}
	
	/*
	 *	To check NMS is able to make available a single message of current week in inbox when user is subscribed to
	 *	Child Pack with single message per week configuration.
	 */
	@Test
	public void verifyFT113() throws NoInboxForSubscriptionException {

		DateTime now = DateTime.now();
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        Subscriber subscriber = new Subscriber(1000000002L);
		subscriber.setDateOfBirth(now);
		subscriberService.create(subscriber);
        transactionManager.commit(status);

		// create subscription for childPack with one message per week.
	
        status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(), rh.hindiLanguage(), sh.childPack(),
				SubscriptionOrigin.MCTS_IMPORT);

		subscriber = subscriberService.getSubscriber(subscriber.getCallingNumber()).get(0);
		Set<Subscription> subscriptions = subscriber.getAllSubscriptions();
		Subscription subscription = subscriptions.iterator().next();
		SubscriptionPackMessage msg = inboxService.getInboxMessage(subscription);
        transactionManager.commit(status);

		// first msg should be in inbox
		assertEquals(msg.getWeekId(), "w1_1");
		assertEquals(msg.getMessageFileName(), "w1_1.wav");

		// Configuration for last msg of the week
        status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        subscriber.setDateOfBirth(now.minusDays(6));
		subscriberService.updateStartDate(subscriber);

		subscriber = subscriberService.getSubscriber(subscriber.getCallingNumber()).get(0);
		subscriptions = subscriber.getAllSubscriptions();
		subscription = subscriptions.iterator().next();
		msg = inboxService.getInboxMessage(subscription);
        transactionManager.commit(status);

		// still first message should be in inbox because messagePerWeek is one
		assertEquals(msg.getWeekId(), "w1_1");
		assertEquals(msg.getMessageFileName(), "w1_1.wav");

	}


	/*
	 * To verify in case of "Early Subscription" of Pregnancy Pack, inbox should not contain any message.
	 */
    @Ignore
	@Test
	public void verifyFT162() throws NoInboxForSubscriptionException {
		DateTime now = DateTime.now();
		// create subscriber for early subscription
		Subscriber subscriber = new Subscriber(1000000002L);
		subscriber.setLastMenstrualPeriod(now.minusDays(30));
		subscriberService.create(subscriber);

		subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(), rh.hindiLanguage(), sh.pregnancyPack(),
				SubscriptionOrigin.MCTS_IMPORT);

		subscriber = subscriberService.getSubscriber(subscriber.getCallingNumber()).get(0);
		Set<Subscription> subscriptions = subscriber.getAllSubscriptions();
		Subscription subscription = subscriptions.iterator().next();

		// Inbox should be empty
		assertNull(inboxService.getInboxMessage(subscription));
	}

	/*
	 *  To check NMS is able to make a message available for 7 days  after user's subscription gets completed for
	 *  Child Pack.
	 */
    @Ignore
	@Test
	public void verifyFT122() throws Exception {
		DateTime now = DateTime.now();
		Subscriber subscriber = new Subscriber(1000000002L);
		subscriber.setDateOfBirth(now.minusDays(335));
		subscriberService.create(subscriber);
		
		// create subscription for childPack with one message per week.
		subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(), rh.hindiLanguage(), sh.childPack(),
				SubscriptionOrigin.MCTS_IMPORT);
		
		subscriber = subscriberService.getSubscriber(subscriber.getCallingNumber()).get(0);
		Set<Subscription> subscriptions = subscriber.getAllSubscriptions();
		Subscription subscription = subscriptions.iterator().next();
		SubscriptionPackMessage msg = inboxService.getInboxMessage(subscription);
		
		// last msg should be in inbox
		assertEquals(msg.getWeekId(), "w48_1");
		assertEquals(msg.getMessageFileName(), "w48_1.wav");
		
		subscriber.setDateOfBirth(DateTime.now().minusDays(342)); // completion_duration + 7 days
		subscriberService.updateStartDate(subscriber);

		subscriber = subscriberService.getSubscriber(subscriber.getCallingNumber()).get(0);
		subscriptions = subscriber.getAllSubscriptions();
		subscription = subscriptions.iterator().next();
		msg = inboxService.getInboxMessage(subscription);

		// last message should be in inbox till 7 days after subscription completion
		assertEquals(msg.getWeekId(), "w48_1");
		assertEquals(msg.getMessageFileName(), "w48_1.wav");

	}
	
	/*
	 *	"To check NMS is able to make available a single message of current week in inbox
	 *	 when user is subscribed to 72Weeks Pack with single message per week configuration."
	 */
	@Test
	public void verifyFT107() throws NoInboxForSubscriptionException {

		TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
		DateTime now = DateTime.now();
		// create subscriber for pregnancyPack
		Subscriber subscriber = new Subscriber(1000000002L);
		subscriber.setLastMenstrualPeriod(now.minusDays(90));
		subscriberService.create(subscriber);
		transactionManager.commit(status);
		
		// create pregnancyPack subscription for one message per week.
        status = transactionManager.getTransaction(new DefaultTransactionDefinition());
		subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(), rh.hindiLanguage(),
				sh.pregnancyPackFor1MessagePerWeek(subscriptionPackMessageDataService), SubscriptionOrigin.MCTS_IMPORT);
		
		subscriber = subscriberService.getSubscriber(subscriber.getCallingNumber()).get(0);
		Set<Subscription> subscriptions = subscriber.getAllSubscriptions();
		Subscription subscription = subscriptions.iterator().next();
		SubscriptionPackMessage msg = inboxService.getInboxMessage(subscription);
        transactionManager.commit(status);
		
		// first msg should be in inbox
        status = transactionManager.getTransaction(new DefaultTransactionDefinition());
		assertEquals(msg.getWeekId(), "w1_1");
		assertEquals(msg.getMessageFileName(), "w1_1.wav");
		// set lmp as 7th day week of week to check message exist for seven days.
		subscriber.setLastMenstrualPeriod(DateTime.now().minusDays(96)); 
		subscriberService.updateStartDate(subscriber);

		subscriber = subscriberService.getSubscriber(subscriber.getCallingNumber()).get(0);
		subscriptions = subscriber.getAllSubscriptions();
		subscription = subscriptions.iterator().next();
		msg = inboxService.getInboxMessage(subscription);

		// still first message should be in inbox because messagePerWeek is one
		assertEquals(msg.getWeekId(), "w1_1");
		assertEquals(msg.getMessageFileName(), "w1_1.wav");
        transactionManager.commit(status);
		
	}
	
	/*
	 *	"To check NMS is able to make available a single message of current week in inbox
	 *	 when user is subscribed to 48Weeks Pack with two message per week configuration."
	 */
    @Ignore
	@Test
	public void verifyFT114() throws NoInboxForSubscriptionException {
		
		DateTime now = DateTime.now();
		// create subscriber for childPack
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        Subscriber subscriber = new Subscriber(1000000002L);
		subscriber.setDateOfBirth(now);
		subscriberService.create(subscriber);
        transactionManager.commit(status);
		
		// create childPack subscription for two message per week.
        status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(), rh.hindiLanguage(),
				sh.childPackFor2MessagePerWeek(subscriptionPackMessageDataService),
				SubscriptionOrigin.MCTS_IMPORT);
		
		subscriber = subscriberService.getSubscriber(subscriber.getCallingNumber()).get(0);
		Set<Subscription> subscriptions = subscriber.getAllSubscriptions();
		Subscription subscription = subscriptions.iterator().next();
		SubscriptionPackMessage msg = inboxService.getInboxMessage(subscription);
        transactionManager.commit(status);
		
		// first msg should be in inbox
		assertEquals(msg.getWeekId(), "w1_1");
		assertEquals(msg.getMessageFileName(), "w1_1.wav");

        status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        subscriber.setDateOfBirth(now.minusDays(4));
		subscriberService.updateStartDate(subscriber);

		subscriber = subscriberService.getSubscriber(subscriber.getCallingNumber()).get(0);
		subscriptions = subscriber.getAllSubscriptions();
		subscription = subscriptions.iterator().next();
		msg = inboxService.getInboxMessage(subscription);
        transactionManager.commit(status);

		// second message should be in inbox because messagePerWeek is two
		assertEquals(msg.getWeekId(), "w1_2");
		assertEquals(msg.getMessageFileName(), "w1_2.wav");
		
	}
	
	/*
	 * To verify number of Messages per week should be modified successfully from 1 to 2.
	 */
    @Ignore
	@Test
 	public void verifyFT178() throws InterruptedException, Exception {
 		
 		DateTime now = DateTime.now();

 		Subscriber subscriber = new Subscriber(1000000002L);
 		subscriber.setDateOfBirth(now.minusDays(4));
 		subscriberService.create(subscriber);

 		Subscription childSubscription = subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(), rh.hindiLanguage(), sh.childPack(),
				SubscriptionOrigin.MCTS_IMPORT);
 		assertEquals(1, childSubscription.getSubscriptionPack().getMessagesPerWeek());
 		SubscriptionPackMessage packMessage = inboxService.getInboxMessage(childSubscription);
 		assertEquals("w1_1", packMessage.getWeekId());
 		assertEquals("w1_1.wav", packMessage.getMessageFileName());

 		//update subscriptionPack and subscriptionPackMeassage list according to 2 msg per week for childPack
 		sh.childPackFor2MessagePerWeek(subscriptionPackMessageDataService);

 		subscriber = subscriberService.getSubscriber(subscriber.getCallingNumber()).get(0);
 		childSubscription = subscriber.getSubscriptions().iterator().next();
 		assertEquals(2, childSubscription.getSubscriptionPack().getMessagesPerWeek());
 		
 		packMessage = inboxService.getInboxMessage(childSubscription);
 		assertEquals("w1_2", packMessage.getWeekId());
 		assertEquals("w1_2.wav", packMessage.getMessageFileName());
 	}

	/*
	 * To verify number of Messages per week should be modified successfully from 2 to 1.
	 */
    @Ignore
 	@Test
 	public void verifyFT179() throws InterruptedException, Exception {
 		DateTime now = DateTime.now();

 		Subscriber subscriber = new Subscriber(1000000002L);
 		subscriber.setLastMenstrualPeriod(now.minusDays(94));
 		subscriberService.create(subscriber);

 		Subscription motherSubscription = subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(), rh.hindiLanguage(), sh.pregnancyPack(),
 				SubscriptionOrigin.MCTS_IMPORT);
 		assertEquals(2, motherSubscription.getSubscriptionPack().getMessagesPerWeek());
 		
 		SubscriptionPackMessage packMessage = inboxService.getInboxMessage(motherSubscription);
 		assertEquals("w1_2", packMessage.getWeekId());
 		assertEquals("w1_2.wav", packMessage.getMessageFileName());

 		//update subscriptionPack and subscriptionPackMeassage list according to 1 msg per week for pregnancyPack
 		sh.pregnancyPackFor1MessagePerWeek(subscriptionPackMessageDataService);
 		
 		subscriber = subscriberService.getSubscriber(subscriber.getCallingNumber()).get(0);
 		motherSubscription = subscriber.getSubscriptions().iterator().next();
 		assertEquals(1, motherSubscription.getSubscriptionPack().getMessagesPerWeek());
 		
 		packMessage = inboxService.getInboxMessage(motherSubscription);
 		assertEquals("w1_1", packMessage.getWeekId());
 		assertEquals("w1_1.wav", packMessage.getMessageFileName());

 	}

    @Ignore
	@Test
	public void testInboxPlaysWelcomeMessageAfterActivation() throws Exception {
		DateTime now = DateTime.now();

		// Configuration for second msg of the week
		Subscriber subscriber = new Subscriber(1000000002L, rh.hindiLanguage());
		subscriber.setLastMenstrualPeriod(now.minusDays(108));
		subscriberService.create(subscriber);

		subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(), rh.hindiLanguage(),
				sh.pregnancyPack(), SubscriptionOrigin.MCTS_IMPORT);

		subscriber = subscriberService.getSubscriber(subscriber.getCallingNumber()).get(0);
		Set<Subscription> subscriptions = subscriber.getAllSubscriptions();
		Subscription subscription = subscriptions.iterator().next();
		SubscriptionPackMessage msg = inboxService.getInboxMessage(subscription);

		// second msg of 3rd week should be in inbox
		assertEquals(msg.getWeekId(), "w3_2");
		assertEquals(msg.getMessageFileName(), "w3_2.wav");

		// Configuration for first msg of the week
		subscriber.setLastMenstrualPeriod(DateTime.now().minusDays(104));
		subscriberService.updateStartDate(subscriber);

		subscriber = subscriberService.getSubscriber(subscriber.getCallingNumber()).get(0);
		subscriptions = subscriber.getAllSubscriptions();
		subscription = subscriptions.iterator().next();
		msg = inboxService.getInboxMessage(subscription);

		// welcome msg should be in inbox
		assertEquals(msg.getWeekId(), "w1_1");
		assertEquals(msg.getMessageFileName(), "w1_1.wav");

	}

}

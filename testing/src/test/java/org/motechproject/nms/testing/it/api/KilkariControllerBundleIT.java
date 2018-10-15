package org.motechproject.nms.testing.it.api;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.motechproject.nms.api.web.contract.BadRequest;
import org.motechproject.nms.api.web.contract.kilkari.CallDataRequest;
import org.motechproject.nms.api.web.contract.kilkari.InboxCallDetailsRequest;
import org.motechproject.nms.api.web.contract.kilkari.InboxResponse;
import org.motechproject.nms.api.web.contract.kilkari.InboxSubscriptionDetailResponse;
import org.motechproject.nms.api.web.contract.kilkari.SubscriptionRequest;
import org.motechproject.nms.kilkari.domain.DeactivationReason;
import org.motechproject.nms.kilkari.domain.InboxCallData;
import org.motechproject.nms.kilkari.domain.InboxCallDetailRecord;
import org.motechproject.nms.kilkari.domain.Subscriber;
import org.motechproject.nms.kilkari.domain.Subscription;
import org.motechproject.nms.kilkari.domain.SubscriptionOrigin;
import org.motechproject.nms.kilkari.domain.SubscriptionStatus;
import org.motechproject.nms.kilkari.repository.InboxCallDataDataService;
import org.motechproject.nms.kilkari.repository.InboxCallDetailRecordDataService;
import org.motechproject.nms.kilkari.repository.SubscriberDataService;
import org.motechproject.nms.kilkari.repository.SubscriptionDataService;
import org.motechproject.nms.kilkari.repository.SubscriptionPackDataService;
import org.motechproject.nms.kilkari.repository.SubscriptionPackMessageDataService;
import org.motechproject.nms.kilkari.service.SubscriberService;
import org.motechproject.nms.kilkari.service.SubscriptionService;
import org.motechproject.nms.props.domain.DeployedService;
import org.motechproject.nms.props.domain.Service;
import org.motechproject.nms.props.repository.DeployedServiceDataService;
import org.motechproject.nms.region.domain.Circle;
import org.motechproject.nms.region.domain.District;
import org.motechproject.nms.region.domain.Language;
import org.motechproject.nms.region.domain.State;
import org.motechproject.nms.region.repository.CircleDataService;
import org.motechproject.nms.region.repository.DistrictDataService;
import org.motechproject.nms.region.repository.LanguageDataService;
import org.motechproject.nms.region.repository.StateDataService;
import org.motechproject.nms.region.service.DistrictService;
import org.motechproject.nms.region.service.LanguageService;
import org.motechproject.nms.testing.it.api.utils.HttpDeleteWithBody;
import org.motechproject.nms.testing.it.utils.RegionHelper;
import org.motechproject.nms.testing.it.utils.SubscriptionHelper;
import org.motechproject.nms.testing.service.TestingService;
import org.motechproject.testing.osgi.BasePaxIT;
import org.motechproject.testing.osgi.container.MotechNativeTestContainerFactory;
import org.motechproject.testing.osgi.http.SimpleHttpClient;
import org.motechproject.testing.utils.TestContext;
import org.ops4j.pax.exam.ExamFactory;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.motechproject.nms.testing.it.utils.RegionHelper.createDistrict;
import static org.motechproject.nms.testing.it.utils.RegionHelper.createState;


/**
 * Verify that Kilkari API is functional.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
@ExamFactory(MotechNativeTestContainerFactory.class)
public class KilkariControllerBundleIT extends BasePaxIT {
    private static final String ADMIN_USERNAME = "motech";
    private static final String ADMIN_PASSWORD = "motech";

    private static final String VALID_CALL_ID = "1234567890123456789012345";

    @Inject
    SubscriberService subscriberService;
    @Inject
    SubscriptionService subscriptionService;
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
    CircleDataService circleDataService;
    @Inject
    StateDataService stateDataService;
    @Inject
    DistrictDataService districtDataService;
    @Inject
    DistrictService districtService;
    @Inject
    DeployedServiceDataService deployedServiceDataService;
    @Inject
    TestingService testingService;
    @Inject
    InboxCallDetailRecordDataService inboxCallDetailsDataService;
    @Inject
    InboxCallDataDataService inboxCallDataDataService;

    @Inject
    PlatformTransactionManager transactionManager;

    private RegionHelper rh;
    private SubscriptionHelper sh;

    @Before
    public void setupTestData() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        testingService.clearDatabase();

        rh = new RegionHelper(languageDataService, languageService, circleDataService, stateDataService,
                districtDataService, districtService);

        sh = new SubscriptionHelper(subscriptionService, subscriberDataService, subscriptionPackDataService,
                languageDataService, languageService, circleDataService, stateDataService, districtDataService,
                districtService);

        // subscriber1 subscribed to child pack only
        Subscriber subscriber1 = subscriberDataService.create(new Subscriber(1000000000L));
        subscriptionService.createSubscription(subscriber1, subscriber1.getCallingNumber(), rh.hindiLanguage(),
                sh.childPack(), SubscriptionOrigin.IVR);

        // subscriber2 subscribed to both child & pregnancy pack
        Subscriber subscriber2 = subscriberDataService.create(new Subscriber(2000000000L));
        subscriptionService.createSubscription(subscriber2, subscriber2.getCallingNumber(), rh.hindiLanguage(),
                sh.childPack(), SubscriptionOrigin.IVR);
        subscriptionService.createSubscription(subscriber2, subscriber2.getCallingNumber(), rh.hindiLanguage(),
                sh.pregnancyPack(), SubscriptionOrigin.IVR);

        // subscriber3 subscribed to pregnancy pack only
        Subscriber subscriber3 = subscriberDataService.create(new Subscriber(4000000000L));
        subscriptionService.createSubscription(subscriber3, subscriber3.getCallingNumber(), rh.hindiLanguage(),
                sh.pregnancyPack(), SubscriptionOrigin.IVR);

        deployedServiceDataService.create(new DeployedService(rh.delhiState(), Service.KILKARI));

        transactionManager.commit(status);
     }


    private HttpGet createHttpGet(boolean includeCallingNumber, String callingNumber,
                                  boolean includeCallId, String callId) {

        StringBuilder sb = new StringBuilder(String.format("http://localhost:%d/api/kilkari/inbox?",
                TestContext.getJettyPort()));
        String sep = "";
        if (includeCallingNumber) {
            sb.append(String.format("callingNumber=%s", callingNumber));
            sep = "&";
        }
        if (includeCallId) {
            sb.append(String.format("%scallId=%s", sep, callId));
        }

        return new HttpGet(sb.toString());
    }


    private String createInboxResponseJson(Set<InboxSubscriptionDetailResponse> inboxSubscriptionDetailList)
            throws IOException {
        InboxResponse response = new InboxResponse(inboxSubscriptionDetailList);
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(response);
    }


    private String createFailureResponseJson(String failureReason) throws IOException {
        BadRequest badRequest = new BadRequest(failureReason);
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(badRequest);
    }

    @Test
    public void testInboxRequest() throws IOException, InterruptedException {

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberDataService.findByNumber(1000000000L).get(0); // 1 subscription
        Subscription subscription = subscriber.getSubscriptions().iterator().next();

        // override the default start date (today + 1 day) in order to see a non-empty inbox
        subscription.setStartDate(DateTime.now().minusDays(2));
        subscription.setStatus(Subscription.getStatus(subscription, DateTime.now()));
        subscriptionDataService.update(subscription);

        transactionManager.commit(status);

        HttpGet httpGet = createHttpGet(true, "1000000000", true, VALID_CALL_ID);
        String expectedJson = createInboxResponseJson(new HashSet<>(Arrays.asList(
                new InboxSubscriptionDetailResponse(
                        subscription.getSubscriptionId(),
                        sh.childPack().getName(),
                        sh.getWeekId(subscription, 0),
                        sh.getContentMessageFile(subscription, 0)
                )
        )));

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        assertEquals(expectedJson, EntityUtils.toString(response.getEntity()));
    }


    @Test
    public void testInboxRequestTwoSubscriptions() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setDateOfBirth(DateTime.now().minusDays(250));
        subscriberDataService.create(mctsSubscriber);

        // create subscription to child pack
        Subscription child = subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        mctsSubscriber = subscriberDataService.findByNumber(9999911122L).get(0);
        child.setNeedsWelcomeMessageViaObd(false);
        subscriptionDataService.update(child);

        // due to subscription rules detailed in #157, we need to clear out the DOB and set an LMP in order to
        // create a second subscription for this MCTS subscriber
        mctsSubscriber.setDateOfBirth(null);
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now().minusDays(103));
        subscriberDataService.update(mctsSubscriber);

        // create subscription to pregnancy pack
        Subscription mother = subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        mother.setNeedsWelcomeMessageViaObd(false);
        subscriptionDataService.update(mother);

        transactionManager.commit(status);

        Pattern childPackJsonPattern = Pattern.compile(".*\"subscriptionPack\":\"childPack\",\"inboxWeekId\":\"w36_1\",\"contentFileName\":\"w36_1\\.wav.*");
        Pattern pregnancyPackJsonPattern = Pattern.compile(".*\"subscriptionPack\":\"pregnancyPack\",\"inboxWeekId\":\"w2_2\",\"contentFileName\":\"w2_2\\.wav.*");

        HttpGet httpGet = createHttpGet(true, "9999911122", true, VALID_CALL_ID);
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, childPackJsonPattern, ADMIN_USERNAME,
                ADMIN_PASSWORD));
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, pregnancyPackJsonPattern, ADMIN_USERNAME,
                ADMIN_PASSWORD));
    }


    @Test
    public void testInboxRequestEarlySubscription() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now().minusDays(30));
        subscriberDataService.create(mctsSubscriber);
        // create subscription to pregnancy pack, not due to start for 60 days
        subscriptionService.createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        transactionManager.commit(status);

        Pattern expectedJsonPattern = Pattern.compile(
                ".*\"subscriptionPack\":\"pregnancyPack\",\"inboxWeekId\":null,\"contentFileName\":null.*");

        HttpGet httpGet = createHttpGet(true, "9999911122", true, VALID_CALL_ID);
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, expectedJsonPattern, ADMIN_USERNAME, ADMIN_PASSWORD));
    }

    @Test
    public void testInboxRequestCompletedSubscription() throws IOException, InterruptedException {

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberService.getSubscriber(1000000000L).get(0);
        Subscription subscription = subscriber.getSubscriptions().iterator().next();
        // setting the subscription to have ended more than a week ago -- no message should be returned
        subscription.setStartDate(DateTime.now().minusDays(500));
        subscription.setStatus(SubscriptionStatus.COMPLETED);
        subscriptionDataService.update(subscription);

        transactionManager.commit(status);

        String expectedJson = "{\"inboxSubscriptionDetailList\":[]}";

        HttpGet httpGet = createHttpGet(true, "1000000000", true, VALID_CALL_ID);
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, expectedJson, ADMIN_USERNAME, ADMIN_PASSWORD));
    }


    @Test
    public void testInboxRequestRecentlyCompletedSubscription() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberService.getSubscriber(1000000000L).get(0);
        Subscription subscription = subscriber.getSubscriptions().iterator().next();
        // setting the subscription to have ended less than a week ago -- the final message should be returned
        subscription.setStartDate(DateTime.now().minusDays(340));
        subscription.setStatus(SubscriptionStatus.COMPLETED);
        subscriptionDataService.update(subscription);

        transactionManager.commit(status);

        Pattern expectedJsonPattern = Pattern.compile(".*\"subscriptionPack\":\"childPack\",\"inboxWeekId\":\"w48_1\",\"contentFileName\":\"w48_1\\.wav.*");

        HttpGet httpGet = createHttpGet(true, "1000000000", true, VALID_CALL_ID);
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, expectedJsonPattern, ADMIN_USERNAME, ADMIN_PASSWORD));
    }


    @Test
    public void testInboxRequestDeactivatedSubscription() throws IOException, InterruptedException {

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberService.getSubscriber(1000000000L).get(0);
        Subscription subscription = subscriber.getSubscriptions().iterator().next();
        subscription.setStatus(SubscriptionStatus.DEACTIVATED);
        subscription.setDeactivationReason(DeactivationReason.DEACTIVATED_BY_USER);
        subscriptionDataService.update(subscription);

        transactionManager.commit(status);

        String expectedJson = "{\"inboxSubscriptionDetailList\":[]}";

        HttpGet httpGet = createHttpGet(true, "1000000000", true, VALID_CALL_ID);
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, expectedJson, ADMIN_USERNAME, ADMIN_PASSWORD));
    }


    @Test
    public void testInboxRequestSeveralSubscriptionsInDifferentStates() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // subscriber has two active subscriptions
        Subscriber subscriber = subscriberService.getSubscriber(2000000000L).get(0);

        Iterator<Subscription> subscriptionIterator = subscriber.getSubscriptions().iterator();
        Subscription subscription1 = subscriptionIterator.next();
        Subscription subscription2 = subscriptionIterator.next();

        // deactivate one of them
        subscription1.setStatus(SubscriptionStatus.DEACTIVATED);
        subscription1.setDeactivationReason(DeactivationReason.DEACTIVATED_BY_USER);
        subscriptionDataService.update(subscription1);

        // complete the other one
        subscription2.setStartDate(subscription2.getStartDate().minusDays(1000));
        subscription2.setStatus(SubscriptionStatus.COMPLETED);
        subscriptionDataService.update(subscription2);

        transactionManager.commit(status);

        // inbox request should return empty inbox
        String expectedJson = "{\"inboxSubscriptionDetailList\":[]}";
        HttpGet httpGet = createHttpGet(true, "2000000000", true, VALID_CALL_ID);
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, expectedJson, ADMIN_USERNAME, ADMIN_PASSWORD));

        // create two more subscriptions -- this time using MCTS import as subscription origin

        status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // create subscription to child pack
        subscriber.setDateOfBirth(DateTime.now().minusDays(250));
        subscriberDataService.update(subscriber);
        Subscription child = subscriptionService.createSubscription(subscriber, 2000000000L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        child.setNeedsWelcomeMessageViaObd(false);
        subscriptionDataService.update(child);
        subscriber = subscriberDataService.findByNumber(2000000000L).get(0);

        // due to subscription rules detailed in #157, we need to clear out the DOB and set an LMP in order to
        // create a second subscription for this subscriber
        subscriber.setDateOfBirth(null);
        subscriber.setLastMenstrualPeriod(DateTime.now().minusDays(103));
        subscriberDataService.update(subscriber);

        // create subscription to pregnancy pack
        Subscription mother = subscriptionService.createSubscription(subscriber, 2000000000L, rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        mother.setNeedsWelcomeMessageViaObd(false);
        subscriptionDataService.update(mother);

        subscriber = subscriberDataService.findByNumber(2000000000L).get(0);
        assertEquals(2, subscriber.getActiveAndPendingSubscriptions().size());
        assertEquals(4, subscriber.getAllSubscriptions().size());

        transactionManager.commit(status);

        Pattern childPackJsonPattern = Pattern.compile(".*\"subscriptionPack\":\"childPack\",\"inboxWeekId\":\"w36_1\",\"contentFileName\":\"w36_1\\.wav.*");
        Pattern pregnancyPackJsonPattern = Pattern.compile(".*\"subscriptionPack\":\"pregnancyPack\",\"inboxWeekId\":\"w2_2\",\"contentFileName\":\"w2_2\\.wav.*");

        httpGet = createHttpGet(true, "2000000000", true, VALID_CALL_ID);

        // inbox request should return two subscriptions

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, childPackJsonPattern, ADMIN_USERNAME,
                ADMIN_PASSWORD));
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, pregnancyPackJsonPattern, ADMIN_USERNAME,
                ADMIN_PASSWORD));
    }


    @Test
    public void testInboxRequestBadSubscriber() throws IOException, InterruptedException {

        HttpGet httpGet = createHttpGet(true, "3000000000", true, VALID_CALL_ID);
        String expectedJsonResponse = createFailureResponseJson("<callingNumber: Not Found>");

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_NOT_FOUND, expectedJsonResponse,
                ADMIN_USERNAME, ADMIN_PASSWORD));
    }


    @Test
    public void testInboxRequestNoSubscriber() throws IOException, InterruptedException {

        HttpGet httpGet = createHttpGet(false, null, true, VALID_CALL_ID);
        String expectedJsonResponse = createFailureResponseJson("<callingNumber: Not Present>");

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_BAD_REQUEST, expectedJsonResponse,
                ADMIN_USERNAME, ADMIN_PASSWORD));
    }


    private HttpPost createSubscriptionHttpPost(long callingNumber, String subscriptionPack)
            throws IOException {

        SubscriptionRequest subscriptionRequest = new SubscriptionRequest(
                callingNumber,
                rh.airtelOperator(),
                rh.delhiCircle().getName(),
                VALID_CALL_ID,
                rh.hindiLanguage().getCode(),
                subscriptionPack);
        ObjectMapper mapper = new ObjectMapper();
        String subscriptionRequestJson = mapper.writeValueAsString(subscriptionRequest);

        HttpPost httpPost = new HttpPost(String.format(
                "http://localhost:%d/api/kilkari/subscription", TestContext.getJettyPort()));
        httpPost.setHeader("Content-type", "application/json");
        httpPost.setEntity(new StringEntity(subscriptionRequestJson));
        return httpPost;
    }


    @Test
    public void testCreateSubscriptionRequest() throws IOException, InterruptedException {
        HttpPost httpPost = createSubscriptionHttpPost(9999911122L, sh.childPack().getName());

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
    }

    @Ignore
    @Test
    public void testCreateSubscriptionRequestAlreadySubscribedViaMCTS() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberService.getSubscriber(1000000000L).get(0);
        Subscription subscription = subscriber.getActiveAndPendingSubscriptions().iterator().next();
        subscription.setOrigin(SubscriptionOrigin.MCTS_IMPORT);
        subscriptionDataService.update(subscription);

        transactionManager.commit(status);

        String subscriptionId = subscription.getSubscriptionId();

        HttpPost httpPost = createSubscriptionHttpPost(1000000000L, sh.childPack().getName());

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

        subscription = subscriptionDataService.findBySubscriptionId(subscriptionId);
        assertEquals(SubscriptionOrigin.IVR, subscription.getOrigin());
    }

    @Test
    public void testCreateSubscriptionRequestInvalidPack() throws IOException, InterruptedException {
        HttpPost httpPost = createSubscriptionHttpPost(9999911122L, "pack99999");

        // Should return HTTP 404 (Not Found) because the subscription pack won't be found
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
    }


    @Test
    public void testCreateSubscriptionRequestSamePack() throws IOException, InterruptedException {
        long callingNumber = 9999911122L;

        HttpPost httpPost = createSubscriptionHttpPost(callingNumber, "childPack");

        SimpleHttpClient.execHttpRequest(httpPost, HttpStatus.SC_OK, ADMIN_USERNAME, ADMIN_PASSWORD);

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberService.getSubscriber(callingNumber).get(0);
        int numberOfSubsBefore = subscriber.getActiveAndPendingSubscriptions().size();

        transactionManager.commit(status);

        SimpleHttpClient.execHttpRequest(httpPost, HttpStatus.SC_OK, ADMIN_USERNAME, ADMIN_PASSWORD);

        status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        subscriber = subscriberService.getSubscriber(callingNumber).get(0);
        int numberOfSubsAfter = subscriber.getActiveAndPendingSubscriptions().size();

        transactionManager.commit(status);

        // No additional subscription should be created because subscriber already has an active subscription
        // to this pack
        assertEquals(numberOfSubsBefore, numberOfSubsAfter);
    }

    @Ignore
    @Test
    public void testCreateSubscriptionRequestDifferentPacks() throws IOException, InterruptedException {
        long callingNumber = 9999911122L;

        HttpPost httpPost = createSubscriptionHttpPost(callingNumber, sh.childPack().getName());
        HttpResponse resp = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, resp.getStatusLine().getStatusCode());

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberService.getSubscriber(callingNumber).get(0);
        int numberOfSubsBefore = subscriber.getActiveAndPendingSubscriptions().size();

        transactionManager.commit(status);

        httpPost = createSubscriptionHttpPost(callingNumber, sh.pregnancyPack().getName());
        resp = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, resp.getStatusLine().getStatusCode());

        status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        subscriber = subscriberService.getSubscriber(callingNumber).get(0);
        int numberOfSubsAfter = subscriber.getActiveAndPendingSubscriptions().size();

        transactionManager.commit(status);

        // Another subscription should be allowed because these are two different packs
        assertTrue((numberOfSubsBefore + 1) == numberOfSubsAfter);
    }


    @Test
    public void testCreateSubscriptionsNoLanguageInDB() throws IOException, InterruptedException {
        SubscriptionRequest subscriptionRequest = new SubscriptionRequest(9999911122L, rh.airtelOperator(),
                rh.delhiCircle().getName(), VALID_CALL_ID, "99", sh.childPack().getName());
        ObjectMapper mapper = new ObjectMapper();
        String subscriptionRequestJson = mapper.writeValueAsString(subscriptionRequest);

        HttpPost httpPost = new HttpPost(String.format(
                "http://localhost:%d/api/kilkari/subscription", TestContext.getJettyPort()));
        httpPost.setHeader("Content-type", "application/json");
        httpPost.setEntity(new StringEntity(subscriptionRequestJson));


        // Should return HTTP 404 (Not Found) because the language won't be found
        assertTrue(SimpleHttpClient
                .execHttpRequest(httpPost, HttpStatus.SC_NOT_FOUND, ADMIN_USERNAME, ADMIN_PASSWORD));
    }


    @Test
    public void testCreateSubscriptionForUndeployedState() throws IOException, InterruptedException {

        SubscriptionRequest subscriptionRequest = new SubscriptionRequest(
                9999911122L,
                rh.airtelOperator(),
                rh.karnatakaCircle().getName(),
                VALID_CALL_ID,
                rh.kannadaLanguage().getCode(),
                sh.childPack().getName());
        ObjectMapper mapper = new ObjectMapper();
        String subscriptionRequestJson = mapper.writeValueAsString(subscriptionRequest);

        HttpPost httpPost = new HttpPost(String.format(
                "http://localhost:%d/api/kilkari/subscription", TestContext.getJettyPort()));
        httpPost.setHeader("Content-type", "application/json");
        httpPost.setEntity(new StringEntity(subscriptionRequestJson));

        // Should return HTTP 501 (Not Implemented) because the service is not deployed for the specified state
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getStatusLine().getStatusCode());
    }


    @Test
    public void testDeactivateSubscriptionRequest() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberService.getSubscriber(1000000000L).get(0);
        Subscription subscription = subscriber.getActiveAndPendingSubscriptions().iterator().next();
        String subscriptionId = subscription.getSubscriptionId();

        transactionManager.commit(status);

        SubscriptionRequest subscriptionRequest = new SubscriptionRequest(1000000000L, rh.airtelOperator(),
                rh.delhiCircle().getName(), VALID_CALL_ID, subscriptionId);
        ObjectMapper mapper = new ObjectMapper();
        String subscriptionRequestJson = mapper.writeValueAsString(subscriptionRequest);

        HttpDeleteWithBody httpDelete = new HttpDeleteWithBody(String.format(
                "http://localhost:%d/api/kilkari/subscription", TestContext.getJettyPort()));
        httpDelete.setHeader("Content-type", "application/json");
        httpDelete.setEntity(new StringEntity(subscriptionRequestJson));

        assertTrue(SimpleHttpClient.execHttpRequest(httpDelete, HttpStatus.SC_OK, ADMIN_USERNAME,
                ADMIN_PASSWORD));

        subscription = subscriptionService.getSubscription(subscriptionId);
        assertTrue(subscription.getStatus().equals(SubscriptionStatus.DEACTIVATED));
        assertTrue(subscription.getDeactivationReason().equals(DeactivationReason.DEACTIVATED_BY_USER));
    }


    @Test
    public void testDeactivateSubscriptionRequestAlreadyInactive() throws IOException, InterruptedException {

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberService.getSubscriber(1000000000L).get(0);
        Subscription subscription = subscriber.getActiveAndPendingSubscriptions().iterator().next();
        String subscriptionId = subscription.getSubscriptionId();
        subscriptionService.deactivateSubscription(subscription, DeactivationReason.DEACTIVATED_BY_USER);

        transactionManager.commit(status);

        SubscriptionRequest subscriptionRequest = new SubscriptionRequest(1000000000L, rh.airtelOperator(), rh.delhiCircle().getName(),
                VALID_CALL_ID, subscriptionId);
        ObjectMapper mapper = new ObjectMapper();
        String subscriptionRequestJson = mapper.writeValueAsString(subscriptionRequest);

        HttpDeleteWithBody httpDelete = new HttpDeleteWithBody(String.format(
                "http://localhost:%d/api/kilkari/subscription", TestContext.getJettyPort()));
        httpDelete.setHeader("Content-type", "application/json");
        httpDelete.setEntity(new StringEntity(subscriptionRequestJson));

        // Should return HTTP 200 (OK) because DELETE on a Deactivated subscription is idempotent
        assertTrue(SimpleHttpClient.execHttpRequest(httpDelete, HttpStatus.SC_OK, ADMIN_USERNAME,
                ADMIN_PASSWORD));

        subscription = subscriptionService.getSubscription(subscriptionId);
        assertTrue(subscription.getStatus().equals(SubscriptionStatus.DEACTIVATED));
    }


    @Test
    public void testDeactivateSubscriptionRequestInvalidSubscription() throws IOException, InterruptedException {

        SubscriptionRequest subscriptionRequest = new SubscriptionRequest(1000000000L, rh.airtelOperator(), rh.delhiCircle().getName(),
                VALID_CALL_ID, "77f13128-037e-4f98-8651-285fa618d94a");
        ObjectMapper mapper = new ObjectMapper();
        String subscriptionRequestJson = mapper.writeValueAsString(subscriptionRequest);

        HttpDeleteWithBody httpDelete = new HttpDeleteWithBody(String.format(
                "http://localhost:%d/api/kilkari/subscription", TestContext.getJettyPort()));
        httpDelete.setHeader("Content-type", "application/json");
        httpDelete.setEntity(new StringEntity(subscriptionRequestJson));

        // Should return HTTP 404 (Not Found) because the subscription ID won't be found
        assertTrue(SimpleHttpClient.execHttpRequest(httpDelete, HttpStatus.SC_NOT_FOUND, ADMIN_USERNAME,
                ADMIN_PASSWORD));
    }


    private HttpPost createInboxCallDetailsRequestHttpPost(InboxCallDetailsRequest request) throws IOException {
        HttpPost httpPost = new HttpPost(String.format("http://localhost:%d/api/kilkari/inboxCallDetails",
                TestContext.getJettyPort()));
        ObjectMapper mapper = new ObjectMapper();
        StringEntity params = new StringEntity(mapper.writeValueAsString(request));
        httpPost.setEntity(params);
        httpPost.addHeader("content-type", "application/json");
        return httpPost;
    }


    @Test
    public void testSaveInboxCallDetails() throws IOException, InterruptedException {
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, //callingNumber
                rh.airtelOperator(), //operator
                rh.delhiCircle().getName(), //circle
                VALID_CALL_ID, //callId
                123L, //callStartTime
                456L, //callEndTime
                123, //callDurationInPulses
                1, //callStatus
                1, //callDisconnectReason
                new HashSet<>(Arrays.asList(
                        new CallDataRequest(
                                "00000000-0000-0000-0000-000000000000", //subscriptionId
                                "48WeeksPack", //subscriptionPack
                                "123", //inboxWeekId
                                "foo", //contentFileName
                                123L, //startTime
                                456L), //endTime
                        new CallDataRequest(
                                "00000000-0000-0000-0000-000000000001", //subscriptionId
                                "72WeeksPack", //subscriptionPack
                                "123", //inboxWeekId
                                "foo", //contentFileName
                                123L, //startTime
                                456L) //endTime
                )))); //content

        assertTrue(SimpleHttpClient.execHttpRequest(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD));
    }


    @Test
    public void testSaveInboxCallDetailsInvalidParams() throws IOException, InterruptedException {
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, //callingNumber
                rh.airtelOperator(), //operator
                rh.delhiCircle().getName(), //circle
                VALID_CALL_ID, //callId
                123L, //callStartTime
                456L, //callEndTime
                123, //callDurationInPulses
                9, //callStatus
                9, //callDisconnectReason
                null)); //content
        String expectedJsonResponse = createFailureResponseJson("<callStatus: Invalid><callDisconnectReason: Invalid>");

        assertTrue(SimpleHttpClient.execHttpRequest(httpPost, HttpStatus.SC_BAD_REQUEST, expectedJsonResponse,
                ADMIN_USERNAME, ADMIN_PASSWORD));
    }


    @Test
    public void testSaveInboxCallDetailsInvalidContent() throws IOException, InterruptedException {
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, //callingNumber
                rh.airtelOperator(), //operator
                rh.delhiCircle().getName(), //circle
                VALID_CALL_ID, //callId
                123L, //callStartTime
                456L, //callEndTime
                123, //callDurationInPulses
                1, //callStatus
                1, //callDisconnectReason
                new HashSet<>(Arrays.asList(
                        new CallDataRequest(
                                "00000000-0000-0000-0000-000000000000", //subscriptionId
                                "48WeeksPack", //subscriptionPack
                                "123", //inboxWeekId
                                "foo", //contentFileName
                                123L, //startTime
                                456L), //endTime
                        new CallDataRequest(
                                "00000000-0000-0000-0000", //subscriptionId
                                "foobar", //subscriptionPack
                                "123", //inboxWeekId
                                "foo", //contentFileName
                                123L, //startTime
                                456L) //endTime
                )))); //content
        String expectedJsonResponse = createFailureResponseJson(
                "<subscriptionId: Invalid><subscriptionPack: Invalid><content: Invalid>");

        assertTrue(SimpleHttpClient.execHttpRequest(httpPost, HttpStatus.SC_BAD_REQUEST, expectedJsonResponse,
                ADMIN_USERNAME, ADMIN_PASSWORD));
    }

    /**
     * NMS_FT_22 To verify the that Save Inbox call Details API request should succeed with content being saved for both
     * Packs as blank.
     */
    @Test
    public void verifyFT22()
            throws IOException, InterruptedException {
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                3000000000L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                2, // callStatus
                4, // callDisconnectReason
                new HashSet<CallDataRequest>())); // content

        assertTrue(SimpleHttpClient.execHttpRequest(httpPost, HttpStatus.SC_OK,
                ADMIN_USERNAME, ADMIN_PASSWORD));
        // assert inboxCallDetailRecord
        InboxCallDetailRecord inboxCallDetailRecord = inboxCallDetailsDataService
                .retrieve("callingNumber", 3000000000L);
        assertTrue(3000000000L == inboxCallDetailRecord.getCallingNumber());
        assertEquals(VALID_CALL_ID, inboxCallDetailRecord.getCallId());
        assertEquals("A", inboxCallDetailRecord.getOperator());
        assertEquals("AP", inboxCallDetailRecord.getCircle());
        assertTrue(123 == inboxCallDetailRecord.getCallDurationInPulses());
        assertTrue(2 == inboxCallDetailRecord.getCallStatus());
        assertTrue(4 == inboxCallDetailRecord.getCallDisconnectReason());
        assertTrue(123000L == inboxCallDetailRecord.getCallStartTime().getMillis());
        assertTrue(456000L == inboxCallDetailRecord.getCallEndTime().getMillis());

    }


    /**
     * To verify the that Save Inbox call Details API request should succeed with content being saved for both Packs.
     */
    @Test
    public void verifyFT23() throws IOException, InterruptedException {

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber1 = subscriberDataService.findByNumber(1000000000L).get(0);
        Subscription subscription1 = subscriber1.getAllSubscriptions().iterator().next();
        Subscriber subscriber2 = subscriberDataService.findByNumber(2000000000L).get(0);
        Subscription subscription2 = subscriber2.getAllSubscriptions().iterator().next();

        transactionManager.commit(status);

        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                3000000000L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                new HashSet<>(Arrays.asList(
                        new CallDataRequest(subscription1.getSubscriptionId(), // subscriptionId

                                "48WeeksPack", // subscriptionPack
                                "123", // inboxWeekId
                                "foo1.wav", // contentFileName
                                123L, // startTime
                                456L), // endTime
                        new CallDataRequest(subscription2.getSubscriptionId(), // subscriptionId

                                "72WeeksPack", // subscriptionPack
                                "124", // inboxWeekId
                                "foo2.wav", // contentFileName
                                192L, // startTime
                                678L) // endTime
                )))); // content

        assertTrue(SimpleHttpClient.execHttpRequest(httpPost, HttpStatus.SC_OK,
                ADMIN_USERNAME, ADMIN_PASSWORD));
        // assert inboxCallDetailRecord
        InboxCallDetailRecord inboxCallDetailRecord = inboxCallDetailsDataService
                .retrieve("callingNumber", 3000000000L);
        assertTrue(3000000000L == inboxCallDetailRecord.getCallingNumber());
        assertEquals(VALID_CALL_ID, inboxCallDetailRecord.getCallId());
        assertEquals("A", inboxCallDetailRecord.getOperator());
        assertEquals("AP", inboxCallDetailRecord.getCircle());
        assertTrue(123 == inboxCallDetailRecord.getCallDurationInPulses());
        assertTrue(1 == inboxCallDetailRecord.getCallStatus());
        assertTrue(1 == inboxCallDetailRecord.getCallDisconnectReason());
        assertTrue(123000L == inboxCallDetailRecord.getCallStartTime().getMillis());
        assertTrue(456000L == inboxCallDetailRecord.getCallEndTime().getMillis());

        // assert inboxCallData for both packs
        InboxCallData inboxCallData48Pack = inboxCallDataDataService.retrieve(
                "contentFileName", "foo1.wav");
        assertEquals("foo1.wav", inboxCallData48Pack.getContentFileName());
        assertTrue(456000L == inboxCallData48Pack.getEndTime().getMillis());
        assertEquals("123", inboxCallData48Pack.getInboxWeekId());
        assertTrue(123000L == inboxCallData48Pack.getStartTime().getMillis());
        assertEquals(subscription1.getSubscriptionId(),
                inboxCallData48Pack.getSubscriptionId());
        assertEquals("48WeeksPack", inboxCallData48Pack.getSubscriptionPack());

        InboxCallData inboxCallData72Pack = inboxCallDataDataService.retrieve(
                "contentFileName", "foo2.wav");
        assertEquals("foo2.wav", inboxCallData72Pack.getContentFileName());
        assertTrue(678000L == inboxCallData72Pack.getEndTime().getMillis());
        assertEquals("124", inboxCallData72Pack.getInboxWeekId());
        assertTrue(192000L == inboxCallData72Pack.getStartTime().getMillis());
        assertEquals(subscription2.getSubscriptionId(),
                inboxCallData72Pack.getSubscriptionId());
        assertEquals("72WeeksPack", inboxCallData72Pack.getSubscriptionPack());
    }


    /**
     * NMS_FT_77 To check that message should be returned from inbox within days of user's subscription gets completed
     * for Pregnancy Pack.
     */
    @Test
    public void verifyFT77() throws IOException, InterruptedException {

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // 4000000000L subscribed to 72Week Pack subscription
        Subscriber subscriber = subscriberService.getSubscriber(4000000000L).get(0);
        Subscription subscription = subscriber.getSubscriptions().iterator()
                .next();
        // setting the subscription to have ended less than a week ago -- the
        // final message should be returned
        subscription.setStartDate(DateTime.now().minusDays(505));
        subscription.setStatus(SubscriptionStatus.COMPLETED);
        subscriptionDataService.update(subscription);

        transactionManager.commit(status);

        Pattern expectedJsonPattern = Pattern
                .compile(".*\"subscriptionPack\":\"pregnancyPack\",\"inboxWeekId\":\"w72_2\",\"contentFileName\":\"w72_2\\.wav.*");


        HttpGet httpGet = createHttpGet(true, "4000000000", true,
                VALID_CALL_ID);

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        assertTrue(Pattern.matches(expectedJsonPattern.pattern(), EntityUtils.toString(response.getEntity())));

    }


    /**
     * NMS_FT_75 To check that no message should be returned from inbox after 7 days of user's subscription gets
     * completed for Pregnancy Pack.
     */
    @Test
    public void verifyFT75() throws IOException, InterruptedException {

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // 4000000000L subscribed to Pregnancy Pack subscription
        Subscriber subscriber = subscriberService.getSubscriber(4000000000L).get(0);
        Subscription subscription = subscriber.getSubscriptions().iterator()
                .next();
        // setting the subscription to have ended more than a week ago -- no
        // message should be returned
        subscription.setStartDate(DateTime.now().minusDays(512));
        subscription.setStatus(SubscriptionStatus.COMPLETED);
        subscriptionDataService.update(subscription);

        transactionManager.commit(status);

        String expectedJson = "{\"inboxSubscriptionDetailList\":[]}";

        HttpGet httpGet = createHttpGet(true, "4000000000", true,
                VALID_CALL_ID);
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        assertTrue(expectedJson.equals(EntityUtils.toString(response.getEntity()))  );

    }

    /*
     * To verify that Get Inbox Details API request fails if the provided parameter value of callingNumber is blank.
     */
    @Test
    public void verifyFT92() throws IOException, InterruptedException {

        HttpGet httpGet = createHttpGet(true, "", true, VALID_CALL_ID);
        String expectedJsonResponse = createFailureResponseJson("<callingNumber: Not Present>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
        assertTrue(expectedJsonResponse.equals(EntityUtils.toString(response.getEntity())));
    }

    /*
     * To verify that Get Inbox Details API request fails if the provided parameter value of callId is blank.
     */
    @Test
    public void verifyFT93() throws IOException, InterruptedException {

        HttpGet httpGet = createHttpGet(true, "1234567890", true, "");
        String expectedJsonResponse = createFailureResponseJson("<callId: Not Present>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
        assertTrue(expectedJsonResponse.equals(EntityUtils.toString(response.getEntity())));
    }

    /*
     * To check that message for both Packs should be returned from inbox within 7 days of user's subscription gets
     * completed for Pregnancy Pack while user is subscribed for both Packs.
     */
    public void verifyFT78() throws IOException, InterruptedException {
        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setDateOfBirth(DateTime.now());
        subscriberDataService.create(mctsSubscriber);

        // create subscription to child pack
        Subscription childPackSubscription = subscriptionService
                .createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(),
                        sh.childPack(),
                        SubscriptionOrigin.MCTS_IMPORT);
        mctsSubscriber = subscriberDataService.findByNumber(9999911122L).get(0);

        // due to subscription rules detailed in #157, we need to clear out the
        // DOB and set an LMP in order to
        // create a second subscription for this MCTS subscriber
        mctsSubscriber.setDateOfBirth(null);
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now());
        subscriberDataService.update(mctsSubscriber);

        // create subscription to pregnancy pack
        Subscription pregnancyPackSubscription = subscriptionService
                .createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(),
                        sh.pregnancyPack(),
                        SubscriptionOrigin.MCTS_IMPORT);
        // update pregnancy subscription pack to mark complete
        // setting the subscription to have ended less than a week ago -- the
        // final message should be
        // returned
        subscriptionService.updateStartDate(pregnancyPackSubscription, DateTime
                .now().minusDays(505 + 90));

        Pattern childPackJsonPattern = Pattern
                .compile(".*\"subscriptionId\":\""
                        + childPackSubscription.getSubscriptionId()
                        + "\",\"subscriptionPack\":\"childPack\",\"inboxWeekId\":\"w1_1\",\"contentFileName\":\"w1_1.wav.*");
        Pattern pregnancyPackJsonPattern = Pattern
                .compile(".*\"subscriptionId\":\""
                        + pregnancyPackSubscription.getSubscriptionId()
                        + "\",\"subscriptionPack\":\"pregnancyPack\",\"inboxWeekId\":\"w72_2\",\"contentFileName\":\"w72_2.wav.*");

        HttpGet httpGet = createHttpGet(true, "9999911122", true,
                VALID_CALL_ID);
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                childPackJsonPattern, ADMIN_USERNAME, ADMIN_PASSWORD));
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                pregnancyPackJsonPattern, ADMIN_USERNAME, ADMIN_PASSWORD));
    }


    /*
     * To check that only message for Active Pack should be returned from inbox after 7 days of user's subscription gets
     * completed for Pregnancy Pack while user is subscribed to both Packs.
     */
    @Test
    public void verifyFT76() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setDateOfBirth(DateTime.now());
        subscriberDataService.create(mctsSubscriber);

        // create subscription to child pack for which start date is as per DOB
        Subscription childPackSubscription = subscriptionService.createSubscription(
                mctsSubscriber,
                9999911122L,
                rh.hindiLanguage(),
                sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        mctsSubscriber = subscriberDataService.findByNumber(9999911122L).get(0);

        // due to subscription rules detailed in #157, we need to clear out the DOB and set an LMP in order to
        // create a second subscription for this MCTS subscriber
        mctsSubscriber.setDateOfBirth(null);
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now());
        subscriberDataService.update(mctsSubscriber);

        // create subscription to pregnancy pack
        Subscription pregnancyPackSubscription = subscriptionService.createSubscription(
                mctsSubscriber,
                9999911122L,
                rh.hindiLanguage(),
                sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        // update pregnancy subscription pack to mark complete
        // setting the subscription to have ended more than a week ago -- no message should be returned
        subscriptionService.updateStartDate(pregnancyPackSubscription, DateTime
                .now().minusDays(512 + 90));

        transactionManager.commit(status);

        String expectedJsonResponse = "{\"inboxSubscriptionDetailList\":[{\"subscriptionId\":\""
                + childPackSubscription.getSubscriptionId()
                + "\",\"subscriptionPack\":\"childPack\",\"inboxWeekId\":\"w1_1\",\"contentFileName\":\"w1_1.wav\"}]}";

        HttpGet httpGet = createHttpGet(true, "9999911122", true,
                VALID_CALL_ID);
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                expectedJsonResponse, ADMIN_USERNAME, ADMIN_PASSWORD));
    }


    /*
     * To check that messages for both Packs should be returned from inbox within 7 days of user's subscription gets
     * completed for Child Pack while user is subscribed for both Packs.
     */
    @Test
    public void verifyFT82() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setDateOfBirth(DateTime.now());
        subscriberDataService.create(mctsSubscriber);

        // create subscription to child pack
        Subscription childPackSubscription = subscriptionService
                .createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(),
                        sh.childPack(),
                        SubscriptionOrigin.MCTS_IMPORT);

        // update child pack subscription to mark complete
        // setting the subscription to have ended less than a week ago -- the
        // final message should be
        // returned
        subscriptionService.updateStartDate(childPackSubscription, DateTime
                .now().minusDays(337));

        mctsSubscriber = subscriberDataService.findByNumber(9999911122L).get(0);

        // due to subscription rules detailed in #157, we need to clear out the
        // DOB and set an LMP in order to
        // create a second subscription for this MCTS subscriber
        mctsSubscriber.setDateOfBirth(null);
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now().minusDays(91));
        subscriberDataService.update(mctsSubscriber);

        // create subscription to pregnancy pack
        Subscription pregnancyPackSubscription = subscriptionService
                .createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(),
                        sh.pregnancyPack(),
                        SubscriptionOrigin.MCTS_IMPORT);

        transactionManager.commit(status);

        Pattern childPackJsonPattern = Pattern
                .compile(".*\"subscriptionId\":\""
                        + childPackSubscription.getSubscriptionId()
                        + "\",\"subscriptionPack\":\"childPack\",\"inboxWeekId\":\"w48_1\",\"contentFileName\":\"w48_1.wav.*");
        Pattern pregnancyPackJsonPattern = Pattern
                .compile(".*\"subscriptionId\":\""
                        + pregnancyPackSubscription.getSubscriptionId()
                        + "\",\"subscriptionPack\":\"pregnancyPack\",\"inboxWeekId\":\"w1_1\",\"contentFileName\":\"w1_1.wav.*");

        HttpGet httpGet = createHttpGet(true, "9999911122", true,
                VALID_CALL_ID);
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                childPackJsonPattern, ADMIN_USERNAME, ADMIN_PASSWORD));
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                pregnancyPackJsonPattern, ADMIN_USERNAME, ADMIN_PASSWORD));
    }


    /*
     * To check that only message for Active Pack should be returned from inbox after 7 days of user's subscription gets
     * completed for Child Pack while user is subscribed for both Packs.
     */
    @Test
    public void verifyFT80() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setDateOfBirth(DateTime.now());
        subscriberDataService.create(mctsSubscriber);

        // create subscription to child pack
        Subscription childPackSubscription = subscriptionService
                .createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(),
                        sh.childPack(),
                        SubscriptionOrigin.MCTS_IMPORT);

        // update child Pack Subscription to mark complete
        // setting the subscription to have ended more than a week ago -- no
        // message should be returned
        subscriptionService.updateStartDate(childPackSubscription, DateTime
                .now().minusDays(344));

        mctsSubscriber = subscriberDataService.findByNumber(9999911122L).get(0);

        // due to subscription rules detailed in #157, we need to clear out the
        // DOB and set an LMP in order to
        // create a second subscription for this MCTS subscriber
        mctsSubscriber.setDateOfBirth(null);
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now().minusDays(91));
        subscriberDataService.update(mctsSubscriber);

        // create subscription to pregnancy pack for which start date is
        // yesterday
        // date as per LMP
        Subscription pregnancyPackSubscription = subscriptionService
                .createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(),
                        sh.pregnancyPack(),
                        SubscriptionOrigin.MCTS_IMPORT);

        transactionManager.commit(status);

        String expectedJsonResponse = "{\"inboxSubscriptionDetailList\":[{\"subscriptionId\":\""
                + pregnancyPackSubscription.getSubscriptionId()
                + "\",\"subscriptionPack\":\"pregnancyPack\",\"inboxWeekId\":\"w1_1\",\"contentFileName\":\"w1_1.wav\"}]}";

        HttpGet httpGet = createHttpGet(true, "9999911122", true,
                VALID_CALL_ID);
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                expectedJsonResponse, ADMIN_USERNAME, ADMIN_PASSWORD));
    }

    /*
     * To verify the behavior of Get Inbox Details API if provided beneficiary's callingNumber is less than 10 digits.
     */
    @Test
    public void verifyFT83() throws IOException, InterruptedException {

    	HttpGet httpGet = createHttpGet(true, "123456789", true, VALID_CALL_ID);
    	String expectedJsonResponse = createFailureResponseJson("<callingNumber: Invalid>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
        assertTrue(expectedJsonResponse.equals(EntityUtils.toString(response.getEntity()))  );
    }


    /*
     * To verify the behavior of  Get Inbox Details API  if provided beneficiary's callingNumber is more than 10 digits.
     */
    @Test
    public void verifyFT84() throws IOException, InterruptedException {
        HttpGet httpGet = createHttpGet(true, "12345678901", true, VALID_CALL_ID);
        String expectedJsonResponse = createFailureResponseJson("<callingNumber: Invalid>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
        assertTrue(expectedJsonResponse.equals(EntityUtils.toString(response.getEntity()))  );
    }

    /**
     * To verify the behavior of Save Inbox call Details API if provided beneficiary's subscriptionPack does not exist.
     */
    @Test
    public void verifyFT36() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberDataService.create(new Subscriber(
                3000000000L));
        Subscription subscription1 = subscriptionService.createSubscription(
                subscriber, subscriber.getCallingNumber(), rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.IVR);
        Subscription subscription2 = subscriptionService.createSubscription(
                subscriber, subscriber.getCallingNumber(), rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.IVR);

        transactionManager.commit(status);

        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                new HashSet<>(Arrays.asList(
                        new CallDataRequest(
                                subscription1.getSubscriptionId(), // subscriptionId
                                sh.childPack().getName(), // subscriptionPack
                                "123", // inboxWeekId
                                "foo", // contentFileName
                                123L, // startTime
                                456L), // endTime
                        new CallDataRequest(
                                subscription2.getSubscriptionId(), // subscriptionId
                                "12WeeksPack", // Invalid subscriptionPack
                                "123", // inboxWeekId
                                "foo", // contentFileName
                                123L, // startTime
                                456L) // endTime
                )))); // content
        String expectedJsonResponse = createFailureResponseJson("<subscriptionPack: Invalid><content: Invalid>");
        assertTrue(SimpleHttpClient.execHttpRequest(httpPost,
                HttpStatus.SC_BAD_REQUEST, expectedJsonResponse,
                ADMIN_USERNAME, ADMIN_PASSWORD));
    }


    /**
     * To verify that Save Inbox call Details API request succeeds if specified subscription doesn't exist for
     * beneficiary.
     */
    @Test
    //https://applab.atlassian.net/browse/NMS-178
    public void verifyFT185() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // subscribed caller with deactivated subscription i.e no active and
        // pending subscriptions
        Subscriber subscriber = subscriberDataService.create(new Subscriber(3000000000L));
        Subscription subscription1 = subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(),
                rh.hindiLanguage(), sh.childPack(), SubscriptionOrigin.IVR);
        // deactivate subscription
        subscriptionService.deactivateSubscription(subscription1, DeactivationReason.DEACTIVATED_BY_USER);

        transactionManager.commit(status);

        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                3000000000L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                new HashSet<>(Arrays.asList(
                        new CallDataRequest(subscription1.getSubscriptionId(), // subscriptionId
                                // refer
                                // deactivated
                                // subscription
                                "48WeeksPack", // subscriptionPack
                                "123", // inboxWeekId
                                "foo", // contentFileName
                                123L, // startTime
                                456L), // endTime
                        new CallDataRequest(
                                "ae7681ae-1f3c-4dba-365d-4b26e19f4335", // subscriptionId
                                // not
                                // exist
                                "72WeeksPack", // subscriptionPack
                                "123", // inboxWeekId
                                "foo", // contentFileName
                                123L, // startTime
                                456L) // endTime
                )))); // content

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
    }


    /*
     * To verify the behavior of Get Inbox Details API if provided beneficiary's callId is not valid: more than 15 digits.
     */
    @Test
    public void verifyFT85() throws IOException, InterruptedException {

        // callingNumber alphanumeric
        HttpGet httpGet = createHttpGet(true, "12345DF7890", true, VALID_CALL_ID);

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    /**
     * To verify the that Save Inbox call Details API request should not succeed with content provided for only 1
     * subscription pack and not even a place holder for second Pack details
     *
     * //TODO : FT case to be modified .Refer NMS -182
     */
    @Test
    public void verifyFT35() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // subscriber subscribed to both pack
        Subscriber subscriber = subscriberDataService.create(new Subscriber(3000000000L));
        Subscription subscription = subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(),
                rh.hindiLanguage(), sh.childPack(), SubscriptionOrigin.IVR);
        subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(), rh.hindiLanguage(),
                sh.pregnancyPack(), SubscriptionOrigin.IVR);

        transactionManager.commit(status);

        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                3000000000L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                new HashSet<>(Arrays.asList(new CallDataRequest(subscription
                        .getSubscriptionId(), // subscriptionId
                        "48WeeksPack", // subscriptionPack
                        "123", // inboxWeekId
                        "foo1.wav", // contentFileName
                        123L, // startTime
                        456L))))); // content



        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine()
                .getStatusCode());
        // assert inboxCallDetailRecord
        InboxCallDetailRecord inboxCallDetailRecord = inboxCallDetailsDataService
                .retrieve("callingNumber", 3000000000L);
        assertNotNull(inboxCallDetailRecord);
    }


    /**
     * To verify that Save Inbox call Details API request should succeed with content provided for only 1
     * subscription pack
     */
    @Test
    public void verifyFT186() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberDataService.create(new Subscriber(3000000000L));
        Subscription subscription = subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(),
                rh.hindiLanguage(), sh.childPack(), SubscriptionOrigin.IVR);
        subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(), rh.hindiLanguage(),
                sh.pregnancyPack(), SubscriptionOrigin.IVR);

        transactionManager.commit(status);

        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                3000000000L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                new HashSet<>(Arrays.asList(
                        new CallDataRequest(subscription.getSubscriptionId(), // subscriptionId
                                "48WeeksPack", // subscriptionPack
                                "123", // inboxWeekId
                                "foo1.wav", // contentFileName
                                123L, // startTime
                                456L)))));

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

        // assert inboxCallDetailRecord
        InboxCallDetailRecord inboxCallDetailRecord = inboxCallDetailsDataService
                .retrieve("callingNumber", 3000000000L);
        assertTrue(3000000000L == inboxCallDetailRecord.getCallingNumber());
        assertEquals(VALID_CALL_ID, inboxCallDetailRecord.getCallId());
        assertEquals("A", inboxCallDetailRecord.getOperator());
        assertEquals("AP", inboxCallDetailRecord.getCircle());
        assertTrue(123 == inboxCallDetailRecord.getCallDurationInPulses());
        assertTrue(1 == inboxCallDetailRecord.getCallStatus());
        assertTrue(1 == inboxCallDetailRecord.getCallDisconnectReason());
        assertEquals(new DateTime(123000L), inboxCallDetailRecord.getCallStartTime());
        assertEquals(new DateTime(456000L), inboxCallDetailRecord.getCallEndTime());

        // assert inboxCallData for 48WeeksPack
        InboxCallData inboxCallData48Pack = inboxCallDataDataService.retrieve("contentFileName", "foo1.wav");
        assertEquals("foo1.wav", inboxCallData48Pack.getContentFileName());
        assertEquals(new DateTime(456000L), inboxCallData48Pack.getEndTime());
        assertEquals("123", inboxCallData48Pack.getInboxWeekId());
        assertEquals(new DateTime(123000L), inboxCallData48Pack.getStartTime());
        assertEquals(subscription.getSubscriptionId(), inboxCallData48Pack.getSubscriptionId());
        assertEquals("48WeeksPack", inboxCallData48Pack.getSubscriptionPack());
    }


    /**
     * To verify that Save Inbox call Details API request should gracefully fail with content provided for 2
     * subscription packs with one valid and one null
     */
    @Test
    public void verifyFT186_2() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberDataService.create(new Subscriber(3000000000L));
        Subscription subscription = subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(),
                rh.hindiLanguage(), sh.childPack(), SubscriptionOrigin.IVR);
        subscriptionService.createSubscription(subscriber, subscriber.getCallingNumber(), rh.hindiLanguage(),
                sh.pregnancyPack(), SubscriptionOrigin.IVR);

        transactionManager.commit(status);

        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                3000000000L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                new HashSet<>(Arrays.asList(
                        new CallDataRequest(subscription.getSubscriptionId(), // subscriptionId
                                "48WeeksPack", // subscriptionPack
                                "123", // inboxWeekId
                                "foo1.wav", // contentFileName
                                123L, // startTime
                                456L),
                        null)))); // place holder for pack2

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }


    /**
     * This method is a utility method for running the test cases.
     */
    private HttpGet createGetSubscriberDetailsRequest(String callingNumber, String operator, String circle,
                                                      String callId) {

        StringBuilder sb = new StringBuilder(String.format(
                "http://localhost:%d/api/kilkari/user?",
                TestContext.getJettyPort()));
        String sep = "";
        if (callingNumber != null) {
            sb.append(String.format("callingNumber=%s", callingNumber));
            sep = "&";
        }
        if (operator != null) {
            sb.append(String.format("%soperator=%s", sep, operator));
            sep = "&";
        }
        if (circle != null) {
            sb.append(String.format("%scircle=%s", sep, circle));
            sep = "&";
        }
        if (callId != null) {
            sb.append(String.format("%scallId=%s", sep, callId));
            sep = "&";
        }

        return new HttpGet(sb.toString());
    }


    /**
     * test GetSubscriberDetails API with Blank Params
     */
    @Test
    public void verifyFT17() throws IOException, InterruptedException {
        HttpGet httpGet = createGetSubscriberDetailsRequest("", // callingNumber
                // Blank
                "A", // operator
                "AP", // circle
                VALID_CALL_ID // callId
        );

        String expectedJsonResponse = createFailureResponseJson("<callingNumber: Not Present>");

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet,
                HttpStatus.SC_BAD_REQUEST, expectedJsonResponse,
                ADMIN_USERNAME, ADMIN_PASSWORD));
    }


    /**
     * test GetSubscriberDetails API with Blank Params
     */
    @Test
    public void verifyFT18() throws IOException, InterruptedException {
        HttpGet httpGet = createGetSubscriberDetailsRequest("1234567890", // callingNumber
                "", // operator Blank(optional param)
                "AP", // circle
                VALID_CALL_ID // callId
        );

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine()
                .getStatusCode());
    }


    /**
     * test GetSubscriberDetails API with Blank Params
     */
    @Test
    public void verifyFT19() throws IOException, InterruptedException {
        HttpGet httpGet = createGetSubscriberDetailsRequest("1234567890", // callingNumber
                "A", // operator
                "", // circle Blank (optional param)
                VALID_CALL_ID // callId
        );

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine()
                .getStatusCode());
    }


    /**
     * test GetSubscriberDetails API with Blank Params
     */
    @Test
    public void verifyFT20() throws IOException, InterruptedException {
        HttpGet httpGet = createGetSubscriberDetailsRequest("1234567890", // callingNumber
                "A", // operator
                "AP", // circle
                "" // callId Blank
        );

        String expectedJsonResponse = createFailureResponseJson("<callId: Not Present>");

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet,
                HttpStatus.SC_BAD_REQUEST, expectedJsonResponse,
                ADMIN_USERNAME, ADMIN_PASSWORD));
    }

    /**
     * To check NMS is able to make available a single message of current week in inbox with single message per week
     * configuration, when:
     *  (a) user's MSISDN is subscribed for Pregnancy Pack.
     *  (b) user's MSISDN is deactivated for an old subscription of Pregnancy Pack.
     */
    @Test
    public void verifyFT109() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // setup data to remove 2 messages per week configuration for Pregnancy pack
        testingService.clearDatabase();

        deployedServiceDataService.create(new DeployedService(rh.delhiState(), Service.KILKARI));

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setDateOfBirth(null);

        // set LMP for old pack
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now().minusDays(180));
        subscriberDataService.create(mctsSubscriber);

        // create old subscription for pregnancy pack and deactivate it
        Subscription oldSubscription = subscriptionService.createSubscription(
                mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(1), SubscriptionOrigin.MCTS_IMPORT);
        subscriptionService.deactivateSubscription(oldSubscription, DeactivationReason.DEACTIVATED_BY_USER);

        mctsSubscriber = subscriberDataService.findByNumber(9999911122L).get(0);

        // create new subscription for pregnancy pack in Active state such that next OBD date falls on current date
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now().minusDays(90));
        subscriberDataService.update(mctsSubscriber);
        Subscription newSubscription = subscriptionService.createSubscription(
                mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(1), SubscriptionOrigin.MCTS_IMPORT);

        transactionManager.commit(status);

        String expectedJsonResponse = "{\"inboxSubscriptionDetailList\":[{\"subscriptionId\":\""
                + newSubscription.getSubscriptionId()
                + "\",\"subscriptionPack\":\"pregnancyPack\",\"inboxWeekId\":\"w1_1\",\"contentFileName\":\"w1_1.wav\"}]}";

        HttpGet httpGet = createHttpGet(true, "9999911122", true, VALID_CALL_ID);
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                expectedJsonResponse, ADMIN_USERNAME, ADMIN_PASSWORD));
    }

    /**
     * To check NMS is able to make available a single message of current week in inbox with single message per week
     * configuration, when:
     *  (a) user's MSISDN is subscribed for Pregnancy Pack.
     *  (b) user's MSISDN status is completed for an old subscription of Pregnancy Pack.
     */
    @Test
    public void verifyFT110() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // setup data to remove 2 messages per week configuration for Pregnancy pack
        testingService.clearDatabase();

        deployedServiceDataService.create(new DeployedService(rh.delhiState(), Service.KILKARI));

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setDateOfBirth(null);

        // set LMP for old pack
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now().minusDays(180));
        subscriberDataService.create(mctsSubscriber);

        // create old subscription for pregnancy pack
        Subscription oldSubscription = subscriptionService.createSubscription(
                mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(1), SubscriptionOrigin.MCTS_IMPORT);

        // update old pregnancy subscription pack to complete, setting the subscription to have ended > a week ago
        subscriptionService.updateStartDate(oldSubscription, DateTime.now().minusDays(512 + 90));
        oldSubscription.setNeedsWelcomeMessageViaObd(false);

        mctsSubscriber = subscriberDataService.findByNumber(9999911122L).get(0);

        // create new subscription to pregnancy pack in Active state such that next OBD date falls on current date
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now().minusDays(90));
        subscriberDataService.update(mctsSubscriber);
        Subscription newSubscription = subscriptionService.createSubscription(
                mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(1), SubscriptionOrigin.MCTS_IMPORT);

        transactionManager.commit(status);

        String expectedJsonResponse = "{\"inboxSubscriptionDetailList\":[{\"subscriptionId\":\""
                + newSubscription.getSubscriptionId()
                + "\",\"subscriptionPack\":\"pregnancyPack\",\"inboxWeekId\":\"w1_1\",\"contentFileName\":\"w1_1.wav\"}]}";

        HttpGet httpGet = createHttpGet(true, "9999911122", true, VALID_CALL_ID);
        String responseBody = EntityUtils.toString(SimpleHttpClient.httpRequestAndResponse(
                httpGet, ADMIN_USERNAME, ADMIN_PASSWORD).getEntity());
        assertEquals(responseBody, expectedJsonResponse);
    }

    // This method is a utility method for running the test cases. this is
    // already
    // used in the branch NMS.FT.24.25.26
    private HttpPost createInboxCallDetailsRequestHttpPost(
            String callingNumber, String operator, String circle,
            String callId, String callStartTime, String callEndTime,
            String callDurationInPulses, String callStatus,
            String callDisconnectReason) throws IOException {
        HttpPost httpPost = new HttpPost(String.format(
                "http://localhost:%d/api/kilkari/inboxCallDetails",
                TestContext.getJettyPort()));

        StringBuilder sb = new StringBuilder();
        String seperator = "";
        sb.append("{");
        if (callingNumber != null) {
            sb.append(String.format("%s\"callingNumber\": %s", seperator,
                    callingNumber));
            seperator = ",";
        }
        if (operator != null) {
            sb.append(String.format("%s\"operator\": \"%s\"", seperator,
                    operator));
            seperator = ",";
        }
        if (circle != null) {
            sb.append(String.format("%s\"circle\": \"%s\"", seperator, circle));
            seperator = ",";
        }
        if (callId != null) {
            sb.append(String.format("%s\"callId\": %s", seperator, callId));
            seperator = ",";
        }
        if (callStartTime != null) {
            sb.append(String.format("%s\"callStartTime\": %s", seperator,
                    callStartTime));
            seperator = ",";
        }
        if (callEndTime != null) {
            sb.append(String.format("%s\"callEndTime\": %s", seperator,
                    callEndTime));
            seperator = ",";
        }
        if (callDurationInPulses != null) {
            sb.append(String.format("%s\"callDurationInPulses\": %s",
                    seperator, callDurationInPulses));
            seperator = ",";
        }
        if (callStatus != null) {
            sb.append(String.format("%s\"callStatus\": %s", seperator,
                    callStatus));
            seperator = ",";
        }
        if (callDisconnectReason != null) {
            sb.append(String.format("%s\"callDisconnectReason\": %s",
                    seperator, callDisconnectReason));
            seperator = ",";
        }

        sb.append("}");

        StringEntity params = new StringEntity(sb.toString());
        httpPost.setEntity(params);
        httpPost.addHeader("content-type", "application/json");
        return httpPost;
    }

    /**
     * To verify the behavior of Save Inbox call Details API if provided
     * beneficiary's callId is not valid : less than 25 digits.
     */
    @Test
    public void verifyFT27() throws IOException, InterruptedException {
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID.substring(1), // callId less than 25
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                null)); // content
        String expectedJsonResponse = createFailureResponseJson("<callId: Invalid>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
    }

    /**
     * To verify the behavior of Save Inbox call Details API if provided
     * beneficiary's callId is not valid : more than 25 digits.
     */
    @Test
    public void verifyFT28() throws IOException, InterruptedException {
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID + "12", // callId more than 25 digit
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                null)); // content
        String expectedJsonResponse = createFailureResponseJson("<callId: Invalid>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
    }

    /**
     * To verify the behavior of Save Inbox call Details API if provided
     * beneficiary's callId is not valid : Alphanumeric value.
     */
    @Test
    public void verifyFT29() throws IOException, InterruptedException {
        // callId alpha numeric
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost("1234567890", // callingNumber
                "A", // operator
                "AP", // circle
                "123456789A12345", // callId alphanumeric
                "123", // callStartTime
                "456", // callEndTime
                "123", // callDurationInPulses
                "1", // callStatus
                "1" // callDisconnectReason
        );

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    /**
     * To check anonymous user is able to access Kilkari with multiple states in
     * user's circle, given that service is deployed in at least one of these
     * states
     */
    @Test
    public void verifyFT124() throws IOException, InterruptedException {
        // setup state1 data
        Language language1 = new Language("Ur", "urdu");
        languageDataService.create(language1);

        // create circle and add states to it
        Circle circle = new Circle("NR");
        circleDataService.create(circle);

        District district1 = new District();
        district1.setName("Lucknow");
        district1.setRegionalName("Lucknow");
        district1.setLanguage(language1);
        district1.setCode(11L);
        district1.setCircle(circle);
        circle.getDistricts().add(district1);

        State state1 = new State();
        state1.setName("UP");
        state1.setCode(11L);
        state1.getDistricts().add(district1);

        stateDataService.create(state1);

        // setup state2 data
        Language language2 = new Language("Br", "bhojpuri");
        languageDataService.create(language2);

        District district2 = new District();
        district2.setName("Bhopal");
        district2.setRegionalName("Bhopal");
        district2.setLanguage(language2);
        district2.setCode(21L);
        district2.setCircle(circle);
        circle.getDistricts().add(district2);

        State state2 = new State();
        state2.setName("MP");
        state2.setCode(21L);
        state2.getDistricts().add(district2);

        stateDataService.create(state2);

        // deployed KILKARI service for state1 only
        deployedServiceDataService.create(new DeployedService(state1,
                Service.KILKARI));

        Language language3 = new Language("RJ", "Rajasthai");
        languageDataService.create(language3);

        State s = createState(7L, "New State in delhi");
        stateDataService.create(s);
        districtDataService.create(createDistrict(s, 1L, "Circle", circle));

        // setup create subscription request
        SubscriptionRequest subscriptionRequest = new SubscriptionRequest(
                9999911122L, rh.airtelOperator(), circle.getName(),
                VALID_CALL_ID, language3.getCode(),
                sh.childPack().getName());
        ObjectMapper mapper = new ObjectMapper();
        String subscriptionRequestJson = mapper
                .writeValueAsString(subscriptionRequest);
        HttpPost httpPost = new HttpPost(String.format(
                "http://localhost:%d/api/kilkari/subscription",
                TestContext.getJettyPort()));
        httpPost.setHeader("Content-type", "application/json");
        httpPost.setEntity(new StringEntity(subscriptionRequestJson));

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberDataService
                .findByNumber(9999911122L).get(0);
        assertNotNull(subscriber);
        assertNotNull(subscriber.getSubscriptions());

        transactionManager.commit(status);
    }

    /**
     * To check anonymous user is not able to access Kilkari with multiple
     * states in user's circle and service is not deployed in at least one of
     * these states.
     * //TODO : FT doc needs correction.
     */
    @Test
    public void verifyFT126() throws IOException, InterruptedException {
        // setup state1 data
        Language language1 = new Language("Ur", "urdu");
        languageDataService.create(language1);

        // create circle and add states to it
        Circle circle = new Circle("NR");
        circleDataService.create(circle);

        District district1 = new District();
        district1.setName("Lucknow");
        district1.setRegionalName("Lucknow");
        district1.setLanguage(language1);
        district1.setCode(11L);
        district1.setCircle(circle);
        circle.getDistricts().add(district1);

        State state1 = new State();
        state1.setName("UP");
        state1.setCode(11L);
        state1.getDistricts().add(district1);

        stateDataService.create(state1);

        // setup state2 data
        Language language2 = new Language("Br", "bhojpuri");
        languageDataService.create(language2);

        District district2 = new District();
        district2.setName("Bhopal");
        district2.setRegionalName("Bhopal");
        district2.setLanguage(language2);
        district2.setCode(21L);
        district2.setCircle(circle);
        circle.getDistricts().add(district2);

        State state2 = new State();
        state2.setName("MP");
        state2.setCode(21L);
        state2.getDistricts().add(district2);

        stateDataService.create(state2);

        // Not deployed KILKARI service for state1 and state2

        Language language3 = new Language("RJ", "Rajasthai");
        languageDataService.create(language3);


        // setup create subscription request
        SubscriptionRequest subscriptionRequest = new SubscriptionRequest(
                9999911122L, rh.airtelOperator(), circle.getName(),
                VALID_CALL_ID, language3.getCode(), sh.childPack().getName());
        ObjectMapper mapper = new ObjectMapper();
        String subscriptionRequestJson = mapper
                .writeValueAsString(subscriptionRequest);
        HttpPost httpPost = new HttpPost(String.format(
                "http://localhost:%d/api/kilkari/subscription",
                TestContext.getJettyPort()));
        httpPost.setHeader("Content-type", "application/json");
        httpPost.setEntity(new StringEntity(subscriptionRequestJson));


        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine()
                .getStatusCode());
        Subscriber subscriber = subscriberDataService
                .findByNumber(9999911122L).get(0);
        assertNotNull(subscriber);

    }

     /* NMS_FT_21 To verify the that Save Inbox call Details API request should
     * succeed for unsubscribed caller or caller with no active subscription
     * without any content being saved.
     */
    @Test
    public void verifyFT21() throws IOException, InterruptedException {
        // Test for unsubscribed caller
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567899L, // unsubscribed callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                2, // callStatus
                4, // callDisconnectReason
                null)); // no content

        assertTrue(SimpleHttpClient.execHttpRequest(httpPost, HttpStatus.SC_OK,
                ADMIN_USERNAME, ADMIN_PASSWORD));
        // assert inboxCallDetailRecord
        InboxCallDetailRecord inboxCallDetailRecord = inboxCallDetailsDataService
                .retrieve("callingNumber", 1234567899L);
        assertTrue(1234567899L == inboxCallDetailRecord.getCallingNumber());
        assertEquals(VALID_CALL_ID, inboxCallDetailRecord.getCallId());
        assertEquals("A", inboxCallDetailRecord.getOperator());
        assertEquals("AP", inboxCallDetailRecord.getCircle());
        assertTrue(123 == inboxCallDetailRecord.getCallDurationInPulses());
        assertTrue(2 == inboxCallDetailRecord.getCallStatus());
        assertTrue(4 == inboxCallDetailRecord.getCallDisconnectReason());
        assertTrue(123000L == inboxCallDetailRecord.getCallStartTime().getMillis());
        assertTrue(456000L == inboxCallDetailRecord.getCallEndTime().getMillis());

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // subscribed caller with deactivated subscription i.e no active and
        // pending subscriptions
        Subscriber subscriber = subscriberDataService.create(new Subscriber(5000000000L));
        Subscription subscription1 = subscriptionService.createSubscription(
                subscriber, subscriber.getCallingNumber(), rh.hindiLanguage(),
                sh.childPack(), SubscriptionOrigin.IVR);
        subscriptionService.deactivateSubscription(subscription1,
                DeactivationReason.DEACTIVATED_BY_USER);

        transactionManager.commit(status);

        httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                5000000000L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                3, // callStatus
                5, // callDisconnectReason
                null)); // no content

        assertTrue(SimpleHttpClient.execHttpRequest(httpPost, HttpStatus.SC_OK,
                ADMIN_USERNAME, ADMIN_PASSWORD));
        // assert inboxCallDetailRecord
        InboxCallDetailRecord inboxCallDetailRecord2 = inboxCallDetailsDataService
                .retrieve("callingNumber", 5000000000L);
        assertEquals(5000000000L,
                (long) inboxCallDetailRecord2.getCallingNumber());
        assertEquals(VALID_CALL_ID, inboxCallDetailRecord2.getCallId());
        assertEquals("A", inboxCallDetailRecord2.getOperator());
        assertEquals("AP", inboxCallDetailRecord2.getCircle());
        assertEquals(123,
                (int) inboxCallDetailRecord2.getCallDurationInPulses());
        assertEquals(3, (int) inboxCallDetailRecord2.getCallStatus());
        assertEquals(5, (int) inboxCallDetailRecord2.getCallDisconnectReason());
        assertEquals(123000L, (long) inboxCallDetailRecord2.getCallStartTime()
                .getMillis());
        assertEquals(456000L, (long) inboxCallDetailRecord2.getCallEndTime()
                .getMillis());
    }


    /**
     * To verify the behavior of Get Subscriber Details API if a mandatory
     * parameter : callingNumber is missing from the API request.
     */
    @Test
    public void verifyFT12() throws IOException, InterruptedException {
        HttpGet httpGet = createGetSubscriberDetailsRequest(null, // callingNumber
                // missing
                "A", // operator
                "AP", // circle
                VALID_CALL_ID // callId
        );

        String expectedJsonResponse = createFailureResponseJson("<callingNumber: Not Present>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Get Subscriber Details API if a mandatory
     * parameter : operator is missing from the API request.
     */
    @Test
    public void verifyFT13() throws IOException, InterruptedException {
        /**
         * test GetSubscriberDetails API with operator Missing.. operator is
         * treated as optional with this API spec update
         * https://github.com/motech-implementations/mim/issues/287
         */
        HttpGet httpGet = createGetSubscriberDetailsRequest("1234567890", // callingNumber
                null, // operator missing
                "AP", // circle
                VALID_CALL_ID // callId more than 15 digits
        );

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
    }

    /**
     * To verify the behavior of Get Subscriber Details API if a mandatory
     * parameter : Circle is missing from the API request.
     */
    @Test
    public void verifyFT14() throws IOException, InterruptedException {
        /**
         * test GetSubscriberDetails API with circle Missing.. circle is treated
         * as optional with this API spec update
         * https://github.com/motech-implementations/mim/issues/287
         */
        HttpGet httpGet = createGetSubscriberDetailsRequest("1234567890", // callingNumber
                "A", // operator
                null, // circle missing
                VALID_CALL_ID // callId
        );

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                ADMIN_USERNAME, ADMIN_PASSWORD));
    }

    /**
     * To verify the behavior of Get Subscriber Details API if a mandatory
     * parameter : callId is missing from the API request.
     */
    @Test
    public void verifyFT15() throws IOException, InterruptedException {
        HttpGet httpGet = createGetSubscriberDetailsRequest("1234567890", // callingNumber
                "A", // operator
                "AP", // circle
                null // callId missing
        );

        String expectedJsonResponse = createFailureResponseJson("<callId: Not Present>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));

    }


    // This method is a utility method for running the test cases. this is
    // already
    // used in the branch NMS.FT.95.96.97
    private HttpDeleteWithBody createDeactivateSubscriptionHttpDelete(
            String calledNumber, String operator, String circle, String callId,
            String subscriptionId) throws IOException {

        StringBuilder sb = new StringBuilder();
        String seperator = "";
        sb.append("{");
        if (calledNumber != null) {
            sb.append(String.format("%s\"calledNumber\": %s", seperator,
                    calledNumber));
            seperator = ",";
        }
        if (operator != null) {
            sb.append(String.format("%s\"operator\": \"%s\"", seperator,
                    operator));
            seperator = ",";
        }
        if (circle != null) {
            sb.append(String.format("%s\"circle\": \"%s\"", seperator, circle));
            seperator = ",";
        }
        if (callId != null) {
            sb.append(String.format("%s\"callId\": %s", seperator, callId));
            seperator = ",";
        }
        if (subscriptionId != null) {
            sb.append(String.format("%s\"subscriptionId\": \"%s\"", seperator,
                    subscriptionId));
            seperator = ",";
        }

        sb.append("}");

        HttpDeleteWithBody httpDelete = new HttpDeleteWithBody(String.format(
                "http://localhost:%d/api/kilkari/subscription",
                TestContext.getJettyPort()));
        httpDelete.setHeader("Content-type", "application/json");
        httpDelete.setEntity(new StringEntity(sb.toString()));
        return httpDelete;
    }

    /**
     * To verify the behavior of Deactivate Subscription Request API if provided
     * beneficiary's callId is not valid : less than 15 digits.
     */
    @Test
    public void verifyFT98() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberService.getSubscriber(1000000000L).get(0);
        Subscription subscription = subscriber.getActiveAndPendingSubscriptions()
                .iterator().next();
        String subscriptionId = subscription.getSubscriptionId();

        transactionManager.commit(status);

        // callId less than 15 digits
        HttpDeleteWithBody httpDelete = createDeactivateSubscriptionHttpDelete(
                "1000000000", "A", "AP", "12345678901234", subscriptionId);
        String expectedJsonResponse = createFailureResponseJson("<callId: Invalid>");
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpDelete, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Deactivate Subscription Request API if provided
     * beneficiary's callId is not valid : more than 15 digits.
     */
    @Test
    public void verifyFT99() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberService.getSubscriber(1000000000L).get(0);
        Subscription subscription = subscriber.getActiveAndPendingSubscriptions()
                .iterator().next();
        String subscriptionId = subscription.getSubscriptionId();

        transactionManager.commit(status);

        // callId more than 15 digits
        HttpDeleteWithBody httpDelete = createDeactivateSubscriptionHttpDelete(
                "1000000000", "A", "AP", "1234567890123455", subscriptionId);
        String expectedJsonResponse = createFailureResponseJson("<callId: Invalid>");
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpDelete, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Deactivate Subscription Request API if provided
     * beneficiary's callId is not valid : Alphanumeric value.
     */
    @Test
    public void verifyFT100() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberService.getSubscriber(1000000000L).get(0);
        Subscription subscription = subscriber.getActiveAndPendingSubscriptions().iterator().next();
        String subscriptionId = subscription.getSubscriptionId();

        transactionManager.commit(status);

        // callId alphanumeric
        HttpDeleteWithBody httpDelete = createDeactivateSubscriptionHttpDelete(
                "1000000000", "A", "AP", "12345RF89012345", subscriptionId);

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpDelete, ADMIN_USERNAME,
                ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }


    /**
     * To verify Deactivate Subscription Request API fails if provided
     * calledNumber has invalid value : less than 10 digits.
     */
    @Test
    public void verifyFT95() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberService.getSubscriber(1000000000L).get(0);
        Subscription subscription = subscriber.getActiveAndPendingSubscriptions()
                .iterator().next();
        String subscriptionId = subscription.getSubscriptionId();

        transactionManager.commit(status);

        // callingNumber less than 10 digits
        HttpDeleteWithBody httpDelete = createDeactivateSubscriptionHttpDelete(
                "100000000", "A", "AP", VALID_CALL_ID, subscriptionId);
        String expectedJsonResponse = createFailureResponseJson("<callingNumber: Invalid>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpDelete, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
    }

    /**
     * To verify Deactivate Subscription Request API fails if provided
     * calledNumber has invalid value : more than 10 digits.
     */
    @Test
    public void verifyFT96() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberService.getSubscriber(1000000000L).get(0);
        Subscription subscription = subscriber.getActiveAndPendingSubscriptions()
                .iterator().next();
        String subscriptionId = subscription.getSubscriptionId();
        String expectedJsonResponse = createFailureResponseJson("<callingNumber: Invalid>");

        transactionManager.commit(status);

        // callingNumber more than 10 digits
        HttpDeleteWithBody httpDelete = createDeactivateSubscriptionHttpDelete(
                "12345678901", "A", "AP", VALID_CALL_ID, subscriptionId);

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpDelete, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
    }

    /**
     * To verify the behavior of Deactivate Subscription Request API if provided
     * Subscriber's callingNumber is not valid : Alphanumeric value.
     */
    @Test
    public void verifyFT97() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberService.getSubscriber(1000000000L).get(0);
        Subscription subscription = subscriber.getActiveAndPendingSubscriptions().iterator().next();
        String subscriptionId = subscription.getSubscriptionId();

        transactionManager.commit(status);

        // callingNumber alphanumeric
        HttpDeleteWithBody httpDelete = createDeactivateSubscriptionHttpDelete(
                "1234DE678901", "A", "AP", VALID_CALL_ID, subscriptionId);

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpDelete, ADMIN_USERNAME,
                ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    //To verify the behavior of  Deactivate Subscription Request API  if a mandatory parameter :
    // calledNumber is missing from the API request.

    @Test
    public void verifyFT101() throws IOException, InterruptedException {

        // calledNumber missing
        HttpDeleteWithBody httpDelete = createDeactivateSubscriptionHttpDelete(
                null, "A", "AP", VALID_CALL_ID,
                "77f13128-037e-4f98-8651-285fa618d94a");
        String expectedJsonResponse = createFailureResponseJson("<callingNumber: Not Present>");
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpDelete, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    //To verify the behavior of  Deactivate Subscription Request API  if a mandatory parameter :
    // operator is missing from the API request.

    @Test
    public void verifyFT102() throws IOException, InterruptedException {

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // operator missing
        Subscriber subscriber = subscriberService.getSubscriber(1000000000L).get(0);
        Subscription subscription = subscriber.getActiveAndPendingSubscriptions()
                .iterator().next();
        String subscriptionId = subscription.getSubscriptionId();

        transactionManager.commit(status);

        // circle missing
        HttpDeleteWithBody httpDelete = createDeactivateSubscriptionHttpDelete(
                "1000000000", null, "AP", VALID_CALL_ID,
                subscriptionId);
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpDelete, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine()
                .getStatusCode());
    }


    //To verify the behavior of  Deactivate Subscription Request API  if a mandatory parameter :
    // circle is missing from the API request.
    @Test
    public void verifyFT103() throws IOException, InterruptedException {

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberService.getSubscriber(1000000000L).get(0);
        Subscription subscription = subscriber.getActiveAndPendingSubscriptions()
                .iterator().next();
        String subscriptionId = subscription.getSubscriptionId();

        transactionManager.commit(status);

        // circle missing
        HttpDeleteWithBody httpDelete = createDeactivateSubscriptionHttpDelete(
                "1000000000", "A", null, VALID_CALL_ID,
                subscriptionId);
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpDelete, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine()
                .getStatusCode());
    }

    //To verify the behavior of  Deactivate Subscription Request API  if a mandatory parameter :
    // callId is missing from the API request.

    @Test
    public void verifyFT104() throws IOException, InterruptedException {

        // callId missing
        HttpDeleteWithBody httpDelete = createDeactivateSubscriptionHttpDelete(
                "1000000000", "A", "AP", null,
                "77f13128-037e-4f98-8651-285fa618d94a");
        String expectedJsonResponse = createFailureResponseJson("<callId: Not Present>");
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpDelete, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    // This method is a utility method for running the test cases. this is
    // already
    // used in the branch NMS.FT.58.59.60
    private HttpPost createSubscriptionHttpPost(String callingNumber,
                                                String operator, String circle, String callId,
                                                String languageLocationCode, String subscriptionPack)
            throws IOException {

        StringBuilder sb = new StringBuilder();
        String seperator = "";
        sb.append("{");
        if (callingNumber != null) {
            sb.append(String.format("%s\"callingNumber\": %s", seperator,
                    callingNumber));
            seperator = ",";
        }
        if (operator != null) {
            sb.append(String.format("%s\"operator\": \"%s\"", seperator,
                    operator));
            seperator = ",";
        }
        if (circle != null) {
            sb.append(String.format("%s\"circle\": \"%s\"", seperator, circle));
            seperator = ",";
        }
        if (callId != null) {
            sb.append(String.format("%s\"callId\": \"%s\"", seperator, callId));
            seperator = ",";
        }
        if (languageLocationCode != null) {
            sb.append(String.format("%s\"languageLocationCode\": \"%s\"",
                    seperator, languageLocationCode));
            seperator = ",";
        }
        if (subscriptionPack != null) {
            sb.append(String.format("%s\"subscriptionPack\": \"%s\"",
                    seperator, subscriptionPack));
            seperator = ",";
        }

        sb.append("}");

        HttpPost httpPost = new HttpPost(String.format(
                "http://localhost:%d/api/kilkari/subscription",
                TestContext.getJettyPort()));
        httpPost.setHeader("Content-type", "application/json");
        httpPost.setEntity(new StringEntity(sb.toString()));
        return httpPost;
    }

    /**
     * To verify the behavior of Create Subscription Request API if provided
     * beneficiary's callId is not valid : less than 15 digits.
     */
    @Test
    public void verifyFT61() throws IOException, InterruptedException {
        // callId less than 15 digit
        HttpPost httpPost = createSubscriptionHttpPost("1234567890", "A", "AP",
                "12345678901254", "10", "childPack");

        String expectedJsonResponse = createFailureResponseJson("<callId: Invalid>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Create Subscription Request API if provided
     * beneficiary's callId is not valid : more than 25 digits.
     */
    @Test
    public void verifyFT62() throws IOException, InterruptedException {
        // callId more than 25 digit
        HttpPost httpPost = createSubscriptionHttpPost("1234567890", "A", "AP",
                VALID_CALL_ID.concat("foo"), "10", "childPack");
        String expectedJsonResponse = createFailureResponseJson("<callId: Invalid>");
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Create Subscription Request API if provided
     * beneficiary's callId is not valid : Alphanumeric value.
     */
    @Test
    public void verifyFT63() throws IOException, InterruptedException {
        // callId alphanumeric
        HttpPost httpPost = createSubscriptionHttpPost("1234567890", "A", "AP", "12345678AR12545", "10",
                "childPack");
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }


    /**
     * To verify the behavior of Save Inbox call Details API if provided
     * beneficiary's callStartTime is not valid : not in epoch format :
     * 7/5/2015.
     */
    @Test
    public void verifyFT30() throws IOException, InterruptedException {
        // Invalid callStartTime not in Epoch format
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost("1234567890", // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                "7/12/2015", // callStartTime invalid
                "456", // callEndTime
                "123", // callDurationInPulses
                "1", // callStatus
                "1" // callDisconnectReason
        );
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    /**
     * To verify the behavior of Save Inbox call Details API if provided
     * beneficiary's callEndTime is not valid : not in epoch format : 7/5/2015.
     */
    @Test
    public void verifyFT31() throws IOException, InterruptedException {
        // Invalid callEndTime not in Epoch format
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost("1234567890", // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                "456", // callStartTime
                "07/12/2015", // callEndTime invalid
                "123", // callDurationInPulses
                "1", // callStatus
                "1" // callDisconnectReason
        );
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }


    private HttpPost createInboxCallDetailsRequestHttpPost(
            String callingNumber, String operator, String circle,
            String callId, String callStartTime, String callEndTime,
            String callDurationInPulses, String callStatus,
            String callDisconnectReason, String content_subscriptionId,
            String content_subscriptionPack, String content_inboxWeekId,
            String content_contentFileName, String content_startTime,
            String content_endTime) throws IOException {
        HttpPost httpPost = new HttpPost(String.format(
                "http://localhost:%d/api/kilkari/inboxCallDetails",
                TestContext.getJettyPort()));

        StringBuilder sb = new StringBuilder();
        String seperator = "";
        sb.append("{");
        if (callingNumber != null) {
            sb.append(String.format("%s\"callingNumber\": %s", seperator,
                    callingNumber));
            seperator = ",";
        }
        if (operator != null) {
            sb.append(String.format("%s\"operator\": \"%s\"", seperator,
                    operator));
            seperator = ",";
        }
        if (circle != null) {
            sb.append(String.format("%s\"circle\": \"%s\"", seperator, circle));
            seperator = ",";
        }
        if (callId != null) {
            sb.append(String.format("%s\"callId\": %s", seperator, callId));
            seperator = ",";
        }
        if (callStartTime != null) {
            sb.append(String.format("%s\"callStartTime\": %s", seperator,
                    callStartTime));
            seperator = ",";
        }
        if (callEndTime != null) {
            sb.append(String.format("%s\"callEndTime\": %s", seperator,
                    callEndTime));
            seperator = ",";
        }
        if (callDurationInPulses != null) {
            sb.append(String.format("%s\"callDurationInPulses\": %s",
                    seperator, callDurationInPulses));
            seperator = ",";
        }
        if (callStatus != null) {
            sb.append(String.format("%s\"callStatus\": %s", seperator,
                    callStatus));
            seperator = ",";
        }
        if (callDisconnectReason != null) {
            sb.append(String.format("%s\"callDisconnectReason\": %s",
                    seperator, callDisconnectReason));
            seperator = ",";
        }
        if (content_contentFileName != null || content_endTime != null
                || content_inboxWeekId != null || content_startTime != null
                || content_subscriptionId != null
                || content_subscriptionPack != null) {
            sb.append(String.format("%s\"content\": [{", seperator));
            seperator = "";
            if (content_subscriptionId != null) {
                sb.append(String.format("%s\"subscriptionId\": %s", seperator,
                        content_subscriptionId));
                seperator = ",";
            }
            if (content_subscriptionPack != null) {
                sb.append(String.format("%s\"subscriptionPack\": %s",
                        seperator, content_subscriptionPack));
                seperator = ",";
            }
            if (content_inboxWeekId != null) {
                sb.append(String.format("%s\"inboxWeekId\": %s", seperator,
                        content_inboxWeekId));
                seperator = ",";
            }
            if (content_contentFileName != null) {
                sb.append(String.format("%s\"contentFileName\": %s", seperator,
                        content_contentFileName));
                seperator = ",";
            }
            if (content_startTime != null) {
                sb.append(String.format("%s\"startTime\": %s", seperator,
                        content_startTime));
                seperator = ",";
            }
            if (content_endTime != null) {
                sb.append(String.format("%s\"endTime\": %s", seperator,
                        content_endTime));
                seperator = ",";
            }
            sb.append("}]");
        }

        sb.append("}");

        StringEntity params = new StringEntity(sb.toString());
        httpPost.setEntity(params);
        httpPost.addHeader("content-type", "application/json");
        return httpPost;
    }

    /**
     * To verify the behavior of Save Inbox call Details API if provided
     * beneficiary's startTime is not valid : not in epoch format : 7/5/2015.
     */
    // JIRA issue https://applab.atlassian.net/browse/NMS-199
    @Test
    public void verifyFT37() throws IOException, InterruptedException {
        // Invalid content startTime: not in Epoch format
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost("1234567890", // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                "456", // callStartTime
                "876", // callEndTime
                "123", // callDurationInPulses
                "1", // callStatus
                "1", // callDisconnectReason
                // content part
                "00000000-0000-0000-0000-000000000000", // subscriptionId
                "48WeeksPack", // subscriptionPack
                "10_1", // inboxWeekId missing
                "foo", // contentFileName
                "7/5/15", // startTime not in epoch format
                "456" // endTime
        );
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    /**
     * To verify the behavior of Save Inbox call Details API if provided
     * beneficiary's endTime is not valid : not in epoch format : 7/5/2015.
     */
    // JIRA issue https://applab.atlassian.net/browse/NMS-199
    @Test
    public void verifyFT38() throws IOException, InterruptedException {
        // Invalid content endTime: not in Epoch format
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost("1234567890", // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                "456", // callStartTime
                "786", // callEndTime
                "123", // callDurationInPulses
                "1", // callStatus
                "1", // callDisconnectReason
                // content part
                "00000000-0000-0000-0000-000000000000", // subscriptionId
                "48WeeksPack", // subscriptionPack
                "10_1", // inboxWeekId missing
                "foo", // contentFileName
                "454", // startTime
                "7/5/15" // endTime not in epoch format
        );
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    /**
     * To verify that Save Inbox call Details API request fails if the provided
     * parameter value of callingNumber is : blank value.
     */
    // JIRA issue https://applab.atlassian.net/browse/NMS-198
    @Test
    public void verifyFT54_72() throws IOException, InterruptedException {
        // Blank callingNumber
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost("", // callingNumber
                // blank
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                "456", // callStartTime
                "785", // callEndTime invalid
                "123", // callDurationInPulses
                "1", // callStatus
                "1" // callDisconnectReason
        );

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());

        httpPost = createInboxCallDetailsRequestHttpPost(" ", // callingNumber
                // blank(single space)
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                "456", // callStartTime
                "785", // callEndTime invalid
                "123", // callDurationInPulses
                "1", // callStatus
                "1" // callDisconnectReason
        );

        response = SimpleHttpClient.httpRequestAndResponse(httpPost,ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    /**
     * To verify that Save Inbox call Details API request fails if the provided
     * parameter value of circle is : empty value.
     */
    @Test
    public void verifyFT55_73() throws IOException, InterruptedException {
        // Blank circle
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost("1234567890",
                "A", // operator
                "", // circle blank
                VALID_CALL_ID, // callId
                "456", // callStartTime
                "785", // callEndTime invalid
                "123", // callDurationInPulses
                "1", // callStatus
                "1" // callDisconnectReason
        );

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

        httpPost = createInboxCallDetailsRequestHttpPost("1234567890", "A", // operator
                " ", // circle blank(single space)
                VALID_CALL_ID, // callId
                "456", // callStartTime
                "785", // callEndTime invalid
                "123", // callDurationInPulses
                "1", // callStatus
                "1" // callDisconnectReason
        );

        response = SimpleHttpClient.httpRequestAndResponse(httpPost,
                ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
    }

    /**
     * To verify that Save Inbox call Details API request fails if the provided
     * parameter value of callDisconnectReason is : empty value.
     */
    // JIRA issue https://applab.atlassian.net/browse/NMS-198
    @Test
    public void verifyFT56() throws IOException, InterruptedException {
        // Blank callDisconnectReason
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost("1234567890",
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                "456", // callStartTime
                "785", // callEndTime invalid
                "123", // callDurationInPulses
                "1", // callStatus
                "" // callDisconnectReason blank
        );

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());

        httpPost = createInboxCallDetailsRequestHttpPost("1234567890", "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                "456", // callStartTime
                "785", // callEndTime invalid
                "123", // callDurationInPulses
                "1", // callStatus
                " " // callDisconnectReason blank(single space)
        );

        response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    /**
     * test DeactivateSubscription API with Blank Params
     *
     */
    // JIRA issue: https://applab.atlassian.net/browse/NMS-193
    @Test
    public void verifyFT105() throws IOException, InterruptedException {

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberService.getSubscriber(1000000000L).get(0);
        Subscription subscription = subscriber.getActiveAndPendingSubscriptions().iterator().next();
        String subscriptionId = subscription.getSubscriptionId();

        transactionManager.commit(status);

        // callingNumber blank
        HttpDeleteWithBody httpDelete = createDeactivateSubscriptionHttpDelete("", "A", "AP", VALID_CALL_ID,
                subscriptionId);
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpDelete, ADMIN_USERNAME, ADMIN_PASSWORD);

        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());

        // callingNumber blank
        httpDelete = createDeactivateSubscriptionHttpDelete(" ", "A", "AP", VALID_CALL_ID, subscriptionId);
        response = SimpleHttpClient.httpRequestAndResponse(httpDelete, ADMIN_USERNAME, ADMIN_PASSWORD);

        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    /**
     * To verify that Deactivate Subscription Request API request fails if the
     * provided parameter value of circle is : empty value.
     */
    @Test
    public void verifyFT106() throws IOException, InterruptedException {

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber subscriber = subscriberService.getSubscriber(1000000000L).get(0);
        Subscription subscription = subscriber.getActiveAndPendingSubscriptions()
                .iterator().next();
        String subscriptionId = subscription.getSubscriptionId();

        transactionManager.commit(status);

        // circle blank
        HttpDeleteWithBody httpDelete = createDeactivateSubscriptionHttpDelete(
                "1000000000", "A", "", VALID_CALL_ID, subscriptionId);

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpDelete, ADMIN_USERNAME, ADMIN_PASSWORD);

        assertEquals(HttpStatus.SC_OK, response.getStatusLine()
                .getStatusCode());

        // circle blank(single space)
        httpDelete = createDeactivateSubscriptionHttpDelete("1000000000", "A",
                " ", VALID_CALL_ID, subscriptionId);

        response = SimpleHttpClient.httpRequestAndResponse(httpDelete,
                ADMIN_USERNAME, ADMIN_PASSWORD);

        assertEquals(HttpStatus.SC_OK, response.getStatusLine()
                .getStatusCode());
    }

    /**
     * To verify the behavior of Create Subscription Request API if a mandatory
     * parameter : callingNumber is missing from the API request.
     */
    @Test
    public void verifyFT65() throws IOException,
            InterruptedException {
        // callingNumber missing
        HttpPost httpPost = createSubscriptionHttpPost(null, "A", "AP",
                VALID_CALL_ID, "10", "childPack");

        String expectedJsonResponse = createFailureResponseJson("<callingNumber: Not Present>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Create Subscription Request API if a mandatory
     * parameter : operator is missing from the API request.
     */
    @Test
    public void verifyFT66() throws IOException, InterruptedException {
        // operator missing
        HttpPost httpPost = createSubscriptionHttpPost("1234567890", null, rh.delhiCircle().getName(), VALID_CALL_ID, rh.hindiLanguage().getCode(),
                sh.childPack().getName());

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);

            assertEquals(HttpStatus.SC_OK, response.getStatusLine()
                .getStatusCode());
    }

    /**
     * To verify the behavior of Create Subscription Request API if a mandatory
     * parameter : circle is missing from the API request.
     */
    @Test
    public void verifyFT67() throws IOException, InterruptedException {
        // circle missing(optional parameter)
        HttpPost httpPost = createSubscriptionHttpPost("9999911122",
                rh.airtelOperator(), null, VALID_CALL_ID, rh
                        .hindiLanguage().getCode(), sh.childPack().getName());

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);

        assertEquals(HttpStatus.SC_OK, response.getStatusLine()
                .getStatusCode());
    }


    

    /**
     * To verify the behavior of Create Subscription Request API if a mandatory
     * parameter : callId is missing from the API request.
     */
    @Test
    public void verifyFT68() throws IOException, InterruptedException {
        // callId missing
        HttpPost httpPost = createSubscriptionHttpPost("1234567890", "A", "AP", null,
                "10", "childPack");

        String expectedJsonResponse = createFailureResponseJson("<callId: Not Present>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Create Subscription Request API if a mandatory
     * parameter : languageLocationCode is missing from the API request.
     */
    @Test
    public void verifyFT69() throws IOException, InterruptedException {
        // languageLocationCode missing
        HttpPost httpPost = createSubscriptionHttpPost("1234567890", "A", "AP",
                VALID_CALL_ID, null, "childPack");
        String expectedJsonResponse = createFailureResponseJson("<languageLocationCode: Not Present>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Create Subscription Request API if a mandatory
     * parameter : subscriptionPack is missing from the API request.
     */
    @Test
    public void verifyFT70() throws IOException, InterruptedException {
        // subscriptionPack missing
        HttpPost httpPost = createSubscriptionHttpPost("1234567890", "A", "AP",
                VALID_CALL_ID, "10", null);

        String expectedJsonResponse = createFailureResponseJson("<subscriptionPack: Not Present>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Save Inbox call Details API if provided
     * beneficiary's callDurationInPulses is not valid : Alphanumeric value.
     */
    @Test
    public void verifyFT32() throws IOException, InterruptedException {
        // Invalid callDurationInPulses: AlphaNumeric value
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost("1234567890", // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId less
                "123", // callStartTime
                "456", // callEndTime
                "1A3", // callDurationInPulses alphanumeric
                "1", // callStatus
                "1" // callDisconnectReason
        );

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    /**
     * To verify the behavior of Save Inbox call Details API if provided
     * beneficiary's callStatus is not valid : Alphanumeric value.
     */
    @Test
    public void verifyFT33() throws IOException, InterruptedException {
        // Invalid callStatus: AlphaNumeric value
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost("1234567890", // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                "456", // callStartTime
                "856", // callEndTime
                "123", // callDurationInPulses
                "1A", // callStatus alphanumeric
                "1" // callDisconnectReason
        );

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    /**
     * To verify the behavior of Save Inbox call Details API if provided
     * beneficiary's callDisconnectReason is not valid : Alphanumeric value.
     */
    @Test
    public void verifyFT34() throws IOException, InterruptedException {
        // Invalid callDisconnectReason: AlphaNumeric value
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost("1234567890", // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                "456", // callStartTime
                "856", // callEndTime
                "123", // callDurationInPulses
                "1", // callStatus
                "1A" // callDisconnectReason alphanumeric
        );

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    /**
     * To verify the behavior of Get Subscriber Details API if provided
     * beneficiary's callId is not valid : less than 15 digits.
     */
    @Test
    public void verifyFT9() throws IOException, InterruptedException {
        HttpGet httpGet = createGetSubscriberDetailsRequest("1234567890", // callingNumber
                "A", // operator
                "AP", // circle
                "12345678901234" // callId less than 15 digits
        );

        String expectedJsonResponse = createFailureResponseJson("<callId: Invalid>");
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Get Subscriber Details API if provided
     * beneficiary's callId is not valid : more than 15 digits.
     */
    @Test
    public void verifyFT10() throws IOException, InterruptedException {
        HttpGet httpGet = createGetSubscriberDetailsRequest("1234567890", // callingNumber
                "A", // operator
                "AP", // circle
                "1234567890123456" // callId more than 15 digits
        );
        String expectedJsonResponse = createFailureResponseJson("<callId: Invalid>");
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Get Subscriber Details API if provided
     * beneficiary's callId is not valid : Alphanumeric value.
     */
    @Test
    public void verifyFT11() throws IOException, InterruptedException {
        HttpGet httpGet = createGetSubscriberDetailsRequest("1234567890", // callingNumber
                "A", // operator
                "AP", // circle
                "123456789A12345" // callId alpha numeric
        );

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    /**
     * To verify the behavior of Save Inbox call Details API if a mandatory
     * parameter : subscriptionId is missing from the API request.
     */
    @Test
    public void verifyFT48() throws IOException, InterruptedException {
        // Missing subscriptionPack
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                new HashSet<>(Arrays.asList(
                        new CallDataRequest(null, // subscriptionId missing
                                "48WeeksPack", // subscriptionPack
                                "123", // inboxWeekId
                                "foo", // contentFileName
                                123L, // startTime
                                456L // endTime
                        ), new CallDataRequest(
                                "00000000-0000-0000-0000-000000000000", // subscriptionId
                                "72WeeksPack", // subscriptionPack
                                "123", // inboxWeekId
                                "foo", // contentFileName
                                123L, // startTime
                                456L // endTime
                        ))))); // content
        String expectedJsonResponse = createFailureResponseJson("<subscriptionId: Not Present><content: Invalid>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Save Inbox call Details API if a mandatory
     * parameter : subscritpionPack is missing from the API request.
     */
    // TODO JIRA issue https://applab.atlassian.net/browse/NMS-185
    @Test
    public void verifyFT49() throws IOException, InterruptedException {
        // Missing subscriptionPack
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                new HashSet<>(Arrays.asList(new CallDataRequest(
                        "00000000-0000-0000-0000-000000000000", // subscriptionId
                        null, // subscriptionPack missing
                        "123", // inboxWeekId
                        "foo", // contentFileName
                        123L, // startTime
                        456L // endTime
                ), new CallDataRequest(
                        "00000100-0000-0000-0000-000000000000", // subscriptionId
                        "72WeeksPack", // subscriptionPack
                        "123", // inboxWeekId
                        "foo", // contentFileName
                        123L, // startTime
                        456L // endTime
                ))))); // content
        String expectedJsonResponse = createFailureResponseJson("<subscriptionPack: Invalid><content: Invalid>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Save Inbox call Details API if a mandatory
     * parameter : inboxWeekId is missing from the API request.
     */
    // TODO JIRA issue https://applab.atlassian.net/browse/NMS-185
    @Test
    public void verifyFT50() throws IOException, InterruptedException {
        // Missing inboxWeekId
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                new HashSet<>(Arrays.asList(new CallDataRequest(
                        "00000000-0000-0000-0000-000000000000", // subscriptionId
                        "48WeeksPack", // subscriptionPack
                        null, // inboxWeekId missing
                        "foo", // contentFileName
                        123L, // startTime
                        456L // endTime
                ), new CallDataRequest(
                        "00000000-0000-0000-0000-000000000000", // subscriptionId
                        "72WeeksPack", // subscriptionPack
                        "123", // inboxWeekId
                        "foo", // contentFileName
                        123L, // startTime
                        456L // endTime
                ))))); // content
        String expectedJsonResponse = createFailureResponseJson("<inboxWeekId: Not Present><content: Invalid>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Save Inbox call Details API if a mandatory
     * parameter : contentFileName is missing from the API request.
     */
    // TODO JIRA issue https://applab.atlassian.net/browse/NMS-185
    @Test
    public void verifyFT51() throws IOException, InterruptedException {
        // Missing contentFileName
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                new HashSet<>(Arrays.asList(new CallDataRequest(
                        "00000000-0000-0000-0000-000000000000", // subscriptionId
                        "48WeeksPack", // subscriptionPack
                        "123", // inboxWeekId
                        null, // contentFileName missing
                        123L, // startTime
                        456L // endTime
                ), new CallDataRequest(
                        "00000000-0000-0000-0000-000000000000", // subscriptionId
                        "72WeeksPack", // subscriptionPack
                        "123", // inboxWeekId
                        "foo", // contentFileName
                        123L, // startTime
                        456L // endTime
                ))))); // content
        String expectedJsonResponse = createFailureResponseJson("<contentFileName: Not Present><content: Invalid>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Save Inbox call Details API if a mandatory
     * parameter : startTime is missing from the API request.
     */
    // TODO JIRA issue https://applab.atlassian.net/browse/NMS-185
    @Test
    public void verifyFT52() throws IOException, InterruptedException {
        // Missing startTime
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                new HashSet<>(Arrays.asList(new CallDataRequest(
                        "00000000-0000-0000-0000-000000000000", // subscriptionId
                        "48WeeksPack", // subscriptionPack
                        "123", // inboxWeekId
                        "foo", // contentFileName
                        null, // startTime missing
                        456L // endTime
                ), new CallDataRequest(
                        "00000000-0000-0000-2000-000000000000", // subscriptionId
                        "72WeeksPack", // subscriptionPack
                        "123", // inboxWeekId
                        "foo", // contentFileName
                        123L, // startTime
                        456L // endTime
                ))))); // content

        String expectedJsonResponse = createFailureResponseJson("<startTime: Not Present><content: Invalid>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Save Inbox call Details API if a mandatory
     * parameter : endTime is missing from the API request.
     */
    // TODO JIRA issue https://applab.atlassian.net/browse/NMS-185
    @Test
    public void verifyFT53() throws IOException, InterruptedException {
        // Missing endTime
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                new HashSet<>(Arrays.asList(new CallDataRequest(
                        "00000000-0000-0000-0000-000000000000", // subscriptionId
                        "48WeeksPack", // subscriptionPack
                        "123", // inboxWeekId
                        "foo", // contentFileName
                        123L, // startTime
                        null // endTime missing
                ), new CallDataRequest(
                        "00000000-0000-0000-0200-000000000000", // subscriptionId
                        "72WeeksPack", // subscriptionPack
                        "123", // inboxWeekId
                        "foo", // contentFileName
                        123L, // startTime
                        456L // endTime
                ))))); // content
        String expectedJsonResponse = createFailureResponseJson("<endTime: Not Present><content: Invalid>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }


    /**
     * To verify the behavior of Save Inbox call Details API if a mandatory
     * parameter : callingNumber is missing from the API request.
     */
    @Test
    public void verifyFT39() throws IOException, InterruptedException {
        // Missing calling Number
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                null, // callingNumber missing
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                null)); // content
        String expectedJsonResponse = createFailureResponseJson("<callingNumber: Not Present>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Save Inbox call Details API if a mandatory
     * parameter : operator is missing from the API request.
     */
    @Test
    public void verifyFT40() throws IOException, InterruptedException {
        // Missing Operator
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, // callingNumber
                null, // operator missing
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                null)); // content
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine()
                .getStatusCode());
    }

    /**
     * To verify the behavior of Save Inbox call Details API if a mandatory
     * parameter : circle is missing from the API request.
     */
    @Test
    public void verifyFT41() throws IOException, InterruptedException {
        // Missing circle
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, // callingNumber
                "A", // operator
                null, // circle missing
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                null)); // content

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine()
                .getStatusCode());
    }

    /**
     * To verify the behavior of Save Inbox call Details API if a mandatory
     * parameter : callId is missing from the API request.
     */
    @Test
    public void verifyFT42() throws IOException, InterruptedException {
        // Missing callId
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, // callingNumber
                "A", // operator
                "AP", // circle
                null, // callId missing
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                null)); // content
        String expectedJsonResponse = createFailureResponseJson("<callId: Not Present>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Save Inbox call Details API if a mandatory
     * parameter : callStartTime is missing from the API request.
     */
    @Test
    public void verifyFT43() throws IOException, InterruptedException {
        // Missing callStartTime
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                null, // callStartTime missing
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                null)); // content
        String expectedJsonResponse = createFailureResponseJson("<callStartTime: Not Present>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Save Inbox call Details API if a mandatory
     * parameter : callEndTime is missing from the API request.
     */
    @Test
    public void verifyFT44() throws IOException, InterruptedException {
        // Missing callEndTime
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                null, // callEndTime missing
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                null)); // content
        String expectedJsonResponse = createFailureResponseJson("<callEndTime: Not Present>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Save Inbox call Details API if a mandatory
     * parameter : callDurationInPulses is missing from the API request.
     */
    @Test
    public void verifyFT45() throws IOException, InterruptedException {
        // Missing callDurationInPulses
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                null, // callDurationInPulses missing
                1, // callStatus
                1, // callDisconnectReason
                null)); // content
        String expectedJsonResponse = createFailureResponseJson("<callDurationInPulses: Not Present>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Save Inbox call Details API if a mandatory
     * parameter : callStatus is missing from the API request.
     */
    @Test
    public void verifyFT46() throws IOException, InterruptedException {
        // Missing callStatus
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                null, // callStatus missing
                1, // callDisconnectReason
                null)); // content
        String expectedJsonResponse = createFailureResponseJson("<callStatus: Not Present>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Save Inbox call Details API if a mandatory
     * parameter : callDisconnectReason is missing from the API request.
     */
    @Test
    public void verifyFT47() throws IOException, InterruptedException {
        // Missing callDisconnectReason
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                1234567890L, // callingNumber
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                null, // callDisconnectReason missing
                null)); // content
        String expectedJsonResponse = createFailureResponseJson("<callDisconnectReason: Not Present>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Get Inbox Details API if a mandatory parameter
     * : callingNumber is missing from the API request.
     */
    @Test
    public void verifyFT89() throws IOException,
            InterruptedException {
        // callingNumber missing
        HttpGet httpGet = createHttpGet(false, "", true, VALID_CALL_ID);
        String expectedJsonResponse = createFailureResponseJson("<callingNumber: Not Present>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Get Inbox Details API if a mandatory parameter
     * : callId is missing from the API request.
     */
    @Test
    public void verifyFT90() throws IOException, InterruptedException {
        // CallId missing
        HttpGet httpGet = createHttpGet(true, "1234567890", false, "");
        String expectedJsonResponse = createFailureResponseJson("<callId: Not Present>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Get Inbox Details API if provided beneficiary's
     * callId is not valid : less than 15 digits.
     */
    @Test
    public void verifyFT86() throws IOException,
            InterruptedException {
        // CallId less than 15 digits
        HttpGet httpGet = createHttpGet(true, "1234567890", true,
                "12345678901234");
        String expectedJsonResponse = createFailureResponseJson("<callId: Invalid>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Get Inbox Details API if provided beneficiary's
     * callId is not valid : more than 15 digits.
     */
    @Test
    public void verifyFT87() throws IOException, InterruptedException {
        // CallId more than 15 digits
        HttpGet httpGet = createHttpGet(true, "1234567890", true,
                "1234567890123456");
        String expectedJsonResponse = createFailureResponseJson("<callId: Invalid>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertEquals(expectedJsonResponse,
                EntityUtils.toString(response.getEntity()));
    }

    /**
     * To verify the behavior of Get Inbox Details API if provided beneficiary's
     * callId is not valid : Alphanumeric value.
     */
    @Test
    public void verifyFT88() throws IOException, InterruptedException {
        // CallId alphanumeric
        HttpGet httpGet = createHttpGet(true, "1234567890", true, "12345678GT12345");
        String expectedJsonResponse = createFailureResponseJson("<callId: Invalid>");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    /**
     * NMS_FT_58 To verify the behavior of Create Subscription Request API if
     * provided beneficiary's callingNumber is not valid : less than 10 digits.
     */
    @Test
    public void verifyFT58() throws IOException, InterruptedException {
        // Calling Number less than 10 digit
        HttpPost httpPost = createSubscriptionHttpPost("123456789", "A", "AP",
                VALID_CALL_ID, "10", "childPack");

        String expectedJsonResponse = createFailureResponseJson("<callingNumber: Invalid>");
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertTrue(expectedJsonResponse.equals(EntityUtils.toString(response
                .getEntity())));
    }

    /**
     * To verify the behavior of Create Subscription Request API if provided
     * beneficiary's callingNumber is not valid : more than 10 digits.
     */
    @Test
    public void verifyFT59() throws IOException, InterruptedException {
        // Calling Number more than 10 digit
        HttpPost httpPost = createSubscriptionHttpPost("12345678901", "A",
                "AP", VALID_CALL_ID, "10", "childPack");
        String expectedJsonResponse = createFailureResponseJson("<callingNumber: Invalid>");
        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(
                httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine()
                .getStatusCode());
        assertTrue(expectedJsonResponse.equals(EntityUtils.toString(response
                .getEntity())));
        ;
    }

    /**
     * To verify the behavior of Create Subscription Request API if provided
     * Subscriber's callingNumber is not valid : Alphanumeric value.
     */
    @Test
    public void verifyFT60() throws IOException, InterruptedException {
        // Calling Number alphanumeric
        HttpPost httpPost = createSubscriptionHttpPost("12345AD890", "A", "AP", VALID_CALL_ID, "10",
                "childPack");

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }


    /**
     * NMS_FT_24 To check response of SaveInboxCallDetails API if
     * callingNumber provided in the request is in invalid format
     */
    @Test
    public void verifyFT24() throws IOException,
            InterruptedException {

        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                123456789L, // callingNumber less than 10 digit
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                null)); // content
        String expectedJsonResponse = createFailureResponseJson("<callingNumber: Invalid>");

        assertTrue(SimpleHttpClient.execHttpRequest(httpPost,
                HttpStatus.SC_BAD_REQUEST, expectedJsonResponse,
                ADMIN_USERNAME, ADMIN_PASSWORD));
    }


    /**
     * NMS_FT_25 To check response of SaveInboxCallDetails API if
     * callingNumber provided in the request is in invalid format
     */
    @Test
    public void verifyFT25() throws IOException, InterruptedException {

        HttpPost httpPost = createInboxCallDetailsRequestHttpPost(new InboxCallDetailsRequest(
                12345678901L, // callingNumber more than 10 digit
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                123L, // callStartTime
                456L, // callEndTime
                123, // callDurationInPulses
                1, // callStatus
                1, // callDisconnectReason
                null)); // content

        String expectedJsonResponse = createFailureResponseJson("<callingNumber: Invalid>");

        assertTrue(SimpleHttpClient.execHttpRequest(httpPost,
                HttpStatus.SC_BAD_REQUEST, expectedJsonResponse,
                ADMIN_USERNAME, ADMIN_PASSWORD));
    }


    /**
     * NMS_FT_26 To check response of SaveInboxCallDetails API if
     * callingNumber provided in the request is in invalid format
     */
    @Test
    public void verifyFT26() throws IOException, InterruptedException {

        // taking alpha numeric value of calling Number
        HttpPost httpPost = createInboxCallDetailsRequestHttpPost("12345AF890", // callingNumber
                // alphanumeric
                "A", // operator
                "AP", // circle
                VALID_CALL_ID, // callId
                "123", // callStartTime
                "456", // callEndTime
                "123", // callDurationInPulses
                "1", // callStatus
                "1" // callDisconnectReason
        );

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpPost, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }



    /**
     * NMS_FT_6 To check the response in case of invalid callingNumber in
     * Get Subscriber Details API
     */
    @Test
    public void verifyFT6() throws IOException,
            InterruptedException {



        HttpGet httpGet = createGetSubscriberDetailsRequest("123456789", // callingNumber
                // less
                // than
                // 10
                // digits
                "A", // operator
                "AP", // circle
                VALID_CALL_ID // callId
        );

        String expectedJsonResponse = createFailureResponseJson("<callingNumber: Invalid>");

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet,
                HttpStatus.SC_BAD_REQUEST, expectedJsonResponse,
                ADMIN_USERNAME, ADMIN_PASSWORD));
    }


    /**
     * NMS_FT_7 To check the response in case of invalid callingNumber in
     * Get Subscriber Details API
     */
    @Test
    public void verifyFT7() throws IOException, InterruptedException {


        HttpGet httpGet = createGetSubscriberDetailsRequest("12345678901", // callingNumber
                // more than
                // 10 digits
                "A", // operator
                "AP", // circle
                VALID_CALL_ID // callId
        );
        String expectedJsonResponse = createFailureResponseJson("<callingNumber: Invalid>");

        assertTrue(SimpleHttpClient.execHttpRequest(httpGet,
                HttpStatus.SC_BAD_REQUEST, expectedJsonResponse,
                ADMIN_USERNAME, ADMIN_PASSWORD));
    }


    /**
     * NMS_FT_8 To check the response in case of invalid callingNumber in
     * Get Subscriber Details API
     */

    @Test
    public void verifyFT8() throws IOException, InterruptedException {


        HttpGet httpGet = createGetSubscriberDetailsRequest("12345A6789", // callingNumber
                // alpha
                // numeric
                "A", // operator
                "AP", // circle
                VALID_CALL_ID // callId
        );

        HttpResponse response = SimpleHttpClient.httpRequestAndResponse(httpGet, ADMIN_USERNAME, ADMIN_PASSWORD);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }


    /**
     * To check NMS is able to make available a message corresponding to each
     * Pack of current week when user is subscribed to both pregnancy Pack and
     * child Pack with single message per week configuration .
     **/
    @Test
    public void verifyFT119() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // update 2 messages/week to 1 message/week configuration for pregnancy
        // pack
        sh.pregnancyPackFor1MessagePerWeek(subscriptionPackMessageDataService);

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        // set DOB for child pack such that
        mctsSubscriber.setDateOfBirth(DateTime.now().minusDays(14));
        mctsSubscriber.setLastMenstrualPeriod(null);
        subscriberDataService.create(mctsSubscriber);

        // create subscription for child pack in Active state
        Subscription childPackSubscription = subscriptionService
                .createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(),
                        sh.childPack(), SubscriptionOrigin.MCTS_IMPORT);
        childPackSubscription.setNeedsWelcomeMessageViaObd(false);
        subscriptionDataService.update(childPackSubscription);

        mctsSubscriber = subscriberDataService.findByNumber(9999911122L).get(0);

        // create new subscription for pregnancy pack in Active state
        mctsSubscriber.setDateOfBirth(null);
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now().minusDays(90));
        subscriberDataService.update(mctsSubscriber);
        Subscription pregnancyPackSubscription = subscriptionService
                .createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(),
                        sh.pregnancyPack(), SubscriptionOrigin.MCTS_IMPORT);

        transactionManager.commit(status);

        Pattern childPackJsonPattern = Pattern
                .compile(".*\"subscriptionId\":\""
                        + childPackSubscription.getSubscriptionId()
                        + "\",\"subscriptionPack\":\"childPack\",\"inboxWeekId\":\"w3_1\",\"contentFileName\":\"w3_1.wav.*");
        Pattern pregnancyPackJsonPattern = Pattern
                .compile(".*\"subscriptionId\":\""
                        + pregnancyPackSubscription.getSubscriptionId()
                        + "\",\"subscriptionPack\":\"pregnancyPack\",\"inboxWeekId\":\"w1_1\",\"contentFileName\":\"w1_1.wav.*");

        HttpGet httpGet = createHttpGet(true, "9999911122", true,
                VALID_CALL_ID);
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                childPackJsonPattern, ADMIN_USERNAME, ADMIN_PASSWORD));
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                pregnancyPackJsonPattern, ADMIN_USERNAME, ADMIN_PASSWORD));
    }

    /**
     * To check NMS is able to make available a message corresponding to each
     * Pack of current week when user is subscribed to both pregnancy Pack and
     * child with two message per week configuration .
     **/
    @Test
    public void verifyFT120() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // update child pack to 2 messages per week configuration
        sh.childPackFor2MessagePerWeek(subscriptionPackMessageDataService);

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        // set DOB for child pack
        mctsSubscriber.setDateOfBirth(DateTime.now().minusDays(14 + 4));
        mctsSubscriber.setLastMenstrualPeriod(null);
        subscriberDataService.create(mctsSubscriber);

        // create subscription for child pack in Active state
        Subscription childPackSubscription = subscriptionService
                .createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(),
                        sh.childPack(), SubscriptionOrigin.MCTS_IMPORT);
        childPackSubscription.setNeedsWelcomeMessageViaObd(false);
        subscriptionDataService.update(childPackSubscription);

        mctsSubscriber = subscriberDataService.findByNumber(9999911122L).get(0);

        // create new subscription for pregnancy pack in Active state
        mctsSubscriber.setDateOfBirth(null);
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now().minusDays(90 + 4));
        subscriberDataService.update(mctsSubscriber);
        Subscription pregnancyPackSubscription = subscriptionService
                .createSubscription(mctsSubscriber, 9999911122L, rh.hindiLanguage(),
                        sh.pregnancyPack(), SubscriptionOrigin.MCTS_IMPORT);
        pregnancyPackSubscription.setNeedsWelcomeMessageViaObd(false);
        subscriptionDataService.update(pregnancyPackSubscription);

        transactionManager.commit(status);

        Pattern childPackJsonPattern = Pattern
                .compile(".*\"subscriptionId\":\""
                        + childPackSubscription.getSubscriptionId()
                        + "\",\"subscriptionPack\":\"childPack\",\"inboxWeekId\":\"w3_2\",\"contentFileName\":\"w3_2.wav.*");
        Pattern pregnancyPackJsonPattern = Pattern
                .compile(".*\"subscriptionId\":\""
                        + pregnancyPackSubscription.getSubscriptionId()
                        + "\",\"subscriptionPack\":\"pregnancyPack\",\"inboxWeekId\":\"w1_2\",\"contentFileName\":\"w1_2.wav.*");

        HttpGet httpGet = createHttpGet(true, "9999911122", true,
                VALID_CALL_ID);
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                childPackJsonPattern, ADMIN_USERNAME, ADMIN_PASSWORD));
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                pregnancyPackJsonPattern, ADMIN_USERNAME, ADMIN_PASSWORD));
    }


    /**
     * To check NMS is able to make available a single message of current week
     * in inbox with single message per week configuration . when:
     * a) user's MSISDN is subscribed for child Pack.
     * b)user's MSISDN is deactivated for an old subscription of pregnancy Pack.
     **/
    @Test
    public void verifyFT117() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // update pregnancy pack to 1 message/week configuration
        sh.pregnancyPackFor1MessagePerWeek(subscriptionPackMessageDataService);

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setDateOfBirth(null);
        // set LMP for pregnancy pack
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now().minusDays(240));
        subscriberDataService.create(mctsSubscriber);

        // create old subscription for pregnancy pack and deactivate it
        Subscription oldSubscription = subscriptionService.createSubscription(
                mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        subscriptionService.deactivateSubscription(oldSubscription,
                DeactivationReason.DEACTIVATED_BY_USER);

        mctsSubscriber = subscriberDataService.findByNumber(9999911122L).get(0);

        // create new subscription for child pack in Active state such that
        // next OBD date falls on current date
        mctsSubscriber.setLastMenstrualPeriod(null);
        mctsSubscriber.setDateOfBirth(DateTime.now());
        subscriberDataService.update(mctsSubscriber);
        Subscription newSubscription = subscriptionService.createSubscription(
                mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        transactionManager.commit(status);

        String expectedJsonResponse = "{\"inboxSubscriptionDetailList\":[{\"subscriptionId\":\""
                + newSubscription.getSubscriptionId()
                + "\",\"subscriptionPack\":\"childPack\",\"inboxWeekId\":\"w1_1\",\"contentFileName\":\"w1_1.wav\"}]}";

        HttpGet httpGet = createHttpGet(true, "9999911122", true,
                VALID_CALL_ID);
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                expectedJsonResponse, ADMIN_USERNAME, ADMIN_PASSWORD));
    }

    /**
     * To check NMS is able to make available a single message of current week
     * in inbox with single message per week configuration . when:
     * a) user's MSISDN is subscribed for child Pack.
     * b)user's MSISDN status is completed(with in 7 days) for an old subscription of pregnancy Pack.
     **/
    @Test
    public void verifyFT118() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        // update pregnancy pack to 1 message/week configuration
        sh.pregnancyPackFor1MessagePerWeek(subscriptionPackMessageDataService);

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        mctsSubscriber.setDateOfBirth(null);
        // set LMP for pregnancy pack
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now().minusDays(240));
        subscriberDataService.create(mctsSubscriber);

        // create old subscription for pregnancy pack
        Subscription oldSubscription = subscriptionService.createSubscription(
                mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        // update old pregnancy subscription pack to mark complete setting the
        // subscription to have ended less than a week ago
        subscriptionService.updateStartDate(oldSubscription, DateTime.now()
                .minusDays(505 + 90));

        mctsSubscriber = subscriberDataService.findByNumber(9999911122L).get(0);

        // create new subscription for child pack in Active state such that
        // next OBD date falls on current date
        mctsSubscriber.setLastMenstrualPeriod(null);
        mctsSubscriber.setDateOfBirth(DateTime.now());
        subscriberDataService.update(mctsSubscriber);
        Subscription newSubscription = subscriptionService.createSubscription(
                mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        transactionManager.commit(status);

        Pattern oldPregnancyPackPattern = Pattern
                .compile(".*\"subscriptionId\":\""
                        + oldSubscription.getSubscriptionId()
                        + "\",\"subscriptionPack\":\"pregnancyPack\",\"inboxWeekId\":\"w72_1\",\"contentFileName\":\"w72_1.wav.*");
        Pattern newchildPackPattern = Pattern
                .compile(".*\"subscriptionId\":\""
                        + newSubscription.getSubscriptionId()
                        + "\",\"subscriptionPack\":\"childPack\",\"inboxWeekId\":\"w1_1\",\"contentFileName\":\"w1_1.wav.*");

        HttpGet httpGet = createHttpGet(true, "9999911122", true,
                VALID_CALL_ID);
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                oldPregnancyPackPattern, ADMIN_USERNAME, ADMIN_PASSWORD));
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                newchildPackPattern, ADMIN_USERNAME, ADMIN_PASSWORD));
    }

    /**
     * To check NMS is able to make available a single message of current week
     * in inbox with single message per week configuration . when:
     * a) user's MSISDN is subscribed for pregnancy Pack.
     * b)user's MSISDN is deactivated for an old subscription of child Pack.
     **/
    @Test
    public void verifyFT111() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        //update pregnancy pack to 1 message/week
        sh.pregnancyPackFor1MessagePerWeek(subscriptionPackMessageDataService);

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        // set DOB for old child pack
        mctsSubscriber.setDateOfBirth(DateTime.now().minusDays(100));
        mctsSubscriber.setLastMenstrualPeriod(null);
        subscriberDataService.create(mctsSubscriber);

        // create subscription for child pack and deactivate it
        Subscription oldSubscription = subscriptionService.createSubscription(
                mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        subscriptionService.deactivateSubscription(oldSubscription,
                DeactivationReason.DEACTIVATED_BY_USER);

        mctsSubscriber = subscriberDataService.findByNumber(9999911122L).get(0);

        // create new subscription for pregnancy pack in Active state such that
        // next OBD date falls on current date
        mctsSubscriber.setDateOfBirth(null);
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now().minusDays(90));
        subscriberDataService.update(mctsSubscriber);
        Subscription newSubscription = subscriptionService.createSubscription(
                mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        transactionManager.commit(status);

        String expectedJsonResponse = "{\"inboxSubscriptionDetailList\":[{\"subscriptionId\":\""
                + newSubscription.getSubscriptionId()
                + "\",\"subscriptionPack\":\"pregnancyPack\",\"inboxWeekId\":\"w1_1\",\"contentFileName\":\"w1_1.wav\"}]}";

        HttpGet httpGet = createHttpGet(true, "9999911122", true,
                VALID_CALL_ID);
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                expectedJsonResponse, ADMIN_USERNAME, ADMIN_PASSWORD));
    }

    /**
     * To check NMS is able to make available a single message of current week
     * in inbox with single message per week configuration . when:
     * a) user's MSISDN is subscribed for Pregnancy Pack.
     * b)user's MSISDN status is completed for an old subscription of child Pack.
     **/
    @Test
    public void verifyFT112() throws IOException, InterruptedException {
        //update pregnancy pack to 1 message/week
        sh.pregnancyPackFor1MessagePerWeek(subscriptionPackMessageDataService);

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        // set DOB for old child pack
        mctsSubscriber.setDateOfBirth(DateTime.now().minusDays(100));
        mctsSubscriber.setLastMenstrualPeriod(null);
        subscriberDataService.create(mctsSubscriber);

        // create subscription for child pack and complete it more than a week
        // ago
        Subscription oldSubscription = subscriptionService.createSubscription(
                mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        subscriptionService.updateStartDate(oldSubscription, DateTime.now()
                .minusDays(344));

        mctsSubscriber = subscriberDataService.findByNumber(9999911122L).get(0);

        // create new subscription for pregnancy pack in Active state such that
        // next OBD date falls on current date
        mctsSubscriber.setDateOfBirth(null);
        mctsSubscriber.setLastMenstrualPeriod(DateTime.now().minusDays(90));
        subscriberDataService.update(mctsSubscriber);
        Subscription newSubscription = subscriptionService.createSubscription(
                mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.pregnancyPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        transactionManager.commit(status);

        String expectedJsonResponse = "{\"inboxSubscriptionDetailList\":[{\"subscriptionId\":\""
                + newSubscription.getSubscriptionId()
                + "\",\"subscriptionPack\":\"pregnancyPack\",\"inboxWeekId\":\"w1_1\",\"contentFileName\":\"w1_1.wav\"}]}";

        HttpGet httpGet = createHttpGet(true, "9999911122", true,
                VALID_CALL_ID);
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                expectedJsonResponse, ADMIN_USERNAME, ADMIN_PASSWORD));
    }

    /**
     * To check NMS is able to make available a single message of current week
     * in inbox with single message per week configuration . when:
     * a) user's MSISDN is subscribed for Child Pack.
     * b)user's MSISDN is deactivated for an old subscription of Child Pack.
     **/

    @Test
    public void verifyFT115() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        // set DOB for old child pack
        mctsSubscriber.setDateOfBirth(DateTime.now().minusDays(180));
        mctsSubscriber.setLastMenstrualPeriod(null);
        subscriberDataService.create(mctsSubscriber);

        // create old subscription for child pack and deactivate it
        Subscription oldSubscription = subscriptionService.createSubscription(
                mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);
        subscriptionService.deactivateSubscription(oldSubscription,
                DeactivationReason.DEACTIVATED_BY_USER);

        mctsSubscriber = subscriberDataService.findByNumber(9999911122L).get(0);

        // create new subscription for child pack in Active state such that
        // next OBD date falls on current date
        mctsSubscriber.setDateOfBirth(DateTime.now());
        subscriberDataService.update(mctsSubscriber);
        Subscription newSubscription = subscriptionService.createSubscription(
                mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        transactionManager.commit(status);

        String expectedJsonResponse = "{\"inboxSubscriptionDetailList\":[{\"subscriptionId\":\""
                + newSubscription.getSubscriptionId()
                + "\",\"subscriptionPack\":\"childPack\",\"inboxWeekId\":\"w1_1\",\"contentFileName\":\"w1_1.wav\"}]}";

        HttpGet httpGet = createHttpGet(true, "9999911122", true,
                VALID_CALL_ID);
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                expectedJsonResponse, ADMIN_USERNAME, ADMIN_PASSWORD));
    }

    /**
     * To check NMS is able to make available a single message of current week
     * in inbox with single message per week configuration . when
     * a) user's MSISDN is subscribed for Child Pack.
     * b)user's MSISDN status is completed(with in 7 days) for an old subscription of Child Pack.
     **/

    @Test
    public void verifyFT116() throws IOException, InterruptedException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        Subscriber mctsSubscriber = new Subscriber(9999911122L);
        // set DOB for old child pack
        mctsSubscriber.setDateOfBirth(DateTime.now().minusDays(180));
        mctsSubscriber.setLastMenstrualPeriod(null);
        subscriberDataService.create(mctsSubscriber);

        // create old subscription for child pack
        Subscription oldSubscription = subscriptionService.createSubscription(
                mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        // update old child pack subscription to mark complete setting the
        // subscription to have ended less than a week ago
        subscriptionService.updateStartDate(oldSubscription, DateTime.now()
                .minusDays(337));

        mctsSubscriber = subscriberDataService.findByNumber(9999911122L).get(0);

        // create new subscription to child pack in Active state such that
        // next OBD date falls on current date
        mctsSubscriber.setDateOfBirth(DateTime.now());
        subscriberDataService.update(mctsSubscriber);
        Subscription newSubscription = subscriptionService.createSubscription(
                mctsSubscriber, 9999911122L, rh.hindiLanguage(), sh.childPack(),
                SubscriptionOrigin.MCTS_IMPORT);

        transactionManager.commit(status);

        Pattern oldchildPackPattern = Pattern
                .compile(".*\"subscriptionId\":\""
                        + oldSubscription.getSubscriptionId()
                        + "\",\"subscriptionPack\":\"childPack\",\"inboxWeekId\":\"w48_1\",\"contentFileName\":\"w48_1.wav.*");
        Pattern newchildPackPattern = Pattern
                .compile(".*\"subscriptionId\":\""
                        + newSubscription.getSubscriptionId()
                        + "\",\"subscriptionPack\":\"childPack\",\"inboxWeekId\":\"w1_1\",\"contentFileName\":\"w1_1.wav.*");

        HttpGet httpGet = createHttpGet(true, "9999911122", true,
                VALID_CALL_ID);
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                oldchildPackPattern, ADMIN_USERNAME, ADMIN_PASSWORD));
        assertTrue(SimpleHttpClient.execHttpRequest(httpGet, HttpStatus.SC_OK,
                newchildPackPattern, ADMIN_USERNAME, ADMIN_PASSWORD));
    }
}

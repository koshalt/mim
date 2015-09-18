package org.motechproject.nms.testing.it.imi;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.motechproject.alerts.contract.AlertsDataService;
import org.motechproject.alerts.domain.Alert;
import org.motechproject.nms.imi.service.SettingsService;
import org.motechproject.nms.imi.service.SmsNotificationService;
import org.motechproject.nms.testing.service.TestingService;
import org.motechproject.server.config.SettingsFacade;
import org.motechproject.testing.osgi.BasePaxIT;
import org.motechproject.testing.osgi.container.MotechNativeTestContainerFactory;
import org.motechproject.testing.utils.TestContext;
import org.ops4j.pax.exam.ExamFactory;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import javax.inject.Inject;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
@ExamFactory(MotechNativeTestContainerFactory.class)
public class ImiSMSNotificationServiceBundleIT extends BasePaxIT {

    private static final String SMS_NOTIFICATION_URL = "imi.sms.notification.url";

    private static final String MAX_NOTIFICATION_RETRY_COUNT = "imi.notification_retry_count";

    @Inject
    SettingsService settingsService;

    @Inject
    SmsNotificationService smsNotificationService;

    @Inject
    AlertsDataService alertsDataService;

    @Inject
    TestingService testingService;

    SettingsFacade settingsFacade;

    String oldEndpoint, oldRetryCount;

    @Before
    public void cleanupDatabase() {
        testingService.clearDatabase();

        settingsFacade = settingsService.getSettingsFacade();
        oldEndpoint = settingsFacade.getProperty(SMS_NOTIFICATION_URL);
        oldRetryCount = settingsFacade
                .getProperty(MAX_NOTIFICATION_RETRY_COUNT);
    }

    @After
    public void restore() {
        settingsFacade.setProperty(SMS_NOTIFICATION_URL, oldEndpoint);
        settingsFacade.setProperty(MAX_NOTIFICATION_RETRY_COUNT, oldRetryCount);
    }

    // JIRA issue : https://applab.atlassian.net/browse/NIP-47
    // To test the fix of JIRA isssue 47
    // To check NMS behavious after getting 201 from the IMI    

    @Test
    public void verifyNIP47() {

        // linking with an API which always returns 201
        String newUrl = String.format(
                "http://localhost:%d/testing/sendSMS/outbound",
                TestContext.getJettyPort());
        settingsFacade.setProperty(SMS_NOTIFICATION_URL, newUrl);
        assertTrue(smsNotificationService.sendSms(1234567890l,"testNIP47"));
        List<Alert> alert = alertsDataService
                .findByExternalId("SmsNotification");
        assertEquals(0, alert.size());

    }

}

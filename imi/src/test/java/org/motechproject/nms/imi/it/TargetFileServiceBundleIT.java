package org.motechproject.nms.imi.it;

import org.apache.commons.codec.binary.Hex;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.motechproject.nms.imi.service.SettingsService;
import org.motechproject.nms.imi.service.TargetFileService;
import org.motechproject.nms.imi.service.contract.TargetFileNotification;
import org.motechproject.nms.kilkari.domain.CallRetry;
import org.motechproject.nms.kilkari.domain.CallStage;
import org.motechproject.nms.kilkari.domain.DeactivationReason;
import org.motechproject.nms.kilkari.domain.Subscriber;
import org.motechproject.nms.kilkari.domain.Subscription;
import org.motechproject.nms.kilkari.domain.SubscriptionOrigin;
import org.motechproject.nms.kilkari.domain.SubscriptionPack;
import org.motechproject.nms.kilkari.domain.SubscriptionStatus;
import org.motechproject.nms.kilkari.repository.CallRetryDataService;
import org.motechproject.nms.kilkari.repository.SubscriberDataService;
import org.motechproject.nms.kilkari.repository.SubscriptionDataService;
import org.motechproject.nms.kilkari.repository.SubscriptionPackDataService;
import org.motechproject.nms.kilkari.service.SubscriberService;
import org.motechproject.nms.kilkari.service.SubscriptionService;
import org.motechproject.nms.props.domain.DayOfTheWeek;
import org.motechproject.nms.region.domain.Circle;
import org.motechproject.nms.region.domain.District;
import org.motechproject.nms.region.domain.Language;
import org.motechproject.nms.region.domain.LanguageLocation;
import org.motechproject.nms.region.domain.State;
import org.motechproject.nms.region.repository.CircleDataService;
import org.motechproject.nms.region.repository.DistrictDataService;
import org.motechproject.nms.region.repository.LanguageDataService;
import org.motechproject.nms.region.repository.LanguageLocationDataService;
import org.motechproject.nms.region.repository.StateDataService;
import org.motechproject.testing.osgi.BasePaxIT;
import org.motechproject.testing.osgi.container.MotechNativeTestContainerFactory;
import org.ops4j.pax.exam.ExamFactory;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
@ExamFactory(MotechNativeTestContainerFactory.class)
public class TargetFileServiceBundleIT extends BasePaxIT {

    private static final String LOCAL_OBD_DIR = "imi.local_obd_dir";
    private static final String REMOTE_OBD_DIR = "imi.remote_obd_dir";


    private String localObdDirBackup;
    private String remoteObdDirBackup;

    @Inject
    TargetFileService targetFileService;

    @Inject
    SubscriptionService subscriptionService;

    @Inject
    SubscriptionDataService subscriptionDataService;

    @Inject
    SubscriberDataService subscriberDataService;

    @Inject
    SubscriptionPackDataService subscriptionPackDataService;

    @Inject
    CallRetryDataService callRetryDataService;

    @Inject
    LanguageDataService languageDataService;

    @Inject
    LanguageLocationDataService languageLocationDataService;

    @Inject
    private CircleDataService circleDataService;

    @Inject
    private StateDataService stateDataService;

    @Inject
    private DistrictDataService districtDataService;

    @Inject
    private SubscriberService subscriberService;

    @Inject
    SettingsService settingsService;

    private void setupDatabase() {
        for (Subscription subscription: subscriptionDataService.retrieveAll()) {
            subscription.setStatus(SubscriptionStatus.COMPLETED);
            subscription.setEndDate(new DateTime().withDate(2011, 8, 1));

            subscriptionDataService.update(subscription);
        }

        subscriptionService.deleteAll();
        subscriberDataService.deleteAll();
        languageLocationDataService.deleteAll();
        languageDataService.deleteAll();
        districtDataService.deleteAll();
        stateDataService.deleteAll();
        circleDataService.deleteAll();
        callRetryDataService.deleteAll();

        District district = new District();
        district.setName("District 1");
        district.setRegionalName("District 1");
        district.setCode(1L);

        State state = new State();
        state.setName("State 1");
        state.setCode(1L);
        state.getDistricts().add(district);

        stateDataService.create(state);

        Circle aa = new Circle("AA");
        Circle bb = new Circle("BB");

        LanguageLocation hindi = new LanguageLocation("HI", aa, new Language("Hindi"), false);
        hindi.getDistrictSet().add(district);
        hindi = languageLocationDataService.create(hindi);

        LanguageLocation urdu = new LanguageLocation("UR", aa, new Language("Urdu"), false);
        urdu.getDistrictSet().add(district);
        urdu = languageLocationDataService.create(urdu);

        SubscriptionPack childPack = subscriptionPackDataService.byName("childPack");
        SubscriptionPack pregnancyPack = subscriptionPackDataService.byName("pregnancyPack");

        Subscriber subscriber1 = new Subscriber(1111111111L, hindi, aa);
        subscriber1.setLastMenstrualPeriod(DateTime.now().minusDays(90)); // startDate will be today
        subscriberDataService.create(subscriber1);

        subscriptionService.createSubscription(1111111111L, hindi, pregnancyPack, SubscriptionOrigin.MCTS_IMPORT);

        Subscriber subscriber2 = new Subscriber(2222222222L, urdu, bb);
        subscriber2.setDateOfBirth(DateTime.now()); // startDate will be today
        subscriberDataService.create(subscriber2);

        Subscription s = subscriptionService.createSubscription(2222222222L, urdu, childPack,
                SubscriptionOrigin.MCTS_IMPORT);
        subscriptionService.deactivateSubscription(s, DeactivationReason.CHILD_DEATH);

        //NMS_FT_152
        Subscriber subscriber3 = new Subscriber(6666666666L, urdu, bb);
        subscriber3.setDateOfBirth(DateTime.now().plusDays(1));
        subscriber3 = subscriberDataService.create(subscriber3);


        //create subscription for future date
        Subscription s3 = subscriptionService.createSubscription(6666666666L, urdu, childPack,
                SubscriptionOrigin.MCTS_IMPORT);
        Set<Subscription> subscriptions = new HashSet<Subscription>();
        subscriptions.add(s3);

        subscriber3.setSubscriptions(subscriptions);
        subscriberDataService.update(subscriber3);

        //update dob of subscriber and hence subscription start date
        subscriber3.setDateOfBirth(DateTime.now());
        subscriberService.update(subscriber3);

        //NMS_FT_138
        //NMS_FT_144
        //NMS_FT_145
        //NMS_FT_146
        //NMS_FT_147
        callRetryDataService.create(new CallRetry("33333333-3333-3333-3333-333333333333", 5555555555L,
                DayOfTheWeek.today(), CallStage.RETRY_2,
                "w1_m1.wav", "w1_1", hindi.getCode(), aa.getName(), SubscriptionOrigin.IVR));
        //NMS_FT_139
        callRetryDataService.create(new CallRetry("44444444-4444-4444-4444-444444444444", 9999999999L, DayOfTheWeek.today(), CallStage.RETRY_LAST,
                "w1_m1.wav", "w1_1", hindi.getCode(), aa.getName(), SubscriptionOrigin.IVR));
        callRetryDataService.create(new CallRetry("11111111-1111-1111-1111-111111111111", 3333333333L,
                DayOfTheWeek.today(), CallStage.RETRY_1, "w1_m1.wav", "w1_1", hindi.getCode(), aa.getName(), 
                SubscriptionOrigin.IVR));
        callRetryDataService.create(new CallRetry("22222222-2222-2222-2222-222222222222", 4444444444L,
                DayOfTheWeek.today().nextDay(), CallStage.RETRY_1, "w1_m1.wav", "w1_1", hindi.getCode(),
                bb.getName(), SubscriptionOrigin.MCTS_IMPORT));
    }


    private String setupTestDir(String property, String dir) {
        String backup = settingsService.getSettingsFacade().getProperty(property);
        File directory = new File(System.getProperty("user.home"), dir);
        directory.mkdirs();
        settingsService.getSettingsFacade().setProperty(property, directory.getAbsolutePath());
        return backup;
    }


    @Before
    public void setupSettings() {
        localObdDirBackup = setupTestDir(LOCAL_OBD_DIR, "obd-local-dir-it");
        remoteObdDirBackup = setupTestDir(REMOTE_OBD_DIR, "obd-remote-dir-it");
    }


    @After
    public void restoreSettings() {
        settingsService.getSettingsFacade().setProperty(REMOTE_OBD_DIR, remoteObdDirBackup);
        settingsService.getSettingsFacade().setProperty(LOCAL_OBD_DIR, localObdDirBackup);
    }


    @Test
    public void testTargetFileGeneration() throws NoSuchAlgorithmException, IOException {
        setupDatabase();
        List<Long> msisdns = new ArrayList<>();
        List<String> subscriptionIds = new ArrayList<>();
        String[] values;
        String line;
        TargetFileNotification tfn = targetFileService.generateTargetFile();
        assertNotNull(tfn);

        // Should not pickup subscription2 because its status is COMPLETED nor callRetry 22222222-2222-2222-2222-222222222222
        // because it's for tomorrow
        assertEquals(5, (int) tfn.getRecordCount());

        //read the file to get checksum & record count
        File targetDir = new File(settingsService.getSettingsFacade().getProperty("imi.local_obd_dir"));
        File targetFile = new File(targetDir, tfn.getFileName());
        MessageDigest md = MessageDigest.getInstance("MD5");
        int recordCount = 0;
        try (InputStream is = Files.newInputStream(targetFile.toPath());
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            DigestInputStream dis = new DigestInputStream(is, md);
            while ((line = reader.readLine()) != null) {
                values = line.split(",");
                msisdns.add(Long.parseLong(values[2]));
                subscriptionIds.add(values[0].split(":")[1]);
                recordCount++;
            }
        }
        String md5Checksum = new String(Hex.encodeHex(md.digest()));

        assertEquals((int)tfn.getRecordCount(), recordCount);

        assertEquals(tfn.getChecksum(), md5Checksum);
        assertTrue(msisdns.contains(1111111111L));
        assertTrue(msisdns.contains(6666666666L));
        assertTrue(msisdns.contains(3333333333L));
        assertTrue(msisdns.contains(5555555555L));
        assertTrue(msisdns.contains(9999999999L));
        assertTrue(subscriptionIds.contains("33333333-3333-3333-3333-333333333333"));
        assertTrue(subscriptionIds.contains("44444444-4444-4444-4444-444444444444"));
        assertTrue(subscriptionIds.contains("11111111-1111-1111-1111-111111111111"));
    }


    @Test
    public void testServicePresent() {
        assertTrue(targetFileService != null);
    }


    // un-ignore to create a large sample OBD file
    @Ignore
    public void createLargeFile() {
        SubscriptionHelper sh = new SubscriptionHelper(subscriptionService, subscriberDataService,
                subscriptionPackDataService, languageDataService, languageLocationDataService, circleDataService,
                stateDataService, districtDataService);

        subscriptionService.deleteAll();
        subscriberDataService.deleteAll();
        languageLocationDataService.deleteAll();
        languageDataService.deleteAll();
        districtDataService.deleteAll();
        stateDataService.deleteAll();
        circleDataService.deleteAll();
        callRetryDataService.deleteAll();

        for (int i=0 ; i<1000 ; i++) {
            sh.mksub(SubscriptionOrigin.MCTS_IMPORT, DateTime.now());
        }

        for (int i=0 ; i<1000 ; i++) {

            int randomWeek = (int) (Math.random() * sh.getChildPack().getWeeks());
            Subscription sub = sh.mksub(
                    SubscriptionOrigin.MCTS_IMPORT,
                    DateTime.now().minusDays(7 * randomWeek - 1)
            );
            callRetryDataService.create(new CallRetry(
                    sub.getSubscriptionId(),
                    sub.getSubscriber().getCallingNumber(),
                    DayOfTheWeek.today(),
                    CallStage.RETRY_1,
                    sh.getContentMessageFile(sub, randomWeek),
                    sh.getWeekId(sub, randomWeek),
                    sh.getLanguageLocationCode(sub),
                    sh.getCircle(sub),
                    SubscriptionOrigin.MCTS_IMPORT
            ));
        }

        TargetFileNotification tfn = targetFileService.generateTargetFile();
        assertNotNull(tfn);
        getLogger().debug("Generated {}", tfn.getFileName());
    }


    //todo: test success notification is sent to the IVR system
}

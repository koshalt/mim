package org.motechproject.nms.testing.it.rch;

import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.motechproject.commons.date.util.DateUtil;
import org.motechproject.event.MotechEvent;
import org.motechproject.nms.flw.domain.FrontLineWorker;
import org.motechproject.nms.flw.repository.FrontLineWorkerDataService;
import org.motechproject.nms.imi.service.SettingsService;
import org.motechproject.nms.kilkari.domain.MctsMother;
import org.motechproject.nms.kilkari.domain.SubscriptionPack;
import org.motechproject.nms.kilkari.domain.SubscriptionPackMessage;
import org.motechproject.nms.kilkari.domain.SubscriptionPackType;
import org.motechproject.nms.kilkari.repository.MctsMotherDataService;
import org.motechproject.nms.kilkari.repository.SubscriptionDataService;
import org.motechproject.nms.kilkari.repository.SubscriptionPackDataService;
import org.motechproject.nms.rch.service.RchWebServiceFacade;
import org.motechproject.nms.rch.service.RchWsImportService;
import org.motechproject.nms.rch.utils.Constants;
import org.motechproject.nms.region.domain.*;
import org.motechproject.nms.region.repository.*;
import org.motechproject.nms.rejectionhandler.domain.ChildImportRejection;
import org.motechproject.nms.rejectionhandler.domain.FlwImportRejection;
import org.motechproject.nms.rejectionhandler.repository.ChildRejectionDataService;
import org.motechproject.nms.rejectionhandler.repository.FlwImportRejectionDataService;
import org.motechproject.nms.rejectionhandler.repository.MotherRejectionDataService;
import org.motechproject.nms.testing.it.helperUtils.HelperUtils;
import org.motechproject.nms.testing.it.rch.util.RchImportTestHelper;
import org.motechproject.nms.testing.service.TestingService;
import org.motechproject.scheduler.service.MotechSchedulerService;
import org.motechproject.testing.osgi.BasePaxIT;
import org.motechproject.testing.osgi.container.MotechNativeTestContainerFactory;
import org.motechproject.testing.osgi.http.SimpleHttpServer;
import org.ops4j.pax.exam.ExamFactory;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.inject.Inject;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import static java.util.Collections.singletonList;
import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
@ExamFactory(MotechNativeTestContainerFactory.class)
public class RchWebServiceFacadeBundleIT extends BasePaxIT {

    @Inject
    private HttpService httpService;

    @Inject
    private RchWebServiceFacade rchWebServiceFacade;

    @Inject
    private RchWsImportService rchWsImportService;

    @Inject
    private MotechSchedulerService schedulerService;

    @Inject
    private StateDataService stateDataService;

    @Inject
    private DistrictDataService districtDataService;

    @Inject
    private SubscriptionPackDataService subscriptionPackDataService;

    @Inject
    private TestingService testingService;

    @Inject
    private SettingsService settingsService;

    @Inject
    private MotherRejectionDataService motherRejectionDataService;

    @Inject
    private ChildRejectionDataService childRejectionDataService;

    @Inject
    private MctsMotherDataService mctsMotherDataService;

    @Inject
    private FlwImportRejectionDataService flwImportRejectionDataService;

    @Inject
    private FrontLineWorkerDataService frontLineWorkerDataService;

    @Inject
    private VillageDataService villageDataService;

    @Inject
    private TalukaDataService talukaDataService;

    @Inject
    private HealthFacilityDataService healthFacilityDataService;

    @Inject
    private HealthSubFacilityDataService healthSubFacilityDataService;

    @Inject
    private HealthBlockDataService healthBlockDataService;

    @Inject
    private SubscriptionDataService subscriptionDataService;

    @Inject
    PlatformTransactionManager transactionManager;

    @Before
    public void setUp() throws ServletException, NamespaceException {
        testingService.clearDatabase();
        State state = stateDataService.create(new State("My State", 21L));

        District district = new District();
        district.setCode(4L);
        district.setState(state);
        district.setName("District_Name 4");
        district.setRegionalName("Regional Name 4");
        districtDataService.create(district);


        Taluka taluka = new Taluka();
        taluka.setRegionalName("Taluka Regional");
        taluka.setName("Taluka_Name 1");
        taluka.setCode("0046");
        taluka.setIdentity(55);

        taluka.setDistrict(district);
        district.setTalukas(new ArrayList<>(singletonList(taluka)));
        talukaDataService.create(taluka);

        taluka = talukaDataService.retrieveAll().get(0);
        HealthBlock healthBlock = new HealthBlock();
        healthBlock.setCode(113L);
        healthBlock.setName("HealthBlock_Name 1");
        healthBlock.setRegionalName("HB1");
        healthBlock.setHq("An HQ");
        healthBlock.setStateIdOID(stateDataService.retrieveAll().get(0).getId());
        healthBlock.setTalukaIdOID(taluka.getId());

        healthBlock.setDistrict(district);
        healthBlockDataService.create(healthBlock);

        //TODO HARITHA commented 2 lines m-n taluka hb
//        healthBlock.addTaluka(taluka);
//
//        taluka.addHealthBlock(healthBlock);

        HealthFacilityType phcType = new HealthFacilityType();
        phcType.setCode(11L);
        phcType.setName("PHC TYPE 111");

        HealthFacility facility = new HealthFacility();
        facility.setName("PHC_NAME 3");
        facility.setRegionalName("Regional PHC 3");
        facility.setCode(111L);
        facility.setHealthFacilityType(phcType);
        facility.setDistrictIdOID(districtDataService.retrieveAll().get(0).getId());
        facility.setStateIdOID(stateDataService.retrieveAll().get(0).getId());
        facility.setTalukaIdOID(talukaDataService.retrieveAll().get(0).getId());
        healthBlock.setHealthFacilities(new ArrayList<>(singletonList(facility)));
        facility.setHealthBlock(healthBlock);
        healthFacilityDataService.create(facility);

        HealthSubFacility subcentre = new HealthSubFacility();
        subcentre.setName("SubCentre_Name 1");
        subcentre.setRegionalName("Regional sub name");
        subcentre.setCode(333L);
        subcentre.setTalukaIdOID(talukaDataService.retrieveAll().get(0).getId());
        subcentre.setStateIdOID(stateDataService.retrieveAll().get(0).getId());
        subcentre.setDistrictIdOID(districtDataService.retrieveAll().get(0).getId());
        subcentre.setHealthFacility(facility);
        facility.setHealthSubFacilities(new ArrayList<>(singletonList(subcentre)));

        healthSubFacilityDataService.create(subcentre);

        SubscriptionPack pregnancyPack = new SubscriptionPack("prg", SubscriptionPackType.PREGNANCY, 70, 10,
                Collections.<SubscriptionPackMessage>emptyList());
        SubscriptionPack childPack = new SubscriptionPack("child", SubscriptionPackType.CHILD, 5000, 6,
                Collections.<SubscriptionPackMessage>emptyList());


        subscriptionPackDataService.create(pregnancyPack);
        subscriptionPackDataService.create(childPack);

    }


    @Test
    @Ignore
    public void shouldSerializeMothersDataFromSoapResponse() throws IOException {
        String response = RchImportTestHelper.getRchMothersResponseData();

        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("rchEndpoint", 200, response);

        URL endpoint = new URL(url);
        LocalDate referenceDate = LocalDate.now().minusDays(1);

        //TODO resolve problem with class loader
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWebServiceFacade.getClass().getClassLoader());
        boolean status = rchWebServiceFacade.getMothersData(referenceDate, referenceDate, endpoint, 21l);
        Thread.currentThread().setContextClassLoader(cl);

        assertTrue(status);
    }

    @Test
    @Ignore
    public void shouldSerializeChildrenDataFromSoapResponse() throws IOException {
        String response = RchImportTestHelper.getRchChildrenResponseData();

        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("rchEndpoint", 200, response);

        URL endpoint = new URL(url);
        LocalDate referenceDate = LocalDate.now().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWebServiceFacade.getClass().getClassLoader());
        boolean status = rchWebServiceFacade.getChildrenData(referenceDate, referenceDate, endpoint, 21l);
        Thread.currentThread().setContextClassLoader(cl);

        assertTrue(status);
    }

    @Test
    @Ignore
    public void shouldSerializeAshaDataFromSoapResponse() throws IOException {
        String response = RchImportTestHelper.getAnmAshaResponseData();

        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("rchEndpoint", 200, response);

        URL endpoint = new URL(url);
        LocalDate referenceDate = LocalDate.now().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWebServiceFacade.getClass().getClassLoader());
        boolean status = rchWebServiceFacade.getAnmAshaData(referenceDate, referenceDate, endpoint, 21l);
        Thread.currentThread().setContextClassLoader(cl);

        assertTrue(status);
    }

    @Test
    public void checkForZeroMother() throws IOException {
        String response = RchImportTestHelper.getRchChildrenResponseDataForZeroMother();
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("rchEndpoint", 200, response);

        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(7);
        LocalDate yesterday = DateUtil.today().minusDays(1);

        // this CL workaround is for an issue with PAX IT logging messing things up
        // shouldn't affect production
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());

        // setup motech event
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        List<Long> a = new ArrayList<>();
        a.add(21L);
        MotechEvent event = new MotechEvent("foobar", params);
        rchWsImportService.importFromRch(a, yesterday, endpoint);
//        rchWsImportService.importRchMothersData(event);
        Thread.currentThread().setContextClassLoader(rchWebServiceFacade.getClass().getClassLoader());

        MotechEvent event1 = new MotechEvent(org.motechproject.nms.rch.utils.Constants.RCH_CHILD_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readChildResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Thread.currentThread().setContextClassLoader(cl);

//        Should reject non ASHA FLWs

        List<ChildImportRejection> childImportRejections = childRejectionDataService.retrieveAll();
        assertEquals(0, childImportRejections.size());
        List<MctsMother> mothers = HelperUtils.retrieveAllMothers(mctsMotherDataService);
        assertEquals(0, mothers.size());
    }

    @Test
    @Ignore
    public void testChildRCHImport() throws IOException {
        String response = RchImportTestHelper.getRchChildrenResponseData();
        String remoteLocation = "/home/beehyv/IdeaProjects/nsp/testing/src/test/resources/rch";
        String fileName = "RCH_StateID_21_Child_Response.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("childendpoint", 200, response);
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        List<Long> a = new ArrayList<>();
        a.add(21L);
        // MotechEvent event = new MotechEvent("foobar", params);
        rchWsImportService.importChildFromRch(a, yesterday, endpoint);
        MotechEvent event1 = new MotechEvent(Constants.RCH_CHILD_READ, params);
        try {
            rchWebServiceFacade.readChildResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<ChildImportRejection> childImportRejectionList = childRejectionDataService.retrieveAll();
        assertEquals(1, childImportRejectionList.size());

    }

    @Test
    public void testAshaRCHImport() throws IOException{
        String response = RchImportTestHelper.getAnmAshaResponseData();
        String remoteLocation = "/home/beehyv/nms-nmsbugfix/testing/src/test/resources/rch";
        String fileName =  "rch-anm-asha-data.xml"; //done by vishnu
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("ashendpoint", 200, response);
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        List<Long> a = new ArrayList<>();
        a.add(21L);
        // MotechEvent event = new MotechEvent("foobar", params);
        rchWsImportService.importAshaFromRch(a, yesterday, endpoint);
        MotechEvent event1 = new MotechEvent(org.motechproject.nms.rch.utils.Constants.RCH_ASHA_READ, params);
        try {
            rchWebServiceFacade.readAshaResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<FlwImportRejection> flwImportRejectionList = flwImportRejectionDataService.retrieveAll();
        assertEquals(1, flwImportRejectionList.size());
        List<FrontLineWorker> frontLineWorkers = frontLineWorkerDataService.retrieveAll();
        assertEquals(2, frontLineWorkers.size());
    }



    /*To check village record creating through Rch xml import*/

    @Test
    public void testVillageRCHImport() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_Village_import.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("villageendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);

        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event1 = new MotechEvent(org.motechproject.nms.rch.utils.Constants.RCH_VILLAGE_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readVillageResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<Village> villages = villageDataService.retrieveAll();
        assertEquals(1, villages.size());
    }

    /*To check District record creating through Rch xml import*/

    @Test
    public void testDistrictRCHImport() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_District_import.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("districtendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        // MotechEvent event = new MotechEvent("foobar", params);

        MotechEvent event1 = new MotechEvent(Constants.RCH_DISTRICT_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readDistrictResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<District> districts = districtDataService.retrieveAll();
        assertEquals(2, districts.size());
    }

    /*To check taluka record creating through Rch xml import*/

    @Test
    public void testTalukaRCHImport() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_Taluka_import.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("talukaendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);

        // MotechEvent event = new MotechEvent("foobar", params);

        MotechEvent event1 = new MotechEvent(org.motechproject.nms.rch.utils.Constants.RCH_TALUKA_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readTalukaResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<Taluka> talukas = talukaDataService.retrieveAll();
        assertEquals(2, talukas.size());
    }

    /*To check HealthBlock record creating through Rch xml import*/

    @Test
    public void testHealthBlockRCHImport() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_HealthBlock_import.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("healthblockendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event1 = new MotechEvent(Constants.RCH_HEALTHBLOCK_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readHealthBlockResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<HealthBlock> healthBlocks = healthBlockDataService.retrieveAll();
        assertEquals(3, healthBlocks.size());
    }

    /*To check HealthFacility record creating through Rch xml import*/

    @Test
    public void testHealthFacilityRCHImport() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_HealthFacility_import.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("healthblockendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event1 = new MotechEvent(Constants.RCH_HEALTHFACILITY_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readHealthFacilityResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<HealthFacility> healthFacilities = healthFacilityDataService.retrieveAll();
        assertEquals(2, healthFacilities.size());


    }

    /*To check HealthSubFacility record creating through Rch xml import*/

    @Test
    public void testHealthSubFacilityRCHImport() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_HealthSubFacility_import.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("healthsubfacilityendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);

        // MotechEvent event = new MotechEvent("foobar", params);

        MotechEvent event1 = new MotechEvent(Constants.RCH_HEALTHSUBFACILITY_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readHealthSubFacilityResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<HealthSubFacility> healthSubFacilities = healthSubFacilityDataService.retrieveAll();
        assertEquals(2, healthSubFacilities.size());


    }

    /* Import duplicate District Id/Code within the state */

    @Test
    public void testDistrictNameupdation() throws IOException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        File filepath = new File("src/test/resources/rch");
        String remoteLocation1 = filepath.getAbsolutePath();
        String fileName1 = "RCH_District_import.xml";
        SimpleHttpServer simpleServer1 = SimpleHttpServer.getInstance();
        String url1 = simpleServer1.start("districtendpoint", 200, "abcd");
        URL endpoint1 = new URL(url1);
        LocalDate lastDateToCheck1 = DateUtil.today().minusDays(1);
        LocalDate yesterday1 = DateUtil.today().minusDays(1);
        ClassLoader cl1 = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params1 = new HashMap<>();
        params1.put(Constants.START_DATE_PARAM, lastDateToCheck1);
        params1.put(Constants.END_DATE_PARAM, yesterday1);
        params1.put(Constants.STATE_ID_PARAM, 21L);
        params1.put(Constants.ENDPOINT_PARAM, endpoint1);
        params1.put(Constants.REMOTE_LOCATION, remoteLocation1);
        params1.put(Constants.FILE_NAME, fileName1);
        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event1 = new MotechEvent(Constants.RCH_DISTRICT_READ_SUBJECT, params1);
        try {
            rchWebServiceFacade.readDistrictResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl1);
        List <District> districts1 = districtDataService.retrieveAll();
        assertEquals("District_test1", districts1.get(1).getName());
        transactionManager.commit(status);


        // Updating district name via another XML

        status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        File filepath1 = new File("src/test/resources/rch");
        String remoteLocation = filepath1.getAbsolutePath();
        String fileName = "RCH_District_name_update.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer1.start("districtendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event = new MotechEvent(Constants.RCH_DISTRICT_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readDistrictResponseFromFile(event);
        } catch (Exception e) {
            e.printStackTrace();
        }
        List <District> districts= districtDataService.retrieveAll();
        assertEquals("District_update1", districts.get(1).getName());
        transactionManager.commit(status);
    }

    /*To verify Taluka_Name updated successfully for an existing Taluka_ID*/

    @Test
    public void testTalukanameupdate() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_Taluka_import.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("talukaendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        List<Long> a = new ArrayList<>();
        a.add(21L);
        // MotechEvent event = new MotechEvent("foobar", params);
        rchWsImportService.importTalukaFromRch(a, yesterday, endpoint);
        MotechEvent event = new MotechEvent(Constants.RCH_TALUKA_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readTalukaResponseFromFile(event);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<Taluka> talukas = talukaDataService.retrieveAll();
        assertEquals("Taluka_test1", talukas.get(1).getName());

        // Updating taluka name via another XML

        File filepath1 = new File("src/test/resources/rch");
        String remoteLocation1 = filepath1.getAbsolutePath();
        String fileName1 = "RCH_Taluka_name_update.xml";
        SimpleHttpServer simpleServer1 = SimpleHttpServer.getInstance();
        String url1 = simpleServer1.start("talukaendpoint", 200, "abcd");
        URL endpoint1 = new URL(url1);
        LocalDate lastDateToCheck1 = DateUtil.today().minusDays(1);
        LocalDate yesterday1 = DateUtil.today().minusDays(1);
        ClassLoader cl1 = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params1 = new HashMap<>();
        params1.put(Constants.START_DATE_PARAM, lastDateToCheck1);
        params1.put(Constants.END_DATE_PARAM, yesterday1);
        params1.put(Constants.STATE_ID_PARAM, 21L);
        params1.put(Constants.ENDPOINT_PARAM, endpoint1);
        params1.put(Constants.REMOTE_LOCATION, remoteLocation1);
        params1.put(Constants.FILE_NAME, fileName1);
        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event1 = new MotechEvent(org.motechproject.nms.rch.utils.Constants.RCH_TALUKA_READ_SUBJECT, params1);
        try {
            rchWebServiceFacade.readTalukaResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl1);
        List<Taluka> talukas1 = talukaDataService.retrieveAll();
        assertEquals("Taluka_update1", talukas1.get(1).getName());

    }

    /*To verify HealthBlock_Name updated successfully for an existing HealthBlock_ID*/

    @Test
    public void testHealthBlocknameupdate() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_HealthBlock_import.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("rchendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event = new MotechEvent(Constants.RCH_HEALTHBLOCK_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readHealthBlockResponseFromFile(event);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<HealthBlock> healthBlocks = healthBlockDataService.retrieveAll();
        assertEquals("HB_test1", healthBlocks.get(1).getName());

        // Updating HealthBlock name via another XML

        File filepath1 = new File("src/test/resources/rch");
        String remoteLocation1 = filepath1.getAbsolutePath();
        String fileName1 = "RCH_HealthBlock_name_update.xml";
        SimpleHttpServer simpleServer1 = SimpleHttpServer.getInstance();
        String url1 = simpleServer1.start("rchendpoint", 200, "abcd");
        URL endpoint1 = new URL(url1);
        LocalDate lastDateToCheck1 = DateUtil.today().minusDays(1);
        LocalDate yesterday1 = DateUtil.today().minusDays(1);
        ClassLoader cl1 = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params1 = new HashMap<>();
        params1.put(Constants.START_DATE_PARAM, lastDateToCheck1);
        params1.put(Constants.END_DATE_PARAM, yesterday1);
        params1.put(Constants.STATE_ID_PARAM, 21L);
        params1.put(Constants.ENDPOINT_PARAM, endpoint1);
        params1.put(Constants.REMOTE_LOCATION, remoteLocation1);
        params1.put(Constants.FILE_NAME, fileName1);
        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event1 = new MotechEvent(Constants.RCH_HEALTHBLOCK_READ_SUBJECT, params1);
        try {
            rchWebServiceFacade.readHealthBlockResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl1);
        List<HealthBlock> healthBlocks1 = healthBlockDataService.retrieveAll();
        assertEquals("HB_update1", healthBlocks1.get(1).getName());

    }

    /* Import location data with duplicate Health Facility  ID */

    @Test
    public void testHealthFacilityWithDuplicateId() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_HealthFacility_import.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("healthfacilityendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event = new MotechEvent(Constants.RCH_HEALTHFACILITY_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readHealthFacilityResponseFromFile(event);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<HealthFacility> healthFacilities = healthFacilityDataService.retrieveAll();
        assertEquals("HF_Test1", healthFacilities.get(1).getName());

        // Duplicate Health Facility id via another XML

        File filepath1 = new File("src/test/resources/rch");
        String remoteLocation1 = filepath1.getAbsolutePath();
        String fileName1 = "RCH_HealthFacility_name_update.xml";
        SimpleHttpServer simpleServer1 = SimpleHttpServer.getInstance();
        String url1 = simpleServer1.start("healthfacilityendpoint", 200, "abcd");
        URL endpoint1 = new URL(url1);
        LocalDate lastDateToCheck1 = DateUtil.today().minusDays(1);
        LocalDate yesterday1 = DateUtil.today().minusDays(1);
        ClassLoader cl1 = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params1 = new HashMap<>();
        params1.put(Constants.START_DATE_PARAM, lastDateToCheck1);
        params1.put(Constants.END_DATE_PARAM, yesterday1);
        params1.put(Constants.STATE_ID_PARAM, 21L);
        params1.put(Constants.ENDPOINT_PARAM, endpoint1);
        params1.put(Constants.REMOTE_LOCATION, remoteLocation1);
        params1.put(Constants.FILE_NAME, fileName1);
        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event1 = new MotechEvent(Constants.RCH_HEALTHFACILITY_READ_SUBJECT, params1);
        try {
            rchWebServiceFacade.readHealthFacilityResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl1);
        List<HealthFacility> healthFacilities1 = healthFacilityDataService.retrieveAll();
        assertEquals("Test_update1", healthFacilities1.get(1).getName());
    }

    /*Import Location data with duplicate HealthSubFacility Id with in same state*/

    @Test
    public void testHealthSubFacilityWithDupId() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_HealthSubFacility_import.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("healthsubfacilityendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);

        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event = new MotechEvent(org.motechproject.nms.rch.utils.Constants.RCH_HEALTHSUBFACILITY_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readHealthSubFacilityResponseFromFile(event);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<HealthSubFacility> healthSubFacilities = healthSubFacilityDataService.retrieveAll();
        assertEquals("HSF_test1", healthSubFacilities.get(1).getName());

        // Update healthSubFacility name through another XML

        File filepath1 = new File("src/test/resources/rch");
        String remoteLocation1 = filepath1.getAbsolutePath();
        String fileName1 = "RCH_HealthSubFacility_name_update.xml";
        SimpleHttpServer simpleServer1 = SimpleHttpServer.getInstance();
        String url1 = simpleServer.start("healthsubfacilityendpoint", 200, "abcd");
        URL endpoint1 = new URL(url1);
        LocalDate lastDateToCheck1 = DateUtil.today().minusDays(1);
        LocalDate yesterday1 = DateUtil.today().minusDays(1);
        ClassLoader cl1 = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params1 = new HashMap<>();
        params1.put(Constants.START_DATE_PARAM, lastDateToCheck1);
        params1.put(Constants.END_DATE_PARAM, yesterday1);
        params1.put(Constants.STATE_ID_PARAM, 21L);
        params1.put(Constants.ENDPOINT_PARAM, endpoint1);
        params1.put(Constants.REMOTE_LOCATION, remoteLocation1);
        params1.put(Constants.FILE_NAME, fileName1);

        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event1 = new MotechEvent(org.motechproject.nms.rch.utils.Constants.RCH_HEALTHSUBFACILITY_READ_SUBJECT, params1);
        try {
            rchWebServiceFacade.readHealthSubFacilityResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl1);
        List<HealthSubFacility> healthSubFacilities1 = healthSubFacilityDataService.retrieveAll();
        assertEquals("HSF_update1", healthSubFacilities1.get(1).getName());
    }



    /*To  verify Districts with same codes(D101,D101,D101) existed in different states(S1,S2,S3) */

    @Test
    public void testSameDistrictcodeindiffstates() throws IOException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        File filepath = new File("src/test/resources/rch");
        String remoteLocation1 = filepath.getAbsolutePath();
        String fileName1 = "RCH_District_same_state_21.xml";
        SimpleHttpServer simpleServer1 = SimpleHttpServer.getInstance();
        String url1 = simpleServer1.start("districtendpoint", 200, "abcd");
        URL endpoint1 = new URL(url1);
        LocalDate lastDateToCheck1 = DateUtil.today().minusDays(1);
        LocalDate yesterday1 = DateUtil.today().minusDays(1);
        ClassLoader cl1 = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params1 = new HashMap<>();
        params1.put(Constants.START_DATE_PARAM, lastDateToCheck1);
        params1.put(Constants.END_DATE_PARAM, yesterday1);
        params1.put(Constants.STATE_ID_PARAM, 21L);
        params1.put(Constants.ENDPOINT_PARAM, endpoint1);
        params1.put(Constants.REMOTE_LOCATION, remoteLocation1);
        params1.put(Constants.FILE_NAME, fileName1);
        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event1 = new MotechEvent(Constants.RCH_DISTRICT_READ_SUBJECT, params1);
        try {
            rchWebServiceFacade.readDistrictResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl1);
        List <District> districts1 = districtDataService.retrieveAll();
        assertEquals(2, districts1.size());
        transactionManager.commit(status);


        // Same district codes in different states

        State state1 = stateDataService.create(new State("Other State", 22L));
        status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        File filepath1 = new File("src/test/resources/rch");
        String remoteLocation = filepath1.getAbsolutePath();
        String fileName = "RCH_District_same_state_22.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer1.start("districtendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 22L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event = new MotechEvent(Constants.RCH_DISTRICT_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readDistrictResponseFromFile(event);
        } catch (Exception e) {
            e.printStackTrace();
        }
        List <District> districts= districtDataService.retrieveAll();
        assertEquals(3, districts.size());
        transactionManager.commit(status);
    }

    /*To verify District update received with existing District_Name but with change in District_ID */

    @Test
    public void testDupDistrictnameinSameState() throws IOException {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        File filepath = new File("src/test/resources/rch");
        String remoteLocation1 = filepath.getAbsolutePath();
        String fileName1 = "RCH_District_import.xml";
        SimpleHttpServer simpleServer1 = SimpleHttpServer.getInstance();
        String url1 = simpleServer1.start("districtendpoint", 200, "abcd");
        URL endpoint1 = new URL(url1);
        LocalDate lastDateToCheck1 = DateUtil.today().minusDays(1);
        LocalDate yesterday1 = DateUtil.today().minusDays(1);
        ClassLoader cl1 = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params1 = new HashMap<>();
        params1.put(Constants.START_DATE_PARAM, lastDateToCheck1);
        params1.put(Constants.END_DATE_PARAM, yesterday1);
        params1.put(Constants.STATE_ID_PARAM, 21L);
        params1.put(Constants.ENDPOINT_PARAM, endpoint1);
        params1.put(Constants.REMOTE_LOCATION, remoteLocation1);
        params1.put(Constants.FILE_NAME, fileName1);
        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event1 = new MotechEvent(Constants.RCH_DISTRICT_READ_SUBJECT, params1);
        try {
            rchWebServiceFacade.readDistrictResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl1);
        List <District> districts1 = districtDataService.retrieveAll();
        assertEquals(2, districts1.size());
        transactionManager.commit(status);


        // Duplicate district names in same state

        status = transactionManager.getTransaction(new DefaultTransactionDefinition());        ;
        File filepath1 = new File("src/test/resources/rch");
        String remoteLocation = filepath1.getAbsolutePath();
        String fileName = "RCH_dup_district_in_same_state.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer1.start("districtendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event = new MotechEvent(Constants.RCH_DISTRICT_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readDistrictResponseFromFile(event);
        } catch (Exception e) {
            e.printStackTrace();
        }
        List <District> districts= districtDataService.retrieveAll();
        assertEquals(3, districts.size());
        transactionManager.commit(status);
    }

    /* To verify Village_Name updated successfully for an existing Village_ID*/

    @Test
    public void testVillageNameupdate() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_Village_import.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("villageendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);

        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event = new MotechEvent(org.motechproject.nms.rch.utils.Constants.RCH_VILLAGE_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readVillageResponseFromFile(event);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<Village> villages = villageDataService.retrieveAll();
        assertEquals("Village_test1", villages.get(0).getName());

        // Updating village name via another XML

        File filepath1 = new File("src/test/resources/rch");
        String remoteLocation1 = filepath1.getAbsolutePath();
        String fileName1 = "RCH_Village_name_update.xml";
        SimpleHttpServer simpleServer1 = SimpleHttpServer.getInstance();
        String url1 = simpleServer.start("villageendpoint", 200, "abcd");
        URL endpoint1 = new URL(url1);
        LocalDate lastDateToCheck1 = DateUtil.today().minusDays(1);
        LocalDate yesterday1 = DateUtil.today().minusDays(1);
        ClassLoader cl1 = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params1 = new HashMap<>();
        params1.put(Constants.START_DATE_PARAM, lastDateToCheck1);
        params1.put(Constants.END_DATE_PARAM, yesterday1);
        params1.put(Constants.STATE_ID_PARAM, 21L);
        params1.put(Constants.ENDPOINT_PARAM, endpoint1);
        params1.put(Constants.REMOTE_LOCATION, remoteLocation1);
        params1.put(Constants.FILE_NAME, fileName1);
        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event1 = new MotechEvent(org.motechproject.nms.rch.utils.Constants.RCH_VILLAGE_READ_SUBJECT, params1);
        try {
            rchWebServiceFacade.readVillageResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl1);
        List<Village> villages1 = villageDataService.retrieveAll();
        assertEquals("Village_update1", villages1.get(0).getName());


    }

    /*Verify import XML file without District_ID(Null) and with District_Name */

    @Test
    public void testDistrictwithoutID() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_District_without_id.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("districtendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event1 = new MotechEvent(org.motechproject.nms.rch.utils.Constants.RCH_DISTRICT_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readDistrictResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<District> districts = districtDataService.retrieveAll();
        assertEquals(1, districts.size());

    }


    /*Verify import XML file with Invalid  District_ID and with District_Name */

    @Test
    public void testDistrictwithInvalidID() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_invalid_district_id.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("districtendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        MotechEvent event1 = new MotechEvent(org.motechproject.nms.rch.utils.Constants.RCH_DISTRICT_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readDistrictResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<District> districts = districtDataService.retrieveAll();
        assertEquals(1, districts.size());
    }

    /*Import location data with duplicate Taluka Id in different state*/

    @Test
    public void testDuplicateTalukaidInDiffState() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_Taluka_import.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("talukaendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
//         MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event = new MotechEvent(Constants.RCH_TALUKA_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readTalukaResponseFromFile(event);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<Taluka> talukas = talukaDataService.retrieveAll();
        assertEquals(2, talukas.size());

        // Importing Duplicate Taluka_id in different state via XML

        State state1 = stateDataService.create(new State("Other State", 22L));

        District district1 = new District();
        district1.setCode(5L);
        district1.setState(state1);
        district1.setName("District_New");
        district1.setRegionalName("Regional Name 5");
        districtDataService.create(district1);

        File filepath1 = new File("src/test/resources/rch");
        String remoteLocation1 = filepath1.getAbsolutePath();
        String fileName1 = "RCH_dup_taluka_id.xml";
        SimpleHttpServer simpleServer1 = SimpleHttpServer.getInstance();
        String url1 = simpleServer1.start("talukaendpoint", 200, "abcd");
        URL endpoint1 = new URL(url1);
        LocalDate lastDateToCheck1 = DateUtil.today().minusDays(1);
        LocalDate yesterday1 = DateUtil.today().minusDays(1);
        ClassLoader cl1 = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params1 = new HashMap<>();
        params1.put(Constants.START_DATE_PARAM, lastDateToCheck1);
        params1.put(Constants.END_DATE_PARAM, yesterday1);
        params1.put(Constants.STATE_ID_PARAM, 22L);
        params1.put(Constants.ENDPOINT_PARAM, endpoint1);
        params1.put(Constants.REMOTE_LOCATION, remoteLocation1);
        params1.put(Constants.FILE_NAME, fileName1);
//        MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event1 = new MotechEvent(Constants.RCH_TALUKA_READ_SUBJECT, params1);
        try {
            rchWebServiceFacade.readTalukaResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl1);
        List<Taluka> talukas1 = talukaDataService.retrieveAll();
        assertEquals(3, talukas1.size());
    }




    /*Verify import taluka XML file with Taluka_ID and Taluka_Name without District_ID  */

    @Test
    public void testTalukaWithoutDistrictID() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_without_districtid_taluka_import.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("districtendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        MotechEvent event1 = new MotechEvent(org.motechproject.nms.rch.utils.Constants.RCH_TALUKA_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readTalukaResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<Taluka> talukas = talukaDataService.retrieveAll();
        assertEquals(1, talukas.size());


    }

    /* Verify import taluka XML file with invalid taluka ID/code*/

    @Test
    public void testTalukaWithInvalidID() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_invalid_taluka_id.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("talukaendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        List<Long> a = new ArrayList<>();
        a.add(21L);
        // MotechEvent event = new MotechEvent("foobar", params);
        rchWsImportService.importTalukaFromRch(a, yesterday, endpoint);
        MotechEvent event1 = new MotechEvent(org.motechproject.nms.rch.utils.Constants.RCH_TALUKA_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readTalukaResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<Taluka> talukas = talukaDataService.retrieveAll();
        assertEquals(2, talukas.size());


    }

    /* Verify import taluka XML file with invalid District ID/code */

    @Test
    public void testTalukaWithInvalidDistrictID() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_invalid_districtid_taluka_import.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("talukaendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        List<Long> a = new ArrayList<>();
        a.add(21L);
        // MotechEvent event = new MotechEvent("foobar", params);
        rchWsImportService.importTalukaFromRch(a, yesterday, endpoint);
        MotechEvent event1 = new MotechEvent(org.motechproject.nms.rch.utils.Constants.RCH_TALUKA_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readTalukaResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List talukas = talukaDataService.retrieveAll();
        assertEquals(1, talukas.size());
    }



    /*Verify import XML file without HealthBlock_Id and with HealthBlock_name,District_ID, Taluka_ID */

    @Test
    public void testHealthBlockWithoutId() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_without_HealthBlock_id.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("healthblockendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event1 = new MotechEvent(Constants.RCH_HEALTHBLOCK_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readHealthBlockResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<HealthBlock> healthBlocks = healthBlockDataService.retrieveAll();
        assertEquals(1, healthBlocks.size());
    }




    /*Verify import  HealthFacility XML file without ID and with HealthFacility_Name,HealthBlock_ID, District_ID, Taluka_ID */

    @Test
    public void testHealthFacilityWithoutId() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_without_HealthFacility_id.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("healthfacilityendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event1 = new MotechEvent(Constants.RCH_HEALTHFACILITY_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readHealthFacilityResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<HealthFacility> healthFacilities = healthFacilityDataService.retrieveAll();
        assertEquals(1, healthFacilities.size());
    }



    /*Verify import HealthSubFacility XML file without id and with HealthSubFacility_Name,
    HealthFacility_ID,District_ID,Taluka_ID */

    @Test
    public void testHealthSubFacilityWithoutId() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_without_HealthSubFacility_id.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("healthsubfacilityendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        // MotechEvent event = new MotechEvent("foobar", params);
        MotechEvent event1 = new MotechEvent(org.motechproject.nms.rch.utils.Constants.RCH_HEALTHSUBFACILITY_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readHealthSubFacilityResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<HealthSubFacility> healthSubFacilities = healthSubFacilityDataService.retrieveAll();
        assertEquals(1, healthSubFacilities.size());


    }



    /*Verify import Village Xml file without village_id  and with name,district_id,taluka_id*/

    @Test
    public void testVillageWithoutId() throws IOException {
        File filepath = new File("src/test/resources/rch");
        String remoteLocation = filepath.getAbsolutePath();
        String fileName = "RCH_without_Village_id.xml";
        SimpleHttpServer simpleServer = SimpleHttpServer.getInstance();
        String url = simpleServer.start("villageendpoint", 200, "abcd");
        URL endpoint = new URL(url);
        LocalDate lastDateToCheck = DateUtil.today().minusDays(1);
        LocalDate yesterday = DateUtil.today().minusDays(1);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(rchWsImportService.getClass().getClassLoader());
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.START_DATE_PARAM, lastDateToCheck);
        params.put(Constants.END_DATE_PARAM, yesterday);
        params.put(Constants.STATE_ID_PARAM, 21L);
        params.put(Constants.ENDPOINT_PARAM, endpoint);
        params.put(Constants.REMOTE_LOCATION, remoteLocation);
        params.put(Constants.FILE_NAME, fileName);
        MotechEvent event1 = new MotechEvent(org.motechproject.nms.rch.utils.Constants.RCH_VILLAGE_READ_SUBJECT, params);
        try {
            rchWebServiceFacade.readVillageResponseFromFile(event1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.currentThread().setContextClassLoader(cl);
        List<Village> villages = villageDataService.retrieveAll();
        assertEquals(0, villages.size());


    }
}



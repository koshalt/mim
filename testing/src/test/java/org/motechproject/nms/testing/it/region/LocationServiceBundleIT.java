package org.motechproject.nms.testing.it.region;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.motechproject.nms.region.domain.District;
import org.motechproject.nms.region.domain.HealthBlock;
import org.motechproject.nms.region.domain.HealthFacility;
import org.motechproject.nms.region.domain.HealthFacilityType;
import org.motechproject.nms.region.domain.HealthSubFacility;
import org.motechproject.nms.region.domain.State;
import org.motechproject.nms.region.domain.Taluka;
import org.motechproject.nms.region.domain.Village;
import org.motechproject.nms.region.repository.DistrictDataService;
import org.motechproject.nms.region.repository.HealthBlockDataService;
import org.motechproject.nms.region.repository.HealthFacilityDataService;
import org.motechproject.nms.region.repository.HealthFacilityTypeDataService;
import org.motechproject.nms.region.repository.HealthSubFacilityDataService;
import org.motechproject.nms.region.repository.StateDataService;
import org.motechproject.nms.region.repository.TalukaDataService;
import org.motechproject.nms.region.repository.VillageDataService;
import org.motechproject.nms.region.service.HealthBlockService;
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
import javax.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
@ExamFactory(MotechNativeTestContainerFactory.class)
public class LocationServiceBundleIT extends BasePaxIT {
    @Inject
    StateDataService stateDataService;

    @Inject
    DistrictDataService districtDataService;

    @Inject
    TalukaDataService talukaDataService;

    @Inject
    VillageDataService villageDataService;

    @Inject
    HealthBlockDataService healthBlockDataService;

    @Inject
    HealthBlockService healthBlockService;

    @Inject
    HealthFacilityTypeDataService healthFacilityTypeDataService;

    @Inject
    HealthFacilityDataService healthFacilityDataService;

    @Inject
    HealthSubFacilityDataService healthSubFacilityDataService;

    @Inject
    TestingService testingService;

    @Inject
    PlatformTransactionManager transactionManager;

    State state;
    District district;
    Taluka taluka;
    Village village;
    HealthBlock healthBlock;
    HealthFacilityType healthFacilityType;
    HealthFacility healthFacility;
    HealthSubFacility healthSubFacility;


    @Before
    public void doTheNeedful() {
        try{
            testingService.clearDatabase();
        }
        catch (Exception e){
            testingService.clearDatabase();
        }

        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        healthSubFacility = new HealthSubFacility();
        healthSubFacility.setName("Health Sub Facility 1");
        healthSubFacility.setRegionalName("Health Sub Facility 1");
        healthSubFacility.setCode(1L);

        healthFacilityType = new HealthFacilityType();
        healthFacilityType.setName("Health Facility Type 1");
        healthFacilityType.setCode(1L);

        healthFacility = new HealthFacility();
        healthFacility.setName("Health Facility 1");
        healthFacility.setRegionalName("Health Facility 1");
        healthFacility.setCode(1L);
        healthFacility.setHealthFacilityType(healthFacilityType);
        healthFacility.getHealthSubFacilities().add(healthSubFacility);

        healthBlock = new HealthBlock();
        healthBlock.setName("Health Block 1");
        healthBlock.setRegionalName("Health Block 1");
        healthBlock.setHq("Health Block 1 HQ");
        healthBlock.setCode(1L);
        healthBlock.getHealthFacilities().add(healthFacility);

        village = new Village();
        village.setName("Village 1");
        village.setRegionalName("Village 1");
        village.setVcode(1L);

        taluka = new Taluka();
        taluka.setName("Taluka 1");
        taluka.setRegionalName("Taluka 1");
        taluka.setIdentity(1);
        taluka.setCode("0004");
        taluka.getVillages().add(village);

        //TODO HARITHA commented 2 lines m-n taluka hb
//        taluka.addHealthBlock(healthBlock);
//
//
//        healthBlock.addTaluka(taluka);

        district = new District();
        district.setName("District 1");
        district.setRegionalName("District 1");
        district.setCode(1L);
        district.getTalukas().add(taluka);

        state = new State();
        state.setName("State 1");
        state.setCode(1L);
        state.getDistricts().add(district);
        transactionManager.commit(status);
    }

    @Test(expected = ConstraintViolationException.class)
    public void testCreateStateNoName() throws Exception {
        state.setName(null);

        stateDataService.create(state);
    }

    @Test(expected = ConstraintViolationException.class)
    public void testCreateDistrictNoName() throws Exception {
        district.setName(null);

        districtDataService.create(district);
    }

    @Test(expected = ConstraintViolationException.class)
    public void testCreateTalukaNoName() throws Exception {
        taluka.setName(null);

        talukaDataService.create(taluka);
    }

    @Test(expected = ConstraintViolationException.class)
    public void testCreateVillageNoName() throws Exception {
        village.setName(null);

        // Village is the leaf, I have to create something connected to the object graph so I save the
        // taluka (it's parent) instead
        talukaDataService.create(taluka);
    }

    @Test (expected = ConstraintViolationException.class)
    @Ignore // Remove once https://applab.atlassian.net/browse/MOTECH-1691 is resolved
    public void testCreateVillageNoCode() throws Exception {
        village.setVcode(0);
        village.setSvid(0);

        // Village is the leaf, I have to create something connected to the object graph so I save the
        // taluka (it's parent) instead
        stateDataService.create(state);
    }

    @Test(expected = ConstraintViolationException.class)
    public void testCreateHealthBlockNoName() throws Exception {
        healthBlock.setName(null);

        healthBlockDataService.create(healthBlock);
    }

    @Test(expected = ConstraintViolationException.class)
    public void testCreateHealthFacilityNoName() throws Exception {
        healthFacility.setName(null);

        healthFacilityDataService.create(healthFacility);
    }

    @Test(expected = ConstraintViolationException.class)
    public void testCreateHealthSubFacilityNoName() throws Exception {
        healthSubFacility.setName(null);

        healthSubFacilityDataService.create(healthSubFacility);
    }

    @Rule
    public ExpectedException exception = ExpectedException.none();


    @Test
    public void testDistrictsWithSameCodeDifferentStates() throws Exception {
        stateDataService.create(state);
        districtDataService.create(district);

        State otherState = new State();
        otherState.setName("State 2");
        otherState.setCode(2L);
        stateDataService.create(otherState);

        District otherDistrict = new District();
        otherDistrict.setName("District 2");
        otherDistrict.setRegionalName("District 2");
        otherDistrict.setCode(1L);
        otherDistrict.setState(otherState);
        districtDataService.create(otherDistrict);
    }


    @Test
    public void testDistrictsWithSameCodeSameStates() throws Exception {
        stateDataService.create(state);
        districtDataService.create(district);

        District otherDistrict = new District();
        otherDistrict.setName("District 2");
        otherDistrict.setRegionalName("District 2");
        otherDistrict.setCode(1L);
        otherDistrict.setState(state);

        exception.expect(javax.jdo.JDODataStoreException.class);
        districtDataService.create(otherDistrict);
    }


    @Test
    public void testVillagesWithSameCodeDifferentTalukas() throws Exception {
        stateDataService.create(state);
        districtDataService.create(district);
        talukaDataService.create(taluka);
        villageDataService.create(village);

        Taluka otherTaluka = new Taluka();
        otherTaluka.setName("Taluka 2");
        otherTaluka.setRegionalName("Taluka 2");
        otherTaluka.setDistrict(district);
        otherTaluka.setIdentity(2);
        otherTaluka.setCode("0005");
        talukaDataService.create(otherTaluka);

        Village otherVillage = new Village();
        otherVillage.setName("Village 2");
        otherVillage.setRegionalName("Village 2");
        otherVillage.setVcode(1L);
        otherVillage.setTaluka(otherTaluka);
        villageDataService.create(otherVillage);
    }


    @Test
    public void testVillagesWithSameCodeSameTalukas() throws Exception {
        stateDataService.create(state);
        districtDataService.create(district);
        talukaDataService.create(taluka);
        villageDataService.create(village);

        Village otherVillage = new Village();
        otherVillage.setName("Village 2");
        otherVillage.setRegionalName("Village 2");
        otherVillage.setVcode(1L);
        otherVillage.setTaluka(taluka);
        exception.expect(javax.jdo.JDODataStoreException.class);
        villageDataService.create(otherVillage);
    }

    // Single Taluka Single HB, should find it
    // State -> District -> Taluka -> HealthBlock(1)
    @Ignore //Iest is ignored since there is no direct mapping between Taluka and Health block.
    @Test
    public void testFindHealthBlockByTalukaAndCode1() {
        stateDataService.create(state);
        districtDataService.create(district);
        Taluka t = talukaDataService.create(taluka);
        healthBlockDataService.create(healthBlock);

        HealthBlock hb = healthBlockService.findByTalukaAndCode(t, 1L);
        assertNotNull(hb);
        assertEquals(hb.getCode(), healthBlock.getCode());
    }

    // Multiple, lookup by t(1) and HB(2), should find it
    // State -> District -> Taluka(1) -> HealthBlock(1)
    //                   -> Taluka(2) -> HealthBlock(2)
    @Ignore  //Iest is ignored since there is no direct mapping between Taluka and Health block.
    @Test
    public void testFindHealthBlockByTalukaAndCode2() {
        stateDataService.create(state);
        district = districtDataService.create(district);
        Taluka t = talukaDataService.create(taluka);
        healthBlockDataService.create(healthBlock);

        HealthBlock healthBlock2 = new HealthBlock();
        healthBlock2.setName("Health Block 2");
        healthBlock2.setRegionalName("Health Block 2");
        healthBlock2.setHq("Health Block 2 HQ");
        healthBlock2.setCode(2L);

        Taluka taluka2 = new Taluka();
        taluka2.setName("Taluka 2");
        taluka2.setRegionalName("Taluka 2");
        taluka2.setIdentity(2);
        taluka2.setCode("0005");
        taluka2.setDistrict(district);
        //TODO HARITHA commented 2 lines m-n taluka hb
        //taluka2.addHealthBlock(healthBlock2);

        taluka2 = talukaDataService.create(taluka2);
        healthBlockDataService.create(healthBlock2);

        HealthBlock hb = healthBlockService.findByTalukaAndCode(t, 2L);
        assertNotNull(hb);
        assertEquals(hb.getCode(), healthBlock2.getCode());

        hb = healthBlockService.findByTalukaAndCode(t, 1L);
        assertNotNull(hb);
        assertEquals(hb.getCode(), healthBlock.getCode());

        hb = healthBlockService.findByTalukaAndCode(taluka2, 1L);
        assertNotNull(hb);
        assertEquals(hb.getCode(), healthBlock.getCode());

        hb = healthBlockService.findByTalukaAndCode(taluka2, 2L);
        assertNotNull(hb);
        assertEquals(hb.getCode(), healthBlock2.getCode());
    }

    // Two HB in Single Taluka, lookup by t(1) hb(1), should find it
    // State -> District -> Taluka -> HealthBlock(1)
    //                             -> HealthBlock(2)
    @Ignore  //Iest is ignored since there is no direct mapping between Taluka and Health block.
    @Test
    public void testFindHealthBlockByTalukaAndCode3() {
        stateDataService.create(state);
        districtDataService.create(district);
        Taluka t = talukaDataService.create(taluka);
        healthBlockDataService.create(healthBlock);

        HealthBlock healthBlock2 = new HealthBlock();
        healthBlock2.setName("Health Block 2");
        healthBlock2.setRegionalName("Health Block 2");
        healthBlock2.setHq("Health Block 2 HQ");
        healthBlock2.setCode(2L);
        //TODO HARITHA commented 2 lines m-n taluka hb
        //healthBlock2.addTaluka(t);

        healthBlockDataService.create(healthBlock2);

        HealthBlock hb = healthBlockService.findByTalukaAndCode(t, 1L);
        assertNotNull(hb);
        assertEquals(hb.getCode(), healthBlock.getCode());

        hb = healthBlockService.findByTalukaAndCode(t, 2L);
        assertNotNull(hb);
        assertEquals(hb.getCode(), healthBlock2.getCode());
    }
    // Multiple, lookup by t(1), hb(2) should not find it
    // State(1) -> District -> Taluka(1) -> HealthBlock(1)
    // State(2) -> District -> Taluka(2) -> HealthBlock(2)
    @Ignore  //Iest is ignored since there is no direct mapping between Taluka and Health block.
    @Test
    public void testFindHealthBlockByTalukaAndCode4() {
        stateDataService.create(state);
        districtDataService.create(district);
        Taluka t = talukaDataService.create(taluka);
        healthBlockDataService.create(healthBlock);

        HealthBlock healthBlock2 = new HealthBlock();
        healthBlock2.setName("Health Block 2");
        healthBlock2.setRegionalName("Health Block 2");
        healthBlock2.setHq("Health Block 2 HQ");
        healthBlock2.setCode(2L);

        Taluka taluka2 = new Taluka();
        taluka2.setName("Taluka 2");
        taluka2.setRegionalName("Taluka 2");
        taluka2.setIdentity(2);
        taluka2.setCode("0005");
        //TODO HARITHA commented 2 lines m-n taluka hb
        //taluka2.addHealthBlock(healthBlock2);

        District district2 = new District();
        district2.setName("District 2");
        district2.setRegionalName("District 2");
        district2.setCode(2L);
        district2.getTalukas().add(taluka2);

        State state2 = new State();
        state2.setName("State 2");
        state2.setCode(2L);
        state2.getDistricts().add(district2);

        stateDataService.create(state2);
        districtDataService.create(district2);
        Taluka t2 = talukaDataService.create(taluka2);
        healthBlockDataService.create(healthBlock2);

        HealthBlock hb = healthBlockService.findByTalukaAndCode(t, 2L);
        assertNull(hb);

        hb = healthBlockService.findByTalukaAndCode(t2, 1L);
        assertNull(hb);
    }

    @Test
    @Ignore // TODO: Remove once https://applab.atlassian.net/browse/MOTECH-1678 is resolved
    public void testValidCreate() throws Exception {
        stateDataService.create(state);

        State newState = stateDataService.findByCode(1L);
        assertNotNull(newState);
        Assert.assertEquals(state, newState);

        Set<District> districtList = newState.getDistricts();
        assertEquals(1, districtList.size());
        assertTrue(districtList.contains(district));

        List<Taluka> talukaList = districtList.iterator().next().getTalukas();
        assertEquals(1, talukaList.size());
        assertTrue(talukaList.contains(taluka));

        List<Village> villageList = talukaList.get(0).getVillages();
        assertEquals(1, villageList.size());
        assertTrue(villageList.contains(village));

        //TODO HARITHA commented 2 lines m-n taluka hb
        //Set<HealthBlock> healthBlockList = talukaList.get(0).getHealthBlocks();
//        assertEquals(1, healthBlockList.size());
//        assertTrue(healthBlockList.contains(healthBlock));

//        List<HealthFacility> healthFacilityList = healthBlockList.iterator().next().getHealthFacilities();
//        assertEquals(1, healthFacilityList.size());
//        Assert.assertEquals(healthFacilityType, healthFacilityList.get(0).getHealthFacilityType());
//        assertTrue(healthFacilityList.contains(healthFacility));

//        List<HealthSubFacility> healthSubFacilityList = healthFacilityList.get(0).getHealthSubFacilities();
//        assertEquals(1, healthSubFacilityList.size());
//        assertTrue(healthSubFacilityList.contains(healthSubFacility));
    }
}

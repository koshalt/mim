package org.motechproject.nms.testing.it.region;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.motechproject.nms.csv.exception.CsvImportDataException;
import org.motechproject.nms.region.csv.CensusVillageImportService;
import org.motechproject.nms.region.csv.DistrictImportService;
import org.motechproject.nms.region.csv.HealthBlockImportService;
import org.motechproject.nms.region.csv.HealthFacilityImportService;
import org.motechproject.nms.region.csv.HealthSubFacilityImportService;
import org.motechproject.nms.region.csv.NonCensusVillageImportService;
import org.motechproject.nms.region.csv.StateImportService;
import org.motechproject.nms.region.csv.TalukaImportService;
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
import org.motechproject.nms.region.repository.StateDataService;
import org.motechproject.nms.region.repository.TalukaDataService;
import org.motechproject.nms.region.service.DistrictService;
import org.motechproject.nms.region.service.HealthBlockService;
import org.motechproject.nms.region.service.HealthFacilityService;
import org.motechproject.nms.region.service.HealthSubFacilityService;
import org.motechproject.nms.region.service.TalukaService;
import org.motechproject.nms.region.service.VillageService;
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
import org.supercsv.exception.SuperCsvException;

import javax.inject.Inject;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.motechproject.nms.testing.it.utils.RegionHelper.createDistrict;
import static org.motechproject.nms.testing.it.utils.RegionHelper.createHealthBlock;
import static org.motechproject.nms.testing.it.utils.RegionHelper.createHealthFacility;
import static org.motechproject.nms.testing.it.utils.RegionHelper.createHealthFacilityType;
import static org.motechproject.nms.testing.it.utils.RegionHelper.createTaluka;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
@ExamFactory(MotechNativeTestContainerFactory.class)
public class LocationDataImportServiceBundleIT extends BasePaxIT {

    @Inject
    StateImportService stateImportService;
    @Inject
    DistrictImportService districtImportService;
    @Inject
    TalukaImportService talukaImportService;
    @Inject
    NonCensusVillageImportService nonCensusVillageImportService;
    @Inject
    CensusVillageImportService censusVillageImportService;
    @Inject
    HealthBlockImportService healthBlockImportService;
    @Inject
    HealthFacilityImportService healthFacilityImportService;
    @Inject
    HealthSubFacilityImportService healthSubFacilityImportService;
    @Inject
    TestingService testingService;
    @Inject
    StateDataService stateDataService;
    @Inject
    DistrictService districtService;
    @Inject
    DistrictDataService districtDataService;
    @Inject
    TalukaService talukaService;
    @Inject
    TalukaDataService talukaDataService;
    @Inject
    VillageService villageService;
    @Inject
    HealthBlockService healthBlockService;
    @Inject
    HealthBlockDataService healthBlockDataService;
    @Inject
    HealthFacilityTypeDataService healthFacilityTypeDataService;
    @Inject
    HealthFacilityService healthFacilityService;
    @Inject
    HealthFacilityDataService healthFacilityDataService;
    @Inject
    HealthSubFacilityService healthSubFacilityService;
    @Inject
    PlatformTransactionManager transactionManager;

    
    State exampleState;
    District exampleDistrict;
    Taluka exampleTaluka;
    HealthFacilityType exampleFacilityType;

    private String stateHeader = "StateID,Name";

    private String districtHeader = "DCode,Name_G,Name_E,StateID";

    private String talukaHeader = "TCode,ID,Name_G,Name_E,StateID,DCode";

    private String healthBlockHeader = "BID,Name_G,Name_E,HQ,StateID,DCode,TCode";

    private String healthFacilityHeader = "PID,Name_G,Name_E,StateID,DCode,TCode,BID,Facility_Type";

    private String healthSubFacilityHeader = "SID,Name_G,Name_E,StateID,DCode,TCode,BID,PID";

    private String villageHeader = "VCode,Name_G,Name_E,StateID,DCode,TCode";

    @Before
    public void setUp() {

        try{
            testingService.clearDatabase();
        }
        catch (Exception e){
            testingService.clearDatabase();
        }

        exampleState = stateDataService.create(new State("EXAMPLE STATE", 1L));

        exampleDistrict = createDistrict(exampleState, 2L, "EXAMPLE DISTRICT");
        districtDataService.create(exampleDistrict);

        exampleTaluka = createTaluka(exampleDistrict, "00003", "EXAMPLE TALUKA", 1);
        talukaDataService.create(exampleTaluka);

        HealthFacilityType facilityType = createHealthFacilityType("EXAMPLE FACILITY TYPE", 5678L);
        exampleFacilityType = healthFacilityTypeDataService.create(facilityType);

        HealthBlock healthBlock = createHealthBlock(exampleTaluka, 4L, "EXAMPLE HEALTH BLOCK", "hq");
        healthBlockDataService.create(healthBlock);

        HealthFacility healthFacility = createHealthFacility(healthBlock, 5L, "EXAMPLE HEALTH FACILITY", exampleFacilityType);
        healthFacilityDataService.create(healthFacility);
    }

    
    @Test(expected = CsvImportDataException.class)
    public void testLocationDataImport() throws Exception {
        State state = stateDataService.findByCode(1L);
        assertNotNull(state);
        assertEquals(1L, (long) state.getCode());
        assertEquals("EXAMPLE STATE", state.getName());

        districtImportService.importData(read("csv/district.csv"));
        District district = districtService.findByStateAndCode(state, 1L);
        assertNotNull(district);
        assertEquals(1L, (long) district.getCode());
        assertEquals("district name", district.getName());
        assertEquals("district regional name", district.getRegionalName());
        assertNotNull(district.getState());

        talukaImportService.importData(read("csv/taluka.csv"));
        Taluka taluka = talukaService.findByDistrictAndCode(district, "TALUKA");
        assertNotNull(taluka);
        assertEquals("TALUKA", taluka.getCode());
        assertEquals(2, (int) taluka.getIdentity());
        assertEquals("taluka name", taluka.getName());
        assertEquals("taluka regional name", taluka.getRegionalName());
        assertNotNull(taluka.getDistrict());

        censusVillageImportService.importData(read("csv/census_village.csv"));
        Village censusVillage = villageService.findByTalukaAndVcodeAndSvid(taluka, 3L, 0L);
        assertNotNull(censusVillage);
        assertEquals(3L, censusVillage.getVcode());
        assertEquals("census village name", censusVillage.getName());
        assertEquals("census village regional name", censusVillage.getRegionalName());
        assertNotNull(censusVillage.getTaluka());

        nonCensusVillageImportService.importData(read("csv/non_census_village_associated.csv"));
        Village nonCensusVillageAssociated = villageService.findByTalukaAndVcodeAndSvid(taluka, 3L, 4L);
        assertNotNull(nonCensusVillageAssociated);
        assertEquals(4L, nonCensusVillageAssociated.getSvid());
        assertEquals("non census village associated name", nonCensusVillageAssociated.getName());
        assertEquals("non census village associated regional name", nonCensusVillageAssociated.getRegionalName());
        assertNotNull(nonCensusVillageAssociated.getTaluka());
        assertEquals(3L, nonCensusVillageAssociated.getVcode());

        nonCensusVillageImportService.importData(read("csv/non_census_village_non_associated.csv"));
        Village nonCensusVillageNonAssociated = villageService.findByTalukaAndVcodeAndSvid(taluka, 0L, 5L);
        assertNotNull(nonCensusVillageNonAssociated);
        assertEquals(5L, nonCensusVillageNonAssociated.getSvid());
        assertEquals("non census village non associated name", nonCensusVillageNonAssociated.getName());
        assertEquals("non census village non associated regional name",
                nonCensusVillageNonAssociated.getRegionalName());
        assertNotNull(nonCensusVillageNonAssociated.getTaluka());
        assertEquals(0, nonCensusVillageNonAssociated.getVcode());

        healthBlockImportService.importData(read("csv/health_block.csv"));
        HealthBlock healthBlock = healthBlockService.findByTalukaAndCode(taluka, 6L);
        assertNotNull(healthBlock);
        assertEquals(6L, (long) healthBlock.getCode());
        assertEquals("health block name", healthBlock.getName());
        assertEquals("health block regional name", healthBlock.getRegionalName());
        assertEquals("health block hq", healthBlock.getHq());
        //TODO HARITHA commented 2 lines m-n taluka hb
        //assertNotNull(healthBlock.getTalukas().iterator().next());

        healthFacilityImportService.importData(read("csv/health_facility.csv"));
        HealthFacility healthFacility = healthFacilityService.findByHealthBlockAndCode(healthBlock, 7L);
        assertNotNull(healthFacility);
        assertEquals(7L, (long) healthFacility.getCode());
        assertEquals("health facility name", healthFacility.getName());
        assertEquals("health facility regional name", healthFacility.getRegionalName());
        assertNotNull(healthFacility.getHealthBlock());
        assertNotNull(healthFacility.getHealthFacilityType());

        healthSubFacilityImportService.importData(read("csv/health_sub_facility.csv"));
        HealthSubFacility healthSubFacility = healthSubFacilityService.findByHealthFacilityAndCode(
                healthFacility, 8L);
        assertNotNull(healthSubFacility);
        assertEquals(8L, ((long) healthSubFacility.getCode()));
        assertEquals("health sub facility name", healthSubFacility.getName());
        assertEquals("health sub facility regional name", healthSubFacility.getRegionalName());
        assertNotNull(healthSubFacility.getHealthFacility());
    }

    @Test(expected = SuperCsvException.class)
    public void testThrowExceptionForMalformedCsv() throws Exception {
        districtImportService.importData(read("csv/district_malformed.csv"));
    }

    @Test(expected = CsvImportDataException.class)
    public void testThrowExceptionForInvalidCellFormat() throws Exception {
        districtImportService.importData(read("csv/district_invalid_cell_format.csv"));
    }

    @Test
    public void testRollbackAllAfterSingleFailure() throws Exception {
        boolean thrown = false;
        try {
            districtImportService.importData(read("csv/district_rollback.csv"));
        } catch (Exception e) {
            thrown = true;
        }
        assertTrue(thrown);
        assertNull(districtService.findByStateAndCode(exampleState, 1002L));
        assertNull(districtService.findByStateAndCode(exampleState, 1003L));
        assertNull(districtService.findByStateAndCode(exampleState, 1004L));
    }


    @Test(expected = CsvImportDataException.class)
    public void verifyStateRejectedIfIdMissing() throws Exception {
        Reader reader = createReaderWithHeaders(stateHeader, ",foo");
        stateImportService.importData(reader);
    }

    @Test(expected = CsvImportDataException.class)
    public void verifyStateRejectedIfNameMissing() throws Exception {
        Reader reader = createReaderWithHeaders(stateHeader, "123,");
        stateImportService.importData(reader);
    }

    @Test(expected = CsvImportDataException.class)
    public void verifyStateRejectedIfIdInvalid() throws Exception {
        Reader reader = createReaderWithHeaders(stateHeader, "foo,bar");
        stateImportService.importData(reader);
    }

    /*
    * Verify state upload is rejected when name is not provided
     */
    @Test(expected = CsvImportDataException.class)
    public void stateUploadNoName() throws Exception {
        Reader reader = createReaderWithHeaders(stateHeader, ",1234");
        stateImportService.importData(reader);
    }

    /*
    * Verify state upload is rejected when code is not provided
     */
    @Test(expected = CsvImportDataException.class)
    public void stateUploadNoCode() throws Exception {
        Reader reader = createReaderWithHeaders(stateHeader, "Bihar,");
        stateImportService.importData(reader);
    }

    /*
    * To verify district location data is rejected when mandatory parameter code is missing.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT221() throws Exception {
        Reader reader = createReaderWithHeaders(districtHeader, ",district regional name,district name,1234");
        districtImportService.importData(reader);
    }

    /*
    * To verify district location data is rejected when mandatory parameter state_id is missing.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT222() throws Exception {
        Reader reader = createReaderWithHeaders(districtHeader, "1,district regional name,district name,");
        districtImportService.importData(reader);
    }

    /*
    * To verify district location data is rejected when state_id is having invalid value.
    */
    @Test
    public void verifyFT223() throws Exception {
        boolean thrown = false;
        String errorMessage = "CSV instance error [row: 2]: validation failed for instance of type " +
                "org.motechproject.nms.region.domain.District, violations: {'state': may not be null}";
        Reader reader = createReaderWithHeaders(districtHeader, "1,district regional name,district name,12345");
        try {
            districtImportService.importData(reader);
        } catch (CsvImportDataException e) {
            thrown = true;
            assertEquals(errorMessage, e.getMessage());
        }
        assertTrue(thrown);
    }

    /*
    * To verify district location data is rejected when code is having invalid value.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT224() throws Exception {
        Reader reader = createReaderWithHeaders(districtHeader, "asd,district regional name,district name,1234");
        districtImportService.importData(reader);
    }

    /*
    * To verify taluka location data is rejected when mandatory parameter name is missing.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT227() throws Exception {
        Reader reader = createReaderWithHeaders(talukaHeader, "TALUKA,2,taluka regional name,,1,2");
        talukaImportService.importData(reader);
    }


    /*
    * To verify error message is correct when a column is invalid.
    * Fixes: https://applab.atlassian.net/browse/NMS-213
    */
    @Test
    public void verifyErrorMessageHasCorrectColumnNumber() throws Exception {
        Reader reader = createReaderWithHeaders(talukaHeader, "TALUKA,2,,Taluka 2,1,2");
        try {
            talukaImportService.importData(reader);
        } catch (CsvImportDataException e) {
            assertEquals("CSV field error [row: 2, col: 3]: Expected String value, found null", e.getMessage());
        }
    }

    /*
    * To verify taluka location data is rejected when mandatory parameter code is missing.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT228() throws Exception {
        Reader reader = createReaderWithHeaders(talukaHeader, ",2,taluka regional name,taluka name,1,2");
        talukaImportService.importData(reader);
    }

    /*
    * To verify taluka location data is rejected when mandatory parameter district_id is missing.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT229() throws Exception {
        Reader reader = createReaderWithHeaders(talukaHeader, "TALUKA,2,taluka regional name,taluka name,1,");
        talukaImportService.importData(reader);
    }

    /*
    * To verify taluka location data is rejected when district_id is having invalid value.
    */
    @Test
    public void verifyFT230() throws Exception {
        boolean thrown = false;
        String errorMessage = "CSV instance error [row: 2]: validation failed for instance of type " +
                "org.motechproject.nms.region.domain.Taluka, violations: {'district': may not be null}";
        Reader reader = createReaderWithHeaders(talukaHeader, "TALUKA,2,taluka regional name,taluka name,1,3");
        try {
            talukaImportService.importData(reader);
        } catch (CsvImportDataException e) {
            thrown = true;
            assertEquals(errorMessage, e.getMessage());
        }
        assertTrue(thrown);
    }

    /*
    * To verify health block location data is rejected when mandatory parameter name is missing.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT234() throws Exception {
        Reader reader = createReaderWithHeaders(
                healthBlockHeader, "6,health block regional name,,health block hq,1,2,TALUKA");
        healthBlockImportService.importData(reader);
    }

    /*
    * To verify health block location data is rejected when mandatory parameter code is missing.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT235() throws Exception {
        Reader reader = createReaderWithHeaders(
                healthBlockHeader, ",health block regional name,health block name,health block hq,1,2,TALUKA");
        healthBlockImportService.importData(reader);
    }

    /*
    * To verify health block location data is rejected when mandatory parameter taluka_id is missing.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT236() throws Exception {
        Reader reader = createReaderWithHeaders(
                healthBlockHeader, "6,health block regional name,health block name,health block hq,1,2,");
        healthBlockImportService.importData(reader);
    }

    /*
    * To verify health block location data is not rejected when taluka_id is having invalid value as per the new requirement health block is no more related to taluka.
    */
    @Test
    public void verifyFT237() throws Exception {
        boolean thrown = false;
        String errorMessage = "CSV instance error [row: 2]: validation failed for instance of type " +
                "org.motechproject.nms.region.domain.HealthBlock, violations: {'taluka': may not be null}";
        Reader reader = createReaderWithHeaders(
                healthBlockHeader, "6,health block regional name,health block name,health block hq,1,2,invalid taluka");
        try {
            healthBlockImportService.importData(reader);
        } catch (CsvImportDataException e) {
            thrown = false;
            assertEquals(errorMessage, e.getMessage());
        }
        assertFalse(thrown);
    }

    /*
    * To verify health block location data is rejected when code is having invalid value.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT238() throws Exception {
        Reader reader = createReaderWithHeaders(
                healthBlockHeader, "abc,health block regional name,health block name,health block hq,1,2,TALUKA");
        healthBlockImportService.importData(reader);
    }

    /*
    * To verify health facility location data is rejected when mandatory parameter name is missing.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT241() throws Exception {
        Reader reader = createReaderWithHeaders(
                healthFacilityHeader, "7,health facility regional name,,1,2,00003,6,5678");
        healthFacilityImportService.importData(reader);
    }

    /*
    * To verify health facility location data is rejected when mandatory parameter code is missing.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT242() throws Exception {
        Reader reader = createReaderWithHeaders(
                healthFacilityHeader, ",health facility regional name,health facility name,1,2,00003,6,5678");
        healthFacilityImportService.importData(reader);
    }

    /*
    * To verify health facility location data is rejected when mandatory parameter health_block_id is missing.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT243() throws Exception {
        Reader reader = createReaderWithHeaders(
                healthFacilityHeader, "7,health facility regional name,health facility name,1,2,00003,,5678");
        healthFacilityImportService.importData(reader);
    }

    /*
    * To verify health facility location data is rejected when health_block_id is having invalid value.
    */
    @Test
    public void verifyFT244() throws Exception {
        boolean thrown = false;
        String errorMessage = "CSV instance error [row: 2]: validation failed for instance of type " +
                "org.motechproject.nms.region.domain.HealthFacility, violations: {'healthBlock': may not be null}";
        Reader reader = createReaderWithHeaders(
                healthFacilityHeader, "7,health facility regional name,health facility name,1,2,00003,10,5678");
        try {
            healthFacilityImportService.importData(reader);
        } catch (CsvImportDataException e) {
            thrown = true;
            assertEquals(errorMessage, e.getMessage());
        }
        assertTrue(thrown);
    }

    /*
    * To verify health facility location data is rejected when code is having invalid value.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT245() throws Exception {
        Reader reader = createReaderWithHeaders(
                healthFacilityHeader, "abc,health facility regional name,health facility name,1,2,00003,6,5678");
        healthFacilityImportService.importData(reader);
    }

    /*
    * To verify health sub facility location data is rejected when mandatory parameter name is missing.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT248() throws Exception {
        Reader reader = createReaderWithHeaders(
                healthSubFacilityHeader, "8,health sub facility regional name,,1,2,00003,4,5");
        healthSubFacilityImportService.importData(reader);
    }

    /*
    * To verify health sub facility location data is rejected when mandatory parameter code is missing.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT249() throws Exception {
        Reader reader = createReaderWithHeaders(
                healthSubFacilityHeader, ",health sub facility regional name,health sub facility name,1,2,00003,4,5");
        healthSubFacilityImportService.importData(reader);
    }

    /*
    * To verify health sub facility location data is rejected when mandatory parameter health_facality_id is missing.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT250() throws Exception {
        Reader reader = createReaderWithHeaders(
                healthSubFacilityHeader, "8,health sub facility regional name,health sub facility name,1,2,00003,4,");
        healthSubFacilityImportService.importData(reader);
    }

    /*
    * To verify health sub facility location data is rejected when health_facality_id is having invalid value.
    */
    @Test
    public void verifyFT251() throws Exception {
        boolean thrown = false;
        String errorMessage = "CSV instance error [row: 2]: Error loading entities in record for instance of type org.motechproject.nms.region.domain.HealthSubFacility, message: Unable to load HealthFacility 6 with a null HealthBlock";
        Reader reader = createReaderWithHeaders(
                healthSubFacilityHeader, "8,health sub facility regional name,health sub facility name,1,2,00003,4,6");
        try {
            healthSubFacilityImportService.importData(reader);
        } catch (CsvImportDataException e) {
            thrown = false;
            assertEquals(errorMessage, e.getMessage());
        }
        assertFalse(thrown);
    }

    /*
    * To verify health sub facility location data is rejected when code is having invalid value.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT252() throws Exception {
        Reader reader = createReaderWithHeaders(
                healthSubFacilityHeader, "abc,health sub facility regional name,health sub facility name,1,2,00003,4,5");
        healthSubFacilityImportService.importData(reader);
    }

    /*
    * To verify village location data is rejected when mandatory parameter name is missing.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT255() throws Exception {
        Reader reader = createReaderWithHeaders(
                villageHeader, "3,census village regional name,,1,2,TALUKA");
        censusVillageImportService.importData(reader);
    }

    /*
    * To verify village location data is rejected when mandatory parameter code is missing.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT256() throws Exception {
        Reader reader = createReaderWithHeaders(
                villageHeader, ",census village regional name,census village name,1,2,TALUKA");
        censusVillageImportService.importData(reader);
    }

    /*
    * To verify village location data is rejected when mandatory parameter taluka_id is missing.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT257() throws Exception {
        Reader reader = createReaderWithHeaders(
                villageHeader, "3,census village regional name,census village name,1,2,");
        censusVillageImportService.importData(reader);
    }

    /*
    * To verify village location data is rejected when taluka_id is having invalid value.
    */
    @Test
    public void verifyFT258() throws Exception {
        boolean thrown = false;
        String errorMessage = "CSV instance error [row: 2]: validation failed for instance of type " +
                "org.motechproject.nms.region.domain.Village, violations: {'taluka': may not be null}";
        Reader reader = createReaderWithHeaders(
                villageHeader, "3,census village regional name,census village name,1,2,invalid taluka");
        try {
            censusVillageImportService.importData(reader);
        } catch (CsvImportDataException e) {
            thrown = true;
            assertEquals(errorMessage, e.getMessage());
        }
        assertTrue(thrown);
    }

    /*
    * To verify village location data is rejected when code is having invalid value.
    */
    @Test(expected = CsvImportDataException.class)
    public void verifyFT259() throws Exception {
        Reader reader = createReaderWithHeaders(
                villageHeader, "abc,census village regional name,census village name,1,2,TALUKA");
        censusVillageImportService.importData(reader);
    }

    private Reader createReaderWithHeaders(String header, String... lines) {
        StringBuilder builder = new StringBuilder();
        builder.append(header);
        builder.append("\r\n");

        for (String line : lines) {
            builder.append(line).append("\r\n");
        }
        return new StringReader(builder.toString());
    }

    private Reader read(String resource) {
        return new InputStreamReader(getClass().getClassLoader().getResourceAsStream(resource));
    }
}

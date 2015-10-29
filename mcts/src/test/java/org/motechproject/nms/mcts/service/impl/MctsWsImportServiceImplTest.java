package org.motechproject.nms.mcts.service.impl;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.motechproject.commons.date.util.DateUtil;
import org.motechproject.nms.flw.service.FrontLineWorkerImportService;
import org.motechproject.nms.flw.utils.FlwConstants;
import org.motechproject.nms.kilkari.domain.MctsChild;
import org.motechproject.nms.kilkari.domain.MctsMother;
import org.motechproject.nms.kilkari.service.MctsBeneficiaryImportService;
import org.motechproject.nms.kilkari.service.MctsBeneficiaryValueProcessor;
import org.motechproject.nms.kilkari.utils.KilkariConstants;
import org.motechproject.nms.mcts.contract.AnmAshaDataSet;
import org.motechproject.nms.mcts.contract.AnmAshaRecord;
import org.motechproject.nms.mcts.contract.ChildRecord;
import org.motechproject.nms.mcts.contract.ChildrenDataSet;
import org.motechproject.nms.mcts.contract.MotherRecord;
import org.motechproject.nms.mcts.contract.MothersDataSet;
import org.motechproject.nms.mcts.service.MctsWebServiceFacade;
import org.motechproject.nms.region.domain.State;
import org.motechproject.nms.region.exception.InvalidLocationException;
import org.motechproject.nms.region.repository.StateDataService;

import java.net.URL;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MctsWsImportServiceImplTest {

    @InjectMocks
    private MctsWsImportServiceImpl mctsWsImportServiceImpl = new MctsWsImportServiceImpl();

    @Mock
    private StateDataService stateDataService;

    @Mock
    private MctsWebServiceFacade mctsWebServiceFacade;

    @Mock
    private FrontLineWorkerImportService flwImportService;

    @Mock
    private MctsBeneficiaryImportService mctsBeneficiaryImportService;

    @Mock
    private MctsBeneficiaryValueProcessor mctsBeneficiaryValueProcessor;

    @Mock
    private State state1;

    @Mock
    private State state15;

    private AnmAshaDataSet flwDs1;
    private AnmAshaDataSet flwDs2;
    private ChildrenDataSet childDs1;
    private ChildrenDataSet childDs2;
    private MothersDataSet motherDs1;
    private MothersDataSet motherDs2;

    private final LocalDate today = DateUtil.today();
    private final LocalDate yesterday = today.minusDays(1);

    @Test
    public void shouldImportData() throws InvalidLocationException {
        prepStates();
        prepFlwData();
        prepChildData();
        prepMotherData();
        when(stateDataService.findByCode(1L)).thenReturn(state1);
        when(stateDataService.findByCode(15L)).thenReturn(state15);
        when(mctsBeneficiaryValueProcessor.getDeathFromString("9")).thenReturn(true);
        // flw
        when(mctsWebServiceFacade.getAnmAshaData(eq(yesterday), eq(yesterday), any(URL.class), eq(1L)))
                .thenReturn(flwDs1);
        when(mctsWebServiceFacade.getAnmAshaData(eq(yesterday), eq(yesterday), any(URL.class), eq(15L)))
                .thenReturn(flwDs2);
        // children
        when(mctsWebServiceFacade.getChildrenData(eq(yesterday), eq(yesterday), any(URL.class), eq(1L)))
                .thenReturn(childDs1);
        when(mctsWebServiceFacade.getChildrenData(eq(yesterday), eq(yesterday), any(URL.class), eq(15L)))
                .thenReturn(childDs2);
        // mothers
        when(mctsWebServiceFacade.getMothersData(eq(yesterday), eq(yesterday), any(URL.class), eq(1L)))
                .thenReturn(motherDs1);
        when(mctsWebServiceFacade.getMothersData(eq(yesterday), eq(yesterday), any(URL.class), eq(15L)))
                .thenReturn(motherDs2);

        mctsWsImportServiceImpl.importFromMcts(asList(1L, 15L), yesterday, null);

        // flw
        ArgumentCaptor<Map> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(flwImportService, times(2)).importFrontLineWorker(mapCaptor.capture(), eq(state1));
        verifyFlw(mapCaptor.getAllValues().get(0), 0L);
        verifyFlw(mapCaptor.getAllValues().get(1), 1L);

        mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(flwImportService, times(2)).importFrontLineWorker(mapCaptor.capture(), eq(state15));
        verifyFlw(mapCaptor.getAllValues().get(0), 2L);
        verifyFlw(mapCaptor.getAllValues().get(1), 3L);

        // children
        mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mctsBeneficiaryImportService, times(4)).importChildRecord(mapCaptor.capture());
        verifyChild(mapCaptor.getAllValues().get(0), 0, 1);
        verifyChild(mapCaptor.getAllValues().get(1), 1, 1);
        verifyChild(mapCaptor.getAllValues().get(2), 2, 15);
        verifyChild(mapCaptor.getAllValues().get(3), 3, 15);

        // mothers
        mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mctsBeneficiaryImportService, times(4)).importMotherRecord(mapCaptor.capture());
        verifyMother(mapCaptor.getAllValues().get(0), 0, 1);
        verifyMother(mapCaptor.getAllValues().get(1), 1, 1);
        verifyMother(mapCaptor.getAllValues().get(2), 2, 15);
        verifyMother(mapCaptor.getAllValues().get(3), 3, 15);
    }

    private void prepFlwData() {
        AnmAshaRecord flw1 = createFlw(0);
        AnmAshaRecord flw2 = createFlw(1);
        AnmAshaRecord flw3 = createFlw(2);
        AnmAshaRecord flw4 = createFlw(3);

        flwDs1 = new AnmAshaDataSet();
        flwDs1.setRecords(asList(flw1, flw2));

        flwDs2 = new AnmAshaDataSet();
        flwDs2.setRecords(asList(flw3, flw4));
    }

    private void prepStates() {
        when(state1.getCode()).thenReturn(1L);
        when(state15.getCode()).thenReturn(15L);
    }

    private AnmAshaRecord createFlw(long id) {
        AnmAshaRecord record = new AnmAshaRecord();

        record.setId(id);
        record.setContactNo("48" + id);
        record.setName("AnmAsha " + id);
        record.setTalukaId("Taluka " + id);
        record.setHealthBlockId(id + 1);
        record.setPhcId(id + 2);
        record.setSubCentreId(id + 3);
        record.setVillageId(id + 4);
        record.setType("Type " + id);

        return record;
    }

    private void verifyFlw(Map<String, Object> flwpMap, Long id) {
        assertEquals(id.toString(), flwpMap.get(FlwConstants.ID));
        assertEquals(Long.valueOf("48" + id), flwpMap.get(FlwConstants.CONTACT_NO));
        assertEquals("AnmAsha " + id, flwpMap.get(FlwConstants.NAME));
        assertEquals("Taluka " + id, flwpMap.get(FlwConstants.TALUKA));
        assertEquals(id + 1, flwpMap.get(FlwConstants.HEALTH_BLOCK));
        assertEquals(id + 2, flwpMap.get(FlwConstants.PHC));
        assertEquals(id + 3, flwpMap.get(FlwConstants.SUBCENTRE));
        assertEquals(id + 4, flwpMap.get(FlwConstants.CENSUS_VILLAGE));
        assertEquals("Type " + id, flwpMap.get(FlwConstants.TYPE));
    }

    private void prepChildData() {
        ChildRecord child1 = createChild(0, 1);
        ChildRecord child2 = createChild(1, 1);
        ChildRecord child3 = createChild(2, 15);
        ChildRecord child4 = createChild(3, 15);

        childDs1 = new ChildrenDataSet();
        childDs1.setRecords(asList(child1, child2));

        childDs2 = new ChildrenDataSet();
        childDs2.setRecords(asList(child3, child4));
    }

    private void prepMotherData() {
        MotherRecord mother1 = createMother(0, 1);
        MotherRecord mother2 = createMother(1, 1);
        MotherRecord mother3 = createMother(2, 15);
        MotherRecord mother4 = createMother(3, 15);

        motherDs1 = new MothersDataSet();
        motherDs1.setRecords(asList(mother1, mother2));

        motherDs2 = new MothersDataSet();
        motherDs2.setRecords(asList(mother3, mother4));
    }

    private ChildRecord createChild(long id, long stateId) {
        ChildRecord record = new ChildRecord();

        record.setStateID(stateId);
        record.setDistrictId(stateId + 1);
        record.setTalukaId("Taluka " + id);
        record.setHealthBlockId(id + 1);
        record.setPhcId(id + 2);
        record.setSubCentreId(id + 3);
        record.setVillageId(id + 4);
        record.setName("Child " + id);

        record.setWhomPhoneNo("48" + id);
        when(mctsBeneficiaryValueProcessor.getMsisdnByString("48" + id)).thenReturn(Long.valueOf("48" + id));

        DateTime dob = today.toDateTime(new LocalTime(10, (int) id));
        record.setBirthdate(dob.toString());
        when(mctsBeneficiaryValueProcessor.getDateByString(dob.toString())).thenReturn(dob);

        record.setIdNo(String.valueOf(id));
        MctsChild mctsChild = new MctsChild(String.valueOf(id));
        when(mctsBeneficiaryValueProcessor.getChildInstanceByString(String.valueOf(id))).thenReturn(mctsChild);

        String motherId = String.valueOf(id + 5);
        MctsMother mctsMother = new MctsMother(motherId);
        record.setMotherId(motherId);
        when(mctsBeneficiaryValueProcessor.getMotherInstanceByBeneficiaryId(motherId)).thenReturn(mctsMother);

        record.setEntryType((int) id + 9);

        return record;
    }

    private void verifyChild(Map<String, Object> record, long id, long stateId) {
        assertEquals(stateId, record.get(KilkariConstants.STATE));
        assertEquals(stateId + 1, record.get(KilkariConstants.DISTRICT));
        assertEquals("Taluka " + id, record.get(KilkariConstants.TALUKA));
        assertEquals(id + 1, record.get(KilkariConstants.HEALTH_BLOCK));
        assertEquals(id + 2, record.get(KilkariConstants.PHC));
        assertEquals(id + 3 , record.get(KilkariConstants.SUBCENTRE));
        assertEquals(id + 4, record.get(KilkariConstants.CENSUS_VILLAGE));
        assertEquals("Child " + id, record.get(KilkariConstants.BENEFICIARY_NAME));
        assertEquals(Long.valueOf("48" + id), record.get(KilkariConstants.MSISDN));
        assertEquals(today.toDateTime(new LocalTime(10, (int) id)), record.get(KilkariConstants.DOB));

        MctsChild child = (MctsChild) record.get(KilkariConstants.BENEFICIARY_ID);
        assertEquals(String.valueOf(id), child.getBeneficiaryId());

        MctsMother mother = (MctsMother) record.get(KilkariConstants.MOTHER_ID);
        assertEquals(String.valueOf(id + 5), mother.getBeneficiaryId());
    }

    private MotherRecord createMother(long id, long stateId) {
        MotherRecord mother = new MotherRecord();

        mother.setStateId(stateId);
        mother.setDistrictId(stateId + 1);
        mother.setTalukaId("Taluka " + id);
        mother.setHealthBlockId(id + 1);
        mother.setPhcid(id + 2);
        mother.setSubCentreid(id + 3);
        mother.setVillageId(id + 4);

        String motherId = String.valueOf(id);
        mother.setIdNo(motherId);
        MctsMother mctsMother = new MctsMother(motherId);
        when(mctsBeneficiaryValueProcessor.getOrCreateMotherInstance(motherId)).thenReturn(mctsMother);

        mother.setName("Mother " + id);

        mother.setWhomPhoneNo("49" + id);
        when(mctsBeneficiaryValueProcessor.getMsisdnByString("49" + id)).thenReturn(Long.valueOf("49" + id));

        DateTime lmp = today.toDateTime(new LocalTime(15, (int) id));
        mother.setLmpDate(lmp.toString());
        when(mctsBeneficiaryValueProcessor.getDateByString(lmp.toString())).thenReturn(lmp);

        DateTime dob = today.toDateTime(new LocalTime(16, (int) id));
        mother.setBirthdate(dob.toString());
        when(mctsBeneficiaryValueProcessor.getDateByString(dob.toString())).thenReturn(dob);

        mother.setAbortion(motherId);
        when(mctsBeneficiaryValueProcessor.getAbortionDataFromString(motherId)).thenReturn(id % 2 == 0);

        mother.setOutcomeNos((int) id);
        when(mctsBeneficiaryValueProcessor.getStillBirthFromString(motherId)).thenReturn(id % 2 == 0);

        mother.setEntryType((int) id);
        when(mctsBeneficiaryValueProcessor.getDeathFromString(motherId)).thenReturn(id % 2 == 0);

        return mother;
    }

    private void verifyMother(Map<String, Object> record, long id, long stateId) {
        assertEquals(stateId, record.get(KilkariConstants.STATE));
        assertEquals(stateId + 1, record.get(KilkariConstants.DISTRICT));
        assertEquals("Taluka " + id, record.get(KilkariConstants.TALUKA));
        assertEquals(id + 1, record.get(KilkariConstants.HEALTH_BLOCK));
        assertEquals(id + 2, record.get(KilkariConstants.PHC));
        assertEquals(id + 3, record.get(KilkariConstants.SUBCENTRE));
        assertEquals(id + 4, record.get(KilkariConstants.CENSUS_VILLAGE));

        MctsMother mctsMother = (MctsMother) record.get(KilkariConstants.BENEFICIARY_ID);
        assertEquals(String.valueOf(id), mctsMother.getBeneficiaryId());

        assertEquals("Mother " + id, record.get(KilkariConstants.BENEFICIARY_NAME));
        assertEquals(Long.valueOf("49" + id), record.get(KilkariConstants.MSISDN));
        assertEquals(today.toDateTime(new LocalTime(15, (int) id)), record.get(KilkariConstants.LMP));
        assertEquals(today.toDateTime(new LocalTime(16, (int) id)), record.get(KilkariConstants.MOTHER_DOB));
        assertEquals(id % 2 == 0, record.get(KilkariConstants.ABORTION));
        assertEquals(id % 2 == 0, record.get(KilkariConstants.STILLBIRTH));
        assertEquals(id % 2 == 0, record.get(KilkariConstants.DEATH));

    }
}

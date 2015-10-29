package org.motechproject.nms.flw.service.impl;

import org.apache.commons.lang.StringUtils;
import org.motechproject.nms.csv.exception.CsvImportDataException;
import org.motechproject.nms.csv.utils.ConstraintViolationUtils;
import org.motechproject.nms.csv.utils.CsvImporterBuilder;
import org.motechproject.nms.csv.utils.CsvMapImporter;
import org.motechproject.nms.csv.utils.GetLong;
import org.motechproject.nms.csv.utils.GetString;
import org.motechproject.nms.flw.domain.FrontLineWorker;
import org.motechproject.nms.flw.domain.FrontLineWorkerStatus;
import org.motechproject.nms.flw.exception.FlwImportException;
import org.motechproject.nms.flw.service.FrontLineWorkerImportService;
import org.motechproject.nms.flw.service.FrontLineWorkerService;
import org.motechproject.nms.flw.utils.FlwConstants;
import org.motechproject.nms.region.domain.District;
import org.motechproject.nms.region.domain.HealthBlock;
import org.motechproject.nms.region.domain.HealthFacility;
import org.motechproject.nms.region.domain.HealthSubFacility;
import org.motechproject.nms.region.domain.State;
import org.motechproject.nms.region.domain.Taluka;
import org.motechproject.nms.region.domain.Village;
import org.motechproject.nms.region.exception.InvalidLocationException;
import org.motechproject.nms.region.repository.StateDataService;
import org.motechproject.nms.region.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.prefs.CsvPreference;

import javax.jdo.JDODataStoreException;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service("frontLineWorkerImportService")
public class FrontLineWorkerImportServiceImpl implements FrontLineWorkerImportService {

    private static final int MASK_LENGTH = 3;


    private FrontLineWorkerService frontLineWorkerService;
    private StateDataService stateDataService;
    private LocationService locationService;

    /*
        Expected file format:
        * any number of empty lines
        * first non blank line to contain state name in the following format:  State Name : ACTUAL STATE NAME
        * any number of additional header lines
        * one empty line
        * CSV data (tab-separated)
     */
    @Override
    @Transactional
    public void importData(Reader reader) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(reader);

        State state = importHeader(bufferedReader);

        CsvMapImporter csvImporter = new CsvImporterBuilder()
                .setProcessorMapping(getProcessorMapping())
                .setPreferences(CsvPreference.TAB_PREFERENCE)
                .createAndOpen(bufferedReader);
        try {
            Map<String, Object> record;
            while (null != (record = csvImporter.read())) {
                importFrontLineWorker(record, state);
            }
        } catch (ConstraintViolationException e) {
            throw new CsvImportDataException(createErrorMessage(e.getConstraintViolations(), csvImporter.getRowNumber()), e);
        } catch (InvalidLocationException | FlwImportException | JDODataStoreException e) {
            throw new CsvImportDataException(createErrorMessage(e.getMessage(), csvImporter.getRowNumber()), e);
        }
    }

    @Override
    @Transactional
    public void importFrontLineWorker(Map<String, Object> record, State state) throws InvalidLocationException {
        FrontLineWorker flw = flwFromRecord(record, state);

        record.put(FlwConstants.STATE, state.getCode());
        Map<String, Object> location = locationService.getLocations(record);

        if (flw == null) {
            frontLineWorkerService.add(processInstance(record, location));
        } else {
            frontLineWorkerService.update(processInstance(flw, record, location));
        }
    }

    private State importHeader(BufferedReader bufferedReader) throws IOException {
        String line = readLineWhileBlank(bufferedReader);
        // expect state name in the first line
        if (line.matches("^State Name : .*$")) {
            String stateName = line.substring(line.indexOf(':') + 1).trim();
            State state = stateDataService.findByName(stateName);
            verify(null != state, "State does not exists");
            readLineWhileNotBlank(bufferedReader);
            return state;
        } else {
            throw new IllegalArgumentException("Invalid file format");
        }
    }

    private FrontLineWorker flwFromRecord(Map<String, Object> record, State state) {
        FrontLineWorker flw = null;

        String mctsFlwId = (String) record.get(FlwConstants.ID);
        Long msisdn = (Long) record.get(FlwConstants.CONTACT_NO);

        if (mctsFlwId != null) {
            flw = frontLineWorkerService.getByMctsFlwIdAndState(mctsFlwId, state);
        }

        if (flw == null && msisdn != null) {
            flw = frontLineWorkerService.getByContactNumber(msisdn);

            // If we loaded the flw by msisdn but the flw we found has a different mcts id
            // then the data needs to be hand corrected since we don't know if the msisdn has changed or
            // if the mcts id has changed.
            if (flw != null && mctsFlwId != null && flw.getMctsFlwId() != null && !mctsFlwId.equals(flw.getMctsFlwId())) {
                throw new FlwImportException(String.format("Existing FLW with same MSISDN (%s) but " +
                                        "different MCTS ID (%s != %s)", obscureNumber(msisdn), mctsFlwId, flw.getMctsFlwId()));
            }
        }

        return flw;
    }

    // obscure code from LogHelper in API package. Copy-paste since we cannot do a circular reference
    public static String obscureNumber(Long callingNumber) {
        if (callingNumber == null) {
            return "null";
        }
        String s = callingNumber.toString();
        int l = s.length();
        if (s.length() > MASK_LENGTH) {
            s = org.apache.commons.lang3.StringUtils.repeat('*', l - MASK_LENGTH) + s.substring(l - MASK_LENGTH);
        }
        return s;
    }

    private String readLineWhileBlank(BufferedReader bufferedReader) throws IOException {
        String line;
        do {
            line = bufferedReader.readLine();
        } while (null != line && StringUtils.isBlank(line));
        return line;
    }

    private String readLineWhileNotBlank(BufferedReader bufferedReader) throws IOException {
        String line;
        do {
            line = bufferedReader.readLine();
        } while (null != line && StringUtils.isNotBlank(line));
        return line;
    }


    private FrontLineWorker processInstance(Map<String, Object> record, Map<String, Object> location)
            throws InvalidLocationException {
        Long contactNumber = (Long) record.get(FlwConstants.CONTACT_NO);

        FrontLineWorker flw = new FrontLineWorker(contactNumber);
        flw.setStatus(FrontLineWorkerStatus.INACTIVE);

        return processInstance(flw, record, location);
    }

    private FrontLineWorker processInstance(FrontLineWorker flw, Map<String, Object> record,
                                            Map<String, Object> location) throws InvalidLocationException {

        String mctsFlwId = (String) record.get(FlwConstants.ID);
        Long contactNumber = (Long) record.get(FlwConstants.CONTACT_NO);
        String name = (String) record.get(FlwConstants.NAME);
        String type = (String) record.get(FlwConstants.TYPE);

        if (contactNumber != null) {
            flw.setContactNumber(contactNumber);
        }

        if (mctsFlwId != null) {
            flw.setMctsFlwId(mctsFlwId);
        }

        if (name != null) {
            flw.setName(name);
        }

        setFrontLineWorkerLocation(flw, location);

        if (flw.getLanguage() == null) {
            flw.setLanguage(flw.getDistrict().getLanguage());
        }

        if (flw.getDesignation() == null) {
            flw.setDesignation(type);
        }

        return flw;
    }

    private void setFrontLineWorkerLocation(FrontLineWorker flw, Map<String, Object> locations) throws InvalidLocationException {
        if (locations.get(FlwConstants.STATE) == null && locations.get(FlwConstants.DISTRICT_ID) == null) {
            throw new InvalidLocationException("Missing mandatory state and district fields");
        }

        if (locations.get(FlwConstants.STATE) == null) {
            throw new InvalidLocationException("Missing mandatory state field");
        }

        if (locations.get(FlwConstants.DISTRICT_ID) == null) {
            throw new InvalidLocationException("Missing mandatory district field");
        }

        flw.setState((State) locations.get(FlwConstants.STATE));
        flw.setDistrict((District) locations.get(FlwConstants.DISTRICT_ID));
        flw.setTaluka((Taluka) locations.get(FlwConstants.TALUKA));
        flw.setHealthBlock((HealthBlock) locations.get(FlwConstants.HEALTH_BLOCK));
        flw.setHealthFacility((HealthFacility) locations.get(FlwConstants.PHC));
        flw.setHealthSubFacility((HealthSubFacility) locations.get(FlwConstants.SUBCENTRE));
        flw.setVillage((Village) locations.get(FlwConstants.CENSUS_VILLAGE + FlwConstants.NON_CENSUS_VILAGE));
    }

    private Map<String, CellProcessor> getProcessorMapping() {
        Map<String, CellProcessor> mapping = new HashMap<>();
        mapping.put(FlwConstants.ID, new GetString());
        mapping.put(FlwConstants.CONTACT_NO, new GetLong());
        mapping.put(FlwConstants.NAME, new GetString());
        mapping.put(FlwConstants.DISTRICT_ID, new Optional(new GetLong()));
        mapping.put(FlwConstants.TALUKA, new Optional(new GetString()));
        mapping.put(FlwConstants.HEALTH_BLOCK, new Optional(new GetLong()));
        mapping.put(FlwConstants.PHC, new Optional(new GetLong()));
        mapping.put(FlwConstants.SUBCENTRE, new Optional(new GetLong()));
        mapping.put(FlwConstants.CENSUS_VILLAGE, new Optional(new GetLong()));
        mapping.put(FlwConstants.NON_CENSUS_VILAGE, new Optional(new GetLong()));
        mapping.put(FlwConstants.TYPE, new Optional(new GetString()));

        return mapping;
    }

    private String createErrorMessage(String message, int rowNumber) {
        return String.format("CSV instance error [row: %d]: %s", rowNumber, message);
    }

    private String createErrorMessage(Set<ConstraintViolation<?>> violations, int rowNumber) {
        return String.format("CSV instance error [row: %d]: validation failed for instance of type %s, violations: %s",
                rowNumber, FrontLineWorker.class.getName(), ConstraintViolationUtils.toString(violations));
    }

    private void verify(boolean condition, String message) {
        if (!condition) {
            throw new CsvImportDataException(message);
        }
    }

    @Autowired
    public void setFrontLineWorkerService(FrontLineWorkerService frontLineWorkerService) {
        this.frontLineWorkerService = frontLineWorkerService;
    }

    @Autowired
    public void setStateDataService(StateDataService stateDataService) {
        this.stateDataService = stateDataService;
    }

    @Autowired
    public void setLocationService(LocationService locationService) {
        this.locationService = locationService;
    }
}

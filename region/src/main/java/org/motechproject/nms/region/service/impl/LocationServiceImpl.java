package org.motechproject.nms.region.service.impl;

import org.motechproject.nms.region.domain.District;
import org.motechproject.nms.region.domain.HealthBlock;
import org.motechproject.nms.region.domain.HealthFacility;
import org.motechproject.nms.region.domain.HealthSubFacility;
import org.motechproject.nms.region.domain.State;
import org.motechproject.nms.region.domain.Taluka;
import org.motechproject.nms.region.domain.Village;
import org.motechproject.nms.region.exception.InvalidLocationException;
import org.motechproject.nms.region.repository.StateDataService;
import org.motechproject.nms.region.service.DistrictService;
import org.motechproject.nms.region.service.HealthBlockService;
import org.motechproject.nms.region.service.HealthFacilityService;
import org.motechproject.nms.region.service.HealthSubFacilityService;
import org.motechproject.nms.region.service.LocationService;
import org.motechproject.nms.region.service.TalukaService;
import org.motechproject.nms.region.service.VillageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Location service impl to get location objects
 */
@Service("locationService")
public class LocationServiceImpl implements LocationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocationServiceImpl.class);

    private static final String INVALID = "<%s - %s : Invalid location>";
    private static final String STATE_ID = "StateID";
    private static final String DISTRICT_ID = "District_ID";
    private static final String TALUKA_ID = "Taluka_ID";
    private static final String TALUKA_NAME = "Taluka_Name";
    private static final String HEALTHBLOCK_ID = "HealthBlock_ID";
    private static final String HEALTHBLOCK_NAME = "HealthBlock_Name";
    private static final String PHC_ID = "PHC_ID";
    private static final String PHC_NAME = "PHC_Name";
    private static final String SUBCENTRE_ID = "SubCentre_ID";
    private static final String SUBCENTRE_NAME = "SubCentre_Name";
    private static final String VILLAGE_ID = "Village_ID";
    private static final String VILLAGE_NAME = "Village_Name";
    private static final String NON_CENSUS_VILLAGE = "SVID";

    private StateDataService stateDataService;

    private DistrictService districtService;

    private TalukaService talukaService;

    private VillageService villageService;

    private HealthBlockService healthBlockService;

    private HealthFacilityService healthFacilityService;

    private HealthSubFacilityService healthSubFacilityService;

    @Autowired
    public LocationServiceImpl(StateDataService stateDataService, DistrictService districtService,
                               TalukaService talukaService, VillageService villageService,
                               HealthBlockService healthBlockService, HealthFacilityService healthFacilityService,
                               HealthSubFacilityService healthSubFacilityService) {
        this.stateDataService = stateDataService;
        this.districtService = districtService;
        this.talukaService = talukaService;
        this.villageService = villageService;
        this.healthBlockService = healthBlockService;
        this.healthFacilityService = healthFacilityService;
        this.healthSubFacilityService = healthSubFacilityService;
    }


    private boolean isValidID(final Map<String, Object> map, final String key) {
        Object obj = map.get(key);
        if (obj == null || obj.toString().isEmpty() || "NULL".equalsIgnoreCase(obj.toString())) {
            return false;
        }

        if (obj.getClass().equals(Long.class)) {
            return (Long) obj > 0L;
        }

        return !"0".equals(obj);
    }

    public Map<String, Object> getLocations(Map<String, Object> map) throws InvalidLocationException {
       return getLocations(map, true);
    }

    @Override // NO CHECKSTYLE Cyclomatic Complexity
    @SuppressWarnings("PMD")
    public Map<String, Object> getLocations(Map<String, Object> map, boolean createIfNotExists) throws InvalidLocationException {

        Map<String, Object> locations = new HashMap<>();

        LOGGER.info("map {}", isValidID(map, STATE_ID));

        // set state
        if (!isValidID(map, STATE_ID)) {
            return locations;
        }
        State state = stateDataService.findByCode((Long) map.get(STATE_ID));
        LOGGER.info("state {}", state);
        if (state == null) { // we are here because stateId wasn't null but fetch returned no data
            throw new InvalidLocationException(String.format(INVALID, STATE_ID, map.get(STATE_ID)));
        }
        locations.put(STATE_ID, state);


        // set district
        if (!isValidID(map, DISTRICT_ID)) {
            return locations;
        }
        District district = districtService.findByStateAndCode(state, (Long) map.get(DISTRICT_ID));
        if (district == null) {
            throw new InvalidLocationException(String.format(INVALID, DISTRICT_ID, map.get(DISTRICT_ID)));
        }
        locations.put(DISTRICT_ID, district);


        // set and/or create taluka
        if (!isValidID(map, TALUKA_ID)) {
            return locations;
        }
        Taluka taluka = talukaService.findByDistrictAndCode(district, (String) map.get(TALUKA_ID));
        if (taluka == null && createIfNotExists) {
            taluka = new Taluka();
            taluka.setCode((String) map.get(TALUKA_ID));
            taluka.setName((String) map.get(TALUKA_NAME));
            taluka.setDistrict(district);
            district.getTalukas().add(taluka);
            LOGGER.debug(String.format("Created %s in %s with id %d", taluka, district, taluka.getId()));
        }
        locations.put(TALUKA_ID, taluka);


        // set and/or create village
        Long svid = map.get(NON_CENSUS_VILLAGE) == null ? 0 : (Long) map.get(NON_CENSUS_VILLAGE);
        Long vcode = map.get(VILLAGE_ID) == null ? 0 : (Long) map.get(VILLAGE_ID);
        if (vcode != 0 || svid != 0) {
            Village village = villageService.findByTalukaAndVcodeAndSvid(taluka, vcode, svid);
            if (village == null && createIfNotExists) {
                village = new Village();
                village.setSvid(svid);
                village.setVcode(vcode);
                village.setTaluka(taluka);
                village.setName((String) map.get(VILLAGE_NAME));
                taluka.getVillages().add(village);
                LOGGER.debug(String.format("Created %s in %s with id %d", village, taluka, village.getId()));
            }
            locations.put(VILLAGE_ID + NON_CENSUS_VILLAGE, village);
        }


        // set and/or create health block
        if (!isValidID(map, HEALTHBLOCK_ID)) {
            return locations;
        }
        HealthBlock healthBlock = healthBlockService.findByTalukaAndCode(taluka, (Long) map.get(HEALTHBLOCK_ID));
        if (healthBlock == null && createIfNotExists) {
            healthBlock = new HealthBlock();
            healthBlock.setTaluka(taluka);
            healthBlock.setCode((Long) map.get(HEALTHBLOCK_ID));
            healthBlock.setName((String) map.get(HEALTHBLOCK_NAME));
            taluka.getHealthBlocks().add(healthBlock);
            LOGGER.debug(String.format("Created %s in %s with id %d", healthBlock, taluka, healthBlock.getId()));
        }
        locations.put(HEALTHBLOCK_ID, healthBlock);


        // set and/or create health facility
        if (!isValidID(map, PHC_ID)) {
            return locations;
        }
        HealthFacility healthFacility = healthFacilityService.findByHealthBlockAndCode(healthBlock, (Long) map.get(PHC_ID));
        if (healthFacility == null && createIfNotExists) {
            healthFacility = new HealthFacility();
            healthFacility.setHealthBlock(healthBlock);
            healthFacility.setCode((Long) map.get(PHC_ID));
            healthFacility.setName((String) map.get(PHC_NAME));
            healthBlock.getHealthFacilities().add(healthFacility);
            LOGGER.debug(String.format("Created %s in %s with id %d", healthFacility, healthBlock, healthFacility.getId()));
        }
        locations.put(PHC_ID, healthFacility);


        // set and/or create health sub-facility
        if (!isValidID(map, SUBCENTRE_ID)) {
            return locations;
        }
        HealthSubFacility healthSubFacility = healthSubFacilityService.findByHealthFacilityAndCode(healthFacility, (Long) map.get(SUBCENTRE_ID));
        if (healthSubFacility == null && createIfNotExists) {
            healthSubFacility = new HealthSubFacility();
            healthSubFacility.setHealthFacility(healthFacility);
            healthSubFacility.setCode((Long) map.get(SUBCENTRE_ID));
            healthSubFacility.setName((String) map.get(SUBCENTRE_NAME));
            healthFacility.getHealthSubFacilities().add(healthSubFacility);
            LOGGER.debug(String.format("Created %s in %s with id %d", healthSubFacility, healthFacility, healthSubFacility.getId()));
        }
        locations.put(SUBCENTRE_ID, healthSubFacility);

        return locations;
    }

    @Override
    public Taluka updateTaluka(Map<String, Object> flw, Boolean createIfNotExists) {
        State state = stateDataService.findByCode((Long) flw.get(STATE_ID));
        District district = districtService.findByStateAndCode(state, (Long) flw.get(DISTRICT_ID));
            // set and/or create taluka
        if (!isValidID(flw, TALUKA_ID)) {
            return null;
        }
        Taluka taluka = talukaService.findByDistrictAndCode(district, (String) flw.get(TALUKA_ID));
        if (taluka == null && createIfNotExists) {
            taluka = new Taluka();
            taluka.setCode((String) flw.get(TALUKA_ID));
            taluka.setName((String) flw.get(TALUKA_NAME));
            taluka.setDistrict(district);
            LOGGER.debug(String.format("taluka: %s", taluka.toString()));
            district.getTalukas().add(taluka);
            LOGGER.debug(String.format("Created %s in %s with id %d", taluka, district, taluka.getId()));
        }
        return taluka;
    }

    @Override
    public HealthBlock updateBlock(Map<String, Object> flw, Taluka taluka, Boolean createIfNotExists) {

        // set and/or create health block
        if (!isValidID(flw, HEALTHBLOCK_ID)) {
            return null;
        }
        HealthBlock healthBlock = healthBlockService.findByTalukaAndCode(taluka, (Long) flw.get(HEALTHBLOCK_ID));
        if (healthBlock == null && createIfNotExists) {
            healthBlock = new HealthBlock();
            healthBlock.setTaluka(taluka);
            healthBlock.setCode((Long) flw.get(HEALTHBLOCK_ID));
            healthBlock.setName((String) flw.get(HEALTHBLOCK_NAME));
            taluka.getHealthBlocks().add(healthBlock);
            LOGGER.debug(String.format("Created %s in %s with id %d", healthBlock, taluka, healthBlock.getId()));
        }
        return healthBlock;
    }

    @Override
    public HealthFacility updateFacility(Map<String, Object> flw, HealthBlock healthBlock, Boolean createIfNotExists) {

        // set and/or create health facility
        if (!isValidID(flw, PHC_ID)) {
            return null;
        }
        HealthFacility healthFacility = healthFacilityService.findByHealthBlockAndCode(healthBlock, (Long) flw.get(PHC_ID));
        if (healthFacility == null && createIfNotExists) {
            healthFacility = new HealthFacility();
            healthFacility.setHealthBlock(healthBlock);
            healthFacility.setCode((Long) flw.get(PHC_ID));
            healthFacility.setName((String) flw.get(PHC_NAME));
            healthBlock.getHealthFacilities().add(healthFacility);
            LOGGER.debug(String.format("Created %s in %s with id %d", healthFacility, healthBlock, healthFacility.getId()));
        }
        return healthFacility;
    }

    @Override
    public HealthSubFacility updateSubFacility(Map<String, Object> flw, HealthFacility healthFacility, Boolean createIfNotExists) {

        // set and/or create health sub-facility
        if (!isValidID(flw, SUBCENTRE_ID)) {
            return null;
        }
        HealthSubFacility healthSubFacility = healthSubFacilityService.findByHealthFacilityAndCode(healthFacility, (Long) flw.get(SUBCENTRE_ID));
        if (healthSubFacility == null && createIfNotExists) {
            healthSubFacility = new HealthSubFacility();
            healthSubFacility.setHealthFacility(healthFacility);
            healthSubFacility.setCode((Long) flw.get(SUBCENTRE_ID));
            healthSubFacility.setName((String) flw.get(SUBCENTRE_NAME));
            healthFacility.getHealthSubFacilities().add(healthSubFacility);
            LOGGER.debug(String.format("Created %s in %s with id %d", healthSubFacility, healthFacility, healthSubFacility.getId()));
        }
        return healthSubFacility;
    }

    @Override
    public Village updateVillage(Map<String, Object> flw, Taluka taluka, Boolean createIfNotExists) {

        // set and/or create village
        Long svid = flw.get(NON_CENSUS_VILLAGE) == null ? 0 : (Long) flw.get(NON_CENSUS_VILLAGE);
        Long vcode = flw.get(VILLAGE_ID) == null ? 0 : (Long) flw.get(VILLAGE_ID);
         if (vcode != 0 || svid != 0) {
             Village village = villageService.findByTalukaAndVcodeAndSvid(taluka, vcode, svid);
             if (village == null && createIfNotExists) {
                 village = new Village();
                 village.setSvid(svid);
                 village.setVcode(vcode);
                 village.setTaluka(taluka);
                 village.setName((String) flw.get(VILLAGE_NAME));
                 taluka.getVillages().add(village);
                 LOGGER.debug(String.format("Created %s in %s with id %d", village, taluka, village.getId()));
             }
             return village;
         }
        return null;
    }


    @Override
    public State getState(Long stateId) {

        return stateDataService.findByCode(stateId);
    }

    @Override
    public District getDistrict(Long stateId, Long districtId) {

        State state = getState(stateId);

        if (state != null) {
            return districtService.findByStateAndCode(state, districtId);
        }

        return null;
    }

    @Override
    public Taluka getTaluka(Long stateId, Long districtId, String talukaId) {

        District district = getDistrict(stateId, districtId);

        if (district != null) {
            return talukaService.findByDistrictAndCode(district, talukaId);
        }

        return null;
    }

    @Override
    public Village getVillage(Long stateId, Long districtId, String talukaId, Long vCode, Long svid) {

        Taluka taluka = getTaluka(stateId, districtId, talukaId);

        if (taluka != null) {
            return villageService.findByTalukaAndVcodeAndSvid(taluka, vCode, svid);
        }

        return null;
    }

    @Override
    public Village getCensusVillage(Long stateId, Long districtId, String talukaId, Long vCode) {

        return getVillage(stateId, districtId, talukaId, vCode, 0L);
    }

    @Override
    public Village getNonCensusVillage(Long stateId, Long districtId, String talukaId, Long svid) {

        return getVillage(stateId, districtId, talukaId, 0L, svid);
    }

    @Override
    public HealthBlock getHealthBlock(Long stateId, Long districtId, String talukaId, Long healthBlockId) {

        Taluka taluka = getTaluka(stateId, districtId, talukaId);

        if (taluka != null) {

            return healthBlockService.findByTalukaAndCode(taluka, healthBlockId);
        }

        return null;
    }

    @Override
    public HealthFacility getHealthFacility(Long stateId, Long districtId, String talukaId, Long healthBlockId,
                                            Long healthFacilityId) {

        HealthBlock healthBlock = getHealthBlock(stateId, districtId, talukaId, healthBlockId);

        if (healthBlock != null) {

            return healthFacilityService.findByHealthBlockAndCode(healthBlock, healthFacilityId);
        }

        return null;
    }

    @Override
    public HealthSubFacility getHealthSubFacility(Long stateId, Long districtId, String talukaId,
                                                  Long healthBlockId, Long healthFacilityId,
                                                  Long healthSubFacilityId) {

        HealthFacility healthFacility = getHealthFacility(stateId, districtId, talukaId, healthBlockId,
                healthFacilityId);

        if (healthFacility != null) {

            return healthSubFacilityService.findByHealthFacilityAndCode(healthFacility, healthSubFacilityId);
        }

        return null;
    }
}

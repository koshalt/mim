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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Location service impl to get location objects
 */
@Service("locationService")
public class LocationServiceImpl implements LocationService {

    private static final String INVALID = "<%s - %s : Invalid location>";
    private static final String STATE = "StateID";
    private static final String DISTRICT = "District_ID";
    private static final String TALUKA = "Taluka_ID";
    private static final String HEALTH_BLOCK = "HealthBlock_ID";
    private static final String PHC = "PHC_ID";
    private static final String SUBCENTRE = "SubCentre_ID";
    private static final String CENSUS_VILLAGE = "Village_ID";
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

    @Override // NO CHECKSTYLE Cyclomatic Complexity
    @SuppressWarnings("PMD")
    public Map<String, Object> getLocations(Map<String, Object> locationMapping) throws InvalidLocationException {

        Map<String, Object> locations = new HashMap<>();

        // set state
        if (locationMapping.get(STATE) == null || (Long)locationMapping.get(STATE) == 0) {
            return locations;
        }
        State state = stateDataService.findByCode((Long) locationMapping.get(STATE));
        if (state == null) { // we are here because stateId wasn't null but fetch returned no data
            throw new InvalidLocationException(String.format(INVALID, STATE, locationMapping.get(STATE)));
        }
        locations.put(STATE, state);

        // set district
        if (locationMapping.get(DISTRICT) == null || (Long)locationMapping.get(DISTRICT) == 0) {
            return locations;
        }
        District district = districtService.findByStateAndCode(state, (Long) locationMapping.get(DISTRICT));
        if (district == null) {
            throw new InvalidLocationException(String.format(INVALID, DISTRICT, locationMapping.get(DISTRICT)));
        }
        locations.put(DISTRICT, district);

        // set taluka
        if (locationMapping.get(TALUKA) == null || "0".equals(locationMapping.get(TALUKA))) {
            return locations;
        }
        Taluka taluka = talukaService.findByDistrictAndCode(district, (String) locationMapping.get(TALUKA));
        if (taluka == null) {
            throw new InvalidLocationException(String.format(INVALID, TALUKA, locationMapping.get(TALUKA)));
        }
        locations.put(TALUKA, taluka);

        // check for more sub-locations to fetch
        if (locationMapping.get(HEALTH_BLOCK) != null) {

            // set health block
            HealthBlock healthBlock = healthBlockService.findByTalukaAndCode(taluka, (Long) locationMapping.get(HEALTH_BLOCK));
            if (healthBlock == null) {
                throw new InvalidLocationException(String.format(INVALID, HEALTH_BLOCK, locationMapping.get(HEALTH_BLOCK)));
            }
            locations.put(HEALTH_BLOCK, healthBlock);

            // set health facility
            if (locationMapping.get(PHC) == null || (Long)locationMapping.get(PHC) == 0) {
                return locations;
            }
            HealthFacility healthFacility = healthFacilityService.findByHealthBlockAndCode(healthBlock, (Long) locationMapping.get(PHC));
            if (healthFacility == null) {
                throw new InvalidLocationException(String.format(INVALID, PHC, locationMapping.get(PHC)));
            }
            locations.put(PHC, healthFacility);

            // set health sub-facility
            if (locationMapping.get(SUBCENTRE) == null || (Long)locationMapping.get(SUBCENTRE) == 0) {
                return locations;
            }
            HealthSubFacility healthSubFacility = healthSubFacilityService.findByHealthFacilityAndCode(healthFacility, (Long) locationMapping.get(SUBCENTRE));
            if (healthSubFacility == null) {
                throw new InvalidLocationException(String.format(INVALID, SUBCENTRE, locationMapping.get(SUBCENTRE)));
            }
            locations.put(SUBCENTRE, healthSubFacility);
            return locations;
        } else {
            // Try and set the village if healthblock data isn't available
            Long svid = locationMapping.get(NON_CENSUS_VILLAGE) == null ? 0 : (Long) locationMapping.get(NON_CENSUS_VILLAGE);
            Long vcode = locationMapping.get(CENSUS_VILLAGE) == null ? 0 : (Long) locationMapping.get(CENSUS_VILLAGE);
            if (vcode == 0 && svid == 0) { // nothing to do
                return locations;
            }

            Village village = villageService.findByTalukaAndVcodeAndSvid(taluka, vcode, svid);
            if (village == null) {
                throw new InvalidLocationException(String.format(INVALID,
                        CENSUS_VILLAGE + " " + NON_CENSUS_VILLAGE,
                        locationMapping.get(CENSUS_VILLAGE) + " " + locationMapping.get(NON_CENSUS_VILLAGE)));
            }
            locations.put(CENSUS_VILLAGE + NON_CENSUS_VILLAGE, village);
            return locations;
        }
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

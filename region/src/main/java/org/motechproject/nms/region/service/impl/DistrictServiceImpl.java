package org.motechproject.nms.region.service.impl;

import org.datanucleus.store.rdbms.query.ForwardQueryResult;
import org.motechproject.mds.query.QueryExecution;
import org.motechproject.mds.util.InstanceSecurityRestriction;
import org.motechproject.nms.region.domain.District;
import org.motechproject.nms.region.domain.Language;
import org.motechproject.nms.region.repository.DistrictDataService;
import org.motechproject.nms.region.service.DistrictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.jdo.Query;
import java.util.HashSet;
import java.util.Set;

@Service("districtService")
public class DistrictServiceImpl implements DistrictService {

    private DistrictDataService districtDataService;

    @Autowired
    public DistrictServiceImpl(DistrictDataService districtDataService) {
        this.districtDataService = districtDataService;
    }

    @Override
    public Set<District> getAllForLanguage(final Language language) {

        QueryExecution<Set<District>> stateQueryExecution = new QueryExecution<Set<District>>() {
            @Override
            public Set<District> execute(Query query, InstanceSecurityRestriction restriction) {

                query.setFilter("language == _language");
                query.declareParameters("org.motechproject.nms.region.domain.Language _language");

                Set<District> districts = new HashSet<>();
                ForwardQueryResult fqr = (ForwardQueryResult) query.execute(language);
                for (Object o : fqr) {
                    districts.add((District) o);
                }
                return districts;
            }
        };

        return districtDataService.executeQuery(stateQueryExecution);
    }
}

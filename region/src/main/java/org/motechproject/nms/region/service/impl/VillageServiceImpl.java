package org.motechproject.nms.region.service.impl;

import org.datanucleus.store.rdbms.query.ForwardQueryResult;
import org.motechproject.mds.query.SqlQueryExecution;
import org.motechproject.nms.region.domain.Taluka;
import org.motechproject.nms.region.domain.Village;
import org.motechproject.nms.region.repository.VillageDataService;
import org.motechproject.nms.region.service.VillageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.jdo.Query;

@Service("villageService")
public class VillageServiceImpl implements VillageService {

    @Autowired
    private VillageDataService dataService;


    @Override
    public Village findByTalukaAndVcodeAndSvid(final Taluka taluka, final Long vcode, final Long svid) {

        SqlQueryExecution<Village> queryExecution = new SqlQueryExecution<Village>() {

            @Override
            public String getSqlQuery() {
                return "select *  from nms_villages where taluka_id_oid = ? and vcode = ? and svid = ?";
            }

            @Override
            public Village execute(Query query) {
                query.setClass(Village.class);
                ForwardQueryResult fqr = (ForwardQueryResult) query.execute(taluka.getId(), vcode, svid);
                if (fqr.isEmpty()) {
                    return null;
                }
                if (fqr.size() == 1) {
                    return (Village) fqr.get(0);
                }
                throw new IllegalStateException("More than one row returned!");
            }
        };

        return dataService.executeSQLQuery(queryExecution);
    }


    @Override
    public Village findByTalukaAndSvid(final Taluka taluka, final Long svid) {

        SqlQueryExecution<Village> queryExecution = new SqlQueryExecution<Village>() {

            @Override
            public String getSqlQuery() {
                return "select *  from nms_villages where taluka_id_oid = ? and svid = ?";
            }

            @Override
            public Village execute(Query query) {
                query.setClass(Village.class);
                ForwardQueryResult fqr = (ForwardQueryResult) query.execute(taluka.getId(), svid);
                if (fqr.isEmpty()) {
                    return null;
                }
                if (fqr.size() == 1) {
                    return (Village) fqr.get(0);
                }
                throw new IllegalStateException("More than one row returned!");
            }
        };

        return dataService.executeSQLQuery(queryExecution);
    }
}

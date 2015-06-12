package org.motechproject.nms.region.service.impl;

import org.motechproject.nms.csv.utils.GetLong;
import org.motechproject.nms.csv.utils.GetString;
import org.motechproject.nms.region.domain.HealthBlock;
import org.motechproject.nms.region.repository.HealthBlockDataService;
import org.motechproject.nms.region.service.HealthBlockImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.supercsv.cellprocessor.ift.CellProcessor;

import java.util.HashMap;
import java.util.Map;

@Service("healthBlockImportService")
public class HealthBlockImportServiceImpl extends BaseLocationImportService<HealthBlock> implements HealthBlockImportService {

    public static final String BID = "BID";
    public static final String REGIONAL_NAME = "Name_G";
    public static final String NAME = "Name_E";
    public static final String HQ = "HQ";
    public static final String TALUKA_CODE = "TCode";

    public static final String BID_FIELD = "code";
    public static final String REGIONAL_NAME_FIELD = "regionalName";
    public static final String NAME_FIELD = "name";
    public static final String HQ_FIELD = "hq";
    public static final String TALUKA_CODE_FIELD = "taluka";


    @Autowired
    public HealthBlockImportServiceImpl(HealthBlockDataService healthBlockDataService) {
        super(HealthBlock.class, healthBlockDataService);
    }

    @Override
    protected Map<String, CellProcessor> getProcessorMapping() {
        Map<String, CellProcessor> mapping = new HashMap<>();
        mapping.put(BID, new GetLong());
        mapping.put(REGIONAL_NAME, new GetString());
        mapping.put(NAME, new GetString());
        mapping.put(HQ, new GetString());
        mapping.put(TALUKA_CODE, new GetString());
        return mapping;
    }

    @Override
    protected Map<String, String> getFieldNameMapping() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put(BID, BID_FIELD);
        mapping.put(REGIONAL_NAME, REGIONAL_NAME_FIELD);
        mapping.put(NAME, NAME_FIELD);
        mapping.put(HQ, HQ_FIELD);
        mapping.put(TALUKA_CODE, TALUKA_CODE_FIELD);
        return mapping;
    }
}

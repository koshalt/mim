package org.motechproject.nms.region.service.impl;

import org.motechproject.nms.csv.utils.GetInteger;
import org.motechproject.nms.csv.utils.GetString;
import org.motechproject.nms.region.domain.Taluka;
import org.motechproject.nms.region.repository.TalukaDataService;
import org.motechproject.nms.region.service.TalukaImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.supercsv.cellprocessor.ift.CellProcessor;

import java.util.HashMap;
import java.util.Map;

@Service("talukaImportService")
public class TalukaImportServiceImpl extends BaseLocationImportService<Taluka> implements TalukaImportService {

    public static final String TALUKA_CODE = "TCode";
    public static final String IDENTITY = "ID";
    public static final String REGIONAL_NAME = "Name_G";
    public static final String NAME = "Name_E";
    public static final String DISTRICT_CODE = "DCode";

    public static final String TALUKA_CODE_FIELD = "code";
    public static final String IDENTITY_FIELD = "identity";
    public static final String REGIONAL_NAME_FIELD = "regionalName";
    public static final String NAME_FIELD = "name";
    public static final String DISTRICT_CODE_FIELD = "district";

    @Autowired
    public TalukaImportServiceImpl(TalukaDataService talukaDataService) {
        super(Taluka.class, talukaDataService);
    }

    @Override
    protected Map<String, CellProcessor> getProcessorMapping() {
        Map<String, CellProcessor> mapping = new HashMap<>();
        mapping.put(TALUKA_CODE, new GetString());
        mapping.put(IDENTITY, new GetInteger());
        mapping.put(REGIONAL_NAME, new GetString());
        mapping.put(NAME, new GetString());
        mapping.put(DISTRICT_CODE, new GetString());
        return mapping;
    }

    @Override
    protected Map<String, String> getFieldNameMapping() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put(TALUKA_CODE, TALUKA_CODE_FIELD);
        mapping.put(IDENTITY, IDENTITY_FIELD);
        mapping.put(REGIONAL_NAME, REGIONAL_NAME_FIELD);
        mapping.put(NAME, NAME_FIELD);
        mapping.put(DISTRICT_CODE, DISTRICT_CODE_FIELD);
        return mapping;
    }
}

package org.motechproject.nms.rch.service.impl;

import org.joda.time.LocalDate;
import org.motechproject.nms.rch.domain.RchImportFacilitator;
import org.motechproject.nms.rch.domain.RchUserType;
import org.motechproject.nms.rch.exception.RchFileManipulationException;
import org.motechproject.nms.rch.repository.RchImportFacilitatorDataService;
import org.motechproject.nms.rch.service.RchImportFacilitatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


@Service("rchImportFacilitatorService")
public class RchImportFacilitatorServiceImpl implements RchImportFacilitatorService {

    private RchImportFacilitatorDataService rchImportFacilitatorDataService;

    private static final Logger LOGGER = LoggerFactory.getLogger(RchImportFacilitatorServiceImpl.class);


    @Autowired
    public RchImportFacilitatorServiceImpl(RchImportFacilitatorDataService rchImportFacilitatorDataService) {
        this.rchImportFacilitatorDataService = rchImportFacilitatorDataService;
    }

    @Override
    public void createImportFileAudit(RchImportFacilitator rchImportFacilitator) throws RchFileManipulationException  {
        Long state = rchImportFacilitator.getStateId();
        LocalDate importDate = rchImportFacilitator.getImportDate();
        RchUserType rchUserType = rchImportFacilitator.getUserType();
        RchImportFacilitator rchImportFacilitator1 = rchImportFacilitatorDataService.getByStateIdAndImportDateAndUserType(state, importDate, rchUserType);
        if (rchImportFacilitator.getFileName() == null) {
            throw new RchFileManipulationException("Invalid file name");
        } else if (rchImportFacilitator1 != null) {
            LOGGER.error("A record already present for the same state and today's date.");
        } else {
            rchImportFacilitatorDataService.create(rchImportFacilitator);
        }
    }

    @Override
    public List<RchImportFacilitator> findByImportDateAndRchUserType(LocalDate importDate, RchUserType rchUserType) {
        return rchImportFacilitatorDataService.getByImportDateAndUsertype(importDate, rchUserType);
    }
}


package org.motechproject.nms.rch.service;

import org.joda.time.LocalDate;
import org.motechproject.event.MotechEvent;
import org.motechproject.nms.rch.domain.RchUserType;
import org.motechproject.nms.rch.exception.RchFileManipulationException;

import java.net.URL;

/**
 * Created by beehyvsc on 1/6/17.
 */
public interface RchWebServiceFacade {
    boolean getMothersData(LocalDate from, LocalDate to, URL endpoint, Long stateId);

    boolean getChildrenData(LocalDate from, LocalDate to, URL endpoint, Long stateId);

    boolean getAnmAshaData(LocalDate from, LocalDate to, URL endpoint, Long stateId);

    void readMotherResponseFromFile(MotechEvent event) throws RchFileManipulationException;

    void readChildResponseFromFile(MotechEvent event) throws RchFileManipulationException;

    void locationUpdateInTable(Long stateId, RchUserType rchUserType);

}

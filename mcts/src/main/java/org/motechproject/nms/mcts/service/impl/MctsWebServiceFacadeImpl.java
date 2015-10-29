package org.motechproject.nms.mcts.service.impl;

import org.joda.time.LocalDate;
import org.motechproject.nms.mcts.contract.AnmAshaDataSet;
import org.motechproject.nms.mcts.contract.ChildrenDataSet;
import org.motechproject.nms.mcts.contract.MothersDataSet;
import org.motechproject.nms.mcts.exception.MctsInvalidResponseStructureException;
import org.motechproject.nms.mcts.exception.MctsWebServiceExeption;
import org.motechproject.nms.mcts.service.MctsWebServiceFacade;
import org.motechproject.nms.mcts.soap.DS_GetAnmAshaDataResponseDS_GetAnmAshaDataResult;
import org.motechproject.nms.mcts.soap.DS_GetChildDataResponseDS_GetChildDataResult;
import org.motechproject.nms.mcts.soap.DS_GetMotherDataResponseDS_GetMotherDataResult;
import org.motechproject.nms.mcts.soap.IMctsService;
import org.motechproject.nms.mcts.soap.MctsServiceLocator;
import org.motechproject.nms.mcts.utils.Constants;
import org.motechproject.nms.mcts.utils.MarshallUtils;
import org.motechproject.server.config.SettingsFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBException;
import javax.xml.rpc.ServiceException;
import java.net.URL;
import java.rmi.RemoteException;

@Service("mctsWebServiceFacade")
public class MctsWebServiceFacadeImpl implements MctsWebServiceFacade {

    private static final String DATE_FORMAT = "dd-MM-yyyy";

    @Autowired
    @Qualifier("mctsSettings")
    private SettingsFacade settingsFacade;

    @Autowired
    @Qualifier("mctsServiceLocator")
    private MctsServiceLocator mctsServiceLocator;

    @Override
    public ChildrenDataSet getChildrenData(LocalDate from, LocalDate to, URL endpoint, Long stateId) {
        DS_GetChildDataResponseDS_GetChildDataResult result;
        IMctsService dataService = getService(endpoint);

        try {
            result = dataService.DS_GetChildData(settingsFacade.getProperty(Constants.MCTS_USER_ID), settingsFacade.getProperty(Constants.MCTS_PASSWORD),
                    from.toString(DATE_FORMAT), to.toString(DATE_FORMAT), stateId.toString());
        } catch (RemoteException e) {
            throw new MctsWebServiceExeption(String.format("Cannot read children data from %s state. Remote Server Error", stateId), e);
        }

        try {
            validChildrenDataResponse(result, stateId);
            return (ChildrenDataSet) MarshallUtils.unmarshall(result.get_any()[1].getChildren().get(0).toString(), ChildrenDataSet.class);
        } catch (JAXBException e) {
            throw new MctsInvalidResponseStructureException(String.format("Cannot deserialize children data from %s location", stateId), e);
        }
    }

    @Override
    public MothersDataSet getMothersData(LocalDate from, LocalDate to, URL endpoint, Long stateId) {
        DS_GetMotherDataResponseDS_GetMotherDataResult result;
        IMctsService dataService = getService(endpoint);

        try {
            result = dataService.DS_GetMotherData(settingsFacade.getProperty(Constants.MCTS_USER_ID), settingsFacade.getProperty(Constants.MCTS_PASSWORD),
                    from.toString(DATE_FORMAT), to.toString(DATE_FORMAT), stateId.toString());
        } catch (RemoteException e) {
            throw new MctsWebServiceExeption(String.format("Cannot read mothers data from %s state. Remote Server Error", stateId), e);
        }

        try {
            validMothersDataResponse(result, stateId);
            return (MothersDataSet) MarshallUtils.unmarshall(result.get_any()[1].getChildren().get(0).toString(), MothersDataSet.class);
        } catch (JAXBException e) {
            throw new MctsInvalidResponseStructureException(String.format("Cannot deserialize mothers data from %s location", stateId), e);
        }
    }

    @Override
    public AnmAshaDataSet getAnmAshaData(LocalDate from, LocalDate to, URL endpoint, Long stateId) {
        DS_GetAnmAshaDataResponseDS_GetAnmAshaDataResult result;
        IMctsService dataService = getService(endpoint);

        try {
            result = dataService.DS_GetAnmAshaData(settingsFacade.getProperty(Constants.MCTS_USER_ID), settingsFacade.getProperty(Constants.MCTS_PASSWORD),
                    from.toString(DATE_FORMAT), to.toString(DATE_FORMAT), stateId.toString());
        } catch (RemoteException e) {
            throw new MctsWebServiceExeption(String.format("Cannot read anm asha data from %s state. Remote Server Error", stateId), e);
        }

        try {
            validAnmAshaDataResponse(result, stateId);
            return (AnmAshaDataSet) MarshallUtils.unmarshall(result.get_any()[1].getChildren().get(0).toString(), AnmAshaDataSet.class);
        } catch (JAXBException e) {
            throw new MctsInvalidResponseStructureException(String.format("Cannot deserialize anm asha data from %s location", stateId), e);
        }
    }

    private IMctsService getService(URL endpoint) {
        try {
            if (endpoint != null) {
                return mctsServiceLocator.getbasicEndpoint(endpoint);
            } else {
                return mctsServiceLocator.getbasicEndpoint();
            }
        } catch (ServiceException e) {
            throw new MctsWebServiceExeption("Cannot retrieve MCTS Service for the endpoint", e);
        }
    }

    private void validMothersDataResponse(DS_GetMotherDataResponseDS_GetMotherDataResult data, Long stateId) {
        if (data.get_any().length != 2) {
            throw new MctsInvalidResponseStructureException("Invalid mothers data response for location " + stateId);
        }

        if (data.get_any()[1].getChildren() == null || data.get_any()[1].getChildren().size() < 1) {
            throw new MctsInvalidResponseStructureException("Invalid mothers data response " + stateId);
        }
    }

    private void validChildrenDataResponse(DS_GetChildDataResponseDS_GetChildDataResult data, Long stateId) {
        if (data.get_any().length != 2) {
            throw new MctsInvalidResponseStructureException("Invalid children data response for location " + stateId);
        }

        if (data.get_any()[1].getChildren() == null || data.get_any()[1].getChildren().size() < 1) {
            throw new MctsInvalidResponseStructureException("Invalid children data response " + stateId);
        }
    }

    private void validAnmAshaDataResponse(DS_GetAnmAshaDataResponseDS_GetAnmAshaDataResult data, Long stateId) {
        if (data.get_any().length != 2) {
            throw new MctsInvalidResponseStructureException("Invalid anm asha data response for location " + stateId);
        }

        if (data.get_any()[1].getChildren() == null || data.get_any()[1].getChildren().size() < 1) {
            throw new MctsInvalidResponseStructureException("Invalid anm asha data response " + stateId);
        }
    }
}

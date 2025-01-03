package org.motechproject.nms.imi.service;

import org.motechproject.nms.imi.service.contract.TargetFileNotification;
import org.motechproject.nms.imi.web.contract.FileProcessedStatusRequest;
import org.motechproject.nms.kilkari.domain.SubscriptionOrigin;

import java.util.HashMap;

/**
 * Creating the targetFile: a csv file containing all the phone numbers to be called by the IVR system
 */
public interface TargetFileService {
    /**
     * Probably only to be called by an IT. This service's constructor sets the repeating
     * nms.obd.generate_target_file MOTECH event which triggers the daily generation of the targetFile.
     */
    HashMap<String, TargetFileNotification> generateTargetFile(boolean split);

    HashMap<String, TargetFileNotification> generateTargetFileApi(boolean split);

    TargetFileNotification generateTargetFileWhatsApp();

    /**
     * The IVR system invoked the obdFileProcessedStatusNotification http endpoint signalling the completion of the
     * processing of the targetFile we generated
     *
     * @param request
     */
    void handleFileProcessedStatusNotification(FileProcessedStatusRequest request);



    /**
     * The IVR system invoked the obdFileProcessedStatusNotification http endpoint signalling the completion of the
     * processing of the targetFile for whatsapp we generated
     *
     * @param request
     */
    void handleFileProcessedStatusNotificationWhatsApp(FileProcessedStatusRequest request);

    void handleWhatsAppSMSFileProcessedStatusNotification(FileProcessedStatusRequest request);

    /**
     * given a call type (fresh or retry) and subscription origin (MCTS or IVR), returns the IMI ServiceID
     * (which tells IMI to check for DND or not)
     *
     * @param freshCall    is this a fresh call or a retry?
     * @param origin       MCTS or IVR subscription?
     * @return the IMI ServiceID
     */
    String serviceIdFromOrigin(boolean freshCall, SubscriptionOrigin origin);

    String serviceIdFromOriginJh(boolean freshCall, SubscriptionOrigin origin);

    HashMap<String, TargetFileNotification> generateWhatsAppSMSTargetFile();

    void copyWhatsappSMSTargetFiletoRemoteAndNotifyIVR(HashMap<String, TargetFileNotification> tfn);

}

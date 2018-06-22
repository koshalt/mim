package org.motechproject.nms.flwUpdate.service;

import org.motechproject.nms.flw.exception.FlwExistingRecordException;
import org.motechproject.nms.kilkari.contract.AnmAshaRecord;
import org.motechproject.nms.kilkari.contract.RchAnmAshaRecord;
import org.motechproject.nms.kilkari.domain.SubscriptionOrigin;
import org.motechproject.nms.region.domain.State;
import org.motechproject.nms.region.exception.InvalidLocationException;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;

public interface FrontLineWorkerImportService {

    void importData(Reader reader, SubscriptionOrigin importOrigin) throws IOException;

    void importMctsFrontLineWorker(Map<String, Object> record, State state) throws InvalidLocationException, FlwExistingRecordException;

    void importRchFrontLineWorker(Map<String, Object> record, State state) throws InvalidLocationException, FlwExistingRecordException;
    /**
     * Used to create or update an FLW from mcts or other sync services
     * @param flwRecord key-value pair of properties for flw
     */
    boolean createUpdate(Map<String, Object> flwRecord, SubscriptionOrigin importOrigin);

    boolean updateLoc(Map<String, Object> flwRecord);

    RchAnmAshaRecord convertMapToRchAsha(Map<String, Object> record);

    AnmAshaRecord convertMapToAsha(Map<String, Object> record);
}

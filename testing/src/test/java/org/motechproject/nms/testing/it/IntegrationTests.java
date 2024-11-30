package org.motechproject.nms.testing.it;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.motechproject.nms.testing.it.api.CallDetailsControllerBundleIT;
import org.motechproject.nms.testing.it.api.KilkariControllerBundleIT;
import org.motechproject.nms.testing.it.api.LanguageControllerBundleIT;
import org.motechproject.nms.testing.it.api.MobileAcademyControllerBundleIT;
import org.motechproject.nms.testing.it.api.OpsControllerBundleIT;
import org.motechproject.nms.testing.it.api.UserControllerBundleIT;
import org.motechproject.nms.testing.it.flw.FrontLineWorkerServiceBundleIT;
import org.motechproject.nms.testing.it.flw.ServiceUsageCapServiceBundleIT;
import org.motechproject.nms.testing.it.flw.ServiceUsageServiceBundleIT;
import org.motechproject.nms.testing.it.flwUpdate.FrontLineWorkerImportServiceBundleIT;
import org.motechproject.nms.testing.it.flwUpdate.FrontLineWorkerUpdateImportServiceBundleIT;
import org.motechproject.nms.testing.it.flw.WhiteListServiceBundleIT;
import org.motechproject.nms.testing.it.imi.CdrFileServiceBundleIT;
import org.motechproject.nms.testing.it.imi.ImiControllerCdrBundleIT;
import org.motechproject.nms.testing.it.imi.ImiControllerObdBundleIT;
import org.motechproject.nms.testing.it.imi.TargetFileServiceBundleIT;
import org.motechproject.nms.testing.it.kilkari.CsrServiceBundleIT;
import org.motechproject.nms.testing.it.kilkari.MctsBeneficiaryImportServiceBundleIT;
import org.motechproject.nms.testing.it.kilkari.RchBeneficiaryImportServiceBundleIT;
import org.motechproject.nms.testing.it.kilkari.SubscriberServiceBundleIT;
import org.motechproject.nms.testing.it.kilkari.SubscriptionServiceBundleIT;
import org.motechproject.nms.testing.it.ma.MobileAcademyServiceBundleIT;
import org.motechproject.nms.testing.it.mcts.MctsImportBundleIT;
import org.motechproject.nms.testing.it.mcts.MctsWebServiceFacadeBundleIT;
import org.motechproject.nms.testing.it.props.PropertyServiceBundleIT;
import org.motechproject.nms.testing.it.rch.RchWebServiceFacadeBundleIT;
import org.motechproject.nms.testing.it.region.CircleServiceBundleIT;
import org.motechproject.nms.testing.it.region.LanguageLocationCodesImportServiceBundleIT;
import org.motechproject.nms.testing.it.region.LanguageServiceBundleIT;
import org.motechproject.nms.testing.it.region.LocationDataImportServiceBundleIT;
import org.motechproject.nms.testing.it.region.LocationServiceBundleIT;
import org.motechproject.nms.testing.it.region.NationalDefaultLanguageLocationBundleIT;
import org.motechproject.nms.testing.it.testing.BundleIT;
import org.motechproject.nms.testing.it.tracking.TrackChangesBundleIT;
import org.motechproject.nms.testing.it.tracking.TrackManyToManyChangesBundleIT;
import org.motechproject.nms.testing.it.tracking.TrackOneToManyChangesBundleIT;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    /**
     * API
     */
    LanguageControllerBundleIT.class,
    UserControllerBundleIT.class,
    CallDetailsControllerBundleIT.class,
    KilkariControllerBundleIT.class,
    MobileAcademyControllerBundleIT.class,
    OpsControllerBundleIT.class,

    /**
     * FLW
     */
    ServiceUsageServiceBundleIT.class,
    ServiceUsageCapServiceBundleIT.class,
    WhiteListServiceBundleIT.class,
    FrontLineWorkerServiceBundleIT.class,

    /**
     * FLW UPDATE
     */
    FrontLineWorkerImportServiceBundleIT.class,
    FrontLineWorkerUpdateImportServiceBundleIT.class,

    /**
     * IMI
     * https://github.com/motech-implementations/mim/issues/381 (Re-enable)
     */
    ImiControllerCdrBundleIT.class,
    ImiControllerObdBundleIT.class,
    TargetFileServiceBundleIT.class,
    CdrFileServiceBundleIT.class,

    /**
     * Mobile Academy
     */
    MobileAcademyServiceBundleIT.class,

    /**
     * Region
     */
    LocationDataImportServiceBundleIT.class,
    LocationServiceBundleIT.class,
    CircleServiceBundleIT.class,
    NationalDefaultLanguageLocationBundleIT.class,
    LanguageLocationCodesImportServiceBundleIT.class,
    LanguageServiceBundleIT.class,

    /**
     * RCH
     */
    RchWebServiceFacadeBundleIT.class,

    /**
     * Kilkari
     */
    RchBeneficiaryImportServiceBundleIT.class,
    MctsBeneficiaryImportServiceBundleIT.class,
    SubscriptionServiceBundleIT.class,
    SubscriberServiceBundleIT.class,
    CsrServiceBundleIT.class,

    /**
     * Props
     */
    PropertyServiceBundleIT.class,

    /**
     * MCTS
     */
    MctsWebServiceFacadeBundleIT.class,
    MctsImportBundleIT.class,

    /**
     * Testing
     */
    BundleIT.class,

    /**
     * Tracking
     */
    TrackChangesBundleIT.class,
    TrackOneToManyChangesBundleIT.class,
    TrackManyToManyChangesBundleIT.class,
})
public class IntegrationTests {
}
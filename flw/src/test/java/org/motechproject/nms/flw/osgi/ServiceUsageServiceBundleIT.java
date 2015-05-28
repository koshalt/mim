package org.motechproject.nms.flw.osgi;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.motechproject.nms.flw.domain.FrontLineWorker;
import org.motechproject.nms.flw.domain.FrontLineWorkerStatus;
import org.motechproject.nms.flw.domain.ServiceUsage;
import org.motechproject.nms.flw.repository.FrontLineWorkerDataService;
import org.motechproject.nms.flw.repository.ServiceUsageDataService;
import org.motechproject.nms.flw.service.FrontLineWorkerService;
import org.motechproject.nms.flw.service.ServiceUsageService;
import org.motechproject.nms.props.domain.Service;
import org.motechproject.testing.osgi.BasePaxIT;
import org.motechproject.testing.osgi.container.MotechNativeTestContainerFactory;
import org.ops4j.pax.exam.ExamFactory;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import javax.inject.Inject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Verify that HelloWorldService present, functional.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
@ExamFactory(MotechNativeTestContainerFactory.class)
public class ServiceUsageServiceBundleIT extends BasePaxIT {

    @Inject
    private FrontLineWorkerDataService frontLineWorkerDataService;

    @Inject
    private FrontLineWorkerService frontLineWorkerService;

    @Inject
    private ServiceUsageDataService serviceUsageDataService;

    @Inject
    private ServiceUsageService serviceUsageService;

    private void setupData() {
        for (FrontLineWorker flw: frontLineWorkerDataService.retrieveAll()) {
            flw.setStatus(FrontLineWorkerStatus.INVALID);
            flw.setInvalidationDate(new DateTime().withDate(2011, 8, 1));

            frontLineWorkerDataService.update(flw);
        }

        serviceUsageDataService.deleteAll();
        frontLineWorkerDataService.deleteAll();
    }

    @Test
    public void testServiceUsageServicePresent() throws Exception {
        assertNotNull(serviceUsageService);
    }

    @Test
    public void testGetCurrentMonthlyUsageForFLWAndService() throws Exception {
        setupData();
        FrontLineWorker flw = new FrontLineWorker("Valid Worker", 1111111111L);
        frontLineWorkerService.add(flw);

        FrontLineWorker flwIgnored = new FrontLineWorker("Ignored Worker", 2222222222L);
        frontLineWorkerService.add(flwIgnored);

        // A usage record from last month that should be ignored
        ServiceUsage lastMonth = new ServiceUsage(flw, Service.MOBILE_ACADEMY, 1, 1, 1, DateTime.now().minusMonths(2));
        serviceUsageDataService.create(lastMonth);

        // A usage record for a different service that should be ignored
        ServiceUsage differentService = new ServiceUsage(flw, Service.MOBILE_KUNJI, 1, 1, 1, DateTime.now());
        serviceUsageDataService.create(differentService);

        // A usage record for a different FLW that should be ignored
        ServiceUsage differentFLW = new ServiceUsage(flwIgnored, Service.MOBILE_ACADEMY, 1, 1, 1, DateTime.now());
        serviceUsageDataService.create(differentFLW);

        // Two valid records that should get aggregated
        ServiceUsage recordOne = new ServiceUsage(flw, Service.MOBILE_ACADEMY, 1, 0, 1, DateTime.now());
        serviceUsageDataService.create(recordOne);

        ServiceUsage recordTwo = new ServiceUsage(flw, Service.MOBILE_ACADEMY, 1, 1, 0, DateTime.now());
        serviceUsageDataService.create(recordTwo);

        ServiceUsage serviceUsage = serviceUsageService.getCurrentMonthlyUsageForFLWAndService(flw, Service.MOBILE_ACADEMY);

        assertEquals(flw, serviceUsage.getFrontLineWorker());
        assertEquals(Service.MOBILE_ACADEMY, serviceUsage.getService());
        assertEquals(2, serviceUsage.getUsageInPulses());
        assertEquals(1, serviceUsage.getEndOfUsage());
        assertEquals(1, serviceUsage.getWelcomePrompt());

        serviceUsageDataService.delete(lastMonth);
        serviceUsageDataService.delete(differentService);
        serviceUsageDataService.delete(differentFLW);
        serviceUsageDataService.delete(recordOne);
        serviceUsageDataService.delete(recordTwo);

        flw.setStatus(FrontLineWorkerStatus.INVALID);
        flw.setInvalidationDate(new DateTime().withDate(2011, 8, 1));
        frontLineWorkerService.update(flw);
        frontLineWorkerService.delete(flw);

        flwIgnored.setStatus(FrontLineWorkerStatus.INVALID);
        flwIgnored.setInvalidationDate(new DateTime().withDate(2011, 8, 1));
        frontLineWorkerService.update(flwIgnored);
        frontLineWorkerService.delete(flwIgnored);
    }

    @Test
    public void testGetCurrentMonthlyUsageForFLWAndServiceWithNoService() throws Exception {
        setupData();
        FrontLineWorker flw = new FrontLineWorker("Valid Worker", 1111111111L);
        frontLineWorkerService.add(flw);

        ServiceUsage serviceUsage = serviceUsageService.getCurrentMonthlyUsageForFLWAndService(flw, Service.MOBILE_ACADEMY);

        assertEquals(flw, serviceUsage.getFrontLineWorker());
        assertEquals(Service.MOBILE_ACADEMY, serviceUsage.getService());
        assertEquals(0, serviceUsage.getUsageInPulses());
        assertEquals(0, serviceUsage.getEndOfUsage());
        assertEquals(0, serviceUsage.getWelcomePrompt());

        serviceUsageDataService.delete(serviceUsage);

        flw.setStatus(FrontLineWorkerStatus.INVALID);
        flw.setInvalidationDate(new DateTime().withDate(2011, 8, 1));
        frontLineWorkerService.update(flw);
        frontLineWorkerService.delete(flw);
    }

}

package org.motechproject.nms.location.osgi;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * HelloWorld bundle integration tests suite.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    LocationServiceBundleIT.class
})
public class IntegrationTests {
}

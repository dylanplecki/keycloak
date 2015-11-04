package org.keycloak.testsuite.adapter.example;

import org.keycloak.testsuite.adapter.AbstractExampleAdapterTest;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.graphene.page.Page;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.*;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.testsuite.auth.page.account.Account;
import static org.keycloak.testsuite.util.IOUtil.loadRealm;
import static org.keycloak.testsuite.util.URLAssert.assertCurrentUrlStartsWith;
import org.keycloak.testsuite.adapter.page.CustomerPortalExample;
import org.keycloak.testsuite.adapter.page.DatabaseServiceExample;
import org.keycloak.testsuite.adapter.page.ProductPortalExample;
import static org.keycloak.testsuite.auth.page.AuthRealm.DEMO;
import org.openqa.selenium.By;

public abstract class AbstractDemoExampleAdapterTest extends AbstractExampleAdapterTest {

    @Page
    private CustomerPortalExample customerPortalExample;
    @Page
    private ProductPortalExample productPortalExample;
    @Page
    private DatabaseServiceExample databaseServiceExample;

    @Page
    private Account testRealmAccount;

    @Deployment(name = CustomerPortalExample.DEPLOYMENT_NAME)
    private static WebArchive customerPortalExample() throws IOException {
        return exampleDeployment(CustomerPortalExample.DEPLOYMENT_NAME);
    }

    @Deployment(name = ProductPortalExample.DEPLOYMENT_NAME)
    private static WebArchive productPortalExample() throws IOException {
        return exampleDeployment(ProductPortalExample.DEPLOYMENT_NAME);
    }

    @Deployment(name = DatabaseServiceExample.DEPLOYMENT_NAME)
    private static WebArchive databaseServiceExample() throws IOException {
        return exampleDeployment("database-service");
    }

    @Override
    public void addAdapterTestRealms(List<RealmRepresentation> testRealms) {
        testRealms.add(
                loadRealm(new File(EXAMPLES_HOME_DIR + "/preconfigured-demo/testrealm.json")));
    }

    @Override
    public void setDefaultPageUriParameters() {
        super.setDefaultPageUriParameters();
        testRealmPage.setAuthRealm(DEMO);
        testRealmLoginPage.setAuthRealm(DEMO);
        testRealmAccount.setAuthRealm(DEMO);
    }

    @Before
    public void beforeDemoExampleTest() {
        customerPortalExample.navigateTo();
        driver.manage().deleteAllCookies();
        productPortalExample.navigateTo();
        driver.manage().deleteAllCookies();
    }

    @Test
    public void customerPortalListingTest() {

        customerPortalExample.navigateTo();
        customerPortalExample.customerListing();

        testRealmLoginPage.form().login("bburke@redhat.com", "password");

        assertCurrentUrlStartsWith(customerPortalExample);
        customerPortalExample.waitForCustomerListingHeader();

        Assert.assertTrue(driver.getPageSource().contains("Username: bburke@redhat.com"));
        Assert.assertTrue(driver.getPageSource().contains("Bill Burke"));
        Assert.assertTrue(driver.getPageSource().contains("Stian Thorgersen"));
    }

    @Test
    public void customerPortalSessionTest() {

        customerPortalExample.navigateTo();
        customerPortalExample.customerSession();

        testRealmLoginPage.form().login("bburke@redhat.com", "password");

        assertCurrentUrlStartsWith(customerPortalExample);

        customerPortalExample.waitForCustomerSessionHeader();
        Assert.assertTrue(driver.getPageSource().contains("You visited this page"));
    }

    @Test
    public void productPortalListingTest() {

        productPortalExample.navigateTo();
        productPortalExample.productListing();

        testRealmLoginPage.form().login("bburke@redhat.com", "password");

        assertCurrentUrlStartsWith(productPortalExample);
        productPortalExample.waitForProductListingHeader();

        Assert.assertTrue(driver.getPageSource().contains("iphone"));
        Assert.assertTrue(driver.getPageSource().contains("ipad"));
        Assert.assertTrue(driver.getPageSource().contains("ipod"));

        productPortalExample.goToCustomers();
    }

    @Test
    public void goToProductPortalWithOneLoginTest() {

        productPortalExample.navigateTo();
        productPortalExample.productListing();

        testRealmLoginPage.form().login("bburke@redhat.com", "password");

        assertCurrentUrlStartsWith(productPortalExample);
        productPortalExample.waitForProductListingHeader();
        productPortalExample.goToCustomers();

        assertCurrentUrlStartsWith(customerPortalExample);
        customerPortalExample.customerListing();
        customerPortalExample.goToProducts();
        assertCurrentUrlStartsWith(productPortalExample);
    }

    @Test
    public void logoutFromAllAppsTest() {

        productPortalExample.navigateTo();
        productPortalExample.productListing();

        testRealmLoginPage.form().login("bburke@redhat.com", "password");

        assertCurrentUrlStartsWith(productPortalExample);
        productPortalExample.waitForProductListingHeader();

        productPortalExample.logOut();
        assertCurrentUrlStartsWith(productPortalExample);
        productPortalExample.productListing();

        customerPortalExample.navigateTo();
        customerPortalExample.customerListing();
        testRealmLoginPage.form().login("bburke@redhat.com", "password");

        customerPortalExample.logOut();
    }
}

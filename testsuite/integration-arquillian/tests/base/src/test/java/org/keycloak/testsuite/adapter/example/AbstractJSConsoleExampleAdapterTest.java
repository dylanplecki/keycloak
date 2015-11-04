package org.keycloak.testsuite.adapter.example;

import org.keycloak.testsuite.adapter.AbstractExampleAdapterTest;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.graphene.page.Page;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.keycloak.representations.idm.RealmRepresentation;
import static org.keycloak.testsuite.util.IOUtil.loadRealm;
import static org.keycloak.testsuite.util.URLAssert.assertCurrentUrlStartsWith;
import org.keycloak.testsuite.adapter.page.JSConsoleExample;
import static org.keycloak.testsuite.auth.page.AuthRealm.EXAMPLE;
import static org.keycloak.testsuite.util.URLAssert.assertCurrentUrlDoesntStartWith;
import static org.keycloak.testsuite.util.WaitUtils.pause;

public abstract class AbstractJSConsoleExampleAdapterTest extends AbstractExampleAdapterTest {

    @Page
    private JSConsoleExample jsConsoleExample;

    public static int TOKEN_LIFESPAN_LEEWAY = 3; // seconds

    @Deployment(name = JSConsoleExample.DEPLOYMENT_NAME)
    private static WebArchive jsConsoleExample() throws IOException {
        return exampleDeployment(JSConsoleExample.CLIENT_ID);
    }

    @Override
    public void addAdapterTestRealms(List<RealmRepresentation> testRealms) {
        RealmRepresentation jsConsoleRealm = loadRealm(new File(EXAMPLES_HOME_DIR + "/js-console/example-realm.json"));

        fixClientUrisUsingDeploymentUrl(jsConsoleRealm,
                JSConsoleExample.CLIENT_ID, jsConsoleExample.buildUri().toASCIIString());

        jsConsoleRealm.setAccessTokenLifespan(30 + TOKEN_LIFESPAN_LEEWAY); // seconds

        testRealms.add(jsConsoleRealm);
    }

    @Override
    public void setDefaultPageUriParameters() {
        super.setDefaultPageUriParameters();
        testRealmPage.setAuthRealm(EXAMPLE);
    }

    @Test
    public void testJSConsoleAuth() {
        jsConsoleExample.navigateTo();
        assertCurrentUrlStartsWith(jsConsoleExample);

        pause(1000);

        jsConsoleExample.logIn();
        testRealmLoginPage.form().login("user", "invalid-password");
        assertCurrentUrlDoesntStartWith(jsConsoleExample);

        testRealmLoginPage.form().login("invalid-user", "password");
        assertCurrentUrlDoesntStartWith(jsConsoleExample);

        testRealmLoginPage.form().login("user", "password");
        assertCurrentUrlStartsWith(jsConsoleExample);
        assertTrue(driver.getPageSource().contains("Init Success (Authenticated)"));
        assertTrue(driver.getPageSource().contains("Auth Success"));

        pause(1000);

        jsConsoleExample.logOut();
        assertCurrentUrlStartsWith(jsConsoleExample);
        assertTrue(driver.getPageSource().contains("Init Success (Not Authenticated)"));
    }

    @Test
    public void testRefreshToken() {
        jsConsoleExample.navigateTo();
        assertCurrentUrlStartsWith(jsConsoleExample);

        jsConsoleExample.refreshToken();
        assertTrue(driver.getPageSource().contains("Failed to refresh token"));

        jsConsoleExample.logIn();
        testRealmLoginPage.form().login("user", "password");
        assertCurrentUrlStartsWith(jsConsoleExample);
        assertTrue(driver.getPageSource().contains("Auth Success"));

        jsConsoleExample.refreshToken();
        assertTrue(driver.getPageSource().contains("Auth Refresh Success"));
    }

    @Test
    public void testRefreshTokenIfUnder30s() {
        jsConsoleExample.navigateTo();
        assertCurrentUrlStartsWith(jsConsoleExample);

        jsConsoleExample.refreshToken();
        assertTrue(driver.getPageSource().contains("Failed to refresh token"));

        jsConsoleExample.logIn();
        testRealmLoginPage.form().login("user", "password");
        assertCurrentUrlStartsWith(jsConsoleExample);
        assertTrue(driver.getPageSource().contains("Auth Success"));

        jsConsoleExample.refreshTokenIfUnder30s();
        assertTrue(driver.getPageSource().contains("Token not refreshed, valid for"));

        pause((TOKEN_LIFESPAN_LEEWAY + 2) * 1000);

        jsConsoleExample.refreshTokenIfUnder30s();
        assertTrue(driver.getPageSource().contains("Auth Refresh Success"));
    }
    
    @Test 
    public void testGetProfile() {
        jsConsoleExample.navigateTo();
        assertCurrentUrlStartsWith(jsConsoleExample);
        
        jsConsoleExample.getProfile();
        assertTrue(driver.getPageSource().contains("Failed to load profile"));
        
        jsConsoleExample.logIn();
        testRealmLoginPage.form().login("user", "password");
        assertCurrentUrlStartsWith(jsConsoleExample);
        assertTrue(driver.getPageSource().contains("Auth Success"));

        jsConsoleExample.getProfile();
        assertTrue(driver.getPageSource().contains("Failed to load profile"));
        assertTrue(driver.getPageSource().contains("\"username\": \"user\""));
    }

}

package org.keycloak.testsuite.arquillian.undertow;

import io.undertow.Undertow;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DefaultServletConfig;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ServletInfo;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.client.protocol.metadata.Servlet;
import org.jboss.logging.Logger;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;
import org.jboss.shrinkwrap.undertow.api.UndertowWebArchive;
import org.keycloak.services.filters.KeycloakSessionServletFilter;
import org.keycloak.services.resources.KeycloakApplication;

import javax.servlet.DispatcherType;
import java.util.Collection;
import java.util.Map;

public class CustomUndertowContainer implements DeployableContainer<CustomUndertowContainerConfiguration> {

    protected final Logger log = Logger.getLogger(this.getClass());
    
    private UndertowJaxrsServer undertow;
    private CustomUndertowContainerConfiguration configuration;

    private DeploymentInfo createAuthServerDeploymentInfo() {
        ResteasyDeployment deployment = new ResteasyDeployment();
        deployment.setApplicationClass(KeycloakApplication.class.getName());

        DeploymentInfo di = undertow.undertowDeployment(deployment);
        di.setClassLoader(getClass().getClassLoader());
        di.setContextPath("/auth");
        di.setDeploymentName("Keycloak");

        di.setDefaultServletConfig(new DefaultServletConfig(true));
        di.addWelcomePage("theme/keycloak/welcome/resources/index.html");

        FilterInfo filter = Servlets.filter("SessionFilter", KeycloakSessionServletFilter.class);
        di.addFilter(filter);
        di.addFilterUrlMapping("SessionFilter", "/*", DispatcherType.REQUEST);

        return di;
    }

    public DeploymentInfo getDeplotymentInfoFromArchive(Archive<?> archive) {
        if (archive instanceof UndertowWebArchive) {
            return ((UndertowWebArchive) archive).getDeploymentInfo();
        } else {
            throw new IllegalArgumentException("UndertowContainer only supports UndertowWebArchive or WebArchive.");
        }
    }

    private HTTPContext createHttpContextForDeploymentInfo(DeploymentInfo deploymentInfo) {
        HTTPContext httpContext = new HTTPContext(configuration.getBindAddress(), configuration.getBindHttpPort());
        final Map<String, ServletInfo> servlets = deploymentInfo.getServlets();
        final Collection<ServletInfo> servletsInfo = servlets.values();
        for (ServletInfo servletInfo : servletsInfo) {
            httpContext.add(new Servlet(servletInfo.getName(), deploymentInfo.getContextPath()));
        }
        return httpContext;
    }

    @Override
    public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
        DeploymentInfo di = getDeplotymentInfoFromArchive(archive);
        undertow.deploy(di);
        return new ProtocolMetaData().addContext(
                createHttpContextForDeploymentInfo(di));
    }

    @Override
    public void deploy(Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Class<CustomUndertowContainerConfiguration> getConfigurationClass() {
        return CustomUndertowContainerConfiguration.class;
    }

    @Override
    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription("Servlet 3.1");
    }

    @Override
    public void setup(
            CustomUndertowContainerConfiguration undertowContainerConfiguration) {
        this.configuration = undertowContainerConfiguration;
    }

    @Override
    public void start() throws LifecycleException {
        log.info("Starting auth server on embedded Undertow.");
        long start = System.currentTimeMillis();

        if (undertow == null) {
            undertow = new UndertowJaxrsServer();
        }
        undertow.start(Undertow.builder()
                .addHttpListener(configuration.getBindHttpPort(), configuration.getBindAddress())
                .setWorkerThreads(configuration.getWorkerThreads())
                .setIoThreads(configuration.getWorkerThreads() / 8)
        );

        undertow.deploy(createAuthServerDeploymentInfo());

        log.info("Auth server started in " + (System.currentTimeMillis() - start) + " ms\n");
    }

    @Override
    public void stop() throws LifecycleException {
        log.info("Stopping auth server.");
        undertow.stop();
    }

    @Override
    public void undeploy(Archive<?> archive) throws DeploymentException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void undeploy(Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException("Not implemented");
    }

}

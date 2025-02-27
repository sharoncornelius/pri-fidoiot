// Copyright 2020 Intel Corporation
// SPDX-License-Identifier: Apache 2.0

package org.fidoalliance.fdo.sample;

import java.nio.file.Path;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Service;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.fidoalliance.fdo.api.OwnerCustomerServlet;
import org.fidoalliance.fdo.api.OwnerReplacementVoucherServlet;
import org.fidoalliance.fdo.api.OwnerSetupInfoServlet;
import org.fidoalliance.fdo.api.OwnerSviSettingsServlet;
import org.fidoalliance.fdo.api.OwnerSystemResourceServlet;
import org.fidoalliance.fdo.api.OwnerVoucherServlet;
import org.fidoalliance.fdo.protocol.Const;
import org.h2.server.web.DbStarter;
import org.h2.server.web.WebServlet;

/**
 * Runs the Owner Application service.
 */
public class OwnerServerApp {

  private static final int TO2_PORT = null != OwnerConfigLoader
      .loadConfig(OwnerAppSettings.TO2_PORT)
      ? Integer.parseInt(OwnerConfigLoader.loadConfig(OwnerAppSettings.TO2_PORT))
      : 8042;

  private static final int OWNER_HTTPS_PORT =
      null != OwnerConfigLoader.loadConfig(OwnerAppSettings.OWNER_HTTPS_PORT)
          ? Integer.parseInt(OwnerConfigLoader.loadConfig(
          OwnerAppSettings.OWNER_HTTPS_PORT)) : 443;

  private static final String OWNER_SCHEME =
      null != OwnerConfigLoader.loadConfig(OwnerAppSettings.OWNER_SCHEME)
          ? OwnerConfigLoader.loadConfig(OwnerAppSettings.OWNER_SCHEME) : "http";

  private static String getMessagePath(int msgId) {
    return OwnerAppSettings.WEB_PATH + "/" + Integer.toString(msgId);
  }

  /**
   * Application main.
   *
   * @param args The application arguments.
   */
  public static void main(String[] args) {
    Tomcat tomcat = new Tomcat();

    // set the path of tomcat
    System.setProperty(OwnerAppSettings.SERVER_PATH,
        Path.of(OwnerConfigLoader.loadConfig(OwnerAppSettings.SERVER_PATH)).toAbsolutePath()
            .toString());

    tomcat.setAddDefaultWebXmlToWebapp(false);
    Context ctx = tomcat.addWebapp("", System.getProperty(OwnerAppSettings.SERVER_PATH));

    ctx.addParameter(OwnerAppSettings.DB_URL,
        OwnerConfigLoader.loadConfig(OwnerAppSettings.DB_URL));
    ctx.addParameter(OwnerAppSettings.DB_USER,
        OwnerConfigLoader.loadConfig(OwnerAppSettings.DB_USER));
    ctx.addParameter(OwnerAppSettings.DB_PWD,
        OwnerConfigLoader.loadConfig(OwnerAppSettings.DB_PWD));

    // hard-coded H2 config
    // To enable remote connections to the DB set
    // db.tcpServer=-tcp -tcpAllowOthers -ifNotExists -tcpPort
    // This creates a security hole in the system.
    // Not recommended to use especially on production system
    ctx.addParameter("db.tcpServer", "-tcp -ifNotExists -tcpPort "
        + OwnerConfigLoader.loadConfig(OwnerAppSettings.DB_PORT));

    // To enable remote connections to the DB set webAllowOthers=true
    // This creates a security hole in the system.
    // Not recommended to use especially on production system
    ctx.addParameter("webAllowOthers", "false");
    ctx.addParameter("trace", "");

    try {
      ctx.addParameter(OwnerAppSettings.ONDIE_CACHEDIR,
          OwnerConfigLoader.loadConfig(OwnerAppSettings.ONDIE_CACHEDIR));
      ctx.addParameter(OwnerAppSettings.ONDIE_AUTOUPDATE,
          OwnerConfigLoader.loadConfig(OwnerAppSettings.ONDIE_AUTOUPDATE));
      ctx.addParameter(OwnerAppSettings.ONDIE_ZIP_ARTIFACT,
          OwnerConfigLoader.loadConfig(OwnerAppSettings.ONDIE_ZIP_ARTIFACT));
      ctx.addParameter(OwnerAppSettings.ONDIE_CHECK_REVOCATIONS,
          OwnerConfigLoader.loadConfig(OwnerAppSettings.ONDIE_CHECK_REVOCATIONS));
    } catch (Exception ex) {
      // ondie is optional so if config cannot be loaded just default to no config
    }

    if (null != OwnerConfigLoader.loadConfig(OwnerAppSettings.EPID_URL)) {
      ctx.addParameter(OwnerAppSettings.EPID_URL,
          OwnerConfigLoader.loadConfig(OwnerAppSettings.EPID_URL));
    }
    if (null != OwnerConfigLoader.loadConfig(OwnerAppSettings.EPID_TEST_MODE)) {
      ctx.addParameter(OwnerAppSettings.EPID_TEST_MODE,
          OwnerConfigLoader.loadConfig(OwnerAppSettings.EPID_TEST_MODE));
    }
    ctx.addParameter(OwnerAppSettings.OWNER_KEYSTORE,
        OwnerConfigLoader.loadConfig(OwnerAppSettings.OWNER_KEYSTORE));
    ctx.addParameter(OwnerAppSettings.OWNER_KEYSTORE_PWD,
        OwnerConfigLoader.loadConfig(OwnerAppSettings.OWNER_KEYSTORE_PWD));
    ctx.addParameter(OwnerAppSettings.TO0_SCHEDULING_ENABLED,
        OwnerConfigLoader.loadConfig(OwnerAppSettings.TO0_SCHEDULING_ENABLED));
    ctx.addParameter(OwnerAppSettings.TO0_SCHEDULING_INTREVAL,
        OwnerConfigLoader.loadConfig(OwnerAppSettings.TO0_SCHEDULING_INTREVAL));
    ctx.addParameter(OwnerAppSettings.TO0_RV_BLOB,
        OwnerConfigLoader.loadConfig(OwnerAppSettings.TO0_RV_BLOB));
    ctx.addParameter(OwnerAppSettings.SAMPLE_SVI_PATH,
        OwnerConfigLoader.loadConfig(OwnerAppSettings.SAMPLE_SVI_PATH));
    ctx.addParameter(OwnerAppSettings.SAMPLE_VALUES_PATH,
        OwnerConfigLoader.loadConfig(OwnerAppSettings.SAMPLE_VALUES_PATH));
    ctx.addApplicationListener(DbStarter.class.getName());
    ctx.addApplicationListener(OwnerContextListener.class.getName());
    ctx.setParentClassLoader(ctx.getClass().getClassLoader());

    Wrapper wrapper = tomcat.addServlet(ctx, "opsServlet", new ProtocolServlet());

    wrapper.addMapping(getMessagePath(Const.TO2_HELLO_DEVICE));
    wrapper.addMapping(getMessagePath(Const.TO2_GET_OVNEXT_ENTRY));
    wrapper.addMapping(getMessagePath(Const.TO2_PROVE_DEVICE));
    wrapper.addMapping(getMessagePath(Const.TO2_DEVICE_SERVICE_INFO_READY));
    wrapper.addMapping(getMessagePath(Const.TO2_DEVICE_SERVICE_INFO));
    wrapper.addMapping(getMessagePath(Const.TO2_DONE));

    wrapper.setAsyncSupported(true);

    wrapper = tomcat.addServlet(ctx, "voucherServlet",
        new OwnerVoucherServlet());
    wrapper.addMapping("/api/v1/owner/vouchers/*");
    wrapper = tomcat.addServlet(ctx, "replacementVoucherServlet",
        new OwnerReplacementVoucherServlet());
    wrapper.addMapping("/api/v1/owner/newvoucher/*");
    wrapper = tomcat.addServlet(ctx, "setupinfoServlet",
        new OwnerSetupInfoServlet());
    wrapper.addMapping("/api/v1/owner/setupinfo/*");
    wrapper.setAsyncSupported(true);
    wrapper = tomcat.addServlet(ctx, "ownerCustomerServlet",
        new OwnerCustomerServlet());
    wrapper.addMapping("/api/v1/owner/customer/*");

    wrapper = tomcat.addServlet(ctx, "SystemResource", new OwnerSystemResourceServlet());
    wrapper.addMapping("/api/v1/device/svi");

    wrapper = tomcat.addServlet(ctx, "sviSettingsServlet",
        new OwnerSviSettingsServlet());
    wrapper.addMapping("/api/v1/owner/svi/settings/*");

    wrapper = tomcat.addServlet(ctx, "H2Console", new WebServlet());
    wrapper.addMapping("/console/*");
    wrapper.setLoadOnStartup(3);

    //setup digest auth
    LoginConfig config = new LoginConfig();
    config.setAuthMethod(OwnerAppSettings.AUTH_METHOD);
    ctx.setLoginConfig(config);
    ctx.addSecurityRole(OwnerAppSettings.AUTH_ROLE);
    SecurityConstraint constraint = new SecurityConstraint();
    constraint.addAuthRole(OwnerAppSettings.AUTH_ROLE);
    SecurityCollection collection = new SecurityCollection();
    collection.addPattern("/api/v1/owner/*");
    constraint.addCollection(collection);
    ctx.addConstraint(constraint);
    tomcat.addRole(OwnerConfigLoader.loadConfig(OwnerAppSettings.API_USER),
        OwnerAppSettings.AUTH_ROLE);
    tomcat.addUser(OwnerConfigLoader.loadConfig(OwnerAppSettings.API_USER),
        OwnerConfigLoader.loadConfig(OwnerAppSettings.API_PWD));


    Service service = tomcat.getService();
    Connector httpsConnector = new Connector();

    if (OWNER_SCHEME.toLowerCase().equals("https")) {

      httpsConnector.setPort(OWNER_HTTPS_PORT);
      httpsConnector.setSecure(true);
      httpsConnector.setScheme(OWNER_SCHEME);

      Path keyStoreFile =
          Path.of(OwnerConfigLoader.loadConfig(OwnerAppSettings.SSL_KEYSTORE_PATH));
      String keystorePass =
          OwnerConfigLoader.loadConfig(OwnerAppSettings.SSL_KEYSTORE_PASSWORD);

      httpsConnector.setProperty("keystorePass", keystorePass);
      httpsConnector.setProperty("keystoreFile", keyStoreFile.toFile().getAbsolutePath());
      httpsConnector.setProperty("clientAuth", "false");
      httpsConnector.setProperty("sslProtocol", "TLS");
      httpsConnector.setProperty("SSLEnabled", "true");
      service.addConnector(httpsConnector);

    }

    Connector httpConnector = new Connector();
    httpConnector.setPort(TO2_PORT);
    httpConnector.setScheme("http");
    httpConnector.setRedirectPort(OWNER_HTTPS_PORT);
    httpConnector.setProperty("protocol", "HTTP/1.1");
    httpConnector.setProperty("connectionTimeout", "20000");
    service.addConnector(httpConnector);
    tomcat.setConnector(httpConnector);

    tomcat.getConnector();
    try {
      tomcat.start();
    } catch (LifecycleException e) {
      throw new RuntimeException(e);
    }
  }
}

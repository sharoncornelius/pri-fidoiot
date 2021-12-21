// Copyright 2020 Intel Corporation
// SPDX-License-Identifier: Apache 2.0

package org.fidoalliance.fdo.sample;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Arrays;
import java.util.List;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.fidoalliance.fdo.certutils.PemLoader;
import org.fidoalliance.fdo.loggingutils.LoggerService;
import org.fidoalliance.fdo.protocol.CloseableKey;
import org.fidoalliance.fdo.protocol.Composite;
import org.fidoalliance.fdo.protocol.Const;
import org.fidoalliance.fdo.protocol.CryptoService;
import org.fidoalliance.fdo.protocol.DiServerService;
import org.fidoalliance.fdo.protocol.DiServerStorage;
import org.fidoalliance.fdo.protocol.MessageDispatcher;
import org.fidoalliance.fdo.protocol.MessagingService;
import org.fidoalliance.fdo.protocol.ondie.OnDieCache;
import org.fidoalliance.fdo.protocol.ondie.OnDieService;
import org.fidoalliance.fdo.storage.CertificateResolver;
import org.fidoalliance.fdo.storage.DiDbManager;
import org.fidoalliance.fdo.storage.DiDbStorage;

/**
 * Device Initialization servlet Context Listener.
 */
public class DiContextListener implements ServletContextListener {

  private static final LoggerService logger = new LoggerService(DiContextListener.class);

  private static final String mfgKeyPemEC256 = "-----BEGIN CERTIFICATE-----\n"
      + "MIIBIjCByaADAgECAgkApNMDrpgPU/EwCgYIKoZIzj0EAwIwDTELMAkGA1UEAwwC\n"
      + "Q0EwIBcNMTkwNDI0MTQ0NjQ3WhgPMjA1NDA0MTUxNDQ2NDdaMA0xCzAJBgNVBAMM\n"
      + "AkNBMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAELAJwkDKz/BaWq1Wx7PjkR5W5\n"
      + "LLIbamgSZeVNUlyFM/t0sMAxAWbvEbDzKu924TX4as3WVjMmfekysx30PlDGJaMQ\n"
      + "MA4wDAYDVR0TBAUwAwEB/zAKBggqhkjOPQQDAgNIADBFAiEApUGbgjYT0k63AeRA\n"
      + "tPM2i+VnW6ckYaJyvFLuuWw+QUACIE5w0ntjHLbvwmqgwCfh5T6u8exQdCA2g9Hs\n"
      + "u53hKcaS\n"
      + "-----END CERTIFICATE-----\n"
      + "-----BEGIN EC PARAMETERS-----\n"
      + "BggqhkjOPQMBBw==\n"
      + "-----END EC PARAMETERS-----\n"
      + "-----BEGIN EC PRIVATE KEY-----\n"
      + "MHcCAQEEIJTKW2/54N85RLJu0C5fEkAwQiKqxRqHzx5PUfd/M66UoAoGCCqGSM49\n"
      + "AwEHoUQDQgAELAJwkDKz/BaWq1Wx7PjkR5W5LLIbamgSZeVNUlyFM/t0sMAxAWbv\n"
      + "EbDzKu924TX4as3WVjMmfekysx30PlDGJQ==\n"
      + "-----END EC PRIVATE KEY-----\n";

  private static final String mfgKeyPemEC384 = "-----BEGIN CERTIFICATE-----\n"
      + "MIICHDCCAaKgAwIBAgIUYeUxVbuTfFgas1Sgh9jSOQt6zX4wCgYIKoZIzj0EAwIw\n"
      + "RTELMAkGA1UEBhMCVVMxEzARBgNVBAgMClNvbWUtU3RhdGUxITAfBgNVBAoMGElu\n"
      + "dGVybmV0IFdpZGdpdHMgUHR5IEx0ZDAeFw0yMTEwMTQxNzUzMDRaFw0yNDA3MTAx\n"
      + "NzUzMDRaMEUxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYD\n"
      + "VQQKDBhJbnRlcm5ldCBXaWRnaXRzIFB0eSBMdGQwdjAQBgcqhkjOPQIBBgUrgQQA\n"
      + "IgNiAASWQK3xZZeDeJ2AfhYUEpv7zsGeOSE0vjwPqUPFfhbYktpjWPqtO3Swxtvk\n"
      + "eYKBlmnOzWvSFfWFtaZrSg/y9WBM38bCa7hzVb1m7QEQUb7vaqR09JlEDPinK5Sk\n"
      + "5kV7Q3ijUzBRMB0GA1UdDgQWBBTslYJJYW7ItUdwRIQKAGHOzwYgxzAfBgNVHSME\n"
      + "GDAWgBTslYJJYW7ItUdwRIQKAGHOzwYgxzAPBgNVHRMBAf8EBTADAQH/MAoGCCqG\n"
      + "SM49BAMCA2gAMGUCMQDROIgcJZKgGeH1zmKyChqVHV9ed2vm7lmo8yDtRSHdeUVr\n"
      + "Qiwqh5qPE900pARaGqoCMDS4/E8v7HH9GVgFgh2BRQnt7GXphjuhbfIShhsyzgnN\n"
      + "QKSyYAG18iONZE9AdkLyTQ==\n"
      + "-----END CERTIFICATE-----\n"
      + "-----BEGIN EC PRIVATE KEY-----\n"
      + "MIGkAgEBBDCfCT11Ea/EyDGpLfFG+SmgmFljxlihwE/jZtGbAAIV1HfzGLiRegWa\n"
      + "mEK03XhgRsigBwYFK4EEACKhZANiAASWQK3xZZeDeJ2AfhYUEpv7zsGeOSE0vjwP\n"
      + "qUPFfhbYktpjWPqtO3SwxtvkeYKBlmnOzWvSFfWFtaZrSg/y9WBM38bCa7hzVb1m\n"
      + "7QEQUb7vaqR09JlEDPinK5Sk5kV7Q3g=\n"
      + "-----END EC PRIVATE KEY-----\n"
      + "-----BEGIN PUBLIC KEY-----\n"
      + "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAElkCt8WWXg3idgH4WFBKb+87BnjkhNL48\n"
      + "D6lDxX4W2JLaY1j6rTt0sMbb5HmCgZZpzs1r0hX1hbWma0oP8vVgTN/Gwmu4c1W9\n"
      + "Zu0BEFG+72qkdPSZRAz4pyuUpOZFe0N4\n"
      + "-----END PUBLIC KEY-----\n";

  private static final String mfgKeyPemRSA = "-----BEGIN CERTIFICATE-----\n"
      + "MIIDazCCAlOgAwIBAgIUcg9kASwqV7YrZjAxNwQG80dK9pIwDQYJKoZIhvcNAQEL\n"
      + "BQAwRTELMAkGA1UEBhMCVVMxEzARBgNVBAgMClNvbWUtU3RhdGUxITAfBgNVBAoM\n"
      + "GEludGVybmV0IFdpZGdpdHMgUHR5IEx0ZDAeFw0yMDExMTEyMzQ5NDZaFw0yMTEx\n"
      + "MDYyMzQ5NDZaMEUxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApTb21lLVN0YXRlMSEw\n"
      + "HwYDVQQKDBhJbnRlcm5ldCBXaWRnaXRzIFB0eSBMdGQwggEiMA0GCSqGSIb3DQEB\n"
      + "AQUAA4IBDwAwggEKAoIBAQCYMfVDQCyIPYHtuGGaXNeu+vQ9szHFDZ3nCCI+790j\n"
      + "U4K5gZxsspgVL+hqiGvHmAajEsEu50ZiztbFjVk2L9Qm+n6kxMkFEVyPuZdg0cmV\n"
      + "QQvh8AkJg7S6RfJ+mVaViKuSffd/EXsBJtqjl91aKqsFa/BKIH1duo5SBDEIHnsj\n"
      + "T29St3GBdGj4fBQZK5abr9TBuPKxrAElG3mJ7c95Wp7Y6GrEmdPnf5Z7kh/GA0Hs\n"
      + "j0pxXRYLX9kAaYFxUKcd7pwR0pyXxBlEKCMUtzZnA0nuRiaKTruimffVWeYsEusF\n"
      + "adXkk+pgZGoG/vzOdrAYUoYrpYyxLakpeF7Sxn2S0OpdAgMBAAGjUzBRMB0GA1Ud\n"
      + "DgQWBBT6CwcHvPMR4usp99+5kNCTql2uozAfBgNVHSMEGDAWgBT6CwcHvPMR4usp\n"
      + "99+5kNCTql2uozAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQBM\n"
      + "CeR6ri2wG11icLRUU5QdfaHXoxKlTRyX1Re5DBJm3WfiXPsRIgm9G1lAYNtf2Vqp\n"
      + "Kg9L9q1ApDjWqUK63QEx8G5xRXfPzK/Lzke8Zwdxl+YG6w7AdqpVHNOpOgiTythw\n"
      + "5dZFhugXvbY35+2lDU0wxGnpkrUy3IiaLrIImEc15abD9cEfJ3cKX/MrsE0Z4LE3\n"
      + "XTH71qpyVvEMDgp13eEb7WPd6IwLSMhVYaISfNlm5icWUNIIS3P29koVsbyr5V9p\n"
      + "2hhsqnh8AVAlzLEseg8ix4eVlNpb+6kUip4Em10fWcpjwOgJ7ewQMt4Bmg/Yd86j\n"
      + "xmaagY/MnDlkWsdkUS0j\n"
      + "-----END CERTIFICATE-----\n"
      + "-----BEGIN RSA PRIVATE KEY-----\n"
      + "MIIEowIBAAKCAQEAmDH1Q0AsiD2B7bhhmlzXrvr0PbMxxQ2d5wgiPu/dI1OCuYGc\n"
      + "bLKYFS/oaohrx5gGoxLBLudGYs7WxY1ZNi/UJvp+pMTJBRFcj7mXYNHJlUEL4fAJ\n"
      + "CYO0ukXyfplWlYirkn33fxF7ASbao5fdWiqrBWvwSiB9XbqOUgQxCB57I09vUrdx\n"
      + "gXRo+HwUGSuWm6/UwbjysawBJRt5ie3PeVqe2OhqxJnT53+We5IfxgNB7I9KcV0W\n"
      + "C1/ZAGmBcVCnHe6cEdKcl8QZRCgjFLc2ZwNJ7kYmik67opn31VnmLBLrBWnV5JPq\n"
      + "YGRqBv78znawGFKGK6WMsS2pKXhe0sZ9ktDqXQIDAQABAoIBABIV/btcKO6XwJTr\n"
      + "UE3zsn3MvLGIVeXA1H7b7JXmEzVbezFoQZp5LrF12/ys8YHqgv9l/yb/vNGJGuSn\n"
      + "A275CBEJu3sBF5JOmd1KhL6wr2/ZJvxWdfx3dkacgVCiEDP85camyX6EKUXdxCWk\n"
      + "ql1IrMIcLZXhfoRg4wDEFr+MP73WR86PbrGejWBxzLQW3DRmriqTWkm/AMuDUFqt\n"
      + "lCgkq8Pz5Lmal7slybiGlfEfe4zJ02+KPbqbS0ow4JvjzJbwxXoOFYHZHkO+miPp\n"
      + "WSVIqTMIFo1FJa0b4hEeNZvpL/n8q9+HHDFO8TJiBi/nkKYWlLljRw9neU6ir2sR\n"
      + "TFCcm1ECgYEAxXX7ymYOeN+cCSh8okRNym3tamd8bfcKoJuUUWWaJlStjUmSbKk/\n"
      + "mhZFFXDM4ILmDl8rR1Xe1yhtqE9E9lciDcrgiwOlZCMveoTFXtGZSaR6lcvxJR+r\n"
      + "YZGt1+8GubyelhG9fdTqh+Fbywpqf4y7K9kJk+GyhhJpNKDt6KcL3j8CgYEAxVCY\n"
      + "gVYhd7DEQwWakJGqzFtfB4fPPq8uCpEQS0SBXH1naoOZC0dFviCn8kfiu/8dEKLn\n"
      + "WHoKFUEqbt5RXIlZ40EsuZ4obwTvyXtzMB4ZMCieagrmPBuItrVD9j0WUg0UKXb2\n"
      + "pZhj19kB+dJSYlLQuBJx4dgTX18+kCs/jeO4CGMCgYAJudH5GiXEVl89JD1fULX0\n"
      + "Lo3vG8HJOM3RM1iO2c5J7QnPV7xalcuIL2ifsbqlpEzokE91aAld16PvElh3Obt3\n"
      + "qnJ85mUTFZiVFE0UaoZ+VhejoTPzfCfY2nARnrhaFaxJ//2xYPdcUgFPcufj+G6P\n"
      + "rlUGb6t3Zxi/et29A91VyQKBgFLhL63kB9rGnSmpyOrAxQVhu+Dl4t9ppeU6WaXD\n"
      + "+LJo2m+bJ0XdgiYCgYj7OPnOht7eDl7TpDiZTsGTEInWB7O1RJwTGtjHMhFnkVK8\n"
      + "0cewyBmyylKlHh/gs9NShXWhmL5yAdg80nNv66yL857pOlKXLM64fCqrTxQvllp7\n"
      + "NGoRAoGBAJ0535aIXC6QPMIHhL1bx8nHmlcIJ3R5vT08EV/pKfH7LTlkBNZ21L4i\n"
      + "3fKr82kRIpe78HH0A2IbeI3b0zUQGvHymckUelJAtDGZvJlQ3o0zjyI1i4Q/SlLj\n"
      + "i3ooWFVIO6mctlrkqEfwpADk7umDNQ4vcqnJVp+UEY1+Y9lyhh4L\n"
      + "-----END RSA PRIVATE KEY-----\n"
      + "-----BEGIN PUBLIC KEY-----\n"
      + "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmDH1Q0AsiD2B7bhhmlzX\n"
      + "rvr0PbMxxQ2d5wgiPu/dI1OCuYGcbLKYFS/oaohrx5gGoxLBLudGYs7WxY1ZNi/U\n"
      + "Jvp+pMTJBRFcj7mXYNHJlUEL4fAJCYO0ukXyfplWlYirkn33fxF7ASbao5fdWiqr\n"
      + "BWvwSiB9XbqOUgQxCB57I09vUrdxgXRo+HwUGSuWm6/UwbjysawBJRt5ie3PeVqe\n"
      + "2OhqxJnT53+We5IfxgNB7I9KcV0WC1/ZAGmBcVCnHe6cEdKcl8QZRCgjFLc2ZwNJ\n"
      + "7kYmik67opn31VnmLBLrBWnV5JPqYGRqBv78znawGFKGK6WMsS2pKXhe0sZ9ktDq\n"
      + "XQIDAQAB\n"
      + "-----END PUBLIC KEY-----\n";

  private final String[] mfgPemKeys = {mfgKeyPemRSA, mfgKeyPemEC256, mfgKeyPemEC384};

  private final String ownerKeyPemEC256 = "-----BEGIN PUBLIC KEY-----\n"
      + "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEWVUE2G0GLy8scmAOyQyhcBiF/fSU\n"
      + "d3i/Og7XDShiJb2IsbCZSRqt1ek15IbeCI5z7BHea2GZGgaK63cyD15gNA==\n"
      + "-----END PUBLIC KEY-----\n";

  private final String ownerKeyPemEC384 = "-----BEGIN PUBLIC KEY-----\n"
      + "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEMNMHB3t2Po763C8QteK7/STJRf6F1Sfk\n"
      + "yi2TYmGWdnlXgI+5s7fOkrJzebHGvg61vfpSZ3qcrKJqU6EkWQvy+fqHH609U00W\n"
      + "hNwLYKjiGqtVlBrBs0Q9vPBZVBPiN3Ji\n"
      + "-----END PUBLIC KEY-----\n";

  private final String ownerKeysPemRsa = "-----BEGIN PUBLIC KEY-----\n"
      + "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwTWjO2WTkQJSRuf1sIlx\n"
      + "365VxOxdIAnDZu/GYNMg8oKDapg0uvi/DguFkrxbs3AtRHGWdONYXbGd1ZsGcVY9\n"
      + "DsCDR5R5+NCx8EEYfYSbz88dvncJMEq7iJiQXNdaj9dCHuZqaj5LGChBcLLldynX\n"
      + "mx3ZDE780aKPGomjeXEqcWgpeb0L4O+vGxkvz42C1XtvlsjBNPGKAjMM6xRPkorL\n"
      + "SfC1P0XyER3kqVYc4/cM9FyO7/vHLwH9byPCV4WbUpkti/bEtPs9xLnEtYP0oV30\n"
      + "PcdFVOg8hcuaEy6GoseU1EhlpgWJeBsbHMTlOB20JJa0kfFzREaJENyH6nHW3bSU\n"
      + "AwIDAQAB\n"
      + "-----END PUBLIC KEY-----\n";

  private final String[] ownerPemKeys = {ownerKeysPemRsa, ownerKeyPemEC256, ownerKeyPemEC384};

  CertificateResolver keyResolver;

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    BasicDataSource ds = new BasicDataSource();

    ServletContext sc = sce.getServletContext();
    ds.setUrl(sc.getInitParameter("db.url"));
    ds.setDriverClassName("org.h2.Driver");
    ds.setUsername(sc.getInitParameter("db.user"));
    ds.setPassword(sc.getInitParameter("db.password"));

    logger.info(ds.getUrl());

    ds.setMinIdle(5);
    ds.setMaxIdle(10);
    ds.setMaxOpenPreparedStatements(100);

    CryptoService cs = new CryptoService();

    sc.setAttribute("datasource", ds);
    sc.setAttribute("cryptoservice", cs);

    // To maintain backwards compatibility with installation without
    // any OnDie settings or installations that do not wish to use
    // OnDie we will check if the one required setting is present.
    // If not then the ods object is set to null and operation should
    // proceed without error. If an OnDie operation is attempted then
    // an error will occur at that time and the user will need to
    // correct their configuration.
    OnDieService initialOds = null;
    if (sc.getInitParameter("ods.cacheDir") != null
            && !sc.getInitParameter("ods.cacheDir").isEmpty()) {
      try {
        OnDieCache odc = new OnDieCache(
                URI.create(sc.getInitParameter("ods.cacheDir")),
                sc.getInitParameter("ods.autoUpdate").toLowerCase().equals("true"),
                sc.getInitParameter("ods.zipArtifactUrl"),
                null);

        odc.initializeCache();

        initialOds = new OnDieService(odc,
                sc.getInitParameter("ods.checkRevocations").equals("true"));

      } catch (Exception ex) {
        throw new RuntimeException("OnDie initialization error: " + ex.getMessage());
      }
    }
    final OnDieService ods = initialOds;

    keyResolver = new CertificateResolver() {
      @Override
      public CloseableKey getPrivateKey(Certificate cert) {
        String pemValue;
        try {
          for (String pem : mfgPemKeys) {
            List<Certificate> keyList = PemLoader.loadCerts(pem);
            for (Certificate certificate : keyList) {
              if (Arrays.equals(certificate.getEncoded(), cert.getEncoded())) {
                return new CloseableKey(PemLoader.loadPrivateKey(pem));
              }
            }
          }
        } catch (CertificateEncodingException ex) {
          logger.error("Unable to retrieve Private Key. " + ex.getMessage());
          return null;
        }
        return null;
      }

      @Override
      public Certificate[] getCertChain(int publicKeyType) {
        String pemValue;
        if (publicKeyType == Const.PK_RSA2048RESTR || publicKeyType == Const.PK_RSA3072)  {
          pemValue = mfgKeyPemRSA;
        } else if (publicKeyType == Const.PK_SECP256R1) {
          pemValue = mfgKeyPemEC256;
        } else if (publicKeyType == Const.PK_SECP384R1) {
          pemValue = mfgKeyPemEC384;
        } else if (publicKeyType == Const.PK_ONDIE_ECDSA_384) {
          pemValue = mfgKeyPemEC384;
        } else {
          return new Certificate[0];
        }

        List<Certificate> list = PemLoader.loadCerts(pemValue);
        Certificate[] certs = new Certificate[list.size()];
        list.toArray(certs);
        return certs;
      }
    };

    MessageDispatcher dispatcher = new MessageDispatcher() {
      @Override
      protected MessagingService getMessagingService(Composite request) {
        return createDiService(cs, ds, ods);
      }

      @Override
      protected void replied(Composite reply) {
        String msgId = reply.getAsNumber(Const.SM_MSG_ID).toString();
        logger.info("msg/" + msgId + ": " + reply.toString());
      }

      @Override
      protected void dispatching(Composite request) {
        String msgId = request.getAsNumber(Const.SM_MSG_ID).toString();
        logger.info("msg/" + msgId + ": " + request.toString());
      }

      @Override
      protected void failed(Exception e) {
        StringWriter writer = new StringWriter();
        try (PrintWriter pw = new PrintWriter(writer)) {
          logger.warn("Failed to write data: " + e.getMessage());
        }
        logger.warn(writer.toString());
      }
    };
    sc.setAttribute(Const.DISPATCHER_ATTRIBUTE, dispatcher);
    sc.setAttribute("resolver", keyResolver);

    String ownerKeys = "";
    for (String key : ownerPemKeys) {
      ownerKeys += key;
    }

    //create tables
    DiDbStorage db = new DiDbStorage(cs, ds, keyResolver, ods);
    DiDbManager manager = new DiDbManager();
    manager.createTables(ds);
    manager.addCustomer(ds, 1, "owner", ownerKeys);
    manager.setAutoEnroll(ds, 1);

  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
  }

  private DiServerService createDiService(CryptoService cs, DataSource ds, OnDieService ods) {
    return new DiServerService() {
      private DiServerStorage storage;

      @Override
      public DiServerStorage getStorage() {
        if (storage == null) {
          storage = new DiDbStorage(cs, ds, keyResolver, ods);
        }
        return storage;
      }

      @Override
      public CryptoService getCryptoService() {
        return cs;
      }
    };
  }
}

package com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl;

import com.ofss.digx.cz.bea.extxface.hosttohost.adapter.HthApiCredentialWriteException;
import com.ofss.digx.cz.bea.extxface.hosttohost.adapter.IHthApiCredentialAdapter;
import com.ofss.digx.cz.bea.extxface.hosttohost.adapter.impl.dto.DspApiPasswordPersistRequest;
import com.ofss.fc.infra.config.ConfigurationFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.ws.rs.core.Response;

/** DSP password persistence; Code, identity and onboarding state remain owned by the HTH service. */
public class HthDspApiCredentialAdapter implements IHthApiCredentialAdapter {
  private static final Logger LOGGER = Logger.getLogger(HthDspApiCredentialAdapter.class.getName());

  @Override
  public String getStatus(String partyId, String userId, String clientId) {
    // No DSP status endpoint has been supplied. The service reads confirmed DSP operations.
    return STATUS_UNKNOWN;
  }

  @Override
  public String setup(String partyId, String closeId, String clientId, String password,
      String requestId) throws com.ofss.digx.infra.exceptions.Exception {
    return persist(partyId, closeId, clientId, password, requestId);
  }

  @Override
  public String reset(String partyId, String closeId, String clientId, String password,
      String requestId) throws com.ofss.digx.infra.exceptions.Exception {
    return persist(partyId, closeId, clientId, password, requestId);
  }

  protected DspApiPasswordPersistClient client() {
    return new DspApiPasswordPersistClient();
  }

  protected Preferences configuration() {
    return ConfigurationFactory.getInstance().getConfigurations("HthApiCredentialAdapterConfig");
  }

  private String persist(String partyId, String closeId, String clientId, String password,
      String requestId) throws com.ofss.digx.infra.exceptions.Exception {
    boolean uncertain = false;
    String stage = "CONFIG";
    Response response = null;
    try {
      DspApiPasswordResponsePolicy policy = new DspApiPasswordResponsePolicy(configuration());
      if (partyId == null || closeId == null || !closeId.endsWith("@" + partyId)
          || closeId.length() <= partyId.length() + 1 || requestId == null) {
        throw new IllegalArgumentException();
      }
      DspApiPasswordPersistRequest request = DspApiPasswordPersistRequest.fromPassword(
          clientId, closeId, password);
      DspApiPasswordPersistClient transport = client();
      stage = "POST";
      uncertain = true;
      response = transport.send(request);
      stage = "RESPONSE_VALIDATE";
      if (!policy.confirms(response)) {
        LOGGER.log(Level.WARNING, "HTH_API_PASSWORD DSP: stage=RESPONSE_VALIDATE, httpStatus={0}, result=UNCONFIRMED",
            response == null ? -1 : response.getStatus());
        throw new HthApiCredentialWriteException(true);
      }
      LOGGER.log(Level.INFO, "HTH_API_PASSWORD DSP: stage=PERSIST_CONFIRMED, httpStatus={0}",
          response.getStatus());
      // Local operation reference. No DSP reference field has been specified.
      return requestId;
    } catch (DspApiPasswordPersistClient.TransportException failure) {
      LOGGER.log(Level.WARNING, "HTH_API_PASSWORD DSP: stage={0}, exceptionTypes={1}",
          new Object[] {failure.getStage(), failure.getExceptionTypes()});
      throw new HthApiCredentialWriteException("POST".equals(failure.getStage()));
    } catch (HthApiCredentialWriteException failure) {
      throw failure;
    } catch (java.lang.Exception failure) {
      LOGGER.log(Level.WARNING, "HTH_API_PASSWORD DSP: stage={0}, exceptionType={1}",
          new Object[] {stage, failure.getClass().getName()});
      throw new HthApiCredentialWriteException(uncertain);
    } finally {
      if (response != null) {
        try { response.close(); }
        catch (RuntimeException failure) {
          LOGGER.log(Level.WARNING, "HTH_API_PASSWORD DSP: stage=RESPONSE_CLOSE, exceptionType={0}",
              failure.getClass().getName());
        }
      }
    }
  }
}

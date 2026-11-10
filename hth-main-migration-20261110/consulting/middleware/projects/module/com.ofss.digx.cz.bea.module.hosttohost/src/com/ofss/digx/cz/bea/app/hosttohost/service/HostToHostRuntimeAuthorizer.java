package com.ofss.digx.cz.bea.app.hosttohost.service;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.HthUserAccessAccountRepository;
import com.ofss.digx.infra.exceptions.Exception;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fail-closed authorization boundary for HTH account API requests.
 *
 * <p>Authorization requires a currently active user/account grant, an active account/API grant,
 * and an API that remains enabled in enterprise HTH management. Missing input or any repository
 * failure is denied rather than falling back to channel-level access.
 *
 * <p>This helper must be called by the HTH request ingress after party, CloseID, canonical account
 * number, and API code have been resolved. Defining the helper alone does not enforce access.
 */
public class HostToHostRuntimeAuthorizer {
  private static final Logger LOGGER =
      Logger.getLogger(HostToHostRuntimeAuthorizer.class.getName());

  /**
   * Evaluates an HTH account/API request without propagating repository failures.
   *
   * @return {@code true} only when the complete effective authorization chain is active
   */
  public boolean isAuthorized(String partyId, String closeId, String accountNumber,
      String apiCode) {
    if (blank(partyId) || blank(closeId) || blank(accountNumber) || blank(apiCode)) {
      return false;
    }
    try {
      return HthUserAccessAccountRepository.getInstance().isAuthorized(
          partyId.trim(), closeId.trim(), accountNumber.trim(), apiCode.trim());
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE,
          "Unable to evaluate HTH user access. Authorization denied.", e);
      return false;
    } catch (RuntimeException e) {
      LOGGER.log(Level.SEVERE,
          "Unexpected HTH user access failure. Authorization denied.", e);
      return false;
    }
  }

  /**
   * Rejects an unauthorized HTH account/API request using the public validation error contract.
   */
  public void assertAuthorized(String partyId, String closeId, String accountNumber,
      String apiCode) throws Exception {
    if (!isAuthorized(partyId, closeId, accountNumber, apiCode)) {
      throw new Exception("DIGX_CZ_HTH_UA_005");
    }
  }

  private boolean blank(String value) {
    return value == null || value.trim().isEmpty();
  }
}

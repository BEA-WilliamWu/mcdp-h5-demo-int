package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.infra.das.orm.Query;
import com.ofss.fc.infra.das.orm.Session;
import java.util.List;

/** HTH-only metadata in HTH_USER_PROFILE; never stores a password or its hash. */
public final class HthApiPasswordStateRepository {
  public String read(Session session, String party, String user, String client) throws Exception {
    return state(rows(session, party, user, false), client);
  }

  public String readForCodeGeneration(Session session, String party, String user, String client) throws Exception {
    List result = rows(session, party, user, false);
    // User-create can generate a PENDING Code before approval creates HTH_USER_PROFILE.
    // DSP function A still rejects an existing credential; this is not an upsert permission.
    return result != null && result.isEmpty() ? "NOT_SETUP" : state(result, client);
  }

  private List rows(Session session, String party, String user, boolean lock) throws Exception {
    Query query = session.createSQLQuery("SELECT API_PASSWORD_STATE, API_PASSWORD_CLIENT_ID "
        + "FROM HTH_BEA.HTH_USER_PROFILE WHERE PARTY_ID = ? AND CLOSE_ID IN (?, ?)"
        + (lock ? " FOR UPDATE" : ""));
    query.setParameter(1, party);
    query.setParameter(2, user);
    query.setParameter(3, user + "@" + party);
    return query.list();
  }

  private String state(List rows, String client) {
    if (rows == null || rows.size() != 1 || client == null || client.isEmpty()) { return "UNKNOWN"; }
    Object[] row = (Object[]) rows.get(0);
    String status = row[0] == null ? "UNKNOWN" : String.valueOf(row[0]);
    String boundClient = row[1] == null ? null : String.valueOf(row[1]);
    if ("NOT_SETUP".equals(status) && (boundClient == null || client.equals(boundClient))) {
      return "NOT_SETUP";
    }
    return "ACTIVE".equals(status) && client.equals(boundClient) ? "ACTIVE" : "UNKNOWN";
  }

  public void reserve(Session session, String party, String user, String client, String request,
      String operation) throws Exception {
    String expected = "SETUP".equals(operation) ? "NOT_SETUP" : "ACTIVE";
    if (!expected.equals(state(rows(session, party, user, true), client))) {
      throw new Exception("DIGX_CZ_HTH_API_PASSWORD_007");
    }
    Query query = session.createSQLQuery("UPDATE HTH_BEA.HTH_USER_PROFILE SET "
        + "API_PASSWORD_STATE = 'IN_PROGRESS', API_PASSWORD_CLIENT_ID = ?, API_PASSWORD_REQUEST_ID = ?, "
        + "API_PASSWORD_UPDATED_AT = SYSTIMESTAMP WHERE PARTY_ID = ? AND CLOSE_ID IN (?, ?) "
        + "AND API_PASSWORD_STATE = ? AND (API_PASSWORD_CLIENT_ID IS NULL OR API_PASSWORD_CLIENT_ID = ?)");
    query.setParameter(1, client);
    query.setParameter(2, request);
    query.setParameter(3, party);
    query.setParameter(4, user);
    query.setParameter(5, user + "@" + party);
    query.setParameter(6, expected);
    query.setParameter(7, client);
    if (query.executeUpdate() != 1) { throw new Exception("DIGX_CZ_HTH_API_PASSWORD_007"); }
  }

  public void finish(Session session, String party, String user, String client, String request,
      String status) throws Exception {
    Query query = session.createSQLQuery("UPDATE HTH_BEA.HTH_USER_PROFILE SET "
        + "API_PASSWORD_STATE = ?, API_PASSWORD_UPDATED_AT = SYSTIMESTAMP "
        + "WHERE PARTY_ID = ? AND CLOSE_ID IN (?, ?) AND API_PASSWORD_CLIENT_ID = ? "
        + "AND API_PASSWORD_REQUEST_ID = ? AND API_PASSWORD_STATE = 'IN_PROGRESS'");
    query.setParameter(1, status);
    query.setParameter(2, party);
    query.setParameter(3, user);
    query.setParameter(4, user + "@" + party);
    query.setParameter(5, client);
    query.setParameter(6, request);
    if (query.executeUpdate() != 1) { throw new Exception("DIGX_CZ_HTH_API_PASSWORD_007"); }
  }
}

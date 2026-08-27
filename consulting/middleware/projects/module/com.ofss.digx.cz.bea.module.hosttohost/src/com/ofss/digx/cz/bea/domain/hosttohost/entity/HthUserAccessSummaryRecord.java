package com.ofss.digx.cz.bea.domain.hosttohost.entity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only aggregate of active effective account grants for one company context.
 *
 * <p>The map is keyed by account type so the response can remain extensible even though this Story
 * supports Current and Savings and Time Deposit account totals.
 */
public class HthUserAccessSummaryRecord {
  private String accessPartyId;

  private String linkageType;

  private Map<String, Integer> accountCountByType = new LinkedHashMap<String, Integer>();

  public String getAccessPartyId() {
    return accessPartyId;
  }

  public void setAccessPartyId(String accessPartyId) {
    this.accessPartyId = accessPartyId;
  }

  public String getLinkageType() {
    return linkageType;
  }

  public void setLinkageType(String linkageType) {
    this.linkageType = linkageType;
  }

  public Map<String, Integer> getAccountCountByType() {
    return accountCountByType;
  }
}

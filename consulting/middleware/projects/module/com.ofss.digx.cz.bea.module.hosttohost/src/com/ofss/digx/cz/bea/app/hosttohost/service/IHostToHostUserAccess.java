package com.ofss.digx.cz.bea.app.hosttohost.service;

import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessResponseDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HostToHostUserAccessSearchDTO;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.service.response.TransactionStatus;

/** Application-service contract for HTH user account and API access maintenance. */
public interface IHostToHostUserAccess {
  /** Returns effective and pending access summaries for the specified HTH user. */
  HostToHostUserAccessResponseDTO search(
      SessionContext sessionContext,
      HostToHostUserAccessSearchDTO requestDTO) throws Exception;

  /** Returns eligible Current and Savings/Time Deposit accounts, APIs, and existing access. */
  HostToHostUserAccessResponseDTO accounts(
      SessionContext sessionContext,
      HostToHostUserAccessSearchDTO requestDTO) throws Exception;

  /** Submits creation of HTH access to the maker/checker workflow. */
  HostToHostUserAccessResponseDTO submit(
      SessionContext sessionContext,
      HostToHostUserAccessDTO requestDTO) throws Exception;

  /** Submits replacement of existing HTH access to the maker/checker workflow. */
  TransactionStatus edit(
      SessionContext sessionContext,
      HostToHostUserAccessDTO requestDTO) throws Exception;

  /** Submits soft deletion of existing HTH access to the maker/checker workflow. */
  TransactionStatus delete(
      SessionContext sessionContext,
      HostToHostUserAccessDTO requestDTO) throws Exception;
}

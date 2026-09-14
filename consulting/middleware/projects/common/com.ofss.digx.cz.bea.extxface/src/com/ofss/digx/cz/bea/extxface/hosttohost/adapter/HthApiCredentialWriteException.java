package com.ofss.digx.cz.bea.extxface.hosttohost.adapter;

/** Distinguishes a failure before transmission from an indeterminate remote write. */
public final class HthApiCredentialWriteException extends com.ofss.digx.infra.exceptions.Exception {
  private static final long serialVersionUID = 1L;
  private final boolean uncertain;

  public HthApiCredentialWriteException(boolean uncertain) {
    super("DIGX_CZ_HTH_API_PASSWORD_009");
    this.uncertain = uncertain;
  }

  public boolean isUncertain() {
    return uncertain;
  }
}

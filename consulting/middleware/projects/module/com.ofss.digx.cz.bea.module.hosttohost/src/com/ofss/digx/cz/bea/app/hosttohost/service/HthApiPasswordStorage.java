package com.ofss.digx.cz.bea.app.hosttohost.service;

import java.util.Locale;

/** Captured once per request; an invalid mode must never fall back to a different store. */
enum HthApiPasswordStorage {
  DATABASE, UAM;
  static HthApiPasswordStorage parse(String value) {
    return value == null ? DATABASE : valueOf(value.trim().toUpperCase(Locale.ROOT));
  }
}

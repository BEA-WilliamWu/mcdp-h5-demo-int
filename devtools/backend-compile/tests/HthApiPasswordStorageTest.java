package com.ofss.digx.cz.bea.app.hosttohost.service;

import com.ofss.digx.cz.bea.app.hosttohost.util.HthApiPasswordHash;

/** Standalone checks; fixture independently generated with Python hashlib.pbkdf2_hmac. */
public final class HthApiPasswordStorageTest {
  private static int checks;
  private static void check(boolean result) {
    checks++;
    if (!result) { throw new AssertionError("Check " + checks + " failed"); }
  }
  public static void main(String[] args) {
    check(HthApiPasswordStorage.parse(null) == HthApiPasswordStorage.DATABASE);
    check(HthApiPasswordStorage.parse("DATABASE") == HthApiPasswordStorage.DATABASE);
    check(HthApiPasswordStorage.parse(" uam ") == HthApiPasswordStorage.UAM);
    for (String invalid : new String[] {"", " ", "DB", "REMOTE", "DATABSE"}) {
      boolean rejected = false;
      try { HthApiPasswordStorage.parse(invalid); }
      catch (IllegalArgumentException expected) { rejected = true; }
      check(rejected);
    }
    String fixture = "pbkdf2-sha256-v1$600000$AAECAwQFBgcICQoLDA0ODw==$/m+kPa97EI6Op8NdFFRsENTmUFBcI+yV01la9z17TzE=";
    check(HthApiPasswordHash.verify("Example1234", fixture));
    check(!HthApiPasswordHash.verify("Wrong1234", fixture));
    check(!HthApiPasswordHash.verify("example1234", fixture));
    check(!HthApiPasswordHash.verify(null, fixture));
    check(!HthApiPasswordHash.verify("", fixture));
    check(!HthApiPasswordHash.verify("Example1234", null));
    check(!HthApiPasswordHash.verify("Example1234", fixture.replace("600000", "1")));
    check(!HthApiPasswordHash.verify("Example1234", fixture.replace("600000", "2147483647")));
    check(!HthApiPasswordHash.verify("Example1234", fixture.replace("v1", "v2")));
    check(!HthApiPasswordHash.verify("Example1234", "pbkdf2-sha256-v1$600000$bad$bad"));
    check(!HthApiPasswordHash.verify("Example1234", fixture + "$extra"));
    String first = HthApiPasswordHash.hash("OldPassword12");
    String second = HthApiPasswordHash.hash("OldPassword12");
    check(!first.equals(second));
    check(HthApiPasswordHash.verify("OldPassword12", first));
    String replacement = HthApiPasswordHash.hash("NewPassword34");
    check(!HthApiPasswordHash.verify("OldPassword12", replacement));
    check(HthApiPasswordHash.verify("NewPassword34", replacement));
    check(!replacement.contains("NewPassword34"));
    System.out.println("PASS: " + checks + " storage-mode and password-hash checks");
  }
}

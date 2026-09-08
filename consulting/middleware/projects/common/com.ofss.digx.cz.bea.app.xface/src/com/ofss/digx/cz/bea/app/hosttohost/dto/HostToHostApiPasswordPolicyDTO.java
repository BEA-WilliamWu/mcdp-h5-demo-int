package com.ofss.digx.cz.bea.app.hosttohost.dto;

import java.io.Serializable;

/** Public, non-sensitive password rules rendered by the HTH password form. */
public class HostToHostApiPasswordPolicyDTO implements Serializable {
  private static final long serialVersionUID = -1336904784615867509L;

  private int minLength = 8;
  private int maxLength = 16;
  private int numericRequired = 2;
  private int alphabetRequired = 1;
  private boolean specialCharsAllowed;
  private boolean spacesAllowed;

  public int getMinLength() { return minLength; }
  public void setMinLength(int minLength) { this.minLength = minLength; }
  public int getMaxLength() { return maxLength; }
  public void setMaxLength(int maxLength) { this.maxLength = maxLength; }
  public int getNumericRequired() { return numericRequired; }
  public void setNumericRequired(int numericRequired) { this.numericRequired = numericRequired; }
  public int getAlphabetRequired() { return alphabetRequired; }
  public void setAlphabetRequired(int alphabetRequired) { this.alphabetRequired = alphabetRequired; }
  public boolean isSpecialCharsAllowed() { return specialCharsAllowed; }
  public void setSpecialCharsAllowed(boolean specialCharsAllowed) {
    this.specialCharsAllowed = specialCharsAllowed;
  }
  public boolean isSpacesAllowed() { return spacesAllowed; }
  public void setSpacesAllowed(boolean spacesAllowed) { this.spacesAllowed = spacesAllowed; }
}

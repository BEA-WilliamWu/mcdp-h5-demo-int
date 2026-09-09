/**
 ***************************************************************************** 
* Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
******************************************************************************
 */
package com.ofss.digx.cz.bea.app.sms.dto.user;

import com.ofss.digx.app.common.dto.DomainObjectDTO;
import com.ofss.digx.app.sms.dto.user.UserDTO;
import com.ofss.fc.datatype.Date;

public class UserExtensionDataDTO extends DomainObjectDTO {
	// Reference retained in the original user-maintenance approval snapshot (BCOH2H-787).
	private String hthApiPasswordCodeId;

	public String getHthApiPasswordCodeId() { return hthApiPasswordCodeId; }
	public void setHthApiPasswordCodeId(String value) { hthApiPasswordCodeId = value; }

	private static final long serialVersionUID = 3195227515894062881L;

	private String signerPinReferenceNo;

	private String documentType;

	private String cdcNo;

	private String userID;

	private String loginHoldStatus;

	private String signerHoldStatus;

	private Boolean isAuthorisedPerson;

	private String loginPinstatus;

	private String loginHoldReason;

	private String userExtensionKey;

	private String signerID;

	private String documentID;

	private String documentCountry;

	private String externalReferenceId;

	private String loginID;

	private String loginPinReferenceNo;

	private String signerHoldReason;

	private String signerPinstatus;

	private Boolean idDocSubmitted;

	private String mobileNo;

	private UserDTO userDTO;

	private String loginPinType;

	private String signerPinType;

	private String forceChangeSigner;

	private Boolean suspsendEmailCondition;

	private String mobileCode;

	private Boolean isLoginPinReminder;

	private Boolean isSignerPinReminder;

	private String maskedMobileNo;

	private String maskedEmailId;

	private String maskedDocumentId;

	private String userLocale;

	private Integer signerAttempts;
	
	private String defaultUser;
	
//	----------------------- MigratedUserResetPassword changes - STARTS --------------------- 
	
	private String cdcNoEncrypted;

	private String userIDEncrypted;
	
	private String migtationStatus;
	
	private String isMerchantUser;

	private String userChannelType;
	
	private String bypassFlag;
	
	private String bypassCode;
	
	private Date bypassExpiryTime;
	
	public Date getBypassExpiryTime() {
		return bypassExpiryTime;
	}

	public void setBypassExpiryTime(Date bypassExpiryTime) {
		this.bypassExpiryTime = bypassExpiryTime;
	}

	public String getBypassFlag() {
		return bypassFlag;
	}

	public void setBypassFlag(String bypassFlag) {
		this.bypassFlag = bypassFlag;
	}

	public String getBypassCode() {
		return bypassCode;
	}

	public void setBypassCode(String bypassCode) {
		this.bypassCode = bypassCode;
	}

	public String getMigtationStatus() {
		return migtationStatus;
	}

	public void setMigtationStatus(String migtationStatus) {
		this.migtationStatus = migtationStatus;
	}

	public String getCdcNoEncrypted() {
		return cdcNoEncrypted;
	}

	public void setCdcNoEncrypted(String cdcNoEncrypted) {
		this.cdcNoEncrypted = cdcNoEncrypted;
	}

	public String getUserIDEncrypted() {
		return userIDEncrypted;
	}

	public void setUserIDEncrypted(String userIDEncrypted) {
		this.userIDEncrypted = userIDEncrypted;
	}

	private String keyIndicator;
	
	public String getKeyIndicator() {
		return keyIndicator;
	}

	public void setKeyIndicator(String keyIndicator) {
		this.keyIndicator = keyIndicator;
	}
//	----------------------- MigratedUserResetPassword changes - ENDS -----------------------
	

	public String getSignerPinReferenceNo() {
		return signerPinReferenceNo;
	}

	public void setSignerPinReferenceNo(String signerPinReferenceNo) {
		this.signerPinReferenceNo = signerPinReferenceNo;
	}

	public String getDocumentType() {
		return documentType;
	}

	public void setDocumentType(String documentType) {
		this.documentType = documentType;
	}

	public String getCdcNo() {
		return cdcNo;
	}

	public void setCdcNo(String cdcNo) {
		this.cdcNo = cdcNo;
	}

	public String getLoginHoldStatus() {
		return loginHoldStatus;
	}

	public void setLoginHoldStatus(String loginHoldStatus) {
		this.loginHoldStatus = loginHoldStatus;
	}

	public String getSignerHoldStatus() {
		return signerHoldStatus;
	}

	public void setSignerHoldStatus(String signerHoldStatus) {
		this.signerHoldStatus = signerHoldStatus;
	}

	public Boolean getIsAuthorisedPerson() {
		return isAuthorisedPerson;
	}

	public void setIsAuthorisedPerson(Boolean isAuthorisedPerson) {
		this.isAuthorisedPerson = isAuthorisedPerson;
	}

	public String getLoginPinstatus() {
		return loginPinstatus;
	}

	public void setLoginPinstatus(String loginPinstatus) {
		this.loginPinstatus = loginPinstatus;
	}

	public String getLoginHoldReason() {
		return loginHoldReason;
	}

	public void setLoginHoldReason(String loginHoldReason) {
		this.loginHoldReason = loginHoldReason;
	}

	public String getUserExtensionKey() {
		return userExtensionKey;
	}

	public void setUserExtensionKey(String userExtensionKey) {
		this.userExtensionKey = userExtensionKey;
	}

	public String getSignerID() {
		return signerID;
	}

	public void setSignerID(String signerID) {
		this.signerID = signerID;
	}

	public String getDocumentID() {
		return documentID;
	}

	public void setDocumentID(String documentID) {
		this.documentID = documentID;
	}

	public String getDocumentCountry() {
		return documentCountry;
	}

	public void setDocumentCountry(String documentCountry) {
		this.documentCountry = documentCountry;
	}

	public String getExternalReferenceId() {
		return externalReferenceId;
	}

	public void setExternalReferenceId(String externalReferenceId) {
		this.externalReferenceId = externalReferenceId;
	}

	public String getLoginID() {
		return loginID;
	}

	public void setLoginID(String loginID) {
		this.loginID = loginID;
	}

	public String getLoginPinReferenceNo() {
		return loginPinReferenceNo;
	}

	public void setLoginPinReferenceNo(String loginPinReferenceNo) {
		this.loginPinReferenceNo = loginPinReferenceNo;
	}

	public String getSignerHoldReason() {
		return signerHoldReason;
	}

	public void setSignerHoldReason(String signerHoldReason) {
		this.signerHoldReason = signerHoldReason;
	}

	public String getSignerPinstatus() {
		return signerPinstatus;
	}

	public void setSignerPinstatus(String signerPinstatus) {
		this.signerPinstatus = signerPinstatus;
	}

	public Boolean getIdDocSubmitted() {
		return idDocSubmitted;
	}

	public void setIdDocSubmitted(Boolean idDocSubmitted) {
		this.idDocSubmitted = idDocSubmitted;
	}

	public String getUserID() {
		return userID;
	}

	public void setUserID(String userID) {
		this.userID = userID;
	}

	/**
	 * @return the mobileNo
	 */
	public String getMobileNo() {
		return mobileNo;
	}

	/**
	 * @param mobileNo
	 *            the mobileNo to set
	 */
	public void setMobileNo(String mobileNo) {
		this.mobileNo = mobileNo;
	}

	/**
	 * @return the userDTO
	 */
	public UserDTO getUserDTO() {
		return userDTO;
	}

	/**
	 * @param userDTO
	 *            the userDTO to set
	 */
	public void setUserDTO(UserDTO userDTO) {
		this.userDTO = userDTO;
	}

	public String getLoginPinType() {
		return loginPinType;
	}

	public void setLoginPinType(String loginPinType) {
		this.loginPinType = loginPinType;
	}

	public String getSignerPinType() {
		return signerPinType;
	}

	public void setSignerPinType(String signerPinType) {
		this.signerPinType = signerPinType;
	}

	public String getForceChangeSigner() {
		return forceChangeSigner;
	}

	public void setForceChangeSigner(String forceChangeSigner) {
		this.forceChangeSigner = forceChangeSigner;
	}

	public Boolean getSuspsendEmailCondition() {
		return suspsendEmailCondition;
	}

	public void setSuspsendEmailCondition(Boolean suspsendEmailCondition) {
		this.suspsendEmailCondition = suspsendEmailCondition;
	}

	public String getMobileCode() {
		return mobileCode;
	}

	public void setMobileCode(String mobileCode) {
		this.mobileCode = mobileCode;
	}

	public Boolean getIsLoginPinReminder() {
		return isLoginPinReminder;
	}

	public void setIsLoginPinReminder(Boolean isLoginPinReminder) {
		this.isLoginPinReminder = isLoginPinReminder;
	}

	public Boolean getIsSignerPinReminder() {
		return isSignerPinReminder;
	}

	public void setIsSignerPinReminder(Boolean isSignerPinReminder) {
		this.isSignerPinReminder = isSignerPinReminder;
	}

	public String getMaskedMobileNo() {
		return maskedMobileNo;
	}

	public void setMaskedMobileNo(String maskedMobileNo) {
		this.maskedMobileNo = maskedMobileNo;
	}

	public String getMaskedEmailId() {
		return maskedEmailId;
	}

	public void setMaskedEmailId(String maskedEmailId) {
		this.maskedEmailId = maskedEmailId;
	}

	public String getMaskedDocumentId() {
		return maskedDocumentId;
	}

	public void setMaskedDocumentId(String maskedDocumentId) {
		this.maskedDocumentId = maskedDocumentId;
	}

	/**
	 * @return the userLocale
	 */
	public String getUserLocale() {
		return userLocale;
	}

	/**
	 * @param userLocale
	 *            the userLocale to set
	 */
	public void setUserLocale(String userLocale) {
		this.userLocale = userLocale;
	}

	public Integer getSignerAttempts() {
		return signerAttempts;
	}

	public void setSignerAttempts(Integer signerAttempts) {
		this.signerAttempts = signerAttempts;
	}

	/**
	 * @return the defaultUser
	 */
	public String getDefaultUser() {
		return defaultUser;
	}

	/**
	 * @param defaultUser the defaultUser to set
	 */
	public void setDefaultUser(String defaultUser) {
		this.defaultUser = defaultUser;
	}



	public String getIsMerchantUser() {
		return isMerchantUser;
	}

	public void setIsMerchantUser(String isMerchantUser) {
		this.isMerchantUser = isMerchantUser;
	}

	public String getUserChannelType() {
		return userChannelType;
	}

	public void setUserChannelType(String userChannelType) {
		this.userChannelType = userChannelType;
	}

	@Override
	public String toString() {
		return "UserExtensionDataDTO [signerPinReferenceNo=" + signerPinReferenceNo + ", documentType=" + documentType
				+ ", cdcNo=" + cdcNo + ", userID=" + userID + ", loginHoldStatus=" + loginHoldStatus
				+ ", signerHoldStatus=" + signerHoldStatus + ", isAuthorisedPerson=" + isAuthorisedPerson
				+ ", loginPinstatus=" + loginPinstatus + ", loginHoldReason=" + loginHoldReason + ", userExtensionKey="
				+ userExtensionKey + ", signerID=" + signerID + ", documentID=" + documentID + ", documentCountry="
				+ documentCountry + ", externalReferenceId=" + externalReferenceId + ", loginID=" + loginID
				+ ", loginPinReferenceNo=" + loginPinReferenceNo + ", signerHoldReason=" + signerHoldReason
				+ ", signerPinstatus=" + signerPinstatus + ", idDocSubmitted=" + idDocSubmitted + ", mobileNo="
				+ mobileNo + ", userDTO=" + userDTO + ", loginPinType=" + loginPinType + ", signerPinType="
				+ signerPinType + ", forceChangeSigner=" + forceChangeSigner + ", suspsendEmailCondition="
				+ suspsendEmailCondition + ", mobileCode=" + mobileCode + ", isLoginPinReminder=" + isLoginPinReminder
				+ ", isSignerPinReminder=" + isSignerPinReminder + ", maskedMobileNo=" + maskedMobileNo
				+ ", maskedEmailId=" + maskedEmailId + ", maskedDocumentId=" + maskedDocumentId + ", userLocale="
				+ userLocale + ", cdcNoEncrypted=" + cdcNoEncrypted + ", userIDEncrypted=" + userIDEncrypted
				+ ", migtationStatus=" + migtationStatus + ", bypassFlag=" + bypassFlag + ", bypassCode=" + bypassCode + "]";
	}
}

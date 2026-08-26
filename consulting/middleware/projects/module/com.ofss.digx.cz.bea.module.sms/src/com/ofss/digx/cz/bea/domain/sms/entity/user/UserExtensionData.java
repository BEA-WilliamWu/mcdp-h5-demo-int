/**
 ***************************************************************************** 
* Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
******************************************************************************
 */
package com.ofss.digx.cz.bea.domain.sms.entity.user;


import java.util.List;

import com.ofss.digx.app.sms.dto.user.UserDTO;
import com.ofss.digx.cz.bea.app.logger.BeaSystemOut;
import com.ofss.digx.cz.bea.app.sms.dto.user.AuditLogMigRequestDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.CZUserDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.CreateLoginUserRequestDto;
import com.ofss.digx.cz.bea.app.sms.dto.user.CreateLoginUserResponseDto;
import com.ofss.digx.cz.bea.app.sms.dto.user.MigrationStatusRequestDto;
import com.ofss.digx.cz.bea.app.sms.dto.user.MigrationStatusResponseDto;
import com.ofss.digx.cz.bea.app.sms.dto.user.PasswordExpiryDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.ResetPasswordDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.ResetUserDataDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserDetailsUpdationDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataResponseDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserPartyListDTO;
import com.ofss.digx.cz.bea.domain.sms.entity.user.repository.UserExtensionDataRepository;
import com.ofss.digx.domain.approval.entity.usergroup.UserGroup;
import com.ofss.digx.domain.approval.entity.usergroup.UserGroupUser;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.datatype.Date;
import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;

public class UserExtensionData extends AbstractDomainObject implements IPersistenceObject {
	private static final long serialVersionUID = -2905494113650373268L;

	private String loginHoldStatus;

	private String signerID;

	private String userID;

	private String documentType;

	private UserExtensionDataKey userExtensionDataKey;

	private String documentCountry;

	private Boolean isAuthorisedPerson;
	
	private Boolean companyActivityLog;

	private Date logOutDate;

	private String documentID;

	private String loginPinReferenceNo;

	private String signerPinReferenceNo;

	private String signerHoldStatus;

	private String signerPinstatus;

	private String externalReferenceId;

	private String loginID;

	private String bounceBackReminder;

	private String signerHoldReason;

	private String loginHoldReason;

	private Boolean idDocSubmitted;

	private String loginPinstatus;

	private String cdcNo;

	private String mobileNo;

	private String loginPinType;

	private String signerPinType;

	private String forceChangeSigner;

	public com.ofss.fc.datatype.Date loginPinMapDate;

	public com.ofss.fc.datatype.Date signPinMapDate;

	public com.ofss.fc.datatype.Date signPinExpiryDate;

	private String mobileCode;

	private Boolean isLoginPinReminder;

	private Boolean isSignerPinReminder;

	private String userLocale;

	private Integer signerAttempts;

	private String defaultUser;

	private String migtationStatus;
	
	private String migSignerUpdated;
	
	private Date ccbLastLoginDate;
	
	private String isMerchantUser;

	private String userChannelType;

	private String closeId;

	/** True when the HTH user has at least one active effective account grant. */
	private Boolean hthAccessSetupDone;
	
	private String securityQuestionsBypass;
	
	private String bypassCode;
	
	private Date bypassExpiryTime;

	public String getLoginHoldStatus() {
		return loginHoldStatus;
	}

	public void setLoginHoldStatus(String loginHoldStatus) {
		this.loginHoldStatus = loginHoldStatus;
	}

	public String getSignerID() {
		return signerID;
	}

	public void setSignerID(String signerID) {
		this.signerID = signerID;
	}

	public String getDocumentType() {
		return documentType;
	}

	public void setDocumentType(String documentType) {
		this.documentType = documentType;
	}

	public String getDocumentCountry() {
		return documentCountry;
	}

	public void setDocumentCountry(String documentCountry) {
		this.documentCountry = documentCountry;
	}

	public Boolean getIsAuthorisedPerson() {
		return isAuthorisedPerson;
	}

	public void setIsAuthorisedPerson(Boolean isAuthorisedPerson) {
		this.isAuthorisedPerson = isAuthorisedPerson;
	}

	public Boolean getCompanyActivityLog() {
		return companyActivityLog;
	}

	public void setCompanyActivityLog(Boolean companyActivityLog) {
		this.companyActivityLog = companyActivityLog;
	}

	public String getDocumentID() {
		return documentID;
	}

	public void setDocumentID(String documentID) {
		this.documentID = documentID;
	}

	public String getLoginPinReferenceNo() {
		return loginPinReferenceNo;
	}

	public void setLoginPinReferenceNo(String loginPinReferenceNo) {
		this.loginPinReferenceNo = loginPinReferenceNo;
	}

	public String getSignerPinReferenceNo() {
		return signerPinReferenceNo;
	}

	public void setSignerPinReferenceNo(String signerPinReferenceNo) {
		this.signerPinReferenceNo = signerPinReferenceNo;
	}

	public String getSignerHoldStatus() {
		return signerHoldStatus;
	}

	public void setSignerHoldStatus(String signerHoldStatus) {
		this.signerHoldStatus = signerHoldStatus;
	}

	public String getSignerPinstatus() {
		return signerPinstatus;
	}

	public void setSignerPinstatus(String signerPinstatus) {
		this.signerPinstatus = signerPinstatus;
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

	public String getSignerHoldReason() {
		return signerHoldReason;
	}

	public void setSignerHoldReason(String signerHoldReason) {
		this.signerHoldReason = signerHoldReason;
	}

	public String getLoginHoldReason() {
		return loginHoldReason;
	}

	public void setLoginHoldReason(String loginHoldReason) {
		this.loginHoldReason = loginHoldReason;
	}

	public Boolean getIdDocSubmitted() {
		return idDocSubmitted;
	}

	public void setIdDocSubmitted(Boolean idDocSubmitted) {
		this.idDocSubmitted = idDocSubmitted;
	}

	public String getLoginPinstatus() {
		return loginPinstatus;
	}

	public void setLoginPinstatus(String loginPinstatus) {
		this.loginPinstatus = loginPinstatus;
	}

	public String getCdcNo() {
		return cdcNo;
	}

	public void setCdcNo(String cdcNo) {
		this.cdcNo = cdcNo;
	}

	public UserExtensionDataKey getUserExtensionDataKey() {
		return userExtensionDataKey;
	}

	public void setUserExtensionDataKey(UserExtensionDataKey userExtensionDataKey) {
		this.userExtensionDataKey = userExtensionDataKey;
	}

	public com.ofss.fc.datatype.Date getLoginPinMapDate() {
		return loginPinMapDate;
	}

	public void setLoginPinMapDate(com.ofss.fc.datatype.Date loginPinMapDate) {
		this.loginPinMapDate = loginPinMapDate;
	}

	public com.ofss.fc.datatype.Date getSignPinMapDate() {
		return signPinMapDate;
	}

	public void setSignPinMapDate(com.ofss.fc.datatype.Date signPinMapDate) {
		this.signPinMapDate = signPinMapDate;
	}

	@Override
	protected void validate() {
	}

	public String getUserID() {
		return userID;
	}

	public void setUserID(String userID) {
		this.userID = userID;
	}

	public String getMobileNo() {
		return mobileNo;
	}

	public void setMobileNo(String mobileNo) {
		this.mobileNo = mobileNo;
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

	public com.ofss.fc.datatype.Date getSignPinExpiryDate() {
		return signPinExpiryDate;
	}

	public void setSignPinExpiryDate(com.ofss.fc.datatype.Date signPinExpiryDate) {
		this.signPinExpiryDate = signPinExpiryDate;
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

	public Date getLogOutDate() {
		return logOutDate;
	}

	public void setLogOutDate(Date logOutDate) {
		this.logOutDate = logOutDate;
	}

	public String getUserLocale() {
		return userLocale;
	}

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

	public String getMigtationStatus() {
		return migtationStatus;
	}

	public void setMigtationStatus(String migtationStatus) {
		this.migtationStatus = migtationStatus;
	}

	/**
	 * @return the migSignerUpdated
	 */
	public String getMigSignerUpdated() {
		return migSignerUpdated;
	}


	/**
	 * @return the ccbLastLoginDate
	 */
	public Date getCcbLastLoginDate() {
		return ccbLastLoginDate;
	}

	/**
	 * @param ccbLastLoginDate the ccbLastLoginDate to set
	 */
	public void setCcbLastLoginDate(Date ccbLastLoginDate) {
		this.ccbLastLoginDate = ccbLastLoginDate;
	}

	/**
	 * @param migSignerUpdated the migSignerUpdated to set
	 */
	public void setMigSignerUpdated(String migSignerUpdated) {
		this.migSignerUpdated = migSignerUpdated;
	}

	public String getSecurityQuestionsBypass() {
		return securityQuestionsBypass;
	}

	public void setSecurityQuestionsBypass(String securityQuestionsBypass) {
		this.securityQuestionsBypass = securityQuestionsBypass;
	}

	public String getBypassCode() {
		return bypassCode;
	}

	public void setBypassCode(String bypassCode) {
		this.bypassCode = bypassCode;
	}

	public Date getBypassExpiryTime() {
		return bypassExpiryTime;
	}

	public void setBypassExpiryTime(Date bypassExpiryTime) {
		this.bypassExpiryTime = bypassExpiryTime;
	}

	public UserExtensionData read(UserExtensionDataKey userExtensionDataKey) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		return userExtensionDataRepository.read(userExtensionDataKey);
	}

	public void update(UserExtensionData userExtensionData) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		userExtensionDataRepository.update(userExtensionData);
	}

	public void create(UserExtensionData userExtensionData) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		userExtensionDataRepository.create(userExtensionData);
	}

	public UserExtensionData getDetails(ResetPasswordDTO resetPasswordDTO) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		return userExtensionDataRepository.getDetails(resetPasswordDTO);
	}

	public List<UserExtensionData> listUsers(UserDTO userDTO) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		return userExtensionDataRepository.listUsers(userDTO);
	}

// rkeshari 26/12/24 Added for PRD SR 3-39146654721
	public List<UserExtensionData> listUsersForLogin(UserDTO userDTO) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		return userExtensionDataRepository.listUsersForLogin(userDTO);
	}

	public UserExtensionData getUserDetails(ResetPasswordDTO resetPasswordDTO) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		return userExtensionDataRepository.getUserDetails(resetPasswordDTO);
	}

	public UserExtensionData getSignerDetails(ResetPasswordDTO resetPasswordDTO) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		return userExtensionDataRepository.getSignerDetails(resetPasswordDTO);
	}

	public List<ResetUserDataDTO> listResetPinUsers(UserDTO userDto) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		return userExtensionDataRepository.listResetPinUsers(userDto);
	}

	public List<UserExtensionData> listUsersByParty(UserExtensionData userExtensionData) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		return userExtensionDataRepository.listUsersByParty(userExtensionData);
	}

	public UserExtensionDataResponseDTO fetchCustInfo(UserExtensionData userExtensionData) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		return userExtensionDataRepository.fetchCustInfo(userExtensionData);
	}

	public List<CZUserDTO> listUsersData(UserPartyListDTO partyList) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		return userExtensionDataRepository.listUsersData(partyList);
	}

	public void updatePasswordExpiryDate(PasswordExpiryDTO passwordExpiryDTO) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		userExtensionDataRepository.updatePasswordExpiryDate(passwordExpiryDTO);
	}
	
	public void updatePasswordExpiryDateAndForceChangePassword(PasswordExpiryDTO passwordExpiryDTO, boolean forceChangePassword) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		userExtensionDataRepository.updatePasswordExpiryDateAndForceChangePassword(passwordExpiryDTO, forceChangePassword);
	}

	public List<UserGroupUser> fetchUserGroupListByUsername(String username) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		return userExtensionDataRepository.listUserGroupListByUsername(username);
	}

	public List<UserGroup> fetchGroupIdByName(String groupName) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		return userExtensionDataRepository.fetchGroupIdByName(groupName);
	}

	public void deleteUserFromGroup(String username, String id) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		userExtensionDataRepository.deleteUserFromGroup(username, id);
	}

	public void updateUserLocale(String username, String cdcId, String userLocale) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		userExtensionDataRepository.updateUserLocale(username, cdcId, userLocale);
	}

	public void updateUserExtensionData(UserExtensionData domain) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		userExtensionDataRepository.updateUserExtensionData(domain);
	}

	@Override
	public String toString() {
		return "UserExtensionData [loginHoldStatus=" + loginHoldStatus + ", signerID=" + signerID + ", userID=" + userID
				+ ", documentType=" + documentType + ", userExtensionDataKey=" + userExtensionDataKey
				+ ", documentCountry=" + documentCountry + ", isAuthorisedPerson=" + isAuthorisedPerson
				+ ", companyActivityLog=" + companyActivityLog + ", logOutDate=" + logOutDate
				+ ", documentID=" + documentID + ", loginPinReferenceNo=" + loginPinReferenceNo
				+ ", signerPinReferenceNo=" + signerPinReferenceNo + ", signerHoldStatus=" + signerHoldStatus
				+ ", signerPinstatus=" + signerPinstatus + ", externalReferenceId=" + externalReferenceId + ", loginID="
				+ loginID + ", bounceBackReminder=" + bounceBackReminder + ", signerHoldReason=" + signerHoldReason
				+ ", loginHoldReason=" + loginHoldReason + ", idDocSubmitted=" + idDocSubmitted + ", loginPinstatus="
				+ loginPinstatus + ", cdcNo=" + cdcNo + ", mobileNo=" + mobileNo + ", loginPinType=" + loginPinType
				+ ", signerPinType=" + signerPinType + ", forceChangeSigner=" + forceChangeSigner + ", loginPinMapDate="
				+ loginPinMapDate + ", signPinMapDate=" + signPinMapDate + ", signPinExpiryDate=" + signPinExpiryDate
				+ ", mobileCode=" + mobileCode + ", isLoginPinReminder=" + isLoginPinReminder + ", isSignerPinReminder="
				+ isSignerPinReminder + ", userLocale=" + userLocale + ", signerAttempts=" + signerAttempts
				+ ", defaultUser=" + defaultUser + ", migtationStatus=" + migtationStatus + ", migSignerUpdated="
				+ migSignerUpdated + ", ccbLastLoginDate=" + ccbLastLoginDate + ", isMerchantUser=" + isMerchantUser
				+ "]";
	}

	public String getBounceBackReminder() {
		return bounceBackReminder;
	}

	public void setBounceBackReminder(String bounceBackReminder) {
		this.bounceBackReminder = bounceBackReminder;
	}


	public Date getPreLastLogin(String userId) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		return userExtensionDataRepository.getPreLastLogin(userId);
	}

	public void updateDefaultUser(UserDetailsUpdationDTO userDetailsUpdationDTO, String userName) throws Exception {
		BeaSystemOut.println("Entered updateDefaultUser domain");
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		userExtensionDataRepository.updateDefaultUser(userDetailsUpdationDTO, userName);
		BeaSystemOut.println("Exited updateDefaultUser domain");

	}

	public void updateMigrationStatus(SessionContext sessionContext,
			MigrationStatusRequestDto migrationStatusRequestDto) throws Exception {
		BeaSystemOut.println("User.updateMigrationStatus() before calling  reposity");
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		BeaSystemOut.println("User.updateMigrationStatus() after calling  reposity");
		userExtensionDataRepository.updateMigrationStatus(migrationStatusRequestDto);
	}

	public MigrationStatusResponseDto updateHostMigrationStatus(SessionContext sessionContext,
			MigrationStatusRequestDto migrationStatusRequestDto) throws Exception {
		BeaSystemOut.println("UserExtensionData.readStatus()");
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		return userExtensionDataRepository.updateHostMigrationStatus(migrationStatusRequestDto);
	}

	public CreateLoginUserResponseDto migrateLoginUser(SessionContext sessionContext,
			CreateLoginUserRequestDto createLoginUserRequestDto) throws Exception{
		BeaSystemOut.println("UserExtensionData.createLoginUser()");
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		return userExtensionDataRepository.migrateLoginUser(createLoginUserRequestDto);
	}

	public AuditLogMigRequestDTO downloadCcbAuditLog(String partyId, String auditType) throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		return userExtensionDataRepository.downloadCcbAuditLog(partyId, auditType);
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

	public String getCloseId() {
		return closeId;
	}

	public void setCloseId(String closeId) {
		this.closeId = closeId;
	}

	public Boolean getHthAccessSetupDone() {
		return hthAccessSetupDone;
	}

	public void setHthAccessSetupDone(Boolean hthAccessSetupDone) {
		this.hthAccessSetupDone = hthAccessSetupDone;
	}

	public String readTimerJvmID(String id) throws Exception {
		BeaSystemOut.println("Inside readTimerJvmID domain");
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		return userExtensionDataRepository.readTimerJvmID(id);
	}
	
	public boolean isBypassCodeExpiry() {
		if (bypassExpiryTime == null || bypassExpiryTime.compareTo(new Date()) < 0) {
			return true;
		} else {
			return false;
		}
	}

	public List<UserExtensionData> listUsersBypassCode() throws Exception {
		UserExtensionDataRepository userExtensionDataRepository = UserExtensionDataRepository.getInstance();
		return userExtensionDataRepository.listUsersBypassCode();
	}

}

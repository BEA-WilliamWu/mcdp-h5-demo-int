/**
 ***************************************************************************** 
* Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
******************************************************************************
 */
package com.ofss.digx.cz.bea.app.sms.assembler.user;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataDTO;
import com.ofss.digx.cz.bea.common.util.UserManagementUtils;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionDataKey;
import com.ofss.fc.datatype.Date;
import com.ofss.fc.enumeration.EntityStatus;
import com.ofss.fc.framework.domain.assembler.AbstractAssembler;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;

public class UserExtensionDataAssembler extends AbstractAssembler<UserExtensionData, UserExtensionDataDTO> {
	private static final String THIS_COMPONENT_NAME = UserExtensionDataAssembler.class.getName();

	private transient Logger logger = MultiEntityLogger.getUniqueInstance().getLogger(THIS_COMPONENT_NAME);

	@Override
	public UserExtensionData toDomainObject(UserExtensionDataDTO userExtensionDataDTO) {
		UserExtensionData userExtensionData = null;
		if (userExtensionDataDTO != null) {
			populateDataTransferObjectDTOMap("com.ofss.digx.cz.app.sms.dto.user.UserExtensionDataDTO",
					userExtensionDataDTO);
			try {
				if (retrieveDataTransferObjectDTOMapElement("com.ofss.digx.cz.app.sms.dto.user.UserExtensionDataDTO")
						.getDictionaryArray() == null) {
					userExtensionData = new UserExtensionData();
				} else {
					userExtensionData = (UserExtensionData) getCustomizedDomainObject(
							retrieveDataTransferObjectDTOMapElement(
									"com.ofss.digx.cz.app.sms.dto.user.UserExtensionDataDTO"));
				}
			} catch (Exception e) {
				logger.log(Level.WARNING, "Customized Domain Object failed to be instantiated from "
						+ "com.ofss.digx.cz.app.sms.dto.user.UserExtensionDataDTO", e);
			}
			UserExtensionDataKey userExtensionDataKey = new UserExtensionDataKey();
			if (userExtensionDataDTO.getUserExtensionKey() != null) {
				userExtensionDataKey.setUserExtensionKey(userExtensionDataDTO.getUserExtensionKey());
			} else {
				userExtensionDataKey.setUserExtensionKey(
						userExtensionDataDTO.getCdcNo().concat("@").concat(userExtensionDataDTO.getUserID()));
			}
			userExtensionData.setUserExtensionDataKey(userExtensionDataKey);
			userExtensionData.setLoginHoldStatus(userExtensionDataDTO.getLoginHoldStatus());
			userExtensionData.setUserID(userExtensionDataDTO.getUserID() != null ? userExtensionDataDTO.getUserID().toUpperCase() : userExtensionDataDTO.getUserID());
			userExtensionData.setSignerID(userExtensionDataDTO.getSignerID());
			userExtensionData.setDocumentType(userExtensionDataDTO.getDocumentType());
			userExtensionData.setDocumentCountry(userExtensionDataDTO.getDocumentCountry());
			userExtensionData.setIsAuthorisedPerson(userExtensionDataDTO.getIsAuthorisedPerson());
			userExtensionData.setCompanyActivityLog(userExtensionDataDTO.getCompanyActivityLog());
			userExtensionData.setDocumentID(userExtensionDataDTO.getDocumentID());
			userExtensionData.setLoginPinReferenceNo(userExtensionDataDTO.getLoginPinReferenceNo());
			userExtensionData.setSignerPinReferenceNo(userExtensionDataDTO.getSignerPinReferenceNo());
			userExtensionData.setSignerHoldStatus(userExtensionDataDTO.getSignerHoldStatus());
			userExtensionData.setSignerPinstatus(userExtensionDataDTO.getSignerPinstatus());
			userExtensionData.setExternalReferenceId(userExtensionDataDTO.getExternalReferenceId());
			userExtensionData.setLoginID(userExtensionDataDTO.getLoginID());
			userExtensionData.setSignerHoldReason(userExtensionDataDTO.getSignerHoldReason());
			userExtensionData.setLoginHoldReason(userExtensionDataDTO.getLoginHoldReason());
			userExtensionData.setIdDocSubmitted(userExtensionDataDTO.getIdDocSubmitted());
			userExtensionData.setLoginPinstatus(userExtensionDataDTO.getLoginPinstatus());
			userExtensionData.setCdcNo(userExtensionDataDTO.getCdcNo());
			userExtensionData.setEntityStatus(EntityStatus.ACTIVE);
			userExtensionData.setMobileNo(userExtensionDataDTO.getMobileNo());
			userExtensionData.setMobileCode(userExtensionData.getMobileCode());
			userExtensionData.setLoginPinMapDate(new Date());
			userExtensionData.setSignPinMapDate(new Date());
			userExtensionData.setMobileCode(userExtensionDataDTO.getMobileCode());
			userExtensionData.setIsLoginPinReminder(false);
			userExtensionData.setIsSignerPinReminder(false);
			userExtensionData.setIsMerchantUser(userExtensionDataDTO.getIsMerchantUser());
			userExtensionData.setUserChannelType(userExtensionDataDTO.getUserChannelType());
			
		}
		return userExtensionData;
	}

	@Override
	public UserExtensionDataDTO fromDomainObject(UserExtensionData userExtensionData) {
		UserExtensionDataDTO userExtensionDataDTO = null;
		if (userExtensionData != null) {
			populateAbstractDomainObjectMap("com.ofss.digx.cz.domain.sms.entity.user.UserExtensionData",
					userExtensionData);
			userExtensionDataDTO = new UserExtensionDataDTO();
			if (userExtensionData.getUserExtensionDataKey() != null) {
				userExtensionDataDTO
						.setUserExtensionKey(userExtensionData.getUserExtensionDataKey().getUserExtensionKey());
			}
			userExtensionDataDTO.setSignerPinReferenceNo(userExtensionData.getSignerPinReferenceNo());
			userExtensionDataDTO.setDocumentType(userExtensionData.getDocumentType());
			userExtensionDataDTO.setCdcNo(userExtensionData.getCdcNo());
			userExtensionDataDTO.setUserID(userExtensionData.getUserID());
			userExtensionDataDTO.setLoginHoldStatus(userExtensionData.getLoginHoldStatus());
			userExtensionDataDTO.setSignerHoldStatus(userExtensionData.getSignerHoldStatus());
			userExtensionDataDTO.setIsAuthorisedPerson(userExtensionData.getIsAuthorisedPerson());
			userExtensionDataDTO.setCompanyActivityLog(userExtensionData.getCompanyActivityLog());
			userExtensionDataDTO.setLoginPinstatus(userExtensionData.getLoginPinstatus());
			userExtensionDataDTO.setLoginHoldReason(userExtensionData.getLoginHoldReason());
			userExtensionDataDTO.setSignerID(userExtensionData.getSignerID());
			userExtensionDataDTO.setDocumentID(userExtensionData.getDocumentID());
			userExtensionDataDTO.setDocumentCountry(userExtensionData.getDocumentCountry());
			userExtensionDataDTO.setExternalReferenceId(userExtensionData.getExternalReferenceId());
			userExtensionDataDTO.setLoginID(userExtensionData.getLoginID());
			userExtensionDataDTO.setLoginPinReferenceNo(userExtensionData.getLoginPinReferenceNo());
			userExtensionDataDTO.setSignerHoldReason(userExtensionData.getSignerHoldReason());
			userExtensionDataDTO.setSignerPinstatus(userExtensionData.getSignerPinstatus());
			userExtensionDataDTO.setIdDocSubmitted(userExtensionData.getIdDocSubmitted());
			userExtensionDataDTO.setDictionaryArray(getDictionaryArray(userExtensionData));
			userExtensionDataDTO.setMobileNo(userExtensionData.getMobileNo());
			userExtensionDataDTO.setMobileCode(userExtensionData.getMobileCode());
			userExtensionDataDTO.setLoginPinType(userExtensionData.getLoginPinType());
			userExtensionDataDTO.setSignerPinType(userExtensionData.getSignerPinType());
			userExtensionDataDTO
					.setMaskedDocumentId(UserManagementUtils.getMaskedDocumentID(userExtensionData.getDocumentID()));
			userExtensionDataDTO.setUserLocale(userExtensionData.getUserLocale());
			userExtensionDataDTO.setDefaultUser(userExtensionData.getDefaultUser());
//			----------------------- MigrationUserOnboardingEdit changes - STARTS --------------------- 
			userExtensionDataDTO.setMigtationStatus(userExtensionData.getMigtationStatus());
//			----------------------- MigrationUserOnboardingEdit changes - ENDS -----------------------
			userExtensionDataDTO.setIsMerchantUser(userExtensionData.getIsMerchantUser());
			userExtensionDataDTO.setUserChannelType(userExtensionData.getUserChannelType());
			
			//login pin reset
			userExtensionDataDTO.setBypassCode(userExtensionData.getBypassCode());
			userExtensionDataDTO.setBypassFlag(userExtensionData.getSecurityQuestionsBypass());
			userExtensionDataDTO.setBypassExpiryTime(userExtensionData.getBypassExpiryTime());
			userExtensionDataDTO.setVersion(userExtensionData.getVersion());
		}
		return userExtensionDataDTO;
	}

	public com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData toUpdateDomainObject(
			UserExtensionDataDTO userExtensionDataDTO, UserExtensionData userExtensionData) {
		// TODO Auto-generated method stub

		userExtensionData.setLoginHoldStatus(userExtensionDataDTO.getLoginHoldStatus());
		userExtensionData.setUserID(userExtensionDataDTO.getUserID() != null ? userExtensionDataDTO.getUserID().toUpperCase() : userExtensionDataDTO.getUserID());
		userExtensionData.setSignerID(userExtensionDataDTO.getSignerID());
		userExtensionData.setDocumentType(userExtensionDataDTO.getDocumentType());
		userExtensionData.setDocumentCountry(userExtensionDataDTO.getDocumentCountry());
		userExtensionData.setIsAuthorisedPerson(userExtensionDataDTO.getIsAuthorisedPerson());
		userExtensionData.setCompanyActivityLog(userExtensionDataDTO.getCompanyActivityLog());
		userExtensionData.setDocumentID(userExtensionDataDTO.getDocumentID());
		userExtensionData.setLoginPinReferenceNo(userExtensionDataDTO.getLoginPinReferenceNo());
		userExtensionData.setSignerPinReferenceNo(userExtensionDataDTO.getSignerPinReferenceNo());
		userExtensionData.setSignerHoldStatus(userExtensionDataDTO.getSignerHoldStatus());
		userExtensionData.setSignerPinstatus(userExtensionDataDTO.getSignerPinstatus());
		userExtensionData.setExternalReferenceId(userExtensionDataDTO.getExternalReferenceId());
		userExtensionData.setLoginID(userExtensionDataDTO.getLoginID());
		userExtensionData.setSignerHoldReason(userExtensionDataDTO.getSignerHoldReason());
		userExtensionData.setLoginHoldReason(userExtensionDataDTO.getLoginHoldReason());
		userExtensionData.setIdDocSubmitted(userExtensionDataDTO.getIdDocSubmitted());
		userExtensionData.setLoginPinstatus(userExtensionDataDTO.getLoginPinstatus());
		userExtensionData.setCdcNo(userExtensionDataDTO.getCdcNo());
		userExtensionData.setEntityStatus(EntityStatus.ACTIVE);
		userExtensionData.setMobileNo(userExtensionDataDTO.getMobileNo());
		userExtensionData.setMobileCode(userExtensionDataDTO.getMobileCode());
		userExtensionData.setLoginPinMapDate(new Date());
		userExtensionData.setSignPinMapDate(new Date());
		userExtensionData.setUserLocale(userExtensionDataDTO.getUserLocale());

		return userExtensionData;
	}
}

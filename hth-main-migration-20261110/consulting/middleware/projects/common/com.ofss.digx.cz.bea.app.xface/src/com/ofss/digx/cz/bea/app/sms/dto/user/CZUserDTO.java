/**
 ***************************************************************************** 
* Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
******************************************************************************
 */ 
package com.ofss.digx.cz.bea.app.sms.dto.user;

import com.ofss.digx.app.sms.dto.user.UserDTO;
import com.ofss.fc.framework.domain.common.dto.DomainObjectDTO;

public class CZUserDTO extends DomainObjectDTO   {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8447925879606503381L;

	private String userExtendedType;
	
	private UserDTO userData;
	
	private Boolean isDeleteAllowed; 

	private String userChannelType;

	private String closeId;

	public String getUserExtendedType() {
		return userExtendedType;
	}

	public void setUserExtendedType(String userExtendedType) {
		this.userExtendedType = userExtendedType;
	}

	public UserDTO getUserData() {
		return userData;
	}

	public void setUserData(UserDTO userData) {
		this.userData = userData;
	}

	/**
	 * @return the isDeleteAllowed
	 */
	public Boolean getIsDeleteAllowed() {
		return isDeleteAllowed;
	}

	/**
	 * @param isDeleteAllowed the isDeleteAllowed to set
	 */
	public void setIsDeleteAllowed(Boolean isDeleteAllowed) {
		this.isDeleteAllowed = isDeleteAllowed;
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
}

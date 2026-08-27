/**
 ***************************************************************************** 
* Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
******************************************************************************
 */
package com.ofss.digx.cz.bea.domain.sms.entity.user.repository.adapter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

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
import com.ofss.digx.cz.bea.app.sms.dto.user.ResetPasswordUserRecordDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.ResetUserDataDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserDetailsUpdationDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserExtensionDataResponseDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.UserPartyListDTO;
import com.ofss.digx.cz.bea.app.hosttohost.adapter.IHthUserProfileAdapter;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData;
import com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionDataKey;
import com.ofss.digx.cz.bea.extxface.fmo.adapter.IFMOHelperCallAdapter;
import com.ofss.digx.datatype.complex.Party;
import com.ofss.digx.domain.approval.entity.usergroup.UserGroup;
import com.ofss.digx.enumeration.approval.ApprovalStatus;
import com.ofss.digx.enumeration.sms.user.LockStatus;
import com.ofss.digx.extxface.extxface.ExtxfaceAdapterFactory;
import com.ofss.digx.framework.domain.repository.adapter.AbstractLocalRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.framework.domain.transaction.Transaction;
import com.ofss.digx.framework.security.authentication.entity.TokenGenerationConstant;
import com.ofss.digx.infra.exceptions.Exception;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.datatype.Date;
import com.ofss.fc.enumeration.DeterminantType;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.infra.das.orm.Criteria;
import com.ofss.fc.infra.das.orm.DataAccessManager;
import com.ofss.fc.infra.das.orm.Expression;
import com.ofss.fc.infra.das.orm.Query;
import com.ofss.fc.infra.das.orm.Session;
import com.ofss.fc.infra.jdbc.ConnectionUtil;
import com.ofss.fc.infra.thread.ThreadAttribute;

public class LocalUserExtensionDataRepositoryAdapter extends AbstractLocalRepositoryAdapter<UserExtensionData>
		implements IUserExtensionDataRepositoryAdapter {

	/**
	 * Reference variable for {@code LocalUserExtensionDataRepositoryAdapter}
	 */
	private static LocalUserExtensionDataRepositoryAdapter singletonInstance;

	private static final Preferences preferences = ConfigurationFactory.getInstance()
			.getConfigurations(TokenGenerationConstant.AUTHENTICATION_CONFIGURATION);

	private static final int MAX_ATTEMPTS_ALLOWED = Integer.parseInt(preferences.get("MAX_NO_ATTEMPTS", "0"));

	private static final String DB_HOST = "fsgbu-mum-dbaas-31.snbomprdbaas1.gbucdsint02bom.oraclevcn.com";

	private static final String USERNAME = "OBDX_BEADEV";

	private static final String PASSWORD = "welcome123#BEA#";
	private static final String SUCCES_RESULT = "Migration Status sucussfully updated";
	private static final String FAILURE_RESULT = "Error occured while updating Migration Status";

	private static final String UPDATE_USERNAME = "{CALL UPDATE_DEFAULT_USER(?,?,?,?,?,?,?,?,?,?)}";
	private static final String HTH_ACTIVE_ACCESS_CAPABILITY_METHOD = "listActiveAccessCloseIds";

	/**
	 * Private Constructor of the Repository
	 */
	private LocalUserExtensionDataRepositoryAdapter() {

	}

	/**
	 * Returns unique instance of LocalUserExtensionDataRepositoryAdapter
	 * 
	 * @return LocalUserExtensionDataRepositoryAdapter
	 */
	public static LocalUserExtensionDataRepositoryAdapter getInstance() {
		if (singletonInstance == null) {
			synchronized (LocalResetPasswordRepositoryAdapter.class) {
				if (singletonInstance == null) {
					singletonInstance = new LocalUserExtensionDataRepositoryAdapter();
				}
			}
		}
		return singletonInstance;
	}

	@Override
	public void create(UserExtensionData object) throws Exception {
		String taskId = (String) ThreadAttribute.get(ThreadAttribute.CURRENT_TASK);
		BeaSystemOut.println("local repo call create" + taskId);
		SessionContext session = (SessionContext) ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT);
		IFMOHelperCallAdapter adapter = ExtxfaceAdapterFactory.getInstance().getAdapter(IFMOHelperCallAdapter.class,
				"callFMOHelper", DeterminantType.Enterprise);
		if (com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin") != null
				&& !(Boolean) com.ofss.digx.infra.thread.ThreadAttribute.get("isAdmin")) {
			adapter.callFMOHelper(session, taskId, object, true);
		}
		object.setBounceBackReminder("N");
		BeaSystemOut.println("Set bounce back reminder as N");

		BeaSystemOut.println("local repo call create");
		super.create(object);
		if ("HTH".equals(IHthUserProfileAdapter.normalizeUserChannelType(object.getUserChannelType()))) {
			hthUserProfileAdapter().createUserProfile(object.getCdcNo(), object.getUserID());
		}
	}

	@Override
	public void delete(UserExtensionData object) throws Exception {
		// TODO Auto-generated method stub
		super.delete(object);

	}

	@Override
	public UserExtensionData read(UserExtensionDataKey userExtensionDataKey) throws Exception {
		// TODO Auto-generated method stub
		Session session = null;
		UserExtensionData data = null;
		boolean isSessionOpen = false;
		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession("DIGX");

				isSessionOpen = true;
			}
			data = super.read(UserExtensionData.class, userExtensionDataKey);
		} catch (java.lang.Exception e) {
			BeaSystemOut.printErr(e);

		} finally {
			if (isSessionOpen) {

				DataAccessManager.getManager().closeSession(session);
			}
		}

		return data;
	}

	@Override
	public void update(UserExtensionData object) throws Exception {

		String taskId = (String) ThreadAttribute.get(ThreadAttribute.CURRENT_TASK);
		BeaSystemOut.println("Logout FMO Call - Task ID - " + taskId);
		com.ofss.digx.infra.thread.ThreadAttribute.set("FMO_USER_ID", object.getUserID());
		SessionContext sessionContext = (SessionContext) ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT);
		IFMOHelperCallAdapter adapter = ExtxfaceAdapterFactory.getInstance().getAdapter(IFMOHelperCallAdapter.class,
				"callFMOHelper", DeterminantType.Enterprise);
		if ("AU_CZ_LOGOUT".equalsIgnoreCase(taskId)) {
			adapter.callFMOHelper(sessionContext, taskId, object, true);
			BeaSystemOut.println("FMO Called for Logout");
		}

		Session session = null;
		boolean isSessionOpen = false;
		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession("DIGX");
				session.beginTransaction();
				isSessionOpen = true;
			}
			if (object.getBounceBackReminder() == null) {
				object.setBounceBackReminder("N");
				BeaSystemOut.println("Set bounce back reminder as N");
			}
			BeaSystemOut.println("\n Try block LocalUserExtensionDataRepositoryAdapter update() :: isSessionOpen : " + isSessionOpen);
			BeaSystemOut.println("\n Try block LocalUserExtensionDataRepositoryAdapter update() :: Calling super.update() : STARTS");
			super.update(object);
			BeaSystemOut.println("\n Try block LocalUserExtensionDataRepositoryAdapter update() :: Calling super.update() : ENDS");
		} catch (java.lang.Exception e) {
			BeaSystemOut.println("\n Catch block LocalUserExtensionDataRepositoryAdapter update()");
			DataAccessManager.getManager().rollbackTransaction();
			BeaSystemOut.printErr(e);
		} finally {
			BeaSystemOut.println("\n Finally block LocalUserExtensionDataRepositoryAdapter update() :: isSessionOpen : " + isSessionOpen);
			if (isSessionOpen) {
				session.fetchCurrentTransaction().commit();
				DataAccessManager.getManager().closeSession(session);
			}
		}
	}

	@Override
	public UserExtensionData getDetails(ResetPasswordDTO resetPasswordDTO) throws Exception {
		UserExtensionData userExtensionData = new UserExtensionData();
		HashMap<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("cdcNo", resetPasswordDTO.getCdcAcctNo());
		parameters.put("mobileNo", resetPasswordDTO.getMobileNo());
		parameters.put("isAP", true);

		List<UserExtensionData> userExtensionList = executeNamedQuery("GetExtensionDetails", parameters);

		for (UserExtensionData accountObj : userExtensionList) {

			userExtensionData.setLoginID(accountObj.getLoginID());

		}

		return userExtensionData;
	}

	@Override
	public UserExtensionData getUserDetails(ResetPasswordDTO resetPasswordDTO) throws Exception {
		UserExtensionData userExtensionData = new UserExtensionData();
		List<String> userID = resetPasswordDTO.getResetPasswordUserRecords().stream()
				.map(ResetPasswordUserRecordDTO::getUserId).collect(Collectors.toList());
		HashMap<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("cdcNo", resetPasswordDTO.getCdcAcctNo());
		parameters.put("userID", userID);

		List<UserExtensionData> userExtensionList = executeNamedQuery("GetUserExtensionDetails", parameters);
		if (userExtensionList.size() > 0
				&& userExtensionList.size() == resetPasswordDTO.getResetPasswordUserRecords().size()) {
			for (UserExtensionData accountObj : userExtensionList) {

				userExtensionData.setLoginID(accountObj.getLoginID());

			}
		}

		return userExtensionData;
	}

	@Override
	public UserExtensionData getSignerDetails(ResetPasswordDTO resetPasswordDTO) throws Exception {
		UserExtensionData userExtensionData = new UserExtensionData();
		// Load original signer into List
		List<String> originalSignerUsers = new ArrayList<String>();

		for (ResetPasswordUserRecordDTO item : resetPasswordDTO.getResetPasswordUserRecords()) {
			if (item.getUserType().equals("SIG")) {
				originalSignerUsers.add(item.getUserId());
			}
		}

		// Load original login users into List
		List<String> originalLoginUsers = new ArrayList<String>();
		for (ResetPasswordUserRecordDTO item : resetPasswordDTO.getResetPasswordUserRecords()) {
			if (item.getUserType().equals("LGN")) {
				originalLoginUsers.add(item.getUserId());
			}
		}

		HashMap<String, Object> signerParams = new HashMap<String, Object>();
		signerParams.put("userID", originalSignerUsers);
		List<UserExtensionData> signerExtensionList = new ArrayList<UserExtensionData>();

		if (!originalSignerUsers.isEmpty()) {
			signerExtensionList = executeNamedQuery("GetSignerExtensionDetails", signerParams);
		}

		HashMap<String, Object> loginParams = new HashMap<String, Object>();
		loginParams.put("userID", originalLoginUsers);
		List<UserExtensionData> loginExtensionList = new ArrayList<UserExtensionData>();

		if (!originalLoginUsers.isEmpty()) {
			loginExtensionList = executeNamedQuery("GetLoginExtensionDetails", loginParams);
		}

		// Load signer results from DB
		List<String> signerUserResultList = new ArrayList<String>();
		if (!signerExtensionList.isEmpty()) {
			for (UserExtensionData item : signerExtensionList) {
				signerUserResultList.add(item.getUserID());
			}
		}

		// Load signer results from DB
		List<String> loginUserResultList = new ArrayList<String>();
		if (!loginExtensionList.isEmpty()) {
			for (UserExtensionData item : loginExtensionList) {
				loginUserResultList.add(item.getUserID());
			}
		}

		if (!signerUserResultList.isEmpty() && signerUserResultList.size() == originalSignerUsers.size()) {
			userExtensionData.setSignerID("TRUE");
		} else {
			userExtensionData.setSignerID("FALSE");
		}

		if (!loginUserResultList.isEmpty() && loginUserResultList.size() == originalLoginUsers.size()) {
			userExtensionData.setLoginID("TRUE");
		} else {
			userExtensionData.setLoginID("FALSE");
		}

		return userExtensionData;
	}

	@Override
	public List<UserExtensionData> listUsers(UserDTO userDTO) throws Exception {
		List<UserExtensionData> userExtensionList = null;
		Session session = null;
		boolean isSessionOpen = false;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}

			Criteria criteria = session.createCriteria(UserExtensionData.class);

			if (userDTO.getPartyId() != null && userDTO.getPartyId().getValue() != null
					&& !userDTO.getPartyId().getValue().equals("")) {

				criteria.add(Expression.eq("cdcNo", userDTO.getPartyId().getValue()));
			}
			if (userDTO.getUsername() != null && !userDTO.getUsername().equals("")) {
				criteria.add(Expression.like("userID", "%" + userDTO.getUsername() + "%"));
				BeaSystemOut.println("Reading extentiondata for" + userDTO.getUsername());
			}

			if (userDTO.getHomeBusinessUnit() != null && !userDTO.getHomeBusinessUnit().equals("")) {
				criteria.add(Expression.eq("loginID", userDTO.getHomeBusinessUnit()));
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Reading extentiondata for loginID" + userDTO.getHomeBusinessUnit());
			}

			if (userDTO.getTargetUnit() != null && !userDTO.getTargetUnit().equals("")) {
				criteria.add(Expression.eq("signerID",userDTO.getTargetUnit()));
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Reading extentiondata for signerID" + userDTO.getTargetUnit());
			}
			userExtensionList = super.executeCriteria(criteria);

			String partyId = null;
			if (userDTO.getPartyId() != null && userDTO.getPartyId().getValue() != null
					&& !userDTO.getPartyId().getValue().equals("")) {
				partyId = userDTO.getPartyId().getValue();
			}
			Map<String, String> closeIdsByUserKey = closeIdsByUserKey(partyId);
			// Profile existence identifies an HTH user; effective account grants independently identify
			// whether access setup is complete. Keeping the two lookups separate prevents false setup
			// status when the enterprise channel exists but no account has been approved for the user.
			Set<String> activeHthAccessCloseIds = closeIdsByUserKey.isEmpty()
					? Collections.<String>emptySet()
					: listActiveAccessCloseIdsIfSupported(partyId);
			for (UserExtensionData userExtensionData : userExtensionList) {
				String userProfileKey = IHthUserProfileAdapter.userProfileKey(
						userExtensionData.getCdcNo(), userExtensionData.getUserID());
				boolean isHthUser = closeIdsByUserKey.containsKey(userProfileKey);
				userExtensionData.setUserChannelType(isHthUser ? "HTH" : "BCO");
				userExtensionData.setCloseId(isHthUser ? closeIdsByUserKey.get(userProfileKey) : null);
				userExtensionData.setHthAccessSetupDone(Boolean.valueOf(isHthUser
						&& activeHthAccessCloseIds.contains(userExtensionData.getCloseId())));
			}
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

		return userExtensionList;

	}

	private Map<String, String> closeIdsByUserKey(String partyId) throws Exception {
		return hthUserProfileAdapter().listCloseIdsByUserKey(partyId);
	}

	/**
	 * Reads effective HTH access from implementations that provide the BCOH2H-538 capability.
	 *
	 * <p>The method is deliberately discovered at runtime instead of being added to the shared
	 * {@link IHthUserProfileAdapter} contract. This preserves the original interface binary shape
	 * during rolling deployment. When an older host-to-host implementation is still active, the
	 * capability is absent and the UI conservatively receives {@code hthAccessSetupDone=false}.
	 * Failures from a present implementation are propagated and are not mistaken for an old JAR.
	 *
	 * @param partyId primary corporate party owning the HTH users
	 * @return distinct CloseIDs with active effective account grants, never {@code null}
	 * @throws Exception when the installed capability exists but cannot read effective access
	 */
	private Set<String> listActiveAccessCloseIdsIfSupported(String partyId) throws Exception {
		IHthUserProfileAdapter adapter = hthUserProfileAdapter();
		try {
			Method method = adapter.getClass().getMethod(HTH_ACTIVE_ACCESS_CAPABILITY_METHOD, String.class);
			Object result = method.invoke(adapter, partyId);
			if (result == null) {
				return Collections.emptySet();
			}
			if (!(result instanceof Set<?>)) {
				throw new Exception("HTH active-access capability returned an unsupported result type");
			}

			Set<String> closeIds = new LinkedHashSet<String>();
			for (Object closeId : (Set<?>) result) {
				if (closeId != null) {
					closeIds.add(String.valueOf(closeId));
				}
			}
			return closeIds;
		} catch (NoSuchMethodException e) {
			return Collections.emptySet();
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception) {
				throw (Exception) cause;
			}
			if (cause instanceof java.lang.Exception) {
				throw new Exception((java.lang.Exception) cause);
			}
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw new Exception(e);
		} catch (IllegalAccessException | IllegalArgumentException | SecurityException e) {
			throw new Exception(e);
		}
	}

	private IHthUserProfileAdapter hthUserProfileAdapter() throws Exception {
		return (IHthUserProfileAdapter) RepositoryAdapterFactory.getInstance().getRepositoryAdapter(
				IHthUserProfileAdapter.HTH_USER_PROFILE_LOCAL_REPOSITORY_ADAPTER);
	}

	//rkeshari 26/12/24 fix for PRD SR 3-39146654721
	@Override
	public List<UserExtensionData> listUsersForLogin(UserDTO userDTO) throws Exception {
		List<UserExtensionData> userExtensionList = null;
		Session session = null;
		boolean isSessionOpen = false;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}

			Criteria criteria = session.createCriteria(UserExtensionData.class);

			if (userDTO.getPartyId() != null && userDTO.getPartyId().getValue() != null
					&& !userDTO.getPartyId().getValue().equals("")) {

				criteria.add(Expression.eq("cdcNo", userDTO.getPartyId().getValue()));
			}
			
			if (userDTO.getUsername() != null && !userDTO.getUsername().equals("")) {
				criteria.add(Expression.eq("userID",userDTO.getUsername()));
				BeaSystemOut.println("LocalUserExtensionDataRepositoryAdapter.listUsers userDTO.getUsername is :"+userDTO.getUsername());
				BeaSystemOut.println("Reading extentiondata for" + userDTO.getUsername());
			}
   
			if (userDTO.getHomeBusinessUnit() != null && !userDTO.getHomeBusinessUnit().equals("")) {
				criteria.add(Expression.eq("loginID", userDTO.getHomeBusinessUnit()));
				BeaSystemOut.println("Reading extentiondata for loginID" + userDTO.getHomeBusinessUnit());
			}
			
			if (userDTO.getTargetUnit() != null && !userDTO.getTargetUnit().equals("")) {
				criteria.add(Expression.eq("signerID",userDTO.getTargetUnit()));
				BeaSystemOut.println("Reading extentiondata for signerID" + userDTO.getTargetUnit());
			}
			
			userExtensionList = super.executeCriteria(criteria);
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

		return userExtensionList;

	}


	@Override
	public List<ResetUserDataDTO> listResetPinUsers(UserDTO userDTO) throws Exception {
		List<ResetUserDataDTO> resetUserListDataDTO = new ArrayList<ResetUserDataDTO>();
		Session session = DataAccessManager.getManager().fetchCurrentSession();
		Query query = session.getNamedQuery("ListResetLoginPinUserByCDCId");
		query.setParameter("cdc_no", userDTO.getPartyId().getValue());

		List<Object[]> userListDTO = query.list();
		for (Object[] userObj : userListDTO) {
			ResetUserDataDTO userData = new ResetUserDataDTO();
			String userId = (String) userObj[0];
			userData.setUserID(userId);
			String pinId = (String) userObj[1];
			userData.setInternalUserID(pinId);
			String pinType = (String) userObj[2];
			userData.setPinMappingType(pinType);
			String fullName = (String) userObj[3];
			userData.setUserName(fullName);
			String pin_ref = (String) userObj[4];
			userData.setPinReferenceNumber(pin_ref);
			String pinMappingDate = userObj[5].toString().replace(" ", "T").split("\\.", 2)[0];
			userData.setPinMappingDate(pinMappingDate);
			String pinMappingCenter = (String) userObj[6];
			userData.setPinMappingCenter(pinMappingCenter);
			resetUserListDataDTO.add(userData);
		}

		Query query4 = session.getNamedQuery("ListResetLoginPinByCDCId");
		query4.setParameter("cdc_no", userDTO.getPartyId().getValue());

		List<Object[]> loginListDTO = query4.list();
		for (Object[] userObj : loginListDTO) {
			ResetUserDataDTO userData = new ResetUserDataDTO();
			// String userId = (String) userObj[0];
			userData.setUserID("");
			String pinId = (String) userObj[0];
			userData.setInternalUserID(pinId);
			String pinType = (String) userObj[1];
			userData.setPinMappingType(pinType);

			userData.setUserName("");
			String pin_ref = (String) userObj[2];
			userData.setPinReferenceNumber(pin_ref);
			String pinMappingDate = userObj[3].toString().replace(" ", "T").split("\\.", 2)[0];
			userData.setPinMappingDate(pinMappingDate);
			String pinMappingCenter = (String) userObj[4];
			userData.setPinMappingCenter(pinMappingCenter);
			resetUserListDataDTO.add(userData);
		}

		Query query2 = session.getNamedQuery("ListResetSignerPinUserByCDCId");
		query2.setParameter("cdc_no", userDTO.getPartyId().getValue());
		List<Object[]> signerListDTO = query2.list();

		for (Object[] userObj : signerListDTO) {
			ResetUserDataDTO userData = new ResetUserDataDTO();
			String userId = (String) userObj[0];
			userData.setUserID(userId);
			String pinId = (String) userObj[1];
			userData.setInternalUserID(pinId);
			String pinType = (String) userObj[2];
			userData.setPinMappingType(pinType);
			String fullName = (String) userObj[3];
			userData.setUserName(fullName);
			String pin_ref = (String) userObj[4];
			userData.setPinReferenceNumber(pin_ref);
			String pinMappingDate = userObj[5].toString().replace(" ", "T").split("\\.", 2)[0];
			userData.setPinMappingDate(pinMappingDate);
			String pinMappingCenter = (String) userObj[6];
			userData.setPinMappingCenter(pinMappingCenter);
			resetUserListDataDTO.add(userData);
		}

		Query query3 = session.getNamedQuery("ListResetPhonePinUserByCDCId");
		query3.setParameter("cdc_no", userDTO.getPartyId().getValue());
		List<Object[]> phoneUserListDTO = query3.list();
		for (Object[] userObj : phoneUserListDTO) {
			ResetUserDataDTO userData = new ResetUserDataDTO();
			String userId = (String) userObj[0];
			userData.setUserID(userId);
			String pinId = (String) userObj[1];
			userData.setInternalUserID(pinId);
			String pin_ref = (String) userObj[2];
			userData.setPinReferenceNumber(pin_ref);
			String pinType = (String) userObj[3];
			userData.setPinMappingType(pinType);
			String fullName = "";
			userData.setUserName(fullName);
			String pinMappingDate = userObj[4].toString().replace(" ", "T").split("\\.", 2)[0];
			userData.setPinMappingDate(pinMappingDate);
			String pinMappingCenter = (String) userObj[5];
			userData.setPinMappingCenter(pinMappingCenter);
			resetUserListDataDTO.add(userData);
		}

		return resetUserListDataDTO;
	}

	@Override
	public List<UserExtensionData> listUsersByParty(UserExtensionData userExtensionData) throws Exception {

		Session session = DataAccessManager.getManager().fetchCurrentSession();
		Criteria criteria = session.createCriteria(UserExtensionData.class);

		if (userExtensionData.getCdcNo() != null) {
			criteria.add(Expression.eq("cdcNo", userExtensionData.getCdcNo()));
		}

		return super.executeCriteria(criteria);
	}

	@Override
	public UserExtensionDataResponseDTO fetchCustInfo(UserExtensionData userExtensionData) throws Exception {
		return null;
	}

	@Override
	public List<CZUserDTO> listUsersData(UserPartyListDTO partyList) throws Exception {
		// TODO Auto-generated method stub
		List<CZUserDTO> usertListDTO = new ArrayList<CZUserDTO>();
		Session session = DataAccessManager.getManager().fetchCurrentSession();
		Query query = session.getNamedQuery("listUsersData");

		if (partyList.getParty().getValue() != null) {
			query.setParameter("partyId", "%" + partyList.getParty().getValue() + "%");
		} else {
			query.setParameter("partyId", "%%");
		}

		if (partyList.getEmailAddress() != null) {
			query.setParameter("emailID", "%" + partyList.getEmailAddress() + "%");
		} else {
			query.setParameter("emailID", "%%");
		}
		if (partyList.getFullName() != null) {
			query.setParameter("firstName", "%" + partyList.getFullName() + "%");
		} else {
			query.setParameter("firstName", "%%");
		}
		if (partyList.getUserName() != null) {
			
			if(partyList.getUserName().contains("_")) {
				query.setParameter("userName", "%\\" + partyList.getUserName() + "%");
			}else {
				query.setParameter("userName", "%" + partyList.getUserName() + "%");	
			}			
		} else {
			query.setParameter("userName", "%%");
		}
		if (partyList.getMobileNumber() != null) {
			query.setParameter("mobileNo", "%" + partyList.getMobileNumber() + "%");
		} else {
			query.setParameter("mobileNo", "%%");
		}
		
		BeaSystemOut.println("Printing data for getNamesPArams"+Arrays.toString(query.getNamedParameters()));

		for (String param : query.getNamedParameters()) {
			BeaSystemOut.println("Params-->"+param);
		}

		List<Object[]> userListDTO = query.list();
		
		if(userListDTO != null && userListDTO.size() > 0) {
			for (Object[] userObj : userListDTO) {
				CZUserDTO userData = new CZUserDTO();
				UserDTO userDetails = new UserDTO();
				String fullName = (String) userObj[0];
				userDetails.setFirstName(fullName);
				String userId = (String) userObj[1];
				userDetails.setUsername(userId);
				String deleteStatus = (String) userObj[2];
				userDetails.setDeleteStatus("Y".equalsIgnoreCase(deleteStatus));
				String lockStatus = (String) userObj[3];
				if ("N".equalsIgnoreCase(lockStatus)) {
					userDetails.setLockStatus(LockStatus.UNLOCK);
				} else {
					userDetails.setLockStatus(LockStatus.LOCK);
				}
				String is_AP = (String) userObj[4];

				String signerID = (String) userObj[5];

				if (is_AP != null && is_AP.equalsIgnoreCase("1"))
					userData.setUserExtendedType("AuthorisedPerson");
				else if (signerID != null)
					userData.setUserExtendedType("Signer");
				else
					userData.setUserExtendedType("");

				String cdcNo = (String) userObj[6];
				Party party = new Party(cdcNo);
				userDetails.setPartyId(party);

				String mobileNo = (String) userObj[7];
				userDetails.setMobileNumber(mobileNo);

				String email = (String) userObj[8];
				userDetails.setEmailId(email);
				userData.setUserChannelType((String) userObj[9]);
				userData.setCloseId((String) userObj[10]);

				userDetails.setUpdatable(true);
				userData.setUserData(userDetails);
				usertListDTO.add(userData);
			}
		}
		
		return usertListDTO;
	}

	public List<Transaction> listDuplicateTransactions(Transaction transaction) throws Exception {
		HashMap<String, Object> parameters = new HashMap<String, Object>();
		List<Transaction> transactionList = null;
		if (transaction != null && transaction.getEntityIdentifiers() != null) {
			parameters.put("approvalStatus", ApprovalStatus.PENDING_APPROVAL);
			parameters.put("transactionName", transaction.getTransactionName());
			transactionList = executeNamedQueryWithoutCacheUsage("GET_DUPLICATE_TXN", parameters);
		}
		return transactionList;
	}

	public void updateSigner(String userID, String partyID) {
		// TODO Auto-generated method stub

		Session session = null;
		boolean isSessionOpen = false;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}

			Query query = session.getNamedQuery("UpdateSignerHoldStatus");

			query.setParameter("partyId", partyID);
			query.setParameter("userID", userID);

			int result = query.executeUpdate();
			BeaSystemOut.println("Signer status updated");
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}
	}

	@Override
	public void updatePasswordExpiryDate(PasswordExpiryDTO passwordExpiryDTO) throws Exception {
		if ("LOGIN".equals(passwordExpiryDTO.getUserType())) {
			HashMap<String, Object> parameters = new HashMap<String, Object>();
			parameters.put("pwd_expiry_date",
					passwordExpiryDTO.getPwdExpiryDate().getDateString() + "000000.0Z".substring(6, 9));
			parameters.put("u_name", passwordExpiryDTO.getUserId());
			executeUpdateQuery("updatePasswordExpiryDate", parameters);
		} else if ("SIGNER".equals(passwordExpiryDTO.getUserType())) {
			UserExtensionData userExtensionData = new UserExtensionData();
			UserExtensionDataKey key = new UserExtensionDataKey();
			key.setUserExtensionKey(passwordExpiryDTO.getUserId());
			userExtensionData = read(key);
			userExtensionData.setSignPinExpiryDate(passwordExpiryDTO.getPwdExpiryDate());
			userExtensionData.update(userExtensionData);

		}

	}
	
	@Override
	public void updatePasswordExpiryDateAndForceChangePassword(PasswordExpiryDTO passwordExpiryDTO, boolean forceChangePassword) throws Exception {
		if ("LOGIN".equals(passwordExpiryDTO.getUserType())) {
			HashMap<String, Object> parameters = new HashMap<String, Object>();
			parameters.put("pwd_expiry_date",
					passwordExpiryDTO.getPwdExpiryDate().getDateString() + "000000.0Z".substring(6, 9));
			parameters.put("forceChangePassword", forceChangePassword);
			parameters.put("u_name", passwordExpiryDTO.getUserId());
			executeUpdateQuery("updatePasswordExpiryDateAndForceChangePassword", parameters);
		} 
	}

	@Override
	public List<com.ofss.digx.domain.approval.entity.usergroup.UserGroupUser> fetchUserGroupListByUsername(
			String username) throws Exception {

		HashMap<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("username", username);
		List<com.ofss.digx.domain.approval.entity.usergroup.UserGroupUser> userGroupUserList;

		Session session = null;
		boolean isSessionOpen = false;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}

			userGroupUserList = executeNamedQuery("fetchUserGroupListByUsername", parameters);
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

		return userGroupUserList;
	}

	@Override
	public List<UserGroup> fetchGroupIdByName(String groupName) throws Exception {
		HashMap<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("groupName", groupName);
		List<com.ofss.digx.domain.approval.entity.usergroup.UserGroup> UserGroupList;

		Session session = null;
		boolean isSessionOpen = false;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}

			UserGroupList = executeNamedQuery("fetchGroupIdByName", parameters);
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

		return UserGroupList;
	}

	@SuppressWarnings({ "javadoc", "unchecked" })
	public List<String> listSignerByPartyId(String partyId) throws Exception {

		Session session = DataAccessManager.getManager().fetchCurrentSession();
		Query query = session.getNamedQuery("ListSignerByPartyId");
		query.setParameter("partyId", partyId);
		List<String> signerUsersList = query.list();

		return signerUsersList;
	}
	
	@SuppressWarnings("unchecked")
	public List<String> listPrincipalByUsername(String username) throws Exception {
		HashMap<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("username", username);
		String query = "FetchUserPrincipals";
		return executeNamedQuery(query, parameters);
	}


	@Override
	public void deleteUserFromGroup(String username, String id) throws Exception {
		HashMap<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("username", username);
		parameters.put("id", id);
		Session session = null;
		boolean isSessionOpen = false;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				session.beginTransaction();
				isSessionOpen = true;
			}

			super.executeUpdateQuery("deleteUserFromGroup", parameters);
		} catch (java.lang.Exception e) {
			session.fetchCurrentTransaction().rollback();
		} finally {
			if (isSessionOpen) {
				session.fetchCurrentTransaction().commit();
				DataAccessManager.getManager().closeSession(session);
			}
		}
	}

	@Override
	public void updateUserLocale(String username, String cdcId, String userLocale) throws Exception {
		HashMap<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("username", username);
		parameters.put("userLocale", userLocale);
		parameters.put("cdcId", cdcId);
		executeUpdateQuery("updateUserLocale", parameters);

	}

	public void updateSignerStatusAndAttempts(String userID, String partyID, int attemptNumber) throws Exception {
		Session session = null;
		boolean isSessionOpen = false;
		String query = null;

		try {
			if (DataAccessManager.getManager().isSessionOpen()) {
				session = DataAccessManager.getManager().fetchCurrentSession();
			} else {
				session = DataAccessManager.getManager().openSession();
				isSessionOpen = true;
			}

			if (MAX_ATTEMPTS_ALLOWED == attemptNumber) {
				BeaSystemOut.println("Updating Signer status and attempts");
				query = "UpdateSignerHoldStatusAndAttempts";
			} else {
				BeaSystemOut.println("Updating Signer attempts");
				query = "UpdateSignerAttempts";
			}

			HashMap<String, Object> parameters = new HashMap<String, Object>();
			parameters.put("partyId", partyID);
			parameters.put("userID", userID);
			parameters.put("attemptNumber", attemptNumber);
			executeUpdateQuery(query, parameters);

			BeaSystemOut.println("Signer update done");
		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}
	}

	@Override
	public void updateUserExtensionData(UserExtensionData object) throws Exception {
		Session session = DataAccessManager.getManager().fetchCurrentSession();
		if (object.getBounceBackReminder() == null) {
			object.setBounceBackReminder("N");
			BeaSystemOut.println("Set bounce back reminder as N");
		}
		super.update(object);
		session.flush();
		session.fetchCurrentTransaction().commit();
		DataAccessManager.getManager().fetchCurrentSession().refresh(object);

	}

	@Override
	public Date getPreLastLogin(String userId) throws Exception {
		// TODO Auto-generated method stub
		Date preLastLoginDate = null;
		String username = new String();
		String date = null;

		Session session = DataAccessManager.getManager().fetchCurrentSession();
		Query query = session.getNamedQuery("preLastLoginDeteForUserId");
		query.setParameter("userId", userId);

		List<Object[]> result = query.list();
		for (Object[] userObj : result) {
			if (userObj[1] != null) {

				BeaSystemOut.println("username::" + (String) userObj[0] + " preLastLoginDate format from sql ::"
						+ userObj[1].toString());
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut
						.println("preLastLoginDate format from after split ::" + userObj[1].toString().split("\\.")[0]);
				username = (String) userObj[0];
				date = (String) userObj[1].toString().split("\\.")[0];
				try {
					preLastLoginDate = new Date(date, "yyyy-MM-dd HH:mm:ss");
					BeaSystemOut.println("after conversion logged in date is " + preLastLoginDate);
					BeaSystemOut.println("preLastLoginDate fcdate format ::" + preLastLoginDate.toString());
				} catch (java.lang.Exception e) {
					BeaSystemOut.printErr(e);
				}
			}
		}

		return preLastLoginDate;
	}

	/**
	 * Read String form BEA response and convert monetary values to
	 * com.ofss.fc.datatype.Date
	 * 
	 * @param val
	 * @param pattern (yyyyMMddHHmmss / yyyyMMdd / HHmmss)
	 * @return
	 */
	public static com.ofss.fc.datatype.Date beaResponseStringToFcDate(String val, String pattern) {
		if (val == null || val.trim().length() == 0) {
			return null;
		}

		java.util.Date javaDate = beaResponseStringToJavaDate(val, pattern);
		com.ofss.fc.datatype.Date fcDate = new com.ofss.fc.datatype.Date(javaDate);
		return fcDate;
	}

	/**
	 * Read String form BEA response and convert monetary values to java.util.Date
	 * 
	 * @param val
	 * @param pattern (yyyyMMddHHmmss / yyyyMMdd / HHmmss)
	 * @return
	 */
	public static java.util.Date beaResponseStringToJavaDate(String val, String pattern) {

		java.util.Date opDate = null;
		BeaSystemOut.println("Input Params : val = " + val + ", pattern = " + pattern);
		if (val == null || val.trim().length() == 0) {
			return null;
		}

		SimpleDateFormat sdf = new SimpleDateFormat(pattern);

		try {
			opDate = sdf.parse(val);
		} catch (ParseException e) {
			BeaSystemOut.printErr(e);
		}

		return opDate;
	}

	/*
	 * public Onboarding fetchValidUserToken(Onboarding domain) { // TODO
	 * Auto-generated method stub boolean isSessionOpen = false; Session
	 * session=null; List <Onboarding> userList =null; try { if
	 * (DataAccessManager.getManager().isSessionOpen()) { session =
	 * DataAccessManager.getManager().fetchCurrentSession(); } else { session =
	 * DataAccessManager.getManager().openSession("DIGX");
	 * 
	 * isSessionOpen = true; } session =
	 * DataAccessManager.getManager().fetchCurrentSession(); Criteria
	 * criteria=session.createCriteria(Onboarding.class);
	 * criteria.add(Expression.eq("userID", domain.getUserID()));
	 * criteria.add(Expression.eq("cdcNo", domain.getCdcNo()));
	 * criteria.add(Expression.eq("tokenStatus", domain.getTokenStatus())); userList
	 * = super.executeCriteria(criteria);
	 * 
	 * 
	 * } catch (java.lang.Exception e) { com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
	 * 
	 * } finally { if (isSessionOpen) {
	 * 
	 * DataAccessManager.getManager().closeSession(session); } } if (userList!=null
	 * && userList.size()>0) { return userList.get(0); // User and CDC ID is valid
	 * }else { return null; } }
	 * 
	 * 
	 * 
	 * public Onboarding updateTokenStatus(Onboarding domain) throws Exception {
	 * boolean isSessionOpen = false; Session session=null; try { if
	 * (DataAccessManager.getManager().isSessionOpen()) { session =
	 * DataAccessManager.getManager().fetchCurrentSession();
	 * 
	 * } else { session = DataAccessManager.getManager().openSession("DIGX");
	 * isSessionOpen = true; } session.beginTransaction(); session.update(domain);
	 * DataAccessManager.getManager().commitTransaction(); session.flush(); return
	 * domain; } catch (java.lang.Exception e) {
	 * DataAccessManager.getManager().rollbackTransaction(); com.ofss.digx.cz.bea.app.logger.BeaSystemOut.printErr(e);
	 * 
	 * } finally { if (isSessionOpen) {
	 * DataAccessManager.getManager().closeSession(session); } } return null; }
	 */

	@Override
	public void updateDefaultUser(UserDetailsUpdationDTO userDetailsUpdationDTO, String userName) throws Exception {
		BeaSystemOut.println("Reached updateDefaultUser in LocalUserExtensionDataRepositoryAdapter");
		Connection con = null;
		BeaSystemOut.println("New Username: " + userDetailsUpdationDTO.getUserId());
		BeaSystemOut.println("Old Username: " + userName);
		BeaSystemOut.println(userDetailsUpdationDTO.toString());
		
		try {
			BeaSystemOut.println("Inside try block");
			if (userName != null) {
				con = ConnectionUtil.getConnection("DIGX", true, null);
				CallableStatement st = con.prepareCall(UPDATE_USERNAME);
				int isDefaultUserInt = userDetailsUpdationDTO.getIsDefaultUser() ? 1 : 0;
				int isUserInfoUpdateRequiredInt = userDetailsUpdationDTO.getIsUserInfoUpdateRequired() ? 1 : 0;

				// Set parameters using integer values
				
					st.setString(1, userDetailsUpdationDTO.getUserId());
					st.setString(2, userName);
					st.setString(3, userDetailsUpdationDTO.getDocumentType());
					st.setString(4, userDetailsUpdationDTO.getDocumentCountry());
					st.setString(5, userDetailsUpdationDTO.getDocumentID());
					st.setString(6, userDetailsUpdationDTO.getMobileNoCode());
					st.setString(7, userDetailsUpdationDTO.getMobileNo());
					st.setString(8, userDetailsUpdationDTO.getEmail());
					st.setInt(9, isDefaultUserInt);
					st.setInt(10, isUserInfoUpdateRequiredInt); 
//					st.setBoolean(9, userDetailsUpdationDTO.getIsDefaultUser());
//					st.setBoolean(10, userDetailsUpdationDTO.getIsUserInfoUpdateRequired());
			

//				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("New Username from CallableStatement: " + st.getString(1));
//				com.ofss.digx.cz.bea.app.logger.BeaSystemOut.println("Old Username from CallableStatement: " + st.getString(2));
				st.execute();
				st.close();
				st = null;
			}
			BeaSystemOut.println("Exiting try block");
		} catch (java.lang.Exception e) {
			BeaSystemOut.printErr(e);
		} finally {
			if (con != null) {
				ConnectionUtil.closeConnection("DIGX", con);
				con = null;
				BeaSystemOut.println("Connection closed from finally block");
			}
			con = null;
		}
		BeaSystemOut.println("Exiting updateDefaultUser in LocalUserExtensionDataRepositoryAdapter ");
	}

	@Override
	public void updateMigrationStatus(MigrationStatusRequestDto migrationStatusRequestDto) throws Exception {
		BeaSystemOut.println("LocalUserExtensionDataRepositoryAdapter.updateMigrationStatus() starts here");
		Session session = null;
		boolean isSessionOpen = false;

		try {

			if (DataAccessManager.getManager().isSessionOpen()) {
				BeaSystemOut.println("LocalUserExtensionDataRepositoryAdapter.updateMigrationStatus() entered in if");
				session = DataAccessManager.getManager().fetchCurrentSession();
				BeaSystemOut.println("LocalUserExtensionDataRepositoryAdapter.updateMigrationStatus() in if session is::"
						+ session.toString());
			} else {
				BeaSystemOut.println("LocalUserExtensionDataRepositoryAdapter.updateMigrationStatus() entered in else");
				session = DataAccessManager.getManager().openSession();
				com.ofss.digx.cz.bea.app.logger.BeaSystemOut
						.println("LocalUserExtensionDataRepositoryAdapter.updateMigrationStatus() in else session is::"
								+ session.toString());
				isSessionOpen = true;
			}

			Query query = session.getNamedQuery("UpdateMigrationStatus");
			BeaSystemOut.println("LocalUserExtensionDataRepositoryAdapter.updateMigrationStatus() userId is:"
					+ migrationStatusRequestDto.getUsrId());
			
			com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData domain = new com.ofss.digx.cz.bea.domain.sms.entity.user.UserExtensionData();
			UserExtensionDataKey key = new UserExtensionDataKey();
			key.setUserExtensionKey(migrationStatusRequestDto.getDecryptedUserId());
			domain = domain.read(key);
			
			query.setParameter("userId", migrationStatusRequestDto.getDecryptedUserId());
			if(domain.getMigtationStatus() != null && domain.getMigtationStatus().equals("N")
					&& domain.getSignerID() != null) {
				BeaSystemOut.println("Migration status set as S in new flow from LocalUserExtensionDataRepositoryAdapter");
				
				if(domain.getMigSignerUpdated() != null && domain.getMigSignerUpdated().equals("Y")) {
					BeaSystemOut.println("getMigSignerUpdated is Y");
					query.setParameter("migrationStatus", "Y");	
				} else {
					BeaSystemOut.println("getMigSignerUpdated is null");
					query.setParameter("migrationStatus", "S");	
				}
			}
			else {
				query.setParameter("migrationStatus", "Y");
				BeaSystemOut.println("Migration status set as Y in new flow from LocalUserExtensionDataRepositoryAdapter");
			}

			int result = query.executeUpdate();

			BeaSystemOut.println("Migration status updated query result is::" + result);

		} catch (java.lang.Exception e) {

			BeaSystemOut.printErr(e);

		} finally {
			if (isSessionOpen) {
				DataAccessManager.getManager().closeSession(session);
			}
		}

	}

	@Override
	public MigrationStatusResponseDto updateHostMigrationStatus(MigrationStatusRequestDto migrationStatusRequestDto) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CreateLoginUserResponseDto migrateLoginUser(CreateLoginUserRequestDto createLoginUserRequestDto)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AuditLogMigRequestDTO downloadCcbAuditLog(String partyId, String auditType) throws Exception {
		AuditLogMigRequestDTO request = null;
		Query query = null;
		BeaSystemOut.println("AuditType = "+ auditType);
		Session session = DataAccessManager.getManager().fetchCurrentSession();
		if(auditType != null && auditType.equalsIgnoreCase("TRANSACTION")) {
			query = session.getNamedQuery("getFileContentCCBAudit");
		}
		else if (auditType != null && auditType.equalsIgnoreCase("SYSTEM")) {
			query = session.getNamedQuery("getFileContentCCBSystemAudit");
		}
		query.setParameter("partyId", partyId);

		BeaSystemOut.println("Entered in Local User Ext for Audit");

		List<Object[]> result = query.list();
		for (Object[] auditContent : result) {
			if (auditContent[0] != null) {
				request = new AuditLogMigRequestDTO();
				BeaSystemOut.println("Response from Audit Log : " + auditContent[0].toString());
				request.setFileName(auditContent[0].toString());
				request.setFileContent((byte[]) auditContent[1]);
			}
		}

		return request;
	}
	
	@Override
	public List<UserExtensionData> listNonDeletedUsersByParty(UserExtensionData userExtensionData) throws Exception {
		List<UserExtensionData> listUserExtensionData = new ArrayList<UserExtensionData>();
		HashMap<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("cdcNo", userExtensionData.getCdcNo());
		parameters.put("deleteStatus", false);
		listUserExtensionData = executeNamedQuery("listNonDeletedUsersByParty", parameters);
		return listUserExtensionData;
	}
	
	@Override
	public List<UserExtensionData> listUserExtensionData(String userId) throws Exception {
		HashMap<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("userID", userId);
		String query = "ListUserExtensionData";
		return executeNamedQuery(query, parameters);
	}

	public String fetchSelfTransferCompanyName(String accountId, String partyID) {
		// TODO Auto-generated method stub
		
		AuditLogMigRequestDTO request = null;
		Query query = null;
		BeaSystemOut.println("accountId "+ accountId);
		Session session = DataAccessManager.getManager().fetchCurrentSession();
		
		query = session.getNamedQuery("getPartyIDForAccount");
		
		query.setParameter("accountId", accountId);
		query.setParameter("partyId", partyID);

		
		String relatedPartyID=null;
		String companyName=null;
		
		List<String> result = query.list();
		for (String customerContent : result) {
			if (customerContent != null) {
				relatedPartyID=customerContent;
			}
		}
		
		if (relatedPartyID!=null && relatedPartyID.equalsIgnoreCase(partyID)) {
			
			query = session.getNamedQuery("getPartyNameInformation");
			
			
			query.setParameter("partyId", partyID);

			
		}else {
			
			query = session.getNamedQuery("getRelatedPartyInformation");
			
			
			query.setParameter("partyId", partyID);
			query.setParameter("relatedPartyId", relatedPartyID);

		}
		

		
		List<String> customerDataresult = query.list();
		for (String customerContent : customerDataresult) {
			if (customerContent != null) {
				companyName=customerContent;
			}
		}
		return companyName;
	}

	
	public String readTimerJvmID(String id){
		String jvmID = new String();
		Query query = null;
		Boolean isSessionOpen = null;
		BeaSystemOut.println("Inside readTimerJvmID localAdapter");
		BeaSystemOut.println("TimerID: "+ id);
		Session session = null;
		if (DataAccessManager.getManager().isSessionOpen()) {
			BeaSystemOut.println("LocalUserExtensionDataRepositoryAdapter.readTimerJvmID() entered in if");
			session = DataAccessManager.getManager().fetchCurrentSession();
			BeaSystemOut.println("LocalUserExtensionDataRepositoryAdapter.readTimerJvmID() in if session is::"
					+ session.toString());
		} else {
			BeaSystemOut.println("LocalUserExtensionDataRepositoryAdapter.readTimerJvmID() entered in else");
			session = DataAccessManager.getManager().openSession();
			com.ofss.digx.cz.bea.app.logger.BeaSystemOut
					.println("LocalUserExtensionDataRepositoryAdapter.readTimerJvmID() in else session is::"
							+ session.toString());
			isSessionOpen = true;
		}
		query = session.getNamedQuery("readTimerJvmID");
		
		query.setParameter("id", id);
		
		List<String> jvmIDList = query.list();
		if(jvmIDList!=null) {
			jvmID = jvmIDList.get(0);
		}
		
		if (isSessionOpen) {
			DataAccessManager.getManager().closeSession(session);
		}
		return jvmID;
	}

	@Override
	public List<UserExtensionData> listUsersBypassCode() throws Exception {
		Session session = DataAccessManager.getManager().fetchCurrentSession();
		Criteria criteria = session.createCriteria(UserExtensionData.class);
		criteria.add(Expression.eq("securityQuestionsBypass", "Y"));
		criteria.add(Expression.isNotNull("bypassExpiryTime"));
		return super.executeCriteria(criteria);
	}

}

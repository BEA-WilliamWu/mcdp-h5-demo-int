package com.ofss.digx.cz.bea.app.hosttohost.adapter;

import java.util.Map;

import com.ofss.digx.infra.exceptions.Exception;

/**
 * Provides the HTH user-profile data required by user listing and access maintenance.
 *
 * <p>A profile relates a corporate party and CloseID to the OBDX user key. Access-setup state is
 * derived separately from active effective account grants; profile existence alone does not mean
 * that account access has been configured.
 */
public interface IHthUserProfileAdapter {
	String HTH_USER_PROFILE_LOCAL_REPOSITORY_ADAPTER =
			"HTH_USER_PROFILE_LOCAL_REPOSITORY_ADAPTER";

	void createUserProfile(String partyId, String closeId) throws Exception;

	Map<String, String> listCloseIdsByUserKey(String partyId) throws Exception;

	/**
	 * Builds a collision-safe lookup key from party ID and CloseID.
	 *
	 * <p>Length prefixes prevent ambiguous concatenations such as {@code AB+C} and {@code A+BC}.
	 */
	static String userProfileKey(String partyId, String closeId) {
		return (partyId == null ? "-1:" : partyId.length() + ":" + partyId)
				+ (closeId == null ? "-1:" : closeId.length() + ":" + closeId);
	}

	/** Defaults missing or unsupported channel values to the legacy BCO flow. */
	static String normalizeUserChannelType(String userChannelType) {
		return userChannelType != null && "HTH".equalsIgnoreCase(userChannelType.trim()) ? "HTH" : "BCO";
	}
}

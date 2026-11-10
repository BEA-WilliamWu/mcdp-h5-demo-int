package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter;

import java.util.List;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserProfile;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserProfileKey;
import com.ofss.digx.framework.domain.repository.IRepositoryAdapter;
import com.ofss.digx.infra.exceptions.Exception;

public interface IHthUserProfileRepositoryAdapter
		extends IRepositoryAdapter<HthUserProfile, HthUserProfileKey> {
	String HTH_USER_PROFILE_LOCAL_REPOSITORY_ADAPTER =
			"HTH_USER_PROFILE_LOCAL_REPOSITORY_ADAPTER";

	List<HthUserProfile> listByPartyId(String partyId) throws Exception;
}

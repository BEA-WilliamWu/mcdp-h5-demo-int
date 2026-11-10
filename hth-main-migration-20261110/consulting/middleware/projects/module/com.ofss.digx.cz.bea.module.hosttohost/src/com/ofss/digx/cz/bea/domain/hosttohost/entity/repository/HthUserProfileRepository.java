package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import java.util.List;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserProfile;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthUserProfileKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthUserProfileRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.AbstractDomainObjectRepository;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.infra.exceptions.Exception;

public class HthUserProfileRepository
		extends AbstractDomainObjectRepository<HthUserProfile, HthUserProfileKey> {

	public static HthUserProfileRepository getInstance() {
		return SingletonHolder.INSTANCE;
	}

	@Override
	public HthUserProfile read(HthUserProfileKey key) throws Exception {
		return repositoryAdapter().read(key);
	}

	@Override
	public void create(HthUserProfile object) throws Exception {
		repositoryAdapter().create(object);
	}

	@Override
	public void update(HthUserProfile object) throws Exception {
		repositoryAdapter().update(object);
	}

	@Override
	public void delete(HthUserProfile object) throws Exception {
		repositoryAdapter().delete(object);
	}

	public List<HthUserProfile> listByPartyId(String partyId) throws Exception {
		return repositoryAdapter().listByPartyId(partyId);
	}

	private IHthUserProfileRepositoryAdapter repositoryAdapter() throws Exception {
		return (IHthUserProfileRepositoryAdapter) RepositoryAdapterFactory.getInstance()
				.getRepositoryAdapter(
						IHthUserProfileRepositoryAdapter.HTH_USER_PROFILE_LOCAL_REPOSITORY_ADAPTER);
	}

	private static final class SingletonHolder {
		private static final HthUserProfileRepository INSTANCE = new HthUserProfileRepository();
	}
}

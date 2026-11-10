package com.ofss.digx.cz.bea.domain.hosttohost.entity.repository;

import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthRequest;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.HthRequestKey;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.IHthRequestRepositoryAdapter;
import com.ofss.digx.framework.domain.repository.AbstractDomainObjectRepository;
import com.ofss.digx.framework.domain.repository.RepositoryAdapterFactory;
import com.ofss.digx.infra.exceptions.Exception;
import java.lang.Override;

public class HthRequestRepository extends AbstractDomainObjectRepository<HthRequest, HthRequestKey> {

	public static HthRequestRepository getInstance() {
		return SingletonHolder._INSTANCE;
	}

	@Override
	public HthRequest read(HthRequestKey key) throws Exception {
		return repositoryAdapter().read(key);
	}

	public HthRequest findActiveByTransactionId(String transactionId) throws Exception {
		return repositoryAdapter().findActiveByTransactionId(transactionId);
	}

	public boolean existsPendingApproval(String partyId) throws Exception {
		return repositoryAdapter().existsPendingApproval(partyId);
	}

	public boolean existsPendingApprovalByUamClientId(String uamClientId) throws Exception {
		return repositoryAdapter().existsPendingApprovalByUamClientId(uamClientId);
	}

	@Override
	public void create(HthRequest object) throws Exception {
		repositoryAdapter().create(object);
	}

	@Override
	public void update(HthRequest object) throws Exception {
		repositoryAdapter().update(object);
	}

	@Override
	public void delete(HthRequest object) throws Exception {
		repositoryAdapter().delete(object);
	}

	private IHthRequestRepositoryAdapter repositoryAdapter() throws Exception {
		return (IHthRequestRepositoryAdapter) RepositoryAdapterFactory.getInstance()
				.getRepositoryAdapter(IHthRequestRepositoryAdapter.HTH_REQUEST_LOCAL_REPOSITORY_ADAPTER);
	}

	private static final class SingletonHolder {
		private static final HthRequestRepository _INSTANCE = new HthRequestRepository();
	}
}

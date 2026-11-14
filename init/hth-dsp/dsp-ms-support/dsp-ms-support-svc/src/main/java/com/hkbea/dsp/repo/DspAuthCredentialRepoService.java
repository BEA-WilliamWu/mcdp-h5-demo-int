package com.hkbea.dsp.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hkbea.common.exception.ServiceException;
import com.hkbea.dsp.entity.DspAuthCredential;
import com.hkbea.dsp.exception.DspSupportErrorMapping;
import com.hkbea.dsp.mapper.DspAuthCredentialMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class DspAuthCredentialRepoService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DspAuthCredentialRepoService.class);

    public static final String AUTH_STATUS_ACTIVE = "A";

    @Autowired
    private DspAuthCredentialMapper dspAuthCredentialMapper;

    public DspAuthCredential queryWithStatus(String closeId, String clientId, String status) {
        LambdaQueryWrapper<DspAuthCredential> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(DspAuthCredential::getClientId, clientId)
                .eq(DspAuthCredential::getObjectStatus, status)
                .eq(DspAuthCredential::getCloseId, closeId);

        return dspAuthCredentialMapper.selectOne(queryWrapper);
    }

    public boolean existsForClientAndCloseId(String closeId, String clientId) {
        LambdaQueryWrapper<DspAuthCredential> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(DspAuthCredential::getClientId, clientId)
                .eq(DspAuthCredential::getCloseId, closeId);
        return dspAuthCredentialMapper.selectCount(queryWrapper) > 0;
    }

    public void insert(DspAuthCredential dspAuthCredential) {
        int row = dspAuthCredentialMapper.insert(dspAuthCredential);
        if (row != 1) {
            LOGGER.error("Insert dsp auth credential affected abnormal rows. row={}, clientId={}, closeId={}",
                    row, dspAuthCredential.getClientId(), dspAuthCredential.getCloseId());
            throw new ServiceException(DspSupportErrorMapping.DATABASE_PERSIST_UNNORMAL_ROW_EXCEPTION);
        }
    }

    public void updatePassword(DspAuthCredential dspAuthCredential) {
//        LambdaUpdateWrapper<DspAuthCredential> wrapper = Wrappers.lambdaUpdate(DspAuthCredential.class);
//        wrapper.eq(DspAuthCredential::getClientId, dspAuthCredential.getClientId())
//                .eq(DspAuthCredential::getObjectStatus, dspAuthCredential.getObjectStatus())
//                .eq(DspAuthCredential::getCloseId, dspAuthCredential.getCloseId())
//                .set(DspAuthCredential::getPasswordHash, dspAuthCredential.getPasswordHash())
//                .set(DspAuthCredential::getLastUpdatedBy, dspAuthCredential.getLastUpdatedBy())
//                .set(DspAuthCredential::getLastUpdateDate, dspAuthCredential.getLastUpdateDate());
//        int row = dspAuthCredentialMapper.update(null, wrapper);
        int row = dspAuthCredentialMapper.updatePassword(dspAuthCredential);
        if (row != 1) {
            LOGGER.error("Update dsp auth credential affected abnormal rows. row={}, clientId={}, closeId={}",
                    row, dspAuthCredential.getClientId(), dspAuthCredential.getCloseId());
            throw new ServiceException(DspSupportErrorMapping.DATABASE_PERSIST_UNNORMAL_ROW_EXCEPTION);
        }
    }
}

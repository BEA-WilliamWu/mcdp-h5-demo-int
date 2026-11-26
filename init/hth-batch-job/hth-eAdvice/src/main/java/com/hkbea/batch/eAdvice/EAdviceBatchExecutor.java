package com.hkbea.batch.eAdvice;

import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * E-Advice Batch Executor for running individual eAdvice generation tasks:
 * - GenEAdvice2EBK1 (type 1)
 * - GenEAdvice2EBK2 (type 2)
 * - GenEAdvice2EBK3 (type 3)
 * - GenPaperAdvice2EBK (type P / p)
 */
public class EAdviceBatchExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(EAdviceBatchExecutor.class);

    private final GenEAdvice2EBK1 genEAdvice2EBK1 = new GenEAdvice2EBK1();
    private final GenEAdvice2EBK2 genEAdvice2EBK2 = new GenEAdvice2EBK2();
    private final GenEAdvice2EBK3 genEAdvice2EBK3 = new GenEAdvice2EBK3();
    private final GenPaperAdvice2EBK genPaperAdvice2EBK = new GenPaperAdvice2EBK();

    public void executeGenEAdvice2EBK1(String[] args) throws SQLException {
        LOG.info("HTH_EADVICE task=GenEAdvice2EBK1 delegating to GenEAdvice2EBK1");
        genEAdvice2EBK1.execute(args);
    }

    public void executeGenEAdvice2EBK2(String[] args) throws SQLException {
        LOG.info("HTH_EADVICE task=GenEAdvice2EBK2 delegating to GenEAdvice2EBK2");
        genEAdvice2EBK2.execute(args);
    }

    public void executeGenEAdvice2EBK3(String[] args) throws SQLException {
        LOG.info("HTH_EADVICE task=GenEAdvice2EBK3 delegating to GenEAdvice2EBK3");
        genEAdvice2EBK3.execute(args);
    }

    public void executeGenPaperAdvice2EBK(String[] args) throws SQLException {
        LOG.info("HTH_EADVICE task=GenPaperAdvice2EBK delegating to GenPaperAdvice2EBK");
        genPaperAdvice2EBK.execute(args);
    }
}

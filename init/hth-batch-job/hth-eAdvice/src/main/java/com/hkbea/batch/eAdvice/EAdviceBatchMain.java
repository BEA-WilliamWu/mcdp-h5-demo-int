package com.hkbea.batch.eAdvice;

import com.hkbea.batch.eAdvice.util.BatchConfig;
import com.hkbea.batch.eAdvice.util.DBUtil;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * E-Advice batch launcher.
 * Supports task types:
 *  - 1 or GEN_EADVICE_2EBK1 -> GenEAdvice2EBK1
 *  - 2 or GEN_EADVICE_2EBK2 -> GenEAdvice2EBK2
 *  - 3 or GEN_EADVICE_2EBK3 -> GenEAdvice2EBK3
 *  - P / p or GEN_PAPER_ADVICE_2EBK -> GenPaperAdvice2EBK
 */
public final class EAdviceBatchMain {
    private static final Logger LOG = LoggerFactory.getLogger(EAdviceBatchMain.class);
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Hong_Kong");

    public static final String BATCH_TYPE_GEN_EADVICE_2EBK1 = "GEN_EADVICE_2EBK1";
    public static final String BATCH_TYPE_GEN_EADVICE_2EBK2 = "GEN_EADVICE_2EBK2";
    public static final String BATCH_TYPE_GEN_EADVICE_2EBK3 = "GEN_EADVICE_2EBK3";
    public static final String BATCH_TYPE_GEN_PAPER_ADVICE_2EBK = "GEN_PAPER_ADVICE_2EBK";

    private static final Map<String, String> ALIAS_MAP = Map.of(
        "1", BATCH_TYPE_GEN_EADVICE_2EBK1,
        "GEN_EADVICE_2EBK1", BATCH_TYPE_GEN_EADVICE_2EBK1,
        "2", BATCH_TYPE_GEN_EADVICE_2EBK2,
        "GEN_EADVICE_2EBK2", BATCH_TYPE_GEN_EADVICE_2EBK2,
        "3", BATCH_TYPE_GEN_EADVICE_2EBK3,
        "GEN_EADVICE_2EBK3", BATCH_TYPE_GEN_EADVICE_2EBK3,
        "P", BATCH_TYPE_GEN_PAPER_ADVICE_2EBK,
        "GEN_PAPER_ADVICE_2EBK", BATCH_TYPE_GEN_PAPER_ADVICE_2EBK
    );

    private EAdviceBatchMain() { }

    public static void main(String[] args) {
        System.exit(run(args, Clock.system(BUSINESS_ZONE)));
    }

    static int run(String[] args, Clock clock) {
        String rawType = args.length == 0 ? "" : args[0].trim().toUpperCase(Locale.ROOT);
        String resolvedType = ALIAS_MAP.get(rawType);

        if (resolvedType == null) {
            LOG.error("Usage: EAdviceBatch <1|2|3|P|GEN_EADVICE_2EBK1|GEN_EADVICE_2EBK2|GEN_EADVICE_2EBK3|GEN_PAPER_ADVICE_2EBK> [args...]");
            return 2;
        }

        String stage = "CONFIG";
        String started = ZonedDateTime.now(clock).toString();
        boolean connectionInitialized = false;

        try {
            BatchConfig config = BatchConfig.getInstance();
            EAdviceBatchExecutor executor = new EAdviceBatchExecutor();

            stage = "INIT_DB";
            DBUtil.init(config);
            connectionInitialized = true;

            stage = "EXECUTE";
            LOG.info("HTH_EADVICE stage=START type={} time={}", resolvedType, started);

            switch (resolvedType) {
                case BATCH_TYPE_GEN_EADVICE_2EBK1 -> executor.executeGenEAdvice2EBK1(args);
                case BATCH_TYPE_GEN_EADVICE_2EBK2 -> executor.executeGenEAdvice2EBK2(args);
                case BATCH_TYPE_GEN_EADVICE_2EBK3 -> executor.executeGenEAdvice2EBK3(args);
                case BATCH_TYPE_GEN_PAPER_ADVICE_2EBK -> executor.executeGenPaperAdvice2EBK(args);
                default -> throw new IllegalStateException("Unexpected batch type: " + resolvedType);
            }

            LOG.info("HTH_EADVICE stage=END status=SUCCESS type={} start={} end={}",
                resolvedType, started, ZonedDateTime.now(clock));
            return 0;
        } catch (Exception e) {
            LOG.error("HTH_EADVICE stage=END status=FAILED phase={} type={} start={} end={} errorType={} sqlState={} vendorCode={}",
                stage, resolvedType, started, ZonedDateTime.now(clock), e.getClass().getSimpleName(),
                e instanceof SQLException sql ? sql.getSQLState() : "-",
                e instanceof SQLException sql ? sql.getErrorCode() : 0,
                e);
            if (e instanceof BatchConfig.ConfigurationException) {
                LOG.error("Configuration error: {}", e.getMessage());
            }
            return 50;
        } finally {
            if (connectionInitialized) {
                DBUtil.close();
            }
        }
    }
}

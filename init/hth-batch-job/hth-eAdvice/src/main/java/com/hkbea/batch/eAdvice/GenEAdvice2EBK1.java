package com.hkbea.batch.eAdvice;

import ch.qos.logback.core.util.StringUtil;
import com.hkbea.batch.eAdvice.util.BatchConfig;
import com.hkbea.batch.eAdvice.util.DBUtil;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Task implementation for GenEAdvice2EBK1 (Type 1).
 * Queries Digx_Cz_Py_Nwpayment_Payout and generates fixed 500-char length file:
 * EBKP.HOSTPC.HTHTOEBK.EBFPADV.A.D{YYYYMMDD}.TXT
 */
public class GenEAdvice2EBK1 {

    private static final Logger LOG = LoggerFactory.getLogger(GenEAdvice2EBK1.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Hong_Kong");

    public void execute(String[] args) throws SQLException {
        LOG.info("HTH_EADVICE task=GenEAdvice2EBK1 status=STARTING");

        // Parse batch date from args: args[1] or args[2], fallback to current date
        String rawDate = null;
        if (args != null && args.length > 1 && args[1] != null && !args[1].isBlank()) {
            rawDate = args[1].trim();
        }

        String batchDate8;
        String batchDate6;
        if (rawDate != null && rawDate.length() == 6) {
            batchDate6 = rawDate;
            batchDate8 = "20" + rawDate;
        } else if (rawDate != null && rawDate.length() == 8) {
            batchDate8 = rawDate;
            batchDate6 = rawDate.substring(2);
        } else {
            LocalDate today = LocalDate.now(BUSINESS_ZONE);
            batchDate8 = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            batchDate6 = today.format(DateTimeFormatter.ofPattern("yyMMdd"));
        }

        LOG.info("HTH_EADVICE task=GenEAdvice2EBK1 batchDate8={} batchDate6={}", batchDate8, batchDate6);

        String outputDir = ".";
        try {
            BatchConfig config = BatchConfig.getInstance();
            if (config != null) {
                outputDir = config.getProperty(BatchConfig.BATCH_OUTPUT_DIR, ".");
            }
        } catch (Exception e) {
            LOG.warn("Failed to get output dir from BatchConfig, using default '.'", e);
        }

        String fileName = "EBKP.HOSTPC.HTHTOEBK.EBFPADV.A.D" + batchDate8 + ".TXT";
        Path outputPath = Paths.get(outputDir, fileName);
        EAdviceOutput.withLock(outputPath, output -> generate(batchDate8, batchDate6, output));
    }

    private void generate(String batchDate8, String batchDate6, Path outputPath)
            throws SQLException, IOException {
        String sql = "SELECT\r\n"
                + "       '02' RECORD_TYPE,\r\n"
                + "       PARTY_ID CCB_AC_NO,\r\n"
                + "       '0001' COMP_CODE,\r\n"
                + "       VALUEDATE TXN_YYYYMMDD,\r\n"
                + "       VALUEDATE TXN_hhmmdd,\r\n"
                + "       'FPS' TC_COD,\r\n"
                + "\r\n"
                + "       DEBIT_INTERNAL_ACCOUNT REL_AC_NO,\r\n"
                + "\r\n"
                + "       CASE\r\n"
                + "           WHEN PRODUCT_CODE  IN ('6805','1025') THEN 'S'\r\n"
                + "           WHEN PRODUCT_CODE IN ('6802','6803','4040','4050','4060') THEN 'C'\r\n"
                + "           ELSE ' '\r\n"
                + "       END REL_AC_TYPE,\r\n"
                + "\r\n"
                + "       PRODUCT_CODE  REL_AC_PRD_COD,\r\n"
                + "\r\n"
                + "       DEBIT_ACCOUNT_CUR TX_AMT_CCY,\r\n"
                + "       INSTD_AMOUNT TX_AMT,\r\n"
                + "\r\n"
                + "       CASE\r\n"
                + "           WHEN BENEF_EMAIL IS NOT NULL THEN 'E'\r\n"
                + "           WHEN BENEF_MOBILE IS NOT NULL THEN 'M'\r\n"
                + "           WHEN BENEF_FPSID IS NOT NULL THEN 'F'\r\n"
                + "           WHEN PAYEE_ID IS NOT NULL THEN 'P'\r\n"
                + "           ELSE 'N'\r\n"
                + "       END PROXY_ID_TYPE,\r\n"
                + "\r\n"
                + "       CASE\r\n"
                + "           WHEN BENEF_EMAIL IS NOT NULL THEN BENEF_EMAIL\r\n"
                + "           WHEN BENEF_MOBILE IS NOT NULL THEN BENEF_MOBILE\r\n"
                + "           WHEN BENEF_FPSID IS NOT NULL THEN BENEF_FPSID\r\n"
                + "           ELSE DEBIT_ACCOUNT_ID\r\n"
                + "       END PROXY_ID,\r\n"
                + "\r\n"
                + "       CASE\r\n"
                + "           WHEN BENEF_EMAIL IS NOT NULL THEN BENEF_EMAIL\r\n"
                + "           WHEN BENEF_MOBILE IS NOT NULL THEN BENEF_MOBILE\r\n"
                + "           WHEN BENEF_FPSID IS NOT NULL THEN BENEF_FPSID\r\n"
                + "           ELSE DEBIT_ACCOUNT_ID\r\n"
                + "       END PROXY_ID_FMT,\r\n"
                + "\r\n"
                + "       CASE\r\n"
                + "       WHEN DEBIT_ACCOUNT_ID IS NOT NULL THEN\r\n"
                + "        CASE\r\n"
                + "            WHEN CREDIT_ACCOUNT_NAME IS NOT NULL\r\n"
                + "                 AND LENGTH(CREDIT_ACCOUNT_NAME) < 35\r\n"
                + "                 AND (\r\n"
                + "                     CREDIT_ADDRESS_LINE1 IS NOT NULL\r\n"
                + "                     OR CREDIT_ADDRESS_LINE2 IS NOT NULL\r\n"
                + "        \r\n"
                + "                 )\r\n"
                + "            THEN CREDIT_ACCOUNT_NAME || ' '\r\n"
                + "            ELSE CREDIT_ACCOUNT_NAME\r\n"
                + "        END\r\n"
                + "        ||\r\n"
                + "        CASE\r\n"
                + "            WHEN CREDIT_ADDRESS_LINE1 IS NOT NULL\r\n"
                + "                 AND LENGTH(CREDIT_ADDRESS_LINE1) < 35\r\n"
                + "                 AND (\r\n"
                + "                     CREDIT_ADDRESS_LINE2 IS NOT NULL\r\n"
                + "               \r\n"
                + "                 )\r\n"
                + "            THEN CREDIT_ADDRESS_LINE1 || ' '\r\n"
                + "            ELSE CREDIT_ADDRESS_LINE1\r\n"
                + "        END\r\n"
                + "        ||\r\n"
                + "        CASE\r\n"
                + "            WHEN CREDIT_ADDRESS_LINE2 IS NOT NULL\r\n"
                + "                 AND LENGTH(CREDIT_ADDRESS_LINE2) < 35\r\n"
                + "                \r\n"
                + "            THEN CREDIT_ADDRESS_LINE2 || ' '\r\n"
                + "            ELSE CREDIT_ADDRESS_LINE2\r\n"
                + "        END\r\n"
                + "    ELSE CREDIT_ACCOUNT_NAME\r\n"
                + "END AS DEP_AC_NAME,\r\n"
                + "\r\n"
                + "       remarks  CUST_TXN_REF,\r\n"
                + "\r\n"
                + "       FPS_TXN_REF TEST,\r\n"
                + "\r\n"
                + "       PAYMENT_ID\r\n"
                + "\r\n"
                + "FROM HTH_BEA.HTH_PAYMENT_PAYOUT\r\n"
                + "\r\n"
                + "WHERE payment_type='FPS'\r\n"
                + "  AND IPE_STATUS = 'SUCCESS'\r\n"
                + "  AND (\r\n"
                + "        BATCH_STATUS_FLAG IS NULL\r\n"
                + "        OR (\r\n"
                + "             BATCH_STATUS_FLAG NOT LIKE '%1%'\r\n"
                + "         AND BATCH_STATUS_FLAG NOT LIKE '%2%'\r\n"
                + "         AND BATCH_STATUS_FLAG NOT LIKE '%3%'\r\n"
                + "        )\r\n"
                + "      )\r\n"
                + "\r\n"
                + "  AND VALUEDATE BETWEEN\r\n"
                + "    NVL(\r\n"
                + "        (\r\n"
                + "            SELECT MAX(VALUEDATE)\r\n"
                + "            FROM HTH_BEA.HTH_PAYMENT_PAYOUT\r\n"
                + "            WHERE BATCH_STATUS_FLAG IS NOT NULL AND (BATCH_STATUS_FLAG like '%1%' or BATCH_STATUS_FLAG like '%2%' or BATCH_STATUS_FLAG like '%3%' )\r\n"
                + "              AND VALUEDATE < TO_DATE(?, 'YYMMDD')\r\n"
                + "        ),\r\n"
                + "        TO_DATE('19000101', 'YYYYMMDD')\r\n"
                + "    )\r\n"
                + "    AND TO_DATE(?, 'YYMMDD') + 1\r\n"
                + "\r\n"
                + "ORDER BY CREATION_DATE DESC";

        List<String> bodyLines = new ArrayList<>();
        List<String> paymentIdList = new ArrayList<>();

        SimpleDateFormat yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat hhmmss = new SimpleDateFormat("HHmmss");

        Connection conn = DBUtil.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, batchDate6);
            pstmt.setString(2, batchDate6);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String recordType = padRight(rs.getString("RECORD_TYPE"), 2);
                    String ccbAcNo = rs.getString("CCB_AC_NO");
                    ccbAcNo = ext2intAccNo(ccbAcNo != null ? ccbAcNo.trim() : "", "");
                    String paymentId = rs.getString("payment_id");
                    if (StringUtils.isBlank(ccbAcNo)) {
                        LOG.error("ccbAcNo is null"+paymentId);
                    }
                    ccbAcNo = padRight(ccbAcNo, 18);

                    String compCode = padRight(rs.getString("COMP_CODE"), 4);

                    Timestamp valuedate = rs.getTimestamp("TXN_YYYYMMDD");
                    String txnYmd = valuedate != null ? yyyyMMdd.format(valuedate) : padRight("", 8);
                    String txnTime = valuedate != null ? hhmmss.format(valuedate) : padRight("", 6);

                    String tcCod = padRight(rs.getString("TC_COD"), 4);
                    String relAcNo = padRight(rs.getString("REL_AC_NO"), 18);
                    String relAcType = padRight(rs.getString("REL_AC_TYPE"), 1);
                    String relAcPrdCod = padRight(rs.getString("REL_AC_PRD_COD"), 4);
                    String txAmtCcy = padRight(rs.getString("TX_AMT_CCY"), 3);

                    BigDecimal amount = rs.getBigDecimal("TX_AMT");
                    String txAmt = formatAmountTo15Digits(amount);

                    String proxyIdType = padRight(rs.getString("PROXY_ID_TYPE"), 1);
                    String proxyId = padRight(rs.getString("PROXY_ID"), 32);
                    String proxyIdFmt = padRight(rs.getString("PROXY_ID_FMT"), 32);
                    String depAcName = padRight(rs.getString("DEP_AC_NAME"), 140);
                    String custTxnRef = padRight(StringUtils.isBlank(rs.getString("CUST_TXN_REF"))?
                            rs.getString("CUST_TXN_REF"):
                            rs.getString("CUST_TXN_REF").replaceAll("[\\r\\n]", ""), 40);

                    String fpsTxnRef = rs.getString("TEST");
                    fpsTxnRef = padRight(fpsTxnRef != null ? fpsTxnRef.trim() : "", 9);

                    String filler = " ".repeat(163);

                    StringBuilder bodyLine = new StringBuilder();
                    bodyLine.append(recordType)
                            .append(ccbAcNo)
                            .append(compCode)
                            .append(txnYmd)
                            .append(txnTime)
                            .append(tcCod)
                            .append(relAcNo)
                            .append(relAcType)
                            .append(relAcPrdCod)
                            .append(txAmtCcy)
                            .append(txAmt)
                            .append(proxyIdType)
                            .append(proxyId)
                            .append(proxyIdFmt)
                            .append(depAcName)
                            .append(custTxnRef)
                            .append(fpsTxnRef)
                            .append(filler);

                    bodyLines.add(bodyLine.toString());

                   // String paymentId = rs.getString("payment_id");
                    if (paymentId != null) {
                        paymentIdList.add(paymentId.trim());
                    }
                }
            }
        }

        if (EAdviceOutput.publish(outputPath, batchDate8, bodyLines, conn, paymentIdList, "1")) {
            LOG.info("HTH_EADVICE task=GenEAdvice2EBK1 generated file={} records={}",
                    outputPath, bodyLines.size());
        } else {
            LOG.info("HTH_EADVICE task=GenEAdvice2EBK1 retained file={} because no unprocessed records remain",
                    outputPath);
        }
    }

    private static String padRight(String s, int len) {
        if (s == null) {
            s = "";
        }
        if (s.length() >= len) {
            return s.substring(0, len);
        }
        return s + " ".repeat(len - s.length());
    }

    private static String formatAmountTo15Digits(BigDecimal amount) {
        if (amount == null) {
            return "0".repeat(15);
        }
        long scaled = amount.multiply(BigDecimal.valueOf(100)).longValue();
        return String.format("%015d", Math.abs(scaled));
    }


    public static String ext2intAccNo(String as_extAccNo, String as_easa) {
        if (as_extAccNo == null || as_extAccNo.isEmpty()) {
            return "";
        }
        if (!as_extAccNo.startsWith("015")) {
            as_extAccNo = "015" + as_extAccNo;
        }
        int li_acnoLen = as_extAccNo.length();
        if (li_acnoLen < 14 || li_acnoLen > 15) {
            return as_extAccNo; // for offshore account to work
        }
        String ls_ddd = as_extAccNo.substring(3, 6);
        String ls_type = as_extAccNo.substring(6, 8);
        String ls_seqs = as_extAccNo.substring(8);
        StringBuilder lsb = new StringBuilder();

        if (li_acnoLen == 15) { // new deposit
            lsb.append("00150").append(ls_ddd).append(ls_type).append("0").append(ls_seqs);
        } else if ("89,82,78,79,33,81,68,88,18".indexOf(ls_type) % 3 == 0) { // AIO
            String ls_XX = "10";
            if ("C".equals(as_easa)) {
                ls_XX = "12";
            } else if ("S".equals(as_easa)) {
                ls_XX = "15";
            } else if ("U".equals(as_easa)) {
                ls_XX = "11";
            } else if ("T".equals(as_easa)) {
                ls_XX = "13";
            }
            lsb.append("00150").append(ls_ddd).append(ls_type).append(ls_XX).append(ls_seqs);
        } else { // old deposit
            lsb.append("00150").append(ls_ddd).append("00").append(ls_type).append(ls_seqs);
        }
        return lsb.toString();
    }
}

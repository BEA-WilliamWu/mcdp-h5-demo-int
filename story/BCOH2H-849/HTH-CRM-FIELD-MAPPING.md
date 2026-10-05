# HTH CRM 字段对照（113 列）

基线：BCO `CustomerRelationshipManagement_Event3.orm.xml` 的 99 个不同列；独立 HTH 表增加已有 14 列业务信息。实体类型逐项对齐，不继承或修改 BCO Entity。

本次在原 24 列之上增加 89 列：20 列补充适用的通用取值，69 列为本期未涉及业务或凭据保留列。字段存在不代表每类操作都应有值；原有历史数据不伪造新字段。

长度为 HTH 安装定义，不宣称等于未取得的 BCO 物理 DDL；部分取自 BCO assembler 的截断上限。已有 24 列容量保留，BigDecimal 对应不预设小数位的 NUMBER。新加 89 列全部 nullable，已有安装兼容更宽字符容量。长度来源及英文 comments 的可核对清单在 `devtools/backend-compile/tests/fixtures/hth_crm_849_fields.json`。

## 逐字段定义

| 列 | HTH SQL 类型 | 分类 | 新记录取值 |
|---|---|---|---|
| EVENT_ID | VARCHAR2(36 CHAR) | 原有 | HTH UUID；独立主键，不使用 BCO sequence |
| RECORD_TYPE | VARCHAR2(16 CHAR) | 新增适用 | CRMConfiguration.CRM_RECORD_TYPE |
| FILLER_01 | VARCHAR2(64 CHAR) | 新增适用 | CRMConfiguration.CRM_FILLER_01 |
| EVENT_DTE | VARCHAR2(8 CHAR) | 原有 | 业务 occurredAt 转香港 yyyyMMdd |
| EVENT_TIME | VARCHAR2(6 CHAR) | 原有 | 业务 occurredAt 转香港 HHmmss |
| CHNL_ID | VARCHAR2(64 CHAR) | 新增适用 | Login-Channel=BCM→ELE-BCM，否则 CRM_CHNL-ID_INTERNET |
| CHNL_TYPE_CODE | VARCHAR2(32 CHAR) | 新增适用 | CRMConfiguration.CRM_CHNL-TYPE-CODE_INTERNET |
| EVENT_STATUS_CODE | VARCHAR2(16 CHAR) | 原有 | 本阶段 SUCCESS/PENDING_APPROVAL→A，其余→R；事务回滚→R |
| CR_DR_IND | VARCHAR2(1 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Credit or debit indicator from the BCO model |
| FEE_CHRG_CODE | VARCHAR2(32 CHAR) | 新增适用 | CRMConfiguration.CRM_FEE_CHARGE_CODE |
| EVENT_ACTV_TYPE_CODE | VARCHAR2(64 CHAR) | 原有 | HTHCrmConfiguration 的 ACTIVITY_<内部活动>；未配置为 NULL |
| FIN_IND | VARCHAR2(1 CHAR) | 原有 | 非金融维护活动固定 N |
| SELF_SRV_IND | VARCHAR2(1 CHAR) | 新增适用 | isAdmin=true→N、false→Y，缺失→NULL |
| USER_ID | VARCHAR2(256 CHAR) | 原有 | 快照 actorUserId；缺失回退 FMO_USER_ID / SessionContext.userId；保留完整 ID |
| EVENT_COUNTRY_CODE | VARCHAR2(8 CHAR) | 新增适用 | CRMConfiguration.CRM_EVENT_COUNTRY_CODE |
| ACCT_NBR | VARCHAR2(64 CHAR) | 原有 | 快照 partyId；缺失回退 SessionContext.transactingPartyCode |
| PHONE_NBR | VARCHAR2(20 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Phone proxy number from the BCO model |
| PHONE_NBR_ACCT_NBR | VARCHAR2(20 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Account linked to the phone proxy from the BCO model |
| PHONE_NBR_REQ_RESULT | VARCHAR2(16 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Phone proxy request result from the BCO model |
| PHONE_DEFAULT_IND | VARCHAR2(1 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Default phone proxy indicator from the BCO model |
| ELECT_ADD | VARCHAR2(50 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Electronic-address proxy from the BCO model |
| ELECT_ADD_ACCT_NBR | VARCHAR2(20 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Account linked to the electronic-address proxy from the BCO model |
| ELECT_ADD_REQ_RESULT | VARCHAR2(16 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Electronic-address proxy request result from the BCO model |
| ELECT_ADD_DEFAULT_IND | VARCHAR2(1 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Default electronic-address proxy indicator from the BCO model |
| FPS_ID | VARCHAR2(20 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。FPS proxy identifier from the BCO model |
| FPS_ACCT_NBR | VARCHAR2(20 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Account linked to the FPS proxy from the BCO model |
| FPS_REQ_RESULT | VARCHAR2(16 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。FPS proxy request result from the BCO model |
| DEBIT_ACCT_NBR | VARCHAR2(20 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Debit account number from the BCO model |
| EVENT_CCY_CODE | VARCHAR2(3 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Event currency code from the BCO model |
| EVENT_AMT | NUMBER | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Event amount from the BCO model |
| FEE_CHRG_AMT | NUMBER | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Fee charged amount from the BCO model |
| FEE_CCY_CODE | VARCHAR2(3 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Fee currency code from the BCO model |
| TRF_DTE | VARCHAR2(8 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Transfer date from the BCO model |
| TRF_FREQ | VARCHAR2(32 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Transfer frequency from the BCO model |
| PROXY_ID_TYPE | VARCHAR2(32 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Payment proxy identifier type from the BCO model |
| PROXY_ID | VARCHAR2(34 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Payment proxy identifier from the BCO model |
| PAYEE_NAME | VARCHAR2(50 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Payee name from the BCO model |
| PAYEE_BANK_CODE | VARCHAR2(3 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Payee bank code from the BCO model |
| EVENT_REM | VARCHAR2(40 CHAR) | 新增适用 | 审批引用→业务引用→FC/DIGX TRANS_REF，取前 40 字符，与 BCO assembler 对齐 |
| REF_NBR | VARCHAR2(9 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。BCO numeric-format reference number from the BCO model |
| FROM_DTE | VARCHAR2(8 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Period start date from the BCO model |
| TO_DTE | VARCHAR2(8 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Period end date from the BCO model |
| MANDATE_ID | VARCHAR2(128 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Direct-debit mandate identifier from the BCO model |
| EDDA_MAINT_ACTION | VARCHAR2(32 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Electronic direct-debit maintenance action from the BCO model |
| SOURCE_TRX_REF_NBR | VARCHAR2(128 CHAR) | 原有 | approvalReference，缺失取 referenceNumber；保留完整业务引用 |
| PAY_CAT_PURP_CODE | VARCHAR2(6 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Payment category purpose code from the BCO model |
| PAY_PURP_CODE | VARCHAR2(6 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Payment purpose code from the BCO model |
| DEVICE_ID | VARCHAR2(256 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Device identifier from the BCO model |
| MOBILE_BRAND | VARCHAR2(128 CHAR) | 新增适用 | BCO BeaParser 解析 FMO_USER_AGENT 得到的操作系统 |
| PLATFORM_CODE | VARCHAR2(64 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Client platform code from the BCO model |
| DEVICE_MODEL | VARCHAR2(128 CHAR) | 新增适用 | BCO BeaParser 得到的浏览器名称 |
| DEVICE_OS_VERSION | VARCHAR2(128 CHAR) | 新增适用 | BCO BeaParser 得到的浏览器版本 |
| IP_ADDRESS | VARCHAR2(64 CHAR) | 原有 | FMO_IP_ADDRESS，最多 64 字符 |
| FILLER_02 | VARCHAR2(3 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Secondary CRM filler from the BCO model |
| EVENT_AMT_HKE | NUMBER | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Event amount in Hong Kong dollar equivalent from the BCO model |
| EVENT_EX_RATE | NUMBER | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Event exchange rate from the BCO model |
| MRCH_ID | VARCHAR2(128 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Merchant identifier from the BCO model |
| ACCT_CCY_CODE | VARCHAR2(3 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Account currency code from the BCO model |
| ACCT_AMT | NUMBER | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Account-currency amount from the BCO model |
| SCREEN_ID | VARCHAR2(64 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。BCO screen identifier from the BCO model |
| OMB_FLAG | VARCHAR2(1 CHAR) | 新增适用 | CM 用户/授权：平台已给出 IS_OMB_ENABLED=true 且非银行操作人时为 Y；false 可能为初始化值，未知留 NULL，不重算审批规则 |
| MULTI_APP_REJ_TXN_CNT | VARCHAR2(32 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Count of transactions in a multiple-approval or rejection action from the BCO model |
| COUPON_CODE | VARCHAR2(64 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Coupon code from the BCO model |
| ACTL_INT_RATE | VARCHAR2(64 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Actual interest rate from the BCO model |
| TT_AUTO_ROUTE | VARCHAR2(1 CHAR) | 新增适用 | BCO common 默认 N |
| SUSPICIOUS_ACTIVITY | VARCHAR2(128 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Suspicious activity classification from the BCO model |
| SUSPICIOUS_IND | VARCHAR2(1 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Suspicious activity indicator from the BCO model |
| WITH_MRCH | VARCHAR2(1 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Merchant-presence indicator from the BCO model |
| CMPY_NAME | VARCHAR2(256 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Merchant company name from the BCO model |
| DOC_ID | VARCHAR2(128 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Document identifier from the BCO model |
| DOC_TYPE | VARCHAR2(32 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Document type from the BCO model |
| DOC_COUNTRY_CODE | VARCHAR2(8 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Document issuing country code from the BCO model |
| MRCH_USER_ID | VARCHAR2(256 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Merchant user identifier from the BCO model |
| MRCH_USER_NAME | VARCHAR2(256 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Merchant user name from the BCO model |
| DOC_CMPY_NAME | VARCHAR2(256 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Company name on a document from the BCO model |
| DOC_ACCT_NBR | VARCHAR2(64 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Account number on a document from the BCO model |
| SLE_MRCH_NAME | VARCHAR2(256 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。SLE merchant name from the BCO model |
| FORMAT | VARCHAR2(32 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Advice or document format from the BCO model |
| TXN_COUNT | VARCHAR2(32 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Transaction count from the BCO model |
| ADV_FREQ_TYPE | VARCHAR2(32 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Advice frequency type from the BCO model |
| ADV_TYPE | VARCHAR2(32 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Advice type from the BCO model |
| SLE_MRCH_ID | VARCHAR2(128 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。SLE merchant identifier from the BCO model |
| AR_TOKEN | VARCHAR2(256 CHAR) | 新增保留 | NULL；不采集认证 token/凭据 |
| TREASURY_REF | VARCHAR2(128 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Treasury reference from the BCO model |
| LM_SWEEP_INSTRUCTION_TYPE | VARCHAR2(64 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Liquidity-management sweep instruction type from the BCO model |
| LM_FREQUENCY_EXECUTIONDAY | VARCHAR2(64 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Liquidity-management frequency or execution day from the BCO model |
| LM_EFFECTIVEDATE | VARCHAR2(32 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Liquidity-management effective date from the BCO model |
| LM_RULE_SETUPDATE | VARCHAR2(32 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Liquidity-management rule setup date from the BCO model |
| LM_SWEEPING_AMOUNT_THRESHOLD | VARCHAR2(128 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Liquidity-management sweep amount or threshold from the BCO model |
| LM_FPXTXN_NBR | VARCHAR2(128 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Liquidity-management FPX transaction number from the BCO model |
| LM_INSTRUCTION_NBR | VARCHAR2(128 CHAR) | 新增保留 | NULL；当前 CM/BM 维护场景无对应取值。Liquidity-management instruction number from the BCO model |
| USER_ROLE | VARCHAR2(32 CHAR) | 新增适用 | User-Role 线程属性 |
| TOKEN_ID | VARCHAR2(256 CHAR) | 新增保留 | NULL；不采集认证 token/凭据 |
| ERR_CODE | VARCHAR2(20 CHAR) | 新增适用 | 优先首条审批 ProcessingError 的安全错误代码，其次 TFA_ERR_CODE，取最后 20 字符 |
| BIO_TYPE | VARCHAR2(32 CHAR) | 新增适用 | Bio-Type 线程属性 |
| REG_METHOD | VARCHAR2(64 CHAR) | 新增适用 | Reg-Method 线程属性 |
| AUTH_METHOD | VARCHAR2(64 CHAR) | 新增适用 | Auth-Method 线程属性 |
| LANG | VARCHAR2(2 CHAR) | 新增适用 | BCO locale 简体→sc、繁体→tc、其余→en |
| APP_VERSION | VARCHAR2(64 CHAR) | 新增适用 | App-Version 线程属性 |
| SOURCE_SYSTEM | VARCHAR2(16 CHAR) | 原有 | HTH |
| CHANNEL_TYPE | VARCHAR2(16 CHAR) | 原有 | COMPANY_* 为 BM，其余 CM；不代替 BCO CHNL_ID |
| TARGET_USER_CHANNEL | VARCHAR2(16 CHAR) | 原有 | 公司操作 NULL，HTH 用户操作 HTH |
| ACTIVITY_KEY | VARCHAR2(64 CHAR) | 原有 | 根据已纳入的 HTH service / operation 判定内部活动 |
| PHASE | VARCHAR2(32 CHAR) | 原有 | 快照 crmPhase，或按已有业务结果推导 SUBMIT / APPLY / EXECUTE / GENERATE |
| TARGET_USER_ID | VARCHAR2(256 CHAR) | 原有 | 快照 targetUserId；不覆盖操作人 |
| RELATIONSHIP_TYPE | VARCHAR2(32 CHAR) | 原有 | 快照 linkageType；Related / Associated |
| SERVICE_ID | VARCHAR2(256 CHAR) | 原有 | 实际来源服务 |
| TASK_CODE | VARCHAR2(100 CHAR) | 原有 | FC 线程 CURRENT_TASK；缺失时兼容 DIGX 属性 |
| SOURCE_ACTION_ID | VARCHAR2(128 CHAR) | 原有 | 快照 crmActionId |
| REQUEST_ID | VARCHAR2(128 CHAR) | 原有 | 快照 requestId |
| ERROR_CODE | VARCHAR2(100 CHAR) | 原有 | 快照业务 errorCode；事务回滚为 BUSINESS_ROLLBACK |
| DEDUP_KEY | VARCHAR2(64 CHAR) | 原有 | 成功且有稳定动作 ID 时对活动、阶段、引用、动作、公司、目标用户生成 SHA-256 摘要 |
| CREATED_AT | TIMESTAMP(6) | 原有 | 独立保存前取得香港时间；历史缺失值不回填 |

## 有意保留的 HTH 差异

- `USER_ID` 保留完整操作人，不统一删除 @ 后缀：BCO 用户通用采集与授权 evaluator 本就存在差异；这里保持 HTH 既有追踪信息。
- `SOURCE_TRX_REF_NBR` 保留 HTH 业务/审批关联；`EVENT_REM` 另补 BCO 对应的交易引用及 40 字符限制。
- `EVENT_ID` 使用 HTH UUID、`CREATED_AT` 使用实际写入时间；不依赖 BCO sequence，也不为旧行生成假事件或假时间。
- `PHONE_NBR/ELECT_ADD` 等代理登记相关字段不挪作本次用户联系方式通知数据。BCO 用户维护 evaluator 只补公司标识；未映射的字段不擅自填满。
- `TOKEN_ID/AR_TOKEN` 只保留模型，HTH 不存秘密；不保存整个 request、headers 或用户资料。
- `CHNL_ID/CHNL_TYPE_CODE` 取 BCO 同一来源，`CHANNEL_TYPE=CM/BM` 仅描述 HTH 门户入口；两者不可互相代替。

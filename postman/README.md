# HTH Postman 调试

在 Postman 选择 **Import**，导入两份 Collection 和要使用的 SIT 或 UAT Environment，然后在右上角选择同名环境。

| 文件 | 内容 |
| --- | --- |
| [HTH-APIs.postman_collection.json](HTH-APIs.postman_collection.json) | APIC 产品声明的 HTH、DSP 和 UAM 路由，以及 HTH BFF 直连请求。Payment 包含 ONUS、FPS、CHATS、TT、DR 五份输入示例。 |
| [HTH-Downstream-APIs.postman_collection.json](HTH-Downstream-APIs.postman_collection.json) | BG3、PE3、IPE、TC3、EBK、ADC、RBS、FMO、WMG、UAM HTTP 请求，以及 HTH Support、DSP Support、DSP BFF 直连请求。 |
| [HTH-SIT.postman_environment.json](HTH-SIT.postman_environment.json) | SIT 地址和变量。 |
| [HTH-UAT.postman_environment.json](HTH-UAT.postman_environment.json) | UAT 地址和变量。 |

## 首次调试

1. 核对选中环境的 `gateway_base_url` 和本次调用的 `*_base_url`。Gateway 的值含 APIC catalog 前缀，到 `/hth/api` 或 `/dsp/api` 之前为止。下游值沿用仓库各服务的环境配置，部分 UAT 配置指向 SIT 路径。直连 BFF 或 Support 的地址留空，按实际部署填协议、主机和端口，集合请求会附上 Jersey 路径。
2. 调 APIC 时填 `apic_client_id`、`apic_client_secret`。调下游时按请求 Headers 填该服务的 `*_client_id`、`*_client_secret`，例如 `ipe_client_id` 和 `ipe_client_secret`。ADC 使用 BG3 配置，RBS 使用 TC3 配置，WMG 还使用 `wmg_auth_key`。下游可选的 Bearer header 可以按测试环境要求启用。凭证、签名和 token 的导出初始值为空。
3. 调带 Bearer 的 HTH API 时，把 logon 返回的 token 填入 `access_token`，值只填 token 本身。签名请求的 Headers 区已列出 `Client-Id`、`Request-Time` 和 `Request-Sign`，用注册的测试客户端生成 `request_sign` 和匹配的 `request_time_ms`。Logon body 还需要 `client_assertion` 和 `password_signature`。请求会自动补 Postman GUID；日期或业务交易号未填写时，脚本会用香港当天日期或新交易号补齐。
4. 把 `account_number`、`creditor_account`、`creditor_name`、`creditor_bank_bic` 等替换为此次测试的数据。BG3 和 PE3 的 18 位内部账号相关字段见各请求描述。操作账户、提交付款、更新限额及启动流程时，选定具体请求后逐条发送。

## 常用链路

- **HTH Payment** 从对应付款类型的示例发 `POST /hth/api/payment/v1/initiate`。把返回的 `txnRef` 填到 `txn_ref`，再查 `GET /hth/api/payment/v1/transaction-inquiry`。最终路由以 IPE 结果为准。
- **IPE** 先按付款类型跑 **Smart Routing / Validation**。填好 `ipe_message_id` 并在校验和提交之间保持一致。提交请求附带一份 CHATS 示例，其他类型调试时可用 HTH 同类型的出站 payload。付款结果查询提供 batch ID（CHATS）和 transaction ID（其他付款）的两个请求。
- **PE3** 提供 HTH 使用的 PEECHI11 和 PEECHJ7A。后者是当前代码调用的地址查询。
- **签名** 直连 HTH／DSP BFF 的 `Request-Sign` 校验使用 HTTP method、Jersey 相对 path、query、毫秒级 `Request-Time`、JSON 原文，按此顺序以换行连接。保持签名时的时间、query 顺序和实际发送的 body 一致。当前实现参考 `HthBffRequestHeaderFilter` 和 `CypherServiceImpl`。

## 来源和覆盖

- Gateway 路由取自 `pd-hth-internal/app_1.0.0.yaml` 引用的各 API 定义。直连路径和下游运行时路径以 Java 客户端及 Jersey 配置为准。IPE Name Match、UAM token 和 JWKS 等请求的路径差异已在 Collection 中区分。
- BG3 的 BGECH7E0、BGNC401 和 BGNC400I 使用 Java 报文组装代码补全示例。IPE 旧版 `/outward/credit-transfer`、部分 UAM 操作按仓库声明保留，调试条目注明运行时差异或用途。
- ESP/eStatement 下游通过 ESP Java SDK 调用。可以通过 Collection 调 HTH 的文档接口，仓库没有可直接导入的 ESP HTTP 合同。下游清单覆盖本仓库可确认的 HTTP 接口。
- 文件经过 Postman Collection v2.1 schema 校验。SIT/UAT 认证、权限和接口响应需在目标环境确认。

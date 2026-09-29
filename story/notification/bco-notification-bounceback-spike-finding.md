# BCO Notification Bounce-back Handling

初步调查 · 2026-09-29

本次调查关注：BCO 发出的 Email / SMS 失败后，系统怎样记录、能不能重试、会不会换渠道通知，以及去哪里排查。依据为本地 `cdc-application/consulting` 的源码、数据库映射、批次脚本和附带的框架 JAR；尚未连接运行数据库或核对生产部署版本。下文主要描述普通 MNG 通知链路，SMTP 特例在第 4 节单列。

## 1. 初步结论

BCO 有两条不同的失败处理链路。第一条是发送时通知平台接口直接报错：系统记录发送失败，底层框架具备按配置重试的能力。第二条是接口调用已经完成，通知平台后来报告邮件或短信无法送达：BCO 导入 bounce report，再为符合条件的通知安排备用渠道和联系方式更新提醒。

**Bounce-back 的主要目的，是让客户知道“银行联系不到你，请更新联系方式”。它通常不会把原来那封交易通知或 PIN 通知原样重新发一遍。**

| 遇到什么情况 | BCO 怎么处理 | 客户可能看到什么 |
| --- | --- | --- |
| 调用 Email / SMS 发送接口就失败 | 记录 `Failed`；通知动作失败后，可以由框架按该事件的重试配置再次执行 | 不能保证马上有另一条提醒；要分别查重试结果和后续失败回报 |
| 用户 Email 后来被退回 | 收到失败文件后，检查事件名单、用户当前联系方式及最近 30 天处理记录；符合条件时发备用 SMS | 提醒更新登记的 Email，并可在下次登录时提示 |
| 用户 SMS 后来报告投递失败 | 同样先经过筛选；符合条件时发备用 Email | 提醒更新登记的手机号码，并可在下次登录时提示 |
| 用户没有可用的另一种联系方式，或另一渠道已有失败历史 | 代码有站内信、登录提醒、公司 Email 的分支，但有历史抑制条件，部分分支还有需核对的问题 | 可能收到站内信或登录提醒；不能保证“两个渠道都失败就一定有站内信” |
| 公司 office Email 被退回 | 按公司退信规则，可以发 AP Email、同公司 System Admin 站内信和登录提醒 | 提醒更新公司 Email；不同通知的收件人集合并不完全相同 |
| 失败通知不在指定事件名单，或找不到原发送记录 | 不能进入这里看到的普通 BCO 备用通知主流程 | 不应预期每一次失败都自动换渠道通知 |

这里的 **Webmail 是 BCO 内部站内信**，客户登录后查看，不是发到外部邮箱。Email / SMS 中的 `Success` 也只是本次发送接口调用的结果，不能据此认定客户已收到。

## 2. 一条通知失败后，具体经过哪些步骤

```text
原业务产生通知事件
  ↓
EmailDispatcher / SMSDispatcher 调用 MNG 通知平台
  ├─ 当场报错 → 记录 Failed → 框架按事件配置决定是否重试
  └─ 未返回错误 → 记录 Success（还不是最终送达）
                         ↓ 后来发生投递失败
              MNG 输出 Email / SMS bounce CSV
                         ↓
              loadMNGmsg.sh 导入失败明细
                         ↓
              ValidateAndSendBounceNotify.sh
              关联原通知、筛选事件、检查历史、选备用收件人
                  ├─ 写站内信 / 设置登录提醒
                  └─ 写备用通知任务表
                         ↓
              BatchExecutionScheduler → alertBounceBack
              注册备用 Email / SMS 事件 → 再走通知发送框架
```

**这条 bounce 链路是批次处理，不是发送失败后立刻弹出或实时切换渠道。**多久后客户能看到提醒，取决于 MNG 何时提供文件、导入批次什么时候执行、备用通知 job 什么时候运行。目前不能从这些源码确定生产环境每隔几分钟或每天几点执行。

三个主要阶段的数据可以这样理解：

| 表 | 存的是什么 | 关键关联 / 字段 |
| --- | --- | --- |
| `DIGX_CZ_EMAIL_MNG` | BCO 原发送记录，Email 和 SMS 都使用它 | `REFNUMBER`、`EVENTID`、`RECIPIENTID`、`RESPONSE_STATUS`、`COD_ACT_DATA_ID` |
| `DIGX_CZ_BATCH_BOUNCE_BACK_CCBEMAIL` | MNG 返回的 Email 失败明细 | `SRC_SYS_REF_NUM`、`EMAIL_RECI_ADDR`、`BOUNCE_BACK_MSG` |
| `DIGX_CZ_BATCH_BOUNCE_BACK_CCBSMS` | MNG 返回的 SMS 失败明细 | `SRC_SYS_REF_NUM`、`SMS_RECI_MOB_NUM`、`DELIVERY_MSG`、`SEND_DTTM` |
| `DIGX_CZ_BATCH_HIGH_ALERT_EVENT_LIST` | 哪些原通知事件进入备用通知处理 | `EVENTID` |
| `DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE` | 为这次失败选好的备用渠道、收件人和处理记录 | `REFNUMBER`、`ALT_SMS`、`ALT_EMAIL`、`ALT_OFFICE_EMAIL`、`ISPROCESSED` |
| `DIGX_EP_ACN_LOG_B` | 通知框架实际执行发送动作的记录 | `FLG_PROCESS_STAT`、`TXT_DEST_ADDR`、`NUM_RETRY_CNT`、`TXT_COMMENTS` |

最重要的关联是 `DIGX_CZ_EMAIL_MNG.REFNUMBER = 失败明细.SRC_SYS_REF_NUM`。这里的 `REFNUMBER` 是通知发送参考号；`ORG_REF_NO` 才是另存的原业务参考号，排查时不要混用。

依据：[C1](#c1)、[C2](#c2)、[C3](#c3)、[C5](#c5)。

## 3. 备用通知发给谁

### 3.1 用户自己的 Email / SMS 失败

例如，用户 A 的 Email 通知被退回。批次找到原记录中的用户 A，再检查当前 profile 的 Email 是否仍然等于原来发送失败的地址；匹配后才取该用户的手机作为备用渠道。若用户已换过 Email，不能直接假定仍会按旧通知找到备用手机。

SMS 失败时，初次查询会用用户当前的 `MOBILE_CODE || MOBILENO` 和原失败手机比对，再取用户 Email。不过后续分支还会重新使用当前用户 Email，而且存在空值判断问题，因此不能把这个比对理解成已经严格执行的发送限制。

| 用途 | 收件人 / 数据来源 | 主要条件 |
| --- | --- | --- |
| Email 失败后的备用 SMS | `DIGX_UM_USERPROFILE.MOBILENO`；手机区号来自 `DIGX_CZ_UM_EXTENSIONDATA.MOBILE_CODE` | 要匹配原收件人与当前 profile，并通过历史检查 |
| SMS 失败后的备用 Email | `DIGX_UM_USERPROFILE.EMAIL` | 初次查询比对当前完整手机号；后续还受历史检查和重新赋值逻辑影响，见下方说明 |
| 部分兜底分支的公司 Email | `DIGX_PI_PARTY_PREFERENCES.OFFICE_EMAIL` | 按原通知 `PARTYID` 查询；分支会检查是否与个人 Email 相同及历史记录 |
| 用户站内信 | 原通知关联的用户，在 BCO 站内收件箱查看 | 在对应兜底分支写入 mailbox 表 |
| 用户登录提醒 | `DIGX_CZ_UM_EXTENSIONDATA.BOUNCE_BACK_REMINDER='USER'` | 提醒联系 Authorised Person / Administrator 更新手机或 Email |

**30 天是历史检查窗口，不是“30 天后再重试一次”。**代码根据最近 30 天备用通知处理表的渠道、时间等字段判断是否已经处理过，以决定是否继续通知或换渠道。它没有在这条主流程中建立按 SMTP hard bounce / soft bounce 分类的统一重试策略。

同一次批次还按用户 / party 抑制重复通知，因此同一个用户的多条退信不一定对应同样多条备用通知。

具体来说，SMS 初次查询没有匹配到手机时，备用 Email 可能是 `null`。但后续使用 `getAlt_email() != ""` 判断，`null` 也会进入该分支；没有相应历史记录时，代码又把它赋成当前用户 Email。所以，“用户已经换了手机，就一定不会发备用 Email”也不是这份代码能够保证的行为。

依据：[C2](#c2)、[C4](#c4)、[C6](#c6)。

### 3.2 公司 office Email 失败

这里要特别区分两组人，不能只看 `AP`、`SYSADMIN` 这些变量名称。

| 通知形式 | 代码实际选谁 | 怎么查 |
| --- | --- | --- |
| 发给 AP 的外部 Email | 同一 party 下 `IS_AP='1'` 的用户，取其个人 Email | user profile + extension data + user-party relation |
| System Admin 站内信 | 同一 party 下，`DIGX_UM_USER_PRINCIPAL.PRINCIPAL like '%SYSADM%'` 的用户 | user principal + user-party relation |
| 公司 Email 退信的登录提醒 | 上述 SYSADM 查询选出的用户，写入标志 `'AP'` | `BOUNCE_BACK_REMINDER='AP'` |

也就是说，**登录标志叫 `AP`，实际设置对象却是按 SYSADM principal 查出来的；备用 Email 则按 `IS_AP='1'` 查。**两者在某些公司可能是同一批人，但代码没有保证完全相同。

登录提示文案也不同：个人联系方式失败时提示找 AP / Administrator 更新；公司 Email 失败时提示到分行更新公司 Email。用户可选择下次登录继续提醒，或关闭后续提醒；关闭时调用接口把当前用户的标志改为 `N`。

依据：[C5](#c5)、[C6](#c6)。

## 4. 会不会重试、重发，如何判断成功

### 4.1 发送动作失败：框架具备重试能力

自定义 Email / SMS Dispatcher 本身没有“立即连续重试三次”这样的循环。但附带的底层框架 `FailedEventProcessorService` 包含失败动作重试逻辑，检查以下条件：

```text
动作状态是 FAILED
并且该动作允许重试（FLG_RETRY_ALLOWED = Y）
并且已重试次数 < 配置的最大次数（NUM_RETRY_CNT）
→ 执行失败动作重试
```

这是根据附带 JAR 的字节码整理的条件说明，不是 Java 源码摘录。实际启用情况还要看具体事件的数据库配置及失败事件处理器是否在运行，不能将“框架支持重试”理解成“每一条通知都已配置重试”。MNG / 电信运营商 / 邮件服务本身是否另有重试，需要查这些外部系统；BCO 仓库不能回答其具体次数。

这里存在一个实际边界：如果发送动作已经被记为 `SUCCESS`，后来 MNG 才回报 bounce，不能指望上述只针对 `FAILED` 动作的重试逻辑自动接手；后续要追的是 bounce 批次及其新生成的备用通知。

### 4.2 后续 bounce：主要是另外产生一条提醒

普通备用事件包括：

- `BOUNCE_BACK_EMAIL_ADDRESS_UPDATE_SMS`：Email 失败后，提醒更新 Email。
- `BOUNCE_BACK_MOBILE_NUMBER_UPDATE_EMAIL`：SMS 失败后，提醒更新手机号。
- `BOUNCE_BACK_COMPANY_ALT_SYSADMIN`：公司 Email 相关备用提醒。
- e-statement 等场景有另选模板的分支，以及语言版本。

`BOUNCE_BACK_ESTMT_EVENT_LIST` 决定部分 Email 退信使用哪类备用 SMS 模板，**不是整个 bounce 功能的总开关**。

注意：公司 Email 的部分发送分支虽然指定的是 `DestinationType.EMAIL`，注册的事件名仍以 `_SMS` 结尾。因此查真实渠道要结合 `NotificationDetail.destination`、recipient 配置和动作日志，不能只按事件名后缀判断。

### 4.3 几种“成功 / 已处理”不能混在一起

| 状态 | 实际含义 | 不代表什么 |
| --- | --- | --- |
| `DIGX_CZ_EMAIL_MNG.RESPONSE_STATUS='Success'` | 此次 MNG 调用按 BCO 的判断规则成功；当前代码以 `errorCode == null` 判断 | 不代表手机 / 邮箱已收到 |
| `DIGX_EP_ACN_LOG_B.FLG_PROCESS_STAT='SUCCESS'` | 框架把发送动作判为成功 | 不代表最终投递成功，也不代表客户已读 |
| 失败明细 `IS_VALSENDBOUNCENOTIF_PROCESSED='Y'` | 导入后的记录被验证批次标记为处理过 | 不保证产生了备用任务；代码存在整批标记行为 |
| 备用任务 `ISPROCESSED='Y'` | 相应分支已登记备用通知事件 / 更新处理状态 | 不代表备用通知已送达 |
| mailbox 已有记录 | 站内信已经创建 | 不代表用户登录并看过 |

因此，“PIN reset 成功”和“PIN reset 成功通知发不出去”可以同时发生。需要分别看业务结果和通知结果，不能从退信反推 PIN 操作失败。

### 4.4 发送前失败、模拟发送和 SMTP 特例

SMS 长度超限、Email 地址未找到等问题可能在调用 MNG 之前就失败，因此没有对应 MNG 记录。测试环境还要核对 `DispatchDetails.isDispatchMocked`；短信模拟发送分支会返回成功，但不会真实发送。

`TT_DISCREPANCY_REPORT_ATTACHMENT` 在当前 Email Dispatcher 中走直接 SMTP 发送，该路径没有在当前方法内创建 `EmailMNG` 记录。因此，不能承诺“所有 BCO 邮件都能通过 MNG bounce CSV 查到”。此类附件邮件需另外查看 SMTP 调用日志和邮件平台的退信处理。这里只确认这个例外，没有据此推断 SMTP 后端的退信和重试策略。

依据：[C1](#c1)、[C3](#c3)、[C5](#c5)、[C7](#c7)。

## 5. 实际排查：按这个顺序查

以下为只读 Oracle SQL，`:ref_no`、`:user_id`、`:party_id`、`:event_id` 等是绑定变量。时间参数使用 DATE / TIMESTAMP。表和列名已对照源码或 ORM；不同环境可能不在当前 schema，先确认 owner。这里没有连接运行库执行这些 SQL。

### 5.1 找原发送记录

如果知道通知参考号：

```sql
select refnumber, org_ref_no, customerid, partyid,
       eventid, activityid, actionid, alert_type,
       recipientid, response_status, cod_act_data_id,
       last_updated_date
from DIGX_CZ_EMAIL_MNG
where refnumber = :ref_no;
```

不知道参考号，可以先按用户和时间查；公司类通知也可改用 `partyid = :party_id`：

```sql
select refnumber, org_ref_no, customerid, partyid,
       eventid, alert_type, recipientid, response_status,
       cod_act_data_id, last_updated_date
from DIGX_CZ_EMAIL_MNG
where customerid = :user_id
  and last_updated_date >= :from_date
  and last_updated_date < :to_date
order by last_updated_date desc;
```

有 `Failed`，继续查动作日志；有 `Success` 但客户没收到，继续查失败回报。没有记录也不能立即判定业务没有产生通知：还可能是 recipient / template 解析失败、记录写入失败，或通知尚未执行到 Dispatcher。

### 5.2 查 MNG 后续失败原因

```sql
select src_sys_ref_num, msg_id, email_reci_addr,
       bounce_back_msg, batch_file_name, record_last_updated,
       is_valsendbouncenotif_processed
from DIGX_CZ_BATCH_BOUNCE_BACK_CCBEMAIL
where src_sys_ref_num = :ref_no;

select src_sys_ref_num, msg_id, sms_reci_mob_num,
       delivery_msg, send_dttm, batch_file_name,
       record_last_updated, is_valsendbouncenotif_processed
from DIGX_CZ_BATCH_BOUNCE_BACK_CCBSMS
where src_sys_ref_num = :ref_no;
```

Email 原因看 `BOUNCE_BACK_MSG`，SMS 原因看 `DELIVERY_MSG`。查不到失败行，只能说明当前库里没查到相应回报，不能据此证明送达；还要查文件是否收到、批次是否成功导入。

### 5.3 查这个事件有没有进入备用通知范围

```sql
select eventid
from DIGX_CZ_BATCH_HIGH_ALERT_EVENT_LIST
where eventid = :event_id;
```

针对前一个 Login PIN spike，可以一次检查这些事件：

```sql
with wanted(eventid) as (
  select 'PIN_ACTIVATION_SUCCESS' from dual union all
  select 'PIN_ACTIVATION_FAILURE_TEMPORARY_LOCKED' from dual union all
  select 'PIN_ACTIVATION_FAILURE_LOCKED' from dual union all
  select 'PIN_RESET_SUCCESS' from dual union all
  select 'PIN_RESET_FAILURE_TEMPORARY_LOCKED' from dual union all
  select 'PIN_RESET_FAILURE_LOCKED' from dual union all
  select 'RESET_PASSWORD_USING_SECURITY_QUESTION' from dual
)
select w.eventid,
       case when exists (
         select 1 from DIGX_CZ_BATCH_HIGH_ALERT_EVENT_LIST h
         where h.eventid = w.eventid
       ) then 'Y' else 'N' end as in_bounce_event_list
from wanted w;
```

在名单里只是必要条件之一，还要关联到原通知、符合用户 / party 条件，且通过历史检查。普通 BCO 查询中还存在 `M.PARTYID = 用户关联 party 子查询（rownum=1）` 的限制；多 party 数据需特别核对。

### 5.4 查有没有生成备用任务、选了什么地址

```sql
select refnumber, org_ref_no, userid, party_id, eventid,
       bounced_mode, alt_sms, alt_email, alt_office_email,
       alt_sys_adm, alt_acc_holder_ap, alt_acc_holder_ap_email,
       alt_webmail, reminder_afterlogin, isprocessed,
       processedchannelslist, record_last_updated,
       alt_process_datetime
from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE
where refnumber = :ref_no;

-- 最近 30 天是否已有相关处理；用于解释为什么没有再次通知。
select refnumber, eventid, bounced_mode,
       alt_sms, alt_email, alt_webmail, alt_office_email,
       isprocessed, record_last_updated, alt_process_datetime
from DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE
where userid = :user_id
  and (record_last_updated >= sysdate - 30
       or alt_process_datetime >= sysdate - 30)
order by record_last_updated desc;
```

第二条是方便排查的时间范围查询，代码内的具体日期转换和分支判断请对照 [C4](#c4)，不要把它当作逐字复现业务筛选。

### 5.5 查联系方式、AP 和 System Admin

```sql
select p.u_name, p.email, x.mobile_code, p.mobileno,
       x.is_ap, x.bounce_back_reminder
from DIGX_UM_USERPROFILE p
left join DIGX_CZ_UM_EXTENSIONDATA x on x.user_id = p.u_name
where p.u_name = :user_id;

select partyid, office_email
from DIGX_PI_PARTY_PREFERENCES
where partyid = :party_id;

-- 公司备用 Email 的 AP 集合
select distinct p.u_name, p.email
from DIGX_UM_USERPROFILE p
join DIGX_CZ_UM_EXTENSIONDATA x on x.user_id = p.u_name
join DIGX_UM_USERPARTY_RELATION r on r.user_id = p.u_name
where r.party_id = :party_id
  and x.is_ap = '1';

-- 公司站内信 / AP 登录标志采用的 SYSADM 集合
select distinct u.username, u.principal
from DIGX_UM_USER_PRINCIPAL u
join DIGX_UM_USERPARTY_RELATION r on r.user_id = u.username
where r.party_id = :party_id
  and u.principal like '%SYSADM%';

-- 站内信是否已经创建
select id, message_id, user_id, subject, msg_status, received_date
from DIGX_CO_MAILBOX_MAILER_USER
where user_id = :user_id
  and received_date >= :from_date
  and received_date < :to_date
order by received_date desc;
```

### 5.6 查实际发送动作与重试配置

```sql
-- 用原 MNG 记录中的 COD_ACT_DATA_ID 查询，避免仅凭 event ID 串错通知。
select cod_act_log_id, cod_act_data_id, cod_act_id,
       cod_event_id, cod_action_id, txt_dest_typ, txt_dest_addr,
       flg_process_stat, num_retry_cnt, max_retry_count,
       dat_execution, txt_comments
from DIGX_EP_ACN_LOG_B
where cod_act_data_id = :act_data_id
order by dat_execution desc;

-- 具体事件的重试开关和最大次数
select cod_act_id, cod_event_id, cod_action_id,
       flg_retry_allowed, num_retry_cnt,
       alert_type, alert_dispatch_type, object_status
from DIGX_EP_ACT_EVT_ACN_B
where cod_act_id = :activity_id
  and cod_event_id = :event_id
  and cod_action_id = :action_id;

-- 备用通知是新事件；按收件地址与时间查其新发送动作。
select cod_act_log_id, cod_act_data_id, cod_event_id,
       txt_dest_typ, txt_dest_addr, flg_process_stat,
       num_retry_cnt, dat_execution, txt_comments
from DIGX_EP_ACN_LOG_B
where cod_act_id =
  'com.ofss.digx.cz.bea.domain.scheduler.service.BatchAlertGeneric.alertBounceBack'
  and txt_dest_addr = :alternate_recipient
  and dat_execution >= :from_date
  and dat_execution < :to_date
order by dat_execution desc;
```

备用通知的 `COD_ACT_DATA_ID` 不应直接假定等于原通知的 ID。同一收件人、同一时间段若有多条结果，还要结合事件和日志逐笔核对。

同名事件可以属于不同 activity / action，所以查询具体重试配置时应使用上面三个字段。日志表的 `NUM_RETRY_CNT` 是已经使用的次数；配置表同名字段则是最大次数。

### 5.7 查模板、渠道配置及表结构

```sql
-- 发送框架轮询、失败重试间隔及模拟发送配置。
-- 查出来后结合运行环境 / entity 判断实际生效行。
select category_id, prop_id, prop_value
from DIGX_FW_CONFIG_ALL_B
where category_id = 'AlertPollerPool'
   or (category_id = 'DispatchDetails'
       and prop_id in ('isDispatchMocked', 'email.message.to.mocked'))
order by category_id, prop_id;

select prop_id, preference_name, prop_value, determinant_value
from DIGX_CZ_FW_CONFIG_ALL_O
where preference_name = 'DayOneConfig'
  and prop_id in (
    'BOUNCE_BACK_ESTMT_EVENT_LIST',
    'SMS_DISPATCHER_ALERT_EVENTID_LIST'
  );

select *
from DIGX_EP_ACT_EVT_ACN_B
where cod_act_id =
  'com.ofss.digx.cz.bea.domain.scheduler.service.BatchAlertGeneric.alertBounceBack';

select *
from DIGX_EP_EVT_REC_B
where cod_act_id =
  'com.ofss.digx.cz.bea.domain.scheduler.service.BatchAlertGeneric.alertBounceBack';

select t.*
from DIGX_EP_MSG_TMPL_B t
where t.cod_tmpl_id in (
  select r.cod_msg_tmpl_id
  from DIGX_EP_EVT_REC_B r
  where r.cod_act_id =
    'com.ofss.digx.cz.bea.domain.scheduler.service.BatchAlertGeneric.alertBounceBack'
);

-- 如果表不存在或列名有差异，先定位运行库实际结构。
select owner, table_name, column_name, data_type
from all_tab_columns
where table_name in (
  'DIGX_CZ_EMAIL_MNG',
  'DIGX_CZ_BATCH_BOUNCE_BACK_CCBEMAIL',
  'DIGX_CZ_BATCH_BOUNCE_BACK_CCBSMS',
  'DIGX_CZ_BATCH_VALIDATE_AND_SEND_BOUNCE',
  'DIGX_CZ_BATCH_HIGH_ALERT_EVENT_LIST',
  'DIGX_EP_ACN_LOG_B',
  'DIGX_EP_ACT_EVT_ACN_B'
)
order by owner, table_name, column_id;
```

`AlertPollerPool` 中值得核对的配置名包括 `EventPollerInterval`、`ep.record.failure.retry.interval`、`PollerFetchSize`、`ep.poller.processing.loop.count` 和 `SMSLength`。它们属于发送框架，不等于 bounce 文件导入批次的调度周期；本次没有把未验证的数值或单位写成环境事实。

## 6. 批次、日志和后台报表去哪找

### 6.1 文件与任务

普通 BCO / CDC 文件在脚本中命名为 `MNG_CDC_Bounce_Report_20${Rundate}_EMAIL.csv`、`MNG_CDC_Bounce_Report_20${Rundate}_SMS.csv`，从 `$Inputfile/bounceback/` 读取。库表仍保留 `CCBEMAIL` / `CCBSMS` 名称，不要因为名称不同就以为找错表。

| 检查对象 | 入口 | 要确认什么 |
| --- | --- | --- |
| 失败文件导入 | `loadMNGmsg.sh` → `LoadMNGmsgCCBEMAIL` / `LoadMNGmsgCCBSMS` | 文件有没有到、文件日期是否正确、导入是否报错、Processed / Error 目录 |
| 备用方案计算 | `ValidateAndSendBounceNotify.sh` → `ValidateAndSendBounceNotify` | 有没有匹配原通知、事件名单是否满足、有没有生成备用任务 |
| 备用 Email / SMS 事件生成 | Quartz `BatchExecutionScheduler` → `BatchAlertGeneric.alertBounceBack` | job 是否运行、有没有捞到 `ISPROCESSED='N'`、是否发生异常 |
| PIN 通知发送异常报表 | `mngExceptionReport.sh` → `MNGExceptionProcessor` | 昨日 PIN activation / reset 动作是否为 `FAILED` |

脚本会 source `/CDCBatch/sh/Env.sh`，实际路径以部署环境该文件为准。仓库样例包括 `LogFilePath=/project/CDC/log`、`Outputfile=/project/CDC/ftp/snd`；它们只能用于定位配置，不应直接当成生产路径。

批次数据库连接配置由 `$batchConfigPath/batch_config.properties` 读取；仓库 PRD 样例把 `batchConfigPath` 设为 `/CDCBatch/config`。排查时确认连接的是哪套库即可，无需将账号密码复制到 spike。输入、归档、异常目录分别查看 `Inputfile`、`Processed`、`Error`。导入器里的归档调用有被注释的情况，重跑前应核对实际调度的文件归档和数据库去重，不能假定每次重跑都不会重复入库。

`MNGExceptionProcessor` 是另一份发送异常报表，不是 bounce 导入器，也不是自动重发器。它为 PIN activation / reset 相关事件查询昨日动作失败记录，生成 `mngErrorReport` CSV。报表中 `Complete / Locked / Temporary Locked` 描述的是 PIN 业务结果；`Fail to send Email / SMS` 描述通知失败，两者不矛盾。

### 6.2 后台报表

本地后台 report-generation 配置有：

| 编号 | 报表 | 用途 |
| --- | --- | --- |
| `A220` | Daily Summary Report Of Bounce Back Notification | 查原通知的 bounce-back 情况 |
| `A221` | Alternative Notification Generation For Bounce Back Customer | 查备用通知处理情况 |

报表是否可见还受 department 映射和实际部署影响。后台用户看不到时，可先查：

```sql
select dep_id, report_id
from DIGX_RP_DEP_REPORT_MAP
where report_id in ('A220', 'A221')
order by dep_id, report_id;
```

随后核对对应 BI Publisher 模板是否部署。报表里的“处理状态”也不应直接当成送达证明；本地 `CDC469` 数据模型的 SMS `delivery_status` 实际取备用表的 `ISPROCESSED`，并不是 MNG 手机送达状态。

依据：[C8](#c8)、[C9](#c9)。

## 7. 需要优先验证的几个问题

以下是源码中看到的边界或问题，尚未通过生产数据复现，也未修改实现。

| 发现 | 可能造成什么影响 | 建议怎么验证 |
| --- | --- | --- |
| 原始失败明细存在整批 `N → Y` 更新，范围没有限定为已成功产生备用任务的记录 | “已处理”可能包含没匹配到原通知、没进名单或没有产生备用通知的记录 | 对照失败行、事件名单、备用任务逐笔查；不要只看处理标志 |
| Email 兜底中，外层允许 `smsbounced == true`，内层却要求 `smsbounced == false` | 手机已有失败历史时，这个站内信分支不会执行；不能承诺双渠道失败必有站内信 | 测试“个人 Email 退回 + 30 天内已有 SMS 失败历史” |
| 部分空字符串判断使用 Java `== ""` / `!= ""` | 判断的是对象引用，空值和空串场景可能不符合预期 | 测试手机 / Email 为 null、空串、资料已修改等情况 |
| SMS 返回结果的说明文字被统一设成 `SMS Sent Successfully` | 仅看日志文案可能误判；失败布尔值 / 数据库状态仍需单独看 | 同时核对 `isDispatchSuccessfull`、`RESPONSE_STATUS`、action status |
| 登记备用事件后立即置 `ISPROCESSED='Y'` | 后续发送失败不会仅凭这张任务表自动变回未处理 | 查备用事件 action log 和它自身的 retry 配置 |
| 根目录与 `batchJobs/PRD` 的公司 Email 规则并不完全相同 | 用错版本可能解释错“为什么发给这个人” | 比较实际部署 JAR / class 与两个源码目录；不能只凭 `PRD` 文件夹名称认定正在运行 |

PRD 与根目录的一个具体差异：对“失败公司地址与当前 office Email 不同”的情况，根目录部分分支选择当前 office Email；PRD 对相关历史组合改为 SYSADM 站内信与登录提醒，并新增兜底逻辑。[C4](#c4) 列出了两个版本的位置。

建议第一轮运行验证只选四类样本：发送接口直接失败、个人 Email 退信、个人 SMS 失败、公司 Email 退信。每类从原发送参考号追到失败文件、备用任务、备用动作和登录标志；这样可以把代码机制与实际启用配置对上。

## 8. 代码依据与摘录

路径均从 `cdc-application` 开始。行号对应本次本地副本；升级版本后可能变化。

<a id="c1"></a>
### C1. Dispatcher 对成功 / 失败的判断

`cdc-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.domain.service.dispatch/src/com/ofss/digx/cz/bea/domain/service/dispatch/EmailDispatcher.java:515–527, 541–558`

```java
// 515–527：调用后记录状态，异常也记录 Failed。
dispatchResult = convertToDispatchResult(adapter.createAlert(request), messageBody);
// ...
if (dispatchResult.getIsDispatchSuccessfull()) {
    emailMNG.setResponseStatus("Success");
} else {
    emailMNG.setResponseStatus("Failed");
}
// catch 分支：
dispatchResult.setIsDispatchSuccessfull(false);
emailMNG.setResponseStatus("Failed");

// 549–557：成功依据是 errorCode 为 null。
if (null == mead.getErrorCode()) {
    dispatchResult.setIsDispatchSuccessfull(true);
    // ...
} else {
    dispatchResult.setIsDispatchSuccessfull(false);
    dispatchResult.setMessage("Failed with error " + mead.getErrorCode());
}
```

以上为省略日志后的关键语句摘录。SMS 对应 `SMSDispatcher.java:557–595, 745–765`；同文件 `192–194` 还会无条件设置说明文字：

```java
DispatchResultDTO result = dispatchMNGSms(alertRequestDTO, data, dispatchMessage);
dispatchResult = new DispatchResult(result);
dispatchResult.setMessage("SMS Sent Successfully");
```

发送记录映射：`cdc-application/consulting/config/orm/eclipselink/mappings/cz/mng/EmailMNG.xml:3–53`。

同目录 `SMSDispatcher.java:153–165, 207–215`：模拟发送、长度限制和不安全内容分支。`EmailDispatcher.java:1391–1396, 1442–1449`：SMTP 特例和发送前校验；SMTP 调用及异常处理在 `297–327`。MNG 请求的 `sourceSysRefNumber` 分别在 Email `593`、SMS `653` 赋值。

<a id="c2"></a>
### C2. 哪些失败会进入普通 BCO 备用处理

`cdc-application/consulting/middleware/batchJobs/inboundbatchprocessor/ValidateAndSendBounceNotify.java:105–120, 251–266`

```java
+ "  FROM DIGX_CZ_EMAIL_MNG M, DIGX_CZ_BATCH_BOUNCE_BACK_CCBEMAIL C\r\n" + " WHERE M.eventid in\r\n"
+ "       (SELECT DISTINCT EVENTID FROM DIGX_CZ_BATCH_HIGH_ALERT_EVENT_LIST)\r\n"
+ "   AND M.REFNUMBER = C.SRC_SYS_REF_NUM\r\n"
+ "   AND M.PARTYID=(select party_id from digx_um_userparty_relation  where user_id=M.CUSTOMERID and rownum=1) \r\n"
+ "   AND C.IS_VALSENDBOUNCENOTIF_PROCESSED = 'N'";
```

这是拼接 SQL 的原代码片段，Email / SMS 都有相应查询。按用户 / party 抑制同批重复的分支在同文件 `164–180`。

<a id="c3"></a>
### C3. 原失败记录的处理标志

同文件 `235–237`：

```java
query = "Update DIGX_CZ_BATCH_BOUNCE_BACK_CCBEMAIL set IS_VALSENDBOUNCENOTIF_PROCESSED='Y' where IS_VALSENDBOUNCENOTIF_PROCESSED ='N'";
// System.out.println(query);
updatedRowsCount = stmt.executeUpdate(query);
```

它更新所有未处理 Email 失败记录，不能据这个 `Y` 单独证明每条都产生了备用通知。

<a id="c4"></a>
### C4. 用户备用渠道与 30 天历史

同文件 `1291–1404` 为个人 Email；`1488–1585` 为个人 SMS。Email 分支的关键片段在 `1386–1399`：

```java
if (record.getAlt_sms() != "") {
    // checking user email and sms bounce back
    if (userEmailbounced == false && smsbounced == false) {
        record.setAlt_Sms_Eligible(true);
        // ...
        setBounce_Back_ReminderUSER(conn, record, margs);
    } else {
        record.setAlt_Sms_Eligible(false);
        record.setAlt_sms("");
    }
}
```

省略了日志和局部变量声明；原始 `!= ""` 写法按源码保留。站内信分支 `1370–1384` 的外层与内层条件分别是：

```java
if (record.getAlt_sms() == "" || smsbounced == true) {
    // ...
    if (smsbounced == false) {
        // 设置 USER 登录提醒、写站内信、检查公司 Email
    }
}
```

公司 Email 分支：根目录同文件 `938–1096`；PRD 对应 `cdc-application/consulting/middleware/batchJobs/PRD/inboundbatchprocessor/ValidateAndSendBounceNotify.java:1028–1202`。个人分支的上述边界在 PRD `1510–1539` 仍可见。

<a id="c5"></a>
### C5. 备用事件生成与 AP Email 查询

`cdc-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.domain.service.dispatch/src/com/ofss/digx/cz/bea/domain/scheduler/service/BatchAlertGeneric.java:4070–4080`

```java
super.registerActivityAndGenerateEvent(sessionContext,
        THIS_COMPONENT_NAME + ".alertBounceBack",
        BatchAlertGenericConstants.BOUNCE_BACK_MOBILE_NUMBER_UPDATE_EMAIL,
        new Date(), activityLog);
// ...
bounceBack.setIsProcessed("Y");
bounceBack.setAltProcessDateTime(new Date());
domain.update(bounceBack);
```

Email → SMS 分支在同文件 `3816–3976`；office Email 分支在 `3980–4008, 4085–4114`；AP Email 在 `4158–4260`。Webmail 部分在该 service 中已注释，由前面的批次插入。

`cdc-application/consulting/config/orm/eclipselink/mappings/cz/scheduler/bounceback/BounceBack-Query.orm.xml:6–9`

```sql
select distinct prf.email
from DIGX_UM_USERPROFILE prf, digx_cz_um_extensiondata ext
where ext.user_id = prf.u_name
  and ext.is_ap = '1'
  and ext.user_id in (
    select distinct user_id
    from DIGX_UM_USERPARTY_RELATION
    where party_id = #partyId
  )
```

上面只是重新排版，`#partyId` 是原 ORM 参数写法。任务表映射在同目录 `BounceBack.orm.xml:7–75`；待处理筛选在 `BounceBack-Query.orm.xml:3–4` 及 `LocalBounceBackRepositoryAdapter.java:47–53`。

<a id="c6"></a>
### C6. SYSADM、站内信与登录提醒

`cdc-application/consulting/middleware/batchJobs/inboundbatchprocessor/ValidateAndSendBounceNotify.java:1225–1288`

```java
// 1229–1230：公司提醒对象按 SYSADM principal + party 查询。
String query = "select distinct username from digx_um_user_principal where principal like '%SYSADM%' and username in (select user_id from Digx_UM_userparty_relation where party_id = '"
        + record.getPartyid() + "') ";

// 1243：查询出的用户写 AP 标志。
innerQuery = "Update digx_cz_um_extensiondata set Bounce_Back_Reminder='AP' where user_id=?";

// 1273：用户个人联系方式提醒。
String query = "Update digx_cz_um_extensiondata set Bounce_Back_Reminder='USER' where user_id=?";
```

这些是不同方法 / 位置的独立摘录，不是一段可直接编译的代码。PRD 公司站内信选人及写入在 `cdc-application/consulting/middleware/batchJobs/PRD/inboundbatchprocessor/ValidateAndSendBounceNotify.java:885–895, 950–976`，涉及 `DIGX_CO_MAILBOX_MAILER_USER`、`DIGX_CO_MAILBOX_MAILER`、`DIGX_CO_MAILBOX_MESSAGE`。

前端提示与关闭逻辑：

- `cdc-application/consulting/channel/extensions/resources/nls/dashboard.js:62–66`：个人 / 公司提醒文案。
- `cdc-application/consulting/channel/framework/elements/core/dashboard/dashboard.js:931–954, 966–988`：CORP / CORPADMIN 读取提醒标志，选择不再提醒时传 `N`。
- `cdc-application/consulting/channel/framework/elements/core/dashboard/dashboard.html:315–334`：弹窗与 Yes / No。
- `cdc-application/consulting/channel/framework/elements/core/dashboard/model.js:46–48`：`userExtensionData/bounceBackReminder?flag={flag}`。
- `cdc-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.sms/src/com/ofss/digx/cz/bea/app/sms/service/user/UserExtensionData.java:3081–3102`：更新当前用户标志。

<a id="c7"></a>
### C7. 框架重试配置与日志

`cdc-application/consulting/config_core/orm/eclipselink/mappings/alert/maintenance/ActivityEventAction.orm.xml:24–29`

```xml
<basic attribute-type="int" name="maxRetryCount">
  <column name="NUM_RETRY_CNT"/>
</basic>
<basic attribute-type="java.lang.Boolean" name="isRetryAllowed">
  <column name="FLG_RETRY_ALLOWED"/>
  <convert>yesno</convert>
</basic>
```

动作日志映射：`cdc-application/consulting/config_core/orm/eclipselink/mappings/alert/eventGeneration/ep.ActionLog.orm.xml:5–60`。

失败重试实现依据为 `cdc-application/consulting/middleware/lib/OBDX_FW_LIB/com.ofss.fc.module.ep-2.4-SNAPSHOT.jar` 中 `com.ofss.fc.domain.ep.service.event.FailedEventProcessorService` 的字节码，核对方法包括 `processIncompleteActivty`、`processFailedAction`；调用入口为 `com.ofss.fc.app.ep.service.event.EventPoller`。JAR 没有在此处可引用的 Java 源文件行号。可用以下只读命令复查：

```sh
javap -c -p -classpath cdc-application/consulting/middleware/lib/OBDX_FW_LIB/com.ofss.fc.module.ep-2.4-SNAPSHOT.jar com.ofss.fc.domain.ep.service.event.FailedEventProcessorService
```

命令假设源码根目录实际名为 `cdc-application`；本地副本若是 `cdc-application.zip` 目录，替换这一段路径即可。`AlertPollerPool` / `DispatchDetails` 的数据库来源见 `cdc-application/consulting/config/Preferences.xml:76, 121–122`；重试次数、是否启用及处理周期以运行配置为准。

<a id="c8"></a>
### C8. 批次和定时入口

- `cdc-application/consulting/middleware/batchJobs/Deployables/sh/loadMNGmsg.sh:47–61`：CDC / EPH bounce 文件路径。
- `cdc-application/consulting/middleware/batchJobs/Deployables/sh/ValidateAndSendBounceNotify.sh:1–16`：环境加载与 Java 入口。
- `cdc-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.scheduler.impl/src/com/ofss/digx/cz/bea/scheduler/generic/BatchExecutionScheduler.java:22–23, 55–63`：Quartz job 调用。
- `cdc-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.domain.service.dispatch/src/com/ofss/digx/cz/bea/domain/scheduler/service/BatchAlertGeneric.java:195–200, 3659–3660`：bounce 调用与 e-statement 配置。
- `cdc-application/consulting/middleware/batchJobs/inboundbatchprocessor/MNGExceptionProcessor.java:38–62, 100–148, 169–191`：PIN 发送异常报表。
- `cdc-application/consulting/middleware/batchJobs/Deployables/sh/mngExceptionReport.sh:1–24`：报表运行脚本。
- `cdc-application/consulting/middleware/batchJobs/PRD/inboundbatchprocessor/GenericInBoundProcessor.java:328–344`：批次数据库配置文件读取。
- `cdc-application/consulting/middleware/batchJobs/PRD/Deployables/sh/Env.sh:2–11`：JAR、配置、输入输出目录的仓库样例。

<a id="c9"></a>
### C9. 后台报表及配置

- `cdc-application/consulting/channel/extensions/components/reports/report-generation/paramsComponent_admin.json:137–143`：A220 / A221 入口。
- `cdc-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.common/src/com/ofss/digx/cz/bea/domain/report/entity/repository/adapter/LocalReportDefinitionRepositoryAdapter.java:98–123`：按 department 筛选。
- `cdc-application/consulting/config/orm/eclipselink/mappings/cz/sms/user/UserIDMaintenance-Query.orm.xml:19–20`：department-report 映射查询。
- `cdc-application/consulting/config/resources/report/obi/CDC469_DAILY_REPORT_BOUNCEBACK.xdmz`：其中 `_datamodel.xdm:77–80` 的 SMS `delivery_status` 来自 `max(ISPROCESSED)`，且子查询未关联当前外层 SMS 行；要与运行版报表核对。
- `cdc-application/consulting/config/resources/report/obi/CDC468_ALTERNATIVE_NOTIFICATION_BOUNCEBACK_REPORT.xdmz`：备用通知报表数据模型。
- `cdc-application/consulting/config/resources/report/db/vw_alternate_notification_bounceback.sql:28–30, 60–62`：Webmail 报表来源及 `ALT_WEBMAIL='Done'` 条件。

报表压缩文件内的模型可用 `unzip -p 文件.xdmz _datamodel.xdm | nl -ba` 查看。上述行号是解压后 XML 的行号。

# BCOH2H-1293: HTH CRM daily extract

This is a separate HTH job. It reads the two BCOH2H-849 password activities (`HTH_PWD_CRD`, `HTH_PWD_UPD`) from `DIGX_CZ_CRM_EVENT3_DETAILS` with `CHNL_ID='ELE-HTH'`. It does not use or update `BATCH_PROCESSED_DATE`, and does not modify BCO output files. The BCO `GenTxnLog2CRM` query excludes HTH channel rows so that its seven-character activity field cannot consume or truncate HTH's eleven-character codes.

The CSV columns follow rows 11–95 of `API_MTB_latest(HTH_CRM_MAPPING_BCO).xlsx` in that order. The first line contains column names; even zero-record days produce this header-only file. All cells are UTF-8 RFC 4180-style quoted values with `"` escaped by `""`, and LF line endings. The `Event_Time` field is formatted `HH:mm:ss` as in the existing BCO extraction. This job extracts one business date in the Hong Kong timezone and has no `00`/`99` envelope records. Confirm the final filename and file envelope with the downstream receiver before production handoff.

## Build and run

Use Java 8 or newer and an Oracle JDBC driver compatible with the server. From this directory:

```sh
./build.sh
export HTH_CRM_JDBC_JAR=/path/to/ojdbc8.jar
export HTH_CRM_JDBC_URL='jdbc:oracle:thin:@//host:port/service'
export HTH_CRM_JDBC_USER='...'
export HTH_CRM_JDBC_PASSWORD='...'
export HTH_CRM_OUTPUT_DIR=/path/to/staging
./run-hth-crm-extract.sh                   # previous HKT business day
./run-hth-crm-extract.sh --date 20261007   # authorized date rerun
```

Set the password in the scheduler's protected environment or secret store; do not put it in command arguments, scripts, logs, or Git. Optional `HTH_CRM_CLASS_DIR` overrides the compiled class directory. Optional `HTH_CRM_FILE_PATTERN` changes `HTH_CRM_{date}.csv`; it must contain `{date}` and resolve to a `.csv` basename. The date makes reruns replace only that date's file. Output and `.lock` files stay in the configured staging directory. The output filesystem must support atomic rename.

Configure an **HTH-specific** external scheduler for the approved daily HKT time. For example, if the approved time is 02:00 HKT, schedule `run-hth-crm-extract.sh` at 02:00 with `TZ=Asia/Hong_Kong` and a protected environment. Do not attach this job to `genTxnLog2CRM.sh`. A nonzero exit is a failure and should trigger the scheduler's manual-intervention alert. Start/end log lines include status, date, count, filename and failure reason. The job uses a date-specific lock to reject concurrent runs for the same date. It writes a temporary file first and atomically replaces the target only after extraction succeeds; failed reruns leave the previous file intact.

## Database check

The job's JDBC user needs `SELECT` on `DIGX_CZ_CRM_EVENT3_DETAILS`; no `UPDATE` privilege is needed. Before a run, check the source count:

```sql
SELECT EVENT_DTE, EVENT_ACTV_TYPE_CODE, COUNT(*)
FROM DIGX_CZ_CRM_EVENT3_DETAILS
WHERE EVENT_DTE = '20261007'
  AND CHNL_ID = 'ELE-HTH'
  AND EVENT_ACTV_TYPE_CODE IN ('HTH_PWD_CRD', 'HTH_PWD_UPD')
GROUP BY EVENT_DTE, EVENT_ACTV_TYPE_CODE;
```

After the run, compare the sum with `count=` in `HTH_CRM_1293 stage=END status=SUCCESS` and the number of CSV data records. Verify the file begins with the 85-column header and the correct date's rows. An empty date has `count=0` and one header line. A rerun of the same date yields one replacement file and leaves other dates untouched.

# ACS Database Pod Terminal Guide

Use the existing database Pods in `cms-dev` to inspect CMS and IMK data and check database health. Open the MySQL Pod terminal for SQL queries and the OpenSearch Pod terminal for HTTP API queries. These procedures do not require an additional toolbox image or an ACR push.

The examples use the project's MySQL 8.0 and OpenSearch 1.3.19 deployment configuration. Confirm the running versions with the commands below. Database queries and HTTP requests in this guide read existing data; MySQL session settings affect only the current connection.

## Choose the terminal

| Item | MySQL | OpenSearch |
|---|---|---|
| Namespace | `cms-dev` | `cms-dev` |
| Pod | `shared-mysql-0` | `shared-opensearch-0` |
| Container | `shared-mysql` | `shared-opensearch` |
| Local endpoint inside that Pod | `127.0.0.1:3306` or local socket | `http://127.0.0.1:9200` |
| Query tool | `mysql` | `curl` |
| Data organization | Databases, tables and rows | Indexes and JSON documents |

Open the supplied [MySQL Pod page](https://acs.console.alibabacloud.com/#/k8s/cluster/ce9cd35192ee148c79d14bf6ac7efd012/v2/workload/pod/cms-dev/shared-mysql-0/container?type=pod&clusterType=ManagedKubernetes&profile=Acs&state=running&region=cn-hongkong&ns=cms-dev) or [OpenSearch Pod page](https://acs.console.alibabacloud.com/#/k8s/cluster/ce9cd35192ee148c79d14bf6ac7efd012/v2/workload/pod/cms-dev/shared-opensearch-0/container?type=pod&clusterType=ManagedKubernetes&profile=Acs&state=running&region=cn-hongkong&ns=cms-dev), select the matching container, and open its terminal. Select `/bin/sh`; `/bin/bash` is also usable if available. Terminal access requires the relevant ACS and Kubernetes permissions.

Three command locations appear in this guide:

| Location | How to recognize it | Commands to run there |
|---|---|---|
| **Pod shell** | The container's shell prompt, commonly ending in `$` or `#` | `mysql`, `curl`, `hostname` |
| **MySQL client** | `mysql>` after login | `SELECT`, `SHOW`, `DESCRIBE`, `exit;` |
| **Workbench** | The Alibaba Cloud Workbench terminal | `kubectl` commands in the final section |

Copy command blocks without adding the prompt. The loopback address `127.0.0.1` refers to the Pod whose terminal you opened; it does not refer to another Pod or your Mac. OpenSearch uses REST requests in the shell, so do not paste MySQL SQL statements into its terminal.

## Connect to MySQL

**Location: MySQL Pod shell.** Confirm the container and client:

```sh
hostname
mysql --version
```

The deployment injects CMS credentials as `MYSQL_USER`, `MYSQL_PASSWORD` and `MYSQL_DATABASE`, and IMK credentials as `IMK_DB_USER`, `IMK_DB_PASSWORD` and `IMK_DB_NAME`. The following commands use those values without displaying the passwords. The parentheses keep command settings inside a subshell and return you to the Pod shell when you exit MySQL.

### Open the CMS database

**Location: MySQL Pod shell.**

```sh
(
  set +x
  : "${MYSQL_USER:?CMS user is not injected into this container}"
  : "${MYSQL_PASSWORD:?CMS password is not injected into this container}"
  : "${MYSQL_DATABASE:?CMS database name is not injected into this container}"
  MYSQL_HISTFILE=/dev/null MYSQL_PWD="$MYSQL_PASSWORD" \
    mysql --protocol=TCP -h 127.0.0.1 -P 3306 \
    --connect-timeout=5 --default-character-set=utf8mb4 \
    -u "$MYSQL_USER" --database="$MYSQL_DATABASE" \
    --init-command='SET SESSION TRANSACTION READ ONLY'
)
```

### Open the IMK database

First leave an existing MySQL session with `exit;`. Then run this in the **MySQL Pod shell**:

```sh
(
  set +x
  : "${IMK_DB_USER:?IMK user is not injected into this container}"
  : "${IMK_DB_PASSWORD:?IMK password is not injected into this container}"
  : "${IMK_DB_NAME:?IMK database name is not injected into this container}"
  MYSQL_HISTFILE=/dev/null MYSQL_PWD="$IMK_DB_PASSWORD" \
    mysql --protocol=TCP -h 127.0.0.1 -P 3306 \
    --connect-timeout=5 --default-character-set=utf8mb4 \
    -u "$IMK_DB_USER" --database="$IMK_DB_NAME" \
    --init-command='SET SESSION TRANSACTION READ ONLY'
)
```

The injected accounts may have write permissions. The initialization statement makes subsequent transactions read-only in this connection; it does not change the account's grants. A dedicated read-only database account provides a stronger access boundary. See the [MySQL transaction scope documentation](https://dev.mysql.com/doc/refman/8.0/en/set-transaction.html).

`MYSQL_PWD` is a process environment variable. Keep shell tracing disabled and avoid printing environment variables or including credentials in screenshots or tickets. If the injected credentials are missing, use an existing authorized account with the password prompt instead; replace both placeholder values:

```sh
MYSQL_HISTFILE=/dev/null mysql --protocol=TCP -h 127.0.0.1 -P 3306 \
  --connect-timeout=5 --default-character-set=utf8mb4 \
  -u YOUR_READ_ONLY_USER -p --database=YOUR_DATABASE \
  --init-command='SET SESSION TRANSACTION READ ONLY'
```

Enter the password only when prompted. Do not append it to `-p`. The [MySQL client documentation](https://dev.mysql.com/doc/refman/8.0/en/mysql-command-options.html) describes password prompting and connection initialization.

## Check MySQL health and browse tables

**Location: `mysql>` after either login.**

```sql
SELECT 1 AS connection_ok, VERSION() AS mysql_version,
       DATABASE() AS selected_database, CURRENT_USER() AS authenticated_account;

SELECT @@session.transaction_read_only AS session_read_only;
SET SESSION max_execution_time = 5000;

SHOW TABLES;
SHOW GLOBAL STATUS
WHERE Variable_name IN ('Uptime', 'Threads_connected', 'Threads_running', 'Questions');
```

`connection_ok = 1` confirms that an authenticated SQL query succeeded. `session_read_only = 1` confirms the connection's read-only default. `Threads_connected` is a current count; `Questions` is cumulative and is not a requests-per-second measurement. The execution-time setting bounds supported SELECT execution in this session; it is not a general timeout for every statement.

For an overview of the selected database:

```sql
SELECT TABLE_NAME, ENGINE, TABLE_ROWS,
       ROUND((DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024, 2) AS size_mb
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME
LIMIT 100;
```

For InnoDB, `TABLE_ROWS` is an estimate. Prefer a query filtered by an indexed ID when inspecting business data, and keep a small result limit.

### Inspect CMS content

**Location: `mysql>` in the CMS database.** Check the table definition before querying fields:

```sql
SHOW TABLES LIKE 'acms_composer';
DESCRIBE acms_composer;

SELECT id, title, language, status, publish_date, gmt_modified
FROM acms_composer
ORDER BY id DESC
LIMIT 20;
```

This lists content records ordered by ID. Interpret publication and workflow status using the corresponding CMS screen and application rules. A row in this table alone does not establish that IMK or MCDP received its latest approved content.

### Inspect IMK banners and channel configuration

**Location: `mysql>` in the IMK database.**

```sql
SHOW TABLES LIKE 'banner';
DESCRIBE banner;

SELECT ID, TASK_NAME, USE_CHANNEL, ACTIVITY_ID,
       IS_SEND, ONLINE_STATUS, START_TIME, END_TIME
FROM banner
ORDER BY ID DESC
LIMIT 20;

SELECT CONFIG_KEY, CONFIG_VALUE
FROM sys_config
WHERE CONFIG_KEY IN ('adAimId', 'obkAdAimId')
ORDER BY CONFIG_KEY;
```

The project's requested activity-unit mapping is `adAimId = 182` for BCM and `obkAdAimId = 181` for BCO. Compare the returned values with the intended configuration. These are activity-unit IDs, not individual Banner activity IDs. `ACTIVITY_ID` links a Banner to its MCDP activity; IMK's local `ONLINE_STATUS` alone does not verify the activity's current state in SaaS.

Inspect materials for one Banner by replacing `123` with an ID returned above:

```sql
SELECT ID, BANNER_ID, MATERIALID, URL
FROM material
WHERE BANNER_ID = 123
ORDER BY ID
LIMIT 20;
```

Inspect table definitions first if a column is missing. Keep these checks to `SELECT`, `SHOW` and `DESCRIBE`; do not change publication status, activity IDs or stored material directly to repair application synchronization.

### Exit MySQL

**Location: `mysql>`.**

```sql
exit;
```

If the prompt is `->`, MySQL is waiting for the rest of a statement. Type `\c` to clear the unfinished input. After `exit;`, you are back in the Pod shell; `exit` at that shell closes the terminal session without deleting the Pod.

### Administrator access

For administrators who already have permission to use the injected root credential, the following opens a local socket connection. Use this only for an inspection that the application account cannot perform; it is not needed for the normal examples above.

**Location: MySQL Pod shell.**

```sh
(
  set +x
  : "${MYSQL_ROOT_PASSWORD:?Root password is not injected into this container}"
  MYSQL_HISTFILE=/dev/null MYSQL_PWD="$MYSQL_ROOT_PASSWORD" \
    mysql --protocol=socket -u root \
    --init-command='SET SESSION TRANSACTION READ ONLY'
)
```

The Linux user shown in the shell prompt and the MySQL authenticated account are separate identities.

## Check OpenSearch health

**Location: OpenSearch Pod shell.**

```sh
hostname
command -v curl
OS_URL='http://127.0.0.1:9200'
curl --noproxy '*' -fsS --connect-timeout 3 --max-time 15 "$OS_URL/?pretty"
```

The response identifies the node, cluster and running version. The HTTP examples match the project configuration, which disables the OpenSearch security plugin. If this environment has since enabled HTTPS or authentication, use the configured HTTPS endpoint, trusted CA and authorized credentials instead. Do not disable TLS verification or change the security plugin to make an inspection work.

Check shard allocation health and node status:

```sh
curl --noproxy '*' -fsS --connect-timeout 3 --max-time 15 \
  "$OS_URL/_cluster/health?pretty"

curl --noproxy '*' -fsS --connect-timeout 3 --max-time 15 \
  "$OS_URL/_cat/nodes?v"

curl --noproxy '*' -fsS --connect-timeout 3 --max-time 15 \
  "$OS_URL/_cat/shards?v&h=index,shard,prirep,state,unassigned.reason"
```

| Cluster status | Meaning | How to proceed |
|---|---|---|
| `green` | All primary and replica shards are allocated | Continue with a query against the required index |
| `yellow` | Primary shards are allocated; some replicas are not | Check unassigned shards and replica counts. A single-node deployment can have this state when replicas are requested |
| `red` | At least one primary shard is unassigned | Identify affected indexes and inspect logs before relying on search results |

HTTP success means the API responded. Inspect the JSON `status`, `timed_out` and shard details as well. See the [OpenSearch Cluster Health API](https://docs.opensearch.org/latest/api-reference/cluster-api/cluster-health/).

## Browse OpenSearch indexes and documents

**Location: OpenSearch Pod shell.** Keep using `OS_URL` from the preceding section; define it again after reopening the terminal.

List indexes and aliases:

```sh
curl --noproxy '*' -fsS --connect-timeout 3 --max-time 15 \
  "$OS_URL/_cat/indices?v&h=health,status,index,pri,rep,docs.count,store.size&s=index"

curl --noproxy '*' -fsS --connect-timeout 3 --max-time 15 \
  "$OS_URL/_cat/aliases?v"
```

Choose the exact business index returned by that list. Replace the placeholder below; leave system indexes unchanged.

```sh
INDEX='REPLACE_WITH_EXACT_INDEX_NAME'

curl --noproxy '*' -fsS --connect-timeout 3 --max-time 15 \
  "$OS_URL/$INDEX/_mapping?pretty"

curl --noproxy '*' -fsS --connect-timeout 3 --max-time 15 \
  "$OS_URL/$INDEX/_count?pretty"

curl --noproxy '*' -fsS --connect-timeout 3 --max-time 15 \
  "$OS_URL/$INDEX/_search?size=5&timeout=5s&pretty"
```

The mapping describes fields; the count API counts matching top-level documents; the search returns at most five documents. CAT `docs.count` can include nested Lucene documents, so it can differ from `_count`. See the [CAT Indices API](https://docs.opensearch.org/latest/api-reference/cat/cat-indices/).

To look up a known OpenSearch document ID, replace `REPLACE_WITH_DOCUMENT_ID` in this read-only search. An OpenSearch `_id` is not necessarily the CMS business ID.

```sh
curl --noproxy '*' -fsS --connect-timeout 3 --max-time 15 \
  -X GET "$OS_URL/$INDEX/_search?pretty" \
  -H 'Content-Type: application/json' \
  --data-binary '{
    "size": 5,
    "timeout": "5s",
    "query": {"ids": {"values": ["REPLACE_WITH_DOCUMENT_ID"]}}
  }'
```

Check `timed_out` and `_shards.failed` as well as `hits`. An empty successful result means this query found no matching document; it does not by itself identify a synchronization failure. See the [Search API](https://docs.opensearch.org/latest/api-reference/search-apis/search/). Avoid copying document bodies containing customer or confidential data into troubleshooting tickets.

## Use Workbench when the Pod terminal is unavailable

**Location: Workbench.** These commands enter the same containers. Run one terminal command at a time.

```sh
kubectl -n cms-dev get pods shared-mysql-0 shared-opensearch-0 -o wide

kubectl -n cms-dev exec -it shared-mysql-0 -c shared-mysql -- sh
```

After leaving that shell, open OpenSearch with:

```sh
kubectl -n cms-dev exec -it shared-opensearch-0 -c shared-opensearch -- sh
```

For a quick OpenSearch health check without an interactive shell:

```sh
kubectl -n cms-dev exec shared-opensearch-0 -c shared-opensearch -- \
  curl --noproxy '*' -fsS --connect-timeout 3 --max-time 15 \
  'http://127.0.0.1:9200/_cluster/health?pretty'
```

For Pod readiness, Service endpoints and recent database logs:

```sh
kubectl -n cms-dev get svc shared-mysql shared-opensearch
kubectl -n cms-dev get endpointslices -l kubernetes.io/service-name=shared-mysql
kubectl -n cms-dev get endpointslices -l kubernetes.io/service-name=shared-opensearch
kubectl -n cms-dev logs shared-mysql-0 -c shared-mysql --since=15m --tail=100
kubectl -n cms-dev logs shared-opensearch-0 -c shared-opensearch --since=15m --tail=100
```

A local database check covers that Pod's database process. To check the existing CMS application's reported dependencies, run this in **Workbench**:

```sh
kubectl -n cms-dev exec deployment/cms-admin -c cms-admin -- \
  curl --noproxy '*' -fsS --connect-timeout 3 --max-time 10 \
  'http://127.0.0.1:8115/cms/health'
```

The expected CMS response has `code: 0` and `Service`, `Redis` and `DB` set to `Y`. This endpoint does not establish OpenSearch index completeness or MCDP delivery status.

## Troubleshooting

| Symptom | Check or action |
|---|---|
| `mysql: not found` | Confirm you opened container `shared-mysql` in Pod `shared-mysql-0` |
| A required credential variable is missing | Confirm the Pod and container. Use an existing authorized account or have the operator check Secret references without printing their values |
| MySQL `Access denied` | Check the database account, password and host grants. Do not reset credentials as part of inspection |
| Database or table is missing | Run `SELECT DATABASE();` and `SHOW TABLES;`; confirm you selected the CMS or IMK database intended |
| SQL text gives `sh: SELECT: not found` | Log in to the MySQL client first; SQL belongs at the `mysql>` prompt |
| MySQL prompt stays at `->` | Finish with `;`, or enter `\c` to discard the unfinished statement |
| OpenSearch connection refused | Confirm the OpenSearch Pod, localhost port and readiness; inspect recent logs |
| OpenSearch HTTP `401` or `403` | Use the environment's configured authentication and permissions |
| OpenSearch certificate or HTTPS error | Confirm protocol, server name and trusted CA; retain certificate verification |
| OpenSearch HTTP `404` for an index | List indexes and aliases again and replace the placeholder with an existing name |
| OpenSearch `yellow` | Inspect replica allocation. Do not change replica settings solely to change the displayed color |
| Local query succeeds but the application fails | Check the application's Service DNS, endpoint readiness, credentials and network path |

The existing middleware manifests use `emptyDir` storage. Confirm live persistence before any Pod replacement: do not delete Pods, restart StatefulSets or change database/index settings to perform these checks. A terminal session and the read-only commands above do not require a restart.

## Configuration references

- [Shared middleware deployment](yaml/10-shared-middleware-default-ephemeral.yaml) — container names, environment variable references and original database settings. Its original namespace is `default`; this guide targets the supplied Pods in `cms-dev`.
- [Namespace migration procedure](scripts/migrate-shared-middleware-to-cms-dev.sh) — migration context; do not execute it for routine inspection.
- [IMK schema](../../beaimk/deploy/acs/imk-demo-seed-image/schema-and-demo.sql) — Banner and material fields used in the examples.
- [CMS content mapper](../../beacms/beacms-service-v251203/acms-admin/src/main/resources/mapper/ComposerMapper.xml) — CMS content fields used in the examples.

Prepared on 13 October 2026. Live query results should be recorded at the time of inspection, together with the namespace, Pod name and running version.

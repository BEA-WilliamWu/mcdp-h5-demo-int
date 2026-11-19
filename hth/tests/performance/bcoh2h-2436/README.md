# BCOH2H-2436 JMeter test pack

This pack exercises S1–S6 against `POST /account/v1/transaction-inquiry`. It changes no application code. One scenario is selected per run, so results and environments are not mixed. Tested engine version and fixture results are recorded in VALIDATION.md.

## Requirements and authentication

Use Apache JMeter 5.6.3 with Java 17 or 21 (Java 21 recommended for consistency with the project). The JMX embeds all Groovy scripts and needs no JMeter plugins or Python at runtime. `scripts/` and `build_plan.py` are editable source; run `python build_plan.py` after edits to regenerate the JMX.

Copy `environment.example.properties` to `environment.local.properties`. Configure the environment origin, endpoint including gateway context path, account, dates and independently verified expected count. Use forward slashes in Windows paths. `account` is the CASA account number, not a login username. There is no login/password flow; supply an authorized token.

Default `auth_mode=signed` follows the inspected BFF contract:

- Authorization: configurable prefix (default Bearer) plus token.
- Channel: HTH; a fresh Message-Id UUID on every page.
- Client-Id, Request-Time in epoch milliseconds, and fresh Request-Sign.
- SHA256withRSA over UTF-8 `POST\n<signing_path>\n\n<request_time>\n<exact JSON body>`, encoded as Base64.
- RSA private key: PKCS8 PEM (`BEGIN PRIVATE KEY`), with its public key already registered for the client.

Set `signing_path` if the gateway removes a prefix before the BFF sees the request. The script signs every page separately. It does not obtain/refresh tokens, provision keys or implement DPoP. Use a token valid for the complete run. Where a gateway requires DPoP or additional dynamic authentication, supply `auth_hook` pointing to an environment-specific Groovy script; it has `sampler`, `vars`, `props` and the prepared header manager available. Validate that integration with one request before load testing.

`auth_mode=token` is only for a route whose documented contract needs no BFF request signature, or the local fixture. It does not disable any server validation. `headers_file` adds static gateway headers; do not duplicate Authorization, Content-Type or generated signature headers. Hooks must update headers deliberately and must not change the already signed body without recomputing its signature.

Environment variable alternatives: `HTH_ACCOUNT`, `HTH_TOKEN`, `HTH_CLIENT_ID`, `HTH_PRIVATE_KEY_FILE`. Credentials and private keys must remain outside version control. JTL configuration excludes request/response payloads and headers; do not enable debug payload logging for real tokens.

## Windows GUI quick start for S1, S5 and S6

1. Set `HTH_TOKEN` to the access token without its Bearer prefix. For the signed BFF route, also set `HTH_CLIENT_ID` and `HTH_PRIVATE_KEY_FILE` to the registered client ID and its PKCS8 PEM key path. Start a new JMeter process that inherits these variables. If you set them in PowerShell, launch `jmeter.bat` from that same window. If you set Windows user variables, fully exit JMeter and relaunch after they take effect.
2. In JMeter, select **File → Open** and open `BCOH2H-2436.jmx`. Select the **User Defined Variables** node under the test plan.
3. In the Value column of the `scenario` row, enter `S1`, `S5` or `S6`. The plan chooses the thread count automatically.

   | Scenario | Threads | Dates | Expected count per traversal |
   | --- | --- | --- | --- |
   | S1 | 1 | Set `to_date` and leave `from_date` blank | Exact total, over 100 |
   | S5 | 5 | Set `to_date` and leave `from_date` blank to start on the previous day, or enter both consecutive dates | Exact two-day total, over 200 |
   | S6 | 10 | Same date configuration as S5 | Same two-day total as S5 for a comparable run |

   Use YYYY-MM-DD. For S5 and S6 verify that each day has over 100 records. Enter the expected total for one traversal of the account with the selected filters. Each thread uses the same total and maintains its own cursor.

4. Fill `base_url` with the SIT origin (protocol, hostname and optional port) and `account` with the 14- or 15-digit test account. Adjust `endpoint` for any gateway prefix. Fill `account_type` and `currency` if the request needs those filters. Set `signing_path` only if the BFF verifies a different path from the external endpoint.
5. Set the workload in the same **User Defined Variables** table.

   | Variable | Default | Meaning |
   | --- | ---: | --- |
   | `ramp_seconds` | 1 | Seconds across which JMeter starts the threads. Use 0 to start them without ramp-up delay. |
   | `warmup_journeys` | 0 | Complete warm-up traversals per thread. Use a nonnegative integer. |
   | `measured_journeys` | 1 | Complete measured traversals per thread after that thread finishes warm-up. Use a positive integer. |

   For example, set `ramp_seconds=60`, `warmup_journeys=5` and `measured_journeys=30`. Each thread runs 35 traversals, for 150 measured traversals in S5 or 300 in S6 if all threads finish. Threads can be in different phases at the same time.

6. Clear previous samples and click **Start**. Open **Measured pages - Aggregate Report** under **Follow server cursor → Measured pages → Page request** to view the measured workload. For a short diagnostic run, add **Listener → View Results Tree** under **Selected scenario**. The `records` in each successful journey summary should equal `expected_count`. A failed journey stops the affected thread by default, so compare completed counts with the configuration.

This workflow does not require `-q`. Explicit nonblank JMeter properties supplied through `-J` or `-q` take precedence over GUI values. Authorization always comes from `HTH_TOKEN`; there is no token field in the JMX. Keep local account values out of the shared JMX if you save the plan.

### View and export measured results

1. Use **Run → Clear All** before starting each run so that results from separate runs stay separate.
2. In **Measured pages - Aggregate Report**, read the `S5 measure page` or `S6 measure page` row. The listener receives only measured HTTP page requests, including failed requests and assertion failures. Warm-up requests and journey summaries remain available to listeners at the thread-group level.
3. Read **95% Line** for P95 latency in milliseconds, **Throughput** for request rate (check the unit displayed in the GUI), and **Error %** for the share of failed page requests.
4. Keep **Save Table Header** selected. Click **Save Table Data** and save the table as CSV, for example `S5-measured-pages.csv`. JMeter exports throughput as requests per second.

The table measures individual HTTP page requests. Use the `journey_ms` field described below for the elapsed time of a complete traversal. Warm-up runs per thread, so a thread can still be warming up while another thread is in its measured phase.

## Windows command-line execution

Open PowerShell in this directory. Put environment-specific nonsecret properties in `environment.local.properties`; set credentials in the process environment or your approved credential mechanism:

```powershell
$env:HTH_ACCOUNT = 'YOUR_14_OR_15_DIGIT_ACCOUNT'
$env:HTH_CLIENT_ID = 'YOUR_CLIENT_ID'
$env:HTH_PRIVATE_KEY_FILE = 'C:/secure/client.pk8.pem'
# HTH_TOKEN must already contain your authorized access token.
New-Item -ItemType Directory -Force results | Out-Null
jmeter.bat -n -t BCOH2H-2436.jmx -q environment.local.properties `
  -Jscenario=S1 -Jto_date=2026-11-18 -Jexpected_count=121 `
  -Jjourneys=35 -Jwarmup_journeys=5 `
  -l results/S1.jtl -j results/S1.log
```

Example dates/counts below are placeholders. Replace them with independently reconciled data. Explicit `from_date` in the local properties overrides scenario-derived dates, so leave it blank when using defaults.

| Scenario | Users | Date defaults | Example overrides |
| --- | --- | --- | --- |
| S1 | 1 | to_date only | `-Jscenario=S1 -Jexpected_count=121` |
| S2 | 1 | to_date minus one day through to_date | `-Jscenario=S2 -Jexpected_count=242` |
| S3 | 1 | Two-day range unless from_date supplied | `-Jscenario=S3 -Jpage_size=50 -Jexpected_count=242` |
| S4 | 1 | to_date minus four days through to_date | `-Jscenario=S4 -Jexpected_count=605 -Jjourneys=5` |
| S5 | 5 | Two-day range | `-Jscenario=S5 -Jexpected_count=242 -Jramp_seconds=60 -Jjourneys=35 -Jwarmup_journeys=5` |
| S6 | 10 | Two-day range | `-Jscenario=S6 -Jexpected_count=242 -Jramp_seconds=60 -Jjourneys=35 -Jwarmup_journeys=5` |

When the `journeys` property is set, it takes precedence over `measured_journeys` and defines the total traversals per thread, including the first `warmup_journeys`. Clear that property to use the sum of the two phase counts. Each thread maintains independent cursor, counters and fingerprints. All threads use the same account and token for the contention workload. Adjust journey counts after a pilot and report the actual measured duration.

For page-size comparison variants use S1 or S2 with `-Jpage_size=10` or `25`. S3 deliberately requires 50. Maximum concurrency is fixed by scenario and runs do not overlap scenarios automatically. Pause is between journeys only (`pause_ms`), not pages. Baseline defaults are one journey, no warm-up, one-second ramp and one-second pause. For real load testing, agree ramp, sample size and downstream limits with the environment owner.

## Pagination and checks

The first request omits cursor. Subsequent requests send the server's cursor unchanged. The script validates HTTP success, business code `000`, account, pagination schema, actual page count, requested size, dates, optional currency and descending dates. It fails on:

- Missing/invalid (non-nine-digit) or repeated next cursor, including cycles.
- Empty page claiming more data.
- Returned total greater than expected or a final page before expected_count is reached.
- Exact repeated transaction rows (SHA256 fingerprint of sorted JSON fields).
- Maximum page count or journey deadline reached before valid completion.

An exact row fingerprint is a diagnostic, not proof of global transaction identity. Equal totals cannot detect every missing/replaced transaction. Independently reconcile record identities with host/DB evidence for final acceptance. If indistinguishable but legitimate rows exist, review evidence before setting `check_duplicates=false`; that removes duplicate checking, not the need for reconciliation.

`expected_count` is mandatory. S1 requires >100 and S2/S5/S6 require >200; validate each day's >100 prerequisite independently. S4 requires five calendar dates. The count must remain stable during traversal, or use an agreed controlled dataset/cutoff. A changing live account can fail reconciliation legitimately.

Default `stop_on_failure=true` stops the affected thread after its failed journey summary; it does not silently retry. Other threads may finish. Track attempted/completed journeys: loss of threads invalidates claims of sustained target concurrency. If diagnostics require continuing failed journeys, explicitly use false and retain failures in statistics.

`max_journey_ms` is checked after responses; a single in-flight request is bounded separately by connect/response timeouts. It is not a hard interruption deadline. Stop a load run normally and drain threads when collecting complete-journey results; forced termination can omit summaries, so reconcile page journey IDs against summary IDs.

## Results

Use a new results filename for each run. JTL sample labels distinguish `Sx warmup page`, `Sx measure page`, and corresponding `journey` summaries. Page samples measure HTTP response time; the `journey_ms` column on journey summaries includes preparation, signing, validation and all pages, excluding between-journey pauses. The native `elapsed` of a summary is only script execution time; do not use it for journey latency. Initialization samples are ignored on success; a configuration failure remains visible and stops the thread.

Filter `measure page` for page latency and request throughput; filter `measure journey` for full traversal latency and completion rate. Do not calculate a combined throughput/latency aggregate over both: summary samples overlap the page timings. Save phase/scenario/journey/correlation variables using the supplied properties. Use `summarize.py` to separate these populations:

```powershell
python summarize.py results/S1.jtl
```

The summary is diagnostic, not an automatic performance verdict. Report sample sizes, raw failures, P95 and maximum, and reconcile missing journey summaries. No SLA threshold has been assumed. Existing backend monitoring must supply BG3 call times/counts, SQL plans and resource metrics; JMeter cannot infer them from response time.

## Local verification

`mock_server.py` is a synthetic localhost-only fixture, not a performance benchmark. It returns 101 records for one day and 201 for a multi-day range, with page-size-aware nine-digit cursors. Endpoints `/early`, `/repeat`, `/empty`, `/business` and `/http-error` inject failures.

```powershell
python mock_server.py
# In a second terminal:
$env:HTH_TOKEN = 'fixture-only'
jmeter.bat -n -t BCOH2H-2436.jmx -q environment.example.properties `
  -Jbase_url=http://127.0.0.1:18086 -Jendpoint=/ok -Jauth_mode=token `
  -Jaccount=015000000000001 -Jscenario=S1 `
  -Jto_date=2026-11-18 -Jexpected_count=101 -Jpause_ms=0 `
  -l results/mock.jtl -j results/mock.log
```

For negative cases change endpoint and expect a failed page/journey, not a process exit error: JMeter may exit zero despite sample failures. Inspect the JTL. This fixture does not verify gateway authorization, host correctness, production TLS or response-time targets.

Reference: Apache JMeter component documentation: https://jmeter.apache.org/usermanual/component_reference.html

Run the automated functional matrix (requires Python 3, OpenSSL and JMeter):

```powershell
python verify_fixture.py --jmeter "C:/tools/apache-jmeter-5.6.3/bin/jmeter.bat"
```

The verifier launches its own ephemeral localhost server and checks JTL content rather than trusting the JMeter exit code. It generates a temporary RSA key and verifies every signed request independently with OpenSSL. These keys are fixture-only and are never used against a real environment.

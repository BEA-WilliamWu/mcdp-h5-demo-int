# Functional verification

Verified on 19 November 2026 using Apache JMeter 5.6.3 and Java 21 on macOS, against the bundled synthetic localhost fixture. Windows commands are provided but were not executed on Windows. No SIT, bank gateway, BG3 or database calls were made.

| Case | Page samples | Journey summaries | Expected outcome | Observed |
| --- | --- | --- | --- | --- |
| S1, 101 records | 3 | 1 | Complete | Pass |
| S2, 201 records | 5 | 1 | Complete | Pass |
| S3, page size 50 | 5 | 1 | Complete | Pass |
| S4, five-date range | 5 | 1 | Complete | Pass |
| S5, five independent threads | 25 | 5 | Complete for each thread | Pass |
| S6, ten independent threads | 50 | 10 | Complete for each thread | Pass |
| Premature final page | 1 | 1 | Failed journey | Detected |
| Repeated cursor | 2 | 1 | Failed journey | Detected |
| Empty continuation page | 1 | 1 | Failed journey | Detected |
| Business error with HTTP 200 | 1 | 1 | Failed journey | Detected |
| HTTP 500 | 1 | 1 | Failed journey | Detected |
| Maximum page guard | 1 | 1 | Failed journey | Detected |
| Journey deadline guard | 1 | 1 | Failed journey | Detected |
| One warm-up plus one measured journey | 6 | 2 | Complete, phases separated | Pass |
| RSA signing over each exact request body | 3 | 1 | All page signatures verified with OpenSSL | Pass |
| S1 configured through User Defined Variables, credentials from the environment | 3 | 1 | Complete signed traversal without an input properties file | Pass |
| S5 selected through User Defined Variables, credentials from the environment | 25 | 5 | Five distinct threads complete signed traversals without an input properties file | Pass |
| S6 selected through User Defined Variables, credentials from the environment | 50 | 10 | Ten distinct threads complete signed traversals without an input properties file | Pass |
| S5 with GUI variables for a 3-second ramp-up, 1 warm-up and 2 measured traversals per thread | 75 | 15 | Five warm-up and ten measured traversals, with the configured ramp-up in the engine log | Pass |
| S6 with the same GUI workload settings | 150 | 30 | Ten warm-up and twenty measured traversals, with the configured ramp-up in the engine log | Pass |

All 20 cases satisfied their expected outcomes. The verifier inspected JTL rows, phase order per thread, page and journey counts, completion flags and the ramp-up in the JMeter logs. For the GUI variable cases, it filled the same User Defined Variables table in a temporary JMX and ran the command-line engine without test input properties. Page fixtures checked Authorization, Channel and Message-Id headers. The signed fixture independently verified signatures and timestamp freshness using a temporary RSA key.

The verifier also wrote the Aggregate Report listener's samples to a separate JTL and matched them against the measured page samples in the full JTL. The multi-round S5 and S6 runs reported 50 and 100 measured page samples respectively. Warm-up pages and journey summaries were excluded, and failed measured pages were retained in the negative cases. The GUI CSV export uses JMeter's native Save Table Data action. The Windows GUI action was not exercised by the command-line verifier.

The embedded Groovy was checked against the source scripts, JMX parsed successfully, and Python helpers passed compilation. This is script correctness evidence only. The fixture does not prove production throughput, gateway integration, host transaction identity, real multi-currency behavior or SLA compliance. Token expiry/refresh and environment-specific DPoP require separate integration verification.

Reproduce using `python verify_fixture.py --jmeter <path-to-jmeter>`. Negative fixtures intentionally produce failed samples. Do not use fixture timing as the spike baseline.

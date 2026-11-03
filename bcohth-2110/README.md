# BCOH2H-2110 delivery

Source: `hth-application` commit `0e8faa5b87c746fd049fc39327945d43157f04fe`.

This is an isolated overlay. All source paths are retained beneath `bcohth-2110/hhfi/`. The existing mcdp root `consulting` directory is not a sync target.

- `consulting/middleware/projects/...`: four changed/new Java files in the SMS and approval modules.
- `consulting/db/branch_change_history/20261103_BCOH2H-2110/`: configuration/template SQL and read-only verification SQL.
- `story/notification/BCOH2H-2110-TECHNICAL-DESIGN.md`: scope, recipient/template matrix, approval flow, BCO impact and deployment checks.
- `devtools/backend-compile/tests/`: five changed/new validation files.

The feature covers existing HTH users when **both Email and Mobile change**. After successful final approval and business commit, it selects the reviewed Feature 9 templates for the old/new target contacts, final approver and company. With distinct valid contacts, expect six emails and four SMS. Deduplication is per event/channel; different required templates remain separate. Ordinary BCO and HTH single-field notification paths retain their existing behavior.

Apply the overlay to the matching full source baseline before building or testing. The overlay does not include SDK JARs, cached module classes, the reviewed v81 DOCX or unchanged test dependencies. Build/deploy both SMS and approval modules together. The source baseline also contains previous 880/881 changes; this folder is only the 2110 delta.

Execute the entire UTF-8 `1_HTH_Contact_Update_Notification.sql` block after older notification scripts. It commits once on success and rolls back its own changes on failure. It creates feature-owned event/template mappings and appends only the four private SMS events to the existing country-code skip-list. Run `2_HTH_Contact_Update_Verification.sql` afterwards, and reload notification/configuration caches through the standard deployment procedure.

Local checks passed: Java 8 target compilation against repository SDKs, six-route recipient/dedup/masking/country snapshot tests, final-approval gates, exact reviewed DOCX/SQL text comparison, and existing 851/PIN/880 regressions. Oracle execution/re-execution and actual SIT/UAT approval/MNG delivery remain environment checks. See the design for the request-local completion marker and recovery/OMB limitations.

From the full source checkout, run:

```sh
python3 devtools/backend-compile/tests/verify_hth_2110.py
python3 devtools/backend-compile/tests/verify_hth_851_approver.py
python3 devtools/backend-compile/tests/verify_hth_code_approval.py
python3 devtools/backend-compile/tests/verify_hth_851_compile.py
```

Set `JAVA_HOME` to the installed JDK when needed; tests compile for Java 8.

`SHA256SUMS` covers all twelve files copied byte-for-byte from the source commit. From this directory, verify with `shasum -a 256 -c SHA256SUMS`.

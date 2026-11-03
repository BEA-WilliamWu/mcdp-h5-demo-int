# BCOH2H-880 delivery

Source: `hth-application` commit `e91aa291989cf4083f60d68a55a3d6d064a83872`. Revision dated 4 November 2026.

This isolated overlay retains source paths below `bcohth-880/hhfi/`. Nothing is copied to mcdp's root `consulting` directory. Use the matching full source baseline, which includes the separately delivered 2110 work; SDK JARs, cached classes, reviewed DOCX and H2 are not bundled.

## Revised implementation

- Restore the original **approval time + 24 hours** expiry, calculated in the database. Existing historical rows are unchanged.
- Remove the new 880 thread marker and APPROVE callback. Register Code emails through the existing BCO activity/event framework inside Code activation's transaction.
- Keep target API user + company email recipients, normalized email deduplication, no Code SMS, and the successful user-update gate.
- Restore the earlier rejection-only lifecycle hook. No batch/shared notification package changes; 2110 implementation is unchanged.

The framework's existing non-transactional Code actions (`FLG_TRANSACTIONAL=N`) handle dispatch. Server restart recovery and exactly-once delivery are not guaranteed by this overlay; verify the deployed BCO queue/poller and transaction configuration in UAT.

## Template discrepancy

The SQL still contains the exact reviewed v81 bilingual wording, including **next-day 23:59:59**, which conflicts with the retained **24-hour** expiry. Confirm corrected wording before customer release. This revision does not silently change approved templates or extend Code lifetime.

## Deployment

Deploy hosttohost, sms and approval modules together; repository signatures must match. When applying over the previous 880 overlay, also remove this deleted source file from the build checkout and clean generated classes:

`consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/app/hosttohost/util/HthApiPasswordCodeExpiry.java`

Run the 880 SQL delta after the baseline HTH scripts; execute the full block, COMMIT after success, and run its verification queries. Refresh configuration caches through the standard deployment procedure. The delta checks existing non-transactional actions and six email templates/routes without creating duplicate rows.

See `hhfi/story/notification/BCOH2H-880-TECHNICAL-DESIGN.md` for the flow, scope, tests and UAT checks.

Local Java compilation, rejection/routing regressions, real EclipseLink/H2 activation and rollback tests with a fixture notification registration, and exact DOCX/SQL comparison passed. Oracle execution, the real event processor and MNG delivery were not tested locally.

`SHA256SUMS` covers all 15 source files (six Java, two SQL, six regression files and one design), copied byte-for-byte from the source commit. Verify from this directory with `shasum -a 256 -c SHA256SUMS`.

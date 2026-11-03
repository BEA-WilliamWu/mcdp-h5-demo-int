# BCOH2H-880 delivery

Source: `hth-application` commit `4efb66edd7e941c6deb81653ac311bc132d8e262`.

This is an isolated overlay. Nothing is copied to mcdp's existing root `consulting` directory.
All source paths are retained below `bcohth-880/hhfi/`:

- `consulting/middleware/projects/...`: seven changed/new Java files.
- `consulting/db/branch_change_history/20261103_BCOH2H-880/`: template delta and read-only verification SQL.
- `story/notification/BCOH2H-880-TECHNICAL-DESIGN.md`: AC, trigger, recipients, BCO impact, deployment and verification.
- `devtools/backend-compile/tests/`: six changed/new regression files.

Copy the overlay onto the matching **full source baseline** before compiling or running tests. This delivery does not bundle SDK JARs, cached module classes, the reviewed DOCX, H2 or unchanged regression dependencies. Follow the technical design for test prerequisites and commands.

The implementation sends Code approval email to the target API user and company officeEmail only, after successful final approval/business execution and commit. It deduplicates shared mailboxes, sends no Code SMS, and uses the exact reviewed v81 bilingual template. New Codes expire at 23:59:59 on the following Hong Kong calendar day. Separate BCO User Create/Edit and password setup/reset notifications retain their existing rules.

Deploy the hosttohost, sms and approval module changes together. Run the 880 SQL delta after the existing HTH password notification baseline; execute the complete block, COMMIT after success, and run the verification queries. Refresh notification configuration caches through the standard deployment procedure.

Local Java compilation, approval gates, email routing/dedup, EclipseLink/H2 transaction regression, 21 expiry cases and exact DOCX/SQL template comparison passed. Oracle execution/re-execution and real MNG delivery still require UAT checks. See the design for the post-commit callback's recovery and one-man-band validation limits.

`SHA256SUMS` covers all 16 files copied byte-for-byte from the source commit. From this directory, verify with `shasum -a 256 -c SHA256SUMS`.

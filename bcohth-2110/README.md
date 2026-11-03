# BCOH2H-2110 delivery — minimal BCO reuse

> **880 与 2110 联合部署请改用 [bco-notification](../bco-notification/README.md)。** 本目录保留历史交付快照；不要再将两个旧目录依次覆盖使用，以免同名文件互相回退。

Source: `hth-application` commit `876dd4376179720fb90f637fea85659c120ceada`.

This revision replaces the earlier private 2110 notification implementation. HTH combined Email + Mobile changes reuse the existing BCO events, templates and recipient flow. Remove only the two additional target-specific SMS reminders copied to the final approver; keep the generic `USER_MANAGEMENT_EDIT` event and its existing recipient/template configuration.

The effective additional production rule is three lines, including comments, in the existing `HthProfileApproverNotification.sms` helper. Other Java changes restore the original notification call and remove the 2110 marker, two helper classes and approval callback. Ordinary BCO and HTH single-field flows retain their existing behavior. Current 880 fixes are preserved.

## Apply the overlay

1. Use the matching full source baseline. Copy the source-relative files beneath `hhfi/` into that checkout.
2. Remove the five source-relative files listed in `DELETED_FILES.txt` from an earlier 2110 overlay. A copy-only update would leave the obsolete classes and SQL script behind.
3. Build/deploy the SMS and approval modules from the same revision, using a clean build to exclude deleted classes. This overlay does not contain SDK JARs, cached classes or unchanged test dependencies.
4. **No database update is required.** Do not execute the superseded `1_HTH_Contact_Update_Notification.sql`. If previously executed, its private rows may remain unused; no BCO configuration or historical delivery records are deleted. Old deployments/queued messages may still use the old IDs.
5. Run the read-only `2_HTH_Contact_Update_Verification.sql` to inspect existing BCO templates/routes, dispatcher configuration and delivery records for a new approval. Do not assume a fixed total email/SMS count: the generic event uses existing database mappings.

The delivery is isolated under `bcohth-2110/hhfi/`. Root mcdp `consulting` is not a synchronization target. Unrelated 876/881 changes are outside this delivery.

## Validation

Local regression passed: production combined-contact notification flow compared with BCO events, destinations and template inputs; old/new email snapshots; three target SMS retained and two extra approver SMS removed; ordinary BCO, HTH single-field and PIN behavior; Java 8 target compilation of restored shared classes; existing API Password approval regression. Bank data/configuration/transport are fixtures; actual Oracle configuration, approval execution and MNG inbox/handset delivery still require UAT.

From the full source checkout with `JAVA_HOME` set:

```sh
python3 devtools/backend-compile/tests/verify_hth_2110.py
```

`SHA256SUMS` covers the nine source files copied byte-for-byte from the stated commit. From this directory, verify with `shasum -a 256 -c SHA256SUMS`. See the revised technical design for recipient mapping, deduplication boundaries and remaining environment checks.

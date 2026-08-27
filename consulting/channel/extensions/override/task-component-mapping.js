define(["jquery"], function () {
    "use strict";

    const tasks = {

        FL_C_MIHO: {
            name: "corp-host-limit",
            class: "flow"
        },
        PC_F_CRNBCT: {
            name: "generic-money-transfer",
            class: "flow",
            initName: "generic-money-transfer"
        },
        PC_F_CRNBCT_SI: {
            name: "generic-money-transfer",
            class: "flow",
            initName: "generic-money-transfer"
        },
        PC_F_GCRNBCT: {
            name: "adhoc-payments-generic",
            class: "flow",
            initName: "adhoc-payments-generic"
        },
        PC_F_GCRNBCT_SI: {
            name: "adhoc-payments-generic",
            class: "flow",
            initName: "adhoc-payments-generic"
        },
        PC_F_GCRNINFT_SI: {
            name: "adhoc-payments-generic",
            class: "flow",
            initName: "adhoc-payments-generic"
        },
        CH_N_CBR: {
            name: "cheque-book-request",
            class: "flow",
            initName: "cheque-book-request"
        },
        CH_N_CIN: {
            name: "cheque-stop-unblock",
            class: "flow",
            initName: "cheque-stop-unblock"
        },
        EB_F_BP: {
            name: "quick-bill-payment",
            class: "flow",
            initName: "quick-bill-payment"
        },
        TD_F_RTD: {
            name: "td-redeem",
            class: "flow",
            initName: "td-redeem"
        },
        CH_I_CSE: {
            name: "cheque-status-inquiry",
            class: "flow",
            initName: "cheque-status-inquiry"
        },
        SP_CUST_U: {
            name: "review-special-cust-access",
            class: "legacy",
            module: "customer-preference"
        },
        PP_N_HTH_ENB: {
            name: "host-to-host-review",
            initName: "host-to-host-management",
            class: "legacy",
            module: "host-to-host"
        },
        PP_N_HTH_EDT: {
            name: "host-to-host-review",
            initName: "host-to-host-management",
            class: "legacy",
            module: "host-to-host"
        },
        PP_N_HTH_DIS: {
            name: "host-to-host-review",
            initName: "host-to-host-management",
            class: "legacy",
            module: "host-to-host"
        },
        PP_N_HTH_CERT_UPL: {
            name: "host-to-host-certificate-management",
            initName: "host-to-host-certificate-management",
            class: "legacy",
            module: "corporate-host-to-host"
        },
        // HTH user-access requests share one read-only review component. Register every write task
        // so Pending Approval, Activity Log and approval-detail popups can resolve the component
        // before the maker/checker framework evaluates task.class.
        UAT_N_HUA_NEW: {
            name: "review-hth-user-access",
            class: "legacy",
            module: "account-access-management"
        },
        UAT_N_HUA_EDT: {
            name: "review-hth-user-access",
            class: "legacy",
            module: "account-access-management"
        },
        UAT_N_HUA_DEL: {
            name: "review-hth-user-access",
            class: "legacy",
            module: "account-access-management"
        },
        MT_N_UUS: {
            name: "user-read",
            initName: "users-update",
            class: "legacy",
            module: "user-management"
        },
        MT_N_CUS: {
            name: "user-read",
            initName: "users-create",
            class: "legacy",
            module: "user-management"
        },
        UM_ID_MC: {
            name: "review-create-user-id-maintenance",
            initName: "create-user-id-maintenance",
            class: "legacy",
            module: "user-id-maintenance"
        },
        UM_ID_MU: {
            name: "review-modify-user-id-maintenance",
            initName: "modify-user-id-maintenance",
            class: "legacy",
            module: "user-id-maintenance"
        },
        UM_ID_MD: {
            name: "review-delete-user-id-maintenance",
            initName: "modify-user-id-maintenance",
            class: "legacy",
            module: "user-id-maintenance"
        },
        TD_F_OTD: {
            name: "td-open",
            class: "flow",
            initName: "td-open",
            hostReferenceNumber: null
        },
        EB_F_BP_SI: {
            name: "quick-bill-payment",
            module: "bill-payments",
            class: "flow"
        },
        EB_F_BP_D: {
            name: "quick-bill-payment",
            module: "bill-payments",
            class: "flow"
        },
        PC_N_CIP_A: {
            name: "bank-account-payee",
            class: "flow"
        },
        PC_N_DIP_A: {
            name: "bank-account-payee",
            class: "flow"
        },
        PC_N_UIP_A: {
            name: "bank-account-payee",
            class: "flow"
        },
        MT_N_CAC: {
            name: "activation-letter-generate-offline",
            class: "legacy",
            module: "activation-letter"
        },
        FL_C_MDHO: {
            name: "corp-host-limit",
            class: "flow"
        },
        CH_N_RADHSTMT: {
            name: "req-adhoc-statement",
            class: "flow",
            initName: "req-adhoc-statement"
        },
        EADESTMTC: {
            name: "acc-pref-setting-intiate",
            class: "flow",
            initName: "acc-pref-setting-intiate"
        },
        EADESTMTU: {
            name: "acc-pref-setting-intiate",
            class: "flow",
            initName: "acc-pref-setting-intiate"
        },
        MT_M_COP: {
            name: "open-api-flag-review",
            class: "legacy",
            module: "open-api"
        },
        MT_M_UOP: {
            name: "open-api-flag-review",
            class: "legacy",
            module: "open-api"
        },
        OAC_REVOKE: {
            name: "open-api-revoke-consent-review",
            class: "legacy",
            module: "open-api"
        },
        MT_N_AFP: {
            name: "create-fps-activation",
            class: "legacy",
            module: "fps"
        },
        PC_N_CITNP_A: {
            name: "bank-account-payee",
            class: "flow"
        },
        PC_N_UITNP_A: {
            name: "bank-account-payee",
            class: "flow"
        },
        PC_N_DITNP_A: {
            name: "bank-account-payee",
            class: "flow"
        },
        PC_N_DOP_VT_A: {
            name: "bank-account-payee",
            class: "flow"
        },
        PC_N_UDOP_VT_A: {
            name: "bank-account-payee",
            class: "flow"
        },
        PC_N_DDP_VT_A: {
            name: "bank-account-payee",
            class: "flow"
        },
        MT_N_EFP: {
            name: "create-fps-edit",
            class: "legacy",
            module: "fps"
        },
        MT_N_TFP: {
            name: "create-fps-termination",
            class: "legacy",
            module: "fps"
        },
        PC_F_GCRNDFT_FPS: {
            name: "adhoc-payments-generic",
            class: "flow",
            initName: "adhoc-payments-generic"
        },
        PC_F_GCRNDFT_FPS_SI: {
            name: "adhoc-payments-generic",
            class: "flow",
            initName: "adhoc-payments-generic"
        },
        PC_F_GCRNDFT_SI: {
            name: "adhoc-payments-generic",
            class: "flow",
            initName: "adhoc-payments-generic"
        },
        PC_F_GCRNIFT_SI: {
            name: "adhoc-payments-generic",
            class: "flow",
            initName: "adhoc-payments-generic"
        },
        PC_F_CRNDFT_FPS: {
            name: "generic-money-transfer",
            class: "flow",
            initName: "generic-money-transfer"
        },
        PC_F_CRNDFT_SI: {
            name: "generic-money-transfer",
            class: "flow",
            initName: "generic-money-transfer"
        },
        PC_F_CRNDFT_FPS_SI: {
            name: "generic-money-transfer",
            class: "flow",
            initName: "generic-money-transfer"
        },
        PC_F_CRNSFT_SI: {
            name: "generic-money-transfer",
            class: "flow",
            initName: "generic-money-transfer"
        },
        PC_F_CRNIFT_SI: {
            name: "generic-money-transfer",
            class: "flow",
            initName: "generic-money-transfer"
        },
        PC_F_CRNINFT_SI: {
            name: "generic-money-transfer",
            class: "flow",
            initName: "generic-money-transfer"
        },
        SQ_MT_USQ: {
            name: "review-security-question-maintenance",
            class: "legacy",
            initName: "security-question"
        },
        MT_N_RFP: {
            name: "create-fps-reactive",
            class: "legacy",
            module: "fps"
        },
        ACP_N_CP: {
            name: "access-point-view",
            module: "access-point",
            class: "legacy",
            initName: "access-point-create"
        },
        APG_N_CG: {
            name: "access-point-group-view",
            module: "access-point",
            class: "legacy",
            initName: "access-point-group-create"
        },
        ACP_N_UP: {
            name: "access-point-view",
            module: "access-point",
            class: "legacy",
            initName: "access-point-create"
        },
        APG_N_UG: {
            name: "access-point-group-view",
            module: "access-point",
            class: "legacy",
            initName: "access-point-group-create"
        },
        PC_F_SED: {
            name: "create-set-up-edda",
            initName: "create-set-up-edda",
            class: "legacy",
            module: "edda"
        },
        BU_AUTOPAY_CREATE: {
            name: "review-corp-auto-pay",
            class: "legacy",
            module: "auto-pay-collection",
            initName: "corp-auto-pay"
        },
        BU_PAYROLL_CREATE: {
            name: "review-corp-payroll",
            class: "legacy",
            module: "auto-pay-collection",
            initName: "corp-payroll"
        },
        FD_M_FTU: {
            name: "feedback-home",
            module: "feedback",
            class: "legacy"
        },
        FD_M_FTC: {
            name: "feedback-home",
            module: "feedback",
            class: "legacy"
        },
        BU_COLLECTION_CREATE: {
            name: "review-corp-collection",
            class: "legacy",
            module: "auto-pay-collection",
            initName: "corp-collection"
        },
        PC_N_CBP: {
            name: "bank-account-payee",
            class: "flow"
        },
        PC_N_CBP_A: {
            name: "bank-account-payee",
            class: "flow"
        },
        PC_N_UBP: {
            name: "bank-account-payee",
            class: "flow"
        },
        PC_N_UBP_A: {
            name: "bank-account-payee",
            class: "flow"
        },
        PC_N_DBP: {
            name: "bank-account-payee",
            class: "flow"
        },
        PC_N_DBP_A: {
            name: "bank-account-payee",
            class: "flow"
        },
        WW_N_CWW: {
            name: "review-working-window",
            module: "cutoff",
            class: "legacy"
        },
        WW_N_UWW: {
            name: "review-working-window",
            module: "cutoff",
            class: "legacy"
        },
        WW_N_DWW: {
            name: "review-working-window",
            module: "cutoff",
            class: "legacy"
        },
        AP_N_CR_CZ: {
            name: "rules-review",
            module: "approvals",
            class: "legacy"
        },
        AP_N_UR_CZ: {
            name: "rules-review",
            module: "approvals",
            class: "legacy"
        },
        AP_N_DR_CZ: {
            name: "rules-review",
            module: "approvals",
            class: "legacy"
        },
        UM_N_ULS_L: {
            name: "review-user-status",
            module: "user-management",
            class: "legacy"
        },
        UM_N_ULS_UL: {
            name: "review-user-status",
            module: "user-management",
            class: "legacy"
        },
        FU_F_APC: {
            name: "file-approval",
            initName: "file-upload",
            module: "file-upload",
            class: "legacy"
        },
        PC_F_AED: {
            name: "view-edda-summary",
            initName: "view-edda-summary",
            class: "legacy",
            module: "edda"
        },
        PC_F_AED_RESUME: {
            name: "view-edda-summary",
            initName: "view-edda-summary",
            class: "legacy",
            module: "edda"
        },
        PC_F_AED_REJECT: {
            name: "view-edda-summary",
            initName: "view-edda-summary",
            class: "legacy",
            module: "edda"
        },
        PC_F_AED_SUSPEND: {
            name: "view-edda-summary",
            initName: "view-edda-summary",
            class: "legacy",
            module: "edda"
        },
        PC_F_AED_TERMINATE: {
            name: "view-edda-summary",
            initName: "view-edda-summary",
            class: "legacy",
            module: "edda"
        },
        PC_F_AED_CONFIRM: {
            name: "view-edda-summary",
            initName: "view-edda-summary",
            class: "legacy",
            module: "edda"
        },
        MRCH_N_CME: {
            name: "review-create-merchant-maintenance",
            initName: "merchant-maintenance-create",
            class: "legacy",
            module: "merchant-maintenance"
        },
        MRCH_N_UME: {
            name: "review-modify-merchant-maintenance",
            initName: "modify-merchant-maintenance",
            class: "legacy",
            module: "merchant-maintenance"
        },
        MRCH_N_DME: {
            name: "review-delete-merchant-maintenance",
            initName: "merchant-maintenance-create",
            class: "legacy",
            module: "merchant-maintenance"
        },
        MRCH_N_CMU: {
            name: "review-merchant-users-create",
            initName: "merchant-users-create",
            class: "legacy",
            module: "merchant-user-management"
        },
        MRCH_N_EMU: {
            name: "review-modify-merchant-user-management",
            initName: "modify-merchant-user-management",
            class: "legacy",
            module: "merchant-user-management"
        },
        MRCH_N_DMU: {
            name: "review-delete-merchant-user-management",
            initName: "delete-merchant-user-management",
            class: "legacy",
            module: "merchant-user-management"
        },
        MRCH_N_LDMU: {
            name: "review-merchant-user-pin-mapping",
            initName: "merchant-user-pin-mapping",
            class: "legacy",
            module: "merchant-user-management"
        },
        MRCH_N_LDMC: {
            name: "review-merchant-user-pin-mapping",
            initName: "merchant-user-pin-mapping",
            class: "legacy",
            module: "merchant-user-management"
        },
        MT_N_CFM: {
            name: "fps-merchant-create-review",
            initName: "fps-merchant-create",
            class: "legacy",
            module: "merchant"
        },
        MT_N_DFM: {
            name: "fps-merchant-delete-review",
            initName: "fps-merchant-delete",
            class: "legacy",
            module: "merchant"
        },
        MT_N_EFM: {
            name: "fps-merchant-edit-review",
            initName: "fps-merchant-edit",
            class: "legacy",
            module: "merchant"
        }
    };

    return tasks;
});

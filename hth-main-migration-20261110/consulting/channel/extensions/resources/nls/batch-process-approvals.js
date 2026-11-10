define(["ojL10n!resources/nls/generic", "ojL10n!extensions/resources/nls/user-id-maintenance", "ojL10n!extensions/resources/nls/merchant-user-management"], function(Generic, UserIdMaintenance, MerchantUserMaintenance) {
    "use strict";

    const BatchProcessApprovalsLocale = function() {
        return {
            root: {
                batchProcessApprovals: {
                    reject: "Reject",
                    approve: "Approve",
                    otherTransactionsApproval: "{nature} Transactions Approval",
                    selectedTransactions: "Selected Transactions ({count})",
                    remarks: "Remarks (Optional)",
                    cancel: "Cancel",
                    withdrawal: "Withdrawal",
                    confirm: "Confirm",
                    modify: "Modify",
                    reinitiate: "Copy",
                    paymentTracker: "Payment Tracking Services",
                    edit: "Edit",
                    close: "Close",
                    proceed: "Proceed",
                    requestModification: "Send to Modify",
                    allSuccessText_REJECT: "({successCount}) Transaction(s) rejected. Please click \"My Approval List\" on your dashboard to view the rejection result(s).",
                    allSuccessText_APPROVE: "{successCount} Transaction(s) approved. Please click \"My Approval List\" on your dashboard to view the approval result(s).",
                    someSuccessText_REJECT: "{successCount} transaction(s) successfully rejected, {failureCount} transaction(s) failed",
                    someSuccessText_APPROVE: "{successCount} transaction(s) successfully approved, {failureCount} transaction(s) failed",
                    transactionMessage: "Transaction {task} successfully.",
                    removeMessage: "Click here to remove the message",
                    removeMessageTitle: "Click to remove the message",
                    graceWindowMessage: "Value Date for below transaction IDs are going to change due to approval grace period:",
                    ACCOUNT_FINANCIAL: "Accounts",
                    ACCOUNT_NON_FINANCIAL: "Accounts",
                    BULK_FILE: "Bulk File",
                    BULK_RECORD: "Bulk Record",
                    NON_FINANCIAL_BULK_FILE: "Bulk File",
                    NON_FINANCIAL_BULK_RECORD: "Bulk Record",
                    PAYEE_BILLER: "Payee and Biller",
                    PAYMENTS: "Payments",
                    TRADE_FINANCE: "Trade Finance",
                    OTHER_TRANSACTION: "Others",
                    ADMIN_MAINTENANCE: "Administrative Maintenance",
                    PARTY_MAINTENANCE: "Party Maintenance",
                    newDeposit: "Deposit Number",
                    tncTransaction: "I confirm to have read all the transaction details along with the terms and conditions for each transaction.",
                    tncInterest: "I understand and confirm that time deposit interest rates will be determined at the time of execution.",
                    reminderText: "Reminder: Please click \"Approve\" to proceed with the approval process. To view the approval results, click \"My Approval List\" on your dashboard.",
                    infoText: "\"Pay Now\" transaction execution dates will be determined upon the full approval and authentication of the transaction(s).",
                    reminderRejectText:"Reminder: Please click \"Reject\" to proceed with the rejection process. To view the rejection results, click \"My Approval List\" on your dashboard.",
                    modalHeader: {
                        reject: "Rejection Comment",
                        approve: "Approval Comment",
                        requestModification: "Modification Comment",
                        cancel: "Withdrawal Reason"
                    },
                    txnSubmitMessage:"<span>Your selected transactions have been submitted. Please check the latest status of your transactions under \"My Approved List\" and take any further actions if needed. Pending transactions will remain at \"Pending Approval\". Click <a href='javascript:void(0)' id='refreshView'>here</a> to refresh and view the latest status.</span>",
                    tncRequiredMsg:"Please read the above statement carefully and check this box before further proceed."
                },
                customHeader: {
                    PC_F_CRNBCT: "Fund Transfers",
                    PC_F_CRNBCT_SI: "Fund Transfers",
                    PC_F_CRNDFT_FPS: "Fund Transfers",
                    PC_F_CRNDFT_FPS_SI: "Fund Transfers",
                    PC_F_CRNDFT: "Fund Transfers",
                    PC_F_CRNDFT_SI: "Fund Transfers",
                    PC_F_CRNINFT: "Fund Transfers",
                    PC_F_CRNINFT_SI: "Fund Transfers",
                    PC_F_CRNIFT: "Fund Transfers",
                    PC_F_CRNIFT_SI: "Fund Transfers",
                    PC_F_CRNSFT: "Fund Transfers",
                    PC_F_CRNSFT_SI: "Fund Transfers",
                    UM_N_ULS_L: "User Status - Suspend",
                    UM_N_ULS_UL: "User Status - Normal",
                    UM_N_USD: "Online Banking Access - Revoke",
                    PC_F_GCRNBCT: "Transfer to Non-designated Account - BEA(China)",
                    PC_N_DOP_VT: "Add Beneficiary",
                    PC_N_DOP_VT_A: "Add Beneficiary",
                    PC_N_CIP: "Add Beneficiary",
                    PC_N_CIP_A: "Add Beneficiary",
                    PC_N_CBP: "Add Beneficiary",
                    PC_N_CBP_A: "Add Beneficiary",
                    PC_N_DIP: "Delete Beneficiary",
                    PC_N_DIP_A: "Delete Beneficiary",
                    PC_N_DDP_VT: "Delete Beneficiary",
                    PC_N_DDP_VT_A: "Delete Beneficiary",
                    PC_N_DITNP: "Delete Beneficiary",
                    PC_N_DITNP_A: "Delete Beneficiary",
                    PC_N_DBP: "Delete Beneficiary",
                    PC_N_DBP_A: "Delete Beneficiary",
                    PC_F_GCRNIFT: "Transfer to Non-designated Account \u2013 BEA (HK)",
                    PC_F_GCRNINFT: "Transfer to Non-Designated Account \u2013 Overseas Payments",
                    PC_N_CITNP: "Add Beneficiary",
                    PC_N_CITNP_A: "Add Beneficiary",
                    PC_F_GCRNDFT_FPS: "Transfer to Non-designated Account - Local Payments",
                    PC_F_GCRNIFT_SI: "Scheduled Payment - Transfer to Non-designated Account - BEA (HK) Payments",
                    PC_F_GCRNBCT_SI: "Scheduled Payment - Transfer to Non-designated Account - BEA(China)",
                    PC_F_GCRNDFT_SI: "Scheduled Payment - Transfer to Non-designated Account - Local Payments",
                    PC_F_GCRNDFT_FPS_SI: "Scheduled Payment - Transfer to Non-designated Account - Local Payments [FPS]",
                    PC_F_GCRNINFT_SI: "Scheduled Payment - Transfer to Non-designated Account - Overseas Payments",
                    FU_F_APC: "File Upload Approval",
                    PP_N_HTH_CERT_UPL: "Management Host to Host Certificate",
                    BU_AUTOPAY_CREATE: "Autopay",
                    BU_PAYROLL_CREATE: "Payroll",
                    BU_COLLECTION_CREATE: "Collection",
                    TD_F_OTD: "Time Deposit",
                    MT_N_UUS_SPR: "Reset Signer PIN Request"
                },
                workingWindow: {
                    header: `${""} ${""}`,
                    body: {
                        closed: "This transaction was approved outside business hours. The execution date will be changed to {next_working_date}. Please click 'Yes' to proceed.",
                        rejected: "Sorry, the transaction cannot be approved outside business hours. Please try again later and click 'OK' to continue.",
                        closed_available: "This transaction was approved before business hours. Please re-approve them in the 'Pending Approval' widget during <u><a href=\"javascript:void(0);\" onclick=\"window.open('https://www.hkbea.com/pdf/en/bcohelp/bco_sh_en_v1.0.pdf');\"> service hours</a></u> and click 'Yes' to continue."
                    },
                    buttons: {
                        yes: "Yes",
                        no: "No",
                        ok: "OK"
                    }
                },
                typhoon: {
                    header: "Extreme Weather",
                    body: {
                        rejected: "Due to the typhoon/black rainstorm warning, all transactions cannot be approved.",
                        warning: "Due to the typhoon/black rainstorm warning, the following transactions cannot be approved. Please click \"Continue\" to proceed."
                    },
                    buttons: {
                        continue: "Continue"
                    }

                },
                workingWindowList: {
                    header: "Working Window",
                    body: {
                        warning: "The following transactions will be approved outside service hours. As such, the transaction execution date will on {nextWorkingDate}. Please click \"Yes\" to proceed.",
                        nextWorkingDate: "{txnRefNo}",
                        exclusionMsg:"The following transactions could not be processed before service hours. Please re-approve them in the 'Pending Approval' widget during <u><a href=\"javascript:void(0);\" onclick=\"window.open('https://www.hkbea.com/pdf/en/bcohelp/bco_sh_en_v1.0.pdf');\"> service hours</a></u> and click 'Yes' to continue."
                    }
                },
                suspiciousPayee :{
                    popupHeader :"Alert",
                    alertMessage:"Hong Kong Police Force warning: The payee account is related to a <br><span class=\"scam-report\">scam report.<br></span>Please cancel the payment instruction. Call Police Anti-Scam Helpline 18222 if in doubt.",
                    timeoutHeader :"High Risk Alert service is temporarily unavailable.",
                    timeoutMessage:"Please check whether the payee is trustworthy and decide whether to cancel or proceed with the transaction. You are responsible for taking reasonably practicable steps to safeguard your own interests, money and assets from fraud or other illegal activities.<br><br>Call Police Anti-Scam Helpline 18222 if in doubt.",
                    scamAlertMessage: "By proceeding, you acknowledge that you understand the risks of fraud exposure associated with this payment .",
                    countdown: "Please click after {seconds}",
                    cancel:"Cancel",
                    proceed:"Proceed"
                },
                TTAutoRouting: {
                    alertMessage:"Corporate customers can now make cross-boundary payments to BEA China beneficiaries in HKD, CNY and USD via BEA’s Direct Remittance. Direct Remittance to BEA China is a faster and more cost-efficient alternative to telegraphic transfer as no correspondent bank is involved. BEA will send and process your cross-boundary payment with Direct Remittance rail, instead of telegraphic transfer rail, as you are paying a BEA China beneficiary in HKD / CNY / USD and disregard the intermediary bank stated in the instruction."
                },
                cancelMessage:"Do you want to delete this instruction?",
                cancelMessage1:"To proceed, click \"Approve\".",
                cancelMessage2:"Please note all instructions with the same Instruction Number will be deleted.",
                generic: Generic,
                UserIdMaintenance: UserIdMaintenance,
                MerchantUserMaintenance: MerchantUserMaintenance,
                clickMsg:"Note: By clicking \"Edit\", the status of the transaction will be changed to \"Pending Modification\".",
                pleaseNoteInstruction:"The transaction will not be available for approval till the transaction is re-submitted for approval.",
                yes:"Yes",
                no:"No",
                editInstructionHeader:"Do you want to edit this transaction?",
                paymentSent: "Payment Sent",
                underProcessing: "Under Processing"
            },
            ar: true,
            es: true,
            pt: true,
            fr: true,
            "zh-CN": true,
            "zh-Hant": true,
            cs: true,
            sv: true,
            en: false,
            "en-us": false,
            el: true
        };
    };

    return new BatchProcessApprovalsLocale();
});
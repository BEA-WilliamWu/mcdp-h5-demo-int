define(["ojL10n!resources/nls/generic"], function(Generic) {
    "use strict";

    const DashboardLocale = function() {
        return {
            root: {
                bankName: "BEA Corporate Online",
                backImage: "Go back",
                skipToMainContent: "Skip to main content",
                headers: {
                    approver: "Home",
                    loans: "Loans",
                    maker: "Home",
                    "demand-deposits": "Current and Savings",
                    "term-deposits": "Time Deposits",
                    viewer: "Home",
                    overview: "Overview",
                    payments: "Payments",
                    creditCard: "Credit Cards",
                    systemDashboard: "Home",
                    dashboard: "Home",
                    dashboardnStatistics: "Dashboard and Statistics",
                    "supply-chain-finance": "Supply Chain Finance",
                    "liquidity-management": "Liquidity Management",
                    "virtual-account-management": "Virtual Account Management",
                    "credit-facility-management": "Credit Facility Management",
                    "cash-management": "Cash Management"
                },
                sessionExpiredHeader: "Session Expired",
                sessionExpired: "Your session has expired. Please try again.",
                passwordWarningMessage: "Your password is about to expire in {pwdExpiryWarningDays} days, please change your password at the earliest.",
                fatcaWarningMessage: "You are required to submit FATCA & CRS related information. Please click the link to open the form.",
                fatcaForm: "FATCA & CRS form",
                fatcaFormTitle: "Click to open FATCA & CRS form",
                changePasswordTitle: "Click to Change Password",
                changePassword: "Change Password.",
                overlayDismissTitle: "Click to dismiss overlay",
                overlayDismiss: "Dismiss Overlay",
                backTop: "Back To Top",
                rewardsTitle: "Please click here to view rewards",
                systemConfigPending: "You cannot do any transaction since System Configuration is not set yet.",
                passwordExample: "Example, if your name is Roopa Lal and date of birth is 23-12-1980, then your password is ROOP2312",
                passwordNotification: "Password Combination",
                rewards: "Rewards",
                passCombination: "The document is password protected, it is a combination of the first 4 letters of your name (in capital letters) followed by your date of birth (in DDMM format).",
                pinChangeReminder: {
                    popupHeader: "Change Login PIN Reminder",
                    description: "Your Login PIN has not been changed for 90 days or more. You are advised to change your PIN regularly for optimal security.",
                    question: "Would you like to change your PIN now?",
                    option1: "Keep current Login PIN and remind me later",
                    option2: "Remind me when I log in next",
                    option3: "Change Login PIN now"
                },
                signerPinChangeReminder: {
                    popupHeader: "Change Signer PIN Reminder",
                    description: "Your signer PIN has not been changed for 90 days or more. You are advised to change your PIN regularly for optimal security.",
                    question: "Would you like to change your PIN now?",
                    option1: "Keep current signer PIN and remind me later",
                    option2: "Remind me when I log in next",
                    option3: "Change signer PIN now"
                },
                bounceBackReminder: {
                    popupHeader: "Update Information reminder",
                    usersDesc: "The SMS and/or email sent to you by the Bank have been bounced back recently. Please inform your Authorised Person/Administrator to update your registered mobile phone number and/or email address.",
                    authPersonDesc: "The email sent to your company by the Bank has been bounced back recently. Please visit any of our branches to update the company email address.",
                    subDesc: "Do you want this message to be shown in next login?"
                },
                hthApiPasswordSetup: {
                    popupHeader: "Create HTH Login Password",
                    description: "To activate HTH service, you need to set a password for HTH API authentication.",
                    question: "Would you like to set your HTH login password now?",
                    codeRequired: "Please contact your Authorized Person to generate an HTH API Password Code first."
                },
                Youwillberedirectedtoanexternalsite: "You will be redirected to an external site",
                cancel: "Cancel",
                okay: "Okay",
                confirm: "Confirm",
                generic: Generic,
                addBeneficiary: {
                    warningHeader: "Important Information",
                    warningContent: "Beneficiary Account Name and Address are now separate fields. We have pre-filled these new fields using your existing beneficiary information. Please review the beneficiary information carefully before proceeding with transaction to ensure successful processing.",
                    payeeDetailsHeader: "Action Required",
                    payeeDetailsContent: "The beneficiary details do not meet the latest industry-standard requirements. Please update the beneficiary details to prevent payment delays or rejections.",
                    predesignatedHeader: "Important Information",
                    predesignatedContent: "Beneficiary Account Name and Address are now separate fields. We have pre-filled these new fields using your existing beneficiary information. Please review the beneficiary information carefully before proceeding with transaction to ensure successful processing.",
                    predesignatedTownContent: "The beneficiary address does not meet the latest SWIFT requirement. Please notify your System Administrator or Authorised Person to update the beneficiary to make future transactions.",
                    payeeDetailsTownContent: "The beneficiary address does not meet the latest SWIFT requirement. Please notify your System Administrator or Authorised Person to update the beneficiary to make future transactions.",
                    FavTownContent: "This favourites template is in an old format. Please remember to fill in all the beneficiary information to ensure your transaction is successful."
                },
                hkButton: "BEA Hong Kong",
                cnButton: "BEA China (the Mainland)"
            },
            ar: true,
            fr: true,
            cs: false,
            sv: false,
            en: false,
            "en-us": false,
            el: false,
            "zh-CN": true,
            "zh-Hant": true
        };
    };

    return new DashboardLocale();
});

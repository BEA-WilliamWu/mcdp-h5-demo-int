define(["ojL10n!resources/nls/generic"], function(e) {
    "use strict";

    return new function() {
        return {
            root: {
                header: {
                userChannelType: "User Channel Type",
                    userList: "Search Results",
                    fullName: "Full Name",
                    userName: "Username",
                    contact: "Email / Mobile Phone",
                    status: "User Status",
                    partyData: "Party ID / Name",
                    details: "User List",
                    channelAccess: "Online Banking Access",
                    granted: "Grant",
                    alreadyGranted: "Granted",
                    revoked: "Revoke permanently",
                    unlock: "Normal",
                    lock: "Suspend",
                    userStatus: "Update User Status",
                    lockStatus: "Lock Status",
                    lockUserStatus: "User Status - Suspend",
                    unlockUserStatus: "User Status - Normal",
                    reason: "Reason",
                    userChannelAccess: "Update Online Banking Access",
                    userChannelAccessGrant: "Update Online Banking Access",
                    userChannelAccessRevoke: "Online Banking Access - Revoke",
                    reviewChannelAccess: "Review Channel Access",
                    reviewUserStatus: "Review Lock Status",
                    ariaLabel: "User Search Result List",
                    authPerson: "Signer/Authorised Person",
                    User: "User",
                    Signer: "Signer",
                    AuthorisedPerson: "Authorised Person",
                    statusNotChanged:"Please update User Status.",
                    accessNotChanged:"Please update Online Banking Access.",
                    locked: "Suspended"
                },
                info: {
                    channelAccessReviewHeaderMsg: "You Initiated a request for Update Online Banking Access for the user. Please review details before you confirm!",
                    lockStatusReviewHeaderMsg: "You Initiated a request for Update User Status for the user. Please review details before you confirm!"
                },
                buttons: {
                    submit: "Submit",
                    cancel: "Cancel",
                    confirm: "Confirm",
                    back: "Back"
                },
                fieldname: {
                    fullName: "Full Name",
                    email: "Email Address",
                    mobNumber: "Mobile Number",
                    partyId: "Account Number",
                    displayFullName: "{firstname} {lastname}",
                    mobileWithCode: "{code} - {mobileNo}"
                },
                generic: e
            },
            ar: !0,
            es: !0,
            pt: !0,
            fr: !0,
            cs: !0,
            sv: !0,
            en: !1,
            "en-us": !1,
            el: !0,
            "zh-CN": !0,
            "zh-Hant": !0
        };
    };
});
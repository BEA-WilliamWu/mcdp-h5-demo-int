define([], function() {
  "use strict";

  const CorpProfileLocale = function() {
    return {
      root: {
        lastLoginTime: "Last Login Time",
        itkError:"Verification expired or the transaction has already been processed. Please check and try again.",
        email: "Email Address",
        phone: "Mobile Number",
        dob: "Date of Birth",
        address: "Communication Address",
        city:"City",
        country:"Country",
        pincode:"Zip Code",
        ok: "Ok",
        panCard: "Pan Card",
        aadharCard: "Aadhar Card",
        heading: "Profile",
        profile: "Profile Image",
        personalInformation: "Personal Information",
        contactInformation: "Contact Information",
        addressDetails: "Address Details",
        name: "{firstName} {lastName}",
        download: "Download",
        downloadFile: "Download",
        next : "Next",
        fax: "Fax",
        save: "Save",
        cancel: "Cancel",
        update: "Update Field",
        verification : "Verification",
        successMessage : "Your details have been updated successfully!!",
        emailUpdateMessage:"Your details have been updated successfully!! For receiving e-statements on the updated email id, please unsubscribe the e-statement facility and subscribe it again.",
        close: "Close",
        confirm: "Confirmation",
        line1: "Line 1",
        line2: "Line 2",
        line3: "Line 3",
        line4: "Line 4",
        skip : "Skip",
        yes: "Yes",
        no: "No",
        dialogMsg: "Are you sure you want to cancel the operation?",
        dialogHeader: "Warning",
        signerPINFrozen:"This Signer PIN has been temporarily frozen. Please contact BEA to proceed.",
        signerOTPFrozen:"You have exceeded the maximum number of invalid attempts. The one-time password authentication function will be disabled for {coolingPeriod} seconds.",
        workingWindow: {
            header: `${""} ${""}`,
            body: {
              closed: "This transaction was submitted outside service hours. The execution date will be changed to {next_working_date}. Please click 'Yes' to proceed.",
                rejected: "Sorry, transaction out of service hours. Please submit the instruction during service hours.",
                closed_available: "Sorry, transaction out of service hours. Please submit the instruction during service hours."
            },
            buttons: {
                yes: "Yes",
                no: "No",
                ok: "OK"
            }
        },
        workingWindowList: {
            header: "Working Window",
            body: {
                warning: "The following list of transactions are being approved outside the working hours; the transaction dates will be changed as listed below. Please click on Yes to continue.",
                nextWorkingDate: "{txnRefNo} - {nextWorkingDate}"
            }
        }
      },
      ar: true,
      fr: true,
"zh-CN": true,
"zh-Hant": true,
      cs: false,
      sv: false,
      en: false,
      "en-us": false,
      el: true
    };
  };

  return new CorpProfileLocale();
});

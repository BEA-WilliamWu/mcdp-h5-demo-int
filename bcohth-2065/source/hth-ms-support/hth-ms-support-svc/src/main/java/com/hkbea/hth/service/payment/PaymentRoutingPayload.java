package com.hkbea.hth.service.payment;

import com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest;
import com.hkbea.hth.ipe.model.ChargeAmount;
import com.hkbea.hth.ipe.model.Charges;
import com.hkbea.hth.ipe.model.ChatsRemittance;
import com.hkbea.hth.ipe.model.Creditor;
import com.hkbea.hth.ipe.model.CreditorAccount;
import com.hkbea.hth.ipe.model.CreditorAgent;
import com.hkbea.hth.ipe.model.CreditorAgentPostalAddress;
import com.hkbea.hth.ipe.model.CustomerNotificationInformRequest;
import com.hkbea.hth.ipe.model.Debtor;
import com.hkbea.hth.ipe.model.DebtorAccount;
import com.hkbea.hth.ipe.model.FinancialInstitution;
import com.hkbea.hth.ipe.model.Notification;
import com.hkbea.hth.ipe.model.PaymentInformation;
import com.hkbea.hth.ipe.model.PaymentMethodEnum;
import com.hkbea.hth.ipe.model.PostalAddress;
import com.hkbea.hth.ipe.model.ProxyIdentification;
import com.hkbea.hth.ipe.model.RemittanceInformation;
import com.hkbea.hth.ipe.model.RoutingTransactionDetailRequest;

import java.math.BigDecimal;
import java.util.List;

/**
 * Type-specific payload builders for the smart-routing submission request.
 * <p>
 * The routed payment services enrich the request that was validated by smart
 * routing in place (instead of rebuilding one), and these helpers keep the
 * block-level null/creation rules identical to the previous flat-to-routing
 * conversion so the submitted payload stays unchanged.
 */
final class PaymentRoutingPayload {

    private PaymentRoutingPayload() {
    }

    /** First transaction of the routing request (null-safe). */
    static RoutingTransactionDetailRequest firstTransaction(BatchRoutingCreditTransferRequest request) {
        return request == null || request.getTransactions() == null || request.getTransactions().isEmpty()
                ? null : request.getTransactions().getFirst();
    }

    /** Group-header batch id used as the single identifier for submit + persistence. */
    static String batchId(BatchRoutingCreditTransferRequest request) {
        return request == null || request.getGroupHeader() == null
                ? null : request.getGroupHeader().getMessageId();
    }

    /** End-to-end id carried by the (already routed) first transaction. */
    static String endToEndId(BatchRoutingCreditTransferRequest request) {
        RoutingTransactionDetailRequest txn = firstTransaction(request);
        return txn == null || txn.getPaymentIdentification() == null
                ? null : txn.getPaymentIdentification().getEndToEndId();
    }

    /** Debtor product code resolved by the common routing builder. */
    static String debtorProductCode(BatchRoutingCreditTransferRequest request) {
        RoutingTransactionDetailRequest txn = firstTransaction(request);
        return txn == null || txn.getDebtorAccount() == null
                ? null : txn.getDebtorAccount().getProductCode();
    }

    /** Overrides channel reference and payment method with the routed type's values. */
    static void applyTypeEnvelope(BatchRoutingCreditTransferRequest request,
                                  String channelReference, String paymentMethodValue) {
        if (request == null || request.getPaymentInformation() == null) {
            return;
        }
        PaymentInformation information = request.getPaymentInformation();
        information.setChannelReference(channelReference);
        information.setPaymentMethod(PaymentMethodEnum.fromValue(paymentMethodValue));
    }

    /** Remittance block: customer remarks plus (when present) the split payment details. */
    static RemittanceInformation remittance(String customerRemarks, List<String> paymentDetails) {
        boolean hasUnstructured = paymentDetails != null && !paymentDetails.isEmpty();
        if (customerRemarks == null && !hasUnstructured) {
            return null;
        }
        RemittanceInformation target = new RemittanceInformation();
        target.setCustomerRemarks(customerRemarks);
        if (hasUnstructured) {
            target.setUnstructured(paymentDetails);
        }
        return target;
    }

    /** Charges block, keeping the legacy creation rule: null when nothing is set. */
    static Charges charges(BigDecimal serviceChargeAmount, String chargeCurrency,
                           String chargeBearer, String feeCode) {
        if (feeCode == null || feeCode.isBlank()) {
            if (chargeBearer != null && !chargeBearer.isBlank()) {
                // The legacy contract carries the OVR charge indicator as chargeBearer for
                // CHATS/TT, while the new contract also requires it as feeCode.
                feeCode = chargeBearer;
            }
        }
        if (serviceChargeAmount == null && chargeBearer == null && feeCode == null) {
            return null;
        }
        Charges target = new Charges();
        if (serviceChargeAmount != null || chargeCurrency != null) {
            ChargeAmount chargeAmount = new ChargeAmount();
            chargeAmount.setAmount(serviceChargeAmount);
            chargeAmount.setCurrency(chargeCurrency);
            target.setChargeAmount(chargeAmount);
        }
        target.setChargeBearer(chargeBearer);
        target.setFeeCode(feeCode);
        return target;
    }

    /** Builds legacy name fields with the existing CHATS short-name duplication rule. */
    static ChatsRemittance chatsRemittance(String creditorName) {
        ChatsRemittance chatsRemittance =
                new ChatsRemittance();
        chatsRemittance.setDepositAccountName1(creditorName.substring(0, Math.min(35, creditorName.length())));
        chatsRemittance.setDepositAccountName2(creditorName.length() <= 35
                ? creditorName : creditorName.substring(35, Math.min(70, creditorName.length())));
        if (creditorName.length() > 70) {
            chatsRemittance.setDepositAccountName3(creditorName.substring(70, Math.min(105, creditorName.length())));
        }
        if (creditorName.length() > 105) {
            chatsRemittance.setDepositAccountName4(creditorName.substring(105));
        }
        return chatsRemittance;
    }

    /** Corporate debtor block. */
    static Debtor corporateDebtor(String payerName) {
        Debtor target = new Debtor();
        target.setType(Debtor.TypeEnum.CORPORATE);
        target.setName(payerName);
        return target;
    }

    /** Debtor account block; null when every source field is absent. */
    static DebtorAccount debtorAccount(String identification, String internalAccountNo,
                                       String formattedAccount, String accountType, String currency,
                                       Integer companyCode, String productCode) {
        if (identification == null && internalAccountNo == null && formattedAccount == null
                && accountType == null && currency == null && companyCode == null && productCode == null) {
            return null;
        }
        DebtorAccount target = new DebtorAccount();
        target.setCompanyCode(companyCode);
        target.setProductCode(productCode);
        target.setIdentification(identification);
        target.setFormattedAccount(formattedAccount);
        target.setInternalAccountNo(internalAccountNo);
        target.setAccountType(accountType);
        target.setCurrency(currency);
        return target;
    }

    /** Creditor block; legacy creation rule ignores line2-only addresses. */
    static Creditor creditor(Creditor.TypeEnum payeeType, String name, String mobileNumber,
                             String emailAddress, String addressLine1, String addressLine2,
                             String townName, String country) {
        if (name == null && mobileNumber == null && emailAddress == null
                && addressLine1 == null && townName == null && country == null) {
            return null;
        }
        Creditor target = new Creditor();
        if (payeeType != null) {
            target.setType(payeeType == Creditor.TypeEnum.PROXY
                    ? Creditor.TypeEnum.PROXY : Creditor.TypeEnum.ACCOUNT);
        }
        target.setName(name);
        target.setMobileNumber(mobileNumber);
        target.setEmailAddress(emailAddress);
        if (addressLine1 != null || addressLine2 != null || townName != null || country != null) {
            PostalAddress address = new PostalAddress();
            address.setAddressLine1(addressLine1);
            address.setAddressLine2(addressLine2);
            address.setTownName(townName);
            address.setCountry(country);
            target.setPostalAddress(address);
        }
        return target;
    }

    /** Creditor account block; null when every source field is absent. */
    static CreditorAccount creditorAccount(String accountName, String identification, String currency,
                                           String proxyId, String proxyType, String country,
                                           String telephoneNumber) {
        if (accountName == null && identification == null && currency == null
                && proxyId == null && proxyType == null && country == null && telephoneNumber == null) {
            return null;
        }
        CreditorAccount target = new CreditorAccount();
        target.setAccountName(accountName);
        target.setIdentification(identification);
        target.setCurrency(currency);
        if (proxyId != null || proxyType != null) {
            ProxyIdentification proxy = new ProxyIdentification();
            proxy.setProxyId(proxyId);
            proxy.setProxyType(proxyType);
            target.setProxyIdentification(proxy);
        }
        target.setCountry(country);
        target.setTelephoneNumber(telephoneNumber);
        return target;
    }

    /** Creditor agent block (bankCode mirrors memberId, as in the legacy mapping). */
    static CreditorAgent creditorAgent(String bicfi, String memberId, String bankName,
                                       String addressLine1, String addressLine2, String addressLine3,
                                       String country) {
        if (bicfi == null && memberId == null && bankName == null
                && addressLine1 == null && addressLine2 == null && addressLine3 == null && country == null) {
            return null;
        }
        CreditorAgent target = new CreditorAgent();
        if (bicfi != null || memberId != null || bankName != null) {
            FinancialInstitution institution = new FinancialInstitution();
            institution.setBicfi(bicfi);
            institution.setMemberId(memberId);
            institution.setBankCode(memberId);
            institution.setName(bankName);
            target.setFinancialInstitution(institution);
        }
        if (addressLine1 != null || addressLine2 != null || addressLine3 != null || country != null) {
            CreditorAgentPostalAddress address = new CreditorAgentPostalAddress();
            address.setAddressLine1(addressLine1);
            address.setAddressLine2(addressLine2);
            address.setAddressLine3(addressLine3);
            address.setCountry(country);
            target.setPostalAddress(address);
        }
        return target;
    }

    /** Notification block from the shared customer-notification payload; null when absent. */
    static Notification notification(CustomerNotificationInformRequest source) {
        if (source == null) {
            return null;
        }
        Notification target = new Notification();
        target.setRiskLevel(source.getRiskLevel());
        target.setPreDesignated(source.getPreDesignated());
        target.setNotificationType(source.getNotificationType());
        target.setEmailAddress(source.getPayerEmailBox());
        target.setMobileNumber(source.getPayerPhoneNo());
        target.setOfficeEmail(source.getOfficeEmail());
        target.setAccountType(source.getAccountType());
        target.setSubAccount(source.getSubAccount());
        target.setSendToOwnAccount(source.getSendToOwnAccount());
        return target;
    }

    /** Party account company code: strip leading zeros; blank/unknown resolves to null. */
    static Integer parseCompanyCode(String companyCode) {
        if (companyCode == null || companyCode.isBlank()) {
            return null;
        }
        return Integer.valueOf(companyCode.replaceFirst("^0+", ""));
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

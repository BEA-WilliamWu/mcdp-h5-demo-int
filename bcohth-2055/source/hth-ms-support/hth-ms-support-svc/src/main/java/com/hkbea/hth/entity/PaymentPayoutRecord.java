package com.hkbea.hth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

//TODO This class will replace the PaymentPayout class in the current package in the near future.
@TableName(value = "HTH_PAYMENT_PAYOUT", schema = "HTH_BEA")
public class PaymentPayoutRecord {

    @TableId(value = "ID", type = IdType.INPUT)
    private String id;

    @TableField("MESSAGE_ID")
    private String messageId;

    @TableField("CUSTOMER_TRANSACTION_ID")
    private String customerTransactionId;

    @TableField("END_TO_END_ID")
    private String endToEndId;

    @TableField("PAYMENT_ID")
    private String paymentId;

    @TableField("PAYMENT_TYPE")
    private String paymentType;

    @TableField("PARTY_ID")
    private String partyId;

    @TableField("PARTY_NAME")
    private String partyName;

    @TableField("PAYEE_ID")
    private String payeeId;

    @TableField("DEBIT_ACCOUNT_ID")
    private String debitAccountId;

    @TableField("DEBIT_ACCOUNT_CUR")
    private String debitAccountCur;

    @TableField("DEBIT_ACCOUNT_TYPE")
    private String debitAccountType;

    @TableField("CREDIT_ACCOUNT_ID")
    private String creditAccountId;

    @TableField("CREDIT_ACCOUNT_NAME")
    private String creditAccountName;

    @TableField("CREDIT_ACCOUNT_TYPE")
    private String creditAccountType;

    @TableField("CREDIT_ACCOUNT_CUR")
    private String creditAccountCur;

    @TableField("CREDIT_BIC_CODE")
    private String creditBicCode;

    @TableField("CREDIT_MEMBER_ID")
    private String creditMemberId;

    @TableField("CREDIT_TOWN_NAME")
    private String creditTownName;

    @TableField("CREDIT_COUNTRY_CODE")
    private String creditCountryCode;

    @TableField("PAYEE_IDFN")
    private String payeeIdfn;

    @TableField("INSTD_AMOUNT")
    private BigDecimal instdAmount;

    @TableField("INSTD_CURRENCY")
    private String instdCurrency;

    @TableField("HKD_EQUIVALENT_AMOUNT")
    private BigDecimal hkdEquivalentAmount;

    @TableField("SVC_CUR")
    private String svcCur;

    @TableField("SVC_AMOUNT")
    private BigDecimal svcAmount;

    @TableField("SVC_TYPE")
    private String svcType;

    @TableField("CTGY_PURP")
    private String ctgyPurp;

    @TableField("PURP")
    private String purp;

    @TableField("REMARKS")
    private String remarks;

    @TableField("EMAIL_ADDRESS")
    private String emailAddress;

    @TableField("PRODUCT_CODE")
    private String prodCode;

    @TableField("IS_PAYLATER")
    private String isPayLater;

    @TableField("PAYMENT_DATE")
    private LocalDateTime paymentDate;

    @TableField("STATUS")
    private String status;

    @TableField("REJECT_MESSAGE")
    private String rejectMessage;

    @TableField("IPE_STATUS")
    private String ipeStatus;

    @TableField("IPE_TERMINAL")
    private Boolean ipeTerminal;

    @TableField("IPE_STATUS_REASON")
    private String ipeStatusReason;

    @TableField("IPE_REF_NO")
    private String ipeRefNo;

/*    @TableField("BANK_ADDRESS_LINE1")
    private String bankAddressLine1;

    @TableField("BANK_ADDRESS_LINE2")
    private String bankAddressLine2;

    @TableField("BANK_ADDRESS_LINE3")
    private String bankAddressLine3;

    @TableField("BANK_ADDRESS_LINE4")
    private String bankAddressLine4;

    @TableField("BANK_COUNTRY_ID")
    private String bankCountryId;*/

    @TableField("CREDIT_ADDRESS_LINE1")
    private String creditAddressLine1;

    @TableField("CREDIT_ADDRESS_LINE2")
    private String creditAddressLine2;

    @TableField("CREATED_BY")
    private String createdBy;

    @TableField("CREATION_DATE")
    private LocalDateTime creationDate;

    @TableField("LAST_UPDATED_BY")
    private String lastUpdatedBy;

    @TableField("LAST_UPDATED_DATE")
    private LocalDateTime lastUpdatedDate;

    @TableField("STATUS_FLAG")
    private String statusFlag;

    @TableField("EXECUTION_DATE")
    private String executionDate;

    // ==================== Getters & Setters ====================

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getCustomerTransactionId() {
        return customerTransactionId;
    }

    public void setCustomerTransactionId(String customerTransactionId) {
        this.customerTransactionId = customerTransactionId;
    }

    public String getEndToEndId() {
        return endToEndId;
    }

    public void setEndToEndId(String endToEndId) {
        this.endToEndId = endToEndId;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    public String getPartyId() {
        return partyId;
    }

    public void setPartyId(String partyId) {
        this.partyId = partyId;
    }

    public String getPartyName() {
        return partyName;
    }

    public void setPartyName(String partyName) {
        this.partyName = partyName;
    }

    public String getPayeeId() {
        return payeeId;
    }

    public void setPayeeId(String payeeId) {
        this.payeeId = payeeId;
    }

    public String getDebitAccountId() {
        return debitAccountId;
    }

    public void setDebitAccountId(String debitAccountId) {
        this.debitAccountId = debitAccountId;
    }

    public String getDebitAccountCur() {
        return debitAccountCur;
    }

    public void setDebitAccountCur(String debitAccountCur) {
        this.debitAccountCur = debitAccountCur;
    }

    public String getDebitAccountType() {
        return debitAccountType;
    }

    public void setDebitAccountType(String debitAccountType) {
        this.debitAccountType = debitAccountType;
    }

    public String getCreditAccountId() {
        return creditAccountId;
    }

    public void setCreditAccountId(String creditAccountId) {
        this.creditAccountId = creditAccountId;
    }

    public String getCreditAccountName() {
        return creditAccountName;
    }

    public void setCreditAccountName(String creditAccountName) {
        this.creditAccountName = creditAccountName;
    }

    public String getCreditAccountType() {
        return creditAccountType;
    }

    public void setCreditAccountType(String creditAccountType) {
        this.creditAccountType = creditAccountType;
    }

    public String getCreditAccountCur() {
        return creditAccountCur;
    }

    public void setCreditAccountCur(String creditAccountCur) {
        this.creditAccountCur = creditAccountCur;
    }

    public String getCreditBicCode() {
        return creditBicCode;
    }

    public void setCreditBicCode(String creditBicCode) {
        this.creditBicCode = creditBicCode;
    }

    public String getCreditMemberId() {
        return creditMemberId;
    }

    public void setCreditMemberId(String creditMemberId) {
        this.creditMemberId = creditMemberId;
    }

    public String getCreditTownName() {
        return creditTownName;
    }

    public void setCreditTownName(String creditTownName) {
        this.creditTownName = creditTownName;
    }

    public String getCreditCountryCode() {
        return creditCountryCode;
    }

    public void setCreditCountryCode(String creditCountryCode) {
        this.creditCountryCode = creditCountryCode;
    }

    public String getPayeeIdfn() {
        return payeeIdfn;
    }

    public void setPayeeIdfn(String payeeIdfn) {
        this.payeeIdfn = payeeIdfn;
    }

    public BigDecimal getInstdAmount() {
        return instdAmount;
    }

    public void setInstdAmount(BigDecimal instdAmount) {
        this.instdAmount = instdAmount;
    }

    public String getInstdCurrency() {
        return instdCurrency;
    }

    public void setInstdCurrency(String instdCurrency) {
        this.instdCurrency = instdCurrency;
    }

    public BigDecimal getHkdEquivalentAmount() {
        return hkdEquivalentAmount;
    }

    public void setHkdEquivalentAmount(BigDecimal hkdEquivalentAmount) {
        this.hkdEquivalentAmount = hkdEquivalentAmount;
    }

    public String getSvcCur() {
        return svcCur;
    }

    public void setSvcCur(String svcCur) {
        this.svcCur = svcCur;
    }

    public BigDecimal getSvcAmount() {
        return svcAmount;
    }

    public void setSvcAmount(BigDecimal svcAmount) {
        this.svcAmount = svcAmount;
    }

    public String getSvcType() {
        return svcType;
    }

    public void setSvcType(String svcType) {
        this.svcType = svcType;
    }

    public String getCtgyPurp() {
        return ctgyPurp;
    }

    public void setCtgyPurp(String ctgyPurp) {
        this.ctgyPurp = ctgyPurp;
    }

    public String getPurp() {
        return purp;
    }

    public void setPurp(String purp) {
        this.purp = purp;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getProdCode() {
        return prodCode;
    }

    public void setProdCode(String prodCode) {
        this.prodCode = prodCode;
    }

    public String getIsPayLater() {
        return isPayLater;
    }

    public void setIsPayLater(String isPayLater) {
        this.isPayLater = isPayLater;
    }

    public LocalDateTime getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDateTime paymentDate) {
        this.paymentDate = paymentDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRejectMessage() {
        return rejectMessage;
    }

    public void setRejectMessage(String rejectMessage) {
        this.rejectMessage = rejectMessage;
    }

    public String getIpeStatus() {
        return ipeStatus;
    }

    public void setIpeStatus(String ipeStatus) {
        this.ipeStatus = ipeStatus;
    }

    public Boolean getIpeTerminal() {
        return ipeTerminal;
    }

    public void setIpeTerminal(Boolean ipeTerminal) {
        this.ipeTerminal = ipeTerminal;
    }

    public String getIpeStatusReason() {
        return ipeStatusReason;
    }

    public void setIpeStatusReason(String ipeStatusReason) {
        this.ipeStatusReason = ipeStatusReason;
    }

    public String getIpeRefNo() {
        return ipeRefNo;
    }

    public void setIpeRefNo(String ipeRefNo) {
        this.ipeRefNo = ipeRefNo;
    }

/*    public String getBankAddressLine1() {
        return bankAddressLine1;
    }

    public void setBankAddressLine1(String bankAddressLine1) {
        this.bankAddressLine1 = bankAddressLine1;
    }

    public String getBankAddressLine2() {
        return bankAddressLine2;
    }

    public void setBankAddressLine2(String bankAddressLine2) {
        this.bankAddressLine2 = bankAddressLine2;
    }

    public String getBankAddressLine3() {
        return bankAddressLine3;
    }

    public void setBankAddressLine3(String bankAddressLine3) {
        this.bankAddressLine3 = bankAddressLine3;
    }

    public String getBankAddressLine4() {
        return bankAddressLine4;
    }

    public void setBankAddressLine4(String bankAddressLine4) {
        this.bankAddressLine4 = bankAddressLine4;
    }

    public String getBankCountryId() {
        return bankCountryId;
    }

    public void setBankCountryId(String bankCountryId) {
        this.bankCountryId = bankCountryId;
    }*/

    public String getCreditAddressLine1() {
        return creditAddressLine1;
    }

    public void setCreditAddressLine1(String creditAddressLine1) {
        this.creditAddressLine1 = creditAddressLine1;
    }

    public String getCreditAddressLine2() {
        return creditAddressLine2;
    }

    public void setCreditAddressLine2(String creditAddressLine2) {
        this.creditAddressLine2 = creditAddressLine2;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public void setLastUpdatedBy(String lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }

    public LocalDateTime getLastUpdatedDate() {
        return lastUpdatedDate;
    }

    public void setLastUpdatedDate(LocalDateTime lastUpdatedDate) {
        this.lastUpdatedDate = lastUpdatedDate;
    }

    public String getStatusFlag() {
        return statusFlag;
    }

    public void setStatusFlag(String statusFlag) {
        this.statusFlag = statusFlag;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getExecutionDate() {
        return executionDate;
    }

    public void setExecutionDate(String executionDate) {
        this.executionDate = executionDate;
    }
}

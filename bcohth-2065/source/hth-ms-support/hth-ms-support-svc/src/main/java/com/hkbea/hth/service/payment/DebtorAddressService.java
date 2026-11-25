package com.hkbea.hth.service.payment;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.ipe.model.PostalAddress;
import com.hkbea.hth.pe3.api.Pe3Api;
import com.hkbea.hth.pe3.model.Peechj7aAddressO;
import com.hkbea.hth.pe3.model.Peechj7aContact;
import com.hkbea.hth.pe3.model.Peechj7aContractI;
import com.hkbea.hth.pe3.model.Peechj7aDfhcommarea;
import com.hkbea.hth.pe3.model.Peechj7aInput;
import com.hkbea.hth.pe3.model.Peechj7aKeyAccI;
import com.hkbea.hth.pe3.model.Peechj7aQaechcc;
import com.hkbea.hth.pe3.model.Peechj7aRecord;
import com.hkbea.hth.pe3.model.Peechj7aRequest;
import com.hkbea.hth.pe3.model.Peechj7aResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

@Service
public class DebtorAddressService {
    private static final Logger log = LoggerFactory.getLogger(DebtorAddressService.class);
    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());
    private static final Set<String> NON_STRUCTURED_ADDRESS_TYPES =
            Set.of("40", "50", "60", "70", "80", "90", "00", "ZZ", "Z0", "Z1");

    private final Pe3Api pe3Api;
    private final CommonHeader headers;

    public DebtorAddressService(Pe3Api pe3Api, CommonHeader headers) {
        this.pe3Api = pe3Api;
        this.headers = headers;
    }

    public PostalAddress lookup(String internalAccountNumber) {
        try {
            var response = pe3Api.getCustomerContactInfo(buildContactInfoRequest(internalAccountNumber));
            return mapAddress(firstContact(response));
        } catch (Exception e) {
            // Client exceptions can contain the raw response. Log only the exception type.
            log.warn("PEECHJ7A debtor address omitted, messageId={}, exception={}",
                    headers.getMessageId(), e.getClass().getSimpleName());
            return null;
        }
    }

    private PostalAddress mapAddress(Peechj7aContact contact) {
        if (contact == null || contact.getEhj7aAddressO() == null) {
            log.debug("PEECHJ7A debtor address, messageId={}, countrySource=none, townNameSource=none",
                    headers.getMessageId());
            return null;
        }
        Peechj7aAddressO source = contact.getEhj7aAddressO();
        String contactType = contact.getEhj7aTypCntaadrO() == null ? ""
                : contact.getEhj7aTypCntaadrO().strip().toUpperCase(Locale.ROOT);
        char addressType = contactType.length() == 2 ? contactType.charAt(1) : '\0';
        String country = debtorCountry(contactType, addressType, source);
        String preferredTown = switch (addressType) {
            case 'H' -> source.getEhj7aDesDistrictO();
            case 'C', 'I' -> source.getEhj7aOvrCtyDesO();
            default -> null;
        };
        String town = PaymentRoutingPayload.firstNonBlank(preferredTown, source.getEhj7aDesCityO());
        if (town != null) {
            town = town.strip();
        }
        String[] lines = {source.getEhj7aExtAdrLin1O(), source.getEhj7aExtAdrLin2O(),
                source.getEhj7aExtAdrLin3O(), source.getEhj7aExtAdrLin4O()};
        boolean hasAddress = false;
        for (int i = 0; i < lines.length; i++) {
            if (StringUtils.isBlank(lines[i])) {
                lines[i] = null;
                continue;
            }
            lines[i] = lines[i].strip();
            if (lines[i].codePointCount(0, lines[i].length()) > 70) {
                log.warn("PEECHJ7A debtor address omitted, messageId={}, addressLine{} exceeds 70 characters",
                        headers.getMessageId(), i + 1);
                return null;
            }
            hasAddress = true;
        }
        if (log.isDebugEnabled()) {
            String countrySource = "none";
            if (country != null) {
                countrySource = NON_STRUCTURED_ADDRESS_TYPES.contains(contactType)
                        ? "ehj7aCodCtryValO" : "ehj7aCodCountryAdrO";
            }
            String townSource = "none";
            if (StringUtils.isNotBlank(preferredTown)) {
                townSource = addressType == 'H' ? "ehj7aDesDistrictO" : "ehj7aOvrCtyDesO";
            } else if (town != null) {
                townSource = "ehj7aDesCityO";
            }
            log.debug("PEECHJ7A debtor address, messageId={}, countrySource={}, townNameSource={}",
                    headers.getMessageId(), countrySource, townSource);
        }
        if (!hasAddress && country == null && town == null) {
            return null;
        }
        return new PostalAddress()
                .addressLine1(lines[0]).addressLine2(lines[1])
                .addressLine3(lines[2]).addressLine4(lines[3])
                .country(country).townName(town);
    }

    private String debtorCountry(String contactType, char addressType, Peechj7aAddressO source) {
        String code = null;
        if (NON_STRUCTURED_ADDRESS_TYPES.contains(contactType)) {
            code = source.getEhj7aCodCtryValO();
        } else if (addressType == 'H' || addressType == 'C' || addressType == 'I') {
            code = source.getEhj7aCodCountryAdrO();
        }
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String country = code.strip().toUpperCase(Locale.ROOT);
        if (!ISO_COUNTRIES.contains(country)) {
            log.warn("PEECHJ7A country code omitted, messageId={}, contactType={}, countryCode={}",
                    headers.getMessageId(), contactType, country);
            return null;
        }
        return country;
    }

    private Peechj7aContact firstContact(Peechj7aResponse response) {
        if (response == null || response.getDfhcommarea() == null
                || response.getDfhcommarea().getQaechcc() == null) {
            log.warn("PEECHJ7A debtor address omitted, missing response envelope, messageId={}", headers.getMessageId());
            return null;
        }
        var header = response.getDfhcommarea().getQaechcc();
        if (StringUtils.isNotBlank(header.getQaechccErrData())
                && !"0000".equals(header.getQaechccErrData().strip())) {
            log.warn("PEECHJ7A debtor address omitted, host header error, messageId={}", headers.getMessageId());
            return null;
        }
        var record = header.getPeechj7a();
        if (record == null || record.getEhj7aOutput() == null
                || record.getEhj7aOutput().getEhj7aAnswer() == null) {
            log.warn("PEECHJ7A debtor address omitted, missing host output or answer, messageId={}", headers.getMessageId());
            return null;
        }
        var output = record.getEhj7aOutput();
        var contacts = output.getEhj7aContactO();
        String returnCode = output.getEhj7aAnswer().getEhj7aCodReturn();
        if (!PaymentConstants.SUCC_CODE_00.equals(returnCode)) {
            log.warn("PEECHJ7A debtor address omitted, messageId={}, returnCode={}", headers.getMessageId(), returnCode);
            return null;
        }
        log.info("PEECHJ7A enquiry completed, messageId={}, returnCode={}, contactCount={}",
                headers.getMessageId(), returnCode, contacts == null ? 0 : contacts.size());
        if (contacts == null || contacts.isEmpty()) {
            return null;
        }
        // PEECHJ7A returns contacts sorted by address; preserve the returned order.
        return contacts.getFirst();
    }

    private Peechj7aRequest buildContactInfoRequest(String internalAccountNumber) {
        String entity = internalAccountNumber.substring(0, 4);
        String branch = internalAccountNumber.substring(4, 8);
        String account = internalAccountNumber.substring(8);
        Peechj7aInput input = new Peechj7aInput()
                .ehj7aCodEntityI(entity)
                .ehj7aContractI(new Peechj7aContractI()
                        .ehj7aKeyAccI(new Peechj7aKeyAccI()
                                .ehj7aKeyEntityI(entity).ehj7aBrnOpenI(branch).ehj7aNumAccountI(account)));
        return new Peechj7aRequest().dfhcommarea(new Peechj7aDfhcommarea()
                .qaechcc(new Peechj7aQaechcc()
                        .qaechccTypChannel(PaymentConstants.CHANNEL_TYPE_IN)
                        .qaechccApplId("PE").qaechccCollecName("PE9CHJ7A")
                        .qaechccCollecFunc("").qaechccDateOp(LocalDate.now().toString())
                        .qaechccCodEntity(entity).qaechccCodBrn("").qaechccCodTerm("")
                        .qaechccCodUser("").qaechccErrData("").qaechccFrArea("")
                        .peechj7a(new Peechj7aRecord().ehj7aInput(input))));
    }
}

package com.hkbea.hth.mapper;

import com.hkbea.hth.ipe.model.CustomerContactInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Mapper for DIGX_PI_PARTY_PREFERENCES
 */
@Mapper
public interface PartyPreferenceMapper {

    String selectCustomerNameByPartyId(@Param("partyId") String partyId,@Param("accountNumber") String accountNumber);

    String getOfficeEmail(@Param("partyId") String partyId);

    CustomerContactInfo getCustomerContactInfo(@Param("userName") String userName);

}

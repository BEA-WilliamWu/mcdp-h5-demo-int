"""External bank/approval/repository boundaries for production 1216 runtime tests."""
def write_access_fixtures(write, bean):
    repo='com.ofss.digx.cz.bea.domain.hosttohost.entity.repository'
    entity='com.ofss.digx.cz.bea.domain.hosttohost.entity'
    write('fixture/AccessBank.java', '\n'.join([
        'package fixture; import java.util.*; import '+entity+'.*;',
        'public class AccessBank {',
        'public static boolean hth=true; public static int reads, companyReads;',
        'public static final List<HthUserAccessAccount> accounts=new ArrayList<>();',
        'public static final Map<String,List<HthUserAccessAccountApi>> apis=new HashMap<>();',
        'public static com.ofss.digx.cz.bea.app.party.dto.profile.CZPartyPreferenceDTO company=new com.ofss.digx.cz.bea.app.party.dto.profile.CZPartyPreferenceDTO();',
        'public static String companyParty;',
        '}']))
    write(repo.replace('.','/')+'/HthUserAccessAccountRepository.java', 'package '+repo+'; import '+entity+'.*; public class HthUserAccessAccountRepository {'
        +'public static HthUserAccessAccountRepository getInstance(){return new HthUserAccessAccountRepository();}'
        +'public java.util.List<HthUserAccessAccount> listByContext(String p,String u,String a,String l){fixture.AccessBank.reads++;'
        +'java.util.List<HthUserAccessAccount> rows=new java.util.ArrayList<>();for(HthUserAccessAccount row:fixture.AccessBank.accounts)'
        +'if(p.equals(row.getPartyId()) && u.equals(row.getCloseId()) && a.equals(row.getAccessPartyId()) && l.equals(row.getLinkageType()))rows.add(row);return rows;}}')
    write(repo.replace('.','/')+'/HthUserAccessAccountApiRepository.java', 'package '+repo+'; import '+entity+'.*; public class HthUserAccessAccountApiRepository {'
        +'public static HthUserAccessAccountApiRepository getInstance(){return new HthUserAccessAccountApiRepository();}'
        +'public java.util.List<HthUserAccessAccountApi> listByAccountId(String id){return fixture.AccessBank.apis.get(id);}}')
    write(repo.replace('.','/')+'/HthUserProfileRepository.java','package '+repo+'; import '+entity+'.*; public class HthUserProfileRepository {'
        +'public static HthUserProfileRepository getInstance(){return new HthUserProfileRepository();}'
        +'public HthUserProfile read(HthUserProfileKey key){return fixture.AccessBank.hth?new HthUserProfile():null;}}')
    write('com/ofss/digx/app/adapter/AdapterFactoryConfigurator.java', '''package com.ofss.digx.app.adapter;
import java.lang.reflect.*;
public class AdapterFactoryConfigurator {
 public static AdapterFactoryConfigurator getInstance(){return new AdapterFactoryConfigurator();}
 public IAdapterFactory getAdapterFactory(String id){return (IAdapterFactory)Proxy.newProxyInstance(IAdapterFactory.class.getClassLoader(),new Class[]{IAdapterFactory.class},(p,m,a)->{
  if(!m.getName().equals("getAdapter"))throw new AssertionError(m.getName());
  Class<?> type=com.ofss.digx.cz.bea.app.sms.adapter.user.IUserExtensionAdapter.class;
  return Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p2,m2,a2)->{
   if(!m2.getName().equals("getPartyPreferences"))throw new AssertionError(m2.getName());
   fixture.AccessBank.companyReads++;fixture.AccessBank.companyParty=(String)a2[0];return fixture.AccessBank.company;
  });
 });}
}''')

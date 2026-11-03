"""Compile the full production CM service, then execute its actual catalogue/validation/save
methods with in-memory bank repositories. No Oracle, approval server or DSP is contacted.
"""
from pathlib import Path
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
PROJECTS = ROOT / 'consulting/middleware/projects'
SERVICE = next(p for p in PROJECTS.rglob('HostToHostUserAccess.java')
               if '/app/hosttohost/service/' in str(p))
CP = os.pathsep.join([str(ROOT / 'devtools/backend-compile/build/classes/java/main')]
                     + [str(p) for p in (ROOT / 'consulting/middleware/lib').rglob('*.jar')])
JDK = Path(os.environ['JAVA_HOME']) / 'bin'


def method(source, name):
    start = re.search(r'^  private [^\n]*\b' + name + r'\(', source, re.M).start()
    end = source.index('\n  }', start) + len('\n  }')
    return source[start:end]


def run(arguments):
    result = subprocess.run(arguments, check=False)
    if result.returncode:
        raise SystemExit(result.returncode)


with tempfile.TemporaryDirectory(prefix='hth876-') as temporary:
    work = Path(temporary)
    dto_files = [next(PROJECTS.rglob(name)) for name in (
        'HostToHostUserAccessDTO.java', 'HostToHostUserAccessAccountDTO.java',
        'HostToHostUserAccessApiDTO.java')]
    dependencies = [next(PROJECTS.rglob(name)) for name in (
        'HthUserAccessNotification.java', 'HthOnboardingAudit.java', 'HthUserAccessAudit.java',
        'UserManagementActivityLogDTO.java')]
    run([str(JDK / 'javac'), '-proc:none', '--release', '8', '-cp', CP,
         '-d', str(work / 'production'), str(SERVICE), *map(str, dto_files + dependencies)])
    fixtures = []

    def write(name, text):
        target = work / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)
        fixtures.append(str(target))

    def bean(pkg, cls, fields, extra=''):
        body = 'package ' + pkg + '; public class ' + cls + ' {\n'
        for name, typ in fields.items():
            body += f'private {typ} {name}; public {typ} get{name}(){{return {name};}} public void set{name}({typ} value){{{name}=value;}}\n'
        write(pkg.replace('.', '/') + '/' + cls + '.java', body + extra + '\n}')

    pkg = 'com.ofss.digx.cz.bea.domain.hosttohost.entity'
    repo = pkg + '.repository'
    write('fixture/Bank.java', '''package fixture;
import java.util.*;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.*;
import com.ofss.digx.cz.bea.app.hosttohost.dto.*;
public class Bank {
 public static final Map<String,HthApiMaster> masters=new LinkedHashMap<>();
 public static final Map<String,HthManagement> companies=new HashMap<>();
 public static final List<HthManagementApi> companyApis=new ArrayList<>();
 public static final List<HthUserAccessAccount> accounts=new ArrayList<>();
 public static final List<HthUserAccessAccountApi> grants=new ArrayList<>();
 public static final List<HostToHostUserAccessAccountDTO> eligible=new ArrayList<>();
 public static final List<String> writes=new ArrayList<>();
 public static void reset(){masters.clear();companies.clear();companyApis.clear();accounts.clear();grants.clear();eligible.clear();writes.clear();}
}''')
    for key in ('HthApiMasterKey', 'HthManagementKey', 'HthUserAccessAccountKey', 'HthUserAccessAccountApiKey'):
        bean(pkg, key, {'Id': 'String'})
    bean(pkg, 'HthApiMaster', {'Key': 'HthApiMasterKey', 'ApiCode': 'String', 'ApiName': 'String',
                              'ObjectStatus': 'String', 'DisplayOrder': 'Long'},
         'public HthApiMaster read(HthApiMasterKey key){return fixture.Bank.masters.get(key.getId());}')
    bean(pkg, 'HthManagement', {'Key': 'HthManagementKey', 'HthStatus': 'String'},
         'public HthManagement findActiveByPartyId(String party){return fixture.Bank.companies.get(party);}')
    bean(pkg, 'HthManagementApi', {'ApiMasterId': 'String', 'HthManagementId': 'String', 'ObjectStatus': 'String'}, '''
 public java.util.List<HthManagementApi> listActiveByManagementId(String id){
  java.util.List<HthManagementApi> result=new java.util.ArrayList<>();
  for(HthManagementApi row:fixture.Bank.companyApis)if(id.equals(row.getHthManagementId())&&"A".equals(row.getObjectStatus()))result.add(row);
  return result;
 }''')
    bean(pkg, 'HthUserAccessAccount', {'Key': 'HthUserAccessAccountKey', **{name: 'String' for name in (
        'PartyId', 'CloseId', 'AccessPartyId', 'LinkageType', 'AccountNumber', 'AccountNumberFormatted',
        'ProductCode', 'AccountType', 'Currency', 'ObjectStatus', 'CreatedBy', 'LastUpdatedBy')}})
    bean(pkg, 'HthUserAccessAccountApi', {'Key': 'HthUserAccessAccountApiKey', **{name: 'String' for name in (
        'HthUserAccessAccountId', 'ApiMasterId', 'ObjectStatus', 'CreatedBy', 'LastUpdatedBy')}})
    write(repo.replace('.', '/') + '/HthUserAccessAccountRepository.java', 'package ' + repo + ';\nimport ' + pkg + '''.*;
public class HthUserAccessAccountRepository {
 public static HthUserAccessAccountRepository getInstance(){return new HthUserAccessAccountRepository();}
 public java.util.List<HthUserAccessAccount> listByContext(String p,String u,String a,String l){
  java.util.List<HthUserAccessAccount> result=new java.util.ArrayList<>();
  for(HthUserAccessAccount row:fixture.Bank.accounts)
   if(p.equals(row.getPartyId())&&u.equals(row.getCloseId())&&a.equals(row.getAccessPartyId())&&l.equals(row.getLinkageType()))result.add(row);
  return result;
 }
 public void create(HthUserAccessAccount row){fixture.Bank.accounts.add(row);fixture.Bank.writes.add("ACCOUNT:"+row.getKey().getId());}
 public void update(HthUserAccessAccount row){fixture.Bank.writes.add("ACCOUNT:"+row.getKey().getId());}
}''')
    write(repo.replace('.', '/') + '/HthUserAccessAccountApiRepository.java', 'package ' + repo + ';\nimport ' + pkg + '''.*;
public class HthUserAccessAccountApiRepository {
 public static HthUserAccessAccountApiRepository getInstance(){return new HthUserAccessAccountApiRepository();}
 public java.util.List<HthUserAccessAccountApi> listByAccountId(String id){
  java.util.List<HthUserAccessAccountApi> result=new java.util.ArrayList<>();
  for(HthUserAccessAccountApi row:fixture.Bank.grants)if(id.equals(row.getHthUserAccessAccountId()))result.add(row);
  return result;
 }
 public HthUserAccessAccountApi findByAccountIdAndApiMasterId(String account,String api){
  for(HthUserAccessAccountApi row:fixture.Bank.grants)
   if(account.equals(row.getHthUserAccessAccountId())&&api.equals(row.getApiMasterId()))return row;
  return null;
 }
 public void create(HthUserAccessAccountApi row){fixture.Bank.grants.add(row);fixture.Bank.writes.add("API:"+row.getKey().getId());}
 public void update(HthUserAccessAccountApi row){fixture.Bank.writes.add("API:"+row.getKey().getId());}
}''')
    # Bank exception initialization loads server configuration; the harness preserves the code only.
    write('com/ofss/digx/infra/exceptions/Exception.java', '''package com.ofss.digx.infra.exceptions;
public class Exception extends java.lang.Exception {
 public Exception(String code){super(code);}
}''')
    source = SERVICE.read_text()
    names = ('listEnterpriseApis', 'validateSelectedAccess', 'applyApprovedAccess',
             'replaceEffectiveAccounts', 'deactivateAccounts', 'selectedAccounts', 'selectedApis',
             'isAuditLogApi', 'removeAuditLogServices', 'isAuditLogGrant', 'normalizeAccountType',
             'isSupportedAccountType', 'accountKey', 'normalizeAccountIdentifier', 'canonicalAccountNumber',
             'externalAccountNumber', 'mergeEffectiveGrants', 'groupActiveGrantIds', 'copyApis',
             'firstNonBlank', 'maskAccountNumber', 'generateId', 'normalize', 'safe')
    constants = '\n'.join(re.search(r'^  private static final String ' + name + r' = [^;]+;', source, re.M).group()
                          for name in ('ENABLE', 'DELETE', 'OBJECT_ACTIVE', 'OBJECT_INACTIVE',
                                       'ACCOUNT_TYPE_CSA', 'ACCOUNT_TYPE_TD', 'API_CODE_AUDIT_LOG'))
    write('com/ofss/digx/cz/bea/app/hosttohost/service/Hth876Harness.java', '''
package com.ofss.digx.cz.bea.app.hosttohost.service;
import java.util.*;
import com.ofss.digx.cz.bea.app.hosttohost.dto.*;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.*;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.*;
import com.ofss.digx.cz.bea.common.util.CZAccountHelper;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.digx.infra.exceptions.Exception;
public class Hth876Harness {
 private List<HostToHostUserAccessAccountDTO> listEligibleAccountsForValidation(SessionContext c,
     String p,String u,String a,String l,List<HostToHostUserAccessApiDTO> apis,boolean approved){
  return fixture.Bank.eligible;
 }
 public List<HostToHostUserAccessApiDTO> catalog() throws Exception{return listEnterpriseApis("PARTY");}
 public void validate(HostToHostUserAccessDTO r,boolean approved) throws Exception{validateSelectedAccess(null,r,approved);}
 public void save(HostToHostUserAccessDTO r,String action) throws Exception{
  if(!"DELETE".equals(action))validate(r,true);else removeAuditLogServices(r);
  applyApprovedAccess(r,action,"CHECKER");
 }
 public void readEffective(HostToHostUserAccessDTO r) throws Exception{
  for(HostToHostUserAccessAccountDTO account:r.getAccounts())account.setApiServices(copyApis(catalog()));
  mergeEffectiveGrants(r,HthUserAccessAccountRepository.getInstance().listByContext(
   r.getPartyId(),r.getCloseId(),r.getAccessPartyId(),r.getLinkageType()),catalog());
 }
''' + constants + '\n' + '\n'.join(method(source, name) for name in names) + '\n}')
    fixtures.append(str(Path(__file__).with_name('Hth876UserAccessTest.java')))
    run([str(JDK / 'javac'), '-proc:none', '--release', '8', '-cp', CP,
         '-d', str(work / 'fixtures'), *fixtures, *map(str, dto_files)])
    run([str(JDK / 'java'), '-cp', os.pathsep.join([str(work / 'fixtures'), CP]),
         'com.ofss.digx.cz.bea.app.hosttohost.service.Hth876UserAccessTest'])
print('PASS: complete CM production service compiles; 876 catalogue/validation/save regressions pass')

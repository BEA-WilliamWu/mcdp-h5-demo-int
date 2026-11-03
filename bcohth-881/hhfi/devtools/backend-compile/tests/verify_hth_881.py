"""Run production BM catalogue/save methods against in-memory repository fixtures.

Also compile the complete production service with current DTOs and workspace JARs.
No Oracle connection, approval engine, DSP request or notification is started.
"""
from pathlib import Path
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
PROJECTS = ROOT / 'consulting/middleware/projects'
SERVICE = next(p for p in PROJECTS.rglob('HostToHostManagement.java')
               if '/app/hosttohost/service/' in str(p))
CP = os.pathsep.join([str(ROOT / 'devtools/backend-compile/build/classes/java/main')]
                     + [str(p) for p in (ROOT / 'consulting/middleware/lib').rglob('*.jar')])
JDK = Path(os.environ['JAVA_HOME']) / 'bin'


def method(source, name):
    start = re.search(r'^    private [^\n]*\b' + name + r'\(', source, re.M).start()
    end = source.index('\n    }', start) + len('\n    }')
    return source[start:end]


with tempfile.TemporaryDirectory(prefix='hth881-') as temporary:
    work = Path(temporary)
    production_files = [SERVICE] + [next(PROJECTS.rglob(name)) for name in (
        'EligibleAccountDTO.java', 'HostToHostManagementDTO.java',
        'HostToHostApiAuthorizationDTO.java')]
    subprocess.run([str(JDK / 'javac'), '-proc:none', '--release', '8', '-cp', CP,
                    '-d', str(work / 'production'), *map(str, production_files)], check=True)

    fixtures = []

    def write(name, text):
        target = work / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)
        fixtures.append(str(target))

    write('fixture/Bank.java', '''package fixture;
import java.util.*;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.*;
public class Bank {
 public static Map<String,HthApiMaster> masters=new LinkedHashMap<>();
 public static List<HthManagementApi> links=new ArrayList<>();
 public static int writes;
}''')
    pkg = 'com.ofss.digx.cz.bea.domain.hosttohost.entity'
    for key in ('HthApiMasterKey', 'HthManagementApiKey'):
        write(pkg.replace('.', '/') + '/' + key + '.java', 'package ' + pkg + '; public class ' + key + ''' {
 private String id; public String getId(){return id;} public void setId(String value){id=value;}
}''')
    write(pkg.replace('.', '/') + '/HthApiMaster.java', 'package ' + pkg + ''';
import java.util.*;
public class HthApiMaster {
 private HthApiMasterKey key; private String code,name,status="A";
 public HthApiMasterKey getKey(){return key;} public void setKey(HthApiMasterKey value){key=value;}
 public String getApiCode(){return code;} public void setApiCode(String value){code=value;}
 public String getApiName(){return name;} public void setApiName(String value){name=value;}
 public String getObjectStatus(){return status;} public void setObjectStatus(String value){status=value;}
 public HthApiMaster read(HthApiMasterKey key){return fixture.Bank.masters.get(key.getId());}
 public List<HthApiMaster> listActive(){
  List<HthApiMaster> result=new ArrayList<>();
  for(HthApiMaster item:fixture.Bank.masters.values())if("A".equals(item.status))result.add(item);
  return result;
 }
 public List<HthApiMaster> listActiveByApiCodes(Set<String> codes){
  List<HthApiMaster> result=new ArrayList<>();
  for(HthApiMaster item:listActive())if(codes.contains(item.code))result.add(item);
  return result;
 }
}''')
    write(pkg.replace('.', '/') + '/HthManagementApi.java', 'package ' + pkg + ''';
import java.util.*;
public class HthManagementApi {
 private HthManagementApiKey key; private String management,master,status,createdBy,updatedBy;
 public HthManagementApiKey getKey(){return key;} public void setKey(HthManagementApiKey value){key=value;}
 public String getHthManagementId(){return management;} public void setHthManagementId(String value){management=value;}
 public String getApiMasterId(){return master;} public void setApiMasterId(String value){master=value;}
 public String getObjectStatus(){return status;} public void setObjectStatus(String value){status=value;}
 public void setCreatedBy(String value){createdBy=value;} public void setLastUpdatedBy(String value){updatedBy=value;}
 public String getLastUpdatedBy(){return updatedBy;}
 public List<HthManagementApi> listActiveByManagementId(String id){
  List<HthManagementApi> result=new ArrayList<>();
  for(HthManagementApi item:fixture.Bank.links)if(id.equals(item.management)&&"A".equals(item.status))result.add(item);
  return result;
 }
 public HthManagementApi findByManagementIdAndApiMasterId(String id,String api){
  for(HthManagementApi item:fixture.Bank.links)if(id.equals(item.management)&&api.equals(item.master))return item;
  return null;
 }
 public void create(HthManagementApi item){fixture.Bank.links.add(item);fixture.Bank.writes++;}
 public void update(HthManagementApi item){fixture.Bank.writes++;}
}''')
    source = SERVICE.read_text()
    names = ('fetchApiAuthorizations', 'fetchSelectedApiCodes', 'fetchActiveApiByCode',
             'mapApiMastersByCode', 'extractSelectedApiCodes', 'hasSelectedApiAuthorization',
             'isMaintainableApiCode', 'refreshEffectiveManagementApis',
             'deactivateEffectiveManagementApis', 'isAuditLogMapping',
             'upsertEffectiveManagementApiLinks', 'createEffectiveManagementApiLink',
             'reactivateEffectiveManagementApiLink', 'assertAllApiCodesResolved',
             'isDisableAction', 'normalize', 'isBlank')
    constants = '\n'.join(re.search(r'^    private static final String ' + name + r' = [^;]+;', source, re.M).group()
                          for name in ('OBJECT_STATUS_ACTIVE', 'OBJECT_STATUS_INACTIVE',
                                       'API_CODE_AUDIT_LOG', 'ACTION_DISABLE'))
    write('com/ofss/digx/cz/bea/app/hosttohost/service/Hth881Harness.java', '''
package com.ofss.digx.cz.bea.app.hosttohost.service;
import java.util.*;
import com.ofss.digx.cz.bea.app.hosttohost.dto.*;
import com.ofss.digx.cz.bea.domain.hosttohost.entity.*;
public class Hth881Harness {
 private String generateId(String type){return type+"-"+fixture.Bank.links.size();}
 public List<HostToHostApiAuthorizationDTO> catalog(Set<String> selected) throws Exception{return fetchApiAuthorizations(selected);}
 public Set<String> effective(String id) throws Exception{return fetchSelectedApiCodes(id);}
 public Set<String> requested(HostToHostManagementDTO request){return extractSelectedApiCodes(request);}
 public boolean hasSelection(HostToHostManagementDTO request){return hasSelectedApiAuthorization(request);}
 public void save(HostToHostManagementDTO request,String action) throws Exception{refreshEffectiveManagementApis("COMPANY",request,action,"CHECKER");}
''' + constants + '\n' + '\n'.join(method(source, name) for name in names) + '\n}')
    fixtures.append(str(Path(__file__).with_name('Hth881ManagementTest.java')))
    fixture_cp = os.pathsep.join([str(work / 'fixtures'), CP])
    subprocess.run([str(JDK / 'javac'), '-proc:none', '--release', '8', '-cp', CP,
                    '-d', str(work / 'fixtures'), *fixtures,
                    *map(str, production_files[2:])], check=True)
    subprocess.run([str(JDK / 'java'), '-cp', fixture_cp,
                    'com.ofss.digx.cz.bea.app.hosttohost.service.Hth881ManagementTest'], check=True)
print('PASS: complete BM production service compiles; 881 catalogue/save regressions pass')

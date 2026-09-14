"""Run the production OBDX metadata lookup with the exact DTO paths from deployment SQL.

Only ActivityLog's bank-name/bootstrap parent is replaced; HTH getters and the
framework HostServiceMetadataService implementation are real. SQL replay tests
cover relational wiring. This does not send email or bootstrap the UAT event engine.
"""
from pathlib import Path
import subprocess,tempfile,os,re
root=Path(__file__).resolve().parents[3];java=Path(os.environ['JAVA_HOME'])/'bin'
cp=':'.join(str(p) for p in (root/'consulting/middleware/lib').rglob('*.jar'))
dto=root/'consulting/middleware/projects/common/com.ofss.digx.cz.bea.app.xface/src/com/ofss/digx/cz/bea/app/hosttohost/dto/HthApiPasswordActivityLogDTO.java'
source='''import java.lang.reflect.Proxy;
import java.util.*;
import com.ofss.fc.framework.domain.service.hostservice.metadata.HostServiceMetadataService;
import com.ofss.fc.framework.domain.entity.service.metadata.IServiceAttribute;
import com.ofss.fc.framework.domain.metadata.IGenericAttributeDefinition;
import com.ofss.fc.enumeration.MessageDataSourceType;
import com.ofss.fc.datatype.NameValuePair;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthApiPasswordActivityLogDTO;
public class HthTemplateMetadataTest {
 public static void main(String[] args) throws Exception {
   HthApiPasswordActivityLogDTO log=new HthApiPasswordActivityLogDTO();
   log.setHthApiPasswordUserName("TEST_USER");log.setHthApiPasswordExpiryDateTime("15/09/2030 20:30:00");
   HostServiceMetadataService service=new HostServiceMetadataService(null);
   for(int i=0;i<args.length;i+=3){
     final String field=args[i], ref=args[i+1], expected=args[i+2];
     IGenericAttributeDefinition generic=(IGenericAttributeDefinition)Proxy.newProxyInstance(IGenericAttributeDefinition.class.getClassLoader(),new Class[]{IGenericAttributeDefinition.class},(p,m,a)-> {
       if(m.getName().equals("getName"))return field;
       if(m.getName().equals("getDataType"))return "java.lang.String";
       return null;
     });
     IServiceAttribute attr=(IServiceAttribute)Proxy.newProxyInstance(IServiceAttribute.class.getClassLoader(),new Class[]{IServiceAttribute.class},(p,m,a)-> {
       switch(m.getName()){
         case "fetchIsInError":return false;
         case "getGenericAttribute":return generic;
         case "getSourceType":return MessageDataSourceType.DTO;
         case "getRefFieldDefnId":return ref;
         default:return null;
       }
     });
     NameValuePair value=service.fetchNameValue(attr,Collections.emptyMap(),null,log);
     if(!expected.equals(value.getValue()))throw new AssertionError(field+" failed runtime lookup");
   }
   System.out.println("PASS: production HostServiceMetadataService resolves configured DTO paths");
 }
}'''
sql=(root/'consulting/db/branch_change_history/20260907_HTH_API_Password/6_HTH_API_Password_Notification.sql').read_text()
paths=re.findall(r"REF_FIELD_DEFN_ID = '([^']+)'",sql)
args=[]
for ref in paths:
 field=ref.rsplit('.',1)[1];field=field[0].lower()+field[1:]
 args += [field,ref,'TEST_USER' if field.endswith('UserName') else '15/09/2030 20:30:00']
assert len(args)==6
with tempfile.TemporaryDirectory(prefix='hth-template-runtime-') as tmp:
 p=Path(tmp)/'HthTemplateMetadataTest.java';p.write_text(source)
 stub=Path(tmp)/'ActivityLog.java';stub.write_text('package com.ofss.digx.app.alerts.dto.eventgen; public class ActivityLog extends com.ofss.fc.xface.ep.dto.ActivityLog {}')
 subprocess.run([str(java/'javac'),'-proc:none','--release','8','-cp',cp,'-d',tmp,str(stub),str(dto),str(p)],check=True)
 r=subprocess.run([str(java/'java'),'-cp',tmp+':'+cp,'HthTemplateMetadataTest',*args],capture_output=True,text=True)
 print((r.stdout+r.stderr)[-4000:]);raise SystemExit(r.returncode)

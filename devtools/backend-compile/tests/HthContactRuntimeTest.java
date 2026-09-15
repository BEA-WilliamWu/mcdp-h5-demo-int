import java.nio.file.*;
import java.lang.reflect.Proxy;
import java.util.*;
import fixture.Bank;
import com.ofss.digx.cz.bea.app.sms.dto.user.HthProfileContactUpdateActivityLogDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthUserAccessActivityLogDTO;
import com.ofss.fc.framework.domain.service.hostservice.metadata.HostServiceMetadataService;
import com.ofss.fc.framework.domain.entity.service.metadata.IServiceAttribute;
import com.ofss.fc.framework.domain.metadata.IGenericAttributeDefinition;
import com.ofss.fc.enumeration.MessageDataSourceType;
import com.ofss.fc.datatype.NameValuePair;

public class HthContactRuntimeTest {
    public static void main(String[] args)throws Exception{
        Bank.source.setURL("jdbc:h2:mem:hth851;MODE=Oracle;DB_CLOSE_DELAY=-1");Bank.source.setUser("sa");Bank.source.setPassword("");
        Bank.sql(new String(Files.readAllBytes(Paths.get(args[0],"ledger.sql")),java.nio.charset.StandardCharsets.UTF_8));
        Bank.sql("CREATE TABLE DIGX_CZ_EMAIL_MNG(REFNUMBER VARCHAR2(100) PRIMARY KEY,RECIPIENTID VARCHAR2(320),MESSAGEBODY VARCHAR2(4000),SUBJECT VARCHAR2(4000),CUSTOMERID VARCHAR2(254),PARTYID VARCHAR2(50),ACTIVITYID VARCHAR2(500),ACTIONID VARCHAR2(10),EVENTID VARCHAR2(100),ORG_REF_NO VARCHAR2(100),ALERT_TYPE VARCHAR2(5),RESPONSE_STATUS VARCHAR2(30),COD_ACT_DATA_ID VARCHAR2(100),TXNTYPE VARCHAR2(100),LAST_UPDATED_DATE TIMESTAMP)");
        Bank.sql("CREATE TABLE DIGX_PI_PARTY_PREFERENCES(PARTYID VARCHAR2(50), OFFICE_EMAIL VARCHAR2(320))");
        Bank.sql("CREATE TABLE DIGX_CZ_BATCH_BOUNCE_BACK_CCBEMAIL(SRC_SYS_REF_NUM VARCHAR2(100))");
        com.ofss.digx.cz.bea.app.sms.service.user.HthContactCaptureTest.run();
        com.ofss.digx.cz.bea.domain.service.dispatch.HthContactDeliveryTest.run();
        Bank.sql(new String(Files.readAllBytes(Paths.get(args[0],"access-ledger.sql")),java.nio.charset.StandardCharsets.UTF_8));
        com.ofss.digx.cz.bea.app.hosttohost.service.HthAccessCaptureTest.run();
        com.ofss.digx.cz.bea.domain.service.dispatch.HthAccessDeliveryTest.run();
        HthProfileContactUpdateActivityLogDTO log=new HthProfileContactUpdateActivityLogDTO();
        log.setHthContactUserName("TEST_USER");log.setHthContactApprovedAt("15/09/2026 12:30:00");
        HostServiceMetadataService service=new HostServiceMetadataService(null);
        for(String line:Files.readAllLines(Paths.get(args[0],"metadata.tsv"))){
            String[] parts=line.split("\t");final String field=parts[0],ref=parts[1];
            IGenericAttributeDefinition generic=(IGenericAttributeDefinition)Proxy.newProxyInstance(IGenericAttributeDefinition.class.getClassLoader(),new Class[]{IGenericAttributeDefinition.class},(p,m,a)->{
                if(m.getName().equals("getName"))return field;if(m.getName().equals("getDataType"))return "java.lang.String";return null;});
            IServiceAttribute attr=(IServiceAttribute)Proxy.newProxyInstance(IServiceAttribute.class.getClassLoader(),new Class[]{IServiceAttribute.class},(p,m,a)->{
                switch(m.getName()){case "fetchIsInError":return false;case "getGenericAttribute":return generic;case "getSourceType":return MessageDataSourceType.DTO;case "getRefFieldDefnId":return ref;default:return null;}});
            NameValuePair value=service.fetchNameValue(attr,Collections.emptyMap(),null,log);
            Bank.check((field.endsWith("UserName")?"TEST_USER":"15/09/2026 12:30:00").equals(value.getValue()),"real metadata lookup: "+field);
        }
        System.out.println("PASS: production OBDX metadata resolves both SQL-configured 851 DTO getters");
        verifyAccessTemplates(Paths.get(args[0]),service);
    }
    private static void verifyAccessTemplates(Path work,HostServiceMetadataService service)throws Exception {
        // Use a DTO published by the production 1216 service, not a manually populated substitute.
        HthUserAccessActivityLogDTO log=null;
        for(com.ofss.digx.app.alerts.dto.eventgen.ActivityLog candidate:Bank.logs)
            if(candidate instanceof HthUserAccessActivityLogDTO){log=(HthUserAccessActivityLogDTO)candidate;break;}
        Bank.check(log!=null,"production 1216 publisher supplied template DTO");
        Map<String,String> expected=new HashMap<>();
        expected.put("userNameId",log.getUserNameId());expected.put("userSysDate",log.getUserSysDate());expected.put("compName",log.getCompName());
        for(String value:expected.values())Bank.check(value!=null && !value.isEmpty(),"published notification has all template values");
        Map<String,String> values=new HashMap<>();int lookups=0;
        for(String line:Files.readAllLines(work.resolve("access-metadata.tsv"))){
            String[] parts=line.split("\t");final String field=parts[0],ref=parts[1];
            IGenericAttributeDefinition generic=(IGenericAttributeDefinition)Proxy.newProxyInstance(IGenericAttributeDefinition.class.getClassLoader(),new Class[]{IGenericAttributeDefinition.class},(p,m,a)->{
                if(m.getName().equals("getName"))return field;if(m.getName().equals("getDataType"))return "java.lang.String";return null;});
            IServiceAttribute attr=(IServiceAttribute)Proxy.newProxyInstance(IServiceAttribute.class.getClassLoader(),new Class[]{IServiceAttribute.class},(p,m,a)->{
                switch(m.getName()){case "fetchIsInError":return false;case "getGenericAttribute":return generic;case "getSourceType":return MessageDataSourceType.DTO;case "getRefFieldDefnId":return ref;default:return null;}});
            NameValuePair value=service.fetchNameValue(attr,Collections.emptyMap(),null,log);
            Bank.check(expected.get(field).equals(value.getValue()),"real 1216 metadata lookup: "+field);
            values.put(field,String.valueOf(value.getValue()));lookups++;
        }
        Bank.check(lookups==6 && values.size()==3,"both LINK/UPDATE services have three resolved metadata fields");
        int rendered=0;
        for(String line:Files.readAllLines(work.resolve("access-templates.tsv"))){
            String[] parts=line.split("\t",-1);
            String body=new String(Base64.getDecoder().decode(parts[1]),java.nio.charset.StandardCharsets.UTF_8);
            String subject=new String(Base64.getDecoder().decode(parts[2]),java.nio.charset.StandardCharsets.UTF_8);
            for(Map.Entry<String,String> entry:values.entrySet()){
                body=body.replace("#"+entry.getKey()+"#",entry.getValue());
                subject=subject.replace("#"+entry.getKey()+"#",entry.getValue());
            }
            Bank.check(!java.util.regex.Pattern.compile("#[A-Za-z][A-Za-z0-9_]*#").matcher(body+subject).find(),"no unresolved SQL template placeholders: "+parts[0]);
            if(parts[0].contains("_EMAIL_"))for(String value:expected.values())Bank.check(body.contains(value),"email has actual published values: "+parts[0]);
            rendered++;
        }
        Bank.check(rendered==12,"all 1216 deployed templates rendered");
        System.out.println("PASS: production 1216 DTO + OBDX metadata getters resolve all 12 SQL templates (six service bindings)");
    }
}

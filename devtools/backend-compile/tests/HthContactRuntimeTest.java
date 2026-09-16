import java.nio.file.*;
import java.lang.reflect.Proxy;
import java.util.*;
import fixture.Bank;
import com.ofss.digx.cz.bea.app.sms.dto.user.HthProfileContactUpdateActivityLogDTO;
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
        HthProfileContactUpdateActivityLogDTO log=new HthProfileContactUpdateActivityLogDTO();
        log.setProfileUser("TEST_USER");log.setEmailId("maske********");log.setEngMailSubj("Email Address Update");
        log.setEngMailContent("email address.");log.setZhMailSubj("測試");log.setZhMailContent("電郵地址");log.setUserId("TEST_USER@PARTY");
        log.setHthContactNotificationId("snapshot-id");
        com.ofss.fc.framework.domain.entity.ep.dto.ActivityData activity=new com.ofss.fc.framework.domain.entity.ep.dto.ActivityData();
        activity.setActivityLog(log);
        Bank.check(activity.getActivityLog() instanceof HthProfileContactUpdateActivityLogDTO,"real activity serialization preserves HTH subtype");
        log=(HthProfileContactUpdateActivityLogDTO)activity.getActivityLog();
        Bank.check("snapshot-id".equals(log.getHthContactNotificationId()),"real activity serialization preserves snapshot id");
        HostServiceMetadataService service=new HostServiceMetadataService(null);
        for(String field:new String[]{"ProfileUser","EmailId","EngMailSubj","EngMailContent","ZhMailSubj","ZhMailContent","UserId"}){
            final String ref="com.ofss.digx.cz.bea.app.sms.dto.user.UserProfUpdateActivityLogDTO."+field;
            IGenericAttributeDefinition generic=(IGenericAttributeDefinition)Proxy.newProxyInstance(IGenericAttributeDefinition.class.getClassLoader(),new Class[]{IGenericAttributeDefinition.class},(p,m,a)->{
                if(m.getName().equals("getName"))return field;if(m.getName().equals("getDataType"))return "java.lang.String";return null;});
            IServiceAttribute attr=(IServiceAttribute)Proxy.newProxyInstance(IServiceAttribute.class.getClassLoader(),new Class[]{IServiceAttribute.class},(p,m,a)->{
                switch(m.getName()){case "fetchIsInError":return false;case "getGenericAttribute":return generic;case "getSourceType":return MessageDataSourceType.DTO;case "getRefFieldDefnId":return ref;default:return null;}});
            NameValuePair value=service.fetchNameValue(attr,Collections.emptyMap(),null,log);
            Bank.check(log.getClass().getMethod("get"+field).invoke(log).equals(value.getValue()),"real inherited BCO metadata lookup: "+field);
        }
        System.out.println("PASS: production OBDX metadata resolves inherited BCO DTO getters on the 851 subtype");
    }
}

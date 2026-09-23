package com.ofss.digx.cz.bea.app.hosttohost.mtb;

import java.sql.*;
import java.util.*;
import java.lang.reflect.*;
import java.nio.file.*;
import com.ofss.fc.infra.das.orm.Session;
import com.ofss.fc.infra.das.orm.Query;

/** Real H2 transactions with Session API proxies; Oracle timestamp syntax is the only SQL rewrite. */
public class HthMtbJdbcTest {
    static final String URL="jdbc:h2:mem:mtb;MODE=Oracle;DB_CLOSE_DELAY=-1";
    static int closes;
    static void check(boolean b){if(!b)throw new AssertionError();}
    static int count(Connection c,String table)throws Exception {
        try(ResultSet rs=c.createStatement().executeQuery("SELECT COUNT(*) FROM "+table)){rs.next();return rs.getInt(1);}
    }
    public static void main(String[] args)throws Exception {
        Class.forName("org.h2.Driver");
        try(Connection business=DriverManager.getConnection(URL);Connection observer=DriverManager.getConnection(URL)) {
            business.createStatement().execute("CREATE SCHEMA HTH_BEA");
            business.createStatement().execute(new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8"));
            business.createStatement().execute("CREATE UNIQUE INDEX DEDUP ON HTH_BEA.HTH_MTB_EVENT_DETAILS(DEDUP_KEY)");
            business.createStatement().execute("CREATE TABLE BCO_BUSINESS(ID INT PRIMARY KEY)");
            business.setAutoCommit(false);business.createStatement().executeUpdate("INSERT INTO BCO_BUSINESS VALUES(1)");
            Map<String,Object> source=HthMtbTest.data("RESET");source.put("requestId","same-request");
            HthMtbEvent event=HthMtbEventAssembler.assemble("x.HostToHostApiPassword.reset",source,null);
            HthMtbWriter.write(event,new DbResources());
            check(count(observer,"BCO_BUSINESS")==0); // HTH commit did not commit caller
            check(count(observer,"HTH_BEA.HTH_MTB_EVENT_DETAILS")==1);
            business.rollback();check(count(observer,"BCO_BUSINESS")==0);
            check(count(observer,"HTH_BEA.HTH_MTB_EVENT_DETAILS")==1); // caller rollback did not rollback HTH
            HthMtbWriter.write(HthMtbEventAssembler.assemble("x.HostToHostApiPassword.reset",source,null),new DbResources());
            check(count(observer,"HTH_BEA.HTH_MTB_EVENT_DETAILS")==1); // DB unique constraint prevents replay
            business.createStatement().executeUpdate("INSERT INTO BCO_BUSINESS VALUES(2)");
            observer.createStatement().execute("DROP TABLE HTH_BEA.HTH_MTB_EVENT_DETAILS");
            HthMtbWriter.write(event,new DbResources()); // a real database failure must not escape
            business.commit();check(count(observer,"BCO_BUSINESS")==1);
            check(closes==3);
        }
        System.out.println("PASS: real H2 inserts, unique replay rejection, independent commit/rollback, table failure and caller completion");
    }
    static class DbResources implements HthMtbWriter.Resources {
        Connection connection;boolean active;
        public int transactionStatus(){return javax.transaction.Status.STATUS_NO_TRANSACTION;}
        public Session open()throws Exception {
            connection=DriverManager.getConnection(URL);connection.setAutoCommit(false);
            com.ofss.fc.infra.das.orm.Transaction tx=(com.ofss.fc.infra.das.orm.Transaction)Proxy.newProxyInstance(
                Session.class.getClassLoader(),new Class[]{com.ofss.fc.infra.das.orm.Transaction.class},(p,m,a)->{
                    if(m.getName().equals("isActive"))return active;
                    if(m.getName().equals("commit")){connection.commit();active=false;return null;}
                    if(m.getName().equals("rollback")){connection.rollback();active=false;return null;}
                    throw new AssertionError(m.getName());
                });
            return (Session)Proxy.newProxyInstance(Session.class.getClassLoader(),new Class[]{Session.class},(p,m,a)->{
                if(m.getName().equals("beginTransaction")){active=true;return tx;}
                if(m.getName().equals("fetchCurrentTransaction"))return tx;
                if(m.getName().equals("createSQLQuery")) {
                    String sql=((String)a[0]).replace("CAST(SYSTIMESTAMP AT TIME ZONE 'Asia/Hong_Kong' AS TIMESTAMP)","CURRENT_TIMESTAMP");
                    Map<Integer,Object> parameters=new TreeMap<Integer,Object>();
                    return Proxy.newProxyInstance(Session.class.getClassLoader(),new Class[]{Query.class},(qp,qm,qa)->{
                        if(qm.getName().equals("setParameter")){parameters.put((Integer)qa[0],qa[1]);return qp;}
                        if(qm.getName().equals("setTimeout"))return qp;
                        if(qm.getName().equals("executeUpdate")) {
                            try(PreparedStatement statement=connection.prepareStatement(sql)) {
                                for(Map.Entry<Integer,Object> entry:parameters.entrySet())statement.setObject(entry.getKey(),entry.getValue());
                                return statement.executeUpdate();
                            }
                        }
                        throw new AssertionError(qm.getName());
                    });
                }
                throw new AssertionError(m.getName());
            });
        }
        public void close(Session session)throws Exception {closes++;connection.close();}
    }
}

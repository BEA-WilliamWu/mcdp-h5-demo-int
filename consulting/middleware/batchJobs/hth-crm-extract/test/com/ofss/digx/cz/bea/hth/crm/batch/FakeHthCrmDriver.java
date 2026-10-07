package com.ofss.digx.cz.bea.hth.crm.batch;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

/** In-memory JDBC fixture exercising the actual command-line job. */
public final class FakeHthCrmDriver implements Driver {
  static { try { DriverManager.registerDriver(new FakeHthCrmDriver()); }
    catch (SQLException e) { throw new ExceptionInInitializerError(e); } }

  public boolean acceptsURL(String url) { return "jdbc:fake:hth".equals(url); }
  public Connection connect(String url, Properties info) {
    if (!acceptsURL(url)) return null;
    return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
        new Class<?>[] {Connection.class}, new InvocationHandler() {
          public Object invoke(Object proxy, Method method, Object[] args) {
            if ("prepareStatement".equals(method.getName())) return statement();
            if ("setReadOnly".equals(method.getName()) || "close".equals(method.getName())) return null;
            throw new UnsupportedOperationException(method.getName());
          }
        });
  }

  private PreparedStatement statement() {
    return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
        new Class<?>[] {PreparedStatement.class}, new InvocationHandler() {
          String date;
          public Object invoke(Object proxy, Method method, Object[] args) {
            if ("setString".equals(method.getName())) { date = (String) args[1]; return null; }
            if ("setFetchSize".equals(method.getName()) || "close".equals(method.getName())) return null;
            if ("executeQuery".equals(method.getName())) return rows(date);
            throw new UnsupportedOperationException(method.getName());
          }
        });
  }

  private ResultSet rows(String date) {
    final String[][] data;
    if ("20261007".equals(date)) {
      data = new String[][] {row("CDC1", "HTH_PWD_CRD"), row("CDC2", "HTH_PWD_UPD")};
      if ("Y".equals(System.getenv("HTH_CRM_FAKE_DUPLICATE"))) data[1][2] = "CDC1";
    } else data = new String[0][];
    return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
        new Class<?>[] {ResultSet.class}, new InvocationHandler() {
          int current = -1;
          public Object invoke(Object proxy, Method method, Object[] args) {
            if ("next".equals(method.getName())) return ++current < data.length;
            if ("getString".equals(method.getName())) return data[current][((Integer) args[0]) - 1];
            if ("close".equals(method.getName())) return null;
            throw new UnsupportedOperationException(method.getName());
          }
        });
  }

  private String[] row(String id, String activity) {
    String[] row = new String[HthCrmExtractJob.FIELDS.length];
    row[0] = "50";
    row[2] = id;
    row[3] = "20261007";
    row[5] = "ELE-HTH";
    row[10] = activity;
    return row;
  }

  public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
  public int getMajorVersion() { return 1; }
  public int getMinorVersion() { return 0; }
  public boolean jdbcCompliant() { return false; }
  public Logger getParentLogger() { return Logger.getGlobal(); }
}

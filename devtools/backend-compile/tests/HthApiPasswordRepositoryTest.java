import com.ofss.fc.infra.das.orm.Query;
import com.ofss.fc.infra.das.orm.Session;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exercises session-bound adapters without permitting transaction or connection operations. */
public final class HthApiPasswordRepositoryTest {
  private static String sql;
  private static final Map<Integer, Object> bindings = new LinkedHashMap<Integer, Object>();
  private static int statements;

  public static void main(String[] args) throws java.lang.Exception {
    Query query = (Query) Proxy.newProxyInstance(Query.class.getClassLoader(),
        new Class<?>[] {Query.class}, (proxy, method, values) -> {
          if ("setParameter".equals(method.getName())) {
            bindings.put((Integer) values[0], values[1]);
            return method.getReturnType() == Void.TYPE ? null : proxy;
          }
          if ("setMaxResults".equals(method.getName())) {
            return method.getReturnType() == Void.TYPE ? null : proxy;
          }
          if ("list".equals(method.getName()) || "executeUpdate".equals(method.getName())) {
            int placeholders = sql.length() - sql.replace("?", "").length();
            if (bindings.size() != placeholders) {
              throw new AssertionError("Incomplete SQL bindings: " + bindings.keySet());
            }
            for (int i = 1; i <= placeholders; i++) {
              if (!bindings.containsKey(i)) {
                throw new AssertionError("Missing parameter " + i);
              }
            }
            statements++;
            return "list".equals(method.getName()) ? Collections.emptyList() : 1;
          }
          throw new AssertionError("Unexpected query operation: " + method.getName());
        });
    Session session = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(),
        new Class<?>[] {Session.class}, (proxy, method, values) -> {
          if (!"createSQLQuery".equals(method.getName())) {
            throw new AssertionError("Adapter must not manage the caller's session: " + method.getName());
          }
          sql = (String) values[0];
          bindings.clear();
          return query;
        });
    int calls = 0;
    for (String kind : new String[] {"Code", "Credential", "State", "Operation"}) {
      Class<?> type = Class.forName("com.ofss.digx.cz.bea.domain.hosttohost.entity.repository.adapter.LocalHthApiPassword"
          + kind + "RepositoryAdapter");
      Object adapter = type.getMethod("getInstance").invoke(null);
      for (Method method : type.getDeclaredMethods()) {
        Class<?>[] types = method.getParameterTypes();
        if (types.length == 0 || types[0] != Session.class) {
          continue;
        }
        Object[] values = new Object[types.length];
        values[0] = session;
        for (int i = 1; i < values.length; i++) {
          values[i] = types[i] == Boolean.TYPE ? true : types[i] == Integer.TYPE ? 24 : "value";
        }
        method.invoke(adapter, values);
        calls++;
        if ("write".equals(method.getName())) {
          values[3] = "SETUP";
          method.invoke(adapter, values);
          if (!sql.startsWith("INSERT INTO HTH_BEA.HTH_API_PASSWORD_CREDENTIAL")) {
            throw new AssertionError("SETUP must insert a credential");
          }
          calls++;
        }
      }
    }
    if (calls != 20 || statements != calls) {
      throw new AssertionError("Expected 20 adapter calls/statements: " + calls + "/" + statements);
    }
    System.out.println("PASS: 20 adapter calls; complete parameter bindings; no session or transaction ownership changes");
  }
}

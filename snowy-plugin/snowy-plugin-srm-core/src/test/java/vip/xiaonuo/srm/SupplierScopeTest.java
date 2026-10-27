package vip.xiaonuo.srm;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import vip.xiaonuo.srm.modular.supplier.mapper.SrmSupplierMapper;
import vip.xiaonuo.srm.modular.supplier.service.impl.SrmSupplierServiceImpl;
import static org.junit.jupiter.api.Assertions.*;
class SupplierScopeTest {
    @Test void searchCannotEscapeOrganizationAndNeverLoadsSensitiveFields() throws Exception {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:suppliers;DB_CLOSE_DELAY=-1");
        try (var c = ds.getConnection(); var st = c.createStatement()) {
            st.execute("CREATE TABLE SRM_SUPPLIER(ID VARCHAR PRIMARY KEY, SUPPLIER_CODE VARCHAR, NAME VARCHAR, CATEGORY VARCHAR, TIER VARCHAR, STATUS VARCHAR, ORG_ID VARCHAR, ADDRESS VARCHAR, CREDIT_CODE VARCHAR, BANK_ACCOUNT VARCHAR, DELETE_FLAG VARCHAR)");
            st.execute("INSERT INTO SRM_SUPPLIER VALUES('1','SUP001','A supplier','RAW','C','POTENTIAL','org-a',null,'secret-credit','secret-bank','NOT_DELETE'),('2','SUP002','A supplier','RAW','C','POTENTIAL','org-b',null,'secret-credit','secret-bank','NOT_DELETE')");
        }
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        var db = new GlobalConfig.DbConfig(); db.setLogicNotDeleteValue("NOT_DELETE"); db.setLogicDeleteValue("DELETED");
        GlobalConfigUtils.setGlobalConfig(config, new GlobalConfig().setDbConfig(db));
        config.setEnvironment(new Environment("test",new JdbcTransactionFactory(),ds));
        config.addMapper(SrmSupplierMapper.class);
        try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession()) {
            var service = new SrmSupplierServiceImpl(session.getMapper(SrmSupplierMapper.class));
            var rows = service.page("org-a",1,20,"A supplier").getRecords();
            assertEquals(1, rows.size()); assertEquals("org-a", rows.get(0).getOrgId());
            assertNull(rows.get(0).getCreditCode()); assertNull(rows.get(0).getBankAccount());
            assertTrue(service.page("org-c",1,20,"").getRecords().isEmpty());
        }
    }
}

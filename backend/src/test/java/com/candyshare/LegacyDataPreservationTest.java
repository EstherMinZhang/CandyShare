package com.candyshare;

import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=update")
@ContextConfiguration(initializers = LegacyDataPreservationTest.ExistingDatabase.class)
class LegacyDataPreservationTest {
    @Autowired JdbcTemplate database;

    public static class ExistingDatabase implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            String url = context.getEnvironment().getRequiredProperty("spring.datasource.url");
            String user = context.getEnvironment().getRequiredProperty("spring.datasource.username");
            String password = context.getEnvironment().getProperty("spring.datasource.password", "");
            try (Connection connection = DriverManager.getConnection(url, user, password)) {
                connection.createStatement().execute("create table if not exists books (id bigint primary key, title varchar(160))");
                connection.createStatement().execute("insert into books (id, title) values (987654321, 'Legacy book retained')");
            } catch (Exception exception) {
                throw new IllegalStateException("Could not prepare the disposable legacy test database", exception);
            }
        }
    }

    @Test
    void schemaUpdatePreservesUnmappedBookTableAndItsData() {
        assertThat(database.queryForObject("select title from books where id = 987654321", String.class))
                .isEqualTo("Legacy book retained");
        database.update("delete from books where id = 987654321");
    }
}

package com.boversal.authenticate.infrastructure;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class MySqlDataSourceConfig {
    @Bean
    DataSource dataSource(Environment environment) {
        var connection = environment.getRequiredProperty("DATABASE_URL");
        var values = parse(connection);
        var dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:mysql://" + values.get("server") + ":" + values.get("port")
                + "/" + values.get("database") + "?useSSL=true&serverTimezone=UTC");
        dataSource.setUsername(values.get("user"));
        dataSource.setPassword(values.get("password"));
        dataSource.setMaximumPoolSize(Integer.parseInt(values.getOrDefault("maximumpoolsize", "10")));
        dataSource.setMinimumIdle(Integer.parseInt(values.getOrDefault("minimumpoolsize", "2")));
        return dataSource;
    }

    private Map<String, String> parse(String connection) {
        var values = new HashMap<String, String>();
        for (var segment : connection.split(";")) {
            var separator = segment.indexOf('=');
            if (separator > 0) {
                values.put(segment.substring(0, separator).trim().toLowerCase(Locale.ROOT),
                        segment.substring(separator + 1).trim());
            }
        }
        return values;
    }
}
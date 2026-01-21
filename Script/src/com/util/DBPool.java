package com.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public class DBPool {

    private static HikariDataSource dataSource;

    static {
    	Properties properties = new Properties();
		try {
			properties.load(DBUtil.class.getResourceAsStream("mysql.properties"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String dbconnectionstring = properties.getProperty("dbconnstring");
		String dbusername =  properties.getProperty("dbusername");
		String dbpassword =  properties.getProperty("dbpassword");
		
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbconnectionstring);
        config.setUsername(dbusername);
        config.setPassword(dbpassword);
        config.setMaximumPoolSize(20);   // max connections
        config.setMinimumIdle(5);        // idle connections
        config.setIdleTimeout(30000);    // 30s
        config.setMaxLifetime(600000);   // 10min
        config.setConnectionTimeout(10000); // 10s

        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}


package com.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DBUtil {

	public static Connection getConnection(){
		try {
			Properties properties = new Properties();
			properties.load(DBUtil.class.getResourceAsStream("mysql.properties"));
			String dbconnectionstring = properties.getProperty("dbconnstring");
			String dbusername =  properties.getProperty("dbusername");
			String dbpassword =  properties.getProperty("dbpassword");
			Connection con = DriverManager.getConnection(dbconnectionstring, dbusername,
					dbpassword);
			con.setAutoCommit(false);
			return con;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		return null;
	}
	
	public static void main(String[] args) {
		Connection con = getConnection();
		System.out.println("ok");
	}
}

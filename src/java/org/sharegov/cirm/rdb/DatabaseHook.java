package org.sharegov.cirm.rdb;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;

import javax.sql.DataSource;
import mjson.Json;

public interface DatabaseHook
{
	DataSource createDataSource(Json description);
	DataSource createPooledDataSource(Json description);
	long nextSequence(Connection conn, String sequenceName)  throws SQLException;
	String nextSequenceClause(String sequenceName);
	Date timeStamp(Connection conn)  throws SQLException;
	String paginate(String sql, long minValue, long maxValue);
	void resetSequence(Connection conn, String sequenceName) throws SQLException;
}
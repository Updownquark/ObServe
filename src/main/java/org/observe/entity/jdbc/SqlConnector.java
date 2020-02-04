package org.observe.entity.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

public interface SqlConnector {
	Connection getConnection() throws SQLException;
}

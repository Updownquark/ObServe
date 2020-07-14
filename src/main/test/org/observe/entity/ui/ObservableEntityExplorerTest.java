package org.observe.entity.ui;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.entity.EntityOperationException;
import org.observe.entity.ObservableEntityDataSet;
import org.observe.entity.jdbc.DefaultConnectionPool;
import org.observe.entity.jdbc.JdbcEntityProvider;
import org.observe.entity.jdbc.JdbcEntitySupport;
import org.observe.entity.jdbc.SqlConnector;
import org.observe.util.Identified;
import org.observe.util.swing.ObservableSwingUtils;
import org.qommons.Nameable;
import org.qommons.collect.StampedLockingStrategy;

public class ObservableEntityExplorerTest {
	public static void main(String... args) {
		try {
			Class.forName("org.h2.Driver");
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("Could not find H2 DB driver", e);
		}
		Connection connection;
		try {
			connection = DriverManager.getConnection("jdbc:h2:./obervableEntityTest", "h2", "h2");
		} catch (SQLException e) {
			throw new IllegalStateException("Could not connect to test DB", e);
		}
		ObservableEntityDataSet ds;
		try {
			Duration refreshDuration = Duration.ofMillis(100);
			ds = ObservableEntityDataSet
				.build(new JdbcEntityProvider(new StampedLockingStrategy(null), //
					JdbcEntitySupport.DEFAULT.withAutoIncrement("AUTO_INCREMENT").build(), //
					new DefaultConnectionPool("test", SqlConnector.of(connection)), "test", true))//
				.withEntityType(A.class).fillFieldsFromClass().build()//
				.withEntityType(B.class).fillFieldsFromClass().build()//
				.withRefresh(Observable.every(refreshDuration, refreshDuration, null, d -> d, null))
				.build(Observable.empty());
		} catch (EntityOperationException e) {
			throw new IllegalStateException("Could not set up entity persistence", e);
		}

		ObservableSwingUtils.buildUI()//
		.withConfig("entity-explorer-test").withConfigAt("observableEntityTest.config")//
		.withTitle("Observable Entity Explorer Test")//
		.systemLandF()//
		.build(config -> {
			ObservableEntityExplorer explorer = new ObservableEntityExplorer(config, ObservableValue.of(ds));
			return explorer;
		});
	}

	public interface A extends Identified, Nameable {
		public int getValue();
	}

	public interface B extends Identified, Nameable {
		public Duration getTime();
	}
}

package org.observe.entity.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import javax.swing.JFrame;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfig.XmlEncoding;
import org.observe.entity.ObservableEntityDataSet;
import org.observe.entity.jdbc.DefaultConnectionPool;
import org.observe.entity.jdbc.JdbcEntityProvider;
import org.observe.entity.jdbc.JdbcEntitySupport;
import org.observe.entity.jdbc.SqlConnector;
import org.observe.util.Identified;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.io.XmlSerialWriter;
import org.xml.sax.SAXException;

public class ObservableEntityExplorerTest {
	public static void main(String... args) {
		try {
			Class.forName("org.h2.Driver");
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("Could not find H2 DB driver", e);
		}
		Connection connection;
		try {
			connection = DriverManager.getConnection("jdbc:h2:~/obervableEntityTest", "h2", "h2");
		} catch (SQLException e) {
			throw new IllegalStateException("Could not connect to test DB", e);
		}
		ObservableEntityDataSet ds = ObservableEntityDataSet
			.build(new JdbcEntityProvider(new StampedLockingStrategy(null), JdbcEntitySupport.DEFAULT,
				new DefaultConnectionPool("test", SqlConnector.of(connection)), "test"))//
			.withEntityType(A.class).fillFieldsFromClass().build()//
			.withEntityType(B.class).fillFieldsFromClass().build()//
			.build(Observable.empty());

		ObservableConfig config = ObservableConfig.createRoot("entity-explorer-test");
		File configFile = new File("observableEntityTest.xml");
		if (!configFile.exists()) {
			try (FileWriter writer = new FileWriter(configFile)) {
				XmlSerialWriter.createDocument(writer)//
				.writeRoot(config.getName(), null);
			} catch (IOException e) {
				throw new IllegalStateException("Could not initialize test config", e);
			}
		}
		try (FileInputStream configStream = new FileInputStream(configFile)) {
			ObservableConfig.readXml(config, configStream, XmlEncoding.DEFAULT);
		} catch (IOException | SAXException e) {
			throw new IllegalStateException("Could not read or parse test config file " + configFile.getAbsolutePath(), e);
		}
		ObservableEntityExplorer explorer = new ObservableEntityExplorer(config, ObservableValue.of(ds));
		JFrame frame = new JFrame("Observable Entity Explorer Test");
		frame.getContentPane().add(explorer);
		frame.pack();
		frame.setVisible(true);
	}

	public interface A extends Identified {}

	public interface B extends Identified {}
}

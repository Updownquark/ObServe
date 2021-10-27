package org.observe.util.swing;

import java.awt.BorderLayout;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;

import javax.swing.JFrame;

import org.junit.Before;
import org.junit.Test;
import org.observe.util.ObservableModelSet;
import org.observe.util.ObservableModelQonfigParser;
import org.qommons.config.DefaultQonfigParser;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpreter;
import org.qommons.config.QonfigParseException;

public class QuickTests {
	private DefaultQonfigParser theParser;
	private QonfigInterpreter theInterpreter;

	@Before
	public void setup() {
		theParser = new DefaultQonfigParser()//
			.withToolkit(ObservableModelQonfigParser.TOOLKIT.get(), QuickSwingParser.CORE.get(), QuickSwingParser.BASE.get(),
				QuickSwingParser.SWING.get());
		QonfigInterpreter.Builder builder = QonfigInterpreter.build(QuickSwingParser.BASE.get(), QuickSwingParser.SWING.get());
		new QuickSwingParser().configureInterpreter(builder);
		theInterpreter = builder.build();
	}

	@Test
	public void testSuperBasic() throws IOException, QonfigParseException, ParseException {
		ObservableModelSet.ExternalModelSetBuilder extModels = ObservableModelSet.buildExternal();
		extModels.addSubModel("extModel").withValue("value1", double.class, v -> v.safe(false).withValue(42.0).build());
		testQuick("super-basic.qml", extModels.build());
	}

	private void testQuick(String fileName, ObservableModelSet.ExternalModelSet extModels)
		throws IOException, QonfigParseException, ParseException {
		URL location = QuickTests.class.getResource(fileName);
		QonfigElement element;
		try (InputStream in = location.openStream()) {
			element = theParser.parseDocument(location.toString(), in).getRoot();
		}
		QuickSwingParser.QuickDocument doc = theInterpreter.interpret(element, QuickSwingParser.QuickDocument.class);

		JFrame frame = new JFrame(fileName);
		frame.setSize(640, 480);
		frame.setLocationRelativeTo(null);
		frame.getContentPane().setLayout(new BorderLayout());
		doc.install(frame, extModels, null);
		frame.setVisible(true);

		while (frame.isVisible()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
	}
}

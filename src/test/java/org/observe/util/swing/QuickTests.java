package org.observe.util.swing;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.swing.JFrame;

import org.junit.Before;
import org.junit.Test;
import org.observe.SettableValue;
import org.observe.util.ModelTypes;
import org.observe.util.ObservableModelQonfigParser;
import org.observe.util.ObservableModelSet;
import org.qommons.config.DefaultQonfigParser;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpreter;
import org.qommons.config.QonfigInterpreter.QonfigInterpretationException;
import org.qommons.config.QonfigParseException;

public class QuickTests {
	private DefaultQonfigParser theParser;
	private QonfigInterpreter theInterpreter;

	@Before
	public void setup() {
		theParser = new DefaultQonfigParser()//
			.withToolkit(ObservableModelQonfigParser.TOOLKIT.get(), QuickSwingParser.CORE.get(), QuickSwingParser.BASE.get(),
				QuickSwingParser.SWING.get());
		QonfigInterpreter.Builder builder = QonfigInterpreter.build(QuickTests.class, QuickSwingParser.BASE.get(),
			QuickSwingParser.SWING.get());
		new QuickSwingParser().configureInterpreter(builder);
		theInterpreter = builder.build();
	}

	@Test
	public void testSuperBasic() throws IOException, QonfigParseException, QonfigInterpretationException {
		ObservableModelSet.ExternalModelSetBuilder extModels = ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER);
		extModels.addSubModel("extModel").with("value1", ModelTypes.Value.forType(double.class),
			SettableValue.build(double.class).withDescription("extModel.value1").withValue(42.0).build());
		testQuick("super-basic.qml", extModels.build());
	}

	private void testQuick(String fileName, ObservableModelSet.ExternalModelSet extModels)
		throws IOException, QonfigParseException, QonfigInterpretationException {
		URL location = QuickTests.class.getResource(fileName);
		QonfigElement element;
		try (InputStream in = location.openStream()) {
			element = theParser.parseDocument(location.toString(), in).getRoot();
		}
		QuickSwingParser.QuickDocument doc = theInterpreter.interpret(element, QuickSwingParser.QuickDocument.class);

		JFrame frame = doc.createUI(extModels).createFrame();
		frame.setSize(640, 480);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		while (frame.isVisible()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
	}
}

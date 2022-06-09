package org.observe.util.swing;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.swing.JFrame;

import org.junit.Before;
import org.junit.Test;
import org.observe.SettableValue;
import org.observe.expresso.Expresso;
import org.observe.expresso.ExpressoInterpreter;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.quick.QuickBase;
import org.observe.quick.QuickCore;
import org.observe.quick.QuickDocument;
import org.observe.quick.QuickInterpreter;
import org.observe.quick.QuickSwing;
import org.qommons.config.DefaultQonfigParser;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigParseException;

public class QuickTests {
	private DefaultQonfigParser theParser;
	private ExpressoInterpreter<?> theInterpreter;

	@Before
	public void setup() {
		theParser = new DefaultQonfigParser()//
			.withToolkit(Expresso.EXPRESSO.get(), QuickCore.CORE.get(), QuickBase.BASE.get(), QuickSwing.SWING.get());
		theInterpreter = QuickInterpreter.build(QuickTests.class, QuickBase.BASE.get(), QuickSwing.SWING.get())//
			.configure(new QuickSwing<>()).build();
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
		QuickDocument doc = theInterpreter.interpret(element).interpret(QuickDocument.class);

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

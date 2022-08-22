package org.observe.util.swing;

import java.io.IOException;
import java.net.URL;

import javax.swing.JFrame;

import org.junit.Test;
import org.observe.SettableValue;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.quick.QuickDocument;
import org.qommons.config.QonfigApp;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigParseException;

public class QuickTests {
	@Test
	public void testSuperBasic() throws IOException, QonfigParseException, QonfigInterpretationException {
		ObservableModelSet.ExternalModelSetBuilder extModels = ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER);
		extModels.addSubModel("extModel").with("value1", ModelTypes.Value.forType(double.class),
			SettableValue.build(double.class).withDescription("extModel.value1").withValue(42.0).build());
		testQuick("super-basic-app.qml", extModels.build());
	}

	private void testQuick(String fileName, ObservableModelSet.ExternalModelSet extModels)
		throws IOException, QonfigParseException, QonfigInterpretationException {
		URL location = QuickTests.class.getResource(fileName);
		QuickDocument doc = QonfigApp.interpretApp(location, QuickDocument.class);

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

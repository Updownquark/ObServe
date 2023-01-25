package org.observe.quick;

import java.io.IOException;
import java.net.URL;

import javax.swing.JFrame;

import org.junit.Test;
import org.observe.SettableValue;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.qommons.config.QonfigApp;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigParseException;

/** Tests for Quick */
public class QuickTests {
	/**
	 * Just pops up a little UI to test some of the basic functionality of Quick. It's not a good unit test because the user has to interact
	 * with it for the test, but it is a test.
	 *
	 * @throws IOException If any of the files can't be found or read
	 * @throws ModelException If the models can't be created
	 * @throws QonfigParseException If any of the files can't be parsed
	 * @throws QonfigInterpretationException If the Quick document can't be created
	 */
	@Test
	public void testSuperBasic() throws IOException, ModelException, QonfigParseException, QonfigInterpretationException {
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

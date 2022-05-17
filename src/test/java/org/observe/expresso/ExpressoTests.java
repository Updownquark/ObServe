package org.observe.expresso;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.quick.QuickBase;
import org.observe.quick.QuickCore;
import org.observe.quick.QuickDocument;
import org.observe.quick.QuickUiDef;
import org.observe.util.swing.QuickTests;
import org.qommons.config.DefaultQonfigParser;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigParseException;

public class ExpressoTests {
	private DefaultQonfigParser theParser;
	private ExpressoInterpreter<?> theInterpreter;

	@Before
	public void setup() {
		theParser = new DefaultQonfigParser()//
			.withToolkit(Expresso.EXPRESSO.get(), QuickCore.CORE.get(), QuickBase.BASE.get());
		theInterpreter = new QuickBase().configureInterpreter(ExpressoInterpreter.build(QuickTests.class, QuickBase.BASE.get())).build();
	}

	@Test
	public void testExpresso() throws IOException, QonfigParseException, QonfigInterpretationException {
		ObservableModelSet.ExternalModelSetBuilder extModels = ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER);
		QuickUiDef doc = testExpresso("expresso-tests.qml", extModels.build());

		SettableValue<ExpressoTestEntity> testEntity = doc.getModels().get("model1.test",
			ModelTypes.Value.forType(ExpressoTestEntity.class));
		SettableValue<ExpressoTestEntity> expectEntity = doc.getModels().get("model1.expected",
			ModelTypes.Value.forType(ExpressoTestEntity.class));
		SettableValue<Integer> anyInt = doc.getModels().get("model1.anyInt", ModelTypes.Value.forType(int.class));
		ObservableAction<?> assignInt = doc.getModels().get("model1.assignInt", ModelTypes.Action.any());
		SettableValue<String> error = doc.getModels().get("model1.error", ModelTypes.Value.forType(String.class));
		expectEntity.get().setInt(anyInt.get());
		expectEntity.set(expectEntity.get(), null); // Update, so the error field knows to update
		Assert.assertNotNull(error.get());
		assignInt.act(null);
		Assert.assertEquals(expectEntity.get().getInt(), testEntity.get().getInt());
		Assert.assertNull(error.get());
	}

	private QuickUiDef testExpresso(String fileName, ObservableModelSet.ExternalModelSet extModels)
		throws IOException, QonfigParseException, QonfigInterpretationException {
		URL location = ExpressoTests.class.getResource(fileName);
		QonfigElement element;
		try (InputStream in = location.openStream()) {
			element = theParser.parseDocument(location.toString(), in).getRoot();
		}
		QuickDocument doc = theInterpreter.interpret(element).interpret(QuickDocument.class);
		return doc.createUI(extModels);
	}
}

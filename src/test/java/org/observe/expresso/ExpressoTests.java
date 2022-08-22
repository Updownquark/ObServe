package org.observe.expresso;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;
import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.quick.QuickDocument;
import org.observe.quick.QuickUiDef;
import org.qommons.config.QonfigApp;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigParseException;

public class ExpressoTests {
	@Test
	public void testExpresso() throws IOException, QonfigParseException, QonfigInterpretationException {
		ObservableModelSet.ExternalModelSetBuilder extModels = ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER);
		QuickDocument doc = QonfigApp.interpretApp(ExpressoTests.class.getResource("expresso-tests-app.qml"), QuickDocument.class);

		QuickUiDef ui = doc.createUI(extModels);
		SettableValue<ExpressoTestEntity> testEntity = ui.getModels().get("model1.test",
			ModelTypes.Value.forType(ExpressoTestEntity.class));
		SettableValue<ExpressoTestEntity> expectEntity = ui.getModels().get("model1.expected",
			ModelTypes.Value.forType(ExpressoTestEntity.class));
		SettableValue<Integer> anyInt = ui.getModels().get("model1.anyInt", ModelTypes.Value.forType(int.class));
		ObservableAction<?> assignInt = ui.getModels().get("model1.assignInt", ModelTypes.Action.any());
		SettableValue<String> error = ui.getModels().get("model1.error", ModelTypes.Value.forType(String.class));
		expectEntity.get().setInt(anyInt.get());
		expectEntity.set(expectEntity.get(), null); // Update, so the error field knows to update
		Assert.assertNotNull(error.get());
		assignInt.act(null);
		Assert.assertEquals(expectEntity.get().getInt(), testEntity.get().getInt());
		Assert.assertNull(error.get());
	}
}

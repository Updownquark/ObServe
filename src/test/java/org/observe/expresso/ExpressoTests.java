package org.observe.expresso;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;
import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigApp;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigParseException;

/** Some tests for Expresso functionality */
public class ExpressoTests {
	/**
	 * Simple model tests
	 *
	 * @throws IOException If any of the files can't be found or read
	 * @throws QonfigParseException If any of the files can't be parsed
	 * @throws QonfigInterpretationException If the document can't be created
	 */
	@Test
	public void testExpresso() throws IOException, QonfigParseException, QonfigInterpretationException {
		ObservableModelSet.ExternalModelSetBuilder extModels = ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER);
		Expresso expresso = QonfigApp.interpretApp(ExpressoTests.class.getResource("expresso-tests-app.qml"), Expresso.class);

		ModelSetInstance msi = expresso.getModels().createInstance(extModels, Observable.empty());

		SettableValue<ExpressoTestEntity> testEntity = msi.get("model1.test", ModelTypes.Value.forType(ExpressoTestEntity.class));
		SettableValue<ExpressoTestEntity> expectEntity = msi.get("model1.expected", ModelTypes.Value.forType(ExpressoTestEntity.class));
		SettableValue<Integer> anyInt = msi.get("model1.anyInt", ModelTypes.Value.forType(int.class));
		ObservableAction<?> assignInt = msi.get("model1.assignInt", ModelTypes.Action.any());
		SettableValue<String> error = msi.get("model1.error", ModelTypes.Value.forType(String.class));
		expectEntity.get().setInt(anyInt.get());
		expectEntity.set(expectEntity.get(), null); // Update, so the error field knows to update
		Assert.assertNotNull(error.get());
		assignInt.act(null);
		Assert.assertEquals(expectEntity.get().getInt(), testEntity.get().getInt());
		Assert.assertNull(error.get());
	}
}

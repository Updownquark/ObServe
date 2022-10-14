package org.observe.expresso;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
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
	private static Expresso TEST_EXPRESSO;

	static synchronized Expresso getTestExpresso() throws IOException, QonfigParseException, QonfigInterpretationException {
		if (TEST_EXPRESSO == null)
			TEST_EXPRESSO = QonfigApp.interpretApp(ExpressoTests.class.getResource("expresso-tests-app.qml"), Expresso.class);
		return TEST_EXPRESSO;
	}

	private SettableValue<String> theActionName;
	private ModelSetInstance theModelInstance;
	private SettableValue<ExpressoTestEntity> testEntity;
	private SettableValue<ExpressoTestEntity> expectEntity;
	private SettableValue<Integer> anyInt;
	private ObservableAction<?> assignInt;
	private SettableValue<String> error;

	private Map<String, Map<String, ObservableAction<?>>> theTestActions;

	/**
	 * Parses the model document (first test only) and instantiates the models, then pulls values from the models into this test instance's
	 * fields
	 *
	 * @throws IOException If any of the files can't be found or read
	 * @throws QonfigParseException If any of the files can't be parsed
	 * @throws QonfigInterpretationException If the document can't be created
	 */
	@Before
	public void instantiateModels() throws IOException, QonfigParseException, QonfigInterpretationException {
		Expresso expresso = getTestExpresso();
		theActionName = SettableValue.build(String.class).build();
		ObservableModelSet.ExternalModelSet extModels = ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER)//
			.withSubModel("ext", ext -> ext.with("actionName", ModelTypes.Value.forType(String.class), theActionName))//
			.build();
		theModelInstance = expresso.getModels().createInstance(extModels, Observable.empty());

		testEntity = theModelInstance.get("models.test", ModelTypes.Value.forType(ExpressoTestEntity.class));
		expectEntity = theModelInstance.get("models.expected", ModelTypes.Value.forType(ExpressoTestEntity.class));
		anyInt = theModelInstance.get("models.anyInt", ModelTypes.Value.forType(int.class));
		assignInt = theModelInstance.get("models.assignInt", ModelTypes.Action.any());
		error = theModelInstance.get("models.error", ModelTypes.Value.forType(String.class));

		theTestActions = new HashMap<>();
		ObservableModelSet tests = expresso.getModels().getSubModel("tests");
		for (String modelName : tests.getContentNames()) {
			Object thing = tests.getThing(modelName);
			if (!(thing instanceof ObservableModelSet))
				continue;
			ObservableModelSet subModel = (ObservableModelSet) thing;
			Map<String, ObservableAction<?>> actions = new LinkedHashMap<>();
			theTestActions.put(modelName, actions);
			for (String actionName : subModel.getContentNames()) {
				try {
					actions.put(actionName, theModelInstance.get(//
						"tests." + modelName + "." + actionName, ModelTypes.Action.any()));
				} catch (IllegalArgumentException e) {
					// Not an action, just don't add it
				}
			}
		}
	}

	/** Tests int assignment */
	@Test
	public void testAssignInt() {
		executeTestActions("assignInt");
	}

	/** Tests instant assignment */
	@Test
	public void testAssignInst() {
		executeTestActions("assignInst");
	}

	private void executeTestActions(String testName) {
		Map<String, ObservableAction<?>> actions = theTestActions.get(testName);
		if (actions == null)
			throw new IllegalStateException("No such test in markup: " + testName);
		for (Map.Entry<String, ObservableAction<?>> action : actions.entrySet()) {
			theActionName.set(action.getKey(), null);
			action.getValue().act(null);
		}
		Assert.assertNull(error.get());
		Assert.assertEquals(expectEntity.get().getInt(), testEntity.get().getInt());
	}
}

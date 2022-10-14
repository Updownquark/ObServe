package org.observe.expresso;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

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

	static synchronized Expresso getTestExpresso() {
		if (TEST_EXPRESSO == null) {
			try {
				TEST_EXPRESSO = QonfigApp.interpretApp(ExpressoTests.class.getResource("expresso-tests-app.qml"), Expresso.class);
			} catch (RuntimeException e) {
				e.printStackTrace();
				throw e;
			}
		}
		return TEST_EXPRESSO;
	}

	private SettableValue<String> theActionName;
	private ModelSetInstance theModelInstance;

	private Map<String, Map<String, ObservableAction<?>>> theTestActions;

	/**
	 * Parses the model document (first test only) and instantiates the models, then pulls values from the models into this test instance's
	 * fields and compiles the actions for each test
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

	/** Tests the constant model type */
	@Test
	public void testConstant() {
		executeTestActions("constant");
	}

	/** Tests the value model type, initialized with or set to a simple value */
	@Test
	public void testSimpleValue() {
		executeTestActions("simpleValue");
	}

	/** Tests the value model type, slaved to an expression */
	@Test
	public void testDerivedValue() {
		executeTestActions("derivedValue");
	}

	/** Tests the list model type */
	@Test
	public void testList() {
		executeTestActions("list");
	}

	/** Tests int assignment */
	@Test
	public void testAssignInt() {
		executeTestActions("assignInt");
	}

	/** Tests instant assignment */
	@Test
	public void testAssignInstant() {
		executeTestActions("assignInstant");
	}

	private void executeTestActions(String testName) {
		Map<String, ObservableAction<?>> actions = theTestActions.get(testName);
		if (actions == null)
			throw new IllegalStateException("No such test in markup: " + testName);
		else if (actions.isEmpty())
			throw new IllegalStateException("No actions for test: " + testName);
		for (Map.Entry<String, ObservableAction<?>> action : actions.entrySet()) {
			String actionName = action.getKey();
			int x = actionName.indexOf('X');
			String exName;
			if (x >= 0) {
				exName = actionName.substring(x + 1);
				actionName = actionName.substring(0, x);
			} else
				exName = null;
			theActionName.set(actionName, null);
			try {
				action.getValue().act(null);
				if (exName != null)
					throw new AssertionError("Expected action " + actionName + " to throw " + exName);
			} catch (RuntimeException e) {
				if (exName == null || !exName.equals(e.getClass().getSimpleName()))
					throw e;
			}
		}
	}
}

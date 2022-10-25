package org.observe.expresso;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigApp;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigParseException;

/** Some tests for Expresso functionality */
public class ExpressoTests {
	private static Expresso TEST_EXPRESSO;
	private static Comparator<? super ExpressoTestEntity> ENTITY_COMPARE;

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
		theModelInstance = expresso.getModels().createInstance(extModels, Observable.empty()).build();

		ENTITY_COMPARE = expresso.getModels()
			.getValue("models.sortedEntityList", ModelTypes.SortedCollection.forType(ExpressoTestEntity.class))
			.get(theModelInstance).comparator();

		theTestActions = new HashMap<>();
		ObservableModelSet tests = expresso.getModels().getSubModel("tests");
		for (ModelComponentNode<?, ?> component : tests.getComponents().values()) {
			if (component.getModel() == null)
				continue;
			ObservableModelSet subModel = component.getModel();
			Map<String, ObservableAction<?>> actions = new LinkedHashMap<>();
			theTestActions.put(subModel.getIdentity().getName(), actions);
			for (ModelComponentNode<?, ?> action : subModel.getComponents().values()) {
				if (action.getType().getModelType() == ModelTypes.Action) {
					try {
						actions.put(action.getIdentity().getName(), TEST_EXPRESSO.getModels().getValue(//
							"tests." + subModel.getIdentity().getName() + "." + action.getIdentity().getName(), ModelTypes.Action.any())
							.get(theModelInstance));
					} catch (IllegalArgumentException e) {
						// Not an action, just don't add it
					}
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

	/** Tests the mapping transformation type */
	@Test
	public void testMapTo() {
		executeTestActions("mapTo");
	}

	/** Tests the sort transformation type */
	@Test
	public void testSort() {
		executeTestActions("sort");
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

	/** Tests value-derived model values (see {@link DynamicModelValues}) */
	@Test
	public void testInternalState() {
		executeTestActions("internalState");
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
					throw new IllegalStateException("Unexpected exception on action " + actionName, e);
			}
		}
	}

	/**
	 * Called by the sort test from Expresso. Ensures that all the entities are {@link #ENTITY_COMPARE order}.
	 *
	 * @param entities The entities to check
	 * @throws AssertionError If the entities are not in order
	 */
	public static void checkEntityListOrder(List<ExpressoTestEntity> entities) throws AssertionError {
		ExpressoTestEntity prev = null;
		for (ExpressoTestEntity entity : entities) {
			if (prev != null && ENTITY_COMPARE.compare(prev, entity) > 0)
				throw new AssertionError(prev + ">" + entity);
			prev = entity;
		}
	}
}

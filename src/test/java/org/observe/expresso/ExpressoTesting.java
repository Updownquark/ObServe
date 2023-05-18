package org.observe.expresso;

import java.util.List;
import java.util.Map;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.qommons.BreakpointHere;
import org.qommons.Named;
import org.qommons.io.LocatedFilePosition;

/**
 * A testing structure parsed from a Qonfig XML file for testing expresso or expresso-dependent toolkits
 *
 * @param <H> The sub-type of the head
 */
public class ExpressoTesting<H extends Expresso> {
	/** A test to execute */
	public static class ExpressoTest implements Named {
		private final String theName;
		private final ObservableModelSet.Built theModel;
		private InterpretedModelSet theInterpretedModel;
		private final ExpressoQIS theSession;
		private final List<TestAction> theActions;

		/**
		 * @param name The name of the test
		 * @param model The test's models
		 * @param session The test's expresso session
		 * @param actions The test actions
		 */
		public ExpressoTest(String name, ObservableModelSet.Built model, ExpressoQIS session, List<TestAction> actions) {
			theName = name;
			theModel = model;
			theSession = session;
			theActions = actions;
		}

		@Override
		public String getName() {
			return theName;
		}

		/** @return This test's models */
		public ObservableModelSet.Built getModel() {
			return theModel;
		}

		/**
		 * @param testingModel The global testing model instance
		 * @return The model instance to use for this test
		 * @throws ExpressoInterpretationException If the test could not be evaluated
		 * @throws ModelInstantiationException If the test could not be instantiated
		 */
		public ModelSetInstance createModelInstance(ModelSetInstance testingModel)
			throws ExpressoInterpretationException, ModelInstantiationException {
			if (theInterpretedModel == null)
				theInterpretedModel = theModel.interpret();
			return theSession.getExpressoEnv()
				.wrapLocal(theInterpretedModel.createInstance(testingModel.getUntil()).withAll(testingModel).build());
		}

		/** @return All the actions to execute for this test */
		public List<TestAction> getActions() {
			return theActions;
		}
	}

	/** An action to execute for a test */
	public static class TestAction {
		private final String theName;
		private final ObservableModelSet.Built theActionModel;
		private InterpretedModelSet theInterpretedModel;
		private final ExpressoQIS theExpressoSession;
		private final CompiledModelValue<ObservableAction<?>, ObservableAction<?>> theAction;
		private final String theExpectedException;
		private final boolean isBreakpoint;
		private final LocatedFilePosition thePosition;

		/**
		 * @param name The name of the test action
		 * @param actionModel The action's models
		 * @param expressoSession The action's expresso session
		 * @param action The value container to produce the action
		 * @param expectedException The exception type expected to be thrown
		 * @param breakpoint Whether a {@link BreakpointHere breakpoint} should be caught before executing this action
		 * @param position The position in the file where this test action was defined
		 */
		public TestAction(String name, ObservableModelSet.Built actionModel, ExpressoQIS expressoSession,
			CompiledModelValue<ObservableAction<?>, ObservableAction<?>> action, String expectedException, boolean breakpoint,
			LocatedFilePosition position) {
			theName = name;
			theActionModel = actionModel;
			theExpressoSession = expressoSession;
			theAction = action;
			theExpectedException = expectedException;
			isBreakpoint = breakpoint;
			thePosition = position;
		}

		/** @return This action's name */
		public String getName() {
			return theName;
		}

		/**
		 * @param testModel The test's model instance
		 * @param until An observable that will clean up this action's model instance
		 * @return The model instance to use for this action
		 * @throws ModelInstantiationException If the action could not be instantiated
		 */
		public ModelSetInstance getActionModel(ModelSetInstance testModel, Observable<?> until) throws ModelInstantiationException {
			return theExpressoSession.getExpressoEnv().wrapLocal(//
				theInterpretedModel.createInstance(until).withAll(testModel)//
				.build());
		}

		/**
		 * @return The value container to produce this action
		 * @throws ExpressoInterpretationException If the action could not be evaluated
		 */
		public ModelValueSynth<ObservableAction<?>, ObservableAction<?>> getAction() throws ExpressoInterpretationException {
			if (theInterpretedModel == null)
				theInterpretedModel = theActionModel.interpret();
			return theAction.createSynthesizer();
		}

		/** @return The name of the exception type that is expected to be thrown by this test action */
		public String getExpectedException() {
			return theExpectedException;
		}

		/** @return Whether a {@link BreakpointHere breakpoint} should be caught before executing this action */
		public boolean isBreakpoint() {
			return isBreakpoint;
		}

		/** @return The position in the file where this test action was defined */
		public LocatedFilePosition getPosition() {
			return thePosition;
		}
	}

	private final H theHead;
	private final Map<String, ExpressoTest> theTests;

	/**
	 * @param head The head containing models, etc. for this testing
	 * @param tests All the tests to execute
	 */
	public ExpressoTesting(H head, Map<String, ExpressoTest> tests) {
		theHead = head;
		theTests = tests;
	}

	/** @return The head containing models, etc. for this testing */
	public H getHead() {
		return theHead;
	}

	/** @return All the tests to execute, by name */
	public Map<String, ExpressoTest> getTests() {
		return theTests;
	}

	/**
	 * @param testName The name of the test to execute
	 * @throws ExpressoInterpretationException If the test structures could not be evaluated
	 * @throws ModelInstantiationException If the test structures could not be instantiated
	 * @throws IllegalArgumentException If no such test was found in this test's XML definition
	 */
	public void executeTest(String testName) throws ExpressoInterpretationException, ModelInstantiationException {
		ExpressoTest test = theTests.get(testName);
		if (test == null)
			throw new IllegalArgumentException("No such test in markup: " + testName);

		System.out.print("Preparing test " + testName + "...");
		System.out.flush();
		SettableValue<String> actionName = SettableValue.build(String.class).build();
		ObservableModelSet.ExternalModelSet extModels;
		try {
			extModels = ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER)//
				.withSubModel("ext", ext -> ext.with("actionName", ModelTypes.Value.forType(String.class), actionName))//
				.build();
		} catch (ModelException e) {
			throw new IllegalStateException("Could not assemble external models", e);
		}
		SimpleObservable<Void> until = new SimpleObservable<>();
		InterpretedModelSet interpretedModels = theHead.getModels().interpret();
		try {
			System.out.print("global models...");
			System.out.flush();
			ModelSetInstance globalModels = interpretedModels.createInstance(extModels, until).build();

			System.out.print("test models...");
			System.out.flush();
			ModelSetInstance testModels = test.createModelInstance(globalModels);
			System.out.println("ready");

			for (TestAction action : test.getActions()) {
				actionName.set(action.getName(), null);
				ModelValueSynth<ObservableAction<?>, ObservableAction<?>> actionContainer = action.getAction();
				ModelSetInstance actionModels = action.getActionModel(testModels, actionName.noInitChanges());
				System.out.print(action.getName());
				System.out.flush();
				ObservableAction<?> testAction = actionContainer.get(actionModels);
				System.out.println(":");
				if (action.isBreakpoint())
					BreakpointHere.breakpoint();
				try {
					testAction.act(null);
					if (action.getExpectedException() != null)
						throw new AssertionError("Expected action " + actionName + " to throw " + action.getExpectedException());
				} catch (RuntimeException e) {
					if (action.getExpectedException() == null || !action.getExpectedException().equals(e.getClass().getSimpleName()))
						throw new IllegalStateException(action.getPosition() + ": Unexpected exception on test action " + actionName, e);
				} catch (AssertionError e) {
					throw new AssertionError(action.getPosition() + ":\n\t" + e.getMessage(), e);
				}
			}
		} finally {
			until.onNext(null);
		}
	}
}

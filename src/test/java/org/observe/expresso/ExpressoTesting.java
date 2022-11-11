package org.observe.expresso;

import java.util.List;
import java.util.Map;

import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.qommons.BreakpointHere;
import org.qommons.Named;
import org.qommons.config.QonfigInterpretationException;

public class ExpressoTesting<H extends Expresso> {
	public static class ExpressoTest implements Named {
		private final String theName;
		private final ObservableModelSet theModel;
		private final List<TestAction> theActions;

		public ExpressoTest(String name, ObservableModelSet model, List<TestAction> actions) {
			theName = name;
			theModel = model;
			theActions = actions;
		}

		@Override
		public String getName() {
			return theName;
		}

		public ObservableModelSet getModel() {
			return theModel;
		}

		public List<TestAction> getActions() {
			return theActions;
		}
	}

	public static class TestAction {
		private final String theName;
		private final ValueContainer<ObservableAction<?>, ObservableAction<?>> theAction;
		private final String theExpectedException;
		private final boolean isBreakpoint;

		public TestAction(String name, ValueContainer<ObservableAction<?>, ObservableAction<?>> action, String expectedException,
			boolean breakpoint) {
			theName = name;
			theAction = action;
			theExpectedException = expectedException;
			isBreakpoint = breakpoint;
		}

		public String getName() {
			return theName;
		}

		public ValueContainer<ObservableAction<?>, ObservableAction<?>> getAction() {
			return theAction;
		}

		public String getExpectedException() {
			return theExpectedException;
		}

		public boolean isBreakpoint() {
			return isBreakpoint;
		}
	}

	private final H theHead;
	private final Map<String, ExpressoTest> theTests;

	public ExpressoTesting(H head, Map<String, ExpressoTest> tests) {
		theHead = head;
		theTests = tests;
	}

	public H getHead() {
		return theHead;
	}

	public Map<String, ExpressoTest> getTests() {
		return theTests;
	}

	public void executeTest(String testName) {
		ExpressoTest test = theTests.get(testName);
		if (test == null)
			throw new IllegalStateException("No such test in markup: " + testName);

		System.out.print("Preparing test " + testName + "...");
		System.out.flush();
		SettableValue<String> actionName = SettableValue.build(String.class).build();
		ObservableModelSet.ExternalModelSet extModels;
		try {
			extModels = ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER)//
				.withSubModel("ext", ext -> ext.with("actionName", ModelTypes.Value.forType(String.class), actionName))//
				.build();
		} catch (QonfigInterpretationException e) {
			throw new IllegalStateException("Could not assemble external models", e);
		}
		SimpleObservable<Void> until = new SimpleObservable<>();
		try {
			System.out.print("global models...");
			System.out.flush();
			ModelSetInstance globalModels = theHead.getModels().createInstance(extModels, until).build();

			System.out.print("test models...");
			System.out.flush();
			ModelSetInstance modelInstance = test.getModel().createInstance(until).withAll(globalModels).build();
			System.out.println("ready");

			for (TestAction action : test.getActions()) {
				actionName.set(action.getName(), null);
				System.out.print(action.getName());
				System.out.flush();
				ObservableAction<?> testAction = action.getAction().get(modelInstance);
				System.out.println(":");
				if (action.isBreakpoint())
					BreakpointHere.breakpoint();
				try {
					testAction.act(null);
					if (action.getExpectedException() != null)
						throw new AssertionError("Expected action " + actionName + " to throw " + action.getExpectedException());
				} catch (RuntimeException e) {
					if (action.getExpectedException() == null || !action.getExpectedException().equals(e.getClass().getSimpleName()))
						throw new IllegalStateException("Unexpected exception on test action " + actionName, e);
				}
			}
		} finally {
			until.onNext(null);
		}
	}
}

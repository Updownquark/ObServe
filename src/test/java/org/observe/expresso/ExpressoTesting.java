package org.observe.expresso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.observe.ObservableAction;
import org.observe.SimpleObservable;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExNamed;
import org.observe.expresso.qonfig.Expresso;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ExpressoQonfigValues;
import org.qommons.BreakpointHere;
import org.qommons.Named;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedFilePosition;

/**
 * A testing structure parsed from a Qonfig XML file for testing expresso or expresso-dependent toolkits
 */
public class ExpressoTesting extends ExElement.Def.Abstract<ExElement> {
	/** A test to execute */
	public static class ExpressoTest extends ExElement.Abstract {
		public static class Def extends ExElement.Def.Abstract<ExpressoTest> implements Named {
			private final List<TestAction> theActions;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
				theActions = new ArrayList<>();
			}

			@Override
			public String getName() {
				return getAddOn(ExNamed.Def.class).getName();
			}

			/** @return All the actions to execute for this test */
			public List<TestAction> getActions() {
				return Collections.unmodifiableList(theActions);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				ExElement.syncDefs(TestAction.class, theActions, //
					session.forChildren("test-action").stream().map(s -> s.asElement("test-action")).collect(Collectors.toList()));
			}

			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends ExElement.Interpreted.Abstract<ExpressoTest> implements Named {
			private final List<TestAction.Interpreted<?>> theActions;

			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theActions = new ArrayList<>();
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public String getName() {
				return getDefinition().getName();
			}

			public List<TestAction.Interpreted<?>> getActions() {
				return Collections.unmodifiableList(theActions);
			}

			public void updateTest(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				CollectionUtils.synchronize(theActions, getDefinition().getActions(), (i, d) -> i.getIdentity() == d.getIdentity())//
				.<ExpressoInterpretationException> simpleE(d -> d.interpret().setParentElement(this))//
				.onLeftX(el -> el.getLeftValue().destroy())//
				.onRightX(el -> el.getLeftValue().updateValue(getExpressoEnv()))//
				.onCommonX(el -> el.getLeftValue().updateValue(getExpressoEnv()))//
				.adjust();
			}

			public ExpressoTest create(ExElement parent) {
				return new ExpressoTest(this, parent);
			}
		}

		private final List<TestAction.TestActionElement<?>> theActions;

		public ExpressoTest(Interpreted interpreted, ExElement parent) {
			super(interpreted, parent);
			theActions = new ArrayList<>();
		}

		public void execute() {
			for (TestAction.TestActionElement<?> action : theActions) {
				System.out.print(action.reporting().getPosition().printPosition());
				System.out.flush();
				System.out.println(":");
				ObservableAction<?> obsAction = action.getAction();
				if (action.isBreakpoint())
					BreakpointHere.breakpoint();
				try {
					obsAction.act(null);
					if (action.getExpectedException() != null)
						throw new AssertionError(action.reporting().getPosition() + ":\n\tExpected exception "
							+ action.getExpectedException());
				} catch (RuntimeException e) {
					if (action.getExpectedException() == null || !action.getExpectedException().isInstance(e))
						throw new AssertionError(action.reporting().getPosition() + ":\n\tUnexpected exception", e);
				} catch (AssertionError e) {
					throw new AssertionError(action.reporting().getPosition() + ":\n\t" + e.getMessage(), e);
				}
			}
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			Interpreted myInterpreted = (Interpreted) interpreted;
			CollectionUtils.synchronize(theActions, myInterpreted.getActions(), (a, i) -> a.getIdentity() == i.getIdentity())//
			.<ModelInstantiationException> simpleE(i -> i.create(this))//
			.onRightX(el -> el.getLeftValue().update(el.getRightValue(), myModels))//
			.onCommonX(el -> el.getLeftValue().update(el.getRightValue(), myModels))//
			.adjust();
		}
	}

	/** An action to execute for a test */
	public static class TestAction extends ExpressoQonfigValues.Action {
		private String theExpectedException;
		private boolean isBreakpoint;

		public TestAction(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
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
			return reporting().getFileLocation().getPosition(0);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theExpectedException = session.getAttributeText("expect-throw");
			isBreakpoint = session.getAttribute("breakpoint", boolean.class);
		}

		@Override
		public Interpreted<?> interpret() {
			return new Interpreted<>(this);
		}

		public static class Interpreted<T> extends ExpressoQonfigValues.Action.Interpreted<T> {
			private Class<? extends Throwable> theExpectedException;

			Interpreted(TestAction def) {
				super(def);
			}

			@Override
			public Interpreted<T> setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			@Override
			public TestAction getDefinition() {
				return (TestAction) super.getDefinition();
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				if (getDefinition().getExpectedException() != null) {
					Class<?> exType;
					exType = getExpressoEnv().getClassView().getType(getDefinition().getExpectedException());
					if (exType == null)
						throw new ExpressoInterpretationException("No such exception type found: '" + theExpectedException + "'",
							getPosition(), 0);
					if (!Throwable.class.isAssignableFrom(exType))
						throw new ExpressoInterpretationException(
							"Expected exception type is not an exception: '" + theExpectedException + "'", getPosition(), 0);
					theExpectedException = (Class<? extends Throwable>) exType;
				} else
					theExpectedException = null;
			}

			public Class<? extends Throwable> getExpectedException() {
				return theExpectedException;
			}

			public boolean isBreakpoint() {
				return getDefinition().isBreakpoint();
			}

			public LocatedFilePosition getPosition() {
				return getDefinition().getPosition();
			}

			public TestActionElement<T> create(ExElement parent) {
				return new TestActionElement<>(this, parent);
			}
		}

		static class TestActionElement<T> extends ExElement.Abstract {
			private ObservableAction<T> theAction;
			private Class<? extends Throwable> theExpectedException;
			private boolean isBreakpoint;

			public TestActionElement(TestAction.Interpreted<T> interpreted, ExElement parent) {
				super(interpreted, parent);
			}

			public ObservableAction<T> getAction() {
				return theAction;
			}

			public Class<? extends Throwable> getExpectedException() {
				return theExpectedException;
			}

			public boolean isBreakpoint() {
				return isBreakpoint;
			}

			@Override
			protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
				super.updateModel(interpreted, myModels);
				TestAction.Interpreted<T> myInterpreted = (TestAction.Interpreted<T>) interpreted;
				theAction = myInterpreted.get(myModels);
				theExpectedException = myInterpreted.getExpectedException();
				isBreakpoint = myInterpreted.isBreakpoint();
			}
		}
	}

	private Expresso.Def theHead;
	private final List<ExpressoTest.Def> theTests;
	private final Map<String, ExpressoTest.Def> theTestsByName;

	public ExpressoTesting(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType);
		theTests = new ArrayList<>();
		theTestsByName = new LinkedHashMap<>();
	}

	public Expresso.Def getHead() {
		return theHead;
	}

	/** @return All the tests to execute */
	public List<ExpressoTest.Def> getTests() {
		return Collections.unmodifiableList(theTests);
	}

	/** @return All the tests to execute, by name */
	public Map<String, ExpressoTest.Def> getTestsByName() {
		return Collections.unmodifiableMap(theTestsByName);
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session);
		theHead = ExElement.useOrReplace(Expresso.Def.class, theHead, session, "head");
		setExpressoEnv(theHead.getExpressoEnv()); // Inherit all the models and imports from the head section
		session.setExpressoEnv(getExpressoEnv());
		ExElement.syncDefs(ExpressoTest.Def.class, theTests, session.forChildren("test"));
		for (ExpressoTest.Def test : theTests) {
			if (theTestsByName.putIfAbsent(test.getName(), test) != null)
				throw new QonfigInterpretationException("Multiple tests named '" + test.getName() + "'",
					test.getElement().getPositionInFile(), 0);
		}
	}

	/**
	 * @param testName The name of the test to execute
	 * @throws ExpressoInterpretationException If the test structures could not be evaluated
	 * @throws ModelInstantiationException If the test structures could not be instantiated
	 * @throws IllegalArgumentException If no such test was found in this test's XML definition
	 */
	public void executeTest(String testName) throws ExpressoInterpretationException, ModelInstantiationException {
		ExpressoTest.Def testDef = theTestsByName.get(testName);
		if (testDef == null)
			throw new IllegalArgumentException("No such test in markup: " + testName);

		System.out.print("Interpreting global models...");
		System.out.flush();
		InterpretedExpressoEnv env = InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA;
		Expresso head = theHead.interpret(null);
		if (theHead.getClassViewElement() != null)
			env = env.with(theHead.getClassViewElement().configureClassView(env.getClassView().copy()).build());
		head.updateExpresso(env);
		env = head.getExpressoEnv(); // Use all the models and imports from the head section
		System.out.println("complete");

		System.out.print("Interpreting test " + testName + "...");
		System.out.flush();
		ExpressoTest.Interpreted testInterp = testDef.interpret(null);
		testInterp.updateTest(env);
		System.out.println("complete");

		System.out.print("Instantiating global models...");
		System.out.flush();
		SimpleObservable<Void> until = new SimpleObservable<>();
		try {
			ModelSetInstance models = env.getModels().createInstance(until).build();
			System.out.println("complete");

			System.out.print("Instantiating test " + testName + "...");
			System.out.flush();
			ExpressoTest test = testInterp.create(null);
			test.update(testInterp, models);
			System.out.println("complete");

			System.out.println("Executing test " + testName + "...");
			test.execute();
		} finally {
			until.onNext(null);
		}
	}
}

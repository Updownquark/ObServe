package org.observe.expresso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.observe.ObservableAction;
import org.observe.expresso.ExpressoTesting.TestAction.TestActionElement;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.TestInterpretation.StatefulStruct;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExNamed;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ExpressoQonfigValues;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.qommons.BreakpointHere;
import org.qommons.Named;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedFilePosition;

/** A testing structure parsed from a Qonfig XML file for testing expresso or expresso-dependent toolkits */
@ExElementTraceable(toolkit = ExpressoTestFrameworkInterpretation.TESTING,
qonfigType = "testing",
interpretation = StatefulStruct.Interpreted.class)
public class ExpressoTesting extends ExElement.Abstract {
	/** A test to execute */
	public static class ExpressoTest extends ExElement.Abstract implements Named {
		/** Definition of {@link ExpressoTest} */
		@ExElementTraceable(toolkit = ExpressoTestFrameworkInterpretation.TESTING,
			qonfigType = "test",
			interpretation = Interpreted.class,
			instance = ExpressoTest.class)
		public static class Def extends ExElement.Def.Abstract<ExpressoTest> implements Named {
			private final List<TestAction> theActions;

			/**
			 * @param parent The parent element of the test element
			 * @param qonfigType The Qonfig type of the test element
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
				theActions = new ArrayList<>();
			}

			@Override
			public String getName() {
				return getAddOn(ExNamed.Def.class).getName();
			}

			/** @return All the actions to execute for this test */
			@QonfigChildGetter("test-action")
			public List<TestAction> getActions() {
				return Collections.unmodifiableList(theActions);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				QonfigElementOrAddOn testAction = session.getType("test-action");
				syncChildren(TestAction.class, theActions,
					BetterList.of2(session.forChildren("test-action").stream(), s -> s.asElement(testAction)));
			}

			/**
			 * @param parent The parent for the interpreted test
			 * @return The interpreted test
			 */
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		/** Interpretation of {@link ExpressoTest} */
		public static class Interpreted extends ExElement.Interpreted.Abstract<ExpressoTest> implements Named {
			private final List<TestAction.Interpreted> theActions;

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

			/** @return All the actions to execute for this test */
			public List<TestAction.Interpreted> getActions() {
				return Collections.unmodifiableList(theActions);
			}

			/**
			 * Initializes or updates this test
			 *
			 * @param env The expresso environment for interpreting expressions
			 * @throws ExpressoInterpretationException If this test could not be interpreted
			 */
			public void updateTest(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				syncChildren(getDefinition().getActions(), theActions, d -> d.interpretValue(this), TestAction.Interpreted::updateValue);
			}

			/** @return The test instance */
			public ExpressoTest create() {
				return new ExpressoTest(getIdentity());
			}
		}

		private final List<TestAction.TestActionElement> theActions;

		ExpressoTest(Object id) {
			super(id);
			theActions = new ArrayList<>();
		}

		@Override
		public String getName() {
			return getAddOn(ExNamed.class).getName();
		}

		/** @return All the actions to execute for this test */
		public List<TestAction.TestActionElement> getActions() {
			return theActions;
		}

		/** Executes this test */
		public void execute() {
			for (TestAction.TestActionElement action : theActions) {
				System.out.print(action.reporting().getPosition().printPosition());
				System.out.flush();
				System.out.println(":");
				ObservableAction obsAction = action.getAction();
				if (action.isBreakpoint())
					BreakpointHere.breakpoint();
				try {
					obsAction.act(null);
					if (action.getExpectedException() != null)
						throw new AssertionError(
							action.reporting().getPosition() + ":\n\tExpected exception " + action.getExpectedException());
				} catch (RuntimeException e) {
					if (action.getExpectedException() == null || !action.getExpectedException().isInstance(e))
						throw new AssertionError(action.reporting().getPosition() + ":\n\tUnexpected exception", e);
				} catch (AssertionError e) {
					throw new AssertionError(action.reporting().getPosition() + ":\n\t" + e.getMessage(), e);
				} catch (Error e) {
					throw new AssertionError(action.reporting().getPosition() + ":\n\tUnexpected exception", e);
				}
			}
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);
			Interpreted myInterpreted = (Interpreted) interpreted;
			CollectionUtils.synchronize(theActions, myInterpreted.getActions(), (a, i) -> a.getIdentity() == i.getIdentity())//
			.simple(i -> i.create(this))//
			.onRight(el -> el.getLeftValue().update(el.getRightValue(), this))//
			.onCommon(el -> el.getLeftValue().update(el.getRightValue(), this))//
			.adjust();
		}

		@Override
		public void instantiated() {
			super.instantiated();
			for (TestActionElement action : theActions)
				action.instantiated();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);
			for (TestAction.TestActionElement action : theActions)
				action.instantiate(myModels);
		}
	}

	/** An action to execute for a test */
	@ExElementTraceable(toolkit = ExpressoTestFrameworkInterpretation.TESTING,
		qonfigType = "test-action",
		interpretation = TestAction.Interpreted.class,
		instance = TestAction.TestActionElement.class)
	public static class TestAction extends ExpressoQonfigValues.Action {
		private String theExpectedException;
		private boolean isBreakpoint;

		/**
		 * @param parent The parent element for this action
		 * @param type The Qonfig type of this action
		 */
		public TestAction(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The name of the exception type that is expected to be thrown by this test action */
		@QonfigAttributeGetter("expect-throw")
		public String getExpectedException() {
			return theExpectedException;
		}

		/** @return Whether a {@link BreakpointHere breakpoint} should be caught before executing this action */
		@QonfigAttributeGetter("breakpoint")
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
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		/** Interpretation of a {@link TestAction} */
		public static class Interpreted extends ExpressoQonfigValues.Action.Interpreted {
			private Class<? extends Throwable> theExpectedException;

			Interpreted(TestAction def, ExElement.Interpreted<?> parent) {
				super(def, parent);
			}

			@Override
			public Interpreted setParentElement(ExElement.Interpreted<?> parent) {
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

			/** @return The class of the exception that is expected to be thrown by the action */
			public Class<? extends Throwable> getExpectedException() {
				return theExpectedException;
			}

			/** @return Whether to catch a breakpoint before executing this action */
			public boolean isBreakpoint() {
				return getDefinition().isBreakpoint();
			}

			/** @return The position in the source file where this action was declared */
			public LocatedFilePosition getPosition() {
				return getDefinition().getPosition();
			}

			/**
			 * @param parent The parent element for the action instance
			 * @return The action instance
			 */
			public TestActionElement create(ExElement parent) {
				return new TestActionElement(getIdentity());
			}
		}

		static class TestActionElement extends ExElement.Abstract {
			private ModelValueInstantiator<ObservableAction> theActionInstantiator;
			private ObservableAction theAction;
			private Class<? extends Throwable> theExpectedException;
			private boolean isBreakpoint;

			public TestActionElement(Object id) {
				super(id);
			}

			public ObservableAction getAction() {
				return theAction;
			}

			public Class<? extends Throwable> getExpectedException() {
				return theExpectedException;
			}

			public boolean isBreakpoint() {
				return isBreakpoint;
			}

			@Override
			protected void doUpdate(Interpreted<?> interpreted) {
				super.doUpdate(interpreted);
				TestAction.Interpreted myInterpreted = (TestAction.Interpreted) interpreted;
				theActionInstantiator = myInterpreted.instantiate();
				theExpectedException = myInterpreted.getExpectedException();
				isBreakpoint = myInterpreted.isBreakpoint();
			}

			@Override
			protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
				super.doInstantiate(myModels);
				theAction = theActionInstantiator.get(myModels);
			}
		}
	}

	/** Definition of {@link ExpressoTesting} */
	public static class Def extends ExElement.Def.Abstract<ExpressoTesting> {
		private final List<ExpressoTest.Def> theTests;
		private final Map<String, ExpressoTest.Def> theTestsByName;

		/**
		 * @param parent The parent element for this test set
		 * @param qonfigType The Qonfig type of this test set
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theTests = new ArrayList<>();
			theTestsByName = new LinkedHashMap<>();
		}

		/** @return All the tests to execute */
		@QonfigChildGetter("test")
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
			syncChildren(ExpressoTest.Def.class, theTests, session.forChildren("test"));
			for (ExpressoTest.Def test : theTests) {
				if (theTestsByName.putIfAbsent(test.getName(), test) != null)
					throw new QonfigInterpretationException("Multiple tests named '" + test.getName() + "'",
						test.getElement().getPositionInFile(), 0);
			}
		}

		/**
		 * @param targetTest The name of the test to execute
		 * @return The interpretation of this testing for the given test
		 * @throws IllegalArgumentException If no such test was found in this test's XML definition
		 */
		public Interpreted interpret(String targetTest) throws IllegalArgumentException {
			ExpressoTest.Def testDef = theTestsByName.get(targetTest);
			if (testDef == null)
				throw new IllegalArgumentException("No such test in markup: " + targetTest);

			return new Interpreted(this, testDef);
		}
	}

	/** Interpretation of {@link ExpressoTesting} */
	public static class Interpreted extends ExElement.Interpreted.Abstract<ExpressoTesting> {
		private ExpressoTest.Def theTargetTestDef;
		private ExpressoTest.Interpreted theTargetTest;

		Interpreted(Def definition, ExpressoTest.Def targetTest) {
			super(definition, null);
			theTargetTestDef = targetTest;
		}

		/** @return The interpretation of the test we're actually going to execute */
		public ExpressoTest.Interpreted getTargetTest() {
			return theTargetTest;
		}

		/**
		 * Initializes or updates this test set
		 *
		 * @throws ExpressoInterpretationException If this test set could not be interpreted
		 */
		public void updateTest() throws ExpressoInterpretationException {
			update(getDefinition().getExpressoEnv().interpret(null, InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA.getClassView()));
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
			System.out.print("Interpreting global models...");
			System.out.flush();
			super.doUpdate(expressoEnv);
			System.out.println("complete");

			System.out.print("Interpreting test " + theTargetTestDef.getName() + "...");
			System.out.flush();
			theTargetTest = syncChild(theTargetTestDef, theTargetTest, def -> def.interpret(this), (i, iEnv) -> i.updateTest(iEnv));
			System.out.println("complete");
		}

		/** @return The test set instance */
		public ExpressoTesting create() {
			return new ExpressoTesting(getIdentity());
		}
	}

	private ExpressoTest theTargetTest;

	ExpressoTesting(Object id) {
		super(id);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theTargetTest = myInterpreted.getTargetTest().create();
		theTargetTest.update(myInterpreted.getTargetTest(), this);
	}

	@Override
	public void instantiated() {
		super.instantiated();

		theTargetTest.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		System.out.print("Instantiating test " + theTargetTest.getName() + "...");
		System.out.flush();
		theTargetTest.instantiate(myModels);
		System.out.println("complete");
	}

	/** Executes the target test in this test set */
	public void execute() {
		System.out.println("Executing test " + theTargetTest.getName() + "...");
		theTargetTest.execute();
		System.out.println("complete");
	}
}

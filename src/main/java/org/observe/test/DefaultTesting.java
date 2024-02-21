package org.observe.test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.qommons.Causable;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterHashMap;
import org.qommons.collect.BetterMap;
import org.qommons.ex.ExConsumer;

class DefaultTesting implements InteractiveTesting {
	private final WrappedEnv theEnv;
	private final UserInteraction theUI;
	private final Consumer<TestingState> theUpdate;

	private final List<DefaultInteractiveTestSuite> theSuiteStack;
	private InteractiveTest theCurrentTest;
	private ObservableConfig theCurrentTestConfig;
	private TestStage theStage;
	private final List<TestResult> theFailures;
	private boolean isTesting;
	private final List<ExConsumer<Causable, ?>> thePostActions;

	private Instant theTestTime;

	DefaultTesting(InteractiveTestEnvironment env, UserInteraction ui, Consumer<TestingState> update) {
		theEnv = new WrappedEnv(env);
		theUI = ui;
		theUpdate = update;

		theSuiteStack = new ArrayList<>();
		theFailures = new ArrayList<>();
		thePostActions = new ArrayList<>();
	}

	@Override
	public InteractiveTestEnvironment getEnv() {
		return theEnv;
	}

	@Override
	public UserInteraction getUI() {
		return theUI;
	}

	@Override
	public ObservableConfig getConfig() {
		return theCurrentTestConfig;
	}

	@Override
	public void testProgress() {
		if (theUpdate != null)
			theUpdate.accept(this);
	}

	@Override
	public void fail(String message, boolean exitTest) {
		if (message == null)
			throw new IllegalArgumentException("Failure message may not be null");
		if (theCurrentTest == null)
			throw new IllegalStateException("Not currently testing");
		TestResult failure = new DefaultTestResult(theTestTime, theStage, theCurrentTest.getStatusMessage(),
			theCurrentTest.getEstimatedLength(), theCurrentTest.getEstimatedProgress(), message);
		theFailures.add(failure);
		getTopSuite().getModifiableResults(theCurrentTest.getName()).add(failure);

		isTesting = !exitTest;
	}

	@Override
	public void postTest(ExConsumer<Causable, ?> action) {
		thePostActions.add(action);
	}

	@Override
	public List<InteractiveTestSuite> getSuiteStack() {
		return Collections.unmodifiableList(theSuiteStack);
	}

	@Override
	public DefaultInteractiveTestSuite getTopSuite() {
		return theSuiteStack.get(theSuiteStack.size() - 1);
	}

	@Override
	public InteractiveTest getCurrentTest() {
		return theCurrentTest;
	}

	@Override
	public TestStage getCurrentStage() {
		return theStage;
	}

	@Override
	public boolean isTesting() {
		return isTesting;
	}

	@Override
	public List<TestResult> getFailuresSoFar() {
		return Collections.unmodifiableList(theFailures);
	}

	DefaultTesting testTo(DefaultInteractiveTestSuite suite, InteractiveTest test) {
		if (theTestTime == null)
			theTestTime = Instant.now();
		isTesting = true;
		theUI.reset();
		theFailures.clear();
		_testTo(suite, test);
		return this;
	}

	void cancel() {
		theUI.cancel();
	}

	private void _testTo(DefaultInteractiveTestSuite suite, InteractiveTest test) {
		theSuiteStack.add(suite);
		for (InteractiveTestOrSuite tos : suite.getContent()) {
			if (!isTesting)
				break;
			if (test == null || suite.isSequential() || tos == test) {
				execute(tos);
			}
			if (isTesting && tos == test)
				isTesting = false;
		}
		theSuiteStack.remove(theSuiteStack.size() - 1);
	}

	private void execute(InteractiveTestOrSuite tos) {
		List<TestResult> oldResults = null;
		try {
			if (tos instanceof DefaultInteractiveTestSuite)
				_testTo((DefaultInteractiveTestSuite) tos, null);
			else if (!(tos instanceof InteractiveTest))
				throw new IllegalStateException("How did this get in here? " + tos.getClass().getName());
			else {
				int preFailures = theFailures.size();
				InteractiveTest test = (InteractiveTest) tos;
				theCurrentTest = test;
				ObservableCollection<TestResult> results = getTopSuite().getModifiableResults(theCurrentTest.getName());
				theStage = TestStage.Initialize;
				if (theUpdate != null)
					theUpdate.accept(this);
				oldResults = QommonsUtils.unmodifiableCopy(results);
				theCurrentTestConfig = getTopSuite().getConfig(theCurrentTest.getName());

				try {
					theStage = TestStage.Setup;
					if (theUpdate != null)
						theUpdate.accept(this);
					test.setup(this);
					if (isTesting) {
						theStage = TestStage.Execution;
						if (theUpdate != null)
							theUpdate.accept(this);
						test.execute(this);
					}
					if (isTesting) {
						theStage = TestStage.Analysis;
						if (theUpdate != null)
							theUpdate.accept(this);
						test.analyze(this);
					}
					if (theUpdate != null)
						theUpdate.accept(this);

					if (theFailures.size() == preFailures) {
						TestResult result = new DefaultTestResult(theTestTime, null, null, 0, 0, null);
						results.add(result);
					}
				} finally {
					test.close();
					List<TestResult> newResults = new ArrayList<>(results.size() - oldResults.size());
					newResults.addAll(results.subList(oldResults.size(), results.size()));
					theSuiteStack.get(theSuiteStack.size() - 1).tested(tos.getName(), oldResults, newResults);
				}
			}
		} catch (TestCanceledException e) {
			isTesting = false;
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage() == null ? e.toString() : e.getMessage(), true);
		} finally {
			if (!isInSequential()) {
				Causable cause = Causable.simpleCause();
				try (Transaction t = cause.use()) {
					for (int a = thePostActions.size() - 1; a >= 0; a--) {
						ExConsumer<Causable, ?> action = thePostActions.remove(a);
						try {
							action.accept(cause);
						} catch (Throwable e) {
							fail(action + ": " + e.getMessage(), true);
						}
					}
					theEnv.reset(cause);
				} catch (RuntimeException e) {
					fail(e.getMessage(), true);
				}
			}
			thePostActions.clear();
			theStage = null;
			theCurrentTestConfig = null;
			theCurrentTest = null;
			theUI.reset();
		}
	}

	private boolean isInSequential() {
		for (int s = 0; s < theSuiteStack.size() - 1; s++)
			if (theSuiteStack.get(s).isSequential())
				return true;
		return false;
	}

	class WrappedEnv implements InteractiveTestEnvironment {
		private final InteractiveTestEnvironment theWrappedEnv;
		final BetterMap<String, Object> thePreviousValues;

		WrappedEnv(InteractiveTestEnvironment wrapped) {
			theWrappedEnv = wrapped;
			thePreviousValues = BetterHashMap.build().build();
		}

		@Override
		public ObservableValue<?> getValueIfExists(String name) {
			ObservableValue<?> wrapped = theWrappedEnv.getValueIfExists(name);
			if (!(wrapped instanceof SettableValue))
				return wrapped;
			return new WrappedValue<>(name, (SettableValue<?>) wrapped);
		}

		@Override
		public InputStream getResource(String location) throws IOException {
			return theWrappedEnv.getResource(location);
		}

		void reset(Causable cause) {
			for (Map.Entry<String, Object> value : thePreviousValues.reverse().entrySet()) {
				try {
					((SettableValue<Object>) theWrappedEnv.getValueIfExists(value.getKey())).set(value.getValue(), cause);
				} catch (RuntimeException e) {
					fail("Resetting " + value.getKey() + " to " + value.getValue() + ": " + e.getMessage(), true);
				}
			}
		}

		class WrappedValue<T> implements SettableValue<T> {
			private final String theName;
			private final SettableValue<T> theWrappedValue;

			WrappedValue(String name, SettableValue<T> wrapped) {
				theName = name;
				theWrappedValue = wrapped;
			}

			@Override
			public T get() {
				return theWrappedValue.get();
			}

			@Override
			public Observable<ObservableValueEvent<T>> noInitChanges() {
				return theWrappedValue.noInitChanges();
			}

			@Override
			public Object getIdentity() {
				return theWrappedValue.getIdentity();
			}


			@Override
			public Transaction lock(boolean write, Object cause) {
				return theWrappedValue.lock(write, cause);
			}

			@Override
			public Transaction tryLock(boolean write, Object cause) {
				return theWrappedValue.tryLock(write, cause);
			}

			@Override
			public Collection<Cause> getCurrentCauses() {
				return theWrappedValue.getCurrentCauses();
			}

			@Override
			public long getStamp() {
				return theWrappedValue.getStamp();
			}

			@Override
			public boolean isLockSupported() {
				return theWrappedValue.isLockSupported();
			}

			@Override
			public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
				thePreviousValues.computeIfAbsent(theName, __ -> theWrappedValue.get());
				return theWrappedValue.get();
			}

			@Override
			public <V extends T> String isAcceptable(V value) {
				return theWrappedValue.isAcceptable(value);
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return theWrappedValue.isEnabled();
			}
		}
	}
}

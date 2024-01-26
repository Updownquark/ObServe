package org.observe.expresso;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Before;
import org.observe.SimpleObservable;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExpressoHeadSection;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.qommons.ValueHolder;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigApp;

/**
 * Abstract class for a unit test backed by an expresso Qonfig file
 *
 * @param <H> The sub-type of this testing's head structure
 */
public abstract class AbstractExpressoTest<H extends ExpressoHeadSection> {
	private static final Map<Class<?>, ExpressoTesting.Def> TESTING = new ConcurrentHashMap<>();

	/** @return The location to find the app configuration for the test. See expresso-test-app-template.qml. */
	protected abstract String getTestAppFile();

	private ExpressoTesting.Def theTesting;

	/**
	 * @param testName The name of the test to execute
	 * @throws ExpressoInterpretationException If the test with the given name could not be interpreted
	 * @throws ModelInstantiationException If the test with the given name could not be instantiated
	 */
	public void executeTest(String testName) throws ExpressoInterpretationException, ModelInstantiationException {
		ExpressoTesting.Interpreted interpreted = theTesting.interpret(testName);
		interpreted.updateTest();
		ExpressoTesting instance = interpreted.create();
		instance.update(interpreted, null);
		instance.instantiated();

		SimpleObservable<Void> until = new SimpleObservable<>();
		try {
			ModelSetInstance models = InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA.getModels().createInstance(until).build();
			instance.instantiate(models);
			instance.execute();
		} finally {
			until.onNext(null);
		}
	}

	/** Parses the test's QML (for the first test only) */
	@Before
	public void compileTesting() {
		theTesting = TESTING.computeIfAbsent(getClass(), __ -> {
			System.out.print("Interpreting test files...");
			System.out.flush();
			ExpressoTesting.Def testing;
			try {
				QonfigApp app = QonfigApp.parseApp(getClass().getResource(getTestAppFile()));
				ValueHolder<AbstractQIS<?>> session = new ValueHolder<>();
				testing = app.interpretApp(ExpressoTesting.Def.class, session);
				testing.update(session.get().as(ExpressoQIS.class));
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			System.out.println("done");
			return testing;
		});
	}
}

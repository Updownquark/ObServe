package org.observe.test;

import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.observe.SimpleObservable;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfig.XmlEncoding;
import org.observe.config.ObservableConfigPath;
import org.observe.config.SyncValueSet;
import org.qommons.ThreadConstraint;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.config.QommonsConfig;
import org.qommons.io.BetterFile;
import org.qommons.io.FileUtils;
import org.qommons.tree.BetterTreeList;
import org.xml.sax.SAXException;

class DefaultInteractiveTestSuite implements InteractiveTestSuite {
	private final DefaultInteractiveTestSuite theParent;
	private final String theName;
	private final boolean isSequential;
	private final CollectionLockingStrategy theLocker;
	private final ObservableCollection<InteractiveTestOrSuite> theContent;
	private final Map<String, ObservableConfig> theConfigs;
	private final Map<String, ObservableCollection<TestResult>> theResults;
	private final ObservableCollection<TestResult> theAllTestResults;
	private final Map<String, String> theConfigLocations;

	DefaultInteractiveTestSuite(DefaultInteractiveTestSuite parent, String name, boolean sequential, CollectionLockingStrategy locker) {
		theParent = parent;
		theName = name;
		isSequential = sequential;
		theLocker = locker;
		theContent = ObservableCollection.build(InteractiveTestOrSuite.class).withLocking(locker).build();
		theConfigLocations = new HashMap<>();
		theConfigs = new HashMap<>();
		theResults = new HashMap<>();
		theAllTestResults = theContent.flow()//
			.flatMap(TestResult.class, tos -> {
				if (tos instanceof InteractiveTest)
					return getModifiableResults(tos.getName()).flow();
				else
					return ((InteractiveTestSuite) tos).getAllTestResults().flow();
			})//
			.collect();
	}

	@Override
	public DefaultInteractiveTestSuite getParent() {
		return theParent;
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public boolean isSequential() {
		return isSequential;
	}

	@Override
	public synchronized void addTestCase(InteractiveTest test, String configLocation) {
		if (theConfigs.containsKey(test.getName()))
			throw new IllegalArgumentException("A test named " + test.getName() + " already exists in this suite");
		theConfigs.put(test.getName(),
			ObservableConfig.createRoot(test.getName(), null, __ -> new FastFailLockingStrategy(theLocker.getThreadConstraint())));
		theContent.add(test);
		if (configLocation != null)
			theConfigLocations.putIfAbsent(test.getName(), configLocation);
	}

	@Override
	public synchronized void addTestSuite(String suiteName, String configLocation, boolean sequential,
		Consumer<InteractiveTestSuite> suite) {
		if (sequential) {
			InteractiveTestSuite s = this;
			while (s != null) {
				if (s.isSequential()) {
					System.err.println("Sequential suites (" + s.getName() + ") cannot contain sequential suites (" + suiteName + ")");
					sequential = false;
					break;
				}
				s = s.getParent();
			}
		}
		for (InteractiveTestOrSuite tos : theContent) {
			if (tos instanceof InteractiveTestSuite && tos.getName().equals(suiteName)) {
				suite.accept((InteractiveTestSuite) tos);
				return;
			}
		}
		DefaultInteractiveTestSuite newSuite = new DefaultInteractiveTestSuite(this, suiteName, sequential, theLocker);
		theContent.add(newSuite);
		if (configLocation != null)
			theConfigLocations.putIfAbsent(suiteName, configLocation);
		suite.accept(newSuite);
	}

	@Override
	public ObservableCollection<InteractiveTestOrSuite> getContent() {
		return theContent.flow().unmodifiable().collect();
	}

	@Override
	public synchronized ObservableConfig getConfig(String testName) throws IOException {
		ObservableConfig config = theConfigs.get(testName);
		if (config == null)
			throw new IllegalArgumentException("No such test or suite: " + testName);
		if (!config.getName().isEmpty())
			return config.unmodifiable();
		ObservableConfig suiteTestConfig = getSuiteConfig()
			.getChild(ObservableConfigPath.buildPath("tests").andThen("test").withAttribute("name", testName).build(), false, null);
		if (suiteTestConfig != null)
			config.copyFrom(suiteTestConfig, false);
		String location = theConfigLocations.get(testName);
		if (location != null) {
			URL url = QommonsConfig.toUrl(location);
			ObservableConfig xmlConfig = ObservableConfig.createRoot("", null, __ -> new FastFailLockingStrategy(ThreadConstraint.ANY));
			try {
				ObservableConfig.readXml(xmlConfig, url.openStream(), XmlEncoding.DEFAULT);
			} catch (SAXException e) {
				throw new IOException("Could not parse " + url.getFile(), e);
			}
			config.copyFrom(xmlConfig, false);
		}
		return config.unmodifiable();
	}

	@Override
	public List<TestResult> execute(InteractiveTest test, UserInteraction ui, Consumer<TestingState> state) {
		DefaultTesting testing = new DefaultTesting(getEnv(), ui, state);
		setTesting(testing);
		try {
			return new DefaultTesting(getEnv(), ui, state).testTo(this, test).getFailuresSoFar();
		} finally {
			setTesting(null);
		}
	}

	void setTesting(DefaultTesting testing) {
		theParent.setTesting(testing);
	}

	@Override
	public synchronized ObservableCollection<TestResult> getTestResults(String testName) {
		return getModifiableResults(testName).flow().unmodifiable().collect();
	}

	@Override
	public ObservableCollection<TestResult> getAllTestResults() {
		return theAllTestResults;
	}

	ObservableCollection<TestResult> getModifiableResults(String testName) {
		ObservableCollection<TestResult> results = theResults.get(testName);
		if (results != null)
			return results;
		results = ObservableCollection.build(TestResult.class).build();
		theResults.put(testName, results);
		InteractiveTestOrSuite test = null;
		for (InteractiveTestOrSuite tos : theContent) {
			if (tos.getName().equals(testName)) {
				test = tos;
				break;
			}
		}
		if (test == null) {
			System.err.println("No such test: " + testName);
		} else if (!(test instanceof InteractiveTest)) {
			System.err.println(testName + " is a suite, not a test");
		} else {
			BetterList<InteractiveTestSuite> suitePath = BetterTreeList.<InteractiveTestSuite> build().build();
			DefaultInteractiveTestSuite suite = this;
			while (suite != null) {
				suitePath.addFirst(suite);
				suite = suite.theParent;
			}
			BetterList<InteractiveTestSuite> fSuitePath = BetterCollections.unmodifiableList(suitePath);
			InteractiveTest fTest = (InteractiveTest) test;
			results.onChange(evt -> {
				if (evt.getType() == CollectionChangeType.add) {
					evt.getNewValue().setSuitePath(fSuitePath).setTest(fTest);
				}
			});
			BetterFile testResultsFile = getTestResultsFile(test.getClass());
			if (testResultsFile.exists()) {
				ObservableConfig resultsConfig = ObservableConfig.createRoot(test.getName(), null,
					__ -> new FastFailLockingStrategy(ThreadConstraint.ANY));
				try {
					ObservableConfig.readXml(resultsConfig, testResultsFile.read(), XmlEncoding.DEFAULT);
				} catch (IOException | SAXException e) {
					System.err.println("Could not read test results for " + test.getClass().getName());
					e.printStackTrace();
				}
				SimpleObservable<Void> until = SimpleObservable.build().build();
				results.addAll(resultsConfig.asValue(TestResult.class).at("result").until(until).buildCollection(null)//
					.stream().map(DefaultTestResult::new).collect(Collectors.toList()));
				until.onNext(null);
			}
		}
		return results;
	}

	void tested(String testName, List<TestResult> oldResults, List<TestResult> newResults) {
		if (newResults.isEmpty())
			return; // Nothing new to write--maybe the user canceled the test
		ObservableCollection<TestResult> results = getModifiableResults(testName);
		Instant lastFailure = null, lastSuccess = null;
		for (TestResult result : results) {
			if (result.getFailMessage() != null && (lastFailure == null || result.getTestTime().compareTo(lastFailure) > 0))
				lastFailure = result.getTestTime();
			if (result.getFailMessage() == null && (lastSuccess == null || result.getTestTime().compareTo(lastSuccess) > 0))
				lastSuccess = result.getTestTime();
		}
		if (lastFailure != null && lastSuccess != null && lastSuccess.compareTo(lastFailure) > 0)
			lastFailure = null;

		// Keep only the most recent successful test (if any), as well as the most recent set of failures since then
		for (CollectionElement<TestResult> el : results.elements()) {
			if (el.get().getFailMessage() == null) {
				if (!el.get().getTestTime().equals(lastSuccess))
					results.mutableElement(el.getElementId()).remove();
			} else if (lastFailure == null || !el.get().getTestTime().equals(lastFailure))
				results.mutableElement(el.getElementId()).remove();
		}
		CollectionElement<InteractiveTestOrSuite> test = null;
		for (CollectionElement<InteractiveTestOrSuite> tos : theContent.elements()) {
			if (tos.get().getName().equals(testName)) {
				test = tos;
				break;
			}
		}
		if (test.get() instanceof InteractiveTest) {
			BetterFile testFailFile = getTestResultsFile(test.get().getClass());
			try {
				if (testFailFile.exists() && results.isEmpty())
					testFailFile.delete(null);
				else if (!results.isEmpty()) {
					if (!testFailFile.exists()) {
						BetterFile parent = testFailFile.getParent();
						if (parent != null && !parent.exists()) {
							try {
								parent.create(true);
							} catch (IOException e) {
								// The next bit will print an error
							}
						}
					}
					// Write the results to XML
					ObservableConfig resultsConfig = ObservableConfig.createRoot("results", null,
						__ -> new FastFailLockingStrategy(ThreadConstraint.ANY));
					SimpleObservable<Void> until = SimpleObservable.build().build();
					SyncValueSet<TestResult> configFailures = resultsConfig.asValue(TestResult.class).at("result").until(until)
						.buildEntitySet(null);
					for (TestResult result : results) {
						configFailures.create()//
						.copy(result)//
						.create();
					}
					ObservableConfig
					.toWriter(() -> new OutputStreamWriter(testFailFile.write(), Charset.forName("UTF-8")), XmlEncoding.DEFAULT)//
					.persist(resultsConfig);
					until.onNext(null);
				}
			} catch (IOException e) {
				System.err.println("Could not persist test results changes for " + test.get().getClass().getName());
				e.printStackTrace();
			}
		}
		try {
			CollectionElement<InteractiveTestOrSuite> fTest = test;
			EventQueue.invokeAndWait(() -> {
				theContent.mutableElement(fTest.getElementId()).set(fTest.get());
			});
		} catch (InvocationTargetException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	File getTestResultsDirectory() {
		return theParent.getTestResultsDirectory();
	}

	private BetterFile getTestResultsFile(Class<?> test) {
		File trd = getTestResultsDirectory();
		if (trd != null)
			return FileUtils.better(new File(trd, test.getName().replaceAll("\\.", "/") + ".test"));
		return FileUtils.getClassFile(test).getParent().at(resultsFileName(test.getName()));
	}

	private static String resultsFileName(String testClassName) {
		// Keep all the $ signs and stuff, but drop the package name
		int lastDot = testClassName.lastIndexOf('.');
		return (lastDot < 0 ? testClassName : testClassName.substring(lastDot + 1)) + ".test";
	}

	InteractiveTestEnvironment getEnv() {
		return theParent.getEnv();
	}

	ObservableConfig getSuiteConfig() throws IOException {
		return theParent.getConfig(theName);
	}
}

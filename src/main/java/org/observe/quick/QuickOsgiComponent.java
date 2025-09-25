package org.observe.quick;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.observe.ds.ComponentController;
import org.observe.ds.ComponentStage;
import org.observe.ds.DependencyService;
import org.observe.ds.DependencyServiceStage;
import org.observe.ds.Service;
import org.observe.ds.impl.Activate;
import org.observe.ds.impl.Component;
import org.observe.ds.impl.Configure;
import org.observe.expresso.ClassView;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ExternalModelSetBuilder;
import org.observe.expresso.ObservableModelSet.ModelSetInstanceBuilder;
import org.observe.expresso.qonfig.ExNamed;
import org.observe.expresso.qonfig.ExpressoDocument;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ExtModelValueElement;
import org.observe.expresso.qonfig.ObservableModelElement;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigParseException;
import org.qommons.io.BetterFile;
import org.qommons.io.ErrorReporting;
import org.qommons.io.FileUtils;
import org.qommons.io.TextParseException;
import org.qommons.threading.QommonsTimer;

/**
 * <p>
 * A parameterizable DS component that creates UI component with content loaded from a Quick file.
 * </p>
 * <p>
 * Requires a DS attribute "app" whose value is the path to the Quick app file referring to the UI file and instructing Quick how to load
 * it.
 * </p>
 */
@Component(loadStatus = "loadStatus()")
public abstract class QuickOsgiComponent {
	private final ThreadConstraint theThreading;
	private final SimpleObservable<Void> theUntil;
	private ClassLoader theClassLoader;
	private DependencyService<?> theDS;
	private URL theQuickAppFile;
	private QuickApp theQuickApp;
	private boolean isPrintingDocument;
	private final Map<BetterFile, Long> theRefreshFiles;

	private final Set<Class<?>> theWaitingServices;
	private QuickDocument.Interpreted theWaitingDoc;

	/** @param threading The thread constraint for creating and modifying UI components */
	protected QuickOsgiComponent(ThreadConstraint threading) {
		theThreading = threading;
		theUntil = new SimpleObservable<>();
		theWaitingServices = new LinkedHashSet<>();

		if (isDynamicRefresh()) {
			theRefreshFiles = new ConcurrentHashMap<>();
			QommonsTimer.getCommonInstance().build(() -> {
				if (theClassLoader == null || !isDynamicRefresh())
					return;
				BetterFile refresh = checkForRefresh();
				if (refresh != null) {
					System.out.println("Refreshing " + refresh);
					Thread thread = Thread.currentThread();
					ClassLoader preCCL = thread.getContextClassLoader();
					try {
						thread.setContextClassLoader(theClassLoader);
						refresh();
					} finally {
						thread.setContextClassLoader(preCCL);
					}
				}
			}, Duration.ofSeconds(1), false).setActive(true);
		} else
			theRefreshFiles = null;
	}

	/** @param appFile The quick-app file defining the Quick UI to load */
	@Configure("app")
	protected void forAppFile(URL appFile) {
		theQuickAppFile = appFile;
	}

	/** @param print Whether to print the Quick document to {@link System#out} after parsing it */
	@Configure(value = "print-document", optional = true)
	protected void printDocument(boolean print) {
		isPrintingDocument = print;
	}

	/** @return The dependency service loading this component */
	public DependencyService<?> getDS() {
		return theDS;
	}

	/** @return The thread constraint for creating and modifying UI components */
	public ThreadConstraint getThreading() {
		return theThreading;
	}

	/** @return The loaded Quick application */
	public QuickApp getQuickApp() {
		return theQuickApp;
	}

	/** @return The status message to display to the user while this component is loading */
	protected ObservableValue<String> loadStatus() {
		return ObservableValue.of("Loading User Interface");
	}

	/** @return An observable that will fire when the Quick source documents have changed and need to be reloaded */
	public SimpleObservable<Void> getUntil() {
		return theUntil;
	}

	/**
	 * <p>
	 * Whether this component should watch the Quick source documents and reload itself when they change
	 * </p>
	 * <p>
	 * This method is called once from the constructor to determine whether to set up the mechanism, and then periodically thereafter (if
	 * the first call returned true). This gives the component the opportunity to stop refreshing. E.g. if the component was not built with
	 * refreshing in mind, refreshing an active document can be destructive, so a component might enable auto-refreshing until the document
	 * is successfully displayed, whereupon auto-refresh can be stopped.
	 * </p>
	 *
	 * @return Whether this component dynamically refreshes
	 */
	protected abstract boolean isDynamicRefresh();

	/** @param file A file to watch. When the file changes this component will refresh itself (if so configured). */
	protected void addRefreshFile(BetterFile file) {
		if (theRefreshFiles != null)
			theRefreshFiles.put(file, file.getLastModified());
	}

	/**
	 * Activates this component
	 *
	 * @param controller The DS controller for this component
	 */
	@Activate
	protected void activate(ComponentController<?> controller) {
		theDS = controller.getDependencyService();
		theClassLoader = Thread.currentThread().getContextClassLoader();
		refresh();
	}

	/** @return Whether any of the Quick source documents for this component have changed */
	protected BetterFile checkForRefresh() {
		for (Map.Entry<BetterFile, Long> file : theRefreshFiles.entrySet()) {
			if (file.getKey().getLastModified() != file.getValue().longValue())
				return file.getKey();
		}
		return null;
	}

	/** Reloads the Quick UI for this component */
	protected void refresh() {
		try {
			theUntil.onNext(null);
			theWaitingDoc = null;
			theWaitingServices.clear();

			if (theRefreshFiles != null)
				theRefreshFiles.clear();
			addRefreshFile(FileUtils.ofUrl(theQuickAppFile));
			theQuickApp = null;
			URL quickAppToolkitUrl = QuickApplication.class.getResource("quick-app.qtd");
			if (quickAppToolkitUrl == null) {
				error("Could not locate Quick App toolkit definition 'quick-app.qtd'", null);
				return;
			}

			Appendable printDocument;
			if (isPrintingDocument || "true".equalsIgnoreCase(System.getProperty("osgi.quick.printAllDocs")))
				printDocument = System.out;
			else
				printDocument = null;

			try {
				theQuickApp = QuickApp.parseApp(theQuickAppFile, new URL[] { quickAppToolkitUrl }, Collections.emptyList(), printDocument);
			} catch (TextParseException | IllegalStateException | IOException | QonfigParseException e) {
				if (e instanceof QonfigParseException && theRefreshFiles != null) {
					try {
						addRefreshFile(
							FileUtils.ofUrl(new URL(((QonfigParseException) e).getIssues().get(0).fileLocation.getFileLocation())));
					} catch (MalformedURLException e2) {
					}
				}
				error("Could not parse Quick application file " + theQuickAppFile, e);
				return;
			}

			if (theRefreshFiles != null) {
				try {
					addRefreshFile(FileUtils.ofUrl(new URL(QommonsConfig.resolve(theQuickApp.getAppFile(), theQuickAppFile.toString()))));
				} catch (IOException e) {
				}
			}

			ValueHolder<AbstractQIS<?>> docSession = new ValueHolder<>();
			QuickDocument.Def quickDocDef;
			try {
				quickDocDef = theQuickApp.interpretApp(QuickDocument.Def.class, docSession);
			} catch (IllegalArgumentException | TextParseException | IOException | QonfigParseException e) {
				if (e instanceof QonfigParseException && theRefreshFiles != null) {
					try {
						for (ErrorReporting.Issue issue : ((QonfigParseException) e).getIssues()) {
							if (issue.fileLocation != null)
								addRefreshFile(FileUtils.ofUrl(new URL(issue.fileLocation.getFileLocation())));
						}
					} catch (MalformedURLException e2) {
					}
				}
				error("Could not parse Quick file " + theQuickApp.getAppFile(), e);
				return;
			}
			// We've successfully parsed the document as Qonfig.
			// Now go through the whole structure and add refresh files for all the toolkits and documents
			if (theRefreshFiles != null)
				monitorDocument(docSession.get().getElement(), new HashSet<>());

			try {
				quickDocDef.update(docSession.get().as(ExpressoQIS.class));
			} catch (QonfigInterpretationException e) {
				error("Could not interpret Quick file " + quickDocDef.reporting().getPosition().getFileName(), e);
				return;
			}
			docSession.clear(); // Free up memory

			InterpretedExpressoEnv env = InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA;
			ClassView.Builder classView = quickDocDef.getHead().getClassViewElement().configureClassView(env.getClassView().copy());
			if (theClassLoader instanceof URLClassLoader) {
				// If the quick file is in a folder with class files, add a wildcard import for that package
				String quickFileDir = quickDocDef.getElement().getDocument().getLocation();
				int lastSlash = quickFileDir.lastIndexOf('/');
				if (lastSlash >= 0) { // Nothing to import if it's in the root
					quickFileDir = quickFileDir.substring(0, lastSlash);
					if (quickFileDir.startsWith("jar:")) {
						int jarSep = quickFileDir.lastIndexOf("!/");
						if (jarSep >= 0)
							classView.withWildcardImport(quickFileDir.substring(jarSep + 2).replace('/', '.'));
					} else {
						for (URL clRoot : ((URLClassLoader) theClassLoader).getURLs()) {
							String path = clRoot.toString();
							String relLoc = null;
							if (quickFileDir.startsWith(path))
								relLoc = quickFileDir.substring(path.length());
							else if (path.startsWith("file:///")) {
								path = path.substring("file://".length());
								if (quickFileDir.startsWith(path))
									relLoc = quickFileDir.substring(path.length());
								else {
									path = path.substring(1);
									if (quickFileDir.startsWith(path))
										relLoc = quickFileDir.substring(path.length());
								}
							}
							if (relLoc != null && !relLoc.isEmpty()) {
								classView.withWildcardImport(relLoc//
									.substring(1) // Take off the file separator
									.replace('/', '.'));
								break;
							}
						}
					}
				}
			}
			env = env.with(classView.build());
			env = augmentEnvironment(env);
			ObservableModelSet.ExternalModelSetBuilder extModels = ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER);
			try {
				ExpressoDocument.Def<?, ?> expressoDoc = quickDocDef.getAddOn(ExpressoDocument.Def.class);
				for (ObservableModelElement.Def<?, ?> model : expressoDoc.getHead().getModelElement().getSubModels()) {
					if (model instanceof ObservableModelElement.ExtModelElement.Def) {
						ExternalModelSetBuilder extSubModel;
						try {
							extSubModel = extModels.addSubModel(model.getName());
						} catch (ModelException e) {
							throw new IllegalStateException("Argument conflict", e);
						}
						populateExtModel((ObservableModelElement.ExtModelElement.Def<?>) model, extSubModel, env);
					} else if (model instanceof ObservableModelElement.ConfigModelElement.Def) {
						ObservableModelElement.ConfigModelElement.Def<?> config = (ObservableModelElement.ConfigModelElement.Def<?>) model;
						if (config.getConfigName().isEmpty()) {
							config.setModelInitializer((configData, models) -> {
								loadAndPersistConfig(configData, config);
							});
						}
					}
				}
			} catch (ExpressoInterpretationException e) {
				error("Could not satisfy external model requirements for Quick UI " + theQuickApp.getAppFile(), e);
				return;
			}

			QuickDocument.Interpreted interpretedDoc = quickDocDef.interpret(null);
			quickDocDef = null; // Free up memory
			try {
				interpretedDoc.updateDocument(env.withExt(extModels.build()));
			} catch (ExpressoInterpretationException e) {
				error("Could not interpret Quick UI for " + theQuickApp.getAppFile(), e);
				return;
			}

			theWaitingDoc = interpretedDoc;
			if (theWaitingServices.isEmpty())
				installDocInstance();
			else {
				getDS().getStage().value().filter(v -> v == DependencyServiceStage.Initialized).take(1).act(__ -> {
					if (!theWaitingServices.isEmpty()) {
						StringBuilder message = new StringBuilder("Could not load Quick DS component '").append(theQuickApp.getAppFile())//
							.append("'.\n\tDS dependenc")//
							.append(theWaitingServices.size() == 1 ? "y was" : "ies were")//
							.append(" not provided:\n");
						StringUtils.print(message, "\n\t", theWaitingServices, (str, type) -> str.append(type.getName()));
						message.append(" were not provided");
						error(message.toString(), null);
					}
				});
			}
		} catch (RuntimeException | Error e) {
			error("Could not interpret Quick component", e);
		}
	}

	private void monitorDocument(QonfigElement element, Set<String> files) {
		if (files.add(element.getPositionInFile().getFileLocation())) {
			try {
				addRefreshFile(FileUtils.ofUrl(new URL(element.getPositionInFile().getFileLocation())));
			} catch (MalformedURLException e) {
			}
		}
		if (files.add(element.getType().getDeclarer().getLocationString())) {
			try {
				addRefreshFile(FileUtils.ofUrl(new URL(element.getType().getDeclarer().getLocationString())));
			} catch (MalformedURLException e) {
			}
		}
		for (QonfigElement child : element.getChildren())
			monitorDocument(child, files);
	}

	/**
	 * Provides subclasses with a chance to augment the expresso environment in which this component's models are interpreted
	 *
	 * @param env The expresso environment to augment
	 * @return The augmented environment
	 */
	protected InterpretedExpressoEnv augmentEnvironment(InterpretedExpressoEnv env) {
		return env;
	}

	private void installDocInstance() {
		QuickDocument.Interpreted interpretedDoc = theWaitingDoc;
		theWaitingDoc = null;
		if (interpretedDoc == null)
			return;
		try {
			QuickApplication app;
			try {
				app = theQuickApp.interpretQuickApplication(interpretedDoc);
			} catch (ExpressoInterpretationException e) {
				error("Could not interpret Quick UI for " + theQuickApp.getAppFile(), e);
				return;
			}

			QuickDocument doc = interpretedDoc.create();
			try {
				doc.update(interpretedDoc);

				doc.instantiated();

				ModelSetInstanceBuilder runtimeModels = InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA.getModels()
					.createInstance(getUntil());
				configureRuntimeModels(runtimeModels);
				doc.instantiate(runtimeModels.build());
			} catch (ModelInstantiationException e) {
				System.err.println("Could not instantiate Quick UI for " + theQuickApp.getAppFile());
				e.printStackTrace();
				return;
			}

			// Clean up to free memory
			interpretedDoc.destroy();
			interpretedDoc = null;

			theThreading.invoke(() -> {
				try {
					installQuickUI(app, doc);
				} catch (RuntimeException | Error e) {
					error("Could not interpret Quick component", e);
				}
			});
		} catch (RuntimeException | Error e) {
			error("Could not interpret Quick component", e);
		}
	}

	/**
	 * Configures runtime models for this component
	 *
	 * @param runtimeModels The runtime model builder to configure
	 */
	protected void configureRuntimeModels(ModelSetInstanceBuilder runtimeModels) {
	}

	/**
	 * @param message The error message to display
	 * @param x The exception (may be null)
	 */
	protected abstract void error(String message, Throwable x);

	/**
	 * @param app The Quick application
	 * @param doc The Quick document
	 */
	protected abstract void installQuickUI(QuickApplication app, QuickDocument doc);

	/**
	 * @param modelEl The model element defined in the Quick document
	 * @param extModels The builder for the external models to provide the Quick UI
	 * @param env The expresso environment to use to interpret types and such
	 * @throws ExpressoInterpretationException If the value could not be interpreted
	 */
	protected void populateExtModel(ObservableModelElement.ExtModelElement.Def<?> modelEl, ExternalModelSetBuilder extModels,
		InterpretedExpressoEnv env) throws ExpressoInterpretationException {

		for (ExtModelValueElement.Def<?> value : modelEl.getValues()) {
			String name = value.getAddOn(ExNamed.Def.class).getName();
			ModelInstanceType<Object, Object> type = (ModelInstanceType<Object, Object>) value.getType(env);
			try {
				extModels.with(name, type, satisfyExtModelValue(modelEl, value, type, env));
			} catch (ModelException e) {
				System.err.println("Could not install external model value " + modelEl.getModelPath() + "." + name);
				e.printStackTrace();
			}
		}
		for (ObservableModelElement.ExtModelElement.Def<?> subModel : modelEl.getSubModels()) {
			ExternalModelSetBuilder extSubModel;
			try {
				extSubModel = extModels.addSubModel(subModel.getName());
			} catch (ModelException e) {
				throw new IllegalStateException("Argument conflict", e);
			}
			populateExtModel(subModel, extSubModel, env);
		}
	}

	/**
	 * Loads a custom model value into the Quick application's models
	 *
	 * @param <M> The model type of the value to load
	 * @param <MV> The instance type of the value to load
	 * @param modelEl The expresso element defining the model to load the value into
	 * @param valueEl The expresso element defining the model value to load
	 * @param type The instance type of the value to load
	 * @param env The expresso environment to use to interpret types and expressions
	 * @return The satisfied model value
	 * @throws ExpressoInterpretationException If the value could not be loaded
	 */
	protected abstract <M, MV extends M> MV satisfyExtModelValue(ObservableModelElement.ExtModelElement.Def<?> modelEl,
		ExtModelValueElement.Def<?> valueEl, ModelInstanceType<M, MV> type, InterpretedExpressoEnv env)
			throws ExpressoInterpretationException;

	/**
	 * Satisfies an external model value
	 *
	 * @param <M> The model type of the value to satisfy
	 * @param <MV> The instance type of the value to satisfy
	 * @param <T> The type of the value to satisfy
	 * @param modelEl The model element definition defining the value
	 * @param valueEl The element definition defining the value
	 * @param type The type for the value
	 * @param env The expresso environment to create the value in
	 * @return The instantiated value
	 * @throws ExpressoInterpretationException If the value could not be created
	 */
	protected <M, MV extends M, T> MV satisfyServiceValue(ObservableModelElement.ExtModelElement.Def<?> modelEl,
		ExtModelValueElement.Def<?> valueEl, ModelInstanceType<M, MV> type, InterpretedExpressoEnv env)
			throws ExpressoInterpretationException {
		if (type.getModelType() == ModelTypes.Value) {
			Class<T> serviceType = (Class<T>) TypeTokens.getRawType(type.getType(0));
			ObservableValue<T> serviceValue = getDS().getServices().flow()//
				.filter(service -> service.getServiceType() == serviceType ? null : "Wrong service")//
				.flatMap(service -> getDS().getProviders(service).flow()//
					.refreshEach(provider -> provider.getStage().noInitChanges())//
					.filter(provider -> provider.getStage().get() == ComponentStage.Satisfied ? null : "Not satisfied")//
					.<T> transform(tx -> tx.cache(false).map(provider -> provider.provide((Service<T>) service))))//
				.collectActive(getUntil())//
				.observeFind(__ -> true).first().find();
			// Don't connect the UI to the service locking at all
			Object[] value = new Object[] { serviceValue.get() };
			SettableValue<T> container = SettableValue.<T> build()//
				.withThreadConstraint(ThreadConstraint.EDT)//
				.withValue((T) value[0])//
				.build();
			boolean[] satisfied = new boolean[] { value[0] != null };
			if (!satisfied[0]) {
				if (!theWaitingServices.add(serviceType))
					throw new ExpressoInterpretationException("Service " + serviceType.getName() + " requested multiple times",
						valueEl.reporting().getFileLocation());
			}
			serviceValue.changes().takeUntil(getUntil()).act(evt -> {
				if (evt.getNewValue() == value[0])
					return;
				value[0] = evt.getNewValue();
				ThreadConstraint.EDT.invoke(() -> {
					container.set(evt.getNewValue(), null);
					if (!satisfied[0]) {
						satisfied[0] = true;
						serviceSatisfied(serviceType);
					}
				});
			});
			return (MV) container.disableWith(SettableValue.ALWAYS_DISABLED);
		} else if (type.getModelType() == ModelTypes.Collection) {
			Class<T> serviceType = (Class<T>) TypeTokens.getRawType(type.getType(0));
			ObservableCollection<T> serviceValues = getDS().getServices().flow()//
				.filter(service -> service.getServiceType() == serviceType ? null : "Wrong service")//
				.flatMap(service -> getDS().getProviders(service).flow()//
					.<T> transform(tx -> tx.cache(false).map(provider -> provider.provide((Service<T>) service))))//
				.collectActive(getUntil());
			// Don't connect the UI to the service locking at all
			ObservableCollection<T> serviceCopy = ObservableCollection.<T> build()//
				.withThreadConstraint(ThreadConstraint.EDT)//
				.build();
			try (Transaction t = serviceValues.lock(false, null)) {
				if (!serviceValues.isEmpty()) {
					getThreading().invoke(() -> serviceCopy.addAll(QommonsUtils.unmodifiableCopy(serviceValues)));
				}
				serviceValues.changes().takeUntil(getUntil()).act(evt -> {
					getThreading().invoke(() -> {
						try (Transaction t2 = serviceCopy.lock(true, Causable.broken(evt))) {
							switch (evt.type) {
							case add:
								for (CollectionChangeEvent.ElementChange<T> change : evt.elements) {
									serviceCopy.add(change.index, change.newValue);
								}
								break;
							case remove:
								for (CollectionChangeEvent.ElementChange<T> change : evt.getElementsReversed()) {
									serviceCopy.remove(change.index);
								}
								break;
							case set:
								for (CollectionChangeEvent.ElementChange<T> change : evt.elements) {
									serviceCopy.set(change.index, change.newValue);
								}
								break;
							}
						}
					});
				});
			}
			return (MV) serviceCopy.flow().unmodifiable(false).collectPassive();
		} else {
			throw new ExpressoInterpretationException("Cannot satisfy Sage service value of model type " + type.getModelType(),
				valueEl.reporting().getFileLocation());
		}
	}

	private void serviceSatisfied(Class<?> serviceType) {
		theWaitingServices.remove(serviceType);
		if (theWaitingServices.isEmpty() && theWaitingDoc != null)
			installDocInstance();
	}

	/**
	 * Called for &lt;config> elements in this component's model
	 *
	 * @param config The config element to populate
	 * @param modelDef The &lt;config> element definition
	 */
	protected void loadAndPersistConfig(ObservableConfig config, ObservableModelElement.ConfigModelElement.Def<?> modelDef) {
	}
}

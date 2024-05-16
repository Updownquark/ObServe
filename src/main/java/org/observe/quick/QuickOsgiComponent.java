package org.observe.quick;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.observe.SimpleObservable;
import org.observe.ds.ComponentController;
import org.observe.ds.DependencyService;
import org.observe.ds.impl.Activate;
import org.observe.ds.impl.Component;
import org.observe.ds.impl.Configure;
import org.observe.expresso.ClassView;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ExternalModelSetBuilder;
import org.observe.expresso.qonfig.ExNamed;
import org.observe.expresso.qonfig.ExpressoDocument;
import org.observe.expresso.qonfig.ExtModelValueElement;
import org.observe.expresso.qonfig.ObservableModelElement;
import org.qommons.ThreadConstraint;
import org.qommons.config.QonfigParseException;
import org.qommons.io.BetterFile;
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
@Component(loadStatus = "Loading User Interface")
public abstract class QuickOsgiComponent {
	private final ThreadConstraint theThreading;
	private final SimpleObservable<Void> theUntil;
	private ClassLoader theClassLoader;
	private DependencyService<?> theDS;
	private URL theQuickAppFile;
	private QuickApp theQuickApp;
	private final Map<BetterFile, Long> theRefreshFiles;

	/**
	 * @param threading The thread constraint for creating and modifying UI components
	 * @param dynamicRefresh Whether this component should watch the Quick source documents and reload itself when they change. This feature
	 *        has not been well-tested
	 */
	protected QuickOsgiComponent(ThreadConstraint threading, boolean dynamicRefresh) {
		theThreading = threading;
		theUntil = new SimpleObservable<>();

		if (dynamicRefresh) {
			theRefreshFiles = new ConcurrentHashMap<>();
			QommonsTimer.getCommonInstance().build(() -> {
				if (theClassLoader != null && checkForRefresh()) {
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

	/** @return An observable that will fire when the Quick source documents have changed and need to be reloaded */
	public SimpleObservable<Void> getUntil() {
		return theUntil;
	}

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
	protected boolean checkForRefresh() {
		for (Map.Entry<BetterFile, Long> file : theRefreshFiles.entrySet()) {
			if (file.getKey().getLastModified() != file.getValue().longValue())
				return true;
		}
		return false;
	}

	/** Reloads the Quick UI for this component */
	protected void refresh() {
		try {
			theUntil.onNext(null);
			if (theRefreshFiles != null)
				theRefreshFiles.clear();
			addRefreshFile(FileUtils.ofUrl(theQuickAppFile));
			theQuickApp = null;
			URL quickAppToolkitUrl = QuickApplication.class.getResource("quick-app.qtd");
			if (quickAppToolkitUrl == null) {
				error("Could not locate Quick App toolkit definition 'quick-app.qtd'", null);
				return;
			}

			try {
				theQuickApp = QuickApp.parseApp(theQuickAppFile, new URL[] { quickAppToolkitUrl }, Collections.emptyList());
			} catch (TextParseException | IllegalStateException | IOException | QonfigParseException e) {
				if (e instanceof QonfigParseException) {
					try {
						addRefreshFile(
							FileUtils.ofUrl(new URL(((QonfigParseException) e).getIssues().get(0).fileLocation.getFileLocation())));
					} catch (MalformedURLException e2) {
					}
				}
				error("Could not parse Quick application file " + theQuickAppFile, e);
				return;
			}

			QuickDocument.Def quickDocDef;
			try {
				quickDocDef = theQuickApp.parseQuick(null);
			} catch (IllegalArgumentException | TextParseException | IOException | QonfigParseException e) {
				if (e instanceof QonfigParseException) {
					try {
						addRefreshFile(
							FileUtils.ofUrl(new URL(((QonfigParseException) e).getIssues().get(0).fileLocation.getFileLocation())));
					} catch (MalformedURLException e2) {
					}
				}
				error("Could not parse Quick file " + theQuickApp.getAppFile(), e);
				return;
			}
			try {
				addRefreshFile(FileUtils.ofUrl(new URL(quickDocDef.reporting().getPosition().getFileLocation())));
			} catch (MalformedURLException e) {
			}

			InterpretedExpressoEnv env = InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA;
			env = env.with(quickDocDef.getHead().getClassViewElement().configureClassView(ClassView.build()//
				.withWildcardImport("java.lang")).build());
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
					}
				}
			} catch (ExpressoInterpretationException e) {
				error("Could not satisfy external model requirements for Quick UI " + theQuickApp.getAppFile(), e);
				return;
			}

			QuickDocument.Interpreted interpretedDoc = quickDocDef.interpret(null);
			quickDocDef = null; // Free up memory
			QuickApplication app;
			try {
				interpretedDoc.updateDocument(env.withExt(extModels.build()));
				app = theQuickApp.interpretQuickApplication(interpretedDoc);
			} catch (ExpressoInterpretationException e) {
				error("Could not interpret Quick UI for " + theQuickApp.getAppFile(), e);
				return;
			}

			theThreading.invoke(() -> {
				try {
					createQuickUI(interpretedDoc, app);
				} catch (RuntimeException | Error e) {
					error("Could not interpret Quick component", e);
				}
			});
		} catch (RuntimeException | Error e) {
			error("Could not interpret Quick component", e);
		}
	}

	/**
	 * @param message The error message to display
	 * @param x The exception (may be null)
	 */
	protected abstract void error(String message, Throwable x);

	private void createQuickUI(QuickDocument.Interpreted interpretedDoc, QuickApplication app) {
		QuickDocument doc = interpretedDoc.create();
		try {
			doc.update(interpretedDoc);

			doc.instantiated();

			doc.instantiate(getUntil());
		} catch (ModelInstantiationException e) {
			System.err.println("Could not instantiate Quick UI for " + theQuickApp.getAppFile());
			e.printStackTrace();
			return;
		}

		// Clean up to free memory
		interpretedDoc.destroy();
		interpretedDoc = null;

		installQuickUI(app, doc);
	}

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
}

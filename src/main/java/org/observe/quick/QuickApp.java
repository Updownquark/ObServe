package org.observe.quick;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.jar.Manifest;

import javax.swing.JOptionPane;

import org.observe.Observable;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet;
import org.qommons.ArgumentParsing;
import org.qommons.Transformer;
import org.qommons.ValueHolder;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigApp;
import org.qommons.config.QonfigDocument;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigParseException;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSessionImplementation;
import org.qommons.io.TextParseException;

public class QuickApp extends QonfigApp {
	/** The name of the Quick-App toolkit */
	public static final String TOOLKIT_NAME = "Quick-App";

	/**
	 * @param appDefUrl The location of the {@link #getQonfigAppToolkit() Qonfig-App}-formatted application to parse
	 * @param appToolkits The locations of other toolkit definitions that may be needed to parse the application
	 * @return The parsed application
	 * @throws IOException If the application could not be read
	 * @throws TextParseException If the application could not be parsed as XML
	 * @throws QonfigParseException If the application could not be parsed as Qonfig
	 * @throws IllegalStateException If a references resource, like a toolkit, cannot be resolved
	 */
	public static QuickApp parseApp(URL appDefUrl, URL... appToolkits)
		throws IOException, TextParseException, QonfigParseException, IllegalStateException {
		QonfigApp qonfigApp = QonfigApp.parseApp(appDefUrl, appToolkits);
		QonfigToolkit quickAppTk = findQuickAppTk(qonfigApp.getDocument().getDocToolkit());
		if (quickAppTk == null)
			throw new IllegalStateException("Quick application file '" + qonfigApp.getLocation() + "' does not use the Quick-App toolkit");
		List<QuickInterpretation> quickInterpretation = QonfigApp.create(//
			qonfigApp.getDocument().getRoot().getChildrenInRole(quickAppTk, "quick-app", "quick-interpretation"),
			QuickInterpretation.class);
		return new QuickApp(qonfigApp.getDocument(), qonfigApp.getAppFile(), qonfigApp.getToolkits(), qonfigApp.getSessionTypes(),
			qonfigApp.getInterpretations(), quickInterpretation);
	}

	private final List<QuickInterpretation> theQuickInterpretations;

	protected QuickApp(QonfigDocument document, String appFile, Set<QonfigToolkit> toolkits,
		List<SpecialSessionImplementation<?>> sessionTypes,
		List<QonfigInterpretation> interpretations, List<QuickInterpretation> quickInterpretations) {
		super(document, appFile, toolkits, sessionTypes, interpretations);
		theQuickInterpretations = quickInterpretations;
	}

	public List<QuickInterpretation> getQuickInterpretations() {
		return theQuickInterpretations;
	}

	/**
	 * @param clArgs Command-line arguments. --quick-app=? may be used to specify the application setup file. The rest will be passed to the
	 *        quick document's external models (not yet implemented)
	 * @throws IllegalArgumentException If the {@link #getAppFile()} cannot be resolved
	 * @throws IOException If the application file or the quick file cannot be read
	 * @throws TextParseException If the application file or the quick file cannot be parsed as XML
	 * @throws QonfigParseException If the application file or the quick file cannot be validated
	 * @throws QonfigInterpretationException If the quick file cannot be interpreted
	 * @throws ExpressoInterpretationException If model configuration or references in the quick file contain errors
	 * @throws ModelInstantiationException If the quick document could not be loaded
	 */
	public QuickDocument.Def parseQuick(QuickDocument.Def previous, String... clArgs) throws IllegalArgumentException, IOException,
	TextParseException, QonfigParseException, QonfigInterpretationException {
		ValueHolder<AbstractQIS<?>> docSession = new ValueHolder<>();
		QuickDocument.Def quickDocDef;
		if (previous != null)
			quickDocDef = previous;
		else
			quickDocDef = interpretApp(QuickDocument.Def.class, docSession);
		quickDocDef.update(docSession.get().as(ExpressoQIS.class));
		docSession.clear(); // Free up memory
		return quickDocDef;
	}

	/**
	 * @throws ExpressoInterpretationException If model configuration or references in the quick file contain errors
	 */
	public QuickApplication interpretQuickApplication(QuickDocument.Interpreted quickDoc) throws ExpressoInterpretationException {
		Transformer.Builder<ExpressoInterpretationException> transformBuilder = Transformer.build();
		for (QuickInterpretation interp : getQuickInterpretations())
			interp.configure(transformBuilder);
		Transformer<ExpressoInterpretationException> transformer = transformBuilder.build();

		return transformer.transform(quickDoc, QuickApplication.class);
	}

	/**
	 * @param clArgs Command-line arguments. --quick-app=? may be used to specify the application setup file. The rest will be passed to the
	 *        quick document's external models (not yet implemented)
	 */
	public static void main(String... clArgs) {
		try {
			startQuick(clArgs);
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, e.getMessage(), "Quick Failed To Start", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}
	}

	/**
	 * @param clArgs Command-line arguments. --quick-app=? may be used to specify the application setup file. The rest will be passed to the
	 *        quick document's external models (not yet implemented)
	 * @throws IllegalArgumentException If the argument does not contain a reference to a quick-app file
	 * @throws IOException If the application file or the quick file cannot be read
	 * @throws TextParseException If the application file or the quick file cannot be parsed as XML
	 * @throws QonfigParseException If the application file or the quick file cannot be validated
	 * @throws QonfigInterpretationException If the quick file cannot be interpreted
	 * @throws ExpressoInterpretationException If model configuration or references in the quick file contain errors
	 * @throws ModelInstantiationException If the quick document could not be loaded
	 * @throws IllegalStateException If an error occurs loading any internal resources, such as toolkits
	 */
	public static void startQuick(String... clArgs) throws IllegalArgumentException, IOException, TextParseException, QonfigParseException,
	QonfigInterpretationException, ExpressoInterpretationException, ModelInstantiationException, IllegalStateException {
		// TODO Status (replace Splash Screen a la OSGi)

		QuickApp quickApp = parseQuickApp(clArgs);

		QuickDocument.Def quickDocDef = quickApp.parseQuick(null, clArgs);

		ObservableModelSet.ExternalModelSet extModels = parseExtModels(
			ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER), clArgs);

		QuickDocument.Interpreted interpretedDoc = quickDocDef.interpret(null);
		quickDocDef = null; // Free up memory
		interpretedDoc.update();

		QuickApplication app = quickApp.interpretQuickApplication(interpretedDoc);

		QuickDocument doc = interpretedDoc.create();
		doc.update(interpretedDoc, extModels, Observable.empty());

		// Clean up to free memory
		interpretedDoc.destroy();
		interpretedDoc = null;

		app.runApplication(doc, Observable.empty());
	}

	public static QuickApp parseQuickApp(String... clArgs)
		throws IllegalArgumentException, IOException, TextParseException, QonfigParseException {
		// Find the app definition
		ArgumentParsing.Arguments args = ArgumentParsing.build()//
			.forValuePattern(p -> p//
				.addStringArgument("quick-app", a -> a.optional())//
				)//
			.acceptUnmatched(true)//
			.build()//
			.parse(clArgs);
		String quickAppFile = args.get("quick-app", String.class);
		if (quickAppFile == null) {
			InputStream mfIn = QuickApplication.class.getResourceAsStream("/META-INF/MANIFEST.MF");
			if (mfIn == null)
				throw new IllegalStateException("Could not locate manifest");
			Manifest mf;
			try {
				mf = new Manifest(mfIn);
			} catch (IOException e) {
				throw new IllegalStateException("Could not read manifest", e);
			}
			quickAppFile = mf.getMainAttributes().getValue("Quick-App");
			if (quickAppFile == null)
				throw new IllegalArgumentException("No Quick-App specified");
		}

		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		if (loader == null)
			loader = QuickApplication.class.getClassLoader();
		URL quickAppUrl = loader.getResource(quickAppFile);
		if (quickAppUrl == null)
			quickAppUrl = QuickApplication.class.getResource(quickAppFile);
		if (quickAppUrl == null)
			throw new FileNotFoundException("Quick application file '" + quickAppFile + "' not found");

		URL quickAppToolkitUrl = QuickApplication.class.getResource("quick-app.qtd");
		if (quickAppToolkitUrl == null)
			throw new IllegalStateException("Could not locate Quick App toolkit quick-app.qtd");

		return QuickApp.parseApp(quickAppUrl, quickAppToolkitUrl);
	}

	public static ObservableModelSet.ExternalModelSet parseExtModels(ObservableModelSet.ExternalModelSetBuilder ext, String... clArgs) {
		/* TODO
		 * * Inspect external models
		 * * Build ArgumentParser2
		 * * Apply to command-line arguments
		 * * Create external models from argument values */
		return ext.build();
	}

	private static QonfigToolkit findQuickAppTk(QonfigToolkit toolkit) {
		if (TOOLKIT_NAME.equals(toolkit.getName()))
			return toolkit;
		for (QonfigToolkit dep : toolkit.getDependencies().values()) {
			QonfigToolkit found = findQuickAppTk(dep);
			if (found != null)
				return found;
		}
		return null;
	}
}

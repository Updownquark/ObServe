package org.observe.quick;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.jar.Manifest;

import javax.swing.JOptionPane;

import org.observe.Observable;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.ArgumentParsing;
import org.qommons.Transformer;
import org.qommons.ValueHolder;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigApp;
import org.qommons.config.QonfigDocument;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigParseException;
import org.qommons.config.QonfigToolkit;
import org.qommons.io.TextParseException;

/** Runs a Quick application from an application setup file configured as quick-app.qtd */
public interface QuickApplication {
	/** The name of the Quick-App toolkit */
	public static final String TOOLKIT_NAME = "Quick-App";

	/**
	 * Runs the application
	 *
	 * @param doc The document containing the Quick configuration for the application
	 * @throws ModelInstantiationException If an error occurs initializing the application
	 */
	void runApplication(QuickDocument doc) throws ModelInstantiationException;

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

		QonfigDocument quickApp = QonfigApp.parseApp(quickAppUrl, quickAppToolkitUrl);
		QonfigToolkit quickAppTk = Impl.findQuickAppTk(quickApp.getDocToolkit());
		if (quickAppTk == null)
			throw new IllegalStateException("Quick application file '" + quickAppFile + "' does not use the Quick-App toolkit");

		ValueHolder<AbstractQIS<?>> docSession = new ValueHolder<>();
		QuickDocument.Def quickDocDef = QonfigApp.interpretApp(quickApp, QuickDocument.Def.class, docSession);
		quickDocDef.update(docSession.get().as(ExpressoQIS.class));
		docSession.clear(); // Free up memory

		ObservableModelSet.ExternalModelSet extModels = ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER).build();
		/* TODO
		 * * Inspect external models
		 * * Build ArgumentParser2
		 * * Apply to command-line arguments
		 * * Create external models from argument values */

		List<QuickInterpretation> quickInterpretation = QonfigApp.create(//
			quickApp.getRoot().getChildrenInRole(quickAppTk, "quick-app", "quick-interpretation"), QuickInterpretation.class);
		quickApp = null; // Free up memory
		quickAppTk = null;
		Transformer.Builder<ExpressoInterpretationException> transformBuilder = Transformer.build();
		for (QuickInterpretation interp : quickInterpretation)
			interp.configure(transformBuilder);
		Transformer<ExpressoInterpretationException> transformer = transformBuilder.build();

		QuickDocument.Interpreted interpretedDoc = quickDocDef.interpret(null);
		quickDocDef = null; // Free up memory
		interpretedDoc.update();

		QuickApplication app = transformer.transform(interpretedDoc, QuickApplication.class);

		ModelSetInstance msi = interpretedDoc.getHead().getModels().createInstance(extModels, Observable.empty()).build();
		QuickDocument doc = interpretedDoc.create();
		doc.update(interpretedDoc, msi);

		// Clean up to free memory
		interpretedDoc.destroy();
		interpretedDoc = null;

		app.runApplication(doc);
	}

	/** Implementation details */
	class Impl {
		static QonfigToolkit findQuickAppTk(QonfigToolkit toolkit) {
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
}

package org.observe.quick;

import java.awt.EventQueue;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Manifest;

import javax.swing.JOptionPane;

import org.observe.expresso.ObservableModelSet;
import org.observe.util.TypeTokens;
import org.qommons.ArgumentParsing2;
import org.qommons.config.CustomValueType;
import org.qommons.config.DefaultQonfigParser;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigDocument;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigParseException;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;
import org.qommons.config.SpecialSessionImplementation;

public class QuickMain {
	public static final String QUICK_FILE_ENTRY="Quick-File";
	public static final String QUICK_TOOLKIT_ENTRY = "Quick-Toolkit";

	public static void main(String... clArgs) {
		try {
			startQuick(clArgs);
		} catch (RuntimeException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, e.getMessage(), "Quick Failed To Start", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}
	}

	public static void startQuick(String... clArgs) {
		//TODO Status (replace Splash Screen a la OSGi)

		// Find the app definition
		ArgumentParsing2.Arguments args = ArgumentParsing2.build()//
			.forValuePattern(p -> p//
				.addStringArgument("quick-app", a -> a.optional())//
				)//
			.acceptUnmatched(true)//
			.build()//
			.parse(clArgs);
		String quickAppFile = args.get("quick-app", String.class);
		if (quickAppFile == null) {
			InputStream mfIn = QuickMain.class.getResourceAsStream("/META-INF/MANIFEST.MF");
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
		if (!quickAppFile.startsWith("/")) // Force absolute
			quickAppFile = "/" + quickAppFile;
		URL appDefUrl = QuickMain.class.getResource(quickAppFile);
		if (appDefUrl == null)
			throw new IllegalArgumentException("Could not locate Quick-App definition file: " + quickAppFile);

		// Parse the app definition
		DefaultQonfigParser qonfigParser = new DefaultQonfigParser();
		URL appTKUrl = QuickMain.class.getResource("quick-app.qtd");
		QonfigToolkit appTK;
		try (InputStream aTKIn = appTKUrl.openStream()) {
			appTK = qonfigParser.parseToolkit(appTKUrl, aTKIn);
		} catch (NullPointerException e) {
			throw new IllegalStateException("Could not locate app toolkit definition");
		} catch(IOException e) {
			throw new IllegalStateException("Could not read app toolkit definition", e);
		} catch (QonfigParseException e) {
			throw new IllegalStateException("Could not parse app toolkit definition", e);
		}
		QonfigElement appDef;
		try (InputStream appDefIn = appDefUrl.openStream()) {
			appDef = qonfigParser.parseDocument(quickAppFile, appDefIn).getRoot();
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not read Quick-App definition: " + quickAppFile, e);
		} catch (QonfigParseException e) {
			throw new IllegalArgumentException("Could not parse Quick-App definition: " + quickAppFile, e);
		}

		// Ensure the Quick file exists
		String quickFile = appDef.getAttributeText(appTK.getAttribute("quick-app", "file"));
		URL quickFileURL = QuickMain.class.getResource(quickFile);
		if (quickFileURL == null) {
			try {
				String resolved = QommonsConfig.resolve(quickFile, appDefUrl.toString());
				if (resolved == null)
					throw new IllegalArgumentException("Could not find quick file " + quickFile);
				quickFileURL = new URL(resolved);
			} catch (IOException e) {
				throw new IllegalArgumentException("Could not find quick file " + quickFile, e);
			}
		}

		// Install the dependency toolkits in the Qonfig parser
		for (QonfigElement toolkitEl : appDef.getChildrenInRole(appTK, "quick-app", "toolkit")) {
			List<CustomValueType> valueTypes = create(toolkitEl.getChildrenInRole(appTK, "toolkit", "value-type"), CustomValueType.class);
			String toolkitDef = toolkitEl.getAttributeText(appTK.getAttribute("toolkit", "def"));
			URL toolkitURL = QuickMain.class.getResource(toolkitDef);
			if (toolkitURL == null)
				throw new IllegalArgumentException("Could not find toolkit " + toolkitDef);
			try (InputStream tkIn = toolkitURL.openStream()) {
				qonfigParser.parseToolkit(toolkitURL, tkIn, //
					valueTypes.toArray(new CustomValueType[valueTypes.size()]));
			} catch (IOException e) {
				throw new IllegalStateException("Could not read toolkit " + toolkitDef, e);
			} catch (QonfigParseException e) {
				throw new IllegalStateException("Could not parse toolkit " + toolkitDef, e);
			} catch (RuntimeException e) {
				throw new IllegalStateException("Could not parse toolkit " + toolkitDef, e);
			}
		}

		// Parse the Quick file
		QonfigDocument qonfigDoc;
		try (InputStream quickIn = quickFileURL.openStream()) {
			qonfigDoc = qonfigParser.parseDocument(quickFileURL.toString(), quickIn);
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not read Quick file " + quickFile, e);
		} catch (QonfigParseException e) {
			throw new IllegalArgumentException("Could not parse Quick file " + quickFile, e);
		}

		// Build the interpreter
		Set<QonfigToolkit> toolkits = new LinkedHashSet<>();
		addToolkits(qonfigDoc.getDocToolkit(), toolkits);
		QonfigInterpreterCore.Builder coreBuilder = QonfigInterpreterCore.build(QuickMain.class,
			toolkits.toArray(new QonfigToolkit[toolkits.size()]));
		for (SpecialSessionImplementation<?> ssi : create(appDef.getChildrenInRole(appTK, "quick-app", "special-session"),
			SpecialSessionImplementation.class)) {
			addSpecial(ssi, coreBuilder);
		}

		for (QonfigInterpretation interp : create(appDef.getChildrenInRole(appTK, "quick-app", "interpretation"),
			QonfigInterpretation.class)) {
			coreBuilder.configure(interp);
		}
		QonfigInterpreterCore interpreter = coreBuilder.build();

		// Compile QuickDocument
		QuickDocument quickDoc;
		try {
			quickDoc = interpreter.interpret(qonfigDoc.getRoot())//
				.interpret(QuickDocument.class);
		} catch (QonfigInterpretationException e) {
			throw new IllegalStateException("Could not interpret Quick file at " + quickFile, e);
		}

		ObservableModelSet.ExternalModelSet extModels = ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER).build();
		/* TODO
		 * * Inspect external models
		 * * Build ArgumentParser2
		 * * Apply to command-line arguments
		 * * Create external models from argument values */

		// Instantiate frame from document and models
		QuickUiDef ui = quickDoc.createUI(extModels);

		EventQueue.invokeLater(() -> {
			ui.run(null, null).setVisible(true);
			// TODO Shut down Splash Screen
		});
	}

	static <T> List<T> create(Collection<QonfigElement> elements, Class<T> type) {
		List<T> values = new ArrayList<>(elements.size());
		for (QonfigElement el : elements) {
			Class<?> elType;
			try {
				elType = Class.forName(el.getValueText());
			} catch (ClassNotFoundException e) {
				throw new IllegalArgumentException("No such " + type.getSimpleName() + " findable: " + el.getValueText());
			}
			if (!type.isAssignableFrom(elType))
				throw new IllegalArgumentException("Class " + elType.getName() + " is not a " + type.getName());
			T value;
			try {
				value = (T) elType.newInstance();
			} catch (IllegalAccessException e) {
				throw new IllegalArgumentException(
					"Could not access " + type.getSimpleName() + " " + elType.getName() + " for instantiation", e);
			} catch (InstantiationException e) {
				throw new IllegalArgumentException("Could not instantiate " + type.getSimpleName() + " " + elType.getName(), e);
			}
			values.add(value);
		}
		return values;
	}

	private static void addToolkits(QonfigToolkit toolkit, Set<QonfigToolkit> toolkits) {
		for (QonfigToolkit dep : toolkit.getDependencies().values()) {
			if (toolkits.add(dep))
				addToolkits(dep, toolkits);
		}
	}

	private static <QIS extends SpecialSession<QIS>> void addSpecial(SpecialSessionImplementation<QIS> ssi, Builder coreBuilder) {
		Class<QIS> sessionType = (Class<QIS>) TypeTokens
			.getRawType(TypeTokens.get().of(ssi.getClass()).resolveType(SpecialSessionImplementation.class.getTypeParameters()[0]));
		coreBuilder.withSpecial(sessionType, ssi);
	}
}

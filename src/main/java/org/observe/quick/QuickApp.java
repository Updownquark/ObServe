package org.observe.quick;

import java.awt.EventQueue;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Manifest;

import javax.swing.JOptionPane;

import org.observe.expresso.ObservableModelSet;
import org.qommons.ArgumentParsing2;
import org.qommons.config.QonfigApp;

/** Runs a Quick application from an application setup file configured as qonfig-app.qtd */
public class QuickApp {
	/**
	 * @param clArgs Command-line arguments. --quick-app=? may be used to specify the application setup file. The rest will be passed to the
	 *        quick document's external models (not yet implemented)
	 */
	public static void main(String... clArgs) {
		try {
			startQuick(clArgs);
		} catch (RuntimeException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, e.getMessage(), "Quick Failed To Start", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}
	}

	/**
	 * @param clArgs Command-line arguments. --quick-app=? may be used to specify the application setup file. The rest will be passed to the
	 *        quick document's external models (not yet implemented)
	 */
	public static void startQuick(String... clArgs) {
		// TODO Status (replace Splash Screen a la OSGi)

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
			InputStream mfIn = QuickApp.class.getResourceAsStream("/META-INF/MANIFEST.MF");
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

		QuickDocument quickDoc = QonfigApp.interpretApp(quickAppFile, QuickDocument.class);

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
}

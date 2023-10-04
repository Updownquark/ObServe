package org.observe.quick;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ExtValueRef;
import org.observe.expresso.ObservableModelSet.ExternalModelSetBuilder;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.util.TypeTokens;
import org.qommons.ArgumentParsing;
import org.qommons.ArgumentParsing.Arguments;
import org.qommons.ArgumentParsing.ParserBuilder;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
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
import org.qommons.io.BetterFile;
import org.qommons.io.TextParseException;

public class QuickApp extends QonfigApp {
	/** The name of the Quick-App toolkit */
	public static final String TOOLKIT_NAME = "Quick-App";

	public static final String UNMATCHED_CL_ARGS = "$UNMATCHED$";

	/**
	 * @param appDefUrl The location of the {@link #getQonfigAppToolkit() Qonfig-App}-formatted application to parse
	 * @param appToolkits The locations of other toolkit definitions that may be needed to parse the application
	 * @return The parsed application
	 * @throws IOException If the application could not be read
	 * @throws TextParseException If the application could not be parsed as XML
	 * @throws QonfigParseException If the application could not be parsed as Qonfig
	 * @throws IllegalStateException If a references resource, like a toolkit, cannot be resolved
	 */
	public static QuickApp parseApp(URL appDefUrl, URL[] appToolkits, List<String> clArgs)
		throws IOException, TextParseException, QonfigParseException, IllegalStateException {
		QonfigApp qonfigApp = QonfigApp.parseApp(appDefUrl, appToolkits);
		QonfigToolkit quickAppTk = findQuickAppTk(qonfigApp.getDocument().getDocToolkit());
		if (quickAppTk == null)
			throw new IllegalStateException("Quick application file '" + qonfigApp.getLocation() + "' does not use the Quick-App toolkit");
		List<QuickInterpretation> quickInterpretation = QonfigApp.create(//
			qonfigApp.getDocument().getRoot().getChildrenInRole(quickAppTk, "quick-app", "quick-interpretation"),
			QuickInterpretation.class);
		return new QuickApp(qonfigApp.getDocument(), qonfigApp.getAppFile(), qonfigApp.getToolkits(), qonfigApp.getSessionTypes(),
			qonfigApp.getInterpretations(), quickInterpretation, QommonsUtils.unmodifiableCopy(clArgs));
	}

	private final List<QuickInterpretation> theQuickInterpretations;
	private final List<String> theCommandLineArgs;

	protected QuickApp(QonfigDocument document, String appFile, Set<QonfigToolkit> toolkits,
		List<SpecialSessionImplementation<?>> sessionTypes, List<QonfigInterpretation> interpretations,
		List<QuickInterpretation> quickInterpretations, List<String> commandLineArgs) {
		super(document, appFile, toolkits, sessionTypes, interpretations);
		theQuickInterpretations = quickInterpretations;
		theCommandLineArgs = commandLineArgs;
	}

	public List<QuickInterpretation> getQuickInterpretations() {
		return theQuickInterpretations;
	}

	public List<String> getCommandLineArgs() {
		return theCommandLineArgs;
	}

	/**
	 * @throws IllegalArgumentException If the {@link #getAppFile()} cannot be resolved
	 * @throws IOException If the application file or the quick file cannot be read
	 * @throws TextParseException If the application file or the quick file cannot be parsed as XML
	 * @throws QonfigParseException If the application file or the quick file cannot be validated
	 * @throws QonfigInterpretationException If the quick file cannot be interpreted
	 * @throws ExpressoInterpretationException If model configuration or references in the quick file contain errors
	 * @throws ModelInstantiationException If the quick document could not be loaded
	 */
	public QuickDocument.Def parseQuick(QuickDocument.Def previous)
		throws IllegalArgumentException, IOException, TextParseException, QonfigParseException, QonfigInterpretationException {
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
		} catch (TextParseException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, "<html>" + e.getPosition() + "<br>" + e.getMessage(), "Quick Failed To Start",
				JOptionPane.ERROR_MESSAGE);
			System.exit(1);
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

		QuickDocument.Def quickDocDef = quickApp.parseQuick(null);

		InterpretedExpressoEnv env = InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA;
		ObservableModelSet.ExternalModelSet extModels = parseExtModels(quickDocDef.getHead().getExpressoEnv().getBuiltModels(),
			quickApp.getCommandLineArgs(), ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER), env);

		QuickDocument.Interpreted interpretedDoc = quickDocDef.interpret(null);
		quickDocDef = null; // Free up memory
		interpretedDoc.updateDocument(env.withExt(extModels));

		QuickApplication app = quickApp.interpretQuickApplication(interpretedDoc);

		QuickDocument doc = interpretedDoc.create();
		doc.update(interpretedDoc);
		doc.instantiated();

		// Clean up to free memory
		interpretedDoc.destroy();
		interpretedDoc = null;

		doc.instantiate(Observable.empty());
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

		return QuickApp.parseApp(quickAppUrl, new URL[] { quickAppToolkitUrl }, args.getUnmatched());
	}

	public static ObservableModelSet.ExternalModelSet parseExtModels(ObservableModelSet.Built models, List<String> clArgs,
		ObservableModelSet.ExternalModelSetBuilder ext, InterpretedExpressoEnv env) {
		ArgumentParsing.ParserBuilder apBuilder = ArgumentParsing.build();
		// Inspect external models and build an ArgumentParser
		buildArgumentParser(models, apBuilder, null, new StringBuilder(), env);
		// Apply to command-line arguments
		ArgumentParsing.Arguments parsedArgs = apBuilder.build()//
			.parse(clArgs);
		// Create external models from argument values
		populateExtModel(models, ext, parsedArgs, null, new StringBuilder(), env);
		return ext.build();
	}

	private static void buildArgumentParser(ObservableModelSet.Built models, ArgumentParsing.ParserBuilder builder, String modelName,
		StringBuilder path, InterpretedExpressoEnv env) {
		int preLen = path.length();
		for (String name : models.getComponentNames()) {
			ObservableModelSet.ModelComponentNode<?> comp = models.getComponentIfExists(name);
			if (comp.getThing() instanceof ExtValueRef) {
				if (path.length() > 0)
					path.append('.');
				path.append(toArgName(name));
				buildArgument(modelName, path.toString(), (ExtValueRef<?>) comp.getThing(), builder, env);
			} else if (comp.getThing() instanceof ObservableModelSet) {
				String compModelName;
				if (modelName == null)
					compModelName = name;
				else {
					compModelName = modelName;
					if (path.length() > 0)
						path.append('.');
					path.append(toArgName(name));
				}
				buildArgumentParser((ObservableModelSet.Built) comp.getThing(), builder, compModelName, path, env);
			}
			path.setLength(preLen);
		}
	}

	private static String toArgName(String name) {
		if (name.equals(UNMATCHED_CL_ARGS))
			return name;
		return StringUtils.parseByCase(name, true).toKebabCase();
	}

	protected static <M, MV extends M> void buildArgument(String modelName, String name, ExtValueRef<M> thing, ParserBuilder builder,
		InterpretedExpressoEnv env) {
		ModelType<M> modelType = thing.getModelType();
		if (modelType.getTypeCount() != 1)
			throw new IllegalArgumentException("External model value '" + modelName + "." + name
				+ "' cannot be satisfied via command-line.  Model type " + modelType + " is unsupported.");
		boolean hasDefault = thing.hasDefault();
		Consumer<ArgumentParsing.ArgumentBuilder<?, ?>> argConfig;
		if (modelType == ModelTypes.Value) {
			argConfig = arg -> {
				if (hasDefault)
					arg.optional();
				else
					arg.required();
			};
		} else if (modelType == ModelTypes.Collection) {
			if (name.equals(UNMATCHED_CL_ARGS)) {
				builder.acceptUnmatched(true);
				return;
			} else
				argConfig = arg -> arg.required();
		} else {
			argConfig = arg -> arg.required();
		}
		Consumer<ArgumentParsing.ValuedArgumentSetBuilder> argsBuilder;
		Class<?> type;
		try {
			type = TypeTokens.get().unwrap(TypeTokens.getRawType(thing.getType(env).getType(0)));
		} catch (ExpressoInterpretationException e) {
			throw new IllegalArgumentException("Unable to evaluate type of external component " + thing, e);
		}
		if (type == boolean.class) {
			if (!hasDefault && modelType == ModelTypes.Value) {
				builder.forFlagPattern(p -> p.add(name, null));
				return;
			} else
				argsBuilder = p -> p.addBooleanArgument(name, argConfig);
		} else if (type == int.class) {
			argsBuilder = p -> p.addIntArgument(name, argConfig);
		} else if (type == long.class) {
			argsBuilder = p -> p.addLongArgument(name, argConfig);
		} else if (type == double.class) {
			argsBuilder = p -> p.addDoubleArgument(name, argConfig);
		} else if (type == String.class) {
			argsBuilder = p -> p.addStringArgument(name, argConfig);
		} else if (Enum.class.isAssignableFrom(type)) {
			argsBuilder = p -> p.addEnumArgument(name, (Class<? extends Enum<?>>) type, argConfig);
		} else if (type == Duration.class) {
			argsBuilder = p -> p.addDurationArgument(name, argConfig);
		} else if (type == Instant.class) {
			argsBuilder = p -> p.addInstantArgument(name, argConfig);
		} else if (type == File.class) {
			argsBuilder = p -> p.addFileArgument(name, argConfig);
		} else if (type == BetterFile.class) {
			argsBuilder = p -> p.addBetterFileArgument(name, argConfig);
		} else
			throw new IllegalArgumentException("External model value '" + modelName + "." + name
				+ "' cannot be satisfied via command-line.  Value type " + type.getName() + " is unsupported.");
		if (modelType == ModelTypes.Value) {
			builder.forValuePattern(argsBuilder);
		} else if (modelType == ModelTypes.Collection || modelType == ModelTypes.Set) {
			builder.forMultiValuePattern(argsBuilder);
		} else if (modelType == ModelTypes.SortedCollection || modelType == ModelTypes.SortedSet) {
			if (!Comparable.class.isAssignableFrom(TypeTokens.get().wrap(type)))
				throw new IllegalArgumentException("External model value '" + modelName + "." + name
					+ "' cannot be satisfied via command-line.  Value type " + type.getName() + " is not intrinsically sortable.");
			builder.forMultiValuePattern(argsBuilder);
		} else
			throw new IllegalArgumentException("External model value '" + modelName + "." + name
				+ "' cannot be satisfied via command-line.  Model type " + modelType + " is unsupported.");

	}

	private static void populateExtModel(ObservableModelSet.Built models, ExternalModelSetBuilder ext, Arguments parsedArgs,
		String modelName, StringBuilder path, InterpretedExpressoEnv env) {
		int preLen = path.length();
		for (String name : models.getComponentNames()) {
			ObservableModelSet.ModelComponentNode<?> comp = models.getComponentIfExists(name);
			if (comp.getThing() instanceof ExtValueRef) {
				if (path.length() > 0)
					path.append('.');
				path.append(name);
				satisfyArgument(path.toString(), (ExtValueRef<?>) comp.getThing(), ext, parsedArgs, env);
			} else if (comp.getThing() instanceof ObservableModelSet) {
				String compModelName;
				if (modelName == null)
					compModelName = name;
				else {
					compModelName = modelName;
					if (path.length() > 0)
						path.append('.');
					path.append(toArgName(name));
				}
				ExternalModelSetBuilder subModel;
				try {
					subModel = ext.addSubModel(name);
				} catch (ModelException e) {
					throw new IllegalStateException("Argument conflict", e);
				}
				populateExtModel((ObservableModelSet.Built) comp.getThing(), subModel, parsedArgs, compModelName, path, env);
			}
			path.setLength(preLen);
		}
	}

	protected static <M, MV extends M> void satisfyArgument(String valueName, ExtValueRef<M> thing, ExternalModelSetBuilder ext,
		Arguments parsedArgs, InterpretedExpressoEnv env) {
		String argName = String.join(".", Arrays.stream(valueName.split("\\.")).map(n -> StringUtils.parseByCase(n, true).toKebabCase())//
			.collect(Collectors.toList()));
		ModelType<M> modelType = thing.getModelType();
		boolean hasDefault = thing.hasDefault();
		ModelInstanceType<M, MV> type;
		try {
			type = (ModelInstanceType<M, MV>) thing.getType(env);
		} catch (ExpressoInterpretationException e) {
			throw new IllegalArgumentException("Unable to evaluate type of external component " + thing, e);
		}
		Class<?> valueType = TypeTokens.get().unwrap(TypeTokens.getRawType(type.getType(0)));
		MV value;
		if (modelType == ModelTypes.Value) {
			if (valueType == boolean.class && !hasDefault)
				value = (MV) SettableValue.of(boolean.class, parsedArgs.has(argName), "Command-line argument");
			else
				value = (MV) SettableValue.of((Class<Object>) valueType, parsedArgs.get(argName), "Command-line argument");
		} else {
			ObservableCollection<Object> collection;
			if (modelType == ModelTypes.Collection)
				collection = ObservableCollection.build((Class<Object>) valueType).build();
			else if (modelType == ModelTypes.SortedCollection) {
				if (!Comparable.class.isAssignableFrom(TypeTokens.get().wrap(valueType)))
					return;
				collection = ObservableSortedCollection
					.build((Class<Object>) valueType, (o1, o2) -> ((Comparable<Object>) o1).compareTo(o2))
					.build();
			} else if (modelType == ModelTypes.Set)
				collection = ObservableSet.build((Class<Object>) valueType).build();
			else if (modelType == ModelTypes.SortedSet) {
				if (!Comparable.class.isAssignableFrom(TypeTokens.get().wrap(valueType)))
					return;
				collection = ObservableSortedSet.build((Class<Object>) valueType, (o1, o2) -> ((Comparable<Object>) o1).compareTo(o2))
					.build();
			} else
				return;

			if (valueName.equals(UNMATCHED_CL_ARGS))
				collection.addAll(parsedArgs.getUnmatched());
			else
				collection.addAll(parsedArgs.getAll(argName));

			value = (MV) collection.flow().unmodifiable(false).collect();
		}
		try {
			ext.with(valueName, type, value);
		} catch (ModelException e) {
			throw new IllegalStateException("Failed to satisfy external model value " + thing + " with command-line argument", e);
		}
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

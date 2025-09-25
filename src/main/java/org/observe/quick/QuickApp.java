package org.observe.quick;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Enumeration;
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
import org.observe.expresso.CompiledExpressoEnv;
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
import org.observe.expresso.qonfig.ExpressoDocument;
import org.observe.expresso.qonfig.ExpressoHeadSection;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.util.TypeTokens;
import org.qommons.ArgumentParsing;
import org.qommons.ArgumentParsing.Arguments;
import org.qommons.ArgumentParsing.ParserBuilder;
import org.qommons.BiTuple;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.Transformer;
import org.qommons.ValueHolder;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigApp;
import org.qommons.config.QonfigDocument;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigParseException;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSessionImplementation;
import org.qommons.io.BetterFile;
import org.qommons.io.ResourceLocator;
import org.qommons.io.TextParseException;

/** A class to facilitate instantiation of a Quick application from files */
public class QuickApp extends QonfigApp {
	/** The name of the Quick-App toolkit */
	public static final String TOOLKIT_NAME = "Quick-App";

	/**
	 * <p>
	 * If an argument by this name is specified in a Quick application file, the application will accept command-line arguments that do not
	 * correspond to external model values explicitly expected by the application.
	 * </p>
	 * <p>
	 * These arguments will then be passed to the application in a string collection-typed model value with this name.
	 * </p>
	 */
	public static final String UNMATCHED_CL_ARGS = "$UNMATCHED$";

	/**
	 * @param appDefUrl The location of the {@link #getQonfigAppToolkit() Qonfig-App}-formatted application to parse
	 * @param appToolkits The locations of other toolkit definitions that may be needed to parse the application
	 * @param clArgs The command-line arguments to use to populate the application's external models
	 * @param printDocument If given, the Quick document (with promised content spliced in) will be printed to this appendable
	 * @return The parsed application
	 * @throws IOException If the application could not be read
	 * @throws TextParseException If the application could not be parsed as XML
	 * @throws QonfigParseException If the application could not be parsed as Qonfig
	 * @throws IllegalStateException If a references resource, like a toolkit, cannot be resolved
	 */
	public static QuickApp parseApp(URL appDefUrl, URL[] appToolkits, List<String> clArgs, Appendable printDocument)
		throws IOException, TextParseException, QonfigParseException, IllegalStateException {
		QonfigApp qonfigApp = QonfigApp.parseApp(appDefUrl, appToolkits);
		QonfigToolkit quickAppTk = findQuickAppTk(qonfigApp.getDocument().getDocToolkit());
		if (quickAppTk == null)
			throw new IllegalStateException("Quick application file '" + qonfigApp.getLocation() + "' does not use the Quick-App toolkit");
		List<QuickInterpretation> quickInterpretation = QonfigApp.create(//
			qonfigApp.getDocument().getRoot().getChildrenInRole(quickAppTk, "quick-app", "quick-interpretation"),
			QuickInterpretation.class);
		return new QuickApp(qonfigApp.getDocument(), qonfigApp.getAppFile(), qonfigApp.getToolkits(),
			qonfigApp.getSessionTypes(), qonfigApp.getInterpretations(), quickInterpretation, QommonsUtils.unmodifiableCopy(clArgs),
			printDocument);
	}

	private final List<QuickInterpretation> theQuickInterpretations;
	private final List<String> theCommandLineArgs;
	private final Appendable printDocument;

	/**
	 * @param document The Qonfig document that this instance was parsed from
	 * @param appFile The path to the app file that was used to parse this instance
	 * @param toolkits All Qonfig toolkits loaded for the application
	 * @param sessionTypes Qonfig sesson implementations for parsing
	 * @param interpretations Qonfig interpretations to transform Qonfig-parsed elements into usable structures
	 * @param quickInterpretations Quick interpretations to transform Quick elements into application behaviors
	 * @param commandLineArgs The command-line arguments to pass to the application as external model values
	 * @param printDocument If given, the Quick document (with promised content spliced in) will be printed to this appendable
	 */
	protected QuickApp(QonfigDocument document, String appFile, Set<QonfigToolkit> toolkits,
		List<SpecialSessionImplementation<?>> sessionTypes,
		List<QonfigInterpretation> interpretations, List<QuickInterpretation> quickInterpretations, List<String> commandLineArgs,
		Appendable printDocument) {
		super(document, appFile, toolkits, sessionTypes, interpretations);
		theQuickInterpretations = quickInterpretations;
		theCommandLineArgs = commandLineArgs;
		this.printDocument = printDocument;
	}

	/** @return All classes configured for this application to interpret Quick types into application behaviors */
	public List<QuickInterpretation> getQuickInterpretations() {
		return theQuickInterpretations;
	}

	/** @return Command-line arguments to be passed to the application in external model values */
	public List<String> getCommandLineArgs() {
		return theCommandLineArgs;
	}

	/**
	 * Parses the Quick document for the application
	 *
	 * @param previous The Quick document previously parsed from by application, if this has been called previously
	 * @return The Quick document of the application
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
	 * @param quickDoc The interpreted document to interpret as an application
	 * @return The interpreted application
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
	 * A shortcut to parse, interpret, and instantiate this configured Quick application
	 *
	 * @return A tuple containing the {@link QuickApplication} ready to run and the {@link QuickDocument} instance to run it with
	 * @throws IllegalArgumentException If the {@link #getAppFile()} cannot be resolved
	 * @throws IOException If the application file or the quick file cannot be read
	 * @throws TextParseException If the application file or the quick file cannot be parsed as XML
	 * @throws QonfigParseException If the application file or the quick file cannot be validated
	 * @throws QonfigInterpretationException If the quick file cannot be interpreted
	 * @throws ExpressoInterpretationException If model configuration or references in the quick file contain errors
	 * @throws ModelInstantiationException If the quick document could not be loaded
	 */
	public BiTuple<QuickApplication, QuickDocument> prepareQuick()
		throws QonfigInterpretationException, IllegalArgumentException, TextParseException, IOException, QonfigParseException {
		QuickDocument.Def quickDocDef = parseQuick(null);

		InterpretedExpressoEnv env = InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA;
		if (quickDocDef.getHead().getClassViewElement() != null)
			env = env.with(quickDocDef.getHead().getClassViewElement().configureClassView(env.getClassView().copy()).build());
		ExpressoHeadSection.Def head = quickDocDef.getAddOn(ExpressoDocument.Def.class).getHead();
		CompiledExpressoEnv headEnv = head.getExpressoEnv(head.getDocument());
		ObservableModelSet.ExternalModelSet extModels = parseExtModels(headEnv.getBuiltModels(), getCommandLineArgs(),
			ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER), env);

		QuickDocument.Interpreted interpretedDoc = quickDocDef.interpret(null);
		quickDocDef = null; // Free up memory
		interpretedDoc.updateDocument(env.withExt(extModels));

		QuickApplication app = interpretQuickApplication(interpretedDoc);

		QuickDocument doc = interpretedDoc.create();
		doc.update(interpretedDoc);
		doc.instantiated();

		// Clean up to free memory
		interpretedDoc.destroy();
		interpretedDoc = null;

		return new BiTuple<>(app, doc);
	}

	@Override
	protected void appFileParsed(QonfigDocument doc) {
		super.appFileParsed(doc);
		if (printDocument != null) {
			try {
				printDocument.append(doc.getLocation()).append(":\n");
				printElement(doc.getRoot(), 0);
			} catch (IOException e) {
				System.err.println("Failed to print parsed Quick document");
				e.printStackTrace();
			}
		}
	}

	private void printElement(QonfigElement element, int indent) throws IOException {
		StringBuilder str = new StringBuilder();
		StringUtils.indent(str, indent);
		str.append('<').append(element.getType().getName());
		if (element.getPromise() != null)
			str.append(" promised-by=\"").append(element.getPromise().getType().getName()).append('"');
		if (!element.getDeclaredRoles().isEmpty())
			str.append(" role=\"").append(StringUtils.print(",", element.getDeclaredRoles(), r -> r.getName())).append('"');
		if (!element.getAttributes().isEmpty()) {
			for (QonfigElement.AttributeValue attr : element.getAttributes().values()) {
				StringUtils.indent(str.append('\n'), indent + 1)//
				.append(attr.getNamePosition()).append("=\"").append(attr.position).append('"');
			}
		}
		str.append('>');
		if (element.getValue() != null)
			StringUtils.indent(str.append('\n'), indent + 1).append(element.getValue().position);
		str.append('\n');
		printDocument.append(str);
		for (QonfigElement child : element.getChildren())
			printElement(child, indent + 1);
		str.setLength(0);
		StringUtils.indent(str, indent);
		str.append("</").append(element.getType().getName()).append(">\n");
		printDocument.append(str);
	}

	/**
	 * @param clArgs Command-line arguments. --quick-app=? may be used to specify the application setup file. The rest will be passed to the
	 *        quick document's external models (not yet implemented)
	 */
	public static void main(String... clArgs) {
		// TODO Status (replace Splash Screen a la OSGi)
		try {
			startQuick(clArgs);
		} catch (TextParseException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, "<html>" + e.getPosition() + "<br>" + e.getMessage().replace("<", "&lt;"),
				"Quick Failed To Start", JOptionPane.ERROR_MESSAGE);
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
		QuickApp quickApp = parseQuickApp(clArgs);

		BiTuple<QuickApplication, QuickDocument> prepared = quickApp.prepareQuick();

		prepared.getValue2().instantiate(Observable.empty());
		prepared.getValue1().runApplication(prepared.getValue2(), Observable.empty());
	}

	/**
	 * <p>
	 * Parses an instance of this class from command-line arguments and the application environment.
	 * </p>
	 * <p>
	 * If the '--quick-app=' argument is specified, 'quick-app.qtd' will be used to parse that file. Otherwise, this method will look for
	 * the 'Quick-App' manifest attribute.
	 * </p>
	 *
	 * @param clArgs Command-line arguments passed to the application
	 * @return The parsed application
	 * @throws IllegalArgumentException If the --quick-app argument is not present in the command-line arguments and no Quick-App manifest
	 *         property was specified
	 * @throws IOException If the Quick app file could not be found or could not be read
	 * @throws TextParseException If the Quick app file could not be parsed as XML
	 * @throws QonfigParseException If the Quick app file could not be parsed via the toolkit definition
	 */
	public static QuickApp parseQuickApp(String... clArgs)
		throws IllegalArgumentException, IOException, TextParseException, QonfigParseException {
		// Find the app definition
		ArgumentParsing.Arguments args = ArgumentParsing.build()//
			.forValuePattern(p -> p//
				.addStringArgument("quick-app", a -> a.optional())//
				.addBooleanArgument("print-document", a -> a.defaultValue(false))//
				)//
			.acceptUnmatched(true)//
			.build()//
			.parse(clArgs);
		String quickAppFile = args.get("quick-app", String.class);
		if (quickAppFile == null) {
			Enumeration<URL> manifests = QuickApplication.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
			if (!manifests.hasMoreElements())
				throw new IllegalStateException("Could not locate manifest");
			do {
				URL mfUrl = manifests.nextElement();
				try (InputStream mfIn = mfUrl.openStream()) {
					Manifest mf;
					try {
						mf = new Manifest(mfIn);
					} catch (IOException e) {
						System.err.println("Could not read manifest " + mfUrl + ": " + e);
						continue;
					}
					quickAppFile = mf.getMainAttributes().getValue("Quick-App");
					if (quickAppFile != null)
						break;
				}
			} while (manifests.hasMoreElements());
			if (quickAppFile == null)
				throw new IllegalArgumentException("No quick-app command line argument or Quick-App manifest property specified");
		}

		ResourceLocator locator = new ResourceLocator();
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		if (loader == null)
			locator.relativeTo(loader);
		locator.relativeTo(QuickApplication.class);
		URL quickAppUrl = locator.findResource(quickAppFile);
		if (quickAppUrl == null)
			throw new FileNotFoundException("Quick application file '" + quickAppFile + "' not found");

		URL quickAppToolkitUrl = QuickApplication.class.getResource("quick-app.qtd");
		if (quickAppToolkitUrl == null)
			throw new IllegalStateException("Could not locate Quick App toolkit definition 'quick-app.qtd'");

		Appendable printDocument = args.get("print-document", boolean.class) ? System.out : null;
		return QuickApp.parseApp(quickAppUrl, new URL[] { quickAppToolkitUrl }, args.getUnmatched(), printDocument);
	}

	/**
	 * Creates an external model set from command-line arguments passed to the application
	 *
	 * @param models The application's models
	 * @param clArgs The command-line arguments passed to the application
	 * @param ext The builder to build the external model set with
	 * @param env The expresso environment to use to evaluate model expressions
	 * @return The external model set to pass to the application
	 */
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

	/**
	 * Builds a command-line argument parser for an external model value specified by the Quick application.
	 *
	 * @param <M> The type of the model value
	 * @param modelName The name of the model the value will belong to
	 * @param name The name of the model value
	 * @param thing The reference of the external value to build
	 * @param builder The builder for the command-line argument that will populate the model value
	 * @param env The expresso environment to parse model expressions
	 */
	protected static <M> void buildArgument(String modelName, String name, ExtValueRef<M> thing, ParserBuilder builder,
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

	/**
	 * @param <M> The model type of the model value
	 * @param <MV> The instance type of the model value
	 * @param valueName The name of the model value
	 * @param thing The reference of the external value to satisfy
	 * @param ext The builder for the external model set that the model value will belong to
	 * @param parsedArgs The parsed command-line arguments passed to the application
	 * @param env The expresso environment to parse model expressions
	 */
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
				value = (MV) SettableValue.of(parsedArgs.has(argName), "Command-line argument");
			else
				value = (MV) SettableValue.of(parsedArgs.get(argName), "Command-line argument");
		} else {
			ObservableCollection<Object> collection;
			if (modelType == ModelTypes.Collection)
				collection = ObservableCollection.build().build();
			else if (modelType == ModelTypes.SortedCollection) {
				if (!Comparable.class.isAssignableFrom(TypeTokens.get().wrap(valueType)))
					return;
				collection = ObservableSortedCollection.build((o1, o2) -> ((Comparable<Object>) o1).compareTo(o2)).build();
			} else if (modelType == ModelTypes.Set)
				collection = ObservableSet.build().build();
			else if (modelType == ModelTypes.SortedSet) {
				if (!Comparable.class.isAssignableFrom(TypeTokens.get().wrap(valueType)))
					return;
				collection = ObservableSortedSet.build((o1, o2) -> ((Comparable<Object>) o1).compareTo(o2)).build();
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

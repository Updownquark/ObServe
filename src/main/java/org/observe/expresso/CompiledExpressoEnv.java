package org.observe.expresso;

import java.awt.Color;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.observe.SettableValue;
import org.observe.expresso.ObservableModelSet.ExternalModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ops.ExternalLiteral;
import org.observe.util.TypeTokens;
import org.qommons.Colors;
import org.qommons.IterableUtils;
import org.qommons.MultiInheritanceView;
import org.qommons.MultiInheritanceView.MultiInheritanceMap2;
import org.qommons.MultiInheritanceView.TypeMatch;
import org.qommons.TimeUtils;
import org.qommons.config.SessionValues;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedPositionedContent;

import com.google.common.reflect.TypeToken;

/** An environment to support some operations on {@link ObservableModelSet.CompiledModelValue compiled model values} */
public class CompiledExpressoEnv implements SessionValues {
	/**
	 * A compiled environment with standard java operators (and some {@link #withDefaultNonStructuredParsing() default}
	 * {@link NonStructuredParser}s
	 */
	public static final CompiledExpressoEnv STANDARD_JAVA = new CompiledExpressoEnv(//
		ObservableModelSet.build("StandardJava", ObservableModelSet.JAVA_NAME_CHECKER).build(), //
		UnaryOperatorSet.STANDARD_JAVA, BinaryOperatorSet.STANDARD_JAVA, new ErrorReporting.Default(null),
		SessionValues.EMPTY)//
		.withDefaultNonStructuredParsing();

	private final ObservableModelSet theModels;
	private final Map<String, ModelComponentId> theAttributes;
	private final UnaryOperatorSet theUnaryOperators;
	private final BinaryOperatorSet theBinaryOperators;
	private final MultiInheritanceMap2<Class<?>, Set<NonStructuredParser>> theNonStructuredParsers;
	private final MultiInheritanceMap2<Class<?>, Map<String, SyntheticField.Def<?, ?>>> theSyntheticFields;
	private final ErrorReporting theErrorReporting;
	private SessionValues theProperties;

	/**
	 * @param models The model set containing all values and sub-models available to expressions
	 * @param unaryOperators The set of unary operators available for expressions
	 * @param binaryOperators The set of binary operators available for expressions
	 * @param reporting The error reporting for the environment
	 * @param properties The properties for this environment
	 */
	public CompiledExpressoEnv(ObservableModelSet models, UnaryOperatorSet unaryOperators, BinaryOperatorSet binaryOperators,
		ErrorReporting reporting, SessionValues properties) {
		this(models, Collections.emptyMap(), null, unaryOperators, binaryOperators, null, reporting, properties);
	}

	/**
	 * @param models The models for the environment
	 * @param attributes A mapping of attribute name to variable ID for values that are named via a text attribute
	 * @param nonStructuredParsers The non-structured parsers for the environment
	 * @param unaryOperators The unary operators for the environment
	 * @param binaryOperators The binary operators for the environment
	 * @param syntheticFields Synthetic fields for the environment
	 * @param reporting The error reporting for the environment
	 * @param properties The properties for this environment
	 */
	protected CompiledExpressoEnv(ObservableModelSet models, Map<String, ModelComponentId> attributes,
		MultiInheritanceMap2<Class<?>, Set<NonStructuredParser>> nonStructuredParsers, UnaryOperatorSet unaryOperators,
		BinaryOperatorSet binaryOperators, MultiInheritanceMap2<Class<?>, Map<String, SyntheticField.Def<?, ?>>> syntheticFields,
		ErrorReporting reporting, SessionValues properties) {
		theModels = models;
		theAttributes = attributes;
		theUnaryOperators = unaryOperators;
		theBinaryOperators = binaryOperators;
		theNonStructuredParsers = MultiInheritanceView.createClassMap();
		if (nonStructuredParsers != null)
			theNonStructuredParsers.putAll(nonStructuredParsers);
		theSyntheticFields = MultiInheritanceView.createClassMap();
		if (syntheticFields != null) {
			for (Map.Entry<Class<?>, Map<String, SyntheticField.Def<?, ?>>> field : syntheticFields.allEntries()) {
				theSyntheticFields.computeIfAbsent(field.getKey(), () -> new HashMap<>()).putAll(field.getValue());
			}
		}
		theErrorReporting = reporting;
		theProperties = properties;
	}

	/**
	 * @param models The models for the environment
	 * @param attributes A mapping of attribute name to variable ID for values that are named via a text attribute
	 * @param nonStructuredParsers The non-structured parsers for the environment
	 * @param unaryOperators The unary operators for the environment
	 * @param binaryOperators The binary operators for the environment
	 * @param syntheticFields Synthetic fields for the environment
	 * @param reporting The error reporting for the new environment
	 * @param properties The properties for the new environment
	 * @return A copy of this environment with the given information
	 */
	protected CompiledExpressoEnv copy(ObservableModelSet models, Map<String, ModelComponentId> attributes,
		MultiInheritanceMap2<Class<?>, Set<NonStructuredParser>> nonStructuredParsers, UnaryOperatorSet unaryOperators,
		BinaryOperatorSet binaryOperators, MultiInheritanceMap2<Class<?>, Map<String, SyntheticField.Def<?, ?>>> syntheticFields,
		ErrorReporting reporting, SessionValues properties) {
		return new CompiledExpressoEnv(models, attributes, nonStructuredParsers, unaryOperators, binaryOperators, syntheticFields,
			reporting, properties);
	}

	/** @return The model set containing all values and sub-models available to expressions */
	public ObservableModelSet getModels() {
		return theModels;
	}

	/** @return This environment's {@link #getModels() model}, built if it is a {@link ObservableModelSet.Builder} */
	public ObservableModelSet.Built getBuiltModels() {
		return buildModel(theModels);
	}

	/** @return A mapping of attribute name to variable name for values that are named via a text attribute */
	protected Map<String, ModelComponentId> getAttributes() {
		return theAttributes;
	}

	/** @return All this environment's {@link NonStructuredParser}s */
	protected MultiInheritanceMap2<Class<?>, Set<NonStructuredParser>> getNonStructuredParsers() {
		return theNonStructuredParsers;
	}

	/** @return This environment's properties */
	protected SessionValues getProperties() {
		return theProperties;
	}

	/** @return A new properties set that inherits this environment's properties */
	protected SessionValues createChildProperties() {
		if (theProperties instanceof SessionValues.Default)
			return ((SessionValues.Default) theProperties).createChild();
		else
			return theProperties;
	}

	/** @return The set of unary operators available for expressions */
	public UnaryOperatorSet getUnaryOperators() {
		return theUnaryOperators;
	}

	/** @return The set of binary operators available for expressions */
	public BinaryOperatorSet getBinaryOperators() {
		return theBinaryOperators;
	}

	/** @return The set of synthetic fields available for expressions */
	protected MultiInheritanceMap2<Class<?>, Map<String, SyntheticField.Def<?, ?>>> getSyntheticFields() {
		return theSyntheticFields;
	}

	/** @return The error reporting for expressions evaluated in this environment to use */
	public ErrorReporting reporting() {
		return theErrorReporting;
	}

	private static ObservableModelSet.Built buildModel(ObservableModelSet model) {
		if (model == null)
			throw new IllegalStateException("No models set");
		else if (model instanceof ObservableModelSet.Built)
			return (ObservableModelSet.Built) model;
		else if (model instanceof ObservableModelSet.Builder)
			return ((ObservableModelSet.Builder) model).build();
		else
			throw new IllegalStateException("Models is a " + model.getClass().getName() + ", not either built or a builder");
	}

	/**
	 * @param extModels The external models for the interpreted environment
	 * @param classView The class view for the interpreted environment
	 * @return The interpretation of this compiled environment
	 */
	public InterpretedExpressoEnv interpret(ExternalModelSet extModels, ClassView classView) {
		if (extModels == null)
			extModels = ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER).build();
		if (classView == null)
			classView = ClassView.build().build();

		InterpretedExpressoEnv interpreted = new InterpretedExpressoEnv(null, extModels, classView, Collections.emptyMap(),
			getNonStructuredParsers(), getUnaryOperators(), getBinaryOperators(), getSyntheticFields(), reporting(),
			createChildProperties(), false);

		InterpretedModelSet interpretedModels = getBuiltModels().createInterpreted(interpreted);
		return interpreted.with(interpretedModels);
	}

	/**
	 * @param models The model set
	 * @return A copy of this environment with the given model set
	 */
	public CompiledExpressoEnv with(ObservableModelSet models) {
		if (models == theModels)
			return this;
		else
			return copy(models, theAttributes, theNonStructuredParsers, theUnaryOperators, theBinaryOperators, theSyntheticFields,
				theErrorReporting, createChildProperties());
	}

	/**
	 * @param unaryOps The unary operator set to use (or null to keep this environment's)
	 * @param binaryOps The binary operator set to use (or null to keep this environment's)
	 * @return A new environment with the given unary and binary operator sets
	 */
	public CompiledExpressoEnv withOperators(UnaryOperatorSet unaryOps, BinaryOperatorSet binaryOps) {
		if ((unaryOps == null || theUnaryOperators == unaryOps) && (binaryOps == null || theBinaryOperators == binaryOps))
			return this;
		return copy(theModels, theAttributes, theNonStructuredParsers, //
			unaryOps == null ? theUnaryOperators : unaryOps, //
				binaryOps == null ? theBinaryOperators : binaryOps, theSyntheticFields, theErrorReporting, getProperties());
	}

	/**
	 * @param type The type to query the parser for
	 * @param parser The non-structured parser to use to evaluate {@link ExternalLiteral} expressions
	 * @return This environment
	 */
	public CompiledExpressoEnv withNonStructuredParser(Class<?> type, NonStructuredParser parser) {
		Set<NonStructuredParser> parsers = theNonStructuredParsers.get(type, TypeMatch.EXACT);
		if (parsers != null && parsers.contains(parser))
			return this;
		MultiInheritanceMap2<Class<?>, Set<NonStructuredParser>> nspCopy = nspCopy();
		nspCopy.computeIfAbsent(type, () -> new LinkedHashSet<>()).add(parser);
		return copy(theModels, theAttributes, nspCopy, theUnaryOperators, theBinaryOperators, theSyntheticFields, theErrorReporting,
			getProperties());
	}

	MultiInheritanceMap2<Class<?>, Set<NonStructuredParser>> nspCopy() {
		MultiInheritanceMap2<Class<?>, Set<NonStructuredParser>> nspCopy = MultiInheritanceView.createClassMap();
		for (Map.Entry<Class<?>, Set<NonStructuredParser>> entry : theNonStructuredParsers.allEntries())
			nspCopy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
		return nspCopy;
	}

	/**
	 * Removes a parser registered with {@link #withNonStructuredParser(Class, NonStructuredParser)} to parse {@link ExternalLiteral}
	 * expressions
	 *
	 * @param type The type that the non-structured parser is registered for
	 * @param parser The non-structured parser to remove
	 * @return This environment
	 */
	public CompiledExpressoEnv removeNonStructuredParser(Class<?> type, NonStructuredParser parser) {
		Set<NonStructuredParser> parsers = theNonStructuredParsers.get(type, TypeMatch.EXACT);
		if (parsers == null || !parsers.contains(parser))
			return this;
		MultiInheritanceMap2<Class<?>, Set<NonStructuredParser>> nspCopy = nspCopy();
		nspCopy.get(type, TypeMatch.EXACT).remove(parser);
		return copy(theModels, theAttributes, nspCopy, theUnaryOperators, theBinaryOperators, theSyntheticFields, theErrorReporting,
			getProperties());
	}

	/**
	 * @param type The type to parse
	 * @return All non-structured parsers that may be able to parse a value of the given type
	 */
	public Iterable<NonStructuredParser> getNonStructuredParsers(Class<?> type) {
		return IterableUtils.flatten(theNonStructuredParsers.getAll(type, null));
	}

	/**
	 * @param env The environment to copy all the {@link NonStructuredParser}s from
	 * @return A copy of this environment with all the {@link NonStructuredParser}s from this environment and the other
	 */
	public CompiledExpressoEnv withAllNonStructuredParsers(CompiledExpressoEnv env) {
		MultiInheritanceMap2<Class<?>, Set<NonStructuredParser>> nspCopy = null;
		for (Map.Entry<Class<?>, Set<NonStructuredParser>> entry : env.theNonStructuredParsers.allEntries()) {
			if (nspCopy == null && theNonStructuredParsers.getOrDefault(entry.getKey(), TypeMatch.EXACT, Collections.emptySet())
				.containsAll(entry.getValue()))
				continue;
			if (nspCopy == null)
				nspCopy = nspCopy();
			Set<NonStructuredParser> forType = nspCopy.get(entry.getKey(), TypeMatch.EXACT);
			if (forType == null) {
				forType = new LinkedHashSet<>();
				nspCopy.put(entry.getKey(), forType);
			}
			forType.addAll(entry.getValue());
		}
		if (nspCopy == null)
			return this;
		return copy(theModels, theAttributes, nspCopy, theUnaryOperators, theBinaryOperators, theSyntheticFields, theErrorReporting,
			getProperties());
	}

	/**
	 * Registers some simple default non-structured parsers for utility
	 *
	 * @return This environment
	 */
	public CompiledExpressoEnv withDefaultNonStructuredParsing() {
		// To make it easier to specify strings in XML, the string literal need not be evaluated by type
		return withNonStructuredParser(Object.class, new NonStructuredParser() {
			@Override
			public <T> InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<? extends T>> parse(TypeToken<T> type, String text,
				InterpretedExpressoEnv env) throws ParseException {
				return (InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<? extends T>>) InterpretedValueSynth
					.literalValue(TypeTokens.get().STRING, text, text);
			}

			@Override
			public String getDescription() {
				return "Simple String literal";
			}

			@Override
			public String toString() {
				return getDescription();
			}
		})//
			.withNonStructuredParser(Duration.class, NonStructuredParser.simple(//
				s -> {
					try {
						return TimeUtils.parseDuration(s, false) != null;
					} catch (ParseException e) {
						return false;
					}
				}, (t, s) -> TimeUtils.parseDuration(s).asDuration(), "Simple duration literal"))//
			.withNonStructuredParser(Instant.class, NonStructuredParser.simple(//
				s -> {
					try {
						return TimeUtils.parseInstant(s, true, false, null) != null;
					} catch (ParseException e) {
						return false;
					}
				}, (t, s) -> TimeUtils.parseInstant(s, true, true, null).evaluate(Instant::now), "Simple date/time literal"))//
			.withNonStructuredParser(Enum.class, new NonStructuredParser() {
				@Override
				public boolean canParse(TypeToken<?> type, String text, InterpretedExpressoEnv env) {
					Class<?> raw = TypeTokens.getRawType(type);
					return Enum.class.isAssignableFrom(raw) && !Modifier.isAbstract(raw.getModifiers());
				}

				@Override
				public <T> InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<? extends T>> parse(TypeToken<T> type,
					String text, InterpretedExpressoEnv env) throws ParseException {
					return parseEnum(TypeTokens.getRawType(type), text);
				}

				private <E extends Enum<E>> E parseEnum(Class<?> type, String text) throws ParseException {
					try {
						return Enum.valueOf((Class<E>) type, text);
					} catch (IllegalArgumentException e) {
						throw new ParseException(e.getMessage(), 0);
					}
				}

				@Override
				public String getDescription() {
					return "Enum literal";
				}

				@Override
				public String toString() {
					return getDescription();
				}
			})//
			.withNonStructuredParser(Color.class, NonStructuredParser.simple(//
				s -> {
					try {
						return Colors.parseIfColor(s) != null;
					} catch (ParseException e) {
						return false;
					}
				}, (t, s) -> Colors.parseColor(s), "Color literal"));
	}

	/**
	 * @param <E> The entity type for the new field
	 * @param entityType The entity type for the new field
	 * @param fieldName The name for the new field
	 * @param field The synthetic field definition
	 * @return This environment
	 */
	public <E> CompiledExpressoEnv withSyntheticField(Class<E> entityType, String fieldName, SyntheticField.Def<? super E, ?> field) {
		theSyntheticFields.computeIfAbsent(entityType, () -> new HashMap<>()).put(fieldName, field);
		return this;
	}

	/**
	 * @param <E> The entity type of the field to get
	 * @param entityType The entity type of the field to get
	 * @param fieldName The name of the field to get
	 * @return The field defined for the given entity type with the given name, or null if no such field was defined
	 */
	public <E> SyntheticField.Def<? super E, ?> getSyntheticField(Class<E> entityType, String fieldName) {
		List<Map<String, SyntheticField.Def<?, ?>>> fieldList = new ArrayList<>();
		theSyntheticFields.getAll(entityType, TypeMatch.SUPER_TYPE).forEach(fieldList::add);
		// Traverse from most specific to least
		Collections.reverse(fieldList);
		for (Map<String, SyntheticField.Def<?, ?>> fields : fieldList) {
			SyntheticField.Def<?, ?> field = fields.get(fieldName);
			if (field != null)
				return (SyntheticField.Def<? super E, ?>) field;
		}
		return null;
	}

	/**
	 * @param other The environment to copy synthetic fields from
	 * @return This environment
	 */
	public CompiledExpressoEnv withAllSyntheticFields(CompiledExpressoEnv other) {
		for (Map.Entry<Class<?>, Map<String, SyntheticField.Def<?, ?>>> fieldOwner : other.theSyntheticFields.allEntries()) {
			for (Map.Entry<String, SyntheticField.Def<?, ?>> field : fieldOwner.getValue().entrySet())
				withSyntheticField(fieldOwner.getKey(), field.getKey(), (SyntheticField.Def<Object, ?>) field.getValue());
		}
		return this;
	}

	/**
	 * @param position The position at which to report errors for the new expresso environment
	 * @return The new environment
	 */
	public CompiledExpressoEnv at(LocatedPositionedContent position) {
		ErrorReporting reporting = reporting().at(position);
		if (reporting == reporting())
			return this;
		return copy(theModels, theAttributes, theNonStructuredParsers, theUnaryOperators, theBinaryOperators, theSyntheticFields, reporting,
			getProperties());
	}

	/**
	 * @param positionOffset The relative position offset at which to report errors for the new expresso environment
	 * @return The new environment
	 */
	public CompiledExpressoEnv at(int positionOffset) {
		ErrorReporting reporting = reporting().at(positionOffset);
		if (reporting == reporting())
			return this;
		return copy(theModels, theAttributes, theNonStructuredParsers, theUnaryOperators, theBinaryOperators, theSyntheticFields, reporting,
			getProperties());
	}

	/**
	 * Adds a mapping of an attribute name to its text value, so that attribute reference expressions can retrieve values via a reference to
	 * the attribute name, without knowing what the value of that attribute might be in a given context
	 *
	 * @param attributeName The name of the attribute
	 * @param value The variable ID of the attribute
	 * @return The (possible) copy of this environment containing the mapping
	 */
	public CompiledExpressoEnv withAttribute(String attributeName, ModelComponentId value) {
		CompiledExpressoEnv env;
		if (theAttributes.isEmpty())
			env = copy(theModels, new LinkedHashMap<>(), theNonStructuredParsers, theUnaryOperators, theBinaryOperators, theSyntheticFields,
				theErrorReporting,
				getProperties());
		else
			env = this;
		env.theAttributes.put(attributeName, value);
		return env;
	}

	/**
	 * @param name The name of the attribute to get
	 * @return The value of the attribute
	 */
	public ModelComponentId getAttribute(String name) {
		return theAttributes.get(name);
	}

	/** @return A new environment with null model and class view */
	public CompiledExpressoEnv clearModels() {
		if (theModels == null && theAttributes == Collections.EMPTY_MAP)
			return this;
		return copy(null, Collections.emptyMap(), theNonStructuredParsers, theUnaryOperators, theBinaryOperators, theSyntheticFields,
			theErrorReporting,
			getProperties());
	}

	/** @return A (possible) copy of this environment with its attribute mappings cleared */
	public CompiledExpressoEnv clearAttributes() {
		if (theAttributes == Collections.EMPTY_MAP)
			return this;
		return copy(theModels, Collections.emptyMap(), theNonStructuredParsers, theUnaryOperators, theBinaryOperators, theSyntheticFields,
			theErrorReporting,
			getProperties());
	}

	/** @return A copy of this environment */
	public CompiledExpressoEnv copy() {
		return copy(theModels, new LinkedHashMap<>(theAttributes), theNonStructuredParsers.copy(), theUnaryOperators, theBinaryOperators,
			theSyntheticFields, theErrorReporting, getProperties());
	}

	@Override
	public Object get(String sessionKey, boolean localOnly) {
		return theProperties.get(sessionKey, localOnly);
	}

	@Override
	public <T> T get(String sessionKey, Class<? super T> type) {
		return theProperties.get(sessionKey, type);
	}

	private SessionValues initProperties() {
		if (theProperties == SessionValues.EMPTY) {
			if (this == STANDARD_JAVA || this == InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA)
				throw new IllegalStateException("Cannot set a property on a static environment constant");
			theProperties = SessionValues.newRoot();
		}
		return theProperties;
	}

	@Override
	public CompiledExpressoEnv put(String sessionKey, Object value) {
		initProperties().put(sessionKey, value);
		return this;
	}

	@Override
	public CompiledExpressoEnv putLocal(String sessionKey, Object value) {
		initProperties().putLocal(sessionKey, value);
		return this;
	}

	@Override
	public CompiledExpressoEnv putGlobal(String sessionKey, Object value) {
		initProperties().putGlobal(sessionKey, value);
		return this;
	}

	@Override
	public <T> T computeIfAbsent(String sessionKey, Supplier<T> creator) {
		return initProperties().computeIfAbsent(sessionKey, creator);
	}

	@Override
	public Set<String> keySet() {
		return theProperties.keySet();
	}

	@Override
	public ValueSource getSource(String sessionKey) {
		return theProperties.getSource(sessionKey);
	}

	@Override
	public String toString() {
		return new StringBuilder().append("models=").append(theModels).toString();
	}
}

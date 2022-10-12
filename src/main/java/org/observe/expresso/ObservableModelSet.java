package org.observe.expresso;

import static org.observe.expresso.ObservableModelSet.buildExternal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.util.TypeTokens;
import org.qommons.BreakpointHere;
import org.qommons.Named;
import org.qommons.QommonsUtils;
import org.qommons.config.CustomValueType;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigParseSession;
import org.qommons.config.QonfigToolkit;
import org.qommons.ex.ExConsumer;

import com.google.common.reflect.TypeToken;

public interface ObservableModelSet {
	interface NameChecker {
		void checkName(String name) throws IllegalArgumentException;
	}

	public static class JavaNameChecker implements NameChecker {
		public static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^([a-zA-Z_$][a-zA-Z\\d_$]*)$");
		public static final Set<String> JAVA_KEY_WORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(//
			"abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", //
			"default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", //
			"if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", //
			"package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", //
			"this", "throw", "throws", "transient", "try", "void", "volatile", "while")));

		public static void checkJavaName(String name) throws IllegalArgumentException {
			if (!IDENTIFIER_PATTERN.matcher(name).matches())
				throw new IllegalArgumentException("'" + name + "' is not a valid identifier");
			else if (JAVA_KEY_WORDS.contains(name))
				throw new IllegalArgumentException("'" + name + "' is a reserved word and cannot be used as an identifier");
		}

		@Override
		public void checkName(String name) throws IllegalArgumentException {
			checkJavaName(name);
		}

		@Override
		public String toString() {
			return "Java Name Checker";
		}
	}

	public static final JavaNameChecker JAVA_NAME_CHECKER = new JavaNameChecker();
	public static final CustomValueType JAVA_IDENTIFIER_TYPE = new CustomValueType() {
		@Override
		public String getName() {
			return "identifier";
		}

		@Override
		public Object parse(String value, QonfigToolkit tk, QonfigParseSession session) {
			try {
				JavaNameChecker.checkJavaName(value);
			} catch (IllegalArgumentException e) {
				session.withError(e.getMessage());
			}
			return value;
		}

		@Override
		public boolean isInstance(Object value) {
			return value instanceof String;
		}
	};

	interface ValueContainer<M, MV extends M> extends Function<ModelSetInstance, MV> {
		ModelInstanceType<M, MV> getType();

		MV get(ModelSetInstance models);

		@Override
		default MV apply(ModelSetInstance models) {
			return get(models);
		}

		default <M2, MV2 extends M2> ValueContainer<M2, MV2> map(ModelInstanceType<M2, MV2> type, Function<? super MV, ? extends MV2> map) {
			ValueContainer<M, MV> outer = this;
			return new AbstractValueContainer<M2, MV2>(type) {
				@Override
				public MV2 get(ModelSetInstance models) {
					return map.apply(outer.get(models));
				}

				@Override
				public String toString() {
					return map + "(" + outer + ")";
				}
			};
		}

		default <M2, MV2 extends M2> ValueContainer<M2, MV2> map(ModelInstanceType<M2, MV2> type,
			BiFunction<? super MV, ModelSetInstance, ? extends MV2> map) {
			ValueContainer<M, MV> outer = this;
			return new AbstractValueContainer<M2, MV2>(type) {
				@Override
				public MV2 get(ModelSetInstance models) {
					return map.apply(outer.get(models), models);
				}
			};
		}
	}

	interface ValueCreator<M, MV extends M> {
		ValueContainer<M, MV> createValue();

		static <M, MV extends M> ValueCreator<M, MV> constant(ValueContainer<M, MV> container) {
			return new ValueCreator<M, MV>() {
				@Override
				public ValueContainer<M, MV> createValue() {
					return container;
				}

				@Override
				public String toString() {
					return container.toString();
				}
			};
		}

		static <T> ValueCreator<SettableValue<?>, SettableValue<T>> literal(TypeToken<T> type, T value, String text) {
			return constant(ObservableModelSet.literalContainer(ModelTypes.Value.forType(type), value, text));
		}
	}

	abstract class AbstractValueContainer<M, MV extends M> implements ValueContainer<M, MV> {
		private final ModelInstanceType<M, MV> theType;

		public AbstractValueContainer(ModelInstanceType<M, MV> type) {
			theType = type;
		}

		@Override
		public ModelInstanceType<M, MV> getType() {
			return theType;
		}
	}

	static <M, MV extends M> ValueContainer<M, MV> container(Function<ModelSetInstance, MV> value, ModelInstanceType<M, MV> type) {
		class SyntheticValueContainer implements ValueContainer<M, MV> {
			@Override
			public ModelInstanceType<M, MV> getType() {
				return type;
			}

			private Function<ModelSetInstance, MV> getValue() {
				return value;
			}

			@Override
			public MV get(ModelSetInstance extModels) {
				return value.apply(extModels);
			}

			@Override
			public int hashCode() {
				return value.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (this == obj)
					return true;
				else if (!(obj instanceof SyntheticValueContainer))
					return false;
				return value.equals(((SyntheticValueContainer) obj).getValue());
			}

			@Override
			public String toString() {
				return value.toString();
			}
		}
		return new SyntheticValueContainer();
	}

	public static <T> SettableValue<T> literal(TypeToken<T> type, T value, String text) {
		return SettableValue.asSettable(ObservableValue.of(value), __ -> "Literal value '" + text + "'");
	}

	public static <T> SettableValue<T> literal(T value, String text) {
		return literal(TypeTokens.get().of((Class<T>) value.getClass()), value, text);
	}

	public static <T> ValueGetter<SettableValue<T>> literalGetter(T value, String text) {
		return literalGetter(TypeTokens.get().of((Class<T>) value.getClass()), value, text);
	}

	public static <T> ValueGetter<SettableValue<T>> literalGetter(TypeToken<T> type, T value, String text) {
		return new ValueGetter<SettableValue<T>>() {
			private final SettableValue<T> theValue = literal(type, value, text);

			@Override
			public SettableValue<T> get(ModelSetInstance models) {
				return theValue;
			}

			@Override
			public String toString() {
				return value.toString();
			}
		};
	}

	public static <T> ValueContainer<SettableValue<?>, SettableValue<T>> literalContainer(
		ModelInstanceType<SettableValue<?>, SettableValue<T>> type, T value, String text) {
		return new ValueContainer<SettableValue<?>, SettableValue<T>>() {
			private final SettableValue<T> theValue = literal((TypeToken<T>) type.getType(0), value, text);

			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<T>> getType() {
				return type;
			}

			@Override
			public SettableValue<T> get(ModelSetInstance extModels) {
				return theValue;
			}
		};
	}

	ObservableModelSet getParent();

	String getPath();

	/**
	 * @return The names of all this model's contents, whether
	 *         <ul>
	 *         <li>an internal value (created with {@link ObservableModelSet.Builder#with(String, ModelInstanceType, ValueGetter)}),</li>
	 *         <li>a dynamic value (created with {@link ObservableModelSet.Builder#withMaker(String, ValueCreator)}),</li>
	 *         <li>an external value (created with
	 *         {@link ObservableModelSet.Builder#withExternal(String, ModelInstanceType, ExtValueRef)}),</li>
	 *         <li>or a sub-model (created with {@link ObservableModelSet.Builder#createSubModel(String)}).</li>
	 *         </ul>
	 */
	Set<String> getContentNames();

	String pathTo(String name);

	ValueContainer<?, ?> get(String path, boolean required) throws QonfigInterpretationException;

	/**
	 * @param path The path of the thing to get
	 * @return The model structure at the given path, which may be:
	 *         <ul>
	 *         <li>a {@link ValueGetter} if the thing is an internal value created with
	 *         {@link ObservableModelSet.Builder#with(String, ModelInstanceType, ValueGetter)})</li>
	 *         <li>a {@link ValueCreator} if the thing is a dynamic value (created with
	 *         {@link ObservableModelSet.Builder#withMaker(String, ValueCreator)})</li>
	 *         <li>a {@link ExtValueRef} if the thing is an external value (created with
	 *         {@link ObservableModelSet.Builder#withExternal(String, ModelInstanceType, ExtValueRef)})</li>
	 *         <li>an {@link ObservableModelSet} if the thing is a sub-model (created with
	 *         {@link ObservableModelSet.Builder#createSubModel(String)})</li>
	 *         <li>any other structure that the particular model implementation knows how to deal with</li>
	 *         </ul>
	 */
	Object getThing(String path);

	NameChecker getNameChecker();

	default <M> ValueContainer<M, ?> get(String path, ModelType<M> type) throws QonfigInterpretationException {
		ValueContainer<?, ?> thing = get(path, true);
		if (thing.getType().getModelType() != type)
			throw new QonfigInterpretationException(path + " is a " + thing.getType() + ", not a " + type);
		return (ValueContainer<M, ?>) thing;
	}

	default <M, MV extends M> ValueContainer<M, MV> get(String path, ModelInstanceType<M, MV> type) throws QonfigInterpretationException {
		ValueContainer<Object, Object> thing = (ValueContainer<Object, Object>) get(path, true);
		if (type == null)
			return (ValueContainer<M, MV>) thing;
		return thing.getType().as(thing, type);
	}

	ModelSetInstance createInstance(ExternalModelSet extModel, Observable<?> until);

	default WrappedBuilder wrap() {
		return new DefaultWrapped.DWBuilder(this);
	}

	public static Builder build(NameChecker nameChecker) {
		return new Default.DefaultBuilder(null, null, "", nameChecker);
	}

	public static ExternalModelSetBuilder buildExternal(NameChecker nameChecker) {
		return new ExternalModelSetBuilder(null, "", nameChecker);
	}

	public interface ModelSetInstance {
		ObservableModelSet getModel();

		Object getModelConfiguration();

		Observable<?> getUntil();

		default <M, MV extends M> MV get(String path, ModelInstanceType<M, MV> type) {
			try {
				return getModel().get(path, type).get(this);
			} catch (QonfigInterpretationException e) {
				throw new IllegalArgumentException(e.getMessage(), e);
			}
		}
	}

	public abstract class AbstractMSI implements ModelSetInstance {
		protected abstract Object getThing(ValueContainer<?, ?> placeholder);
	}

	abstract class ModelSetInstanceBuilder extends AbstractMSI {
		public abstract void setModelConfiguration(Object modelConfig);

		public abstract void installThing(ValueContainer<?, ?> placeholder, Object thing);

		public abstract ModelSetInstance build();
	}

	public interface ExternalModelSet {
		NameChecker getNameChecker();

		<M> M get(String path, ModelInstanceType<?, ?> type) throws QonfigInterpretationException;
	}

	public class DefaultExternalModelSet implements ExternalModelSet {
		final DefaultExternalModelSet theRoot;
		private final String thePath;
		final Map<String, Placeholder> theThings;
		/** Checker for model and value names */
		protected final NameChecker theNameChecker;

		DefaultExternalModelSet(DefaultExternalModelSet root, String path, Map<String, Placeholder> things, NameChecker nameChecker) {
			if (!path.isEmpty() && path.charAt(0) == '.')
				BreakpointHere.breakpoint();
			theRoot = root == null ? this : root;
			thePath = path;
			theThings = things;
			theNameChecker = nameChecker;
		}

		public String getPath() {
			return thePath;
		}

		@Override
		public NameChecker getNameChecker() {
			return theNameChecker;
		}

		@Override
		public <M> M get(String path, ModelInstanceType<?, ?> type) throws QonfigInterpretationException {
			int dot = path.lastIndexOf('.');
			if (dot >= 0) {
				DefaultExternalModelSet subModel = theRoot.getSubModel(path.substring(0, dot));
				return subModel.get(path.substring(dot + 1), type);
			}
			theNameChecker.checkName(path);
			Placeholder thing = theThings.get(path);
			if (thing == null)
				throw new QonfigInterpretationException("No such " + type + " declared: '" + pathTo(path) + "'");
			ModelType.ModelInstanceConverter<Object, M> converter = (ModelType.ModelInstanceConverter<Object, M>) thing.type.convert(type);
			if (converter == null)
				throw new QonfigInterpretationException("Cannot convert " + path + " (" + thing.type + ") to " + type);
			return converter.convert(thing.thing);
		}

		private DefaultExternalModelSet getSubModel(String path) throws QonfigInterpretationException {
			int dot = path.indexOf('.');
			String modelName;
			if (dot >= 0) {
				modelName = path.substring(0, dot);
			} else
				modelName = path;
			theNameChecker.checkName(modelName);
			Placeholder subModel = theThings.get(modelName);
			if (subModel == null)
				throw new QonfigInterpretationException("No such sub-model declared: '" + pathTo(modelName) + "'");
			else if (subModel.type.getModelType() != ModelTypes.Model)
				throw new QonfigInterpretationException("'" + pathTo(modelName) + "' is a " + subModel.type + ", not a Model");
			if (dot < 0)
				return (DefaultExternalModelSet) subModel.thing;
			else
				return ((DefaultExternalModelSet) subModel.thing).getSubModel(path.substring(dot + 1));
		}

		String pathTo(String name) {
			if (thePath.isEmpty())
				return name;
			else
				return thePath + "." + name;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(thePath);
			print(str, 1);
			return str.toString();
		}

		protected void print(StringBuilder str, int indent) {
			for (Map.Entry<String, Placeholder> thing : theThings.entrySet()) {
				str.append('\n');
				for (int i = 0; i < indent; i++)
					str.append('\t');
				str.append(thing.getKey()).append(": ").append(thing.getValue().type);
				if (thing.getValue().thing instanceof ExternalModelSet)
					((DefaultExternalModelSet) thing.getValue().thing).print(str, indent + 1);
			}
		}

		static class Placeholder {
			final ModelInstanceType<?, ?> type;
			final Object thing;

			Placeholder(ModelInstanceType<?, ?> type, Object thing) {
				this.type = type;
				this.thing = thing;
			}
		}
	}

	public interface ExtValueRef<T> {
		T get(ExternalModelSet extModels);

		T getDefault(ModelSetInstance models);
	}

	public interface ValueGetter<T> {
		T get(ModelSetInstance models);
	}

	public class ExternalModelSetBuilder extends DefaultExternalModelSet {
		ExternalModelSetBuilder(ExternalModelSetBuilder root, String path, NameChecker nameChecker) {
			super(root, path, new LinkedHashMap<>(), nameChecker);
		}

		public <M, MV extends M> ExternalModelSetBuilder with(String name, ModelInstanceType<M, MV> type, MV item)
			throws QonfigInterpretationException {
			theNameChecker.checkName(name);
			if (theThings.containsKey(name))
				throw new QonfigInterpretationException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new DefaultExternalModelSet.Placeholder(type, item));
			return this;
		}

		public ExternalModelSetBuilder withSubModel(String name,
			ExConsumer<ExternalModelSetBuilder, QonfigInterpretationException> modelBuilder) throws QonfigInterpretationException {
			theNameChecker.checkName(name);
			if (theThings.containsKey(name))
				throw new QonfigInterpretationException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			ExternalModelSetBuilder subModel = new ExternalModelSetBuilder((ExternalModelSetBuilder) theRoot, pathTo(name), theNameChecker);
			theThings.put(name, new DefaultExternalModelSet.Placeholder(ModelTypes.Model.instance(), subModel));
			modelBuilder.accept(subModel);
			return this;
		}

		public ExternalModelSetBuilder addSubModel(String name) throws QonfigInterpretationException {
			theNameChecker.checkName(name);
			if (theThings.containsKey(name))
				throw new QonfigInterpretationException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			ExternalModelSetBuilder subModel = new ExternalModelSetBuilder((ExternalModelSetBuilder) theRoot, pathTo(name), theNameChecker);
			theThings.put(name, new DefaultExternalModelSet.Placeholder(ModelTypes.Model.instance(), subModel));
			return subModel;
		}

		public ExternalModelSet build() {
			return _build(null, getPath());
		}

		private ExternalModelSet _build(DefaultExternalModelSet root, String path) {
			Map<String, DefaultExternalModelSet.Placeholder> things = new LinkedHashMap<>(theThings.size() * 3 / 2 + 1);
			DefaultExternalModelSet model = new DefaultExternalModelSet(root, path, Collections.unmodifiableMap(things), theNameChecker);
			if (root == null)
				root = model;
			for (Map.Entry<String, DefaultExternalModelSet.Placeholder> thing : theThings.entrySet()) {
				if (thing.getValue().type.getModelType() == ModelTypes.Model) {
					String childPath = path.isEmpty() ? thing.getKey() : path + "." + thing.getKey();
					things.put(thing.getKey(), new DefaultExternalModelSet.Placeholder(ModelTypes.Model.instance(), //
						((ExternalModelSetBuilder) thing.getValue().thing)._build(root, childPath)));
				} else
					things.put(thing.getKey(), thing.getValue());
			}
			return model;
		}
	}

	public interface Builder extends ObservableModelSet {
		Builder setModelConfiguration(Function<ModelSetInstance, ?> modelConfiguration);

		<M, MV extends M> Builder with(String name, ModelInstanceType<M, MV> type, ValueGetter<MV> getter);

		<M, MV extends M> Builder withExternal(String name, ModelInstanceType<M, MV> type, ExtValueRef<MV> extGetter);

		default <M, MV extends M> Builder with(String name, ValueContainer<M, MV> value) {
			return with(name, value.getType(), msi -> value.get(msi));
		}

		Builder withMaker(String name, ValueCreator<?, ?> maker);

		Builder createSubModel(String name);

		ObservableModelSet build();
	}

	public class Default implements ObservableModelSet {
		private final Default theParent;
		protected final Default theRoot;
		protected final String thePath;
		protected final Map<String, Placeholder<?, ?>> theThings;
		protected final Function<ModelSetInstance, ?> theModelConfiguration;
		protected final NameChecker theNameChecker;

		protected Default(Default root, Default parent, String path, Map<String, Placeholder<?, ?>> things,
			Function<ModelSetInstance, ?> modifiableConfiguration, NameChecker nameChecker) {
			theRoot = root == null ? this : root;
			theParent = parent;
			thePath = path;
			theThings = things;
			theModelConfiguration = modifiableConfiguration;
			theNameChecker = nameChecker;
		}

		protected Default getRoot() {
			return theRoot;
		}

		Map<String, Placeholder<?, ?>> getThings() {
			return theThings;
		}

		@Override
		public Default getParent() {
			return theParent;
		}

		@Override
		public NameChecker getNameChecker() {
			return theNameChecker;
		}

		@Override
		public Set<String> getContentNames() {
			return Collections.unmodifiableSet(theThings.keySet());
		}

		@Override
		public ValueContainer<?, ?> get(String path, boolean required) throws QonfigInterpretationException {
			Placeholder<?, ?> placeholder = getPlaceholder(path, required);
			return placeholder;
		}

		@Override
		public Object getThing(String path) {
			Placeholder<?, ?> placeholder;
			try {
				placeholder = getPlaceholder(path, false);
			} catch (QonfigInterpretationException e) {
				throw new IllegalStateException("Should not throw anything here", e);
			}
			return placeholder == null ? null : placeholder.getThing();
		}

		private Placeholder<?, ?> getPlaceholder(String path, boolean required) throws QonfigInterpretationException {
			int dot = path.lastIndexOf('.');
			if (dot >= 0) {
				Default subModel = theRoot.getSubModel(path.substring(0, dot), required);
				return subModel == null ? null : subModel.getPlaceholder(path.substring(dot + 1), required);
			}
			theNameChecker.checkName(path);
			Placeholder<?, ?> thing = theThings.get(path);
			if (thing == null && theRoot != this)
				return theRoot.getPlaceholder(path, required);
			if (thing == null && required)
				throw new QonfigInterpretationException("No such value " + path);
			return thing;
		}

		private Default getSubModel(String path, boolean required) throws QonfigInterpretationException {
			int dot = path.indexOf('.');
			String modelName;
			if (dot >= 0) {
				modelName = path.substring(0, dot);
			} else
				modelName = path;
			theNameChecker.checkName(modelName);
			Placeholder<?, ?> subModel = theThings.get(modelName);
			if (subModel == null) {
				if (required)
					throw new QonfigInterpretationException("No such sub-model declared: '" + pathTo(modelName) + "'");
				else
					return null;
			} else if (subModel.getType().getModelType() != ModelTypes.Model)
				throw new QonfigInterpretationException("'" + pathTo(modelName) + "' is a " + subModel.getType() + ", not a Model");
			if (dot < 0)
				return subModel.getModel();
			else
				return subModel.getModel().getSubModel(path.substring(dot + 1), required);
		}

		@Override
		public String pathTo(String name) {
			if (thePath.isEmpty())
				return name;
			else
				return thePath + "." + name;
		}

		@Override
		public String getPath() {
			return thePath;
		}

		@Override
		public ModelSetInstance createInstance(ExternalModelSet extModel, Observable<?> until) {
			if (until == null)
				until = Observable.empty();
			DefaultModelSetInstanceBuilder modelSet = new DefaultModelSetInstanceBuilder(this, until);
			install(modelSet, extModel);
			return modelSet.build();
		}

		protected void install(ModelSetInstanceBuilder modelSet, ExternalModelSet extModel) {
			Object modelConfig = theModelConfiguration == null ? null : theModelConfiguration.apply(modelSet);
			modelSet.setModelConfiguration(modelConfig);
			for (Placeholder<?, ?> thing : theThings.values()) {
				if (thing.getModel() != null) {
					thing.getModel().install(modelSet, extModel);
					modelSet.setModelConfiguration(modelConfig);
				} else
					modelSet.installThing(thing, thing.get(modelSet, extModel));
			}
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append(thePath.isEmpty() ? "<root>" : thePath);
			print(str, 1);
			return str.toString();
		}

		protected void print(StringBuilder str, int indent) {
			for (Map.Entry<String, Placeholder<?, ?>> thing : theThings.entrySet()) {
				str.append('\n');
				for (int i = 0; i < indent; i++)
					str.append('\t');
				str.append(thing.getKey()).append(": ").append(thing.getValue().getType());
				if (thing.getValue().getModel() != null)
					thing.getValue().getModel().print(str, indent + 1);
			}
		}

		protected interface Placeholder<M, MV extends M> extends ValueContainer<M, MV> {
			String getPath();

			Default getModel();

			MV get(ModelSetInstanceBuilder modelSet, ExternalModelSet extModel);

			Object getThing();
		}

		static class PlaceholderImpl<M, MV extends M> implements Placeholder<M, MV> {
			private final String thePath;
			private final ModelInstanceType<M, MV> theType;
			private final ValueGetter<MV> theGetter;
			private final ExtValueRef<MV> theExtRev;
			private final Default theModel;

			PlaceholderImpl(String path, ModelInstanceType<M, MV> type, ValueGetter<MV> getter, ExtValueRef<MV> extRef, Default model) {
				thePath = path;
				this.theType = type;
				this.theGetter = getter;
				theExtRev = extRef;
				this.theModel = model;
			}

			@Override
			public String getPath() {
				return thePath;
			}

			@Override
			public Default getModel() {
				return theModel;
			}

			@Override
			public MV get(ModelSetInstanceBuilder modelSet, ExternalModelSet extModel) {
				if (theGetter != null)
					return theGetter.get(modelSet);
				else {
					MV value = theExtRev.get(extModel);
					if (value != null)
						return value;
					else
						return theExtRev.getDefault(modelSet);
				}
			}

			@Override
			public ModelInstanceType<M, MV> getType() {
				return theType;
			}

			@Override
			public MV get(ModelSetInstance extModels) {
				return (MV) ((AbstractMSI) extModels).getThing(this);
			}

			@Override
			public Object getThing() {
				if (theGetter != null)
					return theGetter;
				else if (theExtRev != null)
					return theExtRev;
				else
					return theModel;
			}

			@Override
			public String toString() {
				return new StringBuilder(theType.toString()).append('@').append(thePath).toString();
			}
		}

		static class MakerPlaceholderImpl<M, MV extends M> implements Placeholder<M, MV> {
			private final String thePath;
			private final ValueCreator<M, MV> theValueMaker;
			private ValueContainer<M, MV> theValue;
			private final Default theModel;

			public MakerPlaceholderImpl(String path, ValueCreator<M, MV> value, Default model) {
				if (value == null && model == null)
					throw new NullPointerException();
				thePath = path;
				theValueMaker = value;
				theModel = model;
			}

			@Override
			public String getPath() {
				return thePath;
			}

			@Override
			public Default getModel() {
				return theModel;
			}

			@Override
			public ModelInstanceType<M, MV> getType() {
				if (theModel != null)
					return (ModelInstanceType<M, MV>) ModelTypes.Model.instance();
				if (theValue == null) {
					theValue = theValueMaker.createValue();
					if (theValue == null)
						throw new IllegalStateException("Value maker failed to create value");
				}
				return theValue.getType();
			}

			@Override
			public MV get(ModelSetInstanceBuilder modelSet, ExternalModelSet extModel) {
				if (theValue == null) {
					theValue = theValueMaker.createValue();
					if (theValue == null)
						throw new IllegalStateException("Value maker failed to create value");
				}
				return theValue.get(modelSet);
			}

			@Override
			public MV get(ModelSetInstance models) {
				return (MV) ((AbstractMSI) models).getThing(this);
			}

			@Override
			public Object getThing() {
				return theValueMaker;
			}

			@Override
			public String toString() {
				if (theValue != null)
					return new StringBuilder(theValue.getType().toString()).append('@').append(thePath).toString();
				else
					return new StringBuilder("?").append('@').append(thePath).toString();
			}
		}

		protected static class DefaultModelSetInstanceBuilder extends ModelSetInstanceBuilder {
			private final ObservableModelSet theModel;
			private final Map<ValueContainer<?, ?>, Object> theThings;
			private Object theModelConfiguration;
			private final Observable<?> theUntil;

			protected DefaultModelSetInstanceBuilder(ObservableModelSet model, Observable<?> until) {
				theModel = model;
				theThings = new LinkedHashMap<>();
				theUntil = until;
			}

			protected Map<ValueContainer<?, ?>, Object> getThings() {
				return QommonsUtils.unmodifiableCopy(theThings);
			}

			@Override
			public ObservableModelSet getModel() {
				return theModel;
			}

			@Override
			protected Object getThing(ValueContainer<?, ?> placeholder) {
				return theThings.get(placeholder);
			}

			@Override
			public Object getModelConfiguration() {
				return theModelConfiguration;
			}

			@Override
			public void setModelConfiguration(Object modelConfiguration) {
				theModelConfiguration = modelConfiguration;
			}

			@Override
			public Observable<?> getUntil() {
				return theUntil;
			}

			@Override
			public void installThing(ValueContainer<?, ?> placeholder, Object thing) {
				theThings.put(placeholder, thing);
			}

			@Override
			public ModelSetInstance build() {
				return new DefaultModelSetInstance(theModel, getThings(), theUntil);
			}
		}

		protected static class DefaultModelSetInstance extends AbstractMSI {
			private final ObservableModelSet theModel;
			private final Map<ValueContainer<?, ?>, Object> theThings;
			private final Observable<?> theUntil;

			protected DefaultModelSetInstance(ObservableModelSet model, Map<ValueContainer<?, ?>, Object> things, Observable<?> until) {
				theModel = model;
				theThings = things;
				theUntil = until;
			}

			@Override
			public ObservableModelSet getModel() {
				return theModel;
			}

			@Override
			public Object getModelConfiguration() {
				return null;
			}

			@Override
			public Observable<?> getUntil() {
				return theUntil;
			}

			@Override
			protected Object getThing(ValueContainer<?, ?> placeholder) {
				return theThings.get(placeholder);
			}
		}

		protected static class DefaultBuilder extends Default implements Builder {
			private Function<ModelSetInstance, ?> theModelConfigurationCreator;

			protected DefaultBuilder(DefaultBuilder root, DefaultBuilder parent, String path, NameChecker nameChecker) {
				super(root, parent, path, new LinkedHashMap<>(), null, nameChecker);
			}

			@Override
			public Builder setModelConfiguration(Function<ModelSetInstance, ?> modelConfiguration) {
				theModelConfigurationCreator = modelConfiguration;
				return this;
			}

			@Override
			public <M, MV extends M> Builder with(String name, ModelInstanceType<M, MV> type, ValueGetter<MV> getter) {
				theNameChecker.checkName(name);
				if (theThings.containsKey(name))
					throw new IllegalArgumentException(
						"A value of type " + theThings.get(name).getType() + " has already been added as '" + name + "'");
				theThings.put(name, createPlaceholder(pathTo(name), type, getter, null, null));
				return this;
			}

			@Override
			public <M, MV extends M> Builder withExternal(String name, ModelInstanceType<M, MV> type, ExtValueRef<MV> extGetter) {
				theNameChecker.checkName(name);
				if (theThings.containsKey(name))
					throw new IllegalArgumentException(
						"A value of type " + theThings.get(name).getType() + " has already been added as '" + name + "'");
				theThings.put(name, createPlaceholder(pathTo(name), type, null, extGetter, null));
				return this;
			}

			@Override
			public Builder withMaker(String name, ValueCreator<?, ?> maker) {
				theNameChecker.checkName(name);
				if (theThings.containsKey(name))
					throw new IllegalArgumentException(
						"A value of type " + theThings.get(name).getType() + " has already been added as '" + name + "'");
				theThings.put(name, createMakerPlaceholder(pathTo(name), maker, null));
				return this;
			}

			@Override
			public Builder createSubModel(String name) {
				theNameChecker.checkName(name);
				Placeholder<?, ?> thing = theThings.get(name);
				if (thing == null) {
					DefaultBuilder subModel = new DefaultBuilder((DefaultBuilder) theRoot, this, pathTo(name), theNameChecker);
					theThings.put(name, createPlaceholder(pathTo(name), ModelTypes.Model.instance(), null, null, subModel));
					return subModel;
				} else if (thing.getModel() != null)
					return (Builder) thing.getModel();
				else
					throw new IllegalArgumentException("A value of type " + thing.getType() + " has already been added as '" + name + "'");
			}

			protected <M, MV extends M> Placeholder<M, MV> createPlaceholder(String path, ModelInstanceType<M, MV> type,
				ValueGetter<MV> getter, ExtValueRef<MV> extGetter, Default subModel) {
				return new PlaceholderImpl<>(path, type, getter, extGetter, subModel);
			}

			protected <M, MV extends M> Placeholder<M, MV> createExtPlaceholder(String path, ModelInstanceType<M, MV> type,
				ExtValueRef<MV> extGetter, Default subModel) {
				return new PlaceholderImpl<>(path, type, null, extGetter, subModel);
			}

			protected <M, MV extends M> Placeholder<M, MV> createMakerPlaceholder(String path, ValueCreator<M, MV> maker,
				Default subModel) {
				return new MakerPlaceholderImpl<>(path, maker, subModel);
			}

			@Override
			public ObservableModelSet build() {
				return _build(null, null, thePath);
			}

			private Default _build(Default root, Default parent, String path) {
				Map<String, Placeholder<?, ?>> things = new LinkedHashMap<>(theThings.size() * 3 / 2 + 1);
				Default model = create(root, parent, path, things, theModelConfigurationCreator);
				if (root == null)
					root = model;
				for (Map.Entry<String, Placeholder<?, ?>> thing : theThings.entrySet()) {
					if (things.containsKey(thing.getKey())) { // Already create dynamically
					} else if (thing.getValue().getType().getModelType() == ModelTypes.Model)
						things.put(thing.getKey(),
							createPlaceholder(thing.getValue().getPath(), ModelTypes.Model.instance(), null, null,
								((DefaultBuilder) thing.getValue().getModel())._build(root, model, //
									(path.isEmpty() ? "" : path + ".") + thing.getKey())));
					else
						things.put(thing.getKey(), thing.getValue());
				}
				return model;
			}

			protected Default create(Default root, Default parent, String path, Map<String, Placeholder<?, ?>> things,
				Function<ModelSetInstance, ?> modelConfiguration) {
				return new Default(root, parent, path, Collections.unmodifiableMap(things), modelConfiguration, theNameChecker);
			}
		}
	}

	public interface Wrapped extends ObservableModelSet {
		WrappedInstanceBuilder wrap(ModelSetInstance msi);
	}

	public interface RuntimeValuePlaceholder<M, MV extends M> extends ValueContainer<M, MV>, Named {
	}

	public interface WrappedBuilder extends Builder {
		Map<String, RuntimeValuePlaceholder<?, ?>> getRuntimeValues();

		<M, MV extends M> RuntimeValuePlaceholder<M, MV> withRuntimeValue(String name, ModelInstanceType<M, MV> type);

		<M, MV extends M> WrappedBuilder withCustomValue(String name, ValueContainer<M, MV> value);

		@Override
		<M, MV extends M> WrappedBuilder with(String name, ModelInstanceType<M, MV> type, ValueGetter<MV> getter);

		@Override
		<M, MV extends M> WrappedBuilder with(String name, ValueContainer<M, MV> value);

		@Override
		Wrapped build();
	}

	public interface WrappedInstanceBuilder {
		<M, MV extends M> WrappedInstanceBuilder with(RuntimeValuePlaceholder<M, MV> placeholder, MV value);

		<M, MV extends M> WrappedInstanceBuilder withCustom(ValueContainer<M, MV> customValue, MV valueInstance);

		WrappedInstanceBuilder withUntil(Observable<?> until);

		ModelSetInstance build() throws IllegalStateException;
	}

	public class DefaultWrapped extends Default implements Wrapped {
		private final ObservableModelSet theWrapped;
		private final Map<String, RuntimePlaceholderImpl<?, ?>> thePlaceholders;
		private final Map<String, ValueContainer<?, ?>> theCustomValues;
		private final Object theModelId;

		protected DefaultWrapped(DefaultWrapped root, DefaultWrapped parent, String path, Map<String, Placeholder<?, ?>> things,
			ObservableModelSet wrapped, Function<ModelSetInstance, ?> modelConfiguration,
			Map<String, RuntimePlaceholderImpl<?, ?>> placeholders, Map<String, ValueContainer<?, ?>> customValues, Object modelId) {
			super(root, parent, path, things, modelConfiguration, wrapped.getNameChecker());
			theWrapped = wrapped;
			thePlaceholders = placeholders;
			theCustomValues = customValues;
			theModelId = modelId;
		}

		@Override
		public ValueContainer<?, ?> get(String path, boolean required) throws QonfigInterpretationException {
			ValueContainer<?, ?> thing = null;
			if (path.indexOf('.') < 0)
				thing = thePlaceholders.get(path);
			if (thing == null)
				thing = theCustomValues.get(path);
			if (thing == null)
				thing = super.get(path, false);
			if (thing == null)
				thing = theWrapped.get(path, required); // Let the wrapped model throw the exception if not found
			return thing;
		}

		@Override
		public DefaultModelSetInstanceBuilder createInstance(ExternalModelSet extModel, Observable<?> until) {
			throw new UnsupportedOperationException("Not supported for wrapped model set");
		}

		@Override
		public WrappedInstanceBuilder wrap(ModelSetInstance msi) {
			WrappedInstanceBuilderImpl instBuilder = new WrappedInstanceBuilderImpl(this, theModelId, msi, thePlaceholders.values(),
				theCustomValues.values());
			install(instBuilder, buildExternal(theNameChecker).build());
			return instBuilder;
		}

		static class DWBuilder extends Default.DefaultBuilder implements WrappedBuilder {
			private final ObservableModelSet theWrapped;
			private final Map<String, RuntimePlaceholderImpl<?, ?>> thePlaceholders;
			private final Map<String, ValueContainer<?, ?>> theCustomValues;

			public DWBuilder(ObservableModelSet wrapped) {
				super(null, null, "", wrapped.getNameChecker()); // Currently, wrapped models are only supported for the root
				theWrapped = wrapped;
				thePlaceholders = new LinkedHashMap<>();
				theCustomValues = new LinkedHashMap<>();
			}

			@Override
			public Map<String, RuntimeValuePlaceholder<?, ?>> getRuntimeValues() {
				return Collections.unmodifiableMap(thePlaceholders);
			}

			@Override
			public <M, MV extends M> RuntimeValuePlaceholder<M, MV> withRuntimeValue(String name, ModelInstanceType<M, MV> type) {
				checkName(name);
				RuntimePlaceholderImpl<M, MV> placeholder = new RuntimePlaceholderImpl<>(this, name, type);
				thePlaceholders.put(name, placeholder);
				return placeholder;
			}

			@Override
			public <M, MV extends M> WrappedBuilder withCustomValue(String name, ValueContainer<M, MV> value) {
				checkName(name);
				theCustomValues.put(name, value);
				return this;
			}

			@Override
			public <M, MV extends M> WrappedBuilder with(String name, ModelInstanceType<M, MV> type, ValueGetter<MV> getter) {
				super.with(name, type, getter);
				return this;
			}

			@Override
			public <M, MV extends M> WrappedBuilder with(String name, ValueContainer<M, MV> value) {
				super.with(name, value);
				return this;
			}

			private void checkName(String name) {
				theNameChecker.checkName(name);
				if (thePlaceholders.get(name) != null)
					throw new IllegalArgumentException("A placeholder named '" + name + "' is already added");
				else if (theCustomValues.get(name) != null)
					throw new IllegalArgumentException("A custom value named '" + name + "' is already added");
				try {
					if (super.get(name, false) != null)
						throw new IllegalArgumentException("A value named '" + name + "' already exists");
					// Allow overriding
					// if (theWrapped.get(name, false) != null)
					// throw new IllegalArgumentException("A value named '" + name + "' already exists");
				} catch (QonfigInterpretationException e) {
					throw new IllegalStateException(e);
				}
			}

			@Override
			protected <M, MV extends M> Placeholder<M, MV> createPlaceholder(String path, ModelInstanceType<M, MV> type,
				ValueGetter<MV> getter, ExtValueRef<MV> extGetter, Default subModel) {
				return new WrappedPlaceholderImpl<>(path, type, getter, extGetter, subModel, this);
			}

			@Override
			protected <M, MV extends M> Placeholder<M, MV> createMakerPlaceholder(String path, ValueCreator<M, MV> maker,
				Default subModel) {
				return new WrappedMakerPlaceholderImpl<>(path, maker, subModel, this);
			}

			@Override
			public ValueContainer<?, ?> get(String path, boolean required) throws QonfigInterpretationException {
				ValueContainer<?, ?> thing = null;
				if (path.indexOf('.') < 0)
					thing = thePlaceholders.get(path);
				if (thing == null)
					thing = super.get(path, false);
				if (thing == null)
					thing = theWrapped.get(path, required);
				return thing;
			}

			@Override
			public DefaultWrapped build() {
				return (DefaultWrapped) super.build();
			}

			@Override
			protected Default create(Default root, Default parent, String path, Map<String, Default.Placeholder<?, ?>> things,
				Function<ModelSetInstance, ?> modelConfiguration) {
				return new DefaultWrapped((DefaultWrapped) root, (DefaultWrapped) parent, path, things, theWrapped, modelConfiguration,
					QommonsUtils.unmodifiableCopy(thePlaceholders), QommonsUtils.unmodifiableCopy(theCustomValues), this);
			}
		}

		interface WrappedPlaceholder<M, MV extends M> extends Placeholder<M, MV> {
			Object getModelId();
		}

		static class WrappedPlaceholderImpl<M, MV extends M> extends PlaceholderImpl<M, MV> implements WrappedPlaceholder<M, MV> {
			private final Object theModelId;

			WrappedPlaceholderImpl(String path, ModelInstanceType<M, MV> type, ValueGetter<MV> getter, ExtValueRef<MV> extGetter,
				Default model, Object modelId) {
				super(path, type, getter, extGetter, model);
				theModelId = modelId;
			}

			@Override
			public Object getModelId() {
				return theModelId;
			}
		}

		static class WrappedMakerPlaceholderImpl<M, MV extends M> extends MakerPlaceholderImpl<M, MV> implements WrappedPlaceholder<M, MV> {
			private final Object thePMModelId;

			WrappedMakerPlaceholderImpl(String path, ValueCreator<M, MV> value, Default model, Object modelId) {
				super(path, value, model);
				thePMModelId = modelId;
			}

			@Override
			public Object getModelId() {
				return thePMModelId;
			}
		}

		protected static class RuntimePlaceholderImpl<M, MV extends M>
		implements RuntimeValuePlaceholder<M, MV>, WrappedPlaceholder<M, MV> {
			private final Object theModelId;
			private final String theName;
			private final ModelInstanceType<M, MV> theType;

			RuntimePlaceholderImpl(Object modelId, String name, ModelInstanceType<M, MV> type) {
				theModelId = modelId;
				theName = name;
				theType = type;
			}

			@Override
			public ModelInstanceType<M, MV> getType() {
				return theType;
			}

			@Override
			public MV get(ModelSetInstance models) {
				return (MV) ((AbstractMSI) models).getThing(this);
			}

			@Override
			public String getName() {
				return theName;
			}

			@Override
			public String getPath() {
				return theName;
			}

			@Override
			public Object getModelId() {
				return theModelId;
			}

			@Override
			public Default getModel() {
				return null;
			}

			@Override
			public MV get(ModelSetInstanceBuilder modelSet, ExternalModelSet extModel) {
				throw new IllegalStateException("Placeholder " + this + " is unfulfilled");
			}

			@Override
			public Object getThing() {
				return this;
			}

			@Override
			public String toString() {
				return theName + " (" + theType + ")";
			}
		}

		static class WrappedInstanceBuilderImpl extends ModelSetInstanceBuilder implements WrappedInstanceBuilder {
			static Object UNFULFILLED = new Object() {
				@Override
				public String toString() {
					return "UNFULFILLED";
				}
			};

			private final ObservableModelSet.Wrapped theModel;
			private final Object theModelId;
			private final ModelSetInstance theWrapped;
			private final Map<ValueContainer<?, ?>, Object> theValues;
			private List<Observable<?>> theUntils;
			private Object theModelConfig;

			protected WrappedInstanceBuilderImpl(ObservableModelSet.Wrapped model, Object modelId, ModelSetInstance wrapped,
				Collection<RuntimePlaceholderImpl<?, ?>> placeholders, Collection<ValueContainer<?, ?>> customValues) {
				theModel = model;
				theModelId = modelId;
				theWrapped = wrapped;
				theValues = new LinkedHashMap<>();
				for (RuntimePlaceholderImpl<?, ?> placeholder : placeholders)
					theValues.put(placeholder, UNFULFILLED);
				for (ValueContainer<?, ?> value : customValues)
					theValues.put(value, UNFULFILLED);
			}

			@Override
			public ObservableModelSet.Wrapped getModel() {
				return theModel;
			}

			@Override
			public <M, MV extends M> WrappedInstanceBuilder with(RuntimeValuePlaceholder<M, MV> placeholder, MV value) {
				if (!(placeholder instanceof RuntimePlaceholderImpl)
					|| theModelId != ((RuntimePlaceholderImpl<?, ?>) placeholder).getModelId())
					throw new IllegalArgumentException("Unrecognized placeholder: " + placeholder);
				if (!placeholder.getType().getModelType().modelType.isInstance(value))
					throw new IllegalArgumentException(
						"Bad model value type " + value.getClass().getName() + " for placeholder " + placeholder);
				theValues.put(placeholder, value);
				return this;
			}

			@Override
			public <M, MV extends M> WrappedInstanceBuilder withCustom(ValueContainer<M, MV> customValue, MV valueInstance) {
				if (valueInstance == null)
					throw new IllegalArgumentException("Cannot install a null value");
				theValues.put(customValue, valueInstance);
				return this;
			}

			@Override
			public void setModelConfiguration(Object modelConfig) {
				theModelConfig = modelConfig;
			}

			@Override
			public void installThing(ValueContainer<?, ?> placeholder, Object thing) {
				theValues.put(placeholder, thing);
			}

			@Override
			public Object getModelConfiguration() {
				return theModelConfig;
			}

			@Override
			public Observable<?> getUntil() {
				return theWrapped.getUntil();
			}

			@Override
			protected Object getThing(ValueContainer<?, ?> placeholder) {
				ValueContainer<?, ?> root = placeholder;
				if (root instanceof ModelType.ConvertedValue)
					root = ((ModelType.ConvertedValue<?, ?, ?, ?>) root).getSource();
				Object thing = theValues.get(root);
				if (thing == WrappedInstanceBuilderImpl.UNFULFILLED) {
					thing = root.get(this);
					theValues.put(root, thing);
				}
				if (thing != null) {
					if (placeholder instanceof ModelType.ConvertedValue)
						thing = ((ModelType.ConvertedValue<Object, Object, Object, Object>) placeholder).getConverter().convert(thing);
					return thing;
				} else
					return ((AbstractMSI) theWrapped).getThing(placeholder);
			}

			@Override
			public WrappedInstanceBuilder withUntil(Observable<?> until) {
				if (theUntils == null)
					theUntils = new ArrayList<>(3);
				theUntils.add(until);
				return this;
			}

			@Override
			public ModelSetInstance build() throws IllegalStateException {
				for (Map.Entry<ValueContainer<?, ?>, Object> value : theValues.entrySet()) {
					if (value.getValue() == UNFULFILLED && value.getKey() instanceof RuntimePlaceholderImpl)
						throw new IllegalStateException("Placeholder " + value.getKey() + " is unfulfilled");
				}

				Observable<?> until;
				if (theUntils == null)
					until = theWrapped.getUntil();
				else {
					theUntils.add(0, theWrapped.getUntil());
					until = Observable.or(theUntils.toArray(new Observable[theUntils.size()]));
					theUntils.remove(0);
				}
				return new WrappedMSI(theModel, theWrapped, QommonsUtils.unmodifiableCopy(theValues), until);
			}
		}

		static class WrappedMSI extends AbstractMSI {
			private final ObservableModelSet.Wrapped theModel;
			private final ModelSetInstance theWrapped;
			private final Map<ValueContainer<?, ?>, Object> thePlaceholderValues;
			private final Observable<?> theUntil;

			protected WrappedMSI(ObservableModelSet.Wrapped model, ModelSetInstance wrapped,
				Map<ValueContainer<?, ?>, Object> placeholderValues, Observable<?> until) {
				theModel = model;
				theWrapped = wrapped;
				thePlaceholderValues = placeholderValues;
				theUntil = until;
			}

			@Override
			public ObservableModelSet getModel() {
				return theModel;
			}

			@Override
			public Object getModelConfiguration() {
				return theWrapped.getModelConfiguration();
			}

			@Override
			public Observable<?> getUntil() {
				return theUntil;
			}

			@Override
			public <M, MV extends M> MV get(String path, ModelInstanceType<M, MV> type) {
				ValueContainer<M, MV> container;
				try {
					container = theModel.get(path, type);
				} catch (QonfigInterpretationException e) {
					throw new IllegalArgumentException(e.getMessage(), e);
				}
				return (MV) getThing(container);
			}

			@Override
			protected Object getThing(ValueContainer<?, ?> placeholder) {
				ValueContainer<?, ?> root = placeholder;
				if (root instanceof ModelType.ConvertedValue)
					root = ((ModelType.ConvertedValue<?, ?, ?, ?>) root).getSource();
				Object thing = thePlaceholderValues.get(root);
				if (thing == WrappedInstanceBuilderImpl.UNFULFILLED) {
					thing = root.get(this);
					thePlaceholderValues.put(root, thing);
				}
				if (thing != null) {
					if (placeholder instanceof ModelType.ConvertedValue)
						thing = ((ModelType.ConvertedValue<Object, Object, Object, Object>) placeholder).getConverter().convert(thing);
					return thing;
				} else
					return ((AbstractMSI) theWrapped).getThing(placeholder);
			}
		}
	}
}

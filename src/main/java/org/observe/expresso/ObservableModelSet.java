package org.observe.expresso;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.qommons.Identifiable;
import org.qommons.Named;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.collect.BetterList;
import org.qommons.config.CustomValueType;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigParseSession;
import org.qommons.config.QonfigToolkit;
import org.qommons.ex.ExConsumer;

import com.google.common.reflect.TypeToken;

/**
 * <p>
 * An ObservableModelSet is a bag containing definitions for typed, typically observable model values, actions, events, etc.
 * </p>
 * <p>
 * An ObservableModelSet is create with {@link #build(NameChecker)}. The {@link ObservableModelSet.NameChecker} parameter validates the
 * names of all identifiers (e.g. variable names) so that they can be referenced by expressions.
 * </p>
 *
 */
public interface ObservableModelSet extends Identifiable {
	public final class ModelComponentId {
		private final ModelComponentId theRootId;
		private final ModelComponentId theOwnerId;
		private final String theName;
		private final int theHashCode;

		public ModelComponentId(ModelComponentId ownerId, String name) {
			theRootId = ownerId == null ? this : ownerId.theRootId;
			theOwnerId = ownerId;
			theName = name;
			theHashCode = System.identityHashCode(this);
		}

		public ModelComponentId getRootId() {
			return theRootId;
		}

		public ModelComponentId getOwnerId() {
			return theOwnerId;
		}

		public String getName() {
			return theName;
		}

		@Override
		public int hashCode() {
			return theHashCode;
		}

		@Override
		public boolean equals(Object obj) {
			return this == obj;
		}

		public String getPath() {
			if (this == theRootId)
				return "";
			else if (theOwnerId == theRootId)
				return theName;
			else
				return theOwnerId.getPath() + "." + theName;
		}

		@Override
		public String toString() {
			if (theOwnerId != null)
				return theOwnerId + "." + theName;
			else
				return theName;
		}
	}

	public interface ModelComponentNode<M, MV extends M> extends ValueContainer<M, MV>, Identifiable {
		@Override
		ModelComponentId getIdentity();

		ObservableModelSet getModel();

		MV create(ModelSetInstance modelSet, ExternalModelSet extModels);

		Object getThing();
	}

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

	interface ValueContainer<M, MV extends M> {
		ModelInstanceType<M, MV> getType();

		MV get(ModelSetInstance models);

		BetterList<ValueContainer<?, ?>> getCores();

		default <M2, MV2 extends M2> ValueContainer<M2, MV2> map(ModelInstanceType<M2, MV2> type, Function<? super MV, ? extends MV2> map) {
			ValueContainer<M, MV> outer = this;
			return new AbstractValueContainer<M2, MV2>(type) {
				@Override
				public MV2 get(ModelSetInstance models) {
					return map.apply(outer.get(models));
				}

				@Override
				public BetterList<ValueContainer<?, ?>> getCores() {
					return outer.getCores();
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

				@Override
				public BetterList<ValueContainer<?, ?>> getCores() {
					return outer.getCores();
				}
			};
		}

		default ValueContainer<M, MV> wrapModels(Function<ModelSetInstance, ModelSetInstance> modelWrapper) {
			ValueContainer<M, MV> outer = this;
			return new ValueContainer<M, MV>() {
				@Override
				public ModelInstanceType<M, MV> getType() {
					return outer.getType();
				}

				@Override
				public MV get(ModelSetInstance models) {
					return outer.get(modelWrapper.apply(models));
				}

				@Override
				public BetterList<ValueContainer<?, ?>> getCores() {
					return outer.getCores();
				}

				@Override
				public int hashCode() {
					return outer.hashCode();
				}

				@Override
				public boolean equals(Object obj) {
					return outer.equals(obj);
				}

				@Override
				public String toString() {
					return outer.toString();
				}
			};
		}

		static <T> ObservableModelSet.ValueContainer<SettableValue<?>, SettableValue<T>> literal(
			ModelInstanceType<SettableValue<?>, SettableValue<T>> type, T value, String text) {
			return new ObservableModelSet.ValueContainer<SettableValue<?>, SettableValue<T>>() {
				private final SettableValue<T> theValue = ObservableModelSet.literal((TypeToken<T>) type.getType(0), value, text);

				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<T>> getType() {
					return type;
				}

				@Override
				public SettableValue<T> get(ObservableModelSet.ModelSetInstance extModels) {
					return theValue;
				}

				@Override
				public BetterList<ObservableModelSet.ValueContainer<?, ?>> getCores() {
					return BetterList.of(this);
				}
			};
		}

		static <T> ObservableModelSet.ValueContainer<SettableValue<?>, SettableValue<T>> literal(TypeToken<T> type, T value, String text) {
			return new ObservableModelSet.ValueContainer<SettableValue<?>, SettableValue<T>>() {
				private final SettableValue<T> theValue = ObservableModelSet.literal(type, value, text);

				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<T>> getType() {
					return ModelTypes.Value.forType(theValue.getType());
				}

				@Override
				public BetterList<ObservableModelSet.ValueContainer<?, ?>> getCores() {
					return BetterList.of(this);
				}

				@Override
				public SettableValue<T> get(ObservableModelSet.ModelSetInstance models) {
					return theValue;
				}

				@Override
				public String toString() {
					return value.toString();
				}
			};
		}

		static <M, MV extends M> ValueContainer<M, MV> of(ModelInstanceType<M, MV> type, Function<ModelSetInstance, MV> value) {
			class SimpleVC implements ValueContainer<M, MV> {
				@Override
				public ModelInstanceType<M, MV> getType() {
					return type;
				}

				@Override
				public MV get(ModelSetInstance models) {
					return value.apply(models);
				}

				@Override
				public BetterList<ValueContainer<?, ?>> getCores() {
					return BetterList.of(this);
				}

				@Override
				public String toString() {
					return type.toString();
				}
			}
			return new SimpleVC();
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
			return constant(ValueContainer.literal(ModelTypes.Value.forType(type), value, text));
		}
	}

	public interface ExtValueRef<M, MV extends M> {
		ModelInstanceType<M, MV> getType();

		MV get(ExternalModelSet extModels);

		MV getDefault(ModelSetInstance models);

		public static <M, MV extends M> ExtValueRef<M, MV> of(ModelInstanceType<M, MV> type, Function<ExternalModelSet, MV> value) {
			return of(type, value, null);
		}

		public static <M, MV extends M> ExtValueRef<M, MV> of(ModelInstanceType<M, MV> type, Function<ExternalModelSet, MV> value,
			Function<ModelSetInstance, MV> defaultValue) {
			class SimpleEVR implements ExtValueRef<M, MV> {
				@Override
				public ModelInstanceType<M, MV> getType() {
					return type;
				}

				@Override
				public MV get(ExternalModelSet extModels) {
					return value.apply(extModels);
				}

				@Override
				public MV getDefault(ModelSetInstance models) {
					return defaultValue == null ? null : defaultValue.apply(models);
				}

				@Override
				public String toString() {
					return type.toString();
				}
			}
			return new SimpleEVR();
		}
	}

	public interface RuntimeValuePlaceholder<M, MV extends M> extends Named {
		ModelComponentId getIdentity();

		ModelInstanceType<M, MV> getType();
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

	static <M, MV extends M> ValueContainer<M, MV> container(ModelInstanceType<M, MV> type, Function<ModelSetInstance, MV> value) {
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
			public BetterList<ValueContainer<?, ?>> getCores() {
				return BetterList.of(this);
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

	@Override
	ModelComponentId getIdentity();

	ObservableModelSet getParent();

	ObservableModelSet getRoot();

	Map<ModelComponentId, ObservableModelSet> getInheritance();

	default boolean isRelated(ModelComponentId modelId) {
		ModelComponentId root = modelId.getRootId();
		if (root == getIdentity().getRootId())
			return true;
		return getInheritance().containsKey(root);
	}

	/** @return All this model's contents */
	Map<String, ModelComponentNode<?, ?>> getComponents();

	default ModelComponentNode<?, ?> getComponentIfExists(String path) {
		ModelComponentNode<?, ?> node;
		int dot = path.lastIndexOf('.');
		if (dot >= 0) {
			ObservableModelSet subModel = getSubModelIfExists(path.substring(0, dot));
			if (subModel == null)
				return null;
			String name = path.substring(dot + 1);
			node = subModel.getComponentIfExists(name);
		} else {
			node = getComponents().get(path);
			if (node == null && getParent() != null)
				node = getParent().getComponentIfExists(path);
			if (node == null) {
				for (ObservableModelSet inh : getInheritance().values()) {
					node = inh.getComponentIfExists(path);
					if (node != null)
						break;
				}
			}
		}
		return node;
	}

	default ModelComponentNode<?, ?> getComponent(String path) throws QonfigInterpretationException {
		ModelComponentNode<?, ?> node;
		int dot = path.lastIndexOf('.');
		if (dot >= 0) {
			ObservableModelSet subModel = getSubModel(path.substring(0, dot));
			if (subModel == null)
				return null;
			String name = path.substring(dot + 1);
			node = subModel.getComponent(name);
		} else {
			node = getComponents().get(path);
			if (node == null && getParent() != null)
				node = getParent().getComponentIfExists(path);
			if (node == null) {
				for (ObservableModelSet inh : getInheritance().values()) {
					node = inh.getComponentIfExists(path);
					if (node != null)
						break;
				}
			}
		}
		if (node == null)
			throw new QonfigInterpretationException("Nothing at '" + path + "'");
		return node;
	}

	default ObservableModelSet getSubModelIfExists(String path) {
		ObservableModelSet refModel = this;
		String name;
		int dot = path.indexOf('.');
		if (dot >= 0) {
			// First find the model represented by the first path element
			String modelName = path.substring(0, dot);
			ModelComponentNode<?, ?> node = refModel.getComponentIfExists(modelName);
			if (node == null || node.getModel() == null && getParent() != null)
				node = getParent().getComponentIfExists(modelName);
			if (node == null || node.getModel() == null)
				return null;
			refModel = node.getModel();

			StringBuilder modelPath = new StringBuilder(getIdentity().toString()).append('.').append(modelName);
			int lastDot = dot;
			dot = path.indexOf('.', dot + 1);
			while (dot >= 0) {
				modelName = path.substring(0, dot);
				modelPath.append('.').append(modelName);
				node = refModel.getComponentIfExists(modelName);
				if (node == null || node.getModel() == null)
					return null;
				refModel = node.getModel();
				dot = path.indexOf('.', dot + 1);
			}
			name = path.substring(lastDot + 1);
		} else
			name = path;

		ModelComponentNode<?, ?> node = refModel.getComponentIfExists(name);
		if (node == null || node.getModel() == null)
			return null;
		return node.getModel();
	}

	default ObservableModelSet getSubModel(String path) throws QonfigInterpretationException {
		ObservableModelSet refModel = this;
		String name;
		int dot = path.indexOf('.');
		if (dot >= 0) {
			// First find the model represented by the first path element
			String modelName = path.substring(0, dot);
			ModelComponentNode<?, ?> node = refModel.getComponentIfExists(modelName);
			if (node == null || node.getModel() == null && getParent() != null)
				node = getParent().getComponentIfExists(modelName);
			if (node == null)
				throw new QonfigInterpretationException("No such sub-model at '" + modelName + "'");
			if (node == null || node.getModel() == null)
				throw new QonfigInterpretationException("'" + modelName + "' is not a sub-model");
			refModel = node.getModel();

			StringBuilder modelPath = new StringBuilder(getIdentity().toString()).append('.').append(modelName);
			int lastDot = dot;
			dot = path.indexOf('.', dot + 1);
			while (dot >= 0) {
				modelName = path.substring(0, dot);
				modelPath.append('.').append(modelName);
				node = refModel.getComponentIfExists(modelName);
				if (node == null)
					throw new QonfigInterpretationException("No such sub-model at '" + modelPath + "'");
				if (node == null || node.getModel() == null)
					throw new QonfigInterpretationException("'" + modelPath + "' is not a sub-model");
				refModel = node.getModel();
				dot = path.indexOf('.', dot + 1);
			}
			name = path.substring(lastDot + 1);
		} else
			name = path;

		ModelComponentNode<?, ?> node = refModel.getComponentIfExists(name);
		if (node == null)
			throw new QonfigInterpretationException("No such sub-model at '" + path + "'");
		else if (node.getModel() == null)
			throw new QonfigInterpretationException("'" + path + "' is not a sub-model");
		return node.getModel();
	}

	default <M, MV extends M> ValueContainer<M, MV> getValue(String path, ModelInstanceType<M, MV> type)
		throws QonfigInterpretationException {
		ModelComponentNode<Object, Object> node = (ModelComponentNode<Object, Object>) getComponent(path);
		if (node.getModel() != null)
			throw new QonfigInterpretationException("'" + path + "' is a sub-model, not a value");
		return node.getType().as(node, type);
	}

	NameChecker getNameChecker();

	ModelSetInstanceBuilder createInstance(ExternalModelSet extModel, Observable<?> until);

	default ModelSetInstanceBuilder createInstance(Observable<?> until) {
		return createInstance(null, until);
	}

	default Builder wrap(String modelName) {
		return build(modelName, getNameChecker()).withAll(this);
	}

	public static Builder build(String modelName, NameChecker nameChecker) {
		return new DefaultModelSet.DefaultBuilder(new ModelComponentId(null, modelName), null, null, nameChecker);
	}

	public static ExternalModelSetBuilder buildExternal(NameChecker nameChecker) {
		return new ExternalModelSetBuilder(null, "", nameChecker);
	}

	public interface ModelSetInstance {
		ObservableModelSet getModel();

		Object getModelConfiguration();

		Observable<?> getUntil();

		<M, MV extends M> MV get(ModelComponentNode<M, MV> component);
	}

	public interface ModelSetInstanceBuilder {
		<M, MV extends M> ModelSetInstanceBuilder with(RuntimeValuePlaceholder<M, MV> placeholder, MV value);

		ModelSetInstanceBuilder withAll(ModelSetInstance other);

		ModelSetInstance build();
	}

	public interface ExternalModelSet {
		NameChecker getNameChecker();

		ExternalModelSet getSubModel(String path) throws QonfigInterpretationException;

		ExternalModelSet getSubModelIfExists(String path);

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

		@Override
		public DefaultExternalModelSet getSubModel(String path) throws QonfigInterpretationException {
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

		@Override
		public ExternalModelSet getSubModelIfExists(String path) {
			int dot = path.indexOf('.');
			String modelName;
			if (dot >= 0) {
				modelName = path.substring(0, dot);
			} else
				modelName = path;
			theNameChecker.checkName(modelName);
			Placeholder subModel = theThings.get(modelName);
			if (subModel == null)
				return null;
			else if (subModel.type.getModelType() != ModelTypes.Model)
				return null;
			if (dot < 0)
				return (DefaultExternalModelSet) subModel.thing;
			else
				return ((DefaultExternalModelSet) subModel.thing).getSubModelIfExists(path.substring(dot + 1));
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

	public class ExternalModelSetBuilder extends DefaultExternalModelSet {
		ExternalModelSetBuilder(ExternalModelSetBuilder root, String path, NameChecker nameChecker) {
			super(root, path, new LinkedHashMap<>(), nameChecker);
		}

		public <M, MV extends M> ExternalModelSetBuilder with(String name, ModelInstanceType<M, MV> type, MV item)
			throws QonfigInterpretationException {
			if (item == null)
				throw new NullPointerException("Installing null for " + type + "@" + name);
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

		<M, MV extends M> Builder withExternal(String name, ExtValueRef<M, MV> extGetter);

		Builder withMaker(String name, ValueCreator<?, ?> maker);

		<M, MV extends M> RuntimeValuePlaceholder<M, MV> withRuntimeValue(String name, ModelInstanceType<M, MV> type);

		Builder createSubModel(String name);

		Builder withAll(ObservableModelSet other);

		ObservableModelSet build();

		default <M, MV extends M> Builder withExternal(String name, ModelInstanceType<M, MV> type, Function<ExternalModelSet, MV> value) {
			return withExternal(name, ExtValueRef.of(type, value));
		}

		default <M, MV extends M> Builder withExternal(String name, ModelInstanceType<M, MV> type, Function<ExternalModelSet, MV> value,
			Function<ModelSetInstance, MV> defaultValue) {
			return withExternal(name, ExtValueRef.of(type, value, defaultValue));
		}

		default <M, MV extends M> Builder with(String name, ValueContainer<M, MV> value) {
			return withMaker(name, ValueCreator.constant(value));
		}

		default <M, MV extends M> Builder with(String name, ModelInstanceType<M, MV> type, Function<ModelSetInstance, MV> value) {
			return with(name, ValueContainer.of(type, value));
		}
	}

	public class DefaultModelSet implements ObservableModelSet {
		private final ModelComponentId theId;
		private final DefaultModelSet theParent;
		private final Map<ModelComponentId, ObservableModelSet> theInheritance;
		private final DefaultModelSet theRoot;
		protected final Map<String, ModelComponentNode<?, ?>> theComponents;
		protected final Function<ModelSetInstance, ?> theModelConfiguration;
		private final NameChecker theNameChecker;

		protected DefaultModelSet(ModelComponentId id, DefaultModelSet root, DefaultModelSet parent,
			Map<ModelComponentId, ObservableModelSet> inheritance, Map<String, ModelComponentNode<?, ?>> things,
			Function<ModelSetInstance, ?> modelConfiguration, NameChecker nameChecker) {
			theId = id;
			theRoot = root == null ? this : root;
			theInheritance = inheritance;
			theParent = parent;
			theComponents = things;
			theModelConfiguration = modelConfiguration;
			theNameChecker = nameChecker;
		}

		@Override
		public ModelComponentId getIdentity() {
			return theId;
		}

		@Override
		public DefaultModelSet getRoot() {
			return theRoot;
		}

		@Override
		public DefaultModelSet getParent() {
			return theParent;
		}

		@Override
		public Map<ModelComponentId, ObservableModelSet> getInheritance() {
			return theInheritance;
		}

		@Override
		public NameChecker getNameChecker() {
			return theNameChecker;
		}

		@Override
		public Map<String, ModelComponentNode<?, ?>> getComponents() {
			return theComponents;
		}

		@Override
		public ModelSetInstanceBuilder createInstance(ExternalModelSet extModel, Observable<?> until) {
			return new DefaultMSIBuilder(this, extModel, until, theModelConfiguration);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append(theId);
			print(str, 1);
			return str.toString();
		}

		private void print(StringBuilder str, int indent) {
			for (Map.Entry<String, ModelComponentNode<?, ?>> thing : theComponents.entrySet()) {
				str.append('\n');
				for (int i = 0; i < indent; i++)
					str.append('\t');
				str.append(thing.getKey()).append(": ").append(thing.getValue().getType());
				if (thing.getValue().getModel() != null)
					((DefaultModelSet) thing.getValue().getModel()).print(str, indent + 1);
			}
		}

		static class ModelNodeImpl<M, MV extends M> implements ModelComponentNode<M, MV> {
			private final ModelComponentId theId;
			private final ValueCreator<M, MV> theCreator;
			private final RuntimeValuePlaceholder<M, MV> theRuntimePlaceholder;
			private ValueContainer<M, MV> theValue;
			private final ExtValueRef<M, MV> theExtRef;
			private final DefaultModelSet theModel;

			ModelNodeImpl(ModelComponentId id, ValueCreator<M, MV> creator, RuntimeValuePlaceholder<M, MV> runtimePlaceholder,
				ExtValueRef<M, MV> extRef, DefaultModelSet model) {
				theId = id;
				theCreator = creator;
				theRuntimePlaceholder = runtimePlaceholder;
				theExtRef = extRef;
				theModel = model;
			}

			@Override
			public ModelComponentId getIdentity() {
				return theId;
			}

			@Override
			public DefaultModelSet getModel() {
				return theModel;
			}

			@Override
			public ModelInstanceType<M, MV> getType() {
				if (theCreator != null) {
					if (theValue == null) {
						theValue = theCreator.createValue();
						if (theValue == null)
							return null;
					}
					return theValue.getType();
				} else if (theRuntimePlaceholder != null)
					return theRuntimePlaceholder.getType();
				else if (theExtRef != null)
					return theExtRef.getType();
				else if (theModel != null)
					return (ModelInstanceType<M, MV>) ModelTypes.Model.instance();
				else
					throw new IllegalStateException();
			}

			@Override
			public MV get(ModelSetInstance models) {
				return models.get(this);
			}

			@Override
			public MV create(ModelSetInstance modelSet, ExternalModelSet extModels) {
				if (theCreator != null) {
					if (theValue == null) {
						theValue = theCreator.createValue();
						if (theValue == null)
							return null;
					}
					return theValue.get(modelSet);
				} else if (theExtRef != null) {
					MV value = extModels == null ? null : theExtRef.get(extModels);
					if (value == null)
						value = theExtRef.getDefault(modelSet);
					if (value == null) {
						if (extModels == null)
							throw new IllegalArgumentException("No such external model: " + theId.getOwnerId());
						else
							throw new IllegalArgumentException("No such external value specified: " + theId);
					} else
						return value;
				} else
					throw new IllegalStateException(theId + " is not an internal value");
			}

			@Override
			public BetterList<ValueContainer<?, ?>> getCores() {
				return BetterList.of(this);
			}

			@Override
			public Object getThing() {
				if (theCreator != null)
					return theCreator;
				else if (theRuntimePlaceholder != null)
					return theRuntimePlaceholder;
				else if (theExtRef != null)
					return theExtRef;
				else
					return theModel;
			}

			@Override
			public String toString() {
				if (theCreator != null)
					return theCreator + "@" + theId;
				else if (theExtRef != null)
					return "ext:" + theExtRef.getType() + "@" + theId;
				else if (theRuntimePlaceholder != null)
					return "runtime:" + theRuntimePlaceholder.getType() + "@" + theId;
				else
					return theId.toString();
			}
		}

		static class RVPI<M, MV extends M> implements RuntimeValuePlaceholder<M, MV> {
			private final ModelComponentId theId;
			private final ModelInstanceType<M, MV> theType;

			RVPI(ModelComponentId id, ModelInstanceType<M, MV> type) {
				theId = id;
				theType = type;
			}

			@Override
			public ModelComponentId getIdentity() {
				return theId;
			}

			@Override
			public String getName() {
				return theId.getName();
			}

			@Override
			public ModelInstanceType<M, MV> getType() {
				return theType;
			}

			@Override
			public String toString() {
				return theId + ":" + theType.toString();
			}
		}

		public static class DefaultBuilder extends DefaultModelSet implements Builder {
			private Function<ModelSetInstance, ?> theModelConfigurationCreator;

			protected DefaultBuilder(ModelComponentId id, DefaultBuilder root, DefaultBuilder parent, NameChecker nameChecker) {
				super(id, root, parent, new LinkedHashMap<>(), new LinkedHashMap<>(), null, nameChecker);
			}

			@Override
			public Map<String, ModelComponentNode<?, ?>> getComponents() {
				return Collections.unmodifiableMap(super.getComponents());
			}

			@Override
			public Map<ModelComponentId, ObservableModelSet> getInheritance() {
				return Collections.unmodifiableMap(super.getInheritance());
			}

			@Override
			public Builder setModelConfiguration(Function<ModelSetInstance, ?> modelConfiguration) {
				theModelConfigurationCreator = modelConfiguration;
				return this;
			}

			@Override
			public <M, MV extends M> Builder withExternal(String name, ExtValueRef<M, MV> extGetter) {
				getNameChecker().checkName(name);
				if (theComponents.containsKey(name))
					throw new IllegalArgumentException(
						"A value of type " + theComponents.get(name).getType() + " has already been added as '" + name + "'");
				theComponents.put(name, createPlaceholder(new ModelComponentId(getIdentity(), name), null, null, extGetter, null));
				return this;
			}

			@Override
			public Builder withMaker(String name, ValueCreator<?, ?> maker) {
				getNameChecker().checkName(name);
				if (theComponents.containsKey(name))
					throw new IllegalArgumentException(
						"A value of type " + theComponents.get(name).getType() + " has already been added as '" + name + "'");
				theComponents.put(name, createPlaceholder(new ModelComponentId(getIdentity(), name), maker, null, null, null));
				return this;
			}

			@Override
			public <M, MV extends M> RuntimeValuePlaceholder<M, MV> withRuntimeValue(String name, ModelInstanceType<M, MV> type) {
				getNameChecker().checkName(name);
				if (theComponents.containsKey(name))
					throw new IllegalArgumentException(
						"A value of type " + theComponents.get(name).getType() + " has already been added as '" + name + "'");
				ModelComponentId id = new ModelComponentId(getIdentity(), name);
				RVPI<M, MV> rvp = new RVPI<>(id, type);
				theComponents.put(name, createPlaceholder(id, null, rvp, null, null));
				return rvp;
			}

			@Override
			public DefaultBuilder createSubModel(String name) {
				getNameChecker().checkName(name);
				ModelComponentNode<?, ?> thing = theComponents.get(name);
				if (thing == null) {
					DefaultBuilder subModel = new DefaultBuilder(new ModelComponentId(getIdentity(), name), (DefaultBuilder) getRoot(),
						this, getNameChecker());
					theComponents.put(name, createPlaceholder(new ModelComponentId(getIdentity(), name), null, null, null, subModel));
					return subModel;
				} else if (thing.getModel() != null)
					return (DefaultBuilder) thing.getModel();
				else
					throw new IllegalArgumentException("A value of type " + thing.getType() + " has already been added as '" + name + "'");
			}

			@Override
			public Builder withAll(ObservableModelSet other) {
				if (super.getInheritance().put(other.getIdentity().getRootId(), other) != null)
					return this;
				// For each model that other inherits, add an inheritance entry for that model ID mapped to other (if not present)
				for (ModelComponentId subInh : other.getInheritance().keySet())
					super.getInheritance().putIfAbsent(subInh, other);
				// For any sub-models with the same name, this model's sub-model should inherit that of the other
				for (ModelComponentNode<?, ?> component : other.getComponents().values()) {
					if (component.getModel() == null)
						continue;
					createSubModel(component.getIdentity().getName()).withAll(component.getModel());
				}
				return this;
			}

			protected <M, MV extends M> ModelComponentNode<M, MV> createPlaceholder(ModelComponentId componentId,
				ValueCreator<M, MV> getter, RuntimeValuePlaceholder<M, MV> runtimeValue, ExtValueRef<M, MV> extGetter,
				DefaultModelSet subModel) {
				return new ModelNodeImpl<>(componentId, getter, runtimeValue, extGetter, subModel);
			}

			@Override
			public ObservableModelSet build() {
				return _build(null, null);
			}

			private DefaultModelSet _build(DefaultModelSet root, DefaultModelSet parent) {
				Map<String, ModelComponentNode<?, ?>> things = new LinkedHashMap<>(theComponents.size() * 3 / 2 + 1);
				DefaultModelSet model = createModel(root, parent, things, theModelConfigurationCreator);
				if (root == null)
					root = model;
				for (Map.Entry<String, ModelComponentNode<?, ?>> thing : theComponents.entrySet()) {
					if (thing.getValue().getModel() != null) {
						DefaultModelSet subModel = ((DefaultBuilder) thing.getValue().getModel())._build(root, model);
						things.put(thing.getKey(), createPlaceholder(thing.getValue().getIdentity(), null, null, null, subModel));
					} else
						things.put(thing.getKey(), thing.getValue());
				}
				return model;
			}

			protected DefaultModelSet createModel(DefaultModelSet root, DefaultModelSet parent,
				Map<String, ModelComponentNode<?, ?>> things, Function<ModelSetInstance, ?> modelConfiguration) {
				return new DefaultModelSet(getIdentity(), root, parent, QommonsUtils.unmodifiableCopy(super.getInheritance()),
					Collections.unmodifiableMap(things), modelConfiguration, getNameChecker());
			}
		}

		static class DefaultMSIBuilder implements ModelSetInstanceBuilder {
			private final Map<ModelComponentId, Object> theComponents;
			private final Map<ModelComponentId, ModelSetInstance> theInheritance;
			private final Map<RuntimeValuePlaceholder<?, ?>, Boolean> theRuntimeVariables;
			private final DefaultMSI theMSI;

			DefaultMSIBuilder(ObservableModelSet models, ExternalModelSet extModels, Observable<?> until,
				Function<ModelSetInstance, ?> modelConfiguration) {
				theComponents = new HashMap<>();
				theInheritance = new LinkedHashMap<>();
				theRuntimeVariables = new HashMap<>();
				theMSI = new DefaultMSI(models.getRoot(), extModels, until, modelConfiguration, theComponents,
					Collections.unmodifiableMap(theInheritance));
				lookForRuntimeVars(models);
			}

			private void lookForRuntimeVars(ObservableModelSet models) {
				for (ModelComponentNode<?, ?> component : models.getComponents().values()) {
					if (component.getThing() instanceof RuntimeValuePlaceholder
						&& component.getIdentity().getRootId() == theMSI.getModel().getIdentity())
						theRuntimeVariables.put((RuntimeValuePlaceholder<?, ?>) component.getThing(), false);
					else if (component.getModel() != null)
						lookForRuntimeVars(component.getModel());
				}
			}

			@Override
			public <M, MV extends M> ModelSetInstanceBuilder with(RuntimeValuePlaceholder<M, MV> placeholder, MV value) {
				if (null == theRuntimeVariables.computeIfPresent(placeholder, (p, b) -> true))
					throw new IllegalStateException(
						"Runtime value " + placeholder.getName() + " not recognized for model " + theMSI.getModel().getIdentity());
				if (!placeholder.getType().isInstance(value))
					throw new IllegalArgumentException("Cannot satisfy runtime value " + placeholder + " with " + value);
				theComponents.put(placeholder.getIdentity(), value);
				return this;
			}

			@Override
			public ModelSetInstanceBuilder withAll(ModelSetInstance other) {
				if (!theMSI.getModel().getInheritance().containsKey(other.getModel().getIdentity().getRootId())) {
					throw new IllegalArgumentException("Model " + other.getModel().getIdentity() + " is not related to this model ("
						+ theMSI.getModel().getIdentity() + ")");
				}
				if (theInheritance.computeIfAbsent(other.getModel().getIdentity().getRootId(), __ -> other) != other)
					throw new IllegalStateException(
						"An instance of model " + other.getModel().getIdentity().getRootId() + " has already been added to this model");
				for (ModelComponentId modelId : other.getModel().getInheritance().keySet()) {
					if (theInheritance.computeIfAbsent(other.getModel().getIdentity(), __ -> other) != other)
						throw new IllegalStateException("An instance of model " + modelId + ", inherited by "
							+ other.getModel().getIdentity() + " has already been added to this model");
				}
				return this;
			}

			@Override
			public ModelSetInstance build() {
				StringBuilder error = null;
				for (Map.Entry<ModelComponentId, ObservableModelSet> inh : theMSI.getModel().getInheritance().entrySet()) {
					if (!theInheritance.containsKey(inh.getKey())) {
						if (error == null)
							error = new StringBuilder();
						error.append("Inherited model " + inh.getKey() + " not satisfied");
					}
				}
				for (Map.Entry<RuntimeValuePlaceholder<?, ?>, Boolean> rv : theRuntimeVariables.entrySet()) {
					if (!rv.getValue()) {
						if (error == null)
							error = new StringBuilder();
						error.append("Runtime value " + rv.getKey() + " not satisfied");
					}
				}
				if (error != null)
					throw new IllegalStateException(error.toString());
				fulfill(theMSI.getModel());
				theMSI.built();
				return theMSI;
			}

			private void fulfill(ObservableModelSet model) {
				for (ModelComponentNode<?, ?> component : model.getComponents().values()) {
					if (component.getIdentity().getRootId() != theMSI.getModel().getIdentity())
						continue;
					if (component.getModel() != null)
						fulfill(component.getModel());
					else
						theMSI.get(component);
				}
			}
		}

		static class DefaultMSI implements ModelSetInstance {
			private final ObservableModelSet theModel;
			private final ExternalModelSet theExtModels;
			private final Observable<?> theUntil;
			private Function<ModelSetInstance, ?> theModelConfigurationCreator;
			private Object theModelConfiguration;
			protected final Map<ModelComponentId, Object> theComponents;
			private final Map<ModelComponentId, ModelSetInstance> theInheritance;
			private Set<ModelComponentNode<?, ?>> theCircularityDetector;

			protected DefaultMSI(ObservableModelSet models, ExternalModelSet extModels, Observable<?> until,
				Function<ModelSetInstance, ?> configuration, Map<ModelComponentId, Object> components,
				Map<ModelComponentId, ModelSetInstance> inheritance) {
				theModel = models;
				theExtModels = extModels;
				theUntil = until;
				theModelConfigurationCreator = configuration;
				theComponents = components;
				theInheritance = inheritance;
				theCircularityDetector = new LinkedHashSet<>();
			}

			@Override
			public ObservableModelSet getModel() {
				return theModel;
			}

			@Override
			public Object getModelConfiguration() {
				if (theModelConfiguration != null)
					return theModelConfiguration;
				else if (theModelConfigurationCreator == null)
					return null;
				theModelConfiguration = theModelConfigurationCreator.apply(this);
				return theModelConfiguration;
			}

			@Override
			public Observable<?> getUntil() {
				return theUntil;
			}

			@Override
			public <M, MV extends M> MV get(ModelComponentNode<M, MV> component) {
				ModelComponentId rootModelId = component.getIdentity().getRootId();
				if (rootModelId != theModel.getIdentity()) {
					ModelSetInstance inh = theInheritance.get(rootModelId);
					if (inh != null)
						return inh.get(component);
					else if (theModel.getInheritance().containsKey(rootModelId))
						throw new IllegalStateException(
							"Missing inheritance " + rootModelId + ": use ModelSetInstanceBuilder.withAll(ModelSetInstance)");
					else
						throw new IllegalArgumentException("Unrecognized model component: " + component);
				}
				MV thing = (MV) theComponents.get(component.getIdentity());
				if (thing != null)
					return thing;
				else if (theCircularityDetector == null)
					throw new IllegalArgumentException("Unrecognized model component: " + component);
				else if (!theCircularityDetector.add(component))
					throw new IllegalArgumentException(
						"Dynamic value circularity detected: " + StringUtils.print("<-", theCircularityDetector, Object::toString));
				try {
					ExternalModelSet extModel = theExtModels == null ? null
						: theExtModels.getSubModelIfExists(component.getIdentity().getOwnerId().getPath());
					thing = component.create(this, extModel);
					theComponents.put(component.getIdentity(), thing);
				} finally {
					theCircularityDetector.remove(component);
				}
				return thing;
			}

			void built() {
				theCircularityDetector = null;
			}
		}
	}
}

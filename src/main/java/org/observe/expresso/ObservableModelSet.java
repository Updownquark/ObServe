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
import org.qommons.config.QonfigInterpretationException;
import org.qommons.ex.ExConsumer;

import com.google.common.reflect.TypeToken;

/**
 * <p>
 * An ObservableModelSet is a bag containing definitions for typed, typically observable model values, actions, events, etc.
 * </p>
 * <p>
 * An ObservableModelSet is create with {@link #build(String, NameChecker)}. The {@link ObservableModelSet.NameChecker} parameter validates
 * the names of all identifiers (e.g. variable names) so that they can be referenced by expressions.
 * </p>
 */
public interface ObservableModelSet extends Identifiable {
	/** An identifier for a model or model component */
	public final class ModelComponentId {
		private final ModelComponentId theRootId;
		private final ModelComponentId theOwnerId;
		private final String theName;
		private final int theHashCode;

		/**
		 * @param ownerId The component ID of the model that owns this component, or null if this is the identifier of a root model
		 * @param name The name of this component in its model, or the name of the root model
		 */
		public ModelComponentId(ModelComponentId ownerId, String name) {
			theRootId = ownerId == null ? this : ownerId.theRootId;
			theOwnerId = ownerId;
			theName = name;
			theHashCode = System.identityHashCode(this);
		}

		/** @return The component ID of this component's root model */
		public ModelComponentId getRootId() {
			return theRootId;
		}

		/** @return The component ID oif this component's owner model, or null if this is the identifier of a root model */
		public ModelComponentId getOwnerId() {
			return theOwnerId;
		}

		/** @return The name of this component in its model, or the name of the root model */
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

		/** @return The path of this component, relative to the root model */
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

	/**
	 * A holder for a component in an {@link ObservableModelSet}
	 *
	 * @param <M> The model type of the component
	 * @param <MV> The value type of the component
	 */
	public interface ModelComponentNode<M, MV extends M> extends ValueContainer<M, MV>, Identifiable {
		@Override
		ModelComponentId getIdentity();

		/** @return The sub-model held by this component, or null if this component is not a sub-model */
		ObservableModelSet getModel();

		/**
		 * Creates a value for this model component
		 *
		 * @param modelSet The model set instance to create the value for
		 * @param extModels The external model set to retrieve the value from
		 * @return The value to use for this component for the model set instance
		 */
		MV create(ModelSetInstance modelSet, ExternalModelSet extModels);

		/**
		 * @return One of:
		 *         <ul>
		 *         <li>A {@link ValueCreator} if this component represents a model value installed with
		 *         {@link ObservableModelSet.Builder#withMaker(String, ValueCreator)},
		 *         {@link ObservableModelSet.Builder#with(String, ValueContainer)}, or another value method</li>
		 *         <li>A {@link ExtValueRef} if this component represents a placeholder for an external model value installed with
		 *         {@link ObservableModelSet.Builder#withExternal(String, ExtValueRef)} or another external value method</li>
		 *         <li>A {@link RuntimeValuePlaceholder} if this component represents a placeholder for a model value to be
		 *         {@link ModelSetInstanceBuilder#with(RuntimeValuePlaceholder, Object) installed} at runtime, installed with
		 *         {@link ObservableModelSet.Builder#withRuntimeValue(String, ModelInstanceType)}</li>
		 *         <li>An {@link ObservableModelSet} if this component represents a sub-model installed with
		 *         {@link ObservableModelSet.Builder#createSubModel(String)}</li>
		 *         <li>Potentially anything else if this model is extended from the default</li>
		 *         </ul>
		 */
		Object getThing();
	}

	/** Checks names of model components to ensure they are accessible as identifiers from expressions */
	interface NameChecker {
		/**
		 * @param name The identifier name to check
		 * @throws IllegalArgumentException If the given name is illegal as an identifier in the model
		 */
		void checkName(String name) throws IllegalArgumentException;
	}

	/** A {@link NameChecker} that ensures names are accessible as identifiers from java expressions */
	public static class JavaNameChecker implements NameChecker {
		/** The regex pattern java identifiers must match */
		public static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^([a-zA-Z_$][a-zA-Z\\d_$]*)$");
		/** Java reserved key words that cannot be identifiers */
		public static final Set<String> JAVA_KEY_WORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(//
			"abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", //
			"default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", //
			"if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", //
			"package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", //
			"this", "throw", "throws", "transient", "try", "void", "volatile", "while")));

		/**
		 * Static version of {@link #checkName(String)}
		 *
		 * @param name The identifier name to check
		 * @throws IllegalArgumentException If the given name is illegal as a java identifier
		 */
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

	/** Singleton instance of {@link JavaNameChecker} */
	public static final JavaNameChecker JAVA_NAME_CHECKER = new JavaNameChecker();

	/**
	 * Represents a model value that is ready to be, but may not have been, instantiated in a {@link ModelSetInstance}
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 */
	public interface ValueContainer<M, MV extends M> {
		/** @return The type of the value */
		ModelInstanceType<M, MV> getType();

		/**
		 * @param models The model instance set to get the model value for this container from
		 * @return The model value for this container in the given model instance set
		 */
		MV get(ModelSetInstance models);

		/** @return All the self-sufficient containers that compose this value container */
		BetterList<ValueContainer<?, ?>> getCores();

		/**
		 * @param <M2> The model type for the mapped value
		 * @param <MV2> The type for the mapped value
		 * @param type The type for the mapped value
		 * @param map The function to take a value of this container's type and transform it to the target type
		 * @return A ValueContainer that returns this container's value, transformed to the given type
		 */
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

		/**
		 * @param <M2> The model type for the mapped value
		 * @param <MV2> The type for the mapped value
		 * @param type The type for the mapped value
		 * @param map The function to take a value of this container's type and transform it to the target type
		 * @return A ValueContainer that returns this container's value, transformed to the given type
		 */
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

		/**
		 * @param modelWrapper The function to wrap the model instance set passed to {@link #get(ModelSetInstance)}
		 * @return A ValueContainer that is the same as this one, but which uses the given function to wrap the model set instance before
		 *         passing it to this container's {@link #get(ModelSetInstance)} method
		 */
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

		/**
		 * @param <T> The type of the value
		 * @param type The type of the value
		 * @param value The value to wrap
		 * @param text The text to represent the value
		 * @return A ValueContainer that always produces a constant value for the given value
		 */
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

		/**
		 * @param <T> The type of the value
		 * @param type The type of the value
		 * @param value The value to wrap
		 * @param text The text to represent the value
		 * @return A ValueContainer that always produces a constant value for the given value
		 */
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

		/**
		 * @param <M> The model type for the value
		 * @param <MV> The type for the value
		 * @param type The type for the value
		 * @param value Produces the value from a model instance set
		 * @return A value container with the given type, implemented by the given function
		 */
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

	/**
	 * A value added to an {@link ObservableModelSet} via {@link Builder#with(String, ValueContainer)},
	 * {@link Builder#withMaker(String, ValueCreator)}, or another value method
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 */
	public interface ValueCreator<M, MV extends M> {
		/** @return The created value container */
		ValueContainer<M, MV> createValue();

		/**
		 * @param <M> The model type of the value
		 * @param <MV> The type of the value
		 * @param container The value container to return
		 * @return A {@link ValueCreator} that always {@link #createValue() returns} the given container
		 */
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

		/**
		 * @param <T> The type of the value
		 * @param type The type of the value
		 * @param value The value
		 * @param text The text representing the value
		 * @return A {@link ValueCreator} that always returns a value container which produces a constant value for the given value
		 */
		static <T> ValueCreator<SettableValue<?>, SettableValue<T>> literal(TypeToken<T> type, T value, String text) {
			return constant(ValueContainer.literal(ModelTypes.Value.forType(type), value, text));
		}
	}

	/**
	 * A value added to an {@link ObservableModelSet} with {@link Builder#withExternal(String, ExtValueRef)} or another external value
	 * method
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 */
	public interface ExtValueRef<M, MV extends M> {
		/** @return The expected type of the external value */
		ModelInstanceType<M, MV> getType();

		/**
		 * @param extModels The external model set to get the value from
		 * @return The value represented by this reference in the given external model set
		 */
		MV get(ExternalModelSet extModels);

		/**
		 * If no value is supplied in the external model set for this value, this reference may optionally supply a default value. If no
		 * default value is supplied, then an error will be thrown (but not by this method).
		 *
		 * @param models The model set to use to get the default value for this unsatisfied external reference
		 * @return The default value for this external reference, or null if this reference does not specify a default value
		 */
		MV getDefault(ModelSetInstance models);

		/**
		 * @param <M> The model type for the reference
		 * @param <MV> The type for the reference
		 * @param type The type for the reference
		 * @param value Retrieves the external value from the external model set
		 * @return The external reference
		 */
		public static <M, MV extends M> ExtValueRef<M, MV> of(ModelInstanceType<M, MV> type, Function<ExternalModelSet, MV> value) {
			return of(type, value, null);
		}

		/**
		 * @param <M> The model type for the reference
		 * @param <MV> The type for the reference
		 * @param type The type for the reference
		 * @param value Retrieves the external value from the external model set
		 * @param defaultValue Retrieves or constructs a default value for the external reference (may be null)
		 * @return The external reference
		 */
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

	/**
	 * A placeholder returned by {@link Builder#withRuntimeValue(String, ModelInstanceType)}. This placeholder can be used to satisfy the
	 * value in a {@link ModelSetInstance} with {@link ModelSetInstanceBuilder#with(RuntimeValuePlaceholder, Object)}.
	 *
	 * @param <M> The model type of the runtime value
	 * @param <MV> The type of the runtime value
	 */
	public interface RuntimeValuePlaceholder<M, MV extends M> extends Named, Identifiable {
		@Override
		ModelComponentId getIdentity();

		/** @return The declared type of the runtime value */
		ModelInstanceType<M, MV> getType();
	}

	/**
	 * Abstract {@link ValueContainer} implementation
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 */
	public abstract class AbstractValueContainer<M, MV extends M> implements ValueContainer<M, MV> {
		private final ModelInstanceType<M, MV> theType;

		/** @param type The type of the value */
		public AbstractValueContainer(ModelInstanceType<M, MV> type) {
			theType = type;
		}

		@Override
		public ModelInstanceType<M, MV> getType() {
			return theType;
		}
	}

	/**
	 * Simple utility function to produce a literal value
	 *
	 * @param <T> The type of the value
	 * @param type The type of the value
	 * @param value The value
	 * @param text The text to represent the value
	 * @return A SettableValue that is not modifiable
	 */
	public static <T> SettableValue<T> literal(TypeToken<T> type, T value, String text) {
		return SettableValue.asSettable(ObservableValue.of(value), __ -> "Literal value '" + text + "'");
	}

	/**
	 * Simple utility function to produce a literal value
	 *
	 * @param <T> The type of the value
	 * @param value The value
	 * @param text The text to represent the value
	 * @return A SettableValue that is not modifiable
	 */
	public static <T> SettableValue<T> literal(T value, String text) {
		return literal(TypeTokens.get().of((Class<T>) value.getClass()), value, text);
	}

	@Override
	ModelComponentId getIdentity();

	/** @return The parent model that this model is a sub-model of */
	ObservableModelSet getParent();

	/** @return The root model that this model belongs to as itself or a descendant */
	ObservableModelSet getRoot();

	/** @return All model sets (by root ID) that were added to this model with {@link Builder#withAll(ObservableModelSet)} */
	Map<ModelComponentId, ObservableModelSet> getInheritance();

	/**
	 * @param modelId The ID of the model to check
	 * @return Whether this model is the same as, or a descendant of, the given model's root, whether any of its {@link #getInheritance()
	 *         inherited models} are
	 */
	default boolean isRelated(ModelComponentId modelId) {
		ModelComponentId root = modelId.getRootId();
		if (root == getIdentity().getRootId())
			return true;
		return getInheritance().containsKey(root);
	}

	/** @return All this model's contents */
	Map<String, ModelComponentNode<?, ?>> getComponents();

	/**
	 * @param path The dot-separated path of the component to get
	 * @return The node representing the target component, or null if no such component exists accessible to this model
	 */
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

	/**
	 * @param path The dot-separated path of the component to get
	 * @return The node representing the target component
	 * @throws QonfigInterpretationException If no such component exists accessible to this model
	 */
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

	/**
	 * @param path The dot-separated path of the sub-model to get
	 * @return The node representing the target sub-model, or null if no such sub-model exists accessible to this model
	 */
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

	/**
	 * @param path The dot-separated path of the sub-model to get
	 * @return The node representing the target sub-model
	 * @throws QonfigInterpretationException If no such sub-model exists accessible to this model
	 */
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

	/**
	 * @param <M> The model type of the value to get
	 * @param <MV> The type of the value to get
	 * @param path The dot-separated path of the value to get
	 * @param type The type of the value to get
	 * @return The container of the value in this model at the given path, converted to the given type if needed
	 * @throws QonfigInterpretationException If no such value exists accessible at the given path, or if the value at the path could not be
	 *         converted to the target type
	 */
	default <M, MV extends M> ValueContainer<M, MV> getValue(String path, ModelInstanceType<M, MV> type)
		throws QonfigInterpretationException {
		ModelComponentNode<Object, Object> node = (ModelComponentNode<Object, Object>) getComponent(path);
		if (node.getModel() != null)
			throw new QonfigInterpretationException("'" + path + "' is a sub-model, not a value");
		return node.getType().as(node, type);
	}

	/** @return Checks the names of components in this model set to ensure they are accessible from expressions */
	NameChecker getNameChecker();

	/**
	 * Creates a builder for a {@link ModelSetInstance} which will contain values for all the components in this model set
	 *
	 * @param extModel The external model set to satisfy {@link ExtValueRef external value references} installed with
	 *        {@link Builder#withExternal(String, ExtValueRef)}
	 * @param until An observable that fires when the lifetime of the new model instance set expires (or null if the lifetime of the new
	 *        model instance set is to be infinite)
	 * @return A builder for the new instance set
	 */
	ModelSetInstanceBuilder createInstance(ExternalModelSet extModel, Observable<?> until);

	/**
	 * Creates a builder for a {@link ModelSetInstance} which will contain values for all the components in this model set. This builder may
	 * only be used if no {@link ExtValueRef external value references} were installed in this model set with
	 * {@link Builder#withExternal(String, ExtValueRef)}
	 *
	 * @param until An observable that fires when the lifetime of the new model instance set expires (or null if the lifetime of the new
	 *        model instance set is to be infinite)
	 * @return A builder for the new instance set
	 */
	default ModelSetInstanceBuilder createInstance(Observable<?> until) {
		return createInstance(null, until);
	}

	/**
	 * Shorthand for <code>build(modelName, getNameChecker()).withAll(this)</code>
	 *
	 * @param modelName The name for the new model set
	 * @return A builder for a model set containing all of this model's information and potentially more
	 */
	default Builder wrap(String modelName) {
		return build(modelName, getNameChecker()).withAll(this);
	}

	/**
	 * Builds a new {@link ObservableModelSet}
	 *
	 * @param modelName The name for the new root model
	 * @param nameChecker The {@link ObservableModelSet#getNameChecker() name checker} for the new model
	 * @return A builder for the new model set
	 */
	public static Builder build(String modelName, NameChecker nameChecker) {
		return new DefaultModelSet.DefaultBuilder(new ModelComponentId(null, modelName), null, null, nameChecker);
	}

	/**
	 * Builds a new {@link ExternalModelSet}
	 *
	 * @param nameChecker The {@link ObservableModelSet#getNameChecker() name checker} for the new model
	 * @return A builder for the new external model set
	 */
	public static ExternalModelSetBuilder buildExternal(NameChecker nameChecker) {
		return new ExternalModelSetBuilder(null, "", nameChecker);
	}

	/** Builds an {@link ObservableModelSet} */
	public interface Builder extends ObservableModelSet {
		/**
		 * @param modelConfiguration A function to produce the {@link ModelSetInstance#getModelConfiguration() model configuration} for
		 *        instances of this model
		 * @return This builder
		 */
		Builder setModelConfiguration(Function<ModelSetInstance, ?> modelConfiguration);

		/**
		 * Declares a dependency on a value from an {@link ExternalModelSet}
		 *
		 * @param <M> The model type of the value
		 * @param <MV> The type of the value
		 * @param name The name of the value in this model set
		 * @param extGetter The reference to retrieve the value from an {@link ExternalModelSet} passed to
		 *        {@link ObservableModelSet#createInstance(ExternalModelSet, Observable)}
		 * @return This builder
		 */
		<M, MV extends M> Builder withExternal(String name, ExtValueRef<M, MV> extGetter);

		/**
		 * Installs a creator for a model value
		 *
		 * @param name The name of the value in this model set
		 * @param maker The creator to create the model value
		 * @return This builder
		 */
		Builder withMaker(String name, ValueCreator<?, ?> maker);

		/**
		 * Declares a value to be satisfied with {@link ModelSetInstanceBuilder#with(RuntimeValuePlaceholder, Object)}
		 *
		 * @param <M> The model type of the runtime value
		 * @param <MV> The type of the runtime value
		 * @param name The name of the value in this model set
		 * @param type The type of the runtime value
		 * @return This builder
		 */
		<M, MV extends M> RuntimeValuePlaceholder<M, MV> withRuntimeValue(String name, ModelInstanceType<M, MV> type);

		/**
		 * Retrieves or creates a builder for a sub-model under this model set
		 *
		 * @param name The name for the sub-model
		 * @return The builder for the sub-model
		 */
		Builder createSubModel(String name);

		/**
		 * Causes this model to {@link ObservableModelSet#getInheritance() inherit} all of the other model's components. All of the other
		 * model's components will be accessible from this model.
		 *
		 * @param other The model whose components to inherit
		 * @return This builder
		 */
		Builder withAll(ObservableModelSet other);

		/**
		 * Declares a dependency on a value from an {@link ExternalModelSet}
		 *
		 * @param <M> The model type of the value
		 * @param <MV> The type of the value
		 * @param name The name of the value in this model set
		 * @param type The type of the external value
		 * @param value The function to retrieve the value from an {@link ExternalModelSet} passed to
		 *        {@link ObservableModelSet#createInstance(ExternalModelSet, Observable)}
		 * @return This builder
		 */
		default <M, MV extends M> Builder withExternal(String name, ModelInstanceType<M, MV> type, Function<ExternalModelSet, MV> value) {
			return withExternal(name, ExtValueRef.of(type, value));
		}

		/**
		 * Declares a dependency on a value from an {@link ExternalModelSet}
		 *
		 * @param <M> The model type of the value
		 * @param <MV> The type of the value
		 * @param name The name of the value in this model set
		 * @param type The type of the external value
		 * @param value The function to retrieve the value from an {@link ExternalModelSet} passed to
		 *        {@link ObservableModelSet#createInstance(ExternalModelSet, Observable)}
		 * @param defaultValue Produces a default value if there is no such reference in the {@link ExternalModelSet} (may be null)
		 * @return This builder
		 */
		default <M, MV extends M> Builder withExternal(String name, ModelInstanceType<M, MV> type, Function<ExternalModelSet, MV> value,
			Function<ModelSetInstance, MV> defaultValue) {
			return withExternal(name, ExtValueRef.of(type, value, defaultValue));
		}

		/**
		 * Installs a container for a model value
		 *
		 * @param name The name of the value in this model set
		 * @param value The container to create the model value
		 * @return This builder
		 */
		default <M, MV extends M> Builder with(String name, ValueContainer<M, MV> value) {
			return withMaker(name, ValueCreator.constant(value));
		}

		/**
		 * Installs a creator for a model value
		 *
		 * @param name The name of the value in this model set
		 * @param type The type of the new value
		 * @param value The function to create the model value
		 * @return This builder
		 */
		default <M, MV extends M> Builder with(String name, ModelInstanceType<M, MV> type, Function<ModelSetInstance, MV> value) {
			return with(name, ValueContainer.of(type, value));
		}

		/** @return The immutable {@link ObservableModelSet} configured with this builder */
		ObservableModelSet build();
	}

	/** A set of actual values created by the containers declared in an {@link ObservableModelSet} */
	public interface ModelSetInstance {
		/**
		 * @return The model set that this instance was {@link ObservableModelSet#createInstance(ExternalModelSet, Observable) created} for
		 */
		ObservableModelSet getModel();

		/**
		 * An object created for this model instance set by {@link ObservableModelSet.Builder#setModelConfiguration(Function)}
		 *
		 * @return This model set's model configuration object
		 */
		Object getModelConfiguration();

		/** @return An observable that will fire when this model instance set's lifetime expires */
		Observable<?> getUntil();

		/**
		 * @param <M> The model type of the component to get the value for
		 * @param <MV> The type of the component to get the value for
		 * @param component The component to get the value for
		 * @return The model value in this model instance set for the given component
		 */
		<M, MV extends M> MV get(ModelComponentNode<M, MV> component);
	}

	/** Builds a {@link ModelSetInstance} */
	public interface ModelSetInstanceBuilder {
		/**
		 * Satisfies a runtime value declared with {@link ObservableModelSet.Builder#withRuntimeValue(String, ModelInstanceType)}
		 *
		 * @param <M> The model type of the value
		 * @param <MV> The type of the value
		 * @param placeholder The placeholder returned by {@link ObservableModelSet.Builder#withRuntimeValue(String, ModelInstanceType)}
		 *        when the value was declared
		 * @param value The value to install for the runtime placeholder
		 * @return This builder
		 */
		<M, MV extends M> ModelSetInstanceBuilder with(RuntimeValuePlaceholder<M, MV> placeholder, MV value);

		/**
		 * @param other The model instance set created for one of the {@link ObservableModelSet#getInheritance() inherited} models of the
		 *        model that this instance set is being built for
		 * @return This builder
		 */
		ModelSetInstanceBuilder withAll(ModelSetInstance other);

		/** @return The model instance set configured with this builder */
		ModelSetInstance build();
	}

	/**
	 * A set of values supplied to {@link ObservableModelSet#createInstance(ExternalModelSet, Observable)} to satisfy placeholders installed
	 * in the {@link ObservableModelSet} with {@link Builder#withExternal(String, ExtValueRef)} or another external value method
	 */
	public interface ExternalModelSet {
		/**
		 * @return The name checker for the model set
		 * @see ObservableModelSet#getNameChecker()
		 */
		NameChecker getNameChecker();

		/**
		 * @param path The dot-separated path of the sub-model to get
		 * @return The node representing the target sub-model, or null if no such sub-model exists accessible to this model
		 */
		ExternalModelSet getSubModelIfExists(String path);

		/**
		 * @param path The dot-separated path of the sub-model to get
		 * @return The node representing the target sub-model
		 * @throws QonfigInterpretationException If no such sub-model exists accessible to this model
		 */
		ExternalModelSet getSubModel(String path) throws QonfigInterpretationException;

		/**
		 * @param <M> The model type of the value to get
		 * @param <MV> The type of the value to get
		 * @param path The dot-separated path of the value to get
		 * @param type The type of the value to get
		 * @return The value in this model at the given path, converted to the given type if needed
		 * @throws QonfigInterpretationException If no such value exists accessible at the given path, or if the value at the path could not
		 *         be converted to the target type
		 */
		<M, MV extends M> MV getValue(String path, ModelInstanceType<M, MV> type) throws QonfigInterpretationException;
	}

	/** Default {@link ExternalModelSet} implementation */
	public class DefaultExternalModelSet implements ExternalModelSet {
		final DefaultExternalModelSet theRoot;
		private final String thePath;
		final Map<String, Placeholder> theComponents;
		/** Checker for model and value names */
		protected final NameChecker theNameChecker;

		DefaultExternalModelSet(DefaultExternalModelSet root, String path, Map<String, Placeholder> things, NameChecker nameChecker) {
			if (!path.isEmpty() && path.charAt(0) == '.')
				BreakpointHere.breakpoint();
			theRoot = root == null ? this : root;
			thePath = path;
			theComponents = things;
			theNameChecker = nameChecker;
		}

		/** @return This model's path under its root */
		public String getPath() {
			return thePath;
		}

		@Override
		public NameChecker getNameChecker() {
			return theNameChecker;
		}

		@Override
		public <M, MV extends M> MV getValue(String path, ModelInstanceType<M, MV> type) throws QonfigInterpretationException {
			int dot = path.lastIndexOf('.');
			if (dot >= 0) {
				DefaultExternalModelSet subModel = theRoot.getSubModel(path.substring(0, dot));
				return subModel.getValue(path.substring(dot + 1), type);
			}
			theNameChecker.checkName(path);
			Placeholder thing = theComponents.get(path);
			if (thing == null)
				throw new QonfigInterpretationException("No such " + type + " declared: '" + pathTo(path) + "'");
			ModelType.ModelInstanceConverter<Object, M> converter = ((ModelInstanceType<Object, Object>) thing.type).convert(type);
			if (converter == null)
				throw new QonfigInterpretationException("Cannot convert " + path + " (" + thing.type + ") to " + type);
			return (MV) converter.convert(thing.thing);
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
			Placeholder subModel = theComponents.get(modelName);
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
			Placeholder subModel = theComponents.get(modelName);
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

		void print(StringBuilder str, int indent) {
			for (Map.Entry<String, Placeholder> thing : theComponents.entrySet()) {
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

	/** Builds an {@link ExternalModelSet} */
	public class ExternalModelSetBuilder extends DefaultExternalModelSet {
		ExternalModelSetBuilder(ExternalModelSetBuilder root, String path, NameChecker nameChecker) {
			super(root, path, new LinkedHashMap<>(), nameChecker);
		}

		/**
		 * @param <M> The model type of the new value
		 * @param <MV> The type of the new value
		 * @param name The name of the new value in this model
		 * @param type The type of the new value
		 * @param item The new value
		 * @return This builder
		 * @throws QonfigInterpretationException If a name conflict occurs
		 */
		public <M, MV extends M> ExternalModelSetBuilder with(String name, ModelInstanceType<M, MV> type, MV item)
			throws QonfigInterpretationException {
			if (item == null)
				throw new NullPointerException("Installing null for " + type + "@" + name);
			theNameChecker.checkName(name);
			if (theComponents.containsKey(name))
				throw new QonfigInterpretationException(
					"A value of type " + theComponents.get(name).type + " has already been added as '" + name + "'");
			theComponents.put(name, new DefaultExternalModelSet.Placeholder(type, item));
			return this;
		}

		/**
		 * @param name The name for the new sub-model
		 * @param modelBuilder Accepts a {@link ExternalModelSetBuilder} to builder the sub-model
		 * @return This model builder
		 * @throws QonfigInterpretationException If a name conflict occurs
		 */
		public ExternalModelSetBuilder withSubModel(String name,
			ExConsumer<ExternalModelSetBuilder, QonfigInterpretationException> modelBuilder) throws QonfigInterpretationException {
			theNameChecker.checkName(name);
			if (theComponents.containsKey(name))
				throw new QonfigInterpretationException(
					"A value of type " + theComponents.get(name).type + " has already been added as '" + name + "'");
			ExternalModelSetBuilder subModel = new ExternalModelSetBuilder((ExternalModelSetBuilder) theRoot, pathTo(name), theNameChecker);
			theComponents.put(name, new DefaultExternalModelSet.Placeholder(ModelTypes.Model.instance(), subModel));
			modelBuilder.accept(subModel);
			return this;
		}

		/**
		 * @param name The name for the new sub-model
		 * @return an {@link ExternalModelSetBuilder} for the new sub-model
		 * @throws QonfigInterpretationException If a name conflict occurs
		 */
		public ExternalModelSetBuilder addSubModel(String name) throws QonfigInterpretationException {
			theNameChecker.checkName(name);
			if (theComponents.containsKey(name))
				throw new QonfigInterpretationException(
					"A value of type " + theComponents.get(name).type + " has already been added as '" + name + "'");
			ExternalModelSetBuilder subModel = new ExternalModelSetBuilder((ExternalModelSetBuilder) theRoot, pathTo(name), theNameChecker);
			theComponents.put(name, new DefaultExternalModelSet.Placeholder(ModelTypes.Model.instance(), subModel));
			return subModel;
		}

		/** @return An {@link ExternalModelSet} configured with values from this builder */
		public ExternalModelSet build() {
			return _build(null, getPath());
		}

		private ExternalModelSet _build(DefaultExternalModelSet root, String path) {
			Map<String, DefaultExternalModelSet.Placeholder> things = new LinkedHashMap<>(theComponents.size() * 3 / 2 + 1);
			DefaultExternalModelSet model = new DefaultExternalModelSet(root, path, Collections.unmodifiableMap(things), theNameChecker);
			if (root == null)
				root = model;
			for (Map.Entry<String, DefaultExternalModelSet.Placeholder> thing : theComponents.entrySet()) {
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

	/** Default {@link ObservableModelSet} implementation */
	public class DefaultModelSet implements ObservableModelSet {
		private final ModelComponentId theId;
		private final DefaultModelSet theParent;
		private final Map<ModelComponentId, ObservableModelSet> theInheritance;
		private final DefaultModelSet theRoot;
		/** This model's components */
		protected final Map<String, ModelComponentNode<?, ?>> theComponents;
		/** The function to produce the {@link ModelSetInstance#getModelConfiguration() model configuration} for this model */
		protected final Function<ModelSetInstance, ?> theModelConfiguration;
		private final NameChecker theNameChecker;

		/**
		 * @param id The component id for the new model
		 * @param root The root model for the new model, or null if the new model is to be the root
		 * @param parent The parent model for the new model, or null if the new model is to be the root
		 * @param inheritance The new model's {@link ObservableModelSet#getInheritance() inheritance}
		 * @param components The {@link ObservableModelSet#getComponents() components} for the new model
		 * @param modelConfiguration The function to produce {@link ModelSetInstance#getModelConfiguration() model configuration} for the
		 *        new model
		 * @param nameChecker The {@link ObservableModelSet#getNameChecker() name checker} for the new model
		 */
		protected DefaultModelSet(ModelComponentId id, DefaultModelSet root, DefaultModelSet parent,
			Map<ModelComponentId, ObservableModelSet> inheritance, Map<String, ModelComponentNode<?, ?>> components,
			Function<ModelSetInstance, ?> modelConfiguration, NameChecker nameChecker) {
			theId = id;
			theRoot = root == null ? this : root;
			theInheritance = inheritance;
			theParent = parent;
			theComponents = components;
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

		/** {@link Builder} for a {@link DefaultModelSet} */
		public static class DefaultBuilder extends DefaultModelSet implements Builder {
			private Function<ModelSetInstance, ?> theModelConfigurationCreator;

			/**
			 * @param id The component ID for the new model
			 * @param root The root for the new model, or null if this is to be the root
			 * @param parent The parent for the new model, or null if this is to be the root
			 * @param nameChecker The {@link ObservableModelSet#getNameChecker() name checker} for the new model
			 */
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

			/**
			 * Creates a new {@link ModelComponentNode} for a new component in this model
			 *
			 * @param <M> The model type of the component
			 * @param <MV> The type of the component
			 * @param componentId The component ID for the component
			 * @param creator The value creator for the component
			 * @param runtimeValue The runtime value of the component
			 * @param extRef The external value retriever of the component
			 * @param subModel The sub model of the component
			 * @return The new component node
			 */
			protected <M, MV extends M> ModelComponentNode<M, MV> createPlaceholder(ModelComponentId componentId,
				ValueCreator<M, MV> creator, RuntimeValuePlaceholder<M, MV> runtimeValue, ExtValueRef<M, MV> extRef,
				DefaultModelSet subModel) {
				return new ModelNodeImpl<>(componentId, creator, runtimeValue, extRef, subModel);
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

			/**
			 * Creates a {@link DefaultModelSet} from {@link #build()}
			 *
			 * @param root The root model, or null if the new model is to be the root
			 * @param parent The parent model, or null if the new model is to be the root
			 * @param components The component map for the new model
			 * @param modelConfiguration The model configuration producer for the new model
			 * @return The new model
			 */
			protected DefaultModelSet createModel(DefaultModelSet root, DefaultModelSet parent,
				Map<String, ModelComponentNode<?, ?>> components, Function<ModelSetInstance, ?> modelConfiguration) {
				return new DefaultModelSet(getIdentity(), root, parent, QommonsUtils.unmodifiableCopy(super.getInheritance()),
					Collections.unmodifiableMap(components), modelConfiguration, getNameChecker());
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

package org.observe.expresso;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.util.TypeTokens;
import org.qommons.BreakpointHere;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Named;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.collect.BetterList;
import org.qommons.ex.ExBiFunction;
import org.qommons.ex.ExConsumer;
import org.qommons.ex.ExFunction;
import org.qommons.ex.ExSupplier;
import org.qommons.ex.ExceptionHandler;
import org.qommons.io.LocatedFilePosition;

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
	/**
	 * <p>
	 * A value added to an {@link ObservableModelSet} via {@link Builder#with(String, InterpretedValueSynth, LocatedFilePosition)},
	 * {@link Builder#withMaker(String, CompiledModelValue, LocatedFilePosition)}, or another value method.
	 * </p>
	 * <p>
	 * Its role is to create a {@link InterpretedValueSynth} at the moment it is needed. This allows values that depend on each other to be
	 * added to a model in any order.
	 * </p>
	 *
	 * @param <M> The model type of the value
	 */
	public interface CompiledModelValue<M> {
		/**
		 * @param env The compiled environment, which may be needed to interpret the model type for some model structures
		 * @return The model type of the values that this compiled structure creates
		 * @throws ExpressoCompilationException If the model type cannot be evaluated
		 */
		ModelType<M> getModelType(CompiledExpressoEnv env) throws ExpressoCompilationException;

		/**
		 * @param env The environment in which to interpret this model set's values
		 * @return The created value container
		 * @throws ExpressoInterpretationException If this value could not be interpreted
		 */
		InterpretedValueSynth<M, ?> interpret(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		/**
		 * @param <M> The model type of the new value
		 * @param name The name of the value (for {@link Object#toString()})
		 * @param modelType The type of the new value
		 * @param synth The function to create the value synthesizer
		 * @return The new compiled model value
		 */
		static <M> CompiledModelValue<M> of(String name, ModelType<M> modelType,
			ExFunction<InterpretedExpressoEnv, ? extends InterpretedValueSynth<M, ?>, ExpressoInterpretationException> synth) {
			return of(LambdaUtils.constantSupplier(name, name, null), modelType, synth);
		}

		/**
		 * @param <M> The model type of the new value
		 * @param name The name of the value (for {@link Object#toString()})
		 * @param modelType The type of the new value
		 * @param synth The function to create the value synthesizer
		 * @return The new compiled model value
		 */
		static <M> CompiledModelValue<M> of(Supplier<String> name, ModelType<M> modelType,
			ExFunction<InterpretedExpressoEnv, ? extends InterpretedValueSynth<M, ?>, ExpressoInterpretationException> synth) {
			return new CompiledModelValue<M>() {
				@Override
				public ModelType<M> getModelType(CompiledExpressoEnv env) {
					return modelType;
				}

				@Override
				public InterpretedValueSynth<M, ?> interpret(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
					return synth.apply(env);
				}

				@Override
				public String toString() {
					return name.get();
				}
			};
		}

		/**
		 * @param <M> The model type of the value
		 * @param container The value container to return
		 * @return A {@link CompiledModelValue} that always {@link #interpret(InterpretedExpressoEnv) returns} the given container
		 */
		static <M> CompiledModelValue<M> constant(InterpretedValueSynth<M, ?> container) {
			return new CompiledModelValue<M>() {
				@Override
				public ModelType<M> getModelType(CompiledExpressoEnv env) {
					return container.getModelType();
				}

				@Override
				public InterpretedValueSynth<M, ?> interpret(InterpretedExpressoEnv env) {
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
		 * @return A {@link CompiledModelValue} that always returns a value container which produces a constant value for the given value
		 */
		static <T> CompiledModelValue<SettableValue<?>> literal(TypeToken<T> type, T value, String text) {
			return constant(InterpretedValueSynth.literalValue(type, value, text));
		}
	}

	/**
	 * A compiled value for a value that has an identity outside of this model. Such values can be
	 * {@link ObservableModelSet#getIdentifiedComponent(Object) retrieved} by their value ID.
	 *
	 * @param <M> The model type of the value
	 */
	public interface IdentifiableCompiledValue<M> extends CompiledModelValue<M>, Identifiable {}

	/**
	 * A value synthesizer that has been interpreted. It contains instance type information and links to other model values
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 */
	public interface InterpretedValueSynth<M, MV extends M> extends CompiledModelValue<M> {
		/** @return The model type of the values that this interpreted structure creates */
		default ModelType<M> getModelType() {
			return getType().getModelType();
		}

		@Override
		default ModelType<M> getModelType(CompiledExpressoEnv env) {
			return getModelType();
		}

		@Override
		default InterpretedValueSynth<M, ?> interpret(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			return this;
		}

		/** @return The instance type of the values that this interpreted structure creates */
		ModelInstanceType<M, MV> getType();

		/** @return All components of this value synthesizer */
		List<? extends InterpretedValueSynth<?, ?>> getComponents();

		/** @return All the self-sufficient (fundamental) containers that compose this value container */
		default BetterList<InterpretedValueSynth<?, ?>> getCores() {
			List<? extends InterpretedValueSynth<?, ?>> components = getComponents();
			if (components.isEmpty())
				return BetterList.of(this);
			return BetterList.of(components.stream(), v -> v.getCores().stream());
		}

		/**
		 * @return An instantiator for this interpreted model value
		 * @throws ModelInstantiationException If any model values could not be created
		 */
		ModelValueInstantiator<MV> instantiate() throws ModelInstantiationException;

		/**
		 * Converts this value interpretation to another type
		 *
		 * @param <M2> The model type to convert to
		 * @param <MV2> The instance type to convert to
		 * @param type The type to convert to
		 * @param env The interpreted environment which may be needed for the conversion
		 * @param exHandler A handler for type conversion exceptions that may be encountered
		 * @return The converted value
		 * @throws TX If the conversion could not be made
		 */
		default <M2, MV2 extends M2, TX extends Throwable> InterpretedValueSynth<M2, MV2> as(ModelInstanceType<M2, MV2> type,
			InterpretedExpressoEnv env, ExceptionHandler.Single<TypeConversionException, TX> exHandler) throws TX {
			return getType().as(this, type, env, exHandler);
		}

		/**
		 * @param <M2> The model type for the mapped value
		 * @param <MV2> The type for the mapped value
		 * @param type The type for the mapped value
		 * @param map The function to take a value of this container's type and transform it to the target type
		 * @return A ModelValueSynth that returns this container's value, transformed to the given type
		 */
		default <M2, MV2 extends M2> InterpretedValueSynth<M2, MV2> map(ModelInstanceType<M2, MV2> type,
			ExFunction<ModelValueInstantiator<MV>, ModelValueInstantiator<MV2>, ModelInstantiationException> map) {
			return new MappedIVC<>(this, type, map);
		}

		/**
		 * @param <M2> The model type of the new value
		 * @param <MV2> The instance type of the new value
		 * @param type The instance type of the new value
		 * @param map The map to produce values for the new model value from this value
		 * @return The mapped, interpreted model value
		 */
		default <M2, MV2 extends M2> InterpretedValueSynth<M2, MV2> mapValue(ModelInstanceType<M2, MV2> type,
			ExFunction<? super MV, ? extends MV2, ModelInstantiationException> map) {
			return new MappedIVC<>(this, type, LambdaUtils.printableExFn(inst -> inst.map(map), map::toString, map));
		}

		/**
		 * An interpreted value container that is a map of another
		 *
		 * @param <M> The source model type
		 * @param <MV> The source instance type
		 * @param <M2> The target model type
		 * @param <MV2> The target instance type
		 */
		class MappedIVC<M, MV extends M, M2, MV2 extends M2> implements InterpretedValueSynth<M2, MV2> {
			private final InterpretedValueSynth<M, MV> theSource;
			private final ModelInstanceType<M2, MV2> theType;
			private final ExFunction<ModelValueInstantiator<MV>, ModelValueInstantiator<MV2>, ModelInstantiationException> theMap;

			public MappedIVC(InterpretedValueSynth<M, MV> source, ModelInstanceType<M2, MV2> type,
				ExFunction<ModelValueInstantiator<MV>, ModelValueInstantiator<MV2>, ModelInstantiationException> map) {
				theSource = source;
				theType = type;
				theMap = map;
			}

			protected InterpretedValueSynth<M, MV> getSource() {
				return theSource;
			}

			@Override
			public ModelInstanceType<M2, MV2> getType() {
				return theType;
			}

			@Override
			public <M3, MV3 extends M3, TX extends Throwable> InterpretedValueSynth<M3, MV3> as(ModelInstanceType<M3, MV3> type,
				InterpretedExpressoEnv env, ExceptionHandler.Single<TypeConversionException, TX> exHandler) throws TX {
				return InterpretedValueSynth.super.as(type, env, exHandler);
			}

			@Override
			public ModelValueInstantiator<MV2> instantiate() throws ModelInstantiationException {
				return theMap.apply(theSource.instantiate());
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.singletonList(getSource());
			}

			@Override
			public String toString() {
				return theSource + "." + theMap + "(" + getType() + ")";
			}
		}

		/**
		 * @param <T> The type of the value
		 * @param type The type of the value
		 * @param value The value to wrap
		 * @param text The text to represent the value
		 * @return A ModelValueSynth that always produces a constant value for the given value
		 */
		static <T> InterpretedValueSynth<SettableValue<?>, SettableValue<T>> literalValue(TypeToken<T> type, T value, String text) {
			return literal(ModelTypes.Value.forType(type), SettableValue.of(type, value, "Literal"), text);
		}

		/**
		 * @param <M> The model type for the value
		 * @param <MV> The type for the value
		 * @param type The type for the value
		 * @param value Produces the value from a model instance set
		 * @param components The components of the model value
		 * @return A value container with the given type, implemented by the given function
		 */
		static <M, MV extends M> InterpretedValueSynth<M, MV> of(ModelInstanceType<M, MV> type,
			ExSupplier<ModelValueInstantiator<MV>, ModelInstantiationException> value,
			InterpretedValueSynth<?, ?>... components) {
			class SimpleVC implements InterpretedValueSynth<M, MV> {
				@Override
				public ModelType<M> getModelType() {
					return type.getModelType();
				}

				@Override
				public ModelInstanceType<M, MV> getType() {
					return type;
				}

				@Override
				public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
					return QommonsUtils.unmodifiableCopy(components);
				}

				@Override
				public ModelValueInstantiator<MV> instantiate() throws ModelInstantiationException {
					return value.get();
				}

				@Override
				public String toString() {
					return type.toString();
				}
			}
			return new SimpleVC();
		}

		/**
		 * @param <M> The model type for the value
		 * @param <MV> The type for the value
		 * @param type The type for the value
		 * @param value Produces the value from a model instance set
		 * @param components The components of the model value
		 * @return A value container with the given type, implemented by the given function
		 */
		static <M, MV extends M> InterpretedValueSynth<M, MV> simple(ModelInstanceType<M, MV> type, ModelValueInstantiator<MV> value,
			InterpretedValueSynth<?, ?>... components) {
			class SimpleVC implements InterpretedValueSynth<M, MV> {
				@Override
				public ModelType<M> getModelType() {
					return type.getModelType();
				}

				@Override
				public ModelInstanceType<M, MV> getType() {
					return type;
				}

				@Override
				public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
					return QommonsUtils.unmodifiableCopy(components);
				}

				@Override
				public ModelValueInstantiator<MV> instantiate() {
					return value;
				}

				@Override
				public String toString() {
					return type.toString();
				}
			}
			return new SimpleVC();
		}

		/**
		 * @param <M> The model type for the value
		 * @param <MV> The type for the value
		 * @param type The type for the value
		 * @param value Produces the value from a model instance set
		 * @param text The text for the literal
		 * @param components The components of the model value
		 * @return A value container with the given type, implemented by the given function
		 */
		static <M, MV extends M> InterpretedValueSynth<M, MV> literal(ModelInstanceType<M, MV> type, MV value, String text,
			InterpretedValueSynth<?, ?>... components) {
			class SimpleVC implements InterpretedValueSynth<M, MV> {
				@Override
				public ModelType<M> getModelType() {
					return type.getModelType();
				}

				@Override
				public ModelInstanceType<M, MV> getType() {
					return type;
				}

				@Override
				public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
					return QommonsUtils.unmodifiableCopy(components);
				}

				@Override
				public ModelValueInstantiator<MV> instantiate() {
					return ModelValueInstantiator.literal(value, text);
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
	 * Instantiates actual values for a model value
	 *
	 * @param <MV> The type of values that this instantiator produces
	 */
	public interface ModelValueInstantiator<MV> {
		/**
		 * Must be called once on this object after it is created. Initializes internal structures.
		 *
		 * @throws ModelInstantiationException If any internal structures fail to initialize
		 */
		void instantiate() throws ModelInstantiationException;

		/**
		 * @param models The model instance set to get the model value for this container from
		 * @return The model value for this container in the given model instance set
		 * @throws ModelInstantiationException If this value could not be instantiated
		 * @throws IllegalStateException If this value is a part of a dependency cycle, depending on values that depend upon it
		 */
		MV get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException;

		/**
		 * @param value The value to copy
		 * @param sourceModels The model instance that the value is from
		 * @param newModels The new model to copy the value into
		 * @return A copy of the given value for the new model instance. Often this will be exactly the source <code>value</code>, but if
		 *         the value is a composite of values that have been replaced in the target models, the return value will be a re-built
		 *         composite using the copied components.
		 * @throws ModelInstantiationException If the value could not be copied
		 */
		MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException;

		/**
		 * @param <MV2> The type of the mapped instantiator
		 * @param map The function to produce values for the new instantiator from this instantiator's values
		 * @return The mapped instatiator
		 */
		default <MV2> ModelValueInstantiator<MV2> map(ExFunction<? super MV, ? extends MV2, ModelInstantiationException> map) {
			return map(LambdaUtils.printableExBiFn((src, msi) -> map.apply(src), map::toString, map));
		}

		/**
		 * @param <MV2> The type of the mapped instantiator
		 * @param map The function to produce values for the new instantiator from this instantiator's values and the
		 *        {@link ModelSetInstance} it was created with
		 * @return The mapped instatiator
		 */
		default <MV2> ModelValueInstantiator<MV2> map(
			ExBiFunction<? super MV, ModelSetInstance, ? extends MV2, ModelInstantiationException> map) {
			return new MappedMVI<>(this, map);
		}

		/**
		 * Implements {@link ModelValueInstantiator#map(ExBiFunction)}
		 *
		 * @param <MV> The type of the source instantiator
		 * @param <MV2> The type of this instantiator
		 */
		class MappedMVI<MV, MV2> implements ModelValueInstantiator<MV2> {
			private final ModelValueInstantiator<MV> theSource;
			private final ExBiFunction<? super MV, ModelSetInstance, ? extends MV2, ModelInstantiationException> theMap;

			public MappedMVI(ModelValueInstantiator<MV> source,
				ExBiFunction<? super MV, ModelSetInstance, ? extends MV2, ModelInstantiationException> map) {
				theSource = source;
				theMap = map;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theSource.instantiate();
			}

			@Override
			public MV2 get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				MV sourceV = theSource.get(models);
				return theMap.apply(sourceV, models);
			}

			@Override
			public MV2 forModelCopy(MV2 value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				MV sourceV = theSource.get(sourceModels);
				MV sourceCopy = theSource.forModelCopy(sourceV, sourceModels, newModels);
				if (sourceCopy == sourceV)
					return value;
				return theMap.apply(sourceCopy, newModels);
			}

			@Override
			public String toString() {
				return theSource + "." + theMap;
			}
		}

		/**
		 * @param <MV> The type of the instantiator
		 * @param value The value to return from {@link #get(ModelSetInstance)}
		 * @param text The text to represent the literal
		 * @return The literal instantiator
		 */
		static <MV> ModelValueInstantiator<MV> literal(MV value, String text) {
			return new ModelValueInstantiator<MV>() {
				@Override
				public void instantiate() {}

				@Override
				public MV get(ObservableModelSet.ModelSetInstance extModels) {
					return value;
				}

				@Override
				public MV forModelCopy(MV value2, ModelSetInstance sourceModels, ModelSetInstance newModels) {
					return value2;
				}

				@Override
				public String toString() {
					return text;
				}
			};
		}

		/**
		 * @param <MV> The type for the value
		 * @param type The type for the value
		 * @param value Produces the value from a model instance set
		 * @param components The components of the model value
		 * @return A value container with the given type, implemented by the given function
		 */
		static <MV> ModelValueInstantiator<MV> of(ExFunction<ModelSetInstance, MV, ModelInstantiationException> value) {
			return new ModelValueInstantiator<MV>() {
				@Override
				public void instantiate() {}

				@Override
				public MV get(ModelSetInstance models) throws ModelInstantiationException {
					return value.apply(models);
				}

				@Override
				public MV forModelCopy(MV value2, ModelSetInstance sourceModels, ModelSetInstance newModels)
					throws ModelInstantiationException {
					return value.apply(newModels);
				}

				@Override
				public String toString() {
					return value.toString();
				}
			};
		}
	}

	/** An identifier for a model or model component */
	public final class ModelComponentId {
		private final ModelComponentId theRootId;
		private final ModelComponentId theOwnerId;
		private final String theName;
		private final int theDepth;
		private final int theHashCode;

		/**
		 * @param ownerId The component ID of the model that owns this component, or null if this is the identifier of a root model
		 * @param name The name of this component in its model, or the name of the root model
		 */
		public ModelComponentId(ModelComponentId ownerId, String name) {
			theRootId = ownerId == null ? this : ownerId.theRootId;
			theOwnerId = ownerId;
			theName = name;
			if (ownerId == null)
				theDepth = 0;
			else
				theDepth = ownerId.theDepth + 1;
			theHashCode = System.identityHashCode(this);
		}

		/** @return The component ID of this component's root model */
		public ModelComponentId getRootId() {
			return theRootId;
		}

		/** @return The component ID of this component's owner model, or null if this is the identifier of a root model */
		public ModelComponentId getOwnerId() {
			return theOwnerId;
		}

		/** @return The name of this component in its model, or the name of the root model */
		public String getName() {
			return theName;
		}

		/** @return Zero if this is the root node, otherwise the sub-model depth below the root node of this component */
		public int getDepth() {
			return theDepth;
		}

		/**
		 * @param includeRoot Whether to include the root at the start of the path
		 * @param includeSelf Whether to include this node's name at the end of the path
		 * @return The path of this component ID below its root
		 */
		public List<String> getPath(boolean includeRoot, boolean includeSelf) {
			int size = theDepth;
			if (includeRoot)
				size++;
			if (!includeSelf)
				size--;
			if (size <= 0) // If !includeRoot and !includeSelf and we're the root, this will be -1
				return Collections.emptyList();
			String[] path = new String[size];
			ModelComponentId node = includeSelf ? this : theOwnerId;
			for (int i = path.length - 1; i >= 0; i--) {
				path[i] = node.theName;
				node = node.theOwnerId;
			}
			return Arrays.asList(path);
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
	 */
	public interface ModelComponentNode<M> extends CompiledModelValue<M>, Identifiable {
		@Override
		ModelComponentId getIdentity();

		/** @return The {@link IdentifiableCompiledValue#getIdentity() identity} of the value itself, independent of this model */
		Object getValueIdentity();

		/** @return The sub-model held by this component, or null if this component is not a sub-model */
		ObservableModelSet getModel();

		/**
		 * @return One of:
		 *         <ul>
		 *         <li>A {@link CompiledModelValue} if this component represents a model value installed with
		 *         {@link ObservableModelSet.Builder#withMaker(String, CompiledModelValue, LocatedFilePosition)},
		 *         {@link ObservableModelSet.Builder#with(String, InterpretedValueSynth, LocatedFilePosition)}, or another value method</li>
		 *         <li>A {@link ExtValueRef} if this component represents a placeholder for an external model value installed with
		 *         {@link ObservableModelSet.Builder#withExternal(String, ExtValueRef, LocatedFilePosition)} or another external value
		 *         method</li>
		 *         <li>An {@link ObservableModelSet} if this component represents a sub-model installed with
		 *         {@link ObservableModelSet.Builder#createSubModel(String, LocatedFilePosition)}</li>
		 *         <li>Potentially anything else if this model is extended from the default</li>
		 *         </ul>
		 */
		Object getThing();

		/** @return The location where this value was declared in its source file. May be null. */
		LocatedFilePosition getSourceLocation();

		@Override
		InterpretedModelComponentNode<M, ?> interpret(InterpretedExpressoEnv env) throws ExpressoInterpretationException;
	}

	/**
	 * <p>
	 * A placeholder in-between a {@link ModelComponentNode} and an {@link InterpretableModelComponentNode}. A node in this state <i>may</i>
	 * have been interpreted, but may not have been. Its {@link #interpret(InterpretedExpressoEnv)} or {@link #interpreted()} methods return
	 * an {@link InterpretableModelComponentNode} which has been interpreted.
	 * </p>
	 * <p>
	 * This additional layer between compiled and interpreted is necessary because the interpretation of a value may require the
	 * interpretation of another value, and so on. Facilitating this graph resolution requires a structure containing both interpreted and
	 * not-yet-interpreted model values.
	 * </p>
	 *
	 * @param <M> The model type of the value
	 */
	public interface InterpretableModelComponentNode<M> extends ModelComponentNode<M> {
		@Override
		InterpretedModelSet getModel();

		/**
		 * @return The interpreted model node, interpreted using the environment passed to
		 *         {@link Built#createInterpreted(InterpretedExpressoEnv)}
		 * @throws ExpressoInterpretationException If this value could not be interpreted
		 */
		InterpretedModelComponentNode<M, ?> interpreted() throws ExpressoInterpretationException;
	}

	/**
	 * A {@link ModelComponentNode} that has been {@link InterpretedValueSynth interpreted}
	 *
	 * @param <M> The model type of this node
	 * @param <MV> The instance type of this node
	 */
	public interface InterpretedModelComponentNode<M, MV extends M>
	extends InterpretableModelComponentNode<M>, InterpretedValueSynth<M, MV> {
		@Override
		ModelType<M> getModelType();

		@Override
		default InterpretedModelComponentNode<M, ?> interpret(InterpretedExpressoEnv env) {
			return this;
		}

		@Override
		InterpretedModelSet getModel();

		/** @return The interpreted value evaluated from this node */
		InterpretedValueSynth<M, MV> getValue();

		/**
		 * Identical to {@link ObservableModelSet.ModelComponentNode#getThing()}, except that if the component is a sub-model installed with
		 * {@link ObservableModelSet.Builder#createSubModel(String, LocatedFilePosition)}, the value will also be an instance of
		 * {@link InterpretedModelSet}.
		 */
		@Override
		Object getThing();

		@Override
		default InterpretedModelComponentNode<M, ?> interpreted() {
			return this;
		}
	}

	/**
	 * An instantiator for an {@link InterpretableModelComponentNode}
	 *
	 * @param <MV> The type of this instantiator
	 */
	public interface ModelComponentInstantiator<MV> extends ModelValueInstantiator<MV>, Identifiable {
		@Override
		ModelComponentId getIdentity();

		/**
		 * Creates a value for this model component
		 *
		 * @param modelSet The model set instance to create the value for
		 * @return The value to use for this component for the model set instance
		 * @throws ModelInstantiationException If this node's model value could not be created
		 */
		MV create(ModelSetInstance modelSet) throws ModelInstantiationException;
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
	 * An external value reference in an {@link ObservableModelSet}. These are installed using
	 * {@link Builder#withExternal(String, ExtValueRef, LocatedFilePosition)}, and upon {@link InterpretableModelComponentNode#interpreted()
	 * interpretation}, the reference is evaluated, typically by resolving the reference to a model value from the
	 * {@link InterpretedExpressoEnv environment}'s {@link InterpretedExpressoEnv#getExtModels() external models}.
	 *
	 * @param <M> The model type of the external value reference
	 */
	public interface ExtValueRef<M> {
		/**
		 * @return The model type of the external value. Unlike {@link CompiledModelValue}s, {@link ExtValueRef}s must know their model type
		 *         upon declaration
		 */
		ModelType<M> getModelType();

		/**
		 * @param env The interpreted environment in which to evaluate the external reference
		 * @return The instance type of the external value in the environment
		 * @throws ExpressoInterpretationException If the external reference could not be resolved
		 */
		ModelInstanceType<M, ?> getType(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		/**
		 * @param env The interpreted environment in which to evaluate the external reference
		 * @return The interpretation of the external value reference in the environment
		 * @throws ExpressoInterpretationException If the external reference could not be resolved
		 */
		InterpretedValueSynth<M, ?> createSynthesizer(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		/** @return Whether this value reference has a default value that shall be used if the external value is missing */
		boolean hasDefault();

		/** @return This reference's location in its source file */
		LocatedFilePosition getFilePosition();

		/**
		 * Abstract {@link ExtValueRef} implementation
		 *
		 * @param <M> The model type of the external value reference
		 */
		public static abstract class Abstract<M> implements ExtValueRef<M> {
			private final ModelType<M> theModelType;
			private final CompiledModelValue<M> theDefault;

			/**
			 * @param modelType The model type of the reference
			 * @param def The default value to use if the reference is not satisfied (may be null)
			 */
			protected Abstract(ModelType<M> modelType, CompiledModelValue<M> def) {
				theModelType = modelType;
				theDefault = def;
			}

			/** @return The model path to look for the external value in */
			protected abstract String getModelPath();

			@Override
			public ModelType<M> getModelType() {
				return theModelType;
			}

			/** @return The default value to use if the reference is not satisfied */
			protected CompiledModelValue<M> getDefault() {
				return theDefault;
			}

			@Override
			public InterpretedValueSynth<M, ?> createSynthesizer(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				ModelInstanceType<M, ?> type = getType(env);
				try {
					M value = env.getExtModels().getValue(getModelPath(), type, env);
					return InterpretedValueSynth.literal((ModelInstanceType<M, M>) type, value, getModelPath());
				} catch (ModelException e) {
					// No such model--use the default if present
					if (theDefault != null)
						return theDefault.interpret(env);
					else
						throw new ExpressoInterpretationException(e.getMessage(), getFilePosition(), 0, e);
				} catch (TypeConversionException e) {
					throw new ExpressoInterpretationException(e.getMessage(), getFilePosition(), 0, e);
				} catch (ModelInstantiationException e) {
					throw new ExpressoInterpretationException(e.getMessage(), e.getPosition(), e.getErrorLength(), e);
				}
			}

			@Override
			public boolean hasDefault() {
				return theDefault != null;
			}

			@Override
			public String toString() {
				return getModelPath();
			}
		}
	}

	/**
	 * A tag on a model set. This tag is not used by the {@link ObservableModelSet.InterpretedModelSet#createInstance(Observable) instance},
	 * but just serves as a marker on the model set itself.
	 *
	 * @param <T> The type of values for the tag
	 */
	public interface ModelTag<T> extends Named {
		/** @return The type of values that the tag may have */
		TypeToken<T> getType();

		/**
		 * Creates a model tag
		 *
		 * @param <T> The type of the tag
		 * @param name The name of the tag
		 * @param type The type of the tag
		 * @return The new tag
		 */
		public static <T> ModelTag<T> of(String name, TypeToken<T> type) {
			return new ModelTag<T>() {
				@Override
				public String getName() {
					return name;
				}

				@Override
				public TypeToken<T> getType() {
					return type;
				}

				@Override
				public String toString() {
					return name + "(" + type + ")";
				}
			};
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
		return SettableValue.asSettable(ObservableValue.of(type, value), __ -> "Literal value '" + text + "'");
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

	/**
	 * @param <T> The type of the tag
	 * @param tag The model tag to get the value of
	 * @return The value for the given tag in this model set
	 */
	<T> T getLocalTagValue(ModelTag<T> tag);

	/**
	 * @param <T> The type of the tag
	 * @param tag The model tag to get the value of
	 * @return The value for the given tag in this model set or any of its {@link #getInheritance() inherited} models
	 */
	default <T> T getTagValue(ModelTag<T> tag) {
		T value = getLocalTagValue(tag);
		if (value == null) {
			for (ObservableModelSet inh : getInheritance().values()) {
				value = inh.getTagValue(tag);
				if (value != null)
					break;
			}
		}
		if (value == null && getParent() != null)
			value = getParent().getTagValue(tag);
		return value;
	}

	/** @return All model sets (by ID) that were added to this model with {@link Builder#withAll(ObservableModelSet)} */
	Map<ModelComponentId, ? extends ObservableModelSet> getInheritance();

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

	/** @return The names of all this model's components */
	Set<String> getComponentNames();

	/**
	 * @param name The name of the component to get
	 * @return The node representing the target component, or null if no such component exists in this model
	 */
	ModelComponentNode<?> getLocalComponent(String name);

	/**
	 * @param path The dot-separated path of the component to get
	 * @return The node representing the target component, or null if no such component exists accessible to this model
	 */
	default ModelComponentNode<?> getComponentIfExists(String path) {
		return getComponentIfExists(path, true);
	}

	/**
	 * @param path The dot-separated path of the component to get
	 * @param inheritFromParent Whether values should be inherited from parent models by simple name
	 * @return The node representing the target component, or null if no such component exists accessible to this model
	 */
	default ModelComponentNode<?> getComponentIfExists(String path, boolean inheritFromParent) {
		ModelComponentNode<?> node;
		int dot = path.lastIndexOf('.');
		if (dot >= 0) {
			ObservableModelSet subModel = getSubModelIfExists(path.substring(0, dot));
			if (subModel == null)
				return null;
			String name = path.substring(dot + 1);
			// If the value is asked for by path, don't inherit from parents
			node = subModel.getComponentIfExists(name, false);
		} else {
			node = getLocalComponent(path);
			if (node == null && inheritFromParent && getParent() != null)
				node = getParent().getComponentIfExists(path);
			if (node == null) {
				for (ObservableModelSet inh : getInheritance().values()) {
					node = inh.getComponentIfExists(path, inheritFromParent);
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
	 * @throws ModelException If no such component exists accessible to this model
	 */
	default ModelComponentNode<?> getComponent(String path) throws ModelException {
		ModelComponentNode<?> node;
		int dot = path.lastIndexOf('.');
		if (dot >= 0) {
			ObservableModelSet subModel = getSubModel(path.substring(0, dot));
			if (subModel == null)
				return null;
			String name = path.substring(dot + 1);
			node = subModel.getComponent(name);
		} else {
			node = getLocalComponent(path);
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
			throw new ModelException("Nothing at '" + path + "'");
		return node;
	}

	/**
	 * @param id The identity of the component to get
	 * @return The component in this model or any of its inherited models with the given ID
	 * @throws IllegalArgumentException If the given component does not exist in this model
	 */
	default ModelComponentNode<?> getComponent(ModelComponentId id) throws IllegalArgumentException {
		if (id.getOwnerId() == getIdentity()) { // A component we own
			ModelComponentNode<?> component = getLocalComponent(id.getName());
			if (component == null)
				throw new IllegalArgumentException("No such local component " + id + ".  Did you make that up??");
			return component;
		} else if (id.getRootId() == getIdentity().getRootId()) { // We don't own it locally, but it belongs to this model structure
			List<String> path = id.getPath(false, false);
			ObservableModelSet model = getRoot();
			for (String pathEl : path) {
				ModelComponentNode<?> child = model.getLocalComponent(pathEl);
				if (child == null)
					throw new IllegalArgumentException(
						"No such component " + model.getIdentity() + "." + pathEl + ".  Did you make that up??");
				model = child.getModel();
				if (model == null)
					throw new IllegalArgumentException(
						"Component " + child.getIdentity() + " is not a model, so " + id + " does not exist.  Did you make that up??");
			}
			ModelComponentNode<?> component = model.getLocalComponent(id.getName());
			if (component == null)
				throw new IllegalArgumentException("No such component " + id + ".  Did you make that up??");
			return component;
		} else {
			ObservableModelSet inh = getInheritance().get(id.getRootId());
			if (inh == null)
				inh = getRoot().getInheritance().get(id.getRootId());
			if (inh == null)
				throw new IllegalArgumentException("Component " + id + " is for a model unrelated to this one (" + getIdentity() + ")");
			return inh.getComponent(id);
		}
	}

	/** @return The identities of all {@link IdentifiableCompiledValue identified} components in this model */
	Set<Object> getComponentIdentifiers();

	/**
	 * @param valueIdentifier The identifier of the value
	 * @return The {@link IdentifiableCompiledValue identified} value in this model with the given value ID
	 * @throws ModelException If no such identified value exists in this model
	 */
	default ModelComponentNode<?> getIdentifiedComponent(Object valueIdentifier) throws ModelException {
		ModelComponentNode<?> node = getIdentifiedComponentIfExists(valueIdentifier);
		if (node == null)
			throw new ModelException("No such value in this model with identifier " + valueIdentifier);
		return node;
	}

	/**
	 * @param valueIdentifier The identifier of the value
	 * @return The {@link IdentifiableCompiledValue identified} value in this model or one of its inherited models with the given value ID,
	 *         or null if there is no such value
	 */
	ModelComponentNode<?> getIdentifiedComponentIfExists(Object valueIdentifier);

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
			ModelComponentNode<?> node = refModel.getComponentIfExists(modelName);
			if (node == null || node.getModel() == null && getParent() != null)
				node = getParent().getComponentIfExists(modelName);
			if (node == null || node.getModel() == null)
				return null;
			refModel = node.getModel();

			int lastDot = dot;
			dot = path.indexOf('.', dot + 1);
			while (dot >= 0) {
				modelName = path.substring(0, dot);
				node = refModel.getComponentIfExists(modelName);
				if (node == null || node.getModel() == null)
					return null;
				refModel = node.getModel();
				dot = path.indexOf('.', dot + 1);
			}
			name = path.substring(lastDot + 1);
		} else
			name = path;

		ModelComponentNode<?> node = refModel.getComponentIfExists(name);
		if (node == null || node.getModel() == null)
			return null;
		return node.getModel();
	}

	/**
	 * @param path The dot-separated path of the sub-model to get
	 * @return The node representing the target sub-model
	 * @throws ModelException If no such sub-model exists accessible to this model
	 */
	default ObservableModelSet getSubModel(String path) throws ModelException {
		ObservableModelSet refModel = this;
		String name;
		int dot = path.indexOf('.');
		if (dot >= 0) {
			// First find the model represented by the first path element
			String modelName = path.substring(0, dot);
			ModelComponentNode<?> node = refModel.getComponentIfExists(modelName);
			if (node == null || node.getModel() == null && getParent() != null)
				node = getParent().getComponentIfExists(modelName);
			if (node == null)
				throw new ModelException("No such sub-model at '" + modelName + "'");
			if (node == null || node.getModel() == null)
				throw new ModelException("'" + modelName + "' is not a sub-model");
			refModel = node.getModel();

			StringBuilder modelPath = new StringBuilder(getIdentity().toString()).append('.').append(modelName);
			int lastDot = dot;
			dot = path.indexOf('.', dot + 1);
			while (dot >= 0) {
				modelName = path.substring(0, dot);
				modelPath.append('.').append(modelName);
				node = refModel.getComponentIfExists(modelName);
				if (node == null)
					throw new ModelException("No such sub-model at '" + modelPath + "'");
				if (node == null || node.getModel() == null)
					throw new ModelException("'" + modelPath + "' is not a sub-model");
				refModel = node.getModel();
				dot = path.indexOf('.', dot + 1);
			}
			name = path.substring(lastDot + 1);
		} else
			name = path;

		ModelComponentNode<?> node = refModel.getComponentIfExists(name);
		if (node == null)
			throw new ModelException("No such sub-model at '" + path + "'");
		else if (node.getModel() == null)
			throw new ModelException("'" + path + "' is not a sub-model");
		return node.getModel();
	}

	/** @return Checks the names of components in this model set to ensure they are accessible from expressions */
	NameChecker getNameChecker();

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
	 * Builds a new {@link ObservableModelSet.ExternalModelSet}
	 *
	 * @param nameChecker The {@link ObservableModelSet#getNameChecker() name checker} for the new model
	 * @return A builder for the new external model set
	 */
	public static ExternalModelSetBuilder buildExternal(NameChecker nameChecker) {
		return new ExternalModelSetBuilder(null, "", nameChecker);
	}

	/**
	 * @param until The until observable that will destroy any derived instances owned by the new instance set
	 * @return A builder for a {@link ModelSetInstance} that may contain values from any number of {@link ObservableModelSet models}
	 */
	public static ModelSetInstanceBuilder createMultiModelInstanceBag(Observable<?> until) {
		return new DefaultModelSet.MultipleModelInstanceBuilder(until);
	}

	/** Builds an {@link ObservableModelSet} */
	public interface Builder extends ObservableModelSet {
		/**
		 * Assigns a value to a model tag in this model
		 *
		 * @param <T> The type of the tag
		 * @param tag The tag to set
		 * @param value The value to set
		 * @return This builder
		 */
		<T> Builder withTagValue(ModelTag<T> tag, T value);

		/**
		 * Declares a dependency on a value from an {@link ObservableModelSet.ExternalModelSet}
		 *
		 * @param <M> The model type of the value
		 * @param <MV> The type of the value
		 * @param name The name of the value in this model set
		 * @param extGetter The reference to retrieve the value from an {@link ObservableModelSet.ExternalModelSet}
		 * @param sourceLocation The location in the source file where the external value was declared. May be null.
		 * @return This builder
		 */
		<M, MV extends M> Builder withExternal(String name, ExtValueRef<M> extGetter, LocatedFilePosition sourceLocation);

		/**
		 * Installs a creator for a model value
		 *
		 * @param name The name of the value in this model set
		 * @param maker The creator to create the model value
		 * @param sourceLocation The location in the source file where the value was declared. May be null.
		 * @return This builder
		 */
		Builder withMaker(String name, CompiledModelValue<?> maker, LocatedFilePosition sourceLocation);

		/**
		 * Retrieves or creates a builder for a sub-model under this model set
		 *
		 * @param name The name for the sub-model
		 * @param sourceLocation The location in the source file where the model was declared. May be null.
		 * @return The builder for the sub-model
		 */
		Builder createSubModel(String name, LocatedFilePosition sourceLocation);

		/**
		 * Causes this model to {@link ObservableModelSet#getInheritance() inherit} all of the other model's components. All of the other
		 * model's components will be accessible from this model.
		 *
		 * @param other The model whose components to inherit
		 * @return This builder
		 */
		Builder withAll(ObservableModelSet other);

		/**
		 * Installs a container for a model value
		 *
		 * @param name The name of the value in this model set
		 * @param value The container to create the model value
		 * @param sourceLocation The location in the source file where the value was declared. May be null.
		 * @return This builder
		 */
		default <M> Builder with(String name, InterpretedValueSynth<M, ?> value, LocatedFilePosition sourceLocation) {
			return withMaker(name, CompiledModelValue.constant(value), sourceLocation);
		}

		/**
		 * Installs a creator for a model value
		 *
		 * @param name The name of the value in this model set
		 * @param type The type of the new value
		 * @param value The function to create the model value
		 * @param sourceLocation The location in the source file where the value was declared. May be null.
		 * @return This builder
		 */
		default <M, MV extends M> Builder with(String name, ModelInstanceType<M, MV> type, ModelValueInstantiator<MV> value,
			LocatedFilePosition sourceLocation) {
			return with(name, InterpretedValueSynth.simple(type, value), sourceLocation);
		}

		/** @return The immutable {@link ObservableModelSet} configured with this builder */
		Built build();
	}

	/**
	 * An immutable {@link ObservableModelSet} {@link ObservableModelSet.Builder#build() build} from a {@link ObservableModelSet.Builder}
	 */
	public interface Built extends ObservableModelSet {
		@Override
		Built getParent();

		@Override
		Built getRoot();

		@Override
		default Built getSubModelIfExists(String path) {
			return (Built) ObservableModelSet.super.getSubModelIfExists(path);
		}

		@Override
		default Built getSubModel(String path) throws ModelException {
			return (Built) ObservableModelSet.super.getSubModel(path);
		}

		/**
		 * Creates the interpretation of this model, but does not perform the actual
		 * {@link InterpretedModelSet#interpret(InterpretedExpressoEnv) interpretation} yet
		 *
		 * @param env The environment to do the interpretation in
		 * @return The interpretation
		 */
		InterpretedModelSet createInterpreted(InterpretedExpressoEnv env);
	}

	/** An {@link ObservableModelSet} whose components are all {@link InterpretedModelComponentNode interpreted} */
	public interface InterpretedModelSet extends Built {
		@Override
		InterpretedModelSet getParent();

		@Override
		InterpretedModelSet getRoot();

		@Override
		Map<ModelComponentId, ? extends InterpretedModelSet> getInheritance();

		@Override
		InterpretableModelComponentNode<?> getLocalComponent(String name);

		@Override
		InterpretableModelComponentNode<?> getIdentifiedComponentIfExists(Object valueIdentifier);

		@Override
		default InterpretedModelSet getSubModelIfExists(String path) {
			return (InterpretedModelSet) Built.super.getSubModelIfExists(path);
		}

		@Override
		default InterpretedModelSet getSubModel(String path) throws ModelException {
			return (InterpretedModelSet) Built.super.getSubModel(path);
		}

		@Override
		default InterpretableModelComponentNode<?> getComponentIfExists(String path) {
			return (InterpretableModelComponentNode<?>) Built.super.getComponentIfExists(path);
		}

		@Override
		default InterpretableModelComponentNode<?> getComponentIfExists(String path, boolean inheritFromParent) {
			return (InterpretableModelComponentNode<?>) Built.super.getComponentIfExists(path, inheritFromParent);
		}

		@Override
		default InterpretableModelComponentNode<?> getComponent(String path) throws ModelException {
			return (InterpretableModelComponentNode<?>) Built.super.getComponent(path);
		}

		@Override
		default InterpretableModelComponentNode<?> getComponent(ModelComponentId id) throws IllegalArgumentException {
			return (InterpretableModelComponentNode<?>) Built.super.getComponent(id);
		}

		@Override
		default InterpretableModelComponentNode<?> getIdentifiedComponent(Object valueIdentifier) throws ModelException {
			return (InterpretableModelComponentNode<?>) Built.super.getIdentifiedComponent(valueIdentifier);
		}

		/**
		 * @param <M> The model type of the value to get
		 * @param <MV> The type of the value to get
		 * @param path The dot-separated path of the value to get
		 * @param type The type of the value to get
		 * @param env The environment to assist with the conversion
		 * @return The container of the value in this model at the given path, converted to the given type if needed
		 * @throws ModelException If no such value exists accessible at the given path
		 * @throws ExpressoInterpretationException If the value could not be interpreted
		 * @throws TypeConversionException If the value at the path could not be converted to the target type
		 */
		default <M, MV extends M> InterpretedValueSynth<M, MV> getValue(String path, ModelInstanceType<M, MV> type,
			InterpretedExpressoEnv env) throws ModelException, ExpressoInterpretationException, TypeConversionException {
			InterpretedModelComponentNode<?, ?> node = getComponent(path).interpreted();
			if (node.getModel() != null)
				throw new ModelException("'" + path + "' is a sub-model, not a value");
			return node.as(type, env, ExceptionHandler.thrower());
		}

		/**
		 * @param <M> The model type of the value to get
		 * @param <MV> The type of the value to get
		 * @param componentId The component ID of the value to get
		 * @param type The type of the value to get
		 * @param env The environment to assist with the conversion
		 * @return The container of the value in this model at the given path, converted to the given type if needed
		 * @throws ModelException If no such value exists accessible at the given path
		 * @throws ExpressoInterpretationException If the value could not be interpreted
		 * @throws TypeConversionException If the value at the path could not be converted to the target type
		 */
		default <M, MV extends M> InterpretedValueSynth<M, MV> getValue(ModelComponentId componentId, ModelInstanceType<M, MV> type,
			InterpretedExpressoEnv env) throws ModelException, ExpressoInterpretationException, TypeConversionException {
			InterpretedModelComponentNode<?, ?> node = getComponent(componentId).interpreted();
			if (node.getModel() != null)
				throw new ModelException("'" + componentId + "' is a sub-model, not a value");
			return node.as(type, env, ExceptionHandler.thrower());
		}

		@Override
		default InterpretedModelSet createInterpreted(InterpretedExpressoEnv env) {
			return this;
		}

		/**
		 * Interprets all of this model set's components which have not yet been interpreted
		 *
		 * @param env The environment in which to do the interpretation
		 * @throws ExpressoInterpretationException If any of this model set's components could not be
		 *         {@link CompiledModelValue#interpret(InterpretedExpressoEnv) interpreted}
		 */
		void interpret(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		/** @return An instantiator for this interpreted model */
		ModelInstantiator instantiate();

		/**
		 * Creates a builder for a {@link ModelSetInstance} which will contain values for all the components in this model set
		 *
		 * @param until An observable that fires when the lifetime of the new model instance set expires (or null if the lifetime of the new
		 *        model instance set is to be infinite)
		 * @return A builder for the new instance set
		 * @throws ModelInstantiationException If any models fail to initialize
		 */
		default ModelSetInstanceBuilder createInstance(Observable<?> until) throws ModelInstantiationException {
			ModelInstantiator instantiated = instantiate();
			instantiated.instantiate();
			return instantiated.createInstance(until);
		}
	}

	/** A structure holding instantiators for a {@link ObservableModelSet model} */
	public interface ModelInstantiator extends Identifiable {
		@Override
		ModelComponentId getIdentity();

		/**
		 * @param <T> The type of the tag
		 * @param tag The model tag to get the value of
		 * @return The value for the given tag in this model set
		 */
		<T> T getLocalTagValue(ModelTag<T> tag);

		/**
		 * @param <T> The type of the tag
		 * @param tag The model tag to get the value of
		 * @return The value for the given tag in this model set or any of its {@link #getInheritance() inherited} models
		 */
		default <T> T getTagValue(ModelTag<T> tag) {
			T value = getLocalTagValue(tag);
			if (value == null) {
				for (ModelComponentId inh : getInheritance()) {
					value = getInheritance(inh).getTagValue(tag);
					if (value != null)
						break;
				}
			}
			return value;
		}

		/** @return The model identifiers of all components in this structure */
		Set<ModelComponentId> getComponents();

		/** @return The IDs of all {@link IdentifiableCompiledValue identified} components in this structure */
		Set<Object> getComponentIdentifiers();

		/**
		 * @param component The {@link ModelComponentNode#getIdentity() ID} of the component to get
		 * @return The value instantiator for the given component
		 * @throws ModelInstantiationException IF the model component could not be instantiated
		 */
		ModelValueInstantiator<?> getComponent(ModelComponentId component) throws ModelInstantiationException;

		/** @return The IDs of all models that this instantiator's models inherit */
		Set<ModelComponentId> getInheritance();

		/**
		 * @param inherited The model ID of a model that one of this structure's models inherits from
		 * @return The instantiator that this structure inherits for the given inherited model
		 */
		ModelInstantiator getInheritance(ModelComponentId inherited);

		/**
		 * Must be called once after creation. Creates and initializes internal structures.
		 *
		 * @throws ModelInstantiationException If any model values fail to initialize
		 */
		void instantiate() throws ModelInstantiationException;

		/**
		 * Creates a builder for a {@link ModelSetInstance} which will contain values for all the components in this model set
		 *
		 * @param until An observable that fires when the lifetime of the new model instance set expires (or null if the lifetime of the new
		 *        model instance set is to be infinite)
		 * @return A builder for the new instance set
		 */
		ModelSetInstanceBuilder createInstance(Observable<?> until);

		/**
		 * @param models The model instance to wrap
		 * @return A new model instance for this structure's models containing all data in the given model instance set
		 * @throws ModelInstantiationException If any of this structure's models could not be instantiated
		 */
		default ModelSetInstance wrap(ModelSetInstance models) throws ModelInstantiationException {
			if (models.getTopLevelModels().contains(getIdentity()) || models.getInheritance().contains(getIdentity()))
				return models;
			else
				return createInstance(models.getUntil()).withAll(models).build();
		}

		/**
		 * @param updatingModels The models to copy
		 * @param until The until observable to destroy instances created for the resulting instance set
		 * @return A copy of the given models populated with instances for this structure's model values
		 * @throws ModelInstantiationException If any of model values could not be copied or instantiated
		 */
		default ModelSetInstanceBuilder createCopy(ModelSetInstance updatingModels, Observable<?> until)
			throws ModelInstantiationException {
			ModelSetInstanceBuilder copy = updatingModels.copy(until);
			if (updatingModels.getTopLevelModels().size() != 1 || !updatingModels.getTopLevelModels().contains(getIdentity())) {
				ModelSetInstance myModel = updatingModels.getInherited(getIdentity());
				copy.withAll(myModel.copy(until).build());
			}
			return copy;
		}
	}

	/** A structure holding model value instances for one or more {@link ObservableModelSet model}s */
	public interface ModelInstance {
		/**
		 * @return The identities of all models that this instance contains values for which are not inherited by any other models in this
		 *         instance
		 */
		Set<ModelComponentId> getTopLevelModels();

		/** @return All models inherited by any of this instance's {@link #getTopLevelModels() top-level models} */
		Set<ModelComponentId> getInheritance();

		/**
		 * @param modelId The identity of the model to get the instantiator for
		 * @return The model instantiator for the given model
		 * @throws IllegalArgumentException If this instance does not contain the given model
		 */
		ModelInstantiator getModel(ModelComponentId modelId) throws IllegalArgumentException;

		/** @return The observable that the model set instance will die with */
		Observable<?> getUntil();

		/**
		 * @param modelId The id of the inherited model
		 * @return The instance of the given model inherited by this instance, or null if this is a {@link ModelSetInstanceBuilder} and this
		 *         model does not contain or inherit the given model
		 * @throws IllegalArgumentException If this is a built {@link ModelSetInstance} and this model does not contain the given model
		 */
		ModelSetInstance getInherited(ModelComponentId modelId) throws IllegalArgumentException;

		/** @return A map containing all components of all models in this structure that are {@link IdentifiableCompiledValue identified} */
		Map<Object, ModelComponentId> getComponentsByValueId();

		/**
		 * @param component The {@link ModelComponentNode#getIdentity() ID} of the model component to get the value for
		 * @return The model value in this model instance set for the given component
		 * @throws ModelInstantiationException If the model component could not be instantiated
		 * @throws IllegalArgumentException If the component does not belong to this model or is a sub-model
		 */
		Object get(ModelComponentId component) throws ModelInstantiationException, IllegalArgumentException;

		/**
		 * @param valueId The {@link ModelComponentNode#getValueIdentity() value ID} of the model component to get the value for
		 * @return The model value in this model instance set for the given component
		 * @throws ModelInstantiationException If the model component could not be instantiated
		 * @throws IllegalArgumentException If there is no component with the given value ID in this model
		 */
		default Object getByValueId(Object valueId) throws ModelInstantiationException, IllegalArgumentException {
			ModelComponentId componentId = getComponentsByValueId().get(valueId);
			if (componentId == null)
				throw new IllegalArgumentException("No value in this model with value identifier '" + valueId + "'");
			return get(componentId);
		}
	}

	/** A set of actual values created by the containers declared in one or more {@link ObservableModelSet}s */
	public interface ModelSetInstance extends ModelInstance {
		/**
		 * @param component The {@link ModelComponentNode#getIdentity() ID} of the model component to get the value for
		 * @return The model value in this model instance set for the given component
		 * @throws ModelInstantiationException If the model component could not be instantiated
		 * @throws IllegalArgumentException If the component does not belong to this model or is a sub-model
		 */
		@Override
		Object get(ModelComponentId component) throws ModelInstantiationException, IllegalArgumentException;

		/**
		 * @param valueId The {@link ModelComponentNode#getValueIdentity() value ID} of the model component to get the value for
		 * @return The model value in this model instance set for the given component
		 * @throws ModelInstantiationException If the model component could not be instantiated
		 * @throws IllegalArgumentException If there is no component with the given value ID in this model
		 */
		@Override
		default Object getByValueId(Object valueId) throws ModelInstantiationException, IllegalArgumentException {
			ModelComponentId componentId = getComponentsByValueId().get(valueId);
			if (componentId == null)
				throw new IllegalArgumentException("No value in this model with value identifier '" + valueId + "'");
			return get(componentId);
		}

		/**
		 * @return A builder that will produce a {@link ModelSetInstance} containing
		 *         {@link ModelValueInstantiator#forModelCopy(Object, ModelSetInstance, ModelSetInstance) copies} of this model's data.
		 */
		default ModelSetInstanceBuilder copy() {
			return copy(getUntil());
		}

		/**
		 * @param until The observable to destroy the model copy
		 * @return A builder that will produce a {@link ModelSetInstance} containing
		 *         {@link ModelValueInstantiator#forModelCopy(Object, ModelSetInstance, ModelSetInstance) copies} of this model's data.
		 */
		ModelSetInstanceBuilder copy(Observable<?> until);
	}

	/** Builds a {@link ModelSetInstance} */
	public interface ModelSetInstanceBuilder extends ModelInstance {
		/**
		 * Satisfies inherited models in this instance builder. The inherited models of the given model instance will not be used, but only
		 * the given model instance.
		 *
		 * @param other The model instance set created for one of the models in this builder
		 * @return This builder
		 */
		ModelSetInstanceBuilder with(ModelInstance other);

		/**
		 * Satisfies inherited models in this instance builder. The inherited models of the given model instance will also be used.
		 *
		 * @param other The model instance set created for one of the models in this builder, or an instance of a model that may inherit
		 *        models also inherited by this model.
		 * @return This builder
		 */
		ModelSetInstanceBuilder withAll(ModelInstance other);

		/**
		 * @return The model instance set configured with this builder
		 * @throws ModelInstantiationException If any model components could not be instantiated
		 */
		ModelSetInstance build() throws ModelInstantiationException;
	}

	/**
	 * A set of values supplied from an external source to satisfy placeholders installed in the {@link ObservableModelSet} with
	 * {@link Builder#withExternal(String, ExtValueRef, LocatedFilePosition)} or another external value method
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
		 * @throws ModelException If no such sub-model exists accessible to this model
		 */
		ExternalModelSet getSubModel(String path) throws ModelException;

		/**
		 * @param <M> The model type of the value to get
		 * @param <MV> The type of the value to get
		 * @param path The dot-separated path of the value to get
		 * @param type The type of the value to get
		 * @param env The environment that may be needed to interpret any default values, for example
		 * @return The value in this model at the given path, converted to the given type if needed
		 * @throws ModelException If no such value exists accessible at the given path
		 * @throws TypeConversionException If the value at the path could not be converted to the target type (i.e. there's no known way to
		 *         convert)
		 * @throws ModelInstantiationException If the actual conversion to the given type fails
		 */
		<M, MV extends M> MV getValue(String path, ModelInstanceType<M, MV> type, InterpretedExpressoEnv env)
			throws ModelException, TypeConversionException, ModelInstantiationException;
	}

	/** Default {@link ObservableModelSet.ExternalModelSet} implementation */
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
		public <M, MV extends M> MV getValue(String path, ModelInstanceType<M, MV> type, InterpretedExpressoEnv env)
			throws ModelException, TypeConversionException, ModelInstantiationException {
			int dot = path.lastIndexOf('.');
			if (dot >= 0) {
				DefaultExternalModelSet subModel = theRoot.getSubModel(path.substring(0, dot));
				return subModel.getValue(path.substring(dot + 1), type, env);
			}
			theNameChecker.checkName(path);
			Placeholder thing = theComponents.get(path);
			if (thing == null)
				throw new ModelException("No such " + type + " declared: '" + pathTo(path) + "'");
			ModelType.ModelInstanceConverter<Object, M> converter = ((ModelInstanceType<Object, Object>) thing.type).convert(type, env);
			if (converter == null)
				throw new TypeConversionException(path, thing.type, type);
			return (MV) converter.convert(thing.thing);
		}

		@Override
		public DefaultExternalModelSet getSubModel(String path) throws ModelException {
			int dot = path.indexOf('.');
			String modelName;
			if (dot >= 0) {
				modelName = path.substring(0, dot);
			} else
				modelName = path;
			theNameChecker.checkName(modelName);
			Placeholder subModel = theComponents.get(modelName);
			if (subModel == null)
				throw new ModelException("No such external sub-model declared: '" + pathTo(modelName) + "'");
			else if (subModel.type.getModelType() != ModelTypes.Model)
				throw new ModelException("'" + pathTo(modelName) + "' is a " + subModel.type + ", not a Model");
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

	/** Builds an {@link ObservableModelSet.ExternalModelSet} */
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
		 * @throws ModelException If a name conflict occurs
		 */
		public <M, MV extends M> ExternalModelSetBuilder with(String name, ModelInstanceType<M, MV> type, MV item) throws ModelException {
			if (item == null)
				throw new NullPointerException("Installing null for " + type + "@" + name);
			theNameChecker.checkName(name);
			if (theComponents.containsKey(name))
				throw new ModelException("A value of type " + theComponents.get(name).type + " has already been added as '" + name + "'");
			theComponents.put(name, new DefaultExternalModelSet.Placeholder(type, item));
			return this;
		}

		/**
		 * @param name The name for the new sub-model
		 * @param modelBuilder Accepts a {@link ExternalModelSetBuilder} to builder the sub-model
		 * @return This model builder
		 * @throws ModelException If a name conflict occurs
		 */
		public ExternalModelSetBuilder withSubModel(String name, ExConsumer<ExternalModelSetBuilder, ModelException> modelBuilder)
			throws ModelException {
			theNameChecker.checkName(name);
			DefaultExternalModelSet.Placeholder existing = theComponents.get(name);
			if (existing != null) {
				if (existing.thing instanceof ExternalModelSetBuilder) {
					modelBuilder.accept((ExternalModelSetBuilder) existing.thing);
					return this;
				} else
					throw new ModelException(
						"A value of type " + theComponents.get(name).type + " has already been added as '" + name + "'");
			}
			if (theComponents.containsKey(name))
				throw new ModelException("A value of type " + theComponents.get(name).type + " has already been added as '" + name + "'");
			ExternalModelSetBuilder subModel = new ExternalModelSetBuilder((ExternalModelSetBuilder) theRoot, pathTo(name), theNameChecker);
			theComponents.put(name, new DefaultExternalModelSet.Placeholder(ModelTypes.Model.instance(), subModel));
			modelBuilder.accept(subModel);
			return this;
		}

		/**
		 * @param name The name for the new sub-model
		 * @return an {@link ExternalModelSetBuilder} for the new sub-model
		 * @throws ModelException If a name conflict occurs
		 */
		public ExternalModelSetBuilder addSubModel(String name) throws ModelException {
			theNameChecker.checkName(name);
			DefaultExternalModelSet.Placeholder existing = theComponents.get(name);
			if (existing != null) {
				if (existing.thing instanceof ExternalModelSetBuilder)
					return (ExternalModelSetBuilder) existing.thing;
				else
					throw new ModelException(
						"A value of type " + theComponents.get(name).type + " has already been added as '" + name + "'");
			}
			ExternalModelSetBuilder subModel = new ExternalModelSetBuilder((ExternalModelSetBuilder) theRoot, pathTo(name), theNameChecker);
			theComponents.put(name, new DefaultExternalModelSet.Placeholder(ModelTypes.Model.instance(), subModel));
			return subModel;
		}

		/** @return An {@link ObservableModelSet.ExternalModelSet} configured with values from this builder */
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
		private final Map<ModelTag<?>, Object> theTagValues;
		private final Map<ModelComponentId, ? extends ObservableModelSet> theInheritance;
		private final DefaultModelSet theRoot;
		/** This model's components */
		protected final Map<String, ? extends ModelComponentNode<?>> theComponents;
		private final Map<Object, ModelComponentId> theComponentsByModelId;
		private final NameChecker theNameChecker;

		/**
		 * @param id The component id for the new model
		 * @param root The root model for the new model, or null if the new model is to be the root
		 * @param parent The parent model for the new model, or null if the new model is to be the root
		 * @param tagValues Model tag values for this model
		 * @param inheritance The new model's {@link ObservableModelSet#getInheritance() inheritance}
		 * @param components The {@link ObservableModelSet#getComponentNames() components} for the new model
		 * @param componentsByModelId All {@link IdentifiableCompiledValue identified} values in the model
		 * @param nameChecker The {@link ObservableModelSet#getNameChecker() name checker} for the new model
		 */
		protected DefaultModelSet(ModelComponentId id, DefaultModelSet root, DefaultModelSet parent, Map<ModelTag<?>, Object> tagValues,
			Map<ModelComponentId, ? extends ObservableModelSet> inheritance, Map<String, ? extends ModelComponentNode<?>> components,
				Map<Object, ModelComponentId> componentsByModelId, NameChecker nameChecker) {
			theId = id;
			theRoot = root == null ? this : root;
			theTagValues = tagValues;
			theInheritance = inheritance;
			theParent = parent;
			theComponents = components;
			theComponentsByModelId = componentsByModelId;
			theNameChecker = nameChecker;

			ModelComponentId parentId = parent == null ? null : parent.getIdentity();
			if (parentId != theId.getOwnerId())
				throw new IllegalStateException(
					"Given parent model (" + parentId + ") is incorrect for this model (" + theId + "). Should be " + theId.getOwnerId());
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

		/** @return This model's (modifiable) components */
		protected Map<String, ? extends ModelComponentNode<?>> getComponents() {
			return theComponents;
		}

		/** @return This model's (modifiable) identified components */
		protected Map<Object, ModelComponentId> getIdentifiedComponents() {
			return theComponentsByModelId;
		}

		@Override
		public Map<ModelComponentId, ? extends ObservableModelSet> getInheritance() {
			return theInheritance;
		}

		@Override
		public NameChecker getNameChecker() {
			return theNameChecker;
		}

		@Override
		public <T> T getLocalTagValue(ModelTag<T> tag) {
			return (T) theTagValues.get(tag);
		}

		/** @return All model tag values in this model */
		protected Map<ModelTag<?>, Object> getTagValues() {
			return theTagValues;
		}

		@Override
		public Set<String> getComponentNames() {
			return theComponents.keySet();
		}

		@Override
		public ModelComponentNode<?> getLocalComponent(String name) {
			return theComponents.get(name);
		}

		@Override
		public Set<Object> getComponentIdentifiers() {
			return theComponentsByModelId.keySet();
		}

		@Override
		public ModelComponentNode<?> getIdentifiedComponentIfExists(Object valueIdentifier) {
			ModelComponentId componentId = theComponentsByModelId.get(valueIdentifier);
			return componentId == null ? null : getComponent(componentId);
		}

		@Override
		public String toString() {
			try {
				StringBuilder str = new StringBuilder();
				str.append(theId);
				print(str, 1);
				return str.toString();
			} catch (RuntimeException e) {
				// I've been having trouble with this, but the stack trace hadn't been printing, so I couldn't find the problem
				e.printStackTrace();
				return "(exception)";
			}
		}

		private void print(StringBuilder str, int indent) {
			Set<String> components = new LinkedHashSet<>();
			addComponents(components, this, new HashSet<>());
			for (String component : components) {
				str.append('\n');
				for (int i = 0; i < indent; i++)
					str.append('\t');
				ModelComponentNode<?> node = getComponentIfExists(component, false);
				str.append(component).append(':');
				if (node.getModel() != null)
					((DefaultModelSet) node.getModel()).print(str, indent + 1);
				else if (node instanceof InterpretedValueSynth)
					str.append(' ').append(((InterpretedValueSynth<?, ?>) node).getType());
			}
		}

		private static void addComponents(Set<String> components, ObservableModelSet model, Set<ModelComponentId> models) {
			if (!models.add(model.getIdentity()))
				return;
			components.addAll(model.getComponentNames());
			for (ObservableModelSet inh : model.getInheritance().values())
				addComponents(components, inh, models);
		}

		class ModelNodeImpl<M> implements ModelComponentNode<M> {
			private final ModelComponentId theNodeId;
			private final LocatedFilePosition theSourceLocation;
			private final CompiledModelValue<M> theCreator;
			private InterpretedValueSynth<M, ?> theInterpretedValue;
			private final ExtValueRef<M> theExtRef;
			private final DefaultModelSet theModel;

			ModelNodeImpl(ModelComponentId id, CompiledModelValue<M> creator, ExtValueRef<M> extRef, DefaultModelSet model,
				LocatedFilePosition sourceLocation) {
				theNodeId = id;
				theCreator = creator;
				theExtRef = extRef;
				theModel = model;
				theSourceLocation = sourceLocation;
			}

			@Override
			public ModelComponentId getIdentity() {
				return theNodeId;
			}

			@Override
			public Object getValueIdentity() {
				return theCreator instanceof IdentifiableCompiledValue ? ((IdentifiableCompiledValue<M>) theCreator).getIdentity() : null;
			}

			@Override
			public DefaultModelSet getModel() {
				return theModel;
			}

			@Override
			public ModelType<M> getModelType(CompiledExpressoEnv env) throws ExpressoCompilationException {
				if (theCreator != null)
					return theCreator.getModelType(env);
				else if (theExtRef != null)
					return theExtRef.getModelType();
				else if (theModel != null)
					return (ModelType<M>) ModelTypes.Model;
				else
					throw new IllegalStateException();
			}

			private InterpretedValueSynth<M, ?> getInterpretedValue(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				if (theInterpretedValue == null) {
					if (theCreator != null)
						theInterpretedValue = theCreator.interpret(env);
					else if (theExtRef != null)
						theInterpretedValue = theExtRef.createSynthesizer(env);
					else
						return null;
				}
				return theInterpretedValue;
			}

			@Override
			public Object getThing() {
				if (theCreator != null)
					return theCreator;
				else if (theExtRef != null)
					return theExtRef;
				else
					return theModel;
			}

			@Override
			public LocatedFilePosition getSourceLocation() {
				return theSourceLocation;
			}

			@Override
			public InterpretedModelComponentNode<M, ?> interpret(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				if (theModel instanceof DefaultBuilt)
					return new InterpretedModelNodeImpl<>(theNodeId, null, null, null, ((DefaultBuilt) theModel).createInterpreted(env),
						theSourceLocation);
				else if (theModel != null)
					throw new IllegalStateException("Cannot interpret a model element here");
				else if (theCreator != null)
					return new InterpretedModelNodeImpl<>(theNodeId, theCreator, //
						getInterpretedValue(env), null, null, theSourceLocation);
				else if (theExtRef != null)
					return new InterpretedModelNodeImpl<>(theNodeId, null, getInterpretedValue(env), theExtRef, null, theSourceLocation);
				else
					throw new IllegalArgumentException("Unrecognized node value: " + getThing().getClass().getName());
			}

			@Override
			public String toString() {
				if (theCreator != null)
					return theCreator + "@" + theNodeId;
				else if (theExtRef != null)
					return "ext:" + theExtRef.getModelType() + "@" + theNodeId;
				else
					return theNodeId.toString();
			}
		}

		static class InterpretedModelNodeImpl<M, MV extends M> implements InterpretedModelComponentNode<M, MV> {
			private final ModelComponentId theNodeId;
			private final LocatedFilePosition theSourceLocation;
			private final CompiledModelValue<M> theCreator;
			private final InterpretedValueSynth<M, MV> theValue;
			private final ExtValueRef<M> theExtRef;
			private final DefaultInterpreted theModel;

			public InterpretedModelNodeImpl(ModelComponentId nodeId, CompiledModelValue<M> creator, InterpretedValueSynth<M, MV> value,
				ExtValueRef<M> extRef, DefaultInterpreted model, LocatedFilePosition sourceLocation) {
				theNodeId = nodeId;
				theSourceLocation = sourceLocation;
				theCreator = creator;
				theValue = value;
				theExtRef = extRef;
				theModel = model;
			}

			@Override
			public ModelComponentId getIdentity() {
				return theNodeId;
			}

			@Override
			public Object getValueIdentity() {
				return theCreator instanceof IdentifiableCompiledValue ? ((IdentifiableCompiledValue<M>) theCreator).getIdentity() : null;
			}

			@Override
			public ModelType<M> getModelType() {
				if (theValue != null)
					return theValue.getModelType();
				else if (theModel != null)
					return (ModelType<M>) ModelTypes.Model;
				else
					throw new IllegalStateException();
			}

			@Override
			public InterpretedValueSynth<M, MV> getValue() {
				return theValue;
			}

			@Override
			public ModelValueInstantiator<MV> instantiate() throws ModelInstantiationException {
				return new DefaultComponentInstantiator<>(this);
			}

			@Override
			public Object getThing() {
				if (theCreator != null)
					return theCreator;
				else if (theExtRef != null)
					return theExtRef;
				else
					return theModel;
			}

			@Override
			public ModelInstanceType<M, MV> getType() {
				if (theValue != null)
					return theValue.getType();
				else if (theModel != null)
					return (ModelInstanceType<M, MV>) ModelTypes.Model.instance();
				else
					throw new IllegalStateException();
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				if (theValue == null)
					return Collections.emptyList();
				else
					return Collections.singletonList(theValue);
			}

			@Override
			public InterpretedModelSet getModel() {
				return theModel;
			}

			@Override
			public LocatedFilePosition getSourceLocation() {
				return theSourceLocation;
			}

			@Override
			public String toString() {
				return getThing().toString();
			}
		}

		static class DefaultComponentInstantiator<MV> implements ModelComponentInstantiator<MV> {
			private final ModelComponentId theIdentity;
			private ModelValueInstantiator<MV> theInstantiator;

			DefaultComponentInstantiator(InterpretedModelComponentNode<?, MV> component) throws ModelInstantiationException {
				theIdentity = component.getIdentity();
				theInstantiator = component.getValue().instantiate();
			}

			@Override
			public ModelComponentId getIdentity() {
				return theIdentity;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theInstantiator.instantiate();
			}

			@Override
			public MV get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				return (MV) models.get(theIdentity);
			}

			@Override
			public MV create(ModelSetInstance modelSet) throws ModelInstantiationException {
				return theInstantiator.get(modelSet);
			}

			@Override
			public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return theInstantiator.forModelCopy(value, sourceModels, newModels);
			}

			@Override
			public int hashCode() {
				return theIdentity.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof ModelComponentInstantiator && theIdentity.equals(((ModelComponentInstantiator<?>) obj).getIdentity());
			}

			@Override
			public String toString() {
				return theIdentity.toString();
			}
		}

		/** {@link Builder} for a {@link DefaultModelSet} */
		public static class DefaultBuilder extends DefaultModelSet implements Builder {
			private DefaultBuilt theBuilt;

			/**
			 * @param id The component ID for the new model
			 * @param root The root for the new model, or null if this is to be the root
			 * @param parent The parent for the new model, or null if this is to be the root
			 * @param nameChecker The {@link ObservableModelSet#getNameChecker() name checker} for the new model
			 */
			protected DefaultBuilder(ModelComponentId id, DefaultBuilder root, DefaultBuilder parent, NameChecker nameChecker) {
				super(id, root, parent, new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
					nameChecker);
			}

			@Override
			public DefaultBuilder getParent() {
				return (DefaultBuilder) super.getParent();
			}

			@Override
			public Map<ModelComponentId, ObservableModelSet> getInheritance() {
				return Collections.unmodifiableMap(super.getInheritance());
			}

			@Override
			protected Map<String, ModelComponentNode<?>> getComponents() {
				return (Map<String, ModelComponentNode<?>>) super.getComponents();
			}

			void assertNotBuilt() {
				if (theBuilt != null)
					throw new IllegalStateException("This model set has already been built and may not be added to");
			}

			@Override
			public <T> Builder withTagValue(ModelTag<T> tag, T value) {
				if (value != null && !TypeTokens.get().isInstance(tag.getType(), value))
					throw new IllegalArgumentException(
						"Illegal value of type " + value.getClass().getName() + " for tag " + tag.getName() + "(" + tag.getType() + ")");
				getTagValues().put(tag, value);
				return this;
			}

			private void checkNameForAdd(String name) {
				assertNotBuilt();
				ModelComponentNode<?> existing = theComponents.get(name);
				if (existing != null) {
					throw new IllegalArgumentException(
						"A value (" + existing.getThing() + ") has already been added as '" + name + "'");
				}
			}

			@Override
			public <M, MV extends M> Builder withExternal(String name, ExtValueRef<M> extGetter, LocatedFilePosition sourceLocation) {
				getNameChecker().checkName(name);
				checkNameForAdd(name);
				getComponents().put(name,
					createPlaceholder(new ModelComponentId(getIdentity(), name), null, extGetter, null, sourceLocation));
				return this;
			}

			@Override
			public Builder withMaker(String name, CompiledModelValue<?> maker, LocatedFilePosition sourceLocation) {
				getNameChecker().checkName(name);
				checkNameForAdd(name);
				ModelComponentNode<?> node = createPlaceholder(new ModelComponentId(getIdentity(), name), maker, null, null,
					sourceLocation);
				getComponents().put(name, node);
				if (node.getValueIdentity() != null)
					getIdentifiedComponents().put(node.getValueIdentity(), node.getIdentity());
				return this;
			}

			@Override
			public DefaultBuilder createSubModel(String name, LocatedFilePosition sourceLocation) {
				getNameChecker().checkName(name);
				ModelComponentNode<?> thing = theComponents.get(name);
				if (thing == null) {
					DefaultBuilder subModel = new DefaultBuilder(new ModelComponentId(getIdentity(), name), (DefaultBuilder) getRoot(),
						this, getNameChecker());
					getComponents().put(name,
						createPlaceholder(new ModelComponentId(getIdentity(), name), null, null, subModel, sourceLocation));
					return subModel;
				} else if (thing.getModel() != null)
					return (DefaultBuilder) thing.getModel();
				else {
					checkNameForAdd(name);
					throw new IllegalStateException(); // Shouldn't get here
				}
			}

			@Override
			public Builder withAll(ObservableModelSet other) {
				if (other.getIdentity() == getIdentity())
					throw new IllegalStateException("A model cannot inherit itself: " + getIdentity());
				assertNotBuilt();
				if (((Map<ModelComponentId, ObservableModelSet>) super.getInheritance()).put(other.getIdentity().getRootId(),
					other) != null)
					return this;
				if (getParent() == null) { //
				} else if (other.getParent() == null)
					throw new IllegalStateException(
						"A child model (" + getIdentity() + ") cannot inherit from a root model (" + other.getIdentity() + ")");
				else if (!getParent().getInheritance().containsKey(other.getParent().getIdentity().getRootId()))
					throw new IllegalStateException("A child model (" + getIdentity() + ") cannot inherit from another child model ("
						+ other.getIdentity() + ") whose parents are not related");
				for (Object id : other.getComponentIdentifiers())
					getIdentifiedComponents().computeIfAbsent(id, __ -> other.getIdentifiedComponentIfExists(id).getIdentity());

				// For each model that other inherits, add an inheritance entry for that model ID mapped to other (if not present)
				for (Map.Entry<ModelComponentId, ? extends ObservableModelSet> subInh : other.getInheritance().entrySet()) {
					if (!isRelated(subInh.getKey()))
						((Map<ModelComponentId, ObservableModelSet>) super.getInheritance()).putIfAbsent(subInh.getKey(),
							subInh.getValue());
				}
				// For any sub-models with the same name, this model's sub-model should inherit that of the other
				for (String name : other.getComponentNames()) {
					ModelComponentNode<?> component = other.getLocalComponent(name);
					if (component.getModel() != null)
						createSubModel(component.getIdentity().getName(), component.getSourceLocation()).withAll(component.getModel());
				}
				return this;
			}

			/**
			 * Creates a new {@link ModelComponentNode} for a new component in this model
			 *
			 * @param <M> The model type of the component
			 * @param componentId The component ID for the component
			 * @param creator The value creator for the component
			 * @param extRef The external value retriever of the component
			 * @param subModel The sub model of the component
			 * @param sourceLocation The location where the runtime value was declared in the source. May be null
			 * @return The new component node
			 */
			protected <M> ModelComponentNode<M> createPlaceholder(ModelComponentId componentId, CompiledModelValue<M> creator,
				ExtValueRef<M> extRef, DefaultModelSet subModel, LocatedFilePosition sourceLocation) {
				return new ModelNodeImpl<>(componentId, creator, extRef, subModel, sourceLocation);
			}

			@Override
			public DefaultBuilt build() {
				if (theBuilt != null)
					return theBuilt;
				else if (getParent() != null) {
					DefaultBuilt parent = getParent().build();
					if (theBuilt != null)
						return theBuilt;
					else
						return _build(parent.getRoot(), parent);
				} else
					return _build(null, null);
			}

			private DefaultBuilt _build(DefaultBuilt root, DefaultBuilt parent) {
				Map<String, ModelComponentNode<?>> components = new LinkedHashMap<>(theComponents.size() * 3 / 2 + 1);
				Map<Object, ModelComponentId> idComponents = QommonsUtils.unmodifiableCopy(getIdentifiedComponents());
				Map<ModelComponentId, ObservableModelSet.Built> inheritance = new LinkedHashMap<>(
					super.getInheritance().size() * 3 / 2 + 1);
				DefaultBuilt model = createModel(root, parent, components, idComponents, inheritance);
				theBuilt = model;
				for (Map.Entry<ModelComponentId, ? extends ObservableModelSet> inh : getInheritance().entrySet()) {
					ObservableModelSet.Built inhModel;
					if (inh.getValue() instanceof ObservableModelSet.Built)
						inhModel = (ObservableModelSet.Built) inh.getValue();
					else if (inh.getValue() instanceof ObservableModelSet.Builder)
						inhModel = ((ObservableModelSet.Builder) inh.getValue()).build();
					else
						throw new IllegalStateException("Model " + inh + " is not either built or a builder?");
					inheritance.put(inh.getKey(), inhModel);
				}
				if (root == null)
					root = model;
				for (Map.Entry<String, ? extends ModelComponentNode<?>> component : theComponents.entrySet()) {
					ModelComponentNode<?> node;
					if (component.getValue().getModel() != null) {
						DefaultBuilt subModel = ((DefaultBuilder) component.getValue().getModel())._build(root, model);
						node = createPlaceholder(component.getValue().getIdentity(), null, null, subModel,
							component.getValue().getSourceLocation());
					} else
						node = component.getValue();
					components.put(component.getKey(), node);
				}
				return model;
			}

			/**
			 * Creates a {@link DefaultModelSet} from {@link #build()}
			 *
			 * @param root The root model, or null if the new model is to be the root
			 * @param parent The parent model, or null if the new model is to be the root
			 * @param components The component map for the new model
			 * @param idComponents The identified component map for the new model
			 * @param inheritance The inheritance for the new model
			 * @return The new model
			 */
			protected DefaultBuilt createModel(DefaultBuilt root, DefaultBuilt parent, Map<String, ModelComponentNode<?>> components,
				Map<Object, ModelComponentId> idComponents, Map<ModelComponentId, ObservableModelSet.Built> inheritance) {
				return new DefaultBuilt(getIdentity(), root, parent, //
					QommonsUtils.unmodifiableCopy(getTagValues()), //
					Collections.unmodifiableMap(inheritance), //
					Collections.unmodifiableMap(components), //
					idComponents == Collections.EMPTY_MAP ? idComponents : Collections.unmodifiableMap(idComponents), //
						getNameChecker());
			}
		}

		/** Default {@link Built} implementation */
		public static class DefaultBuilt extends DefaultModelSet implements Built {
			private DefaultInterpreted theInterpreted;

			/**
			 * @param id The component id for the new model
			 * @param root The root model for the new model, or null if the new model is to be the root
			 * @param parent The parent model for the new model, or null if the new model is to be the root
			 * @param tagValues Model tag values for this model
			 * @param inheritance The new model's {@link ObservableModelSet#getInheritance() inheritance}
			 * @param components The components for the new model
			 * @param identifiedComponents All {@link IdentifiableCompiledValue identified} components in the model
			 * @param nameChecker The {@link ObservableModelSet#getNameChecker() name checker} for the new model
			 */
			protected DefaultBuilt(ModelComponentId id, DefaultBuilt root, DefaultBuilt parent, Map<ModelTag<?>, Object> tagValues,
				Map<ModelComponentId, ObservableModelSet.Built> inheritance, Map<String, ? extends ModelComponentNode<?>> components,
					Map<Object, ModelComponentId> identifiedComponents, NameChecker nameChecker) {
				super(id, root, parent, tagValues, inheritance, components, identifiedComponents, nameChecker);
			}

			@Override
			public DefaultBuilt getRoot() {
				return (DefaultBuilt) super.getRoot();
			}

			@Override
			public DefaultBuilt getParent() {
				return (DefaultBuilt) super.getParent();
			}

			@Override
			public Map<ModelComponentId, ? extends ObservableModelSet.Built> getInheritance() {
				return (Map<ModelComponentId, ? extends ObservableModelSet.Built>) super.getInheritance();
			}

			@Override
			public DefaultInterpreted createInterpreted(InterpretedExpressoEnv env) {
				if (theInterpreted != null)
					return theInterpreted;
				if (getParent() != null)
					throw new IllegalStateException("Can only call this method on a root model");
				return createInterpreted(null, null, env);
			}

			/**
			 * @param root The root interpreted model
			 * @param parent The parent for the interpreted model
			 * @param env The environment in which to do the interpretation
			 * @return The interpreted model implementation, not yet interpreted
			 */
			protected DefaultInterpreted createInterpreted(DefaultInterpreted root, DefaultInterpreted parent, InterpretedExpressoEnv env) {
				if (theInterpreted != null)
					return theInterpreted;
				Map<ModelComponentId, InterpretedModelSet> inheritance = new LinkedHashMap<>(super.getInheritance().size() * 3 / 2 + 1);
				for (Map.Entry<ModelComponentId, ? extends ObservableModelSet.Built> inh : getInheritance().entrySet()) {
					if (inh.getValue() instanceof InterpretedModelSet)
						inheritance.put(inh.getKey(), (InterpretedModelSet) inh.getValue());
					else if (inh.getValue() instanceof DefaultBuilt) {
						DefaultInterpreted inhParent = parent == null ? null
							: (DefaultInterpreted) parent.getInheritance().get(inh.getValue().getIdentity().getOwnerId());
						DefaultInterpreted inhRoot = inhParent == null ? null : inhParent.getRoot();
						inheritance.put(inh.getKey(), ((DefaultBuilt) inh.getValue()).createInterpreted(inhRoot, inhParent, env));
					} else
						throw new IllegalStateException(
							"Unexpected model set type in inheritance: " + inh.getKey() + ": " + inh.getValue().getClass().getName());
				}
				DefaultInterpreted model = createModel(root, parent, Collections.unmodifiableMap(inheritance), env);
				theInterpreted = model;
				return model;
			}

			/**
			 * Creates an {@link DefaultInterpreted} for {@link #createInterpreted(InterpretedExpressoEnv)}
			 *
			 * @param root The root model, or null if the new model is to be the root
			 * @param parent The parent model, or null if the new model is to be the root
			 * @param inheritance The inheritance for the new model
			 * @param env The environment to interpret the values in
			 * @return The new model
			 */
			protected DefaultInterpreted createModel(DefaultInterpreted root, DefaultInterpreted parent,
				Map<ModelComponentId, InterpretedModelSet> inheritance, InterpretedExpressoEnv env) {
				return new DefaultInterpreted(getIdentity(), root, parent, //
					getTagValues(), inheritance, getIdentifiedComponents(), //
					getNameChecker(), this, env);
			}
		}

		/** Default {@link InterpretedModelSet} implementation */
		public static class DefaultInterpreted extends DefaultModelSet implements InterpretedModelSet {
			// These fields are transient, only used during interpretation
			// They are discarded after interpretation is complete
			private ObservableModelSet theSource;
			private InterpretedExpressoEnv theExpressoEnv;
			private Set<ModelComponentNode<?>> theCycleChecker;
			private boolean isInterpreting;

			private ModelInstantiator theInstantiator;

			/**
			 * @param id The component id for the new model
			 * @param root The root model for the new model, or null if the new model is to be the root
			 * @param parent The parent model for the new model, or null if the new model is to be the root
			 * @param tagValues Model tag values for this model
			 * @param inheritance The new model's {@link ObservableModelSet#getInheritance() inheritance}
			 * @param nameChecker The {@link ObservableModelSet#getNameChecker() name checker} for the new model
			 * @param source The model this is the interpretation of
			 * @param env The environment to interpret in
			 * @param componentsByValueId The components of this model by their value identifier
			 */
			public DefaultInterpreted(ModelComponentId id, DefaultInterpreted root, DefaultInterpreted parent,
				Map<ModelTag<?>, Object> tagValues, Map<ModelComponentId, InterpretedModelSet> inheritance,
				Map<Object, ModelComponentId> componentsByValueId, NameChecker nameChecker,
				DefaultBuilt source, InterpretedExpressoEnv env) {
				// No good reason to use linked hash maps here, since dependencies between model values
				// may change the order in which they are added to the maps
				super(id, root, parent, tagValues, inheritance, new HashMap<>(), componentsByValueId, nameChecker);
				theSource = source;
				theExpressoEnv = env.with(this);
				theCycleChecker = root == null ? new LinkedHashSet<>() : root.theCycleChecker;
				// Create child models
				for (Map.Entry<String, ? extends ModelComponentNode<?>> component : source.getComponents().entrySet()) {
					if (component.getValue().getModel() instanceof DefaultBuilt) {
						DefaultInterpreted interpretedModel = ((DefaultBuilt) component.getValue().getThing()).createInterpreted(getRoot(),
							this, env);
						InterpretedModelNodeImpl<?, ?> interpreted = new InterpretedModelNodeImpl<>(component.getValue().getIdentity(),
							null, null, null, interpretedModel, component.getValue().getSourceLocation());
						getComponents().put(component.getValue().getIdentity().getName(), interpreted);
					}
				}
			}

			@Override
			public DefaultInterpreted getRoot() {
				return (DefaultInterpreted) super.getRoot();
			}

			@Override
			public DefaultInterpreted getParent() {
				return (DefaultInterpreted) super.getParent();
			}

			@Override
			public Map<ModelComponentId, ? extends InterpretedModelSet> getInheritance() {
				return (Map<ModelComponentId, ? extends InterpretedModelSet>) super.getInheritance();
			}

			@Override
			protected Map<String, InterpretedModelComponentNode<?, ?>> getComponents() {
				return (Map<String, InterpretedModelComponentNode<?, ?>>) super.getComponents();
			}

			@Override
			public Set<String> getComponentNames() {
				if (theSource != null) // Still interpreting, our components aren't filed out yet
					return theSource.getComponentNames();
				else
					return super.getComponentNames();
			}

			@Override
			public Set<Object> getComponentIdentifiers() {
				if (theSource != null) // Still interpreting, our components aren't filed out yet
					return theSource.getComponentIdentifiers();
				else
					return super.getComponentIdentifiers();
			}

			@Override
			protected Map<Object, ModelComponentId> getIdentifiedComponents() {
				return super.getIdentifiedComponents();
			}

			@Override
			public InterpretableModelComponentNode<?> getLocalComponent(String name) {
				InterpretedModelComponentNode<?, ?> interpreted = getComponents().get(name);
				if (interpreted != null || theSource == null)
					return interpreted;
				else
					return interpretable(theSource.getLocalComponent(name));
			}

			@Override
			public InterpretableModelComponentNode<?> getIdentifiedComponentIfExists(Object valueIdentifier) {
				ModelComponentId interpreted = getIdentifiedComponents().get(valueIdentifier);
				if (interpreted != null)
					return getComponent(interpreted);
				else
					return null;
			}

			private <M> InterpretableModelComponentNode<M> interpretable(ModelComponentNode<M> sourceNode) {
				if (sourceNode == null)
					return null;
				else
					return new InterpretableNode<>(sourceNode);
			}

			@Override
			public ModelInstantiator instantiate() {
				if (theSource != null)
					throw new IllegalStateException(
						"Attempting to instantiate a model that has not finished being interpreted: " + theSource);
				if (getParent() != null)
					return getParent().instantiate();
				else if (theInstantiator == null)
					theInstantiator = new ModelInstantiatorImpl(this, getTagValues(), getIdentifiedComponents());
				return theInstantiator;
			}

			@Override
			public void interpret(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				if (theSource == null)
					return; // Already interpreted
				else if (isInterpreting)
					return;
				isInterpreting = true;
				if (env.getModels().isRelated(getIdentity()))
					theExpressoEnv = env;
				else
					theExpressoEnv = env.with(this);
				for (String name : theSource.getComponentNames()) {
					InterpretedModelComponentNode<?, ?> localNode = getComponents().get(name);
					if (localNode == null)
						interpretComponent(theSource.getLocalComponent(name));
					else if (localNode.getModel() instanceof DefaultInterpreted)
						((DefaultInterpreted) localNode.getModel()).interpret(theExpressoEnv);
				}
				isInterpreting = false;
				theSource = null;
				theExpressoEnv = null;
				theCycleChecker = null;
			}

			<M> InterpretedModelComponentNode<M, ?> interpretComponent(ModelComponentNode<M> sourceNode)
				throws ExpressoInterpretationException {
				if (!theCycleChecker.add(sourceNode)) {
					String depPath;
					if (theCycleChecker.size() == 1)
						depPath = theCycleChecker.iterator().next() + " depends on itself";
					else
						depPath = StringUtils.print("<-", theCycleChecker, Object::toString).toString() + "<-" + sourceNode;
					throw new ExpressoInterpretationException("Dependency cycle detected: " + depPath, sourceNode.getSourceLocation(), 0);
				}
				try {
					InterpretedModelComponentNode<M, ?> interpreted = sourceNode.interpret(theExpressoEnv);
					getComponents().put(sourceNode.getIdentity().getName(), interpreted);
					return interpreted;
				} finally {
					theCycleChecker.remove(sourceNode);
				}
			}

			class InterpretableNode<M> implements InterpretableModelComponentNode<M> {
				private final ModelComponentNode<M> theSourceNode;

				InterpretableNode(ModelComponentNode<M> definition) {
					theSourceNode = definition;
				}

				@Override
				public ModelComponentId getIdentity() {
					return theSourceNode.getIdentity();
				}

				@Override
				public Object getValueIdentity() {
					return theSourceNode.getValueIdentity();
				}

				@Override
				public ModelType<M> getModelType(CompiledExpressoEnv env) throws ExpressoCompilationException {
					return theSourceNode.getModelType(env);
				}

				@Override
				public Object getThing() {
					return theSourceNode.getThing();
				}

				@Override
				public LocatedFilePosition getSourceLocation() {
					return theSourceNode.getSourceLocation();
				}

				@Override
				public InterpretedModelSet getModel() {
					return null; // This node type is not created for sub-models;
				}

				@Override
				public InterpretedModelComponentNode<M, ?> interpret(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
					InterpretedExpressoEnv prevEnv = theExpressoEnv;
					theExpressoEnv = env;
					try {
						return interpreted();
					} finally {
						theExpressoEnv = prevEnv;
					}
				}

				@Override
				public InterpretedModelComponentNode<M, ?> interpreted() throws ExpressoInterpretationException {
					InterpretedModelComponentNode<?, ?> localNode = getComponents().get(theSourceNode.getIdentity().getName());
					if (localNode != null)
						return (InterpretedModelComponentNode<M, ?>) localNode;
					return interpretComponent(theSourceNode);
				}
			}
		}

		static class ModelInstantiatorImpl implements ModelInstantiator {
			private final ModelComponentId theModelId;
			private final Map<ModelTag<?>, Object> theTagValues;
			private final Map<ModelComponentId, ModelValueInstantiator<?>> theComponents;
			private final Map<Object, ModelComponentId> theComponentsByValueId;
			private final Map<ModelComponentId, ModelInstantiator> theInheritance;
			private InterpretedModelSet theInterpretedModels;
			// It may be noticed that I'm not checking for cycles here.
			// I figure I already did that with the compiled and interpreted structures.

			ModelInstantiatorImpl(InterpretedModelSet interpreted, Map<ModelTag<?>, Object> tagValues,
				Map<Object, ModelComponentId> componentsByValueId) {
				theModelId = interpreted.getIdentity();
				theTagValues = tagValues;
				theComponentsByValueId = componentsByValueId;
				theInterpretedModels = interpreted;
				if (interpreted.getComponentNames().isEmpty())
					theComponents = Collections.emptyMap();
				else
					theComponents = new HashMap<>();
				if (interpreted.getInheritance().isEmpty())
					theInheritance = Collections.emptyMap();
				else {
					Map<ModelComponentId, ModelInstantiator> inheritance = new HashMap<>(interpreted.getInheritance().size() * 3 / 2 + 1);
					theInheritance = Collections.unmodifiableMap(inheritance);
					for (Map.Entry<ModelComponentId, ? extends InterpretedModelSet> inh : interpreted.getInheritance().entrySet())
						inheritance.put(inh.getKey(), inh.getValue().instantiate());
				}
			}

			@Override
			public ModelComponentId getIdentity() {
				return theModelId;
			}

			@Override
			public <T> T getLocalTagValue(ModelTag<T> tag) {
				return (T) theTagValues.get(tag);
			}

			@Override
			public Set<ModelComponentId> getComponents() {
				return Collections.unmodifiableSet(theComponents.keySet());
			}

			@Override
			public Set<Object> getComponentIdentifiers() {
				return Collections.unmodifiableSet(theComponentsByValueId.keySet());
			}

			@Override
			public ModelValueInstantiator<?> getComponent(ModelComponentId component) throws ModelInstantiationException {
				ModelComponentId rootModelId = component.getRootId();
				if (rootModelId != theModelId.getRootId()) {
					ModelInstantiator inh = theInheritance.get(rootModelId);
					if (inh != null)
						return inh.getComponent(component);
					else
						throw new IllegalArgumentException(
							"This model component (" + component + ") is for an unrelated model (" + rootModelId + ")");
				}
				ModelValueInstantiator<?> valueOrModel = theComponents.get(component);
				if (valueOrModel == null) {
					if (theInterpretedModels != null) {
						InterpretableModelComponentNode<?> interpretableNode;
						try {
							interpretableNode = theInterpretedModels.getComponent(component);
						} catch (IllegalArgumentException e) {
							theInterpretedModels.getComponent(component);
							throw new IllegalArgumentException("Unrecognized model component: " + component, e);
						}
						if (interpretableNode != null) {
							if (interpretableNode.getModel() != null)
								throw new IllegalArgumentException("Component is a model, not a value: " + component);
							InterpretedModelComponentNode<?, ?> interpreted;
							try {
								interpreted = interpretableNode.interpreted();
							} catch (ExpressoInterpretationException e) {
								throw new IllegalStateException("Attempting to instantiate a model that is not completely interpreted", e);
							}
							ModelValueInstantiator<?> instantiated = interpreted.instantiate();
							theComponents.put(interpreted.getIdentity(), instantiated);
							return instantiated;
						}
					}
					throw new IllegalArgumentException("Unrecognized model component: " + component);
				}
				return valueOrModel;
			}

			@Override
			public Set<ModelComponentId> getInheritance() {
				return theInheritance.keySet();
			}

			@Override
			public ModelInstantiator getInheritance(ModelComponentId inherited) {
				return theInheritance.get(inherited);
			}

			@Override
			public ModelSetInstanceBuilder createInstance(Observable<?> until) {
				if (theInterpretedModels != null)
					throw new IllegalStateException(
						"Attempting to build a model instance set from a model instantiator that has not been completely instantiated: "
							+ getIdentity());
				return new SingleModelInstanceBuilder(this, null, theComponentsByValueId, until);
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				if (theInterpretedModels == null)
					return;
				instantiate(theInterpretedModels);
				for (Map.Entry<ModelComponentId, ? extends InterpretedModelSet> inh : theInterpretedModels.getInheritance().entrySet())
					theInheritance.get(inh.getKey()).instantiate();
				theInterpretedModels = null;
				for (ModelValueInstantiator<?> component : theComponents.values())
					component.instantiate();
			}

			private void instantiate(InterpretedModelSet models) throws ModelInstantiationException {
				for (String name : models.getComponentNames()) {
					InterpretableModelComponentNode<?> interpretableNode = models.getLocalComponent(name);
					if (interpretableNode.getModel() != null)
						instantiate(interpretableNode.getModel());
					else {
						Object found = theComponents.get(interpretableNode.getIdentity());
						if (found == null) {
							InterpretedModelComponentNode<?, ?> component;
							try {
								component = interpretableNode.interpreted();
							} catch (ExpressoInterpretationException e) {
								throw new IllegalStateException("Attempting to instantiate a model that is not completely interpreted", e);
							}
							theComponents.put(component.getIdentity(), component.instantiate());
						}
					}
				}
				for (ModelInstantiator inh : theInheritance.values())
					inh.instantiate();
			}

			@Override
			public String toString() {
				return "Instantiator: " + theModelId;
			}
		}

		static class SingleModelInstanceBuilder implements ModelSetInstanceBuilder {
			private final Map<ModelComponentId, Object> theComponents;
			private final Map<Object, ModelComponentId> theComponentsByValueId;
			private final Map<ModelComponentId, ModelSetInstance> theInheritance;
			private final SingleModelInstance theMSI;

			SingleModelInstanceBuilder(ModelInstantiatorImpl modelInstantiator, ModelSetInstance sourceModel,
				Map<Object, ModelComponentId> componentsByValueId, Observable<?> until) {
				theComponents = new HashMap<>();
				theComponentsByValueId = componentsByValueId;
				theInheritance = new LinkedHashMap<>();
				theMSI = new SingleModelInstance(modelInstantiator, sourceModel, until, theComponents, theComponentsByValueId,
					Collections.unmodifiableMap(theInheritance));
			}

			@Override
			public Set<ModelComponentId> getTopLevelModels() {
				return theMSI.getTopLevelModels();
			}

			@Override
			public ModelInstantiator getModel(ModelComponentId modelId) {
				return theMSI.getModel(modelId);
			}

			@Override
			public Set<ModelComponentId> getInheritance() {
				return theMSI.getInheritance();
			}

			@Override
			public Map<Object, ModelComponentId> getComponentsByValueId() {
				return theComponentsByValueId;
			}

			public ModelInstantiator getModel() {
				return theMSI.getModel();
			}

			@Override
			public ModelSetInstance getInherited(ModelComponentId id) throws IllegalArgumentException {
				// Unlike the built instance, this method:
				// * Does not return anything if id==getModel().getIdentity()--that instance is not yet built and should not be accessible
				// * Does not throw an exception if the inheritance is missing--it just returns null
				return theInheritance.get(id);
			}

			@Override
			public Observable<?> getUntil() {
				return theMSI.getUntil();
			}

			@Override
			public Object get(ModelComponentId component) throws ModelInstantiationException, IllegalArgumentException {
				return theMSI.get(component);
			}

			@Override
			public ModelSetInstanceBuilder with(ModelInstance other) {
				return with(other, false);
			}

			@Override
			public ModelSetInstanceBuilder withAll(ModelInstance other) {
				return with(other, true);
			}

			private ModelSetInstanceBuilder with(ModelInstance other, boolean withInheritance) {
				if (other.getTopLevelModels().contains(theMSI.getModel().getIdentity())//
					|| other.getInheritance().contains(theMSI.getModel().getIdentity())) {
					// The model knows about us. Grab its contents
					ModelSetInstance otherMe = other.getInherited(getModel().getIdentity());
					if (otherMe != null)
						addAll(theMSI.getModel(), otherMe);
					if (withInheritance) {
						for (ModelComponentId inh : theMSI.getModel().getInheritance())
							theInheritance.put(inh, other.getInherited(inh));
					}
				} else { // The other model doesn't know of us, but it may inherit from models we need as well
					for (ModelComponentId inh : getModel().getInheritance()) {
						if (other.getTopLevelModels().contains(inh) || other.getInheritance().contains(inh)) {
							ModelSetInstance otherMSI = other.getInherited(inh);
							if (otherMSI != null) // May be a builder, which won't expose its unbuilt model
								theInheritance.put(inh, otherMSI);
						}
					}
				}
				return this;
			}

			private void addAll(ModelInstantiator model, ModelSetInstance other) {
				for (ModelComponentId comp : model.getComponents()) {
					Object value;
					try {
						value = other.get(comp);
					} catch (ModelInstantiationException e) {
						throw new IllegalStateException("But you said you had it!", e);
					}
					theComponents.put(comp, value);
				}
				addInheritance(other);
			}

			private void addInheritance(ModelSetInstance other) {
				for (ModelComponentId inh : other.getInheritance()) {
					ModelSetInstance inhInstance = other.getInherited(inh);
					theInheritance.put(inh, inhInstance);
					addInheritance(inhInstance);
				}
			}

			@Override
			public ModelSetInstance build() throws ModelInstantiationException {
				StringBuilder error = null;
				for (ModelComponentId inh : theMSI.getModel().getInheritance()) {
					if (!theInheritance.containsKey(inh)) {
						if (error == null)
							error = new StringBuilder();
						else
							error.append('\n');
						error.append("Inherited model " + inh + " not satisfied");
					}
				}
				if (error != null)
					throw new IllegalStateException(error.toString());
				fulfill(theMSI.getModel());
				theMSI.built();
				return theMSI;
			}

			private void fulfill(ModelInstantiator model) throws ModelInstantiationException {
				for (ModelComponentId comp : model.getComponents()) {
					// Don't remember what this is for or how it could happen. Shouldn't do any harm though.
					if (comp.getRootId() != theMSI.getModel().getIdentity())
						continue;
					theMSI.get(comp);
				}
			}

			@Override
			public String toString() {
				return "instanceBuilder:" + theMSI.getModel().getIdentity();
			}
		}

		static class SingleModelInstance implements ModelSetInstance {
			private final ModelInstantiatorImpl theModelInstantiator;
			protected final Map<ModelComponentId, Object> theComponents;
			private final Map<Object, ModelComponentId> theComponentsByValueId;
			private final Map<ModelComponentId, ModelSetInstance> theInheritance;
			private final Observable<?> theUntil;

			private ModelSetInstance theSourceModel;
			private Set<ModelComponentId> theCircularityDetector;

			protected SingleModelInstance(ModelInstantiatorImpl instantiator, ModelSetInstance sourceModel, Observable<?> until,
				Map<ModelComponentId, Object> components, Map<Object, ModelComponentId> componentsByValueId,
				Map<ModelComponentId, ModelSetInstance> inheritance) {
				theModelInstantiator = instantiator;
				theComponents = components;
				theComponentsByValueId = componentsByValueId;
				theInheritance = inheritance;
				theUntil = until;

				theSourceModel = sourceModel;
				theCircularityDetector = new LinkedHashSet<>();
			}

			@Override
			public Set<ModelComponentId> getTopLevelModels() {
				return Collections.singleton(theModelInstantiator.getIdentity());
			}

			@Override
			public Set<ModelComponentId> getInheritance() {
				return theInheritance.keySet();
			}

			@Override
			public ModelInstantiator getModel(ModelComponentId modelId) {
				if (modelId == theModelInstantiator.getIdentity())
					return theModelInstantiator;
				ModelSetInstance inh = theInheritance.get(modelId);
				if (inh != null)
					return inh.getModel(modelId);
				throw new IllegalArgumentException("This model (" + theModelInstantiator.getIdentity() + ") does not inherit " + modelId);
			}

			public ModelInstantiator getModel() {
				return theModelInstantiator;
			}

			@Override
			public Observable<?> getUntil() {
				return theUntil;
			}

			@Override
			public Object get(ModelComponentId component) throws ModelInstantiationException {
				ModelComponentId rootModelId = component.getRootId();
				if (rootModelId != theModelInstantiator.getIdentity()) {
					ModelSetInstance inh = theInheritance.get(rootModelId);
					if (inh != null)
						return inh.get(component);
					else if (theModelInstantiator.getInheritance().contains(rootModelId))
						throw new IllegalStateException(
							"Inheritance " + rootModelId + "not satisfied: use ModelSetInstanceBuilder.withAll(ModelSetInstance)");
					else
						throw new IllegalArgumentException("This model component (" + component + ") is for an unrelated model this=("
							+ theModelInstantiator.getIdentity() + ")");
				}
				// Component is local
				Object valueOrModel = theModelInstantiator.getComponent(component);
				if (valueOrModel instanceof ModelInstantiatorImpl)
					throw new IllegalArgumentException("Model component " + component + " is a model, not a value");
				return getLocalComponent(component, (ModelComponentInstantiator<?>) valueOrModel);
			}

			private <MV> MV getLocalComponent(ModelComponentId component, ModelComponentInstantiator<MV> instantiator)
				throws ModelInstantiationException {
				MV thing = (MV) theComponents.get(component);
				if (thing != null)
					return thing;
				else if (theCircularityDetector == null)
					throw new IllegalArgumentException("Unrecognized model component: " + component);
				else if (!theCircularityDetector.add(component))
					throw new IllegalArgumentException(
						"Dynamic value circularity detected: " + StringUtils.print("<-", theCircularityDetector, Object::toString));
				try {
					if (theSourceModel != null)
						thing = instantiator.forModelCopy((MV) theSourceModel.get(component), theSourceModel, this);
					else
						thing = instantiator.create(this);
					if (thing == null)
						throw new NullPointerException(instantiator + " create a null component");
					theComponents.put(component, thing);
				} finally {
					theCircularityDetector.remove(component);
				}
				return thing;
			}

			@Override
			public Map<Object, ModelComponentId> getComponentsByValueId() {
				return theComponentsByValueId;
			}

			@Override
			public ModelSetInstanceBuilder copy(Observable<?> until) {
				if (theCircularityDetector != null)
					throw new IllegalStateException("Cannot create a copy of a model that is currently being built");
				ModelSetInstanceBuilder builder = new SingleModelInstanceBuilder(theModelInstantiator, this, theComponentsByValueId, until);

				for (ModelSetInstance inh : theInheritance.values())
					builder.withAll(inh);
				return builder;
			}

			@Override
			public ModelSetInstance getInherited(ModelComponentId modelId) throws IllegalArgumentException {
				if (modelId == theModelInstantiator.getIdentity())
					return this;
				ModelSetInstance inh = theInheritance.get(modelId);
				if (inh != null)
					return inh;
				else if (theModelInstantiator.getInheritance().contains(modelId))
					throw new IllegalStateException(
						"Inheritance " + modelId + "not satisfied: use ModelSetInstanceBuilder.withAll(ModelSetInstance)");
				else
					throw new IllegalArgumentException(theModelInstantiator.getIdentity() + " inherits no such model: " + modelId);
			}

			void built() {
				theSourceModel = null;
				theCircularityDetector = null;
			}

			@Override
			public String toString() {
				return "instance:" + theModelInstantiator.getIdentity();
			}
		}

		static class MultipleModelInstanceBuilder implements ModelSetInstanceBuilder {
			private final Map<ModelComponentId, ModelSetInstance> theTopLevelModels;
			private final Map<ModelComponentId, ModelSetInstance> theInheritance;
			private final Map<Object, ModelComponentId> theComponentsByValueId;
			private final MultipleModelInstance theMSI;
			private boolean isBuilt;

			MultipleModelInstanceBuilder(Observable<?> until) {
				theTopLevelModels = new LinkedHashMap<>();
				theInheritance = new LinkedHashMap<>();
				theComponentsByValueId = new LinkedHashMap<>();
				theMSI = new MultipleModelInstance(Collections.unmodifiableMap(theTopLevelModels),
					Collections.unmodifiableMap(theInheritance), Collections.unmodifiableMap(theComponentsByValueId), until);
			}

			@Override
			public Set<ModelComponentId> getTopLevelModels() {
				return theMSI.getTopLevelModels();
			}

			@Override
			public Set<ModelComponentId> getInheritance() {
				return theMSI.getInheritance();
			}

			@Override
			public ModelInstantiator getModel(ModelComponentId modelId) {
				return theMSI.getModel(modelId);
			}

			@Override
			public Observable<?> getUntil() {
				return theMSI.getUntil();
			}

			@Override
			public ModelSetInstance getInherited(ModelComponentId modelId) throws IllegalArgumentException {
				ModelSetInstance model = theTopLevelModels.get(modelId);
				if (model == null)
					model = theInheritance.get(modelId);
				return model;
			}

			@Override
			public Map<Object, ModelComponentId> getComponentsByValueId() {
				return theMSI.getComponentsByValueId();
			}

			@Override
			public Object get(ModelComponentId component) throws ModelInstantiationException, IllegalArgumentException {
				return theMSI.get(component);
			}

			@Override
			public ModelSetInstanceBuilder with(ModelInstance other) {
				return with(other, false);
			}

			@Override
			public ModelSetInstanceBuilder withAll(ModelInstance other) {
				return with(other, true);
			}

			private ModelSetInstanceBuilder with(ModelInstance other, boolean withInheritance) {
				if (isBuilt)
					throw new IllegalStateException("This model instance is built and cannot be modified");
				for (ModelComponentId model : other.getTopLevelModels()) {
					ModelSetInstance instance = other.getInherited(model);
					if (instance == null) {
						// other is a single builder and doesn't expose the un-built model
						// We can still use its inheritance
						for (ModelComponentId inh : other.getInheritance())
							withAll(other.getInherited(inh));
					} else {
						if (theTopLevelModels.containsKey(model))
							theTopLevelModels.put(model, instance);
						else if (theInheritance.containsKey(model))
							theInheritance.put(model, instance);
						else {
							// Remove any currently top-level models which are inherited by the new model
							theTopLevelModels.keySet().removeAll(instance.getInheritance());
							// Install the new model as top-level
							theTopLevelModels.put(model, instance);
						}

						if (withInheritance) {
							// Add the inheritance too, which we know is not top-level
							for (ModelComponentId inh : other.getInheritance())
								theInheritance.put(inh, other.getInherited(inh));
						}

						theComponentsByValueId.putAll(instance.getComponentsByValueId());
					}
				}
				return this;
			}

			@Override
			public ModelSetInstance build() throws ModelInstantiationException {
				isBuilt = true;
				return theMSI;
			}

			@Override
			public String toString() {
				return "instanceBuilder:" + getTopLevelModels();
			}
		}

		static class MultipleModelInstance implements ModelSetInstance {
			private final Map<ModelComponentId, ModelSetInstance> theTopLevelModels;
			private final Map<ModelComponentId, ModelSetInstance> theInheritance;
			private final Map<Object, ModelComponentId> theComponentsByValueId;
			private final Observable<?> theUntil;

			MultipleModelInstance(Map<ModelComponentId, ModelSetInstance> topLevelModels,
				Map<ModelComponentId, ModelSetInstance> inheritance, Map<Object, ModelComponentId> componentsByValueId,
				Observable<?> until) {
				theTopLevelModels = topLevelModels;
				theInheritance = inheritance;
				theComponentsByValueId = componentsByValueId;
				theUntil = until;
			}

			@Override
			public Set<ModelComponentId> getTopLevelModels() {
				return theTopLevelModels.keySet();
			}

			@Override
			public Set<ModelComponentId> getInheritance() {
				return theInheritance.keySet();
			}

			@Override
			public ModelInstantiator getModel(ModelComponentId modelId) {
				ModelSetInstance found = theTopLevelModels.get(modelId);
				if (found != null)
					return found.getModel(modelId);
				found = theInheritance.get(modelId);
				if (found != null)
					return found.getModel(modelId);
				throw new IllegalArgumentException("This model " + getTopLevelModels() + " does not inherit " + modelId);
			}

			@Override
			public Observable<?> getUntil() {
				return theUntil;
			}

			@Override
			public ModelSetInstance getInherited(ModelComponentId modelId) throws IllegalArgumentException {
				ModelSetInstance model = theTopLevelModels.get(modelId);
				if (model != null)
					return model;
				model = theInheritance.get(modelId);
				if (model != null)
					return model;
				throw new IllegalArgumentException("This model " + getTopLevelModels() + " does not inherit " + modelId);
			}

			@Override
			public Map<Object, ModelComponentId> getComponentsByValueId() {
				return theComponentsByValueId;
			}

			@Override
			public Object get(ModelComponentId component) throws ModelInstantiationException, IllegalArgumentException {
				ModelComponentId root = component.getRootId();
				ModelSetInstance models = getInherited(root);
				return models.get(component);
			}

			@Override
			public ModelSetInstanceBuilder copy(Observable<?> until) {
				return new MultipleModelInstanceBuilder(until)//
					.withAll(this);
			}

			@Override
			public String toString() {
				return "instance:" + getTopLevelModels();
			}
		}
	}
}

package org.observe.expresso;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.util.TypeTokens;
import org.observe.util.TypeTokens.TypeConverter;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterList;
import org.qommons.ex.ExceptionHandler;

import com.google.common.reflect.TypeToken;

/** A parsed expression that is capable of producing observable results */
public interface ObservableExpression {
	/** A placeholder expression signifying the lack of any attempt to provide an expression */
	ObservableExpression EMPTY = new ObservableExpression() {
		@Override
		public List<? extends ObservableExpression> getComponents() {
			return Collections.emptyList();
		}

		@Override
		public int getComponentOffset(int childIndex) {
			throw new IndexOutOfBoundsException(childIndex + " of 0");
		}

		@Override
		public int getExpressionLength() {
			return 0;
		}

		@Override
		public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
			return replace.apply(this);
		}

		@Override
		public ModelType<?> getModelType(CompiledExpressoEnv env, int expressionOffset) {
			return ModelTypes.Value;
		}

		@Override
		public <M, MV extends M, EX extends Throwable> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type,
			InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
				throws EX {
			if (type.getModelType() == ModelTypes.Action)
				return ObservableExpression.evEx(expressionOffset, 0, (InterpretedValueSynth<M, MV>) InterpretedValueSynth
					.literal(ModelTypes.Action.instance(), ObservableAction.DO_NOTHING, "(empty)"), null);
			else
				return ObservableExpression.evEx(expressionOffset, 0,
					(InterpretedValueSynth<M, MV>) InterpretedValueSynth.literalValue(TypeTokens.get().WILDCARD, null, "(empty)"), null);
		}

		@Override
		public String toString() {
			return "null";
		}
	};

	/**
	 * The {@link InterpretedValueSynth} extension returned by
	 * {@link ObservableExpression#evaluate(ModelInstanceType, InterpretedExpressoEnv, int, org.qommons.ex.ExceptionHandler.Double)}
	 *
	 * @param <M> The model type of the interpreted expression
	 * @param <MV> The instance type of the interpreted expression
	 */
	public interface EvaluatedExpression<M, MV extends M> extends InterpretedValueSynth<M, MV> {
		/** @return The offset of this expression in the root */
		int getExpressionOffset();

		/** @return The length of this expression */
		int getExpressionLength();

		/** @return A descriptor object which provides some information about the interpreted expression */
		Object getDescriptor();

		@Override
		List<? extends EvaluatedExpression<?, ?>> getComponents();

		/**
		 * @return The interpreted divisions advertised by the expression
		 * @see ObservableExpression#getDivisionCount()
		 * @see ObservableExpression#getDivisionOffset(int)
		 * @see ObservableExpression#getDivisionLength(int)
		 */
		default List<? extends EvaluatedExpression<?, ?>> getDivisions() {
			return Collections.emptyList();
		}
	}

	/**
	 * Utility method to create an {@link EvaluatedExpression} with no {@link EvaluatedExpression#getDivisions()}
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The instance type of the value
	 * @param offset The offset of the expression in the root
	 * @param length The length of the expression
	 * @param value The type of the value
	 * @param descriptor The expression descriptor
	 * @param children The children of the expression
	 * @return The evaluated expression
	 */
	static <M, MV extends M> EvaluatedExpression<M, MV> evEx(int offset, int length, InterpretedValueSynth<M, MV> value, Object descriptor,
		EvaluatedExpression<?, ?>... children) {
		return evEx(offset, length, value, descriptor, QommonsUtils.unmodifiableCopy(children));
	}

	/**
	 * Utility method to create an {@link EvaluatedExpression} with no {@link EvaluatedExpression#getDivisions()}
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The instance type of the value
	 * @param offset The offset of the expression in the root
	 * @param length The length of the expression
	 * @param value The type of the value
	 * @param descriptor The expression descriptor
	 * @param children The children of the expression
	 * @return The evaluated expression
	 */
	static <M, MV extends M> EvaluatedExpression<M, MV> evEx(int offset, int length, InterpretedValueSynth<M, MV> value, Object descriptor,
		List<? extends EvaluatedExpression<?, ?>> children) {
		return evEx2(offset, length, value, descriptor, children, Collections.emptyList());
	}

	/**
	 * Utility method to create an {@link EvaluatedExpression} with possible {@link EvaluatedExpression#getDivisions()}
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The instance type of the value
	 * @param offset The offset of the expression in the root
	 * @param length The length of the expression
	 * @param value The type of the value
	 * @param descriptor The expression descriptor
	 * @param children The children of the expression
	 * @param divisions The divisions of the expression
	 * @return The evaluated expression
	 */
	static <M, MV extends M> EvaluatedExpression<M, MV> evEx2(int offset, int length, InterpretedValueSynth<M, MV> value, Object descriptor,
		List<? extends EvaluatedExpression<?, ?>> children, List<? extends EvaluatedExpression<?, ?>> divisions) {
		if (value == null)
			return null;
		return new EvaluatedExpression<M, MV>() {
			@Override
			public ModelInstanceType<M, MV> getType() {
				return value.getType();
			}

			@Override
			public ModelValueInstantiator<MV> instantiate() {
				return value.instantiate();
			}

			@Override
			public int getExpressionOffset() {
				return offset;
			}

			@Override
			public int getExpressionLength() {
				return length;
			}

			@Override
			public Object getDescriptor() {
				return descriptor;
			}

			@Override
			public List<? extends EvaluatedExpression<?, ?>> getComponents() {
				return children;
			}

			@Override
			public List<? extends EvaluatedExpression<?, ?>> getDivisions() {
				return divisions;
			}

			@Override
			public String toString() {
				return value.toString();
			}
		};
	}

	/**
	 * Wraps another expression, for expressions that perform no operation on their only component
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The instance type of the value
	 * @param wrapped The value to wrap
	 * @return The wrapped expression value
	 */
	static <M, MV extends M> EvaluatedExpression<M, MV> wrap(EvaluatedExpression<M, MV> wrapped) {
		if (wrapped == null)
			return null;
		return new EvaluatedExpression<M, MV>() {
			@Override
			public ModelInstanceType<M, MV> getType() {
				return wrapped.getType();
			}

			@Override
			public ModelValueInstantiator<MV> instantiate() {
				return wrapped.instantiate();
			}

			@Override
			public int getExpressionOffset() {
				return wrapped.getExpressionOffset();
			}

			@Override
			public int getExpressionLength() {
				return wrapped.getExpressionLength();
			}

			@Override
			public Object getDescriptor() {
				return null; // No descriptor for wrappers
			}

			@Override
			public List<? extends EvaluatedExpression<?, ?>> getComponents() {
				return Collections.singletonList(wrapped);
			}

			@Override
			public String toString() {
				return wrapped.toString();
			}
		};
	}

	/** @return All expressions that are components of this expression */
	List<? extends ObservableExpression> getComponents();

	/**
	 * @param childIndex The index of the {@link #getComponents() child}
	 * @return The number of characters in this expression occurring before the start of the given child expression
	 */
	int getComponentOffset(int childIndex);

	/** @return The total number of characters in the textual representation of this expression */
	int getExpressionLength();

	/** @return The number of divisions in this expression's text */
	default int getDivisionCount() {
		return 0;
	}

	/**
	 * @param division The index of the division to get the offset for
	 * @return The offset in this expression of the given division
	 */
	default int getDivisionOffset(int division) {
		throw new IndexOutOfBoundsException("0 of 0");
	}

	/**
	 * @param division The index of the division to get the offset for
	 * @return The length of the given division in this expression
	 */
	default int getDivisionLength(int division) {
		throw new IndexOutOfBoundsException("0 of 0");
	}

	/**
	 * Allows replacement of this expression or one or more of its {@link #getComponents() children}. For any expression in the hierarchy:
	 * <ol>
	 * <li>if the function returns a different expression, that is returned. Otherwise...</li>
	 * <li>{@link #replaceAll(Function)} is called for each child. If any of the children are replaced with something different, a new
	 * expression of the same kind as this is returned with the children replaced. Otherwise...</li>
	 * <li>This expression is returned</li>
	 * </ol>
	 *
	 * @param replace The function to replace expressions in this hierarchy
	 * @return The replaced expression
	 */
	ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace);

	/**
	 * @param search The search to apply
	 * @return All of this expression or its descendants that match the given search
	 */
	default BetterList<ObservableExpression> find(Predicate<ObservableExpression> search) {
		boolean thisApplies = search.test(this);
		BetterList<ObservableExpression> children = BetterList.of(getComponents().stream().flatMap(child -> child.find(search).stream()));
		if (thisApplies) {
			if (children.isEmpty())
				return BetterList.of(this);
			else {
				ObservableExpression[] found = new ObservableExpression[children.size() + 1];
				found[0] = this;
				for (int i = 0; i < children.size(); i++)
					found[i + 1] = children.get(i);
				return BetterList.of(found);
			}
		} else
			return children;
	}

	/**
	 * @param env The environment in which the expression will be evaluated
	 * @return A best guess as to the model type that this expression would evaluate to in the given environment
	 * @throws ExpressoCompilationException If the model type could not be evaluated
	 */
	default ModelType<?> getModelType(CompiledExpressoEnv env) throws ExpressoCompilationException {
		return getModelType(env, 0);
	}

	/**
	 * @param env The environment in which the expression will be evaluated
	 * @param expressionOffset The offset of this expression in the evaluated root
	 * @return A best guess as to the model type that this expression would evaluate to in the given environment
	 * @throws ExpressoCompilationException If the model type could not be evaluated
	 */
	ModelType<?> getModelType(CompiledExpressoEnv env, int expressionOffset) throws ExpressoCompilationException;

	/**
	 * Attempts to evaluate this expression
	 *
	 * @param <M> The model type to evaluate the expression as
	 * @param <MV> The model instance type to evaluate the expression as
	 * @param <EX> The type of exception thrown by the handler in response to an {@link ExpressoInterpretationException}
	 * @param <TX> The type of exception thrown by the handler in response to a {@link TypeConversionException}
	 * @param type The model instance type to evaluate the expression as
	 * @param env The environment in which to evaluate the expression
	 * @param expressionOffset The offset of this expression in the evaluated root
	 * @param exHandler The exception handler
	 * @return A value container to generate the expression's value from a {@link ModelSetInstance model instance}
	 * @throws ExpressoInterpretationException If the expression cannot be evaluated
	 * @throws EX If the expression cannot be evaluated in the given environment
	 * @throws TX If this expression could not be interpreted as the given type
	 */
	default <M, MV extends M, EX extends Throwable, TX extends Throwable> EvaluatedExpression<M, MV> evaluate(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env, int expressionOffset,
		ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, EX, TX> exHandler)
			throws ExpressoInterpretationException, EX, TX {
		EvaluatedExpression<M, MV> value = evaluateInternal(type, env, expressionOffset, exHandler);
		if (value == null)
			return null;
		InterpretedValueSynth<M, MV> cast = value.as(type, env, exHandler.twoOnly());
		if (cast instanceof EvaluatedExpression) // Generally means a cast was not necessary
			return (EvaluatedExpression<M, MV>) cast;
		else
			return evEx2(expressionOffset, getExpressionLength(), cast, value.getDescriptor(), value.getComponents(), value.getDivisions());
	}

	/**
	 * Does the work of interpreting this expression, but without type-checking or conversion
	 *
	 * @param <M> The model type to evaluate the expression as
	 * @param <MV> The model instance type to evaluate the expression as
	 * @param <EX> The type of exception thrown by the handler in response to an {@link ExpressoInterpretationException}
	 * @param type The model instance type to evaluate the expression as
	 * @param env The environment in which to evaluate the expression
	 * @param expressionOffset The offset of this expression in the evaluated root
	 * @param exHandler The exception handler
	 * @return A value container to generate the expression's value from a {@link ModelSetInstance model instance}
	 * @throws ExpressoInterpretationException If the expression cannot be evaluated
	 * @throws EX If the expression cannot be evaluated in the given environment as the given type
	 */
	<M, MV extends M, EX extends Throwable> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
			throws ExpressoInterpretationException, EX;

	/**
	 * An expression that always returns a constant value
	 *
	 * @param <T> The type of the value
	 */
	class LiteralExpression<T> implements ObservableExpression {
		private final String theText;
		private final T theValue;

		/**
		 * @param text The parsed expression
		 * @param value The value
		 */
		public LiteralExpression(String text, T value) {
			theText = text;
			theValue = value;
		}

		public T getValue() {
			return theValue;
		}

		@Override
		public List<? extends ObservableExpression> getComponents() {
			return Collections.emptyList();
		}

		@Override
		public int getComponentOffset(int childIndex) {
			throw new IndexOutOfBoundsException(childIndex + " of 0");
		}

		@Override
		public int getExpressionLength() {
			return theText.length();
		}

		@Override
		public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
			return replace.apply(this);
		}

		@Override
		public ModelType<?> getModelType(CompiledExpressoEnv env, int expressionOffset) {
			return ModelTypes.Value;
		}

		@Override
		public <M, MV extends M, EX extends Throwable> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type,
			InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
				throws EX {
			if (type.getModelType() != ModelTypes.Value) {
				if (theValue == null)
					return ObservableExpression.evEx(expressionOffset, getExpressionLength(),
						InterpretedValueSynth.literal(type, null, "null"), this);
				else {
					exHandler.handle1(new ExpressoInterpretationException(theText, env.reporting().getPosition(), getExpressionLength()));
					return null;
				}
			}
			if (theValue == null) {
				if (type.getType(0).isPrimitive()) {
					exHandler
					.handle1(new ExpressoInterpretationException("Cannot assign null to a primitive type (" + type.getType(0) + ")",
						env.reporting().getPosition(), getExpressionLength()));
					return null;
				}
				MV value = (MV) createValue(type.getType(0), null);
				return ObservableExpression.evEx(expressionOffset, getExpressionLength(),
					InterpretedValueSynth.literal(type, value, theText), this);
			} else if (TypeTokens.get().isInstance(type.getType(0), theValue)) {
				MV value = (MV) createValue(type.getType(0), theValue);
				return ObservableExpression.evEx(expressionOffset, getExpressionLength(),
					InterpretedValueSynth.literal((ModelInstanceType<M, MV>) ModelTypes.Value.forType(theValue.getClass()), value, theText),
					this);
			} else if (TypeTokens.get().isAssignable(type.getType(0), TypeTokens.get().of(theValue.getClass()))) {
				TypeToken<Object> targetType = (TypeToken<Object>) type.getType(0);
				TypeTokens.TypeConverter<T, ?, ?, Object> convert = (TypeConverter<T, ?, ?, Object>) TypeTokens.get().getCast(targetType,
					TypeTokens.get().of((Class<T>) theValue.getClass()));
				targetType = convert.getConvertedType();
				MV value = (MV) createValue(targetType, convert.apply(theValue));
				return ObservableExpression.evEx(expressionOffset, getExpressionLength(),
					InterpretedValueSynth.literal((ModelInstanceType<M, MV>) ModelTypes.Value.forType(targetType), value, theText), this);
			} else {
				// Don't throw this. Maybe the type architecture can convert it.
				// throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
				// "'" + theText + "' cannot be evaluated as a " + type);
				MV value = (MV) createValue(TypeTokens.get().of(theValue.getClass()), theValue);
				return ObservableExpression.evEx(expressionOffset, getExpressionLength(),
					InterpretedValueSynth.literal((ModelInstanceType<M, MV>) ModelTypes.Value.forType(theValue.getClass()), value, theText),
					this);
			}
		}

		SettableValue<?> createValue(TypeToken<?> type, Object value) {
			return SettableValue.asSettable(ObservableValue.of((TypeToken<Object>) type, value), //
				__ -> "Literal value '" + theText + "'");
		}

		@Override
		public String toString() {
			return theText;
		}
	}
}

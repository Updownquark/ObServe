package org.observe.expresso.ops;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.TypeConversionException;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.Stamped;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.CollectionUtils.ElementSyncAction;
import org.qommons.collect.CollectionUtils.ElementSyncInput;
import org.qommons.ex.ExceptionHandler;
import org.qommons.ex.NeverThrown;
import org.qommons.io.ErrorReporting;

import com.google.common.reflect.TypeToken;

/** An expression representing the assignment of one value into another */
public class AssignmentExpression implements ObservableExpression {
	private final ObservableExpression theTarget;
	private final ObservableExpression theValue;

	/**
	 * @param target The variable or field that will be assigned
	 * @param value The value to assign to the variable or field
	 */
	public AssignmentExpression(ObservableExpression target, ObservableExpression value) {
		theTarget = target;
		theValue = value;
	}

	/** @return The variable or field that will be assigned */
	public ObservableExpression getTarget() {
		return theTarget;
	}

	/** @return The value to assign to the variable or field */
	public ObservableExpression getValue() {
		return theValue;
	}

	@Override
	public int getComponentOffset(int childIndex) {
		switch (childIndex) {
		case 0:
			return 0;
		case 1:
			return theTarget.getExpressionLength() + 1;
		default:
			throw new IndexOutOfBoundsException(childIndex + " of 2");
		}
	}

	@Override
	public int getExpressionLength() {
		return theTarget.getExpressionLength() + 1 + theValue.getExpressionLength();
	}

	@Override
	public List<? extends ObservableExpression> getComponents() {
		return QommonsUtils.unmodifiableCopy(theTarget, theValue);
	}

	@Override
	public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
		ObservableExpression replacement = replace.apply(this);
		if (replacement != this)
			return replacement;
		ObservableExpression target = theTarget.replaceAll(replace);
		ObservableExpression value = theValue.replaceAll(replace);
		if (target != theTarget || value != theValue)
			return new AssignmentExpression(target, value);
		return this;
	}

	@Override
	public ModelType<?> getModelType(CompiledExpressoEnv env, int expressionOffset) {
		return ModelTypes.Action;
	}

	@Override
	public <M, MV extends M, EX extends Throwable> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
			throws ExpressoInterpretationException, EX {
		if (type.getModelType() != ModelTypes.Action)
			throw new ExpressoInterpretationException("Assignments cannot be used as " + type.getModelType() + "s",
				env.reporting().getPosition(), getExpressionLength());
		return (EvaluatedExpression<M, MV>) this
			.<Object, Object, EX> _evaluate((ModelInstanceType<ObservableAction, ObservableAction>) type, env, expressionOffset, exHandler);
	}

	private <S, T extends S, EX extends Throwable> EvaluatedExpression<ObservableAction, ObservableAction> _evaluate(
		ModelInstanceType<ObservableAction, ObservableAction> type, InterpretedExpressoEnv env, int expressionOffset,
		ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler) throws ExpressoInterpretationException, EX {
		EvaluatedExpression<SettableValue<?>, SettableValue<S>> target;
		ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, EX, NeverThrown> doubleX = exHandler
			.stack(ExceptionHandler.holder());
		target = theTarget.evaluate(ModelTypes.Value.anyAs(), env, expressionOffset, doubleX);
		if (doubleX.get2() != null) {
			exHandler.handle1(new ExpressoInterpretationException(doubleX.get2().getMessage(), env.reporting().getPosition(),
				theTarget.getExpressionLength(), doubleX.get2()));
			return null;
		} else if (target == null)
			return null;
		EvaluatedExpression<SettableValue<?>, SettableValue<T>> value;
		int valueOffset = expressionOffset + theTarget.getExpressionLength() + 1;
		try (Transaction t = Invocation.asAction()) {
			value = theValue.evaluate(
				ModelTypes.Value.forType((TypeToken<T>) TypeTokens.get().getExtendsWildcard(target.getType().getType(0))),
				env.at(theTarget.getExpressionLength() + 1), valueOffset, doubleX);
			if (doubleX.get2() != null) {
				exHandler.handle1(new ExpressoInterpretationException(doubleX.get2().getMessage(),
					env.reporting().at(valueOffset).getPosition(), theValue.getExpressionLength(), doubleX.get2()));
				return null;
			} else if (value == null)
				return null;
		}
		boolean listAction = List.class.isAssignableFrom(TypeTokens.getRawType(target.getType().getType(0)));
		ErrorReporting reporting = env.reporting();
		return ObservableExpression.evEx(expressionOffset, getExpressionLength(),
			new InterpretedValueSynth<ObservableAction, ObservableAction>() {
			@Override
			public ModelType<ObservableAction> getModelType() {
				return ModelTypes.Action;
			}

			@Override
			public ModelInstanceType<ObservableAction, ObservableAction> getType() {
				return ModelTypes.Action.instance();
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return QommonsUtils.unmodifiableCopy(target, value);
			}

			@Override
			public ModelValueInstantiator<ObservableAction> instantiate() throws ModelInstantiationException {
					return new Instantiator<>(target.instantiate(), value.instantiate(), reporting, listAction);
			}

			@Override
			public String toString() {
				return AssignmentExpression.this.toString();
			}
		}, null, target, value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(theTarget, theValue);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof AssignmentExpression && theTarget.equals(((AssignmentExpression) obj).theTarget)
			&& theValue.equals(((AssignmentExpression) obj).theValue);
	}

	@Override
	public String toString() {
		return theTarget + "=" + theValue;
	}

	static class Instantiator<S, T extends S> implements ModelValueInstantiator<ObservableAction> {
		private final ModelValueInstantiator<SettableValue<S>> theTarget;
		private final ModelValueInstantiator<SettableValue<T>> theSource;
		private final ErrorReporting theReporting;
		private final boolean isList;

		Instantiator(ModelValueInstantiator<SettableValue<S>> target, ModelValueInstantiator<SettableValue<T>> source,
			ErrorReporting reporting, boolean list) {
			theTarget = target;
			theSource = source;
			theReporting = reporting;
			isList = list;
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			theTarget.instantiate();
			theSource.instantiate();
		}

		@Override
		public ObservableAction get(ModelSetInstance models) throws ModelInstantiationException {
			SettableValue<S> ctxValue = theTarget.get(models);
			SettableValue<T> valueValue = theSource.get(models);
			if (isList)
				return new ListAssignmentAction<>((SettableValue<List<Object>>) ctxValue, (SettableValue<List<Object>>) valueValue,
					theReporting);
			else
				return ctxValue.assignmentTo(valueValue, err -> theReporting.error(null, err));
		}

		@Override
		public ObservableAction forModelCopy(ObservableAction value2, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			SettableValue<S> oldTarget = theTarget.get(sourceModels);
			SettableValue<S> newTarget = theTarget.forModelCopy(oldTarget, sourceModels, newModels);
			SettableValue<T> oldSource = theSource.get(sourceModels);
			SettableValue<T> newSource = theSource.get(newModels);
			if (oldTarget == newTarget && oldSource == newSource)
				return value2;
			else
				return newTarget.assignmentTo(newSource);
		}
	}

	/**
	 * <p>
	 * This action supports the assignment operation for lists in the situation where the target value cannot be assigned directly.
	 * </p>
	 * <p>
	 * In this case, this action will attempt to synchronize the values from the source list into the target.
	 * </p>
	 *
	 * @param <T> The type of values in the list
	 * @param <C> The type of the list
	 */
	static class ListAssignmentAction<T, C extends List<T>> implements ObservableAction {
		private final SettableValue<C> theTarget;
		private final SettableValue<? extends C> theSource;
		private final ErrorReporting theReporting;

		public ListAssignmentAction(SettableValue<C> target, SettableValue<? extends C> source, ErrorReporting reporting) {
			theTarget = target;
			theSource = source;
			theReporting = reporting;
		}

		@Override
		public void act(Object cause) throws IllegalStateException {
			if (theTarget.isEnabled().get() == null) {
				try {
					C newValue = theSource.get();
					theTarget.set(newValue, cause);
				} catch (IllegalArgumentException e) {
					theReporting.error("Assignment failed", e);
				}
			} else {
				C target = theTarget.get();
				if (target == null)
					theReporting.error("Cannot synchronize with null target collection");
				else {
					C source = theSource.get();
					try (Transaction targetT = Transactable.lock(target, true, null); //
						Transaction sourceT = Transactable.lock(source, false, null)) {
						if (source == null)
							target.clear();
						else {
							boolean ordered = target instanceof BetterList ? !((BetterList<T>) target).isContentControlled() : true;
							CollectionUtils.synchronize(target, source).adjust(new CollectionUtils.CollectionSynchronizer<T, T>() {
								@Override
								public boolean getOrder(ElementSyncInput<T, T> element) {
									return true;
								}

								@Override
								public ElementSyncAction leftOnly(ElementSyncInput<T, T> element) {
									return element.remove();
								}

								@Override
								public ElementSyncAction rightOnly(ElementSyncInput<T, T> element) {
									return element.useValue(element.getRightValue());
								}

								@Override
								public ElementSyncAction common(ElementSyncInput<T, T> element) {
									if (element.getLeftValue() != element.getRightValue())
										element.useValue(element.getRightValue());
									return element.preserve();
								}
							}, ordered ? CollectionUtils.AdjustmentOrder.RightOrder : CollectionUtils.AdjustmentOrder.AddLast);
						}
					} catch (RuntimeException e) {
						theReporting.error("Synchronization failed", e);
					}
				}
			}
		}

		@Override
		public ObservableValue<String> isEnabled() {
			ObservableValue<String> simpleAssignmentEnabled = theTarget.refresh(theTarget.noInitChanges())
				.map(v -> theTarget.isAcceptable(v));
			ObservableValue<String> listAssignmentEnabled = ObservableValue.of(() -> {
				C target = theTarget.get();
				if (!(target instanceof BetterList))
					return null; // No way to tell, hope for the best

				// This here isn't perfect. It can't be. It attempts to determine the compatibility of all changes that need to be made
				// without performing any of those changes.
				// This could fail in some situations. If, for example, the collection has a size limit, any single add may be admissible,
				// but multiple adds would fail.
				// I think the situations where this would fail are weird enough that this is still value added.
				BetterList<T> betterTarget = (BetterList<T>) target;
				C source = theSource.get();
				boolean ordered = !betterTarget.isContentControlled();
				// We could compile a huge message of every error that we get (they could be different),
				// but I think it's sufficient (and better) to just use one error. Most of the time they'll all be the same anyway.
				String[] message = new String[1];
				try (Transaction targetT = Transactable.lock(target, false, null); //
					Transaction sourceT = Transactable.lock(source, false, null)) {
					CollectionUtils.synchronize(betterTarget.elements(), source, (el, s) -> Objects.equals(el.get(), s))//
					.adjust(new CollectionUtils.CollectionSynchronizer<CollectionElement<T>, T>() {
						@Override
						public boolean getOrder(ElementSyncInput<CollectionElement<T>, T> element) {
							return true;
						}

						@Override
						public ElementSyncAction leftOnly(ElementSyncInput<CollectionElement<T>, T> element) {
							if (message[0] == null)
								message[0] = betterTarget.mutableElement(element.getLeftValue().getElementId()).canRemove();
							return element.preserve();
						}

						@Override
						public ElementSyncAction rightOnly(ElementSyncInput<CollectionElement<T>, T> element) {
							if (message[0] == null) {
								if (ordered) {
									CollectionElement<T> after = element.getTargetIndex() == betterTarget.size() ? null
										: betterTarget.getElement(element.getTargetIndex());
									CollectionElement<T> before = after == null ? betterTarget.getTerminalElement(false)
										: betterTarget.getAdjacentElement(after.getElementId(), false);
									message[0] = betterTarget.canAdd(element.getRightValue(), CollectionElement.getElementId(after),
										CollectionElement.getElementId(before));
								} else
									message[0] = betterTarget.canAdd(element.getRightValue());
							}
							return element.preserve();
						}

						@Override
						public ElementSyncAction common(ElementSyncInput<CollectionElement<T>, T> element) {
							if (element.getLeftValue().get() == element.getRightValue())
								return element.preserve();
							else if (message[0] == null)
								message[0] = betterTarget.mutableElement(element.getLeftValue().getElementId())
								.isAcceptable(element.getRightValue());
							return element.preserve();
						}
					}, ordered ? CollectionUtils.AdjustmentOrder.RightOrder : CollectionUtils.AdjustmentOrder.AddLast);
				}
				return message[0];
			}, () -> Stamped.compositeStamp(theTarget.getStamp(), theSource.getStamp()), //
				Observable.or(theTarget.noInitChanges(), theSource.noInitChanges()));
			return ObservableValue.flatten(theTarget.isEnabled().map(e -> e == null ? simpleAssignmentEnabled : listAssignmentEnabled));

		}

		@Override
		public String toString() {
			return theTarget + "=" + theSource;
		}
	}
}

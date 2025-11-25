package org.observe.expresso.ops;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.observe.Eventable;
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
import org.qommons.StringUtils;
import org.qommons.ThreadConstrained;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.CollectionUtils.ElementSyncAction;
import org.qommons.collect.CollectionUtils.ElementSyncInput;
import org.qommons.collect.ListElement;
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
		EvaluatedExpression<ObservableAction, ObservableAction> action = this
			.<Object, Object, EX> _evaluate((ModelInstanceType<ObservableAction, ObservableAction>) type, env, expressionOffset, exHandler);
		if (action == null)
			return null;
		if (action.getType().equals(type))
			return (EvaluatedExpression<M, MV>) action;
		ExceptionHandler.Single<TypeConversionException, NeverThrown> tce = ExceptionHandler.holder(true);
		InterpretedValueSynth<M, MV> converted = action.as(type, env, tce);
		if (converted != null)
			return ObservableExpression.evEx(expressionOffset, getExpressionLength(), converted, null, action);
		else
			throw new ExpressoInterpretationException(
				"Assignments can only be actions and cannot be converted to " + StringUtils.getIndefiniteArticle(type.toString()) + type,
				env.reporting().getPosition(), getExpressionLength(), tce.get1());
	}

	private <S, T extends S, EX extends Throwable> EvaluatedExpression<ObservableAction, ObservableAction> _evaluate(
		ModelInstanceType<ObservableAction, ObservableAction> type, InterpretedExpressoEnv env, int expressionOffset,
		ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler) throws ExpressoInterpretationException, EX {
		EvaluatedExpression<SettableValue<?>, SettableValue<S>> target;
		ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, EX, NeverThrown> doubleX = exHandler
			.stack(ExceptionHandler.holder(exHandler.isInstantiating()));
		target = theTarget.evaluate(ModelTypes.Value.anyAs(), env, expressionOffset, doubleX.use());
		if (doubleX.hasException2()) {
			exHandler.handle1(() -> new ExpressoInterpretationException(doubleX.get2().getMessage(), env.reporting().getPosition(),
				theTarget.getExpressionLength(), doubleX.get2()));
			return null;
		} else if (target == null)
			return null;
		EvaluatedExpression<SettableValue<?>, SettableValue<T>> value;
		int myValueOffset = theTarget.getExpressionLength() + 1;
		int valueOffset = expressionOffset + myValueOffset;
		try (Transaction t = Invocation.asAction()) {
			TypeToken<S> targetType = (TypeToken<S>) target.getType().getType(0);
			if (SettableValue.class.isAssignableFrom(TypeTokens.getRawType(targetType))) {
				/* There's a weird case where if there's a field on a java object of type SettableValue<T>
				 * and in expresso one tries to assign a value of type T to it,
				 * the code would, as previously written, interpret the right hand side as a SettableValue<T> with a constant value,
				 * and attempt to assign the constant SettableValue to the SettableValue field.
				 * This is typically not what is intended.  If the field is final, the assignment is always disabled.
				 * If it's not final then it will succeed, but will often not do what the user intended.
				 * This code block is a workaround for this.
				 */
				TypeToken<T> valueType = (TypeToken<T>) targetType.resolveType(SettableValue.class.getTypeParameters()[0]);
				value = theValue.evaluate(ModelTypes.Value.forType(TypeTokens.get().getExtendsWildcard(valueType)),
					env.at(myValueOffset), valueOffset, doubleX.use());
				if (doubleX.hasException2()) {
					EvaluatedExpression<SettableValue<?>, SettableValue<S>> target2 = theTarget.evaluate(
						ModelTypes.Value.forType((TypeToken<S>) TypeTokens.get().getSuperWildcard(value.getType().getType(0))), env,
						expressionOffset, doubleX.clear2());
					if (target2 != null)
						target = target2;
					else {
						value = null;
						doubleX.clear2();
					}
				} else {
					value = null;
					doubleX.clear2();
				}
			} else
				value = null;
			if (value == null) {
				value = theValue.evaluate(
					ModelTypes.Value.forType((TypeToken<T>) TypeTokens.get().getExtendsWildcard(target.getType().getType(0))),
					env.at(myValueOffset), valueOffset, doubleX.use());
				if (doubleX.hasException2()) {
					exHandler.handle1(() -> new ExpressoInterpretationException(doubleX.get2().getMessage(),
						env.reporting().at(myValueOffset).getPosition(), theValue.getExpressionLength(), doubleX.get2()));
					return null;
				} else if (value == null)
					return null;
			}
		}
		EvaluatedExpression<SettableValue<?>, SettableValue<S>> fTarget = target;
		EvaluatedExpression<SettableValue<?>, SettableValue<T>> fValue = value;
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
				return QommonsUtils.unmodifiableCopy(fTarget, fValue);
			}

			@Override
			public ModelValueInstantiator<ObservableAction> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(fTarget.instantiate(), fValue.instantiate(), reporting, listAction);
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
			else if (isList)
				return new ListAssignmentAction<>((SettableValue<List<Object>>) newTarget, (SettableValue<List<Object>>) newSource,
					theReporting);
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
		public boolean isEventing() {
			if (theTarget.isEventing())
				return true;
			C target = theTarget.get();
			return target != null && target instanceof Eventable && ((Eventable) target).isEventing();
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
					CollectionUtils
					.synchronize((BetterList<ListElement<T>>) betterTarget.elements(), source, (el, s) -> Objects.equals(el.get(), s))//
					.adjust(new CollectionUtils.CollectionSynchronizer<ListElement<T>, T>() {
						@Override
						public boolean getOrder(ElementSyncInput<ListElement<T>, T> element) {
							return true;
						}

						@Override
						public ElementSyncAction leftOnly(ElementSyncInput<ListElement<T>, T> element) {
							if (message[0] == null)
								message[0] = betterTarget.mutableElement(element.getLeftValue().getElementId()).canRemove();
							return element.preserve();
						}

						@Override
						public ElementSyncAction rightOnly(ElementSyncInput<ListElement<T>, T> element) {
							if (message[0] == null) {
								if (ordered) {
									CollectionElement<T> after = element.getTargetIndex() == betterTarget.size() ? null
										: betterTarget.getElement(element.getTargetIndex());
									CollectionElement<T> before = after == null ? betterTarget.getTerminalElement(false)
											: after.getAdjacent(false);
									message[0] = betterTarget.canAdd(element.getRightValue(), CollectionElement.getElementId(after),
										CollectionElement.getElementId(before));
								} else
									message[0] = betterTarget.canAdd(element.getRightValue());
							}
							return element.preserve();
						}

						@Override
						public ElementSyncAction common(ElementSyncInput<ListElement<T>, T> element) {
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
			}, Observable.or(theTarget.noInitChanges(), theSource.noInitChanges()));
			ObservableValue<ObservableValue<String>> toFlatten = theTarget.isEnabled()
				.map(e -> e == null ? simpleAssignmentEnabled : listAssignmentEnabled);
			return new ObservableValue.FlattenedObservableValue<String>(toFlatten, null) {
				@Override
				public ThreadConstraint getThreadConstraint() {
					return ThreadConstrained.getThreadConstraint(theTarget, theSource);
				}
			};

		}

		@Override
		public String toString() {
			return theTarget + "=" + theSource;
		}
	}
}

package org.observe.expresso.ops;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.config.QonfigInterpretationException;

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
	public List<? extends ObservableExpression> getChildren() {
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
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws QonfigInterpretationException {
		if (type.getModelType() != ModelTypes.Action)
			throw new QonfigInterpretationException("Assignments cannot be used as " + type.getModelType() + "s");
		ValueContainer<SettableValue<?>, SettableValue<Object>> context = theTarget
			.evaluate((ModelInstanceType<SettableValue<?>, SettableValue<Object>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any(), env);
		boolean isVoid = type.getType(0).getType() == void.class || type.getType(0).getType() == Void.class;
		if (!isVoid && !TypeTokens.get().isAssignable(type.getType(0), context.getType().getType(0)))
			throw new QonfigInterpretationException(
				"Cannot assign " + context + ", type " + type.getType(0) + " to " + context.getType().getType(0));
		ValueContainer<SettableValue<?>, SettableValue<Object>> value;
		try (Transaction t = Invocation.asAction()) {
			value = theValue.evaluate(
				ModelTypes.Value.forType((TypeToken<Object>) TypeTokens.get().getExtendsWildcard(context.getType().getType(0))), env);
		}
		return (ValueContainer<M, MV>) new ValueContainer<ObservableAction<?>, ObservableAction<?>>() {
			@Override
			public ModelInstanceType<ObservableAction<?>, ObservableAction<?>> getType() {
				if (isVoid)
					return (ModelInstanceType<ObservableAction<?>, ObservableAction<?>>) type;
				else
					return (ModelInstanceType<ObservableAction<?>, ObservableAction<?>>) (ModelInstanceType<?, ?>) ModelTypes.Action
						.forType(context.getType().getType(0));
			}

			@Override
			public ObservableAction<?> get(ModelSetInstance models) {
				SettableValue<Object> ctxValue = context.get(models);
				SettableValue<Object> valueValue = value.get(models);
				return ctxValue.assignmentTo(valueValue);
			}

			@Override
			public String toString() {
				return AssignmentExpression.this.toString();
			}
		};
	}

	@Override
	public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ExpressoEnv env)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not implemented");
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
}
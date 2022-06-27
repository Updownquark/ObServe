package org.observe.expresso.ops;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.RuntimeValuePlaceholder;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.qommons.TriFunction;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class LambdaExpression implements ObservableExpression {
	private final List<String> theParameters;
	private final ObservableExpression theBody;

	public LambdaExpression(List<String> parameters, ObservableExpression body) {
		theParameters = parameters;
		theBody = body;
	}

	public List<String> getParameters() {
		return theParameters;
	}

	public ObservableExpression getBody() {
		return theBody;
	}

	@Override
	public List<? extends ObservableExpression> getChildren() {
		return Collections.singletonList(theBody);
	}

	@Override
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not yet implemented");
	}

	@Override
	public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ExpressoEnv env)
		throws QonfigInterpretationException {
		return new MethodFinder<P1, P2, P3, T>(targetType) {
			@Override
			public Function<ModelSetInstance, TriFunction<P1, P2, P3, T>> find3() throws QonfigInterpretationException {
				QonfigInterpretationException ex = null;
				String exMsg = null;
				for (MethodOption option : theOptions) {
					if (option.size() != theParameters.size()) {
						if (exMsg == null)
							exMsg = theParameters.size() + " parameter" + (theParameters.size() == 1 ? "" : "s") + " provided, not "
								+ option.size();
						continue;
					}
					ObservableModelSet.WrappedBuilder wrappedModelsBuilder = env.getModels().wrap();
					ObservableModelSet.RuntimeValuePlaceholder<?, ?>[] placeholders = new ObservableModelSet.RuntimeValuePlaceholder[theParameters
					                                                                                                                 .size()];
					for (int i = 0; i < theParameters.size(); i++)
						placeholders[i] = configureParameter(wrappedModelsBuilder, theParameters.get(i), option.resolve(i));
					ObservableModelSet.Wrapped wrappedModels = wrappedModelsBuilder.build();
					ValueContainer<SettableValue<?>, SettableValue<T>> body;
					try {
						body = theBody.evaluate(ModelTypes.Value.forType(targetType), env.with(wrappedModels, null));
					} catch (QonfigInterpretationException e) {
						if (ex == null)
							ex = e;
						continue;
					}
					ArgMaker<P1, P2, P3> argMaker = option.getArgMaker();
					setResultType((TypeToken<T>) body.getType().getType(0));
					return msi -> (p1, p2, p3) -> {
						Object[] args = new Object[theParameters.size()];
						if (argMaker != null)
							argMaker.makeArgs(p1, p2, p3, args, msi);
						ObservableModelSet.WrappedInstanceBuilder instBuilder = wrappedModels.wrap(msi);
						for (int i = 0; i < theParameters.size(); i++)
							instBuilder.with((RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>>) placeholders[i],
								ObservableModelSet.literal((TypeToken<Object>) placeholders[i].getType().getType(0), args[i],
									String.valueOf(args[i])));
						ModelSetInstance wrappedMSI = instBuilder.build();
						return body.apply(wrappedMSI).get();
					};
				}
				if (ex != null)
					throw ex;
				else if (exMsg != null)
					throw new QonfigInterpretationException(exMsg);
				else
					throw new QonfigInterpretationException("No options given");
			}

			private <T2> RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T2>> configureParameter(
				ObservableModelSet.WrappedBuilder modelBuilder, String paramName, TypeToken<T2> paramType) {
				ModelInstanceType<SettableValue<?>, SettableValue<T2>> type = ModelTypes.Value.forType(paramType);
				return modelBuilder.withRuntimeValue(paramName, type);
			}
		};
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		if (theParameters.size() == 1)
			str.append(theParameters.get(0));
		else {
			str.append('(');
			for (int i = 0; i < theParameters.size(); i++) {
				if (i > 0)
					str.append(',');
				str.append(theParameters.get(i));
			}
			str.append(')');
		}
		str.append("->");
		str.append(theBody);
		return str.toString();
	}
}
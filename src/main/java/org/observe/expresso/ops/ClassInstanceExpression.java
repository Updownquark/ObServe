package org.observe.expresso.ops;

import java.text.ParseException;
import java.util.Collections;
import java.util.List;

import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class ClassInstanceExpression implements ObservableExpression {
	private final String theType;

	public ClassInstanceExpression(String type) {
		theType = type;
	}

	public String getType() {
		return theType;
	}

	@Override
	public List<? extends ObservableExpression> getChildren() {
		return Collections.emptyList();
	}

	@Override
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws QonfigInterpretationException {
		if (type.getModelType() != ModelTypes.Value)
			throw new QonfigInterpretationException("A class instance expression can only be evaluated to a value");
		Class<?> clazz;
		try {
			clazz = TypeTokens.getRawType(TypeTokens.get().parseType(theType));
		} catch (ParseException e) {
			throw new QonfigInterpretationException(e.getMessage(), e);
		}
		TypeToken<Class<?>> classType = TypeTokens.get().keyFor(Class.class).parameterized(clazz);
		if (!TypeTokens.get().isAssignable(type.getType(0), classType))
			throw new QonfigInterpretationException(theType + ".class cannot be evaluated as a " + type.getType(0));
		return (ValueContainer<M, MV>) ObservableModelSet.literalContainer(ModelTypes.Value.forType(classType), clazz, theType + ".class");
	}

	@Override
	public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ExpressoEnv env)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not supported for class instance expression");
	}
}

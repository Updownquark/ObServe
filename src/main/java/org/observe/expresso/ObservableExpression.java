package org.observe.expresso;

import java.text.ParseException;

import org.observe.util.ClassView;
import org.observe.util.ModelType.ModelInstanceType;
import org.observe.util.ObservableModelSet;
import org.observe.util.ObservableModelSet.ValueContainer;

public interface ObservableExpression {
	ObservableExpression EMPTY = new ObservableExpression() {
		@Override
		public <M, MV extends M> ValueContainer<M, MV> evaluate(ModelInstanceType<M, MV> type, ObservableModelSet models,
			ClassView classView) throws ParseException {
			return null;
		}
	};

	<M, MV extends M> ObservableModelSet.ValueContainer<M, MV> evaluate(ModelInstanceType<M, MV> type, ObservableModelSet models,
		ClassView classView) throws ParseException;
}

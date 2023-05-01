package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.quick.QuickElement;

public interface MultiValueWidget<T> extends MultiValueRenderable<T> {
	public interface Def<W extends MultiValueWidget<?>> extends MultiValueRenderable.Def<W> {
		CompiledExpression getSelection();

		CompiledExpression getMultiSelection();
	}

	public interface Interpreted<T, W extends MultiValueWidget<T>> extends MultiValueRenderable.Interpreted<T, W> {
		InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getSelection();

		InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getMultiSelection();

		@Override
		W create(QuickElement parent);
	}

	@Override
	Interpreted<? super T, ?> getInterpreted();

	SettableValue<T> getSelection();

	ObservableCollection<T> getMultiSelection();
}

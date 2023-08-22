package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.qonfig.CompiledExpression;

public interface MultiValueWidget<T> extends MultiValueRenderable<T> {
	public interface Def<W extends MultiValueWidget<?>> extends MultiValueRenderable.Def<W> {
		@Override
		CompiledExpression getSelection();

		@Override
		CompiledExpression getMultiSelection();
	}

	public interface Interpreted<T, W extends MultiValueWidget<T>> extends MultiValueRenderable.Interpreted<T, W> {
		@Override
		Def<? super W> getDefinition();

		@Override
		InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getSelection();

		@Override
		InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getMultiSelection();

		@Override
		W create();
	}

	ModelComponentId getSelectedVariable();

	ModelComponentId getRowIndexVariable();

	ModelComponentId getColumnIndexVariable();

	@Override
	SettableValue<T> getSelection();

	@Override
	ObservableCollection<T> getMultiSelection();
}

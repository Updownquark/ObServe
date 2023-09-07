package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.QonfigAttributeGetter;

public interface MultiValueWidget<T> extends MultiValueRenderable<T>, ValueTyped<T> {
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = "multi-value-widget",
		interpretation = Interpreted.class,
		instance = MultiValueWidget.class)
	public interface Def<W extends MultiValueWidget<?>> extends MultiValueRenderable.Def<W>, ValueTyped.Def<W> {
		@QonfigAttributeGetter("selection")
		CompiledExpression getSelection();

		@QonfigAttributeGetter("multi-selection")
		CompiledExpression getMultiSelection();
	}

	public interface Interpreted<T, W extends MultiValueWidget<T>>
		extends MultiValueRenderable.Interpreted<T, W>, ValueTyped.Interpreted<T, W> {
		@Override
		Def<? super W> getDefinition();

		InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getSelection();

		InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getMultiSelection();

		@Override
		W create();
	}

	SettableValue<T> getSelection();

	ObservableCollection<T> getMultiSelection();
}

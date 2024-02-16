package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.QonfigAttributeGetter;

/**
 * A widget that represents multiple values to to the user at once, allowing one or multiple to be selected
 *
 * @param <T> The type of values in the widget
 */
public interface MultiValueWidget<T> extends MultiValueRenderable<T>, ValueTyped<T> {
	/** The XML name of this element */
	public static final String MULTI_VALUE_WIDGET = "multi-value-widget";

	/**
	 * {@link MultiValueWidget} definition
	 *
	 * @param <W> The sub-type of widget to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = MULTI_VALUE_WIDGET,
		interpretation = Interpreted.class,
		instance = MultiValueWidget.class)
	public interface Def<W extends MultiValueWidget<?>> extends MultiValueRenderable.Def<W>, ValueTyped.Def<W> {
		/** @return The currently selected value */
		@QonfigAttributeGetter("selection")
		CompiledExpression getSelection();

		/** @return All currently selected values */
		@QonfigAttributeGetter("multi-selection")
		CompiledExpression getMultiSelection();
	}

	/**
	 * {@link MultiValueWidget} interpretation
	 *
	 * @param <T> The type of values in the widget
	 * @param <W> The sub-type of widget to create
	 */
	public interface Interpreted<T, W extends MultiValueWidget<T>>
	extends MultiValueRenderable.Interpreted<T, W>, ValueTyped.Interpreted<T, W> {
		@Override
		Def<? super W> getDefinition();

		/** @return The currently selected value */
		InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getSelection();

		/** @return All currently selected values */
		InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getMultiSelection();

		@Override
		W create();
	}

	/** @return The currently selected value */
	SettableValue<T> getSelection();

	/** @return All currently selected values */
	ObservableCollection<T> getMultiSelection();
}

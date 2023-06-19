package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.QuickWidget;

public interface MultiValueRenderable<T> extends QuickWidget {
	public static final ExElement.AttributeValueGetter.Expression<MultiValueRenderable<?>, Interpreted<?, ?>, Def<?>, SettableValue<?>, SettableValue<?>> SELECTION = ExElement.AttributeValueGetter
		.<MultiValueRenderable<?>, Interpreted<?, ?>, Def<?>, SettableValue<?>, SettableValue<?>> ofX(Def::getSelection,
			Interpreted::getSelection, MultiValueRenderable::getSelection,
			"The value the the user has selected in the widget, or null if no value or multiple values are selected");
	public static final ExElement.AttributeValueGetter.Expression<MultiValueRenderable<?>, Interpreted<?, ?>, Def<?>, ObservableCollection<?>, ObservableCollection<?>> MULTI_SELECTION = ExElement.AttributeValueGetter
		.<MultiValueRenderable<?>, Interpreted<?, ?>, Def<?>, ObservableCollection<?>, ObservableCollection<?>> ofX(Def::getMultiSelection,
			Interpreted::getMultiSelection, MultiValueRenderable::getMultiSelection,
			"The values the the user has selected in the widget");
	public static final ExElement.AttributeValueGetter<MultiValueRenderable<?>, Interpreted<?, ?>, Def<?>> VALUE_NAME = ExElement.AttributeValueGetter
		.<MultiValueRenderable<?>, Interpreted<?, ?>, Def<?>> of(Def::getValueName, i -> i.getDefinition().getValueName(),
			MultiValueRenderable::getValueName, "The name of the currently applicable value, e.g. for rendering");

	public interface Def<W extends MultiValueRenderable<?>> extends QuickWidget.Def<W> {
		String getValueName();

		CompiledExpression getSelection();

		CompiledExpression getMultiSelection();
	}

	public interface Interpreted<T, W extends MultiValueRenderable<T>> extends QuickWidget.Interpreted<W> {
		@Override
		Def<? super W> getDefinition();

		InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getSelection();

		InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getMultiSelection();

		@Override
		W create(ExElement parent);
	}

	public interface MultiValueRenderContext<T> {
		SettableValue<T> getRenderValue();

		SettableValue<Boolean> isSelected();

		public class Default<T> implements MultiValueRenderContext<T> {
			private final SettableValue<T> theRenderValue;
			private final SettableValue<Boolean> isSelected;

			public Default(SettableValue<T> renderValue, SettableValue<Boolean> selected) {
				theRenderValue = renderValue;
				isSelected = selected;
			}

			@Override
			public SettableValue<T> getRenderValue() {
				return theRenderValue;
			}

			@Override
			public SettableValue<Boolean> isSelected() {
				return isSelected;
			}
		}
	}

	SettableValue<T> getSelection();

	ObservableCollection<T> getMultiSelection();

	String getValueName();

	public void setContext(MultiValueRenderContext<T> ctx) throws ModelInstantiationException;
}

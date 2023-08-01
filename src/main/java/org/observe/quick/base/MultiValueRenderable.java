package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.QuickWidget;

public interface MultiValueRenderable<T> extends QuickWidget {
	public static final SingleTypeTraceability<MultiValueRenderable<?>, Interpreted<?, ?>, Def<?>> MV_RENDERABLE_TRACEABILITY = ElementTypeTraceability
		.<MultiValueRenderable<?>, Interpreted<?, ?>, Def<?>> build(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION,
			"multi-value-renderable")//
		.withAttribute("value-name", Def::getValueName, null)//
		.build();
	public static final SingleTypeTraceability<MultiValueRenderable<?>, Interpreted<?, ?>, Def<?>> MV_WIDGET_TRACEABILITY = ElementTypeTraceability
		.<MultiValueRenderable<?>, Interpreted<?, ?>, Def<?>> build(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION,
			"multi-value-widget")//
		.withAttribute("selection", Def::getSelection, Interpreted::getSelection)//
		.withAttribute("multi-selection", Def::getMultiSelection, Interpreted::getMultiSelection)//
		.build();

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

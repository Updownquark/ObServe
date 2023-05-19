package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.quick.QuickElement;
import org.observe.quick.QuickWidget;

public interface MultiValueRenderable<T> extends QuickWidget {
	public interface Def<W extends MultiValueRenderable<?>> extends QuickWidget.Def<W> {
		String getValueName();
	}

	public interface Interpreted<T, W extends MultiValueRenderable<T>> extends QuickWidget.Interpreted<W> {
		@Override
		Def<? super W> getDefinition();

		@Override
		W create(QuickElement parent);
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

	public void setContext(MultiValueRenderContext<T> ctx) throws ModelInstantiationException;
}

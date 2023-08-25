package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickMouseListener.QuickMouseButtonListener;
import org.observe.quick.QuickWidget;

public interface MultiValueRenderable<T> extends QuickWidget {
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = "multi-value-renderable",
		interpretation = Interpreted.class,
		instance = QuickMouseButtonListener.class)
	public interface Def<W extends MultiValueRenderable<?>> extends QuickWidget.Def<W> {
		@QonfigAttributeGetter("value-name")
		ModelComponentId getValueVariable();
	}

	public interface Interpreted<T, W extends MultiValueRenderable<T>> extends QuickWidget.Interpreted<W> {
		@Override
		Def<? super W> getDefinition();

		@Override
		W create();
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

	ModelComponentId getValueVariable();

	public void setContext(MultiValueRenderContext<T> ctx) throws ModelInstantiationException;
}

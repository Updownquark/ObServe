package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickMouseListener.QuickMouseButtonListener;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

public interface MultiValueRenderable<T> extends QuickWidget {
	public static final String MULTI_VALUE_RENDERABLE = "multi-value-renderable";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = "multi-value-renderable",
		interpretation = Interpreted.class,
		instance = QuickMouseButtonListener.class)
	public interface Def<W extends MultiValueRenderable<?>> extends QuickWidget.Def<W> {
		@QonfigAttributeGetter("active-value-name")
		ModelComponentId getActiveValueVariable();
	}

	public interface Interpreted<T, W extends MultiValueRenderable<T>> extends QuickWidget.Interpreted<W> {
		@Override
		Def<? super W> getDefinition();

		@Override
		W create();
	}

	public interface MultiValueRenderContext<T> {
		SettableValue<T> getActiveValue();

		SettableValue<Boolean> isSelected();

		public class Default<T> implements MultiValueRenderContext<T> {
			private final SettableValue<T> theActiveValue;
			private final SettableValue<Boolean> isSelected;

			public Default(SettableValue<T> activeValue, SettableValue<Boolean> selected) {
				theActiveValue = activeValue;
				isSelected = selected;
			}

			public Default(TypeToken<T> valueType) {
				this(SettableValue.build(valueType).withDescription("activeValue").withValue(TypeTokens.get().getDefaultValue(valueType))
					.build(), SettableValue.build(boolean.class).withDescription("selected").withValue(false).build());
			}

			@Override
			public SettableValue<T> getActiveValue() {
				return theActiveValue;
			}

			@Override
			public SettableValue<Boolean> isSelected() {
				return isSelected;
			}
		}
	}

	ModelComponentId getActiveValueVariable();

	ModelComponentId getSelectedVariable();

	public void setContext(MultiValueRenderContext<T> ctx) throws ModelInstantiationException;
}

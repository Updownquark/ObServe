package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickMouseListener.QuickMouseButtonListener;
import org.observe.quick.QuickWidget;

/**
 * A widget that represents multiple values to the user
 *
 * @param <T> The type of the values
 */
public interface MultiValueRenderable<T> extends QuickWidget {
	/** The XML name of this element */
	public static final String MULTI_VALUE_RENDERABLE = "multi-value-renderable";

	/**
	 * {@link MultiValueRenderable} definition
	 *
	 * @param <W> The sub-type of widget to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = "multi-value-renderable",
		interpretation = Interpreted.class,
		instance = QuickMouseButtonListener.class)
	public interface Def<W extends MultiValueRenderable<?>> extends QuickWidget.Def<W> {
		/**
		 * @return The model ID of the variable by which the active value (the one being rendered or acted upon) will be available to
		 *         expressions
		 */
		@QonfigAttributeGetter("active-value-name")
		ModelComponentId getActiveValueVariable();
	}

	/**
	 * {@link MultiValueRenderable} interpretation
	 *
	 * @param <T> The type of the values
	 * @param <W> The sub-type of widget to create
	 */
	public interface Interpreted<T, W extends MultiValueRenderable<T>> extends QuickWidget.Interpreted<W> {
		@Override
		Def<? super W> getDefinition();

		@Override
		W create();
	}

	/**
	 * Model context for a {@link MultiValueRenderable}
	 *
	 * @param <T> The type of values in the widget
	 */
	public interface MultiValueRenderContext<T> {
		/** @return The active value for the widget */
		SettableValue<T> getActiveValue();

		/** @return Whether the active value is selected */
		SettableValue<Boolean> isSelected();

		/**
		 * Default {@link MultiValueRenderContext} implementation
		 *
		 * @param <T> The type of values in the widget
		 */
		public class Default<T> implements MultiValueRenderContext<T> {
			private final SettableValue<T> theActiveValue;
			private final SettableValue<Boolean> isSelected;

			/**
			 * @param activeValue The active value for the widget
			 * @param selected Whether the active value is selected
			 */
			public Default(SettableValue<T> activeValue, SettableValue<Boolean> selected) {
				theActiveValue = activeValue;
				isSelected = selected;
			}

			/** Creates the context */
			public Default() {
				this(SettableValue.<T> build().withDescription("activeValue").build(),
					SettableValue.<Boolean> build().withDescription("selected").withValue(false).build());
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

	/**
	 * @return The model ID of the variable by which the active value (the one being rendered or acted upon) will be available to
	 *         expressions
	 */
	ModelComponentId getActiveValueVariable();

	/** @return The model ID of the variable by which the selected status of the active value will be available to expressions */
	ModelComponentId getSelectedVariable();

	/**
	 * @param ctx The model context for this widget
	 * @throws ModelInstantiationException If the model context could not be installed
	 */
	public void setContext(MultiValueRenderContext<T> ctx) throws ModelInstantiationException;
}

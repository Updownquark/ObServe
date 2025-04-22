package org.observe.quick.style;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.style.QuickStyled.QuickInstanceStyle;
import org.qommons.config.QonfigElementOrAddOn;

/** A Quick element that has style */
public interface QuickStyledElement extends ExElement {
	/**
	 * The definition of a styled element
	 *
	 * @param <S> The type of the styled element that this definition is for
	 */
	public interface Def<S extends QuickStyledElement> extends ExElement.Def<S> {
		/** @return This element's style */
		default QuickInstanceStyle.Def getStyle() {
			return getAddOnValue(QuickStyled.Def.class, QuickStyled.Def::getStyle);
		}

		/**
		 * Provides the element an opportunity to wrap the standard style with one specific to this element
		 *
		 * @param parentStyle The parent style to inherit from
		 * @param style The style interpreted from the {@link #getStyle() compiled style}
		 * @return The style to use for this element
		 */
		QuickInstanceStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style);

		/**
		 * An abstract {@link Def} implementation
		 *
		 * @param <S> The type of styled object that this definition is for
		 */
		public abstract class Abstract<S extends QuickStyledElement> extends ExElement.Def.Abstract<S> implements Def<S> {
			/**
			 * @param parent The parent container definition
			 * @param type The type that this widget is interpreted from
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}
		}
	}

	/**
	 * An interpretation of a styled element
	 *
	 * @param <S> The type of styled element that this interpretation creates
	 */
	public interface Interpreted<S extends QuickStyledElement> extends ExElement.Interpreted<S> {
		@Override
		Def<? super S> getDefinition();

		/** @return This element's interpreted style */
		default QuickInstanceStyle.Interpreted getStyle() {
			return getAddOnValue(QuickStyled.Interpreted.class, QuickStyled.Interpreted::getStyle);
		}

		/**
		 * Populates and updates this interpretation. Must be called once after being produced by the {@link #getDefinition() definition}.
		 *
		 * @throws ExpressoInterpretationException If any models could not be interpreted from their expressions in this widget or its
		 *         content
		 */
		void updateElement() throws ExpressoInterpretationException;

		/**
		 * An abstract {@link Interpreted} implementation
		 *
		 * @param <S> The type of widget that this interpretation is for
		 */
		public abstract class Abstract<S extends QuickStyledElement> extends ExElement.Interpreted.Abstract<S> implements Interpreted<S> {
			/**
			 * @param definition The definition producing this interpretation
			 * @param parent The interpreted parent
			 */
			protected Abstract(Def<? super S> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super S> getDefinition() {
				return (Def<? super S>) super.getDefinition();
			}

			@Override
			public void updateElement() throws ExpressoInterpretationException {
				update();
			}
		}
	}

	/** @return This element's style */
	default QuickInstanceStyle getStyle() {
		return getAddOnValue(QuickStyled.class, QuickStyled::getStyle);
	}

	/** An abstract {@link QuickStyledElement} implementation */
	public abstract class Abstract extends ExElement.Abstract implements QuickStyledElement {
		/** @param id The element identifier for this element */
		protected Abstract(Object id) {
			super(id);
		}
	}
}

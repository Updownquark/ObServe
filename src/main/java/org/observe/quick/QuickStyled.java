package org.observe.quick;

import java.util.HashMap;
import java.util.Map;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.style.CompiledStyleApplication;
import org.observe.quick.style.InterpretedStyleApplication;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.StyleQIS;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

/** A Quick element that has style */
public interface QuickStyled extends QuickElement {
	/**
	 * The definition of a styled element
	 *
	 * @param <S> The type of the styled element that this definition is for
	 */
	public interface Def<S extends QuickStyled> extends QuickElement.Def<S> {
		/** @return This element's style */
		QuickCompiledStyle getStyle();

		/**
		 * An abstract {@link Def} implementation
		 *
		 * @param <S> The type of styled object that this definition is for
		 */
		public abstract class Abstract<S extends QuickStyled> extends QuickElement.Def.Abstract<S> implements Def<S> {
			private QuickCompiledStyle theStyle;

			/**
			 * @param parent The parent container definition
			 * @param element The element that this widget is interpreted from
			 */
			public Abstract(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@Override
			public QuickCompiledStyle getStyle() {
				return theStyle;
			}

			@Override
			public Def.Abstract<S> update(AbstractQIS<?> session) throws QonfigInterpretationException {
				super.update(session);
				theStyle = getStyleSession().getStyle();
				return this;
			}
		}
	}

	/** Needed to {@link QuickStyled.Interpreted#update(QuickInterpretationCache) update} an interpreted widget */
	class QuickInterpretationCache {
		/** A cache of interpreted style applications */
		public final Map<CompiledStyleApplication, InterpretedStyleApplication> applications = new HashMap<>();
	}

	/**
	 * An interpretation of a styled element
	 *
	 * @param <S> The type of styled element that this interpretation creates
	 */
	public interface Interpreted<S extends QuickStyled> extends QuickElement.Interpreted<S> {
		@Override
		Def<? super S> getDefinition();

		/** @return This element's interpreted style */
		QuickInterpretedStyle getStyle();

		/**
		 * Populates and updates this interpretation. Must be called once after being produced by the {@link #getDefinition() definition}.
		 *
		 * @param cache The cache to use to interpret the widget
		 * @return This interpretation
		 * @throws ExpressoInterpretationException If any models could not be interpreted from their expressions in this widget or its
		 *         content
		 */
		Interpreted<S> update(QuickStyled.QuickInterpretationCache cache) throws ExpressoInterpretationException;

		/**
		 * An abstract {@link Interpreted} implementation
		 *
		 * @param <S> The type of widget that this interpretation is for
		 */
		public abstract class Abstract<S extends QuickStyled> extends QuickElement.Interpreted.Abstract<S> implements Interpreted<S> {
			private QuickInterpretedStyle theStyle;

			/**
			 * @param definition The definition producing this interpretation
			 * @param parent The interpreted parent
			 */
			public Abstract(Def<? super S> definition, QuickElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super S> getDefinition() {
				return (Def<? super S>) super.getDefinition();
			}

			@Override
			public QuickInterpretedStyle getStyle() {
				return theStyle;
			}

			@Override
			public Interpreted.Abstract<S> update(QuickStyled.QuickInterpretationCache cache) throws ExpressoInterpretationException {
				super.update();
				QuickElement.Interpreted<?> parent = getParentElement();
				while (parent != null && !(parent instanceof QuickStyled.Interpreted))
					parent = parent.getParentElement();
				theStyle = getDefinition().getStyle().interpret(parent == null ? null : ((QuickStyled.Interpreted<?>) parent).getStyle(),
					cache.applications);
				return this;
			}
		}
	}

	@Override
	Interpreted<?> getInterpreted();

	/** An abstract {@link QuickStyled} implementation */
	public abstract class Abstract extends QuickElement.Abstract implements QuickStyled {
		/**
		 * @param interpreted The interpretation that is creating this element
		 * @param parent The parent element
		 */
		public Abstract(QuickStyled.Interpreted<?> interpreted, QuickElement parent) {
			super(interpreted, parent);
		}

		@Override
		public QuickStyled.Interpreted<?> getInterpreted() {
			return (QuickStyled.Interpreted<?>) super.getInterpreted();
		}

		@Override
		public QuickStyled.Abstract update(ModelSetInstance models) throws ModelInstantiationException {
			super.update(models);
			QuickElement parent = getParentElement();
			StyleQIS.installParentModels(getModels(), parent == null ? null : parent.getModels());
			return this;
		}
	}
}

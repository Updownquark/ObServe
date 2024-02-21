package org.observe.quick;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/** An add-on for an element that is to be a window */
public interface QuickAbstractWindow extends ExAddOn<ExElement> {
	/** The XML name of this type */
	public static final String ABSTRACT_WINDOW = "abstract-window";

	/**
	 * The definition of a {@link QuickAbstractWindow}
	 *
	 * @param <W> The type of the window
	 */
	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = ABSTRACT_WINDOW,
		interpretation = Interpreted.class,
		instance = QuickAbstractWindow.class)
	public interface Def<W extends QuickAbstractWindow> extends ExAddOn.Def<ExElement, W> {
		/** @return The expression defining the title for the window */
		@QonfigAttributeGetter("title")
		public CompiledExpression getTitle();

		/** @return The expression defining when the window is visible--to hide/show and to be updated when the user closes the window */
		@QonfigAttributeGetter("visible")
		public CompiledExpression isVisible();

		/**
		 * Default window definition
		 *
		 * @param <W> The type of window
		 */
		public static class Default<W extends QuickAbstractWindow> extends ExAddOn.Def.Abstract<ExElement, W> implements Def<W> {
			private CompiledExpression theTitle;
			private CompiledExpression theVisible;

			/**
			 * @param type The add-on that the Qonfig toolkit uses to represent this type
			 * @param element The element that this add-on is added onto
			 */
			public Default(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
				super(type, element);
			}

			@Override
			public CompiledExpression getTitle() {
				return theTitle;
			}

			@Override
			public CompiledExpression isVisible() {
				return theVisible;
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
				theTitle = element.getAttributeExpression("title", session);
				theVisible = element.getAttributeExpression("visible", session);
			}

			@Override
			public Interpreted<W> interpret(ExElement.Interpreted<? extends ExElement> element) {
				return new Interpreted.Default<>(this, element);
			}
		}
	}

	/**
	 * An interpretation of a {@link QuickAbstractWindow}
	 *
	 * @param <W> The type of window
	 */
	public interface Interpreted<W extends QuickAbstractWindow> extends ExAddOn.Interpreted<ExElement, W> {
		/** @return The expression defining the title for the window */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTitle();

		/** @return The expression defining when the window is visible--to hide/show and to be updated when the user closes the window */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible();

		/**
		 * Default window interpretation
		 *
		 * @param <W> The type of window
		 */
		public static class Default<W extends QuickAbstractWindow> extends ExAddOn.Interpreted.Abstract<ExElement, W>
		implements Interpreted<W> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTitle;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> theVisible;

			/**
			 * @param definition The definition producing this interpretation
			 * @param element The element interpretation that this add-on is added onto
			 */
			public Default(Def<W> definition, ExElement.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public Def<W> getDefinition() {
				return (Def<W>) super.getDefinition();
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTitle() {
				return theTitle;
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible() {
				return theVisible;
			}

			@Override
			public void update(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
				super.update(element);
				theVisible = getElement().interpret(getDefinition().isVisible(), ModelTypes.Value.BOOLEAN);
				theTitle = getElement().interpret(getDefinition().getTitle(), ModelTypes.Value.STRING);
			}

			@Override
			public Class<W> getInstanceType() {
				return (Class<W>) QuickAbstractWindow.Default.class;
			}

			@Override
			public W create(ExElement element) {
				return (W) new QuickAbstractWindow.Default(element);
			}
		}
	}

	/** @return The value defining the title for the window */
	public SettableValue<String> getTitle();

	/** @return The value defining when the window is visible--to hide/show and to be updated when the user closes the window */
	public SettableValue<Boolean> isVisible();

	/** Default window implementation */
	public static class Default extends ExAddOn.Abstract<ExElement> implements QuickAbstractWindow {
		private ModelValueInstantiator<SettableValue<String>> theTitleInstantiator;
		private ModelValueInstantiator<SettableValue<Boolean>> theVisibleInstantiator;
		private SettableValue<SettableValue<String>> theTitle;
		private SettableValue<SettableValue<Boolean>> isVisible;

		/** @param element The element that this add-on is added onto */
		public Default(ExElement element) {
			super(element);
			theTitle = SettableValue.<SettableValue<String>> build().build();
			isVisible = SettableValue.<SettableValue<Boolean>> build().build();
		}

		@Override
		public Class<? extends QuickAbstractWindow.Interpreted<?>> getInterpretationType() {
			return (Class<QuickAbstractWindow.Interpreted<?>>) (Class<?>) QuickAbstractWindow.Interpreted.class;
		}

		@Override
		public SettableValue<String> getTitle() {
			return SettableValue.flatten(theTitle);
		}

		@Override
		public SettableValue<Boolean> isVisible() {
			return SettableValue.flatten(isVisible, () -> true);
		}

		/**
		 * Attempts to instantiate this window's title. Since no model instances are available here, this will fail if the title is not a
		 * literal or a product of literals.
		 *
		 * @throws ModelInstantiationException If the title is not a literal or could not be instantiated
		 */
		protected void tryInstantiateTitle() throws ModelInstantiationException {
			try {
				theTitle.set(theTitleInstantiator == null ? null : theTitleInstantiator.get(null), null);
			} catch (NullPointerException e) {
				throw new ModelInstantiationException("Title is not a literal--could not evaluate", getElement().reporting().getPosition(),
					0);
			}
		}

		@Override
		public void update(ExAddOn.Interpreted<?, ?> interpreted, ExElement element) throws ModelInstantiationException {
			super.update(interpreted, element);
			QuickAbstractWindow.Interpreted<?> myInterpreted = (QuickAbstractWindow.Interpreted<?>) interpreted;
			theTitleInstantiator = myInterpreted.getTitle() == null ? null : myInterpreted.getTitle().instantiate();
			theVisibleInstantiator = myInterpreted.isVisible() == null ? null : myInterpreted.isVisible().instantiate();
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();
			if (theTitleInstantiator != null)
				theTitleInstantiator.instantiate();
			if (theVisibleInstantiator != null)
				theVisibleInstantiator.instantiate();
		}

		@Override
		public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
			super.instantiate(models);

			theTitle.set(theTitleInstantiator == null ? null : theTitleInstantiator.get(models), null);
			isVisible.set(theVisibleInstantiator == null ? null : theVisibleInstantiator.get(models), null);
		}

		@Override
		public Default copy(ExElement element) {
			Default copy = (Default) super.copy(element);

			copy.theTitle = SettableValue.<SettableValue<String>> build().build();
			copy.isVisible = SettableValue.<SettableValue<Boolean>> build().build();

			return copy;
		}
	}
}

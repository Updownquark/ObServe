package org.observe.quick;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
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
public class QuickAbstractWindow extends ExAddOn.Abstract<ExElement> {
	public static final String ABSTRACT_WINDOW = "abstract-window";

	/** The definition of a {@link QuickAbstractWindow} */
	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = ABSTRACT_WINDOW,
		interpretation = Interpreted.class,
		instance = QuickAbstractWindow.class)
	public static class Def<W extends QuickAbstractWindow> extends ExAddOn.Def.Abstract<ExElement, W> {
		private CompiledExpression theTitle;
		private CompiledExpression theVisible;

		/**
		 * @param type The add-on that the Qonfig toolkit uses to represent this type
		 * @param element The element that this add-on is added onto
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		/** @return The expression defining the title for the window */
		@QonfigAttributeGetter("title")
		public CompiledExpression getTitle() {
			return theTitle;
		}

		/** @return The expression defining when the window is visible--to hide/show and to be updated when the user closes the window */
		@QonfigAttributeGetter("visible")
		public CompiledExpression isVisible() {
			return theVisible;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			theTitle = session.getAttributeExpression("title");
			theVisible = session.getAttributeExpression("visible");
		}

		@Override
		public Interpreted<W> interpret(ExElement.Interpreted<? extends ExElement> element) {
			return new Interpreted<>(this, element);
		}
	}

	/** An interpretation of a {@link QuickAbstractWindow} */
	public static class Interpreted<W extends QuickAbstractWindow> extends ExAddOn.Interpreted.Abstract<ExElement, W> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTitle;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> theVisible;

		/**
		 * @param definition The definition producing this interpretation
		 * @param element The element interpretation that this add-on is added onto
		 */
		public Interpreted(Def<W> definition, ExElement.Interpreted<?> element) {
			super(definition, element);
		}

		@Override
		public Def<W> getDefinition() {
			return (Def<W>) super.getDefinition();
		}

		/** @return The expression defining the title for the window */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTitle() {
			return theTitle;
		}

		/** @return The expression defining when the window is visible--to hide/show and to be updated when the user closes the window */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible() {
			return theVisible;
		}

		@Override
		public void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			theTitle = getDefinition().getTitle() == null ? null : getDefinition().getTitle().interpret(ModelTypes.Value.STRING, env);
			theVisible = getDefinition().isVisible() == null ? null : getDefinition().isVisible().interpret(ModelTypes.Value.BOOLEAN, env);
		}

		@Override
		public W create(ExElement element) {
			return (W) new QuickAbstractWindow(element);
		}
	}

	private ModelValueInstantiator<SettableValue<String>> theTitleInstantiator;
	private ModelValueInstantiator<SettableValue<Boolean>> theVisibleInstantiator;
	private SettableValue<String> theTitle;
	private SettableValue<Boolean> theVisible;

	/** @param element The element that this add-on is added onto */
	public QuickAbstractWindow(ExElement element) {
		super(element);
	}

	@Override
	public Class<? extends Interpreted<?>> getInterpretationType() {
		return (Class<Interpreted<?>>) (Class<?>) Interpreted.class;
	}

	/** @return The value defining the title for the window */
	public SettableValue<String> getTitle() {
		return theTitle;
	}

	/** @return The value defining when the window is visible--to hide/show and to be updated when the user closes the window */
	public SettableValue<Boolean> isVisible() {
		return theVisible;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted) {
		super.update(interpreted);
		QuickAbstractWindow.Interpreted<?> myInterpreted = (QuickAbstractWindow.Interpreted<?>) interpreted;
		theTitleInstantiator = myInterpreted.getTitle() == null ? null : myInterpreted.getTitle().instantiate();
		theVisibleInstantiator = myInterpreted.isVisible() == null ? null : myInterpreted.isVisible().instantiate();
	}

	@Override
	public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
		super.instantiate(models);

		theTitle = theTitleInstantiator == null ? null : theTitleInstantiator.get(models);
		theVisible = theVisibleInstantiator == null ? null : theVisibleInstantiator.get(models);
	}
}

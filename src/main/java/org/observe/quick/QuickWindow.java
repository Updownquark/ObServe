package org.observe.quick;

import javax.swing.Icon;

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
public class QuickWindow extends QuickAbstractWindow.Default {
	/** The name of the Qonfig add-on this implements */
	public static final String WINDOW = "window";

	/** An action to perform when the user closes the window (e.g. clicks the "X") */
	public enum CloseAction {
		/** Do nothing when the user attempts to close */
		DoNothing,
		/** Hide the window, but keep it ready */
		Hide,
		/** Dispose of the window, releasing all its resources */
		Dispose,
		/** Exit the application/process */
		Exit
	}

	/** The definition of a {@link QuickWindow} */
	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = WINDOW,
		interpretation = Interpreted.class,
		instance = QuickWindow.class)
	public static class Def extends QuickAbstractWindow.Def.Default<QuickWindow> {
		private CompiledExpression theX;
		private CompiledExpression theY;
		private CompiledExpression theWidth;
		private CompiledExpression theHeight;
		private CompiledExpression theWindowIcon;
		private CloseAction theCloseAction;

		/**
		 * @param type The add-on that the Qonfig toolkit uses to represent this type
		 * @param element The element that this add-on is added onto
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		/** @return The expression defining the x-coordinate of the window--to move and be updated when the user moves it */
		@QonfigAttributeGetter("x")
		public CompiledExpression getX() {
			return theX;
		}

		/** @return The expression defining the y-coordinate of the window--to move and be updated when the user moves it */
		@QonfigAttributeGetter("y")
		public CompiledExpression getY() {
			return theY;
		}

		/** @return The expression defining the width of the window--to size and be updated when the user resizes it */
		@QonfigAttributeGetter("width")
		public CompiledExpression getWidth() {
			return theWidth;
		}

		/** @return The expression defining the height of the window--to size and be updated when the user resizes it */
		@QonfigAttributeGetter("height")
		public CompiledExpression getHeight() {
			return theHeight;
		}

		/** @return The expression defining the icon of the window */
		@QonfigAttributeGetter("window-icon")
		public CompiledExpression getWindowIcon() {
			return theWindowIcon;
		}

		/** @return The action to perform when the user closes the window */
		@QonfigAttributeGetter("close-action")
		public CloseAction getCloseAction() {
			return theCloseAction;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			super.update(session, element);
			theX = session.getAttributeExpression("x");
			theY = session.getAttributeExpression("y");
			theWidth = session.getAttributeExpression("width");
			theHeight = session.getAttributeExpression("height");
			theWindowIcon = session.getAttributeExpression("window-icon");
			String closeAction = session.getAttributeText("close-action");
			switch (closeAction) {
			case "do-nothing":
				theCloseAction = CloseAction.DoNothing;
				break;
			case "hide":
				theCloseAction = CloseAction.Hide;
				break;
			case "dispose":
				theCloseAction = CloseAction.Dispose;
				break;
			case "exit":
				theCloseAction = CloseAction.Exit;
				break;
			default:
				throw new QonfigInterpretationException("Unrecognized close action: " + closeAction,
					session.getAttributeValuePosition("close-action", 0), closeAction.length());
			}
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<? extends ExElement> element) {
			return new Interpreted(this, element);
		}
	}

	/** An interpretation of a {@link QuickWindow} */
	public static class Interpreted extends QuickAbstractWindow.Interpreted.Default<QuickWindow> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theX;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theY;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theWidth;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theHeight;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> theWindowIcon;

		/**
		 * @param definition The definition producing this interpretation
		 * @param element The element interpretation that this add-on is added onto
		 */
		public Interpreted(Def definition, ExElement.Interpreted<?> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The expression defining the x-coordinate of the window--to move and be updated when the user moves it */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getX() {
			return theX;
		}

		/** @return The expression defining the y-coordinate of the window--to move and be updated when the user moves it */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getY() {
			return theY;
		}

		/** @return The expression defining the width of the window--to size and be updated when the user resizes it */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getWidth() {
			return theWidth;
		}

		/** @return The expression defining the height of the window--to size and be updated when the user resizes it */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getHeight() {
			return theHeight;
		}

		/** @return The expression defining the icon to display for the window */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> getWindowIcon() {
			return theWindowIcon;
		}

		@Override
		public void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.update(env);
			theX = getDefinition().getX() == null ? null : getDefinition().getX().interpret(ModelTypes.Value.INT, env);
			theY = getDefinition().getY() == null ? null : getDefinition().getY().interpret(ModelTypes.Value.INT, env);
			theWidth = getDefinition().getWidth() == null ? null : getDefinition().getWidth().interpret(ModelTypes.Value.INT, env);
			theHeight = getDefinition().getHeight() == null ? null : getDefinition().getHeight().interpret(ModelTypes.Value.INT, env);
			theWindowIcon = getDefinition().getWindowIcon() == null ? null : QuickCoreInterpretation
				.evaluateIcon(getDefinition().getWindowIcon(), env, getDefinition().getElement().getElement().getDocument().getLocation());
		}

		@Override
		public Class<QuickWindow> getInstanceType() {
			return QuickWindow.class;
		}

		@Override
		public QuickWindow create(ExElement element) {
			return new QuickWindow(element);
		}
	}

	private ModelValueInstantiator<SettableValue<Integer>> theXInstantiator;
	private ModelValueInstantiator<SettableValue<Integer>> theYInstantiator;
	private ModelValueInstantiator<SettableValue<Integer>> theWidthInstantiator;
	private ModelValueInstantiator<SettableValue<Integer>> theHeightInstantiator;
	private ModelValueInstantiator<SettableValue<Icon>> theWindowIconInstantiator;
	private CloseAction theCloseAction;
	private SettableValue<Integer> theX;
	private SettableValue<Integer> theY;
	private SettableValue<Integer> theWidth;
	private SettableValue<Integer> theHeight;
	private SettableValue<Icon> theWindowIcon;

	/** @param element The element that this add-on is added onto */
	public QuickWindow(ExElement element) {
		super(element);
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}

	/** @return The action to take when the user closes the window */
	public CloseAction getCloseAction() {
		return theCloseAction;
	}

	/** @return The value defining the x-coordinate of the window--to move and be updated when the user moves it */
	public SettableValue<Integer> getX() {
		return theX;
	}

	/** @return The value defining the y-coordinate of the window--to move and be updated when the user moves it */
	public SettableValue<Integer> getY() {
		return theY;
	}

	/** @return The value defining the width of the window--to size and be updated when the user resizes it */
	public SettableValue<Integer> getWidth() {
		return theWidth;
	}

	/** @return The value defining the height of the window--to size and be updated when the user resizes it */
	public SettableValue<Integer> getHeight() {
		return theHeight;
	}

	/** @return The icon to display for the window */
	public SettableValue<Icon> getWindowIcon() {
		return theWindowIcon;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted) {
		super.update(interpreted);
		QuickWindow.Interpreted myInterpreted = (QuickWindow.Interpreted) interpreted;
		theCloseAction = myInterpreted.getDefinition().getCloseAction();
		theXInstantiator = myInterpreted.getX() == null ? null : myInterpreted.getX().instantiate();
		theYInstantiator = myInterpreted.getY() == null ? null : myInterpreted.getY().instantiate();
		theWidthInstantiator = myInterpreted.getWidth() == null ? null : myInterpreted.getWidth().instantiate();
		theHeightInstantiator = myInterpreted.getHeight() == null ? null : myInterpreted.getHeight().instantiate();
		theWindowIconInstantiator = myInterpreted.getWindowIcon() == null ? null : myInterpreted.getWindowIcon().instantiate();
	}

	@Override
	public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
		super.instantiate(models);

		theX = theXInstantiator == null ? null : theXInstantiator.get(models);
		theY = theYInstantiator == null ? null : theYInstantiator.get(models);
		theWidth = theWidthInstantiator == null ? null : theWidthInstantiator.get(models);
		theHeight = theHeightInstantiator == null ? null : theHeightInstantiator.get(models);
		theWindowIcon = theWindowIconInstantiator == null ? null : theWindowIconInstantiator.get(models);
	}
}

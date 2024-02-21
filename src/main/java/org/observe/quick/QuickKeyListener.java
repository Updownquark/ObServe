package org.observe.quick;

import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A listener for key events on a Quick widget */
public interface QuickKeyListener extends QuickEventListener {
	/**
	 * The definition of a {@link QuickKeyListener}
	 *
	 * @param <L> The sub-type of listener to create
	 */
	public interface Def<L extends QuickKeyListener> extends QuickEventListener.Def<L> {
	}

	/**
	 * The interpretation of a {@link QuickKeyListener}
	 *
	 * @param <L> The sub-type of listener to create
	 */
	public interface Interpreted<L extends QuickKeyListener> extends QuickEventListener.Interpreted<L> {
		@Override
		Def<? super L> getDefinition();
	}

	/** Context for a {@link QuickKeyTypedListener} */
	public interface KeyTypedContext extends ListenerContext {
		/** @return The character that was typed */
		SettableValue<Character> getTypedChar();

		/** Default {@link KeyTypedContext} implementation */
		public class Default extends ListenerContext.Default implements KeyTypedContext {
			private final SettableValue<Character> theTypedChar;

			/**
			 * @param altPressed Whether the user is currently pressing the ALT key
			 * @param ctrlPressed Whether the user is currently pressing the CTRL key
			 * @param shiftPressed Whether the user is currently pressing the SHIFT key
			 * @param typedChar The character that was typed
			 */
			public Default(SettableValue<Boolean> altPressed, SettableValue<Boolean> ctrlPressed, SettableValue<Boolean> shiftPressed,
				SettableValue<Character> typedChar) {
				super(altPressed, ctrlPressed, shiftPressed);
				theTypedChar = typedChar;
			}

			/** Creates a context with default value containers */
			public Default() {
				theTypedChar = SettableValue.<Character> build().withValue((char) 0).build();
			}

			@Override
			public SettableValue<Character> getTypedChar() {
				return theTypedChar;
			}
		}
	}

	/** Context for a {@link QuickKeyCodeListener} */
	public interface KeyCodeContext extends ListenerContext {
		/** @return The key code of the key that was pressed or released */
		SettableValue<KeyCode> getKeyCode();

		/** Default {@link KeyCodeContext} implementation */
		public class Default extends ListenerContext.Default implements KeyCodeContext {
			private final SettableValue<KeyCode> theKeyCode;

			/**
			 * @param altPressed Whether the user is currently pressing the ALT key
			 * @param ctrlPressed Whether the user is currently pressing the CTRL key
			 * @param shiftPressed Whether the user is currently pressing the SHIFT key
			 * @param keyCode The key code of the key that was pressed or released
			 */
			public Default(SettableValue<Boolean> altPressed, SettableValue<Boolean> ctrlPressed, SettableValue<Boolean> shiftPressed,
				SettableValue<KeyCode> keyCode) {
				super(altPressed, ctrlPressed, shiftPressed);
				theKeyCode = keyCode;
			}

			/** Creates a context with default value containers */
			public Default() {
				theKeyCode = SettableValue.<KeyCode> build().build();
			}

			@Override
			public SettableValue<KeyCode> getKeyCode() {
				return theKeyCode;
			}
		}
	}

	/** A listener for the user typing a character while focused on a widget */
	public class QuickKeyTypedListener extends QuickEventListener.Abstract implements QuickKeyListener {
		/** The XML name of this type */
		public static final String KEY_TYPED_LISTENER = "on-type";

		/** The definition of a {@link QuickKeyTypedListener} */
		@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
			qonfigType = KEY_TYPED_LISTENER,
			interpretation = Interpreted.class,
			instance = QuickKeyTypedListener.class)
		public static class Def extends QuickEventListener.Def.Abstract<QuickKeyTypedListener>
		implements QuickKeyListener.Def<QuickKeyTypedListener> {
			private char theCharFilter;
			private ModelComponentId theTypedCharValue;

			/**
			 * @param parent The parent element of this listener
			 * @param type The Qonfig type of this listener
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			/**
			 * @return The character that this listener's action will be called for, or <code>0</code> if the action will be called for any
			 *         character
			 */
			@QonfigAttributeGetter("char")
			public char getCharFilter() {
				return theCharFilter;
			}

			/** @return The model ID of the model value containing the character that the user typed for the current event */
			public ModelComponentId getTypedCharValue() {
				return theTypedCharValue;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement(session.getFocusType()// on-type
					.getSuperElement() // key-listener
					.getSuperElement() // event-listener
					));
				String charFilterStr = session.getAttributeText("char");
				if (charFilterStr == null || charFilterStr.isEmpty())
					theCharFilter = 0;
				else if (charFilterStr.length() > 1)
					throw new QonfigInterpretationException("char attribute must be a single character",
						session.attributes().get("char").getLocatedContent());
				else
					theCharFilter = charFilterStr.charAt(0);

				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theTypedCharValue = elModels.getElementValueModelId("typedChar");
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		/** The interpretation of a {@link QuickKeyTypedListener} */
		public static class Interpreted extends QuickEventListener.Interpreted.Abstract<QuickKeyTypedListener>
		implements QuickKeyListener.Interpreted<QuickKeyTypedListener> {

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for this listener
			 */
			public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickKeyTypedListener create() {
				return new QuickKeyTypedListener(getIdentity());
			}
		}

		private ModelComponentId theTypedCharValue;
		private SettableValue<SettableValue<Character>> theTypedChar;
		private char theCharFilter;

		/** @param id The element ID for this listener */
		public QuickKeyTypedListener(Object id) {
			super(id);
			theTypedChar = SettableValue.<SettableValue<Character>> build().build();
		}

		/**
		 * @return The character that this listener's action will be called for, or <code>0</code> if the action will be called for any
		 *         character
		 */
		public char getCharFilter() {
			return theCharFilter;
		}

		/** @param ctx Context for this listener from the Quick implementation */
		public void setListenerContext(KeyTypedContext ctx) {
			setListenerContext((ListenerContext) ctx);
			theTypedChar.set(ctx.getTypedChar(), null);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			QuickKeyTypedListener.Interpreted myInterpreted = (QuickKeyTypedListener.Interpreted) interpreted;
			theCharFilter = myInterpreted.getDefinition().getCharFilter();
			theTypedCharValue = myInterpreted.getDefinition().getTypedCharValue();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);
			ExFlexibleElementModelAddOn.satisfyElementValue(theTypedCharValue, myModels, SettableValue.flatten(theTypedChar));
		}

		@Override
		protected QuickKeyTypedListener clone() {
			QuickKeyTypedListener copy = (QuickKeyTypedListener) super.clone();

			copy.theTypedChar = SettableValue.<SettableValue<Character>> build().build();

			return copy;
		}
	}

	/** A listener for the user pressing or releasing a key on the keyboard while focused on a widget */
	public class QuickKeyCodeListener extends QuickEventListener.Abstract implements QuickKeyListener {
		/** The XML name of this type */
		public static final String KEY_CODE_LISTENER = "key-code-listener";
		/** The XML name of the listener type that listens for key press events */
		public static final String KEY_PRESSED_LISTENER = "on-key-press";
		/** The XML name of the listener type that listens for key released events */
		public static final String KEY_RELEASED_LISTENER = "on-key-release";

		/** The definition of a {@link QuickKeyCodeListener} */
		@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
			qonfigType = KEY_CODE_LISTENER,
			interpretation = Interpreted.class,
			instance = QuickKeyCodeListener.class)
		public static class Def extends QuickEventListener.Def.Abstract<QuickKeyCodeListener>
		implements QuickKeyListener.Def<QuickKeyCodeListener> {
			private final boolean isPressed;
			private KeyCode theKeyCode;
			private ModelComponentId theKeyCodeValue;

			/**
			 * @param parent The parent element of this listener
			 * @param type The Qonfig type of this listener
			 * @param pressed Whether this listener is for pressed or released events
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type, boolean pressed) {
				super(parent, type);
				isPressed = pressed;
			}

			/** @return Whether this listener is for pressed or released events */
			public boolean isPressed() {
				return isPressed;
			}

			/**
			 * @return The key code that this listener's action will be fired for, or null if the action will be fired for all key events
			 */
			@QonfigAttributeGetter("key")
			public KeyCode getKeyCode() {
				return theKeyCode;
			}

			/** @return The model ID of the model value containing the key code that the user pressed or released for the current event */
			public ModelComponentId getKeyCodeValue() {
				return theKeyCodeValue;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				switch (session.getFocusType().getName()) {
				case KEY_PRESSED_LISTENER:
				case KEY_RELEASED_LISTENER: // Don't need a separate subclass for these
					session = session.asElement(session.getFocusType()// on-key-press or on-key-release
						.getSuperElement()// key-code-listener
						);
					break;
				default:
				}
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()// key-listener
					.getSuperElement() // event-listener
					));
				String keyCodeStr = session.getAttributeText("key");
				if (keyCodeStr == null || keyCodeStr.isEmpty())
					theKeyCode = null;
				else {
					try {
						theKeyCode = KeyCode.parse(keyCodeStr);
					} catch (IllegalArgumentException e) {
						throw new QonfigInterpretationException(e.getMessage(), session.attributes().get("key").getLocatedContent(), e);
					}
				}

				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theKeyCodeValue = elModels.getElementValueModelId("keyCode");
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		/** The interpretation of a {@link QuickKeyCodeListener} */
		public static class Interpreted extends QuickEventListener.Interpreted.Abstract<QuickKeyCodeListener>
		implements QuickKeyListener.Interpreted<QuickKeyCodeListener> {
			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickKeyCodeListener create() {
				return new QuickKeyCodeListener(getIdentity());
			}
		}

		private SettableValue<SettableValue<KeyCode>> theEventKeyCode;
		private boolean isPressed;
		private KeyCode theKeyCode;
		private ModelComponentId theKeyCodeValue;

		QuickKeyCodeListener(Object id) {
			super(id);
			theEventKeyCode = SettableValue.<SettableValue<KeyCode>> build().build();
		}

		/** @param ctx The listener context from the Quick implementation */
		public void setListenerContext(KeyCodeContext ctx) {
			setListenerContext((ListenerContext) ctx);
			theEventKeyCode.set(ctx.getKeyCode(), null);
		}

		/** @return Whether this listener is for pressed or released events */
		public boolean isPressed() {
			return isPressed;
		}

		/** @return The key code that this listener's action will be fired for, or null if the action will be fired for all key events */
		public KeyCode getKeyCode() {
			return theKeyCode;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);

			QuickKeyCodeListener.Interpreted myInterpreted = (QuickKeyCodeListener.Interpreted) interpreted;
			theKeyCodeValue = myInterpreted.getDefinition().getKeyCodeValue();
			isPressed = myInterpreted.getDefinition().isPressed();
			theKeyCode = myInterpreted.getDefinition().getKeyCode();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);
			ExFlexibleElementModelAddOn.satisfyElementValue(theKeyCodeValue, myModels, SettableValue.flatten(theEventKeyCode));
		}

		@Override
		public QuickKeyCodeListener copy(ExElement parent) {
			QuickKeyCodeListener copy = (QuickKeyCodeListener) super.copy(parent);

			copy.theEventKeyCode = SettableValue.<SettableValue<KeyCode>> build().build();

			return copy;
		}
	}
}

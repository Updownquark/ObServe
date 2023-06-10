package org.observe.quick;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

public interface QuickKeyListener extends QuickEventListener {
	public interface Def<L extends QuickKeyListener> extends QuickEventListener.Def<L> {
	}

	public interface Interpreted<L extends QuickKeyListener> extends QuickEventListener.Interpreted<L> {
		@Override
		Def<? super L> getDefinition();
	}

	public interface KeyTypedContext extends ListenerContext {
		SettableValue<Character> getTypedChar();

		public class Default extends ListenerContext.Default implements KeyTypedContext {
			private final SettableValue<Character> theTypedChar;

			public Default(SettableValue<Boolean> altPressed, SettableValue<Boolean> ctrlPressed, SettableValue<Boolean> shiftPressed,
				SettableValue<Character> typedChar) {
				super(altPressed, ctrlPressed, shiftPressed);
				theTypedChar = typedChar;
			}

			public Default() {
				theTypedChar = SettableValue.build(char.class).withValue((char) 0).build();
			}

			@Override
			public SettableValue<Character> getTypedChar() {
				return theTypedChar;
			}
		}
	}

	public interface KeyCodeContext extends ListenerContext {
		SettableValue<KeyCode> getKeyCode();

		public class Default extends ListenerContext.Default implements KeyCodeContext {
			private final SettableValue<KeyCode> theKeyCode;

			public Default(SettableValue<Boolean> altPressed, SettableValue<Boolean> ctrlPressed, SettableValue<Boolean> shiftPressed,
				SettableValue<KeyCode> keyCode) {
				super(altPressed, ctrlPressed, shiftPressed);
				theKeyCode = keyCode;
			}

			public Default() {
				theKeyCode = SettableValue.build(KeyCode.class).build();
			}

			@Override
			public SettableValue<KeyCode> getKeyCode() {
				return theKeyCode;
			}
		}
	}

	public class QuickKeyTypedListener extends QuickEventListener.Abstract implements QuickKeyListener {
		public static final String KEY_TYPED_LISTENER = "on-type";

		public static class Def extends QuickEventListener.Def.Abstract<QuickKeyTypedListener>
		implements QuickKeyListener.Def<QuickKeyTypedListener> {
			private char theCharFilter;

			public Def(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			public char getCharFilter() {
				return theCharFilter;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				checkElement(session.getFocusType(), QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, KEY_TYPED_LISTENER);
				super.update(session.asElement(session.getFocusType()// on-type
					.getSuperElement() // key-listener
					.getSuperElement() // event-listener
					));
				String charFilterStr = session.getAttributeText("char");
				if (charFilterStr == null || charFilterStr.isEmpty())
					theCharFilter = 0;
				else if (charFilterStr.length() > 1)
					throw new QonfigInterpretationException("char attribute must be a single character",
						session.getAttributeValuePosition("char", 1), charFilterStr.length() - 1);
				else
					theCharFilter = charFilterStr.charAt(0);
			}

			@Override
			public Interpreted interpret(QuickElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends QuickEventListener.Interpreted.Abstract<QuickKeyTypedListener>
		implements QuickKeyListener.Interpreted<QuickKeyTypedListener> {

			public Interpreted(Def definition, QuickElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickKeyTypedListener create(QuickElement parent) {
				return new QuickKeyTypedListener(this, parent);
			}
		}

		private final SettableValue<SettableValue<Character>> theTypedChar;
		private char theCharFilter;

		public QuickKeyTypedListener(Interpreted interpreted, QuickElement parent) {
			super(interpreted, parent);
			theTypedChar = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Character>> parameterized(Character.class)).build();
		}

		public char getCharFilter() {
			return theCharFilter;
		}

		public void setListenerContext(KeyTypedContext ctx) throws ModelInstantiationException {
			setListenerContext((ListenerContext) ctx);
			theTypedChar.set(ctx.getTypedChar(), null);
		}

		@Override
		protected void updateModel(QuickElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			QuickElement.satisfyContextValue("typedChar", ModelTypes.Value.forType(char.class), SettableValue.flatten(theTypedChar),
				myModels, this);
			QuickKeyTypedListener.Interpreted myInterpreted = (QuickKeyTypedListener.Interpreted) interpreted;
			theCharFilter = myInterpreted.getDefinition().getCharFilter();
		}
	}

	public class QuickKeyCodeListener extends QuickEventListener.Abstract implements QuickKeyListener {
		public static final String KEY_CODE_LISTENER = "key-code-listener";
		public static final String KEY_PRESSED_LISTENER = "on-key-press";
		public static final String KEY_RELEASED_LISTENER = "on-key-release";

		public static class Def extends QuickEventListener.Def.Abstract<QuickKeyCodeListener>
		implements QuickKeyListener.Def<QuickKeyCodeListener> {
			private final boolean isPressed;
			private KeyCode theKeyCode;

			public Def(QuickElement.Def<?> parent, QonfigElement element, boolean pressed) {
				super(parent, element);
				isPressed = pressed;
			}

			public boolean isPressed() {
				return isPressed;
			}

			public KeyCode getKeyCode() {
				return theKeyCode;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				switch (session.getFocusType().getName()) {
				case KEY_PRESSED_LISTENER:
				case KEY_RELEASED_LISTENER: // Don't need a separate subclass for these
					session = session.asElement(session.getFocusType()// on-key-press or on-key-release
						.getSuperElement()// key-code-listener
						);
					break;
				default:
				}
				checkElement(session.getFocusType(), QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, KEY_CODE_LISTENER);
				super.update(session.asElement(session.getFocusType().getSuperElement()// key-listener
					.getSuperElement() // event-listener
				));
				String keyCodeStr = session.getAttributeText("key");
				if (keyCodeStr == null || keyCodeStr.isEmpty())
					theKeyCode = null;
				else {
					try {
						theKeyCode = KeyCode.parse(keyCodeStr);
					} catch (IllegalArgumentException e) {
						throw new QonfigInterpretationException(e.getMessage(), session.getAttributeValuePosition("key", 0),
							keyCodeStr.length(), e);
					}
				}
			}

			@Override
			public Interpreted interpret(QuickElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends QuickEventListener.Interpreted.Abstract<QuickKeyCodeListener>
		implements QuickKeyListener.Interpreted<QuickKeyCodeListener> {
			public Interpreted(Def definition, QuickElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickKeyCodeListener create(QuickElement parent) {
				return new QuickKeyCodeListener(this, parent);
			}
		}

		private final SettableValue<SettableValue<KeyCode>> theEventKeyCode;
		private boolean isPressed;
		private KeyCode theKeyCode;

		public QuickKeyCodeListener(Interpreted interpreted, QuickElement parent) {
			super(interpreted, parent);
			theEventKeyCode = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<KeyCode>> parameterized(KeyCode.class)).build();
		}

		public void setListenerContext(KeyCodeContext ctx) throws ModelInstantiationException {
			setListenerContext((ListenerContext) ctx);
			theEventKeyCode.set(ctx.getKeyCode(), null);
		}

		public boolean isPressed() {
			return isPressed;
		}

		public KeyCode getKeyCode() {
			return theKeyCode;
		}

		@Override
		protected void updateModel(QuickElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			QuickElement.satisfyContextValue("keyCode", ModelTypes.Value.forType(KeyCode.class), SettableValue.flatten(theEventKeyCode),
				myModels, this);
			QuickKeyCodeListener.Interpreted myInterpreted = (QuickKeyCodeListener.Interpreted) interpreted;
			isPressed = myInterpreted.getDefinition().isPressed();
			theKeyCode = myInterpreted.getDefinition().getKeyCode();
		}
	}
}

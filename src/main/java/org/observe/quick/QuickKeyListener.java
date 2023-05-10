package org.observe.quick;

import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.qommons.config.AbstractQIS;
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

			@Override
			public SettableValue<KeyCode> getKeyCode() {
				return theKeyCode;
			}
		}
	}

	public class QuickKeyTypedListener extends QuickEventListener.Abstract implements QuickKeyListener {
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
			public Def update(AbstractQIS<?> session) throws QonfigInterpretationException {
				super.update(session);
				String charFilterStr = session.getAttributeText("char");
				if (charFilterStr == null || charFilterStr.isEmpty())
					theCharFilter = 0;
				else if (charFilterStr.length() > 1)
					throw new QonfigInterpretationException("char attribute must be a single character",
						session.getAttributeValuePosition("char", 1), charFilterStr.length() - 1);
				else
					theCharFilter = charFilterStr.charAt(0);
				return this;
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

		public QuickKeyTypedListener(Interpreted interpreted, QuickElement parent) {
			super(interpreted, parent);
		}

		@Override
		public Interpreted getInterpreted() {
			return (Interpreted) super.getInterpreted();
		}

		public void setListenerContext(KeyTypedContext ctx) throws ModelInstantiationException {
			setListenerContext((ListenerContext) ctx);
			QuickElement.satisfyContextValue("typedChar", ModelTypes.Value.forType(char.class), ctx.getTypedChar(), this);
		}
	}

	public class QuickKeyCodeListener extends QuickEventListener.Abstract implements QuickKeyListener {
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
			public Def update(AbstractQIS<?> session) throws QonfigInterpretationException {
				super.update(session);
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
				return this;
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

		public QuickKeyCodeListener(Interpreted interpreted, QuickElement parent) {
			super(interpreted, parent);
		}

		@Override
		public Interpreted getInterpreted() {
			return (Interpreted) super.getInterpreted();
		}

		public void setListenerContext(KeyCodeContext ctx) throws ModelInstantiationException {
			setListenerContext((ListenerContext) ctx);
			QuickElement.satisfyContextValue("keyCode", ModelTypes.Value.forType(KeyCode.class), ctx.getKeyCode(), this);
		}
	}

	@Override
	Interpreted<?> getInterpreted();
}
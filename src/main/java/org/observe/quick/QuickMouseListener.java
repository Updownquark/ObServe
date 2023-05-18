package org.observe.quick;

import org.observe.SettableValue;
import org.observe.expresso.DynamicModelValue;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

public abstract class QuickMouseListener extends QuickEventListener.Abstract {
	public static abstract class Def<L extends QuickMouseListener> extends QuickEventListener.Def.Abstract<L> {
		protected Def(QuickElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		@Override
		public abstract QuickMouseListener.Interpreted<? extends L> interpret(QuickElement.Interpreted<?> parent);
	}

	public static abstract class Interpreted<L extends QuickMouseListener> extends QuickEventListener.Interpreted.Abstract<L> {
		protected Interpreted(Def<? super L> definition, QuickElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super L> getDefinition() {
			return (Def<? super L>) super.getDefinition();
		}
	}

	public interface MouseListenerContext extends ListenerContext {
		SettableValue<Integer> getX();

		SettableValue<Integer> getY();

		public class Default extends ListenerContext.Default implements MouseListenerContext {
			private final SettableValue<Integer> theX;
			private final SettableValue<Integer> theY;
			public Default(SettableValue<Boolean> altPressed, SettableValue<Boolean> ctrlPressed, SettableValue<Boolean> shiftPressed,
				SettableValue<Integer> x, SettableValue<Integer> y) {
				super(altPressed, ctrlPressed, shiftPressed);
				theX = x;
				theY = y;
			}

			Default() {
				theX = SettableValue.build(int.class).withValue(0).build();
				theY = SettableValue.build(int.class).withValue(0).build();
			}

			@Override
			public SettableValue<Integer> getX() {
				return theX;
			}

			@Override
			public SettableValue<Integer> getY() {
				return theY;
			}
		}
	}

	public void setListenerContext(MouseListenerContext ctx) throws ModelInstantiationException {
		setListenerContext((ListenerContext) ctx);
		satisfyContextValue("x", ModelTypes.Value.INT, ctx.getX());
		satisfyContextValue("y", ModelTypes.Value.INT, ctx.getY());
	}

	protected QuickMouseListener(QuickMouseListener.Interpreted<?> interpreted, QuickElement parent) {
		super(interpreted, parent);
	}

	@Override
	public Interpreted<?> getInterpreted() {
		return (Interpreted<?>) super.getInterpreted();
	}

	public enum MouseButton {
		Left, Middle, Right
	}

	public enum MouseButtonEventType {
		Press, Release, Click
	}

	public enum MouseMoveEventType {
		Move, Enter, Exit
	}

	public interface MouseButtonListenerContext extends MouseListenerContext {
		SettableValue<MouseButton> getMouseButton();

		public class Default extends MouseListenerContext.Default implements MouseButtonListenerContext {
			private final SettableValue<MouseButton> theMouseButton;

			public Default(SettableValue<Boolean> altPressed, SettableValue<Boolean> ctrlPressed, SettableValue<Boolean> shiftPressed,
				SettableValue<Integer> x, SettableValue<Integer> y, SettableValue<MouseButton> mouseButton) {
				super(altPressed, ctrlPressed, shiftPressed, x, y);
				theMouseButton = mouseButton;
			}

			public Default() {
				theMouseButton = SettableValue.build(MouseButton.class).build();
			}

			@Override
			public SettableValue<MouseButton> getMouseButton() {
				return theMouseButton;
			}
		}
	}

	public interface ScrollListenerContext extends MouseListenerContext {
		SettableValue<Integer> getScrollAmount();

		public class Default extends MouseListenerContext.Default implements ScrollListenerContext {
			private final SettableValue<Integer> theScrollAmount;

			public Default(SettableValue<Boolean> altPressed, SettableValue<Boolean> ctrlPressed, SettableValue<Boolean> shiftPressed,
				SettableValue<Integer> x, SettableValue<Integer> y, SettableValue<Integer> scrollAmount) {
				super(altPressed, ctrlPressed, shiftPressed, x, y);
				theScrollAmount = scrollAmount;
			}

			@Override
			public SettableValue<Integer> getScrollAmount() {
				return theScrollAmount;
			}
		}
	}

	public static class QuickMouseMoveListener extends QuickMouseListener {
		public static class Def extends QuickMouseListener.Def<QuickMouseMoveListener> {
			private final MouseMoveEventType theEventType;

			public Def(QuickElement.Def<?> parent, QonfigElement element, MouseMoveEventType eventType) {
				super(parent, element);
				theEventType = eventType;
			}

			public MouseMoveEventType getEventType() {
				return theEventType;
			}

			@Override
			public Interpreted interpret(QuickElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends QuickMouseListener.Interpreted<QuickMouseMoveListener> {
			public Interpreted(Def definition, QuickElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickMouseMoveListener create(QuickElement parent) {
				return new QuickMouseMoveListener(this, parent);
			}
		}

		public QuickMouseMoveListener(Interpreted interpreted, QuickElement parent) {
			super(interpreted, parent);
		}

		@Override
		public Interpreted getInterpreted() {
			return (Interpreted) super.getInterpreted();
		}
	}

	public static class QuickMouseButtonListener extends QuickMouseListener {
		public static class Def extends QuickMouseListener.Def<QuickMouseButtonListener> {
			private final MouseButtonEventType theEventType;
			private MouseButton theButton;

			public Def(QuickElement.Def<?> parent, QonfigElement element, MouseButtonEventType eventType) {
				super(parent, element);
				theEventType = eventType;
			}

			public MouseButtonEventType getEventType() {
				return theEventType;
			}

			public MouseButton getButton() {
				return theButton;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				super.update(session);
				String button = session.getAttributeText("button");
				if (button == null)
					theButton = null;
				else {
					switch (button) {
					case "left":
						theButton = MouseButton.Left;
						break;
					case "right":
						theButton = MouseButton.Right;
						break;
					case "middle":
						theButton = MouseButton.Middle;
						break;
					default:
						throw new IllegalStateException("Unrecognized mouse button: '" + button + "'");
					}
				}
				DynamicModelValue.satisfyDynamicValueType("button", getModels(), ModelTypes.Value.forType(MouseButton.class));
			}

			@Override
			public Interpreted interpret(QuickElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends QuickMouseListener.Interpreted<QuickMouseButtonListener> {
			public Interpreted(Def definition, QuickElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickMouseButtonListener create(QuickElement parent) {
				return new QuickMouseButtonListener(this, parent);
			}
		}

		public QuickMouseButtonListener(QuickMouseButtonListener.Interpreted interpreted, QuickElement parent) {
			super(interpreted, parent);
		}

		@Override
		public QuickMouseButtonListener.Interpreted getInterpreted() {
			return (QuickMouseButtonListener.Interpreted) super.getInterpreted();
		}

		public void setListenerContext(MouseButtonListenerContext ctx) throws ModelInstantiationException {
			setListenerContext((MouseListenerContext) ctx);
			satisfyContextValue("button", ModelTypes.Value.forType(MouseButton.class), ctx.getMouseButton());
		}
	}

	public static class QuickScrollListener extends QuickMouseListener {
		public static class Def extends QuickMouseListener.Def<QuickScrollListener> {
			public Def(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@Override
			public Interpreted interpret(QuickElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends QuickMouseListener.Interpreted<QuickScrollListener> {
			public Interpreted(Def definition, QuickElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickScrollListener create(QuickElement parent) {
				return new QuickScrollListener(this, parent);
			}
		}

		public QuickScrollListener(Interpreted interpreted, QuickElement parent) {
			super(interpreted, parent);
		}

		@Override
		public Interpreted getInterpreted() {
			return (Interpreted) super.getInterpreted();
		}

		public void setListenerContext(ScrollListenerContext ctx) throws ModelInstantiationException {
			setListenerContext((MouseListenerContext) ctx);
			satisfyContextValue("scrollAmount", ModelTypes.Value.INT, ctx.getScrollAmount());
		}
	}
}

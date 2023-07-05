package org.observe.quick;

import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.DynamicModelValue;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

public abstract class QuickMouseListener extends QuickEventListener.Abstract {
	public static final String MOUSE_LISTENER = "mouse-listener";
	private static final ElementTypeTraceability<QuickMouseListener, Interpreted<?>, Def<?>> TRACEABILITY = ElementTypeTraceability
		.<QuickMouseListener, Interpreted<?>, Def<?>> build(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, MOUSE_LISTENER)//
		.reflectMethods(Def.class, Interpreted.class, QuickMouseListener.class)//
		.build();

	public static abstract class Def<L extends QuickMouseListener> extends QuickEventListener.Def.Abstract<L> {
		protected Def(ExElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.update(session.asElement(session.getFocusType().getSuperElement()));
		}

		@Override
		public abstract QuickMouseListener.Interpreted<? extends L> interpret(ExElement.Interpreted<?> parent);
	}

	public static abstract class Interpreted<L extends QuickMouseListener> extends QuickEventListener.Interpreted.Abstract<L> {
		protected Interpreted(Def<? super L> definition, ExElement.Interpreted<?> parent) {
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

	private final SettableValue<SettableValue<Integer>> theEventX;
	private final SettableValue<SettableValue<Integer>> theEventY;

	protected QuickMouseListener(QuickMouseListener.Interpreted<?> interpreted, ExElement parent) {
		super(interpreted, parent);
		theEventX = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(int.class))
			.build();
		theEventY = SettableValue.build(theEventX.getType()).build();
	}

	public void setListenerContext(MouseListenerContext ctx) throws ModelInstantiationException {
		setListenerContext((ListenerContext) ctx);
		theEventX.set(ctx.getX(), null);
		theEventY.set(ctx.getY(), null);
	}

	@Override
	protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
		super.updateModel(interpreted, myModels);
		satisfyContextValue("x", ModelTypes.Value.INT, SettableValue.flatten(theEventX), myModels);
		satisfyContextValue("y", ModelTypes.Value.INT, SettableValue.flatten(theEventY), myModels);
	}

	public enum MouseButton {
		Left, Middle, Right
	}

	public enum MouseMoveEventType {
		Move("on-mouse-move"), Enter("on-mouse-enter"), Exit("on-mouse-exit");

		public final String elementName;
		public final ElementTypeTraceability<QuickMouseMoveListener, QuickMouseMoveListener.Interpreted, QuickMouseMoveListener.Def> traceability;

		private MouseMoveEventType(String elementName) {
			this.elementName = elementName;
			traceability = ElementTypeTraceability.<QuickMouseMoveListener, QuickMouseMoveListener.Interpreted, QuickMouseMoveListener.Def> build(
				QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, elementName)//
				.reflectMethods(QuickMouseMoveListener.Def.class, QuickMouseMoveListener.Interpreted.class, QuickMouseMoveListener.class)//
				.build();
		}
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

			public Def(ExElement.Def<?> parent, QonfigElement element, MouseMoveEventType eventType) {
				super(parent, element);
				theEventType = eventType;
			}

			public MouseMoveEventType getEventType() {
				return theEventType;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(theEventType.traceability.validate(session.getFocusType(), session.reporting()));
				super.update(session.asElement(session.getFocusType().getSuperElement()));
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends QuickMouseListener.Interpreted<QuickMouseMoveListener> {
			public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickMouseMoveListener create(ExElement parent) {
				return new QuickMouseMoveListener(this, parent);
			}
		}

		private MouseMoveEventType theEventType;

		public QuickMouseMoveListener(Interpreted interpreted, ExElement parent) {
			super(interpreted, parent);
		}

		public MouseMoveEventType getEventType() {
			return theEventType;
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			QuickMouseMoveListener.Interpreted myInterpreted = (QuickMouseMoveListener.Interpreted) interpreted;
			theEventType = myInterpreted.getDefinition().getEventType();
		}
	}

	public static abstract class QuickMouseButtonListener extends QuickMouseListener {
		public static final String MOUSE_BUTTON_LISTENER = "mouse-button-listener";
		private static final ElementTypeTraceability<QuickMouseButtonListener, Interpreted<?>, Def<?>> TRACEABILITY = ElementTypeTraceability
			.<QuickMouseButtonListener, Interpreted<?>, Def<?>> build(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION,
				MOUSE_BUTTON_LISTENER)//
			.reflectMethods(Def.class, Interpreted.class, QuickMouseButtonListener.class)//
			.build();

		public static abstract class Def<L extends QuickMouseButtonListener> extends QuickMouseListener.Def<L> {
			private MouseButton theButton;

			protected Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@QonfigAttributeGetter("button")
			public MouseButton getButton() {
				return theButton;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.update(session.asElement(session.getFocusType().getSuperElement()));
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
		}

		public static abstract class Interpreted<L extends QuickMouseButtonListener> extends QuickMouseListener.Interpreted<L> {
			protected Interpreted(Def<? super L> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super L> getDefinition() {
				return (Def<? super L>) super.getDefinition();
			}
		}

		private final SettableValue<SettableValue<MouseButton>> theEventButton;
		private MouseButton theButton;

		public QuickMouseButtonListener(Interpreted<?> interpreted, ExElement parent) {
			super(interpreted, parent);
			theEventButton = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<MouseButton>> parameterized(MouseButton.class)).build();
		}

		public MouseButton getButton() {
			return theButton;
		}

		public void setListenerContext(MouseButtonListenerContext ctx) throws ModelInstantiationException {
			setListenerContext((MouseListenerContext) ctx);
			theEventButton.set(ctx.getMouseButton(), null);
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			satisfyContextValue("button", ModelTypes.Value.forType(MouseButton.class), SettableValue.flatten(theEventButton), myModels);
			Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;
			theButton = myInterpreted.getDefinition().getButton();
		}
	}

	public static class QuickMouseClickListener extends QuickMouseButtonListener {
		public static final String ON_MOUSE_CLICK = "on-click";
		private static final ElementTypeTraceability<QuickMouseClickListener, Interpreted, Def> TRACEABILITY = ElementTypeTraceability
			.<QuickMouseClickListener, Interpreted, Def> build(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION,
				ON_MOUSE_CLICK)//
			.reflectMethods(Def.class, Interpreted.class, QuickMouseClickListener.class)//
			.build();

		public static class Def extends QuickMouseButtonListener.Def<QuickMouseClickListener> {
			private int theClickCount;

			public Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@QonfigAttributeGetter("click-count")
			public int getClickCount() {
				return theClickCount;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.update(session.asElement(session.getFocusType().getSuperElement()));

				String txt = session.getAttributeText("click-count");
				if (txt == null)
					theClickCount = 0;
				else {
					theClickCount = Integer.parseInt(txt);
					if (theClickCount < 1)
						session.reporting().at(session.getAttributeValuePosition("click-count"))
						.error("click-count must be greater than zero");
				}
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}

		}

		public static class Interpreted extends QuickMouseButtonListener.Interpreted<QuickMouseClickListener> {
			public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickMouseClickListener create(ExElement parent) {
				return new QuickMouseClickListener(this, parent);
			}
		}

		private int theClickCount;

		public QuickMouseClickListener(Interpreted interpreted, ExElement parent) {
			super(interpreted, parent);
		}

		public int getClickCount() {
			return theClickCount;
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			Interpreted myInterpreted = (Interpreted) interpreted;
			theClickCount = myInterpreted.getDefinition().getClickCount();
		}
	}

	public static class QuickMousePressedListener extends QuickMouseButtonListener {
		public static final String ON_MOUSE_PRESSED = "on-mouse-press";
		private static final ElementTypeTraceability<QuickMousePressedListener, Interpreted, Def> TRACEABILITY = ElementTypeTraceability
			.<QuickMousePressedListener, Interpreted, Def> build(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION,
				ON_MOUSE_PRESSED)//
			.reflectMethods(Def.class, Interpreted.class, QuickMousePressedListener.class)//
			.build();

		public static class Def extends QuickMouseButtonListener.Def<QuickMousePressedListener> {

			public Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.update(session.asElement(session.getFocusType().getSuperElement()));
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}

		}

		public static class Interpreted extends QuickMouseButtonListener.Interpreted<QuickMousePressedListener> {
			public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickMousePressedListener create(ExElement parent) {
				return new QuickMousePressedListener(this, parent);
			}
		}

		public QuickMousePressedListener(Interpreted interpreted, ExElement parent) {
			super(interpreted, parent);
		}
	}

	public static class QuickMouseReleasedListener extends QuickMouseButtonListener {
		public static final String ON_MOUSE_RELEASED = "on-mouse-release";
		private static final ElementTypeTraceability<QuickMouseReleasedListener, Interpreted, Def> TRACEABILITY = ElementTypeTraceability
			.<QuickMouseReleasedListener, Interpreted, Def> build(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION,
				ON_MOUSE_RELEASED)//
			.reflectMethods(Def.class, Interpreted.class, QuickMouseReleasedListener.class)//
			.build();

		public static class Def extends QuickMouseButtonListener.Def<QuickMouseReleasedListener> {

			public Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.update(session.asElement(session.getFocusType().getSuperElement()));
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}

		}

		public static class Interpreted extends QuickMouseButtonListener.Interpreted<QuickMouseReleasedListener> {
			public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickMouseReleasedListener create(ExElement parent) {
				return new QuickMouseReleasedListener(this, parent);
			}
		}

		public QuickMouseReleasedListener(Interpreted interpreted, ExElement parent) {
			super(interpreted, parent);
		}
	}

	public static class QuickScrollListener extends QuickMouseListener {
		public static final String SCROLL_LISTENER = "on-scroll";
		private static final ElementTypeTraceability<QuickScrollListener, Interpreted, Def> TRACEABILITY = ElementTypeTraceability
			.<QuickScrollListener, Interpreted, Def> build(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, SCROLL_LISTENER)//
			.reflectMethods(Def.class, Interpreted.class, QuickScrollListener.class)//
			.build();

		public static class Def extends QuickMouseListener.Def<QuickScrollListener> {
			public Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.update(session.asElement(session.getFocusType().getSuperElement()));
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends QuickMouseListener.Interpreted<QuickScrollListener> {
			public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickScrollListener create(ExElement parent) {
				return new QuickScrollListener(this, parent);
			}
		}

		private final SettableValue<SettableValue<Integer>> theScrollAmount;

		public QuickScrollListener(Interpreted interpreted, ExElement parent) {
			super(interpreted, parent);
			theScrollAmount = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(Integer.class)).build();
		}

		public void setListenerContext(ScrollListenerContext ctx) throws ModelInstantiationException {
			setListenerContext((MouseListenerContext) ctx);
			theScrollAmount.set(ctx.getScrollAmount(), null);
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			satisfyContextValue("scrollAmount", ModelTypes.Value.INT, SettableValue.flatten(theScrollAmount), myModels);
		}
	}
}

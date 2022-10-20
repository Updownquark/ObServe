package org.observe.quick;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.IllegalComponentStateException;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.font.TextAttribute;
import java.lang.reflect.Type;
import java.net.URL;
import java.text.AttributedCharacterIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.expresso.ClassView;
import org.observe.expresso.DynamicModelValue;
import org.observe.expresso.Expression.ExpressoParseException;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.QonfigExpression;
import org.observe.expresso.SuppliedModelValue;
import org.observe.quick.QuickCore.MouseValueSupport;
import org.observe.quick.style.QuickElementStyle;
import org.observe.quick.style.QuickElementStyle.QuickElementStyleAttribute;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.StyleQIS;
import org.observe.util.TypeTokens;
import org.observe.util.swing.ComponentDecorator.ModifiableLineBorder;
import org.observe.util.swing.ObservableSwingUtils;
import org.qommons.Causable;
import org.qommons.Causable.CausableInUse;
import org.qommons.Colors;
import org.qommons.Identifiable;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.Version;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

public class QuickCore implements QonfigInterpretation {
	/** The name of the Quick-Core toolkit */
	public static final String NAME = "Quick-Core";
	/** The supported version of the Quick-Core toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	public interface QuickBorder {
		ObservableValue<Border> createBorder(Component component, QuickComponent.Builder builder);
	}

	public interface QuickMouseListener {
		public interface Container {
			boolean isMotionListener();

			boolean isWheelListener();

			QuickMouseListener createListener(ModelSetInstance models);
		}

		boolean applies(MouseEvent evt);

		void actionPerformed(MouseEvent evt);
	}

	public interface QuickKeyListener {
		public interface Container {
			QuickKeyListener createListener(ModelSetInstance models);
		}

		boolean applies(KeyEvent evt);

		void actionPerformed(KeyEvent evt);
	}

	private QonfigToolkit theCoreToolkit;

	@Override
	public String getToolkitName() {
		return NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public void init(QonfigToolkit toolkit) {
		theCoreToolkit = toolkit;
	}

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return QommonsUtils.unmodifiableDistinctCopy(ExpressoQIS.class, StyleQIS.class);
	}

	public static Function<ModelSetInstance, SettableValue<QuickPosition>> parsePosition(QonfigExpression expression, ExpressoQIS session)
		throws QonfigInterpretationException {
		if (expression == null)
			return null;
		QuickPosition.PositionUnit unit = null;
		for (QuickPosition.PositionUnit u : QuickPosition.PositionUnit.values()) {
			if (expression.text.length() > u.name.length() && expression.text.endsWith(u.name)
				&& Character.isWhitespace(expression.text.charAt(expression.text.length() - u.name.length() - 1))) {
				unit = u;
				break;
			}
		}
		Function<ModelSetInstance, SettableValue<QuickPosition>> positionValue;
		try {
			if (unit != null) {
				QuickPosition.PositionUnit fUnit = unit;
				Function<ModelSetInstance, SettableValue<Double>> num = session.getExpressoParser()
					.parse(expression.text.substring(0, expression.text.length() - unit.name.length()))//
					.evaluate(ModelTypes.Value.forType(double.class), session.getExpressoEnv());
				positionValue = msi -> {
					SettableValue<Double> numV = num.apply(msi);
					return numV.transformReversible(QuickPosition.class, tx -> tx//
						.map(n -> new QuickPosition(n.floatValue(), fUnit))//
						.replaceSource(p -> (double) p.value, rev -> rev//
							.allowInexactReverse(true).rejectWith(
								p -> p.type == fUnit ? null : "Only positions with the same unit as the source (" + fUnit + ") can be set")//
							)//
						);
				};
			} else {
				ObservableExpression obEx = session.getExpressoParser().parse(expression.text);
				try {
					positionValue = obEx.evaluate(ModelTypes.Value.forType(QuickPosition.class), session.getExpressoEnv());
				} catch (QonfigInterpretationException e1) {
					// If it doesn't parse as a position, try parsing as a number.
					positionValue = obEx.evaluate(ModelTypes.Value.forType(int.class), session.getExpressoEnv())//
						.andThen(v -> v.transformReversible(QuickPosition.class, tx -> tx
							.map(d -> new QuickPosition(d, QuickPosition.PositionUnit.Pixels)).withReverse(pos -> Math.round(pos.value))));
				}
			}
		} catch (ExpressoParseException e) {
			throw new QonfigInterpretationException("Could not parse position expression: " + expression, e);
		}
		return positionValue;
	}

	public static Function<ModelSetInstance, SettableValue<QuickSize>> parseSize(QonfigExpression expression, ExpressoQIS session)
		throws QonfigInterpretationException {
		if (expression == null)
			return null;
		QuickSize.SizeUnit unit = null;
		for (QuickSize.SizeUnit u : QuickSize.SizeUnit.values()) {
			if (expression.text.length() > u.name.length() && expression.text.endsWith(u.name)
				&& Character.isWhitespace(expression.text.charAt(expression.text.length() - u.name.length() - 1))) {
				unit = u;
				break;
			}
		}
		Function<ModelSetInstance, SettableValue<QuickSize>> positionValue;
		try {
			if (unit != null) {
				QuickSize.SizeUnit fUnit = unit;
				Function<ModelSetInstance, SettableValue<Double>> num = session.getExpressoParser()
					.parse(expression.text.substring(0, expression.text.length() - unit.name.length()))//
					.evaluate(ModelTypes.Value.forType(double.class), session.getExpressoEnv());
				positionValue = msi -> {
					SettableValue<Double> numV = num.apply(msi);
					return numV.transformReversible(QuickSize.class, tx -> tx//
						.map(n -> new QuickSize(n.floatValue(), fUnit))//
						.replaceSource(s -> (double) s.value, rev -> rev//
							.allowInexactReverse(true).rejectWith(
								p -> p.type == fUnit ? null : "Only sizes with the same unit as the source (" + fUnit + ") can be set")//
							)//
						);
				};
			} else {
				ObservableExpression obEx = session.getExpressoParser().parse(expression.text);
				try {
					positionValue = obEx.evaluate(ModelTypes.Value.forType(QuickSize.class), session.getExpressoEnv());
				} catch (QonfigInterpretationException e1) {
					// If it doesn't parse as a position, try parsing as a number.
					positionValue = obEx.evaluate(ModelTypes.Value.forType(int.class), session.getExpressoEnv())//
						.andThen(v -> v.transformReversible(QuickSize.class,
							tx -> tx.map(d -> new QuickSize(d, QuickSize.SizeUnit.Pixels)).withReverse(pos -> Math.round(pos.value))));
				}
			}
		} catch (ExpressoParseException e) {
			throw new QonfigInterpretationException("Could not parse size expression: " + expression, e);
		}
		return positionValue;
	}

	public static Function<ModelSetInstance, SettableValue<Icon>> parseIcon(ObservableExpression expression, ExpressoQIS session,
		ExpressoEnv env) throws QonfigInterpretationException {
		if (expression != null) {
			ValueContainer<SettableValue<?>, SettableValue<?>> iconV = expression.evaluate(ModelTypes.Value.any(), env);
			Class<?> iconType = TypeTokens.getRawType(iconV.getType().getType(0));
			if (Icon.class.isAssignableFrom(iconType))
				return (Function<ModelSetInstance, SettableValue<Icon>>) (ValueContainer<?, ?>) iconV;
			else if (Image.class.isAssignableFrom(iconType)) {
				return msi -> SettableValue.asSettable(iconV.apply(msi).map(img -> img == null ? null : new ImageIcon((Image) img)),
					__ -> "unsettable");
			} else if (URL.class.isAssignableFrom(iconType)) {
				return msi -> SettableValue.asSettable(iconV.apply(msi).map(url -> url == null ? null : new ImageIcon((URL) url)),
					__ -> "unsettable");
			} else if (String.class.isAssignableFrom(iconType)) {
				Class<?> callingClass = session.getWrapped().getInterpreter().getCallingClass();
				return msi -> SettableValue.asSettable(iconV.apply(msi).map(loc -> loc == null ? null//
					: ObservableSwingUtils.getFixedIcon(callingClass, (String) loc, 16, 16)), __ -> "unsettable");
			} else {
				session.withWarning("Cannot use value " + expression + ", type " + iconV.getType().getType(0) + " as an icon");
				return msi -> SettableValue.of(Icon.class, null, "unsettable");
			}
		} else {
			return msi -> SettableValue.of(Icon.class, null, "None provided");
		}
	}

	StyleQIS wrap(CoreSession session) throws QonfigInterpretationException {
		return session.as(StyleQIS.class);
	}

	@Override
	public QonfigInterpreterCore.Builder configureInterpreter(QonfigInterpreterCore.Builder interpreter) {
		interpreter//
		.createWith("quick", QuickDocument.class, session -> interpretQuick(wrap(session)))//
		.createWith("head", QuickHeadSection.class, session -> interpretHead(wrap(session)))//
		.modifyWith("window", QuickDocument.class, (doc, session) -> modifyWindow(doc, wrap(session)))//
		.modifyWith("widget", QuickComponentDef.class, (comp, session) -> modifyWidget(comp, wrap(session)))//
		.modifyWith("text-widget", QuickComponentDef.class, (txt, session) -> modifyTextWidget(txt, wrap(session)))//
		.createWith("line-border", QuickBorder.class, session -> interpretLineBorder(wrap(session)))//
		.createWith("titled-border", QuickBorder.class, session -> interpretTitledBorder(wrap(session)))//
		.createWith("mouse-listener", QuickMouseListener.Container.class, session -> interpretMouseListener(wrap(session)))//
		.modifyWith("on-click", QuickMouseListener.Container.class,
			(listener, session) -> modifyMouseClickListener(listener, wrap(session)))//
		.modifyWith("on-mouse-press", QuickMouseListener.Container.class,
			(listener, session) -> modifyMouseListenerWithType(listener, MouseEvent.MOUSE_PRESSED, false))//
		.modifyWith("on-mouse-release", QuickMouseListener.Container.class,
			(listener, session) -> modifyMouseListenerWithType(listener, MouseEvent.MOUSE_RELEASED, false))//
		.modifyWith("on-hover", QuickMouseListener.Container.class,
			(listener, session) -> modifyMouseListenerWithType(listener, MouseEvent.MOUSE_MOVED, true))//
		.modifyWith("on-mouse-enter", QuickMouseListener.Container.class,
			(listener, session) -> modifyMouseListenerWithType(listener, MouseEvent.MOUSE_ENTERED, false))//
		.modifyWith("on-mouse-exit", QuickMouseListener.Container.class,
			(listener, session) -> modifyMouseListenerWithType(listener, MouseEvent.MOUSE_EXITED, false))//
		.modifyWith("on-scroll", QuickMouseListener.Container.class,
			(listener, session) -> modifyMouseWheelListener(listener, wrap(session)))//
		.modifyWith("left-button", QuickMouseListener.Container.class,
			(listener, session) -> modifyMouseButtonListener(listener, MouseEvent.BUTTON1))//
		.modifyWith("right-button", QuickMouseListener.Container.class,
			(listener, session) -> modifyMouseButtonListener(listener, MouseEvent.BUTTON3))//
		.modifyWith("middle-button", QuickMouseListener.Container.class,
			(listener, session) -> modifyMouseButtonListener(listener, MouseEvent.BUTTON2))//
		;
		return interpreter;
	}

	private QuickDocument interpretQuick(StyleQIS session) throws QonfigInterpretationException {
		StyleQIS headSession = session.forChildren("head").getFirst();
		QuickHeadSection head = headSession.interpret(QuickHeadSection.class);
		session.as(ExpressoQIS.class).setExpressoEnv(headSession.as(ExpressoQIS.class).getExpressoEnv());
		session.setStyleSheet(head.getStyleSheet());
		QuickDocument doc = new QuickDocument.QuickDocumentImpl(session.getElement(), head, //
			session.interpretChildren("root", QuickComponentDef.class).getFirst());
		return doc;
	}

	private QuickHeadSection interpretHead(StyleQIS session) throws QonfigInterpretationException {
		StyleQIS importSession = session.forChildren("imports").peekFirst();
		ClassView cv = importSession == null ? null : importSession.interpret(ClassView.class);
		if (cv == null) {
			ClassView defaultCV = ClassView.build().withWildcardImport("java.lang").build();
			TypeTokens.get().addClassRetriever(new TypeTokens.TypeRetriever() {
				@Override
				public Type getType(String typeName) {
					return defaultCV.getType(typeName);
				}
			});
			cv = defaultCV;
		}

		if (importSession != null)
			session.as(ExpressoQIS.class).setExpressoEnv(importSession.as(ExpressoQIS.class).getExpressoEnv()).setModels(null, cv);
		ObservableModelSet model = session.interpretChildren("models", ObservableModelSet.class).peekFirst();
		if (model == null)
			model = ObservableModelSet.build(ObservableModelSet.JAVA_NAME_CHECKER).build();
		session.as(ExpressoQIS.class).setModels(model, null);
		QuickStyleSheet styleSheet;
		if (session.getChildren("style-sheet").isEmpty())
			styleSheet = QuickStyleSheet.EMPTY;
		else
			styleSheet = session.interpretChildren("style-sheet", QuickStyleSheet.class).getFirst();
		session.setStyleSheet(styleSheet);
		return new QuickHeadSection(cv, model, styleSheet);
	}

	private QuickDocument modifyWindow(QuickDocument doc, StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		ObservableExpression visibleEx = exS.getAttributeExpression("visible");
		if (visibleEx != null)
			doc.setVisible(visibleEx.evaluate(ModelTypes.Value.forType(Boolean.class), exS.getExpressoEnv()));
		ObservableExpression titleEx = exS.getAttributeExpression("title");
		if (titleEx != null)
			doc.setTitle(titleEx.evaluate(ModelTypes.Value.forType(String.class), exS.getExpressoEnv()));
		doc.withBounds(//
			exS.getAttribute("x", ModelTypes.Value.forType(Integer.class), null), //
			exS.getAttribute("y", ModelTypes.Value.forType(Integer.class), null), //
			exS.getAttribute("width", ModelTypes.Value.forType(Integer.class), null), //
			exS.getAttribute("height", ModelTypes.Value.forType(Integer.class), null) //
			);
		switch (session.getAttributeText("close-action")) {
		case "do-nothing":
			doc.setCloseAction(WindowConstants.DO_NOTHING_ON_CLOSE);
			break;
		case "hide":
			doc.setCloseAction(WindowConstants.HIDE_ON_CLOSE);
			break;
		case "dispose":
			doc.setCloseAction(WindowConstants.DISPOSE_ON_CLOSE);
			break;
		case "exit":
			doc.setCloseAction(WindowConstants.EXIT_ON_CLOSE);
			break;
		default:
			throw new IllegalStateException("Unrecognized close-action: " + session.getAttributeText("close-action"));
		}
		return doc;
	}

	private static boolean isMouseListening;
	private static volatile Point theMouseLocation;
	private static volatile boolean isLeftPressed;
	private static volatile boolean isRightPressed;

	private void initMouseListening() {
		if (isMouseListening)
			return;
		synchronized (QuickCore.class) {
			if (isMouseListening)
				return;
			Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
				@Override
				public void eventDispatched(AWTEvent event) {
					MouseEvent mouse = (MouseEvent) event;
					theMouseLocation = mouse.getLocationOnScreen();
					switch (mouse.getID()) {
					case MouseEvent.MOUSE_PRESSED:
						isLeftPressed |= SwingUtilities.isLeftMouseButton(mouse);
						isRightPressed |= SwingUtilities.isRightMouseButton(mouse);
						break;
					case MouseEvent.MOUSE_RELEASED:
						if (SwingUtilities.isLeftMouseButton(mouse))
							isLeftPressed = false;
						if (SwingUtilities.isRightMouseButton(mouse))
							isRightPressed = false;
						break;
					}
				}
			}, MouseEvent.MOUSE_EVENT_MASK | MouseEvent.MOUSE_MOTION_EVENT_MASK);
			isMouseListening = true;
		}
	}

	static class MouseValueSupport extends ObservableValue.LazyObservableValue<Boolean> implements SettableValue<Boolean>, MouseListener {
		private Component theComponent;
		private final SuppliedModelValue<SettableValue<?>, SettableValue<Boolean>> theModelValue;
		private final Boolean theButton;
		private BiConsumer<Boolean, Object> theListener;
		private boolean isListening;

		public MouseValueSupport(SuppliedModelValue<SettableValue<?>, SettableValue<Boolean>> modelValue, Boolean button) {
			super(TypeTokens.get().BOOLEAN, Transactable.noLock(ThreadConstraint.EDT));
			theModelValue = modelValue;
			theButton = button;
		}

		void install(Component component) {
			theComponent = component;
			if (theListener != null)
				setListening(true);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(theComponent, theModelValue.getName());
		}

		@Override
		protected Boolean getSpontaneous() {
			if (theComponent == null)
				return false;
			boolean compVisible;
			if (theComponent instanceof JComponent)
				compVisible = ((JComponent) theComponent).isShowing();
			else
				compVisible = theComponent.isVisible();
			if (!compVisible)
				return false;
			if (theButton == null) { // No button filter
			} else if (theButton) { // Left
				if (!isLeftPressed)
					return false;
			} else { // Right
				if (!isRightPressed)
					return false;
			}
			Point screenPos;
			try {
				screenPos = theComponent.getLocationOnScreen();
			} catch (IllegalComponentStateException e) {
				return false;
			}
			if (screenPos == null)
				return false;
			Point mousePos = theMouseLocation;
			if (mousePos == null || mousePos.x < screenPos.x || mousePos.y < screenPos.y)
				return false;
			if (mousePos.x >= screenPos.x + theComponent.getWidth() || mousePos.y >= screenPos.y + theComponent.getHeight())
				return false;
			Component child = theComponent.getComponentAt(mousePos.x - screenPos.x, mousePos.y - screenPos.y);
			return child == null || !child.isVisible();
		}

		@Override
		protected Subscription subscribe(BiConsumer<Boolean, Object> listener) {
			theListener = listener;
			setListening(true);
			return () -> setListening(false);
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public boolean isLockSupported() {
			return false;
		}

		@Override
		public Boolean set(Boolean value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String isAcceptable(Boolean value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return SettableValue.ALWAYS_DISABLED;
		}

		private void setListening(boolean listening) {
			if (listening == isListening)
				return;
			if (listening && (theComponent == null || theListener == null))
				return;
			isListening = listening;
			if (listening)
				theComponent.addMouseListener(this);
			else if (theComponent != null)
				theComponent.removeMouseListener(this);
			if (!listening)
				theListener = null;
		}

		@Override
		public void mousePressed(MouseEvent e) {
			if (theListener == null)
				return;
			if (theButton == null) { // No button filter
				return;
			} else if (theButton) { // Left
				if (!SwingUtilities.isLeftMouseButton(e))
					return;
			} else { // Right
				if (!SwingUtilities.isRightMouseButton(e))
					return;
			}
			theListener.accept(true, e);
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			if (theListener == null)
				return;
			if (theButton == null) { // No button filter
				return;
			} else if (theButton) { // Left
				if (!SwingUtilities.isLeftMouseButton(e))
					return;
			} else { // Right
				if (!SwingUtilities.isRightMouseButton(e))
					return;
			}
			theListener.accept(false, e);
		}

		@Override
		public void mouseEntered(MouseEvent e) {
			if (theListener == null)
				return;
			if (theButton == null) { // No button filter
			} else if (theButton) { // Left
				if (!isLeftPressed)
					return;
			} else { // Right
				if (!isRightPressed)
					return;
			}
			theListener.accept(true, e);
		}

		@Override
		public void mouseExited(MouseEvent e) {
			if (theListener == null)
				return;
			if (theButton == null) { // No button filter
			} else if (theButton) { // Left
				if (!isLeftPressed)
					return;
			} else { // Right
				if (!isRightPressed)
					return;
			}
			theListener.accept(false, e);
		}

		@Override
		public void mouseClicked(MouseEvent e) {
		}
	}

	private QuickComponentDef modifyWidget(QuickComponentDef widget, StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		widget.modify((editor, builder) -> editor.modifyComponent(comp -> {
			builder.withComponent(comp);
			exS.installInterpretedValue(comp, builder.getModels());
		}));
		String name = session.getAttribute("name", String.class);
		ValueContainer<SettableValue<?>, SettableValue<String>> tooltip = exS.getAttribute("tooltip",
			ModelTypes.Value.forType(String.class), null);
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> visible = exS.getAttribute("visible",
			ModelTypes.Value.forType(boolean.class), null);
		QuickBorder border = session.interpretChildren("border", QuickBorder.class).peekFirst();
		if (name != null) {
			widget.modify((comp, build) -> {
				comp.modifyComponent(c -> c.setName(name));
			});
		}
		if (tooltip != null) {
			widget.modify((comp, builder) -> {
				comp.withTooltip(tooltip.apply(builder.getModels()));
			});
		}
		QuickElementStyleAttribute<? extends Color> bgColorStyle = widget.getStyle().get(//
			session.getStyleAttribute(null, "color", Color.class));
		SuppliedModelValue<SettableValue<?>, SettableValue<Boolean>> hovered = exS.getModelValueOwner().getModelValue("hovered",
			ModelTypes.Value.forType(boolean.class));
		SuppliedModelValue<SettableValue<?>, SettableValue<Boolean>> focused = exS.getModelValueOwner().getModelValue("focused",
			ModelTypes.Value.forType(boolean.class));
		SuppliedModelValue<SettableValue<?>, SettableValue<Boolean>> pressed = exS.getModelValueOwner().getModelValue("pressed",
			ModelTypes.Value.forType(boolean.class));
		SuppliedModelValue<SettableValue<?>, SettableValue<Boolean>> rightPressed = exS.getModelValueOwner().getModelValue("rightPressed",
			ModelTypes.Value.forType(boolean.class));
		initMouseListening();
		// Install style model value support
		if (!DynamicModelValue.isDynamicValueSatisfied("hovered", ModelTypes.Value.forType(boolean.class), comp.getModels()))
			exS.satisfy(hovered, Component.class, () -> new MouseValueSupport(hovered, null), MouseValueSupport::install);
		if (!exS.isSatisfied(pressed, Component.class))
			exS.satisfy(pressed, Component.class, () -> new MouseValueSupport(pressed, true), MouseValueSupport::install);
		if (!exS.isSatisfied(rightPressed, Component.class))
			exS.satisfy(pressed, Component.class, () -> new MouseValueSupport(rightPressed, false), MouseValueSupport::install);
		if (!exS.isSatisfied(focused, Component.class)) {
			class FocusSupport extends ObservableValue.LazyObservableValue<Boolean> implements SettableValue<Boolean>, FocusListener {
				private Component theComponent;
				private BiConsumer<Boolean, Object> theListener;
				private boolean isListening;

				FocusSupport() {
					super(TypeTokens.get().BOOLEAN, Transactable.noLock(ThreadConstraint.EDT));
				}

				void install(Component component) {
					theComponent = component;
					if (theListener != null)
						setListening(true);
				}

				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(theComponent, focused.getName());
				}

				@Override
				protected Boolean getSpontaneous() {
					return theComponent != null && theComponent.isFocusOwner();
				}

				@Override
				protected Subscription subscribe(BiConsumer<Boolean, Object> listener) {
					theListener = listener;
					setListening(true);
					return () -> setListening(false);
				}

				@Override
				public Transaction lock(boolean write, Object cause) {
					return Transaction.NONE;
				}

				@Override
				public Transaction tryLock(boolean write, Object cause) {
					return Transaction.NONE;
				}

				@Override
				public boolean isLockSupported() {
					return false;
				}

				@Override
				public Boolean set(Boolean value, Object cause)
					throws IllegalArgumentException, UnsupportedOperationException {
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				}

				@Override
				public String isAcceptable(Boolean value) {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public ObservableValue<String> isEnabled() {
					return SettableValue.ALWAYS_DISABLED;
				}

				private void setListening(boolean listening) {
					if (listening == isListening)
						return;
					if (listening && (theComponent == null || theListener == null))
						return;
					isListening = listening;
					if (listening)
						theComponent.addFocusListener(this);
					else if (theComponent != null)
						theComponent.removeFocusListener(this);
					if (!listening)
						theListener = null;
				}

				@Override
				public void focusGained(FocusEvent e) {
					if (theListener != null)
						theListener.accept(true, e);
				}

				@Override
				public void focusLost(FocusEvent e) {
					if (theListener != null)
						theListener.accept(false, e);
				}
			}
			exS.satisfy(focused, Component.class, () -> new FocusSupport(), FocusSupport::install);
		}
		List<QuickMouseListener.Container> mouseListeners = session.asElement("widget").interpretChildren("mouse-listener",
			QuickMouseListener.Container.class);
		List<QuickKeyListener.Container> keyListeners = session.asElement("widget").interpretChildren("key-listener",
			QuickKeyListener.Container.class);
		widget.modify((comp, builder) -> {
			// Listeners
			List<QuickMouseListener> motionListeners = new ArrayList<>();
			List<QuickMouseListener> nonMotionListeners = new ArrayList<>();
			List<QuickMouseListener> wheelListeners = new ArrayList<>();
			for (QuickMouseListener.Container ml : mouseListeners) {
				if (ml.isWheelListener())
					wheelListeners.add(ml.createListener(builder.getModels()));
				else if (ml.isMotionListener())
					motionListeners.add(ml.createListener(builder.getModels()));
				else
					nonMotionListeners.add(ml.createListener(builder.getModels()));
			}
			List<QuickKeyListener> keyListeners2 = new ArrayList<>();
			for (QuickKeyListener.Container kl : keyListeners)
				keyListeners2.add(kl.createListener(builder.getModels()));
			MouseAdapter mouseListener = new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					mouseEvent(e, nonMotionListeners);
				}

				@Override
				public void mousePressed(MouseEvent e) {
					mouseEvent(e, nonMotionListeners);
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					mouseEvent(e, nonMotionListeners);
				}

				@Override
				public void mouseEntered(MouseEvent e) {
					mouseEvent(e, nonMotionListeners);
				}

				@Override
				public void mouseExited(MouseEvent e) {
					mouseEvent(e, nonMotionListeners);
				}

				@Override
				public void mouseMoved(MouseEvent e) {
					mouseEvent(e, motionListeners);
				}

				private void mouseEvent(MouseEvent e, List<QuickMouseListener> listeners) {
					for (QuickMouseListener listener : listeners) {
						if (listener.applies(e))
							listener.actionPerformed(e);
					}
				}

				@Override
				public void mouseWheelMoved(MouseWheelEvent e) {
					for (QuickMouseListener listener : wheelListeners) {
						if (listener.applies(e))
							listener.actionPerformed(e);
					}
				}
			};
			KeyListener keyListener = new KeyListener() {
				@Override
				public void keyTyped(KeyEvent e) {
					keyEvent(e);
				}

				@Override
				public void keyReleased(KeyEvent e) {
					keyEvent(e);
				}

				@Override
				public void keyPressed(KeyEvent e) {
					keyEvent(e);
				}

				private void keyEvent(KeyEvent e) {
					for (QuickKeyListener listener : keyListeners2) {
						if (listener.applies(e))
							listener.actionPerformed(e);
					}
				}
			};

			comp.modifyComponent(c -> {
				if (!nonMotionListeners.isEmpty())
					c.addMouseListener(mouseListener);
				if (!motionListeners.isEmpty())
					c.addMouseMotionListener(mouseListener);
				if (!wheelListeners.isEmpty())
					c.addMouseWheelListener(mouseListener);
				if (!keyListeners2.isEmpty())
					c.addKeyListener(keyListener);

				// Set style
				boolean[] preOpaque = new boolean[1];
				ObservableValue<? extends Color> bgColor = bgColorStyle.evaluate(builder.getModels());
				Color[] oldBG = new Color[] { c.getBackground() };
				bgColor.changes().takeUntil(builder.getModels().getUntil()).act(evt -> {
					if (evt.getOldValue() == null) {
						oldBG[0] = c.getBackground();
						preOpaque[0] = c instanceof JComponent && ((JComponent) c).isOpaque();
					}
					Color colorV = evt.getNewValue();
					if (colorV != null) {
						c.setBackground(colorV);
						if (c instanceof JComponent)
							((JComponent) c).setOpaque(colorV.getAlpha() > 0);
						c.repaint();
					} else if (c.getBackground() == evt.getOldValue()) {
						c.setBackground(oldBG[0]);
						if (c instanceof JComponent)
							((JComponent) c).setOpaque(preOpaque[0]);
					} else {
						oldBG[0] = c.getBackground();
						preOpaque[0] = c instanceof JComponent && ((JComponent) c).isOpaque();
					}
				});
			});
		});
		if (visible != null)
			widget.modify((comp, builder) -> {
				comp.visibleWhen(visible.apply(builder.getModels()));
			});
		if (border != null)
			widget.modify((comp, builder) -> {
				comp.modifyComponent(c -> {
					if (c instanceof JComponent) {
						JComponent jc = (JComponent) c;
						border.createBorder(c, builder).changes().takeUntil(builder.getModels().getUntil()).act(evt -> {
							if (evt.getNewValue() != jc.getBorder())
								jc.setBorder(evt.getNewValue());
							jc.repaint();
						});
					}
				});
			});
		return widget;
	}

	private QuickComponentDef modifyTextWidget(QuickComponentDef widget, StyleQIS session) throws QonfigInterpretationException {
		QuickElementStyleAttribute<? extends Color> fontColorStyle = widget.getStyle()
			.get(session.getStyleAttribute(null, "font-color", Color.class));
		QuickElementStyleAttribute<? extends Double> fontSizeStyle = widget.getStyle()
			.get(session.getStyleAttribute(null, "font-size", double.class));
		QuickElementStyleAttribute<? extends Double> fontWeightStyle = widget.getStyle()
			.get(session.getStyleAttribute(null, "font-weight", double.class));
		QuickElementStyleAttribute<? extends Double> fontSlantStyle = widget.getStyle()
			.get(session.getStyleAttribute(null, "font-slant", double.class));

		widget.modify((comp, builder) -> {
			comp.modifyComponent(c -> {
				Font defaultFont = c.getFont();
				ObservableValue<? extends Color> fontColor = fontColorStyle.evaluate(builder.getModels());
				ObservableValue<? extends Double> fontSize = fontSizeStyle.evaluate(builder.getModels());
				ObservableValue<? extends Double> fontWeight = fontWeightStyle.evaluate(builder.getModels());
				ObservableValue<? extends Double> fontSlant = fontSlantStyle.evaluate(builder.getModels());
				ObservableValue<Font> font = getFont(defaultFont, fontColor, fontSize, fontWeight, fontSlant);
				font.changes().takeUntil(builder.getModels().getUntil()).act(evt -> {
					c.setFont(evt.getNewValue());
					c.invalidate();
				});
			});
		});
		return widget;
	}

	public static ObservableValue<Font> getFont(Font baseFont, ObservableValue<? extends Color> color,
		ObservableValue<? extends Double> size, ObservableValue<? extends Double> weight, ObservableValue<? extends Double> slant) {
		return ObservableValue.assemble(TypeTokens.get().of(java.awt.Font.class), () -> {
			Map<AttributedCharacterIterator.Attribute, Object> attribs = new HashMap<>();
			Color c = color.get();
			Double sz = size.get();
			Double w = weight.get();
			Double s = slant.get();
			if (c == null && sz == null && w == null && s == null)
				return baseFont;
			// attribs.put(TextAttribute.FAMILY, family.get());
			if (sz != null)
				attribs.put(TextAttribute.SIZE, sz);
			attribs.put(TextAttribute.BACKGROUND, Colors.transparent);
			if (c != null)
				attribs.put(TextAttribute.FOREGROUND, c);
			// if(kerning.get())
			// attribs.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
			// if(ligs.get())
			// attribs.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
			// switch (underline.get()) {
			// case none:
			// break;
			// case on:
			// attribs.put(TextAttribute.INPUT_METHOD_UNDERLINE, TextAttribute.UNDERLINE_LOW_ONE_PIXEL);
			// break;
			// case heavy:
			// attribs.put(TextAttribute.INPUT_METHOD_UNDERLINE, TextAttribute.UNDERLINE_LOW_TWO_PIXEL);
			// break;
			// case dashed:
			// attribs.put(TextAttribute.INPUT_METHOD_UNDERLINE, TextAttribute.UNDERLINE_LOW_DASHED);
			// break;
			// case dotted:
			// attribs.put(TextAttribute.INPUT_METHOD_UNDERLINE, TextAttribute.UNDERLINE_LOW_DOTTED);
			// break;
			// }
			if (w != null)
				attribs.put(TextAttribute.WEIGHT, w);
			// attribs.put(TextAttribute.STRIKETHROUGH, strike.get());
			if (s != null)
				attribs.put(TextAttribute.POSTURE, s);
			return baseFont.deriveFont(attribs);
		}, /*family,*/ color, /*kerning, ligs, underline,*/ size, weight, /*strike,*/ slant);
	}

	private QuickBorder interpretLineBorder(StyleQIS session) throws QonfigInterpretationException {
		QuickElementStyle style = session.getStyle();
		QuickElementStyleAttribute<? extends Color> colorStyle = style.get(session.getStyleAttribute(null, "border-color", Color.class));
		QuickElementStyleAttribute<? extends Integer> thicknessStyle = style
			.get(session.getStyleAttribute(null, "thickness", Integer.class));
		return (comp, builder) -> {
			ModifiableLineBorder border = new ModifiableLineBorder(Color.black, 1, false);
			SettableValue<Border> borderV = SettableValue.build(Border.class).withValue(border).build();
			colorStyle.evaluate(builder.getModels()).changes().takeUntil(builder.getModels().getUntil()).act(evt -> {
				border.setColor(evt.getNewValue());
				borderV.set(border, evt);
			});
			thicknessStyle.evaluate(builder.getModels()).changes().takeUntil(builder.getModels().getUntil()).act(evt -> {
				border.setThickness(evt.getNewValue());
				borderV.set(border, evt);
			});
			return borderV;
		};
	}

	private QuickBorder interpretTitledBorder(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		Function<ModelSetInstance, SettableValue<String>> title = exS.getAttribute("title", ModelTypes.Value.forType(String.class), null);
		QuickElementStyle style = session.getStyle();
		QuickElementStyleAttribute<? extends Color> colorStyle = style.get(session.getStyleAttribute(null, "border-color", Color.class));
		QuickElementStyleAttribute<? extends Integer> thicknessStyle = style
			.get(session.getStyleAttribute(null, "thickness", Integer.class));
		QuickElementStyleAttribute<? extends Color> fontColorStyle = style.get(session.getStyleAttribute(null, "font-color", Color.class));
		QuickElementStyleAttribute<? extends Double> fontSizeStyle = style.get(session.getStyleAttribute(null, "font-size", double.class));
		QuickElementStyleAttribute<? extends Double> fontWeightStyle = style
			.get(session.getStyleAttribute(null, "font-weight", double.class));
		QuickElementStyleAttribute<? extends Double> fontSlantStyle = style
			.get(session.getStyleAttribute(null, "font-slant", double.class));

		return (comp, builder) -> {
			ModifiableLineBorder lineBorder = new ModifiableLineBorder(Color.black, 1, false);
			TitledBorder border = BorderFactory.createTitledBorder(lineBorder, "");
			Font defaultFont = border.getTitleFont();
			ObservableValue<? extends Color> fontColor = fontColorStyle.evaluate(builder.getModels());
			ObservableValue<? extends Double> fontSize = fontSizeStyle.evaluate(builder.getModels());
			ObservableValue<? extends Double> fontWeight = fontWeightStyle.evaluate(builder.getModels());
			ObservableValue<? extends Double> fontSlant = fontSlantStyle.evaluate(builder.getModels());
			ObservableValue<Font> font = getFont(defaultFont, fontColor, fontSize, fontWeight, fontSlant);
			SettableValue<Border> borderV = SettableValue.build(Border.class).withValue(border).build();
			title.apply(builder.getModels()).changes().takeUntil(builder.getModels().getUntil()).act(evt -> {
				border.setTitle(evt.getNewValue());
				borderV.set(border, evt);
				comp.repaint();
			});
			font.changes().takeUntil(builder.getModels().getUntil()).act(evt -> {
				border.setTitleFont(evt.getNewValue());
				comp.invalidate();
				comp.repaint();
			});
			colorStyle.evaluate(builder.getModels()).changes().takeUntil(builder.getModels().getUntil()).act(evt -> {
				Color c = evt.getNewValue() == null ? Color.black : evt.getNewValue();
				lineBorder.setColor(c);
				border.setTitleColor(c);
				borderV.set(border, evt);
				comp.repaint();
			});
			thicknessStyle.evaluate(builder.getModels()).changes().takeUntil(builder.getModels().getUntil()).act(evt -> {
				lineBorder.setThickness(evt.getNewValue() == null ? 1 : evt.getNewValue());
				borderV.set(border, evt);
				comp.repaint();
			});
			return borderV;
		};
	}

	private QuickMouseListener.Container interpretMouseListener(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exSession = session.as(ExpressoQIS.class);
		List<ValueContainer<SettableValue<?>, SettableValue<Boolean>>> filters = new ArrayList<>();
		for (ExpressoQIS filterS : exSession.forChildren("filter"))
			filters.add(filterS.getValueAsValue(boolean.class, null));
		ValueContainer<ObservableAction<?>, ObservableAction<?>> action = exSession.getValueExpression().evaluate(ModelTypes.Action.any(),
			exSession.getExpressoEnv());

		SuppliedModelValue<SettableValue<?>, SettableValue<Integer>> x = exSession.getModelValueOwner().getModelValue("x",
			ModelTypes.Value.forType(int.class));
		SuppliedModelValue<SettableValue<?>, SettableValue<Integer>> y = exSession.getModelValueOwner().getModelValue("y",
			ModelTypes.Value.forType(int.class));
		SuppliedModelValue<SettableValue<?>, SettableValue<Boolean>> alt = exSession.getModelValueOwner().getModelValue("altPressed",
			ModelTypes.Value.forType(boolean.class));
		SuppliedModelValue<SettableValue<?>, SettableValue<Boolean>> ctrl = exSession.getModelValueOwner().getModelValue("ctrlPressed",
			ModelTypes.Value.forType(boolean.class));
		SuppliedModelValue<SettableValue<?>, SettableValue<Boolean>> shift = exSession.getModelValueOwner().getModelValue("shiftPressed",
			ModelTypes.Value.forType(boolean.class));
		ExpressoQIS.SimpleModelValueSupport<Integer> xSupport = new ExpressoQIS.SimpleModelValueSupport<>(int.class, 0);
		ExpressoQIS.SimpleModelValueSupport<Integer> ySupport = new ExpressoQIS.SimpleModelValueSupport<>(int.class, 0);
		ExpressoQIS.SimpleModelValueSupport<Boolean> altSupport = new ExpressoQIS.SimpleModelValueSupport<>(boolean.class, false);
		ExpressoQIS.SimpleModelValueSupport<Boolean> ctrlSupport = new ExpressoQIS.SimpleModelValueSupport<>(boolean.class, false);
		ExpressoQIS.SimpleModelValueSupport<Boolean> shiftSupport = new ExpressoQIS.SimpleModelValueSupport<>(boolean.class, false);
		exSession.satisfy(x, Component.class, xSupport, null);
		exSession.satisfy(y, Component.class, ySupport, null);
		exSession.satisfy(alt, Component.class, altSupport, null);
		exSession.satisfy(ctrl, Component.class, ctrlSupport, null);
		exSession.satisfy(shift, Component.class, shiftSupport, null);

		return new QuickMouseListener.Container() {
			@Override
			public boolean isMotionListener() {
				return false;
			}

			@Override
			public boolean isWheelListener() {
				return false;
			}

			@Override
			public QuickMouseListener createListener(ModelSetInstance models) {
				List<SettableValue<Boolean>> filterVs = filters.stream().map(f -> f.get(models)).collect(Collectors.toList());
				ObservableAction<?> actionV = action.get(models);
				SettableValue<Integer> xV = exSession.getIfSupported(x.apply(models), xSupport);
				SettableValue<Integer> yV = exSession.getIfSupported(y.apply(models), ySupport);
				SettableValue<Boolean> altV = exSession.getIfSupported(alt.apply(models), altSupport);
				SettableValue<Boolean> ctrlV = exSession.getIfSupported(ctrl.apply(models), ctrlSupport);
				SettableValue<Boolean> shiftV = exSession.getIfSupported(shift.apply(models), shiftSupport);
				return new QuickMouseListener() {
					@Override
					public boolean applies(MouseEvent evt) {
						for (SettableValue<Boolean> filterV : filterVs) {
							if (filterV != null && !filterV.get())
								return false;
						}
						return true;
					}

					@Override
					public void actionPerformed(MouseEvent evt) {
						try (CausableInUse cause = Causable.cause(evt)) {
							xV.set(evt.getX(), cause);
							yV.set(evt.getY(), cause);
							altV.set(evt.isAltDown(), cause);
							ctrlV.set(evt.isControlDown(), cause);
							shiftV.set(evt.isShiftDown(), cause);
							actionV.act(evt);
						}
					}
				};
			}
		};
	}

	private QuickMouseListener.Container modifyMouseButtonListener(QuickMouseListener.Container listener, int buttonID)
		throws QonfigInterpretationException {
		return new QuickMouseListener.Container() {
			@Override
			public boolean isMotionListener() {
				return listener.isMotionListener();
			}

			@Override
			public boolean isWheelListener() {
				return listener.isWheelListener();
			}

			@Override
			public QuickMouseListener createListener(ModelSetInstance models) {
				QuickMouseListener superL = listener.createListener(models);
				return new QuickMouseListener() {
					@Override
					public boolean applies(MouseEvent evt) {
						if (!superL.applies(evt))
							return false;
						else if (evt.getButton() != buttonID)
							return false;
						return true;
					}

					@Override
					public void actionPerformed(MouseEvent evt) {
						superL.actionPerformed(evt);
					}
				};
			}
		};
	}

	private QuickMouseListener.Container modifyMouseClickListener(QuickMouseListener.Container listener, StyleQIS session)
		throws QonfigInterpretationException {
		ExpressoQIS exSession = session.as(ExpressoQIS.class);
		ValueContainer<SettableValue<?>, SettableValue<Integer>> clickCount = exSession.getAttributeAsValue("click-count", Integer.class,
			null);
		SuppliedModelValue<SettableValue<?>, SettableValue<Integer>> clickCountMV = exSession.getModelValueOwner()
			.getModelValue("clickCount", ModelTypes.Value.forType(int.class));
		ExpressoQIS.SimpleModelValueSupport<Integer> ccSupport = new ExpressoQIS.SimpleModelValueSupport<>(int.class, 0);
		exSession.satisfy(clickCountMV, Component.class, ccSupport, null);
		return new QuickMouseListener.Container() {
			@Override
			public boolean isMotionListener() {
				return listener.isMotionListener();
			}

			@Override
			public boolean isWheelListener() {
				return listener.isWheelListener();
			}

			@Override
			public QuickMouseListener createListener(ModelSetInstance models) {
				QuickMouseListener superL = listener.createListener(models);
				ObservableValue<Integer> clickCountV = clickCount == null ? null : clickCount.get(models);
				SettableValue<Integer> ccMV = exSession.getIfSupported(clickCountMV.apply(models), ccSupport);
				return new QuickMouseListener() {
					@Override
					public boolean applies(MouseEvent evt) {
						if (!superL.applies(evt))
							return false;
						else if (evt.getID() != MouseEvent.MOUSE_CLICKED)
							return false;
						if (clickCountV != null) {
							Integer clockCountI = clickCountV.get();
							if (clockCountI != null && clockCountI > 0 && evt.getClickCount() != clockCountI)
								return false;
						}
						return true;
					}

					@Override
					public void actionPerformed(MouseEvent evt) {
						try (CausableInUse cause = Causable.cause(evt)) {
							ccMV.set(evt.getClickCount(), cause);
							superL.actionPerformed(evt);
						}
					}
				};
			}
		};
	}

	private QuickMouseListener.Container modifyMouseListenerWithType(QuickMouseListener.Container listener, int eventType, boolean motion)
		throws QonfigInterpretationException {
		return new QuickMouseListener.Container() {
			@Override
			public boolean isMotionListener() {
				return motion || listener.isMotionListener();
			}

			@Override
			public boolean isWheelListener() {
				return listener.isWheelListener();
			}

			@Override
			public QuickMouseListener createListener(ModelSetInstance models) {
				QuickMouseListener superL = listener.createListener(models);
				return new QuickMouseListener() {
					@Override
					public boolean applies(MouseEvent evt) {
						if (evt.getID() != eventType)
							return false;
						else if (!superL.applies(evt))
							return false;
						return true;
					}

					@Override
					public void actionPerformed(MouseEvent evt) {
						superL.actionPerformed(evt);
					}
				};
			}
		};
	}

	private QuickMouseListener.Container modifyMouseWheelListener(QuickMouseListener.Container listener, StyleQIS session)
		throws QonfigInterpretationException {
		ExpressoQIS exSession = session.as(ExpressoQIS.class);
		SuppliedModelValue<SettableValue<?>, SettableValue<Integer>> scrollAmountMV = exSession.getModelValueOwner()
			.getModelValue("scrollAmount", ModelTypes.Value.forType(int.class));
		ExpressoQIS.SimpleModelValueSupport<Integer> scrollSupport = new ExpressoQIS.SimpleModelValueSupport<>(int.class, 0);
		exSession.satisfy(scrollAmountMV, Component.class, scrollSupport, null);
		return new QuickMouseListener.Container() {
			@Override
			public boolean isMotionListener() {
				return listener.isMotionListener();
			}

			@Override
			public boolean isWheelListener() {
				return true;
			}

			@Override
			public QuickMouseListener createListener(ModelSetInstance models) {
				QuickMouseListener superL = listener.createListener(models);
				SettableValue<Integer> scrollMV = exSession.getIfSupported(scrollAmountMV.apply(models), scrollSupport);
				return new QuickMouseListener() {
					@Override
					public boolean applies(MouseEvent evt) {
						if (!superL.applies(evt))
							return false;
						else if (evt.getID() != MouseEvent.MOUSE_WHEEL_EVENT_MASK)
							return false;
						return true;
					}

					@Override
					public void actionPerformed(MouseEvent evt) {
						try (CausableInUse cause = Causable.cause(evt)) {
							scrollMV.set(((MouseWheelEvent) evt).getScrollAmount(), cause);
							superL.actionPerformed(evt);
						}
					}
				};
			}
		};
	}

	private QuickKeyListener.Container createKeyListener(StyleQIS session, boolean pressed) throws QonfigInterpretationException {
		ExpressoQIS exSession = session.as(ExpressoQIS.class);
		List<ValueContainer<SettableValue<?>, SettableValue<Boolean>>> filters = new ArrayList<>();
		for (ExpressoQIS filterS : exSession.forChildren("filter"))
			filters.add(filterS.getValueAsValue(boolean.class, null));
		ValueContainer<ObservableAction<?>, ObservableAction<?>> action = exSession.getValueExpression().evaluate(ModelTypes.Action.any(),
			exSession.getExpressoEnv());
		ValueContainer<SettableValue<?>, SettableValue<KeyCode>> key = exSession.getAttributeAsValue("key", KeyCode.class, null);

		SuppliedModelValue<SettableValue<?>, SettableValue<KeyCode>> keyMV = exSession.getModelValueOwner().getModelValue("key",
			ModelTypes.Value.forType(KeyCode.class));
		SuppliedModelValue<SettableValue<?>, SettableValue<Boolean>> alt = exSession.getModelValueOwner().getModelValue("altPressed",
			ModelTypes.Value.forType(boolean.class));
		SuppliedModelValue<SettableValue<?>, SettableValue<Boolean>> ctrl = exSession.getModelValueOwner().getModelValue("ctrlPressed",
			ModelTypes.Value.forType(boolean.class));
		SuppliedModelValue<SettableValue<?>, SettableValue<Boolean>> shift = exSession.getModelValueOwner().getModelValue("shiftPressed",
			ModelTypes.Value.forType(boolean.class));
		ExpressoQIS.SimpleModelValueSupport<KeyCode> keySupport = new ExpressoQIS.SimpleModelValueSupport<>(KeyCode.class, null);
		ExpressoQIS.SimpleModelValueSupport<Boolean> altSupport = new ExpressoQIS.SimpleModelValueSupport<>(boolean.class, false);
		ExpressoQIS.SimpleModelValueSupport<Boolean> ctrlSupport = new ExpressoQIS.SimpleModelValueSupport<>(boolean.class, false);
		ExpressoQIS.SimpleModelValueSupport<Boolean> shiftSupport = new ExpressoQIS.SimpleModelValueSupport<>(boolean.class, false);
		exSession.satisfy(keyMV, Component.class, keySupport, null);
		exSession.satisfy(alt, Component.class, altSupport, null);
		exSession.satisfy(ctrl, Component.class, ctrlSupport, null);
		exSession.satisfy(shift, Component.class, shiftSupport, null);

		int eventType = pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED;
		return models -> {
			List<SettableValue<Boolean>> filterVs = filters.stream().map(f -> f.get(models)).collect(Collectors.toList());
			SettableValue<KeyCode> keyV = key == null ? null : key.get(models);
			ObservableAction<?> actionV = action.get(models);
			SettableValue<KeyCode> keyMV2 = exSession.getIfSupported(keyMV.apply(models), keySupport);
			SettableValue<Boolean> altV = exSession.getIfSupported(alt.apply(models), altSupport);
			SettableValue<Boolean> ctrlV = exSession.getIfSupported(ctrl.apply(models), ctrlSupport);
			SettableValue<Boolean> shiftV = exSession.getIfSupported(shift.apply(models), shiftSupport);
			return new QuickKeyListener() {
				@Override
				public boolean applies(KeyEvent evt) {
					if (evt.getID() != eventType)
						return false;
					else if (keyV != null && getKeyCodeFromAWT(evt.getKeyCode(), evt.getKeyLocation()) != keyV.get())
						return false;
					for (SettableValue<Boolean> filterV : filterVs) {
						if (filterV != null && !filterV.get())
							return false;
					}
					return true;
				}

				@Override
				public void actionPerformed(KeyEvent evt) {
					try (CausableInUse cause = Causable.cause(evt)) {
						keyMV2.set(getKeyCodeFromAWT(evt.getKeyCode(), evt.getKeyLocation()), cause);
						altV.set(evt.isAltDown(), cause);
						ctrlV.set(evt.isControlDown(), cause);
						shiftV.set(evt.isShiftDown(), cause);
						actionV.act(evt);
					}
				}
			};
		};
	}

	/**
	 * @param keyCode The key code (see java.awt.KeyEvent.VK_*, {@link KeyEvent#getKeyCode()})
	 * @param keyLocation The key's location (see java.awt.KeyEvent.KEY_LOCATION_*, {@link KeyEvent#getKeyLocation()}
	 * @return The Quick key code for the AWT codes
	 */
	public static KeyCode getKeyCodeFromAWT(int keyCode, int keyLocation) {
		switch (keyCode) {
		case KeyEvent.VK_ENTER:
			return KeyCode.ENTER;
		case KeyEvent.VK_BACK_SPACE:
			return KeyCode.BACKSPACE;
		case KeyEvent.VK_TAB:
			return KeyCode.TAB;
		case KeyEvent.VK_CANCEL:
			return KeyCode.CANCEL;
		case KeyEvent.VK_CLEAR:
			return KeyCode.CLEAR;
		case KeyEvent.VK_SHIFT:
			if (keyLocation == KeyEvent.KEY_LOCATION_LEFT)
				return KeyCode.SHIFT_LEFT;
			else
				return KeyCode.SHIFT_RIGHT;
		case KeyEvent.VK_CONTROL:
			if (keyLocation == KeyEvent.KEY_LOCATION_LEFT)
				return KeyCode.CTRL_LEFT;
			else
				return KeyCode.CTRL_RIGHT;
		case KeyEvent.VK_ALT:
			if (keyLocation == KeyEvent.KEY_LOCATION_LEFT)
				return KeyCode.ALT_LEFT;
			else
				return KeyCode.ALT_RIGHT;
		case KeyEvent.VK_PAUSE:
			return KeyCode.PAUSE;
		case KeyEvent.VK_CAPS_LOCK:
			return KeyCode.CAPS_LOCK;
		case KeyEvent.VK_ESCAPE:
			return KeyCode.ESCAPE;
		case KeyEvent.VK_SPACE:
			return KeyCode.SPACE;
		case KeyEvent.VK_PAGE_UP:
			return KeyCode.PAGE_UP;
		case KeyEvent.VK_PAGE_DOWN:
			return KeyCode.PAGE_DOWN;
		case KeyEvent.VK_END:
			return KeyCode.END;
		case KeyEvent.VK_HOME:
			return KeyCode.HOME;
		case KeyEvent.VK_LEFT:
			return KeyCode.LEFT_ARROW;
		case KeyEvent.VK_UP:
			return KeyCode.UP_ARROW;
		case KeyEvent.VK_RIGHT:
			return KeyCode.RIGHT_ARROW;
		case KeyEvent.VK_DOWN:
			return KeyCode.DOWN_ARROW;
		case KeyEvent.VK_COMMA:
		case KeyEvent.VK_LESS:
			return KeyCode.COMMA;
		case KeyEvent.VK_MINUS:
			if (keyLocation == KeyEvent.KEY_LOCATION_NUMPAD)
				return KeyCode.PAD_MINUS;
			else
				return KeyCode.MINUS;
		case KeyEvent.VK_UNDERSCORE:
			return KeyCode.MINUS;
		case KeyEvent.VK_PERIOD:
			if (keyLocation == KeyEvent.KEY_LOCATION_NUMPAD)
				return KeyCode.PAD_DOT;
			else
				return KeyCode.DOT;
		case KeyEvent.VK_GREATER:
			return KeyCode.DOT;
		case KeyEvent.VK_SLASH:
			if (keyLocation == KeyEvent.KEY_LOCATION_NUMPAD)
				return KeyCode.PAD_SLASH;
			else
				return KeyCode.FORWARD_SLASH;
		case KeyEvent.VK_0:
		case KeyEvent.VK_RIGHT_PARENTHESIS:
			return KeyCode.NUM_0;
		case KeyEvent.VK_1:
		case KeyEvent.VK_EXCLAMATION_MARK:
			return KeyCode.NUM_1;
		case KeyEvent.VK_2:
		case KeyEvent.VK_AT:
			return KeyCode.NUM_2;
		case KeyEvent.VK_3:
		case KeyEvent.VK_NUMBER_SIGN:
			return KeyCode.NUM_3;
		case KeyEvent.VK_4:
		case KeyEvent.VK_DOLLAR:
			return KeyCode.NUM_4;
		case KeyEvent.VK_5:
			return KeyCode.NUM_5;
		case KeyEvent.VK_6:
		case KeyEvent.VK_CIRCUMFLEX:
			return KeyCode.NUM_6;
		case KeyEvent.VK_7:
		case KeyEvent.VK_AMPERSAND:
			return KeyCode.NUM_7;
		case KeyEvent.VK_8:
		case KeyEvent.VK_ASTERISK:
			return KeyCode.NUM_8;
		case KeyEvent.VK_9:
		case KeyEvent.VK_LEFT_PARENTHESIS:
			return KeyCode.NUM_9;
		case KeyEvent.VK_SEMICOLON:
		case KeyEvent.VK_COLON:
			return KeyCode.SEMICOLON;
		case KeyEvent.VK_EQUALS:
			if (keyLocation == KeyEvent.KEY_LOCATION_NUMPAD)
				return KeyCode.PAD_EQUAL;
			else
				return KeyCode.EQUAL;
		case KeyEvent.VK_A:
			return KeyCode.A;
		case KeyEvent.VK_B:
			return KeyCode.B;
		case KeyEvent.VK_C:
			return KeyCode.C;
		case KeyEvent.VK_D:
			return KeyCode.D;
		case KeyEvent.VK_E:
			return KeyCode.E;
		case KeyEvent.VK_F:
			return KeyCode.F;
		case KeyEvent.VK_G:
			return KeyCode.G;
		case KeyEvent.VK_H:
			return KeyCode.H;
		case KeyEvent.VK_I:
			return KeyCode.I;
		case KeyEvent.VK_J:
			return KeyCode.J;
		case KeyEvent.VK_K:
			return KeyCode.K;
		case KeyEvent.VK_L:
			return KeyCode.L;
		case KeyEvent.VK_M:
			return KeyCode.M;
		case KeyEvent.VK_N:
			return KeyCode.N;
		case KeyEvent.VK_O:
			return KeyCode.O;
		case KeyEvent.VK_P:
			return KeyCode.P;
		case KeyEvent.VK_Q:
			return KeyCode.Q;
		case KeyEvent.VK_R:
			return KeyCode.R;
		case KeyEvent.VK_S:
			return KeyCode.S;
		case KeyEvent.VK_T:
			return KeyCode.T;
		case KeyEvent.VK_U:
			return KeyCode.U;
		case KeyEvent.VK_V:
			return KeyCode.V;
		case KeyEvent.VK_W:
			return KeyCode.W;
		case KeyEvent.VK_X:
			return KeyCode.X;
		case KeyEvent.VK_Y:
			return KeyCode.Y;
		case KeyEvent.VK_Z:
			return KeyCode.Z;
		case KeyEvent.VK_OPEN_BRACKET:
		case KeyEvent.VK_BRACELEFT:
			return KeyCode.LEFT_BRACE;
		case KeyEvent.VK_BACK_SLASH:
			return KeyCode.BACK_SLASH;
		case KeyEvent.VK_CLOSE_BRACKET:
		case KeyEvent.VK_BRACERIGHT:
			return KeyCode.RIGHT_BRACE;
		case KeyEvent.VK_NUMPAD0:
			return KeyCode.PAD_0;
		case KeyEvent.VK_NUMPAD1:
			return KeyCode.PAD_1;
		case KeyEvent.VK_NUMPAD2:
		case KeyEvent.VK_KP_DOWN:
			return KeyCode.PAD_2;
		case KeyEvent.VK_NUMPAD3:
			return KeyCode.PAD_3;
		case KeyEvent.VK_NUMPAD4:
		case KeyEvent.VK_KP_LEFT:
			return KeyCode.PAD_4;
		case KeyEvent.VK_NUMPAD5:
			return KeyCode.PAD_5;
		case KeyEvent.VK_NUMPAD6:
		case KeyEvent.VK_KP_RIGHT:
			return KeyCode.PAD_6;
		case KeyEvent.VK_NUMPAD7:
			return KeyCode.PAD_7;
		case KeyEvent.VK_NUMPAD8:
		case KeyEvent.VK_KP_UP:
			return KeyCode.PAD_8;
		case KeyEvent.VK_NUMPAD9:
			return KeyCode.PAD_9;
		case KeyEvent.VK_MULTIPLY:
			return KeyCode.PAD_MULTIPLY;
		case KeyEvent.VK_ADD:
			return KeyCode.PAD_PLUS;
		case KeyEvent.VK_SEPARATOR:
			return KeyCode.PAD_SEPARATOR;
		case KeyEvent.VK_SUBTRACT:
			return KeyCode.PAD_MINUS;
		case KeyEvent.VK_DECIMAL:
			return KeyCode.PAD_DOT;
		case KeyEvent.VK_DIVIDE:
			return KeyCode.PAD_SLASH;
		case KeyEvent.VK_DELETE:
			return KeyCode.PAD_BACKSPACE;
		case KeyEvent.VK_NUM_LOCK:
			return KeyCode.NUM_LOCK;
		case KeyEvent.VK_SCROLL_LOCK:
			return KeyCode.SCROLL_LOCK;
		case KeyEvent.VK_F1:
			return KeyCode.F1;
		case KeyEvent.VK_F2:
			return KeyCode.F2;
		case KeyEvent.VK_F3:
			return KeyCode.F3;
		case KeyEvent.VK_F4:
			return KeyCode.F4;
		case KeyEvent.VK_F5:
			return KeyCode.F5;
		case KeyEvent.VK_F6:
			return KeyCode.F6;
		case KeyEvent.VK_F7:
			return KeyCode.F7;
		case KeyEvent.VK_F8:
			return KeyCode.F8;
		case KeyEvent.VK_F9:
			return KeyCode.F9;
		case KeyEvent.VK_F10:
			return KeyCode.F10;
		case KeyEvent.VK_F11:
			return KeyCode.F11;
		case KeyEvent.VK_F12:
			return KeyCode.F12;
		case KeyEvent.VK_F13:
			return KeyCode.F13;
		case KeyEvent.VK_F14:
			return KeyCode.F14;
		case KeyEvent.VK_F15:
			return KeyCode.F15;
		case KeyEvent.VK_F16:
			return KeyCode.F16;
		case KeyEvent.VK_F17:
			return KeyCode.F17;
		case KeyEvent.VK_F18:
			return KeyCode.F18;
		case KeyEvent.VK_F19:
			return KeyCode.F19;
		case KeyEvent.VK_F20:
			return KeyCode.F20;
		case KeyEvent.VK_F21:
			return KeyCode.F21;
		case KeyEvent.VK_F22:
			return KeyCode.F22;
		case KeyEvent.VK_F23:
			return KeyCode.F23;
		case KeyEvent.VK_F24:
			return KeyCode.F24;
		case KeyEvent.VK_PRINTSCREEN:
			return KeyCode.PRINT_SCREEN;
		case KeyEvent.VK_INSERT:
			return KeyCode.INSERT;
		case KeyEvent.VK_HELP:
			return KeyCode.HELP;
		case KeyEvent.VK_META:
			return KeyCode.META;
		case KeyEvent.VK_BACK_QUOTE:
			return KeyCode.BACK_QUOTE;
		case KeyEvent.VK_QUOTE:
		case KeyEvent.VK_QUOTEDBL:
			return KeyCode.QUOTE;
		case KeyEvent.VK_WINDOWS:
			return KeyCode.COMMAND_KEY;
		case KeyEvent.VK_CONTEXT_MENU:
			return KeyCode.CONTEXT_MENU;
		default:
			return null;
		}
	}

	private QuickKeyListener.Container createKeyTypeListener(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exSession = session.as(ExpressoQIS.class);
		List<ValueContainer<SettableValue<?>, SettableValue<Boolean>>> filters = new ArrayList<>();
		for (ExpressoQIS filterS : exSession.forChildren("filter"))
			filters.add(filterS.getValueAsValue(boolean.class, null));
		ValueContainer<ObservableAction<?>, ObservableAction<?>> action = exSession.getValueExpression().evaluate(ModelTypes.Action.any(),
			exSession.getExpressoEnv());
		String typedChar = exSession.getAttributeText("char");
		if (typedChar != null && typedChar.length() != 1)
			session.withError("char attribute must only have a single character");

		SuppliedModelValue<SettableValue<?>, SettableValue<Character>> charMV = exSession.getModelValueOwner().getModelValue("char",
			ModelTypes.Value.forType(Character.class));
		SuppliedModelValue<SettableValue<?>, SettableValue<Boolean>> alt = exSession.getModelValueOwner().getModelValue("altPressed",
			ModelTypes.Value.forType(boolean.class));
		SuppliedModelValue<SettableValue<?>, SettableValue<Boolean>> ctrl = exSession.getModelValueOwner().getModelValue("ctrlPressed",
			ModelTypes.Value.forType(boolean.class));
		SuppliedModelValue<SettableValue<?>, SettableValue<Boolean>> shift = exSession.getModelValueOwner().getModelValue("shiftPressed",
			ModelTypes.Value.forType(boolean.class));
		ExpressoQIS.SimpleModelValueSupport<Character> charSupport = new ExpressoQIS.SimpleModelValueSupport<>(Character.class, (char) 0);
		ExpressoQIS.SimpleModelValueSupport<Boolean> altSupport = new ExpressoQIS.SimpleModelValueSupport<>(boolean.class, false);
		ExpressoQIS.SimpleModelValueSupport<Boolean> ctrlSupport = new ExpressoQIS.SimpleModelValueSupport<>(boolean.class, false);
		ExpressoQIS.SimpleModelValueSupport<Boolean> shiftSupport = new ExpressoQIS.SimpleModelValueSupport<>(boolean.class, false);
		exSession.satisfy(charMV, Component.class, charSupport, null);
		exSession.satisfy(alt, Component.class, altSupport, null);
		exSession.satisfy(ctrl, Component.class, ctrlSupport, null);
		exSession.satisfy(shift, Component.class, shiftSupport, null);

		return models -> {
			List<SettableValue<Boolean>> filterVs = filters.stream().map(f -> f.get(models)).collect(Collectors.toList());
			ObservableAction<?> actionV = action.get(models);
			SettableValue<Character> charMV2 = exSession.getIfSupported(charMV.apply(models), charSupport);
			SettableValue<Boolean> altV = exSession.getIfSupported(alt.apply(models), altSupport);
			SettableValue<Boolean> ctrlV = exSession.getIfSupported(ctrl.apply(models), ctrlSupport);
			SettableValue<Boolean> shiftV = exSession.getIfSupported(shift.apply(models), shiftSupport);
			return new QuickKeyListener() {
				@Override
				public boolean applies(KeyEvent evt) {
					if (evt.getID() != KeyEvent.KEY_TYPED)
						return false;
					else if (typedChar != null && typedChar.charAt(0) != evt.getKeyChar())
						return false;
					for (SettableValue<Boolean> filterV : filterVs) {
						if (filterV != null && !filterV.get())
							return false;
					}
					return true;
				}

				@Override
				public void actionPerformed(KeyEvent evt) {
					try (CausableInUse cause = Causable.cause(evt)) {
						charMV2.set(evt.getKeyChar(), cause);
						altV.set(evt.isAltDown(), cause);
						ctrlV.set(evt.isControlDown(), cause);
						shiftV.set(evt.isShiftDown(), cause);
						actionV.act(evt);
					}
				}
			};
		};
	}
}

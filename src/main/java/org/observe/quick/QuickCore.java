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
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.font.TextAttribute;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.AttributedCharacterIterator;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.expresso.ClassView;
import org.observe.expresso.Expression.ExpressoParseException;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.QonfigExpression;
import org.observe.quick.QuickComponentDef.ModelValueSupport;
import org.observe.quick.style.FontValueParser;
import org.observe.quick.style.QuickElementStyle;
import org.observe.quick.style.QuickElementStyle.QuickElementStyleAttribute;
import org.observe.quick.style.QuickModelValue;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleSet;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.QuickStyleType;
import org.observe.quick.style.QuickStyleValue;
import org.observe.quick.style.StyleValueApplication;
import org.observe.util.TypeTokens;
import org.observe.util.swing.ComponentDecorator.ModifiableLineBorder;
import org.observe.util.swing.ObservableSwingUtils;
import org.qommons.Colors;
import org.qommons.Identifiable;
import org.qommons.IdentityKey;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Version;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.MultiMap;
import org.qommons.config.DefaultQonfigParser;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigDocument;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigParseException;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

public class QuickCore implements QonfigInterpretation {
	public interface QuickBorder {
		ObservableValue<Border> createBorder(Component component, QuickComponent.Builder builder);
	}

	public static final String STYLE_NAME = "quick-style-name";
	public static final String STYLE_APPLICATION = "quick-parent-style-application";
	public static final String STYLE_ATTRIBUTE = "quick-style-attribute";
	public static final String STYLE_SHEET_REF = "quick-style-sheet-ref";

	public static abstract class StyleValues extends AbstractList<QuickStyleValue<?>> {
		private static final ThreadLocal<LinkedHashSet<IdentityKey<StyleValues>>> STACK = ThreadLocal.withInitial(LinkedHashSet::new);

		private final String theName;
		private List<QuickStyleValue<?>> theValues;

		StyleValues(String name) {
			theName = name;
		}

		public StyleValues init() throws QonfigInterpretationException {
			if (theValues != null)
				return this;
			LinkedHashSet<IdentityKey<StyleValues>> stack = STACK.get();
			if (!stack.add(new IdentityKey<>(this))) {
				StringBuilder str = new StringBuilder("Style sheet cycle detected:");
				for (IdentityKey<StyleValues> se : stack) {
					if (se.value.theName != null)
						str.append(se.value.theName).append("->");
				}
				str.append(theName);
				throw new QonfigInterpretationException(str.toString());
			}
			try {
				theValues = get();
			} finally {
				stack.remove(new IdentityKey<>(this));
			}
			return this;
		}

		protected abstract List<QuickStyleValue<?>> get() throws QonfigInterpretationException;

		@Override
		public QuickStyleValue<?> get(int index) {
			if (theValues == null)
				throw new IllegalStateException("Not initialized");
			return theValues.get(index);
		}

		@Override
		public int size() {
			if (theValues == null)
				throw new IllegalStateException("Not initialized");
			return theValues.size();
		}
	}

	@Override
	public String getToolkitName() {
		return QuickSessionImplV0_1.NAME;
	}

	@Override
	public Version getVersion() {
		return QuickSessionImplV0_1.VERSION;
	}

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return QommonsUtils.unmodifiableDistinctCopy(ExpressoQIS.class, QuickQIS.class);
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

	QuickQIS wrap(CoreSession session) throws QonfigInterpretationException {
		return session.as(QuickQIS.class);
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
		;
		configureStyleInterpreter(interpreter);
		return interpreter;
	}

	private void configureStyleInterpreter(QonfigInterpreterCore.Builder interpreter) {
		interpreter//
		.createWith("style", StyleValues.class, session -> interpretStyle(wrap(session)))//
		.createWith("style-sheet", QuickStyleSheet.class, session -> interpretStyleSheet(wrap(session)))//
		;
	}

	private QuickDocument interpretQuick(QuickQIS session) throws QonfigInterpretationException {
		QuickQIS headSession = session.forChildren("head").getFirst();
		QuickHeadSection head = headSession.interpret(QuickHeadSection.class);
		session.as(ExpressoQIS.class).setExpressoEnv(headSession.as(ExpressoQIS.class).getExpressoEnv());
		session.setStyleSheet(head.getStyleSheet());
		QuickDocument doc = new QuickDocument.QuickDocumentImpl(session.getElement(), head, //
			session.interpretChildren("root", QuickComponentDef.class).getFirst());
		return doc;
	}

	private QuickHeadSection interpretHead(QuickQIS session) throws QonfigInterpretationException {
		QuickQIS importSession = session.forChildren("imports").peekFirst();
		ClassView cv = importSession == null ? null : importSession.interpret(ClassView.class);
		if (cv == null)
			cv = ClassView.build().withWildcardImport("java.lang").build();

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

	private QuickDocument modifyWindow(QuickDocument doc, QuickQIS session) throws QonfigInterpretationException {
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

	static class MouseValueSupport extends ObservableValue.LazyObservableValue<Boolean>
	implements ModelValueSupport<Boolean>, MouseListener {
		private Component theComponent;
		private final QuickModelValue<Boolean> theModelValue;
		private final Boolean theButton;
		private BiConsumer<Boolean, Object> theListener;
		private boolean isListening;

		public MouseValueSupport(QuickModelValue<Boolean> modelValue, Boolean button) {
			super(TypeTokens.get().BOOLEAN, Transactable.noLock(ThreadConstraint.EDT));
			theModelValue = modelValue;
			theButton = button;
		}

		@Override
		public void install(Component component) {
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

	private QuickComponentDef modifyWidget(QuickComponentDef widget, QuickQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		widget.modify((editor, builder) -> editor.modifyComponent(builder::withComponent));
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
		QuickModelValue<Boolean> hovered = session.getStyleModelValue("widget", "hovered", boolean.class);
		QuickModelValue<Boolean> focused = session.getStyleModelValue("widget", "focused", boolean.class);
		QuickModelValue<Boolean> pressed = session.getStyleModelValue("widget", "pressed", boolean.class);
		QuickModelValue<Boolean> rightPressed = session.getStyleModelValue("widget", "rightPressed", boolean.class);
		initMouseListening();
		// Install style model value support
		if (widget.getSupport(hovered) == null)
			widget.support(hovered, () -> new MouseValueSupport(hovered, null));
		if (widget.getSupport(pressed) == null)
			widget.support(pressed, () -> new MouseValueSupport(pressed, true));
		if (widget.getSupport(rightPressed) == null)
			widget.support(rightPressed, () -> new MouseValueSupport(rightPressed, false));
		if (widget.getSupport(focused) == null) {
			class FocusSupport extends ObservableValue.LazyObservableValue<Boolean> implements ModelValueSupport<Boolean>, FocusListener {
				private Component theComponent;
				private BiConsumer<Boolean, Object> theListener;
				private boolean isListening;

				FocusSupport() {
					super(TypeTokens.get().BOOLEAN, Transactable.noLock(ThreadConstraint.EDT));
				}

				@Override
				public void install(Component component) {
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
			widget.support(focused, () -> new FocusSupport());
		}
		widget.modify((comp, builder) -> {
			// Set style
			comp.modifyComponent(c -> {
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

	private QuickComponentDef modifyTextWidget(QuickComponentDef widget, QuickQIS session) throws QonfigInterpretationException {
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

	private QuickBorder interpretLineBorder(QuickQIS session) throws QonfigInterpretationException {
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

	private QuickBorder interpretTitledBorder(QuickQIS session) throws QonfigInterpretationException {
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

	private void modifyForStyle(QuickQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		exS.setExpressoEnv(exS.getExpressoEnv().with(null, null)// Create a copy
			.withNonStructuredParser(double.class, new FontValueParser()));
	}

	private StyleValues interpretStyle(QuickQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		QuickStyleSet styleSet = session.getStyleSet();
		QuickStyleSheet styleSheet = session.getStyleSheet();
		StyleValueApplication application = session.get(STYLE_APPLICATION, StyleValueApplication.class);
		if (application == null)
			application = StyleValueApplication.ALL;
		QuickStyleAttribute<?> attr = session.get(STYLE_ATTRIBUTE, QuickStyleAttribute.class);
		QonfigElement element = (QonfigElement) session.get(QuickQIS.STYLE_ELEMENT);
		modifyForStyle(session);

		String rolePath = session.getAttributeText("child");
		if (rolePath != null) {
			if (application == null)
				throw new QonfigInterpretationException("Cannot specify a style role without a type above it");
			for (String roleName : rolePath.split("\\.")) {
				roleName = roleName.trim();
				QonfigChildDef child = null;
				if (application.getRole() != null)
					child = application.getRole().getType().getChild(roleName);
				for (QonfigElementOrAddOn type : application.getTypes().values()) {
					if (child != null)
						break;
					child = type.getChild(roleName);
				}
				if (child == null)
					throw new QonfigInterpretationException("No such role '" + roleName + "' for parent style " + application);
				application = application.forChild(child);
			}
		}
		String elName = session.getAttributeText("element");
		if (elName != null) {
			QonfigElementOrAddOn el;
			try {
				el = session.getElement().getDocument().getDocToolkit().getElementOrAddOn(elName);
				if (el == null)
					throw new QonfigInterpretationException("No such element found: " + elName);
			} catch (IllegalArgumentException e) {
				throw new QonfigInterpretationException(e.getMessage());
			}
			application = application.forType(el);
		}
		ObservableExpression newCondition = exS.getAttributeExpression("condition");
		if (newCondition != null) {
			MultiMap<String, QuickModelValue<?>> availableModelValues = BetterHashMultiMap.<String, QuickModelValue<?>> build()
				.buildMultiMap();
			for (QonfigElementOrAddOn type : application.getTypes().values()) {
				QuickStyleType styled = styleSet.styled(type, exS);
				availableModelValues.putAll(styled.getModelValues());
			}
			if (element != null) {
				QuickStyleType styled = styleSet.styled(element.getType(), exS);
				availableModelValues.putAll(styled.getModelValues());
				for (QonfigAddOn inh : element.getInheritance().values()) {
					styled = styleSet.styled(inh, exS);
					availableModelValues.putAll(styled.getModelValues());
				}
			}
			application = application.forCondition(newCondition, exS.getExpressoEnv(), availableModelValues);
		}
		session.put(STYLE_APPLICATION, application);

		String attrName = session.getAttributeText("attr");
		if (attrName != null) {
			if (attr != null)
				throw new QonfigInterpretationException(
					"Cannot specify an attribute (" + attrName + ") if an ancestor style has (" + attr + ")");

			Set<QuickStyleAttribute<?>> attrs = new HashSet<>();
			if (element != null) {
				QuickStyleType styled = styleSet.styled(element.getType(), exS);
				if (styled != null)
					attrs.addAll(styleSet.styled(element.getType(), exS).getAttributes(attrName));
				for (QonfigAddOn inh : element.getInheritance().values()) {
					if (attrs.size() > 1)
						break;
					styled = styleSet.styled(inh, exS);
					if (styled != null)
						attrs.addAll(styled.getAttributes(attrName));
				}
			} else {
				for (QonfigElementOrAddOn type : application.getTypes().values()) {
					if (attrs.size() > 1)
						break;
					QuickStyleType styled = styleSet.styled(type, exS);
					if (styled != null)
						attrs.addAll(styled.getAttributes(attrName));
				}
			}
			if (attrs.isEmpty())
				throw new QonfigInterpretationException("No such style attribute: '" + attrName + "'");
			else if (attrs.size() > 1)
				throw new QonfigInterpretationException("Multiple style attributes found matching '" + attrName + "'");
			attr = attrs.iterator().next();
			session.put(STYLE_ATTRIBUTE, attr);
		}
		ObservableExpression value = exS.getValueExpression();
		if ((value != null && value != ObservableExpression.EMPTY) && attr == null)
			throw new QonfigInterpretationException("Cannot specify a style value without an attribute");
		String styleSetName = session.getAttributeText("style-set");
		List<QuickStyleValue<?>> styleSetRef;
		if (styleSetName != null) {
			if (attr != null)
				throw new QonfigInterpretationException("Cannot refer to a style set when an attribute is specified");
			try {
				styleSetRef = styleSheet.getStyleSet(styleSetName);
			} catch (IllegalArgumentException e) {
				throw new QonfigInterpretationException(e.getMessage());
			}
			if (styleSetRef instanceof StyleValues)
				((StyleValues) styleSetRef).init();
		} else
			styleSetRef = null;
		List<StyleValues> subStyles = session.interpretChildren("sub-style", StyleValues.class);
		for (StyleValues subStyle : subStyles)
			subStyle.init();

		StyleValueApplication theApplication = application;
		QuickStyleAttribute<?> theAttr = attr;
		return new StyleValues((String) session.get(STYLE_NAME)) {
			@Override
			protected List<QuickStyleValue<?>> get() throws QonfigInterpretationException {
				List<QuickStyleValue<?>> values = new ArrayList<>();
				if (value != null && value != ObservableExpression.EMPTY)
					values.add(new QuickStyleValue<>(styleSheet, theApplication, theAttr, value, exS.getExpressoEnv()));
				if (styleSetRef != null)
					values.addAll(styleSetRef);
				for (StyleValues child : subStyles)
					values.addAll(child);
				return values;
			}
		};
	}

	private static String printElOptions(Collection<QonfigElementOrAddOn> roleEls) {
		if (roleEls.size() == 1)
			return roleEls.iterator().next().toString();
		StringBuilder str = new StringBuilder().append('(');
		boolean first = true;
		for (QonfigElementOrAddOn el : roleEls) {
			if (first)
				first = false;
			else
				str.append('|');
			str.append(el);
		}
		return str.append(')').toString();
	}

	private QuickStyleSheet interpretStyleSheet(QuickQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		// First import style sheets
		Map<String, QuickStyleSheet> imports = new LinkedHashMap<>();
		DefaultQonfigParser parser = null;
		for (QuickQIS sse : session.forChildren("style-sheet-ref")) {
			String name = sse.getAttributeText("name");
			if (parser == null) {
				parser = new DefaultQonfigParser();
				for (QonfigToolkit tk : session.getElement().getDocument().getDocToolkit().getDependencies().values())
					parser.withToolkit(tk);
			}
			URL ref;
			try {
				String address = sse.getAttributeText("ref");
				String urlStr = QommonsConfig.resolve(address, session.getElement().getDocument().getLocation());
				ref = new URL(urlStr);
			} catch (IOException e) {
				throw new QonfigInterpretationException("Bad style-sheet reference: " + sse.getAttributeText("ref"), e);
			}
			QonfigDocument ssDoc;
			try (InputStream in = new BufferedInputStream(ref.openStream())) {
				ssDoc = parser.parseDocument(ref.toString(), in);
			} catch (IOException e) {
				throw new QonfigInterpretationException("Could not access style-sheet reference " + ref, e);
			} catch (QonfigParseException e) {
				throw new QonfigInterpretationException("Malformed style-sheet reference " + ref, e);
			}
			if (!session.getStyleSet().getCoreToolkit().getElement("style-sheet").isAssignableFrom(ssDoc.getRoot().getType()))
				throw new QonfigInterpretationException(
					"Style-sheet reference does not parse to a style-sheet (" + ssDoc.getRoot().getType() + "): " + ref);
			QuickQIS importSession = session.intepretRoot(ssDoc.getRoot())//
				.put(STYLE_SHEET_REF, ref);
			importSession.as(ExpressoQIS.class)//
			.setModels(ObservableModelSet.build(exS.getExpressoEnv().getModels().getNameChecker()).build(),
				exS.getExpressoEnv().getClassView());
			modifyForStyle(session);
			QuickStyleSheet imported = importSession.interpret(QuickStyleSheet.class);
			imports.put(name, imported);
		}

		// Next, compile style-sets
		Map<String, List<QuickStyleValue<?>>> styleSets = new LinkedHashMap<>();
		for (QuickQIS styleSetEl : session.forChildren("style-set")) {
			String name = styleSetEl.getAttributeText("name");
			styleSetEl.put(STYLE_NAME, name);
			styleSets.put(name, styleSetEl.interpretChildren("style", StyleValues.class).getFirst());
		}

		// Now compile the style-sheet styles
		String name = session.getElement().getDocument().getLocation();
		int slash = name.lastIndexOf('/');
		if (slash >= 0)
			name = name.substring(slash + 1);
		session.put(STYLE_NAME, name);
		List<QuickStyleValue<?>> values = new ArrayList<>();
		QuickStyleSheet styleSheet = new QuickStyleSheet((URL) session.get(STYLE_SHEET_REF), Collections.unmodifiableMap(styleSets),
			Collections.unmodifiableList(values), Collections.unmodifiableMap(imports));
		session.setStyleSheet(styleSheet);
		for (StyleValues sv : session.interpretChildren("style", StyleValues.class)) {
			sv.init();
			values.addAll(sv);
		}

		// Replace the StyleValues instances in the styleSets map with regular lists. Don't keep that silly type around.
		// This also forces parsing of all the values if they weren't referred to internally.
		for (Map.Entry<String, List<QuickStyleValue<?>>> ss : styleSets.entrySet()) {
			((StyleValues) ss.getValue()).init();
			ss.setValue(QommonsUtils.unmodifiableCopy(ss.getValue()));
		}
		return styleSheet;
	}
}

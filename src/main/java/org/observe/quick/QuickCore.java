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
import java.awt.event.MouseAdapter;
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
import org.observe.expresso.ExpressionValueType;
import org.observe.expresso.Expresso;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoInterpreter.ExpressoSession;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.quick.QuickInterpreter.QuickSession;
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
import org.qommons.ArrayUtils;
import org.qommons.Colors;
import org.qommons.Identifiable;
import org.qommons.IdentityKey;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.MultiMap;
import org.qommons.config.DefaultQonfigParser;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigDocument;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreter;
import org.qommons.config.QonfigParseException;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.QonfigToolkitAccess;

public class QuickCore<QIS extends QuickSession<?>> extends Expresso<QIS> {
	public static final QonfigToolkitAccess CORE = new QonfigToolkitAccess(QuickCore.class, "quick-core.qtd", Expresso.EXPRESSO)
		.withCustomValueType(//
			new QuickPosition.PositionValueType(Expresso.EXPRESSION_PARSER), //
			new QuickSize.SizeValueType(Expresso.EXPRESSION_PARSER));

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

	public static Function<ModelSetInstance, SettableValue<QuickPosition>> parsePosition(ObservableExpression expression, ExpressoEnv env)
		throws QonfigInterpretationException {
		if (expression == null)
			return null;
		Function<ModelSetInstance, SettableValue<QuickPosition>> positionValue;
		Function<String, QuickPosition> colorParser = str -> {
			try {
				return QuickPosition.parse(str);
			} catch (NumberFormatException e) {
				System.err.println("Could not evaluate '" + str + "' as a position from " + expression + ": " + e.getMessage());
				e.printStackTrace();
				// There's really no sensible default, but this is better than causing NPEs
				return new QuickPosition(50, QuickPosition.PositionUnit.Percent);
			}
		};
		if (expression instanceof ExpressionValueType.Literal)
			positionValue = expression.evaluate(ModelTypes.Value.forType(String.class), env)//
			.andThen(v -> SettableValue.asSettable(v.map(colorParser), __ -> "Cannot reverse position"));
		else {
			try {
				positionValue = expression.evaluate(ModelTypes.Value.forType(QuickPosition.class), env);
			} catch (QonfigInterpretationException e1) {
				// If it doesn't parse as a position, try parsing as a number.
				try {
					positionValue = expression.evaluate(ModelTypes.Value.forType(int.class), env)//
						.andThen(v -> v.transformReversible(QuickPosition.class, tx -> tx
							.map(d -> new QuickPosition(d, QuickPosition.PositionUnit.Pixels)).withReverse(pos -> Math.round(pos.value))));
				} catch (QonfigInterpretationException e2) {
					// If it doesn't parse as a position or a number, try parsing as a string and then parse that as a position
					positionValue = expression.evaluate(ModelTypes.Value.forType(String.class), env)//
						.andThen(v -> v.transformReversible(QuickPosition.class,
							tx -> tx.map(colorParser).withReverse(QuickPosition::toString)));
				}
			}
		}
		return positionValue;
	}

	public static Function<ModelSetInstance, SettableValue<Icon>> parseIcon(ObservableExpression expression, ExpressoSession<?> session,
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
				Class<?> callingClass = session.getInterpreter().getCallingClass();
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

	@Override
	public QuickInterpreter.Builder<QIS, ?> createInterpreter(Class<?> callingClass, QonfigToolkit... others) {
		return (QuickInterpreter.Builder<QIS, ?>) QuickInterpreter.build(callingClass, ArrayUtils.add(others, 0, CORE.get()));
	}

	@Override
	public <B extends QonfigInterpreter.Builder<? extends QIS, B>> B configureInterpreter(B interpreter) {
		super.configureInterpreter(interpreter);
		B coreInterpreter = interpreter.forToolkit(CORE.get());
		coreInterpreter//
		.createWith("quick", QuickDocument.class, this::interpretQuick)//
		.createWith("head", QuickHeadSection.class, this::interpretHead)//
		.modifyWith("window", QuickDocument.class, this::modifyWindow)//
		.modifyWith("widget", QuickComponentDef.class, this::modifyWidget)//
		.modifyWith("text-widget", QuickComponentDef.class, this::modifyTextWidget)//
		.createWith("line-border", QuickBorder.class, this::interpretLineBorder)//
		.createWith("titled-border", QuickBorder.class, this::interpretTitledBorder)//
		;
		configureStyleInterpreter(coreInterpreter);
		return interpreter;
	}

	private void configureStyleInterpreter(QonfigInterpreter.Builder<? extends QIS, ?> interpreter) {
		interpreter//
		.createWith("style", StyleValues.class, this::interpretStyle)//
		.createWith("style-sheet", QuickStyleSheet.class, this::interpretStyleSheet)//
		;
	}

	private QuickDocument interpretQuick(QIS session) throws QonfigInterpretationException {
		QuickHeadSection head = session.interpretChildren("head", QuickHeadSection.class).getFirst();
		session.setModels(head.getModels(), head.getImports());
		session.setStyleSheet(head.getStyleSheet());
		QuickDocument doc = new QuickDocument.QuickDocumentImpl(session.getElement(), head, //
			session.interpretChildren("root", QuickComponentDef.class).getFirst());
		return doc;
	}

	private QuickHeadSection interpretHead(QIS session) throws QonfigInterpretationException {
		ClassView cv = session.interpretChildren("imports", ClassView.class).peekFirst();
		if (cv == null)
			cv = ClassView.build().withWildcardImport("java.lang").build();
		session.setModels(null, cv);
		ObservableModelSet model = session.interpretChildren("models", ObservableModelSet.class).peekFirst();
		if (model == null)
			model = ObservableModelSet.build(ObservableModelSet.JAVA_NAME_CHECKER).build();
		session.setModels(model, null);
		QuickStyleSheet styleSheet;
		if (session.getChildren("style-sheet").isEmpty())
			styleSheet = QuickStyleSheet.EMPTY;
		else
			styleSheet = session.interpretChildren("style-sheet", QuickStyleSheet.class).getFirst();
		session.setStyleSheet(styleSheet);
		return new QuickHeadSection(cv, model, styleSheet);
	}

	private QuickDocument modifyWindow(QuickDocument doc, QIS session) throws QonfigInterpretationException {
		ObservableExpression visibleEx = session.getAttribute("visible", ObservableExpression.class);
		if (visibleEx != null)
			doc.setVisible(visibleEx.evaluate(ModelTypes.Value.forType(Boolean.class), session.getExpressoEnv()));
		ObservableExpression titleEx = session.getAttribute("title", ObservableExpression.class);
		if (titleEx != null)
			doc.setTitle(titleEx.evaluate(ModelTypes.Value.forType(String.class), session.getExpressoEnv()));
		doc.withBounds(//
			session.interpretAttribute("x", ObservableExpression.class, true,
				ex -> ex.evaluate(ModelTypes.Value.forType(Integer.class), session.getExpressoEnv())), //
			session.interpretAttribute("y", ObservableExpression.class, true,
				ex -> ex.evaluate(ModelTypes.Value.forType(Integer.class), session.getExpressoEnv())), //
			session.interpretAttribute("width", ObservableExpression.class, true,
				ex -> ex.evaluate(ModelTypes.Value.forType(Integer.class), session.getExpressoEnv())), //
			session.interpretAttribute("height", ObservableExpression.class, true,
				ex -> ex.evaluate(ModelTypes.Value.forType(Integer.class), session.getExpressoEnv())) //
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

	private QuickComponentDef modifyWidget(QuickComponentDef widget, QIS session) throws QonfigInterpretationException {
		widget.modify((editor, builder) -> editor.modifyComponent(builder::withComponent));
		String name = session.getAttribute("name", String.class);
		ObservableExpression tooltipX = session.getAttribute("tooltip", ObservableExpression.class);
		ObservableExpression visibleX = session.getAttribute("visible", ObservableExpression.class);
		QuickBorder border = session.interpretChildren("border", QuickBorder.class).peekFirst();
		ValueContainer<SettableValue<?>, SettableValue<String>> tooltip = tooltipX == null ? null
			: tooltipX.evaluate(ModelTypes.Value.forType(String.class), session.getExpressoEnv());
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> visible = visibleX == null ? null
			: visibleX.evaluate(ModelTypes.Value.forType(boolean.class), session.getExpressoEnv());
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
		class MouseValueSupport extends ObservableValue.LazyObservableValue<Boolean> {
			private final Component theComponent;
			private final QuickModelValue<Boolean> theModelValue;
			private final Boolean theButton;

			public MouseValueSupport(Component component, QuickModelValue<Boolean> modelValue, Boolean button) {
				super(TypeTokens.get().BOOLEAN, Transactable.noLock(ThreadConstraint.EDT));
				theComponent = component;
				theModelValue = modelValue;
				theButton = button;
			}

			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(theComponent, theModelValue.getName());
			}

			@Override
			protected Boolean getSpontaneous() {
				Component c = theComponent;
				boolean compVisible;
				if (c instanceof JComponent)
					compVisible = ((JComponent) c).isShowing();
				else
					compVisible = c.isVisible();
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
					screenPos = c.getLocationOnScreen();
				} catch (IllegalComponentStateException e) {
					return false;
				}
				if (screenPos == null)
					return false;
				Point mousePos = theMouseLocation;
				if (mousePos == null || mousePos.x < screenPos.x || mousePos.y < screenPos.y)
					return false;
				if (mousePos.x >= screenPos.x + c.getWidth() || mousePos.y >= screenPos.y + c.getHeight())
					return false;
				Component child = c.getComponentAt(mousePos.x - screenPos.x, mousePos.y - screenPos.y);
				return child == null || !child.isVisible();
			}

			@Override
			protected Subscription subscribe(BiConsumer<Boolean, Object> listener) {
				MouseListener mouseListener = new MouseAdapter() {
					@Override
					public void mousePressed(MouseEvent e) {
						if (theButton == null) { // No button filter
							return;
						} else if (theButton) { // Left
							if (!SwingUtilities.isLeftMouseButton(e))
								return;
						} else { // Right
							if (!SwingUtilities.isRightMouseButton(e))
								return;
						}
						listener.accept(true, e);
					}

					@Override
					public void mouseReleased(MouseEvent e) {
						if (theButton == null) { // No button filter
							return;
						} else if (theButton) { // Left
							if (!SwingUtilities.isLeftMouseButton(e))
								return;
						} else { // Right
							if (!SwingUtilities.isRightMouseButton(e))
								return;
						}
						listener.accept(false, e);
					}

					@Override
					public void mouseEntered(MouseEvent e) {
						if (theButton == null) { // No button filter
						} else if (theButton) { // Left
							if (!isLeftPressed)
								return;
						} else { // Right
							if (!isRightPressed)
								return;
						}
						listener.accept(true, e);
					}

					@Override
					public void mouseExited(MouseEvent e) {
						if (theButton == null) { // No button filter
						} else if (theButton) { // Left
							if (!isLeftPressed)
								return;
						} else { // Right
							if (!isRightPressed)
								return;
						}
						listener.accept(false, e);
					}
				};
				theComponent.addMouseListener(mouseListener);
				return () -> theComponent.removeMouseListener(mouseListener);
			}
		}
		widget.support(hovered, comp -> new MouseValueSupport(comp, hovered, null));
		widget.support(pressed, comp -> new MouseValueSupport(comp, pressed, true));
		widget.support(rightPressed, comp -> new MouseValueSupport(comp, rightPressed, false));
		widget.support(focused,
			comp -> new ObservableValue.LazyObservableValue<Boolean>(TypeTokens.get().BOOLEAN, Transactable.noLock(ThreadConstraint.EDT)) {
				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(comp, focused.getName());
				}

				@Override
				protected Boolean getSpontaneous() {
					return comp.isFocusOwner();
				}

				@Override
				protected Subscription subscribe(BiConsumer<Boolean, Object> listener) {
					FocusListener focusListener = new FocusListener() {
						@Override
						public void focusGained(FocusEvent e) {
							listener.accept(true, e);
						}

						@Override
						public void focusLost(FocusEvent e) {
							listener.accept(false, e);
						}
					};
					comp.addFocusListener(focusListener);
					return () -> comp.removeFocusListener(focusListener);
				}
			});
		widget.modify((comp, builder) -> {
			// Set style
			comp.modifyComponent(c -> {
				Color defaultBG = c.getBackground();
				boolean defaultOpaque = c instanceof JComponent && ((JComponent) c).isOpaque();
				ObservableValue<? extends Color> bgColor = bgColorStyle.evaluate(builder.getModels(), builder::getParentModels);
				bgColor.changes().takeUntil(builder.getModels().getUntil()).act(evt -> {
					Color colorV = evt.getNewValue();
					if (colorV != null) {
						c.setBackground(colorV);
						if (c instanceof JComponent)
							((JComponent) c).setOpaque(colorV.getAlpha() > 0);
						c.repaint();
					} else if (c.getBackground() == evt.getOldValue()) { // If this is set by someone else, don't override with the original
						c.setBackground(defaultBG);
						if (c instanceof JComponent)
							((JComponent) c).setOpaque(defaultOpaque);
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

	private QuickComponentDef modifyTextWidget(QuickComponentDef widget, QIS session) throws QonfigInterpretationException {
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
				ObservableValue<? extends Color> fontColor = fontColorStyle.evaluate(builder.getModels(), builder::getParentModels);
				ObservableValue<? extends Double> fontSize = fontSizeStyle.evaluate(builder.getModels(), builder::getParentModels);
				ObservableValue<? extends Double> fontWeight = fontWeightStyle.evaluate(builder.getModels(), builder::getParentModels);
				ObservableValue<? extends Double> fontSlant = fontSlantStyle.evaluate(builder.getModels(), builder::getParentModels);
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

	private QuickBorder interpretLineBorder(QIS session) throws QonfigInterpretationException {
		QuickElementStyle style = session.getStyle();
		QuickElementStyleAttribute<? extends Color> colorStyle = style.get(session.getStyleAttribute(null, "border-color", Color.class));
		QuickElementStyleAttribute<? extends Integer> thicknessStyle = style
			.get(session.getStyleAttribute(null, "thickness", Integer.class));
		return (comp, builder) -> {
			ModifiableLineBorder border = new ModifiableLineBorder(Color.black, 1, false);
			SettableValue<Border> borderV = SettableValue.build(Border.class).withValue(border).build();
			colorStyle.evaluate(builder.getModels(), builder::getParentModels).changes().takeUntil(builder.getModels().getUntil())
			.act(evt -> {
				border.setColor(evt.getNewValue());
				borderV.set(border, evt);
			});
			thicknessStyle.evaluate(builder.getModels(), builder::getParentModels).changes().takeUntil(builder.getModels().getUntil())
			.act(evt -> {
				border.setThickness(evt.getNewValue());
				borderV.set(border, evt);
			});
			return borderV;
		};
	}

	private QuickBorder interpretTitledBorder(QIS session) throws QonfigInterpretationException {
		ObservableExpression titleX = session.getAttribute("title", ObservableExpression.class);
		Function<ModelSetInstance, SettableValue<String>> title = titleX.evaluate(ModelTypes.Value.forType(String.class),
			session.getExpressoEnv());
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
			ObservableValue<? extends Color> fontColor = fontColorStyle.evaluate(builder.getModels(), builder::getParentModels);
			ObservableValue<? extends Double> fontSize = fontSizeStyle.evaluate(builder.getModels(), builder::getParentModels);
			ObservableValue<? extends Double> fontWeight = fontWeightStyle.evaluate(builder.getModels(), builder::getParentModels);
			ObservableValue<? extends Double> fontSlant = fontSlantStyle.evaluate(builder.getModels(), builder::getParentModels);
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
			colorStyle.evaluate(builder.getModels(), builder::getParentModels).changes().takeUntil(builder.getModels().getUntil())
			.act(evt -> {
				Color c = evt.getNewValue() == null ? Color.black : evt.getNewValue();
				lineBorder.setColor(c);
				border.setTitleColor(c);
				borderV.set(border, evt);
				comp.repaint();
			});
			thicknessStyle.evaluate(builder.getModels(), builder::getParentModels).changes().takeUntil(builder.getModels().getUntil())
			.act(evt -> {
				lineBorder.setThickness(evt.getNewValue() == null ? 1 : evt.getNewValue());
				borderV.set(border, evt);
				comp.repaint();
			});
			return borderV;
		};
	}

	private StyleValues interpretStyle(QIS session) throws QonfigInterpretationException {
		QuickStyleSet styleSet = session.getInterpreter().getStyleSet();
		QuickStyleSheet styleSheet = session.getStyleSheet();
		StyleValueApplication application = session.get(STYLE_APPLICATION, StyleValueApplication.class);
		if (application == null)
			application = StyleValueApplication.ALL;
		QuickStyleAttribute<?> attr = session.get(STYLE_ATTRIBUTE, QuickStyleAttribute.class);
		QonfigElement element = (QonfigElement) session.get(QuickInterpreter.STYLE_ELEMENT);

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
		ObservableExpression newCondition = session.getAttribute("condition", ObservableExpression.class);
		if (newCondition != null) {
			MultiMap<String, QuickModelValue<?>> availableModelValues = BetterHashMultiMap.<String, QuickModelValue<?>> build()
				.buildMultiMap();
			for (QonfigElementOrAddOn type : application.getTypes().values()) {
				QuickStyleType styled = styleSet.styled(type, session);
				availableModelValues.putAll(styled.getModelValues());
			}
			if (element != null) {
				QuickStyleType styled = styleSet.styled(element.getType(), session);
				availableModelValues.putAll(styled.getModelValues());
				for (QonfigAddOn inh : element.getInheritance().values()) {
					styled = styleSet.styled(inh, session);
					availableModelValues.putAll(styled.getModelValues());
				}
			}
			application = application.forCondition(newCondition, session.getExpressoEnv(), availableModelValues);
		}
		session.put(STYLE_APPLICATION, application);

		String attrName = session.getAttributeText("attr");
		if (attrName != null) {
			if (attr != null)
				throw new QonfigInterpretationException(
					"Cannot specify an attribute (" + attrName + ") if an ancestor style has (" + attr + ")");

			Set<QuickStyleAttribute<?>> attrs = new HashSet<>();
			if (element != null) {
				QuickStyleType styled = styleSet.styled(element.getType(), session);
				if (styled != null)
					attrs.addAll(styleSet.styled(element.getType(), session).getAttributes(attrName));
				for (QonfigAddOn inh : element.getInheritance().values()) {
					if (attrs.size() > 1)
						break;
					styled = styleSet.styled(inh, session);
					if (styled != null)
						attrs.addAll(styled.getAttributes(attrName));
				}
			} else {
				for (QonfigElementOrAddOn type : application.getTypes().values()) {
					if (attrs.size() > 1)
						break;
					QuickStyleType styled = styleSet.styled(type, session);
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
		ObservableExpression value = session.getValue(ObservableExpression.class, null);
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
					values.add(
						new QuickStyleValue<>(styleSheet, theApplication, theAttr, value, session.getExpressoEnv()));
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

	private QuickStyleSheet interpretStyleSheet(QIS session) throws QonfigInterpretationException {
		// First import style sheets
		Map<String, QuickStyleSheet> imports = new LinkedHashMap<>();
		DefaultQonfigParser parser = null;
		for (ExpressoSession<?> sse : session.forChildren("style-sheet-ref")) {
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
			if (!QuickCore.CORE.get().getElement("style-sheet").isAssignableFrom(ssDoc.getRoot().getType()))
				throw new QonfigInterpretationException(
					"Style-sheet reference does not parse to a style-sheet (" + ssDoc.getRoot().getType() + "): " + ref);
			QuickSession<?> importSession = session.getInterpreter().interpret(ssDoc.getRoot())//
				.put(STYLE_SHEET_REF, ref)//
				.setModels(ObservableModelSet.build(session.getExpressoEnv().getModels().getNameChecker()).build(),
					session.getExpressoEnv().getClassView());
			QuickStyleSheet imported = importSession.interpret(QuickStyleSheet.class);
			imports.put(name, imported);
		}

		// Next, compile style-sets
		Map<String, List<QuickStyleValue<?>>> styleSets = new LinkedHashMap<>();
		for (ExpressoSession<?> styleSetEl : session.forChildren("style-set")) {
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

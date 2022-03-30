package org.observe.quick;

import java.awt.Color;
import java.awt.Image;
import java.net.URL;
import java.text.ParseException;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ClassView;
import org.observe.expresso.ExpressionValueType;
import org.observe.expresso.ExpressoInterpreter;
import org.observe.expresso.ExpressoInterpreter.ExpressoSession;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelQonfigParser;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.observe.util.swing.ComponentDecorator.ModifiableLineBorder;
import org.observe.util.swing.ObservableSwingUtils;
import org.qommons.Colors;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.QonfigToolkitAccess;

public class QuickCore extends ObservableModelQonfigParser {
	public static final QonfigToolkitAccess CORE = new QonfigToolkitAccess(QuickCore.class, "quick-core.qtd",
		ObservableModelQonfigParser.OBSERVE).withCustomValueType(//
			new QuickPosition.PositionValueType(ObservableModelQonfigParser.EXPRESSION_PARSER), //
			new QuickSize.SizeValueType(ObservableModelQonfigParser.EXPRESSION_PARSER));

	public interface QuickBorder extends Function<ModelSetInstance, SettableValue<Border>> {
	}

	public static Function<ModelSetInstance, SettableValue<Color>> parseColor(ObservableExpression expression, ObservableModelSet model,
		ClassView cv) throws QonfigInterpretationException {
		if (expression == null)
			return null;
		Function<ModelSetInstance, SettableValue<Color>> colorValue;
		Function<String, Color> colorParser = str -> {
			try {
				return Colors.parseIfColor(str);
			} catch (ParseException e) {
				System.err.println("Could not evaluate '" + str + "' as a color from " + expression + ": " + e.getMessage());
				e.printStackTrace();
				return Colors.transparent;
			}
		};
		if (expression instanceof ExpressionValueType.Literal)
			colorValue = expression.evaluate(ModelTypes.Value.forType(String.class), model, cv)//
			.andThen(v -> SettableValue.asSettable(v.map(colorParser), __ -> "Cannot reverse color"));
		else {
			try {
				colorValue = expression.evaluate(ModelTypes.Value.forType(Color.class), model, cv);
			} catch (QonfigInterpretationException e1) {
				// If it doesn't parse as a java color, parse it as a string and then parse that as a color
				colorValue = expression.evaluate(ModelTypes.Value.forType(String.class), model, cv)//
					.andThen(v -> SettableValue.asSettable(v.map(colorParser), __ -> "Cannot reverse color"));
			}
		}
		return colorValue;
	}

	public static Function<ModelSetInstance, SettableValue<QuickPosition>> parsePosition(ObservableExpression expression,
		ObservableModelSet model, ClassView cv) throws QonfigInterpretationException {
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
			positionValue = expression.evaluate(ModelTypes.Value.forType(String.class), model, cv)//
			.andThen(v -> SettableValue.asSettable(v.map(colorParser), __ -> "Cannot reverse position"));
		else {
			try {
				positionValue = expression.evaluate(ModelTypes.Value.forType(QuickPosition.class), model, cv);
			} catch (QonfigInterpretationException e1) {
				// If it doesn't parse as a position, try parsing as a number.
				try {
					positionValue = expression.evaluate(ModelTypes.Value.forType(int.class), model, cv)//
						.andThen(v -> v.transformReversible(QuickPosition.class, tx -> tx
							.map(d -> new QuickPosition(d, QuickPosition.PositionUnit.Pixels)).withReverse(pos -> Math.round(pos.value))));
				} catch (QonfigInterpretationException e2) {
					// If it doesn't parse as a position or a number, try parsing as a string and then parse that as a position
					positionValue = expression.evaluate(ModelTypes.Value.forType(String.class), model, cv)//
						.andThen(v -> v.transformReversible(QuickPosition.class,
							tx -> tx.map(colorParser).withReverse(QuickPosition::toString)));
				}
			}
		}
		return positionValue;
	}

	public static Function<ModelSetInstance, SettableValue<Icon>> parseIcon(ObservableExpression expression, ExpressoSession<?> session,
		ObservableModelSet model, ClassView cv) throws QonfigInterpretationException {
		if (expression != null) {
			ValueContainer<SettableValue, SettableValue<?>> iconV = expression.evaluate(ModelTypes.Value.any(), model, cv);
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
	public <QIS extends ExpressoInterpreter.ExpressoSession<QIS>, B extends ExpressoInterpreter.Builder<QIS, B>> B configureInterpreter(
		B interpreter) {
		super.configureInterpreter(interpreter);
		QonfigToolkit core = CORE.get();
		ExpressoInterpreter.Builder<?, ?> coreInterpreter = interpreter.forToolkit(core);
		coreInterpreter//
		.createWith("quick", QuickDocument.class, this::interpretQuick)//
		.createWith("head", QuickHeadSection.class, this::interpretHead)//
		.modifyWith("window", QuickDocument.class, this::modifyWindow)//
		.modifyWith("widget", QuickComponentDef.class, this::modifyWidget)//
		.createWith("line-border", QuickBorder.class, this::interpretLineBorder)//
		.createWith("titled-border", QuickBorder.class, this::interpretTitledBorder)//
		;
		return interpreter;
	}

	private QuickDocument interpretQuick(ExpressoSession<?> session) throws QonfigInterpretationException {
		QuickHeadSection head = session.interpretChildren("head", QuickHeadSection.class).getFirst();
		session.setClassView(head.getImports());
		session.setModels(head.getModels());
		QuickDocument doc = new QuickDocument.QuickDocumentImpl(session.getElement(), head, //
			session.interpretChildren("root", QuickComponentDef.class).getFirst());
		return doc;
	}

	private QuickHeadSection interpretHead(ExpressoSession<?> session) throws QonfigInterpretationException {
		ClassView cv = session.interpretChildren("imports", ClassView.class).peekFirst();
		if (cv == null)
			cv = ClassView.build().build();
		session.setClassView(cv);
		ObservableModelSet model = session.interpretChildren("models", ObservableModelSet.class).peekFirst();
		if (model == null)
			model = ObservableModelSet.build(ObservableModelSet.JAVA_NAME_CHECKER).build();
		return new QuickHeadSection(cv, model);
	}

	private QuickDocument modifyWindow(QuickDocument doc, ExpressoSession<?> session) throws QonfigInterpretationException {
		ObservableModelSet model = doc.getHead().getModels();
		ObservableExpression visibleEx = session.getAttribute("visible", ObservableExpression.class);
		if (visibleEx != null)
			doc.setVisible(
				visibleEx.evaluate(ModelTypes.Value.forType(Boolean.class), doc.getHead().getModels(), doc.getHead().getImports()));
		ObservableExpression titleEx = session.getAttribute("title", ObservableExpression.class);
		if (titleEx != null)
			doc.setTitle(titleEx.evaluate(ModelTypes.Value.forType(String.class), model, doc.getHead().getImports()));
		doc.withBounds(//
			session.interpretAttribute("x", ObservableExpression.class, true,
				ex -> ex.evaluate(ModelTypes.Value.forType(Integer.class), model, doc.getHead().getImports())), //
			session.interpretAttribute("y", ObservableExpression.class, true,
				ex -> ex.evaluate(ModelTypes.Value.forType(Integer.class), model, doc.getHead().getImports())), //
			session.interpretAttribute("width", ObservableExpression.class, true,
				ex -> ex.evaluate(ModelTypes.Value.forType(Integer.class), model, doc.getHead().getImports())), //
			session.interpretAttribute("height", ObservableExpression.class, true,
				ex -> ex.evaluate(ModelTypes.Value.forType(Integer.class), model, doc.getHead().getImports())) //
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

	private QuickComponentDef modifyWidget(QuickComponentDef widget, ExpressoSession<?> session) throws QonfigInterpretationException {
		widget.modify((editor, builder) -> editor.modifyComponent(builder::withComponent));
		ClassView cv = session.getClassView();
		ObservableModelSet model = session.getModels();
		String name = session.getAttribute("name", String.class);
		ObservableExpression tooltipX = session.getAttribute("tooltip", ObservableExpression.class);
		ObservableExpression bgColorX = session.getAttribute("bg-color", ObservableExpression.class);
		ObservableExpression visibleX = session.getAttribute("visible", ObservableExpression.class);
		QuickBorder border = session.interpretChildren("border", QuickBorder.class).peekFirst();
		ValueContainer<SettableValue, SettableValue<String>> tooltip = tooltipX == null ? null
			: tooltipX.evaluate(ModelTypes.Value.forType(String.class), model, cv);
		Function<ModelSetInstance, SettableValue<Color>> bgColor = parseColor(bgColorX, model, cv);
		ValueContainer<SettableValue, SettableValue<Boolean>> visible = visibleX == null ? null
			: visibleX.evaluate(ModelTypes.Value.forType(boolean.class), model, cv);
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
		if (bgColor != null) {
			widget.modify((comp, builder) -> {
				comp.modifyComponent(c -> {
					ObservableValue<Color> color = bgColor.apply(builder.getModels());
					color.changes().takeUntil(builder.getModels().getUntil()).act(evt -> {
						Color colorV = evt.getNewValue();
						c.setBackground(colorV == null ? Colors.transparent : colorV);
						if (c instanceof JComponent)
							((JComponent) c).setOpaque(colorV != null && colorV.getAlpha() > 0);
					});
				});
			});
		}
		if (visible != null)
			widget.modify((comp, builder) -> {
				comp.visibleWhen(visible.apply(builder.getModels()));
			});
		if (border != null)
			widget.modify((comp, builder) -> {
				comp.modifyComponent(c -> {
					if (c instanceof JComponent) {
						JComponent jc = (JComponent) c;
						border.apply(builder.getModels()).changes().takeUntil(builder.getModels().getUntil()).act(evt -> {
							if (evt.getNewValue() != jc.getBorder())
								jc.setBorder(evt.getNewValue());
							jc.repaint();
						});
					}
				});
			});
		return widget;
	}

	private QuickBorder interpretLineBorder(ExpressoSession<?> session) throws QonfigInterpretationException {
		ClassView cv = session.getClassView();
		ObservableModelSet model = session.getModels();
		Function<ModelSetInstance, SettableValue<Color>> color = parseColor(session.getAttribute("color", ObservableExpression.class),
			model, cv);
		ObservableExpression thicknessX = session.getAttribute("thickness", ObservableExpression.class);
		Function<ModelSetInstance, SettableValue<Integer>> thickness = thicknessX == null ? null
			: thicknessX.evaluate(ModelTypes.Value.forType(int.class), model, cv);
		return msi -> {
			ModifiableLineBorder border = new ModifiableLineBorder(Color.black, 1, false);
			SettableValue<Border> borderV = SettableValue.build(Border.class).withValue(border).build();
			if (color != null) {
				color.apply(msi).changes().takeUntil(msi.getUntil()).act(evt -> {
					border.setColor(evt.getNewValue());
					borderV.set(border, evt);
				});
			}
			if (thickness != null) {
				thickness.apply(msi).changes().takeUntil(msi.getUntil()).act(evt -> {
					border.setThickness(evt.getNewValue());
					borderV.set(border, evt);
				});
			}
			return borderV;
		};
	}

	private QuickBorder interpretTitledBorder(ExpressoSession<?> session) throws QonfigInterpretationException {
		ClassView cv = session.getClassView();
		ObservableModelSet model = session.getModels();
		ObservableExpression titleX = session.getAttribute("title", ObservableExpression.class);
		Function<ModelSetInstance, SettableValue<Color>> color = parseColor(session.getAttribute("color", ObservableExpression.class),
			model, cv);
		ObservableExpression thicknessX = session.getAttribute("thickness", ObservableExpression.class);
		Function<ModelSetInstance, SettableValue<String>> title = titleX.evaluate(ModelTypes.Value.forType(String.class), model, cv);
		Function<ModelSetInstance, SettableValue<Integer>> thickness = thicknessX == null ? null
			: thicknessX.evaluate(ModelTypes.Value.forType(int.class), model, cv);
		return msi -> {
			ModifiableLineBorder lineBorder = new ModifiableLineBorder(Color.black, 1, false);
			TitledBorder border = BorderFactory.createTitledBorder(lineBorder, "");
			SettableValue<Border> borderV = SettableValue.build(Border.class).withValue(border).build();
			title.apply(msi).changes().takeUntil(msi.getUntil()).act(evt -> {
				border.setTitle(evt.getNewValue());
				borderV.set(border, evt);
			});
			if (color != null) {
				color.apply(msi).changes().takeUntil(msi.getUntil()).act(evt -> {
					lineBorder.setColor(evt.getNewValue());
					border.setTitleColor(evt.getNewValue());
					borderV.set(border, evt);
				});
			}
			if (thickness != null) {
				thickness.apply(msi).changes().takeUntil(msi.getUntil()).act(evt -> {
					lineBorder.setThickness(evt.getNewValue());
					borderV.set(border, evt);
				});
			}
			return borderV;
		};
	}
}

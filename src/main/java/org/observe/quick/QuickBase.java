package org.observe.quick;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.Insets;
import java.awt.TextComponent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ClassView;
import org.observe.expresso.DefaultExpressoParser;
import org.observe.expresso.Expresso;
import org.observe.expresso.ExpressoInterpreter;
import org.observe.expresso.ExpressoInterpreter.ExpressoSession;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableExpression.MethodFinder;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.AbstractValueContainer;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.RuntimeValuePlaceholder;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ObservableModelSet.ValueCreator;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.CellDecorator;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.ModelCell;
import org.observe.util.swing.ObservableCellEditor;
import org.observe.util.swing.ObservableCellEditor.DefaultObservableCellEditor;
import org.observe.util.swing.ObservableCellEditor.EditorSubscription;
import org.observe.util.swing.ObservableCellRenderer;
import org.observe.util.swing.ObservableFileButton;
import org.observe.util.swing.ObservableSwingUtils;
import org.observe.util.swing.ObservableTextField;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.TabEditor;
import org.qommons.collect.BetterList;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.QonfigToolkitAccess;
import org.qommons.io.BetterFile;
import org.qommons.io.FileUtils;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;

import com.google.common.reflect.TypeToken;

public class QuickBase extends QuickCore {
	public static final QonfigToolkitAccess BASE = new QonfigToolkitAccess(QuickBase.class, "quick-base.qtd", QuickCore.CORE);

	public interface Column<R, C> {
		CategoryRenderStrategy<R, C> createColumn(ModelSetInstance models);
	}

	public interface ColumnEditing<R, C> {
		public void modifyColumn(CategoryRenderStrategy<R, C>.CategoryMutationStrategy mutation, ModelSetInstance models);
	}

	public static class ValueAction {
		public final ObservableModelSet.Wrapped model;
		public final RuntimeValuePlaceholder<ObservableCollection, ObservableCollection<Object>> valueListPlaceholder;
		public final ValueContainer<SettableValue, SettableValue<String>> name;
		public final Function<ModelSetInstance, SettableValue<Icon>> icon;
		public final ValueContainer<ObservableAction, ObservableAction<?>> action;
		public final ValueContainer<SettableValue, SettableValue<String>> enabled;
		public final boolean allowForEmpty;
		public final boolean allowForMultiple;
		public final ValueContainer<SettableValue, SettableValue<String>> tooltip;

		public ValueAction(ObservableModelSet.Wrapped model,
			RuntimeValuePlaceholder<ObservableCollection, ObservableCollection<Object>> valueListPlaceholder,
			ValueContainer<SettableValue, SettableValue<String>> name, Function<ModelSetInstance, SettableValue<Icon>> icon,
			ValueContainer<ObservableAction, ObservableAction<?>> action, ValueContainer<SettableValue, SettableValue<String>> enabled,
			boolean allowForEmpty, boolean allowForMultiple, ValueContainer<SettableValue, SettableValue<String>> tooltip) {
			this.model = model;
			this.valueListPlaceholder = valueListPlaceholder;
			this.name = name;
			this.icon = icon;
			this.action = action;
			this.enabled = enabled;
			this.allowForEmpty = allowForEmpty;
			this.allowForMultiple = allowForMultiple;
			this.tooltip = tooltip;
		}
	}

	private static final Format<Object> LABEL_FORMAT = new Format<Object>() {
		@Override
		public void append(StringBuilder text, Object value) {
			if (value != null)
				text.append(value);
		}

		@Override
		public Object parse(CharSequence text) throws ParseException {
			throw new ParseException("This format cannot parse", 0);
		}
	};

	@Override
	public <QIS extends ExpressoInterpreter.ExpressoSession<QIS>, B extends ExpressoInterpreter.Builder<QIS, B>> B configureInterpreter(
		B interpreter) {
		super.configureInterpreter(interpreter);
		QonfigToolkit base = BASE.get();
		ExpressoInterpreter.Builder<?, ?> tkInt = interpreter.forToolkit(base);
		tkInt.createWith("box", QuickBox.class, this::interpretBox)//
		.modifyWith("border", QuickBox.class, this::modifyBoxBorder)//
		.modifyWith("simple", QuickBox.class, this::modifyBoxSimple)//
		.modifyWith("inline", QuickBox.class, this::modifyBoxInline)//
		.createWith("multi-value-action", ValueAction.class, this::interpretMultiValueAction)//
		.createWith("label", QuickComponentDef.class, session -> evaluateLabel(session))//
		.createWith("text-field", QuickComponentDef.class, this::interpretTextField)//
		.createWith("button", QuickComponentDef.class, this::interpretButton)//
		.createWith("table", QuickComponentDef.class, this::interpretTable)//
		.createWith("column", Column.class, this::interpretColumn)//
		.createWith("modify-row-value", ColumnEditing.class, this::interpretRowModify)//
		// .createWith("replace-row-value", ColumnEditing.class, this::interpretRowReplace) TODO
		.createWith("columns", ValueCreator.class, this::interpretColumns)//
		.createWith("split", QuickComponentDef.class, this::interpretSplit)//
		.createWith("field-panel", QuickComponentDef.class, this::interpretFieldPanel)//
		.modifyWith("field", QuickComponentDef.class, this::modifyField)//
		.createWith("spacer", QuickComponentDef.class, this::interpretSpacer)//
		.createWith("tree", QuickComponentDef.class, this::interpretTree)//
		.createWith("text-area", QuickComponentDef.class, this::interpretTextArea)//
		.createWith("check-box", QuickComponentDef.class, this::interpretCheckBox)//
		.createWith("radio-buttons", QuickComponentDef.class, this::interpretRadioButtons)//
		.createWith("tab-set", MultiTabSet.class, MultiTabSet::parse)//
		.createWith("tabs", QuickComponentDef.class, this::interpretTabs)//
		.createWith("file-button", QuickComponentDef.class, this::interpretFileButton)//
		;
		return interpreter;
	}

	private QuickBox interpretBox(ExpressoSession<?> session) throws QonfigInterpretationException {
		ObservableModelSet.Wrapped localModels = parseLocalModel(session);
		List<QuickComponentDef> children = session.interpretChildren("content", QuickComponentDef.class);
		return new QuickBox(session.getElement(), localModels, children, session.getAttributeText("layout"));
	}

	private QuickBox modifyBoxBorder(QuickBox box, ExpressoSession<?> session) throws QonfigInterpretationException {
		box.setLayout(BorderLayout::new);
		for (QuickComponentDef child : box.getChildren()) {
			String layoutConstraint;
			String region = child.getElement().getAttributeText(BASE.get().getAttribute("border-layout-child", "region"));
			switch (region) {
			case "center":
				layoutConstraint = BorderLayout.CENTER;
				break;
			case "north":
				layoutConstraint = BorderLayout.NORTH;
				break;
			case "south":
				layoutConstraint = BorderLayout.SOUTH;
				break;
			case "east":
				layoutConstraint = BorderLayout.EAST;
				break;
			case "west":
				layoutConstraint = BorderLayout.WEST;
				break;
			default:
				System.err.println("ERROR: Unrecognized border-layout region: " + region);
				layoutConstraint = null;
				break;
			}
			child.modify((ch, m) -> ch.withLayoutConstraints(layoutConstraint));
		}
		return box;
	}

	private QuickBox modifyBoxSimple(QuickBox box, ExpressoSession<?> session) throws QonfigInterpretationException {
		box.setLayout(SimpleLayout::new);
		int c = 0;
		for (ExpressoSession<?> child : session.forChildren()) {
			QuickComponentDef childComp = box.getChildren().get(c++);
			ModelInstanceType<SettableValue, SettableValue<QuickPosition>> posType = ModelTypes.Value.forType(QuickPosition.class);
			ModelInstanceType<SettableValue, SettableValue<QuickSize>> sizeType = ModelTypes.Value.forType(QuickSize.class);
			ValueContainer<SettableValue, SettableValue<QuickPosition>> leftC = child.getAttribute("left", posType, null);
			ValueContainer<SettableValue, SettableValue<QuickPosition>> rightC = child.getAttribute("right", posType, null);
			ValueContainer<SettableValue, SettableValue<QuickPosition>> topC = child.getAttribute("top", posType, null);
			ValueContainer<SettableValue, SettableValue<QuickPosition>> bottomC = child.getAttribute("bottom", posType, null);
			ValueContainer<SettableValue, SettableValue<QuickSize>> minWidthC = child.getAttribute("min-width", sizeType, null);
			ValueContainer<SettableValue, SettableValue<QuickSize>> prefWidthC = child.getAttribute("pref-width", sizeType, null);
			ValueContainer<SettableValue, SettableValue<QuickSize>> maxWidthC = child.getAttribute("max-width", sizeType, null);
			ValueContainer<SettableValue, SettableValue<QuickSize>> minHeightC = child.getAttribute("min-height", sizeType, null);
			ValueContainer<SettableValue, SettableValue<QuickSize>> prefHeightC = child.getAttribute("pref-height", sizeType, null);
			ValueContainer<SettableValue, SettableValue<QuickSize>> maxHeightC = child.getAttribute("max-height", sizeType, null);
			childComp.modify((ch, builder) -> {
				ObservableValue<QuickPosition> left = leftC == null ? null : leftC.apply(builder.getModels());
				ObservableValue<QuickPosition> right = rightC == null ? null : rightC.apply(builder.getModels());
				ObservableValue<QuickPosition> top = topC == null ? null : topC.apply(builder.getModels());
				ObservableValue<QuickPosition> bottom = bottomC == null ? null : bottomC.apply(builder.getModels());
				ObservableValue<QuickSize> minWidth = minWidthC == null ? null : minWidthC.apply(builder.getModels());
				ObservableValue<QuickSize> prefWidth = prefWidthC == null ? null : prefWidthC.apply(builder.getModels());
				ObservableValue<QuickSize> maxWidth = maxWidthC == null ? null : maxWidthC.apply(builder.getModels());
				ObservableValue<QuickSize> minHeight = minHeightC == null ? null : minHeightC.apply(builder.getModels());
				ObservableValue<QuickSize> prefHeight = prefHeightC == null ? null : prefHeightC.apply(builder.getModels());
				ObservableValue<QuickSize> maxHeight = maxHeightC == null ? null : maxHeightC.apply(builder.getModels());
				ch.withLayoutConstraints(new SimpleLayout.SimpleConstraints(left, right, top, bottom, //
					minWidth, prefWidth, maxWidth, minHeight, prefHeight, maxHeight));
				Observable.or(//
					left == null ? null : left.noInitChanges(), //
						right == null ? null : right.noInitChanges(), //
							top == null ? null : top.noInitChanges(), //
								bottom == null ? null : bottom.noInitChanges(), //
									minWidth == null ? null : minWidth.noInitChanges(), //
										prefWidth == null ? null : prefWidth.noInitChanges(), //
											maxWidth == null ? null : maxWidth.noInitChanges(), //
												minHeight == null ? null : minHeight.noInitChanges(), //
													prefHeight == null ? null : prefHeight.noInitChanges(), //
														maxHeight == null ? null : maxHeight.noInitChanges()//
					).takeUntil(builder.getModels().getUntil()).act(__ -> {
						Container parent = ch.getComponent().getParent();
						if (parent != null && parent.getLayout() instanceof SimpleLayout) {
							((SimpleLayout) parent.getLayout()).layoutChild(parent, ch.getComponent());
						}
					});
			});
		}
		return box;
	}

	private QuickBox modifyBoxInline(QuickBox box, ExpressoSession<?> session) throws QonfigInterpretationException {
		JustifiedBoxLayout layout = new JustifiedBoxLayout(session.getAttributeText("orientation").equals("vertical"));
		String mainAlign = session.getAttributeText("main-align");
		switch (mainAlign) {
		case "leading":
			layout.mainLeading();
			break;
		case "trailing":
			layout.mainTrailing();
			break;
		case "center":
			layout.mainCenter();
			break;
		case "justify":
			layout.mainJustified();
			break;
		default:
			System.err.println("Unrecognized main-align: " + mainAlign);
		}
		String crossAlign = session.getAttributeText("cross-align");
		switch (crossAlign) {
		case "leading":
			layout.crossLeading();
			break;
		case "trailing":
			layout.crossTrailing();
			break;
		case "center":
			layout.crossCenter();
			break;
		case "justify":
			layout.crossJustified();
			break;
		default:
			System.err.println("Unrecognized cross-align: " + crossAlign);
		}
		return box.setLayout(() -> layout);
	}

	private ValueAction interpretMultiValueAction(ExpressoSession<?> session) throws QonfigInterpretationException {
		ClassView cv = session.getClassView();
		ObservableModelSet model = session.getModels();
		TypeToken<Object> valueType = (TypeToken<Object>) session.get("model-type");
		String valueListName = session.getAttributeText("value-list-name");
		ObservableModelSet.WrappedBuilder actionModel = ObservableModelSet.wrap(model);
		RuntimeValuePlaceholder<ObservableCollection, ObservableCollection<Object>> valueListNamePH = actionModel
			.withRuntimeValue(valueListName, ModelTypes.Collection.forType(valueType));
		ObservableModelSet.Wrapped builtActionModel = actionModel.build();

		ObservableExpression nameX = session.getAttribute("name", ObservableExpression.class);
		ObservableExpression iconX = session.getAttribute("icon", ObservableExpression.class);
		ObservableExpression actionX = session.getAttribute("action", ObservableExpression.class);
		ObservableExpression enabledX = session.getAttribute("enabled", ObservableExpression.class);
		ObservableExpression tooltipX = session.getAttribute("enabled", ObservableExpression.class);

		ValueContainer<SettableValue, SettableValue<String>> nameV = nameX == null ? null
			: nameX.evaluate(ModelTypes.Value.forType(String.class), model, cv);
		Function<ModelSetInstance, SettableValue<Icon>> iconV = parseIcon(iconX, session, model, cv);
		ValueContainer<ObservableAction, ObservableAction<?>> actionV = actionX.evaluate(ModelTypes.Action.any(), builtActionModel, cv);
		ValueContainer<SettableValue, SettableValue<String>> enabledV = enabledX == null ? null
			: enabledX.evaluate(ModelTypes.Value.forType(String.class), model, cv);
		ValueContainer<SettableValue, SettableValue<String>> tooltipV = tooltipX == null ? null
			: tooltipX.evaluate(ModelTypes.Value.forType(String.class), model, cv);

		return new ValueAction(builtActionModel, valueListNamePH, nameV, iconV, actionV, enabledV, //
			session.getAttribute("allow-for-empty", boolean.class), //
			session.getAttribute("allow-for-multiple", boolean.class), //
			tooltipV);
	}

	private QuickComponentDef interpretTextField(ExpressoSession<?> session) throws QonfigInterpretationException {
		ObservableModelSet.Wrapped localModels = parseLocalModel(session);
		ClassView cv = session.getClassView();
		ObservableModelSet model = session.getModels();
		ValueContainer<SettableValue, ?> value = session.getAttribute("value", ModelTypes.Value.any(), null);
		ObservableExpression formatX = session.getAttribute("format", ObservableExpression.class);
		Function<ModelSetInstance, SettableValue<Format<Object>>> format;
		boolean commitOnType = session.getAttribute("commit-on-type", boolean.class);
		String columnsStr = session.getAttributeText("columns");
		int columns = columnsStr == null ? -1 : Integer.parseInt(columnsStr);
		if (formatX != null) {
			format = formatX.evaluate(
				ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).parameterized(value.getType().getType(0))), model, cv);
		} else {
			Class<?> type = TypeTokens.get().unwrap(TypeTokens.getRawType(value.getType().getType(0)));
			Format<?> f;
			if (type == String.class)
				f = SpinnerFormat.NUMERICAL_TEXT;
			else if (type == int.class)
				f = SpinnerFormat.INT;
			else if (type == long.class)
				f = SpinnerFormat.LONG;
			else if (type == double.class)
				f = Format.doubleFormat(4).build();
			else if (type == float.class)
				f = Format.doubleFormat(4).buildFloat();
			else if (type == Instant.class)
				f = SpinnerFormat.flexDate(Instant::now, "EEE MMM dd, yyyy", null);
			else if (type == Duration.class)
				f = SpinnerFormat.flexDuration(false);
			else
				throw new QonfigInterpretationException(
					"No default format available for type " + value.getType().getType(0) + " -- format must be specified");
			format = ObservableModelSet.literalContainer(ModelTypes.Value.forType((Class<Format<Object>>) (Class<?>) Format.class),
				(Format<Object>) f, type.getSimpleName());
		}
		return new AbstractQuickValueEditor(session.getElement(), localModels) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				SettableValue<Object> realValue = value.apply(builder.getModels());
				ObservableValue<String> fieldName;
				if (getFieldName() != null)
					fieldName = getFieldName().apply(builder.getModels());
				else
					fieldName = null;
				container.addTextField(fieldName == null ? null : fieldName.get(), //
					realValue, format.apply(builder.getModels()).get(), field -> {
						field.modifyEditor(tf -> tf.setCommitOnType(commitOnType));
						modify(field, builder);
						if (columns > 0)
							field.getEditor().withColumns(columns);
					});
				return builder.build();
			}

			@Override
			public int getDefaultEditClicks() {
				return 2;
			}

			@Override
			public void startEditing(QuickComponent component) {
				ObservableTextField<Object> field = (ObservableTextField<Object>) component.getComponent();
				if (field.isSelectAllOnFocus())
					field.selectAll();
			}

			@Override
			public boolean flush(QuickComponent component) {
				ObservableTextField<Object> field = (ObservableTextField<Object>) component.getComponent();
				if (!field.isDirty()) { // No need to check the error or anything
				} else if (field.getEditError() != null) {
					field.redisplayErrorTooltip();
					return false;
				} else {
					field.flushEdits(null);
				}
				return true;
			}

			@Override
			public void stopEditing(QuickComponent component) {
			}
		};
	}

	private QuickComponentDef interpretButton(ExpressoSession<?> session) throws QonfigInterpretationException {
		ObservableModelSet.Wrapped localModels = parseLocalModel(session);
		ClassView cv = session.getClassView();
		ObservableModelSet model = session.getModels();
		Function<ModelSetInstance, SettableValue<String>> buttonText;
		ObservableExpression valueX = session.getValue(ObservableExpression.class, null);
		if (valueX == null) {
			String txt = session.getValueText();
			if (txt == null)
				throw new IllegalArgumentException("Either 'text' attribute or element value must be specified");
			buttonText = __ -> ObservableModelSet.literal(txt, txt);
		} else
			buttonText = valueX.evaluate(ModelTypes.Value.forType(String.class), model, cv);
		Function<ModelSetInstance, ? extends ObservableAction> action = model.get(session.getAttributeText("action"), ModelTypes.Action);
		return new AbstractQuickComponentDef(session.getElement(), localModels) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				ObservableValue<String> text = buttonText.apply(builder.getModels());
				container.addButton(text.get(), //
					action.apply(builder.getModels()), //
					btn -> {
						modify(btn, builder);
						btn.withText(text);
					});
				return builder.build();
			}
		};
	}

	private Column interpretColumn(ExpressoSession<?> session) throws QonfigInterpretationException {
		ObservableModelSet model = session.getModels();
		ClassView cv = session.getClassView();
		TypeToken<Object> modelType = (TypeToken<Object>) session.get("model-type");
		String name = session.getAttributeText("name");
		ObservableExpression valueX = session.getAttribute("value", ObservableExpression.class);
		String rowValueName = (String) session.get("value-name");
		String cellValueName = (String) session.get("render-value-name");
		if (modelType == null || rowValueName == null || cellValueName == null)
			throw new IllegalStateException(
				"column intepretation expects 'model-type', 'value-name', and 'render-value-name' session values");
		ObservableModelSet.WrappedBuilder wb = ObservableModelSet.wrap(model);
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue, SettableValue<Object>> valueRowVP = wb.withRuntimeValue(rowValueName,
			ModelTypes.Value.forType(modelType));
		ObservableModelSet.Wrapped valueModel = wb.build();
		TypeToken<Object> columnType;
		Function<ModelSetInstance, Function<Object, Object>> valueFn;
		if (valueX instanceof DefaultExpressoParser.LambdaExpression || valueX instanceof DefaultExpressoParser.MethodReferenceExpression) {
			MethodFinder<Object, Object, Object, Object> finder = valueX.findMethod(TypeTokens.get().OBJECT, model, cv)//
				.withOption(BetterList.of(modelType), new ObservableExpression.ArgMaker<Object, Object, Object>() {
					@Override
					public void makeArgs(Object t, Object u, Object v, Object[] args, ModelSetInstance models) {
						args[0] = t;
					}
				});
			valueFn = finder.find1();
			columnType = (TypeToken<Object>) finder.getResultType();
		} else if (valueX != null) {
			ValueContainer<SettableValue, SettableValue<Object>> colValue = valueX.evaluate(
				(ModelType.ModelInstanceType<SettableValue, SettableValue<Object>>) (ModelType.ModelInstanceType<?, ?>) ModelTypes.Value
				.any(),
				valueModel, cv);
			// MethodFinder<Object, Object, Object, Object> finder = valueX.findMethod(TypeTokens.get().OBJECT, model, cv)//
			// .withOption(BetterList.of(modelType), new ObservableExpression.ArgMaker<Object, Object, Object>() {
			// @Override
			// public void makeArgs(Object t, Object u, Object v, Object[] args, ModelSetInstance models) {
			// args[0] = t;
			// }
			// });
			columnType = (TypeToken<Object>) colValue.getType().getType(0);
			valueFn = msi -> {
				SettableValue<Object> rowValue = SettableValue.build(modelType).withDescription(rowValueName).build();
				ModelSetInstance valueModelInst = valueModel.wrap(msi)//
					.with(valueRowVP, rowValue)//
					.build();
				SettableValue<Object> cvi = colValue.get(valueModelInst);
				return row -> {
					rowValue.set(row, null);
					return cvi.get();
				};
			};
		} else {
			valueFn = msi -> v -> v;
			columnType = modelType;
		}
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue, SettableValue<Object>> cellRowVP = wb.withRuntimeValue(cellValueName,
			ModelTypes.Value.forType(columnType));
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue, SettableValue<Object>> subjectVP = wb.withRuntimeValue("subject",
			ModelTypes.Value.forType(columnType));
		ObservableModelSet.Wrapped cellModel = wb.build();

		QuickComponentDef renderer;
		session.setModels(cellModel);
		renderer = session.interpretChildren("renderer", QuickComponentDef.class).peekFirst();
		ColumnEditing<Object, Object> editing;
		ExpressoSession<?> columnEdit = session.forChildren("edit").peekFirst();
		if (columnEdit != null) {
			ExpressoSession<?> editorSession = columnEdit.forChildren("editor").getFirst();
			QuickComponentDef editor = editorSession.interpret(QuickComponentDef.class);
			if (!(editor instanceof QuickValueEditorDef))
				throw new IllegalArgumentException(
					"Use of '" + editorSession.getElement().getType().getName() + "' as a column editor is not implemented");
			String editValueName = columnEdit.getAttributeText("edit-value-name");
			ObservableModelSet.RuntimeValuePlaceholder<SettableValue, SettableValue<Object>> editorValueVP = wb.withRuntimeValue(editValueName,
				ModelTypes.Value.forType(columnType));
			ObservableModelSet.Wrapped editModel = wb.build();
			columnEdit.setModels(editModel);
			editorSession.setModels(editModel);
			ColumnEditing<Object, Object> editType = columnEdit.as(columnEdit.getAttribute("type", QonfigAddOn.class))
				.interpret(ColumnEditing.class);
			editing = (column, models) -> {
				// Hacky way of synthesizing the component
				SettableValue<Object> rowValue = SettableValue.build(modelType).withDescription(rowValueName).build();
				Function<Object, String>[] filter = new Function[1];
				boolean[] currentlyEditing = new boolean[1];
				SettableValue<Object> editorValue = DefaultObservableCellEditor.createEditorValue(columnType, filter,
					builder -> builder.withDescription(editValueName));
				SettableValue<Object> cellValue = SettableValue.build(columnType).withDescription(cellValueName).build();
				ModelSetInstance cellModelInst = editModel.wrap(models)//
					.with(valueRowVP, rowValue)//
					.with(cellRowVP, cellValue)//
					.with(subjectVP, editorValue)//
					.with(editorValueVP, editorValue)//
					.build();
				editType.modifyColumn(column, cellModelInst);
				QuickComponent.Builder editorBuilder = QuickComponent.build(editor, null, cellModelInst);
				QuickComponent editorComp = editor
					.install(PanelPopulation.populateHPanel(null, new JustifiedBoxLayout(false), cellModelInst.getUntil()), editorBuilder);
				Component c = editorComp.getComponent();
				Insets defMargin = c instanceof JTextComponent ? ((JTextComponent) c).getMargin() : null;
				String clicksStr = columnEdit.getAttribute("clicks", String.class);
				int clicks = clicksStr != null ? Integer.parseInt(clicksStr) : ((QuickValueEditorDef) editor).getDefaultEditClicks();
				ObservableCellEditor<Object, Object> oce = new DefaultObservableCellEditor<>(c, editorValue, //
					new ObservableCellEditor.EditorInstallation<Object>() {
					@Override
					public EditorSubscription install(ObservableCellEditor<?, Object> cellEditor, Component component,
						Function<Object, String> valueFilter, String tooltip, Function<? super Object, String> valueToolTip) {
						ModelCell<Object, Object> cell = ((DefaultObservableCellEditor<Object, Object>) cellEditor).getEditingCell();
						rowValue.set(cell.getModelValue(), null);
						cellValue.set(cell.getCellValue(), null);
						if (defMargin != null && component instanceof JTable) {
							Insets margin = ((JTextComponent) c).getMargin();
							if (margin.top != 0 || margin.bottom != 0) {
								margin.top = 0;
								margin.bottom = 0;
								((JTextComponent) c).setMargin(margin);
							}
						} else if (defMargin != null)
							((JTextComponent) c).setMargin(defMargin);
						filter[0] = valueFilter;
						if (c instanceof JComponent)
							((JComponent) c).setToolTipText(tooltip);
						currentlyEditing[0] = true;
						return commit -> {
							if (!((QuickValueEditorDef) editor).flush(editorComp))
								return false;
							filter[0] = null;
							currentlyEditing[0] = false;
							return true;
						};
					}
				}, ObservableCellEditor.editWithClicks(clicks));
				column.withEditor(oce);
				column.clicks(clicks);
				editorValue.noInitChanges().act(evt -> {
					if (currentlyEditing[0]) {
						((QuickValueEditorDef) editor).stopEditing(editorComp);
						oce.stopCellEditing();
					}
				});
			};
		} else
			editing = null;

		UIDefaults ui = UIManager.getDefaults();
		Color selectionBackground = ui.getColor("List.selectionBackground");
		Color selectionForeground = ui.getColor("List.selectionForeground");
		return extModels -> {
			CategoryRenderStrategy<Object, Object> column = new CategoryRenderStrategy<>(name, columnType, valueFn.apply(extModels));
			if (renderer != null) {
				// Hacky way of synthesizing the component
				SettableValue<Object> rowValue = SettableValue.build(modelType).withDescription(rowValueName).build();
				SettableValue<Object> cellValue = SettableValue.build(columnType).withDescription(cellValueName).build();
				ModelSetInstance cellModelInst = cellModel.wrap(extModels)//
					.with(valueRowVP, rowValue)//
					.with(cellRowVP, cellValue)//
					.with(subjectVP, cellValue)//
					.build();
				QuickComponent.Builder renderBuilder = QuickComponent.build(renderer, null, cellModelInst);
				QuickComponent renderComp = renderer
					.install(PanelPopulation.populateHPanel(null, new JustifiedBoxLayout(false), cellModelInst.getUntil()), renderBuilder);
				Component c = renderComp.getComponent();
				BiFunction<Supplier<?>, Object, String> textRender;
				boolean text = c instanceof TextComponent;
				if (text) {
					textRender = (row, col) -> {
						rowValue.set(row.get(), null);
						cellValue.set(col, null);
						return ((TextComponent) c).getText();
					};
				} else
					textRender = (row, col) -> String.valueOf(col);
					column.withRenderer(new ObservableCellRenderer<Object, Object>() {
						private List<CellDecorator<Object, Object>> theDecorators;

						@Override
						public String renderAsText(Supplier<? extends Object> modelValue, Object columnValue) {
							return textRender.apply(modelValue, columnValue);
						}

						@Override
						public Component getCellRendererComponent(Component parent, ModelCell<? extends Object, ? extends Object> cell,
							CellRenderContext ctx) {
							rowValue.set(cell.getModelValue(), null);
							cellValue.set(cell.getCellValue(), null);
							ObservableCellRenderer.tryEmphasize(c, ctx);
							if (cell.isSelected()) {
								if (c instanceof JComponent)
									((JComponent) c).setOpaque(true);
								c.setBackground(selectionBackground);
								c.setForeground(selectionForeground);
							} else {
								if (c instanceof JComponent)
									((JComponent) c).setOpaque(false);
								c.setBackground(Color.white);
								c.setForeground(Color.black);
							}
							return c;
						}

						@Override
						public ObservableCellRenderer<Object, Object> decorate(CellDecorator<Object, Object> decorator) {
							if (theDecorators == null)
								theDecorators = new ArrayList<>(3);
							theDecorators.add(decorator);
							return this;
						}
					});
			}
			if (editing != null)
				column.withMutation(mut -> editing.modifyColumn(mut, extModels));
			return column;
		};
	}

	private ColumnEditing interpretRowModify(ExpressoSession<?> session) throws QonfigInterpretationException {
		System.err.println("WARNING: modify-row-value is not fully implemented!!"); // TODO
		ObservableModelSet model = session.getModels();
		ClassView cv = session.getClassView();
		TypeToken<Object> modelType = (TypeToken<Object>) session.get("model-type");
		String editValueName = (String) session.get("edit-value-name");
		ObservableExpression commitX = session.getAttribute("commit", ObservableExpression.class);
		ObservableExpression editableIfX = session.getAttribute("editable-if", ObservableExpression.class);
		ObservableExpression acceptX = session.getAttribute("accept", ObservableExpression.class);
		ValueContainer<ObservableAction, ObservableAction<?>> commit = commitX.evaluate(ModelTypes.Action.any(), model, cv);
		boolean rowUpdate = session.getAttribute("row-update", boolean.class);
		return (column, models) -> { // TODO Not done here
			ObservableAction<?> commitAction = commit.apply(models);
			column.mutateAttribute((row, cell) -> commitAction.act(null));
		};
	}

	private ValueCreator<ObservableCollection, ObservableCollection<CategoryRenderStrategy<Object, ?>>> interpretColumns(
		ExpressoSession<?> session) throws QonfigInterpretationException {
		TypeToken<Object> rowType = (TypeToken<Object>) Expresso.parseType(session.getAttributeText("type"));
		session.put("model-type", rowType);
		TypeToken<CategoryRenderStrategy<Object, ?>> columnType = TypeTokens.get().keyFor(CategoryRenderStrategy.class)
			.<CategoryRenderStrategy<Object, ?>> parameterized(rowType, TypeTokens.get().WILDCARD);
		String rowValueName = session.getAttributeText("value-name");
		session.put("value-name", rowValueName);
		String colValueName = session.getAttributeText("render-value-name");
		session.put("render-value-name", colValueName);
		List<Column<Object, ?>> columns = session.interpretChildren("column", Column.class);
		return ()->new AbstractValueContainer<ObservableCollection, ObservableCollection<CategoryRenderStrategy<Object, ?>>>(
			ModelTypes.Collection.forType(columnType)) {
			@Override
			public ObservableCollection<CategoryRenderStrategy<Object, ?>> get(ModelSetInstance models) {
				List<CategoryRenderStrategy<Object, ?>> columnInstances = new ArrayList<>(columns.size());
				for (Column<Object, ?> column : columns)
					columnInstances.add(column.createColumn(models));
				return ObservableCollection.of(columnType, columnInstances);
			}
		};
	}

	private QuickComponentDef interpretSplit(ExpressoSession<?> session) throws QonfigInterpretationException {
		ObservableModelSet.Wrapped localModels = parseLocalModel(session);
		ClassView cv = session.getClassView();
		ObservableModelSet model = session.getModels();
		if (session.getChildren("content").size() != 2)
			throw new UnsupportedOperationException("Currently only 2 (and exactly 2) contents are supported for split");
		List<QuickComponentDef> children = session.interpretChildren("content", QuickComponentDef.class);
		boolean vertical = session.getAttributeText("orientation").equals("vertical");
		Function<ModelSetInstance, SettableValue<QuickPosition>> splitPos = parsePosition(
			session.getAttribute("split-position", ObservableExpression.class), model, cv);
		return new AbstractQuickComponentDef(session.getElement(), localModels) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				container.addSplit(vertical, split -> {
					modify(split.fill().fillV(), builder);
					if (splitPos != null) {
						// Because of the different units in the position we support here and how they're evaluated
						// relative to the overall component width,
						// this is more complicated than it makes sense to support in PanelPopulation,
						// so we gotta do it here ourselves
						split.modifyComponent(c -> {
							boolean[] divCallbackLock = new boolean[1];
							JSplitPane sp = (JSplitPane) c;
							SettableValue<QuickPosition> pos = splitPos.apply(builder.getModels());
							sp.addComponentListener(new ComponentAdapter() {
								boolean init = true;

								void init() {
									init = false;
									pos.changes().takeUntil(builder.getModels().getUntil()).act(evt -> {
										if (divCallbackLock[0])
											return;
										ObservableSwingUtils.onEQ(() -> {
											divCallbackLock[0] = true;
											try {
												sp.setDividerLocation(evt.getNewValue().evaluate(sp.getWidth()));
											} finally {
												divCallbackLock[0] = false;
											}
										});
									});
									sp.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
										if (divCallbackLock[0])
											return;
										divCallbackLock[0] = true;
										try {
											int loc = sp.getDividerLocation();
											QuickPosition p = pos.get();
											switch (p.type) {
											case Pixels:
												pos.set(new QuickPosition(loc, p.type), evt);
												break;
											case Percent:
												pos.set(new QuickPosition(loc * 100.0f / sp.getWidth(), p.type), evt);
												break;
											case Lexips:
												pos.set(new QuickPosition(sp.getWidth() - loc, p.type), evt);
												break;
											}
										} finally {
											divCallbackLock[0] = false;
										}
									});

									Component left = sp.getLeftComponent();
									Component right = sp.getRightComponent();
									left.addComponentListener(new ComponentAdapter() {
										@Override
										public void componentShown(ComponentEvent e) {
											if (right.isVisible())
												return;
											EventQueue.invokeLater(() -> {
												divCallbackLock[0] = true;
												try {
													int target = pos.get().evaluate(vertical ? sp.getHeight() : sp.getWidth());
													if (Math.abs(target - sp.getDividerLocation()) > 1)
														sp.setDividerLocation(target);
												} finally {
													divCallbackLock[0] = false;
												}
											});
										}
									});
									right.addComponentListener(new ComponentAdapter() {
										@Override
										public void componentShown(ComponentEvent e) {
											if (left.isVisible())
												return;
											EventQueue.invokeLater(() -> {
												divCallbackLock[0] = true;
												try {
													int target = pos.get().evaluate(vertical ? sp.getHeight() : sp.getWidth());
													if (Math.abs(target - sp.getDividerLocation()) > 1)
														sp.setDividerLocation(target);
												} finally {
													divCallbackLock[0] = false;
												}
											});
										}
									});
								}

								@Override
								public void componentShown(ComponentEvent e) {
									if (init && sp.getWidth() != 0) {
										init();
									}
									EventQueue.invokeLater(() -> {
										divCallbackLock[0] = true;
										try {
											int target = pos.get().evaluate(vertical ? sp.getHeight() : sp.getWidth());
											if (Math.abs(target - sp.getDividerLocation()) > 1)
												sp.setDividerLocation(target);
										} finally {
											divCallbackLock[0] = false;
										}
									});
								}

								@Override
								public void componentResized(ComponentEvent e) {
									if (init && sp.getWidth() != 0) {
										init();
									}
									if (divCallbackLock[0])
										return;
									divCallbackLock[0] = true;
									try {
										int target = pos.get().evaluate(vertical ? sp.getHeight() : sp.getWidth());
										if (Math.abs(target - sp.getDividerLocation()) > 1)
											sp.setDividerLocation(target);
									} finally {
										divCallbackLock[0] = false;
									}
								}
							});
						});
					}
					split.firstH(new JustifiedBoxLayout(true).mainJustified().crossJustified(), first -> {
						first.withName("split-left");
						QuickComponent.Builder child = QuickComponent.build(children.get(0), builder, builder.getModels());
						children.get(0).install(first, child);
						builder.withChild(child.build());
					});
					split.lastH(new JustifiedBoxLayout(true).mainJustified().crossJustified(), last -> {
						QuickComponent.Builder child = QuickComponent.build(children.get(1), builder, builder.getModels());
						children.get(1).install(last, child);
						builder.withChild(child.build());
					});
				});
				return builder.build();
			}
		};
	}

	private QuickComponentDef interpretFieldPanel(ExpressoSession<?> session) throws QonfigInterpretationException {
		ObservableModelSet.Wrapped localModels = parseLocalModel(session);
		List<QuickComponentDef> children = session.interpretChildren("content", QuickComponentDef.class);
		System.err.println("WARNING: field-panel is not fully implemented!!"); // TODO
		return new AbstractQuickComponentDef(session.getElement(), localModels) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				container.addVPanel(panel -> {
					modify(panel, builder);
					for (QuickComponentDef child : children) {
						QuickComponent.Builder childBuilder = QuickComponent.build(child, builder, builder.getModels());
						child.install(panel, childBuilder);
						builder.withChild(childBuilder.build());
					}
				});
				return builder.build();
			}
		};
	}

	private QuickComponentDef modifyField(QuickComponentDef field, ExpressoSession<?> session) throws QonfigInterpretationException {
		ObservableExpression fieldName = session.getAttribute("field-name", ObservableExpression.class);
		if (fieldName != null) {
			ObservableModelSet model = session.getModels();
			ClassView cv = session.getClassView();
			field.setFieldName(fieldName.evaluate(ModelTypes.Value.forType(String.class), model, cv));
		}
		if (Boolean.TRUE.equals(session.getAttribute("fill", Boolean.class)))
			field.modify((f, m) -> f.fill());
		return field;
	}

	private QuickComponentDef interpretSpacer(ExpressoSession<?> session) throws QonfigInterpretationException {
		ObservableModelSet.Wrapped localModels = parseLocalModel(session);
		int length = Integer.parseInt(session.getAttributeText("length"));
		return new AbstractQuickComponentDef(session.getElement(), localModels) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				container.spacer(length, null);
				return builder.build();
			}
		};
	}

	private QuickComponentDef interpretTextArea(ExpressoSession<?> session) throws QonfigInterpretationException {
		ObservableModelSet.Wrapped localModels = parseLocalModel(session);
		ClassView cv = session.getClassView();
		ObservableModelSet model = session.getModels();
		ValueContainer<SettableValue, ?> value;
		ObservableExpression valueX = session.getAttribute("value", ObservableExpression.class);
		ObservableExpression formatX = session.getAttribute("format", ObservableExpression.class);
		Function<ModelSetInstance, SettableValue<Format<Object>>> format;
		value = valueX.evaluate(ModelTypes.Value.any(), model, cv);
		if (formatX != null) {
			format = formatX.evaluate(//
				ModelTypes.Value.forType(Format.class, value.getType().getType(0)), model, cv);
		} else {
			Class<?> type = TypeTokens.get().unwrap(TypeTokens.getRawType(value.getType().getType(0)));
			Format<?> f;
			if (type == String.class)
				f = SpinnerFormat.NUMERICAL_TEXT;
			else if (type == int.class)
				f = SpinnerFormat.INT;
			else if (type == long.class)
				f = SpinnerFormat.LONG;
			else if (type == double.class)
				f = Format.doubleFormat(4).build();
			else if (type == float.class)
				f = Format.doubleFormat(4).buildFloat();
			else if (type == Instant.class)
				f = SpinnerFormat.flexDate(Instant::now, "EEE MMM dd, yyyy", null);
			else if (type == Duration.class)
				f = SpinnerFormat.flexDuration(false);
			else
				throw new QonfigInterpretationException(
					"No default format available for type " + value.getType().getType(0) + " -- format must be specified");
			format = ObservableModelSet.literalContainer(ModelTypes.Value.forType((Class<Format<Object>>) (Class<?>) Format.class),
				(Format<Object>) f, type.getSimpleName());
		}
		ValueContainer<SettableValue, SettableValue<Integer>> rows = session.interpretAttribute("rows", ObservableExpression.class, true,
			ex -> ex.evaluate(ModelTypes.Value.forType(Integer.class), model, cv));
		ValueContainer<SettableValue, SettableValue<Boolean>> html = session.interpretAttribute("html", ObservableExpression.class, true,
			ex -> ex.evaluate(ModelTypes.Value.forType(Boolean.class), model, cv));
		ValueContainer<SettableValue, SettableValue<Boolean>> editable = session.interpretAttribute("editable", ObservableExpression.class,
			true, ex -> ex.evaluate(ModelTypes.Value.forType(Boolean.class), model, cv));
		return new AbstractQuickField(session.getElement(), localModels) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				ObservableValue<String> fieldName;
				if (getFieldName() != null)
					fieldName = getFieldName().apply(builder.getModels());
				else
					fieldName = null;
				container.addTextArea(fieldName == null ? null : fieldName.get(), //
					(SettableValue<Object>) value.apply(builder.getModels()), format.apply(builder.getModels()).get(), field -> {
						modify(field, builder);
						if (rows != null) {
							SettableValue<Integer> rowsV = rows.apply(builder.getModels());
							rowsV.changes().act(evt -> {
								field.getEditor().withRows(evt.getNewValue());
							});
						}
						if (html != null) {
							SettableValue<Boolean> htmlV = html.apply(builder.getModels());
							htmlV.changes().act(evt -> {
								field.getEditor().asHtml(evt.getNewValue());
							});
						}
						if (editable != null) {
							SettableValue<Boolean> editableV = editable.apply(builder.getModels());
							editableV.changes().act(evt -> {
								field.getEditor().setEditable(evt.getNewValue());
							});
						}
					});
				return builder.build();
			}
		};
	}

	private QuickComponentDef interpretCheckBox(ExpressoSession<?> session) throws QonfigInterpretationException {
		ObservableModelSet.Wrapped localModels = parseLocalModel(session);
		ObservableModelSet model = session.getModels();
		ClassView cv = session.getClassView();
		ValueContainer<SettableValue, SettableValue<Boolean>> value;
		value = session.getAttribute("value", ObservableExpression.class).evaluate(ModelTypes.Value.forType(boolean.class), model, cv);
		ValueContainer<SettableValue, SettableValue<String>> text = session.interpretValue(ObservableExpression.class, true,
			ex -> ex.evaluate(ModelTypes.Value.forType(String.class), model, cv));
		return new AbstractQuickValueEditor(session.getElement(), localModels) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				SettableValue<Boolean> realValue = value.apply(builder.getModels());
				container.addCheckField(null, realValue, check -> {
					if (text != null) {
						text.get(builder.getModels()).changes().act(evt -> {
							check.getEditor().setText(evt.getNewValue());
						});
					}
					check.getEditor().setAlignmentX(JCheckBox.CENTER_ALIGNMENT); // TODO Is this right? Maybe only for cell editing
					modify(check, builder);
				});
				return builder.build();
			}

			@Override
			public int getDefaultEditClicks() {
				return 1;
			}

			@Override
			public void startEditing(QuickComponent component) {
			}

			@Override
			public boolean flush(QuickComponent component) {
				return true;
			}

			@Override
			public void stopEditing(QuickComponent component) {
			}
		};
	}

	private QuickComponentDef interpretRadioButtons(ExpressoSession<?> session) throws QonfigInterpretationException {
		System.err.println("WARNING: radio-buttons is not fully implemented!!"); // TODO
		ObservableModelSet.Wrapped localModels = parseLocalModel(session);
		ObservableModelSet model = session.getModels();
		ClassView cv = session.getClassView();
		ValueContainer<SettableValue, ? extends SettableValue<Object>> value;
		value = (ValueContainer<SettableValue, ? extends SettableValue<Object>>) session.getAttribute("value", ObservableExpression.class)//
			.evaluate(ModelTypes.Value.any(), model, cv);
		ValueContainer<SettableValue, SettableValue<Object[]>> values = session.getAttribute("values", ObservableExpression.class).evaluate(
			ModelTypes.Value.forType((TypeToken<Object[]>) TypeTokens.get().getArrayType(value.getType().getType(0), 1)), model, cv);
		return new AbstractQuickField(session.getElement(), localModels) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				SettableValue<Object> realValue = value.apply(builder.getModels());
				container.addRadioField(null, //
					realValue, //
					values.apply(builder.getModels()).get(), //
					radioBs -> {
						modify(radioBs, builder);
					});
				return builder.build();
			}
		};
	}

	private QuickComponentDef interpretFileButton(ExpressoSession<?> session) throws QonfigInterpretationException {
		ObservableModelSet.Wrapped localModels = parseLocalModel(session);
		ObservableModelSet model = session.getModels();
		ClassView cv = session.getClassView();
		ValueContainer<SettableValue, ? extends SettableValue<Object>> value;
		value = (ValueContainer<SettableValue, ? extends SettableValue<Object>>) session.getAttribute("value", ObservableExpression.class)//
			.evaluate(ModelTypes.Value.any(), model, cv);
		Function<ModelSetInstance, SettableValue<File>> file;
		Class<?> valueType = TypeTokens.getRawType(value.getType().getType(0));
		if (File.class.isAssignableFrom(valueType))
			file = (ValueContainer<SettableValue, SettableValue<File>>) value;
		else if (BetterFile.class.isAssignableFrom(valueType)) {
			file = msi -> {
				SettableValue<BetterFile> betterFile = ((ValueContainer<SettableValue, SettableValue<BetterFile>>) value).apply(msi);
				return betterFile.transformReversible(File.class, tx -> tx//
					.map(FileUtils::asFile)//
					.withReverse(f -> {
						if (betterFile.get() != null)
							return BetterFile.at(betterFile.get().getSource(), f.getAbsolutePath());
						else
							return FileUtils.better(f);
					}));
			};
		} else
			throw new QonfigInterpretationException("Cannot use " + value + " as a File");
		boolean open = session.getAttribute("open", boolean.class);
		return new AbstractQuickValueEditor(session.getElement(), localModels) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				container.addFileField(null, file.apply(builder.getModels()), open, fb -> {
					modify(fb, builder);
				});
				return builder.build();
			}

			@Override
			public void startEditing(QuickComponent component) {

			}

			@Override
			public int getDefaultEditClicks() {
				return 1;
			}

			@Override
			public boolean flush(QuickComponent component) {
				return true;
			}

			@Override
			public void stopEditing(QuickComponent component) {
				((ObservableFileButton) component.getComponent()).stopEditing();
			}
		};
	}

	private QuickComponentDef evaluateLabel(ExpressoSession<?> session) throws QonfigInterpretationException {
		ObservableModelSet.Wrapped localModels = parseLocalModel(session);
		ClassView cv = session.getClassView();
		ObservableModelSet model = session.getModels();
		Function<ModelSetInstance, ? extends SettableValue> value;
		TypeToken<?> valueType;
		ObservableExpression valueEx = session.getAttribute("value", ObservableExpression.class);
		ValueContainer<SettableValue, ?> valueX;
		if (valueEx != null && valueEx != ObservableExpression.EMPTY)
			valueX = valueEx.evaluate(ModelTypes.Value.any(), model, cv);
		else if (session.getElement().getValue() == null) {
			session.withWarning("No value for label");
			valueX = ObservableModelSet.literalContainer(ModelTypes.Value.forType(String.class), "", "");
		} else
			valueX = ObservableModelSet.literalContainer(ModelTypes.Value.forType(String.class), session.getValueText(),
				session.getValueText());
		ObservableExpression formatX = session.getAttribute("format", ObservableExpression.class);
		Function<ModelSetInstance, SettableValue<Format<Object>>> format;
		if (valueX == null) {
			if (formatX != null && formatX != ObservableExpression.EMPTY)
				System.err.println("Warning: format specified on label without value");
			String txt = session.getValueText();
			if (txt == null)
				throw new IllegalArgumentException("Either 'text' or 'value' must be specified");
			value = __ -> ObservableModelSet.literal(txt, txt);
			format = __ -> ObservableModelSet.literal((Format<Object>) (Format<?>) Format.TEXT, "<unspecified>");
		} else {
			value = valueX;
			valueType = ((ValueContainer<?, ?>) value).getType().getType(0);
			if (formatX == null || formatX == ObservableExpression.EMPTY) {
				format = __ -> ObservableModelSet.literal((Format<Object>) (Format<?>) LABEL_FORMAT, "<unspecified>");
			} else
				format = formatX.evaluate(
					ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).parameterized(TypeTokens.get().wrap(valueType))), model,
					cv);
		}
		ObservableExpression iconEx = session.getAttribute("icon", ObservableExpression.class);
		Function<ModelSetInstance, SettableValue<Icon>> iconX = parseIcon(iconEx, session, model, cv);
		return new AbstractQuickField(session.getElement(), localModels) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				ObservableValue<String> fieldName;
				if (getFieldName() != null)
					fieldName = getFieldName().apply(builder.getModels());
				else
					fieldName = null;
				SettableValue<Object> valueV = value.apply(builder.getModels());
				SettableValue<Icon> iconV = iconX.apply(builder.getModels());
				container.addLabel(fieldName == null ? null : fieldName.get(), valueV, //
					format.apply(builder.getModels()).get(), field -> {
						iconV.changes().act(evt -> field.getEditor().setIcon(evt.getNewValue()));
						modify(field, builder);
					});
				return builder.build();
			}
		};
	}

	private <T> QuickComponentDef interpretTable(ExpressoSession<?> session) throws QonfigInterpretationException {
		ObservableModelSet.Wrapped localModels = parseLocalModel(session);
		ObservableModelSet model = session.getModels();
		ClassView cv = session.getClassView();
		ValueContainer<ObservableCollection, ? extends ObservableCollection<T>> rows = (ValueContainer<ObservableCollection, ? extends ObservableCollection<T>>) session
			.getAttribute("rows", ObservableExpression.class).evaluate(ModelTypes.Collection.any(), model, cv);
		String valueName = session.getAttributeText("value-name");
		session.put("value-name", valueName);
		String colValueName = session.getAttributeText("render-value-name");
		session.put("render-value-name", colValueName);

		Function<ModelSetInstance, SettableValue<Object>> selectionV;
		Function<ModelSetInstance, ObservableCollection<Object>> selectionC;
		ObservableExpression selectionS = session.getAttribute("selection", ObservableExpression.class);
		if (selectionS != null) {
			ValueContainer<?, ?> selection = selectionS.evaluate(null, model, cv);
			ModelType<?> type = selection.getType().getModelType();
			if (type == ModelTypes.Value) {
				selectionV = (Function<ModelSetInstance, SettableValue<Object>>) selection;
				selectionC = null;
			} else if (type == ModelTypes.Collection || type == ModelTypes.SortedCollection) {
				selectionV = null;
				selectionC = (Function<ModelSetInstance, ObservableCollection<Object>>) selection;
			} else
				throw new IllegalArgumentException(
					"Model value " + selectionS + " is of type " + type + "--only Value, Collection, and SortedCollection supported");
		} else {
			selectionV = null;
			selectionC = null;
		}
		TypeToken<CategoryRenderStrategy<Object, ?>> columnType = TypeTokens.get().keyFor(CategoryRenderStrategy.class)
			.<CategoryRenderStrategy<Object, ?>> parameterized(rows.getType().getType(0), TypeTokens.get().WILDCARD);
		ObservableExpression columnsX = session.getAttribute("columns", ObservableExpression.class);
		Function<ModelSetInstance, ObservableCollection<CategoryRenderStrategy<Object, ?>>> columnsAttr;
		columnsAttr = columnsX == null ? null : columnsX.evaluate(ModelTypes.Collection.forType(columnType), model, cv);
		session.put("model-type", rows.getType().getType(0));
		List<Column<Object, ?>> columns = new ArrayList<>();
		for (ExpressoSession<?> columnEl : session.forChildren("column"))
			columns.add(columnEl.interpret(Column.class));
		// TODO Make a wrapped model set with variables for value-name and render-value-name
		List<ValueAction> actions = session.interpretChildren("action", ValueAction.class);
		return new AbstractQuickComponentDef(session.getElement(), localModels) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				container.addTable((ObservableCollection<Object>) rows.apply(builder.getModels()), table -> {
					modify(table, builder);
					if (selectionV != null)
						table.withSelection(selectionV.apply(builder.getModels()), false);
					if (selectionC != null)
						table.withSelection(selectionC.apply(builder.getModels()));
					if (columnsAttr != null) {
						// The flatten here is so columns can also be specified on the table.
						// Without this, additional columns could not be added if, as is likely, the columnsAttr collection is unmodifiable.
						table.withColumns(ObservableCollection.flattenCollections(columnType, //
							columnsAttr.apply(builder.getModels()), //
							ObservableCollection.build(columnType).build()).collect());
					}
					for (Column<Object, ?> column : columns)
						table.withColumn(column.createColumn(builder.getModels()));
					for (ValueAction action : actions) {
						ObservableCollection<Object> actionValues = ObservableCollection
							.build((TypeToken<Object>) rows.getType().getType(0)).build();
						ModelSetInstance actionModel = action.model.wrap(builder.getModels())//
							.with(action.valueListPlaceholder, actionValues)//
							.build();
						ObservableAction<?> a = action.action.apply(actionModel);
						if (action.valueListPlaceholder != null) {
							table.withMultiAction(null, values -> {
								actionValues.clear();
								actionValues.addAll(values);
								a.act(null);
							}, configAction -> {
								configAction.allowForEmpty(action.allowForEmpty).allowForMultiple(action.allowForMultiple);
								configAction.modifyButton(btn -> {
									if (action.name != null)
										btn.withText(action.name.apply(builder.getModels()));
									if (action.icon != null)
										btn.withIcon(action.icon.apply(builder.getModels()));
									if (action.enabled != null)
										btn.disableWith(action.enabled.apply(builder.getModels()));
								});
							});
						} else {
							table.withAction(null, value -> {
								a.act(null);
							}, configAction -> {
								configAction.allowForEmpty(action.allowForEmpty).allowForMultiple(action.allowForMultiple);
								configAction.modifyButton(btn -> {
									if (action.name != null)
										btn.withText(action.name.apply(builder.getModels()));
									if (action.icon != null)
										btn.withIcon(action.icon.apply(builder.getModels()));
									if (action.enabled != null)
										btn.disableWith(action.enabled.apply(builder.getModels()));
								});
							});
						}
					}
				});
				return builder.build();
			}
		};
	}

	private <T, E extends PanelPopulation.TreeEditor<T, E>> QuickComponentDef interpretTree(ExpressoSession<?> session)
		throws QonfigInterpretationException {
		return interpretAbstractTree(session, new TreeMaker<T, E>() {
			@Override
			public void configure(ObservableModelSet model, ValueContainer<SettableValue, ? extends SettableValue<T>> root) {
			}

			@Override
			public void makeTree(QuickComponent.Builder builder, PanelPopulator<?, ?> container, ObservableValue<T> root,
				Function<? super BetterList<T>, ? extends ObservableCollection<? extends T>> children, Consumer<E> treeData) {
				container.addTree2(root, children, t -> treeData.accept((E) t));
			}
		});
	}

	interface TreeMaker<T, E extends PanelPopulation.AbstractTreeEditor<T, ?, E>> {
		void configure(ObservableModelSet model, ValueContainer<SettableValue, ? extends SettableValue<T>> root)
			throws QonfigInterpretationException;

		void makeTree(QuickComponent.Builder builder, PanelPopulator<?, ?> container, ObservableValue<T> root,
			Function<? super BetterList<T>, ? extends ObservableCollection<? extends T>> children, Consumer<E> treeData);
	}

	protected <T, E extends PanelPopulation.AbstractTreeEditor<T, ?, E>> QuickComponentDef interpretAbstractTree(ExpressoSession<?> session,
		TreeMaker<T, E> treeMaker) throws QonfigInterpretationException {
		ObservableModelSet.Wrapped localModels = parseLocalModel(session);
		QonfigToolkit base = BASE.get();
		System.err.println("WARNING: " + session.getElement().getType().getName() + " is not fully implemented!!"); // TODO
		ObservableModelSet model = session.getModels();
		ClassView cv = session.getClassView();
		ValueContainer<SettableValue, ? extends SettableValue<T>> root = (ValueContainer<SettableValue, ? extends SettableValue<T>>) session
			.getAttribute("root", ObservableExpression.class).evaluate(ModelTypes.Value.any(), model, cv);
		TypeToken<T> valueType = (TypeToken<T>) root.getType().getType(0);
		TypeToken<BetterList<T>> pathType = TypeTokens.get().keyFor(BetterList.class).parameterized(valueType);
		String valueName = session.getAttributeText("value-name");
		session.put("value-name", valueName);
		String renderValueName = session.getAttributeText("render-value-name");
		session.put("render-value-name", renderValueName);
		ObservableModelSet.WrappedBuilder wb = ObservableModelSet.wrap(model);
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue, SettableValue<BetterList<T>>> pathPlaceholder = wb
			.withRuntimeValue(valueName, ModelTypes.Value.forType(pathType));
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue, SettableValue<T>> cellPlaceholder = wb.withRuntimeValue(renderValueName,
			ModelTypes.Value.forType(valueType));
		ObservableModelSet.Wrapped wModel = wb.build();

		Function<ModelSetInstance, ? extends ObservableCollection<? extends T>> children;
		children = session.getAttribute("children", ObservableExpression.class)
			.evaluate(ModelTypes.Collection.forType(TypeTokens.get().getExtendsWildcard(valueType)), wModel, cv);
		Column<BetterList<T>, T> treeColumn;
		if (!session.getChildren("tree-column").isEmpty()) {
			session.put("model-type", pathType);
			treeColumn = session.forChildren("tree-column").getFirst().interpret(Column.class);
		} else
			treeColumn = null;
		ValueContainer<ObservableCollection, ObservableCollection<T>> multiSelectionV;
		ValueContainer<ObservableCollection, ObservableCollection<BetterList<T>>> multiSelectionPath;
		ValueContainer<SettableValue, SettableValue<T>> singleSelectionV;
		ValueContainer<SettableValue, SettableValue<BetterList<T>>> singleSelectionPath;
		ObservableExpression selectionEx = session.getAttribute("selection", ObservableExpression.class);
		if (selectionEx == null) {
			multiSelectionV = null;
			multiSelectionPath = null;
			singleSelectionV = null;
			singleSelectionPath = null;
		} else {
			ValueContainer<?, ?> selection = selectionEx.evaluate(//
				null, wModel, cv);
			ValueContainer<Object, Object> hackS = (ValueContainer<Object, Object>) selection;
			if (selection.getType().getModelType() == ModelTypes.Value) {
				multiSelectionV = null;
				multiSelectionPath = null;
				if (TypeTokens.get().isAssignable(valueType, selection.getType().getType(0))) {
					singleSelectionPath = null;
					singleSelectionV = hackS.getType().as(hackS, ModelTypes.Value.forType(valueType));
				} else if (TypeTokens.get().isAssignable(pathType, selection.getType().getType(0))) {
					singleSelectionV = null;
					singleSelectionPath = hackS.getType().as(hackS, ModelTypes.Value.forType(pathType));
				} else
					throw new QonfigInterpretationException(
						"Value " + selectionEx + ", type " + selection.getType() + " cannot be used for tree selection");
			} else if (selection.getType().getModelType() == ModelTypes.Collection
				|| selection.getType().getModelType() == ModelTypes.SortedCollection || selection.getType().getModelType() == ModelTypes.Set
				|| selection.getType().getModelType() == ModelTypes.SortedSet) {
				singleSelectionV = null;
				singleSelectionPath = null;
				throw new UnsupportedOperationException("Tree multi-selection is not yet implemented");
			} else
				throw new QonfigInterpretationException(
					"Value " + selectionEx + ", type " + selection.getType() + " cannot be used for tree selection");
		}
		ObservableExpression leafX = session.getAttribute("leaf", ObservableExpression.class);
		ValueContainer<SettableValue, SettableValue<Boolean>> leaf = leafX == null ? null
			: leafX.evaluate(ModelTypes.Value.forType(boolean.class), wModel, cv);
		treeMaker.configure(wModel, root);
		return new AbstractQuickComponentDef(session.getElement(), localModels) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				treeMaker.makeTree(//
					builder, container, root.apply(builder.getModels()), p -> {
						SettableValue<BetterList<T>> pathValue = SettableValue.asSettable(ObservableValue.of(pathType, p),
							__ -> "Can't modify here");
						SettableValue<T> nodeValue = SettableValue.asSettable(ObservableValue.of(valueType, p.getLast()),
							__ -> "Can't modify here");
						ModelSetInstance nodeModel = wModel.wrap(builder.getModels())//
							.with(pathPlaceholder, pathValue)//
							.with(cellPlaceholder, nodeValue)//
							.build();
						return children.apply(nodeModel);
					}, tree -> {
						modify(tree.fill().fillV(), builder);
						if (leaf != null) {
							tree.withLeafTest(value -> {
								SettableValue<T> nodeValue = SettableValue.asSettable(ObservableValue.of(valueType, value),
									__ -> "Can't modify here");
								// TODO Just hacking this one in here
								SettableValue<BetterList<T>> pathValue = SettableValue
									.asSettable(ObservableValue.of(pathType, BetterList.of(value)), __ -> "Can't modify here");
								ModelSetInstance nodeModel = wModel.wrap(builder.getModels())//
									.with(pathPlaceholder, pathValue)//
									.with(cellPlaceholder, nodeValue)//
									.build();
								return leaf.apply(nodeModel).get();
							});
						}
						if (treeColumn != null)
							tree.withRender(treeColumn.createColumn(builder.getModels()));
						if (singleSelectionV != null)
							tree.withValueSelection(singleSelectionV.apply(builder.getModels()), false);
						else if (singleSelectionPath != null)
							tree.withSelection(singleSelectionPath.apply(builder.getModels()), false);
						else if (multiSelectionV != null)
							tree.withValueSelection(multiSelectionV.apply(builder.getModels()));
						else if (multiSelectionPath != null)
							tree.withSelection(multiSelectionPath.apply(builder.getModels()));
					});
				return builder.build();
			}
		};
	}

	public interface TabSet<T> {
		TypeToken<T> getType();

		QuickComponentDef getContent();

		ObservableCollection<T> getValues(ModelSetInstance models);

		ModelSetInstance overrideModels(ModelSetInstance models, SettableValue<T> tabValue, Observable<?> until);

		void modifyTab(ObservableValue<T> value, PanelPopulation.TabEditor<?> tabEditor, ModelSetInstance models);
	}

	static class SingleTab<T> implements TabSet<T> {
		final ObservableModelSet models;
		final ObservableModelSet.RuntimeValuePlaceholder<SettableValue, SettableValue<T>> tabValuePlaceholder;
		final QuickComponentDef content;
		final ValueContainer<SettableValue, SettableValue<T>> tabId;
		final String renderValueName;
		final Function<ModelSetInstance, ? extends ObservableValue<String>> tabName;
		final Function<ModelSetInstance, ? extends ObservableValue<Icon>> tabIcon;
		final boolean removable;
		final Function<ModelSetInstance, Consumer<T>> onRemove;
		final Function<ModelSetInstance, Observable<?>> selectOn;
		final Function<ModelSetInstance, Consumer<T>> onSelect;

		private SingleTab(ObservableModelSet models,
			ObservableModelSet.RuntimeValuePlaceholder<SettableValue, SettableValue<T>> tabValuePlaceholder, QuickComponentDef content,
			ValueContainer<SettableValue, SettableValue<T>> tabId, String renderValueName,
			Function<ModelSetInstance, ? extends ObservableValue<String>> tabName,
				Function<ModelSetInstance, ? extends SettableValue<Icon>> tabIcon, boolean removable,
					Function<ModelSetInstance, Consumer<T>> onRemove, Function<ModelSetInstance, Observable<?>> selectOn,
					Function<ModelSetInstance, Consumer<T>> onSelect) {
			this.models = models;
			this.tabValuePlaceholder = tabValuePlaceholder;
			this.content = content;
			this.tabId = tabId;
			this.renderValueName = renderValueName;
			this.tabName = tabName;
			this.tabIcon = tabIcon;
			this.removable = removable;
			this.onRemove = onRemove;
			this.selectOn = selectOn;
			this.onSelect = onSelect;
		}

		@Override
		public TypeToken<T> getType() {
			return (TypeToken<T>) tabId.getType().getType(0);
		}

		@Override
		public QuickComponentDef getContent() {
			return content;
		}

		@Override
		public ObservableCollection<T> getValues(ModelSetInstance models) {
			// The collection allows removal
			SettableValue<T> id = tabId.get(models);
			ObservableCollection<T> values = ObservableCollection.build(id.getType()).build();
			values.add(id.get());
			id.changes().takeUntil(models.getUntil()).act(evt -> {
				values.set(0, evt.getNewValue());
			});
			return values;
		}

		@Override
		public ModelSetInstance overrideModels(ModelSetInstance models, SettableValue<T> tabValue, Observable<?> until) {
			if (tabValuePlaceholder != null) {
				ModelSetInstance newModels = ((ObservableModelSet.Wrapped) this.models).wrap(models)//
					.with(tabValuePlaceholder, tabValue)//
					.withUntil(until)//
					.build();
				return newModels;
			} else
				return models;
		}

		@Override
		public void modifyTab(ObservableValue<T> value, TabEditor<?> tabEditor, ModelSetInstance models) {
			tabEditor.setName(tabName.apply(models));
			if (tabIcon != null)
				tabEditor.setIcon(tabIcon.apply(models));
			if (removable) {
				tabEditor.setRemovable(true);
				if (onRemove != null) {
					Consumer<T> or = onRemove.apply(models);
					tabEditor.onRemove(v -> or.accept((T) v));
				}
			}
			if (selectOn != null)
				tabEditor.selectOn(selectOn.apply(models));
			if (onSelect != null) {
				Consumer<T> os = onSelect.apply(models);
				tabEditor.onSelect(v -> os.accept((T) v));
			}
		}

		static <T> SingleTab<T> parse(ExpressoSession<?> tab) throws QonfigInterpretationException {
			QonfigToolkit base = BASE.get();
			QuickComponentDef content = tab.interpret(QuickComponentDef.class);
			ValueContainer<SettableValue, SettableValue<T>> tabId = tab.getAttributeAsValue("tab-id",
				(TypeToken<T>) TypeTokens.get().WILDCARD, null);
			String renderValueName;
			ObservableModelSet.RuntimeValuePlaceholder<SettableValue, SettableValue<T>> tvp;
			if (tab.getElement().isInstance(base.getElementOrAddOn("single-rendering"))) {
				renderValueName = tab.getAttributeText("render-value-name");
				ObservableModelSet.WrappedBuilder wb = ObservableModelSet.wrap(tab.getModels());
				tvp = wb.withRuntimeValue(renderValueName, ModelTypes.Value.forType((TypeToken<T>) tabId.getType().getType(0)));
				tab.setModels(wb.build());
			} else {
				renderValueName = null;
				tvp = null;
			}

			ObservableExpression tabNameEx = tab.getAttribute("tab-name", ObservableExpression.class);
			Function<ModelSetInstance, ? extends ObservableValue<String>> tabName = tab.getAttributeAsValue("tab-name", String.class,
				() -> msi -> SettableValue.of(String.class, tabId.get(msi).get().toString(), "Not editable"));

			ObservableExpression tabIconEx = tab.getAttribute("tab-icon", ObservableExpression.class);
			Function<ModelSetInstance, SettableValue<Icon>> tabIcon = parseIcon(tab.getAttribute("tab-icon", ObservableExpression.class),
				tab, tab.getModels(), tab.getClassView());

			boolean removable = tab.getAttribute("removable", boolean.class);

			ObservableExpression onRemoveEx = tab.getAttribute("on-remove", ObservableExpression.class);
			Function<ModelSetInstance, Consumer<T>> onRemove;
			if (onRemoveEx != null) {
				// TODO This should be a value, with the specified renderValueName available
				if (!removable) {
					tab.withWarning("on-remove specified, for tab '" + tab.getAttributeText("tab-id") + "' but tab is not removable");
					return null;
				} else {
					onRemove = onRemoveEx.<T, Object, Object, Void> findMethod(Void.class, tab.getModels(), tab.getClassView())//
						.withOption0().withOption1((TypeToken<T>) tabId.getType().getType(0), t -> t)//
						.find1().andThen(fn -> t -> fn.apply(t));
				}
			} else
				onRemove = null;

			Function<ModelSetInstance, ? extends Observable<?>> selectOn = tab.getAttribute("select-on",
				ModelTypes.Event.forType(TypeTokens.get().WILDCARD), null);

			ObservableExpression onSelectEx = tab.getAttribute("on-select", ObservableExpression.class);
			Function<ModelSetInstance, Consumer<T>> onSelect;
			if (onSelectEx != null) {
				// TODO This should be a value, with the specified renderValueName available
				onSelect = onSelectEx.<T, Object, Object, Void> findMethod(Void.class, tab.getModels(), tab.getClassView())//
					.withOption0().withOption1((TypeToken<T>) tabId.getType().getType(0), t -> t)//
					.find1().andThen(fn -> t -> fn.apply(t));
			} else
				onSelect = null;

			return new SingleTab<>(tab.getModels(), tvp, content, tabId, renderValueName, tabName, tabIcon, removable, onRemove,
				(Function<ModelSetInstance, Observable<?>>) selectOn, onSelect);
		}
	}

	static class MultiTabSet<T> implements TabSet<T> {
		final ObservableModelSet.Wrapped models;
		final ObservableModelSet.RuntimeValuePlaceholder<SettableValue, SettableValue<T>> tabValuePlaceholder;
		final QonfigElement element;
		final ValueContainer<ObservableCollection, ObservableCollection<T>> values;
		final String renderValueName;
		final QuickComponentDef content;
		final Function<ModelSetInstance, Function<T, String>> tabName;
		final Function<ModelSetInstance, Function<T, Icon>> tabIcon;
		final Function<ModelSetInstance, Function<T, Boolean>> removable;
		final Function<ModelSetInstance, Consumer<T>> onRemove;
		final Function<ModelSetInstance, Function<T, Observable<?>>> selectOn;
		final Function<ModelSetInstance, Consumer<T>> onSelect;

		MultiTabSet(ObservableModelSet.Wrapped models,
			ObservableModelSet.RuntimeValuePlaceholder<SettableValue, SettableValue<T>> tabValuePlaceholder, QonfigElement element,
			ValueContainer<ObservableCollection, ObservableCollection<T>> values, String renderValueName, QuickComponentDef content,
			Function<ModelSetInstance, Function<T, String>> tabName, Function<ModelSetInstance, Function<T, Icon>> tabIcon,
			Function<ModelSetInstance, Function<T, Boolean>> removable, Function<ModelSetInstance, Consumer<T>> onRemove,
			Function<ModelSetInstance, Function<T, Observable<?>>> selectOn, Function<ModelSetInstance, Consumer<T>> onSelect) {
			this.models = models;
			this.tabValuePlaceholder = tabValuePlaceholder;
			this.element = element;
			this.values = values;
			this.renderValueName = renderValueName;
			this.content = content;
			this.tabName = tabName;
			this.tabIcon = tabIcon;
			this.removable = removable;
			this.onRemove = onRemove;
			this.selectOn = selectOn;
			this.onSelect = onSelect;
		}

		@Override
		public TypeToken<T> getType() {
			return (TypeToken<T>) values.getType().getType(0);
		}

		@Override
		public QuickComponentDef getContent() {
			return content;
		}

		@Override
		public ObservableCollection<T> getValues(ModelSetInstance models) {
			return values.get(models);
		}

		@Override
		public ModelSetInstance overrideModels(ModelSetInstance models, SettableValue<T> tabValue, Observable<?> until) {
			ModelSetInstance newModels = this.models.wrap(models)//
				.with(tabValuePlaceholder, tabValue)//
				.withUntil(until)//
				.build();
			return newModels;
		}

		@Override
		public void modifyTab(ObservableValue<T> value, TabEditor<?> tabEditor, ModelSetInstance models) {
			tabEditor.setName(value.map(tabName.apply(models)));
			if (tabIcon != null)
				tabEditor.setIcon(value.map(tabIcon.apply(models)));
			if (removable != null) {
				Function<T, Boolean> rem = removable.apply(models);
				value.changes().takeUntil(models.getUntil()).act(evt -> {
					tabEditor.setRemovable(rem.apply(evt.getNewValue()));
				});
				if (onRemove != null) {
					Consumer<T> or = onRemove.apply(models);
					tabEditor.onRemove(v -> or.accept((T) v));
				}
			}
			if (selectOn != null) {
				Function<T, Observable<?>> so = selectOn.apply(models);
				tabEditor.selectOn(ObservableValue.flattenObservableValue(value.map(so)));
			}
			if (onSelect != null) {
				Consumer<T> os = onSelect.apply(models);
				tabEditor.onSelect(v -> os.accept((T) v));
			}
		}

		static <T> MultiTabSet<T> parse(ExpressoSession<?> tabSet) throws QonfigInterpretationException {
			QonfigToolkit base = BASE.get();
			ValueContainer<ObservableCollection, ObservableCollection<T>> values = tabSet.getAttributeAsCollection("values",
				(TypeToken<T>) TypeTokens.get().WILDCARD, null);
			String renderValueName = tabSet.getAttributeText("render-value-name");
			ObservableModelSet.WrappedBuilder wb = ObservableModelSet.wrap(tabSet.getModels());
			ObservableModelSet.RuntimeValuePlaceholder<SettableValue, SettableValue<T>> tvp = wb.withRuntimeValue(renderValueName,
				ModelTypes.Value.forType((TypeToken<T>) values.getType().getType(0)));
			tabSet.setModels(wb.build());

			QuickComponentDef content = tabSet.interpretChildren("renderer", QuickComponentDef.class).getFirst();

			Function<ModelSetInstance, Function<T, String>> tabName = tabSet.interpretAttribute("tab-name", ObservableExpression.class,
				false, ex -> {
					if (ex == null) {
						return msi -> String::valueOf;
					} else {
						return ex.<T, Object, Object, String> findMethod(String.class, tabSet.getModels(), tabSet.getClassView())
							.withOption0().withOption1((TypeToken<T>) values.getType().getType(0), a -> a)//
							.find1();
					}
				});

			Function<ModelSetInstance, Function<T, Icon>> tabIcon = tabSet.interpretAttribute("tab-icon", ObservableExpression.class, true,
				ex -> {
					MethodFinder<T, Object, Object, Object> finder = ex
						.<T, Object, Object, Object> findMethod((TypeToken<Object>) TypeTokens.get().WILDCARD, tabSet.getModels(),
							tabSet.getClassView())
						.withOption0().withOption1((TypeToken<T>) values.getType().getType(0), a -> a);
					Function<ModelSetInstance, Function<T, Object>> iconFn = finder.find1();
					Class<?> iconType = TypeTokens.getRawType(finder.getResultType());
					if (iconType == Image.class)
						return (Function<ModelSetInstance, Function<T, Icon>>) (Function<?, ?>) iconFn;
					else if (iconType == URL.class) {
						return msi -> {
							Function<T, URL> urlFn = (Function<T, URL>) (Function<?, ?>) iconFn.apply(msi);
							return tabValue -> {
								URL url = urlFn.apply(tabValue);
								if (url == null)
									return null;
								return new ImageIcon(url); // There's probably a better way to do this, but this is what I know
							};
						};
					} else if (iconType == String.class) {
						return msi -> {
							Function<T, String> strFn = (Function<T, String>) (Function<?, ?>) iconFn.apply(msi);
							return tabValue -> {
								String str = strFn.apply(tabValue);
								if (str == null)
									return null;
								URL url;
								try {
									String location = QommonsConfig.resolve(str, tabSet.getElement().getDocument().getLocation());
									url = QommonsConfig.toUrl(location);
								} catch (IOException e) {
									throw new IllegalArgumentException("Could not resolve icon location '" + str + "'", e);
								}
								return new ImageIcon(url); // There's probably a better way to do this, but this is what I know
							};
						};
					} else {
						tabSet.withWarning("Cannot interpret tab-icon '" + tabSet.getAttributeText("tab-icon") + "', type "
							+ finder.getResultType() + " as an image");
						return null;
					}
				});

			// TODO This should be a value, with the specified renderValueName available
			Function<ModelSetInstance, Function<T, Boolean>> removable = tabSet.interpretAttribute("removable", ObservableExpression.class,
				true, ex -> ex.<T, Object, Object, Boolean> findMethod(boolean.class, tabSet.getModels(), tabSet.getClassView())//
				.withOption0().withOption1((TypeToken<T>) values.getType().getType(0), t -> t)//
				.find1());

			// TODO This should be a value, with the specified renderValueName available
			Function<ModelSetInstance, Consumer<T>> onRemove = tabSet.interpretAttribute("on-remove", ObservableExpression.class, true,
				ex -> ex.<T, Object, Object, Void> findMethod(Void.class, tabSet.getModels(), tabSet.getClassView())//
				.withOption0().withOption1((TypeToken<T>) values.getType().getType(0), t -> t)//
				.find1().andThen(fn -> t -> fn.apply(t)));

			// TODO This should be a value, with the specified renderValueName available
			Function<ModelSetInstance, Function<T, Observable<?>>> selectOn = tabSet.interpretAttribute("select-on",
				ObservableExpression.class, true,
				ex -> ex
				.<T, Object, Object, Observable<?>> findMethod(TypeTokens.get().keyFor(Observable.class).<Observable<?>> wildCard(),
					tabSet.getModels(), tabSet.getClassView())//
				.withOption0().withOption1((TypeToken<T>) values.getType().getType(0), t -> t)//
				.find1());

			// TODO This should be a value, with the specified renderValueName available
			Function<ModelSetInstance, Consumer<T>> onSelect = tabSet.interpretAttribute("on-select", ObservableExpression.class, true,
				ex -> ex.<T, Object, Object, Void> findMethod(Void.class, tabSet.getModels(), tabSet.getClassView())//
				.withOption0().withOption1((TypeToken<T>) values.getType().getType(0), t -> t)//
				.find1().andThen(fn -> t -> fn.apply(t)));

			return new MultiTabSet<>((ObservableModelSet.Wrapped) tabSet.getModels(), tvp, tabSet.getElement(), values,
				tabSet.getAttributeText("render-value-name"), content, tabName, tabIcon, removable, onRemove, selectOn, onSelect);
		}
	}

	static class TabValue<T> {
		final SettableValue<T> value;
		final ModelSetInstance models;
		final SimpleObservable<?> until;
		final TabSet<T> tabs;
		TabEditor<?> tab;
		QuickComponent component;

		TabValue(SettableValue<T> value, ModelSetInstance models, TabSet<T> tabs) {
			this.value = value;
			this.models = models;
			this.tabs = tabs;
			until = SimpleObservable.build().build();
		}
	}

	private <T> QuickComponentDef interpretTabs(ExpressoSession<?> session) throws QonfigInterpretationException {
		ObservableModelSet.Wrapped localModels = parseLocalModel(session);
		QonfigToolkit base = BASE.get();
		ObservableModelSet model = session.getModels();
		ClassView cv = session.getClassView();
		List<TabSet<? extends T>> tabs = new ArrayList<>();
		QonfigChildDef.Declared tabSetDef = base.getChild("tabs", "tab-set").getDeclared();
		QonfigChildDef.Declared widgetDef = base.getChild("tabs", "content").getDeclared();
		List<TypeToken<? extends T>> tabTypes = new ArrayList<>();
		for (ExpressoSession<?> child : session.forChildren()) {
			if (child.getElement().getDeclaredRoles().contains(tabSetDef)) {
				MultiTabSet tabSet = MultiTabSet.parse(child.as("tab-set"));
				tabTypes.add(tabSet.values.getType().getType(0));
				tabs.add(tabSet);
			} else if (child.getElement().getDeclaredRoles().contains(widgetDef)) {
				SingleTab tab = SingleTab.parse(child.as("tab"));
				tabTypes.add(tab.tabId.getType().getType(0));
				tabs.add(tab);
			}
		}
		TypeToken<T> tabType = TypeTokens.get().getCommonType(tabTypes);
		ValueContainer<SettableValue, SettableValue<T>> selection = session.interpretAttribute("selected", ObservableExpression.class, true,
			ex -> ex.evaluate(ModelTypes.Value.forType(TypeTokens.get().getSuperWildcard(tabType)), model, cv));
		return new AbstractQuickComponentDef(session.getElement(), localModels) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				TypeToken<TabValue<? extends T>> tabValueType = TypeTokens.get().keyFor(TabValue.class)
					.<TabValue<? extends T>> parameterized(TypeTokens.get().getExtendsWildcard(tabType));
				ObservableCollection<TabValue<? extends T>>[] tabValues = new ObservableCollection[tabs.size()];
				int t = 0;
				for (TabSet<? extends T> tab : tabs) {
					TabSet<T> tab2 = (TabSet<T>) tab; // Avoid a bunch of other generic workarounds
					ObservableCollection<T> values = tab2.getValues(builder.getModels());
					ObservableCollection<TabValue<? extends T>> tabValuesI = ObservableCollection.build(tabValueType).build();
					values.changes().takeUntil(builder.getModels().getUntil()).act(evt -> {
						ObservableSwingUtils.onEQ(() -> {
							switch (evt.type) {
							case add:
								for (int i = 0; i < evt.getValues().size(); i++) {
									SettableValue<T> elValue = SettableValue.build(values.getType()).withValue(evt.getValues().get(i))
										.build();
									SimpleObservable<Void> tabUntil = SimpleObservable.build().build();
									tabValuesI.add(evt.getIndexes()[i],
										new TabValue<>(elValue,
											((TabSet<T>) tab).overrideModels(builder.getModels(), elValue, tabUntil.readOnly()), //
											(TabSet<T>) tab));
								}
								break;
							case remove:
								for (int i = evt.getValues().size() - 1; i >= 0; i--) {
									tabValuesI.remove(evt.getIndexes()[i]).until.onNext(null);
								}
								break;
							case set:
								for (int i = 0; i < evt.getValues().size(); i++) {
									// Would like to use the source event as a cause, but this might be executing asynchronously
									((TabValue<T>) tabValuesI.get(evt.getIndexes()[i])).value.set(evt.getValues().get(i), null);
								}
								break;
							}
						});
					});
					tabValues[t++] = tabValuesI;
				}
				ObservableCollection<TabValue<? extends T>> flatTabValues = ObservableCollection.flattenCollections(tabValueType, tabValues)
					.collect();
				container.addTabs(tabPane -> {
					flatTabValues.changes().takeUntil(builder.getModels().getUntil()).act(evt -> {
						switch (evt.type) {
						case add:
							for (int i = 0; i < evt.getValues().size(); i++) {
								TabValue<T> tab = (TabValue<T>) evt.getValues().get(i);
								int index = i;
								tabPane.withHTab(tab.value.get(), new BorderLayout(), tabPanel -> {
									tab.component = tab.tabs.getContent().install(tabPanel,
										QuickComponent.build(tab.tabs.getContent(), builder, tab.models));
									builder.getChildren().add(evt.getIndexes()[index], tab.component);
								}, panelTab -> {
									tab.tab = panelTab;
									tab.tabs.modifyTab(tab.value, panelTab, tab.models);
								});
							}
							break;
						case remove:
							for (int i = evt.getValues().size() - 1; i >= 0; i--) {
								TabValue<T> tab = (TabValue<T>) evt.getValues().get(i);
								tab.tab.remove();
								builder.getChildren().remove(evt.getIndexes()[i]);
							}
							break;
						case set:
							break;
						}
					});
					if (selection != null)
						tabPane.withSelectedTab(selection.get(builder.getModels()));
					modify(tabPane, builder);
				});
				return builder.build();
			}
		};
	}
}

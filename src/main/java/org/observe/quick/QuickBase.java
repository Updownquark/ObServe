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
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ExpressoBaseV0_1;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableExpression.MethodFinder;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.AbstractValueContainer;
import org.observe.expresso.ObservableModelSet.ExternalModelSetBuilder;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.RuntimeValuePlaceholder;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ObservableModelSet.ValueCreator;
import org.observe.expresso.QonfigExpression;
import org.observe.expresso.SuppliedModelValue;
import org.observe.expresso.ops.LambdaExpression;
import org.observe.expresso.ops.MethodReferenceExpression;
import org.observe.quick.QuickComponent.Builder;
import org.observe.quick.style.StyleQIS;
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
import org.observe.util.swing.ObservableTextEditor.ObservableTextEditorWidget;
import org.observe.util.swing.ObservableTextField;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.TabEditor;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.collect.BetterList;
import org.qommons.config.DefaultQonfigParser;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigDocument;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigParseException;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;
import org.qommons.io.BetterFile;
import org.qommons.io.FileUtils;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;

import com.google.common.reflect.TypeToken;

public class QuickBase implements QonfigInterpretation {
	public static final String NAME = "Quick-Base";
	public static final Version VERSION = new Version(0, 1, 0);

	public interface Column<R, C> {
		CategoryRenderStrategy<R, C> createColumn(ModelSetInstance models);
	}

	public interface ColumnEditing<R, C> {
		public void modifyColumn(CategoryRenderStrategy<R, C>.CategoryMutationStrategy mutation, ModelSetInstance models);
	}

	public static class ValueAction {
		public final ObservableModelSet.Wrapped model;
		public final RuntimeValuePlaceholder<ObservableCollection<?>, ObservableCollection<Object>> valueListPlaceholder;
		public final ValueContainer<SettableValue<?>, SettableValue<String>> name;
		public final Function<ModelSetInstance, SettableValue<Icon>> icon;
		public final ValueContainer<ObservableAction<?>, ObservableAction<?>> action;
		public final ValueContainer<SettableValue<?>, SettableValue<String>> enabled;
		public final boolean allowForEmpty;
		public final boolean allowForMultiple;
		public final ValueContainer<SettableValue<?>, SettableValue<String>> tooltip;

		public ValueAction(ObservableModelSet.Wrapped model,
			RuntimeValuePlaceholder<ObservableCollection<?>, ObservableCollection<Object>> valueListPlaceholder,
			ValueContainer<SettableValue<?>, SettableValue<String>> name, Function<ModelSetInstance, SettableValue<Icon>> icon,
			ValueContainer<ObservableAction<?>, ObservableAction<?>> action,
			ValueContainer<SettableValue<?>, SettableValue<String>> enabled, boolean allowForEmpty, boolean allowForMultiple,
			ValueContainer<SettableValue<?>, SettableValue<String>> tooltip) {
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
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return QommonsUtils.unmodifiableDistinctCopy(ExpressoQIS.class, StyleQIS.class);
	}

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
	}

	StyleQIS wrap(CoreSession session) throws QonfigInterpretationException {
		return session.as(StyleQIS.class);
	}

	@Override
	public QonfigInterpreterCore.Builder configureInterpreter(QonfigInterpreterCore.Builder interpreter) {
		QonfigToolkit base = interpreter.getToolkit();
		interpreter//
		.createWith("ext-widget", QuickComponentDef.class, session -> interpretExtWidget(wrap(session)))//
		.createWith("box", QuickBox.class, session -> interpretBox(wrap(session)))//
		.modifyWith("border", QuickBox.class, (box, session) -> modifyBoxBorder(box, wrap(session), base))//
		.modifyWith("simple", QuickBox.class, (box, session) -> modifyBoxSimple(box, wrap(session)))//
		.modifyWith("inline", QuickBox.class, (box, session) -> modifyBoxInline(box, wrap(session)))//
		.createWith("multi-value-action", ValueAction.class, session -> interpretMultiValueAction(wrap(session)))//
		.createWith("label", QuickComponentDef.class, session -> evaluateLabel(wrap(session)))//
		.createWith("text-field", QuickComponentDef.class, session -> interpretTextField(wrap(session)))//
		.modifyWith("editable-text-widget", QuickComponentDef.class, (comp, session) -> modifyTextEditor(comp, wrap(session)))//
		.createWith("button", QuickComponentDef.class, session -> interpretButton(wrap(session)))//
		.createWith("table", QuickComponentDef.class, session -> interpretTable(wrap(session)))//
		.createWith("column", Column.class, session -> interpretColumn(wrap(session), base))//
		.createWith("modify-row-value", ColumnEditing.class, session -> interpretRowModify(wrap(session)))//
		// .createWith("replace-row-value", ColumnEditing.class, session->interpretRowReplace(wrap(session))) TODO
		.createWith("columns", ValueCreator.class, session -> interpretColumns(wrap(session)))//
		.createWith("split", QuickComponentDef.class, session -> interpretSplit(wrap(session)))//
		.createWith("field-panel", QuickComponentDef.class, session -> interpretFieldPanel(wrap(session)))//
		.modifyWith("field", QuickComponentDef.class, (field, session) -> modifyField(field, wrap(session)))//
		.createWith("spacer", QuickComponentDef.class, session -> interpretSpacer(wrap(session)))//
		.createWith("tree", QuickComponentDef.class, session -> interpretTree(wrap(session)))//
		.createWith("text-area", QuickComponentDef.class, session -> interpretTextArea(wrap(session)))//
		.createWith("check-box", QuickComponentDef.class, session -> interpretCheckBox(wrap(session)))//
		.createWith("radio-buttons", QuickComponentDef.class, session -> interpretRadioButtons(wrap(session)))//
		.createWith("tab-set", MultiTabSet.class, session -> MultiTabSet.parse(wrap(session)))//
		.createWith("tabs", QuickComponentDef.class, session -> interpretTabs(wrap(session), base))//
		.createWith("file-button", QuickComponentDef.class, session -> interpretFileButton(wrap(session)))//
		;
		return interpreter;
	}

	private QuickComponentDef interpretExtWidget(StyleQIS session) throws QonfigInterpretationException {
		URL ref;
		try {
			String address = session.getAttributeText("ref");
			String urlStr = QommonsConfig.resolve(address, session.getElement().getDocument().getLocation());
			ref = new URL(urlStr);
		} catch (IOException e) {
			throw new QonfigInterpretationException("Bad style-sheet reference: " + session.getAttributeText("ref"), e);
		}
		DefaultQonfigParser parser = new DefaultQonfigParser();
		for (QonfigToolkit tk : session.getElement().getDocument().getDocToolkit().getDependencies().values())
			parser.withToolkit(tk);
		QonfigDocument ssDoc;
		try (InputStream in = new BufferedInputStream(ref.openStream())) {
			ssDoc = parser.parseDocument(ref.toString(), in);
		} catch (IOException e) {
			throw new QonfigInterpretationException("Could not access style-sheet reference " + ref, e);
		} catch (QonfigParseException e) {
			throw new QonfigInterpretationException("Malformed style-sheet reference " + ref, e);
		}
		QuickDocument doc = session.intepretRoot(ssDoc.getRoot()).interpret(QuickDocument.class);
		ExpressoQIS exSession = session.as(ExpressoQIS.class);
		Map<String, Object> extValues = compileExtModels("", doc.getHead().getModels(), exSession);
		return new AbstractQuickComponentDef(session) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, Builder builder) {
				ObservableModelSet.ExternalModelSetBuilder widgetModels = ObservableModelSet
					.buildExternal(exSession.getExpressoEnv().getModels().getNameChecker());
				try {
					buildExtModels(widgetModels, extValues, builder.getModels());
				} catch (QonfigInterpretationException e) {
					throw new IllegalStateException("Could not configure models for external widget at " + session.getAttributeText("ref"),
						e);
				}
				ObservableModelSet.ExternalModelSet extModels = widgetModels.build();
				QuickUiDef ui = doc.createUI(extModels);
				return ui.installContent(container);
			}
		};
	}

	private static Map<String, Object> compileExtModels(String path, ObservableModelSet models, ExpressoQIS session)
		throws QonfigInterpretationException {
		Map<String, Object> extValues = new HashMap<>();
		for (String key : models.getContentNames()) {
			Object thing = models.getThing(key);
			if (thing instanceof ObservableModelSet.ExtValueRef) {
				ValueContainer<?, ?> vc = models.get(key, true);
				extValues.put(key, session.getExpressoEnv().getModels().get(path + "." + key, vc.getType()));
			} else if (thing instanceof ObservableModelSet) {
				extValues.put(key, compileExtModels(path + "." + key, (ObservableModelSet) thing, session));
			}
		}
		return extValues;
	}

	private static void buildExtModels(ExternalModelSetBuilder widgetModels, Map<String, Object> extValues, ModelSetInstance msi)
		throws QonfigInterpretationException {
		for (Map.Entry<String, Object> entry : extValues.entrySet()) {
			if (entry.getValue() instanceof Map)
				buildExtModels(widgetModels.addSubModel(entry.getKey()), (Map<String, Object>) entry.getValue(), msi);
			else
				installExtValue(widgetModels, entry.getKey(), (ValueContainer<?, ?>) entry.getValue(), msi);
		}
	}

	private static <M, MV extends M> void installExtValue(ExternalModelSetBuilder widgetModels, String name, ValueContainer<M, MV> value,
		ModelSetInstance msi) throws QonfigInterpretationException {
		widgetModels.with(name, value.getType(), value.get(msi));
	}

	private QuickBox interpretBox(StyleQIS session) throws QonfigInterpretationException {
		List<QuickComponentDef> children = session.interpretChildren("content", QuickComponentDef.class);
		return new QuickBox(session, children, session.getAttributeText("layout"));
	}

	private QuickBox modifyBoxBorder(QuickBox box, StyleQIS session, QonfigToolkit base) throws QonfigInterpretationException {
		box.setLayout(BorderLayout::new);
		for (QuickComponentDef child : box.getChildren()) {
			String layoutConstraint;
			String region = child.getElement().getAttributeText(base.getAttribute("border-layout-child", "region"));
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

	private QuickBox modifyBoxSimple(QuickBox box, StyleQIS session) throws QonfigInterpretationException {
		box.setLayout(SimpleLayout::new);
		int c = 0;
		for (ExpressoQIS child : session.as(ExpressoQIS.class).forChildren()) {
			QuickComponentDef childComp = box.getChildren().get(c++);
			Function<ModelSetInstance, SettableValue<QuickPosition>> leftC = QuickCore
				.parsePosition(child.getAttribute("left", QonfigExpression.class), child);
			Function<ModelSetInstance, SettableValue<QuickPosition>> rightC = QuickCore
				.parsePosition(child.getAttribute("right", QonfigExpression.class), child);
			Function<ModelSetInstance, SettableValue<QuickPosition>> topC = QuickCore
				.parsePosition(child.getAttribute("top", QonfigExpression.class), child);
			Function<ModelSetInstance, SettableValue<QuickPosition>> bottomC = QuickCore
				.parsePosition(child.getAttribute("bottom", QonfigExpression.class), child);
			Function<ModelSetInstance, SettableValue<QuickSize>> minWidthC = QuickCore
				.parseSize(child.getAttribute("min-width", QonfigExpression.class), child);
			Function<ModelSetInstance, SettableValue<QuickSize>> prefWidthC = QuickCore
				.parseSize(child.getAttribute("pref-width", QonfigExpression.class), child);
			Function<ModelSetInstance, SettableValue<QuickSize>> maxWidthC = QuickCore
				.parseSize(child.getAttribute("max-width", QonfigExpression.class), child);
			Function<ModelSetInstance, SettableValue<QuickSize>> minHeightC = QuickCore
				.parseSize(child.getAttribute("min-height", QonfigExpression.class), child);
			Function<ModelSetInstance, SettableValue<QuickSize>> prefHeightC = QuickCore
				.parseSize(child.getAttribute("pref-height", QonfigExpression.class), child);
			Function<ModelSetInstance, SettableValue<QuickSize>> maxHeightC = QuickCore
				.parseSize(child.getAttribute("max-height", QonfigExpression.class), child);
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

	private QuickBox modifyBoxInline(QuickBox box, StyleQIS session) throws QonfigInterpretationException {
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

	private ValueAction interpretMultiValueAction(StyleQIS session) throws QonfigInterpretationException {
		TypeToken<Object> valueType = (TypeToken<Object>) session.get("model-type");
		String valueListName = session.getAttributeText("value-list-name");
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		ObservableModelSet.WrappedBuilder actionModel = exS.getExpressoEnv().getModels().wrap();
		RuntimeValuePlaceholder<ObservableCollection<?>, ObservableCollection<Object>> valueListNamePH = actionModel
			.withRuntimeValue(valueListName, ModelTypes.Collection.forType(valueType));
		ObservableModelSet.Wrapped builtActionModel = actionModel.build();

		ObservableExpression nameX = exS.getAttributeExpression("name");
		ObservableExpression iconX = exS.getAttributeExpression("icon");
		ObservableExpression actionX = exS.getAttributeExpression("action");
		ObservableExpression enabledX = exS.getAttributeExpression("enabled");
		ObservableExpression tooltipX = exS.getAttributeExpression("enabled");

		ValueContainer<SettableValue<?>, SettableValue<String>> nameV = nameX == null ? null
			: nameX.evaluate(ModelTypes.Value.forType(String.class), exS.getExpressoEnv());
		Function<ModelSetInstance, SettableValue<Icon>> iconV = QuickCore.parseIcon(iconX, exS, exS.getExpressoEnv());
		ValueContainer<ObservableAction<?>, ObservableAction<?>> actionV = actionX.evaluate(ModelTypes.Action.any(),
			exS.getExpressoEnv().with(builtActionModel, null));
		ValueContainer<SettableValue<?>, SettableValue<String>> enabledV = enabledX == null ? null
			: enabledX.evaluate(ModelTypes.Value.forType(String.class), exS.getExpressoEnv());
		ValueContainer<SettableValue<?>, SettableValue<String>> tooltipV = tooltipX == null ? null
			: tooltipX.evaluate(ModelTypes.Value.forType(String.class), exS.getExpressoEnv());

		return new ValueAction(builtActionModel, valueListNamePH, nameV, iconV, actionV, enabledV, //
			session.getAttribute("allow-for-empty", boolean.class), //
			session.getAttribute("allow-for-multiple", boolean.class), //
			tooltipV);
	}

	private QuickComponentDef interpretTextField(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		ValueContainer<SettableValue<?>, ?> value = exS.getAttribute("value", ModelTypes.Value.any(), null);
		ObservableExpression formatX = exS.getAttributeExpression("format");
		Function<ModelSetInstance, SettableValue<Format<Object>>> format;
		boolean commitOnType = session.getAttribute("commit-on-type", boolean.class);
		String columnsStr = session.getAttributeText("columns");
		int columns = columnsStr == null ? -1 : Integer.parseInt(columnsStr);
		if (formatX != null) {
			format = formatX.evaluate(
				ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).parameterized(value.getType().getType(0))),
				exS.getExpressoEnv());
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
			format = ValueContainer.literalContainer(ModelTypes.Value.forType((Class<Format<Object>>) (Class<?>) Format.class),
				(Format<Object>) f, type.getSimpleName());
		}
		return new AbstractQuickValueEditor(session) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				SettableValue<Object> realValue = (SettableValue<Object>) value.apply(builder.getModels());
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

	private QuickComponentDef modifyTextEditor(QuickComponentDef component, StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exSession = session.as(ExpressoQIS.class);
		exSession.satisfy(exSession.getModelValueOwner().getModelValue("error", ModelTypes.Value.forType(String.class)), Component.class, //
			() -> new ExpressoQIS.ProducerModelValueSupport<>(TypeTokens.get().STRING, null), //
			(mv, comp) -> mv.install(((ObservableTextEditorWidget<?, ?>) comp).getErrorState()));
		exSession.satisfy(exSession.getModelValueOwner().getModelValue("warning", ModelTypes.Value.forType(String.class)), Component.class, //
			() -> new ExpressoQIS.ProducerModelValueSupport<>(TypeTokens.get().STRING, null), //
			(mv, comp) -> mv.install(((ObservableTextEditorWidget<?, ?>) comp).getWarningState()));
		return component;
	}

	private QuickComponentDef interpretButton(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		Function<ModelSetInstance, SettableValue<String>> buttonText;
		ObservableExpression valueX = exS.getValueExpression();
		if (valueX == null) {
			String txt = session.getValueText();
			if (txt == null)
				throw new IllegalArgumentException("Either 'text' attribute or element value must be specified");
			buttonText = __ -> ObservableModelSet.literal(txt, txt);
		} else
			buttonText = valueX.evaluate(ModelTypes.Value.forType(String.class), exS.getExpressoEnv());
		Function<ModelSetInstance, ? extends ObservableAction<?>> action = exS.getExpressoEnv().getModels()
			.get(session.getAttributeText("action"), ModelTypes.Action);
		return new AbstractQuickComponentDef(session) {
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

	private Column<?, ?> interpretColumn(StyleQIS session, QonfigToolkit base) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		TypeToken<Object> modelType = (TypeToken<Object>) session.get("model-type");
		String name = session.getAttributeText("name");
		ObservableExpression valueX = exS.getAttributeExpression("value");
		String rowValueName = (String) session.get("value-name");
		String cellValueName = (String) session.get("render-value-name");
		if (modelType == null || rowValueName == null || cellValueName == null)
			throw new IllegalStateException(
				"column intepretation expects 'model-type', 'value-name', and 'render-value-name' session values");
		ObservableModelSet.WrappedBuilder wb = exS.getExpressoEnv().getModels().wrap();
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>> valueRowVP = wb.withRuntimeValue(rowValueName,
			ModelTypes.Value.forType(modelType));
		ObservableModelSet.Wrapped valueModel = wb.build();
		TypeToken<Object> columnType;
		Function<ModelSetInstance, Function<Object, Object>> valueFn;
		if (valueX instanceof LambdaExpression || valueX instanceof MethodReferenceExpression) {
			MethodFinder<Object, Object, Object, Object> finder = valueX.findMethod(TypeTokens.get().OBJECT, exS.getExpressoEnv())//
				.withOption(BetterList.of(modelType), new ObservableExpression.ArgMaker<Object, Object, Object>() {
					@Override
					public void makeArgs(Object t, Object u, Object v, Object[] args, ModelSetInstance models) {
						args[0] = t;
					}
				});
			valueFn = finder.find1();
			columnType = (TypeToken<Object>) finder.getResultType();
		} else if (valueX != null) {
			ValueContainer<SettableValue<?>, SettableValue<Object>> colValue = valueX.evaluate(
				(ModelType.ModelInstanceType<SettableValue<?>, SettableValue<Object>>) (ModelType.ModelInstanceType<?, ?>) ModelTypes.Value
				.any(),
				exS.getExpressoEnv().with(valueModel, null));
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
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>> cellRowVP = wb.withRuntimeValue(cellValueName,
			ModelTypes.Value.forType(columnType));
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>> subjectVP = wb.withRuntimeValue("subject",
			ModelTypes.Value.forType(columnType));
		ObservableModelSet.Wrapped cellModel = wb.build();

		SuppliedModelValue<SettableValue<?>, SettableValue<Boolean>> focused = exS.getModelValueOwner().getModelValue("focused",
			ModelTypes.Value.forType(boolean.class));
		SuppliedModelValue<SettableValue<?>, SettableValue<Boolean>> selected = exS.getModelValueOwner().getModelValue("selected",
			ModelTypes.Value.forType(boolean.class));
		SuppliedModelValue<SettableValue<?>, SettableValue<Boolean>> hovered = exS.getModelValueOwner().getModelValue("hovered",
			ModelTypes.Value.forType(boolean.class));
		ExpressoQIS.SimpleModelValueSupport<Boolean> focusedSupport = new ExpressoQIS.SimpleModelValueSupport<>(boolean.class, false);
		ExpressoQIS.SimpleModelValueSupport<Boolean> hoveredSupport = new ExpressoQIS.SimpleModelValueSupport<>(boolean.class, false);
		ExpressoQIS.SimpleModelValueSupport<Boolean> selectedSupport = new ExpressoQIS.SimpleModelValueSupport<>(boolean.class, false);
		exS.setModels(cellModel, null);
		QuickComponentDef renderer = session.forChildren("renderer", base.getElement("label"), null).getFirst()
			.interpret(QuickComponentDef.class);
		ExpressoQIS rendererS = exS.forChildren("renderer", base.getElement("label"), null).getFirst();
		rendererS.satisfy(focused, Component.class, focusedSupport, null);
		rendererS.satisfy(hovered, Component.class, hoveredSupport, null);
		rendererS.satisfy(selected, Component.class, selectedSupport, null);
		ColumnEditing<Object, Object> editing;
		ExpressoQIS columnEdit = exS.forChildren("edit").peekFirst();
		if (columnEdit != null) {
			String editValueName = columnEdit.getAttributeText("edit-value-name");
			ObservableModelSet.WrappedBuilder editorModelsB = columnEdit.getExpressoEnv().getModels().wrap();
			ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<Object>> editorValueVP = editorModelsB
				.withRuntimeValue(editValueName, ModelTypes.Value.forType(columnType));
			ObservableModelSet.Wrapped editorModels = editorModelsB.build();
			columnEdit.setModels(editorModels, null);
			ExpressoQIS editorSession = columnEdit.forChildren("editor").getFirst();
			QuickComponentDef editor = editorSession.interpret(QuickComponentDef.class);
			if (!(editor instanceof QuickValueEditorDef))
				throw new IllegalArgumentException(
					"Use of '" + editorSession.getElement().getType().getName() + "' as a column editor is not implemented");
			editorSession.satisfy(focused, Component.class, focusedSupport, null);
			editorSession.satisfy(hovered, Component.class, hoveredSupport, null);
			editorSession.satisfy(selected, Component.class, selectedSupport, null);
			ColumnEditing<Object, Object> editType = columnEdit.asElement(columnEdit.getAttribute("type", QonfigAddOn.class))
				.interpret(ColumnEditing.class);
			editing = (column, models) -> {
				// Hacky way of synthesizing the component
				SettableValue<Object> rowValue = SettableValue.build(modelType).withDescription(rowValueName).build();
				Function<Object, String>[] filter = new Function[1];
				boolean[] currentlyEditing = new boolean[1];
				SettableValue<Object> editorValue = DefaultObservableCellEditor.createEditorValue(columnType, filter,
					builder -> builder.withDescription(editValueName));
				SettableValue<Object> cellValue = SettableValue.build(columnType).withDescription(cellValueName).build();
				ModelSetInstance editModelInst = editorModels.wrap(//
					editor.getModels().wrap(//
						cellModel.wrap(models)//
						.with(valueRowVP, rowValue)//
						.with(cellRowVP, cellValue)//
						.with(subjectVP, editorValue)//
						.build())//
					.build())//
					.with(editorValueVP, editorValue)//
					.build();
				editType.modifyColumn(column, editModelInst);
				QuickComponent.Builder editorBuilder = QuickComponent.build(editor, null, editModelInst);
				QuickComponent editorComp = editor
					.install(PanelPopulation.populateHPanel(null, new JustifiedBoxLayout(false), editModelInst.getUntil()), editorBuilder);
				SettableValue<Boolean> focusV = editorSession.getIfSupported(focused.apply(editorBuilder.getModels()), focusedSupport);
				SettableValue<Boolean> hoveredV = editorSession.getIfSupported(hovered.apply(editorBuilder.getModels()), hoveredSupport);
				SettableValue<Boolean> selectedV = editorSession.getIfSupported(selected.apply(editorBuilder.getModels()), selectedSupport);
				Component c = editorComp.getComponent();
				Insets defMargin = c instanceof JTextComponent ? ((JTextComponent) c).getMargin() : null;
				String clicksStr = columnEdit.getAttribute("clicks", String.class);
				int clicks = clicksStr != null ? Integer.parseInt(clicksStr) : ((QuickValueEditorDef) editor).getDefaultEditClicks();
				ObservableCellEditor<Object, Object> oce = new DefaultObservableCellEditor<Object, Object>(c, editorValue, //
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
				}, ObservableCellEditor.editWithClicks(clicks)) {
					@Override
					protected void renderingValue(Object value, boolean selected2, boolean rowHovered, boolean cellHovered,
						boolean expanded, boolean leaf, int row, int column2) {
						if (focusV != null)
							focusV.set(selected2, null);
						if (selectedV != null)
							selectedV.set(selected2, null);
						if (hoveredV != null)
							hoveredV.set(rowHovered, null);
					}
				};
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
		Color defaultSelectionBackground = ui.getColor("List.selectionBackground");
		Color defaultSelectionForeground = ui.getColor("List.selectionForeground");
		return extModels -> {
			CategoryRenderStrategy<Object, Object> column = new CategoryRenderStrategy<>(name, columnType, valueFn.apply(extModels));
			// Hacky way of synthesizing the component
			SettableValue<Object> rowValue = SettableValue.build(modelType).withDescription(rowValueName).build();
			SettableValue<Object> cellValue = SettableValue.build(columnType).withDescription(cellValueName).build();
			ModelSetInstance cellModelInst = cellModel.wrap(renderer.getModels().wrap(extModels)//
				.build())//
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
			} else {
				textRender = (row, col) -> String.valueOf(col);
			}
			SettableValue<Boolean> focusV = rendererS.getIfSupported(focused.apply(renderBuilder.getModels()), focusedSupport);
			SettableValue<Boolean> hoveredV = rendererS.getIfSupported(hovered.apply(renderBuilder.getModels()), hoveredSupport);
			SettableValue<Boolean> selectedV = rendererS.getIfSupported(selected.apply(renderBuilder.getModels()), selectedSupport);
			column.withRenderer(new ObservableCellRenderer<Object, Object>() {
				private List<CellDecorator<Object, Object>> theDecorators;

				@Override
				public String renderAsText(Supplier<? extends Object> modelValue, Object columnValue) {
					return textRender.apply(modelValue, columnValue);
				}

				@Override
				public Component getCellRendererComponent(Component parent, ModelCell<? extends Object, ? extends Object> cell,
					CellRenderContext ctx) {
					// Not sure if this does any good, but these are what the colors should look like without any styles mucking with them
					if (cell.isSelected()) {
						if (c instanceof JComponent)
							((JComponent) c).setOpaque(true);
						c.setBackground(defaultSelectionBackground);
						c.setForeground(defaultSelectionForeground);
					} else {
						if (c instanceof JComponent)
							((JComponent) c).setOpaque(false);
						c.setBackground(Color.white);
						c.setForeground(Color.black);
					}
					rowValue.set(cell.getModelValue(), null);
					cellValue.set(cell.getCellValue(), null);
					if (focusV != null)
						focusV.set(cell.isSelected(), null);
					if (selectedV != null)
						selectedV.set(cell.isSelected(), null);
					if (hoveredV != null)
						hoveredV.set(cell.isRowHovered(), null);
					ObservableCellRenderer.tryEmphasize(c, ctx);
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
			if (editing != null)
				column.withMutation(mut -> editing.modifyColumn(mut, extModels));
			return column;
		};
	}

	private ColumnEditing<?, ?> interpretRowModify(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		System.err.println("WARNING: modify-row-value is not fully implemented!!"); // TODO
		TypeToken<Object> modelType = (TypeToken<Object>) session.get("model-type");
		String editValueName = (String) session.get("edit-value-name");
		ObservableExpression commitX = exS.getAttributeExpression("commit");
		ObservableExpression editableIfX = exS.getAttributeExpression("editable-if");
		ObservableExpression acceptX = exS.getAttributeExpression("accept");
		ValueContainer<ObservableAction<?>, ObservableAction<?>> commit = commitX.evaluate(ModelTypes.Action.any(), exS.getExpressoEnv());
		boolean rowUpdate = session.getAttribute("row-update", boolean.class);
		return (column, models) -> { // TODO Not done here
			ObservableAction<?> commitAction = commit.apply(models);
			column.mutateAttribute((row, cell) -> commitAction.act(null));
		};
	}

	private ValueCreator<ObservableCollection<?>, ObservableCollection<CategoryRenderStrategy<Object, ?>>> interpretColumns(
		StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		TypeToken<Object> rowType = (TypeToken<Object>) ExpressoBaseV0_1.parseType(session.getAttributeText("type"), exS.getExpressoEnv());
		session.put("model-type", rowType);
		TypeToken<CategoryRenderStrategy<Object, ?>> columnType = TypeTokens.get().keyFor(CategoryRenderStrategy.class)
			.<CategoryRenderStrategy<Object, ?>> parameterized(rowType, TypeTokens.get().WILDCARD);
		String rowValueName = session.getAttributeText("value-name");
		session.put("value-name", rowValueName);
		String colValueName = session.getAttributeText("render-value-name");
		session.put("render-value-name", colValueName);
		List<Column<Object, ?>> columns = session.interpretChildren("column", Column.class);
		return () -> new AbstractValueContainer<ObservableCollection<?>, ObservableCollection<CategoryRenderStrategy<Object, ?>>>(
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

	private QuickComponentDef interpretSplit(StyleQIS session) throws QonfigInterpretationException {
		if (session.getChildren("content").size() != 2)
			throw new UnsupportedOperationException("Currently only 2 (and exactly 2) contents are supported for split");
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		List<QuickComponentDef> children = session.interpretChildren("content", QuickComponentDef.class);
		boolean vertical = session.getAttributeText("orientation").equals("vertical");
		Function<ModelSetInstance, SettableValue<QuickPosition>> splitPos = QuickCore
			.parsePosition(exS.getAttribute("split-position", QonfigExpression.class), exS);
		return new AbstractQuickComponentDef(session) {
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
									EventQueue.invokeLater(() -> {
										divCallbackLock[0] = true;
										try {
											if (left.isVisible()) {
												if (right.isVisible()) {
													int target = pos.get().evaluate(vertical ? sp.getHeight() : sp.getWidth());
													if (Math.abs(target - sp.getDividerLocation()) > 1)
														sp.setDividerLocation(target);
												} else
													sp.setDividerLocation(vertical ? sp.getHeight() : sp.getWidth());
											} else if (right.isVisible())
												sp.setDividerLocation(0);
										} finally {
											divCallbackLock[0] = false;
										}
									});
									left.addComponentListener(new ComponentAdapter() {
										@Override
										public void componentShown(ComponentEvent e) {
											if (divCallbackLock[0])
												return;
											EventQueue.invokeLater(() -> {
												divCallbackLock[0] = true;
												try {
													if (right.isVisible()) {
														int target = pos.get().evaluate(vertical ? sp.getHeight() : sp.getWidth());
														if (Math.abs(target - sp.getDividerLocation()) > 1)
															sp.setDividerLocation(target);
													} else
														sp.setDividerLocation(vertical ? sp.getHeight() : sp.getWidth());
												} finally {
													divCallbackLock[0] = false;
												}
											});
										}

										@Override
										public void componentHidden(ComponentEvent e) {
											EventQueue.invokeLater(() -> {
												divCallbackLock[0] = true;
												try {
													sp.setDividerLocation(0);
												} finally {
													divCallbackLock[0] = false;
												}
											});
										}
									});
									right.addComponentListener(new ComponentAdapter() {
										@Override
										public void componentShown(ComponentEvent e) {
											if (divCallbackLock[0])
												return;
											EventQueue.invokeLater(() -> {
												divCallbackLock[0] = true;
												try {
													if (left.isVisible()) {
														int target = pos.get().evaluate(vertical ? sp.getHeight() : sp.getWidth());
														if (Math.abs(target - sp.getDividerLocation()) > 1)
															sp.setDividerLocation(target);
													} else
														sp.setDividerLocation(0);
												} finally {
													divCallbackLock[0] = false;
												}
											});
										}

										@Override
										public void componentHidden(ComponentEvent e) {
											EventQueue.invokeLater(() -> {
												divCallbackLock[0] = true;
												try {
													sp.setDividerLocation(vertical ? sp.getHeight() : sp.getWidth());
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

	private QuickComponentDef interpretFieldPanel(StyleQIS session) throws QonfigInterpretationException {
		List<QuickComponentDef> children = session.interpretChildren("content", QuickComponentDef.class);
		System.err.println("WARNING: field-panel is not fully implemented!!"); // TODO
		return new AbstractQuickComponentDef(session) {
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

	private QuickComponentDef modifyField(QuickComponentDef field, StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		ObservableExpression fieldName = exS.getAttributeExpression("field-name");
		if (fieldName != null) {
			field.setFieldName(fieldName.evaluate(ModelTypes.Value.forType(String.class), exS.getExpressoEnv()));
		}
		if (Boolean.TRUE.equals(session.getAttribute("fill", Boolean.class)))
			field.modify((f, m) -> f.fill());
		return field;
	}

	private QuickComponentDef interpretSpacer(StyleQIS session) throws QonfigInterpretationException {
		int length = Integer.parseInt(session.getAttributeText("length"));
		return new AbstractQuickComponentDef(session) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				container.spacer(length, null);
				return builder.build();
			}
		};
	}

	private QuickComponentDef interpretTextArea(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		ValueContainer<SettableValue<?>, ?> value;
		ObservableExpression valueX = exS.getAttributeExpression("value");
		ObservableExpression formatX = exS.getAttributeExpression("format");
		Function<ModelSetInstance, SettableValue<Format<Object>>> format;
		value = valueX.evaluate(ModelTypes.Value.any(), exS.getExpressoEnv());
		if (formatX != null) {
			format = formatX.evaluate(//
				ModelTypes.Value.forType(Format.class, value.getType().getType(0)), exS.getExpressoEnv());
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
			format = ValueContainer.literalContainer(ModelTypes.Value.forType((Class<Format<Object>>) (Class<?>) Format.class),
				(Format<Object>) f, type.getSimpleName());
		}
		ValueContainer<SettableValue<?>, SettableValue<Integer>> rows = exS.getAttribute("rows", ModelTypes.Value.forType(Integer.class),
			null);
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> html = exS.getAttribute("html", ModelTypes.Value.forType(Boolean.class),
			null);
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> editable = exS.getAttribute("editable",
			ModelTypes.Value.forType(Boolean.class), null);
		return new AbstractQuickField(session) {
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

	private QuickComponentDef interpretCheckBox(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> value;
		value = exS.getAttribute("value", ModelTypes.Value.forType(boolean.class), null);
		ValueContainer<SettableValue<?>, SettableValue<String>> text = exS.getValue(ModelTypes.Value.forType(String.class), null);
		return new AbstractQuickValueEditor(session) {
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

	private QuickComponentDef interpretRadioButtons(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		System.err.println("WARNING: radio-buttons is not fully implemented!!"); // TODO
		ValueContainer<SettableValue<?>, ? extends SettableValue<Object>> value;
		value = (ValueContainer<SettableValue<?>, ? extends SettableValue<Object>>) exS.getAttribute("value", ModelTypes.Value.any(), null);
		ValueContainer<SettableValue<?>, SettableValue<Object[]>> values = exS.getAttribute("values",
			ModelTypes.Value.forType((TypeToken<Object[]>) TypeTokens.get().getArrayType(value.getType().getType(0), 1)), null);
		return new AbstractQuickField(session) {
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

	private QuickComponentDef interpretFileButton(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		ValueContainer<SettableValue<?>, ? extends SettableValue<Object>> value;
		value = (ValueContainer<SettableValue<?>, ? extends SettableValue<Object>>) exS.getAttribute("value", ModelTypes.Value.any(), null);
		Function<ModelSetInstance, SettableValue<File>> file;
		Class<?> valueType = TypeTokens.getRawType(value.getType().getType(0));
		if (File.class.isAssignableFrom(valueType))
			file = (ValueContainer<SettableValue<?>, SettableValue<File>>) value;
		else if (BetterFile.class.isAssignableFrom(valueType)) {
			file = msi -> {
				SettableValue<BetterFile> betterFile = ((ValueContainer<SettableValue<?>, SettableValue<BetterFile>>) value).apply(msi);
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
		return new AbstractQuickValueEditor(session) {
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

	private QuickComponentDef evaluateLabel(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		Function<ModelSetInstance, ? extends SettableValue<?>> value;
		TypeToken<?> valueType;
		ObservableExpression valueEx = exS.getAttributeExpression("value");
		ValueContainer<SettableValue<?>, ?> valueX;
		if (valueEx != null && valueEx != ObservableExpression.EMPTY)
			valueX = valueEx.evaluate(ModelTypes.Value.any(), exS.getExpressoEnv());
		else if (session.getElement().getValue() == null) {
			session.withWarning("No value for label");
			valueX = ValueContainer.literalContainer(ModelTypes.Value.forType(String.class), "", "");
		} else
			valueX = ValueContainer.literalContainer(ModelTypes.Value.forType(String.class), session.getValueText(),
				session.getValueText());
		ObservableExpression formatX = exS.getAttributeExpression("format");
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
					ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).parameterized(TypeTokens.get().wrap(valueType))),
					exS.getExpressoEnv());
		}
		ObservableExpression iconEx = exS.getAttributeExpression("icon");
		Function<ModelSetInstance, SettableValue<Icon>> iconX = QuickCore.parseIcon(iconEx, exS, exS.getExpressoEnv());
		return new AbstractQuickField(session) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				ObservableValue<String> fieldName;
				if (getFieldName() != null)
					fieldName = getFieldName().apply(builder.getModels());
				else
					fieldName = null;
				SettableValue<Object> valueV = (SettableValue<Object>) value.apply(builder.getModels());
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

	private <T> QuickComponentDef interpretTable(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		ValueContainer<ObservableCollection<?>, ? extends ObservableCollection<T>> rows = (ValueContainer<ObservableCollection<?>, ? extends ObservableCollection<T>>) exS
			.getAttribute("rows", ModelTypes.Collection.any(), null);
		String valueName = session.getAttributeText("value-name");
		session.put("value-name", valueName);
		String colValueName = session.getAttributeText("render-value-name");
		session.put("render-value-name", colValueName);

		Function<ModelSetInstance, SettableValue<Object>> selectionV;
		Function<ModelSetInstance, ObservableCollection<Object>> selectionC;
		ObservableExpression selectionS = exS.getAttributeExpression("selection");
		if (selectionS != null) {
			ValueContainer<?, ?> selection = selectionS.evaluate(null, exS.getExpressoEnv());
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
		Function<ModelSetInstance, ObservableCollection<CategoryRenderStrategy<Object, ?>>> columnsAttr = exS.getAttribute("columns",
			ModelTypes.Collection.forType(columnType), null);
		session.put("model-type", rows.getType().getType(0));
		List<Column<Object, ?>> columns = new ArrayList<>();
		for (StyleQIS columnEl : session.forChildren("column"))
			columns.add(columnEl.interpret(Column.class));
		// TODO Make a wrapped model set with variables for value-name and render-value-name
		List<ValueAction> actions = session.interpretChildren("action", ValueAction.class);
		return new AbstractQuickComponentDef(session) {
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

	private <T, E extends PanelPopulation.TreeEditor<T, E>> QuickComponentDef interpretTree(StyleQIS session)
		throws QonfigInterpretationException {
		return interpretAbstractTree(session, new TreeMaker<T, E>() {
			@Override
			public void configure(ObservableModelSet model, ValueContainer<SettableValue<?>, ? extends SettableValue<T>> root) {
			}

			@Override
			public void makeTree(QuickComponent.Builder builder, PanelPopulator<?, ?> container, ObservableValue<T> root,
				Function<? super BetterList<T>, ? extends ObservableCollection<? extends T>> children, Consumer<E> treeData) {
				container.addTree2(root, children, t -> treeData.accept((E) t));
			}
		});
	}

	interface TreeMaker<T, E extends PanelPopulation.AbstractTreeEditor<T, ?, E>> {
		void configure(ObservableModelSet model, ValueContainer<SettableValue<?>, ? extends SettableValue<T>> root)
			throws QonfigInterpretationException;

		void makeTree(QuickComponent.Builder builder, PanelPopulator<?, ?> container, ObservableValue<T> root,
			Function<? super BetterList<T>, ? extends ObservableCollection<? extends T>> children, Consumer<E> treeData);
	}

	public static <T, E extends PanelPopulation.AbstractTreeEditor<T, ?, E>> QuickComponentDef interpretAbstractTree(StyleQIS session,
		TreeMaker<T, E> treeMaker) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		System.err.println("WARNING: " + session.getElement().getType().getName() + " is not fully implemented!!"); // TODO
		ValueContainer<SettableValue<?>, ? extends SettableValue<T>> root = (ValueContainer<SettableValue<?>, ? extends SettableValue<T>>) exS
			.getAttribute("root", ModelTypes.Value.any(), null);
		TypeToken<T> valueType = (TypeToken<T>) root.getType().getType(0);
		TypeToken<BetterList<T>> pathType = TypeTokens.get().keyFor(BetterList.class).parameterized(valueType);
		String valueName = session.getAttributeText("value-name");
		session.put("value-name", valueName);
		String renderValueName = session.getAttributeText("render-value-name");
		session.put("render-value-name", renderValueName);
		ObservableModelSet.WrappedBuilder wb = exS.getExpressoEnv().getModels().wrap();
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<BetterList<T>>> pathPlaceholder = wb
			.withRuntimeValue(valueName, ModelTypes.Value.forType(pathType));
		ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> cellPlaceholder = wb
			.withRuntimeValue(renderValueName, ModelTypes.Value.forType(valueType));
		ObservableModelSet.Wrapped wModel = wb.build();

		Function<ModelSetInstance, ? extends ObservableCollection<? extends T>> children;
		children = exS.getAttributeExpression("children").evaluate(
			ModelTypes.Collection.forType(TypeTokens.get().getExtendsWildcard(valueType)), exS.getExpressoEnv().with(wModel, null));
		Column<BetterList<T>, T> treeColumn;
		if (!session.getChildren("tree-column").isEmpty()) {
			session.put("model-type", pathType);
			treeColumn = session.forChildren("tree-column").getFirst().interpret(Column.class);
		} else
			treeColumn = null;
		ValueContainer<ObservableCollection<?>, ObservableCollection<T>> multiSelectionV;
		ValueContainer<ObservableCollection<?>, ObservableCollection<BetterList<T>>> multiSelectionPath;
		ValueContainer<SettableValue<?>, SettableValue<T>> singleSelectionV;
		ValueContainer<SettableValue<?>, SettableValue<BetterList<T>>> singleSelectionPath;
		ObservableExpression selectionEx = exS.getAttributeExpression("selection");
		if (selectionEx == null) {
			multiSelectionV = null;
			multiSelectionPath = null;
			singleSelectionV = null;
			singleSelectionPath = null;
		} else {
			ValueContainer<?, ?> selection = selectionEx.evaluate(//
				null, exS.getExpressoEnv().with(wModel, null));
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
		ObservableExpression leafX = exS.getAttributeExpression("leaf");
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> leaf = leafX == null ? null
			: leafX.evaluate(ModelTypes.Value.forType(boolean.class), exS.getExpressoEnv().with(wModel, null));
		treeMaker.configure(wModel, root);
		return new AbstractQuickComponentDef(session) {
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
		final ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> tabValuePlaceholder;
		final QuickComponentDef content;
		final ValueContainer<SettableValue<?>, SettableValue<T>> tabId;
		final String renderValueName;
		final Function<ModelSetInstance, ? extends ObservableValue<String>> tabName;
		final Function<ModelSetInstance, ? extends ObservableValue<Icon>> tabIcon;
		final boolean removable;
		final Function<ModelSetInstance, Consumer<T>> onRemove;
		final Function<ModelSetInstance, Observable<?>> selectOn;
		final Function<ModelSetInstance, Consumer<T>> onSelect;

		private SingleTab(ObservableModelSet models,
			ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> tabValuePlaceholder, QuickComponentDef content,
			ValueContainer<SettableValue<?>, SettableValue<T>> tabId, String renderValueName,
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
		public ObservableCollection<T> getValues(ModelSetInstance models2) {
			// The collection allows removal
			SettableValue<T> id = tabId.get(models2);
			ObservableCollection<T> values = ObservableCollection.build(id.getType()).build();
			values.add(id.get());
			id.changes().takeUntil(models2.getUntil()).act(evt -> {
				values.set(0, evt.getNewValue());
			});
			return values;
		}

		@Override
		public ModelSetInstance overrideModels(ModelSetInstance models2, SettableValue<T> tabValue, Observable<?> until) {
			if (tabValuePlaceholder != null) {
				ModelSetInstance newModels = ((ObservableModelSet.Wrapped) this.models).wrap(models2)//
					.with(tabValuePlaceholder, tabValue)//
					.withUntil(until)//
					.build();
				return newModels;
			} else
				return models2;
		}

		@Override
		public void modifyTab(ObservableValue<T> value, TabEditor<?> tabEditor, ModelSetInstance models2) {
			tabEditor.setName(tabName.apply(models2));
			if (tabIcon != null)
				tabEditor.setIcon(tabIcon.apply(models2));
			if (removable) {
				tabEditor.setRemovable(true);
				if (onRemove != null) {
					Consumer<T> or = onRemove.apply(models2);
					tabEditor.onRemove(v -> or.accept((T) v));
				}
			}
			if (selectOn != null)
				tabEditor.selectOn(selectOn.apply(models2));
			if (onSelect != null) {
				Consumer<T> os = onSelect.apply(models2);
				tabEditor.onSelect(v -> os.accept((T) v));
			}
		}

		static <T2> SingleTab<T2> parse(QonfigToolkit base, StyleQIS tab) throws QonfigInterpretationException {
			ExpressoQIS exTab = tab.as(ExpressoQIS.class);
			QuickComponentDef content = tab.interpret(QuickComponentDef.class);
			ValueContainer<SettableValue<?>, SettableValue<T2>> tabId = exTab.getAttributeAsValue("tab-id",
				(TypeToken<T2>) TypeTokens.get().WILDCARD, null);
			String renderValueName;
			ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T2>> tvp;
			if (tab.getElement().isInstance(base.getElementOrAddOn("single-rendering"))) {
				renderValueName = tab.getAttributeText("render-value-name");
				ObservableModelSet.WrappedBuilder wb = exTab.getExpressoEnv().getModels().wrap();
				tvp = wb.withRuntimeValue(renderValueName, ModelTypes.Value.forType((TypeToken<T2>) tabId.getType().getType(0)));
				exTab.setModels(wb.build(), null);
			} else {
				renderValueName = null;
				tvp = null;
			}

			Function<ModelSetInstance, ? extends ObservableValue<String>> tabName = exTab.getAttributeAsValue("tab-name", String.class,
				() -> msi -> SettableValue.of(String.class, tabId.get(msi).get().toString(), "Not editable"));

			Function<ModelSetInstance, SettableValue<Icon>> tabIcon = QuickCore.parseIcon(exTab.getAttributeExpression("tab-icon"), exTab,
				exTab.getExpressoEnv());

			boolean removable = tab.getAttribute("removable", boolean.class);

			ObservableExpression onRemoveEx = exTab.getAttributeExpression("on-remove");
			Function<ModelSetInstance, Consumer<T2>> onRemove;
			if (onRemoveEx != null) {
				// TODO This should be a value, with the specified renderValueName available
				if (!removable) {
					tab.withWarning("on-remove specified, for tab '" + tab.getAttributeText("tab-id") + "' but tab is not removable");
					return null;
				} else {
					onRemove = onRemoveEx.<T2, Object, Object, Void> findMethod(Void.class, exTab.getExpressoEnv())//
						.withOption0().withOption1((TypeToken<T2>) tabId.getType().getType(0), t -> t)//
						.find1().andThen(fn -> t -> fn.apply(t));
				}
			} else
				onRemove = null;

			Function<ModelSetInstance, ? extends Observable<?>> selectOn = exTab.getAttribute("select-on",
				ModelTypes.Event.forType(TypeTokens.get().WILDCARD), null);

			ObservableExpression onSelectEx = exTab.getAttributeExpression("on-select");
			Function<ModelSetInstance, Consumer<T2>> onSelect;
			if (onSelectEx != null) {
				// TODO This should be a value, with the specified renderValueName available
				onSelect = onSelectEx.<T2, Object, Object, Void> findMethod(Void.class, exTab.getExpressoEnv())//
					.withOption0().withOption1((TypeToken<T2>) tabId.getType().getType(0), t -> t)//
					.find1().andThen(fn -> t -> fn.apply(t));
			} else
				onSelect = null;

			return new SingleTab<>(exTab.getExpressoEnv().getModels(), tvp, content, tabId, renderValueName, tabName, tabIcon, removable,
				onRemove, (Function<ModelSetInstance, Observable<?>>) selectOn, onSelect);
		}
	}

	static class MultiTabSet<T> implements TabSet<T> {
		final ObservableModelSet.Wrapped models;
		final ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> tabValuePlaceholder;
		final QonfigElement element;
		final ValueContainer<ObservableCollection<?>, ObservableCollection<T>> values;
		final String renderValueName;
		final QuickComponentDef content;
		final Function<ModelSetInstance, Function<T, String>> tabName;
		final Function<ModelSetInstance, Function<T, Icon>> tabIcon;
		final Function<ModelSetInstance, Function<T, Boolean>> removable;
		final Function<ModelSetInstance, Consumer<T>> onRemove;
		final Function<ModelSetInstance, Function<T, Observable<?>>> selectOn;
		final Function<ModelSetInstance, Consumer<T>> onSelect;

		MultiTabSet(ObservableModelSet.Wrapped models,
			ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> tabValuePlaceholder, QonfigElement element,
			ValueContainer<ObservableCollection<?>, ObservableCollection<T>> values, String renderValueName, QuickComponentDef content,
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
		public ObservableCollection<T> getValues(ModelSetInstance models2) {
			return values.get(models2);
		}

		@Override
		public ModelSetInstance overrideModels(ModelSetInstance models2, SettableValue<T> tabValue, Observable<?> until) {
			ModelSetInstance newModels = this.models.wrap(models2)//
				.with(tabValuePlaceholder, tabValue)//
				.withUntil(until)//
				.build();
			return newModels;
		}

		@Override
		public void modifyTab(ObservableValue<T> value, TabEditor<?> tabEditor, ModelSetInstance tabModels) {
			tabEditor.setName(value.map(tabName.apply(tabModels)));
			if (tabIcon != null)
				tabEditor.setIcon(value.map(tabIcon.apply(tabModels)));
			if (removable != null) {
				Function<T, Boolean> rem = removable.apply(tabModels);
				value.changes().takeUntil(tabModels.getUntil()).act(evt -> {
					tabEditor.setRemovable(rem.apply(evt.getNewValue()));
				});
				if (onRemove != null) {
					Consumer<T> or = onRemove.apply(tabModels);
					tabEditor.onRemove(v -> or.accept((T) v));
				}
			}
			if (selectOn != null) {
				Function<T, Observable<?>> so = selectOn.apply(tabModels);
				tabEditor.selectOn(ObservableValue.flattenObservableValue(value.map(so)));
			}
			if (onSelect != null) {
				Consumer<T> os = onSelect.apply(tabModels);
				tabEditor.onSelect(v -> os.accept((T) v));
			}
		}

		static <T> MultiTabSet<T> parse(StyleQIS tabSet) throws QonfigInterpretationException {
			ExpressoQIS exTabSet = tabSet.as(ExpressoQIS.class);
			ValueContainer<ObservableCollection<?>, ObservableCollection<T>> values = exTabSet.getAttributeAsCollection("values",
				(TypeToken<T>) TypeTokens.get().WILDCARD, null);
			String renderValueName = tabSet.getAttributeText("render-value-name");
			ObservableModelSet.WrappedBuilder wb = exTabSet.getExpressoEnv().getModels().wrap();
			ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<T>> tvp = wb.withRuntimeValue(renderValueName,
				ModelTypes.Value.forType((TypeToken<T>) values.getType().getType(0)));
			exTabSet.setModels(wb.build(), null);

			QuickComponentDef content = tabSet.interpretChildren("renderer", QuickComponentDef.class).getFirst();

			ObservableExpression tabNameX = exTabSet.getAttributeExpression("tab-name");
			Function<ModelSetInstance, Function<T, String>> tabName;
			if (tabNameX == null)
				tabName = msi -> String::valueOf;
				else
					tabName = tabNameX.<T, Object, Object, String> findMethod(String.class, exTabSet.getExpressoEnv()).withOption0()
					.withOption1((TypeToken<T>) values.getType().getType(0), a -> a)//
					.find1();

			ObservableExpression tabIconX = exTabSet.getAttributeExpression("tab-icon");
			Function<ModelSetInstance, Function<T, Icon>> tabIcon;
			if (tabIconX == null)
				tabIcon = null;
			else {
				MethodFinder<T, Object, Object, Object> finder = tabIconX
					.<T, Object, Object, Object> findMethod((TypeToken<Object>) TypeTokens.get().WILDCARD, exTabSet.getExpressoEnv())
					.withOption0().withOption1((TypeToken<T>) values.getType().getType(0), a -> a);
				Function<ModelSetInstance, Function<T, Object>> iconFn = finder.find1();
				Class<?> iconType = TypeTokens.getRawType(finder.getResultType());
				if (Icon.class.isAssignableFrom(iconType))
					tabIcon = (Function<ModelSetInstance, Function<T, Icon>>) (Function<?, ?>) iconFn;
				else if (Image.class.isAssignableFrom(iconType)) {
					tabIcon = msi -> {
						Function<T, Image> imgFn = (Function<T, Image>) (Function<?, ?>) iconFn.apply(msi);
						return tabValue -> {
							Image img = imgFn.apply(tabValue);
							if (img == null)
								return null;
							return new ImageIcon(img); // There's probably a better way to do this, but this is what I know
						};
					};
				} else if (iconType == URL.class) {
					tabIcon = msi -> {
						Function<T, URL> urlFn = (Function<T, URL>) (Function<?, ?>) iconFn.apply(msi);
						return tabValue -> {
							URL url = urlFn.apply(tabValue);
							if (url == null)
								return null;
							return new ImageIcon(url); // There's probably a better way to do this, but this is what I know
						};
					};
				} else if (iconType == String.class) {
					tabIcon = msi -> {
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
					tabIcon = null;
				}
			}

			// TODO This should be a value, with the specified renderValueName available
			ObservableExpression removableX = exTabSet.getAttributeExpression("removable");
			Function<ModelSetInstance, Function<T, Boolean>> removable;
			if (removableX == null)
				removable = null;
			else {
				removable = removableX.<T, Object, Object, Boolean> findMethod(boolean.class, exTabSet.getExpressoEnv())//
					.withOption0().withOption1((TypeToken<T>) values.getType().getType(0), t -> t)//
					.find1();
			}

			// TODO This should be a value, with the specified renderValueName available
			ObservableExpression onRemoveX = exTabSet.getAttributeExpression("on-remove");
			Function<ModelSetInstance, Consumer<T>> onRemove;
			if (onRemoveX == null)
				onRemove = null;
			else {
				onRemove = onRemoveX.<T, Object, Object, Void> findMethod(Void.class, exTabSet.getExpressoEnv())//
					.withOption0().withOption1((TypeToken<T>) values.getType().getType(0), t -> t)//
					.find1().andThen(fn -> t -> fn.apply(t));
			}

			// TODO This should be a value, with the specified renderValueName available
			ObservableExpression selectOnX = exTabSet.getAttributeExpression("select-on");
			Function<ModelSetInstance, Function<T, Observable<?>>> selectOn;
			if (selectOnX == null)
				selectOn = null;
			else {
				selectOn = selectOnX
					.<T, Object, Object, Observable<?>> findMethod(TypeTokens.get().keyFor(Observable.class).<Observable<?>> wildCard(),
						exTabSet.getExpressoEnv())//
					.withOption0().withOption1((TypeToken<T>) values.getType().getType(0), t -> t)//
					.find1();
			}

			// TODO This should be a value, with the specified renderValueName available
			ObservableExpression onSelectX = exTabSet.getAttributeExpression("on-select");
			Function<ModelSetInstance, Consumer<T>> onSelect;
			if (onSelectX == null)
				onSelect = null;
			else
				onSelect = onSelectX.<T, Object, Object, Void> findMethod(Void.class, exTabSet.getExpressoEnv())//
				.withOption0().withOption1((TypeToken<T>) values.getType().getType(0), t -> t)//
				.find1().andThen(fn -> t -> fn.apply(t));

			return new MultiTabSet<>((ObservableModelSet.Wrapped) exTabSet.getExpressoEnv().getModels(), tvp, tabSet.getElement(), values,
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

	private <T> QuickComponentDef interpretTabs(StyleQIS session, QonfigToolkit base) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		List<TabSet<? extends T>> tabs = new ArrayList<>();
		QonfigChildDef.Declared tabSetDef = base.getChild("tabs", "tab-set").getDeclared();
		QonfigChildDef.Declared widgetDef = base.getChild("tabs", "content").getDeclared();
		List<TypeToken<? extends T>> tabTypes = new ArrayList<>();
		for (StyleQIS child : session.forChildren()) {
			if (child.getElement().getDeclaredRoles().contains(tabSetDef)) {
				MultiTabSet<T> tabSet = MultiTabSet.parse(child.asElement("tab-set"));
				tabTypes.add((TypeToken<? extends T>) tabSet.values.getType().getType(0));
				tabs.add(tabSet);
			} else if (child.getElement().getDeclaredRoles().contains(widgetDef)) {
				SingleTab<T> tab = SingleTab.parse(base, child.asElement("tab"));
				tabTypes.add((TypeToken<? extends T>) tab.tabId.getType().getType(0));
				tabs.add(tab);
			}
		}
		TypeToken<T> tabType = TypeTokens.get().getCommonType(tabTypes);
		ObservableExpression selectionX = exS.getAttributeExpression("selected");
		ValueContainer<SettableValue<?>, SettableValue<T>> selection;
		if (selectionX == null)
			selection = null;
		else
			selection = selectionX.evaluate(ModelTypes.Value.forType(TypeTokens.get().getSuperWildcard(tabType)), exS.getExpressoEnv());
		return new AbstractQuickComponentDef(session) {
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

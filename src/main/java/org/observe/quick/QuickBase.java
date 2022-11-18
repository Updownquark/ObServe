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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.DynamicModelValue;
import org.observe.expresso.Expression.ExpressoParseException;
import org.observe.expresso.ExpressoBaseV0_1;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.JavaExpressoParser;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.AbstractValueContainer;
import org.observe.expresso.ObservableModelSet.ExternalModelSetBuilder;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ObservableModelSet.ValueCreator;
import org.observe.expresso.QonfigExpression;
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
import org.observe.util.swing.ObservableTreeModel;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.DataAction;
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
import com.sun.beans.finder.MethodFinder;

public class QuickBase implements QonfigInterpretation {
	public static final String NAME = "Quick-Base";
	public static final Version VERSION = new Version(0, 1, 0);

	private static final String MODEL_TYPE_KEY = "model-value-key";

	public interface Column<R, C> {
		CategoryRenderStrategy<R, C> createColumn(Supplier<ModelSetInstance> modelCreator, BiConsumer<ModelSetInstance, R> configModelValue,
			BiConsumer<ModelSetInstance, ModelCell<? extends R, ? extends C>> configCell,
			ValueContainer<SettableValue<?>, SettableValue<R>> defaultColumnValue);
	}

	public interface ColumnList<R> {
		ObservableCollection<Column<R, ?>> getColumns();
	}

	public interface ColumnEditing<R, C> {
		public void modifyColumn(CategoryRenderStrategy<R, C>.CategoryMutationStrategy mutation, ModelSetInstance models,
			BiConsumer<ModelSetInstance, ModelCell<? extends R, ? extends C>> config);
	}

	public static class ValueAction<T> {
		public final ObservableModelSet model;
		public final BiConsumer<ModelSetInstance, SettableValue<T>> valueInstaller;
		public final BiConsumer<ModelSetInstance, ObservableCollection<T>> valuesInstaller;
		public final ValueContainer<SettableValue<?>, SettableValue<String>> name;
		public final Function<ModelSetInstance, SettableValue<Icon>> icon;
		public final ValueContainer<ObservableAction<?>, ObservableAction<?>> action;
		public final ValueContainer<SettableValue<?>, SettableValue<String>> enabled;
		public final boolean allowForEmpty;
		public final boolean allowForMultiple;
		public final ValueContainer<SettableValue<?>, SettableValue<String>> tooltip;

		public ValueAction(ObservableModelSet model, BiConsumer<ModelSetInstance, SettableValue<T>> valueInstaller,
			BiConsumer<ModelSetInstance, ObservableCollection<T>> valuesInstaller,
			ValueContainer<SettableValue<?>, SettableValue<String>> name, Function<ModelSetInstance, SettableValue<Icon>> icon,
			ValueContainer<ObservableAction<?>, ObservableAction<?>> action,
			ValueContainer<SettableValue<?>, SettableValue<String>> enabled, boolean allowForEmpty, boolean allowForMultiple,
			ValueContainer<SettableValue<?>, SettableValue<String>> tooltip) {
			this.model = model;
			this.valueInstaller = valueInstaller;
			this.valuesInstaller = valuesInstaller;
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

	private QonfigToolkit theToolkit;

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
		theToolkit = toolkit;
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
		.modifyWith("border", QuickBox.class, (box, session, prep) -> modifyBoxBorder(box, wrap(session), base))//
		.modifyWith("simple", QuickBox.class, (box, session, prep) -> modifyBoxSimple(box, wrap(session)))//
		.modifyWith("inline", QuickBox.class, (box, session, prep) -> modifyBoxInline(box, wrap(session)))//
		.createWith("multi-value-action", ValueAction.class, session -> interpretMultiValueAction(wrap(session)))//
		.createWith("label", QuickComponentDef.class, session -> evaluateLabel(wrap(session)))//
		.createWith("text-field", QuickComponentDef.class, session -> interpretTextField(wrap(session)))//
		.modifyWith("editable-text-widget", QuickComponentDef.class, (comp, session, prep) -> modifyTextEditor(comp, wrap(session)))//
		.createWith("button", QuickComponentDef.class, session -> interpretButton(wrap(session)))//
		.createWith("table", QuickComponentDef.class, session -> interpretTable(wrap(session)))//
		.createWith("column", Column.class, session -> interpretColumn(wrap(session), base))//
		.createWith("modify-row-value", ColumnEditing.class, session -> interpretRowModify(wrap(session)))//
		// .createWith("replace-row-value", ColumnEditing.class, session->interpretRowReplace(wrap(session))) TODO
		.createWith("columns", ValueCreator.class, session -> interpretColumns(wrap(session)))//
		.createWith("split", QuickComponentDef.class, session -> interpretSplit(wrap(session)))//
		.createWith("field-panel", QuickComponentDef.class, session -> interpretFieldPanel(wrap(session)))//
		.modifyWith("field", QuickComponentDef.class, (field, session, prep) -> modifyField(field, wrap(session)))//
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
		for (ObservableModelSet.ModelComponentNode<?, ?> component : models.getComponents().values()) {
			Object thing = component.getThing();
			String key = component.getIdentity().getName();
			if (thing instanceof ObservableModelSet.ExtValueRef) {
				extValues.put(key, session.getExpressoEnv().getModels().getValue(path + "." + key, component.getType()));
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
			ValueContainer<SettableValue<?>, SettableValue<QuickPosition>> leftC = QuickCore
				.parsePosition(child.getAttribute("left", QonfigExpression.class), child);
			ValueContainer<SettableValue<?>, SettableValue<QuickPosition>> rightC = QuickCore
				.parsePosition(child.getAttribute("right", QonfigExpression.class), child);
			ValueContainer<SettableValue<?>, SettableValue<QuickPosition>> topC = QuickCore
				.parsePosition(child.getAttribute("top", QonfigExpression.class), child);
			ValueContainer<SettableValue<?>, SettableValue<QuickPosition>> bottomC = QuickCore
				.parsePosition(child.getAttribute("bottom", QonfigExpression.class), child);
			ValueContainer<SettableValue<?>, SettableValue<QuickSize>> minWidthC = QuickCore
				.parseSize(child.getAttribute("min-width", QonfigExpression.class), child);
			ValueContainer<SettableValue<?>, SettableValue<QuickSize>> prefWidthC = QuickCore
				.parseSize(child.getAttribute("pref-width", QonfigExpression.class), child);
			ValueContainer<SettableValue<?>, SettableValue<QuickSize>> maxWidthC = QuickCore
				.parseSize(child.getAttribute("max-width", QonfigExpression.class), child);
			ValueContainer<SettableValue<?>, SettableValue<QuickSize>> minHeightC = QuickCore
				.parseSize(child.getAttribute("min-height", QonfigExpression.class), child);
			ValueContainer<SettableValue<?>, SettableValue<QuickSize>> prefHeightC = QuickCore
				.parseSize(child.getAttribute("pref-height", QonfigExpression.class), child);
			ValueContainer<SettableValue<?>, SettableValue<QuickSize>> maxHeightC = QuickCore
				.parseSize(child.getAttribute("max-height", QonfigExpression.class), child);
			childComp.modify((ch, builder) -> {
				ObservableValue<QuickPosition> left = leftC == null ? null : leftC.get(builder.getModels());
				ObservableValue<QuickPosition> right = rightC == null ? null : rightC.get(builder.getModels());
				ObservableValue<QuickPosition> top = topC == null ? null : topC.get(builder.getModels());
				ObservableValue<QuickPosition> bottom = bottomC == null ? null : bottomC.get(builder.getModels());
				ObservableValue<QuickSize> minWidth = minWidthC == null ? null : minWidthC.get(builder.getModels());
				ObservableValue<QuickSize> prefWidth = prefWidthC == null ? null : prefWidthC.get(builder.getModels());
				ObservableValue<QuickSize> maxWidth = maxWidthC == null ? null : maxWidthC.get(builder.getModels());
				ObservableValue<QuickSize> minHeight = minHeightC == null ? null : minHeightC.get(builder.getModels());
				ObservableValue<QuickSize> prefHeight = prefHeightC == null ? null : prefHeightC.get(builder.getModels());
				ObservableValue<QuickSize> maxHeight = maxHeightC == null ? null : maxHeightC.get(builder.getModels());
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

	private <T> ValueAction<T> interpretMultiValueAction(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		TypeToken<T> valueType = session.get(MODEL_TYPE_KEY, TypeToken.class);
		// Specify the type here, the value will be specified by the caller
		String valuesName = session.getAttributeText("values-name");
		DynamicModelValue.satisfyDynamicValueType(valuesName, exS.getExpressoEnv().getModels(), ModelTypes.Value.forType(valueType));

		ModelInstanceType<ObservableCollection<?>, ObservableCollection<T>> valuesType = ModelTypes.Collection.forType(valueType);

		ValueContainer<SettableValue<?>, SettableValue<String>> name = exS.getAttribute("name", ModelTypes.Value.forType(String.class),
			null);
		Function<ModelSetInstance, SettableValue<Icon>> icon = QuickCore.parseIcon(//
			exS.getAttributeExpression("icon"), exS, exS.getExpressoEnv());
		ValueContainer<ObservableAction<?>, ObservableAction<?>> action = exS.getAttribute("action", ModelTypes.Action.any(), null);
		ValueContainer<SettableValue<?>, SettableValue<String>> enabled = exS.getAttribute("enabled",
			ModelTypes.Value.forType(String.class), null);
		ValueContainer<SettableValue<?>, SettableValue<String>> tooltip = exS.getAttribute("tooltip",
			ModelTypes.Value.forType(String.class), null);

		return new ValueAction<>(exS.getExpressoEnv().getModels(), null, (models, values) -> {
			DynamicModelValue.satisfyDynamicValue(valuesName, valuesType, models, values);
		}, name, icon, action, enabled, //
			session.getAttribute("allow-for-empty", boolean.class), //
			session.getAttribute("allow-for-multiple", boolean.class), //
			tooltip);
	}

	private <T> QuickComponentDef interpretTextField(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		ValueContainer<SettableValue<?>, SettableValue<T>> value = exS.getAttribute("value",
			(ModelInstanceType<SettableValue<?>, SettableValue<T>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any(), null);
		ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>> type;
		type = (ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>>) value.getType();
		ObservableExpression formatX = exS.getAttributeExpression("format");
		ModelInstanceType.SingleTyped<SettableValue<?>, Format<T>, SettableValue<Format<T>>> formatType;
		formatType = ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<T>> parameterized(type.getValueType()));
		ValueContainer<SettableValue<?>, SettableValue<Format<T>>> format;
		boolean commitOnType = session.getAttribute("commit-on-type", boolean.class);
		String columnsStr = session.getAttributeText("columns");
		int columns = columnsStr == null ? -1 : Integer.parseInt(columnsStr);
		if (formatX != null) {
			format = formatX.evaluate(formatType, exS.getExpressoEnv());
		} else {
			Class<T> rawType = TypeTokens.get().unwrap(TypeTokens.getRawType(type.getValueType()));
			Format<T> f;
			if (rawType == String.class)
				f = (Format<T>) SpinnerFormat.NUMERICAL_TEXT;
			else if (rawType == int.class)
				f = (Format<T>) SpinnerFormat.INT;
			else if (rawType == long.class)
				f = (Format<T>) SpinnerFormat.LONG;
			else if (rawType == double.class)
				f = (Format<T>) Format.doubleFormat(4).build();
			else if (rawType == float.class)
				f = (Format<T>) Format.doubleFormat(4).buildFloat();
			else if (rawType == Instant.class)
				f = (Format<T>) SpinnerFormat.flexDate(Instant::now, "EEE MMM dd, yyyy", null);
			else if (rawType == Duration.class)
				f = (Format<T>) SpinnerFormat.flexDuration(false);
			else
				throw new QonfigInterpretationException(
					"No default format available for type " + value.getType().getType(0) + " -- format must be specified");
			format = ValueContainer.literal(formatType, f, rawType.getSimpleName());
		}
		ValueContainer<SettableValue<?>, SettableValue<String>> disabled = exS.getAttribute("disable-with", ModelTypes.Value.STRING, null);
		return new AbstractQuickValueEditor(session) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				SettableValue<T> realValue = value.get(builder.getModels());
				if (disabled != null)
					realValue = realValue.disableWith(disabled.get(builder.getModels()));
				ObservableValue<String> fieldName;
				if (getFieldName() != null)
					fieldName = getFieldName().apply(builder.getModels());
				else
					fieldName = null;
				container.addTextField(fieldName == null ? null : fieldName.get(), //
					realValue, format.get(builder.getModels()).get(), field -> {
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

	private <T> QuickComponentDef interpretTextArea(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		ValueContainer<SettableValue<?>, SettableValue<T>> value = exS.getAttribute("value",
			(ModelInstanceType<SettableValue<?>, SettableValue<T>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any(), null);
		ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>> type;
		type = (ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>>) value.getType();
		ObservableExpression formatX = exS.getAttributeExpression("format");
		ModelInstanceType.SingleTyped<SettableValue<?>, Format<T>, SettableValue<Format<T>>> formatType;
		formatType = ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<T>> parameterized(type.getValueType()));
		ValueContainer<SettableValue<?>, SettableValue<Format<T>>> format;
		if (formatX != null) {
			format = formatX.evaluate(formatType, exS.getExpressoEnv());
		} else {
			Class<T> rawType = TypeTokens.get().unwrap(TypeTokens.getRawType(type.getValueType()));
			Format<T> f;
			if (rawType == String.class)
				f = (Format<T>) SpinnerFormat.NUMERICAL_TEXT;
			else if (rawType == int.class)
				f = (Format<T>) SpinnerFormat.INT;
			else if (rawType == long.class)
				f = (Format<T>) SpinnerFormat.LONG;
			else if (rawType == double.class)
				f = (Format<T>) Format.doubleFormat(4).build();
			else if (rawType == float.class)
				f = (Format<T>) Format.doubleFormat(4).buildFloat();
			else if (rawType == Instant.class)
				f = (Format<T>) SpinnerFormat.flexDate(Instant::now, "EEE MMM dd, yyyy", null);
			else if (rawType == Duration.class)
				f = (Format<T>) SpinnerFormat.flexDuration(false);
			else
				throw new QonfigInterpretationException(
					"No default format available for type " + value.getType().getType(0) + " -- format must be specified");
			format = ValueContainer.literal(formatType, f, rawType.getSimpleName());
		}
		ValueContainer<SettableValue<?>, SettableValue<Integer>> rows = exS.getAttribute("rows", ModelTypes.Value.forType(Integer.class),
			null);
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> html = exS.getAttribute("html", ModelTypes.Value.forType(Boolean.class),
			null);
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> editable = exS.getAttribute("editable",
			ModelTypes.Value.forType(Boolean.class), null);
		ValueContainer<SettableValue<?>, SettableValue<String>> disabled = exS.getAttribute("disable-with", ModelTypes.Value.STRING, null);
		return new AbstractQuickField(session) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				SettableValue<T> realValue = value.get(builder.getModels());
				if (disabled != null)
					realValue = realValue.disableWith(disabled.get(builder.getModels()));
				ObservableValue<String> fieldName;
				if (getFieldName() != null)
					fieldName = getFieldName().apply(builder.getModels());
				else
					fieldName = null;
				container.addTextArea(fieldName == null ? null : fieldName.get(), realValue, format.get(builder.getModels()).get(),
					field -> {
						modify(field, builder);
						if (rows != null) {
							SettableValue<Integer> rowsV = rows.get(builder.getModels());
							rowsV.changes().act(evt -> {
								field.getEditor().withRows(evt.getNewValue());
							});
						}
						if (html != null) {
							SettableValue<Boolean> htmlV = html.get(builder.getModels());
							htmlV.changes().act(evt -> {
								field.getEditor().asHtml(evt.getNewValue());
							});
						}
						if (editable != null) {
							SettableValue<Boolean> editableV = editable.get(builder.getModels());
							editableV.changes().act(evt -> {
								field.getEditor().setEditable(evt.getNewValue());
							});
						}
					});
				return builder.build();
			}
		};
	}

	private QuickComponentDef modifyTextEditor(QuickComponentDef component, StyleQIS session) throws QonfigInterpretationException {
		component.modify((ce, cb) -> ce.modifyComponent(comp -> {
			DynamicModelValue.satisfyDynamicValue("error", ModelTypes.Value.forType(String.class), cb.getModels(),
				SettableValue.asSettable(((ObservableTextEditorWidget<?, ?>) comp).getErrorState(), __ -> "Not Settable"));
			DynamicModelValue.satisfyDynamicValue("warning", ModelTypes.Value.forType(String.class), cb.getModels(),
				SettableValue.asSettable(((ObservableTextEditorWidget<?, ?>) comp).getWarningState(), __ -> "Not Settable"));
		}));
		return component;
	}

	private QuickComponentDef interpretButton(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		ValueContainer<SettableValue<?>, SettableValue<String>> buttonText;
		ObservableExpression valueX = exS.getValueExpression();
		if (valueX == null) {
			String txt = session.getValueText();
			if (txt == null)
				throw new IllegalArgumentException("Either 'text' attribute or element value must be specified");
			buttonText = ValueContainer.literal(TypeTokens.get().STRING, txt, txt);
		} else
			buttonText = valueX.evaluate(ModelTypes.Value.forType(String.class), exS.getExpressoEnv());
		ValueContainer<ObservableAction<?>, ? extends ObservableAction<?>> action = exS.getExpressoEnv().getModels()
			.getValue(session.getAttributeText("action"), ModelTypes.Action.any());
		return new AbstractQuickComponentDef(session) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				ObservableValue<String> text = buttonText.get(builder.getModels());
				container.addButton(text.get(), //
					action.get(builder.getModels()), //
					btn -> {
						modify(btn, builder);
						btn.withText(text);
					});
				return builder.build();
			}
		};
	}

	private <M, C> Column<M, C> interpretColumn(StyleQIS session, QonfigToolkit base) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		ValueContainer<SettableValue<?>, SettableValue<C>> columnValue = exS.getAttribute("value",
			(ModelInstanceType<SettableValue<?>, SettableValue<C>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any(), null);
		TypeToken<M> modelType = session.get(MODEL_TYPE_KEY, TypeToken.class);
		TypeToken<C> columnType = (TypeToken<C>) (columnValue != null ? columnValue.getType().getType(0) : modelType);
		if (columnValue != null)
			DynamicModelValue.satisfyDynamicValue("columnValue", exS.getExpressoEnv().getModels(), () -> columnValue);
		else
			DynamicModelValue.satisfyDynamicValueType("columnValue", exS.getExpressoEnv().getModels(),
				ModelTypes.Value.forType(columnType));
		String name = session.getAttributeText("name");

		QuickComponentDef renderer = session.forChildren("renderer", base.getElement("label"), null).getFirst()
			.interpret(QuickComponentDef.class);
		ValueContainer<SettableValue<?>, SettableValue<String>> tooltip = exS.getAttribute("tooltip",
			ModelTypes.Value.forType(String.class), null);
		ColumnEditing<M, C> editing;
		ExpressoQIS columnEdit = exS.forChildren("edit").peekFirst();
		if (columnEdit != null) {
			columnEdit.put(MODEL_TYPE_KEY, columnType);
			ExpressoQIS editorSession = columnEdit.forChildren("editor").getFirst();
			QuickComponentDef editor = editorSession.interpret(QuickComponentDef.class);
			if (!(editor instanceof QuickValueEditorDef))
				throw new IllegalArgumentException(
					"Use of '" + editorSession.getElement().getType().getName() + "' as a column editor is not implemented");
			ColumnEditing<M, C> editType = columnEdit.asElement(columnEdit.getAttribute("type", QonfigAddOn.class))
				.interpret(ColumnEditing.class);
			editing = (column, models, config) -> {
				// Hacky way of synthesizing the component
				Function<C, String>[] filter = new Function[1];
				boolean[] currentlyEditing = new boolean[1];
				SettableValue<C> editorValue = DefaultObservableCellEditor.createEditorValue(columnType, filter,
					builder -> builder.withDescription("columnValue"));

				editType.modifyColumn(column, models, config);
				QuickComponent.Builder editorBuilder = QuickComponent.build(editor, null, models);
				QuickComponent editorComp = editor
					.install(PanelPopulation.populateHPanel(null, new JustifiedBoxLayout(false), models.getUntil()), editorBuilder);
				SettableValue<Boolean> focusV = SettableValue.build(boolean.class).build();
				SettableValue<Boolean> hoveredV = SettableValue.build(boolean.class).build();
				SettableValue<Boolean> selectedV = SettableValue.build(boolean.class).build();
				DynamicModelValue.satisfyDynamicValue("focused", ModelTypes.Value.BOOLEAN, models, focusV);
				DynamicModelValue.satisfyDynamicValue("hovered", ModelTypes.Value.BOOLEAN, models, hoveredV);
				DynamicModelValue.satisfyDynamicValue("selected", ModelTypes.Value.BOOLEAN, models, selectedV);
				Component c = editorComp.getComponent();
				Insets defMargin = c instanceof JTextComponent ? ((JTextComponent) c).getMargin() : null;
				String clicksStr = columnEdit.getAttribute("clicks", String.class);
				int clicks = clicksStr != null ? Integer.parseInt(clicksStr) : ((QuickValueEditorDef) editor).getDefaultEditClicks();
				ObservableCellEditor<M, C> oce = new DefaultObservableCellEditor<M, C>(c, editorValue, //
					new ObservableCellEditor.EditorInstallation<C>() {
					@Override
					public EditorSubscription install(ObservableCellEditor<?, C> cellEditor, Component component,
						Function<C, String> valueFilter, String editTooltip, Function<? super C, String> valueToolTip) {
						ModelCell<M, C> cell = (ModelCell<M, C>) cellEditor.getEditingCell();
						config.accept(models, cell);
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
							((JComponent) c).setToolTipText(editTooltip);
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
		return (modelCreator, configModelValue, configCell, defaultColumnValue) -> {
			ModelSetInstance callerModels = modelCreator.get();
			ModelSetInstance renderModels = exS.getExpressoEnv().getModels().createInstance(callerModels.getUntil()).withAll(callerModels)
				.build();
			SettableValue<C> columnV;
			if (columnValue != null)
				columnV = columnValue.get(renderModels);
			else {
				columnV = (SettableValue<C>) defaultColumnValue.get(renderModels);
				DynamicModelValue.satisfyDynamicValue("columnValue", ModelTypes.Value.forType(columnType), renderModels, columnV);
			}
			CategoryRenderStrategy<M, C> column = new CategoryRenderStrategy<>(name, columnType, mv -> {
				configModelValue.accept(renderModels, mv);
				return columnV.get();
			});
			// Hacky way of synthesizing the component
			QuickComponent.Builder renderBuilder = QuickComponent.build(renderer, null, renderModels);
			QuickComponent renderComp = renderer
				.install(PanelPopulation.populateHPanel(null, new JustifiedBoxLayout(false), renderModels.getUntil()), renderBuilder);
			Component c = renderComp.getComponent();
			Function<ModelCell<? extends M, ? extends C>, String> textRender;
			boolean text = c instanceof TextComponent;
			if (text) {
				textRender = cell -> {
					configCell.accept(renderModels, cell);
					return ((TextComponent) c).getText();
				};
			} else {
				textRender = cell -> String.valueOf(cell.getCellValue());
			}
			SettableValue<Boolean> focusV = SettableValue.build(boolean.class).build();
			SettableValue<Boolean> hoveredV = SettableValue.build(boolean.class).build();
			SettableValue<Boolean> selectedV = SettableValue.build(boolean.class).build();
			DynamicModelValue.satisfyDynamicValue("focused", ModelTypes.Value.BOOLEAN, renderModels, focusV);
			DynamicModelValue.satisfyDynamicValue("hovered", ModelTypes.Value.BOOLEAN, renderModels, hoveredV);
			DynamicModelValue.satisfyDynamicValue("selected", ModelTypes.Value.BOOLEAN, renderModels, selectedV);
			column.withRenderer(new ObservableCellRenderer<M, C>() {
				private List<CellDecorator<M, C>> theDecorators;

				@Override
				public String renderAsText(ModelCell<? extends M, ? extends C> cell) {
					return textRender.apply(cell);
				}

				@Override
				public Component getCellRendererComponent(Component parent, ModelCell<? extends M, ? extends C> cell,
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
					configCell.accept(renderModels, cell);
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
				public ObservableCellRenderer<M, C> decorate(CellDecorator<M, C> decorator) {
					if (theDecorators == null)
						theDecorators = new ArrayList<>(3);
					theDecorators.add(decorator);
					return this;
				}
			});
			if (editing != null) {
				ModelSetInstance editModels = modelCreator.get();
				SettableValue<C> editColumnV;
				if (columnValue != null)
					editColumnV = columnValue.get(editModels);
				else {
					editColumnV = (SettableValue<C>) defaultColumnValue.get(editModels);
					DynamicModelValue.satisfyDynamicValue("columnValue", ModelTypes.Value.forType(columnType), editModels, editColumnV);
				}
				column.withMutation(mut -> editing.modifyColumn(mut, editModels, configCell));
			}
			if (tooltip != null) {
				SettableValue<String> tooltipV = tooltip.get(renderModels);
				column.withCellTooltip(cell -> {
					configCell.accept(renderModels, cell);
					return tooltipV.get();
				});
			}
			return column;
		};
	}

	private <M, C> ColumnEditing<M, C> interpretRowModify(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		System.err.println("WARNING: modify-row-value is not fully implemented!!"); // TODO
		TypeToken<M> modelType = session.get(MODEL_TYPE_KEY, TypeToken.class);
		String editValueName = (String) session.get("edit-value-name");
		ObservableExpression commitX = exS.getAttributeExpression("commit");
		ObservableExpression editableIfX = exS.getAttributeExpression("editable-if");
		ObservableExpression acceptX = exS.getAttributeExpression("accept");
		ValueContainer<ObservableAction<?>, ObservableAction<?>> commit = commitX.evaluate(ModelTypes.Action.any(), exS.getExpressoEnv());
		boolean rowUpdate = session.getAttribute("row-update", boolean.class);
		return (column, models, modelValueName) -> { // TODO Not done here
			ObservableAction<?> commitAction = commit.get(models);
			column.mutateAttribute((row, cell) -> commitAction.act(null));
		};
	}

	private <M> ValueCreator<ObservableCollection<?>, ObservableCollection<CategoryRenderStrategy<M, ?>>> interpretColumns(StyleQIS session)
		throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		TypeToken<M> rowType = (TypeToken<M>) ExpressoBaseV0_1.parseType(session.getAttributeText("type"), exS.getExpressoEnv());
		session.put(MODEL_TYPE_KEY, rowType);
		TypeToken<CategoryRenderStrategy<M, ?>> columnType = TypeTokens.get().keyFor(CategoryRenderStrategy.class)
			.<CategoryRenderStrategy<M, ?>> parameterized(rowType, TypeTokens.get().WILDCARD);
		List<Column<M, ?>> columns = session.interpretChildren("column", Column.class);
		return () -> new AbstractValueContainer<ObservableCollection<?>, ObservableCollection<CategoryRenderStrategy<M, ?>>>(
			ModelTypes.Collection.forType(columnType)) {
			@Override
			public ObservableCollection<CategoryRenderStrategy<M, ?>> get(ModelSetInstance models) {
				List<CategoryRenderStrategy<M, ?>> columnInstances = new ArrayList<>(columns.size());
				for (Column<M, ?> column : columns)
					columnInstances.add(column.createColumn(models));
				return ObservableCollection.of(columnType, columnInstances);
			}

			@Override
			public BetterList<ValueContainer<?, ?>> getCores() {
				return BetterList.of(this);
			}
		};
	}

	private QuickComponentDef interpretSplit(StyleQIS session) throws QonfigInterpretationException {
		if (session.getChildren("content").size() != 2)
			throw new UnsupportedOperationException("Currently only 2 (and exactly 2) contents are supported for split");
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		List<QuickComponentDef> children = session.interpretChildren("content", QuickComponentDef.class);
		boolean vertical = session.getAttributeText("orientation").equals("vertical");
		ValueContainer<SettableValue<?>, SettableValue<QuickPosition>> splitPos = QuickCore
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
							SettableValue<QuickPosition> pos = splitPos.get(builder.getModels());
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
			field.setFieldName(fieldName.evaluate(ModelTypes.Value.forType(String.class), exS.getExpressoEnv())::get);
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

	private QuickComponentDef interpretCheckBox(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> value;
		value = exS.getAttribute("value", ModelTypes.Value.forType(boolean.class), null);
		ValueContainer<SettableValue<?>, SettableValue<String>> text = exS.getValue(ModelTypes.Value.forType(String.class), null);
		ValueContainer<SettableValue<?>, SettableValue<String>> disabled = exS.getAttribute("disable-with", ModelTypes.Value.STRING, null);
		return new AbstractQuickValueEditor(session) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				SettableValue<Boolean> realValue = value.get(builder.getModels());
				if (disabled != null)
					realValue = realValue.disableWith(disabled.get(builder.getModels()));
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
		ValueContainer<SettableValue<?>, SettableValue<String>> disabled = exS.getAttribute("disable-with", ModelTypes.Value.STRING, null);
		return new AbstractQuickField(session) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				SettableValue<Object> realValue = value.get(builder.getModels());
				if (disabled != null)
					realValue = realValue.disableWith(disabled.get(builder.getModels()));
				container.addRadioField(null, realValue, //
					values.get(builder.getModels()).get(), //
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
			file = ((ValueContainer<SettableValue<?>, SettableValue<File>>) value)::get;
		else if (BetterFile.class.isAssignableFrom(valueType)) {
			file = msi -> {
				SettableValue<BetterFile> betterFile = ((ValueContainer<SettableValue<?>, SettableValue<BetterFile>>) value).get(msi);
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
		ValueContainer<SettableValue<?>, SettableValue<String>> disabled = exS.getAttribute("disable-with", ModelTypes.Value.STRING, null);
		return new AbstractQuickValueEditor(session) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				SettableValue<File> realValue = file.apply(builder.getModels());
				if (disabled != null)
					realValue = realValue.disableWith(disabled.get(builder.getModels()));
				container.addFileField(null, realValue, open, fb -> {
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

	private <T> QuickComponentDef evaluateLabel(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		Function<ModelSetInstance, ? extends SettableValue<T>> value;
		TypeToken<T> valueType;
		ObservableExpression valueEx = exS.getAttributeExpression("value");
		ValueContainer<SettableValue<?>, SettableValue<T>> valueX;
		if (valueEx != null && valueEx != ObservableExpression.EMPTY)
			valueX = valueEx.evaluate(
				(ModelInstanceType<SettableValue<?>, SettableValue<T>>) (ModelInstanceType<?, ?>) ModelTypes.Value.any(),
				exS.getExpressoEnv());
		else if (session.getElement().getValue() == null) {
			session.withWarning("No value for label");
			valueX = (ValueContainer<SettableValue<?>, SettableValue<T>>) (ValueContainer<?, ?>) ValueContainer
				.literal(ModelTypes.Value.forType(String.class), "", "");
		} else
			valueX = (ValueContainer<SettableValue<?>, SettableValue<T>>) (ValueContainer<?, ?>) ValueContainer
			.literal(ModelTypes.Value.forType(String.class), session.getValueText(), session.getValueText());
		ObservableExpression formatX = exS.getAttributeExpression("format");
		Function<ModelSetInstance, SettableValue<Format<T>>> format;
		if (valueX == null) {
			if (formatX != null && formatX != ObservableExpression.EMPTY)
				System.err.println("Warning: format specified on label without value");
			String txt = session.getValueText();
			if (txt == null)
				throw new IllegalArgumentException("Either 'text' or 'value' must be specified");
			value = __ -> (SettableValue<T>) ObservableModelSet.literal(txt, txt);
			format = __ -> ObservableModelSet.literal((Format<T>) Format.TEXT, "<unspecified>");
		} else {
			value = valueX::get;
			valueType = (TypeToken<T>) valueX.getType().getType(0);
			if (formatX == null || formatX == ObservableExpression.EMPTY) {
				format = __ -> ObservableModelSet.literal((Format<T>) LABEL_FORMAT, "<unspecified>");
			} else
				format = formatX.evaluate(
					ModelTypes.Value
					.forType(TypeTokens.get().keyFor(Format.class).<Format<T>> parameterized(TypeTokens.get().wrap(valueType))),
					exS.getExpressoEnv())::get;
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
				SettableValue<T> valueV = value.apply(builder.getModels());
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

	private <R> QuickComponentDef interpretTable(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		ValueContainer<ObservableCollection<?>, ? extends ObservableCollection<R>> rows;
		rows = (ValueContainer<ObservableCollection<?>, ? extends ObservableCollection<R>>) exS.getAttribute("rows",
			ModelTypes.Collection.any(), null);
		TypeToken<R> modelType = (TypeToken<R>) rows.getType().getType(0);
		session.put(MODEL_TYPE_KEY, modelType);

		String valueName = exS.getAttributeText("value-name");
		DynamicModelValue.satisfyDynamicValueType(valueName, exS.getExpressoEnv().getModels(), ModelTypes.Value.forType(modelType));
		ValueContainer<SettableValue<?>, SettableValue<R>> modelValue = exS.getExpressoEnv().getModels().getValue(valueName,
			ModelTypes.Value.forType(modelType));

		ValueContainer<SettableValue<?>, SettableValue<R>> selectionV;
		ValueContainer<ObservableCollection<?>, ObservableCollection<R>> selectionC;
		ObservableExpression selectionS = exS.getAttributeExpression("selection");
		if (selectionS != null) {
			ValueContainer<?, ?> selection = selectionS.evaluate(null, exS.getExpressoEnv());
			ModelType<?> type = selection.getType().getModelType();
			if (type == ModelTypes.Value) {
				selectionV = (ValueContainer<SettableValue<?>, SettableValue<R>>) selection;
				selectionC = null;
				// TODO Test selection value type
			} else if (type == ModelTypes.Collection || type == ModelTypes.SortedCollection) {
				selectionV = null;
				selectionC = (ValueContainer<ObservableCollection<?>, ObservableCollection<R>>) selection;
				// TODO Test selection value type
			} else
				throw new IllegalArgumentException(
					"Model value " + selectionS + " is of type " + type + "--only Value, Collection, and SortedCollection supported");
		} else {
			selectionV = null;
			selectionC = null;
		}
		ValueContainer<SettableValue<?>, SettableValue<Integer>> rowIndex = exS.getExpressoEnv().getModels().getValue("rowIndex",
			ModelTypes.Value.INT);
		ValueContainer<SettableValue<?>, SettableValue<Integer>> columnIndex = exS.getExpressoEnv().getModels().getValue("columnIndex",
			ModelTypes.Value.INT);
		TypeToken<Column<R, ?>> columnType = TypeTokens.get().keyFor(Column.class).<Column<R, ?>> parameterized(modelType,
			TypeTokens.get().WILDCARD);
		List<ObservableCollection<Column<R, ?>>> columns = new ArrayList<>();
		List<Column<R, ?>> simpleColumns = null;
		for (ExpressoQIS child : exS.forChildren()) {
			if (child.supportsInterpretation(Column.class)) {
				if (simpleColumns == null)
					simpleColumns = new ArrayList<>();
				simpleColumns.add(child.interpret(Column.class));
			} else if (child.supportsInterpretation(ColumnList.class)) {
				if (simpleColumns != null) {
					columns.add(ObservableCollection.of(columnType, simpleColumns));
					simpleColumns = null;
				}
				columns.add(child.interpret(ColumnList.class).getColumns());
			}
		}
		List<ValueAction<R>> actions = session.interpretChildren("action", ValueAction.class);
		return new AbstractQuickComponentDef(session) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				Function<Column<R, ?>, CategoryRenderStrategy<R, ?>> columnEval = col -> col.createColumn(() -> {
					ModelSetInstance columnModels = builder.getModels().copy().build();
					SettableValue<R> modelValueI = SettableValue.build(modelType).build();
					DynamicModelValue.satisfyDynamicValue(valueName, ModelTypes.Value.forType(modelType), columnModels, modelValueI);
					DynamicModelValue.satisfyDynamicValue("rowIndex", ModelTypes.Value.INT, columnModels,
						SettableValue.build(int.class).withValue(-1).build());
					DynamicModelValue.satisfyDynamicValue("columnIndex", ModelTypes.Value.INT, columnModels,
						SettableValue.build(int.class).withValue(-1).build());
					return columnModels;
				}, (columnModels, modelValueV) -> {
					modelValue.get(columnModels).set(modelValueV, null);
				}, (columnModels, cell) -> {
					modelValue.get(columnModels).set(cell.getModelValue(), null);
					rowIndex.get(columnModels).set(cell.getRowIndex(), null);
					columnIndex.get(columnModels).set(cell.getColumnIndex(), null);
				}, modelValue);
				ObservableCollection<CategoryRenderStrategy<R, ?>> tableColumns = ObservableCollection.flattenCollections(columnType, //
					columns.toArray(new ObservableCollection[columns.size()]))//
					.map(CategoryRenderStrategy.class, columnEval)//
					.collect();
				container.addTable(rows.get(builder.getModels()), table -> {
					modify(table, builder);
					if (selectionV != null)
						table.withSelection(selectionV.get(builder.getModels()), false);
					if (selectionC != null)
						table.withSelection(selectionC.get(builder.getModels()));
					table.withColumns(tableColumns);
					for (ValueAction<R> action : actions) {
						ModelSetInstance actionModels = action.model.createInstance(builder.getModels().getUntil())
							.withAll(builder.getModels()).build();
						ObservableAction<?> a = action.action.get(actionModels);
						Consumer<DataAction<R, ?>> actionMod = configAction -> {
							configAction.allowForEmpty(action.allowForEmpty).allowForMultiple(action.allowForMultiple);
							configAction.modifyButton(btn -> {
								if (action.name != null)
									btn.withText(action.name.get(builder.getModels()));
								if (action.icon != null)
									btn.withIcon(action.icon.apply(builder.getModels()));
								if (action.enabled != null)
									btn.disableWith(action.enabled.get(builder.getModels()));
							});
						};
						if (action.valuesInstaller != null) {
							ObservableCollection<R> actionValues = ObservableCollection.build(modelType).build();
							action.valuesInstaller.accept(actionModels, actionValues.flow().unmodifiable(false).collect());
							table.withMultiAction(null, values -> {
								actionValues.clear();
								actionValues.addAll(values);
								a.act(null);
							}, actionMod);
						} else {
							SettableValue<R> actionValue = SettableValue.build(modelType).build();
							action.valueInstaller.accept(actionModels, actionValue.disableWith(SettableValue.ALWAYS_DISABLED));
							table.withAction(null, value -> {
								actionValue.set(value, null);
								a.act(null);
							}, actionMod);
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

	private static final ObservableExpression TREE_VALUE_EXPRESSION;

	static {
		ObservableExpression tve;
		try {
			tve = new JavaExpressoParser().parse("path==null ? null : path.peekLast()");
		} catch (ExpressoParseException e) {
			System.err.println("Could not parse tree value expression:");
			e.printStackTrace();
			tve = null;
		}
		TREE_VALUE_EXPRESSION = tve;
	}

	public static <T, E extends PanelPopulation.AbstractTreeEditor<T, ?, E>> QuickComponentDef interpretAbstractTree(StyleQIS session,
		TreeMaker<T, E> treeMaker) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		System.err.println("WARNING: " + session.getElement().getType().getName() + " is not fully implemented!!"); // TODO
		ValueContainer<SettableValue<?>, ? extends SettableValue<T>> root = (ValueContainer<SettableValue<?>, ? extends SettableValue<T>>) exS
			.getAttribute("root", ModelTypes.Value.any(), null);
		TypeToken<T> valueType = (TypeToken<T>) root.getType().getType(0);
		TypeToken<BetterList<T>> pathType = TypeTokens.get().keyFor(BetterList.class).<BetterList<T>> parameterized(valueType);
		String pathName = exS.getAttributeText("path-name");
		String valueName = exS.getAttributeText("value-name");
		DynamicModelValue.satisfyDynamicValueType(pathName, exS.getExpressoEnv().getModels(), ModelTypes.Value.forType(pathType));
		DynamicModelValue.satisfyDynamicValue(valueName, exS.getExpressoEnv().getModels(), () -> {
			try {
				return TREE_VALUE_EXPRESSION.evaluate(//
					ModelTypes.Value.forType(valueType), exS.getExpressoEnv());
			} catch (QonfigInterpretationException e) {
				e.printStackTrace();
				return null;
			}
		});
		session.put(MODEL_TYPE_KEY, valueType);

		ValueContainer<SettableValue<?>, SettableValue<BetterList<T>>> path = exS.getExpressoEnv().getModels().getValue(pathName,
			ModelTypes.Value.forType(pathType));
		ValueContainer<SettableValue<?>, SettableValue<T>> treeNodeValue = exS.getExpressoEnv().getModels().getValue(valueName,
			ModelTypes.Value.forType(valueType));
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> selected = exS.getExpressoEnv().getModels().getValue("selected",
			ModelTypes.Value.BOOLEAN);
		// ValueContainer<SettableValue<?>, SettableValue<Boolean>> focused = exS.getExpressoEnv().getModels().getValue("focused",
		// ModelTypes.Value.BOOLEAN);
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> hovered = exS.getExpressoEnv().getModels().getValue("hovered",
			ModelTypes.Value.BOOLEAN);
		ValueContainer<ObservableCollection<?>, ObservableCollection<T>> children = exS.getAttribute("children",
			ModelTypes.Collection.forType(valueType), null);
		Column<BetterList<T>, T> treeColumn = session.interpretChildren("tree-column", Column.class).peekFirst();
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
			ValueContainer<?, ?> selection = selectionEx.evaluate(null, exS.getExpressoEnv());
			ValueContainer<Object, Object> hackS = (ValueContainer<Object, Object>) selection;
			if (selection.getType().getModelType() == ModelTypes.Value) {
				multiSelectionV = null;
				multiSelectionPath = null;
				if (TypeTokens.get().isAssignable(valueType, selection.getType().getType(0))) {
					singleSelectionPath = null;
					singleSelectionV = hackS.as(ModelTypes.Value.forType(valueType));
				} else if (TypeTokens.get().isAssignable(pathType, selection.getType().getType(0))) {
					singleSelectionV = null;
					singleSelectionPath = hackS.as(ModelTypes.Value.forType(pathType));
				} else
					throw new QonfigInterpretationException(
						"Value " + selectionEx + ", type " + selection.getType() + " cannot be used for tree selection");
			} else if (selection.getType().getModelType() == ModelTypes.Collection
				|| selection.getType().getModelType() == ModelTypes.SortedCollection//
				|| selection.getType().getModelType() == ModelTypes.Set//
				|| selection.getType().getModelType() == ModelTypes.SortedSet) {
				singleSelectionV = null;
				singleSelectionPath = null;
				throw new UnsupportedOperationException("Tree multi-selection is not yet implemented");
			} else
				throw new QonfigInterpretationException(
					"Value " + selectionEx + ", type " + selection.getType() + " cannot be used for tree selection");
		}
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> leaf = exS.getAttribute("leaf", ModelTypes.Value.BOOLEAN, null);
		treeMaker.configure(exS.getExpressoEnv().getModels(), root);
		return new AbstractQuickComponentDef(session) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				ModelSetInstance treeDataModels = builder.getModels().copy().build();
				SettableValue<BetterList<T>> pathValue = SettableValue.build(pathType).withDescription("treeRootPath").build();
				DynamicModelValue.satisfyDynamicValue(pathName, ModelTypes.Value.forType(pathType), treeDataModels, pathValue);
				treeMaker.makeTree(//
					builder, container, root.get(builder.getModels()), p -> {
						ModelSetInstance nodeModels = treeDataModels.copy().build();
						DynamicModelValue.satisfyDynamicValue(pathName, ModelTypes.Value.forType(pathType), nodeModels,
							SettableValue.of(pathType, p, "Not modifiable here"));
						return children.get(nodeModels);
					}, tree -> {
						modify(tree.fill().fillV(), builder);
						if (leaf != null) {
							ModelSetInstance leafModel = treeDataModels.copy().build();
							ObservableTreeModel<T> treeModel = (ObservableTreeModel<T>) ((JTree) tree.getEditor()).getModel();
							SettableValue<BetterList<T>> leafPathValue = SettableValue.build(pathType).withDescription(pathName).build();
							DynamicModelValue.satisfyDynamicValue(pathName, ModelTypes.Value.forType(pathType), leafModel, leafPathValue);
							SettableValue<Boolean> leafV = leaf.get(leafModel);
							tree.withLeafTest(value -> {
								leafPathValue.set(treeModel.getBetterPath(value, false), null);
								return leafV.get();
							});
						}
						if (treeColumn != null)
							tree.withRender(treeColumn.createColumn(() -> {
								ModelSetInstance newModel = builder.getModels().copy().build();
								SettableValue<BetterList<T>> newPathValue = SettableValue.build(pathType).withDescription(pathName).build();
								SettableValue<Boolean> selectedV = SettableValue.build(boolean.class).withValue(false)
									.withDescription("selected").build();
								SettableValue<Boolean> hoveredV = SettableValue.build(boolean.class).withValue(false)
									.withDescription("hovered").build();
								DynamicModelValue.satisfyDynamicValue(pathName, ModelTypes.Value.forType(pathType), newModel, newPathValue);
								DynamicModelValue.satisfyDynamicValue("selected", ModelTypes.Value.BOOLEAN, newModel, selectedV);
								DynamicModelValue.satisfyDynamicValue("focused", ModelTypes.Value.BOOLEAN, newModel, selectedV);
								DynamicModelValue.satisfyDynamicValue("hovered", ModelTypes.Value.BOOLEAN, newModel, hoveredV);
								return newModel;
							}, (models, modelValue) -> path.get(models).set(modelValue, null), //
								(models, cell) -> {
									path.get(models).set(cell.getModelValue(), null);
									selected.get(models).set(cell.isSelected(), null);
									hovered.get(models).set(cell.isRowHovered() && cell.isCellHovered(), null);
								}, (ValueContainer<SettableValue<?>, SettableValue<BetterList<T>>>) (ValueContainer<?, ?>) treeNodeValue));
						if (singleSelectionV != null)
							tree.withValueSelection(singleSelectionV.get(builder.getModels()), false);
						else if (singleSelectionPath != null)
							tree.withSelection(singleSelectionPath.get(builder.getModels()), false);
						else if (multiSelectionV != null)
							tree.withValueSelection(multiSelectionV.get(builder.getModels()));
						else if (multiSelectionPath != null)
							tree.withSelection(multiSelectionPath.get(builder.getModels()));
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

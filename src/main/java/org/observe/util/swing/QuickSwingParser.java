package org.observe.util.swing;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.LayoutManager;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.ModelType;
import org.observe.util.ModelTypes;
import org.observe.util.ObservableModelQonfigParser;
import org.observe.util.ObservableModelSet;
import org.observe.util.ObservableModelSet.ExternalModelSet;
import org.observe.util.ObservableModelSet.ModelSetInstance;
import org.observe.util.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpreter.Builder;
import org.qommons.config.QonfigToolkitAccess;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

public class QuickSwingParser {
	public static final QonfigToolkitAccess CORE = new QonfigToolkitAccess(QuickSwingParser.class, "quick-core.qtd",
		ObservableModelQonfigParser.TOOLKIT);
	public static final QonfigToolkitAccess BASE = new QonfigToolkitAccess(QuickSwingParser.class, "quick-base.qtd",
		ObservableModelQonfigParser.TOOLKIT, CORE);
	public static final QonfigToolkitAccess SWING = new QonfigToolkitAccess(QuickSwingParser.class, "quick-swing.qtd",
		ObservableModelQonfigParser.TOOLKIT, CORE);

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

	public interface QuickComponent {
		QonfigElement getElement();

		String getFieldName();

		void setFieldName(String fieldName);

		QuickComponent modify(Consumer<ComponentEditor<?, ?>> modification);

		void install(PanelPopulator<?, ?> container, ModelSetInstance modelSet);
	}

	public static abstract class AbstractQuickComponent implements QuickComponent {
		private final QonfigElement theElement;
		private String theFieldName;
		private Consumer<ComponentEditor<?, ?>> theModifications;

		public AbstractQuickComponent(QonfigElement element) {
			theElement = element;
		}

		@Override
		public QonfigElement getElement() {
			return theElement;
		}

		@Override
		public String getFieldName() {
			return theFieldName;
		}

		@Override
		public void setFieldName(String fieldName) {
			theFieldName = fieldName;
		}

		@Override
		public AbstractQuickComponent modify(Consumer<ComponentEditor<?, ?>> fieldModification) {
			if (theModifications == null)
				theModifications = fieldModification;
			else {
				Consumer<ComponentEditor<?, ?>> old = theModifications;
				theModifications = field -> {
					old.accept(field);
					fieldModification.accept(field);
				};
			}
			return this;
		}

		public void modify(ComponentEditor<?, ?> component) {
			if (theModifications != null)
				theModifications.accept(component);
		}
	}

	public interface QuickContainer extends QuickComponent {
		List<QuickComponent> getChildren();

		void installContainer(PanelPopulator<?, ?> container, ModelSetInstance modelSet, Consumer<PanelPopulator<?, ?>> populator);

		default void populateContainer(PanelPopulator<?, ?> thisContainer, ModelSetInstance modelSet) {
			for (int c = 0; c < getChildren().size(); c++) {
				QuickComponent child = getChildren().get(c);
				child.install(thisContainer, modelSet);
			}
		}

		@Override
		default void install(PanelPopulator<?, ?> container, ModelSetInstance modelSet) {
			installContainer(container, modelSet, thisContainer -> populateContainer(thisContainer, modelSet));
		}
	}

	public interface QuickLayout {
		LayoutManager create();
	}

	public static class QuickBox extends AbstractQuickComponent implements QuickContainer {
		private List<QuickComponent> theChildren;
		private final String theLayoutName;
		private QuickLayout theLayout;

		public QuickBox(QonfigElement element, List<QuickComponent> children, String layoutName) {
			super(element);
			theLayoutName = layoutName;
			theChildren = children;
		}

		public QuickBox setLayout(QuickLayout layout) {
			theLayout = layout;
			return this;
		}

		@Override
		public List<QuickComponent> getChildren() {
			return theChildren;
		}

		@Override
		public void installContainer(PanelPopulator<?, ?> container, ModelSetInstance modelSet, Consumer<PanelPopulator<?, ?>> populator) {
			if (theLayout == null)
				throw new IllegalStateException("No interpreter configured for layout " + theLayoutName);
			container.addHPanel(getFieldName(), theLayout.create(), thisContainer -> {
				modify(thisContainer);
				populator.accept(thisContainer);
			});
		}
	}

	public static class QuickDocument {
		private final QuickHeadSection theHead;
		private final QuickComponent theComponent;
		private String theDefaultTitle;
		private Function<ModelSetInstance, SettableValue<String>> theTitleValue;
		private Function<ModelSetInstance, SettableValue<Icon>> theIcon;
		private Function<ModelSetInstance, SettableValue<Integer>> theX;
		private Function<ModelSetInstance, SettableValue<Integer>> theY;
		private Function<ModelSetInstance, SettableValue<Integer>> theWidth;
		private Function<ModelSetInstance, SettableValue<Integer>> theHeight;
		private Function<ModelSetInstance, SettableValue<Boolean>> isVisible;
		private int theCloseAction = WindowConstants.HIDE_ON_CLOSE;

		public QuickDocument(QuickHeadSection head, QuickComponent component) {
			theHead = head;
			theComponent = component;
		}

		public QuickHeadSection getHead() {
			return theHead;
		}

		public QuickComponent getComponent() {
			return theComponent;
		}

		public String getDefaultTitle() {
			return theDefaultTitle;
		}

		public void setTitle(String defaultTitle) {
			theDefaultTitle = defaultTitle;
		}

		public Function<ModelSetInstance, SettableValue<String>> getTitleValue() {
			return theTitleValue;
		}

		public void setTitle(Function<ModelSetInstance, SettableValue<String>> titleValue) {
			theTitleValue = titleValue;
		}

		public Function<ModelSetInstance, SettableValue<Icon>> getIcon() {
			return theIcon;
		}

		public void setIcon(Function<ModelSetInstance, SettableValue<Icon>> icon) {
			theIcon = icon;
		}

		public Function<ModelSetInstance, SettableValue<Integer>> getX() {
			return theX;
		}

		public Function<ModelSetInstance, SettableValue<Integer>> getY() {
			return theY;
		}

		public Function<ModelSetInstance, SettableValue<Integer>> getWidth() {
			return theWidth;
		}

		public Function<ModelSetInstance, SettableValue<Integer>> getHeight() {
			return theHeight;
		}

		public Function<ModelSetInstance, SettableValue<Boolean>> getVisible() {
			return isVisible;
		}

		public int getCloseAction() {
			return theCloseAction;
		}

		public QuickDocument withBounds(//
			Function<ModelSetInstance, SettableValue<Integer>> x, Function<ModelSetInstance, SettableValue<Integer>> y, //
			Function<ModelSetInstance, SettableValue<Integer>> width, Function<ModelSetInstance, SettableValue<Integer>> height) {
			theX = x;
			theY = y;
			theWidth = width;
			theHeight = height;
			return this;
		}

		public QuickDocument setVisible(Function<ModelSetInstance, SettableValue<Boolean>> visible) {
			isVisible = visible;
			return this;
		}

		public void setCloseAction(int closeAction) {
			theCloseAction = closeAction;
		}

		public QuickUI createUI(ExternalModelSet extModels) {
			return new QuickUI(this, extModels);
		}
	}

	public static class QuickUI {
		private final QuickDocument theDocument;
		private final ExternalModelSet theExternalModels;
		private ModelSetInstance theModels;
		private Observable<?> theUntil;

		private QuickUI(QuickDocument doc, ExternalModelSet extModels) {
			theDocument = doc;
			theExternalModels = extModels;
		}

		public QuickDocument getDocument() {
			return theDocument;
		}

		public QuickUI withUntil(Observable<?> until) {
			if (theUntil != null)
				throw new IllegalStateException("Until has already been installed");
			theUntil = until;
			return this;
		}

		public Observable<?> getUntil() {
			if (theUntil == null)
				theUntil = Observable.empty();
			return theUntil;
		}

		public ModelSetInstance getModels() {
			theModels = theDocument.getHead().getModels().createInstance(theExternalModels, getUntil());
			return theModels;
		}

		public JFrame createFrame() {
			return install(new JFrame());
		}

		public JFrame install(JFrame frame) {
			PanelPopulation.WindowBuilder<JFrame, ?> builder = WindowPopulation.populateWindow(frame, getUntil(), false, false);
			if (theDocument.getTitleValue() != null)
				builder.withTitle(theDocument.getTitleValue().apply(getModels()));
			else if (theDocument.getDefaultTitle() != null)
				builder.withTitle(theDocument.getDefaultTitle());
			if (theDocument.getVisible() != null)
				builder.withVisible(theDocument.getVisible().apply(getModels()));
			if (theDocument.getX() != null)
				builder.withX(theDocument.getX().apply(getModels()));
			if (theDocument.getX() != null)
				builder.withY(theDocument.getY().apply(getModels()));
			if (theDocument.getX() != null)
				builder.withWidth(theDocument.getWidth().apply(getModels()));
			if (theDocument.getX() != null)
				builder.withHeight(theDocument.getHeight().apply(getModels()));
			builder.withCloseAction(theDocument.getCloseAction());
			builder.withHContent(new BorderLayout(), content -> installContent(content));
			builder.run(null);
			return frame;
		}

		public void installContent(Container container) {
			installContent(PanelPopulation.populatePanel(container, getUntil()));
		}

		public void installContent(PanelPopulation.PanelPopulator<?, ?> container) {
			theDocument.getComponent().install(container, getModels());
		}
	}

	public static class QuickHeadSection {
		private final ObservableModelSet theModels;

		QuickHeadSection(ObservableModelSet models) {
			theModels = models;
		}

		public ObservableModelSet getModels() {
			return theModels;
		}
	}

	private final ObservableModelQonfigParser theModelParser;

	public QuickSwingParser() {
		theModelParser = new ObservableModelQonfigParser();
	}

	public Builder configureInterpreter(Builder interpreter) {
		theModelParser.configureInterpreter(interpreter);
		Builder coreInterpreter = interpreter.forToolkit(CORE.get());
		coreInterpreter.createWith("quick", QuickDocument.class, (element, session) -> {
			QuickHeadSection head = session.getInterpreter().interpret(element.getChildrenInRole("head").getFirst(), QuickHeadSection.class);
			session.put("quick-model", head.getModels());
			QuickDocument doc = new QuickDocument(head, //
				session.getInterpreter().interpret(element.getChildrenInRole("root").getFirst(), QuickComponent.class));
			if (element.getAttribute("visible") != null)
				doc.setVisible(head.getModels().get(element.getAttributeText("visible"), ModelTypes.Value.forType(Boolean.class)));
			return doc;
		}).createWith("head", QuickHeadSection.class, (element, session) -> {
			QonfigElement modelsEl = element.getChildrenInRole("models").peekFirst();
			ObservableModelSet model = modelsEl == null ? null : session.getInterpreter().interpret(modelsEl, ObservableModelSet.class);
			return new QuickHeadSection(model);
		}).modifyWith("window", QuickDocument.class, (doc, element, session) -> {
			ObservableModelSet model = doc.getHead().getModels();
			if (element.getAttribute("title-value") != null)
				doc.setTitle(model.get(element.getAttributeText("title-value"), ModelTypes.Value.forType(String.class)));
			if (element.getAttribute("title") != null)
				doc.setTitle(element.getAttributeText("title"));
			String x = element.getAttributeText("x");
			String y = element.getAttributeText("y");
			String w = element.getAttributeText("width");
			String h = element.getAttributeText("height");
			doc.withBounds(//
				x == null ? null : model.get(x, ModelTypes.Value.forType(Integer.class)), //
					y == null ? null : model.get(y, ModelTypes.Value.forType(Integer.class)), //
						w == null ? null : model.get(w, ModelTypes.Value.forType(Integer.class)), //
							h == null ? null : model.get(h, ModelTypes.Value.forType(Integer.class))//
				);
			switch (element.getAttributeText("close-action")) {
			case "do-nothing":
				doc.setCloseAction(WindowConstants.DO_NOTHING_ON_CLOSE);
				break;
			case "hide":
				doc.setCloseAction(WindowConstants.HIDE_ON_CLOSE);
				break;
			case "dispose":
				doc.setCloseAction(WindowConstants.DISPOSE_ON_CLOSE);
				break;
			case "edit":
				doc.setCloseAction(WindowConstants.EXIT_ON_CLOSE);
				break;
			}
			return doc;
		});
		if (BASE.isBuilt() && BASE.isValid() && interpreter.dependsOn(BASE.get()))
			configureBase(interpreter.forToolkit(BASE.get()));
		if (SWING.isBuilt() && SWING.isValid() && interpreter.dependsOn(SWING.get()))
			configureSwing(interpreter.forToolkit(SWING.get()));
		return interpreter;
	}

	void configureBase(Builder interpreter) {
		interpreter.createWith("box", QuickBox.class, (element, session) -> {
			List<QuickComponent> children = new ArrayList<>(5);
			for (QonfigElement child : element.getChildrenInRole("content"))
				children.add(session.getInterpreter().interpret(child, QuickComponent.class));
			return new QuickBox(element, children, //
				element.getAttributeText("layout"));
		})//
		.modifyWith("border-layout", QuickBox.class, (value, element, session) -> {
			value.setLayout(BorderLayout::new);
			for (QuickComponent child : value.getChildren()) {
				String layoutConstraint;
				switch (child.getElement().getAttributeText("region")) {
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
					System.err.println("ERROR: Unrecognized border-layout region: " + child.getElement().getAttributeText("region"));
					layoutConstraint = null;
					break;
				}
				child.modify(ch -> ch.withLayoutConstraints(layoutConstraint));
			}
			return value;
		})//
		.modifyWith("inline-layout", QuickBox.class, (value, element, session) -> {
			JustifiedBoxLayout layout = new JustifiedBoxLayout(element.getAttributeText("orientation").equals("vertical"));
			switch (element.getAttributeText("main-align")) {
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
				System.err.println("Unrecognized main-align: " + element.getAttributeText("main-align"));
			}
			switch (element.getAttributeText("cross-align")) {
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
				System.err.println("Unrecognized cross-align: " + element.getAttributeText("cross-align"));
			}
			return value.setLayout(() -> layout);
		})//
		.createWith("label", QuickComponent.class, (element, session) -> {
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			Function<ModelSetInstance, ? extends SettableValue> value;
			TypeToken<?> valueType;
			String valueStr = element.getAttributeText("value");
			String formatStr = element.getAttributeText("format");
			Function<ModelSetInstance, SettableValue<Format<Object>>> format;
			if (valueStr == null || valueStr.isEmpty()) {
				if (formatStr != null && !formatStr.isEmpty())
					System.err.println("Warning: format specified on label without value");
				String txt = element.getValueText();
				if (txt == null)
					throw new IllegalArgumentException("Either 'text' or 'value' must be specified");
				value = __ -> SettableValue.asSettable(ObservableValue.of(txt), ___ -> "Constant value");
				format = __ -> ObservableModelQonfigParser.literal((Format<Object>) (Format<?>) Format.TEXT, "<unspecified>");
			} else {
				value = model.get(valueStr, ModelTypes.Value);
				valueType = ((ValueContainer<?, ?>) value).getType().getType(0);
				if (formatStr == null || formatStr.isEmpty()) {
					format = __ -> ObservableModelQonfigParser.literal((Format<Object>) (Format<?>) LABEL_FORMAT, "<unspecified>");
				} else
					format = model.get(formatStr, ModelTypes.Value
						.forType(TypeTokens.get().keyFor(Format.class).parameterized(TypeTokens.get().wrap(valueType))));
			}
			return new AbstractQuickComponent(element) {
				@Override
				public void install(PanelPopulator<?, ?> container, ModelSetInstance modelSet) {
					SettableValue<Object> valueV = value.apply(modelSet);
					container.addLabel(getFieldName(), valueV, //
						format.apply(modelSet).get(), field -> {
							modify(field);
						});
				}
			};
		}).createWith("text-field", QuickComponent.class, (element, session) -> {
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			ValueContainer<SettableValue, ?> value;
			String valueStr = element.getAttributeText("value");
			String formatStr = element.getAttributeText("format");
			Function<ModelSetInstance, SettableValue<Format<Object>>> format;
			value = model.get(valueStr, ModelTypes.Value);
			format = model.get(formatStr,
				ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).parameterized(value.getType().getType(0))));
			return new AbstractQuickComponent(element) {
				@Override
				public void install(PanelPopulator<?, ?> container, ModelSetInstance modelSet) {
					container.addTextField(getFieldName(), //
						(SettableValue<Object>) value.apply(modelSet), format.apply(modelSet).get(), field -> modify(field));
				}
			};
		}).createWith("button", QuickComponent.class, (element, session) -> {
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			Function<ModelSetInstance, SettableValue<String>> buttonText;
			String valueStr = element.getAttributeText("text-value");
			if (valueStr != null) {
				String txt = element.getAttributeText("text");
				if (txt == null)
					throw new IllegalArgumentException("Either 'text' or 'text-value' must be specified");
				buttonText = __ -> ObservableModelQonfigParser.literal(txt, txt);
			} else
				buttonText = model.get(valueStr, ModelTypes.Value.forType(String.class));
			Function<ModelSetInstance, ? extends ObservableAction> action = model.get(element.getAttributeText("action"),
				ModelTypes.Action);
			return new AbstractQuickComponent(element) {
				@Override
				public void install(PanelPopulator<?, ?> container, ModelSetInstance modelSet) {
					ObservableValue<String> text = buttonText.apply(modelSet);
					container.addButton(text.get(), //
						action.apply(modelSet), //
						btn -> {
							modify(btn);
							btn.withText(text);
						});
				}
			};
		});
		interpreter.createWith("table", QuickComponent.class, (element, session) -> {
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			ValueContainer<ObservableCollection, ?> rows = model.get(//
				element.getAttributeText("rows"), ModelTypes.Collection);
			Function<ModelSetInstance, SettableValue<Object>> selectionV;
			Function<ModelSetInstance, ObservableCollection<Object>> selectionC;
			String selectionS = element.getAttributeText("selection");
			if (selectionS != null) {
				ModelType<?> type = model.get(selectionS).getType().getModelType();
				if (type == ModelTypes.Value) {
					selectionV = model.get(selectionS, ModelTypes.Value.forType((TypeToken<Object>) rows.getType().getType(0)));
					selectionC = null;
				} else if (type == ModelTypes.Collection || type == ModelTypes.SortedCollection) {
					selectionV = null;
					selectionC = model.get(selectionS, ModelTypes.Collection.forType((TypeToken<Object>) rows.getType().getType(0)));
				} else
					throw new IllegalArgumentException("Model value " + selectionS + " is of type " + type
						+ "--only Value, Collection, and SortedCollection supported");
			} else {
				selectionV = null;
				selectionC = null;
			}
			session.put("model-type", rows.getType().getType(0));
			List<Function<ModelSetInstance, CategoryRenderStrategy<Object, ?>>> columns = new ArrayList<>();
			for (QonfigElement columnEl : element.getChildrenInRole("column"))
				columns.add(session.getInterpreter().interpret(columnEl, Function.class));
			return new AbstractQuickComponent(element) {
				@Override
				public void install(PanelPopulator<?, ?> container, ModelSetInstance modelSet) {
					container.addTable((ObservableCollection<Object>) rows.apply(modelSet), table -> {
						modify(table);
						if (selectionV != null)
							table.withSelection(selectionV.apply(modelSet), false);
						if (selectionC != null)
							table.withSelection(selectionC.apply(modelSet));
						for (Function<ModelSetInstance, CategoryRenderStrategy<Object, ?>> column : columns)
							table.withColumn(column.apply(modelSet));
					});
				}
			};
		}).createWith("column", Function.class, (element, session) -> {
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			TypeToken<Object> modelType = (TypeToken<Object>) session.get("model-type");
			String name = element.getAttributeText("name");
			String valueS = element.getAttributeText("value");
			TypeToken<Object> columnType;
			Function<Object, Object> valueFn;
			if (valueS != null) {
				throw new UnsupportedOperationException("column value function not yet implemented");
			} else {
				valueFn = v -> v;
				columnType = modelType;
			}
			return extModels -> {
				CategoryRenderStrategy<Object, Object> column = new CategoryRenderStrategy<>(name, columnType, valueFn);
				return column;
			};
		});
	}

	private void configureSwing(Builder interpreter) {
		interpreter.modifyWith("quick", QuickDocument.class, (value, element, session) -> {
			String lAndFClass;
			switch (element.getAttributeText("look-and-feel")) {
			case "system":
				lAndFClass = UIManager.getSystemLookAndFeelClassName();
				break;
			case "cross-platform":
				lAndFClass = UIManager.getCrossPlatformLookAndFeelClassName();
				break;
			default:
				lAndFClass = element.getAttributeText("look-and-feel");
				break;
			}
			try {
				UIManager.setLookAndFeel(lAndFClass);
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
				System.err.println("Could not load look-and-feel " + element.getAttributeText("look-and-feel"));
				e.printStackTrace();
			}
			return value;
		});
	}
}

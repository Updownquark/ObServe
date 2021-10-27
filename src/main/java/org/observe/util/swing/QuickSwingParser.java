package org.observe.util.swing;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.LayoutManager;
import java.awt.Window;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.RootPaneContainer;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.ObservableModelQonfigParser;
import org.observe.util.ObservableModelSet;
import org.observe.util.ObservableModelSet.ExternalModelSet;
import org.observe.util.ObservableModelSet.ModelSetInstance;
import org.observe.util.ObservableModelSet.ModelType;
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

		public void install(Window window, ExternalModelSet extModels, Observable<?> until) {
			ModelSetInstance modelSet = theHead.theModels == null ? null : theHead.theModels.createInstance(extModels, until);
			if (theHead.theTitleValue != null) {
				SettableValue<String> titleV = theHead.theTitleValue.apply(modelSet);
				titleV.changes().act(evt -> {
					if (window instanceof Frame)
						((Frame) window).setTitle(evt.getNewValue());
					else if (window instanceof Dialog)
						((Dialog) window).setTitle(evt.getNewValue());
				});
			} else if (theHead.theDefaultTitle != null) {
				if (window instanceof Frame)
					((Frame) window).setTitle(theHead.theDefaultTitle);
				else if (window instanceof Dialog)
					((Dialog) window).setTitle(theHead.theDefaultTitle);
			}
			if (window instanceof RootPaneContainer)
				install(PanelPopulation.populatePanel(((RootPaneContainer) window).getContentPane(), until), modelSet);
			else
				install(PanelPopulation.populatePanel(window, until), modelSet);
		}

		public void install(Container container, ExternalModelSet extModels, Observable<?> until) {
			ModelSetInstance modelSet = theHead.theModels == null ? null : theHead.theModels.createInstance(extModels, until);
			install(PanelPopulation.populatePanel(container, until), modelSet);
		}

		public void install(PanelPopulation.PanelPopulator<?, ?> container, ModelSetInstance modelSet) {
			theComponent.install(container, modelSet);
		}
	}

	private static class QuickHeadSection {
		final String theDefaultTitle;
		final Function<ModelSetInstance, SettableValue<String>> theTitleValue;
		final ObservableModelSet theModels;

		QuickHeadSection(String defaultTitle, Function<ModelSetInstance, SettableValue<String>> titleValue, ObservableModelSet models) {
			theDefaultTitle = defaultTitle;
			theTitleValue = titleValue;
			theModels = models;
		}
	}

	private final ObservableModelQonfigParser theModelParser;

	public QuickSwingParser() {
		theModelParser = new ObservableModelQonfigParser();
	}

	public void configureInterpreter(Builder interpreter) {
		theModelParser.configureInterpreter(interpreter);
		Builder coreInterpreter = interpreter.forToolkit(CORE.get());
		coreInterpreter.createWith("quick", QuickDocument.class, (element, session) -> {
			QuickHeadSection head = session.getInterpreter().interpret(element.getChildrenInRole("head").getFirst(), QuickHeadSection.class);
			session.put("quick-model", head.theModels);
			return new QuickDocument(head, //
				session.getInterpreter().interpret(element.getChildrenInRole("root").getFirst(), QuickComponent.class));
		}).createWith("head", QuickHeadSection.class, (element, session) -> {
			QonfigElement modelsEl = element.getChildrenInRole("models").peekFirst();
			ObservableModelSet model = modelsEl == null ? null : session.getInterpreter().interpret(modelsEl, ObservableModelSet.class);
			QonfigElement title = element.getChildrenInRole("title").peekFirst();
			String defaultTitle = title == null ? null : (String) title.getValue();
			String titleValueS = title == null ? null : title.getAttribute("title-value", String.class);
			Function<ModelSetInstance, SettableValue<String>> titleValue;
			if (titleValueS != null) {
				if (defaultTitle != null)
					System.err.println("WARNING: title and title-value both specified for head section");
				titleValue = model.getValue(titleValueS, TypeTokens.get().STRING);
			} else if (defaultTitle != null) {
				SettableValue<String> titleS = ObservableModelQonfigParser.literal(defaultTitle, defaultTitle);
				titleValue = __ -> titleS;
			} else
				titleValue = null;
			return new QuickHeadSection(defaultTitle, titleValue, model);
		});
		if (BASE.isBuilt() && BASE.isValid() && interpreter.dependsOn(BASE.get()))
			configureBase(interpreter.forToolkit(BASE.get()));
		if (SWING.isBuilt() && SWING.isValid() && interpreter.dependsOn(SWING.get()))
			configureSwing(interpreter.forToolkit(SWING.get()));
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
			Function<ModelSetInstance, SettableValue<Object>> value;
			TypeToken<?> valueType;
			String valueStr = element.getAttributeText("value");
			String formatStr = element.getAttributeText("format");
			Function<ModelSetInstance, SettableValue<Format<Object>>> format;
			if (valueStr == null || valueStr.isEmpty()) {
				if (formatStr != null && !formatStr.isEmpty())
					System.err.println("Warning: format specified on label without value");
				String txt = element.getAttributeText("text");
				if (txt == null)
					throw new IllegalArgumentException("Either 'text' or 'value' must be specified");
				value = __ -> SettableValue.asSettable(ObservableValue.of(txt), ___ -> "Constant value");
				format = __ -> ObservableModelQonfigParser.literal((Format<Object>) (Format<?>) Format.TEXT, "<unspecified>");
			} else {
				value = model.getValue(valueStr, null);
				valueType = ((ValueContainer<?, ?>) value).getValueType();
				if (formatStr == null || formatStr.isEmpty()) {
					format = __ -> ObservableModelQonfigParser.literal((Format<Object>) (Format<?>) LABEL_FORMAT, "<unspecified>");
				} else
					format = model.getValue(formatStr,
						TypeTokens.get().keyFor(Format.class).parameterized(TypeTokens.get().wrap(valueType)));
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
			ValueContainer<Object, SettableValue<Object>> value;
			String valueStr = element.getAttributeText("value");
			String formatStr = element.getAttributeText("format");
			Function<ModelSetInstance, SettableValue<Format<Object>>> format;
			value = model.getValue(valueStr, null);
			format = model.getValue(formatStr, TypeTokens.get().keyFor(Format.class).parameterized(value.getValueType()));
			return new AbstractQuickComponent(element) {
				@Override
				public void install(PanelPopulator<?, ?> container, ModelSetInstance modelSet) {
					container.addTextField(getFieldName(), //
						value.apply(modelSet), format.apply(modelSet).get(), field -> modify(field));
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
				buttonText = model.getValue(valueStr, TypeTokens.get().STRING);
			Function<ModelSetInstance, ObservableAction<?>> action = model.getAction(element.getAttributeText("action"), null);
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
		}).createWith("table", QuickComponent.class, (element, session) -> {
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			ValueContainer<Object, ObservableCollection<Object>> rows = model.getCollection(//
				element.getAttributeText("rows"), null);
			ValueContainer<Object, SettableValue<Object>> selectionV;
			ValueContainer<Object, ObservableCollection<Object>> selectionC;
			String selectionS = element.getAttributeText("selection");
			if (selectionS != null) {
				ModelType type = model.getType(selectionS);
				switch (type) {
				case Value:
					selectionV = model.getValue(selectionS, rows.getValueType());
					selectionC = null;
					break;
				case Collection:
				case SortedCollection:
					selectionV = null;
					selectionC = model.getCollection(selectionS, rows.getValueType());
					break;
				default:
					throw new IllegalArgumentException("Model value " + selectionS + " is of type " + type
						+ "--only Value, Collection, and SortedCollection supported");
				}
			} else {
				selectionV = null;
				selectionC = null;
			}
			session.put("model-type", rows.getValueType());
			List<Function<ModelSetInstance, CategoryRenderStrategy<Object, ?>>> columns = new ArrayList<>();
			for (QonfigElement columnEl : element.getChildrenInRole("column"))
				columns.add(session.getInterpreter().interpret(columnEl, Function.class));
			return new AbstractQuickComponent(element) {
				@Override
				public void install(PanelPopulator<?, ?> container, ModelSetInstance modelSet) {
					container.addTable(rows.apply(modelSet), table -> {
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
		interpreter.modifyWith("swing:quick", QuickDocument.class, (value, element, session) -> {
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

package org.observe.util.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.assoc.ObservableMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.DefaultExpressoParser;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableExpression.MethodFinder;
import org.observe.util.ClassView;
import org.observe.util.ExpressionValueType;
import org.observe.util.ModelType;
import org.observe.util.ModelType.ModelInstanceType;
import org.observe.util.ModelTypes;
import org.observe.util.ObservableModelQonfigParser;
import org.observe.util.ObservableModelSet;
import org.observe.util.ObservableModelSet.ExternalModelSet;
import org.observe.util.ObservableModelSet.ModelSetInstance;
import org.observe.util.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.observe.util.swing.ComponentDecorator.ModifiableLineBorder;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.TabEditor;
import org.observe.util.swing.PanelPopulation.WindowBuilder;
import org.qommons.Colors;
import org.qommons.collect.BetterList;
import org.qommons.config.DefaultQonfigParser;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpreter;
import org.qommons.config.QonfigInterpreter.Builder;
import org.qommons.config.QonfigInterpreter.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreter.QonfigInterpretingSession;
import org.qommons.config.QonfigInterpreter.QonfigValueExtension;
import org.qommons.config.QonfigParseException;
import org.qommons.config.QonfigParser;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.QonfigToolkitAccess;
import org.qommons.io.BetterFile;
import org.qommons.io.FileUtils;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;

import com.google.common.reflect.TypeToken;

public class QuickSwingParser {
	public static final QonfigToolkitAccess CORE = new QonfigToolkitAccess(QuickSwingParser.class, "quick-core.qtd",
		ObservableModelQonfigParser.TOOLKIT).withCustomValueType(//
			new QuickPosition.PositionValueType(ObservableModelQonfigParser.EXPRESSION_PARSER), //
			new QuickSize.SizeValueType(ObservableModelQonfigParser.EXPRESSION_PARSER));
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

	public interface QuickComponentDef {
		QonfigElement getElement();

		Function<ModelSetInstance, ? extends ObservableValue<String>> getFieldName();

		void setFieldName(Function<ModelSetInstance, ? extends ObservableValue<String>> fieldName);

		QuickComponentDef modify(BiConsumer<ComponentEditor<?, ?>, ModelSetInstance> modification);

		QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder);
	}

	public static class QuickComponent {
		private final QuickComponentDef theDefinition;
		private final QuickComponent.Builder theParent;
		private final Component theComponent;
		private final Map<QonfigAttributeDef, Object> theAttributeValues;
		private final ObservableCollection<QuickComponent> theChildren;
		private ObservableMultiMap<QuickComponentDef, QuickComponent> theGroupedChildren;
		private ObservableValue<Point> theLocation;
		private ObservableValue<Dimension> theSize;

		public QuickComponent(QuickComponentDef definition, QuickComponent.Builder parent, Component component,
			Map<QonfigAttributeDef, Object> attributeValues, ObservableCollection<QuickComponent> children) {
			super();
			theDefinition = definition;
			theParent = parent;
			theComponent = component;
			theAttributeValues = attributeValues;
			theChildren = children;
		}

		public QuickComponentDef getDefinition() {
			return theDefinition;
		}

		public QuickComponent getParent() {
			return theParent == null ? null : theParent.getBuilt();
		}

		public Component getComponent() {
			return theComponent;
		}

		public Map<QonfigAttributeDef, Object> getAttributeValues() {
			return theAttributeValues;
		}

		public ObservableCollection<QuickComponent> getChildren() {
			return theChildren;
		}

		public ObservableValue<Point> getLocation() {
		}

		public ObservableValue<Dimension> getMinimumSize() {
		}

		public ObservableValue<Dimension> getPreferredSize() {
		}

		public ObservableValue<Dimension> getMaximumSize() {
		}

		public ObservableValue<Dimension> getSize() {
		}

		public ObservableMultiMap<QuickComponentDef, QuickComponent> getGroupedChildren() {
		}

		public static Builder build(QuickComponentDef def, QuickComponent.Builder parent, ModelSetInstance models) {
			return new Builder(def, parent, models);
		}

		public static class Builder {
			private final QuickComponentDef theDefinition;
			private final Builder theParent;
			private final ModelSetInstance theModelsInstance;
			private Component theComponent;
			private final Map<QonfigAttributeDef, Object> theAttributeValues;
			private final ObservableCollection<QuickComponent> theChildren;
			private QuickComponent theBuilt;

			public Builder(QuickComponentDef definition, Builder parent, ModelSetInstance models) {
				theDefinition = definition;
				theParent = parent;
				theModelsInstance = models;
				theAttributeValues = new LinkedHashMap<>();
				theChildren = ObservableCollection.build(QuickComponent.class).build();
			}

			public ModelSetInstance getModels() {
				return theModelsInstance;
			}

			public ObservableCollection<QuickComponent> getChildren() {
				return theChildren;
			}

			public Builder withAttribute(QonfigAttributeDef attr, Object value) {
				if (theBuilt != null)
					throw new IllegalStateException("Already built");
				theAttributeValues.put(attr, value);
				return this;
			}

			public Builder withChild(QuickComponent component) {
				if (theBuilt != null)
					throw new IllegalStateException("Already built");
				theChildren.add(component);
				return this;
			}

			public Builder withComponent(Component component) {
				if (theBuilt != null)
					throw new IllegalStateException("Already built");
				theComponent = component;
				return this;
			}

			QuickComponent getBuilt() {
				return theBuilt;
			}

			public QuickComponent build() {
				if (theBuilt != null)
					throw new IllegalStateException("Already built");
				return new QuickComponent(theDefinition, theParent, theComponent, Collections.unmodifiableMap(theAttributeValues),
					theChildren.flow().unmodifiable().collect());
			}
		}
	}

	public static abstract class AbstractQuickComponentDef implements QuickComponentDef {
		private final QonfigElement theElement;
		private Function<ModelSetInstance, ? extends ObservableValue<String>> theFieldName;
		private BiConsumer<ComponentEditor<?, ?>, ModelSetInstance> theModifications;

		public AbstractQuickComponentDef(QonfigElement element) {
			theElement = element;
		}

		@Override
		public QonfigElement getElement() {
			return theElement;
		}

		@Override
		public Function<ModelSetInstance, ? extends ObservableValue<String>> getFieldName() {
			return theFieldName;
		}

		@Override
		public void setFieldName(Function<ModelSetInstance, ? extends ObservableValue<String>> fieldName) {
			theFieldName = fieldName;
		}

		@Override
		public AbstractQuickComponentDef modify(BiConsumer<ComponentEditor<?, ?>, ModelSetInstance> fieldModification) {
			if (theModifications == null)
				theModifications = fieldModification;
			else {
				BiConsumer<ComponentEditor<?, ?>, ModelSetInstance> old = theModifications;
				theModifications = (field, models) -> {
					old.accept(field, models);
					fieldModification.accept(field, models);
				};
			}
			return this;
		}

		public void modify(ComponentEditor<?, ?> component, QuickComponent.Builder builder) {
			if (theModifications != null)
				theModifications.accept(component, builder.getModels());
		}
	}

	public static abstract class AbstractQuickField extends AbstractQuickComponentDef {
		public AbstractQuickField(QonfigElement element) {
			super(element);
		}

		@Override
		public void modify(ComponentEditor<?, ?> component, QuickComponent.Builder builder) {
			if (getFieldName() != null && component instanceof PanelPopulation.FieldEditor)
				((PanelPopulation.FieldEditor<?, ?>) component).withFieldName(getFieldName().apply(builder.getModels()));
			super.modify(component, builder);
		}
	}

	public interface QuickContainer extends QuickComponentDef {
		List<QuickComponentDef> getChildren();

		QuickComponent installContainer(PanelPopulator<?, ?> container, QuickComponent.Builder builder,
			Consumer<PanelPopulator<?, ?>> populator);

		default ObservableCollection<QuickComponent> populateContainer(PanelPopulator<?, ?> thisContainer, QuickComponent.Builder builder) {
			List<QuickComponent> children = new ArrayList<>(getChildren().size());
			for (int c = 0; c < getChildren().size(); c++) {
				QuickComponentDef childDef = getChildren().get(c);
				QuickComponent.Builder childBuilder = QuickComponent.build(childDef, builder, builder.getModels());
				children.add(childDef.install(thisContainer, childBuilder));
			}
			return ObservableCollection.of(QuickComponent.class, children);
		}

		@Override
		default QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
			return installContainer(container, builder, thisContainer -> populateContainer(thisContainer, builder));
		}
	}

	public static abstract class AbstractQuickContainer extends AbstractQuickComponentDef implements QuickContainer {
		private final List<QuickComponentDef> theChildren;

		public AbstractQuickContainer(QonfigElement element, List<QuickComponentDef> children) {
			super(element);
			theChildren = children;
		}

		@Override
		public List<QuickComponentDef> getChildren() {
			return theChildren;
		}
	}

	public interface QuickLayout {
		LayoutManager create();
	}

	public static class QuickBox extends AbstractQuickContainer {
		private final String theLayoutName;
		private QuickLayout theLayout;

		public QuickBox(QonfigElement element, List<QuickComponentDef> children, String layoutName) {
			super(element, children);
			theLayoutName = layoutName;
		}

		public QuickBox setLayout(QuickLayout layout) {
			theLayout = layout;
			return this;
		}

		@Override
		public QuickComponent installContainer(PanelPopulator<?, ?> container, QuickComponent.Builder builder,
			Consumer<PanelPopulator<?, ?>> populator) {
			if (theLayout == null)
				throw new IllegalStateException("No interpreter configured for layout " + theLayoutName);
			String fieldName = getFieldName() == null ? null : getFieldName().apply(builder.getModels()).get();
			container.addHPanel(fieldName, theLayout.create(), thisContainer -> {
				builder.withComponent(thisContainer.getComponent());
				modify(thisContainer, builder);
				populator.accept(thisContainer);
			});
			return builder.build();
		}
	}

	public interface QuickDocument extends ObservableModelQonfigParser.AppEnvironment {
		public QonfigElement getElement();

		public QuickHeadSection getHead();

		public QuickComponentDef getComponent();

		@Override
		public Function<ModelSetInstance, SettableValue<String>> getTitle();

		public void setTitle(Function<ModelSetInstance, SettableValue<String>> title);

		@Override
		public Function<ModelSetInstance, SettableValue<Image>> getIcon();

		public void setIcon(Function<ModelSetInstance, SettableValue<Image>> icon);

		public Function<ModelSetInstance, SettableValue<Integer>> getX();

		public Function<ModelSetInstance, SettableValue<Integer>> getY();

		public Function<ModelSetInstance, SettableValue<Integer>> getWidth();

		public Function<ModelSetInstance, SettableValue<Integer>> getHeight();

		public QuickDocument withBounds(//
			Function<ModelSetInstance, SettableValue<Integer>> x, Function<ModelSetInstance, SettableValue<Integer>> y, //
			Function<ModelSetInstance, SettableValue<Integer>> width, Function<ModelSetInstance, SettableValue<Integer>> height);

		public Function<ModelSetInstance, SettableValue<Boolean>> getVisible();

		public int getCloseAction();

		public QuickDocument setVisible(Function<ModelSetInstance, SettableValue<Boolean>> visible);

		public void setCloseAction(int closeAction);

		public QuickUiDef createUI(ExternalModelSet extModels);
	}

	public static class QuickDocumentImpl implements QuickDocument {
		private final QonfigElement theElement;
		private final QuickHeadSection theHead;
		private final QuickComponentDef theComponent;
		private Function<ModelSetInstance, SettableValue<String>> theTitle;
		private Function<ModelSetInstance, SettableValue<Image>> theIcon;
		private Function<ModelSetInstance, SettableValue<Integer>> theX;
		private Function<ModelSetInstance, SettableValue<Integer>> theY;
		private Function<ModelSetInstance, SettableValue<Integer>> theWidth;
		private Function<ModelSetInstance, SettableValue<Integer>> theHeight;
		private Function<ModelSetInstance, SettableValue<Boolean>> isVisible;
		private int theCloseAction = WindowConstants.HIDE_ON_CLOSE;

		public QuickDocumentImpl(QonfigElement element, QuickHeadSection head, QuickComponentDef component) {
			theElement = element;
			theHead = head;
			theComponent = component;
		}

		@Override
		public QonfigElement getElement() {
			return theElement;
		}

		@Override
		public QuickHeadSection getHead() {
			return theHead;
		}

		@Override
		public QuickComponentDef getComponent() {
			return theComponent;
		}

		@Override
		public Function<ModelSetInstance, SettableValue<String>> getTitle() {
			return theTitle;
		}

		@Override
		public void setTitle(Function<ModelSetInstance, SettableValue<String>> title) {
			theTitle = title;
		}

		@Override
		public Function<ModelSetInstance, SettableValue<Image>> getIcon() {
			return theIcon;
		}

		@Override
		public void setIcon(Function<ModelSetInstance, SettableValue<Image>> icon) {
			theIcon = icon;
		}

		@Override
		public Function<ModelSetInstance, SettableValue<Integer>> getX() {
			return theX;
		}

		@Override
		public Function<ModelSetInstance, SettableValue<Integer>> getY() {
			return theY;
		}

		@Override
		public Function<ModelSetInstance, SettableValue<Integer>> getWidth() {
			return theWidth;
		}

		@Override
		public Function<ModelSetInstance, SettableValue<Integer>> getHeight() {
			return theHeight;
		}

		@Override
		public Function<ModelSetInstance, SettableValue<Boolean>> getVisible() {
			return isVisible;
		}

		@Override
		public int getCloseAction() {
			return theCloseAction;
		}

		@Override
		public QuickDocument withBounds(//
			Function<ModelSetInstance, SettableValue<Integer>> x, Function<ModelSetInstance, SettableValue<Integer>> y, //
			Function<ModelSetInstance, SettableValue<Integer>> width, Function<ModelSetInstance, SettableValue<Integer>> height) {
			theX = x;
			theY = y;
			theWidth = width;
			theHeight = height;
			return this;
		}

		@Override
		public QuickDocument setVisible(Function<ModelSetInstance, SettableValue<Boolean>> visible) {
			isVisible = visible;
			return this;
		}

		@Override
		public void setCloseAction(int closeAction) {
			theCloseAction = closeAction;
		}

		@Override
		public QuickUiDef createUI(ExternalModelSet extModels) {
			return new QuickUiDef(this, extModels);
		}
	}

	public static class QuickUiDef {
		private final QuickDocument theDocument;
		private final ExternalModelSet theExternalModels;
		private ModelSetInstance theModels;
		private Observable<?> theUntil;

		private QuickUiDef(QuickDocument doc, ExternalModelSet extModels) {
			theDocument = doc;
			theExternalModels = extModels;
		}

		public QuickDocument getDocument() {
			return theDocument;
		}

		public QuickUiDef withUntil(Observable<?> until) {
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
			if (theModels == null)
				theModels = theDocument.getHead().getModels().createInstance(theExternalModels, getUntil());
			return theModels;
		}

		public JFrame createFrame() {
			return install(new JFrame());
		}

		public JFrame install(JFrame frame) {
			return install(WindowPopulation.populateWindow(frame, getUntil(), false, false)).getWindow();
		}

		public JDialog install(JDialog dialog) {
			return install(WindowPopulation.populateDialog(dialog, getUntil(), false)).getWindow();
		}

		public <W extends Window> WindowBuilder<W, ?> install(WindowBuilder<W, ?> builder) {
			if (theDocument.getTitle() != null)
				builder.withTitle(theDocument.getTitle().apply(getModels()));
			if (theDocument.getIcon() != null)
				builder.withIcon(theDocument.getIcon().apply(getModels()));
			if (theDocument.getVisible() != null)
				builder.withVisible(theDocument.getVisible().apply(getModels()));
			if (theDocument.getX() != null)
				builder.withX(theDocument.getX().apply(getModels()));
			if (theDocument.getY() != null)
				builder.withY(theDocument.getY().apply(getModels()));
			if (theDocument.getWidth() != null)
				builder.withWidth(theDocument.getWidth().apply(getModels()));
			if (theDocument.getHeight() != null)
				builder.withHeight(theDocument.getHeight().apply(getModels()));
			builder.withCloseAction(theDocument.getCloseAction());
			builder.withHContent(new BorderLayout(), content -> installContent(content));
			builder.run(null);
			return builder;
		}

		public void installContent(Container container) {
			installContent(PanelPopulation.populatePanel(container, getUntil()));
		}

		public QuickComponent installContent(PanelPopulation.PanelPopulator<?, ?> container) {
			QuickComponent.Builder root = QuickComponent.build(theDocument.getComponent(), null, getModels());
			theDocument.getComponent().install(container, root);
			return root.build();
		}
	}

	public static class QuickHeadSection {
		private final ClassView theClassView;
		private final ObservableModelSet theModels;

		QuickHeadSection(ClassView classView, ObservableModelSet models) {
			theClassView = classView;
			theModels = models;
		}

		public ClassView getImports() {
			return theClassView;
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
		QonfigToolkit core = CORE.get();
		Builder coreInterpreter = interpreter.forToolkit(core);
		coreInterpreter.createWith("quick", QuickDocument.class, (element, session) -> {
			QuickHeadSection head = session.interpretChildren("head", QuickHeadSection.class).getFirst();
			session.put("quick-cv", head.getImports());
			session.put("quick-model", head.getModels());
			QuickDocument doc = new QuickDocumentImpl(element, head, //
				session.interpretChildren("root", QuickComponentDef.class).getFirst());
			return doc;
		}).createWith("head", QuickHeadSection.class, (element, session) -> {
			ClassView cv = session.interpretChildren("imports", ClassView.class).peekFirst();
			if (cv == null)
				cv = ClassView.build().build();
			ObservableModelSet model = session.interpretChildren("models", ObservableModelSet.class).peekFirst();
			if (model == null)
				model = ObservableModelSet.build().build();
			return new QuickHeadSection(cv, model);
		}).modifyWith("window", QuickDocument.class, (doc, element, session) -> {
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
				throw new IllegalStateException(
					"Unrecognized close-action: " + element.getAttributeText(core.getAttribute("window", "close-action")));
			}
			return doc;
		}).modifyWith("widget", QuickComponentDef.class, (widget, element, session) -> {
			ClassView cv = (ClassView) session.get("quick-cv");
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			ObservableExpression tooltipX = session.getAttribute("tooltip", ObservableExpression.class);
			ObservableExpression bgColorX = session.getAttribute("bg-color", ObservableExpression.class);
			ObservableExpression visibleX = session.getAttribute("visible", ObservableExpression.class);
			QuickBorder border = session.interpretChildren("border", QuickBorder.class).peekFirst();
			ValueContainer<SettableValue, SettableValue<String>> tooltip = tooltipX == null ? null
				: tooltipX.evaluate(ModelTypes.Value.forType(String.class), model, cv);
			Function<ModelSetInstance, SettableValue<Color>> bgColor = parseColor(bgColorX, model, cv);
			ValueContainer<SettableValue, SettableValue<Boolean>> visible = visibleX == null ? null
				: visibleX.evaluate(ModelTypes.Value.forType(boolean.class), model, cv);
			if (tooltip != null) {
				widget.modify((comp, models) -> {
					comp.withTooltip(tooltip.apply(models));
				});
			}
			if (bgColor != null) {
				widget.modify((comp, models) -> {
					comp.modifyComponent(c -> {
						ObservableValue<Color> color = bgColor.apply(models);
						color.changes().takeUntil(models.getUntil()).act(evt -> {
							Color colorV = evt.getNewValue();
							c.setBackground(colorV == null ? Colors.transparent : colorV);
							if (c instanceof JComponent)
								((JComponent) c).setOpaque(colorV != null && colorV.getAlpha() > 0);
						});
					});
				});
			}
			if (visible != null)
				widget.modify((comp, models) -> {
					comp.visibleWhen(visible.apply(models));
				});
			if (border != null)
				widget.modify((comp, models) -> {
					comp.modifyComponent(c -> {
						if (c instanceof JComponent) {
							JComponent jc = (JComponent) c;
							border.apply(models).changes().takeUntil(models.getUntil()).act(evt -> {
								if (evt.getNewValue() != jc.getBorder())
									jc.setBorder(evt.getNewValue());
								jc.repaint();
							});
						}
					});
				});
			return widget;
		}).createWith("line-border", QuickBorder.class, (element, session) -> {
			ClassView cv = (ClassView) session.get("quick-cv");
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
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
		}).createWith("titled-border", QuickBorder.class, (element, session) -> {
			ClassView cv = (ClassView) session.get("quick-cv");
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
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
		});
		if (BASE.isBuilt() && BASE.isValid() && interpreter.dependsOn(BASE.get()))
			configureBase(interpreter.forToolkit(BASE.get()));
		if (SWING.isBuilt() && SWING.isValid() && interpreter.dependsOn(SWING.get()))
			configureSwing(interpreter.forToolkit(SWING.get()));
		return interpreter;
	}

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

	@SuppressWarnings("rawtypes")
	void configureBase(Builder interpreter) {
		QonfigToolkit base = interpreter.getToolkit();
		interpreter.createWith("box", QuickBox.class, (element, session) -> {
			List<QuickComponentDef> children = session.interpretChildren("content", QuickComponentDef.class);
			return new QuickBox(element, children, //
				element.getAttributeText(base.getAttribute("box", "layout")));
		});
		interpreter.modifyWith("border", QuickBox.class, (value, element, session) -> {
			value.setLayout(BorderLayout::new);
			for (QuickComponentDef child : value.getChildren()) {
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
			return value;
		});
		interpreter.modifyWith("simple", QuickBox.class, (value, element, session) -> {
			ClassView cv = (ClassView) session.get("quick-cv");
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			value.setLayout(SimpleLayout::new);
			for (QuickComponentDef child : value.getChildren()) {
				ObservableExpression leftX = child.getElement().getAttribute(base.getAttribute("simple-layout-child", "left"),
					ObservableExpression.class);
				ObservableExpression rightX = child.getElement().getAttribute(base.getAttribute("simple-layout-child", "right"),
					ObservableExpression.class);
				ObservableExpression topX = child.getElement().getAttribute(base.getAttribute("simple-layout-child", "top"),
					ObservableExpression.class);
				ObservableExpression bottomX = child.getElement().getAttribute(base.getAttribute("simple-layout-child", "bottom"),
					ObservableExpression.class);
				ObservableExpression minWidthX = child.getElement().getAttribute(base.getAttribute("simple-layout-child", "min-width"),
					ObservableExpression.class);
				ObservableExpression prefWidthX = child.getElement().getAttribute(base.getAttribute("simple-layout-child", "pref-width"),
					ObservableExpression.class);
				ObservableExpression maxWidthX = child.getElement().getAttribute(base.getAttribute("simple-layout-child", "max-width"),
					ObservableExpression.class);
				ObservableExpression minHeightX = child.getElement().getAttribute(base.getAttribute("simple-layout-child", "min-height"),
					ObservableExpression.class);
				ObservableExpression prefHeightX = child.getElement().getAttribute(base.getAttribute("simple-layout-child", "pref-height"),
					ObservableExpression.class);
				ObservableExpression maxHeightX = child.getElement().getAttribute(base.getAttribute("simple-layout-child", "max-height"),
					ObservableExpression.class);
				ModelInstanceType<SettableValue, SettableValue<QuickPosition>> posType = ModelTypes.Value.forType(QuickPosition.class);
				ModelInstanceType<SettableValue, SettableValue<QuickSize>> sizeType = ModelTypes.Value.forType(QuickSize.class);
				ValueContainer<SettableValue, SettableValue<QuickPosition>> leftC = leftX == null ? null
					: leftX.evaluate(posType, model, cv);
				ValueContainer<SettableValue, SettableValue<QuickPosition>> rightC = rightX == null ? null
					: rightX.evaluate(posType, model, cv);
				ValueContainer<SettableValue, SettableValue<QuickPosition>> topC = topX == null ? null : topX.evaluate(posType, model, cv);
				ValueContainer<SettableValue, SettableValue<QuickPosition>> bottomC = bottomX == null ? null
					: bottomX.evaluate(posType, model, cv);
				ValueContainer<SettableValue, SettableValue<QuickSize>> minWidthC = minWidthX == null ? null
					: minWidthX.evaluate(sizeType, model, cv);
				ValueContainer<SettableValue, SettableValue<QuickSize>> prefWidthC = prefWidthX == null ? null
					: prefWidthX.evaluate(sizeType, model, cv);
				ValueContainer<SettableValue, SettableValue<QuickSize>> maxWidthC = maxWidthX == null ? null
					: maxWidthX.evaluate(sizeType, model, cv);
				ValueContainer<SettableValue, SettableValue<QuickSize>> minHeightC = minHeightX == null ? null
					: minHeightX.evaluate(sizeType, model, cv);
				ValueContainer<SettableValue, SettableValue<QuickSize>> prefHeightC = prefHeightX == null ? null
					: prefHeightX.evaluate(sizeType, model, cv);
				ValueContainer<SettableValue, SettableValue<QuickSize>> maxHeightC = maxHeightX == null ? null
					: maxHeightX.evaluate(sizeType, model, cv);
				child.modify((ch, m) -> {
					ObservableValue<QuickPosition> left = leftC == null ? null : leftC.apply(m);
					ObservableValue<QuickPosition> right = rightC == null ? null : rightC.apply(m);
					ObservableValue<QuickPosition> top = topC == null ? null : topC.apply(m);
					ObservableValue<QuickPosition> bottom = bottomC == null ? null : bottomC.apply(m);
					ObservableValue<QuickSize> minWidth = minWidthC == null ? null : minWidthC.apply(m);
					ObservableValue<QuickSize> prefWidth = prefWidthC == null ? null : prefWidthC.apply(m);
					ObservableValue<QuickSize> maxWidth = maxWidthC == null ? null : maxWidthC.apply(m);
					ObservableValue<QuickSize> minHeight = minHeightC == null ? null : minHeightC.apply(m);
					ObservableValue<QuickSize> prefHeight = prefHeightC == null ? null : prefHeightC.apply(m);
					ObservableValue<QuickSize> maxHeight = maxHeightC == null ? null : maxHeightC.apply(m);
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
						).takeUntil(m.getUntil()).act(__ -> {
							Container parent = ch.getComponent().getParent();
							if (parent != null && parent.getLayout() instanceof SimpleLayout) {
								((SimpleLayout) parent.getLayout()).layoutChild(parent, ch.getComponent());
							}
						});
				});
			}
			return value;
		});
		interpreter.modifyWith("inline", QuickBox.class, (value, element, session) -> {
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
			return value.setLayout(() -> layout);
		});
		interpreter.createWith("label", QuickComponentDef.class, (element, session) -> evaluateLabel(element, session));
		interpreter.createWith("text-field", QuickComponentDef.class, (element, session) -> {
			ClassView cv = (ClassView) session.get("quick-cv");
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			ValueContainer<SettableValue, ?> value;
			ObservableExpression valueX = session.getAttribute("value", ObservableExpression.class);
			ObservableExpression formatX = session.getAttribute("format", ObservableExpression.class);
			Function<ModelSetInstance, SettableValue<Format<Object>>> format;
			String columnsStr = session.getAttributeText("columns");
			int columns = columnsStr == null ? -1 : Integer.parseInt(columnsStr);
			value = valueX.evaluate(ModelTypes.Value.any(), model, cv);
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
				format = ObservableModelQonfigParser.literalContainer(
					ModelTypes.Value.forType((Class<Format<Object>>) (Class<?>) Format.class), (Format<Object>) f, type.getSimpleName());
			}
			return new AbstractQuickField(element) {
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
							modify(field, builder);
							if (columns > 0)
								field.getEditor().withColumns(columns);
						});
					return builder.build();
				}
			};
		}).createWith("button", QuickComponentDef.class, (element, session) -> {
			ClassView cv = (ClassView) session.get("quick-cv");
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			Function<ModelSetInstance, SettableValue<String>> buttonText;
			ObservableExpression valueX = session.getAttribute("text", ObservableExpression.class);
			if (valueX == null) {
				String txt = element.getValueText();
				if (txt == null)
					throw new IllegalArgumentException("Either 'text' attribute or element value must be specified");
				buttonText = __ -> ObservableModelQonfigParser.literal(txt, txt);
			} else
				buttonText = valueX.evaluate(ModelTypes.Value.forType(String.class), model, cv);
			Function<ModelSetInstance, ? extends ObservableAction> action = model.get(session.getAttributeText("action"),
				ModelTypes.Action);
			return new AbstractQuickComponentDef(element) {
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
		});
		interpreter.createWith("table", QuickComponentDef.class, (element, session) -> interpretTable(element, session));
		interpreter.createWith("column", Function.class, (element, session) -> {
			System.err.println("WARNING: column is not fully implemented!!"); // TODO
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			ClassView cv = (ClassView) session.get("imports");
			TypeToken<Object> modelType = (TypeToken<Object>) session.get("model-type");
			String name = session.getAttributeText("name");
			ObservableExpression valueX = session.getAttribute("value", ObservableExpression.class);
			String rowValueName = (String) session.get("value-name");
			String cellValueName = (String) session.get("render-value-name");
			if (modelType == null || rowValueName == null || cellValueName == null)
				throw new IllegalStateException(
					"column intepretation expects 'model-type', 'value-name', and 'render-value-name' session values");
			ObservableModelSet.WrappedBuilder wb = ObservableModelSet.wrap(model);
			ObservableModelSet.ModelValuePlaceholder<SettableValue, SettableValue<Object>> valueRowVP = wb.withPlaceholder(rowValueName,
				ModelTypes.Value.forType(modelType));
			ObservableModelSet.Wrapped valueModel = wb.build();
			TypeToken<Object> columnType;
			Function<ModelSetInstance, Function<Object, Object>> valueFn;
			if (valueX instanceof DefaultExpressoParser.LambdaExpression
				|| valueX instanceof DefaultExpressoParser.MethodReferenceExpression) {
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
					SettableValue<Object> rowValue = SettableValue.build(modelType).build();
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
			ObservableModelSet.ModelValuePlaceholder<SettableValue, SettableValue<Object>> cellRowVP = wb.withPlaceholder(cellValueName,
				ModelTypes.Value.forType(columnType));
			ObservableModelSet.Wrapped cellModel = wb.build();
			ObservableExpression renderX = session.getAttribute("format", ObservableExpression.class);
			Function<ModelSetInstance, BiFunction<? super Object, ? super Object, String>> renderFn;
			if (renderX != null) {
				ValueContainer<SettableValue, SettableValue<String>> renderC = renderX == null ? null
					: renderX.evaluate(ModelTypes.Value.forType(String.class), cellModel, cv);
				renderFn = msi -> {
					SettableValue<Object> rowValue = SettableValue.build(modelType).build();
					SettableValue<Object> cellValue = SettableValue.build(columnType).build();
					ModelSetInstance cellModelInst = cellModel.wrap(msi)//
						.with(valueRowVP, rowValue)//
						.with(cellRowVP, cellValue)//
						.build();
					SettableValue<String> renderV = renderC.get(cellModelInst);
					return (row, cell) -> {
						rowValue.set(row, null);
						cellValue.set(cell, null);
						return renderV.get();
					};
				};
			} else
				renderFn = null;
			return extModels -> {
				CategoryRenderStrategy<Object, Object> column = new CategoryRenderStrategy<>(name, columnType,
					valueFn.apply((ModelSetInstance) extModels));
				if (renderFn != null)
					column.formatText(renderFn.apply((ModelSetInstance) extModels));
				return column;
			};
		});
		interpreter.createWith("columns", Void.class, (element, session) -> {
			ObservableModelSet.Builder models = (ObservableModelSet.Builder) session.get("model");
			session.put("quick-model", models);
			ClassView cv = (ClassView) session.get("imports");
			TypeToken<Object> rowType = (TypeToken<Object>) ObservableModelQonfigParser.parseType(session.getAttributeText("type"), cv);
			session.put("model-type", rowType);
			TypeToken<CategoryRenderStrategy<Object, ?>> columnType = TypeTokens.get().keyFor(CategoryRenderStrategy.class)
				.<CategoryRenderStrategy<Object, ?>> parameterized(rowType, TypeTokens.get().WILDCARD);
			String rowValueName = session.getAttributeText("value-name");
			session.put("value-name", rowValueName);
			String colValueName = session.getAttributeText("render-value-name");
			session.put("render-value-name", colValueName);
			List<Function<ModelSetInstance, CategoryRenderStrategy<Object, ?>>> columns = new ArrayList<>();
			for (QonfigElement columnEl : session.getChildren("column")) {
				columns.add(session.getInterpreter().interpret(columnEl, Function.class));
			}
			models.with(session.getAttributeText("name"), ModelTypes.Collection.forType(columnType), //
				(msi, extModels) -> {
					List<CategoryRenderStrategy<Object, ?>> columnInstances = new ArrayList<>(columns.size());
					for (Function<ModelSetInstance, CategoryRenderStrategy<Object, ?>> column : columns)
						columnInstances.add(column.apply(msi));
					return ObservableCollection.of(columnType, columnInstances);
				});
			return null;
		});
		interpreter.createWith("split", QuickComponentDef.class, (element, session) -> {
			ClassView cv = (ClassView) session.get("quick-cv");
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			if (element.getChildrenByRole().get(base.getChild("split", "content").getDeclared()).size() != 2)
				throw new UnsupportedOperationException("Currently only 2 (and exactly 2) contents are supported for split");
			List<QuickComponentDef> children = session.interpretChildren("content", QuickComponentDef.class);
			boolean vertical = session.getAttributeText("orientation").equals("vertical");
			Function<ModelSetInstance, SettableValue<QuickPosition>> splitPos = parsePosition(
				session.getAttribute("split-position", ObservableExpression.class), model, cv);
			return new AbstractQuickComponentDef(element) {
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
														sp.setDividerLocation(pos.get().evaluate(sp.getWidth()));
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
														sp.setDividerLocation(pos.get().evaluate(sp.getWidth()));
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
												sp.setDividerLocation(pos.get().evaluate(sp.getWidth()));
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
											sp.setDividerLocation(pos.get().evaluate(sp.getWidth()));
										} finally {
											divCallbackLock[0] = false;
										}
									}
								});
							});
						}
						split.firstH(new JustifiedBoxLayout(true).mainJustified().crossJustified(), first -> {
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
		});
		interpreter.createWith("field-panel", QuickComponentDef.class, (element, session) -> {
			List<QuickComponentDef> children = session.interpretChildren("content", QuickComponentDef.class);
			System.err.println("WARNING: field-panel is not fully implemented!!"); // TODO
			return new AbstractQuickComponentDef(element) {
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
		});
		interpreter.modifyWith("field", QuickComponentDef.class, (value, element, session) -> {
			ObservableExpression fieldName = session.getAttribute("field-name", ObservableExpression.class);
			if (fieldName != null) {
				ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
				ClassView cv = (ClassView) session.get("imports");
				value.setFieldName(fieldName.evaluate(ModelTypes.Value.forType(String.class), model, cv));
			}
			if (Boolean.TRUE.equals(session.getAttribute("fill", Boolean.class)))
				value.modify((f, m) -> f.fill());
			return value;
		});
		interpreter.createWith("spacer", QuickComponentDef.class, (element, session) -> {
			int length = Integer.parseInt(element.getAttributeText(base.getAttribute("spacer", "length")));
			return new AbstractQuickComponentDef(element) {
				@Override
				public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
					container.spacer(length, builder::withComponent);
					return builder.build();
				}
			};
		});
		interpreter.createWith("tree", QuickComponentDef.class, (element, session) -> {
			return interpretTree(element, session);
		});
		interpreter.createWith("tree-table", QuickComponentDef.class, (element, session) -> {
			return interpretTreeTable(element, session);
		});
		interpreter.createWith("text-area", QuickComponentDef.class, (element, session) -> {
			ClassView cv = (ClassView) session.get("quick-cv");
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
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
				format = ObservableModelQonfigParser.literalContainer(
					ModelTypes.Value.forType((Class<Format<Object>>) (Class<?>) Format.class), (Format<Object>) f, type.getSimpleName());
			}
			ValueContainer<SettableValue, SettableValue<Integer>> rows = session.interpretAttribute("rows", ObservableExpression.class,
				true, ex -> ex.evaluate(ModelTypes.Value.forType(Integer.class), model, cv));
			ValueContainer<SettableValue, SettableValue<Boolean>> html = session.interpretAttribute("html", ObservableExpression.class,
				true, ex -> ex.evaluate(ModelTypes.Value.forType(Boolean.class), model, cv));
			ValueContainer<SettableValue, SettableValue<Boolean>> editable = session.interpretAttribute("editable",
				ObservableExpression.class, true, ex -> ex.evaluate(ModelTypes.Value.forType(Boolean.class), model, cv));
			return new AbstractQuickField(element) {
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
		});
		interpreter.createWith("check-box", QuickComponentDef.class, (element, session) -> {
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			ClassView cv = (ClassView) session.get("quick-cv");
			ValueContainer<SettableValue, SettableValue<Boolean>> value;
			value = session.getAttribute("value", ObservableExpression.class).evaluate(ModelTypes.Value.forType(boolean.class), model, cv);
			ValueContainer<SettableValue, SettableValue<String>> text = session.interpretValue(ObservableExpression.class, true,
				ex -> ex.evaluate(ModelTypes.Value.forType(String.class), model, cv));
			return new AbstractQuickField(element) {
				@Override
				public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
					SettableValue<Boolean> realValue = value.apply(builder.getModels());
					container.addCheckField(null, realValue, check -> {
						if (text != null) {
							text.get(builder.getModels()).changes().act(evt -> {
								check.getEditor().setText(evt.getNewValue());
							});
						}
						modify(check, builder);
					});
					return builder.build();
				}
			};
		});
		interpreter.createWith("radio-buttons", QuickComponentDef.class, (element, session) -> {
			System.err.println("WARNING: radio-buttons is not fully implemented!!"); // TODO
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			ClassView cv = (ClassView) session.get("quick-cv");
			ValueContainer<SettableValue, ? extends SettableValue<Object>> value;
			value = (ValueContainer<SettableValue, ? extends SettableValue<Object>>) session
				.getAttribute("value", ObservableExpression.class)//
				.evaluate(ModelTypes.Value.any(), model, cv);
			ValueContainer<SettableValue, SettableValue<Object[]>> values = session.getAttribute("values", ObservableExpression.class)
				.evaluate(ModelTypes.Value.forType((TypeToken<Object[]>) TypeTokens.get().getArrayType(value.getType().getType(0), 1)),
					model, cv);
			return new AbstractQuickField(element) {
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
		});
		interpreter.createWith("tab-set", MultiTabSet.class, (element, session) -> MultiTabSet.parse(element, session));
		interpreter.createWith("tabs", QuickComponentDef.class, (element, session) -> createTabs(element, session));
		interpreter.createWith("file-button", QuickComponentDef.class, (element, session) -> {
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			ClassView cv = (ClassView) session.get("quick-cv");
			ValueContainer<SettableValue, ? extends SettableValue<Object>> value;
			value = (ValueContainer<SettableValue, ? extends SettableValue<Object>>) session
				.getAttribute("value", ObservableExpression.class)//
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
			boolean open = element.getAttribute(base.getAttribute("file-button", "open"), boolean.class);
			return new AbstractQuickField(element) {
				@Override
				public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
					container.addFileField(null, file.apply(builder.getModels()), open, fb -> {
						modify(fb, builder);
					});
					return builder.build();
				}
			};
		});
	}

	private QuickComponentDef evaluateLabel(QonfigElement element, QonfigInterpretingSession session) throws QonfigInterpretationException {
		ClassView cv = (ClassView) session.get("quick-cv");
		ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
		Function<ModelSetInstance, ? extends SettableValue> value;
		TypeToken<?> valueType;
		ObservableExpression valueEx = session.getAttribute("value", ObservableExpression.class);
		ValueContainer<SettableValue, ?> valueX;
		if (valueEx != null && valueEx != ObservableExpression.EMPTY)
			valueX = valueEx.evaluate(ModelTypes.Value.any(), model, cv);
		else if (element.getValue() == null) {
			session.withWarning("No value for label");
			valueX = ObservableModelQonfigParser.literalContainer(ModelTypes.Value.forType(String.class), "", "");
		} else
			valueX = ObservableModelQonfigParser.literalContainer(ModelTypes.Value.forType(String.class), element.getValueText(),
				element.getValueText());
		ObservableExpression formatX = session.getAttribute("format", ObservableExpression.class);
		Function<ModelSetInstance, SettableValue<Format<Object>>> format;
		if (valueX == null) {
			if (formatX != null && formatX != ObservableExpression.EMPTY)
				System.err.println("Warning: format specified on label without value");
			String txt = element.getValueText();
			if (txt == null)
				throw new IllegalArgumentException("Either 'text' or 'value' must be specified");
			value = __ -> ObservableModelQonfigParser.literal(txt, txt);
			format = __ -> ObservableModelQonfigParser.literal((Format<Object>) (Format<?>) Format.TEXT, "<unspecified>");
		} else {
			value = valueX;
			valueType = ((ValueContainer<?, ?>) value).getType().getType(0);
			if (formatX == null || formatX == ObservableExpression.EMPTY) {
				format = __ -> ObservableModelQonfigParser.literal((Format<Object>) (Format<?>) LABEL_FORMAT, "<unspecified>");
			} else
				format = formatX.evaluate(
					ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).parameterized(TypeTokens.get().wrap(valueType))), model,
					cv);
		}
		return new AbstractQuickField(element) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				ObservableValue<String> fieldName;
				if (getFieldName() != null)
					fieldName = getFieldName().apply(builder.getModels());
				else
					fieldName = null;
				SettableValue<Object> valueV = value.apply(builder.getModels());
				container.addLabel(fieldName == null ? null : fieldName.get(), valueV, //
					format.apply(builder.getModels()).get(), field -> {
						modify(field, builder);
					});
				return builder.build();
			}
		};
	}

	private <T> QuickComponentDef interpretTable(QonfigElement element, QonfigInterpretingSession session)
		throws QonfigInterpretationException {
		ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
		ClassView cv = (ClassView) session.get("imports");
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
		Function<ModelSetInstance, ObservableCollection<CategoryRenderStrategy<Object, ?>>> columnsAttr//
		= columnsX == null ? null : columnsX.evaluate(ModelTypes.Collection.forType(columnType), model, cv);
		session.put("model-type", rows.getType().getType(0));
		List<Function<ModelSetInstance, CategoryRenderStrategy<Object, ?>>> columns = new ArrayList<>();
		for (QonfigElement columnEl : session.getChildren("column"))
			columns.add(session.getInterpreter().interpret(columnEl, Function.class));
		return new AbstractQuickComponentDef(element) {
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
					for (Function<ModelSetInstance, CategoryRenderStrategy<Object, ?>> column : columns)
						table.withColumn(column.apply(builder.getModels()));
				});
				return builder.build();
			}
		};
	}

	private <T, E extends PanelPopulation.TreeEditor<T, E>> QuickComponentDef interpretTree(QonfigElement element,
		QonfigInterpretingSession session) throws QonfigInterpretationException {
		return interpretAbstractTree(element, session, new TreeMaker<T, E>() {
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

	private <T, E extends PanelPopulation.TreeTableEditor<T, E>> QuickComponentDef interpretTreeTable(QonfigElement element,
		QonfigInterpretingSession session) throws QonfigInterpretationException {
		return interpretAbstractTree(element, session, new TreeMaker<T, E>() {
			TypeToken<CategoryRenderStrategy<BetterList<T>, ?>> columnType;
			Function<ModelSetInstance, ObservableCollection<CategoryRenderStrategy<BetterList<T>, ?>>> columnsAttr;
			List<Function<ModelSetInstance, CategoryRenderStrategy<BetterList<T>, ?>>> columns = new ArrayList<>();

			@Override
			public void configure(ObservableModelSet model, ValueContainer<SettableValue, ? extends SettableValue<T>> root)
				throws QonfigInterpretationException {
				ClassView cv = (ClassView) session.get("imports");
				TypeToken<T> type = (TypeToken<T>) root.getType().getType(0);
				columnType = TypeTokens.get().keyFor(CategoryRenderStrategy.class).<CategoryRenderStrategy<BetterList<T>, ?>> parameterized(//
					TypeTokens.get().keyFor(BetterList.class).parameterized(type), TypeTokens.get().WILDCARD);
				ObservableExpression columnsX = session.getAttribute("columns", ObservableExpression.class);
				columnsAttr = columnsX == null ? null : columnsX.evaluate(ModelTypes.Collection.forType(columnType), model, cv);
				session.put("model-type", type);
				for (QonfigElement columnEl : session.getChildren("column"))
					columns.add(session.getInterpreter().interpret(columnEl, Function.class));
			}

			@Override
			public void makeTree(QuickComponent.Builder builder, PanelPopulator<?, ?> container, ObservableValue<T> root,
				Function<? super BetterList<T>, ? extends ObservableCollection<? extends T>> children, Consumer<E> treeData) {
				container.addTreeTable2(root, children, t -> {
					treeData.accept((E) t);
					if (columnsAttr != null) {
						// The flatten here is so columns can also be specified on the table.
						// Without this, additional columns could not be added if, as is likely, the columnsAttr collection is unmodifiable.
						t.withColumns(ObservableCollection.flattenCollections(columnType, //
							columnsAttr.apply(builder.getModels()), //
							ObservableCollection.build(columnType).build()).collect());
					}
					for (Function<ModelSetInstance, CategoryRenderStrategy<BetterList<T>, ?>> column : columns)
						t.withColumn(column.apply(builder.getModels()));
				});
			}
		});
	}

	interface TreeMaker<T, E extends PanelPopulation.AbstractTreeEditor<T, ?, E>> {
		void configure(ObservableModelSet model, ValueContainer<SettableValue, ? extends SettableValue<T>> root)
			throws QonfigInterpretationException;

		void makeTree(QuickComponent.Builder builder, PanelPopulator<?, ?> container, ObservableValue<T> root,
			Function<? super BetterList<T>, ? extends ObservableCollection<? extends T>> children, Consumer<E> treeData);
	}

	private <T, E extends PanelPopulation.AbstractTreeEditor<T, ?, E>> QuickComponentDef interpretAbstractTree(QonfigElement element,
		QonfigInterpretingSession session, TreeMaker<T, E> treeMaker) throws QonfigInterpretationException {
		QonfigToolkit base = BASE.get();
		System.err.println("WARNING: " + element.getType().getName() + " is not fully implemented!!"); // TODO
		ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
		ClassView cv = (ClassView) session.get("imports");
		ValueContainer<SettableValue, ? extends SettableValue<T>> root = (ValueContainer<SettableValue, ? extends SettableValue<T>>) session
			.getAttribute("root", ObservableExpression.class).evaluate(ModelTypes.Value.any(), model, cv);
		TypeToken<T> valueType = (TypeToken<T>) root.getType().getType(0);
		TypeToken<BetterList<T>> pathType = TypeTokens.get().keyFor(BetterList.class).parameterized(valueType);
		String valueName = session.getAttributeText("value-name");
		session.put("value-name", valueName);
		String renderValueName = session.getAttributeText("render-value-name");
		session.put("render-value-name", renderValueName);
		ObservableModelSet.WrappedBuilder wb = ObservableModelSet.wrap(model);
		ObservableModelSet.ModelValuePlaceholder<SettableValue, SettableValue<BetterList<T>>> pathPlaceholder = wb
			.withPlaceholder(valueName, ModelTypes.Value.forType(pathType));
		ObservableModelSet.ModelValuePlaceholder<SettableValue, SettableValue<T>> cellPlaceholder = wb.withPlaceholder(renderValueName,
			ModelTypes.Value.forType(valueType));
		ObservableModelSet.Wrapped wModel = wb.build();

		Function<ModelSetInstance, ? extends ObservableCollection<? extends T>> children;
		children = element.getAttribute(base.getAttribute("tree", "children"), ObservableExpression.class)
			.evaluate(ModelTypes.Collection.forType(TypeTokens.get().getExtendsWildcard(valueType)), wModel, cv);
		Function<ModelSetInstance, CategoryRenderStrategy<BetterList<T>, T>> treeColumn;
		if (!session.getChildren("tree-column").isEmpty()) {
			session.put("model-type", pathType);
			treeColumn = session.getInterpreter().interpret(session.getChildren("tree-column").getFirst(), Function.class);
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
		treeMaker.configure(wModel, root);
		return new AbstractQuickComponentDef(element) {
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
						if (treeColumn != null)
							tree.withRender(treeColumn.apply(builder.getModels()));
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
		final ObservableModelSet.ModelValuePlaceholder<SettableValue, SettableValue<T>> tabValuePlaceholder;
		final QuickComponentDef content;
		final ValueContainer<SettableValue, SettableValue<T>> tabId;
		final String renderValueName;
		final Function<ModelSetInstance, ? extends ObservableValue<String>> tabName;
		final Function<ModelSetInstance, ? extends ObservableValue<Image>> tabIcon;
		final boolean removable;
		final Function<ModelSetInstance, Consumer<T>> onRemove;
		final Function<ModelSetInstance, Observable<?>> selectOn;
		final Function<ModelSetInstance, Consumer<T>> onSelect;

		private SingleTab(ObservableModelSet models,
			ObservableModelSet.ModelValuePlaceholder<SettableValue, SettableValue<T>> tabValuePlaceholder, QuickComponentDef content,
			ValueContainer<SettableValue, SettableValue<T>> tabId, String renderValueName,
			Function<ModelSetInstance, ? extends ObservableValue<String>> tabName,
				Function<ModelSetInstance, ? extends ObservableValue<Image>> tabIcon, boolean removable,
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

		static <T> SingleTab<T> parse(QonfigElement tab, QonfigInterpretingSession session) throws QonfigInterpretationException {
			QonfigToolkit base = BASE.get();
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			ClassView cv = (ClassView) session.get("imports");
			QuickComponentDef content = session.getInterpreter().interpret(tab, QuickComponentDef.class);
			ValueContainer<SettableValue, SettableValue<T>> tabId = tab.getAttribute(base, "tab", "tab-id", ObservableExpression.class)//
				.evaluate(ModelTypes.Value.forType((TypeToken<T>) TypeTokens.get().WILDCARD), model, cv);
			String renderValueName;
			ObservableModelSet.ModelValuePlaceholder<SettableValue, SettableValue<T>> tvp;
			if (tab.isInstance(base.getElementOrAddOn("single-rendering"))) {
				renderValueName = tab.getAttributeText(base.getAttribute("tab", "render-value-name"));
				ObservableModelSet.WrappedBuilder wb = ObservableModelSet.wrap(model);
				tvp = wb.withPlaceholder(renderValueName, ModelTypes.Value.forType((TypeToken<T>) tabId.getType().getType(0)));
				model = wb.build();
			} else {
				renderValueName = null;
				tvp = null;
			}

			ObservableModelSet fModel = model;
			ObservableExpression tabNameEx = tab.getAttribute(base, "tab", "tab-name", ObservableExpression.class);
			Function<ModelSetInstance, ? extends ObservableValue<String>> tabName;
			if (tabNameEx != null)
				tabName = tabNameEx.evaluate(ModelTypes.Value.forType(String.class), fModel, cv);
			else {
				tabName = (Function<ModelSetInstance, ? extends ObservableValue<String>>) msi -> ObservableValue.of(String.class,
					tabId.get(msi).get().toString());
			}

			ObservableExpression tabIconEx = tab.getAttribute(base, "tab", "tab-icon", ObservableExpression.class);
			Function<ModelSetInstance, ObservableValue<Image>> tabIcon;
			if (tabIconEx != null) {
				ValueContainer<SettableValue, SettableValue<?>> iconV = tabIconEx.evaluate(ModelTypes.Value.any(), fModel, cv);
				Class<?> iconType = TypeTokens.getRawType(iconV.getType().getType(0));
				if (iconType == Image.class)
					tabIcon = (Function<ModelSetInstance, ObservableValue<Image>>) (Function<?, ?>) iconV;
				else if (iconType == URL.class) {
					tabIcon = msi -> {
						ObservableValue<URL> urlFn = (ObservableValue<URL>) iconV.apply(msi);
						// There's probably a better way to do this, but this is what I know
						return urlFn.map(Image.class, url -> url == null ? null : new ImageIcon(url).getImage());
					};
				} else if (iconType == String.class) {
					tabIcon = msi -> {
						ObservableValue<String> strFn = (ObservableValue<String>) iconV.apply(msi);
						return strFn.map(Image.class, str -> {
							if (str == null)
								return null;
							URL url;
							try {
								String location = QommonsConfig.resolve(str, tab.getDocument().getLocation());
								url = QommonsConfig.toUrl(location);
							} catch (IOException e) {
								System.err.println("Could not resolve icon location '" + str + "': " + e);
								return null;
							}
							return new ImageIcon(url).getImage(); // There's probably a better way to do this, but this is what I know
						});
					};
				} else {
					session.withWarning("Cannot interpret tab-icon '" + tab.getAttributes().get(base.getAttribute("tab", "tab-icon"))
						+ "', type " + iconV.getType().getType(0) + " as an image");
					tabIcon = null;
				}
			} else
				tabIcon = null;

			boolean removable = tab.getAttribute(base, "tab", "removable", boolean.class);

			ObservableExpression onRemoveEx = tab.getAttribute(base, "tab", "on-remove", ObservableExpression.class);
			Function<ModelSetInstance, Consumer<T>> onRemove;
			if (onRemoveEx != null) {
				// TODO This should be a value, with the specified renderValueName available
				if (!removable) {
					session.withWarning("on-remove specified, for tab '" + tab.getAttributes().get(base.getAttribute("tab", "tab-id"))
						+ "' but tab is not removable");
					return null;
				} else {
					onRemove = onRemoveEx.<T, Object, Object, Void> findMethod(Void.class, fModel, cv)//
						.withOption0().withOption1((TypeToken<T>) tabId.getType().getType(0), t -> t)//
						.find1().andThen(fn -> t -> fn.apply(t));
				}
			} else
				onRemove = null;

			ObservableExpression selectOnEx = tab.getAttribute(base, "tab", "select-on", ObservableExpression.class);
			Function<ModelSetInstance, ? extends Observable<?>> selectOn;
			if (selectOnEx != null)
				selectOn = selectOnEx.evaluate(ModelTypes.Event.forType(TypeTokens.get().WILDCARD), fModel, cv);
			else
				selectOn = null;

			ObservableExpression onSelectEx = tab.getAttribute(base, "tab", "on-select", ObservableExpression.class);
			Function<ModelSetInstance, Consumer<T>> onSelect;
			if (onSelectEx != null) {
				// TODO This should be a value, with the specified renderValueName available
				onSelect = onSelectEx.<T, Object, Object, Void> findMethod(Void.class, fModel, cv)//
					.withOption0().withOption1((TypeToken<T>) tabId.getType().getType(0), t -> t)//
					.find1().andThen(fn -> t -> fn.apply(t));
			} else
				onSelect = null;

			return new SingleTab<>(model, tvp, content, tabId, renderValueName, tabName, tabIcon, removable, onRemove,
				(Function<ModelSetInstance, Observable<?>>) selectOn, onSelect);
		}
	}

	static class MultiTabSet<T> implements TabSet<T> {
		final ObservableModelSet.Wrapped models;
		final ObservableModelSet.ModelValuePlaceholder<SettableValue, SettableValue<T>> tabValuePlaceholder;
		final QonfigElement element;
		final ValueContainer<ObservableCollection, ObservableCollection<T>> values;
		final String renderValueName;
		final QuickComponentDef content;
		final Function<ModelSetInstance, Function<T, String>> tabName;
		final Function<ModelSetInstance, Function<T, Image>> tabIcon;
		final Function<ModelSetInstance, Function<T, Boolean>> removable;
		final Function<ModelSetInstance, Consumer<T>> onRemove;
		final Function<ModelSetInstance, Function<T, Observable<?>>> selectOn;
		final Function<ModelSetInstance, Consumer<T>> onSelect;

		MultiTabSet(ObservableModelSet.Wrapped models,
			ObservableModelSet.ModelValuePlaceholder<SettableValue, SettableValue<T>> tabValuePlaceholder, QonfigElement element,
			ValueContainer<ObservableCollection, ObservableCollection<T>> values, String renderValueName, QuickComponentDef content,
			Function<ModelSetInstance, Function<T, String>> tabName, Function<ModelSetInstance, Function<T, Image>> tabIcon,
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

		static <T> MultiTabSet<T> parse(QonfigElement tabSet, QonfigInterpretingSession session) throws QonfigInterpretationException {
			QonfigToolkit base = BASE.get();
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			ClassView cv = (ClassView) session.get("imports");
			ValueContainer<ObservableCollection, ObservableCollection<T>> values = session
				.getAttribute("values", ObservableExpression.class)//
				.evaluate(ModelTypes.Collection.forType((TypeToken<T>) TypeTokens.get().WILDCARD), model, cv);
			String renderValueName = session.getAttributeText("render-value-name");
			ObservableModelSet.WrappedBuilder wb = ObservableModelSet.wrap(model);
			ObservableModelSet.ModelValuePlaceholder<SettableValue, SettableValue<T>> tvp = wb.withPlaceholder(renderValueName,
				ModelTypes.Value.forType((TypeToken<T>) values.getType().getType(0)));
			model = wb.build();

			QuickComponentDef content = session.interpretChildren("renderer", QuickComponentDef.class).getFirst();

			ObservableModelSet fModel = model;
			Function<ModelSetInstance, Function<T, String>> tabName = session.interpretAttribute("tab-name", ObservableExpression.class,
				false, ex -> {
					if (ex == null) {
						return msi -> String::valueOf;
					} else {
						return ex.<T, Object, Object, String> findMethod(String.class, fModel, cv).withOption0()
							.withOption1((TypeToken<T>) values.getType().getType(0), a -> a)//
							.find1();
					}
				});

			Function<ModelSetInstance, Function<T, Image>> tabIcon = session.interpretAttribute("tab-icon", ObservableExpression.class,
				true, ex -> {
					MethodFinder<T, Object, Object, Object> finder = ex
						.<T, Object, Object, Object> findMethod((TypeToken<Object>) TypeTokens.get().WILDCARD, fModel, cv).withOption0()
						.withOption1((TypeToken<T>) values.getType().getType(0), a -> a);
					Function<ModelSetInstance, Function<T, Object>> iconFn = finder.find1();
					Class<?> iconType = TypeTokens.getRawType(finder.getResultType());
					if (iconType == Image.class)
						return (Function<ModelSetInstance, Function<T, Image>>) (Function<?, ?>) iconFn;
					else if (iconType == URL.class) {
						return msi -> {
							Function<T, URL> urlFn = (Function<T, URL>) (Function<?, ?>) iconFn.apply(msi);
							return tabValue -> {
								URL url = urlFn.apply(tabValue);
								if (url == null)
									return null;
								return new ImageIcon(url).getImage(); // There's probably a better way to do this, but this is what I know
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
									String location = QommonsConfig.resolve(str, tabSet.getDocument().getLocation());
									url = QommonsConfig.toUrl(location);
								} catch (IOException e) {
									throw new IllegalArgumentException("Could not resolve icon location '" + str + "'", e);
								}
								return new ImageIcon(url).getImage(); // There's probably a better way to do this, but this is what I know
							};
						};
					} else {
						session.withWarning("Cannot interpret tab-icon '" + tabSet.getAttributes().get(base.getAttribute("tab", "tab-icon"))
							+ "', type " + finder.getResultType() + " as an image");
						return null;
					}
				});

			// TODO This should be a value, with the specified renderValueName available
			Function<ModelSetInstance, Function<T, Boolean>> removable = session.interpretAttribute("removable", ObservableExpression.class,
				true, ex -> ex.<T, Object, Object, Boolean> findMethod(boolean.class, fModel, cv)//
				.withOption0().withOption1((TypeToken<T>) values.getType().getType(0), t -> t)//
				.find1());

			// TODO This should be a value, with the specified renderValueName available
			Function<ModelSetInstance, Consumer<T>> onRemove = session.interpretAttribute("on-remove", ObservableExpression.class, true,
				ex -> ex.<T, Object, Object, Void> findMethod(Void.class, fModel, cv)//
				.withOption0().withOption1((TypeToken<T>) values.getType().getType(0), t -> t)//
				.find1().andThen(fn -> t -> fn.apply(t)));

			// TODO This should be a value, with the specified renderValueName available
			Function<ModelSetInstance, Function<T, Observable<?>>> selectOn = session.interpretAttribute("select-on",
				ObservableExpression.class, true,
				ex -> ex
				.<T, Object, Object, Observable<?>> findMethod(TypeTokens.get().keyFor(Observable.class).<Observable<?>> wildCard(),
					fModel, cv)//
				.withOption0().withOption1((TypeToken<T>) values.getType().getType(0), t -> t)//
				.find1());

			// TODO This should be a value, with the specified renderValueName available
			Function<ModelSetInstance, Consumer<T>> onSelect = session.interpretAttribute("on-select", ObservableExpression.class, true,
				ex -> ex.<T, Object, Object, Void> findMethod(Void.class, fModel, cv)//
				.withOption0().withOption1((TypeToken<T>) values.getType().getType(0), t -> t)//
				.find1().andThen(fn -> t -> fn.apply(t)));

			return new MultiTabSet<>((ObservableModelSet.Wrapped) model, tvp, tabSet, values, session.getAttributeText("render-value-name"),
				content, tabName, tabIcon, removable, onRemove, selectOn, onSelect);
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

	private <T> QuickComponentDef createTabs(QonfigElement element, QonfigInterpretingSession session)
		throws QonfigInterpretationException {
		QonfigToolkit base = BASE.get();
		ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
		ClassView cv = (ClassView) session.get("imports");
		List<TabSet<? extends T>> tabs = new ArrayList<>();
		QonfigChildDef.Declared tabSetDef = base.getChild("tabs", "tab-set").getDeclared();
		QonfigChildDef.Declared widgetDef = base.getChild("tabs", "content").getDeclared();
		List<TypeToken<? extends T>> tabTypes = new ArrayList<>();
		for (QonfigElement child : element.getChildren()) {
			if (child.getParentRoles().contains(tabSetDef)) {
				MultiTabSet tabSet = MultiTabSet.parse(child, session);
				tabTypes.add(tabSet.values.getType().getType(0));
				tabs.add(tabSet);
			} else if (child.getParentRoles().contains(widgetDef)) {
				SingleTab tab = SingleTab.parse(child, session);
				tabTypes.add(tab.tabId.getType().getType(0));
				tabs.add(tab);
			}
		}
		TypeToken<T> tabType = TypeTokens.get().getCommonType(tabTypes);
		ValueContainer<SettableValue, SettableValue<T>> selection = session.interpretAttribute("selected", ObservableExpression.class, true,
			ex -> ex.evaluate(ModelTypes.Value.forType(TypeTokens.get().getSuperWildcard(tabType)), model, cv));
		return new AbstractQuickComponentDef(element) {
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
									SettableValue<T> elValue = SettableValue.build(values.getType())
										.withValue(evt.getValues().get(i)).build();
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

	private QuickDocument theDebugDoc;
	private QuickDocument theDebugOverlayDoc;

	private void configureSwing(Builder interpreter) {
		QonfigToolkit swing = SWING.get();
		interpreter.extend(CORE.get().getElement("quick"), swing.getElement("quick-debug"), QuickDocument.class, QuickDocument.class, //
			new QonfigValueExtension<QuickDocument, QuickDocument>() {
			@Override
			public QuickDocument createValue(QuickDocument superValue, QonfigElement element, QonfigInterpretingSession session)
				throws QonfigInterpretationException {

				if (theDebugDoc == null) {
					synchronized (QuickSwingParser.this) {
						if (theDebugDoc == null) {
							QonfigParser debugParser = new DefaultQonfigParser().withToolkit(ObservableModelQonfigParser.TOOLKIT.get(),
								CORE.get(), BASE.get(), swing);
							QonfigInterpreter debugInterp = configureInterpreter(QonfigInterpreter.build(BASE.get(), swing)).build();
							URL debugXml = QuickSwingParser.class.getResource("quick-debug.qml");
							try (InputStream in = debugXml.openStream()) {
								theDebugDoc = debugInterp.interpret(//
									debugParser.parseDocument(debugXml.toString(), in).getRoot(), QuickDocument.class);
							} catch (IOException e) {
								throw new QonfigInterpretationException("Could not read quick-debug.qml", e);
							} catch (QonfigParseException e) {
								throw new QonfigInterpretationException("Could not interpret quick-debug.qml", e);
							}
							debugXml = QuickSwingParser.class.getResource("quick-debug-overlay.qml");
							try (InputStream in = debugXml.openStream()) {
								theDebugOverlayDoc = debugInterp.interpret(//
									debugParser.parseDocument(debugXml.toString(), in).getRoot(), QuickDocument.class);
							} catch (IOException e) {
								throw new QonfigInterpretationException("Could not read quick-debug.qml", e);
							} catch (QonfigParseException e) {
								throw new QonfigInterpretationException("Could not interpret quick-debug.qml", e);
							}
						}
					}
				}

				Function<ModelSetInstance, SettableValue<Integer>> xVal, yVal, wVal, hVal;
				Function<ModelSetInstance, SettableValue<Boolean>> vVal;
				ObservableExpression x = session.getAttribute("debug-x", ObservableExpression.class);
				ObservableExpression y = session.getAttribute("debug-y", ObservableExpression.class);
				ObservableExpression w = session.getAttribute("debug-width", ObservableExpression.class);
				ObservableExpression h = session.getAttribute("debug-height", ObservableExpression.class);
				ObservableExpression v = session.getAttribute("debug-visible", ObservableExpression.class);
				if (x != null) {
					xVal = x.evaluate(ModelTypes.Value.forType(int.class), superValue.getHead().getModels(),
						superValue.getHead().getImports());
				} else {
					xVal = msi -> SettableValue.build(int.class).withValue(0).build();
				}
				if (y != null) {
					yVal = y.evaluate(ModelTypes.Value.forType(int.class), superValue.getHead().getModels(),
						superValue.getHead().getImports());
				} else {
					yVal = msi -> SettableValue.build(int.class).withValue(0).build();
				}
				if (w != null) {
					wVal = w.evaluate(ModelTypes.Value.forType(int.class), superValue.getHead().getModels(),
						superValue.getHead().getImports());
				} else {
					wVal = msi -> SettableValue.build(int.class).withValue(0).build();
				}
				if (h != null) {
					hVal = h.evaluate(ModelTypes.Value.forType(int.class), superValue.getHead().getModels(),
						superValue.getHead().getImports());
				} else {
					hVal = msi -> SettableValue.build(int.class).withValue(0).build();
				}
				if (v != null) {
					vVal = v.evaluate(ModelTypes.Value.forType(boolean.class), superValue.getHead().getModels(),
						superValue.getHead().getImports());
				} else {
					vVal = msi -> SettableValue.build(boolean.class).withValue(true).build();
				}

				Function<ModelSetInstance, SettableValue<QuickComponent>> selectedComponent = theDebugDoc.getHead().getModels()
					.get("debug.selectedComponent", ModelTypes.Value.forType(QuickComponent.class));
				// Function<ModelSetInstance, SettableValue<Integer>> scX, scY, scW, scH;
				// Function<ModelSetInstance, SettableValue<Boolean>> scV;
				// scX=msi->

				return new QuickDocument() {
					@Override
					public QonfigElement getElement() {
						return element;
					}

					@Override
					public QuickHeadSection getHead() {
						return superValue.getHead();
					}

					@Override
					public QuickComponentDef getComponent() {
						return superValue.getComponent();
					}

					@Override
					public Function<ModelSetInstance, SettableValue<String>> getTitle() {
						return superValue.getTitle();
					}

					@Override
					public void setTitle(Function<ModelSetInstance, SettableValue<String>> title) {
						superValue.setTitle(title);
					}

					@Override
					public Function<ModelSetInstance, SettableValue<Image>> getIcon() {
						return superValue.getIcon();
					}

					@Override
					public void setIcon(Function<ModelSetInstance, SettableValue<Image>> icon) {
						superValue.setIcon(icon);
					}

					@Override
					public Function<ModelSetInstance, SettableValue<Integer>> getX() {
						return superValue.getX();
					}

					@Override
					public Function<ModelSetInstance, SettableValue<Integer>> getY() {
						return superValue.getY();
					}

					@Override
					public Function<ModelSetInstance, SettableValue<Integer>> getWidth() {
						return superValue.getWidth();
					}

					@Override
					public Function<ModelSetInstance, SettableValue<Integer>> getHeight() {
						return superValue.getHeight();
					}

					@Override
					public QuickDocument withBounds(Function<ModelSetInstance, SettableValue<Integer>> x,
						Function<ModelSetInstance, SettableValue<Integer>> y, Function<ModelSetInstance, SettableValue<Integer>> width,
						Function<ModelSetInstance, SettableValue<Integer>> height) {
						superValue.withBounds(x, y, width, height);
						return this;
					}

					@Override
					public Function<ModelSetInstance, SettableValue<Boolean>> getVisible() {
						return superValue.getVisible();
					}

					@Override
					public int getCloseAction() {
						return superValue.getCloseAction();
					}

					@Override
					public QuickDocument setVisible(Function<ModelSetInstance, SettableValue<Boolean>> visible) {
						superValue.setVisible(visible);
						return this;
					}

					@Override
					public void setCloseAction(int closeAction) {
						superValue.setCloseAction(closeAction);
					}

					@Override
					public QuickUiDef createUI(ExternalModelSet extModels) {
						return new QuickUiDef(this, extModels) {
							private final QuickUiDef theContentUi;
							private final SettableValue<QuickComponent> theRoot;
							private final SettableValue<Integer> theCursorX;
							private final SettableValue<Integer> theCursorY;
							private final QuickUiDef theDebugUi;
							private final QuickUiDef theDebugOverlayUi;

							{
								theContentUi = superValue.createUI(extModels);
								theRoot = SettableValue.build(QuickComponent.class).build();
								theCursorX = SettableValue.build(int.class).withValue(0).build();
								theCursorY = SettableValue.build(int.class).withValue(0).build();
								theDebugUi = theDebugDoc.createUI(createDebugModel());
								theDebugOverlayUi = theDebugOverlayDoc.createUI(createOverlayModel());
							}

							ExternalModelSet createDebugModel() {
								ObservableModelSet.ExternalModelSetBuilder debugExtModelsBuilder = ObservableModelSet.buildExternal();
								try {
									ObservableModelSet.ExternalModelSetBuilder debugUiModels = debugExtModelsBuilder.addSubModel("ext");
									debugUiModels.with("x", ModelTypes.Value.forType(int.class), xVal.apply(theContentUi.getModels()));
									debugUiModels.with("y", ModelTypes.Value.forType(int.class), yVal.apply(theContentUi.getModels()));
									debugUiModels.with("width", ModelTypes.Value.forType(int.class),
										wVal.apply(theContentUi.getModels()));
									debugUiModels.with("height", ModelTypes.Value.forType(int.class),
										hVal.apply(theContentUi.getModels()));
									debugUiModels.with("ui", ModelTypes.Value.forType(QuickComponent.class), theRoot);
									debugUiModels.with("cursorX", ModelTypes.Value.forType(int.class), theCursorX);
									debugUiModels.with("cursorY", ModelTypes.Value.forType(int.class), theCursorY);
									debugUiModels.with("visible", ModelTypes.Value.forType(boolean.class),
										vVal.apply(theContentUi.getModels()));
								} catch (QonfigInterpretationException e) {
									e.printStackTrace();
								}
								return debugExtModelsBuilder.build();
							}

							ExternalModelSet createOverlayModel() {
								SettableValue<QuickComponent> component = selectedComponent.apply(theDebugUi.getModels());
								SettableValue<Integer> x = SettableValue.build(int.class).withValue(0).build();
								SettableValue<Integer> y = SettableValue.build(int.class).withValue(0).build();
								SettableValue<Integer> w = SettableValue.build(int.class).withValue(0).build();
								SettableValue<Integer> h = SettableValue.build(int.class).withValue(0).build();
								SettableValue<Boolean> v = SettableValue.build(boolean.class).withValue(false).build();
								ComponentAdapter listener = new ComponentAdapter() {
									@Override
									public void componentResized(ComponentEvent e) {
										w.set(e.getComponent().getWidth(), e);
										h.set(e.getComponent().getHeight(), e);
									}

									@Override
									public void componentMoved(ComponentEvent e) {
										x.set(e.getComponent().getX(), e);
										y.set(e.getComponent().getY(), e);
									}
								};
								component.changes().act(evt -> {
									if (evt.getOldValue() == evt.getNewValue())
										return;
									v.set(evt.getNewValue() != null, evt);
									if (evt.getOldValue() != null)
										evt.getOldValue().getComponent().removeComponentListener(listener);
									if (evt.getNewValue() == null) {
										x.set(0, evt);
										y.set(0, evt);
										w.set(0, evt);
										h.set(0, evt);
									} else {
										Component c = evt.getNewValue().getComponent();
										c.addComponentListener(listener);
										x.set(c.getX(), evt);
										y.set(c.getY(), evt);
										w.set(c.getWidth(), evt);
										h.set(c.getHeight(), evt);
									}
								});
								ObservableModelSet.ExternalModelSetBuilder debugExtModelsBuilder = ObservableModelSet.buildExternal();
								try {
									ObservableModelSet.ExternalModelSetBuilder debugUiModels = debugExtModelsBuilder
										.addSubModel("selectedComponent");
									debugUiModels.with("visible", ModelTypes.Value.forType(boolean.class), v);
									debugUiModels.with("x", ModelTypes.Value.forType(int.class), x);
									debugUiModels.with("y", ModelTypes.Value.forType(int.class), y);
									debugUiModels.with("width", ModelTypes.Value.forType(int.class), w);
									debugUiModels.with("height", ModelTypes.Value.forType(int.class), h);
									debugUiModels.with("tooltip", ModelTypes.Value.forType(String.class),
										ObservableModelQonfigParser.literal("Not yet implemented", "tooltip"));
									debugUiModels.with("onMouse", ModelTypes.Action.forType(Void.class),
										ObservableAction.of(TypeTokens.get().VOID, evt -> {
											MouseEvent mEvt = (MouseEvent) evt;
											theCursorX.set(mEvt.getX(), evt);
											theCursorY.set(mEvt.getY(), evt);
											return null;
										}));
								} catch (QonfigInterpretationException e) {
									e.printStackTrace();
								}
								return debugExtModelsBuilder.build();
							}

							@Override
							public JFrame install(JFrame frame) {
								frame = super.install(frame);
								JPanel glassPane = new JPanel();
								glassPane.setOpaque(false);
								theDebugOverlayUi.installContent(glassPane);
								theDebugUi.install(new JDialog());
								return frame;
							}
						};
					}
				};
			}
		});
		interpreter.modifyWith("quick", QuickDocument.class, (value, element, session) -> {
			String lAndFClass;
			switch (element.getAttributeText(swing.getAttribute("quick", "look-and-feel"))) {
			case "system":
				lAndFClass = UIManager.getSystemLookAndFeelClassName();
				break;
			case "cross-platform":
				lAndFClass = UIManager.getCrossPlatformLookAndFeelClassName();
				break;
			default:
				lAndFClass = element.getAttributeText(swing.getAttribute("quick", "look-and-feel"));
				break;
			}
			try {
				UIManager.setLookAndFeel(lAndFClass);
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
				System.err
				.println("Could not load look-and-feel " + element.getAttributeText(swing.getAttribute("quick", "look-and-feel")));
				e.printStackTrace();
			}
			return value;
		});
	}
}

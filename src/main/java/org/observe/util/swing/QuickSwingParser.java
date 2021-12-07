package org.observe.util.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
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
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.assoc.ObservableMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.DefaultExpressoParser;
import org.observe.expresso.ExpressoParser;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableExpression.MethodFinder;
import org.observe.util.ClassView;
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
import org.observe.util.swing.PanelPopulation.WindowBuilder;
import org.qommons.collect.BetterList;
import org.qommons.config.DefaultQonfigParser;
import org.qommons.config.QonfigAttributeDef;
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

	public interface QuickComponentDef {
		QonfigElement getElement();

		Function<ModelSetInstance, ? extends ObservableValue<String>> getFieldName();

		void setFieldName(Function<ModelSetInstance, ? extends ObservableValue<String>> fieldName);

		QuickComponentDef modify(Consumer<ComponentEditor<?, ?>> modification);

		QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder);
	}

	public static class QuickComponent {
		private final QuickComponentDef theDefinition;
		private final Component theComponent;
		private final Map<QonfigAttributeDef, Object> theAttributeValues;
		private final ObservableCollection<QuickComponent> theChildren;
		private ObservableMultiMap<QuickComponentDef, QuickComponent> theGroupedChildren;
		private ObservableValue<Point> theLocation;
		private ObservableValue<Dimension> theSize;

		public QuickComponent(QuickComponentDef definition, Component component, Map<QonfigAttributeDef, Object> attributeValues,
			ObservableCollection<QuickComponent> children) {
			super();
			theDefinition = definition;
			theComponent = component;
			theAttributeValues = attributeValues;
			theChildren = children;
		}

		public QuickComponentDef getDefinition() {
			return theDefinition;
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

		public static Builder build(QuickComponentDef def, ModelSetInstance models) {
			return new Builder(def, models);
		}

		public static class Builder<C> {
			private final QuickComponentDef theDefinition;
			private final ModelSetInstance theModelsInstance;
			private Component theComponent;
			private final Map<QonfigAttributeDef, Object> theAttributeValues;
			private final ObservableCollection<QuickComponent> theChildren;
			private boolean isBuilt;

			public Builder(QuickComponentDef definition, ModelSetInstance models) {
				theDefinition = definition;
				theModelsInstance = models;
				theAttributeValues = new LinkedHashMap<>();
				theChildren = ObservableCollection.build(QuickComponent.class).safe(false).build();
			}

			public ModelSetInstance getModels() {
				return theModelsInstance;
			}

			public Builder withAttribute(QonfigAttributeDef attr, Object value) {
				if (isBuilt)
					throw new IllegalStateException("Already built");
				theAttributeValues.put(attr, value);
				return this;
			}

			public Builder withChild(QuickComponent component) {
				if (isBuilt)
					throw new IllegalStateException("Already built");
				theChildren.add(component);
				return this;
			}

			public Builder withComponent(Component component) {
				if (isBuilt)
					throw new IllegalStateException("Already built");
				theComponent = component;
				return this;
			}

			public QuickComponent build() {
				return new QuickComponent(theDefinition, theComponent, Collections.unmodifiableMap(theAttributeValues),
					theChildren.flow().unmodifiable().collect());
			}
		}
	}

	public static abstract class AbstractQuickComponentDef implements QuickComponentDef {
		private final QonfigElement theElement;
		private Function<ModelSetInstance, ? extends ObservableValue<String>> theFieldName;
		private Consumer<ComponentEditor<?, ?>> theModifications;

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
		public AbstractQuickComponentDef modify(Consumer<ComponentEditor<?, ?>> fieldModification) {
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

		public void modify(ComponentEditor<?, ?> component, QuickComponent.Builder builder) {
			if (theModifications != null)
				theModifications.accept(component);
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
				QuickComponent.Builder childBuilder = QuickComponent.build(childDef, builder.getModels());
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
			QuickComponent.Builder root = QuickComponent.build(theDocument.getComponent(), theModels);
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
	private final ExpressoParser theExpressionParser;

	public QuickSwingParser() {
		theModelParser = new ObservableModelQonfigParser();
		theExpressionParser = new DefaultExpressoParser();
	}

	public Builder configureInterpreter(Builder interpreter) {
		theModelParser.configureInterpreter(interpreter);
		QonfigToolkit core = CORE.get();
		Builder coreInterpreter = interpreter.forToolkit(core);
		coreInterpreter.createWith("quick", QuickDocument.class, (element, session) -> {
			QuickHeadSection head = session.getInterpreter().interpret(
				element.getChildrenByRole().get(core.getChild("quick", "head").getDeclared()).getFirst(), QuickHeadSection.class);
			session.put("quick-cv", head.getImports());
			session.put("quick-model", head.getModels());
			QuickDocument doc = new QuickDocumentImpl(element, head, //
				session.getInterpreter().interpret(element.getChildrenByRole().get(core.getChild("quick", "root").getDeclared()).getFirst(),
					QuickComponentDef.class));
			return doc;
		}).createWith("head", QuickHeadSection.class, (element, session) -> {
			QonfigElement importsEl = element.getChildrenByRole().get(core.getChild("head", "imports").getDeclared()).peekFirst();
			ClassView cv = importsEl == null ? ClassView.build().build() : session.getInterpreter().interpret(importsEl, ClassView.class);
			QonfigElement modelsEl = element.getChildrenByRole().get(core.getChild("head", "models").getDeclared()).peekFirst();
			ObservableModelSet model = modelsEl == null ? null : session.getInterpreter().interpret(modelsEl, ObservableModelSet.class);
			return new QuickHeadSection(cv, model);
		}).modifyWith("window", QuickDocument.class, (doc, element, session) -> {
			ObservableModelSet model = doc.getHead().getModels();
			ObservableExpression visibleEx = element.getAttribute(core.getAttribute("window", "visible"), ObservableExpression.class);
			if (visibleEx != null)
				doc.setVisible(
					visibleEx.evaluate(ModelTypes.Value.forType(Boolean.class), doc.getHead().getModels(), doc.getHead().getImports()));
			ObservableExpression titleEx = element.getAttribute(core.getAttribute("window", "title"), ObservableExpression.class);
			if (titleEx != null)
				doc.setTitle(titleEx.evaluate(ModelTypes.Value.forType(String.class), model, doc.getHead().getImports()));
			ObservableExpression x = element.getAttribute(core.getAttribute("window", "x"), ObservableExpression.class);
			ObservableExpression y = element.getAttribute(core.getAttribute("window", "y"), ObservableExpression.class);
			ObservableExpression w = element.getAttribute(core.getAttribute("window", "width"), ObservableExpression.class);
			ObservableExpression h = element.getAttribute(core.getAttribute("window", "height"), ObservableExpression.class);
			doc.withBounds(//
				x == null ? null : x.evaluate(ModelTypes.Value.forType(Integer.class), model, doc.getHead().getImports()), //
					y == null ? null : y.evaluate(ModelTypes.Value.forType(Integer.class), model, doc.getHead().getImports()), //
						w == null ? null : w.evaluate(ModelTypes.Value.forType(Integer.class), model, doc.getHead().getImports()), //
							h == null ? null : h.evaluate(ModelTypes.Value.forType(Integer.class), model, doc.getHead().getImports())//
				);
			switch (element.getAttributeText(core.getAttribute("window", "close-action"))) {
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
		});
		if (BASE.isBuilt() && BASE.isValid() && interpreter.dependsOn(BASE.get()))
			configureBase(interpreter.forToolkit(BASE.get()));
		if (SWING.isBuilt() && SWING.isValid() && interpreter.dependsOn(SWING.get()))
			configureSwing(interpreter.forToolkit(SWING.get()));
		return interpreter;
	}

	@SuppressWarnings("rawtypes")
	void configureBase(Builder interpreter) {
		QonfigToolkit base = interpreter.getToolkit();
		interpreter.createWith("box", QuickBox.class, (element, session) -> {
			List<QuickComponentDef> children = new ArrayList<>(5);
			for (QonfigElement child : element.getChildrenByRole().get(base.getChild("box", "content").getDeclared()))
				children.add(session.getInterpreter().interpret(child, QuickComponentDef.class));
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
				child.modify(ch -> ch.withLayoutConstraints(layoutConstraint));
			}
			return value;
		});
		interpreter.modifyWith("inline", QuickBox.class, (value, element, session) -> {
			JustifiedBoxLayout layout = new JustifiedBoxLayout(
				element.getAttributeText(base.getAttribute("inline", "orientation")).equals("vertical"));
			String mainAlign = element.getAttributeText(base.getAttribute("inline", "main-align"));
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
			String crossAlign = element.getAttributeText(base.getAttribute("inline", "cross-align"));
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
			ObservableExpression valueX = element.getAttribute(base.getAttribute("text-field", "value"), ObservableExpression.class);
			ObservableExpression formatX = element.getAttribute(base.getAttribute("text-field", "format"), ObservableExpression.class);
			Function<ModelSetInstance, SettableValue<Format<Object>>> format;
			String columnsStr = element.getAttributeText(base.getAttribute("text-field", "columns"));
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
			ObservableExpression valueX = element.getAttribute(base.getAttribute("button", "text"), ObservableExpression.class);
			if (valueX == null) {
				String txt = element.getValueText();
				if (txt == null)
					throw new IllegalArgumentException("Either 'text' attribute or element value must be specified");
				buttonText = __ -> ObservableModelQonfigParser.literal(txt, txt);
			} else
				buttonText = valueX.evaluate(ModelTypes.Value.forType(String.class), model, cv);
			Function<ModelSetInstance, ? extends ObservableAction> action = model
				.get(element.getAttributeText(base.getAttribute("button", "action")), ModelTypes.Action);
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
			System.err.println("WARNING: split is not fully implemented!!"); // TODO
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			ClassView cv = (ClassView) session.get("imports");
			TypeToken<Object> modelType = (TypeToken<Object>) session.get("model-type");
			String name = element.getAttributeText(base.getAttribute("column", "name"));
			ObservableExpression valueX = element.getAttribute(base.getAttribute("column", "value"), ObservableExpression.class);
			TypeToken<Object> columnType;
			Function<ModelSetInstance, Function<Object, Object>> valueFn;
			if (valueX != null) {
				MethodFinder<Object, Object, Object, Object> finder = valueX.findMethod(TypeTokens.get().OBJECT, model, cv)//
					.withOption(BetterList.of(modelType), new ObservableExpression.ArgMaker<Object, Object, Object>() {
						@Override
						public void makeArgs(Object t, Object u, Object v, Object[] args, ModelSetInstance models) {
							args[0] = t;
						}
					});
				columnType = (TypeToken<Object>) finder.getResultType();
				valueFn = finder.find1();
			} else {
				valueFn = msi -> v -> v;
				columnType = modelType;
			}
			return extModels -> {
				CategoryRenderStrategy<Object, Object> column = new CategoryRenderStrategy<>(name, columnType,
					valueFn.apply((ModelSetInstance) extModels));
				return column;
			};
		});
		interpreter.createWith("columns", Void.class, (element, session) -> {
			ObservableModelSet.Builder models = (ObservableModelSet.Builder) session.get("model");
			session.put("quick-model", models);
			ClassView cv = (ClassView) session.get("imports");
			TypeToken<Object> rowType = (TypeToken<Object>) ObservableModelQonfigParser.parseType(element.getAttributeText(//
				base.getAttribute("columns", "type")), cv);
			session.put("model-type", rowType);
			TypeToken<CategoryRenderStrategy<Object, ?>> columnType = TypeTokens.get().keyFor(CategoryRenderStrategy.class)
				.<CategoryRenderStrategy<Object, ?>> parameterized(rowType, TypeTokens.get().WILDCARD);
			List<Function<ModelSetInstance, CategoryRenderStrategy<Object, ?>>> columns = new ArrayList<>();
			for (QonfigElement columnEl : element.getChildrenByRole().get(base.getChild("columns", "column").getDeclared())) {
				columns.add(session.getInterpreter().interpret(columnEl, Function.class));
			}
			models.with(element.getAttributeText(base.getAttribute("columns", "name")), ModelTypes.Collection.forType(columnType), //
				(msi, extModels) -> {
					List<CategoryRenderStrategy<Object, ?>> columnInstances = new ArrayList<>(columns.size());
					for (Function<ModelSetInstance, CategoryRenderStrategy<Object, ?>> column : columns)
						columnInstances.add(column.apply(msi));
					return ObservableCollection.of(columnType, columnInstances);
				});
			return null;
		});
		interpreter.createWith("split", QuickComponentDef.class, (element, session) -> {
			if (element.getChildrenByRole().get(base.getChild("split", "content").getDeclared()).size() != 2)
				throw new UnsupportedOperationException("Currently only 2 (and exactly 2) contents are supported for split");
			List<QuickComponentDef> children = new ArrayList<>(2);
			for (QonfigElement child : element.getChildrenByRole().get(base.getChild("split", "content").getDeclared()))
				children.add(session.getInterpreter().interpret(child, QuickComponentDef.class));
			boolean vertical = element.getAttributeText(base.getAttribute("split", "orientation")).equals("vertical");
			System.err.println("WARNING: split is not fully implemented!!"); // TODO
			return new AbstractQuickComponentDef(element) {
				@Override
				public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
					container.addSplit(vertical, split -> {
						modify(split.fill().fillV(), builder);
						split.firstH(new BorderLayout(), first -> {
							QuickComponent.Builder<?> child = QuickComponent.build(children.get(0), builder.getModels());
							children.get(0).install(first, child);
							builder.withChild(child.build());
						});
						split.lastH(new BorderLayout(), last -> {
							QuickComponent.Builder<?> child = QuickComponent.build(children.get(1), builder.getModels());
							children.get(1).install(last, child);
							builder.withChild(child.build());
						});
					});
					return builder.build();
				}
			};
		});
		interpreter.createWith("field-panel", QuickComponentDef.class, (element, session) -> {
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			ClassView cv = (ClassView) session.get("imports");
			List<QuickComponentDef> children = new ArrayList<>();
			for (QonfigElement child : element.getChildrenByRole().get(base.getChild("field-panel", "content").getDeclared())) {
				QuickComponentDef childC = session.getInterpreter().interpret(child, QuickComponentDef.class);
				ObservableExpression fieldName = child.getAttribute(base.getAttribute("field", "field-name"), ObservableExpression.class);
				if (fieldName != null)
					childC.setFieldName(fieldName.evaluate(ModelTypes.Value.forType(String.class), model, cv));
				if (Boolean.TRUE.equals(child.getAttribute(base.getAttribute("field", "fill"), Boolean.class)))
					childC.modify(f -> f.fill());
				children.add(childC);
			}
			System.err.println("WARNING: field-panel is not fully implemented!!"); // TODO
			return new AbstractQuickComponentDef(element) {
				@Override
				public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
					container.addVPanel(panel -> {
						modify(panel, builder);
						for (QuickComponentDef child : children) {
							QuickComponent.Builder<?> childBuilder = QuickComponent.build(child, builder.getModels());
							child.install(panel, childBuilder);
							builder.withChild(childBuilder.build());
						}
					});
					return builder.build();
				}
			};
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
		interpreter.createWith("text-area", QuickComponentDef.class, (element, session) -> {
			ClassView cv = (ClassView) session.get("quick-cv");
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			ValueContainer<SettableValue, ?> value;
			ObservableExpression valueX = element.getAttribute(base.getAttribute("text-area", "value"), ObservableExpression.class);
			ObservableExpression formatX = element.getAttribute(base.getAttribute("text-area", "format"), ObservableExpression.class);
			Function<ModelSetInstance, SettableValue<Format<Object>>> format;
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
			ValueContainer<SettableValue, SettableValue<Integer>> rows;
			if (element.getAttributes().get(base.getAttribute("text-area", "rows")) != null)
				rows = element.getAttribute(base.getAttribute("text-area", "rows"), ObservableExpression.class)
				.evaluate(ModelTypes.Value.forType(Integer.class), model, cv);
			else
				rows = null;
			ValueContainer<SettableValue, SettableValue<Boolean>> html;
			if (element.getAttributes().get(base.getAttribute("text-area", "html")) != null)
				html = element.getAttribute(base.getAttribute("text-area", "html"), ObservableExpression.class)
				.evaluate(ModelTypes.Value.forType(Boolean.class), model, cv);
			else
				html = null;
			ValueContainer<SettableValue, SettableValue<Boolean>> editable;
			if (element.getAttributes().get(base.getAttribute("text-area", "editable")) != null)
				editable = element.getAttribute(base.getAttribute("text-area", "editable"), ObservableExpression.class)
				.evaluate(ModelTypes.Value.forType(Boolean.class), model, cv);
			else
				editable = null;
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
			value = element.getAttribute(base.getAttribute("check-box", "value"), ObservableExpression.class)
				.evaluate(ModelTypes.Value.forType(boolean.class), model, cv);
			ValueContainer<SettableValue, SettableValue<String>> text;
			if (element.getValue() != null)
				text = ((ObservableExpression) element.getValue()).evaluate(ModelTypes.Value.forType(String.class), model, cv);
			else
				text = null;
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
			value = (ValueContainer<SettableValue, ? extends SettableValue<Object>>) element
				.getAttribute(base.getAttribute("radio-buttons", "value"), ObservableExpression.class)//
				.evaluate(ModelTypes.Value.any(), model, cv);
			ValueContainer<SettableValue, SettableValue<Object[]>> values = element
				.getAttribute(base.getAttribute("radio-buttons", "values"), ObservableExpression.class)
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
		interpreter.createWith("file-button", QuickComponentDef.class, (element, session) -> {
			ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
			ClassView cv = (ClassView) session.get("quick-cv");
			ValueContainer<SettableValue, ? extends SettableValue<Object>> value;
			value = (ValueContainer<SettableValue, ? extends SettableValue<Object>>) element
				.getAttribute(base.getAttribute("file-button", "value"), ObservableExpression.class)//
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
		QonfigToolkit base = BASE.get();
		ClassView cv = (ClassView) session.get("quick-cv");
		ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
		Function<ModelSetInstance, ? extends SettableValue> value;
		TypeToken<?> valueType;
		ValueContainer<SettableValue, ?> valueX = element.getAttribute(base.getAttribute("label", "value"), ObservableExpression.class)//
			.evaluate(ModelTypes.Value.any(), model, cv);
		ObservableExpression formatX = element.getAttribute(base.getAttribute("label", "format"), ObservableExpression.class);
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

	private QuickComponentDef interpretTable(QonfigElement element, QonfigInterpretingSession session)
		throws QonfigInterpretationException {
		QonfigToolkit base = BASE.get();
		ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
		ClassView cv = (ClassView) session.get("imports");
		ValueContainer<ObservableCollection, ?> rows = element.getAttribute(base.getAttribute("table", "rows"), ObservableExpression.class)
			.evaluate(ModelTypes.Collection.any(), model, cv);
		Function<ModelSetInstance, SettableValue<Object>> selectionV;
		Function<ModelSetInstance, ObservableCollection<Object>> selectionC;
		ObservableExpression selectionS = element.getAttribute(base.getAttribute("table", "selection"), ObservableExpression.class);
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
		ObservableExpression columnsX = element.getAttribute(base.getAttribute("table", "columns"), ObservableExpression.class);
		Function<ModelSetInstance, ObservableCollection<CategoryRenderStrategy<Object, ?>>> columnsAttr//
		= columnsX == null ? null : columnsX.evaluate(ModelTypes.Collection.forType(columnType), model, cv);
		session.put("model-type", rows.getType().getType(0));
		List<Function<ModelSetInstance, CategoryRenderStrategy<Object, ?>>> columns = new ArrayList<>();
		for (QonfigElement columnEl : element.getChildrenByRole().get(base.getChild("table", "column").getDeclared()))
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
							ObservableCollection.build(columnType).safe(false).build()).collect());
					}
					for (Function<ModelSetInstance, CategoryRenderStrategy<Object, ?>> column : columns)
						table.withColumn(column.apply(builder.getModels()));
				});
				return builder.build();
			}
		};
	}

	private <T> QuickComponentDef interpretTree(QonfigElement element, QonfigInterpretingSession session)
		throws QonfigInterpretationException {
		QonfigToolkit base = BASE.get();
		System.err.println("WARNING: tree is not fully implemented!!"); // TODO
		ObservableModelSet model = (ObservableModelSet) session.get("quick-model");
		ClassView cv = (ClassView) session.get("imports");
		ValueContainer<SettableValue, ? extends SettableValue<T>> root = (ValueContainer<SettableValue, ? extends SettableValue<T>>) element
			.getAttribute(base.getAttribute("tree", "root"), ObservableExpression.class).evaluate(ModelTypes.Value.any(), model, cv);
		TypeToken<T> valueType = (TypeToken<T>) root.getType().getType(0);
		Function<ModelSetInstance, Function<T, ObservableCollection<? extends T>>> children;
		children = element.getAttribute(base.getAttribute("tree", "children"), ObservableExpression.class)
			.<T, Object, Object, ObservableCollection<? extends T>> findMethod(TypeTokens.get().keyFor(ObservableCollection.class)
				.<ObservableCollection<? extends T>> parameterized(TypeTokens.get().getExtendsWildcard(valueType)), model, cv)//
			.withOption1(valueType, null)//
			.find1();
		Function<ModelSetInstance, Function<T, T>> parent;
		ObservableExpression parentEx = element.getAttribute(base.getAttribute("tree", "parent"), ObservableExpression.class);
		if (parentEx != null) {
			parent = parentEx.<T, Object, Object, T> findMethod(valueType, model, cv)//
				.withOption1(valueType, null)//
				.find1();
		} else
			parent = null;
		ValueContainer<ObservableCollection, ObservableCollection<T>> multiSelectionV;
		ValueContainer<ObservableCollection, ObservableCollection<BetterList<T>>> multiSelectionPath;
		ValueContainer<SettableValue, SettableValue<T>> singleSelectionV;
		ValueContainer<SettableValue, SettableValue<BetterList<T>>> singleSelectionPath;
		ObservableExpression selectionEx = element.getAttribute(base.getAttribute("tree", "selection"), ObservableExpression.class);
		if (selectionEx == null) {
			multiSelectionV = null;
			multiSelectionPath = null;
			singleSelectionV = null;
			singleSelectionPath = null;
		} else {
			ValueContainer<?, ?> selection = selectionEx.evaluate(//
				null, model, cv);
			ValueContainer<Object, Object> hackS = (ValueContainer<Object, Object>) selection;
			if (selection.getType().getModelType() == ModelTypes.Value) {
				multiSelectionV = null;
				multiSelectionPath = null;
				if (TypeTokens.get().isAssignable(valueType, selection.getType().getType(0))) {
					if (parent == null)
						throw new QonfigInterpretationException("parent function most be provided for value selection."
							+ " Specify parent as a function which takes a value and returns its parent");
					singleSelectionPath = null;
					singleSelectionV = hackS.getType().as(hackS, ModelTypes.Value.forType(valueType));
				} else if (TypeTokens.get().isAssignable(TypeTokens.get().keyFor(BetterList.class).parameterized(valueType),
					selection.getType().getType(0))) {
					singleSelectionV = null;
					singleSelectionPath = hackS.getType().as(hackS, ModelTypes.Value.forType(//
						TypeTokens.get().keyFor(BetterList.class).parameterized(valueType)));
				} else
					throw new QonfigInterpretationException(
						"Value " + selectionEx + ", type " + selection.getType() + " cannot be used for tree selection");
			} else if (selection.getType().getModelType() == ModelTypes.Collection
				|| selection.getType().getModelType() == ModelTypes.SortedCollection || selection.getType().getModelType() == ModelTypes.Set
				|| selection.getType().getModelType() == ModelTypes.SortedSet) {
				singleSelectionV = null;
				singleSelectionPath = null;
				throw new UnsupportedOperationException("Not yet implemented");
			} else
				throw new QonfigInterpretationException(
					"Value " + selectionEx + ", type " + selection.getType() + " cannot be used for tree selection");
		}
		return new AbstractQuickComponentDef(element) {
			@Override
			public QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
				container.addTree(root.apply(builder.getModels()), children.apply(builder.getModels()), tree -> {
					modify(tree.fill().fillV(), builder);
					if (singleSelectionV != null)
						tree.withSelection(singleSelectionV.apply(builder.getModels()), parent.apply(builder.getModels()), false);
					else if (singleSelectionPath != null)
						tree.withSelection(singleSelectionPath.apply(builder.getModels()), false);
					else if (multiSelectionV != null)
						tree.withSelection(multiSelectionV.apply(builder.getModels()), parent.apply(builder.getModels()));
					else if (multiSelectionPath != null)
						tree.withSelection(multiSelectionPath.apply(builder.getModels()));
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
				ObservableExpression x = element.getAttribute(swing.getAttribute("quick-debug", "debug-x"), ObservableExpression.class);
				ObservableExpression y = element.getAttribute(swing.getAttribute("quick-debug", "debug-y"), ObservableExpression.class);
				ObservableExpression w = element.getAttribute(swing.getAttribute("quick-debug", "debug-width"),
					ObservableExpression.class);
				ObservableExpression h = element.getAttribute(swing.getAttribute("quick-debug", "debug-height"),
					ObservableExpression.class);
				ObservableExpression v = element.getAttribute(swing.getAttribute("quick-debug", "debug-visible"),
					ObservableExpression.class);
				if (x != null) {
					xVal = x.evaluate(ModelTypes.Value.forType(int.class), superValue.getHead().getModels(),
						superValue.getHead().getImports());
				} else {
					xVal = msi -> SettableValue.build(int.class).safe(false).withValue(0).build();
				}
				if (y != null) {
					yVal = y.evaluate(ModelTypes.Value.forType(int.class), superValue.getHead().getModels(),
						superValue.getHead().getImports());
				} else {
					yVal = msi -> SettableValue.build(int.class).safe(false).withValue(0).build();
				}
				if (w != null) {
					wVal = w.evaluate(ModelTypes.Value.forType(int.class), superValue.getHead().getModels(),
						superValue.getHead().getImports());
				} else {
					wVal = msi -> SettableValue.build(int.class).safe(false).withValue(0).build();
				}
				if (h != null) {
					hVal = h.evaluate(ModelTypes.Value.forType(int.class), superValue.getHead().getModels(),
						superValue.getHead().getImports());
				} else {
					hVal = msi -> SettableValue.build(int.class).safe(false).withValue(0).build();
				}
				if (v != null) {
					vVal = v.evaluate(ModelTypes.Value.forType(boolean.class), superValue.getHead().getModels(),
						superValue.getHead().getImports());
				} else {
					vVal = msi -> SettableValue.build(boolean.class).safe(false).withValue(true).build();
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
							private final SettableValue<Integer> theCursorX;
							private final SettableValue<Integer> theCursorY;
							private final QuickUiDef theDebugUi;
							private final QuickUiDef theDebugOverlayUi;

							{
								theContentUi = superValue.createUI(extModels);
								theCursorX = SettableValue.build(int.class).safe(false).withValue(0).build();
								theCursorY = SettableValue.build(int.class).safe(false).withValue(0).build();
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
									debugUiModels.with("ui", ModelTypes.Value.forType(QuickUiDef.class),
										ObservableModelQonfigParser.literal(theContentUi, "ui"));
									debugUiModels.with("cursorX", ModelTypes.Value.forType(int.class), theCursorX);
									debugUiModels.with("cursorY", ModelTypes.Value.forType(int.class), theCursorY);
								} catch (QonfigInterpretationException e) {
									e.printStackTrace();
								}
								return debugExtModelsBuilder.build();
							}

							ExternalModelSet createOverlayModel() {
								SettableValue<QuickComponent> component = selectedComponent.apply(theDebugUi.getModels());
								SettableValue<Integer> x = SettableValue.build(int.class).safe(false).withValue(0).build();
								SettableValue<Integer> y = SettableValue.build(int.class).safe(false).withValue(0).build();
								SettableValue<Integer> w = SettableValue.build(int.class).safe(false).withValue(0).build();
								SettableValue<Integer> h = SettableValue.build(int.class).safe(false).withValue(0).build();
								SettableValue<Boolean> v = SettableValue.build(boolean.class).safe(false).withValue(false).build();
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

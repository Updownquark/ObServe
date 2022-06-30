package org.observe.util.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.LayoutManager2;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;

import org.jdesktop.swingx.JXCollapsiblePane;
import org.jdesktop.swingx.JXPanel;
import org.jdesktop.swingx.JXTreeTable;
import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.observe.dbug.Dbug;
import org.observe.dbug.DbugAnchorType;
import org.observe.util.TypeTokens;
import org.observe.util.swing.PanelPopulationImpl.SimpleAlert;
import org.qommons.BreakpointHere;
import org.qommons.StringUtils;
import org.qommons.collect.BetterList;
import org.qommons.io.Format;
import org.qommons.threading.QommonsTimer;

import com.google.common.reflect.TypeToken;

/** This utility class simplifies the population of many styles of user interface */
public class PanelPopulation {
	// TODO Replace "MIG" everywhere here with "grid"

	/**
	 * <p>
	 * This method creates an API structure that makes building a panel of vertical fields very easy.
	 * </p>
	 * <p>
	 * This API uses net.miginfocom.swing.MigLayout, but this class is not packaged with ObServe, so it must be provided by the caller. If
	 * the panel's layout is not a MigLayout (or the panel is null), the API will attempt to reflectively instantiate one, so MigLayout must
	 * be on the classpath.
	 * </p>
	 * <p>
	 * If the MigLayout is set for the container's layout, its fillx and hidemode 3 layout constraints should be set.
	 * </p>
	 *
	 * @param <C> The type of the container
	 * @param panel The container to add the field widgets to. If null, a new {@link JPanel} will be created (be sure
	 *        net.miginfocom.swing.MigLayout is in your classpath to use this).
	 * @param until The observable that, when fired, will release all associated resources
	 * @return The API structure to add fields with
	 */
	public static <C extends Container> PanelPopulator<C, ?> populateVPanel(C panel, Observable<?> until) {
		if (!EventQueue.isDispatchThread())
			System.err.println(
				"Calling panel population off of the EDT from " + BreakpointHere.getCodeLine(1) + "--could cause threading problems!!");
		if (panel == null)
			panel = (C) new ConformingPanel();
		return new PanelPopulationImpl.MigFieldPanel<>(panel, until == null ? Observable.empty() : until);
	}

	public static <C extends Container> PanelPopulator<C, ?> populateHPanel(C panel, String layoutType, Observable<?> until) {
		if (!EventQueue.isDispatchThread())
			System.err.println(
				"Calling panel population off of the EDT from " + BreakpointHere.getCodeLine(1) + "--could cause threading problems!!");
		return populateHPanel(panel, layoutType == null ? null : makeLayout(layoutType), until);
	}

	public static <C extends Container> PanelPopulator<C, ?> populateHPanel(C panel, LayoutManager layout, Observable<?> until) {
		if (!EventQueue.isDispatchThread())
			System.err.println(
				"Calling panel population off of the EDT from " + BreakpointHere.getCodeLine(1) + "--could cause threading problems!!");
		if (panel == null)
			panel = (C) new ConformingPanel(layout);
		else if (layout != null)
			panel.setLayout(layout);
		return new PanelPopulationImpl.SimpleHPanel<>(null, panel, until == null ? Observable.empty() : until);
	}

	/**
	 * Populates a panel without installing a new layout
	 *
	 * @param <C> The type of the container
	 * @param panel The container to add the field widgets to
	 * @param until The observable that, when fired, will release all associated resources
	 * @return The API structure to add fields with
	 */
	public static <C extends Container> PanelPopulator<C, ?> populatePanel(C panel, Observable<?> until) {
		if (!EventQueue.isDispatchThread())
			System.err.println(
				"Calling panel population off of the EDT from " + BreakpointHere.getCodeLine(1) + "--could cause threading problems!!");
		return new PanelPopulationImpl.SimpleHPanel<>(null, panel, until == null ? Observable.empty() : until);
	}

	public static <R> TableBuilder<R, ?> buildTable(ObservableCollection<R> rows) {
		if (!EventQueue.isDispatchThread())
			System.err.println(
				"Calling panel population off of the EDT from " + BreakpointHere.getCodeLine(1) + "--could cause threading problems!!");
		return new SimpleTableBuilder<>(rows, Observable.empty());
	}

	public interface PanelPopulator<C extends Container, P extends PanelPopulator<C, P>> extends ComponentEditor<C, P> {
		<F> P addTextField(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<FieldEditor<ObservableTextField<F>, ?>> modify);

		<F> P addTextArea(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<FieldEditor<ObservableTextArea<F>, ?>> modify);

		default P addLabel(String fieldName, String label, Consumer<FieldEditor<JLabel, ?>> modify) {
			return addLabel(fieldName, ObservableValue.of(label), v -> v, modify);
		}

		default <F> P addLabel(String fieldName, ObservableValue<F> field, Format<F> format, Consumer<FieldEditor<JLabel, ?>> modify) {
			return addLabel(fieldName, field, v -> format.format(v), modify);
		}

		<F> P addLabel(String fieldName, ObservableValue<F> field, Function<? super F, String> format,
			Consumer<FieldEditor<JLabel, ?>> modify);

		P addIcon(String fieldName, ObservableValue<Icon> icon, Consumer<FieldEditor<JLabel, ?>> modify);

		<F> P addLink(String fieldName, ObservableValue<F> field, Function<? super F, String> format, Consumer<Object> action,
			Consumer<FieldEditor<JLabel, ?>> modify);

		P addCheckField(String fieldName, SettableValue<Boolean> field, Consumer<FieldEditor<JCheckBox, ?>> modify);

		P addToggleButton(String fieldName, SettableValue<Boolean> field, String text, Consumer<ButtonEditor<JToggleButton, ?>> modify);

		/* TODO
		 * slider
		 * progress bar
		 * spinner
		 * offloaded actions
		 * menu button
		 * (multi-) split pane (with configurable splitter panel)
		 * scroll pane
		 * value selector
		 * settings menu
		 * rotation
		 * form controls (e.g. press enter in a text field and a submit action (also tied to a button) fires)
		 * styles: borders, background...
		 *
		 * Dragging!!!
		 *
		 * Complete common locking (RRWL, CLS)
		 */

		default P addIntSpinnerField(String fieldName, SettableValue<Integer> value,
			Consumer<SteppedFieldEditor<JSpinner, Integer, ?>> modify) {
			return addSpinnerField(fieldName, new JSpinner(new SpinnerNumberModel(0, Integer.MIN_VALUE, Integer.MAX_VALUE, 1)), value,
				Number::intValue, modify);
		}

		default P addDoubleSpinnerField(String fieldName, SettableValue<Double> value,
			Consumer<SteppedFieldEditor<JSpinner, Double, ?>> modify) {
			return addSpinnerField(fieldName,
				new JSpinner(new SpinnerNumberModel(0.0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1.0)), value,
				Number::doubleValue, modify);
		}

		<F> P addSpinnerField(String fieldName, JSpinner spinner, SettableValue<F> value, Function<? super F, ? extends F> purifier,
			Consumer<SteppedFieldEditor<JSpinner, F, ?>> modify);

		P addMultiSlider(String fieldName, ObservableCollection<Double> values, Consumer<SliderEditor<?, ?>> modify);

		default <F> P addComboField(String fieldName, SettableValue<F> value, Consumer<ComboEditor<F, ?>> modify, F... availableValues) {
			return addComboField(fieldName, value, Arrays.asList(availableValues), modify);
		}

		<F> P addComboField(String fieldName, SettableValue<F> value, List<? extends F> availableValues,
			Consumer<ComboEditor<F, ?>> modify);

		P addFileField(String fieldName, SettableValue<File> value, boolean open, Consumer<FieldEditor<ObservableFileButton, ?>> modify);

		default <F> P addRadioField(String fieldName, SettableValue<F> value, F[] values,
			Consumer<ToggleEditor<F, JRadioButton, ?>> modify) {
			return addToggleField(fieldName, value, Arrays.asList(values), JRadioButton.class, __ -> new JRadioButton(), modify);
		}

		<F, TB extends JToggleButton> P addToggleField(String fieldName, SettableValue<F> value, List<? extends F> values,
			Class<TB> buttonType, Function<? super F, ? extends TB> buttonCreator, Consumer<ToggleEditor<F, TB, ?>> modify);

		// public <F> MigPanelPopulatorField<F, JToggleButton> addToggleField(String fieldName, SettableValue<F> value,
		// boolean radio, F... availableValues) {
		// return addToggleField(fieldName, value, radio, Arrays.asList(availableValues));
		// }
		//
		// public <F> MigPanelPopulatorField<F, JToggleButton> addToggleField(String fieldName, SettableValue<F> value, boolean radio,
		// List<? extends F> availableValues) {
		// }
		//
		// public <F> MigPanelPopulatorField<F, JSlider> addSliderField(String fieldName, SettableValue<Integer> value) {}
		//
		// public <F> MigPanelPopulatorField<F, ObservableTreeModel> addTree(Object root,
		// Function<Object, ? extends ObservableCollection<?>> branching) {}
		//
		// P addMultiple(String fieldName, Consumer<PanelPopulator<JPanel, ?>> panel);

		default P addButton(String buttonText, Consumer<Object> action, Consumer<ButtonEditor<JButton, ?>> modify) {
			return addButton(buttonText, new ObservableAction<Void>() {
				@Override
				public TypeToken<Void> getType() {
					return TypeTokens.get().of(Void.class);
				}

				@Override
				public Void act(Object cause) throws IllegalStateException {
					action.accept(cause);
					return null;
				}

				@Override
				public ObservableValue<String> isEnabled() {
					return SettableValue.ALWAYS_ENABLED;
				}
			}, modify);
		}

		P addButton(String buttonText, ObservableAction<?> action, Consumer<ButtonEditor<JButton, ?>> modify);

		P addProgressBar(String fieldName, Consumer<ProgressEditor<?>> progress);

		<R> P addList(ObservableCollection<R> rows, Consumer<ListBuilder<R, ?>> list);

		<R> P addTable(ObservableCollection<R> rows, Consumer<TableBuilder<R, ?>> table);

		<F> P addTree(ObservableValue<? extends F> root, Function<? super F, ? extends ObservableCollection<? extends F>> children,
			Consumer<TreeEditor<F, ?>> modify);

		<F> P addTree2(ObservableValue<? extends F> root,
			Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> children, Consumer<TreeEditor<F, ?>> modify);

		<F> P addTreeTable(ObservableValue<F> root, Function<? super F, ? extends ObservableCollection<? extends F>> children,
			Consumer<TreeTableEditor<F, ?>> modify);

		<F> P addTreeTable2(ObservableValue<F> root, Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> children,
			Consumer<TreeTableEditor<F, ?>> modify);

		P addTabs(Consumer<TabPaneEditor<JTabbedPane, ?>> tabs);

		P addSplit(boolean vertical, Consumer<SplitPane<?>> split);

		P addScroll(String fieldName, Consumer<ScrollPane<?>> scroll);

		default P spacer(int size) {
			return spacer(size, null);
		}

		default P spacer(int size, Consumer<Component> component) {
			Component box = Box.createRigidArea(new Dimension(size, size));
			if (component != null)
				component.accept(box);
			return addComponent(null, box, null);
		}

		<S> P addComponent(String fieldName, S component, Consumer<FieldEditor<S, ?>> modify);

		P addVPanel(Consumer<PanelPopulator<JPanel, ?>> panel);

		default P addHPanel(String fieldName, String layoutType, Consumer<PanelPopulator<JPanel, ?>> panel) {
			return addHPanel(fieldName, makeLayout(layoutType), panel);
		}

		P addHPanel(String fieldName, LayoutManager layout, Consumer<PanelPopulator<JPanel, ?>> panel);

		P addSettingsMenu(Consumer<SettingsMenu<JPanel, ?>> menu);

		default P addCollapsePanel(boolean vertical, String layoutType, Consumer<CollapsePanel<JXCollapsiblePane, JXPanel, ?>> panel) {
			return addCollapsePanel(vertical, makeLayout(layoutType), panel);
		}

		P addCollapsePanel(boolean vertical, LayoutManager layout, Consumer<CollapsePanel<JXCollapsiblePane, JXPanel, ?>> panel);

		P withGlassPane(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel);

		@Override
		default Alert alert(String title, String message) {
			return new SimpleAlert(getContainer(), title, message);
		}

		C getContainer();
	}

	public interface ComponentEditor<E, P extends ComponentEditor<E, P>> extends Tooltipped<P> {
		Observable<?> getUntil();

		E getEditor();

		P visibleWhen(ObservableValue<Boolean> visible);

		/**
		 * Specifies that this component should fill the horizontal dimension of its panel (e.g. should expand so that there is no empty
		 * space on the right side of its panel).
		 *
		 * @return This editor
		 */
		P fill();

		/**
		 * Specifies that this component should expand to fill the vertical dimension of its panel (e.g. should expand so that there is no
		 * empty space at the bottom of its panel).
		 *
		 * @return This editor
		 */
		P fillV();

		P decorate(Consumer<ComponentDecorator> decoration);

		P modifyEditor(Consumer<? super E> modify);

		P modifyComponent(Consumer<Component> component);

		Component getComponent();

		Alert alert(String title, String message);

		P withLayoutConstraints(Object constraints);

		default P grabFocus() {
			// This method is typically called when the component is declared, usually before the editor is built or installed.
			// We'll give the Swing system the chance to finish its work and try a few times as well.
			Duration interval = Duration.ofMillis(20);
			QommonsTimer.getCommonInstance().build(() -> {
				Component component = getComponent();
				if (component != null && component.isVisible())
					component.requestFocus();
			}, interval, false).times(5).onEDT().runNextIn(interval);
			return (P) this;
		}

		P onMouse(Consumer<MouseEvent> onMouse);

		default P onClick(Consumer<MouseEvent> onClick) {
			return onMouse(evt -> {
				if (evt.getID() == MouseEvent.MOUSE_CLICKED)
					onClick.accept(evt);
			});
		}

		default P onHover(Consumer<MouseEvent> onClick) {
			return onMouse(evt -> {
				if (evt.getID() == MouseEvent.MOUSE_MOVED)
					onClick.accept(evt);
			});
		}

		default P onEnter(Consumer<MouseEvent> onClick) {
			return onMouse(evt -> {
				if (evt.getID() == MouseEvent.MOUSE_ENTERED)
					onClick.accept(evt);
			});
		}

		default P onExit(Consumer<MouseEvent> onClick) {
			return onMouse(evt -> {
				if (evt.getID() == MouseEvent.MOUSE_EXITED)
					onClick.accept(evt);
			});
		}

		/**
		 * @param name The name for the component (typically for debugging)
		 * @return This editor
		 */
		P withName(String name);
	}

	public interface SettingsMenu<C extends Container, P extends SettingsMenu<C, P>> extends PanelPopulator<C, P>, Iconized<P> {
	}

	public interface Tooltipped<T extends Tooltipped<T>> {
		default T withTooltip(String tooltip) {
			return withTooltip(tooltip == null ? null : ObservableValue.of(tooltip));
		}

		T withTooltip(ObservableValue<String> tooltip);
	}

	public interface FieldEditor<E, P extends FieldEditor<E, P>> extends ComponentEditor<E, P>, Tooltipped<P> {
		default P withFieldName(String fieldName) {
			return withFieldName(fieldName == null ? null : ObservableValue.of(fieldName));
		}

		P withFieldName(ObservableValue<String> fieldName);

		default P withPostLabel(String postLabel) {
			return withPostLabel(postLabel == null ? null : ObservableValue.of(postLabel));
		}

		P withPostLabel(ObservableValue<String> postLabel);

		default P withPostButton(String buttonText, Consumer<Object> action, Consumer<ButtonEditor<JButton, ?>> modify) {
			return withPostButton(buttonText, new ObservableAction<Void>() {
				@Override
				public TypeToken<Void> getType() {
					return TypeTokens.get().of(Void.class);
				}

				@Override
				public Void act(Object cause) throws IllegalStateException {
					action.accept(cause);
					return null;
				}

				@Override
				public ObservableValue<String> isEnabled() {
					return SettableValue.ALWAYS_ENABLED;
				}
			}, modify);
		}

		P withPostButton(String buttonText, ObservableAction<?> action, Consumer<ButtonEditor<JButton, ?>> modify);

		P modifyFieldLabel(Consumer<ObservableSwingUtils.FontAdjuster<?>> font);

		P withFont(Consumer<ObservableSwingUtils.FontAdjuster<?>> font);
	}

	public interface Iconized<I> {
		default I withIcon(Class<?> resourceAnchor, String location, int width, int height) {
			return withIcon(ObservableSwingUtils.getFixedIcon(resourceAnchor, location, width, height));
		}

		default I withIcon(Icon icon) {
			return withIcon(icon == null ? null : ObservableValue.of(icon));
		}

		I withIcon(ObservableValue<? extends Icon> icon);
	}

	public interface ButtonEditor<B extends AbstractButton, P extends ButtonEditor<B, P>> extends FieldEditor<B, P>, Iconized<P> {
		default P withText(String text) {
			return withText(text == null ? null : ObservableValue.of(text));
		}

		P withText(ObservableValue<String> text);

		P disableWith(ObservableValue<String> disabled);
	}

	public interface ImageControl {
		ImageControl setLocation(String location);

		ImageControl variableLocation(ObservableValue<String> location);

		ImageControl variable(ObservableValue<? extends Icon> icon);

		ImageControl withSize(int width, int height);
	}

	public interface ComboEditor<F, P extends ComboEditor<F, P>> extends FieldEditor<JComboBox<F>, P> {
		default P renderAs(Function<? super F, String> renderer) {
			return renderWith(ObservableCellRenderer.formatted(renderer));
		}

		P renderWith(ObservableCellRenderer<F, F> renderer);

		P withValueTooltip(Function<? super F, String> tooltip);

		String getTooltip(F value);
	}

	public interface SteppedFieldEditor<E, F, P extends SteppedFieldEditor<E, F, P>> extends FieldEditor<E, P> {
		P withStepSize(F stepSize);
	}

	public interface SliderEditor<E, P extends SliderEditor<E, P>> extends FieldEditor<E, P> {
		P withMinimum(ObservableValue<Double> min);

		P withMaximum(ObservableValue<Double> max);

		P adjustBoundsForValue(boolean adjustForValue);
	}

	public interface ProgressEditor<P extends ProgressEditor<P>> extends FieldEditor<JProgressBar, P> {
		P withTaskLength(ObservableValue<Integer> length);

		P withProgress(ObservableValue<Integer> progress);

		default P withTaskLength(int length) {
			return withTaskLength(ObservableValue.of(length));
		}

		P indeterminate(ObservableValue<Boolean> indeterminate);

		default P indeterminate() {
			return indeterminate(ObservableValue.of(true));
		}

		P withProgressText(ObservableValue<String> text);
	}

	public interface ToggleEditor<F, TB extends JToggleButton, P extends ToggleEditor<F, TB, P>> extends FieldEditor<Map<F, TB>, P> {
		P render(BiConsumer<? super TB, ? super F> renderer);

		P withValueTooltip(Function<? super F, String> tooltip);
	}

	public interface TabPaneEditor<E, P extends TabPaneEditor<E, P>> extends ComponentEditor<E, P> {
		int getTabCount();

		default P withVTab(Object tabID, Consumer<PanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier) {
			return withVTab(tabID, getTabCount(), panel, tabModifier);
		}

		P withVTab(Object tabID, int tabIndex, Consumer<PanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier);

		default P withHTab(Object tabID, String layoutType, Consumer<PanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier) {
			return withHTab(tabID, makeLayout(layoutType), panel, tabModifier);
		}

		default P withHTab(Object tabID, LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier) {
			return withHTab(tabID, getTabCount(), layout, panel, tabModifier);
		}

		P withHTab(Object tabID, int tabIndex, LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel,
			Consumer<TabEditor<?>> tabModifier);

		default P withTab(Object tabID, Component tabComponent, Consumer<TabEditor<?>> tabModifier) {
			return withTab(tabID, getTabCount(), tabComponent, tabModifier);
		}

		P withTab(Object tabID, int tabIndex, Component tabComponent, Consumer<TabEditor<?>> tabModifier);

		P withSelectedTab(SettableValue<?> tabID);

		P onSelectedTab(Consumer<ObservableValue<Object>> tabID);
	}

	public interface TabEditor<P extends TabEditor<P>> {
		default P setName(String name) {
			if (getName() instanceof SettableValue)
				((SettableValue<String>) getName()).set(name, null);
			else
				setName(ObservableValue.of(name));
			return (P) this;
		}

		P setName(ObservableValue<String> name);

		ObservableValue<String> getName();

		default P setImage(ObservableValue<Image> icon) {
			return setIcon(icon == null ? null : icon.map(img -> new ImageIcon(img)));
		}

		P setIcon(ObservableValue<Icon> icon);

		ObservableValue<Icon> getIcon();

		P selectOn(Observable<?> select);

		P onSelect(Consumer<ObservableValue<Boolean>> onSelect);

		P remove();

		P select();

		P setRemovable(boolean removable);

		P onRemove(Consumer<Object> onRemove);
	}

	public interface SplitPane<P extends SplitPane<P>> extends ComponentEditor<JSplitPane, P> {
		P firstV(Consumer<PanelPopulator<?, ?>> vPanel);

		default P firstH(String layoutType, Consumer<PanelPopulator<?, ?>> hPanel) {
			return firstH(makeLayout(layoutType), hPanel);
		}

		P firstH(LayoutManager layout, Consumer<PanelPopulator<?, ?>> hPanel);

		P first(Component component);

		P lastV(Consumer<PanelPopulator<?, ?>> vPanel);

		default P lastH(String layoutType, Consumer<PanelPopulator<?, ?>> hPanel) {
			return lastH(makeLayout(layoutType), hPanel);
		}

		P lastH(LayoutManager layout, Consumer<PanelPopulator<?, ?>> hPanel);

		P last(Component component);

		P withSplitLocation(int split);
		P withSplitProportion(double split);

		P withSplitLocation(SettableValue<Integer> split);

		P withSplitProportion(SettableValue<Double> split);
	}

	public interface ScrollPane<P extends ScrollPane<P>> extends ComponentEditor<JScrollPane, P> {
		P withVContent(Consumer<PanelPopulator<?, ?>> panel);

		default P withHContent(String layoutType, Consumer<PanelPopulator<?, ?>> panel) {
			return withHContent(makeLayout(layoutType), panel);
		}

		P withHContent(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel);

		P withContent(Component component);
	}

	public interface CollapsePanel<CP extends Container, C extends Container, P extends CollapsePanel<CP, C, P>>
	extends PanelPopulator<C, P> {
		P withCollapsed(SettableValue<Boolean> collapsed);

		P animated(boolean animated);

		P modifyCP(Consumer<CP> cp);

		P withHeader(Consumer<PanelPopulator<JPanel, ?>> header);
	}

	public interface CollectionWidgetBuilder<R, C extends Component, P extends CollectionWidgetBuilder<R, C, P>>
	extends ComponentEditor<C, P> {
		P withSelection(SettableValue<R> selection, boolean enforceSingleSelection);

		P withSelection(ObservableCollection<R> selection);

		List<R> getSelection();

		P withRemove(Consumer<? super List<? extends R>> deletion, Consumer<DataAction<R, ?>> actionMod);

		default P withAction(String actionName, Consumer<? super R> action, Consumer<DataAction<R, ?>> actionMod) {
			return withMultiAction(actionName, values -> {
				for (R value : values)
					action.accept(value);
			}, actionMod);
		}

		P withMultiAction(String actionName, Consumer<? super List<? extends R>> action, Consumer<DataAction<R, ?>> actionMod);

		P withItemName(String itemName);

		String getItemName();
	}

	public interface ListWidgetBuilder<R, C extends Component, P extends ListWidgetBuilder<R, C, P>> {
		P withAdd(Supplier<? extends R> creator, Consumer<DataAction<R, ?>> actionMod);

		P withCopy(Function<? super R, ? extends R> copier, Consumer<DataAction<R, ?>> actionMod);

		ObservableCollection<? extends R> getRows();

		String getItemName();
	}

	public interface ListBuilder<R, P extends ListBuilder<R, P>>
	extends CollectionWidgetBuilder<R, LittleList<R>, P>, ListWidgetBuilder<R, LittleList<R>, P>, FieldEditor<LittleList<R>, P> {
		P render(Consumer<CategoryRenderStrategy<R, R>> render);
	}

	public interface AbstractTableBuilder<R, C extends Component, P extends AbstractTableBuilder<R, C, P>> {
		P withColumns(ObservableCollection<? extends CategoryRenderStrategy<R, ?>> columns);

		P withColumn(CategoryRenderStrategy<R, ?> column);

		default <C> P withColumn(String name, TypeToken<C> type, Function<? super R, ? extends C> accessor, //
			Consumer<CategoryRenderStrategy<R, C>> column) {
			CategoryRenderStrategy<R, C> col = new CategoryRenderStrategy<>(name, type, accessor);
			if (column != null)
				column.accept(col);
			return withColumn(col);
		}

		default <C> P withColumn(String name, Class<C> type, Function<? super R, ? extends C> accessor, //
			Consumer<CategoryRenderStrategy<R, C>> column) {
			return withColumn(name, TypeTokens.get().of(type), accessor, column);
		}

		P withAdaptiveHeight(int minRows, int prefRows, int maxRows);

		P scrollable(boolean scrollable);
	}

	public interface TableBuilder<R, P extends TableBuilder<R, P>>
	extends CollectionWidgetBuilder<R, JTable, P>, ListWidgetBuilder<R, JTable, P>, AbstractTableBuilder<R, JTable, P> {
		@SuppressWarnings("rawtypes")
		static final DbugAnchorType<TableBuilder> DBUG = Dbug.common().anchor(TableBuilder.class, a -> a//
			.withField("type", true, false, TypeTokens.get().keyFor(TypeToken.class).wildCard())//
			);

		default P withNameColumn(Function<? super R, String> getName, BiConsumer<? super R, String> setName, boolean unique,
			Consumer<CategoryRenderStrategy<R, String>> column) {
			return withColumn("Name", String.class, getName, col -> {
				if (setName != null) {
					col.withMutation(mut -> {
						mut.asText(Format.TEXT).mutateAttribute2((row, name) -> {
							setName.accept(row, name);
							return name;
						}).withRowUpdate(true);
						if (unique)
							mut.filterAccept((rowEl, name) -> {
								if (StringUtils.isUniqueName(getRows(), getName, name, rowEl.get())) {
									return null;
								} else {
									String itemName = getItemName();
									return new StringBuilder(StringUtils.getIndefiniteArticle(name)).append(' ').append(itemName)
										.append(" named \"").append(name).append("\" already exists").toString();
								}
							});
					});
				}
				if (column != null)
					column.accept(col);
			});
		}

		P withIndexColumn(String columnName, Consumer<CategoryRenderStrategy<R, Integer>> column);

		P withTableOption(Consumer<? super PanelPopulator<?, ?>> panel);

		P withFiltering(ObservableValue<? extends TableContentControl> filter);

		P withCountTitle(String displayedText);

		ObservableCollection<R> getFilteredRows();

		P withMove(boolean up, Consumer<DataAction<R, ?>> actionMod);

		P withMoveToEnd(boolean up, Consumer<DataAction<R, ?>> actionMod);

		P dragSourceRow(Consumer<? super Dragging.TransferSource<R>> source);

		P dragAcceptRow(Consumer<? super Dragging.TransferAccepter<R, R, R>> accept);

		P withMouseListener(ObservableTableModel.RowMouseListener<? super R> listener);

		P withActionsOnTop(boolean actionsOnTop);
	}

	public interface DataAction<R, A extends DataAction<R, A>> {
		List<R> getActionItems();

		/**
		 * @param allowed Whether this action should be enabled when multiple values are selected
		 * @return This action
		 */
		A allowForMultiple(boolean allowed);

		/**
		 * @param allowed Whether this action should be enabled when no values are selected
		 * @return This action
		 */
		A allowForEmpty(boolean allowed);

		/**
		 * @param allowed Whether this action should be enabled when the action is not {@link #allowWhen(Function, Consumer) allowed} for
		 *        some values, but the set of the values for which the action is allowed match this action's
		 *        {@link #allowForMultiple(boolean) multi} and {@link #allowForEmpty(boolean) empty} selection settings.
		 * @return This action
		 */
		A allowForAnyEnabled(boolean allowed);

		/**
		 * @param display Whether this action should be displayed to the user disabled or hidden completely when not enabled
		 * @return This action
		 */
		A displayWhenDisabled(boolean display);

		boolean isAllowedForMultiple();

		boolean isAllowedForEmpty();

		boolean isDisplayedWhenDisabled();

		A allowWhen(Function<? super R, String> filter, Consumer<ActionEnablement<R>> operation);

		A allowWhenMulti(Function<? super List<? extends R>, String> filter, Consumer<ActionEnablement<List<? extends R>>> operation);

		A withTooltip(Function<? super List<? extends R>, String> tooltip);

		// A disableWith(Function<? super List<? extends R>, String> disabled);

		A modifyAction(Function<? super ObservableAction<?>, ? extends ObservableAction<?>> actionMod);

		default A confirm(String alertTitle, String alertText, boolean confirmType) {
			return confirm(alertTitle, selected -> alertText, confirmType);
		}

		A confirm(String alertTitle, Function<List<? extends R>, String> alertText, boolean confirmType);

		/**
		 * @param alertTitle The title for the alert dialog
		 * @param alertPreText The beginning of the question to use to confirm the action on the items
		 * @param postText The end of the question to use to confirm the action on the items
		 * @param confirmType True for OK/Cancel, false for Yes/No
		 * @return This action
		 */
		A confirmForItems(String alertTitle, String alertPreText, String postText, boolean confirmType);

		A modifyButton(Consumer<ButtonEditor<?, ?>> buttonMod);
	}

	public static class ActionEnablement<E> implements Function<E, String> {
		private Function<? super E, String> theEnablement;

		public ActionEnablement(Function<? super E, String> enablement) {
			theEnablement = enablement;
		}

		public ActionEnablement<E> or(Function<? super E, String> filter, Consumer<ActionEnablement<E>> operation) {
			Function<? super E, String> newFilter;
			if (operation == null) {
				newFilter = filter;
			} else {
				ActionEnablement<E> next = new ActionEnablement<>(filter);
				operation.accept(next);
				newFilter = next;
			}
			if (theEnablement == null)
				theEnablement = filter;
			else {
				Function<? super E, String> oldFilter = theEnablement;
				// TODO Use LambdaUtils here to make the Object methods work well
				theEnablement = value -> {
					String msg = oldFilter.apply(value);
					if (msg == null)
						return null;
					return newFilter.apply(value);
				};
			}
			return this;
		}

		public ActionEnablement<E> and(Function<? super E, String> filter, Consumer<ActionEnablement<E>> operation) {
			Function<? super E, String> newFilter;
			if (operation == null) {
				newFilter = filter;
			} else {
				ActionEnablement<E> next = new ActionEnablement<>(filter);
				operation.accept(next);
				newFilter = next;
			}
			if (theEnablement == null)
				theEnablement = filter;
			else {
				Function<? super E, String> oldFilter = theEnablement;
				// TODO Use LambdaUtils here to make the Object methods work well
				theEnablement = value -> {
					String msg = oldFilter.apply(value);
					if (msg != null)
						return msg;
					return newFilter.apply(value);
				};
			}
			return this;
		}

		@Override
		public String apply(E value) {
			if (theEnablement == null)
				return null;
			else
				return theEnablement.apply(value);
		}

		@Override
		public String toString() {
			return String.valueOf(theEnablement);
		}
	}

	public interface AbstractTreeEditor<F, C extends Component, P extends AbstractTreeEditor<F, C, P>>
	extends CollectionWidgetBuilder<BetterList<F>, C, P> {
		ObservableValue<? extends F> getRoot();

		CategoryRenderStrategy<BetterList<F>, F> getRender();

		P withRender(CategoryRenderStrategy<BetterList<F>, F> render);

		default P withRender(Consumer<CategoryRenderStrategy<BetterList<F>, F>> render) {
			render.accept(getRender());
			return (P) this;
		}

		P withValueSelection(SettableValue<F> selection, boolean enforceSingleSelection);

		P withValueSelection(ObservableCollection<F> selection);

		P withLeafTest(Predicate<? super F> leafTest);

		boolean isVisible(List<? extends F> path);

		boolean isExpanded(List<? extends F> path);
	}

	public interface TreeEditor<F, P extends TreeEditor<F, P>> extends AbstractTreeEditor<F, JTree, P> {
	}

	public interface TreeTableEditor<F, P extends TreeTableEditor<F, P>>
	extends AbstractTreeEditor<F, JXTreeTable, P>, AbstractTableBuilder<BetterList<F>, JXTreeTable, P> {
	}

	public interface Alert {
		Alert info();

		Alert warning();

		Alert error();

		Alert withIcon(String location, Consumer<ImageControl> image);

		void display();

		/**
		 * @param confirmType Whether to display "Yes" and "No" (false), or "OK" and "Cancel" (true) for the confirmation
		 * @return Whether the user clicked "Yes" or "OK"
		 */
		boolean confirm(boolean confirmType);

		<T> T input(TypeToken<T> type, Format<T> format, T initial, Consumer<ObservableTextField<T>> modify);

		default <T> T input(Class<T> type, Format<T> format, T initial, Consumer<ObservableTextField<T>> modify) {
			return input(TypeTokens.get().of(type), format, initial, modify);
		}
	}

	public interface WindowBuilder<W extends Window, P extends WindowBuilder<W, P>> {
		W getWindow();

		default P withTitle(String title) {
			return withTitle(ObservableValue.of(TypeTokens.get().STRING, title));
		}
		P withTitle(ObservableValue<String> title);
		ObservableValue<String> getTitle();

		default P withIcon(Class<?> clazz, String location) {
			ImageIcon icon = ObservableSwingUtils.getIcon(clazz, location);
			if (icon != null)
				withIcon(icon.getImage());
			return (P) this;
		}
		default P withIcon(Image icon) {
			return withIcon(ObservableValue.of(TypeTokens.get().of(Image.class), icon));
		}
		P withIcon(ObservableValue<? extends Image> icon);
		ObservableValue<? extends Image> getIcon();

		P withX(SettableValue<Integer> x);
		P withY(SettableValue<Integer> y);
		P withWidth(SettableValue<Integer> width);
		P withHeight(SettableValue<Integer> height);
		default P withLocation(ObservableConfig locationConfig) {
			withX(locationConfig.asValue(Integer.class).at("x").withFormat(Format.INT, () -> null).buildValue(null));
			return withY(locationConfig.asValue(Integer.class).at("y").withFormat(Format.INT, () -> getWindow().getX()).buildValue(null));
		}
		default P withSize(ObservableConfig sizeConfig) {
			withWidth(sizeConfig.asValue(Integer.class).at("width").withFormat(Format.INT, () -> null).buildValue(null));
			return withHeight(sizeConfig.asValue(Integer.class).at("height").withFormat(Format.INT, () -> null).buildValue(null));
		}
		default P withBounds(ObservableConfig boundsConfig) {
			withLocation(boundsConfig);
			return withSize(boundsConfig);
		}

		default P withSize(int width, int height) {
			withWidth(SettableValue.build(int.class).withValue(width).build());
			withHeight(SettableValue.build(int.class).withValue(height).build());
			return (P) this;
		}

		P withVisible(SettableValue<Boolean> visible);

		P withMenuBar(Consumer<MenuBarBuilder<?>> menuBar);

		default P disposeOnClose(boolean dispose) {
			return withCloseAction(dispose ? WindowConstants.DISPOSE_ON_CLOSE : WindowConstants.HIDE_ON_CLOSE);
		}

		/**
		 * @param closeAction The action to perform when the user closes the window
		 * @return This builder
		 * @see WindowConstants#DO_NOTHING_ON_CLOSE
		 * @see WindowConstants#HIDE_ON_CLOSE
		 * @see WindowConstants#DISPOSE_ON_CLOSE
		 * @see WindowConstants#EXIT_ON_CLOSE
		 */
		P withCloseAction(int closeAction);

		P withVContent(Consumer<PanelPopulator<?, ?>> content);

		default P withHContent(String layoutType, Consumer<PanelPopulator<?, ?>> content) {
			return withHContent(layoutType == null ? null : makeLayout(layoutType), content);
		}

		P withHContent(LayoutManager layout, Consumer<PanelPopulator<?, ?>> content);

		P withContent(Component content);

		P run(Component relativeTo);
	}

	public interface DialogBuilder<D extends JDialog, P extends DialogBuilder<D, P>> extends WindowBuilder<D, P> {
		default P modal(boolean modal) {
			return withModality(modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS);
		}

		default P withModality(ModalityType modality) {
			return withModality(ObservableValue.of(modality));
		}

		P withModality(ObservableValue<ModalityType> modality);
	}

	public interface UiAction<A extends UiAction<A>> extends Iconized<A>, Tooltipped<A> {
		A visibleWhen(ObservableValue<Boolean> visible);

		A decorate(Consumer<ComponentDecorator> decoration);

		A disableWith(ObservableValue<String> disabled);

		@Override
		A withTooltip(ObservableValue<String> tooltip);

		A withText(ObservableValue<String> text);
	}

	public interface MenuBuilder<M extends MenuBuilder<M>> extends UiAction<M> {
		M withAction(String name, Consumer<Object> action, Consumer<UiAction<?>> ui);

		M withSubMenu(String name, Consumer<MenuBuilder<?>> subMenu);
	}

	public interface MenuBarBuilder<M extends MenuBarBuilder<M>> {
		M withMenu(String menuName, Consumer<MenuBuilder<?>> menu);
	}

	// public interface

	// Drag and cut/copy/paste

	static final String MIG_LAYOUT_CLASS_NAME = "net.miginfocom.swing.MigLayout";

	static final Constructor<? extends LayoutManager2> MIG_LAYOUT_CREATOR;

	static {
		Constructor<? extends LayoutManager2> creator;
		try {
			creator = Class.forName(MIG_LAYOUT_CLASS_NAME).asSubclass(LayoutManager2.class).getConstructor(String.class, String.class);
		} catch (NoSuchMethodException | SecurityException | ClassNotFoundException e) {
			System.err.println("Could not retrieve needed " + MIG_LAYOUT_CLASS_NAME + " constructor");
			e.printStackTrace();
			creator = null;
		}
		MIG_LAYOUT_CREATOR = creator;
	}

	static LayoutManager2 createMigLayout(boolean withInsets, Supplier<String> err) {
		String layoutConstraints = "fillx, hidemode 3";
		if (!withInsets)
			layoutConstraints += ", insets 0";
		LayoutManager2 migLayout;
		try {
			migLayout = MIG_LAYOUT_CREATOR.newInstance(layoutConstraints, "[shrink][grow][shrink]");
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
			| SecurityException e) {
			throw new IllegalStateException(
				ObservableSwingUtils.class.getName() + " could not instantiate " + MIG_LAYOUT_CLASS_NAME + ": " + err.get(), e);
		}
		return migLayout;
	}

	static LayoutManager makeLayout(String layoutType) {
		LayoutManager layout;
		if (layoutType == null)
			layoutType = "box";
		switch (layoutType.toLowerCase()) {
		case "mig":
			layout = createMigLayout(false, () -> "use addHPanel(String, LayoutManager, Consumer)");
			break;
		case "ctr":
		case "center":
			layout = new JustifiedBoxLayout(false).mainCenter();
			break;
		case "box":
		case "just":
		case "justify":
		case "justified":
		default:
			layout = new JustifiedBoxLayout(false).mainJustified();
			break;
		}
		return layout;
	}

	private PanelPopulation() {}
}

package org.observe.util.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.LayoutManager2;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeListener;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.jdesktop.swingx.JXCollapsiblePane;
import org.jdesktop.swingx.JXPanel;
import org.jdesktop.swingx.JXTreeTable;
import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.SettableValue.Builder;
import org.observe.SimpleObservable;
import org.observe.SimpleSettableValue;
import org.observe.Subscription;
import org.observe.TypedValueContainer;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.DefaultObservableCollection;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.config.ObservableConfig;
import org.observe.util.SafeObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.ObservableSwingUtils.FontAdjuster;
import org.qommons.BiTuple;
import org.qommons.Identifiable;
import org.qommons.IntList;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.RRWLockingStrategy;
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
		if (panel == null)
			panel = (C) new JPanel();
		return new MigFieldPanel<>(panel, until == null ? Observable.empty() : until, new LazyLock());
	}

	public static <C extends Container> PanelPopulator<C, ?> populateHPanel(C panel, String layoutType, Observable<?> until) {
		return populateHPanel(panel, layoutType == null ? null : makeLayout(layoutType), until);
	}

	public static <C extends Container> PanelPopulator<C, ?> populateHPanel(C panel, LayoutManager layout, Observable<?> until) {
		if (panel == null)
			panel = (C) new JPanel(layout);
		else if (layout != null)
			panel.setLayout(layout);
		return new SimpleHPanel<>(null, panel, new LazyLock(), until == null ? Observable.empty() : until);
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
		return new SimpleHPanel<>(null, panel, new LazyLock(), until == null ? Observable.empty() : until);
	}

	public static <R> TableBuilder<R, ?> buildTable(ObservableCollection<R> rows) {
		return new SimpleTableBuilder<>(rows, new LazyLock());
	}

	public static class LazyLock implements Supplier<Transactable> {
		private final Function<Object, Transactable> theLockCreator;
		private Transactable theLock;

		public LazyLock() {
			this(o -> Transactable.transactable(new ReentrantReadWriteLock(), o));
		}

		public LazyLock(Function<Object, Transactable> lockCreator) {
			theLockCreator = lockCreator;
		}

		@Override
		public Transactable get() {
			if (theLock == null) {
				synchronized (this) {
					if (theLock == null)
						theLock = theLockCreator.apply(this);
				}
			}
			return theLock;
		}
	}

	public interface PanelPopulator<C extends Container, P extends PanelPopulator<C, P>> extends ComponentEditor<C, P> {
		Observable<?> getUntil();

		<F> P addTextField(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<FieldEditor<ObservableTextField<F>, ?>> modify);

		<F> P addTextArea(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<FieldEditor<ObservableTextArea<F>, ?>> modify);

		default P addLabel(String fieldName, String label, Consumer<FieldEditor<JLabel, ?>> modify) {
			return addLabel(fieldName, ObservableValue.of(label), Format.TEXT, modify);
		}

		<F> P addLabel(String fieldName, ObservableValue<F> field, Format<F> format, Consumer<FieldEditor<JLabel, ?>> modify);

		P addIcon(String fieldName, ObservableValue<Icon> icon, Consumer<FieldEditor<JLabel, ?>> modify);

		P addCheckField(String fieldName, SettableValue<Boolean> field, Consumer<FieldEditor<JCheckBox, ?>> modify);

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

		<F> P addTreeTable(ObservableValue<F> root, Function<? super F, ? extends ObservableCollection<? extends F>> children,
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

		default P addCollapsePanel(boolean vertical, String layoutType, Consumer<CollapsePanel<JXCollapsiblePane, JXPanel, ?>> panel) {
			return addCollapsePanel(vertical, makeLayout(layoutType), panel);
		}

		P addCollapsePanel(boolean vertical, LayoutManager layout, Consumer<CollapsePanel<JXCollapsiblePane, JXPanel, ?>> panel);

		@Override
		default Alert alert(String title, String message) {
			return new SimpleAlert(getContainer(), title, message);
		}

		C getContainer();
	}

	public interface ComponentEditor<E, P extends ComponentEditor<E, P>> {
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

		Component getComponent();

		Alert alert(String title, String message);

		P withLayoutConstraints(Object constraints);

		ValueCache values();

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
	}

	public interface ValueCache {
		default <T> ObservableValue<T> declareValue(String name, ObservableValue<T> value) {
			ObservableValue<? extends T> added = getOrDeclareValue(name, value.getType(), () -> value);
			if (added != value)
				throw new IllegalStateException("An ObservableValue named " + name + " has already been declared");
			return value;
		}

		<T> ObservableValue<? extends T> getOrDeclareValue(String name, TypeToken<T> type,
			Supplier<? extends ObservableValue<? extends T>> value);

		default <T> SettableValue<T> declareSettable(String name, SettableValue<T> value) {
			SettableValue<T> added = getOrDeclareSettable(name, value.getType(), builder -> value);
			if (added != value)
				throw new IllegalStateException("A SettableValue<" + value.getType() + "> named " + name + " has already been declared");
			return value;
		}

		default <T> SettableValue<T> getOrDeclareSettable(String name, Class<T> type,
			Function<SettableValue.Builder<T>, SettableValue<T>> builder) {
			return getOrDeclareSettable(name, TypeTokens.get().of(type), builder);
		}

		<T> SettableValue<T> getOrDeclareSettable(String name, TypeToken<T> type,
			Function<SettableValue.Builder<T>, SettableValue<T>> builder);

		default <T> ObservableCollection<T> declareCollection(String name, ObservableCollection<T> value) {
			ObservableCollection<T> added = getOrDeclareCollection(name, value.getType(), builder -> value);
			if (added != value)
				throw new IllegalStateException(
					"An ObservableCollection<" + value.getType() + "> named " + name + " has already been declared");
			return value;
		}

		default <T> ObservableCollection<T> getOrDeclareCollection(String name, Class<T> type,
			Function<ObservableCollectionBuilder<T, ?>, ? extends ObservableCollection<T>> builder) {
			return getOrDeclareCollection(name, TypeTokens.get().of(type), builder);
		}

		<T> ObservableCollection<T> getOrDeclareCollection(String name, TypeToken<T> type,
			Function<ObservableCollectionBuilder<T, ?>, ? extends ObservableCollection<T>> builder);

		default <T> ObservableValue<? extends T> getValue(String name, Class<T> type) {
			return getValue(name, TypeTokens.get().of(type));
		}

		<T> ObservableValue<? extends T> getValue(String name, TypeToken<T> type);

		default <T> SettableValue<T> getSettable(String name, Class<T> type) {
			return getSettable(name, TypeTokens.get().of(type));
		}

		<T> SettableValue<T> getSettable(String name, TypeToken<T> type);

		default <T> SettableValue<? super T> getAccepter(String name, Class<T> type) {
			return getAccepter(name, TypeTokens.get().of(type));
		}

		<T> SettableValue<? super T> getAccepter(String name, TypeToken<T> type);

		default <T> ObservableCollection<T> getCollection(String name, Class<T> type) {
			return getCollection(name, TypeTokens.get().of(type));
		}

		<T> ObservableCollection<T> getCollection(String name, TypeToken<T> type);

		default <T> ObservableCollection<? extends T> getSupplyCollection(String name, Class<T> type) {
			return getSupplyCollection(name, TypeTokens.get().of(type));
		}

		<T> ObservableCollection<? extends T> getSupplyCollection(String name, TypeToken<T> type);
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

		ImageControl withOpacity(double opacity);
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

		P setIcon(ObservableValue<Image> icon);

		ObservableValue<Image> getIcon();

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

		P modifyCP(Consumer<CP> cp);

		P withHeader(Consumer<PanelPopulator<JPanel, ?>> header);
	}

	public interface CollectionWidgetBuilder<R, C extends Component, P extends CollectionWidgetBuilder<R, C, P>>
	extends ComponentEditor<C, P> {
		P withSelection(SettableValue<R> selection, boolean enforceSingleSelection);

		P withSelection(ObservableCollection<R> selection);

		List<R> getSelection();

		P withRemove(Consumer<? super List<? extends R>> deletion, Consumer<DataAction<R, ?>> actionMod);

		default P withAction(Consumer<? super R> action, Consumer<DataAction<R, ?>> actionMod) {
			return withMultiAction(values -> {
				for (R value : values)
					action.accept(value);
			}, actionMod);
		}

		P withMultiAction(Consumer<? super List<? extends R>> action, Consumer<DataAction<R, ?>> actionMod);

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
		P withColumns(ObservableCollection<? extends CategoryRenderStrategy<? super R, ?>> columns);

		P withColumn(CategoryRenderStrategy<? super R, ?> column);

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

		P withMove(boolean up, Consumer<DataAction<R, ?>> actionMod);

		P withMoveToEnd(boolean up, Consumer<DataAction<R, ?>> actionMod);

		P dragSourceRow(Consumer<? super Dragging.TransferSource<R>> source);

		P dragAcceptRow(Consumer<? super Dragging.TransferAccepter<R, R, R>> accept);

		P withMouseListener(ObservableTableModel.RowMouseListener<? super R> listener);
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

		boolean isAllowedForMultiple();

		boolean isAllowedForEmpty();

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

		default P withSelection(SettableValue<F> selection, Function<? super F, ? extends F> parent, boolean enforceSingleSelection) {
			return withSelection(selection.map(TypeTokens.get().keyFor(BetterList.class).parameterized(selection.getType()), //
				s -> pathTo(s, parent, getRoot().get()), //
				path -> path == null ? null : path.get(path.size() - 1), null), enforceSingleSelection);
		}

		default P withSelection(ObservableCollection<F> selection, Function<? super F, ? extends F> parent) {
			return withSelection(selection.flow()
				.<BetterList<F>> transform(TypeTokens.get().keyFor(BetterList.class).parameterized(selection.getType()), //
					tx -> tx.cache(false).map(s -> pathTo(s, parent, getRoot().get())).withReverse(path -> path.get(path.size() - 1)))//
				.collectPassive());
		}

		P withLeafTest(Predicate<? super F> leafTest);

		static <T> BetterList<T> pathTo(T value, Function<? super T, ? extends T> parent, T root) {
			if (value == null)
				return null;
			LinkedList<T> path = new LinkedList<>();
			while (true) {
				path.addFirst(value);
				if (value == null || value == root)
					break;
				value = parent.apply(value);
			}
			return BetterList.of(path);
		}

		boolean isVisible(List<? extends F> path);

		boolean isExpanded(List<? extends F> path);
	}

	public interface TreeEditor<F, P extends TreeEditor<F, P>> extends AbstractTreeEditor<F, JTree, P> {
		default P renderWith(Function<? super F, String> format) {
			return renderWith(ObservableCellRenderer.formatted(format));
		}

		P renderWith(ObservableCellRenderer<BetterList<F>, F> renderer);

		default P withValueTooltip(Function<? super F, String> tooltip) {
			return withCellTooltip(tooltip == null ? null : cell -> tooltip.apply(cell.getCellValue()));
		}

		P withCellTooltip(Function<? super ModelCell<BetterList<F>, F>, String> tooltip);
	}

	public interface TreeTableEditor<F, P extends TreeTableEditor<F, P>>
	extends AbstractTreeEditor<F, JXTreeTable, P>, AbstractTableBuilder<F, JXTreeTable, P> {
		P withMouseListener(ObservableTreeModel.PathMouseListener<? super F> listener);

		CategoryRenderStrategy<? super F, F> getTreeColumn();

		default P withTreeColumn(String name, Consumer<CategoryRenderStrategy<? super F, F>> treeColumn) {
			CategoryRenderStrategy<? super F, F> column = getTreeColumn();
			if (column == null) {
				column = new CategoryRenderStrategy<F, F>(name, (TypeToken<F>) getRoot().getType(), f -> f);
				withTreeColumn(column);
			}
			treeColumn.accept(column);
			return (P) this;
		}

		P withTreeColumn(CategoryRenderStrategy<? super F, F> column);
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

	static class SimpleValueCache implements ValueCache {
		private Map<String, TypedValueContainer<?>> theValues;
		private final Supplier<Transactable> theLock;

		SimpleValueCache(Supplier<Transactable> lock) {
			theLock = lock;
		}

		private void init() {
			if (theValues == null)
				theValues = new HashMap<>();
		}

		private synchronized <T, V extends TypedValueContainer<T>> V getOrDeclare(String name, Class<?> holderType, TypeToken<T> type,
			Supplier<? extends V> value, boolean upperBound, boolean lowerBound) {
			if (theValues == null) {
				if (value == null)
					throw new IllegalArgumentException("No " + holderType.getSimpleName() + " named " + name + " found");
				theValues = new HashMap<>();
			}

			TypedValueContainer<?> found;
			if (value != null)
				found = theValues.computeIfAbsent(name, k -> value.get());
			else {
				found = theValues.get(name);
				if (found == null)
					throw new IllegalArgumentException(holderType.getSimpleName() + "<" + type + "> " + name + " has not been declared");
			}
			if (!holderType.isInstance(found))
				throw new IllegalArgumentException(found.getClass().getSimpleName() + " " + name + " cannot be used as a "
					+ holderType.getSimpleName() + "<" + type + ">");

			TypeToken<?> foundType = found.getType();
			boolean matches;
			String extension;
			if (!upperBound) {
				matches = foundType.isAssignableFrom(type);
				extension = "? super ";
			} else if (!lowerBound) {
				matches = type.isAssignableFrom(foundType);
				extension = "? extends ";
			} else {
				matches = foundType.equals(type);
				extension = "";
			}
			if (!matches)
				throw new IllegalArgumentException(holderType.getSimpleName() + "<" + foundType + "> " + name + " cannot be used as "
					+ holderType.getSimpleName() + "<" + extension + type + ">");
			return (V) found;
		}

		@Override
		public <T> ObservableValue<? extends T> getOrDeclareValue(String name, TypeToken<T> type,
			Supplier<? extends ObservableValue<? extends T>> value) {
			return this.getOrDeclare(name, ObservableValue.class, type, () -> (ObservableValue<T>) value.get(), true, false);
		}

		@Override
		public <T> SettableValue<T> getOrDeclareSettable(String name, TypeToken<T> type, Function<Builder<T>, SettableValue<T>> builder) {
			return this.getOrDeclare(name, SettableValue.class, type, () -> builder.apply(//
				SettableValue.build(type).withLocking(theLock.get()).withDescription(name)), true, true);
		}

		@Override
		public <T> ObservableCollection<T> getOrDeclareCollection(String name, TypeToken<T> type,
			Function<ObservableCollectionBuilder<T, ?>, ? extends ObservableCollection<T>> builder) {
			Transactable lock = theLock.get();
			CollectionLockingStrategy locking = lock == null ? new FastFailLockingStrategy() : new RRWLockingStrategy(lock);
			return this.getOrDeclare(name, ObservableCollection.class, type, () -> builder.apply(//
				DefaultObservableCollection.build(type).withLocking(locking).withDescription(name)), true, true);
		}

		@Override
		public <T> ObservableValue<? extends T> getValue(String name, TypeToken<T> type) {
			return this.<T, ObservableValue<T>> getOrDeclare(name, ObservableValue.class, type, null, true, false);
		}

		@Override
		public <T> SettableValue<T> getSettable(String name, TypeToken<T> type) {
			return this.<T, SettableValue<T>> getOrDeclare(name, SettableValue.class, type, null, true, true);
		}

		@Override
		public <T> SettableValue<? super T> getAccepter(String name, TypeToken<T> type) {
			return this.<T, SettableValue<T>> getOrDeclare(name, SettableValue.class, type, null, false, true);
		}

		@Override
		public <T> ObservableCollection<T> getCollection(String name, TypeToken<T> type) {
			return this.<T, ObservableCollection<T>> getOrDeclare(name, ObservableCollection.class, type, null, true, true);
		}

		@Override
		public <T> ObservableCollection<? extends T> getSupplyCollection(String name, TypeToken<T> type) {
			return this.<T, ObservableCollection<T>> getOrDeclare(name, ObservableCollection.class, type, null, true, false);
		}
	}

	interface PartialPanelPopulatorImpl<C extends Container, P extends PartialPanelPopulatorImpl<C, P>> extends PanelPopulator<C, P> {
		@Override
		Observable<?> getUntil();

		void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel, boolean scrolled);

		Supplier<Transactable> getLock();

		default void doAdd(SimpleFieldEditor<?, ?> field) {
			doAdd(field, false);
		}

		default void doAdd(SimpleFieldEditor<?, ?> field, boolean scrolled) {
			doAdd(field, field.createFieldNameLabel(getUntil()), field.createPostLabel(getUntil()), scrolled);
		}

		@Override
		default <F> P addTextField(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<FieldEditor<ObservableTextField<F>, ?>> modify) {
			SimpleFieldEditor<ObservableTextField<F>, ?> fieldPanel = new SimpleFieldEditor<>(fieldName,
				new ObservableTextField<>(field, format, getUntil()), getLock());
			fieldPanel.getTooltip().changes().takeUntil(getUntil()).act(evt -> fieldPanel.getEditor().setToolTipText(evt.getNewValue()));
			if (modify != null)
				modify.accept(fieldPanel);
			doAdd(fieldPanel);
			return (P) this;
		}

		@Override
		default <F> P addTextArea(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<FieldEditor<ObservableTextArea<F>, ?>> modify) {
			SimpleFieldEditor<ObservableTextArea<F>, ?> fieldPanel = new SimpleFieldEditor<>(fieldName,
				new ObservableTextArea<>(field, format, getUntil()), getLock());
			fieldPanel.getTooltip().changes().takeUntil(getUntil()).act(evt -> fieldPanel.getEditor().setToolTipText(evt.getNewValue()));
			if (modify != null)
				modify.accept(fieldPanel);
			doAdd(fieldPanel, true);
			return (P) this;
		}

		@Override
		default <F> P addLabel(String fieldName, ObservableValue<F> field, Format<F> format, Consumer<FieldEditor<JLabel, ?>> modify) {
			JLabel label = new JLabel();
			field.changes().takeUntil(getUntil()).act(evt -> {
				label.setText(format.format(evt.getNewValue()));
			});
			SimpleFieldEditor<JLabel, ?> fieldPanel = new SimpleFieldEditor<>(fieldName, label, getLock());
			fieldPanel.getTooltip().changes().takeUntil(getUntil()).act(evt -> fieldPanel.getEditor().setToolTipText(evt.getNewValue()));
			if (field instanceof SettableValue) {
				((SettableValue<F>) field).isEnabled().combine((enabled, tt) -> enabled == null ? tt : enabled, fieldPanel.getTooltip())
				.changes().takeUntil(getUntil()).act(evt -> label.setToolTipText(evt.getNewValue()));
			}
			if (modify != null)
				modify.accept(fieldPanel);
			doAdd(fieldPanel);
			return (P) this;
		}

		@Override
		default P addIcon(String fieldName, ObservableValue<Icon> icon, Consumer<FieldEditor<JLabel, ?>> modify) {
			JLabel label = new JLabel();
			icon.changes().takeUntil(getUntil()).act(evt -> label.setIcon(evt.getNewValue()));
			SimpleFieldEditor<JLabel, ?> fieldPanel = new SimpleFieldEditor<>(fieldName, label, getLock());
			fieldPanel.getTooltip().changes().takeUntil(getUntil()).act(evt -> fieldPanel.getEditor().setToolTipText(evt.getNewValue()));
			if (icon instanceof SettableValue) {
				((SettableValue<Icon>) icon).isEnabled().combine((enabled, tt) -> enabled == null ? tt : enabled, fieldPanel.getTooltip())
				.changes().takeUntil(getUntil()).act(evt -> label.setToolTipText(evt.getNewValue()));
			}
			if (modify != null)
				modify.accept(fieldPanel);
			doAdd(fieldPanel);
			return (P) this;
		}

		@Override
		default P addCheckField(String fieldName, SettableValue<Boolean> field, Consumer<FieldEditor<JCheckBox, ?>> modify) {
			SimpleFieldEditor<JCheckBox, ?> fieldPanel = new SimpleFieldEditor<>(fieldName, new JCheckBox(), getLock());
			Subscription sub = ObservableSwingUtils.checkFor(fieldPanel.getEditor(), fieldPanel.getTooltip(), field);
			getUntil().take(1).act(__ -> sub.unsubscribe());
			if (modify != null)
				modify.accept(fieldPanel);
			doAdd(fieldPanel);
			return (P) this;
		}

		@Override
		default <F> P addSpinnerField(String fieldName, JSpinner spinner, SettableValue<F> value, Function<? super F, ? extends F> purifier,
			Consumer<SteppedFieldEditor<JSpinner, F, ?>> modify) {
			SimpleSteppedFieldEditor<JSpinner, F, ?> fieldPanel = new SimpleSteppedFieldEditor<>(fieldName, spinner, getLock(),
				stepSize -> {
					((SpinnerNumberModel) spinner.getModel()).setStepSize((Number) stepSize);
				});
			ObservableSwingUtils.spinnerFor(spinner, fieldPanel.getTooltip().get(), value, purifier);
			if (modify != null)
				modify.accept(fieldPanel);
			doAdd(fieldPanel);
			return (P) this;
		}

		@Override
		default <F> P addComboField(String fieldName, SettableValue<F> value, List<? extends F> availableValues,
			Consumer<ComboEditor<F, ?>> modify) {
			ObservableCollection<? extends F> observableValues;
			if (availableValues instanceof ObservableCollection)
				observableValues = (ObservableCollection<F>) availableValues;
			else
				observableValues = ObservableCollection.of(value.getType(), availableValues);
			SimpleComboEditor<F, ?> fieldPanel = new SimpleComboEditor<>(fieldName, new JComboBox<>(), getLock());
			if (modify != null)
				modify.accept(fieldPanel);
			Subscription sub = ObservableComboBoxModel.comboFor(fieldPanel.getEditor(), fieldPanel.getTooltip(), fieldPanel::getTooltip,
				observableValues, value);
			getUntil().take(1).act(__ -> sub.unsubscribe());
			doAdd(fieldPanel);
			return (P) this;
		}

		@Override
		default <F, TB extends JToggleButton> P addToggleField(String fieldName, SettableValue<F> value, List<? extends F> values,
			Class<TB> buttonType, Function<? super F, ? extends TB> buttonCreator, Consumer<ToggleEditor<F, TB, ?>> modify) {
			ObservableCollection<? extends F> observableValues;
			if (values instanceof ObservableCollection)
				observableValues = (ObservableCollection<F>) values;
			else
				observableValues = ObservableCollection.of(value.getType(), values);
			SimpleToggleButtonPanel<F, TB, ?> radioPanel = new SimpleToggleButtonPanel<>(fieldName, observableValues, value, //
				buttonType, buttonCreator, getLock());
			if (modify != null)
				modify.accept(radioPanel);
			doAdd(radioPanel);
			return (P) this;
		}

		@Override
		default P addFileField(String fieldName, SettableValue<File> value, boolean open,
			Consumer<FieldEditor<ObservableFileButton, ?>> modify) {
			SimpleFieldEditor<ObservableFileButton, ?> fieldPanel = new SimpleFieldEditor<>(fieldName,
				new ObservableFileButton(value, open, getUntil()), getLock());
			fieldPanel.getTooltip().changes().takeUntil(getUntil()).act(evt -> fieldPanel.getEditor().setToolTipText(evt.getNewValue()));
			if (modify != null)
				modify.accept(fieldPanel);
			doAdd(fieldPanel);
			return (P) this;
		}

		@Override
		default P addButton(String buttonText, ObservableAction<?> action, Consumer<ButtonEditor<JButton, ?>> modify) {
			SimpleButtonEditor<JButton, ?> field = new SimpleButtonEditor<>(null, new JButton(), buttonText, action, getLock(), false)
				.withText(buttonText);
			if (modify != null)
				modify.accept(field);
			doAdd(field);
			return (P) this;
		}

		@Override
		default P addProgressBar(String fieldName, Consumer<ProgressEditor<?>> progress) {
			SimpleProgressEditor<?> editor=new SimpleProgressEditor<>(fieldName, getLock());
			progress.accept(editor);
			doAdd(editor);
			return (P) this;
		}

		@Override
		default P addTabs(Consumer<TabPaneEditor<JTabbedPane, ?>> tabs) {
			SimpleTabPaneEditor<?> tabPane = new SimpleTabPaneEditor<>(getLock(), getUntil());
			tabs.accept(tabPane);
			doAdd(tabPane, null, null, false);
			return (P) this;
		}

		@Override
		default P addSplit(boolean vertical, Consumer<SplitPane<?>> split) {
			SimpleSplitEditor<?> splitPane = new SimpleSplitEditor<>(vertical, getLock(), getUntil());
			split.accept(splitPane);
			doAdd(splitPane, null, null, false);
			return (P) this;
		}

		@Override
		default P addScroll(String fieldName, Consumer<ScrollPane<?>> scroll) {
			SimpleScrollEditor<?> scrollPane = new SimpleScrollEditor<>(fieldName, getLock(), getUntil());
			scroll.accept(scrollPane);
			doAdd(scrollPane);
			return (P) this;
		}

		@Override
		default <R> P addList(ObservableCollection<R> rows, Consumer<ListBuilder<R, ?>> list) {
			SimpleListBuilder<R, ?> tb = new SimpleListBuilder<>(rows, getLock());
			list.accept(tb);
			doAdd(tb);
			return (P) this;
		}

		@Override
		default <R> P addTable(ObservableCollection<R> rows, Consumer<TableBuilder<R, ?>> table) {
			SimpleTableBuilder<R, ?> tb = new SimpleTableBuilder<>(rows, getLock());
			table.accept(tb);
			doAdd(tb, null, null, false);
			return (P) this;
		}

		@Override
		default <F> P addTree(ObservableValue<? extends F> root, Function<? super F, ? extends ObservableCollection<? extends F>> children,
			Consumer<TreeEditor<F, ?>> modify) {
			SimpleTreeEditor<F, ?> treeEditor = new SimpleTreeEditor<>(getLock(), root, children);
			if (modify != null)
				modify.accept(treeEditor);
			doAdd(treeEditor, null, null, false);
			return (P) this;
		}

		@Override
		default <F> P addTreeTable(ObservableValue<F> root, Function<? super F, ? extends ObservableCollection<? extends F>> children,
			Consumer<TreeTableEditor<F, ?>> modify) {
			SimpleTreeTableBuilder<F, ?> treeTableEditor = new SimpleTreeTableBuilder<>(getLock(), root, children);
			if (modify != null)
				modify.accept(treeTableEditor);
			doAdd(treeTableEditor, null, null, false);
			return (P) this;
		}

		@Override
		default <S> P addComponent(String fieldName, S component, Consumer<FieldEditor<S, ?>> modify) {
			SimpleFieldEditor<S, ?> subPanel = new SimpleFieldEditor<>(fieldName, component, getLock());
			if (modify != null)
				modify.accept(subPanel);
			doAdd(subPanel);
			return (P) this;
		}

		@Override
		default P addHPanel(String fieldName, LayoutManager layout, Consumer<PanelPopulator<JPanel, ?>> panel) {
			SimpleHPanel<JPanel> subPanel = new SimpleHPanel<>(fieldName, new JPanel(layout), getLock(), getUntil());
			if (panel != null)
				panel.accept(subPanel);
			doAdd(subPanel);
			return (P) this;
		}

		@Override
		default P addVPanel(Consumer<PanelPopulator<JPanel, ?>> panel) {
			MigFieldPanel<JPanel> subPanel = new MigFieldPanel<>(new JPanel(), getUntil(), getLock());
			if (panel != null)
				panel.accept(subPanel);
			doAdd(subPanel, null, null, false);
			return (P) this;
		}

		@Override
		default P addCollapsePanel(boolean vertical, LayoutManager layout, Consumer<CollapsePanel<JXCollapsiblePane, JXPanel, ?>> panel) {
			JXCollapsiblePane cp = new JXCollapsiblePane();
			cp.setLayout(layout);
			SimpleCollapsePane collapsePanel = new SimpleCollapsePane(cp, getUntil(), getLock(), vertical, layout);
			panel.accept(collapsePanel);
			doAdd(collapsePanel, null, null, false);
			return (P) this;
		}
	}

	static abstract class PanelPopulatorImpl<C extends Container, P extends PanelPopulatorImpl<C, P>> implements PanelPopulator<C, P> {
		private final C theContainer;
		private final Observable<?> theUntil;
		private Object theLayoutConstraints;
		private final ReentrantReadWriteLock theLock;
		private final CollectionLockingStrategy theCLS;

		PanelPopulatorImpl(C container, Observable<?> until, ReentrantReadWriteLock lock, CollectionLockingStrategy cls) {
			theContainer = container;
			theUntil = until == null ? Observable.empty() : until;
			theLock = lock;
			theCLS = new RRWLockingStrategy(lock);
		}

		@Override
		public Observable<?> getUntil() {
			return theUntil;
		}

		@Override
		public P withLayoutConstraints(Object constraints) {
			theLayoutConstraints = constraints;
			return (P) this;
		}

		protected ReentrantReadWriteLock getLock() {
			return theLock;
		}

		protected CollectionLockingStrategy getCLS() {
			return theCLS;
		}

		@Override
		public C getContainer() {
			return theContainer;
		}

		protected void doAdd(SimpleFieldEditor<?, ?> editor) {
			doAdd(editor, editor.createFieldNameLabel(theUntil), editor.createPostLabel(theUntil));
		}

		protected abstract void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel);
	}

	static abstract class AbstractComponentEditor<E, P extends AbstractComponentEditor<E, P>> implements ComponentEditor<E, P> {
		final Supplier<Transactable> theLock;
		private final SimpleValueCache theValueCache;
		private final E theEditor;
		private Object theLayoutConstraints;
		private boolean isFillH;
		private boolean isFillV;
		private ComponentDecorator theDecorator;
		private Consumer<MouseEvent> theMouseListener;

		private ObservableValue<Boolean> isVisible;

		AbstractComponentEditor(E editor, Supplier<Transactable> lock) {
			theEditor = editor;
			theLock = lock;
			theValueCache = new SimpleValueCache(lock);
		}

		public Supplier<Transactable> getLock() {
			return theLock;
		}

		@Override
		public E getEditor() {
			return theEditor;
		}

		@Override
		public P visibleWhen(ObservableValue<Boolean> visible) {
			isVisible = visible;
			return (P) this;
		}

		@Override
		public P withLayoutConstraints(Object constraints) {
			theLayoutConstraints = constraints;
			return (P) this;
		}

		Object getLayoutConstraints() {
			return theLayoutConstraints;
		}

		@Override
		public P fill() {
			isFillH = true;
			return (P) this;
		}

		@Override
		public P fillV() {
			isFillV = true;
			return (P) this;
		}

		@Override
		public P decorate(Consumer<ComponentDecorator> decoration) {
			if (theDecorator == null)
				theDecorator = new ComponentDecorator();
			decoration.accept(theDecorator);
			return (P) this;
		}

		@Override
		public P modifyEditor(Consumer<? super E> modify) {
			modify.accept(getEditor());
			return (P) this;
		}

		@Override
		public ValueCache values() {
			return theValueCache;
		}

		@Override
		public Alert alert(String title, String message) {
			return new SimpleAlert(getComponent(), title, message);
		}

		@Override
		public P onMouse(Consumer<MouseEvent> onMouse) {
			if (theMouseListener == null)
				theMouseListener = onMouse;
			else {
				Consumer<MouseEvent> old = theMouseListener;
				theMouseListener = event -> {
					old.accept(event);
					onMouse.accept(event);
				};
			}
			return (P) this;
		}

		protected Component decorate(Component c) {
			if (theDecorator != null)
				theDecorator.decorate(c);
			if (theMouseListener != null)
				c.addMouseListener(new MouseListener() {
					@Override
					public void mouseClicked(MouseEvent e) {
						theMouseListener.accept(e);
					}

					@Override
					public void mousePressed(MouseEvent e) {
						theMouseListener.accept(e);
					}

					@Override
					public void mouseReleased(MouseEvent e) {
						theMouseListener.accept(e);
					}

					@Override
					public void mouseEntered(MouseEvent e) {
						theMouseListener.accept(e);
					}

					@Override
					public void mouseExited(MouseEvent e) {
						theMouseListener.accept(e);
					}
				});
			return c;
		}

		protected Component getOrCreateComponent(Observable<?> until) {
			// Subclasses should override this if the editor is not a component or is not the component that should be added
			if (!(theEditor instanceof Component))
				throw new IllegalStateException("Editor is not a component");
			return decorate((Component) theEditor);
		}

		@Override
		public Component getComponent() {
			// Subclasses should override this if the editor is not a component or is not the component that should be added
			if (!(theEditor instanceof Component))
				throw new IllegalStateException("Editor is not a component");
			return (Component) theEditor;
		}

		protected boolean isFill() {
			return isFillH;
		}

		protected boolean isFillV() {
			return isFillV;
		}

		protected ObservableValue<Boolean> isVisible() {
			return isVisible;
		}

		public abstract ObservableValue<String> getTooltip();

		protected abstract Component createFieldNameLabel(Observable<?> until);

		protected abstract Component createPostLabel(Observable<?> until);
	}

	static class VizChanger implements Consumer<ObservableValueEvent<Boolean>> {
		private final Component theComponent;
		private final Component theFieldLabel;
		private boolean shouldBeVisible;

		public VizChanger(Component component, Component fieldLabel) {
			theComponent = component;
			theFieldLabel = fieldLabel;
		}

		@Override
		public void accept(ObservableValueEvent<Boolean> evt) {
			boolean visible = evt.getNewValue();
			shouldBeVisible = visible;
			if (EventQueue.isDispatchThread())
				setVisible(visible);
			else {
				EventQueue.invokeLater(() -> {
					if (shouldBeVisible == visible)
						setVisible(visible);
				});
			}
		}

		private void setVisible(boolean visible) {
			if (visible == theComponent.isVisible())
				return;
			if (theFieldLabel != null)
				theFieldLabel.setVisible(visible);
			theComponent.setVisible(visible);
			if (theComponent != null)
				theComponent.setVisible(visible);
			if (theComponent.getParent() != null)
				theComponent.getParent().revalidate();
		}
	}

	static class MigFieldPanel<C extends Container> extends AbstractComponentEditor<C, MigFieldPanel<C>>
	implements PartialPanelPopulatorImpl<C, MigFieldPanel<C>> {
		private final Observable<?> theUntil;

		MigFieldPanel(C container, Observable<?> until, Supplier<Transactable> lock) {
			super(//
				container = (container != null ? container
					: (C) new JPanel(createMigLayout(true, () -> "install the layout before using this class"))), //
				lock);
			theUntil = until == null ? Observable.empty() : until;
			if (container.getLayout() == null || !MIG_LAYOUT_CLASS_NAME.equals(container.getLayout().getClass().getName())) {
				LayoutManager2 migLayout = createMigLayout(true, () -> "install the layout before using this class");
				container.setLayout(migLayout);
			}
		}

		@Override
		public C getContainer() {
			return getEditor();
		}

		@Override
		public Observable<?> getUntil() {
			return theUntil;
		}

		@Override
		public Supplier<Transactable> getLock() {
			return theLock;
		}

		@Override
		public void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel, boolean scrolled) {
			if (fieldLabel != null)
				getContainer().add(fieldLabel, "align right");
			StringBuilder constraints = new StringBuilder();
			if (field.getLayoutConstraints() != null)
				constraints.append(field.getLayoutConstraints());
			if (field.isFill()) {
				if (constraints.length() > 0)
					constraints.append(", ");
				constraints.append("growx, pushx");
			} else if (fieldLabel == null && postLabel == null) {
				if (constraints.length() > 0)
					constraints.append(", ");
				constraints.append("align center");
			}
			if (field.isFillV()) {
				if (constraints.length() > 0)
					constraints.append(", ");
				constraints.append("growy, pushy");
			}
			if (postLabel != null) {
				if (fieldLabel == null) {
					if (constraints.length() > 0)
						constraints.append(", ");
					constraints.append("span 2");
				}
			} else {
				if (constraints.length() > 0)
					constraints.append(", ");
				constraints.append("span, wrap");
			}
			Component component = field.getOrCreateComponent(getUntil());
			if (component == null)
				throw new IllegalStateException();
			if (scrolled)
				getContainer().add(new JScrollPane(component), constraints.toString());
			else
				getContainer().add(component, constraints.toString());
			if (postLabel != null)
				getContainer().add(postLabel, "wrap");
			if (field.isVisible() != null)
				field.isVisible().changes().takeUntil(getUntil()).act(new VizChanger(component, fieldLabel));
		}

		@Override
		public ObservableValue<String> getTooltip() {
			return null;
		}

		@Override
		protected Component createFieldNameLabel(Observable<?> until) {
			return null;
		}

		@Override
		protected Component createPostLabel(Observable<?> until) {
			return null;
		}
	}

	static class SimpleFieldEditor<E, P extends SimpleFieldEditor<E, P>> extends AbstractComponentEditor<E, P>
	implements FieldEditor<E, P> {
		private ObservableValue<String> theFieldName;
		private Consumer<FontAdjuster<?>> theFieldLabelModifier;
		private ObservableValue<String> theTooltip;
		private SettableValue<ObservableValue<String>> theSettableTooltip;
		private ObservableValue<String> thePostLabel;
		private SimpleButtonEditor<JButton, ?> thePostButton;
		private Consumer<FontAdjuster<?>> theFont;

		SimpleFieldEditor(String fieldName, E editor, Supplier<Transactable> lock) {
			super(editor, lock);
			theFieldName = fieldName == null ? null : ObservableValue.of(fieldName);
			theSettableTooltip = new SimpleSettableValue<>(TypeTokens.get().keyFor(ObservableValue.class).parameterized(String.class),
				true);
			theTooltip = ObservableValue.flatten(theSettableTooltip);
		}

		@Override
		public P withFieldName(ObservableValue<String> fieldName) {
			theFieldName = fieldName;
			return (P) this;
		}

		@Override
		public P withPostLabel(ObservableValue<String> postLabel) {
			if (thePostButton != null) {
				System.err.println("A field can only have one post component");
				thePostButton = null;
			}
			thePostLabel = postLabel;
			return (P) this;
		}

		@Override
		public P withPostButton(String buttonText, ObservableAction<?> action, Consumer<ButtonEditor<JButton, ?>> modify) {
			if (thePostLabel != null) {
				System.err.println("A field can only have one post component");
				thePostLabel = null;
			}
			thePostButton = new SimpleButtonEditor<>(null, new JButton(), buttonText, action, getLock(), true);
			if (modify != null)
				modify.accept(thePostButton);
			return (P) this;
		}

		@Override
		public P modifyFieldLabel(Consumer<FontAdjuster<?>> labelModifier) {
			if (theFieldLabelModifier == null)
				theFieldLabelModifier = labelModifier;
			else {
				Consumer<FontAdjuster<?>> prev = theFieldLabelModifier;
				theFieldLabelModifier = f -> {
					prev.accept(f);
					labelModifier.accept(f);
				};
			}
			return (P) this;
		}

		@Override
		public P withFont(Consumer<FontAdjuster<?>> font) {
			if (theFont == null)
				theFont = font;
			else {
				Consumer<FontAdjuster<?>> prev = theFont;
				theFont = f -> {
					prev.accept(f);
					font.accept(f);
				};
			}
			return (P) this;
		}

		@Override
		public P withTooltip(ObservableValue<String> tooltip) {
			theSettableTooltip.set(tooltip, null);
			return (P) this;
		}

		@Override
		public ObservableValue<String> getTooltip() {
			return theTooltip;
		}

		protected <C extends JComponent> C onFieldName(C fieldNameComponent, Consumer<String> fieldName, Observable<?> until) {
			if (theFieldName == null)
				return null;
			theFieldName.changes().takeUntil(until).act(evt -> fieldName.accept(evt.getNewValue()));
			if (fieldNameComponent != null && theFieldLabelModifier != null)
				theFieldLabelModifier.accept(new FontAdjuster<>(fieldNameComponent));
			if (fieldNameComponent != null && theFont != null)
				theFont.accept(new FontAdjuster<>(fieldNameComponent));
			return fieldNameComponent;
		}

		@Override
		protected Component createFieldNameLabel(Observable<?> until) {
			if (theFieldName == null)
				return null;
			JLabel fieldNameLabel = new JLabel(theFieldName.get());
			return onFieldName(fieldNameLabel, fieldNameLabel::setText, until);
		}

		@Override
		protected Component createPostLabel(Observable<?> until) {
			if (thePostLabel != null) {
				JLabel postLabel = new JLabel(thePostLabel.get());
				thePostLabel.changes().takeUntil(until).act(evt -> postLabel.setText(evt.getNewValue()));
				if (theFont != null)
					theFont.accept(new FontAdjuster<>(postLabel));
				return postLabel;
			} else if (thePostButton != null)
				return thePostButton.getOrCreateComponent(until);
			else
				return null;
		}

		@Override
		protected Component decorate(Component c) {
			if (theFont != null)
				theFont.accept(new FontAdjuster<>(c));
			return super.decorate(c);
		}
	}

	static class SimpleHPanel<C extends Container> extends SimpleFieldEditor<C, SimpleHPanel<C>>
	implements PartialPanelPopulatorImpl<C, SimpleHPanel<C>> {
		private final Observable<?> theUntil;

		SimpleHPanel(String fieldName, C editor, Supplier<Transactable> lock, Observable<?> until) {
			super(fieldName, editor, lock);
			theUntil = until;
		}

		@Override
		public C getContainer() {
			return getEditor();
		}

		@Override
		public Observable<?> getUntil() {
			return theUntil;
		}

		@Override
		public Supplier<Transactable> getLock() {
			return theLock;
		}

		@Override
		public void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel, boolean scrolled) {
			if (fieldLabel != null)
				getContainer().add(fieldLabel);
			Component component = field.getOrCreateComponent(getUntil());
			Object constraints;
			if ((field.isFill() || field.isFillV()) && getContainer().getLayout().getClass().getName().startsWith("net.mig")) {
				StringBuilder constraintsStr = new StringBuilder();
				if (field.isFill()) {
					if (constraintsStr.length() > 0)
						constraintsStr.append(", ");
					constraintsStr.append("growx, pushx");
				}
				if (field.isFillV()) {
					if (constraintsStr.length() > 0)
						constraintsStr.append(", ");
					constraintsStr.append("growy, pushy");
				}
				constraints = constraintsStr.toString();
			} else
				constraints = field.getLayoutConstraints();
			if (scrolled)
				getContainer().add(new JScrollPane(component), constraints);
			else
				getContainer().add(component, constraints);
			if (postLabel != null)
				getContainer().add(postLabel);
			if (field.isVisible() != null) {
				if (field.isVisible() != null)
					field.isVisible().changes().takeUntil(getUntil()).act(new VizChanger(component, fieldLabel));
			}
		}

		@Override
		public SimpleHPanel<C> addCheckField(String fieldName, SettableValue<Boolean> field, Consumer<FieldEditor<JCheckBox, ?>> modify) {
			SimpleFieldEditor<JCheckBox, ?> fieldPanel = new SimpleFieldEditor<>(fieldName, new JCheckBox(), getLock());
			fieldPanel.getEditor().setHorizontalTextPosition(SwingConstants.LEADING);
			Subscription sub = ObservableSwingUtils.checkFor(fieldPanel.getEditor(), fieldPanel.getTooltip(), field);
			getUntil().take(1).act(__ -> sub.unsubscribe());
			if (modify != null)
				modify.accept(fieldPanel);
			fieldPanel.onFieldName(fieldPanel.getEditor(), name -> fieldPanel.getEditor().setText(name), getUntil());
			doAdd(fieldPanel, null, fieldPanel.createPostLabel(theUntil), false);
			return this;
		}
	}

	static class SimpleImageControl implements ImageControl {
		private final SettableValue<ObservableValue<? extends Icon>> theSettableIcon;
		private final ObservableValue<Icon> theIcon;
		private final SimpleObservable<Void> theTweakObservable;
		private int theWidth;
		private int theHeight;
		private double theOpacity;

		public SimpleImageControl(String location) {
			theTweakObservable = new SimpleObservable<>();
			theSettableIcon = new SimpleSettableValue<>((Class<ObservableValue<? extends Icon>>) (Class<?>) ObservableValue.class, true);
			setLocation(location);
			theIcon = ObservableValue.flatten(theSettableIcon).refresh(theTweakObservable);
			theWidth = -1;
			theHeight = -1;
			theOpacity = 1;
		}

		ImageIcon getIcon(String location) {
			if (location == null)
				return null;
			ImageIcon icon = ObservableSwingUtils.getIcon(getClass(), location);
			return adjustIcon(icon);
		}

		ImageIcon adjustIcon(ImageIcon icon) {
			if (icon != null) {
				if ((theWidth >= 0 && icon.getIconWidth() != theWidth) || (theHeight >= 0 && icon.getIconHeight() != theHeight)) {
					icon = new ImageIcon(icon.getImage().getScaledInstance(//
						theWidth >= 0 ? theWidth : icon.getIconWidth(), //
							theHeight >= 0 ? theHeight : icon.getIconHeight(), //
								Image.SCALE_SMOOTH));
				}
			}
			return icon;
		}

		ObservableValue<Icon> getIcon() {
			return theIcon;
		}

		@Override
		public ImageControl setLocation(String location) {
			return variableLocation(ObservableValue.of(TypeTokens.get().STRING, location));
		}

		@Override
		public ImageControl variableLocation(ObservableValue<String> location) {
			return variable(location.map(this::getIcon));
		}

		@Override
		public ImageControl variable(ObservableValue<? extends Icon> icon) {
			theSettableIcon.set(icon, null);
			return this;
		}

		@Override
		public ImageControl withSize(int width, int height) {
			theWidth = width;
			theHeight = height;
			theTweakObservable.onNext(null);
			return this;
		}

		@Override
		public ImageControl withOpacity(double opacity) {
			if (opacity < 0 || opacity >= 1)
				throw new IllegalArgumentException("Opacity must be between 0 and 1, not " + opacity);
			theOpacity = opacity;
			theTweakObservable.onNext(null);
			return this;
		}
	}

	static class SimpleButtonEditor<B extends AbstractButton, P extends SimpleButtonEditor<B, P>> extends SimpleFieldEditor<B, P>
	implements ButtonEditor<B, P> {
		private final ObservableAction<?> theAction;
		private ObservableValue<String> theText;
		private ObservableValue<? extends Icon> theIcon;
		private ObservableValue<String> theDisablement;
		private final boolean isPostButton;

		SimpleButtonEditor(String fieldName, B button, String buttonText, ObservableAction<?> action, Supplier<Transactable> lock,
			boolean postButton) {
			super(fieldName, button, lock);
			theAction = action;
			theText = ObservableValue.of(TypeTokens.get().STRING, buttonText);
			isPostButton = postButton;
		}

		public ObservableValue<String> getText() {
			return theText;
		}

		public ObservableValue<? extends Icon> getIcon() {
			return theIcon;
		}

		@Override
		public P withText(ObservableValue<String> text) {
			theText = text;
			return (P) this;
		}

		@Override
		public P withIcon(ObservableValue<? extends Icon> icon) {
			theIcon = icon;
			if (icon != null)
				getEditor().setMargin(new Insets(2, 2, 2, 2));
			return (P) this;
		}

		@Override
		public P disableWith(ObservableValue<String> disabled) {
			if (theDisablement == null || disabled == null)
				theDisablement = disabled;
			else {
				ObservableValue<String> old = theDisablement;
				theDisablement = ObservableValue.firstValue(TypeTokens.get().STRING, msg -> msg != null, () -> null, old, disabled);
			}
			return (P) this;
		}

		@Override
		protected Component getOrCreateComponent(Observable<?> until) {
			getEditor().addActionListener(evt -> theAction.act(evt));
			ObservableValue<String> enabled;
			if (theDisablement != null) {
				enabled = ObservableValue.firstValue(TypeTokens.get().STRING, msg -> msg != null, () -> null, theDisablement,
					theAction.isEnabled());
			} else
				enabled = theAction.isEnabled();
			enabled.combine((e, tt) -> e == null ? tt : e, getTooltip()).changes().takeUntil(until)
			.act(evt -> getEditor().setToolTipText(evt.getNewValue()));
			enabled.takeUntil(until).changes().act(evt -> getEditor().setEnabled(evt.getNewValue() == null));
			if (theText != null)
				theText.changes().takeUntil(until).act(evt -> getEditor().setText(evt.getNewValue()));
			if (theIcon != null)
				theIcon.changes().takeUntil(until).act(evt -> getEditor().setIcon(evt.getNewValue()));
			return super.getOrCreateComponent(until);
		}

		@Override
		public P withFieldName(ObservableValue<String> fieldName) {
			if (isPostButton)
				throw new IllegalStateException("No field name for a post-button");
			return super.withFieldName(fieldName);
		}

		@Override
		public P withPostLabel(ObservableValue<String> postLabel) {
			if (isPostButton)
				throw new IllegalStateException("No post label for a post-button");
			return super.withPostLabel(postLabel);
		}

		@Override
		public P modifyFieldLabel(Consumer<FontAdjuster<?>> labelModifier) {
			if (isPostButton)
				throw new IllegalStateException("No field label for a post-button");
			return super.modifyFieldLabel(labelModifier);
		}
	}

	static class SimpleSteppedFieldEditor<E, F, P extends SimpleSteppedFieldEditor<E, F, P>> extends SimpleFieldEditor<E, P>
	implements SteppedFieldEditor<E, F, P> {
		private final Consumer<F> theStepSizeChange;

		SimpleSteppedFieldEditor(String fieldName, E editor, Supplier<Transactable> lock, Consumer<F> stepSizeChange) {
			super(fieldName, editor, lock);
			theStepSizeChange = stepSizeChange;
		}

		@Override
		public P withStepSize(F stepSize) {
			theStepSizeChange.accept(stepSize);
			return (P) this;
		}
	}

	static class SimpleComboEditor<F, P extends SimpleComboEditor<F, P>> extends SimpleFieldEditor<JComboBox<F>, P>
	implements ComboEditor<F, P> {
		private Function<? super F, String> theValueTooltip;

		SimpleComboEditor(String fieldName, JComboBox<F> editor, Supplier<Transactable> lock) {
			super(fieldName, editor, lock);
		}

		@Override
		public P renderWith(ObservableCellRenderer<F, F> renderer) {
			getEditor().setRenderer(renderer);
			return (P) this;
		}

		@Override
		public P withValueTooltip(Function<? super F, String> tooltip) {
			theValueTooltip = tooltip;
			return (P) this;
		}

		@Override
		public String getTooltip(F value) {
			return theValueTooltip == null ? null : theValueTooltip.apply(value);
		}
	}

	static class SimpleToggleButtonPanel<F, TB extends JToggleButton, P extends SimpleToggleButtonPanel<F, TB, P>>
	extends SimpleFieldEditor<Map<F, TB>, P> implements ToggleEditor<F, TB, P> {
		private final JPanel thePanel;
		private final ObservableCollection<? extends F> theValues;
		private final SettableValue<F> theValue;
		private final Class<TB> theButtonType;
		private final Function<? super F, ? extends TB> theButtonCreator;
		private ObservableCollection<TB> theButtons;

		private BiConsumer<? super TB, ? super F> theRenderer;
		private Function<? super F, String> theValueTooltip;

		SimpleToggleButtonPanel(String fieldName, ObservableCollection<? extends F> values, SettableValue<F> value, Class<TB> buttonType,
			Function<? super F, ? extends TB> buttonCreator, Supplier<Transactable> lock) {
			super(fieldName, new HashMap<>(), lock);
			thePanel = new JPanel(new JustifiedBoxLayout(false).setMainAlignment(JustifiedBoxLayout.Alignment.LEADING).crossJustified());

			theValue = value;
			theValues = values;

			theButtonType = buttonType;
			theButtonCreator = buttonCreator;
			theRenderer = (button, v) -> button.setText(String.valueOf(v));
		}

		@Override
		public P render(BiConsumer<? super TB, ? super F> renderer) {
			theRenderer = renderer;
			return (P) this;
		}

		@Override
		public P withValueTooltip(Function<? super F, String> tooltip) {
			theValueTooltip = tooltip;
			return (P) this;
		}

		void render(TB button, F value) {
			theRenderer.accept(button, value);
		}

		String getValueTooltip(F value) {
			return theValueTooltip == null ? null : theValueTooltip.apply(value);
		}

		@Override
		protected Component getOrCreateComponent(Observable<?> until) {
			if (theButtons == null) {
				SafeObservableCollection<? extends F> safeValues;
				if (theValues instanceof SafeObservableCollection)
					safeValues = (SafeObservableCollection<? extends F>) theValues;
				else
					safeValues = new SafeObservableCollection<>(theValues, EventQueue::isDispatchThread, EventQueue::invokeLater, until);
				ObservableCollection<TB>[] _buttons = new ObservableCollection[1];
				Subscription valueSub = ObservableSwingUtils.togglesFor(safeValues, theValue, TypeTokens.get().of(theButtonType),
					theButtonCreator, b -> _buttons[0] = b, this::render, this::getValueTooltip);
				theButtons = _buttons[0];
				for (JToggleButton button : theButtons) {
					thePanel.add(button);
				}
				theButtons.changes().takeUntil(until).act(evt -> {
					for (CollectionChangeEvent.ElementChange<TB> change : evt.getElements()) {
						switch (evt.type) {
						case add:
							thePanel.add(change.newValue, change.index);
							break;
						case remove:
							thePanel.remove(change.index);
							break;
						case set:
							break; // Re-rendering is handled by the togglesFor method
						}
					}
				});
				if (until != null)
					until.take(1).act(__ -> valueSub.unsubscribe());
			}
			return decorate(thePanel);
		}

		@Override
		public Component getComponent() {
			return decorate(thePanel);
		}
	}

	static class SimpleProgressEditor<P extends SimpleProgressEditor<P>> extends SimpleFieldEditor<JProgressBar, P> implements ProgressEditor<P>{
		private ObservableValue<Integer> theTaskLength;
		private ObservableValue<Integer> theProgress;
		private ObservableValue<Boolean> isIndeterminate;
		private ObservableValue<String> theText;
		private boolean isInitialized;

		public SimpleProgressEditor(String fieldName, Supplier<Transactable> lock) {
			super(fieldName, new JProgressBar(JProgressBar.HORIZONTAL), lock);
		}

		@Override
		public P withTaskLength(ObservableValue<Integer> length) {
			theTaskLength=length;
			return (P) this;
		}

		@Override
		public P withProgress(ObservableValue<Integer> progress) {
			theProgress=progress;
			if(theTaskLength==null)
				theTaskLength=ObservableValue.of(100); //Assume it's a percentage
			return (P) this;
		}

		@Override
		public P indeterminate(ObservableValue<Boolean> indeterminate) {
			isIndeterminate=indeterminate;
			return (P) this;
		}

		@Override
		public P indeterminate() {
			if (theProgress == null)
				withProgress(ObservableValue.of(0));
			return ProgressEditor.super.indeterminate();
		}

		@Override
		public P withProgressText(ObservableValue<String> text) {
			theText=text;
			return (P) this;
		}

		@Override
		protected Component getOrCreateComponent(Observable<?> until) {
			if (!isInitialized) {
				isInitialized = true;
				if (theProgress == null)
					throw new IllegalStateException("No progress value set");
				Observable.or(theTaskLength.noInitChanges(), theProgress.noInitChanges(), //
					isIndeterminate == null ? Observable.empty() : isIndeterminate.noInitChanges(), //
						theText == null ? Observable.empty() : theText.noInitChanges())//
				.takeUntil(until)//
				.act(__ -> updateProgress());
				updateProgress();
			}
			return super.getOrCreateComponent(until);
		}

		void updateProgress() {
			if ((isIndeterminate != null && Boolean.TRUE.equals(isIndeterminate.get()))//
				|| theTaskLength.get() == null || theTaskLength.get() <= 0//
				|| theProgress.get() == null || theProgress.get() < 0)
				getEditor().setIndeterminate(true);
			else {
				getEditor().setMaximum(theTaskLength.get());
				getEditor().setValue(theProgress.get());
				getEditor().setIndeterminate(false);
			}
			String text = theText == null ? null : theText.get();
			getEditor().setStringPainted(text != null);
			if (text != null)
				getEditor().setString(text);
		}
	}

	static class SimpleTabPaneEditor<P extends SimpleTabPaneEditor<P>> extends AbstractComponentEditor<JTabbedPane, P>
	implements TabPaneEditor<JTabbedPane, P> {
		static class Tab {
			final Object id;
			final SimpleTabEditor<?> tab;
			Component component;
			SimpleObservable<Void> tabEnd;
			boolean isRemovable;
			ObservableValue<String> theName;
			ObservableValue<Image> theIcon;
			Observable<?> until;
			Consumer<Object> onRemove;

			Tab(Object id, SimpleTabEditor<?> tab) {
				this.id = id;
				this.tab = tab;
			}
		}

		private final Observable<?> theUntil;
		private final Map<Object, Tab> theTabs;
		private final Map<Component, Tab> theTabsByComponent;
		private final SettableValue<Object> theSelectedTabId;
		private List<Runnable> thePostCreateActions;
		private Tab theSelectedTab;

		SimpleTabPaneEditor(Supplier<Transactable> lock, Observable<?> until) {
			super(new JTabbedPane(), lock);
			theUntil = until;
			theTabs = new LinkedHashMap<>();
			theTabsByComponent = new IdentityHashMap<>();
			theSelectedTabId = SettableValue.build(Object.class).build();
			thePostCreateActions = new LinkedList<>();
		}

		@Override
		public int getTabCount() {
			return getEditor().getTabCount();
		}

		@Override
		public P withVTab(Object tabID, int tabIndex, Consumer<PanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier) {
			MigFieldPanel<JPanel> fieldPanel = new MigFieldPanel<>(null, theUntil, theLock);
			panel.accept(fieldPanel);
			return withTabImpl(tabID, tabIndex, fieldPanel.getContainer(), tabModifier, fieldPanel);
		}

		@Override
		public P withHTab(Object tabID, int tabIndex, LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel,
			Consumer<TabEditor<?>> tabModifier) {
			SimpleHPanel<JPanel> hPanel = new SimpleHPanel<>(null, new JPanel(layout), theLock, theUntil);
			panel.accept(hPanel);
			return withTabImpl(tabID, tabIndex, hPanel.getContainer(), tabModifier, hPanel);
		}

		@Override
		public P withTab(Object tabID, int tabIndex, Component tabComponent, Consumer<TabEditor<?>> tabModifier) {
			return withTabImpl(tabID, tabIndex, tabComponent, tabModifier, null);
		}

		P withTabImpl(Object tabID, int tabIndex, Component tabComponent, Consumer<TabEditor<?>> tabModifier,
			AbstractComponentEditor<?, ?> panel) {
			if (tabID == null)
				throw new NullPointerException();
			SimpleTabEditor<?> t = new SimpleTabEditor<>(this, tabID, tabComponent);
			tabModifier.accept(t);
			Tab tab = new Tab(tabID, t);
			tab.onRemove = t.onRemove;
			Tab oldTab = theTabs.put(tabID, tab);
			if (oldTab != null) {
				oldTab.tabEnd.onNext(null);
				tab.tabEnd = oldTab.tabEnd;
				theTabsByComponent.remove(oldTab.component);
			} else
				tab.tabEnd = SimpleObservable.build().withIdentity(Identifiable.baseId("tab " + tabID, new BiTuple<>(this, tabID)))
				.build();
			Observable<?> tabUntil = Observable.or(tab.tabEnd, theUntil);
			tab.until = tabUntil;
			tab.component = t.getComponent(tabUntil);
			if (theTabsByComponent.put(tab.component, tab) != null)
				throw new IllegalStateException("Duplicate tab components (" + tabID + ")");
			if (panel != null && panel.isVisible() != null)
				panel.isVisible().changes().takeUntil(theUntil).act(new VizChanger(tab.component, null));
			if (oldTab != null) {
				for (int i = 0; i < getEditor().getTabCount(); i++) {
					if (getEditor().getComponentAt(i) == oldTab.component) {
						if (getEditor().getSelectedIndex() == i && tab.tab.getOnSelect() != null)
							tab.tab.getOnSelect().set(true, null);
						getEditor().setComponentAt(i, tab.component);
						break;
					}
				}
			} else
				getEditor().add(tab.component, tabIndex);
			tab.theName = t.getName();
			if (tab.theName != null)
				t.theName.changes().takeUntil(tabUntil).act(evt -> {
					getEditor().setTitleAt(getTabIndex(tabID), evt.getNewValue());
				});
			tab.theIcon = t.getIcon();
			if (tab.theIcon != null)
				t.theIcon.changes().takeUntil(tabUntil).act(evt -> {
					getEditor().setIconAt(getTabIndex(tabID), evt.getNewValue() == null ? null : new ImageIcon(evt.getNewValue()));
				});
			if (t.getSelection() != null) {
				t.getSelection().takeUntil(tabUntil).act(__ -> ObservableSwingUtils.onEQ(() -> {
					getEditor().setSelectedComponent(tab.component);
				}));
			}
			setRemovable(tabID, t.isRemovable);
			return (P) this;
		}

		@Override
		public P withSelectedTab(SettableValue<?> tabID) {
			Runnable action = () -> {
				tabID.changes().takeUntil(theUntil).act(evt -> ObservableSwingUtils.onEQ(() -> {
					if ((evt.getNewValue() == null || theTabs.containsKey(evt.getNewValue()))//
						&& !Objects.equals(theSelectedTabId.get(), evt.getNewValue())) {
						theSelectedTabId.set(evt.getNewValue(), evt.isTerminated() ? null : evt);
					}
				}));
				theSelectedTabId.noInitChanges().takeUntil(theUntil).act(evt -> {
					if (!Objects.equals(evt.getNewValue(), tabID.get()))
						((SettableValue<Object>) tabID).set(evt.getNewValue(), evt);
				});
			};
			if (thePostCreateActions != null)
				thePostCreateActions.add(action);
			else
				action.run();
			return (P) this;
		}

		@Override
		public P onSelectedTab(Consumer<ObservableValue<Object>> tabID) {
			tabID.accept(theSelectedTabId.unsettable());
			return (P) this;
		}

		@Override
		public ObservableValue<String> getTooltip() {
			return null;
		}

		private int getTabIndex(Object tabId) {
			int t = 0;
			for (Object tabId2 : theTabs.keySet()) {
				if (tabId2.equals(tabId))
					break;
				t++;
			}
			if (t == theTabs.size())
				return -1;
			return t;
		}

		@Override
		protected Component createFieldNameLabel(Observable<?> until) {
			return null;
		}

		@Override
		protected Component createPostLabel(Observable<?> until) {
			return null;
		}

		@Override
		protected Component getOrCreateComponent(Observable<?> until) {
			Component c = super.getOrCreateComponent(until);

			ObservableSwingUtils.onEQ(() -> {
				boolean[] initialized = new boolean[1];
				ChangeListener tabListener = evt -> {
					if (!initialized[0])
						return;
					Component selected = getEditor().getSelectedComponent();
					Tab selectedTab = selected == null ? null : theTabsByComponent.get(selected);
					if (selectedTab != theSelectedTab) {
						if (theSelectedTab != null && theSelectedTab.tab.getOnSelect() != null)
							theSelectedTab.tab.getOnSelect().set(false, evt);
						theSelectedTab = selectedTab;
						Object selectedTabId = selectedTab == null ? null : selectedTab.id;
						if (!Objects.equals(theSelectedTabId.get(), selectedTabId))
							theSelectedTabId.set(selectedTabId, evt);
						if (selectedTab != null && selectedTab.tab.getOnSelect() != null)
							selectedTab.tab.getOnSelect().set(true, evt);
					}
				};
				getEditor().setSelectedIndex(0);
				initialized[0] = true;
				theSelectedTabId.changes().takeUntil(until).act(evt -> {
					Object tabID = evt.getNewValue();
					if (evt.getNewValue() == null) {
						if (theSelectedTab == null)
							return;
						getEditor().setSelectedIndex(-1);
					} else {
						Tab tab = theTabs.get(evt.getNewValue());
						if (theSelectedTab == tab)
							return;
						getEditor().setSelectedComponent(tab.component);
					}
				});
				Component selected = getEditor().getSelectedComponent();
				theSelectedTab = selected == null ? null : theTabsByComponent.get(selected);
				getEditor().addChangeListener(tabListener);
				until.take(1).act(__ -> getEditor().removeChangeListener(tabListener));
				List<Runnable> pcas = thePostCreateActions;
				thePostCreateActions = null;
				for (Runnable action : pcas)
					action.run();
			});
			return c;
		}

		boolean remove(Object tabId, Object cause) {
			Tab found = theTabs.remove(tabId);
			if (found == null)
				return false;
			getEditor().remove(found.component);
			found.tabEnd.onNext(null);
			if (found.onRemove != null)
				found.onRemove.accept(cause);
			return true;
		}

		boolean select(Object tabId) {
			Tab found = theTabs.get(tabId);
			if (found == null)
				return false;
			getEditor().setSelectedComponent(found.component);
			return true;
		}

		void setRemovable(Object tabId, boolean removable) {
			Tab found = theTabs.get(tabId);
			if (found == null)
				return;
			if (found.isRemovable == removable)
				return;
			found.isRemovable = removable;
			int t = getTabIndex(tabId);
			if (t < 0)
				return; // Maybe removed already
			if (getEditor().getTabCount() <= t)
				return; // ??
			JComponent tabC = (JComponent) getEditor().getTabComponentAt(t);
			if (removable) {
				if (tabC == null) {
					tabC = new JPanel(new JustifiedBoxLayout(false));
					JLabel title = new JLabel(getEditor().getTitleAt(t));
					if (found.theName != null)
						found.theName.changes().takeUntil(found.until).act(evt -> {
							title.setText(evt.getNewValue());
						});
					if (found.theIcon != null)
						found.theIcon.changes().takeUntil(found.until).act(evt -> {
							title.setIcon(evt.getNewValue() == null ? null : new ImageIcon(evt.getNewValue()));
						});
					tabC.add(title);
					getEditor().setTabComponentAt(t, tabC);
				}
				JLabel removeLabel = new JLabel(ObservableSwingUtils.getFixedIcon(null, "/icons/redX.png", 8, 8));
				tabC.add(removeLabel);
				removeLabel.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						remove(tabId, e);
					}
				});
			} else if (tabC != null) {
				for (int c = tabC.getComponentCount() - 1; c >= 0; c--) {
					if (tabC instanceof JLabel) {
						tabC.remove(c);
						break;
					}
				}
			}
		}
	}

	static class SimpleTabEditor<P extends SimpleTabEditor<P>> implements TabEditor<P> {
		private final SimpleTabPaneEditor<?> theTabs;
		@SuppressWarnings("unused")
		private final Object theID;
		private final Component theComponent;
		private ObservableValue<String> theName;
		private ObservableValue<Image> theIcon;
		private Observable<?> theSelection;
		private SettableValue<Boolean> theOnSelect;
		private boolean isRemovable;
		private Consumer<Object> onRemove;

		public SimpleTabEditor(SimpleTabPaneEditor<?> tabs, Object id, Component component) {
			theTabs = tabs;
			theID = id;
			theComponent = component;
		}

		@Override
		public P setName(ObservableValue<String> name) {
			theName = name;
			return (P) this;
		}

		@Override
		public ObservableValue<String> getName() {
			return theName;
		}

		@Override
		public P setIcon(ObservableValue<Image> icon) {
			theIcon = icon;
			return (P) this;
		}

		@Override
		public ObservableValue<Image> getIcon() {
			return theIcon;
		}

		@Override
		public P selectOn(Observable<?> select) {
			if (theSelection == null)
				theSelection = select;
			else
				theSelection = Observable.or(theSelection, select);
			return (P) this;
		}

		@Override
		public P onSelect(Consumer<ObservableValue<Boolean>> onSelect) {
			if (theOnSelect == null)
				theOnSelect = SettableValue.build(boolean.class).nullable(false).withValue(false).build();
			onSelect.accept(theOnSelect);
			return (P) this;
		}

		@Override
		public P remove() {
			theTabs.remove(theID, null);
			return (P) this;
		}

		@Override
		public P select() {
			theTabs.select(theID);
			return (P) this;
		}

		@Override
		public P setRemovable(boolean removable) {
			isRemovable = removable;
			theTabs.setRemovable(theID, removable);
			return (P) this;
		}

		@Override
		public P onRemove(Consumer<Object> onRemove) {
			this.onRemove = onRemove;
			return (P) this;
		}

		protected Component getComponent(Observable<?> until) {
			if (theName == null)
				throw new IllegalArgumentException("Failed to set name on tab for " + theComponent);
			theName.changes().takeUntil(until).act(evt -> theComponent.setName(evt.getNewValue()));
			return theComponent;
		}

		Observable<?> getSelection() {
			return theSelection;
		}

		SettableValue<Boolean> getOnSelect() {
			return theOnSelect;
		}
	}

	static class SimpleSplitEditor<P extends SimpleSplitEditor<P>> extends AbstractComponentEditor<JSplitPane, P> implements SplitPane<P> {
		private final Observable<?> theUntil;
		private int theDivLocation = -1;
		private double theDivProportion = Double.NaN;
		private SettableValue<Integer> theObsDivLocation;
		private SettableValue<Double> theObsDivProportion;

		SimpleSplitEditor(boolean vertical, Supplier<Transactable> lock, Observable<?> until) {
			super(new JSplitPane(vertical ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT), lock);
			theUntil = until;
		}

		boolean hasSetFirst;
		boolean hasSetLast;
		boolean initialized;

		@Override
		public P firstV(Consumer<PanelPopulator<?, ?>> vPanel) {
			MigFieldPanel<JPanel> fieldPanel = new MigFieldPanel<>(null, theUntil, theLock);
			vPanel.accept(fieldPanel);
			first(fieldPanel.getOrCreateComponent(theUntil));
			if (fieldPanel.isVisible() != null)
				fieldPanel.isVisible().changes().takeUntil(theUntil).act(new VizChanger(fieldPanel.getComponent(), null));
			return (P) this;
		}

		@Override
		public P firstH(LayoutManager layout, Consumer<PanelPopulator<?, ?>> hPanel) {
			SimpleHPanel<JPanel> panel = new SimpleHPanel<>(null, new JPanel(layout), theLock, theUntil);
			hPanel.accept(panel);
			first(panel.getOrCreateComponent(theUntil));
			if (panel.isVisible() != null)
				panel.isVisible().changes().takeUntil(theUntil).act(new VizChanger(panel.getComponent(), null));
			return (P) this;
		}

		@Override
		public P first(Component component) {
			if (hasSetFirst)
				throw new IllegalStateException("First component has already been configured");
			hasSetFirst = true;
			getEditor().setLeftComponent(component);
			return (P) this;
		}

		@Override
		public P lastV(Consumer<PanelPopulator<?, ?>> vPanel) {
			MigFieldPanel<JPanel> fieldPanel = new MigFieldPanel<>(null, theUntil, theLock);
			vPanel.accept(fieldPanel);
			last(fieldPanel.getOrCreateComponent(theUntil));
			if (fieldPanel.isVisible() != null)
				fieldPanel.isVisible().changes().takeUntil(theUntil).act(new VizChanger(fieldPanel.getComponent(), null));
			return (P) this;
		}

		@Override
		public P lastH(LayoutManager layout, Consumer<PanelPopulator<?, ?>> hPanel) {
			SimpleHPanel<JPanel> panel = new SimpleHPanel<>(null, new JPanel(layout), theLock, theUntil);
			hPanel.accept(panel);
			last(panel.getOrCreateComponent(theUntil));
			if (panel.isVisible() != null)
				panel.isVisible().changes().takeUntil(theUntil).act(new VizChanger(panel.getComponent(), null));
			return (P) this;
		}

		@Override
		public P last(Component component) {
			if (hasSetLast)
				throw new IllegalStateException("Last component has already been configured");
			hasSetLast = true;
			getEditor().setRightComponent(component);
			return (P) this;
		}

		@Override
		public P withSplitLocation(int split) {
			theDivLocation = split;
			return (P) this;
		}

		@Override
		public P withSplitProportion(double split) {
			theDivProportion = split;
			return (P) this;
		}

		@Override
		public P withSplitLocation(SettableValue<Integer> split) {
			if (theObsDivProportion != null)
				throw new IllegalStateException("Cannot set the div location and the div proportion both");
			theObsDivLocation = split;
			return (P) this;
		}

		@Override
		public P withSplitProportion(SettableValue<Double> split) {
			if (theObsDivLocation != null)
				throw new IllegalStateException("Cannot set the div location and the div proportion both");
			theObsDivProportion = split;
			return (P) this;
		}

		@Override
		public ObservableValue<String> getTooltip() {
			return null;
		}

		@Override
		protected Component createFieldNameLabel(Observable<?> until) {
			return null;
		}

		@Override
		protected Component createPostLabel(Observable<?> until) {
			return null;
		}

		@Override
		protected Component getOrCreateComponent(Observable<?> until) {
			boolean init = !initialized;
			Component c = super.getOrCreateComponent(until);
			if (init) {
				initialized = true;
				SettableValue<Integer> divLoc = theObsDivLocation;
				SettableValue<Double> divProp = theObsDivProportion;
				if (divLoc != null || divProp != null) {
					boolean[] callbackLock = new boolean[1];
					ComponentAdapter visListener = new ComponentAdapter() {
						@Override
						public void componentShown(ComponentEvent e) {
							EventQueue.invokeLater(() -> {
								callbackLock[0] = true;
								try {
									if (divProp != null)
										getEditor().setDividerLocation(divProp.get());
									else
										getEditor().setDividerLocation(divLoc.get());
								} finally {
									callbackLock[0] = false;
								}
							});
						}

						@Override
						public void componentResized(ComponentEvent e) {
							if (divProp != null) {
								if (callbackLock[0])
									return;
								callbackLock[0] = true;
								try {
									getEditor().setDividerLocation(divProp.get());
								} finally {
									callbackLock[0] = false;
								}
							}
						}
					};
					if (getEditor().getLeftComponent() != null) {
						getEditor().getLeftComponent().addComponentListener(new ComponentAdapter() {
							@Override
							public void componentShown(ComponentEvent e) {
								if (getEditor().getRightComponent() == null || !getEditor().getRightComponent().isVisible())
									return;
								EventQueue.invokeLater(() -> {
									callbackLock[0] = true;
									try {
										if (divProp != null)
											getEditor().setDividerLocation(divProp.get());
										else
											getEditor().setDividerLocation(divLoc.get());
									} finally {
										callbackLock[0] = false;
									}
								});
							}
						});
					}
					if (getEditor().getRightComponent() != null) {
						getEditor().getRightComponent().addComponentListener(new ComponentAdapter() {
							@Override
							public void componentShown(ComponentEvent e) {
								if (getEditor().getLeftComponent() == null || !getEditor().getLeftComponent().isVisible())
									return;
								EventQueue.invokeLater(() -> {
									callbackLock[0] = true;
									try {
										if (divProp != null)
											getEditor().setDividerLocation(divProp.get());
										else
											getEditor().setDividerLocation(divLoc.get());
									} finally {
										callbackLock[0] = false;
									}
								});
							}
						});
					}
					IntSupplier length = getEditor().getOrientation() == JSplitPane.VERTICAL_SPLIT ? getEditor()::getHeight
						: getEditor()::getWidth;
					getEditor().addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
						if (callbackLock[0])
							return;
						callbackLock[0] = true;
						try {
							if (divProp != null)
								divProp.set(getEditor().getDividerLocation() * 1.0 / length.getAsInt(), evt);
							else
								divLoc.set(getEditor().getDividerLocation(), evt);
						} finally {
							callbackLock[0] = false;
						}
					});
					if (divProp != null) {
						divProp.changes().takeUntil(until).act(evt -> {
							if (callbackLock[0])
								return;
							EventQueue.invokeLater(() -> {
								callbackLock[0] = true;
								try {
									getEditor().setDividerLocation(evt.getNewValue());
								} finally {
									callbackLock[0] = false;
								}
							});
						});
					} else {
						divLoc.changes().takeUntil(until).act(evt -> {
							if (callbackLock[0])
								return;
							EventQueue.invokeLater(() -> {
								callbackLock[0] = true;
								try {
									getEditor().setDividerLocation(evt.getNewValue());
								} finally {
									callbackLock[0] = false;
								}
							});
						});
					}
				} else if (theDivLocation >= 0 || !Double.isNaN(theDivProportion)) {
					if (c.isVisible()) {
						EventQueue.invokeLater(() -> {
							if (theDivLocation >= 0)
								getEditor().setDividerLocation(theDivLocation);
							else
								getEditor().setDividerLocation(theDivProportion);
						});
					} else {
						ComponentAdapter[] visListener = new ComponentAdapter[1];
						visListener[0] = new ComponentAdapter() {
							@Override
							public void componentShown(ComponentEvent e) {
								c.removeComponentListener(visListener[0]);
								EventQueue.invokeLater(() -> {
									if (theDivLocation >= 0)
										getEditor().setDividerLocation(theDivLocation);
									else
										getEditor().setDividerLocation(theDivProportion);
								});
							}
						};
						c.addComponentListener(visListener[0]);
					}
					if (getEditor().getLeftComponent() != null) {
						getEditor().getLeftComponent().addComponentListener(new ComponentAdapter() {
							@Override
							public void componentShown(ComponentEvent e) {
								if (getEditor().getRightComponent() == null || !getEditor().getRightComponent().isVisible())
									return;
								EventQueue.invokeLater(() -> {
									if (theDivLocation >= 0)
										getEditor().setDividerLocation(theDivLocation);
									else
										getEditor().setDividerLocation(theDivProportion);
								});
							}
						});
					}
					if (getEditor().getRightComponent() != null) {
						getEditor().getRightComponent().addComponentListener(new ComponentAdapter() {
							@Override
							public void componentShown(ComponentEvent e) {
								if (getEditor().getLeftComponent() == null || !getEditor().getLeftComponent().isVisible())
									return;
								EventQueue.invokeLater(() -> {
									if (theDivLocation >= 0)
										getEditor().setDividerLocation(theDivLocation);
									else
										getEditor().setDividerLocation(theDivProportion);
								});
							}
						});
					}
				}
			}
			return c;
		}
	}

	static class SimpleScrollEditor<P extends SimpleScrollEditor<P>> extends SimpleFieldEditor<JScrollPane, P>
	implements ScrollPane<P> {
		private final Observable<?> theUntil;

		private boolean isContentSet;

		public SimpleScrollEditor(String fieldName, Supplier<Transactable> lock, Observable<?> until) {
			super(fieldName, new JScrollPane(), lock);
			getEditor().getVerticalScrollBar().setUnitIncrement(10);
			getEditor().getHorizontalScrollBar().setUnitIncrement(10);
			theUntil = until;
		}

		@Override
		public P withVContent(Consumer<PanelPopulator<?, ?>> panel) {
			MigFieldPanel<JPanel> fieldPanel = new MigFieldPanel<>(null, theUntil, theLock);
			panel.accept(fieldPanel);
			withContent(fieldPanel.getOrCreateComponent(theUntil));
			if (fieldPanel.isVisible() != null)
				fieldPanel.isVisible().changes().takeUntil(theUntil).act(new VizChanger(fieldPanel.getComponent(), null));
			return (P) this;
		}

		@Override
		public P withHContent(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel) {
			SimpleHPanel<JPanel> hPanel = new SimpleHPanel<>(null, new JPanel(layout), theLock, theUntil);
			panel.accept(hPanel);
			withContent(hPanel.getOrCreateComponent(theUntil));
			if (hPanel.isVisible() != null)
				hPanel.isVisible().changes().takeUntil(theUntil).act(new VizChanger(hPanel.getComponent(), null));
			return (P) this;
		}

		@Override
		public P withContent(Component component) {
			if (isContentSet)
				throw new IllegalStateException("Content has already been configured");
			isContentSet = true;
			getEditor().add(component);
			return (P) this;
		}
	}

	static class SimpleCollapsePane extends AbstractComponentEditor<JXPanel, SimpleCollapsePane>
	implements PartialPanelPopulatorImpl<JXPanel, SimpleCollapsePane>, CollapsePanel<JXCollapsiblePane, JXPanel, SimpleCollapsePane> {
		private final JXCollapsiblePane theCollapsePane;
		private final PartialPanelPopulatorImpl<JPanel, ?> theOuterContainer;
		private final PartialPanelPopulatorImpl<JXPanel, ?> theContentPanel;
		private SimpleHPanel<JPanel> theHeaderPanel;
		private final Observable<?> theUntil;
		private final SettableValue<Boolean> theInternalCollapsed;

		private Icon theCollapsedIcon;
		private Icon theExpandedIcon;

		private SettableValue<Boolean> isCollapsed;
		private boolean isInitialized;

		SimpleCollapsePane(JXCollapsiblePane cp, Observable<?> until, Supplier<Transactable> lock, boolean vertical, LayoutManager layout) {
			super((JXPanel) cp.getContentPane(), lock);
			theCollapsePane = cp;
			theCollapsePane.setLayout(layout);
			theUntil = until;
			if (vertical)
				theContentPanel = new MigFieldPanel(getEditor(), getUntil(), lock);
			else
				theContentPanel = new SimpleHPanel(null, getEditor(), lock, getUntil());
			theOuterContainer = new MigFieldPanel(new JPanel(), until, lock);
			theHeaderPanel = new SimpleHPanel(null,
				new JPanel(new JustifiedBoxLayout(false).setMainAlignment(JustifiedBoxLayout.Alignment.LEADING)), getLock(), getUntil());

			theCollapsedIcon = ObservableSwingUtils.getFixedIcon(PanelPopulation.class, "/icons/circlePlus.png", 16, 16);
			theExpandedIcon = ObservableSwingUtils.getFixedIcon(PanelPopulation.class, "/icons/circleMinus.png", 16, 16);
			theInternalCollapsed = SettableValue.build(boolean.class).withValue(theCollapsePane.isCollapsed()).build();
			theInternalCollapsed.set(theCollapsePane.isCollapsed(), null);
			theCollapsePane.addPropertyChangeListener("collapsed", evt -> {
				boolean collapsed = Boolean.TRUE.equals(evt.getNewValue());
				theInternalCollapsed.set(collapsed, evt);
				if (collapsed)
					theCollapsePane.setVisible(false);
			});
			theHeaderPanel.fill().decorate(cd -> cd.bold().withFontSize(16))//
			.addIcon(null, theInternalCollapsed.map(collapsed -> collapsed ? theCollapsedIcon : theExpandedIcon),
				icon -> icon.withTooltip(theInternalCollapsed.map(collapsed -> collapsed ? "Expand" : "Collapse")))
			.spacer(2);
			decorate(deco -> deco.withBorder(BorderFactory.createLineBorder(Color.black)));
		}

		@Override
		public Component getComponent() {
			return getOrCreateComponent(theUntil);
		}

		@Override
		public SimpleCollapsePane withCollapsed(SettableValue<Boolean> collapsed) {
			isCollapsed = collapsed;
			return this;
		}

		@Override
		public SimpleCollapsePane modifyCP(Consumer<JXCollapsiblePane> cp) {
			cp.accept(theCollapsePane);
			return this;
		}

		@Override
		public SimpleCollapsePane withHeader(Consumer<PanelPopulator<JPanel, ?>> header) {
			header.accept(theHeaderPanel);
			return this;
		}

		@Override
		public Observable<?> getUntil() {
			return theUntil;
		}

		@Override
		public void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel, boolean scrolled) {
			theContentPanel.doAdd(field, fieldLabel, postLabel, scrolled);
		}

		@Override
		public JXPanel getContainer() {
			return theContentPanel.getContainer();
		}

		@Override
		protected Component getOrCreateComponent(Observable<?> until) {
			if (!isInitialized) {
				isInitialized = true;
				theOuterContainer.doAdd(theHeaderPanel, null, null, false);
				theOuterContainer.addComponent(null, theCollapsePane, c -> c.fill());
				theHeaderPanel.getComponent().addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						theCollapsePane.setVisible(true);
						theCollapsePane.setCollapsed(!theCollapsePane.isCollapsed());
					}
				});
				SettableValue<Boolean> collapsed = isCollapsed;
				if (collapsed != null) {
					boolean[] feedback = new boolean[1];
					collapsed.changes().act(evt -> {
						if (feedback[0])
							return;
						feedback[0] = true;
						try {
							theCollapsePane.setCollapsed(Boolean.TRUE.equals(evt.getNewValue()));
						} finally {
							feedback[0] = false;
						}
					});
					theCollapsePane.addPropertyChangeListener("collapsed", new PropertyChangeListener() {
						@Override
						public void propertyChange(PropertyChangeEvent evt) {
							if (feedback[0])
								return;
							feedback[0] = true;
							try {
								collapsed.set(Boolean.TRUE.equals(evt.getNewValue()), evt);
							} finally {
								feedback[0] = false;
							}
						}
					});
				}
				theContentPanel.getComponent(); // Initialization
			}
			decorate(theOuterContainer.getComponent());
			return theOuterContainer.getComponent();
		}

		@Override
		public ObservableValue<String> getTooltip() {
			return null;
		}

		@Override
		protected Component createFieldNameLabel(Observable<?> until) {
			return null;
		}

		@Override
		protected Component createPostLabel(Observable<?> until) {
			return null;
		}
	}

	static Icon getAddIcon(int size) {
		return ObservableSwingUtils.getFixedIcon(null, "/icons/add.png", size, size);
	}

	static Icon getRemoveIcon(int size) {
		return ObservableSwingUtils.getFixedIcon(null, "/icons/remove.png", size, size);
	}

	static Icon getCopyIcon(int size) {
		return ObservableSwingUtils.getFixedIcon(null, "/icons/copy.png", size, size);
	}

	static Icon getMoveIcon(boolean up, int size) {
		return ObservableSwingUtils.getFixedIcon(null, "/icons/move" + (up ? "Up" : "Down") + ".png", size, size);
	}

	static Icon getMoveEndIcon(boolean up, int size) {
		return ObservableSwingUtils.getFixedIcon(null, "/icons/move" + (up ? "Top" : "Bottom") + ".png", size, size);
	}

	static class SimpleListBuilder<R, P extends SimpleListBuilder<R, P>> extends SimpleFieldEditor<LittleList<R>, P>
	implements ListBuilder<R, P> {
		class ListItemAction<A extends SimpleDataAction<R, A>> extends SimpleDataAction<R, A> {
			private final List<R> theActionItems;

			ListItemAction(Consumer<? super List<? extends R>> action, Supplier<List<R>> selectedValues) {
				super(SimpleListBuilder.this, action, selectedValues);
				theActionItems = new ArrayList<>();
			}

			@Override
			void updateSelection(List<R> selectedValues, Object cause) {
				theActionItems.clear();
				theActionItems.addAll(selectedValues);
				super.updateSelection(selectedValues, cause);
			}
		}
		private String theItemName;
		private SettableValue<R> theSelectionValue;
		private ObservableCollection<R> theSelectionValues;
		private List<ListItemAction<?>> theActions;

		SimpleListBuilder(ObservableCollection<R> rows, Supplier<Transactable> lock) {
			super(null, new LittleList<>(new ObservableListModel<>(rows)), lock);
		}

		@Override
		public P render(Consumer<CategoryRenderStrategy<R, R>> render) {
			render.accept(getEditor().getRenderStrategy());
			return (P) this;
		}

		@Override
		public P withSelection(SettableValue<R> selection, boolean enforceSingleSelection) {
			theSelectionValue = selection;
			if (enforceSingleSelection)
				getEditor().getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			return (P) this;
		}

		@Override
		public P withSelection(ObservableCollection<R> selection) {
			theSelectionValues = selection;
			return (P) this;
		}

		@Override
		public List<R> getSelection() {
			return ObservableSwingUtils.getSelection(getEditor().getModel(), getEditor().getSelectionModel(), null);
		}

		@Override
		public P withAdd(Supplier<? extends R> creator, Consumer<DataAction<R, ?>> actionMod) {
			throw new UnsupportedOperationException("Not implemented yet");
		}

		private CollectionElement<R> findElement(R value) {
			ObservableCollection<R> rows = getEditor().getModel().getWrapped();
			CollectionElement<R> el = rows.getElement(value, false);
			if (el != null && el.get() != value) {
				CollectionElement<R> lastMatch = rows.getElement(value, true);
				if (!lastMatch.getElementId().equals(el.getElementId())) {
					if (lastMatch.get() == value)
						el = lastMatch;
					else {
						while (el.get() != value && !el.getElementId().equals(lastMatch.getElementId()))
							el = rows.getAdjacentElement(el.getElementId(), true);
					}
					if (el.get() != value)
						el = null;
				}
			}
			return el;
		}

		@Override
		public P withRemove(Consumer<? super List<? extends R>> deletion, Consumer<DataAction<R, ?>> actionMod) {
			return withMultiAction(deletion, action -> {
				action.allowForMultiple(false).withTooltip(items -> "Remove selected " + getItemName())//
				.modifyButton(button -> button.withIcon(getRemoveIcon(8)));
				if (actionMod != null)
					actionMod.accept(action);
			});
		}

		@Override
		public P withCopy(Function<? super R, ? extends R> copier, Consumer<DataAction<R, ?>> actionMod) {
			return withMultiAction(values -> {
				try (Transaction t = getEditor().getModel().getWrapped().lock(true, null)) {
					if (getEditor().getModel().getPendingUpdates() > 0) {
						// If there are queued changes, we can't rely on indexes we get back from the model
						simpleCopy(values, copier);
					} else {// Ignore the given values and use the selection model so we get the indexes right in the case of duplicates
						betterCopy(copier);
					}
				}
			}, action -> {
				String single = getItemName();
				String plural = StringUtils.pluralize(single);
				action.allowForMultiple(false).withTooltip(items -> "Duplicate selected " + (items.size() == 1 ? single : plural))//
				.modifyButton(button -> button.withIcon(getCopyIcon(16)));
				if (actionMod != null)
					actionMod.accept(action);
			});
		}

		private void simpleCopy(List<? extends R> selection, Function<? super R, ? extends R> copier) {
			for (R value : selection) {
				R copy = copier.apply(value);
				getEditor().getModel().getWrapped().add(copy);
			}
		}

		private void betterCopy(Function<? super R, ? extends R> copier) {
			ObservableCollection<R> rows = getEditor().getModel().getWrapped();
			ListSelectionModel selModel = getEditor().getSelectionModel();
			IntList newSelection = new IntList();
			for (int i = selModel.getMinSelectionIndex(); i >= 0 && i <= selModel.getMaxSelectionIndex(); i++) {
				if (!selModel.isSelectedIndex(i))
					continue;
				CollectionElement<R> toCopy = rows.getElement(i);
				R copy = copier.apply(toCopy.get());
				CollectionElement<R> copied = findElement(copy);
				if (copied != null) {} else if (rows.canAdd(copy, toCopy.getElementId(), null) == null)
					copied = rows.addElement(copy, toCopy.getElementId(), null, true);
				else
					copied = rows.addElement(copy, false);
				if (copied != null)
					newSelection.add(rows.getElementsBefore(copied.getElementId()));
			}
			selModel.setValueIsAdjusting(true);
			selModel.clearSelection();
			for (int[] interval : ObservableSwingUtils.getContinuousIntervals(newSelection.toArray(), true))
				selModel.addSelectionInterval(interval[0], interval[1]);
			selModel.setValueIsAdjusting(false);
		}

		@Override
		public P withMultiAction(Consumer<? super List<? extends R>> action, Consumer<DataAction<R, ?>> actionMod) {
			if(theActions==null)
				theActions = new ArrayList<>();
			ListItemAction<?>[] tableAction = new ListItemAction[1];
			tableAction[0] = new ListItemAction(action, () -> tableAction[0].theActionItems);
			if (actionMod != null)
				actionMod.accept(tableAction[0]);
			theActions.add(tableAction[0]);
			return (P) this;
		}

		@Override
		public P withItemName(String itemName) {
			theItemName = itemName;
			return (P) this;
		}

		@Override
		public String getItemName() {
			return theItemName;
		}

		@Override
		public ObservableCollection<? extends R> getRows() {
			return getEditor().getModel().getWrapped();
		}

		@Override
		protected Component getOrCreateComponent(Observable<?> until) {
			Supplier<List<R>> selectionGetter = () -> getSelection();
			ObservableListModel<R> model = getEditor().getModel();
			if (theSelectionValue != null)
				ObservableSwingUtils.syncSelection(getEditor(), model, getEditor()::getSelectionModel, model.getWrapped().equivalence(),
					theSelectionValue, until, index -> {
						MutableCollectionElement<R> el = (MutableCollectionElement<R>) getRows()
							.mutableElement(getRows().getElement(index).getElementId());
						if (el.isAcceptable(el.get()) == null)
							el.set(el.get());
					}, false);
			if (theSelectionValues != null)
				ObservableSwingUtils.syncSelection(getEditor(), model, getEditor()::getSelectionModel, model.getWrapped().equivalence(),
					theSelectionValues, until);

			if (theActions != null) {
				for (ListItemAction<?> action : theActions) {
					if (action.isAllowedForEmpty() || action.isAllowedForMultiple()) {
						System.err.println("Multi actions not supported yet");
					} else
						getEditor().addItemAction(itemActionFor(action, until));
				}
			}

			return decorate(new ScrollPaneLite(getEditor()));
		}

		private LittleList.ItemAction<R> itemActionFor(ListItemAction<?> tableAction, Observable<?> until) {
			class ItemAction<P extends ItemAction<P>> implements LittleList.ItemAction<R>, ButtonEditor<JButton, P> {
				private Action theAction;
				private boolean isEnabled;
				private ComponentDecorator theDecorator;

				@Override
				public void configureAction(Action action, R item, int index) {
					theAction = action;
					action.setEnabled(true);
					tableAction.updateSelection(Arrays.asList(item), null);
					if (tableAction.theButtonMod != null)
						tableAction.theButtonMod.accept(this);

					String s;
					if (tableAction.theTooltipString != null) {
						s = tableAction.theTooltipString.get();
						if (s != null)
							action.putValue(Action.LONG_DESCRIPTION, s);
					}
					if (tableAction.theEnabledString != null) {
						s = tableAction.theEnabledString.get();
						if (s != null) {
							action.setEnabled(false);
							action.putValue(Action.LONG_DESCRIPTION, s);
						}
					}
					if (theDecorator != null)
						action.putValue("decorator", theDecorator);
				}

				@Override
				public void actionPerformed(R item, int index, Object cause) {
					theAction = null;
					isEnabled = true;
					tableAction.updateSelection(Arrays.asList(item), null);
					if (tableAction.theButtonMod != null)
						tableAction.theButtonMod.accept(this);
					if (tableAction.theEnabledString != null) {
						String s = tableAction.theEnabledString.get();
						if (s != null)
							isEnabled = false;
					}
					if (isEnabled)
						tableAction.theObservableAction.act(cause);
				}

				@Override
				public P withFieldName(ObservableValue<String> fieldName) {
					return (P) this;
				}

				@Override
				public P withPostLabel(ObservableValue<String> postLabel) {
					return (P) this;
				}

				@Override
				public P withPostButton(String buttonText, ObservableAction<?> action, Consumer<ButtonEditor<JButton, ?>> modify) {
					return (P) this;
				}

				@Override
				public P withTooltip(ObservableValue<String> tooltip) {
					if (theAction != null && theAction.isEnabled())
						theAction.putValue(Action.LONG_DESCRIPTION, tooltip.get());
					return (P) this;
				}

				@Override
				public P modifyFieldLabel(Consumer<FontAdjuster<?>> font) {
					return (P) this;
				}

				@Override
				public P withFont(Consumer<FontAdjuster<?>> font) {
					if (theAction != null)
						theAction.putValue("font", font);
					return (P) this;
				}

				@Override
				public JButton getEditor() {
					throw new UnsupportedOperationException();
				}

				@Override
				public P visibleWhen(ObservableValue<Boolean> visible) {
					boolean vis = visible == null || visible.get();
					isEnabled &= vis;
					if (theAction != null)
						theAction.putValue("visible", vis);
					return (P) this;
				}

				@Override
				public P withLayoutConstraints(Object constraints) {
					return (P) this;
				}

				@Override
				public P fill() {
					return (P) this;
				}

				@Override
				public P fillV() {
					return (P) this;
				}

				@Override
				public P decorate(Consumer<ComponentDecorator> decoration) {
					if (theDecorator != null)
						theDecorator = new ComponentDecorator();
					decoration.accept(theDecorator);
					return (P) this;
				}

				@Override
				public P modifyEditor(Consumer<? super JButton> modify) {
					return (P) this;
				}

				@Override
				public Component getComponent() {
					throw new UnsupportedOperationException();
				}

				@Override
				public Alert alert(String title, String message) {
					return SimpleListBuilder.this.alert(title, message);
				}

				@Override
				public P onMouse(Consumer<MouseEvent> onMouse) {
					throw new UnsupportedOperationException();
				}

				@Override
				public ValueCache values() {
					throw new UnsupportedOperationException();
				}

				@Override
				public P withText(ObservableValue<String> text) {
					if (theAction != null)
						theAction.putValue(Action.NAME, text.get());
					return (P) this;
				}

				@Override
				public P withIcon(ObservableValue<? extends Icon> icon) {
					if (theAction != null)
						theAction.putValue(Action.SMALL_ICON, icon == null ? null : icon.get());
					return (P) this;
				}

				@Override
				public P disableWith(ObservableValue<String> disabled) {
					String msg = disabled.get();
					isEnabled &= (msg != null);
					if (theAction != null) {
						theAction.setEnabled(msg == null);
						if (msg != null)
							theAction.putValue(Action.LONG_DESCRIPTION, msg);
					}
					return (P) this;
				}
			}
			return new ItemAction();
		}
	}

	static class SimpleDataAction<R, A extends SimpleDataAction<R, A>> implements DataAction<R, A> {
		private final CollectionWidgetBuilder<R, ?, ?> theWidget;
		final Consumer<? super List<? extends R>> theAction;
		final Supplier<List<R>> theSelectedValues;
		private Function<? super R, String> theEnablement;
		private Function<? super List<? extends R>, String> theMultiEnablement;
		private Function<? super List<? extends R>, String> theTooltip;
		private boolean zeroAllowed;
		private boolean multipleAllowed;
		private boolean actWhenAnyEnabled;
		ObservableAction<?> theObservableAction;
		SettableValue<String> theEnabledString;
		SettableValue<String> theTooltipString;
		Consumer<ButtonEditor<?, ?>> theButtonMod;

		SimpleDataAction(CollectionWidgetBuilder<R, ?, ?> widget, Consumer<? super List<? extends R>> action,
			Supplier<List<R>> selectedValues) {
			theWidget = widget;
			theAction = action;
			theSelectedValues = selectedValues;
			theEnabledString = new SimpleSettableValue<>(String.class, true);
			theObservableAction = new ObservableAction<Object>() {
				@Override
				public TypeToken<Object> getType() {
					return TypeTokens.get().OBJECT;
				}

				@Override
				public Object act(Object cause) throws IllegalStateException {
					List<R> selected = theSelectedValues.get();
					if (theEnablement != null)
						selected = QommonsUtils.filterMap(selected, v -> theEnablement.apply(v) == null, null);
					theAction.accept(selected);
					return null;
				}

				@Override
				public ObservableValue<String> isEnabled() {
					return theEnabledString;
				}
			};
			multipleAllowed = true;
		}

		public String isEnabled() {
			return theEnabledString.get();
		}

		@Override
		public boolean isAllowedForMultiple() {
			return zeroAllowed;
		}

		@Override
		public boolean isAllowedForEmpty() {
			return multipleAllowed;
		}

		@Override
		public A allowForMultiple(boolean allow) {
			multipleAllowed = allow;
			return (A) this;
		}

		@Override
		public A allowForEmpty(boolean allow) {
			zeroAllowed = allow;
			return (A) this;
		}

		@Override
		public A allowForAnyEnabled(boolean allow) {
			actWhenAnyEnabled = allow;
			return (A) this;
		}

		@Override
		public A allowWhen(Function<? super R, String> filter, Consumer<ActionEnablement<R>> operation) {
			Function<? super R, String> newFilter;
			if (operation != null) {
				ActionEnablement<R> next = new ActionEnablement<>(filter);
				operation.accept(next);
				newFilter = next;
			} else
				newFilter = filter;
			if (theEnablement == null)
				theEnablement = newFilter;
			else {
				Function<? super R, String> oldFilter = theEnablement;
				theEnablement = value -> {
					String msg = oldFilter.apply(value);
					if (msg != null)
						return msg;
					return newFilter.apply(value);
				};
			}
			return (A) this;
		}

		@Override
		public A allowWhenMulti(Function<? super List<? extends R>, String> filter,
			Consumer<ActionEnablement<List<? extends R>>> operation) {
			Function<? super List<? extends R>, String> newFilter;
			if (operation != null) {
				ActionEnablement<List<? extends R>> next = new ActionEnablement<>(filter);
				operation.accept(next);
				newFilter = next;
			} else
				newFilter = filter;
			if (theMultiEnablement == null)
				theMultiEnablement = newFilter;
			else {
				Function<? super List<? extends R>, String> oldFilter = theMultiEnablement;
				theMultiEnablement = value -> {
					String msg = oldFilter.apply(value);
					if (msg != null)
						return msg;
					return newFilter.apply(value);
				};
			}
			return (A) this;
		}

		@Override
		public A withTooltip(Function<? super List<? extends R>, String> tooltip) {
			theTooltip = tooltip;
			if (tooltip != null)
				theTooltipString = new SimpleSettableValue<>(String.class, true);
			return (A) this;
		}

		@Override
		public A modifyAction(Function<? super ObservableAction<?>, ? extends ObservableAction<?>> actionMod) {
			theObservableAction = actionMod.apply(theObservableAction);
			return (A) this;
		}

		@Override
		public A confirm(String alertTitle, Function<List<? extends R>, String> alertText, boolean confirmType) {
			return modifyAction(action -> {
				return new ObservableAction<Void>() {
					@Override
					public TypeToken<Void> getType() {
						return TypeTokens.get().VOID;
					}

					@Override
					public Void act(Object cause) throws IllegalStateException {
						List<? extends R> selected = theWidget.getSelection();
						String text = alertText.apply(selected);
						if (!theWidget.alert(alertTitle, text).confirm(confirmType))
							return null;
						action.act(cause);
						return null;
					}

					@Override
					public ObservableValue<String> isEnabled() {
						return action.isEnabled();
					}
				};
			});
		}

		@Override
		public A confirmForItems(String alertTitle, String alertPreText, String alertPostText, boolean confirmType) {
			return modifyAction(action -> {
				return new ObservableAction<Void>() {
					@Override
					public TypeToken<Void> getType() {
						return TypeTokens.get().VOID;
					}

					@Override
					public Void act(Object cause) throws IllegalStateException {
						List<? extends R> selected = theWidget.getSelection();
						StringBuilder text = new StringBuilder();
						if (alertPreText != null && !alertPreText.isEmpty())
							text.append(alertPreText);
						if (selected.isEmpty())
							text.append("no ").append(StringUtils.pluralize(theWidget.getItemName()));
						else if (selected.size() == 1) {
							if (theWidget instanceof SimpleTableBuilder && ((SimpleTableBuilder<R, ?>) theWidget).getNameFunction() != null)
								text.append(theWidget.getItemName()).append(" \"")
								.append(((SimpleTableBuilder<R, ?>) theWidget).getNameFunction().apply(selected.get(0))).append('"');
							else
								text.append("1 ").append(theWidget.getItemName());
						} else
							text.append(selected.size()).append(' ').append(StringUtils.pluralize(theWidget.getItemName()));
						if (alertPostText != null && !alertPostText.isEmpty())
							text.append(alertPostText);
						if (!theWidget.alert(alertTitle, text.toString()).confirm(confirmType))
							return null;
						action.act(cause);
						return null;
					}

					@Override
					public ObservableValue<String> isEnabled() {
						return action.isEnabled();
					}
				};
			});
		}

		@Override
		public A modifyButton(Consumer<ButtonEditor<?, ?>> buttonMod) {
			if (theButtonMod == null)
				theButtonMod = buttonMod;
			else {
				Consumer<ButtonEditor<?, ?>> oldButtonMod = theButtonMod;
				theButtonMod = field -> {
					oldButtonMod.accept(field);
					buttonMod.accept(field);
				};
			}
			return (A) this;
		}

		@Override
		public List<R> getActionItems() {
			return theSelectedValues.get();
		}

		void updateSelection(List<R> selectedValues, Object cause) {
			if (!zeroAllowed && selectedValues.isEmpty())
				theEnabledString.set("Nothing selected", cause);
			else if (!multipleAllowed && selectedValues.size() > 1)
				theEnabledString.set("Multiple " + StringUtils.pluralize(theWidget.getItemName()) + " selected", cause);
			else {
				StringBuilder message = null;
				if (theMultiEnablement != null) {
					String msg = theMultiEnablement.apply(selectedValues);
					if (msg != null)
						message = new StringBuilder(msg);
				}
				if (message == null && theEnablement != null) {
					Set<String> messages = null;
					int allowedCount = 0;
					for (R value : selectedValues) {
						String msg = theEnablement.apply(value);
						if (msg == null)
							allowedCount++;
						else {
							if (messages == null)
								messages = new LinkedHashSet<>();
							messages.add(msg);
						}
					}
					boolean error = false;
					if (!actWhenAnyEnabled && messages != null) {
						error = true;
					} else if (allowedCount == 0 && !zeroAllowed) {
						error = true;
						if (messages == null) {
							messages = new LinkedHashSet<>();
							messages.add("Nothing selected");
						}
					} else if (allowedCount > 1 && !multipleAllowed) {
						error = true;
						if (messages == null) {
							messages = new LinkedHashSet<>();
							messages.add("Multiple " + StringUtils.pluralize(theWidget.getItemName()) + " selected");
						}
					}
					if (error) {
						message = new StringBuilder("<html>");
						boolean first = true;
						if (messages != null) {
							for (String msg : messages) {
								if (!first)
									message.append("<br>");
								else
									first = false;
								message.append(msg);
							}
						}
					}
				}
				theEnabledString.set(message == null ? null : message.toString(), cause);
			}

			if (theTooltipString != null && theEnabledString.get() == null) { // No point generating the tooltip if the disabled string will
				// show
				theTooltipString.set(theTooltip.apply(selectedValues), cause);
			}
		}

		void addButton(PanelPopulator<?, ?> panel) {
			panel.addButton((String) null, theObservableAction, button -> {
				if (theTooltipString != null)
					button.withTooltip(theTooltipString);
				if (theButtonMod != null)
					theButtonMod.accept(button);
			});
		}
	}

	static class SimpleTreeEditor<F, P extends SimpleTreeEditor<F, P>> extends AbstractComponentEditor<JTree, P>
	implements TreeEditor<F, P> {
		private final ObservableValue<? extends F> theRoot;
		private JScrollPane theComponent;

		private String theItemName;

		private ObservableCellRenderer<BetterList<F>, F> theRenderer;
		private Function<? super ModelCell<BetterList<F>, F>, String> theValueTooltip;
		private SettableValue<BetterList<F>> theSingleSelection;
		private boolean isSingleSelection;
		private ObservableCollection<BetterList<F>> theMultiSelection;
		private List<SimpleDataAction<BetterList<F>, ?>> theActions;

		private JPanel thePanel;

		public SimpleTreeEditor(Supplier<Transactable> lock, ObservableValue<? extends F> root,
			Function<? super F, ? extends ObservableCollection<? extends F>> children) {
			super(new JTree(new PPTreeModel<F>(root, children)), lock);
			theRoot = root;
			theActions = new ArrayList<>();
		}

		static class PPTreeModel<F> extends ObservableTreeModel<F> {
			private final Function<? super F, ? extends ObservableCollection<? extends F>> theChildren;
			private Predicate<? super F> theLeafTest;

			PPTreeModel(ObservableValue<? extends F> root, Function<? super F, ? extends ObservableCollection<? extends F>> children) {
				super(root);
				theChildren = children;
			}

			@Override
			protected ObservableCollection<? extends F> getChildren(F parent) {
				return theChildren.apply(parent);
			}

			@Override
			public void valueForPathChanged(TreePath path, Object newValue) {}

			@Override
			public boolean isLeaf(Object node) {
				Predicate<? super F> leafTest = theLeafTest;
				return leafTest != null && leafTest.test((F) node);
			}

			public void setLeafTest(Predicate<? super F> leafTest) {
				theLeafTest = leafTest;
			}
		}

		@Override
		public List<BetterList<F>> getSelection() {
			TreePath[] selection = getEditor().getSelectionPaths();
			return BetterList.of(Arrays.stream(selection)//
				.map(path -> (BetterList<F>) BetterList.of(path.getPath())));
		}

		@Override
		public P withRemove(Consumer<? super List<? extends BetterList<F>>> deletion, Consumer<DataAction<BetterList<F>, ?>> actionMod) {
			throw new UnsupportedOperationException("Not yet implemented");
		}

		@Override
		public P withMultiAction(Consumer<? super List<? extends BetterList<F>>> action, Consumer<DataAction<BetterList<F>, ?>> actionMod) {
			SimpleDataAction<BetterList<F>, ?> ta = new SimpleDataAction<>(this, action, this::getSelection);
			actionMod.accept(ta);
			theActions.add(ta);
			return (P) this;
		}

		@Override
		public P withItemName(String itemName) {
			theItemName = itemName;
			return (P) this;
		}

		@Override
		public String getItemName() {
			return theItemName;
		}

		@Override
		public ObservableValue<? extends F> getRoot() {
			return theRoot;
		}

		@Override
		public P renderWith(ObservableCellRenderer<BetterList<F>, F> renderer) {
			theRenderer = renderer;
			return (P) this;
		}

		@Override
		public P withCellTooltip(Function<? super ModelCell<BetterList<F>, F>, String> tooltip) {
			theValueTooltip = tooltip;
			return (P) this;
		}

		@Override
		public P withSelection(SettableValue<BetterList<F>> selection, boolean enforceSingleSelection) {
			theSingleSelection = selection;
			isSingleSelection = enforceSingleSelection;
			return (P) this;
		}

		@Override
		public P withSelection(ObservableCollection<BetterList<F>> selection) {
			theMultiSelection = selection;
			return (P) this;
		}

		@Override
		public P withLeafTest(Predicate<? super F> leafTest) {
			((PPTreeModel<F>) getEditor().getModel()).setLeafTest(leafTest);
			return (P) this;
		}

		@Override
		public boolean isVisible(List<? extends F> path) {
			return getEditor().isVisible(new TreePath(path.toArray()));
		}

		@Override
		public boolean isExpanded(List<? extends F> path) {
			return getEditor().isExpanded(new TreePath(path.toArray()));
		}

		@Override
		protected Component getOrCreateComponent(Observable<?> until) {
			if (theComponent != null)
				return theComponent;
			if (theRenderer != null)
				getEditor().setCellRenderer(new ObservableTreeCellRenderer<>(theRenderer));
			MouseMotionListener motion = new MouseAdapter() {
				@Override
				public void mouseEntered(MouseEvent e) {
					mouseMoved(e);
				}

				@Override
				public void mouseMoved(MouseEvent e) {
					TreePath path = getEditor().getPathForLocation(e.getX(), e.getY());
					if (path == null || theValueTooltip == null)
						getEditor().setToolTipText(null);
					else {
						BetterList<F> list = BetterList.of((List<F>) Arrays.asList(path.getPath()));
						int row = getEditor().getRowForLocation(e.getX(), e.getY());
						F value = (F) path.getLastPathComponent();
						ModelCell<BetterList<F>, F> cell = new ModelCell.Default<>(() -> list, value, row, 0,
							getEditor().getSelectionModel().isRowSelected(row), false, !getEditor().isCollapsed(row),
							getEditor().getModel().isLeaf(value));
						getEditor().setToolTipText(theValueTooltip.apply(cell));
					}
				}
			};
			getEditor().addMouseMotionListener(motion);
			until.take(1).act(__ -> getEditor().removeMouseMotionListener(motion));
			if (theMultiSelection != null)
				ObservableTreeModel.syncSelection(getEditor(), theMultiSelection, until);
			if (theSingleSelection != null) {
				ObservableTreeModel.syncSelection(getEditor(), theSingleSelection, false, Equivalence.DEFAULT, until);
				if (isSingleSelection)
					getEditor().getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
			}
			if (!theActions.isEmpty()) {
				JPopupMenu popup = new JPopupMenu();
				SimpleDataAction<BetterList<F>, ?>[] actions = theActions.toArray(new SimpleDataAction[theActions.size()]);
				JMenuItem[] actionMenuItems = new JMenuItem[actions.length];
				for (int a = 0; a < actions.length; a++) {
					actionMenuItems[a] = new JMenuItem();
					SimpleDataAction<BetterList<F>, ?> action = actions[a];
					if (action.theButtonMod != null) {
						SimpleButtonEditor<?, ?> buttonEditor = new SimpleButtonEditor<>(null, actionMenuItems[a], null,
							action.theObservableAction, getLock(), false);
						action.theButtonMod.accept(buttonEditor);
						buttonEditor.getOrCreateComponent(until);
					}
					actionMenuItems[a].addActionListener(evt -> action.theObservableAction.act(evt));
				}
				getEditor().getSelectionModel().addTreeSelectionListener(evt -> {
					List<BetterList<F>> selection = getSelection();
					for (SimpleDataAction<BetterList<F>, ?> action : actions)
						action.updateSelection(selection, evt);
				});
				getEditor().addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent evt) {
						if (!SwingUtilities.isRightMouseButton(evt))
							return;
						popup.removeAll();
						for (int a = 0; a < actions.length; a++) {
							if (actions[a].isEnabled() == null)
								popup.add(actionMenuItems[a]);
						}
						if (popup.getComponentCount() > 0)
							popup.show(getEditor(), evt.getX(), evt.getY());
					}
				});
			}
			theComponent = new JScrollPane(decorate(getEditor()));
			return theComponent;
		}

		@Override
		public Component getComponent() {
			return theComponent;
		}

		@Override
		public ObservableValue<String> getTooltip() {
			return null;
		}

		@Override
		protected Component createFieldNameLabel(Observable<?> until) {
			return null;
		}

		@Override
		protected Component createPostLabel(Observable<?> until) {
			return null;
		}

		static class ObservableTreeCellRenderer<F> implements TreeCellRenderer {
			private final ObservableCellRenderer<BetterList<F>, F> theRenderer;

			ObservableTreeCellRenderer(ObservableCellRenderer<BetterList<F>, F> renderer) {
				theRenderer = renderer;
			}

			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf,
				int row, boolean hasFocus) {
				Supplier<BetterList<F>> modelValue = () -> {
					TreePath path = tree.getPathForRow(row);
					BetterList<F> list = BetterList.of((List<F>) Arrays.asList(path.getPath()));
					return list;
				};
				ModelCell<BetterList<F>, F> cell = new ModelCell.Default<>(modelValue, (F) value, row, 0, selected, hasFocus, expanded,
					leaf);
				return theRenderer.getCellRendererComponent(tree, cell, null);
			}
		}
	}

	enum AlertType {
		PLAIN, INFO, WARNING, ERROR;
	}

	static class SimpleAlert implements Alert {
		private final Component theComponent;
		private String theTitle;
		private String theMessage;
		private AlertType theType;
		private SimpleImageControl theImage;

		SimpleAlert(Component component, String title, String message) {
			theComponent = component;
			theTitle = title;
			theMessage = message;
			theType = AlertType.PLAIN;
		}

		@Override
		public Alert info() {
			theType = AlertType.INFO;
			return this;
		}

		@Override
		public Alert warning() {
			theType = AlertType.WARNING;
			return this;
		}

		@Override
		public Alert error() {
			theType = AlertType.ERROR;
			return this;
		}

		@Override
		public Alert withIcon(String location, Consumer<ImageControl> image) {
			if (theImage == null)
				theImage = new SimpleImageControl(location);
			if (image != null)
				image.accept(theImage);
			return this;
		}

		@Override
		public void display() {
			Icon icon = theImage == null ? null : theImage.getIcon().get();
			if (icon != null)
				JOptionPane.showMessageDialog(theComponent, theMessage, theTitle, getJOptionType(), icon);
			else
				JOptionPane.showMessageDialog(theComponent, theMessage, theTitle, getJOptionType());
		}

		@Override
		public boolean confirm(boolean confirmType) {
			Icon icon = theImage == null ? null : theImage.getIcon().get();
			int ct = confirmType ? JOptionPane.OK_CANCEL_OPTION : JOptionPane.YES_NO_OPTION;
			int result;
			if (icon != null)
				result = JOptionPane.showConfirmDialog(theComponent, theMessage, theTitle, ct, getJOptionType(), icon);
			else
				result = JOptionPane.showConfirmDialog(theComponent, theMessage, theTitle, ct, getJOptionType());
			return (result == JOptionPane.OK_OPTION || result == JOptionPane.YES_OPTION);
		}

		@Override
		public <T> T input(TypeToken<T> type, Format<T> format, T initial, Consumer<ObservableTextField<T>> modify) {
			SimpleObservable<Void> until = SimpleObservable.build().build();
			SettableValue<T> value = SettableValue.build(type).withValue(initial).build();
			WindowBuilder<JDialog, ?> dialog = WindowPopulation.populateDialog(null, until, true)//
				.modal(true).withTitle(theTitle);
			if (theImage != null)
				dialog.withIcon(theImage.getIcon().map(img -> ((ImageIcon) img).getImage()));
			boolean[] provided = new boolean[1];
			dialog.withVContent(panel -> {
				if (theMessage != null)
					panel.addLabel(null, theMessage, null);
				ValueHolder<ObservableTextField<T>> field = new ValueHolder<>();
				panel.addTextField(null, value, format, f -> f.fill().modifyEditor(field));
				if (modify != null)
					modify.accept(field.get());
				panel.addHPanel(null, new JustifiedBoxLayout(false).mainCenter().crossJustified(), buttons -> {
					buttons.addButton("OK", __ -> {
						provided[0] = true;
						dialog.getWindow().setVisible(false);
					}, btn -> btn.disableWith(field.get().getErrorState()))//
					.addButton("Cancel", __ -> dialog.getWindow().setVisible(false), null);
				});
			});
			if (EventQueue.isDispatchThread())
				dialog.run(theComponent);
			else {
				try {
					EventQueue.invokeAndWait(() -> dialog.run(theComponent));
				} catch (InvocationTargetException e) {
					e.getTargetException().printStackTrace();
					return null;
				} catch (InterruptedException e) {
					e.printStackTrace();
					return null;
				}
			}
			until.onNext(null);
			if (provided[0])
				return value.get();
			else
				return null;
		}

		private int getJOptionType() {
			switch (theType) {
			case PLAIN:
				return JOptionPane.PLAIN_MESSAGE;
			case INFO:
				return JOptionPane.INFORMATION_MESSAGE;
			case WARNING:
				return JOptionPane.WARNING_MESSAGE;
			case ERROR:
				return JOptionPane.ERROR_MESSAGE;
			}
			return JOptionPane.ERROR_MESSAGE;
		}
	}
}

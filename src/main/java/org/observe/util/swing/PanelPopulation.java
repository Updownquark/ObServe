package org.observe.util.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.LayoutManager2;
import java.awt.datatransfer.DataFlavor;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionListener;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SettableValue.Builder;
import org.observe.SimpleObservable;
import org.observe.SimpleSettableValue;
import org.observe.Subscription;
import org.observe.TypedValueContainer;
import org.observe.collect.DefaultObservableCollection;
import org.observe.collect.ObservableCollection;
import org.observe.util.SafeObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.ObservableSwingUtils.FontAdjuster;
import org.observe.util.swing.TableContentControl.FilteredValue;
import org.observe.util.swing.TableContentControl.ValueRenderer;
import org.qommons.IntList;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.RRWLockingStrategy;
import org.qommons.io.Format;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

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

	public static <R> TableBuilder<R, ?> buildTable(ObservableCollection<R> rows) {
		return new SimpleTableBuilder<>(rows, new LazyLock());
	}

	public static class LazyLock implements Supplier<Transactable> {
		private final Supplier<Transactable> theLockCreator;
		private Transactable theLock;

		public LazyLock() {
			this(() -> Transactable.transactable(new ReentrantReadWriteLock()));
		}

		public LazyLock(Supplier<Transactable> lockCreator) {
			theLockCreator = lockCreator;
		}

		@Override
		public Transactable get() {
			if (theLock == null) {
				synchronized (this) {
					if (theLock == null)
						theLock = theLockCreator.get();
				}
			}
			return theLock;
		}
	}

	public interface PanelPopulator<C extends Container, P extends PanelPopulator<C, P>> extends ComponentEditor<C, P> {
		<F> P addTextField(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<FieldEditor<ObservableTextField<F>, ?>> modify);

		<F> P addLabel(String fieldName, ObservableValue<F> field, Format<F> format, Consumer<FieldEditor<JLabel, ?>> modify);

		P addCheckField(String fieldName, SettableValue<Boolean> field, Consumer<FieldEditor<JCheckBox, ?>> modify);

		/* TODO
		 * toggle/radio buttons
		 * slider
		 * progress bar
		 * spinner
		 * offloaded actions
		 * menu button
		 * (multi-) split pane (with configurable splitter panel)
		 * scroll pane
		 * accordion pane?
		 * value selector
		 * tree
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

		default P addButton(String buttonText, Consumer<Object> action, Consumer<ButtonEditor<?>> modify) {
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

		P addButton(String buttonText, ObservableAction<?> action, Consumer<ButtonEditor<?>> modify);

		<R> P addTable(ObservableCollection<R> rows, Consumer<TableBuilder<R, ?>> table);

		P addTabs(Consumer<TabPaneEditor<JTabbedPane, ?>> tabs);

		default P spacer(int size) {
			return addComponent(null, Box.createRigidArea(new Dimension(size, size)), null);
		}

		<S> P addComponent(String fieldName, S component, Consumer<FieldEditor<S, ?>> modify);

		P addVPanel(Consumer<PanelPopulator<JPanel, ?>> panel);

		default P addHPanel(String fieldName, String layoutType, Consumer<PanelPopulator<JPanel, ?>> panel) {
			return addHPanel(fieldName, makeLayout(layoutType), panel);
		}

		P addHPanel(String fieldName, LayoutManager layout, Consumer<PanelPopulator<JPanel, ?>> panel);

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

		P modifyEditor(Consumer<? super E> modify);

		Alert alert(String title, String message);

		ValueCache values();
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
			Function<DefaultObservableCollection.Builder<T, ?>, ? extends ObservableCollection<T>> builder) {
			return getOrDeclareCollection(name, TypeTokens.get().of(type), builder);
		}

		<T> ObservableCollection<T> getOrDeclareCollection(String name, TypeToken<T> type,
			Function<DefaultObservableCollection.Builder<T, ?>, ? extends ObservableCollection<T>> builder);

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

	public interface FieldEditor<E, P extends FieldEditor<E, P>> extends ComponentEditor<E, P> {
		default P withFieldName(String fieldName) {
			return withFieldName(fieldName == null ? null : ObservableValue.of(fieldName));
		}

		P withFieldName(ObservableValue<String> fieldName);

		default P withPostLabel(String postLabel) {
			return withPostLabel(postLabel == null ? null : ObservableValue.of(postLabel));
		}

		P withPostLabel(ObservableValue<String> postLabel);

		default P withTooltip(String tooltip) {
			return withTooltip(tooltip == null ? null : ObservableValue.of(tooltip));
		}

		P withTooltip(ObservableValue<String> tooltip);

		P modifyFieldLabel(Consumer<ObservableSwingUtils.FontAdjuster<?>> font);

		P withFont(Consumer<ObservableSwingUtils.FontAdjuster<?>> font);
	}

	public interface ButtonEditor<P extends ButtonEditor<P>> extends FieldEditor<JButton, P> {
		default P withText(String text) {
			return withText(text == null ? null : ObservableValue.of(text));
		}

		P withText(ObservableValue<String> text);

		default P withIcon(Class<?> resourceAnchor, String location, int width, int height) {
			return withIcon(ObservableSwingUtils.getFixedIcon(resourceAnchor, location, width, height));
		}

		default P withIcon(Icon icon) {
			return withIcon(icon == null ? null : ObservableValue.of(icon));
		}

		P withIcon(ObservableValue<? extends Icon> icon);
	}

	public interface ImageControl {
		ImageControl setLocation(String location);

		ImageControl variableLocation(ObservableValue<String> location);

		ImageControl variable(ObservableValue<? extends Icon> icon);

		ImageControl withSize(int width, int height);

		ImageControl withOpacity(double opacity);
	}

	public interface ComboEditor<F, P extends ComboEditor<F, P>> extends FieldEditor<JComboBox<F>, P> {
		P withValueTooltip(Function<? super F, String> tooltip);

		String getTooltip(F value);
	}

	public interface SteppedFieldEditor<E, F, P extends SteppedFieldEditor<E, F, P>> extends FieldEditor<E, P> {
		P withStepSize(F stepSize);
	}

	public interface TabPaneEditor<E, P extends TabPaneEditor<E, P>> extends ComponentEditor<E, P> {
		P withVTab(Object tabID, Consumer<PanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier);

		default P withHTab(Object tabID, String layoutType, Consumer<PanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier) {
			return withHTab(tabID, makeLayout(layoutType), panel, tabModifier);
		}

		P withHTab(Object tabID, LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier);

		P withTab(Object tabID, Component tabComponent, Consumer<TabEditor<?>> tabModifier);
	}

	public interface TabEditor<P extends TabEditor<P>> {
		default P setName(String name) {
			return setName(ObservableValue.of(name));
		}

		P setName(ObservableValue<String> name);

		ObservableValue<String> getName();
	}

	public interface TableBuilder<R, P extends TableBuilder<R, P>> extends ComponentEditor<JTable, P> {
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

		P withSelection(SettableValue<R> selection, boolean enforceSingleSelection);

		P withSelection(ObservableCollection<R> selection);

		List<R> getSelection();

		P withFiltering(ObservableValue<? extends TableContentControl> filter);

		P withAdd(Supplier<? extends R> creator, Consumer<TableAction<R, ?>> actionMod);

		P withRemove(Consumer<? super List<? extends R>> deletion, Consumer<TableAction<R, ?>> actionMod);

		P withCopy(Function<? super R, ? extends R> copier, Consumer<TableAction<R, ?>> actionMod);

		default P withAction(Consumer<? super R> action, Consumer<TableAction<R, ?>> actionMod) {
			return withMultiAction(values -> {
				for (R value : values)
					action.accept(value);
			}, actionMod);
		}

		P withMultiAction(Consumer<? super List<? extends R>> action, Consumer<TableAction<R, ?>> actionMod);

		Component getOrCreateComponent(Observable<?> until);
	}

	public interface TableAction<R, A extends TableAction<R, A>> {
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

		A allowWhen(Function<? super R, String> filter, Consumer<ActionEnablement<R>> operation);

		A withTooltip(Function<? super List<? extends R>, String> tooltip);

		A modifyAction(Function<? super ObservableAction<?>, ? extends ObservableAction<?>> actionMod);

		A modifyButton(Consumer<ButtonEditor<?>> buttonMod);
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

	public interface Alert {
		Alert info();

		Alert warning();

		Alert error();

		Alert withIcon(String location, Consumer<ImageControl> image);

		void display();
	}

	// Drag and cut/copy/paste

	public interface TransferSource<E> {
		<E2> TransferSource<E2> forType(Predicate<? super E> filter, Function<? super E, ? extends E2> map);

		default <E2 extends E> TransferSource<E2> forType(Class<E2> type) {
			return forType(value -> type.isInstance(value), value -> (E2) value);
		}

		TransferSource<E> draggable(boolean draggable);

		TransferSource<E> copyable(boolean copyable);

		TransferSource<E> movable(boolean movable);

		TransferSource<E> appearance(Consumer<TransferAppearance<E>> image);

		default TransferSource<E> toFlavor(DataFlavor flavor, DataTransform<? super E> transform) {
			return toFlavor(Arrays.asList(flavor), transform);
		}

		TransferSource<E> toFlavor(Collection<? extends DataFlavor> flavors, DataTransform<? super E> transform);

		TransferSource<E> toObject(); // TODO default this
		// TODO default this, supporting multiple text-based flavors

		TransferSource<E> toText(Function<? super E, ? extends CharSequence> toString);
	}

	public interface TransferAppearance<E> {
		TransferAppearance<E> withDragIcon(String imageLocation, Consumer<ImageControl> imgConfig);

		TransferAppearance<E> withDragOffset(int x, int y);

		TransferAppearance<E> withVisualRep(String imageLocation, Consumer<ImageControl> imgConfig);
	}

	public interface DataTransform<E> {
		Object transform(E value, DataFlavor flavor);
	}

	public interface TransferAccepter<E> {
		<E2> TransferAccepter<E2> forType(Predicate<? super E2> filter, Function<? super E2, ? extends E> map);

		TransferAccepter<E> draggable(boolean draggable);

		TransferAccepter<E> pastable(boolean pastable);

		default TransferAccepter<E> fromFlavor(DataFlavor flavor, Function<? super DataFlavor, ? extends E> data) {
			return fromFlavor(Arrays.asList(flavor), data);
		}

		TransferAccepter<E> fromFlavor(Collection<? extends DataFlavor> flavor, Function<? super DataFlavor, ? extends E> data);

		TransferAccepter<E> fromObject();
	}

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
				SettableValue.build(type).safe(false).withLock(theLock.get()).withDescription(name)), true, true);
		}

		@Override
		public <T> ObservableCollection<T> getOrDeclareCollection(String name, TypeToken<T> type,
			Function<DefaultObservableCollection.Builder<T, ?>, ? extends ObservableCollection<T>> builder) {
			Transactable lock = theLock.get();
			CollectionLockingStrategy locking = lock == null ? new FastFailLockingStrategy() : new RRWLockingStrategy(lock);
			return this.getOrDeclare(name, ObservableCollection.class, type, () -> builder.apply(//
				DefaultObservableCollection.build(type).withLocker(locking).withDescription(name)), true, true);
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
		Observable<?> getUntil();

		void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel);

		Supplier<Transactable> getLock();

		default void doAdd(SimpleFieldEditor<?, ?> field) {
			doAdd(field, field.createFieldNameLabel(getUntil()), field.createPostLabel(getUntil()));
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
		default <F> P addLabel(String fieldName, ObservableValue<F> field, Format<F> format, Consumer<FieldEditor<JLabel, ?>> modify) {
			JLabel label = new JLabel();
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
			Subscription sub = ObservableComboBoxModel.comboFor(fieldPanel.getEditor(), fieldPanel.getTooltip(), fieldPanel::getTooltip,
				observableValues, value);
			getUntil().take(1).act(__ -> sub.unsubscribe());
			if (modify != null)
				modify.accept(fieldPanel);
			doAdd(fieldPanel);
			return (P) this;
		}

		@Override
		default P addButton(String buttonText, ObservableAction<?> action, Consumer<ButtonEditor<?>> modify) {
			JButton button = new JButton();
			button.addActionListener(evt -> action.act(evt));
			SimpleButtonEditor<?> field = new SimpleButtonEditor<>(null, button, getLock()).withText(buttonText);
			action.isEnabled().combine((enabled, tt) -> enabled == null ? tt : enabled, field.getTooltip()).changes().takeUntil(getUntil())
			.act(evt -> button.setToolTipText(evt.getNewValue()));
			action.isEnabled().takeUntil(getUntil()).changes().act(evt -> button.setEnabled(evt.getNewValue() == null));
			if (modify != null)
				modify.accept(field);
			if (field.getText() != null)
				field.getText().changes().takeUntil(getUntil()).act(evt -> button.setText(evt.getNewValue()));
			if (field.getIcon() != null)
				field.getIcon().changes().takeUntil(getUntil()).act(evt -> button.setIcon(evt.getNewValue()));
			doAdd(field);
			return (P) this;
		}

		@Override
		default P addTabs(Consumer<TabPaneEditor<JTabbedPane, ?>> tabs) {
			SimpleTabPaneEditor<?> tabPane = new SimpleTabPaneEditor<>(getLock(), getUntil());
			tabs.accept(tabPane);
			doAdd(tabPane, null, null);
			return (P) this;
		}

		@Override
		default <R> P addTable(ObservableCollection<R> rows, Consumer<TableBuilder<R, ?>> table) {
			SimpleTableBuilder<R, ?> tb = new SimpleTableBuilder<>(rows, getLock());
			table.accept(tb);
			doAdd(tb, null, null);
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
			doAdd(subPanel, null, null);
			return (P) this;
		}
	}

	static abstract class PanelPopulatorImpl<C extends Container, P extends PanelPopulatorImpl<C, P>> implements PanelPopulator<C, P> {
		private final C theContainer;
		private final Observable<?> theUntil;
		private final ReentrantReadWriteLock theLock;
		private final CollectionLockingStrategy theCLS;

		PanelPopulatorImpl(C container, Observable<?> until, ReentrantReadWriteLock lock, CollectionLockingStrategy cls) {
			theContainer = container;
			theUntil = until == null ? Observable.empty() : until;
			theLock = lock;
			theCLS = new RRWLockingStrategy(lock);
		}

		protected Observable<?> getUntil() {
			return theUntil;
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
		private boolean isFillH;
		private boolean isFillV;

		private ObservableValue<Boolean> isVisible;

		AbstractComponentEditor(E editor, Supplier<Transactable> lock) {
			theEditor = editor;
			theLock = lock;
			theValueCache = new SimpleValueCache(lock);
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

		protected Component getOrCreateComponent(Observable<?> until) {
			// Subclasses should override this if the editor is not a component or is not the component that should be added
			if (!(theEditor instanceof Component))
				throw new IllegalStateException("Editor is not a component");
			return (Component) theEditor;
		}

		protected Component getComponent() {
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

	static class MigFieldPanel<C extends Container> extends AbstractComponentEditor<C, MigFieldPanel<C>>
	implements PartialPanelPopulatorImpl<C, MigFieldPanel<C>> {
		private final Observable<?> theUntil;

		MigFieldPanel(C container, Observable<?> until, Supplier<Transactable> lock) {
			super(container, lock);
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
		public void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel) {
			if (fieldLabel != null)
				getContainer().add(fieldLabel, "align right");
			StringBuilder constraints = new StringBuilder();
			if (field.isFill())
				constraints.append("growx, pushx");
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
			getContainer().add(component, constraints.toString());
			if (postLabel != null)
				getContainer().add(postLabel, "wrap");
			if (field.isVisible() != null) {
				field.isVisible().changes().takeUntil(getUntil()).act(evt -> {
					if (fieldLabel != null)
						fieldLabel.setVisible(evt.getNewValue());
					component.setVisible(evt.getNewValue());
					if (postLabel != null)
						postLabel.setVisible(evt.getNewValue());
				});
			}
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
		private Consumer<FontAdjuster<?>> theFont;

		SimpleFieldEditor(String fieldName, E editor, Supplier<Transactable> lock) {
			super(editor, lock);
			theFieldName = fieldName == null ? null : ObservableValue.of(fieldName);
			theSettableTooltip = new SimpleSettableValue<>(ObservableValue.TYPE_KEY.getCompoundType(String.class), true);
			theTooltip = ObservableValue.flatten(theSettableTooltip);
		}

		@Override
		public P withFieldName(ObservableValue<String> fieldName) {
			theFieldName = fieldName;
			return (P) this;
		}

		@Override
		public P withPostLabel(ObservableValue<String> postLabel) {
			thePostLabel = postLabel;
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

		@Override
		protected Component createFieldNameLabel(Observable<?> until) {
			if (theFieldName == null)
				return null;
			JLabel fieldNameLabel = new JLabel(theFieldName.get());
			theFieldName.changes().takeUntil(until).act(evt -> fieldNameLabel.setText(evt.getNewValue()));
			if (theFieldLabelModifier != null)
				theFieldLabelModifier.accept(new FontAdjuster<>(fieldNameLabel));
			if (theFont != null)
				theFont.accept(new FontAdjuster<>(fieldNameLabel));
			return fieldNameLabel;
		}

		@Override
		protected JLabel createPostLabel(Observable<?> until) {
			if (thePostLabel == null)
				return null;
			JLabel postLabel = new JLabel(thePostLabel.get());
			thePostLabel.changes().takeUntil(until).act(evt -> postLabel.setText(evt.getNewValue()));
			if (theFont != null)
				theFont.accept(new FontAdjuster<>(postLabel));
			return postLabel;
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
		public void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel) {
			if (fieldLabel != null)
				getContainer().add(fieldLabel);
			Component component = field.getOrCreateComponent(getUntil());
			StringBuilder constraints = new StringBuilder();
			if ((field.isFill() || field.isFillV()) && getContainer().getLayout().getClass().getName().startsWith("net.mig")) {
				if (field.isFill()) {
					if (constraints.length() > 0)
						constraints.append(", ");
					constraints.append("growx, pushx");
				}
				if (field.isFillV())
					constraints.append("growy, pushy");
			}
			getContainer().add(component, constraints.toString());
			if (postLabel != null)
				getContainer().add(postLabel);
			if (field.isVisible() != null) {
				field.isVisible().changes().takeUntil(getUntil()).act(evt -> {
					if (fieldLabel != null)
						fieldLabel.setVisible(evt.getNewValue());
					component.setVisible(evt.getNewValue());
					if (postLabel != null)
						postLabel.setVisible(evt.getNewValue());
				});
			}
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

	static class SimpleButtonEditor<P extends SimpleButtonEditor<P>> extends SimpleFieldEditor<JButton, P> implements ButtonEditor<P> {
		private ObservableValue<String> theText;
		private ObservableValue<? extends Icon> theIcon;

		SimpleButtonEditor(String fieldName, JButton editor, Supplier<Transactable> lock) {
			super(fieldName, editor, lock);
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
			return (P) this;
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
		public P withValueTooltip(Function<? super F, String> tooltip) {
			theValueTooltip = tooltip;
			return (P) this;
		}

		@Override
		public String getTooltip(F value) {
			return theValueTooltip == null ? null : theValueTooltip.apply(value);
		}
	}

	static class SimpleTabPaneEditor<P extends SimpleTabPaneEditor<P>> extends AbstractComponentEditor<JTabbedPane, P>
	implements TabPaneEditor<JTabbedPane, P> {
		private final Observable<?> theUntil;

		SimpleTabPaneEditor(Supplier<Transactable> lock, Observable<?> until) {
			super(new JTabbedPane(), lock);
			theUntil = until;
		}

		@Override
		public P withVTab(Object tabID, Consumer<PanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier) {
			MigFieldPanel<JPanel> fieldPanel = new MigFieldPanel<>(null, theUntil, theLock);
			panel.accept(fieldPanel);
			return withTab(tabID, fieldPanel.getContainer(), tabModifier);
		}

		@Override
		public P withHTab(Object tabID, LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier) {
			SimpleHPanel<JPanel> hPanel = new SimpleHPanel<>(null, new JPanel(layout), theLock, theUntil);
			panel.accept(hPanel);
			return withTab(tabID, hPanel.getContainer(), tabModifier);
		}

		@Override
		public P withTab(Object tabID, Component tabComponent, Consumer<TabEditor<?>> tabModifier) {
			SimpleTabEditor<?> t = new SimpleTabEditor<>(tabID, tabComponent);
			tabModifier.accept(t);
			getEditor().add(t.getComponent(theUntil));
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
	}

	static class SimpleTabEditor<P extends SimpleTabEditor<P>> implements TabEditor<P> {
		@SuppressWarnings("unused")
		private final Object theID;
		private final Component theComponent;
		private ObservableValue<String> theName;

		public SimpleTabEditor(Object id, Component component) {
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

		protected Component getComponent(Observable<?> until) {
			if (theName == null)
				throw new IllegalArgumentException("Failed to set name on tab for " + theComponent);
			theName.changes().takeUntil(until).act(evt -> theComponent.setName(evt.getNewValue()));
			return theComponent;
		}
	}

	static class SimpleTableBuilder<R, P extends SimpleTableBuilder<R, P>> extends AbstractComponentEditor<JTable, P>
	implements TableBuilder<R, P> {
		private final ObservableCollection<R> theRows;
		private ObservableCollection<? extends CategoryRenderStrategy<? super R, ?>> theColumns;
		private SettableValue<R> theSelectionValue;
		private ObservableCollection<R> theSelectionValues;
		private List<SimpleTableAction<R, ?>> theActions;
		private ObservableValue<? extends TableContentControl> theFilter;

		private Component theBuildComponent;

		SimpleTableBuilder(ObservableCollection<R> rows, Supplier<Transactable> lock) {
			super(new JTable(), lock);
			theRows = rows;
			theActions = new LinkedList<>();
		}

		@Override
		public P withColumns(ObservableCollection<? extends CategoryRenderStrategy<? super R, ?>> columns) {
			theColumns = columns;
			return (P) this;
		}

		@Override
		public P withColumn(CategoryRenderStrategy<? super R, ?> column) {
			if (theColumns == null)
				theColumns = ObservableCollection
				.create(new TypeToken<CategoryRenderStrategy<? super R, ?>>() {}.where(new TypeParameter<R>() {}, theRows.getType()));
			((ObservableCollection<CategoryRenderStrategy<? super R, ?>>) theColumns).add(column);
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
		public P withFiltering(ObservableValue<? extends TableContentControl> filter) {
			theFilter = filter;
			return (P) this;
		}

		@Override
		public List<R> getSelection() {
			return ObservableSwingUtils.getSelection(((ObservableTableModel<R>) getEditor().getModel()).getRowModel(),
				getEditor().getSelectionModel(), null);
		}

		private Icon getAddIcon() {
			return ObservableSwingUtils.getFixedIcon(null, "icons/add.png", 16, 16);
		}

		private Icon getRemoveIcon() {
			return ObservableSwingUtils.getFixedIcon(null, "icons/remove.png", 16, 16);
		}

		private Icon getCopyIcon() {
			return ObservableSwingUtils.getFixedIcon(null, "icons/copy.png", 16, 16);
		}

		@Override
		public P withAdd(Supplier<? extends R> creator, Consumer<TableAction<R, ?>> actionMod) {
			return withMultiAction(values -> {
				R value = creator.get();
				CollectionElement<R> el = theRows.addElement(value, false);
				// Assuming here that the action is only called on the EDT,
				// meaning the above add operation has now been propagated to the list model and the selection model
				// It also means that the row model is sync'd with the collection, so we can use the index from the collection here
				int index = theRows.getElementsBefore(el.getElementId());
				getEditor().getSelectionModel().setSelectionInterval(index, index);
			}, action -> {
				action.allowForMultiple(true).allowForEmpty(true).allowForAnyEnabled(true)//
				.modifyButton(button -> button.withIcon(getAddIcon()).withTooltip("Add new item"));
				if (actionMod != null)
					actionMod.accept(action);
			});
		}

		@Override
		public P withRemove(Consumer<? super List<? extends R>> deletion, Consumer<TableAction<R, ?>> actionMod) {
			return withMultiAction(deletion, action -> {
				action.allowForMultiple(true).withTooltip(items -> "Remove selected item" + (items.size() == 1 ? "" : "s"))//
				.modifyButton(button -> button.withIcon(getRemoveIcon()));
				if (actionMod != null)
					actionMod.accept(action);
			});
		}

		@Override
		public P withCopy(Function<? super R, ? extends R> copier, Consumer<TableAction<R, ?>> actionMod) {
			return withMultiAction(values -> {
				// Ignore the given values and use the selection model so we get the indexes right in the case of duplicates
				ListSelectionModel selModel = getEditor().getSelectionModel();
				IntList newSelection = new IntList();
				try (Transaction t = theRows.lock(true, null)) {
					// Not only do we need to obtain a write lock,but we also need to allow the EDT to purge any queued actions
					// to ensure that the model is caught up with the source collection
					EventQueue.invokeAndWait(() -> {
						for (int i = selModel.getMinSelectionIndex(); i >= 0 && i <= selModel.getMaxSelectionIndex(); i++) {
							if (!selModel.isSelectedIndex(i))
								continue;
							CollectionElement<R> toCopy = theRows.getElement(i);
							R copy = copier.apply(toCopy.get());
							CollectionElement<R> copied;
							if (theRows.canAdd(copy, toCopy.getElementId(), null) == null)
								copied = theRows.addElement(copy, toCopy.getElementId(), null, true);
							else
								copied = theRows.addElement(copy, false);
							newSelection.add(theRows.getElementsBefore(copied.getElementId()));
						}
					});
					selModel.setValueIsAdjusting(true);
					selModel.clearSelection();
					for (int[] interval : ObservableSwingUtils.getContinuousIntervals(newSelection.toArray(), true))
						selModel.addSelectionInterval(interval[0], interval[1]);
					selModel.setValueIsAdjusting(false);
				} catch (InvocationTargetException e) {
					if (e.getTargetException() instanceof RuntimeException)
						throw (RuntimeException) e.getTargetException();
					else
						throw (Error) e.getTargetException();
				} catch (InterruptedException e) {}
			}, action -> {
				action.allowForMultiple(true).withTooltip(items -> "Duplicate selected item" + (items.size() == 1 ? "" : "s"))//
				.modifyButton(button -> button.withIcon(getCopyIcon()));
				if (actionMod != null)
					actionMod.accept(action);
			});
		}

		@Override
		public P withMultiAction(Consumer<? super List<? extends R>> action, Consumer<TableAction<R, ?>> actionMod) {
			SimpleTableAction<R, ?> ta = new SimpleTableAction<>(action, this::getSelection);
			actionMod.accept(ta);
			theActions.add(ta);
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
		public Component getOrCreateComponent(Observable<?> until) {
			if (theBuildComponent != null)
				return theBuildComponent;
			ObservableTableModel<R> model;
			ObservableCollection<TableContentControl.FilteredValue<R>> filtered;
			if (theFilter != null) {
				ObservableCollection<R> safeRows = new SafeObservableCollection<>(theRows, EventQueue::isDispatchThread,
					EventQueue::invokeLater, until);
				ObservableCollection<CategoryRenderStrategy<? super R, ?>> safeColumns = new SafeObservableCollection<>(//
					(ObservableCollection<CategoryRenderStrategy<? super R, ?>>) TableContentControl.applyColumnControl(theColumns,
						theFilter, until),
					EventQueue::isDispatchThread, EventQueue::invokeLater, until);
				Observable<?> columnChanges = safeColumns.simpleChanges();
				List<ValueRenderer<R>> renderers = new ArrayList<>();
				columnChanges.act(__ -> {
					renderers.clear();
					for (CategoryRenderStrategy<? super R, ?> column : safeColumns) {
						renderers.add(new TableContentControl.ValueRenderer<R>() {
							@Override
							public String getName() {
								return column.getName();
							}

							@Override
							public boolean searchGeneral() {
								return column.isFilterable();
							}

							@Override
							public CharSequence render(R row) {
								return column.print(row);
							}

							@Override
							public int compare(R o1, R o2) {
								Object c1 = column.getCategoryValue(o1);
								Object c2 = column.getCategoryValue(o2);
								if (c1 instanceof String && c2 instanceof String)
									return StringUtils.compareNumberTolerant((String) c1, (String) c2, true, true);
								else if (c1 instanceof Comparable && c2 instanceof Comparable) {
									try {
										return ((Comparable<Object>) c1).compareTo(c2);
									} catch (ClassCastException e) {
										// Ignore
									}
								}
								return 0;
							}
						});
					}
				});
				filtered = TableContentControl.applyRowControl(safeRows, () -> renderers, //
					theFilter.refresh(safeColumns.simpleChanges()), until);
				model = new ObservableTableModel<>(filtered.flow().map(theRows.getType(), f -> f.value, opts -> opts.withElementSetting(//
					new ObservableCollection.ElementSetter<TableContentControl.FilteredValue<R>, R>() {
						@Override
						public String setElement(FilteredValue<R> element, R newValue, boolean replace) {
							element.setValue(newValue);
							return null;
						}
					})).collectActive(until), //
					true, safeColumns, true);
			} else {
				filtered = null;
				model = new ObservableTableModel<>(theRows, theColumns);
			}
			JTable table = getEditor();
			table.setModel(model);
			Subscription sub = ObservableTableModel.hookUp(table, model, //
				filtered == null ? null : new ObservableTableModel.TableRenderContext() {
				@Override
				public int[][] getEmphaticRegions(int row, int column) {
					TableContentControl.FilteredValue<R> fv = filtered.get(row);
					if (column >= fv.getColumns() || !fv.isFiltered())
						return null;
					return fv.getMatches(column);
				}
			});
			if (until != null)
				until.take(1).act(__ -> sub.unsubscribe());

			JScrollPane scroll = new JScrollPane(table);
			// Default scroll increments are ridiculously small
			scroll.getVerticalScrollBar().setUnitIncrement(10);
			scroll.getHorizontalScrollBar().setUnitIncrement(10);

			// Selection
			Supplier<List<R>> selectionGetter = () -> {
				ListSelectionModel selModel = table.getSelectionModel();
				List<R> selValues = new ArrayList<>(selModel.getMaxSelectionIndex() - selModel.getMinSelectionIndex() + 1);
				for (int i = 0; i < model.getRowModel().getSize(); i++) {
					if (selModel.isSelectedIndex(i))
						selValues.add(model.getRowModel().getElementAt(i));
				}
				return selValues;
			};
			if (theSelectionValue != null)
				ObservableSwingUtils.syncSelection(table, model.getRowModel(), table::getSelectionModel, model.getRows().equivalence(),
					theSelectionValue, until, false);
			if (theSelectionValues != null)
				ObservableSwingUtils.syncSelection(table, model.getRowModel(), table::getSelectionModel, model.getRows().equivalence(),
					theSelectionValues, until);
			if (!theActions.isEmpty()) {
				ListSelectionListener selListener = e -> {
					List<R> selection = selectionGetter.get();
					for (SimpleTableAction<R, ?> action : theActions)
						action.updateSelection(selection, e);
				};
				ListDataListener dataListener = new ListDataListener() {
					@Override
					public void intervalAdded(ListDataEvent e) {}

					@Override
					public void intervalRemoved(ListDataEvent e) {}

					@Override
					public void contentsChanged(ListDataEvent e) {
						ListSelectionModel selModel = table.getSelectionModel();
						if (selModel.getMinSelectionIndex() >= 0 && e.getIndex0() >= selModel.getMinSelectionIndex()
							&& e.getIndex1() <= selModel.getMaxSelectionIndex()) {
							List<R> selection = selectionGetter.get();
							for (SimpleTableAction<R, ?> action : theActions)
								action.updateSelection(selection, e);
						}
					}
				};
				List<R> selection = selectionGetter.get();
				for (SimpleTableAction<R, ?> action : theActions)
					action.updateSelection(selection, null);

				PropertyChangeListener selModelListener = evt -> {
					((ListSelectionModel) evt.getOldValue()).removeListSelectionListener(selListener);
					((ListSelectionModel) evt.getNewValue()).addListSelectionListener(selListener);
				};
				table.getSelectionModel().addListSelectionListener(selListener);
				table.addPropertyChangeListener("selectionModel", selModelListener);
				model.getRowModel().addListDataListener(dataListener);
				until.take(1).act(__ -> {
					table.removePropertyChangeListener("selectionModel", selModelListener);
					table.getSelectionModel().removeListSelectionListener(selListener);
					model.getRowModel().removeListDataListener(dataListener);
				});
				SimpleHPanel<JPanel> buttonPanel = new SimpleHPanel<>(null,
					new JPanel(new JustifiedBoxLayout(false).setMainAlignment(JustifiedBoxLayout.Alignment.LEADING)), theLock, until);
				for (SimpleTableAction<R, ?> action : theActions)
					action.addButton(buttonPanel);
				JPanel tablePanel = new JPanel(new BorderLayout());
				tablePanel.add(buttonPanel.getOrCreateComponent(until), BorderLayout.NORTH);
				tablePanel.add(scroll, BorderLayout.CENTER);
				theBuildComponent = tablePanel;
			} else
				theBuildComponent = scroll;
			return theBuildComponent;
		}

		@Override
		protected Component getComponent() {
			return theBuildComponent;
		}
	}

	static class SimpleTableAction<R, A extends SimpleTableAction<R, A>> implements TableAction<R, A> {
		final Consumer<? super List<? extends R>> theAction;
		final Supplier<List<R>> theSelectedValues;
		private Function<? super R, String> theEnablement;
		private Function<? super List<? extends R>, String> theTooltip;
		private boolean zeroAllowed;
		private boolean multipleAllowed;
		private boolean actWhenAnyEnabled;
		private ObservableAction<?> theObservableAction;
		private SettableValue<String> theEnabledString;
		private SettableValue<String> theTooltipString;
		private Consumer<ButtonEditor<?>> theButtonMod;

		SimpleTableAction(Consumer<? super List<? extends R>> action, Supplier<List<R>> selectedValues) {
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
		public A modifyButton(Consumer<ButtonEditor<?>> buttonMod) {
			if (theButtonMod == null)
				theButtonMod = buttonMod;
			else {
				Consumer<ButtonEditor<?>> oldButtonMod = theButtonMod;
				theButtonMod = field -> {
					oldButtonMod.accept(field);
					buttonMod.accept(field);
				};
			}
			return (A) this;
		}

		void updateSelection(List<R> selectedValues, Object cause) {
			if (!zeroAllowed && selectedValues.isEmpty())
				theEnabledString.set("Nothing selected", cause);
			else if (!multipleAllowed && selectedValues.size() > 1)
				theEnabledString.set("Multiple items selected", cause);
			else {
				if (theEnablement != null) {
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
					if (!actWhenAnyEnabled && !messages.isEmpty()) {
						error = true;
					} else if (allowedCount == 0 && !zeroAllowed) {
						error = true;
						if (messages.isEmpty())
							messages.add("Nothing selected");
					} else if (allowedCount > 1 && !multipleAllowed) {
						error = true;
						if (messages.isEmpty())
							messages.add("Multiple items selected");
					}
					if (!error)
						theEnabledString.set(null, cause);
					else {
						StringBuilder message = new StringBuilder("<html>");
						boolean first = true;
						for (String msg : messages) {
							if (!first)
								message.append("<br>");
							else
								first = false;
							message.append(msg);
						}
						theEnabledString.set(message.toString(), cause);
					}
				} else
					theEnabledString.set(null, cause);
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

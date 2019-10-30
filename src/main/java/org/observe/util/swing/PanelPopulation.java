package org.observe.util.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.LayoutManager;
import java.awt.LayoutManager2;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
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
import org.observe.SimpleSettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.util.SafeObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.ListFilter.FilteredValue;
import org.observe.util.swing.ObservableSwingUtils.FontAdjuster;
import org.qommons.IntList;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.RRWLockingStrategy;
import org.qommons.io.Format;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

public class PanelPopulation {
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
	public static <C extends Container> VPanelPopulator<C, ?> populateVPanel(C panel, Observable<?> until) {
		if (panel == null)
			panel = (C) new JPanel();
		return new MigFieldPanel<>(panel, until == null ? Observable.empty() : until);
	}

	public static <C extends Container> HPanelPopulator<C, ?> populateHPanel(C panel, String layoutType, Observable<?> until) {
		return populateHPanel(panel, layoutType == null ? null : makeLayout(layoutType), until);
	}

	public static <C extends Container> HPanelPopulator<C, ?> populateHPanel(C panel, LayoutManager layout, Observable<?> until) {
		if (panel == null)
			panel = (C) new JPanel(layout);
		else if (layout != null)
			panel.setLayout(layout);
		return new SimpleHPanel<>(null, panel, until == null ? Observable.empty() : until);
	}

	public static <R> TableBuilder<R, ?> buildTable(ObservableCollection<R> rows) {
		return new SimpleTableBuilder<>(rows);
	}

	public interface PanelPopulator<C extends Container, P extends PanelPopulator<C, P>> extends ComponentEditor<C, P> {
		<F> P addTextField(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<FieldEditor<ObservableTextField<F>, ?>> modify);

		<F> P addLabel(String fieldName, SettableValue<F> field, Format<F> format, Consumer<FieldEditor<JLabel, ?>> modify);

		P addCheckField(String fieldName, SettableValue<Boolean> field, Consumer<FieldEditor<JCheckBox, ?>> modify);

		/* TODO
		 * toggle/radio buttons
		 * slider
		 * split pane
		 * scroll pane
		 * accordion pane?
		 * value selector
		 * tree
		 * form controls (e.g. press enter in a text field and a submit action (also tied to a button) fires)
		 * styles: borders, background...
		 *
		 * Common locking (RRWL, CLS)
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

		P addButton(String buttonText, ObservableAction<?> action, Consumer<ButtonEditor<?>> modify);

		<R> P addTable(ObservableCollection<R> rows, Consumer<TableBuilder<R, ?>> table);

		P addTabs(Consumer<TabPaneEditor<JTabbedPane, ?>> tabs);

		default P spacer(int size) {
			return addComponent(null, Box.createRigidArea(new Dimension(size, size)), null);
		}

		<S> P addComponent(String fieldName, S component, Consumer<FieldEditor<S, ?>> modify);

		C getContainer();
	}

	public interface VPanelPopulator<C extends Container, P extends VPanelPopulator<C, P>> extends PanelPopulator<C, P> {
		default P addHPanel(String fieldName, String layoutType, Consumer<HPanelPopulator<JPanel, ?>> panel) {
			return addHPanel(fieldName, makeLayout(layoutType), panel);
		}

		P addHPanel(String fieldName, LayoutManager layout, Consumer<HPanelPopulator<JPanel, ?>> panel);
	}

	public interface HPanelPopulator<C extends Container, P extends HPanelPopulator<C, P>> extends PanelPopulator<C, P>, FieldEditor<C, P> {
		P addVPanel(Consumer<VPanelPopulator<JPanel, ?>> panel);
	}

	public interface ComponentEditor<E, P extends ComponentEditor<E, P>> {
		E getEditor();

		P visibleWhen(ObservableValue<Boolean> visible);

		P fill();

		P modifyEditor(Consumer<? super E> modify);
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

	public interface ComboEditor<F, P extends ComboEditor<F, P>> extends FieldEditor<JComboBox<F>, P> {
		P withValueTooltip(Function<? super F, String> tooltip);

		String getTooltip(F value);
	}

	public interface SteppedFieldEditor<E, F, P extends SteppedFieldEditor<E, F, P>> extends FieldEditor<E, P> {
		P withStepSize(F stepSize);
	}

	public interface TabPaneEditor<E, P extends TabPaneEditor<E, P>> extends ComponentEditor<E, P> {
		P withVTab(Object tabID, Consumer<VPanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier);

		default P withHTab(Object tabID, String layoutType, Consumer<HPanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier) {
			return withHTab(tabID, makeLayout(layoutType), panel, tabModifier);
		}

		P withHTab(Object tabID, LayoutManager layout, Consumer<HPanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier);

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

		P withFiltering(ObservableValue<? extends ListFilter> filter);

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

	interface PartialPanelPopulatorImpl<C extends Container, P extends PartialPanelPopulatorImpl<C, P>> extends PanelPopulator<C, P> {
		Observable<?> getUntil();

		void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel);

		default void doAdd(SimpleFieldEditor<?, ?> field) {
			doAdd(field, field.createFieldNameLabel(getUntil()), field.createPostLabel(getUntil()));
		}

		@Override
		default <F> P addTextField(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<FieldEditor<ObservableTextField<F>, ?>> modify) {
			SimpleFieldEditor<ObservableTextField<F>, ?> fieldPanel = new SimpleFieldEditor<>(fieldName,
				new ObservableTextField<>(field, format, getUntil()));
			fieldPanel.getTooltip().changes().takeUntil(getUntil()).act(evt -> fieldPanel.getEditor().setToolTipText(evt.getNewValue()));
			if (modify != null)
				modify.accept(fieldPanel);
			doAdd(fieldPanel);
			return (P) this;
		}

		@Override
		default <F> P addLabel(String fieldName, SettableValue<F> field, Format<F> format, Consumer<FieldEditor<JLabel, ?>> modify) {
			JLabel label = new JLabel();
			SimpleFieldEditor<JLabel, ?> fieldPanel = new SimpleFieldEditor<>(fieldName, label);
			fieldPanel.getTooltip().changes().takeUntil(getUntil()).act(evt -> fieldPanel.getEditor().setToolTipText(evt.getNewValue()));
			field.isEnabled().combine((enabled, tt) -> enabled == null ? tt : enabled, fieldPanel.getTooltip()).changes()
			.takeUntil(getUntil()).act(evt -> label.setToolTipText(evt.getNewValue()));
			if (modify != null)
				modify.accept(fieldPanel);
			doAdd(fieldPanel);
			return (P) this;
		}

		@Override
		default P addCheckField(String fieldName, SettableValue<Boolean> field, Consumer<FieldEditor<JCheckBox, ?>> modify) {
			SimpleFieldEditor<JCheckBox, ?> fieldPanel = new SimpleFieldEditor<>(fieldName, new JCheckBox());
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
			SimpleSteppedFieldEditor<JSpinner, F, ?> fieldPanel = new SimpleSteppedFieldEditor<>(fieldName, spinner, stepSize -> {
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
			SimpleComboEditor<F, ?> fieldPanel = new SimpleComboEditor<>(fieldName, new JComboBox<>());
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
			SimpleButtonEditor<?> field = new SimpleButtonEditor<>(null, button).withText(buttonText);
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
			SimpleTabPaneEditor<?> tabPane = new SimpleTabPaneEditor<>(getUntil());
			tabs.accept(tabPane);
			doAdd(tabPane, null, null);
			return (P) this;
		}

		@Override
		default <R> P addTable(ObservableCollection<R> rows, Consumer<TableBuilder<R, ?>> table) {
			SimpleTableBuilder<R, ?> tb = new SimpleTableBuilder<>(rows);
			table.accept(tb);
			doAdd(tb, null, null);
			return (P) this;
		}

		@Override
		default <S> P addComponent(String fieldName, S component, Consumer<FieldEditor<S, ?>> modify) {
			SimpleFieldEditor<S, ?> subPanel = new SimpleFieldEditor<>(fieldName, component);
			if (modify != null)
				modify.accept(subPanel);
			doAdd(subPanel);
			return (P) this;
		}

		default P addHPanel(String fieldName, LayoutManager layout, Consumer<HPanelPopulator<JPanel, ?>> panel) {
			SimpleHPanel<JPanel> subPanel = new SimpleHPanel<>(fieldName, new JPanel(layout), getUntil());
			if (panel != null)
				panel.accept(subPanel);
			doAdd(subPanel);
			return (P) this;
		}

		default P addVPanel(Consumer<VPanelPopulator<JPanel, ?>> panel) {
			MigFieldPanel<JPanel> subPanel = new MigFieldPanel<>(new JPanel(), getUntil());
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
		private final E theEditor;
		private boolean isGrow;
		private ObservableValue<Boolean> isVisible;

		AbstractComponentEditor(E editor) {
			theEditor = editor;
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
			isGrow = true;
			return (P) this;
		}

		@Override
		public P modifyEditor(Consumer<? super E> modify) {
			modify.accept(getEditor());
			return (P) this;
		}

		protected Component getComponent(Observable<?> until) {
			// Subclasses should override this if the editor is not a component or is not the component that should be added
			if (!(theEditor instanceof Component))
				throw new IllegalStateException("Editor is not a component");
			return (Component) theEditor;
		}

		protected boolean isGrow() {
			return isGrow;
		}

		protected ObservableValue<Boolean> isVisible() {
			return isVisible;
		}

		public abstract ObservableValue<String> getTooltip();

		protected abstract Component createFieldNameLabel(Observable<?> until);

		protected abstract Component createPostLabel(Observable<?> until);
	}

	static class MigFieldPanel<C extends Container> extends AbstractComponentEditor<C, MigFieldPanel<C>>
	implements PartialPanelPopulatorImpl<C, MigFieldPanel<C>>, VPanelPopulator<C, MigFieldPanel<C>> {
		private final Observable<?> theUntil;

		public MigFieldPanel(C container, Observable<?> until) {
			super(container);
			theUntil = until == null ? Observable.empty() : until;
			if (container.getLayout() == null || !MIG_LAYOUT_CLASS_NAME.equals(container.getLayout().getClass().getName())) {
				LayoutManager2 migLayout = createMigLayout(true, () -> "install the layout before using this class");
				container.setLayout(migLayout);
			}
		}

		@Override
		public MigFieldPanel<C> addHPanel(String fieldName, LayoutManager layout, Consumer<HPanelPopulator<JPanel, ?>> panel) {
			return PartialPanelPopulatorImpl.super.addHPanel(fieldName, layout, panel);
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
		public void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel) {
			if (fieldLabel != null)
				getContainer().add(fieldLabel, "align right");
			StringBuilder constraints = new StringBuilder();
			if (field.isGrow())
				constraints.append("growx, pushx");
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
			Component component = field.getComponent(getUntil());
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

		SimpleFieldEditor(String fieldName, E editor) {
			super(editor);
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
	implements PartialPanelPopulatorImpl<C, SimpleHPanel<C>>, HPanelPopulator<C, SimpleHPanel<C>> {
		private final Observable<?> theUntil;

		SimpleHPanel(String fieldName, C editor, Observable<?> until) {
			super(fieldName, editor);
			theUntil = until;
		}

		@Override
		public C getContainer() {
			return getEditor();
		}

		@Override
		public SimpleHPanel<C> addVPanel(Consumer<VPanelPopulator<JPanel, ?>> panel) {
			return PartialPanelPopulatorImpl.super.addVPanel(panel);
		}

		@Override
		public Observable<?> getUntil() {
			return theUntil;
		}

		@Override
		public void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel) {
			if (fieldLabel != null)
				getContainer().add(fieldLabel);
			Component component = field.getComponent(getUntil());
			String constraints = null;
			if (field.isGrow() && getContainer().getLayout().getClass().getName().startsWith("net.mig"))
				constraints = "growx, pushx";
			getContainer().add(component, constraints);
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

	static class SimpleButtonEditor<P extends SimpleButtonEditor<P>> extends SimpleFieldEditor<JButton, P> implements ButtonEditor<P> {
		private ObservableValue<String> theText;
		private ObservableValue<? extends Icon> theIcon;

		SimpleButtonEditor(String fieldName, JButton editor) {
			super(fieldName, editor);
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

		SimpleSteppedFieldEditor(String fieldName, E editor, Consumer<F> stepSizeChange) {
			super(fieldName, editor);
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

		SimpleComboEditor(String fieldName, JComboBox<F> editor) {
			super(fieldName, editor);
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

		SimpleTabPaneEditor(Observable<?> until) {
			super(new JTabbedPane());
			theUntil = until;
		}

		@Override
		public P withVTab(Object tabID, Consumer<VPanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier) {
			MigFieldPanel<JPanel> fieldPanel = new MigFieldPanel<>(null, theUntil);
			panel.accept(fieldPanel);
			return withTab(tabID, fieldPanel.getContainer(), tabModifier);
		}

		@Override
		public P withHTab(Object tabID, LayoutManager layout, Consumer<HPanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier) {
			SimpleHPanel<JPanel> hPanel = new SimpleHPanel<>(null, new JPanel(layout), theUntil);
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
		private ObservableValue<? extends ListFilter> theFilter;

		SimpleTableBuilder(ObservableCollection<R> rows) {
			super(new JTable());
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
		public P withFiltering(ObservableValue<? extends ListFilter> filter) {
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
		protected Component getComponent(Observable<?> until) {
			ObservableTableModel<R> model;
			ObservableCollection<ListFilter.FilteredValue<R>> filtered;
			if (theFilter != null) {
				ObservableCollection<R> safeRows = new SafeObservableCollection<>(theRows, EventQueue::isDispatchThread,
					EventQueue::invokeLater, until);
				ObservableCollection<CategoryRenderStrategy<? super R, ?>> safeColumns = new SafeObservableCollection<>(//
					(ObservableCollection<CategoryRenderStrategy<? super R, ?>>) theColumns, EventQueue::isDispatchThread,
					EventQueue::invokeLater, until);
				filtered = ListFilter.applyFilter(safeRows, //
					() -> QommonsUtils.filterMap(safeColumns, c -> c.isFilterable(), c -> row -> c.print(row)), //
					theFilter, until);
				model = new ObservableTableModel<>(filtered.flow().map(theRows.getType(), f -> f.value, opts -> opts.withElementSetting(//
					new ObservableCollection.ElementSetter<ListFilter.FilteredValue<R>, R>() {
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
					ListFilter.FilteredValue<R> fv = filtered.get(row);
					int c = 0;
					for (int i = 0; i < column; i++) {
						if (model.getColumn(i).isFilterable())
							c++;
					}
					if (c >= fv.getColumns() || fv.isTrivial())
						return null;
					return fv.getMatches(c);
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
					new JPanel(new JustifiedBoxLayout(false).setMainAlignment(JustifiedBoxLayout.Alignment.LEADING)), until);
				for (SimpleTableAction<R, ?> action : theActions)
					action.addButton(buttonPanel);
				JPanel tablePanel = new JPanel(new BorderLayout());
				tablePanel.add(buttonPanel.getComponent(until), BorderLayout.NORTH);
				tablePanel.add(scroll, BorderLayout.CENTER);
				return tablePanel;
			} else
				return scroll;
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

			if (theTooltipString != null && theEnabledString.get() == null) { // No point generating the tooltip if the disabled strign will
				// show
				theTooltipString.set(theTooltip.apply(selectedValues), cause);
			}
		}

		void addButton(HPanelPopulator<?, ?> panel) {
			panel.addButton((String) null, theObservableAction, button -> {
				if (theTooltipString != null)
					button.withTooltip(theTooltipString);
				if (theButtonMod != null)
					theButtonMod.accept(button);
			});
		}
	}
}

package org.observe.util.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.LayoutManager2;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
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
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleSettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.ObservableSwingUtils.ActionEnablement;
import org.observe.util.swing.ObservableSwingUtils.FieldPanelPopulator;
import org.observe.util.swing.ObservableSwingUtils.FontAdjuster;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.RRWLockingStrategy;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

public class PanelPopulation {
	public interface PanelPopulator<C extends Container, P extends PanelPopulator<C, P>> {
		<F> P addTextField(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<FieldEditor<ObservableTextField<F>, ?>> modify);

		<F> P addLabel(String fieldName, SettableValue<F> field, Format<F> format, Consumer<FieldEditor<JLabel, ?>> modify);

		FieldPanelPopulator<C> addCheckField(String fieldName, SettableValue<Boolean> field, Consumer<FieldEditor<JCheckBox, ?>> modify);

		/* TODO
		 * toggle/radio buttons
		 * slider
		 * split pane
		 * scroll pane
		 * accordion pane?
		 * value selector
		 * tree
		 * better button controls (variable text, easier icon selection, etc.)
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

		default P addButton(String text, ObservableAction<?> action, Consumer<ButtonEditor<?>> modify) {
			return addButton(ObservableValue.of(text), action, modify);
		}

		P addButton(ObservableValue<String> text, ObservableAction<?> action, Consumer<ButtonEditor<?>> modify);

		<R> P addTable(ObservableCollection<R> rows, Consumer<TableBuilder<R, ?>> table);

		P addTabs(Consumer<TabPaneEditor<JTabbedPane, ?>> tabs);

		default P spacer(int size) {
			return addComponent(null, Box.createRigidArea(new Dimension(size, size)), null);
		}

		<S> P addComponent(String fieldName, S component, Consumer<ComponentEditor<S, ?>> modify);

		C getContainer();
	}

	public interface VPanelPopulator<C extends Container, P extends PanelPopulator<C, P>> {
		default P addHPanel(String fieldName, String layoutType, Consumer<HPanelPopulator<?, ?>> panel) {
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
			return addHPanel(fieldName, layout, panel);
		}

		P addHPanel(String fieldName, LayoutManager layout, Consumer<HPanelPopulator<?, ?>> panel);
	}

	public interface HPanelPopulator<C extends Container, P extends PanelPopulator<C, P>> {
		P addVPanel(Consumer<VPanelPopulator<?, ?>> panel);
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

		P modifyFieldLabel(Consumer<ObservableSwingUtils.FontAdjuster<?>> font);

		P withFont(Consumer<ObservableSwingUtils.FontAdjuster<?>> font);
	}

	public interface ButtonEditor<P extends ButtonEditor<P>> extends FieldEditor<JButton, P> {
		default P withText(String text) {
			return withText(text == null ? null : ObservableValue.of(text));
		}

		P withText(ObservableValue<String> text);

		P withIcon(Icon icon);

		default P withIcon(Class<?> resourceAnchor, String location, int width, int height) {
			return withIcon(ObservableSwingUtils.getFixedIcon(resourceAnchor, location, width, height));
		}
	}

	public interface ComboEditor<F, P extends ComboEditor<F, P>> extends FieldEditor<JComboBox<F>, P> {
		P withValueTooltip(Function<? super F, String> tooltip);

		String getTooltip(F value);
	}

	public interface SteppedFieldEditor<E, F, P extends SteppedFieldEditor<E, F, P>> extends FieldEditor<E, P> {
		P withStepSize(F stepSize);
	}

	public interface TabPaneEditor<E, P extends TabPaneEditor<E, P>> extends ComponentEditor<E, P> {
		P withVTab(Object tabID, Consumer<VPanelPopulator<?, ?>> panel);

		P withHTab(Object tabID, Consumer<HPanelPopulator<?, ?>> panel);

		P withTab(Object tabID, Component tabComponent, Consumer<TabEditor<E, ?>> tabModifier);
	}

	public interface TabEditor<E, P extends TabEditor<E, P>> extends ComponentEditor<E, P> {
		default P setName(String name) {
			return setName(ObservableValue.of(name));
		}

		P setName(ObservableValue<String> name);

		ObservableValue<String> getName();
	}

	public interface TableBuilder<R, P extends TableBuilder<R, P>> extends ComponentEditor<JTable, P> {
		TableBuilder<R, P> withColumn(CategoryRenderStrategy<? super R, ?> column);

		default <C> TableBuilder<R, P> withColumn(String name, TypeToken<C> type, Function<? super R, ? extends C> accessor, //
			Consumer<CategoryRenderStrategy<R, C>> column) {
			CategoryRenderStrategy<R, C> col = new CategoryRenderStrategy<>(name, type, accessor);
			if (column != null)
				column.accept(col);
			return withColumn(col);
		}

		default <C> TableBuilder<R, P> withColumn(String name, Class<C> type, Function<? super R, ? extends C> accessor, //
			Consumer<CategoryRenderStrategy<R, C>> column) {
			return withColumn(name, TypeTokens.get().of(type), accessor, column);
		}

		TableBuilder<R, P> withSelection(SettableValue<R> selection, boolean enforceSingleSelection);

		TableBuilder<R, P> withSelection(ObservableCollection<R> selection);

		List<R> getSelection();

		TableBuilder<R, P> withAdd(Supplier<? extends R> creator, Consumer<TableAction<R, ?>> actionMod);

		TableBuilder<R, P> withRemove(Consumer<? super List<? extends R>> deletion, Consumer<TableAction<R, ?>> actionMod);

		TableBuilder<R, P> withCopy(Function<? super R, ? extends R> copier, Consumer<TableAction<R, ?>> actionMod);

		default TableBuilder<R, P> withAction(Consumer<? super R> action, Consumer<TableAction<R, ?>> actionMod) {
			return withMultiAction(values -> {
				for (R value : values)
					action.accept(value);
			}, actionMod);
		}

		TableBuilder<R, P> withMultiAction(Consumer<? super List<? extends R>> action, Consumer<TableAction<R, ?>> actionMod);
	}

	public interface TableAction<R, A extends TableAction<R, A>> {
		A allowForMultiple(boolean allowed);

		A allowForEmpty(boolean allowed);

		A allowForAnyEnabled(boolean allowed);

		A allowWhen(Function<? super R, String> filter, Consumer<ActionEnablement<R>> operation);

		A withTooltip(Function<? super List<? extends R>, String> tooltip);

		A modifyAction(Function<? super ObservableAction<?>, ? extends ObservableAction<?>> actionMod);

		A modifyButton(Consumer<ButtonEditor<?>> buttonMod);
	}

	static final String MIG_LAYOUT_CLASS_NAME = "net.miginfocom.swing.MigLayout";

	static LayoutManager2 createMigLayout(boolean withInsets, Supplier<String> err) {
		String layoutConstraints = "fillx, hidemode 3";
		if (!withInsets)
			layoutConstraints += ", insets 0";
		LayoutManager2 migLayout;
		try {
			migLayout = (LayoutManager2) Class.forName(MIG_LAYOUT_CLASS_NAME).getConstructor(String.class).newInstance(layoutConstraints);
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException | IllegalArgumentException
			| InvocationTargetException | NoSuchMethodException | SecurityException e) {
			throw new IllegalStateException(
				ObservableSwingUtils.class.getName() + " could not instantiate " + MIG_LAYOUT_CLASS_NAME + ": " + err.get(), e);
		}
		return migLayout;
	}

	private PanelPopulation() {}

	interface PartialComponentEditorImpl<E, P extends PartialComponentEditorImpl<E, P>> extends ComponentEditor<E, P> {
		Observable<?> getUntil();

		void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel);
	}

	abstract class PanelPopulatorImpl<C extends Container, P extends PanelPopulatorImpl<C, P>> implements PanelPopulator<C, P> {
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

	class MigFieldPanel<C extends Container> implements PanelPopulator<C, MigFieldPanel<C>> {}

	abstract class AbstractComponentEditor<E, P extends AbstractComponentEditor<E, P>> implements ComponentEditor<E, P> {
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

	class SimpleFieldEditor<E, P extends SimpleFieldEditor<E, P>> extends AbstractComponentEditor<E, P> implements FieldEditor<E, P> {
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
}

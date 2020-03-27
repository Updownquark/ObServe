package org.observe.util.swing;

import java.awt.event.MouseEvent;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

/**
 * Contains utilities to render and edit values in a table column (for a table whose model is an {@link ObservableTableModel})
 *
 * @param <R> The type of the table row values
 * @param <C> The type of the column value
 */
public class CategoryRenderStrategy<R, C> {
	public class CategoryMutationStrategy {
		private BiPredicate<? super R, ? super C> theEditability;
		private BiFunction<? super R, ? super C, ? extends C> theAttributeMutator;
		private BiFunction<? super R, ? super C, ? extends R> theRowMutator;
		private boolean updateRowIfUnchanged;

		private ObservableCellEditor<R, ? super C> theEditor;
		private BiFunction<? super R, ? super C, String> theEditorTooltip;

		private BiFunction<MutableCollectionElement<R>, ? super C, String> theValueFilter;

		CategoryMutationStrategy() {}

		public CategoryMutationStrategy editableIf(BiPredicate<? super R, ? super C> editable) {
			theEditability = editable;
			return this;
		}

		public CategoryMutationStrategy mutateAttribute(BiConsumer<? super R, ? super C> mutator) {
			return mutateAttribute2((row, col) -> {
				mutator.accept(row, col);
				return theAccessor.apply(row);
			});
		}

		public CategoryMutationStrategy mutateAttribute2(BiFunction<? super R, ? super C, ? extends C> mutator) {
			theAttributeMutator = mutator;
			return this;
		}

		public CategoryMutationStrategy withRowValueSwitch(BiFunction<? super R, ? super C, ? extends R> rowMutator) {
			theRowMutator = rowMutator;
			return this;
		}

		public CategoryMutationStrategy withRowUpdate(boolean updateIfUnchanged) {
			updateRowIfUnchanged = updateIfUnchanged;
			return this;
		}

		public CategoryMutationStrategy immutable() {
			theAttributeMutator = null;
			theRowMutator = null;
			return this;
		}

		public CategoryMutationStrategy filterAccept(BiFunction<MutableCollectionElement<R>, ? super C, String> filter) {
			theValueFilter = filter;
			return this;
		}

		public CategoryMutationStrategy withEditor(ObservableCellEditor<R, ? super C> editor) {
			theEditor = editor;
			return this;
		}

		public CategoryMutationStrategy asText(Format<C> format) {
			return withEditor(ObservableCellEditor.createTextEditor(format));
		}

		public CategoryMutationStrategy asText(Format<C> format, Consumer<ObservableTextField<C>> textField) {
			return withEditor(ObservableCellEditor.createTextEditor(format, textField));
		}

		public CategoryMutationStrategy asCombo(Function<? super C, String> renderer, ObservableCollection<? extends C> options) {
			return withEditor(ObservableCellEditor.createComboEditor(renderer, options));
		}

		public CategoryMutationStrategy asCheck() {
			if (!TypeTokens.get().isBoolean(getType()))
				throw new IllegalStateException("Can only use checkbox editing with a boolean-typed category, not " + getType());
			return withEditor((ObservableCellEditor<R, C>) ObservableCellEditor.createCheckBoxEditor());
		}

		public CategoryMutationStrategy asSlider(int minValue, int maxValue) {
			Class<?> raw = TypeTokens.getRawType(TypeTokens.get().wrap(getType()));
			if (raw == Integer.class)
				return withEditor((ObservableCellEditor<R, C>) ObservableCellEditor.createIntSliderEditor(minValue, maxValue));
			else if (raw == Double.class)
				return withEditor((ObservableCellEditor<R, C>) ObservableCellEditor.createDoubleSliderEditor(minValue, maxValue));
			else
				throw new IllegalStateException("Can only use slider editing with an int- or double-typed category, not " + getType());
		}

		public CategoryMutationStrategy asSlider(double minValue, double maxValue) {
			Class<?> raw = TypeTokens.getRawType(TypeTokens.get().wrap(getType()));
			if (raw == Integer.class)
				throw new IllegalStateException("Use asSlider(int, int)");
			else if (raw == Double.class)
				return withEditor((ObservableCellEditor<R, C>) ObservableCellEditor.createDoubleSliderEditor(minValue, maxValue));
			else
				throw new IllegalStateException("Can only use slider editing with an int- or double-typed category, not " + getType());
		}

		public CategoryMutationStrategy asButtonCell(Function<? super C, String> renderer,
			Function<? super ModelCell<R, ? extends C>, ? extends C> action) {
			return withEditor(ObservableCellEditor.createButtonCellEditor(renderer, action));
		}

		public CategoryMutationStrategy asButton(Function<? super C, String> renderer, Function<? super C, ? extends C> action) {
			return withEditor(ObservableCellEditor.createButtonEditor(renderer, action));
		}

		public CategoryMutationStrategy clicks(int clicks) {
			if (theEditor == null)
				throw new IllegalStateException("The editor has not been configured yet");
			theEditor.withClicks(clicks);
			return this;
		}

		public CategoryMutationStrategy withEditorTooltip(BiFunction<? super R, ? super C, String> tooltip) {
			theEditorTooltip = tooltip;
			return this;
		}

		public boolean isEditable(R row, C category) {
			if (theAttributeMutator == null && theRowMutator == null) {
				if (theEditor != null)
					System.err.println("Warning: Editor configured for column " + theName + ", but no mutation function");
				return false;
			}
			return theEditability == null || theEditability.test(row, category);
		}

		public String isAcceptable(MutableCollectionElement<R> row, C category) {
			return theValueFilter == null ? null : theValueFilter.apply(row, category);
		}

		public <R2 extends R> void mutate(MutableCollectionElement<R2> rowElement, C categoryValue) {
			R2 oldRow = rowElement.get();
			R2 newRow = oldRow;
			if (theRowMutator != null) {
				newRow = (R2) theRowMutator.apply(oldRow, categoryValue); // Just assume that the result will also be an instance of R2
			} else if (theAttributeMutator != null)
				theAttributeMutator.apply(oldRow, categoryValue);

			if (newRow != oldRow || updateRowIfUnchanged)
				rowElement.set(newRow);
		}

		public ObservableCellEditor<? super R, ? super C> getEditor() {
			return theEditor;
		}

		public BiFunction<? super R, ? super C, String> getEditorTooltip() {
			return theEditorTooltip;
		}

		public void setEditorTooltip(BiFunction<? super R, ? super C, String> editorTooltip) {
			theEditorTooltip = editorTooltip;
		}

		public BiPredicate<? super R, ? super C> getEditability() {
			return theEditability;
		}
	}

	/**
	 * Allows code execution on mouse events for table cells, even if they're not being edited
	 *
	 * @param <R> The row type of the table
	 * @param <C> The type of the category
	 */
	public interface CategoryMouseListener<R, C> {
		void mouseClicked(CollectionElement<? extends R> row, C category, MouseEvent e);

		void mousePressed(CollectionElement<? extends R> row, C category, MouseEvent e);

		void mouseReleased(CollectionElement<? extends R> row, C category, MouseEvent e);

		void mouseEntered(CollectionElement<? extends R> row, C category, MouseEvent e);

		void mouseExited(CollectionElement<? extends R> row, C category, MouseEvent e);
	}

	/**
	 * An abstract {@link CategoryRenderStrategy.CategoryMouseListener} that allows the implementation to implement only the methods it
	 * cares about
	 *
	 * @param <R> The row type of the table
	 * @param <C> The type of the category
	 */
	public static abstract class CategoryMouseAdapter<R, C> implements CategoryMouseListener<R, C> {
		@Override
		public void mouseClicked(CollectionElement<? extends R> row, C category, MouseEvent e) {}

		@Override
		public void mousePressed(CollectionElement<? extends R> row, C category, MouseEvent e) {}

		@Override
		public void mouseReleased(CollectionElement<? extends R> row, C category, MouseEvent e) {}

		@Override
		public void mouseEntered(CollectionElement<? extends R> row, C category, MouseEvent e) {}

		@Override
		public void mouseExited(CollectionElement<? extends R> row, C category, MouseEvent e) {}
	}

	/**
	 * This class is an optimization to tell the table that the category is not interested in mouse movement (enter or exit) events
	 *
	 * @param <R> The row type of the table
	 * @param <C> The type of the category
	 */
	public static abstract class CategoryClickAdapter<R, C> implements CategoryMouseListener<R, C> {
		@Override
		public final void mouseEntered(CollectionElement<? extends R> row, C category, MouseEvent e) {}

		@Override
		public final void mouseExited(CollectionElement<? extends R> row, C category, MouseEvent e) {}
	}

	public class AddRowRenderer extends CategoryRenderStrategy<R, C> {
		private final Supplier<? extends R> theEditSeedRow;

		public AddRowRenderer(Supplier<? extends R> editSeedRow, Function<? super R, ? extends C> accessor) {
			super("Add", theType, accessor);
			theEditSeedRow = editSeedRow;
		}

		@Override
		protected String printDefault(Supplier<? extends R> row, C column) {
			return "Add...";
		}

		public Supplier<? extends R> getEditSeedRow() {
			return theEditSeedRow;
		}

		public AddRowRenderer withText(Supplier<String> text) {
			formatText((r, c) -> text.get());
			return this;
		}
	}

	private String theName;
	private Object theIdentifier;
	private final TypeToken<C> theType;
	private final Function<? super R, ? extends C> theAccessor;
	private final CategoryMutationStrategy theMutator;
	private CategoryMouseListener<? super R, ? super C> theMouseListener;
	private String theHeaderTooltip;
	private BiFunction<? super R, ? super C, String> theTooltip;
	private ObservableCellRenderer<? super R, ? super C> theRenderer;
	private CellDecorator<R, C> theDecorator;
	private AddRowRenderer theAddRow;
	private int theMinWidth;
	private int thePrefWidth;
	private int theMaxWidth;
	private boolean isResizable;

	private boolean isFilterable;

	public CategoryRenderStrategy(String name, TypeToken<C> type, Function<? super R, ? extends C> accessor) {
		theName = name;
		theType = type;
		theAccessor = accessor;
		theMutator = new CategoryMutationStrategy();
		theRenderer = new ObservableCellRenderer.DefaultObservableCellRenderer<>(this::printDefault);
		theMinWidth = thePrefWidth = theMaxWidth = -1;
		isResizable = true;
		isFilterable = true;
	}

	protected String printDefault(Supplier<? extends R> row, C column) {
		return column == null ? "" : column.toString();
	}

	public String getName() {
		return theName;
	}

	public Object getIdentifier() {
		return theIdentifier;
	}

	public CategoryRenderStrategy<R, C> withIdentifier(Object identifier) {
		theIdentifier = identifier;
		return this;
	}

	public TypeToken<C> getType() {
		return theType;
	}

	public C getCategoryValue(R rowValue) {
		return theAccessor.apply(rowValue);
	}

	public String getTooltip(R row, C category) {
		return theTooltip == null ? null : theTooltip.apply(row, category);
	}

	public CategoryMutationStrategy getMutator() {
		return theMutator;
	}

	/**
	 * An easy way to configure mutation inline with chaining
	 *
	 * @param mutation The function to apply to this category's {@link #getMutator() mutator}
	 * @return This category
	 */
	public CategoryRenderStrategy<R, C> withMutation(Consumer<CategoryMutationStrategy> mutation) {
		mutation.accept(theMutator);
		return this;
	}

	public CategoryRenderStrategy<R, C> withMouseListener(CategoryMouseListener<? super R, ? super C> listener) {
		theMouseListener = listener;
		return this;
	}

	public CategoryRenderStrategy<R, C> withHeaderTooltip(String tooltip) {
		theHeaderTooltip = tooltip;
		return this;
	}

	public CategoryRenderStrategy<R, C> withValueTooltip(BiFunction<? super R, ? super C, String> tooltip) {
		theTooltip = tooltip;
		return this;
	}

	public CategoryRenderStrategy<R, C> setName(String name) {
		theName = name;
		return this;
	}

	public String getHeaderTooltip() {
		return theHeaderTooltip;
	}

	public CategoryMouseListener<? super R, ? super C> getMouseListener() {
		return theMouseListener;
	}

	public CategoryRenderStrategy<R, C> withRenderer(ObservableCellRenderer<? super R, ? super C> renderer) {
		theRenderer = renderer;
		return this;
	}

	public CategoryRenderStrategy<R, C> decorate(CellDecorator<R, C> decorator) {
		if (theDecorator == null)
			theDecorator = decorator;
		else
			theDecorator = theDecorator.modify(decorator);
		return this;
	}

	public CategoryRenderStrategy<R, C> decorateAll(Consumer<ComponentDecorator> decorator) {
		return decorate(CellDecorator.constant(decorator));
	}

	public CellDecorator<R, C> getDecorator() {
		return theDecorator;
	}

	public CategoryRenderStrategy<R, C> formatText(BiFunction<? super R, ? super C, String> format) {
		theRenderer = ObservableCellRenderer.formatted(format);
		return this;
	}

	public CategoryRenderStrategy<R, C> formatText(Function<? super C, String> format) {
		theRenderer = ObservableCellRenderer.formatted(format);
		return this;
	}

	public ObservableCellRenderer<? super R, ? super C> getRenderer() {
		return theRenderer;
	}

	public String print(R rowValue) {
		C colValue = getCategoryValue(rowValue);
		if (theRenderer != null)
			return theRenderer.renderAsText(() -> rowValue, colValue);
		else
			return printDefault(() -> rowValue, colValue);
	}

	public CategoryRenderStrategy<R, C> withWidth(String type, int width) {
		switch (type.toLowerCase()) {
		case "min":
		case "minimum":
			theMinWidth = width;
			break;
		case "pref":
		case "preferred":
			thePrefWidth = width;
			break;
		case "max":
		case "maximum":
			theMaxWidth = width;
			break;
		default:
			throw new IllegalArgumentException("Unrecognized width type: " + type + "; use min, max, or pref");
		}
		return this;
	}

	public CategoryRenderStrategy<R, C> withWidths(int min, int pref, int max) {
		theMinWidth = min;
		thePrefWidth = pref;
		theMaxWidth = max;
		return this;
	}

	public int getMinWidth() {
		return theMinWidth;
	}

	public int getPrefWidth() {
		return thePrefWidth;
	}

	public int getMaxWidth() {
		return theMaxWidth;
	}

	public boolean isResizable() {
		return isResizable;
	}

	public CategoryRenderStrategy<R, C> setResizable(boolean resizable) {
		isResizable = resizable;
		return this;
	}

	public CategoryRenderStrategy<R, C> filterable(boolean filterable) {
		isFilterable = filterable;
		return this;
	}

	public boolean isFilterable() {
		return isFilterable;
	}

	public CategoryRenderStrategy<R, C> withAddRow(Supplier<? extends R> rowSeed, Function<? super R, ? extends C> cat,
		Consumer<AddRowRenderer> addRow) {
		theAddRow = new AddRowRenderer(rowSeed, cat);
		addRow.accept(theAddRow);
		if (theAddRow.getMutator().getEditor() == null)
			throw new IllegalStateException("Add row configured with no editor");
		return this;
	}

	public AddRowRenderer getAddRow() {
		return theAddRow;
	}

	@Override
	public String toString() {
		return theName;
	}
}

package org.observe.util.swing;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.Observable;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.TableContentControl.ValueRenderer;
import org.qommons.LambdaUtils;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

/**
 * Contains utilities to render and edit values in a table column (for a table whose model is an {@link ObservableTableModel})
 *
 * @param <R> The type of the table row values
 * @param <C> The type of the column value
 */
public class CategoryRenderStrategy<R, C> implements ValueRenderer<R> {
	public class CategoryMutationStrategy {
		private BiPredicate<? super R, ? super C> theEditability;
		private BiFunction<? super R, ? super C, ? extends C> theAttributeMutator;
		private BiFunction<? super R, ? super C, ? extends R> theRowMutator;
		private boolean updateRowIfUnchanged;

		private ObservableCellEditor<R, C> theEditor;
		private Function<? super ModelCell<? extends R, ? extends C>, String> theEditorTooltip;

		private BiFunction<MutableCollectionElement<R>, ? super C, String> theValueFilter;
		private Dragging.SimpleTransferAccepter<R, C, C> theDragAccepter;

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

		public CategoryMutationStrategy withEditor(ObservableCellEditor<R, C> editor) {
			theEditor = editor;
			return this;
		}

		public CategoryMutationStrategy asText(Format<C> format) {
			withEditor(ObservableCellEditor.createTextEditor(format));
			if (isRenderDefault)
				formatText(LambdaUtils.printableFn(format::format, format::toString, format));
			return this;
		}

		public CategoryMutationStrategy asText(Format<C> format, Consumer<ObservableTextField<C>> textField) {
			withEditor(ObservableCellEditor.createTextEditor(format, textField));
			if (isRenderDefault)
				formatText(LambdaUtils.printableFn(format::format, format::toString, format));
			return this;
		}

		public CategoryMutationStrategy asCombo(Function<? super C, String> renderer, ObservableCollection<? extends C> options) {
			return asCombo(renderer, (__, ___) -> options);
		}

		public CategoryMutationStrategy asCombo(Function<? super C, String> renderer,
			BiFunction<? super ModelCell<? extends R, ? extends C>, Observable<?>, ObservableCollection<? extends C>> options) {
			withEditor(ObservableCellEditor.createComboEditor(renderer, options));
			if (isRenderDefault)
				formatText(renderer);
			return this;
		}

		public CategoryMutationStrategy asCheck() {
			if (!TypeTokens.get().isBoolean(getType()))
				throw new IllegalStateException("Can only use checkbox editing with a boolean-typed category, not " + getType());
			withRenderer(ObservableCellRenderer.checkRenderer(c -> Boolean.TRUE.equals(c.getCellValue())));
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
			if (isRenderDefault)
				formatText(renderer);
			return withEditor(ObservableCellEditor.createButtonCellEditor(renderer, action));
		}

		public CategoryMutationStrategy asButton(Function<? super C, String> renderer, Function<? super C, ? extends C> action) {
			if (isRenderDefault)
				formatText(renderer);
			return withEditor(ObservableCellEditor.createButtonEditor(renderer, action));
		}

		public CategoryMutationStrategy clicks(int clicks) {
			if (theEditor == null)
				throw new IllegalStateException("The editor has not been configured yet");
			theEditor.withClicks(clicks);
			return this;
		}

		public CategoryMutationStrategy withEditorTooltip(BiFunction<? super R, ? super C, String> tooltip) {
			return withEditorTooltip(cell -> tooltip.apply(cell.getModelValue(), cell.getCellValue()));
		}

		public CategoryMutationStrategy withEditorTooltip(Function<? super ModelCell<? extends R, ? extends C>, String> tooltip) {
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

			if (newRow != oldRow || updateRowIfUnchanged //
				&& rowElement.isAcceptable(newRow) == null) // Don't break if update is not supported
				rowElement.set(newRow);
		}

		public ObservableCellEditor<R, C> getEditor() {
			return theEditor;
		}

		public Function<? super ModelCell<? extends R, ? extends C>, String> getEditorTooltip() {
			return theEditorTooltip;
		}

		public BiPredicate<? super R, ? super C> getEditability() {
			return theEditability;
		}

		public CategoryMutationStrategy dragAccept(Consumer<Dragging.TransferAccepter<R, C, C>> accepter) {
			if (theDragAccepter == null)
				theDragAccepter = new Dragging.SimpleTransferAccepter<>(theType);
			accepter.accept(theDragAccepter);
			return this;
		}

		public Dragging.TransferAccepter<R, C, C> getDragAccepter() {
			return theDragAccepter;
		}
	}

	/**
	 * Allows code execution on mouse events for table cells, even if they're not being edited
	 *
	 * @param <R> The row type of the table
	 * @param <C> The type of the category
	 */
	public interface CategoryMouseListener<R, C> {
		boolean isMovementListener();

		void mouseClicked(ModelCell<? extends R, ? extends C> cell, MouseEvent e);

		void mousePressed(ModelCell<? extends R, ? extends C> cell, MouseEvent e);

		void mouseReleased(ModelCell<? extends R, ? extends C> cell, MouseEvent e);

		void mouseEntered(ModelCell<? extends R, ? extends C> cell, MouseEvent e);

		void mouseExited(ModelCell<? extends R, ? extends C> cell, MouseEvent e);

		void mouseMoved(ModelCell<? extends R, ? extends C> cell, MouseEvent e);
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
		public boolean isMovementListener() {
			return true;
		}

		@Override
		public void mouseClicked(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {}

		@Override
		public void mousePressed(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {}

		@Override
		public void mouseReleased(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {}

		@Override
		public void mouseEntered(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {}

		@Override
		public void mouseExited(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {}

		@Override
		public void mouseMoved(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
		}
	}

	public interface CategoryKeyListener<R, C> {
		void keyPressed(ModelCell<? extends R, ? extends C> cell, KeyEvent e);

		void keyReleased(ModelCell<? extends R, ? extends C> cell, KeyEvent e);

		void keyTyped(ModelCell<? extends R, ? extends C> cell, KeyEvent e);
	}

	public static abstract class CategoryKeyAdapter<R, C> implements CategoryKeyListener<R, C> {
		@Override
		public void keyPressed(ModelCell<? extends R, ? extends C> cell, KeyEvent e) {}

		@Override
		public void keyReleased(ModelCell<? extends R, ? extends C> cell, KeyEvent e) {}

		@Override
		public void keyTyped(ModelCell<? extends R, ? extends C> cell, KeyEvent e) {}
	}

	/**
	 * This class is an optimization to tell the table that the category is not interested in mouse movement (enter or exit) events
	 *
	 * @param <R> The row type of the table
	 * @param <C> The type of the category
	 */
	public static abstract class CategoryClickAdapter<R, C> implements CategoryMouseListener<R, C> {
		@Override
		public boolean isMovementListener() {
			return false;
		}

		@Override
		public void mouseClicked(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {}

		@Override
		public void mousePressed(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {}

		@Override
		public void mouseReleased(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {}

		@Override
		public final void mouseEntered(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {}

		@Override
		public final void mouseExited(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {}

		@Override
		public final void mouseMoved(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
		}
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
	private ListenerList<CategoryMouseListener<? super R, ? super C>> theMouseListeners;
	private CategoryKeyListener<? super R, ? super C> theKeyListener;
	private String theHeaderTooltip;
	private Function<? super ModelCell<? extends R, ? extends C>, String> theTooltip;
	private ObservableCellRenderer<R, C> theRenderer;
	private boolean isRenderDefault;
	private CellDecorator<R, C> theDecorator;
	private AddRowRenderer theAddRow;
	private int theMinWidth;
	private int thePrefWidth;
	private int theMaxWidth;
	private boolean isResizable;

	private Dragging.SimpleTransferSource<C> theDragSource;

	private boolean isFilterable;

	public CategoryRenderStrategy(String name, TypeToken<C> type, Function<? super R, ? extends C> accessor) {
		theName = name;
		theType = type;
		theAccessor = accessor;
		theMutator = new CategoryMutationStrategy();
		isRenderDefault = true;
		theRenderer = new ObservableCellRenderer.DefaultObservableCellRenderer<>(this::printDefault);
		theMinWidth = 10;
		thePrefWidth = 100;
		theMaxWidth = 10000;
		isResizable = true;
		isFilterable = true;
	}

	protected String printDefault(Supplier<? extends R> row, C column) {
		return column == null ? "" : column.toString();
	}

	@Override
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

	public Function<? super ModelCell<? extends R, ? extends C>, String> getTooltipFn() {
		return theTooltip;
	}

	public String getTooltip(ModelCell<R, C> cell) {
		return theTooltip == null ? null : theTooltip.apply(cell);
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
		addMouseListener(listener);
		return this;
	}

	public Runnable addMouseListener(CategoryMouseListener<? super R, ? super C> listener) {
		if (theMouseListeners == null)
			theMouseListeners = ListenerList.build().build();
		return theMouseListeners.add(listener, true);
	}

	public void removeMouseListener(CategoryMouseListener<? super R, ? super C> listener) {
	}

	public CategoryRenderStrategy<R, C> withKeyListener(CategoryKeyListener<? super R, ? super C> listener) {
		theKeyListener = listener;
		return this;
	}

	public CategoryRenderStrategy<R, C> withHeaderTooltip(String tooltip) {
		theHeaderTooltip = tooltip;
		return this;
	}

	public CategoryRenderStrategy<R, C> withValueTooltip(BiFunction<? super R, ? super C, String> tooltip) {
		return withCellTooltip(cell -> tooltip.apply(cell.getModelValue(), cell.getCellValue()));
	}

	public CategoryRenderStrategy<R, C> withCellTooltip(Function<? super ModelCell<? extends R, ? extends C>, String> tooltip) {
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
		if (theMouseListeners == null)
			return null;
		return new CategoryMouseListener<R, C>() {
			@Override
			public boolean isMovementListener() {
				boolean[] movement = new boolean[1];
				theMouseListeners.forEach(l -> {
					if (l.isMovementListener())
						movement[0] = true;
				});
				return movement[0];
			}

			@Override
			public void mousePressed(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
				theMouseListeners.forEach(//
					l -> l.mousePressed(cell, e));
			}

			@Override
			public void mouseReleased(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
				theMouseListeners.forEach(//
					l -> l.mouseReleased(cell, e));
			}

			@Override
			public void mouseClicked(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
				theMouseListeners.forEach(//
					l -> l.mouseClicked(cell, e));
			}

			@Override
			public void mouseEntered(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
				theMouseListeners.forEach(//
					l -> l.mouseEntered(cell, e));
			}

			@Override
			public void mouseExited(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
				theMouseListeners.forEach(//
					l -> l.mouseExited(cell, e));
			}

			@Override
			public void mouseMoved(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
				theMouseListeners.forEach(//
					l -> l.mouseMoved(cell, e));
			}
		};
	}

	public CategoryKeyListener<? super R, ? super C> getKeyListener() {
		return theKeyListener;
	}

	public CategoryRenderStrategy<R, C> withRenderer(ObservableCellRenderer<R, C> renderer) {
		isRenderDefault = false;
		theRenderer = renderer;
		if (theRenderer != null && theDecorator != null)
			theRenderer.decorate(theDecorator);
		return this;
	}

	public CategoryRenderStrategy<R, C> decorate(CellDecorator<R, C> decorator) {
		if (decorator == null)
			return this;
		if (theDecorator == null)
			theDecorator = decorator;
		else
			theDecorator = theDecorator.modify(decorator);
		if (theRenderer != null && theDecorator != null)
			theRenderer.decorate(theDecorator);
		return this;
	}

	public CategoryRenderStrategy<R, C> decorateAll(Consumer<ComponentDecorator> decorator) {
		return decorate(CellDecorator.constant(decorator));
	}

	public CellDecorator<R, C> getDecorator() {
		return theDecorator;
	}

	public CategoryRenderStrategy<R, C> formatText(BiFunction<? super R, ? super C, String> format) {
		return withRenderer(ObservableCellRenderer.formatted(format));
	}

	public CategoryRenderStrategy<R, C> formatText(Function<? super C, String> format) {
		return withRenderer(ObservableCellRenderer.formatted(format));
	}

	public ObservableCellRenderer<? super R, ? super C> getRenderer() {
		return theRenderer;
	}

	public String print(R rowValue) {
		C colValue = getCategoryValue(rowValue);
		return print(() -> rowValue, colValue);
	}

	public String print(Supplier<? extends R> rowValue, C colValue) {
		if (theRenderer != null)
			return theRenderer.renderAsText(rowValue, colValue);
		else
			return printDefault(rowValue, colValue);
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

	public CategoryRenderStrategy<R, C> dragSource(Consumer<Dragging.TransferSource<C>> source) {
		if (theDragSource == null)
			theDragSource = new Dragging.SimpleTransferSource<>(theType);
		source.accept(theDragSource);
		return this;
	}

	public Dragging.TransferSource<C> getDragSource() {
		return theDragSource;
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
	public boolean searchGeneral() {
		return isFilterable();
	}

	@Override
	public CharSequence render(R row) {
		return print(row);
	}

	private boolean compareComparable = true;

	@Override
	public int compare(R row1, R row2, boolean reverse) {
		C val1 = getCategoryValue(row1);
		C val2 = getCategoryValue(row2);
		// First try to find ways to compare the column values
		// The null and empty comparisons here are switched from typical, under the assumption that if the user is sorting by this column,
		// they are most likely interested in rows with values in this column
		// Also, note that these do not respect the reverse parameter,
		// again assuming that non-empty values are more relevant even for a reverse search
		if (val1 == null) {
			if (val2 == null)
				return 0;
			else
				return 1;
		} else if (val2 == null)
			return -1;
		else if (val1.equals(val2))
			return 0;
		else if (val1 instanceof CharSequence && val2 instanceof CharSequence) {
			return TableContentControl.compareColumnRenders((CharSequence) val1, (CharSequence) val2, reverse);
		} else if (compareComparable && val1 instanceof Comparable && val2 instanceof Comparable) {
			try {
				int comp = ((Comparable<Object>) val1).compareTo(val2);
				return reverse ? -comp : comp;
			} catch (ClassCastException e) {
				// If this fails once, don't try it again--mixed comparables
				compareComparable = false;
			}
		}
		// If that didn't work, then compare the formatted text
		String render1 = print(() -> row1, val1);
		String render2 = print(() -> row2, val2);
		return TableContentControl.compareColumnRenders(render1, render2, reverse);
	}

	@Override
	public String toString() {
		return theName;
	}
}

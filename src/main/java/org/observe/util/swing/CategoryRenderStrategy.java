package org.observe.util.swing;

import java.awt.event.MouseEvent;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

public class CategoryRenderStrategy<R, C> {
	public class CategoryMutationStrategy<R, C> {
		private BiPredicate<? super R, ? super C> theEditability;
		private BiFunction<? super R, ? super C, ? extends C> theAttributeMutator;
		private BiFunction<? super R, ? super C, ? extends R> theRowMutator;
		private boolean updateRowIfUnchanged;

		private ObservableCellEditor<? super R, ? super C> theEditor;
		private BiFunction<? super R, ? super C, String> theEditorTooltip;

		private BiFunction<MutableCollectionElement<? super R>, ? super C, String> theValueFilter;

		CategoryMutationStrategy() {}

		public CategoryMutationStrategy<R, C> editableIf(BiPredicate<? super R, ? super C> editable) {
			theEditability = editable;
			return this;
		}

		public CategoryMutationStrategy<R, C> mutateAttribute(BiFunction<? super R, ? super C, ? extends C> mutator) {
			theAttributeMutator = mutator;
			return this;
		}

		public CategoryMutationStrategy<R, C> withRowValueSwitch(BiFunction<? super R, ? super C, ? extends R> rowMutator) {
			theRowMutator = rowMutator;
			return this;
		}

		public CategoryMutationStrategy<R, C> withRowUpdate(boolean updateIfUnchanged) {
			updateRowIfUnchanged = updateIfUnchanged;
			return this;
		}

		public CategoryMutationStrategy<R, C> immutable() {
			theAttributeMutator = null;
			theRowMutator = null;
			return this;
		}

		public CategoryMutationStrategy<R, C> filterAccept(BiFunction<MutableCollectionElement<? super R>, ? super C, String> filter) {
			theValueFilter = filter;
			return this;
		}

		public CategoryMutationStrategy<R, C> withEditor(ObservableCellEditor<? super R, ? super C> editor) {
			theEditor = editor;
			return this;
		}

		public CategoryMutationStrategy<R, C> asText(Format<C> format) {
			return withEditor(ObservableCellEditor.createTextEditor(format));
		}

		public CategoryMutationStrategy<R, C> asCombo(Function<? super C, String> renderer, ObservableCollection<? extends C> options) {
			return withEditor(ObservableCellEditor.createComboEditor(renderer, options));
		}

		public CategoryMutationStrategy<R, C> asCheck() {
			if (!TypeTokens.get().isBoolean(getType()))
				throw new IllegalStateException("Can only use checkbox editing with a boolean-typed category, not " + getType());
			return withEditor((ObservableCellEditor<R, C>) ObservableCellEditor.createCheckBoxEditor());
		}

		public CategoryMutationStrategy<R, C> asSlider(int minValue, int maxValue) {
			Class<?> raw = TypeTokens.getRawType(TypeTokens.get().wrap(getType()));
			if (raw == Integer.class)
				return withEditor((ObservableCellEditor<R, C>) ObservableCellEditor.createIntSliderEditor(minValue, maxValue));
			else if (raw == Double.class)
				return withEditor((ObservableCellEditor<R, C>) ObservableCellEditor.createDoubleSliderEditor(minValue, maxValue));
			else
				throw new IllegalStateException("Can only use slider editing with an int- or double-typed category, not " + getType());
		}

		public CategoryMutationStrategy<R, C> asSlider(double minValue, double maxValue) {
			Class<?> raw = TypeTokens.getRawType(TypeTokens.get().wrap(getType()));
			if (raw == Integer.class)
				throw new IllegalStateException("Use asSlider(int, int)");
			else if (raw == Double.class)
				return withEditor((ObservableCellEditor<R, C>) ObservableCellEditor.createDoubleSliderEditor(minValue, maxValue));
			else
				throw new IllegalStateException("Can only use slider editing with an int- or double-typed category, not " + getType());
		}

		public CategoryMutationStrategy<R, C> asButton(Function<? super C, String> renderer, Function<? super C, ? extends C> action) {
			return withEditor(ObservableCellEditor.createButtonEditor(renderer, action));
		}

		public CategoryMutationStrategy<R, C> withEditorTooltip(BiFunction<? super R, ? super C, String> tooltip) {
			theEditorTooltip = tooltip;
			return this;
		}

		public boolean isEditable(R row, C category) {
			if (theAttributeMutator == null && theRowMutator == null)
				return false;
			return theEditability == null || theEditability.test(row, category);
		}

		public String isAcceptable(MutableCollectionElement<? super R> row, C category) {
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

	private String theName;
	private final TypeToken<C> theType;
	private final Function<? super R, ? extends C> theAccessor;
	private final CategoryMutationStrategy<R, C> theMutator;
	private CategoryMouseListener<? super R, ? super C> theMouseListener;
	private String theHeaderTooltip;
	private BiFunction<? super R, ? super C, String> theTooltip;

	public CategoryRenderStrategy(String name, TypeToken<C> type, Function<? super R, ? extends C> accessor) {
		theName = name;
		theType = type;
		theAccessor = accessor;
		theMutator = new CategoryMutationStrategy<>();
	}

	public String getName() {
		return theName;
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

	public CategoryMutationStrategy<R, C> getMutator() {
		return theMutator;
	}

	/**
	 * An easy way to configure mutation inline with chaining
	 *
	 * @param mutation The function to apply to this category's {@link #getMutator() mutator}
	 * @return This category
	 */
	public CategoryRenderStrategy<R, C> withMutation(Consumer<CategoryMutationStrategy<R, C>> mutation) {
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
}

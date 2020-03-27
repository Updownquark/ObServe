package org.observe.util.swing;

import java.awt.Component;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.EventObject;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;

import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;
import javax.swing.tree.TreeCellEditor;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.SimpleSettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

public interface ObservableCellEditor<M, C> extends TableCellEditor, TreeCellEditor {
	public interface EditorSubscription {
		boolean uninstall(boolean commit);
	}

	public interface EditorInstallation<C> {
		EditorSubscription install(ObservableCellEditor<?, C> editor, Component component, Function<C, String> valueFilter, String tooltip,
			Function<? super C, String> valueToolTip);
	}

	ModelCell<M, C> getEditingCell();

	ObservableCellEditor<M, C> decorate(CellDecorator<M, C> decorator);

	default ObservableCellEditor<M, C> decorateAll(Consumer<ComponentDecorator> decorator) {
		return decorate(CellDecorator.constant(decorator));
	}

	ObservableCellEditor<M, C> withValueTooltip(BiFunction<? super M, ? super C, String> tooltip);

	/**
	 * @param clickCount The number of clicks to signal cell editing
	 * @return This editor
	 */
	ObservableCellEditor<M, C> withClicks(int clickCount);

	<E extends M> Component getListCellEditorComponent(LittleList<E> list, E modelValue, int rowIndex, boolean selected);

	/**
	 * Creates an edit test to edit a cell with a minimum mouse click count. Follows the same behavior as {@link DefaultCellEditor}.
	 *
	 * @param clickCount The minimum click count needed to start cell editing
	 * @return The edit test
	 */
	public static Predicate<EventObject> editWithClicks(int clickCount) {
		return evt -> {
			if (evt instanceof MouseEvent)
				return ((MouseEvent) evt).getClickCount() >= clickCount;
				else
					return true;
		};
	}

	public static Predicate<EventObject> editWithNotDrag() {
		return evt -> {
			if (evt instanceof MouseEvent) {
				MouseEvent e = (MouseEvent) evt;
				return e.getID() != MouseEvent.MOUSE_DRAGGED;
			}
			return true;
		};
	}

	public static <M, C> ObservableCellEditor<M, C> createTextEditor(Format<C> format) {
		return createTextEditor(format, null);
	}

	public static <M, C> ObservableCellEditor<M, C> createTextEditor(Format<C> format, Consumer<ObservableTextField<C>> textField) {
		Function<C, String>[] filter = new Function[1];
		SettableValue<C> value = DefaultObservableCellEditor.createEditorValue(filter);
		ObservableTextField<C> field = new ObservableTextField<>(value, format, Observable.empty);
		if (textField != null)
			textField.accept(field);
		// Default margins for the text field don't fit into the rendered cell
		Insets defMargin = field.getMargin();
		boolean[] editing = new boolean[1];
		ObservableCellEditor<M, C> editor = new DefaultObservableCellEditor<M, C>(field, value, (e, c, f, tt, vtt) -> {
			if (c instanceof JTable) {
				Insets margin = field.getMargin();
				if (margin.top != 0 || margin.bottom != 0) {
					margin.top = 0;
					margin.bottom = 0;
					field.setMargin(margin);
				}
			} else
				field.setMargin(defMargin);
			filter[0] = f;
			field.setToolTipText(tt);
			editing[0] = true;
			if (field.isSelectAllOnFocus())
				field.selectAll();
			return commit -> {
				filter[0] = null;
				if (commit) {
					if (!field.isDirty()) {
						editing[0] = false;
						return true;
					} else if (field.getEditError() != null) {
						field.redisplayErrorTooltip();
						return false;
					} else {
						editing[0] = false;
						field.flushEdits(null);
					}
				}
				editing[0] = false;
				return true;
			};
		}, editWithClicks(2));
		value.noInitChanges().act(evt -> {
			if (editing[0])
				editor.stopCellEditing();
		});
		return editor;
	}

	public static <M, C> ObservableCellEditor<M, C> createComboEditor(Function<? super C, String> renderer,
		ObservableCollection<? extends C> options) {
		Function<C, String>[] filter = new Function[1];
		String[] tooltip = new String[1];
		Function<? super C, String>[] valueToolTip = new Function[1];
		SettableValue<C> value = DefaultObservableCellEditor.createEditorValue(filter);
		JComboBox<C> combo = new JComboBox<>();
		combo.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object option, int index, boolean isSelected,
				boolean cellHasFocus) {
				String text = renderer.apply((C) option);
				return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
			}
		});
		Subscription[] editSub = new Subscription[1];
		ObservableCellEditor<M, C> editor = new DefaultObservableCellEditor<>(combo, value, (e, c, f, tt, vtt) -> {
			filter[0] = f;
			tooltip[0] = tt;
			valueToolTip[0] = vtt;
			editSub[0] = ObservableComboBoxModel.comboFor(combo, tt, vtt, options, value);
			return commit -> {
				filter[0] = null;
				if (combo.isEditable()) // Just copying from DefaultCellEditor, not currently editable here, so just for posterity
					combo.actionPerformed(new ActionEvent(e, 0, ""));
				if (editSub[0] != null) {
					editSub[0].unsubscribe();
					editSub[0] = null;
				}
				return true;
			};
		}, editWithClicks(2));
		value.noInitChanges().act(evt -> {
			if (editSub[0] != null)
				editor.stopCellEditing();
		});
		return editor;
	}

	public static <M> ObservableCellEditor<M, Boolean> createCheckBoxEditor() {
		Function<Boolean, String>[] filter = new Function[1];
		SettableValue<Boolean> value = DefaultObservableCellEditor.createEditorValue(filter);
		JCheckBox check = new JCheckBox();
		Subscription[] editSub = new Subscription[1];
		ObservableCellEditor<M, Boolean> editor = new DefaultObservableCellEditor<>(check, value, (e, c, f, tt, vtt) -> {
			filter[0] = f;
			editSub[0] = ObservableSwingUtils.checkFor(check, tt, value);
			return commit -> {
				filter[0] = null;
				if (editSub[0] != null) {
					editSub[0].unsubscribe();
					editSub[0] = null;
				}
				return true;
			};
		}, editWithClicks(1));
		value.noInitChanges().act(evt -> {
			if (editSub[0] != null)
				editor.stopCellEditing();
		});
		return editor;
	}

	public static <M> ObservableCellEditor<M, Integer> createIntSliderEditor(int minValue, int maxValue) {
		Function<Integer, String>[] filter = new Function[1];
		SettableValue<Integer> value = DefaultObservableCellEditor.createEditorValue(filter);
		JSlider slider = new JSlider(minValue, maxValue);
		Subscription[] editSub = new Subscription[1];
		ObservableCellEditor<M, Integer> editor = new DefaultObservableCellEditor<>(slider, value, (e, c, f, tt, vtt) -> {
			filter[0] = f;
			editSub[0] = ObservableSwingUtils.sliderFor(slider, tt, value);
			return commit -> {
				filter[0] = null;
				if (editSub[0] != null) {
					editSub[0].unsubscribe();
					editSub[0] = null;
				}
				return true;
			};
		}, evt -> true);
		value.noInitChanges().act(evt -> {
			if (editSub[0] != null && !slider.getValueIsAdjusting())
				editor.stopCellEditing();
		});
		return editor;
	}

	public static <M> ObservableCellEditor<M, Double> createDoubleSliderEditor(double minValue, double maxValue) {
		int ticks = 500;
		double tickSize = (maxValue - minValue) / 500;
		Function<Integer, String>[] filter = new Function[1];
		SettableValue<Integer> sliderValue = DefaultObservableCellEditor.createEditorValue(filter);
		SettableValue<Double> value = sliderValue.map(tick -> minValue + tick * tickSize, v -> (int) Math.round((v - minValue) / tickSize));
		JSlider slider = new JSlider(0, ticks);
		Subscription[] editSub = new Subscription[1];
		ObservableCellEditor<M, Double> editor = new DefaultObservableCellEditor<>(slider, value, (e, c, f, tt, vtt) -> {
			filter[0] = iv -> {
				if (f == null)
					return null;
				double d = minValue + iv * tickSize;
				return f.apply(d);
			};
			editSub[0] = ObservableSwingUtils.sliderFor(slider, tt, sliderValue);
			return commit -> {
				filter[0] = null;
				if (editSub[0] != null) {
					editSub[0].unsubscribe();
					editSub[0] = null;
				}
				return true;
			};
		}, evt -> true);
		sliderValue.noInitChanges().act(evt -> {
			if (editSub[0] != null && !slider.getValueIsAdjusting())
				editor.stopCellEditing();
		});
		return editor;
	}

	public static <M, C> ObservableCellEditor<M, C> createButtonEditor(Function<? super C, String> renderer,
		Function<? super C, ? extends C> action) {
		Function<C, String>[] filter = new Function[1];
		SettableValue<C> value = DefaultObservableCellEditor.createEditorValue(filter);
		JButton button = new JButton();
		boolean[] editing = new boolean[1];
		ObservableCellEditor<M, C> editor = new DefaultObservableCellEditor<>(button, value, (e, c, f, tt, vtt) -> {
			filter[0] = f;
			button.setText(renderer.apply(value.get()));
			button.setToolTipText(tt);
			editing[0] = true;
			return commit -> {
				filter[0] = null;
				editing[0] = false;
				return true;
			};
		}, editWithClicks(1));
		button.addActionListener(evt -> {
			C newVal = action.apply(value.get());
			if (editor.getEditingCell() != null)
				value.set(newVal, evt);
			editor.stopCellEditing();
		});
		return editor;
	}

	public static <M, C> ObservableCellEditor<M, ? super C> createButtonCellEditor(Function<? super C, String> renderer,
		Function<? super ModelCell<M, ? extends C>, ? extends C> action) {
		Function<C, String>[] filter = new Function[1];
		SettableValue<C> value = DefaultObservableCellEditor.createEditorValue(filter);
		JButton button = new JButton();
		boolean[] editing = new boolean[1];
		ObservableCellEditor<M, C> editor = new DefaultObservableCellEditor<>(button, value, (e, c, f, tt, vtt) -> {
			filter[0] = f;
			button.setText(renderer.apply(value.get()));
			button.setToolTipText(tt);
			editing[0] = true;
			return commit -> {
				filter[0] = null;
				editing[0] = false;
				return true;
			};
		}, editWithClicks(1));
		button.addActionListener(evt -> {
			C newVal = action.apply(editor.getEditingCell());
			if (editor.getEditingCell() != null)
				value.set(newVal, evt);
			editor.stopCellEditing();
		});
		return editor;
	}

	public static <M, C> CompositeCellEditor<M, C> composite(IntFunction<M> model, ObservableCellEditor<M, C> defaultEditor) {
		return new CompositeCellEditor<>(model, defaultEditor);
	}

	class DefaultObservableCellEditor<M, C> implements ObservableCellEditor<M, C> {
		private final Component theEditorComponent;
		private final SettableValue<C> theEditorValue;
		private final EditorInstallation<C> theInstallation;
		private Predicate<EventObject> theEditTest;
		private CellDecorator<M, C> theDecorator;
		private BiFunction<? super M, ? super C, String> theValueTooltip;

		private ModelCell<M, C> theEditingCell;

		private final List<CellEditorListener> theListeners;
		private EditorSubscription theEditorSubscription;

		DefaultObservableCellEditor(Component editorComponent, SettableValue<C> editorValue, EditorInstallation<C> installation,
			Predicate<EventObject> editTest) {
			theEditorComponent = editorComponent;
			theEditorValue = editorValue;
			theInstallation = installation;
			theEditTest = editTest;

			theListeners = new LinkedList<>();
		}

		@Override
		public ModelCell<M, C> getEditingCell() {
			return theEditingCell;
		}

		@Override
		public ObservableCellEditor<M, C> decorate(CellDecorator<M, C> decorator) {
			if (theDecorator == null)
				theDecorator = decorator;

			return this;
		}

		@Override
		public ObservableCellEditor<M, C> withValueTooltip(BiFunction<? super M, ? super C, String> tooltip) {
			theValueTooltip = tooltip;
			return this;
		}

		@Override
		public ObservableCellEditor<M, C> withClicks(int clickCount) {
			theEditTest = clickCount == 0 ? editWithNotDrag() : editWithClicks(clickCount);
			return this;
		}

		public Component getEditorComponent() {
			return theEditorComponent;
		}

		@Override
		public Object getCellEditorValue() {
			return theEditorValue.get();
		}

		@Override
		public boolean isCellEditable(EventObject anEvent) {
			return theEditTest.test(anEvent);
		}

		@Override
		public boolean shouldSelectCell(EventObject anEvent) {
			return true;
		}

		@Override
		public boolean stopCellEditing() {
			if (theEditorSubscription != null) {
				if (!theEditorSubscription.uninstall(true))
					return false;
				theEditorSubscription = null;
			}
			ChangeEvent changeEvent = new ChangeEvent(this);
			for (CellEditorListener listener : theListeners)
				listener.editingStopped(changeEvent);
			theEditingCell = null;
			return true;
		}

		@Override
		public void cancelCellEditing() {
			if (theEditorSubscription != null) {
				theEditorSubscription.uninstall(false);
				theEditorSubscription = null;
			}
			ChangeEvent changeEvent = new ChangeEvent(this);
			for (CellEditorListener listener : theListeners)
				listener.editingCanceled(changeEvent);
			theEditingCell = null;
		}

		@Override
		public void addCellEditorListener(CellEditorListener l) {
			theListeners.add(l);
		}

		@Override
		public void removeCellEditorListener(CellEditorListener l) {
			theListeners.remove(l);
		}

		@Override
		public <E extends M> Component getListCellEditorComponent(LittleList<E> list, E modelValue, int rowIndex, boolean selected) {
			if (theEditorSubscription != null) {
				theEditorSubscription.uninstall(false);
				theEditorSubscription = null;
			}
			ObservableListModel<E> model = list.getModel();
			CategoryRenderStrategy<E, E> category = list.getRenderStrategy();
			Function<C, String> valueFilter;
			Function<C, String> valueTooltip;
			String tooltip;

			if (rowIndex < model.getSize()) {
				MutableCollectionElement<E> modelElement = model.getWrapped()
					.mutableElement(model.getWrapped().getElement(rowIndex).getElementId());
				valueFilter = v -> {
					if (v == null || TypeTokens.get().isInstance(model.getWrapped().getType(), v))
						return category.getMutator().isAcceptable(modelElement, (E) v);
					else
						return "Unacceptable value";
				};
			} else {
				valueFilter = v -> {
					if (v == null || TypeTokens.get().isInstance(model.getWrapped().getType(), v)) {
						String msg = category.getMutator().isAcceptable(null, (E) v);
						if (msg == null)
							msg = model.getWrapped().canAdd((E) v);
						return msg;
					} else
						return "Unacceptable value";
				};
			}
			if (category.getMutator().getEditorTooltip() != null)
				tooltip = category.getMutator().getEditorTooltip().apply(modelValue, modelValue);
			else
				tooltip = category.getTooltip(modelValue, modelValue);
			valueTooltip = c -> theValueTooltip.apply(modelValue, c);

			theEditingCell = new ModelCell.Default<>(() -> modelValue, (C) modelValue, rowIndex, 0, selected, selected, true, true);
			if (theEditorValue.get() != modelValue)
				theEditorValue.set((C) modelValue, null);
			if (theDecorator != null) {
				ComponentDecorator cd = new ComponentDecorator();
				theDecorator.decorate(theEditingCell, cd);
				cd.decorate(theEditorComponent);
			}
			theEditorSubscription = theInstallation.install(this, list, valueFilter, tooltip, valueTooltip);
			return theEditorComponent;
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
			if (theEditorSubscription != null) {
				theEditorSubscription.uninstall(false);
				theEditorSubscription = null;
			}
			TableModel model = table.getModel();
			M modelValue;
			Function<C, String> valueFilter;
			Function<C, String> valueTooltip;
			String tooltip;
			if (model instanceof ObservableTableModel) {
				ObservableTableModel<? extends M> obsModel = (ObservableTableModel<? extends M>) model;
				modelValue = obsModel.getRow(row); // This is more reliable and thread-safe
				MutableCollectionElement<M> modelElement = (MutableCollectionElement<M>) obsModel.getRows()
					.mutableElement(obsModel.getRows().getElement(row).getElementId());
				CategoryRenderStrategy<M, C> category = (CategoryRenderStrategy<M, C>) obsModel.getColumn(column);
				valueFilter = v -> {
					if (v == null || TypeTokens.get().isInstance(category.getType(), v))
						return category.getMutator().isAcceptable(modelElement, v);
					else
						return "Unacceptable value";
				};
				if (category.getMutator().getEditorTooltip() != null)
					tooltip = category.getMutator().getEditorTooltip().apply(modelValue, (C) value);
				else
					tooltip = category.getTooltip(modelValue, (C) value);
				valueTooltip = c -> theValueTooltip.apply(modelValue, c);
			} else {
				modelValue = null;
				valueFilter = null;
				tooltip = null;
				valueTooltip = null;
			}
			theEditingCell = new ModelCell.Default<>(() -> modelValue, (C) value, row, column, isSelected, isSelected, true, true);
			if (theEditorValue.get() != value)
				theEditorValue.set((C) value, null);
			if (theDecorator != null) {
				ComponentDecorator cd = new ComponentDecorator();
				theDecorator.decorate(theEditingCell, cd);
				cd.decorate(theEditorComponent);
			}
			theEditorSubscription = theInstallation.install(this, table, valueFilter, tooltip, valueTooltip);
			return theEditorComponent;
		}

		@Override
		public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row) {
			if (theEditorSubscription != null) {
				theEditorSubscription.uninstall(false);
				theEditorSubscription = null;
			}
			// TODO See if there's a way to get the information needed for the value filter and tooltip somewhere
			theEditingCell = new ModelCell.Default<>(() -> (M) value, (C) value, row, 0, isSelected, isSelected, expanded, leaf);
			theEditorValue.set((C) value, null);
			if (theDecorator != null) {
				ComponentDecorator cd = new ComponentDecorator();
				theDecorator.decorate(theEditingCell, cd);
				cd.decorate(theEditorComponent);
			}
			theEditorSubscription = theInstallation.install(this, tree, null, null, null);
			return theEditorComponent;
		}

		public static <C> SettableValue<C> createEditorValue(Function<C, String>[] filter) {
			return new SimpleSettableValue<>((TypeToken<C>) TypeTokens.get().OBJECT, true, null,
				opts -> opts.forEachSafe(false).allowReentrant()).filterAccept(v -> {
					if (filter[0] != null)
						return filter[0].apply(v);
					else
						return null;
				});
		}
	}

	public static class CompositeCellEditor<M, C> implements ObservableCellEditor<M, C> {
		class ComponentEditor {
			final ObservableCellEditor<M, C> editor;
			final BiPredicate<M, C> filter;

			ComponentEditor(ObservableCellEditor<M, C> editor, BiPredicate<M, C> filter) {
				this.editor = editor;
				this.filter = filter;
			}
		}
		private final IntFunction<M> theModel;
		private ObservableCellEditor<M, C> theDefaultEditor;
		private List<ComponentEditor> theComponents;

		private ObservableCellEditor<M, C> theCurrentEditor;

		CompositeCellEditor(IntFunction<M> model, ObservableCellEditor<M, C> defaultEditor) {
			theModel = model;
			theDefaultEditor = defaultEditor;
			theComponents = new LinkedList<>();
		}

		@Override
		public ModelCell<M, C> getEditingCell() {
			return theCurrentEditor == null ? null : theCurrentEditor.getEditingCell();
		}

		public CompositeCellEditor<M, C> withComponent(ObservableCellEditor<M, C> editor, BiPredicate<M, C> filter) {
			theComponents.add(new ComponentEditor(editor, filter));
			return this;
		}

		@Override
		public CompositeCellEditor<M, C> decorate(CellDecorator<M, C> decorator) {
			theDefaultEditor.decorate(decorator);
			for (ComponentEditor component : theComponents)
				component.editor.decorate(decorator);
			return this;
		}

		@Override
		public CompositeCellEditor<M, C> withValueTooltip(BiFunction<? super M, ? super C, String> tooltip) {
			theDefaultEditor.withValueTooltip(tooltip);
			for (ComponentEditor component : theComponents)
				component.editor.withValueTooltip(tooltip);
			return this;
		}

		@Override
		public CompositeCellEditor<M, C> withClicks(int clickCount) {
			theDefaultEditor.withClicks(clickCount);
			for (ComponentEditor component : theComponents)
				component.editor.withClicks(clickCount);
			return this;
		}

		@Override
		public boolean isCellEditable(EventObject anEvent) {
			return theDefaultEditor.isCellEditable(anEvent);
		}

		@Override
		public boolean shouldSelectCell(EventObject anEvent) {
			return theDefaultEditor.shouldSelectCell(anEvent);
		}

		@Override
		public Object getCellEditorValue() {
			return theCurrentEditor.getCellEditorValue();
		}

		@Override
		public boolean stopCellEditing() {
			return theCurrentEditor.stopCellEditing();
		}

		@Override
		public void cancelCellEditing() {
			theCurrentEditor.cancelCellEditing();
		}

		@Override
		public void addCellEditorListener(CellEditorListener l) {
			theDefaultEditor.addCellEditorListener(l);
			for (ComponentEditor component : theComponents)
				component.editor.addCellEditorListener(l);
		}

		@Override
		public void removeCellEditorListener(CellEditorListener l) {
			theDefaultEditor.removeCellEditorListener(l);
			for (ComponentEditor component : theComponents)
				component.editor.removeCellEditorListener(l);
		}

		protected ObservableCellEditor<M, C> selectComponent(M modelValue, C cellValue) {
			ObservableCellEditor<M, C> selected = null;
			for (ComponentEditor component : theComponents) {
				if (component.filter.test(modelValue, cellValue)) {
					selected = component.editor;
					break;
				}
			}
			if (selected == null)
				selected = theDefaultEditor;
			return selected;
		}

		@Override
		public <E extends M> Component getListCellEditorComponent(LittleList<E> list, E modelValue, int rowIndex, boolean selected) {
			theCurrentEditor = selectComponent(modelValue, (C) modelValue);
			return theCurrentEditor.getListCellEditorComponent(list, modelValue, rowIndex, selected);
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
			M modelValue = theModel.apply(row);
			theCurrentEditor = selectComponent(modelValue, (C) value);
			return theCurrentEditor.getTableCellEditorComponent(table, value, isSelected, row, column);
		}

		@Override
		public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row) {
			M modelValue = theModel.apply(row);
			theCurrentEditor = selectComponent(modelValue, (C) value);
			return theCurrentEditor.getTreeCellEditorComponent(tree, value, isSelected, expanded, leaf, row);
		}
	}

}

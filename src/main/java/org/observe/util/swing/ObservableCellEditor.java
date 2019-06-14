package org.observe.util.swing;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.EventObject;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
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

public class ObservableCellEditor<M, C> implements TableCellEditor, TreeCellEditor {
	public interface CellDecorator<M, C> {
		void decorate(Component editorComponent, M modelValue, C cellValue, boolean selected, boolean expanded, boolean leaf);
	}

	public interface EditorSubscription {
		boolean uninstall(boolean commit);
	}

	public interface EditorInstallation<C> {
		EditorSubscription install(ObservableCellEditor<?, C> editor, Function<C, String> valueFilter, String tooltip,
			Function<? super C, String> valueToolTip);
	}

	private final Component theEditorComponent;
	private final SettableValue<C> theEditorValue;
	private final EditorInstallation<C> theInstallation;
	private Predicate<EventObject> theEditTest;
	private CellDecorator<M, C> theDecorator;
	private BiFunction<? super M, ? super C, String> theValueTooltip;

	private final List<CellEditorListener> theListeners;
	private EditorSubscription theEditorSubscription;

	public ObservableCellEditor(Component editorComponent, SettableValue<C> editorValue, EditorInstallation installation,
		Predicate<EventObject> editTest) {
		theEditorComponent = editorComponent;
		theEditorValue = editorValue;
		theInstallation = installation;
		theEditTest = editTest;

		theListeners = new LinkedList<>();
	}

	public ObservableCellEditor<M, C> decorate(CellDecorator<M, C> decorator) {
		theDecorator = decorator;
		return this;
	}

	public ObservableCellEditor<M, C> withValueTooltip(BiFunction<? super M, ? super C, String> tooltip) {
		theValueTooltip = tooltip;
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
				if (TypeTokens.get().isInstance(category.getType(), v))
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
		theEditorValue.set((C) value, null);
		if (theDecorator != null)
			theDecorator.decorate(theEditorComponent, modelValue, (C) value, isSelected, false, true);
		theEditorSubscription = theInstallation.install(this, valueFilter, tooltip, valueTooltip);
		return theEditorComponent;
	}

	@Override
	public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row) {
		if (theEditorSubscription != null) {
			theEditorSubscription.uninstall(false);
			theEditorSubscription = null;
		}
		// TODO See if there's a way to get the information needed for the value filter and tooltip somewhere
		theEditorValue.set((C) value, null);
		if (theDecorator != null)
			theDecorator.decorate(theEditorComponent, (M) value, (C) value, isSelected, false, true);
		theEditorSubscription = theInstallation.install(this, null, null, null);
		return theEditorComponent;
	}

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

	private static <C> SettableValue<C> createEditorValue(Function<C, String>[] filter) {
		return new SimpleSettableValue<>((TypeToken<C>) TypeTokens.get().OBJECT, true, null,
			opts -> opts.forEachSafe(false).allowReentrant()).filterAccept(v -> {
				if (filter[0] != null)
					return filter[0].apply(v);
				else
					return null;
			});
	}

	public static <M, C> ObservableCellEditor<M, C> createTextEditor(Format<C> format) {
		Function<C, String>[] filter = new Function[1];
		SettableValue<C> value = createEditorValue(filter);
		ObservableTextField<C> field = new ObservableTextField<>(value, format, Observable.empty);
		boolean[] editing = new boolean[1];
		ObservableCellEditor<M, C> editor = new ObservableCellEditor<>(field, value, (e, f, tt, vtt) -> {
			filter[0] = f;
			field.setToolTipText(tt);
			editing[0] = true;
			return commit -> {
				if (commit) {
					if (field.getEditError() != null) {
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
		}, editWithClicks(1));
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
		SettableValue<C> value = createEditorValue(filter);
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
		ObservableCellEditor<M, C> editor = new ObservableCellEditor<>(combo, value, (e, f, tt, vtt) -> {
			filter[0] = f;
			tooltip[0] = tt;
			valueToolTip[0] = vtt;
			editSub[0] = ObservableComboBoxModel.comboFor(combo, tt, vtt, options, value);
			return commit -> {
				if (combo.isEditable()) // Just copying from DefaultCellEditor, not currently editable here, so just for posterity
					combo.actionPerformed(new ActionEvent(e, 0, ""));
				if (editSub[0] != null) {
					editSub[0].unsubscribe();
					editSub[0] = null;
				}
				return true;
			};
		}, editWithNotDrag());
		value.noInitChanges().act(evt -> {
			if (editSub[0] != null)
				editor.stopCellEditing();
		});
		return editor;
	}

	public static <M> ObservableCellEditor<M, Boolean> createCheckBoxEditor() {
		Function<Boolean, String>[] filter = new Function[1];
		SettableValue<Boolean> value = createEditorValue(filter);
		JCheckBox check = new JCheckBox();
		Subscription[] editSub = new Subscription[1];
		ObservableCellEditor<M, Boolean> editor = new ObservableCellEditor<>(check, value, (e, f, tt, vtt) -> {
			filter[0] = f;
			editSub[0] = ObservableSwingUtils.checkFor(check, tt, value);
			return commit -> {
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
		SettableValue<Integer> value = createEditorValue(filter);
		JSlider slider = new JSlider(minValue, maxValue);
		Subscription[] editSub = new Subscription[1];
		ObservableCellEditor<M, Integer> editor = new ObservableCellEditor<>(slider, value, (e, f, tt, vtt) -> {
			filter[0] = f;
			editSub[0] = ObservableSwingUtils.sliderFor(slider, tt, value);
			return commit -> {
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
		SettableValue<Integer> sliderValue = createEditorValue(filter);
		SettableValue<Double> value = sliderValue.map(tick -> minValue + tick * tickSize, v -> (int) Math.round((v - minValue) / tickSize));
		JSlider slider = new JSlider(0, ticks);
		Subscription[] editSub = new Subscription[1];
		ObservableCellEditor<M, Double> editor = new ObservableCellEditor<>(slider, value, (e, f, tt, vtt) -> {
			filter[0] = f;
			editSub[0] = ObservableSwingUtils.sliderFor(slider, tt, sliderValue);
			return commit -> {
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
		SettableValue<C> value = createEditorValue(filter);
		JButton button = new JButton();
		boolean[] editing = new boolean[1];
		ObservableCellEditor<M, C> editor = new ObservableCellEditor<>(button, value, (e, f, tt, vtt) -> {
			filter[0] = f;
			button.setText(renderer.apply(value.get()));
			button.setToolTipText(tt);
			editing[0] = true;
			return commit -> {
				editing[0] = false;
				return true;
			};
		}, editWithClicks(1));
		button.addActionListener(evt -> {
			value.set(action.apply(value.get()), evt);
			editor.stopCellEditing();
		});
		return editor;
	}
}

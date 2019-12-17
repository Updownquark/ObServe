package org.observe.util.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.beans.PropertyChangeListener;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.JTextComponent;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.Causable;
import org.qommons.Causable.CausableKey;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;

/** Utilities for the org.observe.util.swing package */
public class ObservableSwingUtils {
	private static final ListenerList<Runnable> EDT_EVENTS = ListenerList.build()//
		.allowReentrant() // Things running on the EDT by onEQ() may call onEQ()
		.forEachSafe(false) // Since we're not preventing reentrancy or using skipCurrent, we don't need this weight
		.withFastSize(false) // Not calling size()
		// Since events will often come as a flood after user interaction, I think it's better to have one lock than two
		.withSyncType(ListenerList.SynchronizationType.LIST)//
		.build();

	/**
	 * Executes a task on the AWT/Swing event thread. If this thread *is* the event thread, the task is executed inline
	 *
	 * @param task The task to execute on the AWT {@link EventQueue}
	 */
	public static void onEQ(Runnable task) {
		boolean queueEmpty = EDT_EVENTS.isEmpty();
		// If the queue is not empty, we need to add the task to the queue instead of running it inline to avoid ordering problems
		if (!queueEmpty || !EventQueue.isDispatchThread()) {
			EDT_EVENTS.add(task, false);
			if (queueEmpty)
				EventQueue.invokeLater(ObservableSwingUtils::emptyEdtEvents);
		} else
			task.run();
	}

	private static void emptyEdtEvents() {
		ListenerList.Element<Runnable> task = EDT_EVENTS.poll(0);
		while (task != null) {
			try {
				task.get().run();
			} catch (RuntimeException e) {
				e.printStackTrace();
			}
			task = EDT_EVENTS.poll(0);
		}
	}

	/**
	 * @param text The text to create the label with
	 * @return The LabelHolder to use to configure the label
	 */
	public static FontAdjuster<Component> label(String text) {
		return new FontAdjuster<>(new JLabel(text));
	}

	/**
	 * @param label The label to create the holder with
	 * @return The LabelHolder to use to configure the label
	 */
	public static FontAdjuster<Component> label(JLabel label) {
		return new FontAdjuster<>(label);
	}

	/**
	 * A holder of a Component that contains many chain-compatible methods for font configuration
	 *
	 * @param <C> The type of the component held by the holder
	 */
	public static class FontAdjuster<C extends Component> implements Supplier<C> {
		/** The label */
		public C label;

		/** @param label The component to create the holder with */
		public FontAdjuster(C label) {
			this.label = label;
		}

		/**
		 * @param label The label to adjust
		 * @return This holder
		 */
		public FontAdjuster<C> setLabel(C label) {
			this.label = label;
			return this;
		}

		/**
		 * Performs a generic operation on the label
		 *
		 * @param adjustment The operation
		 * @return This holder
		 */
		public FontAdjuster<C> adjust(Consumer<C> adjustment) {
			adjustment.accept(label);
			return this;
		}

		/**
		 * Makes the label's font {@link Font#BOLD bold}
		 *
		 * @return This holder
		 */
		public FontAdjuster<C> bold() {
			return withStyle(Font.BOLD);
		}

		/**
		 * @param bold Whether the label should be {@link Font#BOLD bold}
		 * @return This holder
		 */
		public FontAdjuster<C> bold(boolean bold) {
			if (bold)
				bold();
			else
				plain();
			return this;
		}

		/**
		 * Makes the label's font {@link Font#PLAIN plain}
		 *
		 * @return This holder
		 */
		public FontAdjuster<C> plain() {
			return withStyle(Font.PLAIN);
		}

		/**
		 * @param fontSize The point size for the label's font
		 * @return This holder
		 */
		public FontAdjuster<C> withFontSize(float fontSize) {
			label.setFont(label.getFont().deriveFont(fontSize));
			return this;
		}

		/**
		 * @param style The font {@link Font#getStyle() style} for the label
		 * @return This holder
		 */
		public FontAdjuster<C> withStyle(int style) {
			label.setFont(label.getFont().deriveFont(style));
			return this;
		}

		/**
		 * @param style The font {@link Font#getStyle() style} for the label
		 * @param fontSize The point size for the label's font
		 * @return This holder
		 */
		public FontAdjuster<C> withSizeAndStyle(int style, float fontSize) {
			label.setFont(label.getFont().deriveFont(style, fontSize));
			return this;
		}

		/**
		 * @param color The font color for the label
		 * @return This holder
		 */
		public FontAdjuster<C> withColor(Color color) {
			label.setForeground(color);
			return this;
		}

		/**
		 * Changes the horizontal alignment of the component (if supported)
		 *
		 * @param align Negative for left, zero for center, positive for right
		 * @return This holder
		 */
		public FontAdjuster<C> alignH(int align) {
			int swingAlign = align < 0 ? SwingConstants.LEADING : (align > 0 ? SwingConstants.TRAILING : SwingConstants.CENTER);
			if (label instanceof JLabel)
				((JLabel) label).setHorizontalAlignment(swingAlign);
			else if (label instanceof JTextField)
				((JTextField) label).setHorizontalAlignment(//
					align < 0 ? JTextField.LEADING : (align > 0 ? JTextField.TRAILING : JLabel.CENTER));
			else if (label instanceof JTextComponent)
				((JTextComponent) label).setAlignmentX(//
					align < 0 ? 0f : (align > 0 ? 1f : 0.5f));
			return this;
		}

		/**
		 * Changes the horizontal alignment of the component (if supported)
		 *
		 * @param align Negative for left, zero for center, positive for right
		 * @return This holder
		 */
		public FontAdjuster<C> alignV(int align) {
			int swingAlign = align < 0 ? SwingConstants.LEADING : (align > 0 ? SwingConstants.TRAILING : SwingConstants.CENTER);
			if (label instanceof JLabel)
				((JLabel) label).setVerticalAlignment(swingAlign);
			else if (label instanceof JTextComponent)
				((JTextComponent) label).setAlignmentX(//
					align < 0 ? 0f : (align > 0 ? 1f : 0.5f));
			return this;
		}

		@Override
		public C get() {
			return label;
		}

		@Override
		public String toString() {
			return label.toString();
		}
	}

	/**
	 * Links up a check box's {@link JCheckBox#isSelected() selected} state to a settable boolean, such that the user's interaction with the
	 * check box is reported by the value, and setting the value alters the check box.
	 *
	 * @param checkBox The check box to control observably
	 * @param descrip The description for the check box's tool tip when it is enabled
	 * @param selected The settable, observable boolean to control the check box's selection
	 * @return The subscription to {@link Subscription#unsubscribe() unsubscribe} to terminate the link
	 */
	public static Subscription checkFor(JCheckBox checkBox, String descrip, SettableValue<Boolean> selected) {
		return checkFor(checkBox, ObservableValue.of(TypeTokens.get().STRING, descrip), selected);
	}

	/**
	 * Links up a check box's {@link JCheckBox#isSelected() selected} state to a settable boolean, such that the user's interaction with the
	 * check box is reported by the value, and setting the value alters the check box.
	 *
	 * @param checkBox The check box to control observably
	 * @param descrip The description for the check box's tool tip when it is enabled
	 * @param selected The settable, observable boolean to control the check box's selection
	 * @return The subscription to {@link Subscription#unsubscribe() unsubscribe} to terminate the link
	 */
	public static Subscription checkFor(JCheckBox checkBox, ObservableValue<String> descrip, SettableValue<Boolean> selected) {
		ActionListener action = evt -> {
			selected.set(checkBox.isSelected(), evt);
		};
		checkBox.addActionListener(action);
		boolean[] callbackLock = new boolean[1];
		Consumer<String> checkEnabled = enabled -> {
			if (enabled == null) {
				enabled = selected.isAcceptable(!checkBox.isSelected());
			}
			checkBox.setEnabled(enabled == null);
			checkBox.setToolTipText(enabled == null ? descrip.get() : enabled);
		};
		Subscription descripSub = descrip.changes().act(evt -> {
			if (selected.isEnabled().get() == null)
				checkBox.setToolTipText(evt.getNewValue());
		});
		Subscription valueSub = selected.changes().act(evt -> {
			if (!callbackLock[0])
				checkBox.setSelected(evt.getNewValue() == null ? false : evt.getNewValue());
			checkEnabled.accept(selected.isEnabled().get());
		});
		Subscription enabledSub = selected.isEnabled().changes().act(evt -> checkEnabled.accept(evt.getNewValue()));
		return () -> {
			valueSub.unsubscribe();
			enabledSub.unsubscribe();
			descripSub.unsubscribe();
			checkBox.removeActionListener(action);
		};
	}

	/**
	 * Links up the {@link JToggleButton#isSelected() selected} state of a list of {@link JToggleButton}s to a settable value, such that the
	 * user's interaction with the toggle buttons is reported by the value, and setting the value alters the toggle buttons.
	 *
	 * @param <T> The type of the option values
	 * @param buttons The toggle buttons to control observably
	 * @param options The options corresponding to each toggle button. Each toggle button will be selected when the <code>selected</code>
	 *        observable's value is equal to the button's corresponding value.
	 * @param descrips The description tooltips for each toggle button when enabled
	 * @param selected The value observable to control the toggle buttons
	 * @return The subscription to {@link Subscription#unsubscribe() unsubscribe} to terminate the link
	 */
	public static <T> Subscription togglesFor(JToggleButton[] buttons, T[] options, String[] descrips, SettableValue<T> selected) {
		ActionListener[] actions = new ActionListener[buttons.length];
		for (int i = 0; i < buttons.length; i++) {
			int index = i;
			actions[i] = evt -> {
				selected.set(options[index], evt);
			};
			buttons[i].addActionListener(actions[i]);
		}
		Consumer<String> checkEnabled = enabled -> {
			for (int i = 0; i < buttons.length; i++) {
				String enabled_i = enabled;
				if (enabled_i == null) {
					enabled_i = selected.isAcceptable(options[i]);
				}
				buttons[i].setEnabled(enabled_i == null);
				if (enabled != null) {
					buttons[i].setToolTipText(enabled_i);
				} else if (descrips != null) {
					buttons[i].setToolTipText(descrips[i]);
				} else {
					buttons[i].setToolTipText(null);
				}
			}
		};
		Subscription selectedSub = selected.changes().act(evt -> {
			T value = evt.getNewValue();
			for (int i = 0; i < options.length; i++) {
				buttons[i].setSelected(Objects.equals(options[i], value));
			}
			checkEnabled.accept(selected.isEnabled().get());
		});
		Subscription enabledSub = selected.isEnabled().changes().act(evt -> checkEnabled.accept(evt.getNewValue()));
		return () -> {
			selectedSub.unsubscribe();
			enabledSub.unsubscribe();
			for (int i = 0; i < buttons.length; i++)
				buttons[i].removeActionListener(actions[i]);
		};
	}

	/**
	 * Links up a spinner's {@link JSpinner#getValue() value} with the value in a settable integer value, such that the user's interaction
	 * with the spinner is reported by the value, and setting the value alters the spinner.
	 *
	 * @param spinner The spinner to control observably
	 * @param descrip The description tooltip for the spinner when enabled
	 * @param value The value observable to control the spinner
	 * @return The subscription to {@link Subscription#unsubscribe() unsubscribe} to terminate the link
	 */
	public static Subscription intSpinnerFor(JSpinner spinner, String descrip, SettableValue<Integer> value) {
		return spinnerFor(spinner, descrip, value, Number::intValue);
	}

	/**
	 * Links up a spinner's {@link JSpinner#getValue() value} with the value in a settable double value, such that the user's interaction
	 * with the spinner is reported by the value, and setting the value alters the spinner.
	 *
	 * @param spinner The spinner to control observably
	 * @param descrip The description tooltip for the spinner when enabled
	 * @param value The value observable to control the spinner
	 * @return The subscription to {@link Subscription#unsubscribe() unsubscribe} to terminate the link
	 */
	public static Subscription doubleSpinnerFor(JSpinner spinner, String descrip, SettableValue<Double> value) {
		return spinnerFor(spinner, descrip, value, Number::doubleValue);
	}

	/**
	 * Links up a spinner's {@link JSpinner#getValue() value} with the value in a settable value, such that the user's interaction with the
	 * spinner is reported by the value, and setting the value alters the spinner.
	 *
	 * @param <T> The type of the model value
	 * @param spinner The spinner to control observably
	 * @param descrip The description tooltip for the spinner when enabled
	 * @param value The value observable to control the spinner
	 * @return The subscription to {@link Subscription#unsubscribe() unsubscribe} to terminate the link
	 */
	public static <T> Subscription spinnerFor(JSpinner spinner, String descrip, SettableValue<T> value) {
		return spinnerFor(spinner, descrip, value, v -> v);
	}

	/**
	 * @param <T> The type of the model value
	 * @param spinner The spinner to hook up
	 * @param descrip The description tooltip for the spinner when enabled
	 * @param value The value observable to control the spinner
	 * @param purify A function to call on the spinner's value before passing it to the model value
	 * @return The subscription to {@link Subscription#unsubscribe() unsubscribe} to terminate the link
	 */
	public static <T> Subscription spinnerFor(JSpinner spinner, String descrip, SettableValue<T> value,
		Function<? super T, ? extends T> purify) {
		boolean[] callbackLock = new boolean[1];
		ChangeListener changeListener = evt -> {
			if (!callbackLock[0]) {
				callbackLock[0] = true;
				try {
					T newValue = purify.apply((T) spinner.getValue());
					String accept = value.isAcceptable(newValue);
					if (accept != null) {
						JOptionPane.showMessageDialog(spinner.getParent(), accept, "Unacceptable Value", JOptionPane.ERROR_MESSAGE);
						spinner.setValue(value.get());
					} else {
						value.set(newValue, evt);
					}
				} finally {
					callbackLock[0] = false;
				}
			}
		};
		spinner.addChangeListener(changeListener);

		Subscription valueSub = value.changes().act(evt -> {
			if (!callbackLock[0]) {
				callbackLock[0] = true;
				try {
					T newValue = purify.apply(evt.getNewValue());
					spinner.setValue(newValue);
				} finally {
					callbackLock[0] = false;
				}
			}
		});
		Subscription enabledSub = value.isEnabled().changes().act(evt -> {
			String enabled = evt.getNewValue();
			spinner.setEnabled(enabled == null);
			spinner.setToolTipText(enabled != null ? enabled : descrip);
		});
		return () -> {
			valueSub.unsubscribe();
			enabledSub.unsubscribe();
			spinner.removeChangeListener(changeListener);
		};
	}

	/**
	 * Links up a slider's {@link JSlider#getValue() value} with the value in a settable integer value, such that the user's interaction
	 * with the slider is reported by the value, and setting the value alters the slider.
	 *
	 * @param slider The slider to control observably
	 * @param descrip The description tooltip for the slider when enabled
	 * @param value The value observable to control the slider
	 * @return The subscription to {@link Subscription#unsubscribe() unsubscribe} to terminate the link
	 */
	public static Subscription sliderFor(JSlider slider, String descrip, SettableValue<Integer> value) {
		boolean[] callbackLock = new boolean[1];
		ChangeListener changeListener = evt -> {
			if (!callbackLock[0] && !slider.getValueIsAdjusting()) {
				callbackLock[0] = true;
				try {
					int newValue = slider.getValue();
					String accept = value.isAcceptable(newValue);
					if (accept != null) {
						JOptionPane.showMessageDialog(slider.getParent(), accept, "Unacceptable Value", JOptionPane.ERROR_MESSAGE);
						slider.setValue(value.get());
					} else {
						value.set(newValue, evt);
					}
				} finally {
					callbackLock[0] = false;
				}
			}
		};
		slider.addChangeListener(changeListener);
		Subscription valueSub = value.changes().act(evt -> {
			if (!callbackLock[0]) {
				callbackLock[0] = true;
				try {
					slider.setValue(evt.getNewValue());
				} finally {
					callbackLock[0] = false;
				}
			}
		});
		Subscription enabledSub = value.isEnabled().changes().act(evt -> {
			String enabled = evt.getNewValue();
			slider.setEnabled(enabled == null);
			slider.setToolTipText(enabled != null ? enabled : descrip);
		});
		return () -> {
			valueSub.unsubscribe();
			enabledSub.unsubscribe();
			slider.removeChangeListener(changeListener);
		};
	}

	/**
	 * Synchronizes the selection of a list model with an observable collection. The synchronization works in both ways: UI changes to the
	 * selection will cause value changes in the table, and external changes to the selection will affect UI selection, if possible. Some
	 * external changes are incompatible with selection synchronization, including adding values that are not present in the model, adding
	 * duplicate values that are not present multiple times in the model, and set operations. When such operations occur, they will be
	 * undone when their transaction ends.
	 *
	 * <p>
	 * If multiple equivalent values are present in the model and they are selected via external change to the selection collection, (or if
	 * multiple equivalent values are selected and then are unselected externally) the position of the (un)selected value is undetermined.
	 * </p>
	 *
	 * @param component The component (such as a JTable or JList) containing the selection model
	 * @param model The list model with data that may be selected
	 * @param selectionModel Supplies the selected model, which may change at any time
	 * @param equivalence The equivalence by which to synchronize the 2 selection mechanisms
	 * @param selection The collection to synchronize with the UI selection
	 * @param until An observable whose firing will release all resources and listeners installed by this sync operation
	 * @return The selection collection
	 */
	public static <E> ObservableCollection<E> syncSelection(Component component, //
		ListModel<E> model, Supplier<ListSelectionModel> selectionModel, Equivalence<? super E> equivalence,
		ObservableCollection<E> selection, Observable<?> until) {
		Supplier<List<E>> selectionGetter = () -> getSelection(model, selectionModel.get(), null);
		boolean[] callbackLock = new boolean[1];
		Consumer<Object> syncSelection = cause -> {
			List<E> selValues = selectionGetter.get();
			try (Transaction selT = selection.lock(true, cause)) {
				ArrayUtils.adjust(selection, selValues, ArrayUtils.acceptAllDifferences(equivalence::elementEquals, null));
			}
		};
		ListSelectionListener selListener = e -> {
			ListSelectionModel selModel = selectionModel.get();
			if (selModel.getValueIsAdjusting() || callbackLock[0])
				return;
			callbackLock[0] = true;
			try {
				if (selModel.getMinSelectionIndex() < 0) {
					selection.clear();
					return;
				}
				syncSelection.accept(e);
			} finally {
				callbackLock[0] = false;
			}
		};
		ListDataListener modelListener = new ListDataListener() {
			@Override
			public void intervalRemoved(ListDataEvent e) {}

			@Override
			public void intervalAdded(ListDataEvent e) {}

			@Override
			public void contentsChanged(ListDataEvent e) {
				int selIdx = 0;
				callbackLock[0] = true;
				ListSelectionModel selModel = selectionModel.get();
				try (Transaction t = selection.lock(true, e)) {
					for (int i = selModel.getMinSelectionIndex(); i <= selModel.getMaxSelectionIndex() && i <= e.getIndex1(); i++) {
						if (selModel.isSelectedIndex(i)) {
							if (i >= e.getIndex0())
								selection.mutableElement(selection.getElement(selIdx).getElementId()).set(model.getElementAt(i));
							selIdx++;
						}
					}
				} finally {
					callbackLock[0] = false;
				}
			}
		};
		PropertyChangeListener selModelListener = evt -> {
			((ListSelectionModel) evt.getOldValue()).removeListSelectionListener(selListener);
			((ListSelectionModel) evt.getNewValue()).addListSelectionListener(selListener);
		};
		selectionModel.get().addListSelectionListener(selListener);
		if (component != null)
			component.addPropertyChangeListener("selectionModel", selModelListener);
		model.addListDataListener(modelListener);
		syncSelection.accept(null);
		CausableKey key = Causable.key((c, d) -> onEQ(() -> {
			selectionModel.get().setValueIsAdjusting(false);
			syncSelection.accept(c);
		}));
		selection.changes().takeUntil(until).act(evt -> onEQ(() -> {
			if (callbackLock[0])
				return;
			ListSelectionModel selModel = selectionModel.get();
			if (!selModel.getValueIsAdjusting())
				selModel.setValueIsAdjusting(true);
			evt.getRootCausable().onFinish(key);
			callbackLock[0] = true;
			try {
				int intervalStart = -1;
				switch (evt.type) {
				case add:
					for (int i = 0; i < model.getSize(); i++) {
						if (selModel.isSelectedIndex(i)) {
							if (intervalStart >= 0) {
								selModel.addSelectionInterval(intervalStart, i - 1);
								intervalStart = -1;
							}
							continue;
						}
						boolean added = false;
						for (E value : evt.getValues()) {
							if (equivalence.elementEquals(model.getElementAt(i), value)) {
								added = true;
								break;
							}
						}
						if (added) {
							if (intervalStart < 0)
								intervalStart = i;
						} else if (intervalStart >= 0) {
							selModel.addSelectionInterval(intervalStart, i - 1);
							intervalStart = -1;
						}
					}
					if (intervalStart >= 0)
						selModel.addSelectionInterval(intervalStart, model.getSize() - 1);
					break;
				case remove:
					for (int i = model.getSize() - 1; i >= 0; i--) {
						if (!selModel.isSelectedIndex(i)) {
							if (intervalStart >= 0) {
								selModel.removeSelectionInterval(i + 1, intervalStart);
								intervalStart = -1;
							}
							continue;
						}
						boolean removed = false;
						for (E value : evt.getValues()) {
							if (equivalence.elementEquals(model.getElementAt(i), value)) {
								removed = true;
								break;
							}
						}
						if (removed) {
							if (intervalStart < 0)
								intervalStart = i;
						} else if (intervalStart >= 0) {
							selModel.removeSelectionInterval(i + 1, intervalStart);
							intervalStart = -1;
						}
					}
					if (intervalStart >= 0)
						selModel.removeSelectionInterval(0, intervalStart);
					break;
				case set:
					break; // This doesn't have meaning here
				}
			} finally {
				callbackLock[0] = false;
			}
		}));

		until.take(1).act(__ -> onEQ(() -> {
			if (component != null)
				component.removePropertyChangeListener("selectionModel", selModelListener);
			selectionModel.get().removeListSelectionListener(selListener);
			model.removeListDataListener(modelListener);
		}));

		return selection;
	}

	/**
	 * @param <E> The type of the values in the list model
	 * @param <V> The type of the mapped values
	 * @param model The list model to get selection for
	 * @param selectionModel The selection model of the list or table
	 * @param map The function to map the values to the target type
	 * @return The list of currently selected model values
	 */
	public static <E, V> List<V> getSelection(ListModel<E> model, ListSelectionModel selectionModel, Function<? super E, ? extends V> map) {
		if (selectionModel.isSelectionEmpty())
			return Collections.emptyList();
		List<V> selValues = new ArrayList<>(selectionModel.getMaxSelectionIndex() - selectionModel.getMinSelectionIndex() + 1);
		for (int i = selectionModel.getMinSelectionIndex(); i <= selectionModel.getMaxSelectionIndex(); i++) {
			if (selectionModel.isSelectedIndex(i))
				selValues.add(map == null ? (V) model.getElementAt(i) : map.apply(model.getElementAt(i)));
		}
		return selValues;
	}

	/**
	 * Synchronizes selection between a UI selection model and a single value
	 *
	 * @param <E> The type of the model values
	 * @param component The component owning the model
	 * @param model The list model of values
	 * @param selectionModel The selection model of the list or table
	 * @param equivalence The equivalence to use to test equality between values
	 * @param selection The selection value to sync with
	 * @param until The observable to remove all the listeners
	 * @param update Receives an integer model index whenever the selection value is updated
	 * @param enforceSingleSelection Whether to set the list's {@link ListSelectionModel#setSelectionMode(int) selection mode} to
	 *        {@link ListSelectionModel#SINGLE_SELECTION single selection}
	 * @return The selection
	 */
	public static <E> SettableValue<E> syncSelection(Component component, ListModel<E> model, Supplier<ListSelectionModel> selectionModel,
		Equivalence<? super E> equivalence, SettableValue<E> selection, Observable<?> until, IntConsumer update,
		boolean enforceSingleSelection) {
		if (enforceSingleSelection)
			selectionModel.get().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		boolean[] callbackLock = new boolean[1];
		ListSelectionListener selListener = e -> {
			ListSelectionModel selModel = selectionModel.get();
			if (selModel.getValueIsAdjusting() || callbackLock[0])
				return;
			callbackLock[0] = true;
			try {
				if (selModel.getMinSelectionIndex() >= 0 && selModel.getMinSelectionIndex() == selModel.getMaxSelectionIndex()) {
					selection.set(model.getElementAt(selModel.getMinSelectionIndex()), e);
				} else if (selection.get() != null)
					selection.set(null, e);
			} finally {
				callbackLock[0] = false;
			}
		};
		ListDataListener modelListener = new ListDataListener() {
			@Override
			public void intervalRemoved(ListDataEvent e) {}

			@Override
			public void intervalAdded(ListDataEvent e) {}

			@Override
			public void contentsChanged(ListDataEvent e) {
				if (callbackLock[0])
					return;
				ListSelectionModel selModel = selectionModel.get();
				if (selModel.getMinSelectionIndex() < 0 || selModel.getMinSelectionIndex() != selModel.getMaxSelectionIndex())
					return;
				callbackLock[0] = true;
				try {
					if (e.getIndex0() <= selModel.getMinSelectionIndex() && e.getIndex1() >= selModel.getMinSelectionIndex())
						selection.set(model.getElementAt(selModel.getMinSelectionIndex()), e);
				} finally {
					callbackLock[0] = false;
				}
			}
		};
		PropertyChangeListener selModelListener = evt -> {
			((ListSelectionModel) evt.getOldValue()).removeListSelectionListener(selListener);
			((ListSelectionModel) evt.getNewValue()).addListSelectionListener(selListener);
		};
		selectionModel.get().addListSelectionListener(selListener);
		if (component != null)
			component.addPropertyChangeListener("selectionModel", selModelListener);
		model.addListDataListener(modelListener);
		selection.changes().takeUntil(until).act(evt -> onEQ(() -> {
			if (callbackLock[0])
				return;
			callbackLock[0] = true;
			try {
				ListSelectionModel selModel = selectionModel.get();
				if (evt.getNewValue() == null) {
					selModel.clearSelection();
					return;
				} else if (evt.getOldValue() == evt.getNewValue()//
					&& !selModel.isSelectionEmpty() && selModel.getMinSelectionIndex() == selModel.getMaxSelectionIndex()//
					&& equivalence.elementEquals(model.getElementAt(selModel.getMinSelectionIndex()), evt.getNewValue())) {
					if (update != null)
						update.accept(selModel.getMaxSelectionIndex());
					return;
				}
				for (int i = 0; i < model.getSize(); i++) {
					if (equivalence.elementEquals(model.getElementAt(i), evt.getNewValue())) {
						selModel.setSelectionInterval(i, i);
						Rectangle rowBounds = null;
						if (component instanceof JTable)
							rowBounds = ((JTable) component).getCellRect(((JTable) component).convertRowIndexToModel(i), 0, false);
						else if (component instanceof JList)
							rowBounds = ((JList<?>) component).getCellBounds(i, i);
						if (rowBounds != null)
							((JComponent) component).scrollRectToVisible(rowBounds);
						break;
					}
				}
			} finally {
				callbackLock[0] = false;
			}
		}));

		until.take(1).act(__ -> {
			if (component != null)
				component.removePropertyChangeListener("selectionModel", selModelListener);
			selectionModel.get().removeListSelectionListener(selListener);
			model.removeListDataListener(modelListener);
		});

		return selection;
	}

	/**
	 * @param clazz The class with which to get the resource, or null to use this class
	 * @param location The resource location of the image file
	 * @return The icon, or null if the resource could not be found
	 */
	public static ImageIcon getIcon(Class<?> clazz, String location) {
		URL searchUrl = (clazz != null ? clazz : ObservableSwingUtils.class).getResource(location);
		return searchUrl != null ? new ImageIcon(searchUrl) : null;
	}

	/**
	 * @param clazz The class with which to get the resource, or null to use this class
	 * @param location The resource location of the image file
	 * @param width The width for the icon
	 * @param height The height for the icon
	 * @return The icon, or null if the resource could not be found
	 */
	public static ImageIcon getFixedIcon(Class<?> clazz, String location, int width, int height) {
		ImageIcon icon = getIcon(clazz, location);
		if (icon != null && (icon.getIconWidth() != width || icon.getIconHeight() != height))
			icon = new ImageIcon(icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
		return icon;
	}

	/**
	 * Installs the system-specific Swing look and feel
	 *
	 * @return Whether the system-specific look and feel was successfully installed
	 */
	public static boolean systemLandF() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			return true;
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Initializes a frame's location and size from configuration (if present) and persists the frame's location to the config
	 *
	 * @param frame The frame whose location to configure
	 * @param config The configuration to persist to
	 * @return A subscription that will stop persisting the frame's location and size to the config
	 */
	public static Subscription configureFrameBounds(Frame frame, ObservableConfig config) {
		// TODO At some point, maybe dynamically listen to the configuration to control the frame
		if (config.get("x") != null) {
			try {
				frame.setBounds(config.getChild("x").asValue(int.class).parse(), //
					config.getChild("y").asValue(int.class).parse(), //
					config.getChild("width").asValue(int.class).parse(), //
					config.getChild("height").asValue(int.class).parse()//
					);
			} catch (ParseException e) {
				frame.pack();
				frame.setLocationRelativeTo(null);
			}
		} else
			frame.setLocationRelativeTo(null);
		ComponentListener listener = new ComponentListener() {
			@Override
			public void componentResized(ComponentEvent e) {
				config.set("width", String.valueOf(frame.getWidth()));
				config.set("height", String.valueOf(frame.getHeight()));
			}

			@Override
			public void componentMoved(ComponentEvent e) {
				config.set("x", String.valueOf(frame.getX()));
				config.set("y", String.valueOf(frame.getY()));
			}

			@Override
			public void componentShown(ComponentEvent e) {}

			@Override
			public void componentHidden(ComponentEvent e) {}
		};
		frame.addComponentListener(listener);
		return () -> frame.removeComponentListener(listener);
	}

	/**
	 * Sorts a list of element changes and returns the start and end of each continuous interval in the list
	 *
	 * @param elChanges The element changes to parse into intervals
	 * @param forward Whether to iterate from least to greatest or greatest to least
	 * @return The intervals, in the form of [start, end] arrays
	 */
	public static int[][] getContinuousIntervals(List<? extends CollectionChangeEvent.ElementChange<?>> elChanges, boolean forward) {
		int[] indexes = new int[elChanges.size()];
		for (int i = 0; i < indexes.length; i++)
			indexes[i] = elChanges.get(i).index;
		return getContinuousIntervals(indexes, forward);
	}

	/**
	 * Sorts a list of indexes and returns the start and end of each continuous interval in the list
	 *
	 * @param indexes The integers to parse into intervals
	 * @param forward Whether to iterate from least to greatest or greatest to least
	 * @return The intervals, in the form of [start, end] arrays
	 */
	public static int[][] getContinuousIntervals(int[] indexes, boolean forward) {
		int start, end;
		List<int[]> ret = new ArrayList<>();
		if (forward) {
			start = 0;
			end = 0;
			while (end < indexes.length) {
				while (end < indexes.length - 1 && indexes[end + 1] == indexes[end] + 1) {
					end++;
				}
				ret.add(new int[] { indexes[start], indexes[end] });
				start = end = end + 1;
			}
		} else {
			end = indexes.length - 1;
			start = end;
			while (start >= 0) {
				while (start > 0 && indexes[start - 1] == indexes[start] - 1) {
					start--;
				}
				ret.add(new int[] { indexes[start], indexes[end] });
				start = end = start - 1;
			}
		}
		int[][] retArray = new int[ret.size()][];
		for (int i = 0; i < retArray.length; i++) {
			retArray[i] = ret.get(i);
		}
		return retArray;
	}

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
	 * @param container The container to add the field widgets to. If null, a new {@link JPanel} will be created (be sure
	 *        net.miginfocom.swing.MigLayout is in your classpath to use this).
	 * @param until The observable that, when fired, will release all associated resources
	 * @return The API structure to add fields with
	 */
	public static <C extends Container> PanelPopulation.PanelPopulator<C, ?> populateFields(C container, Observable<?> until) {
		return PanelPopulation.populateVPanel(container, until);
	}
}

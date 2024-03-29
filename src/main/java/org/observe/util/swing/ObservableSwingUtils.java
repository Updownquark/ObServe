package org.observe.util.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionListener;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.TriFunction;
import org.qommons.io.TextParseException;

import com.google.common.reflect.TypeToken;

/** Utilities for the org.observe.util.swing package */
public class ObservableSwingUtils {
	/**
	 * Executes a task on the AWT/Swing event thread. If this thread *is* the event thread, the task is executed inline
	 *
	 * @param task The task to execute on the AWT {@link EventQueue}
	 */
	public static void onEQ(Runnable task) {
		ThreadConstraint.EDT.invoke(task);
	}

	private static final Runnable FLUSH_TASK = () -> {
	};

	/** If the current thread is the Event Dispatch Thread, forces the task cache to be flushed */
	public static void flushEQCache() {
		ThreadConstraint.EDT.invoke(FLUSH_TASK);
	}

	/** @return The {@link FontAdjuster} to use to configure the label */
	public static FontAdjuster label() {
		return new FontAdjuster();
	}

	/**
	 * Links up a check box's {@link JCheckBox#isSelected() selected} state to a settable boolean, such that the user's interaction with the
	 * check box is reported by the value, and setting the value alters the check box.
	 *
	 * @param checkBox The toggle button or check box to control observably
	 * @param descrip The description for the check box's tool tip when it is enabled
	 * @param selected The settable, observable boolean to control the check box's selection
	 * @return The subscription to {@link Subscription#unsubscribe() unsubscribe} to terminate the link
	 */
	public static Subscription checkFor(JToggleButton checkBox, String descrip, SettableValue<Boolean> selected) {
		return checkFor(checkBox, ObservableValue.of(TypeTokens.get().STRING, descrip), selected);
	}

	/**
	 * Links up a check box's {@link JCheckBox#isSelected() selected} state to a settable boolean, such that the user's interaction with the
	 * check box is reported by the value, and setting the value alters the check box.
	 *
	 * @param checkBox The toggle button or check box to control observably
	 * @param descrip The description for the check box's tool tip when it is enabled
	 * @param selected The settable, observable boolean to control the check box's selection
	 * @return The subscription to {@link Subscription#unsubscribe() unsubscribe} to terminate the link
	 */
	public static Subscription checkFor(JToggleButton checkBox, ObservableValue<String> descrip, SettableValue<Boolean> selected) {
		SimpleObservable<Void> until = SimpleObservable.build().build();
		ObservableValue<String> safeDescrip = descrip.safe(ThreadConstraint.EDT, until);
		SettableValue<Boolean> safeSelected = selected.safe(ThreadConstraint.EDT, until);

		String[] enDesc = new String[2];
		Runnable checkEnabled = () -> {
			if (enDesc[0] != null) {
				if (checkBox.isEnabled())
					checkBox.setEnabled(false);
				checkBox.setToolTipText(enDesc[0]);
			} else {
				String acceptable = safeSelected.isAcceptable(!checkBox.isSelected());
				if (acceptable != null) {
					if (checkBox.isEnabled())
						checkBox.setEnabled(false);
					checkBox.setToolTipText(acceptable);
				} else {
					if (!checkBox.isEnabled())
						checkBox.setEnabled(true);
					checkBox.setToolTipText(enDesc[1]);
				}
			}
		};
		ActionListener action = evt -> {
			safeSelected.set(checkBox.isSelected(), evt);
			checkEnabled.run();
		};
		checkBox.addActionListener(action);
		Subscription enabledSub = safeSelected.isEnabled().changes().act(evt -> {
			enDesc[0] = evt.getNewValue();
			checkEnabled.run();
		});
		Subscription descripSub = safeDescrip.changes().act(evt -> {
			enDesc[1] = evt.getNewValue();
			checkEnabled.run();
		});
		Subscription selectedSub = safeSelected.changes().act(evt -> {
			checkBox.setSelected(evt.getNewValue() != null && evt.getNewValue());
			checkEnabled.run();
		});
		return () -> {
			selectedSub.unsubscribe();
			descripSub.unsubscribe();
			enabledSub.unsubscribe();
			checkBox.removeActionListener(action);
			until.onNext(null);
		};
	}

	/**
	 * Links up a check box menu item's {@link JCheckBoxMenuItem#isSelected() selected} state to a settable boolean, such that the user's
	 * interaction with the check box is reported by the value, and setting the value alters the check box.
	 *
	 * @param checkBox The menu item to control observably
	 * @param descrip The description for the check box's tool tip when it is enabled
	 * @param selected The settable, observable boolean to control the check box's selection
	 * @return The subscription to {@link Subscription#unsubscribe() unsubscribe} to terminate the link
	 */
	public static Subscription checkFor(JCheckBoxMenuItem checkBox, ObservableValue<String> descrip, SettableValue<Boolean> selected) {
		SimpleObservable<Void> until = SimpleObservable.build().build();
		ObservableValue<String> safeDescrip = descrip.safe(ThreadConstraint.EDT, until);
		SettableValue<Boolean> safeSelected = selected.safe(ThreadConstraint.EDT, until);

		String[] enDesc = new String[2];
		Runnable checkEnabled = () -> {
			if (enDesc[0] != null) {
				if (checkBox.isEnabled())
					checkBox.setEnabled(false);
				checkBox.setToolTipText(enDesc[0]);
			} else {
				String acceptable = safeSelected.isAcceptable(!checkBox.isSelected());
				if (acceptable != null) {
					if (checkBox.isEnabled())
						checkBox.setEnabled(false);
					checkBox.setToolTipText(acceptable);
				} else {
					if (!checkBox.isEnabled())
						checkBox.setEnabled(true);
					checkBox.setToolTipText(enDesc[1]);
				}
			}
		};
		ActionListener action = evt -> {
			safeSelected.set(checkBox.isSelected(), evt);
			checkEnabled.run();
		};
		checkBox.addActionListener(action);
		Subscription enabledSub = safeSelected.isEnabled().changes().act(evt -> {
			enDesc[0] = evt.getNewValue();
			checkEnabled.run();
		});
		Subscription descripSub = safeDescrip.changes().act(evt -> {
			enDesc[1] = evt.getNewValue();
			checkEnabled.run();
		});
		Subscription selectedSub = safeSelected.changes().act(evt -> {
			checkBox.setSelected(evt.getNewValue() != null && evt.getNewValue());
			checkEnabled.run();
		});
		return () -> {
			selectedSub.unsubscribe();
			descripSub.unsubscribe();
			enabledSub.unsubscribe();
			checkBox.removeActionListener(action);
			until.onNext(null);
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
		SimpleObservable<Void> until = SimpleObservable.build().build();
		SettableValue<T> safeSelected = selected.safe(ThreadConstraint.EDT, until);
		ActionListener[] actions = new ActionListener[buttons.length];
		for (int i = 0; i < buttons.length; i++) {
			int index = i;
			actions[i] = evt -> {
				safeSelected.set(options[index], evt);
			};
			buttons[i].addActionListener(actions[i]);
		}
		Consumer<String> checkEnabled = enabled -> {
			for (int i = 0; i < buttons.length; i++) {
				String enabled_i = enabled;
				if (enabled_i == null) {
					enabled_i = safeSelected.isAcceptable(options[i]);
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
		Subscription selectedSub = safeSelected.changes().act(evt -> {
			T value = evt.getNewValue();
			for (int i = 0; i < options.length; i++) {
				buttons[i].setSelected(Objects.equals(options[i], value));
			}
			checkEnabled.accept(safeSelected.isEnabled().get());
		});
		Subscription enabledSub = safeSelected.isEnabled().changes().act(evt -> checkEnabled.accept(evt.getNewValue()));
		return () -> {
			selectedSub.unsubscribe();
			enabledSub.unsubscribe();
			for (int i = 0; i < buttons.length; i++)
				buttons[i].removeActionListener(actions[i]);
			until.onNext(null);
		};
	}

	/**
	 * @param availableValues The set of values to represent, each with its own button
	 * @param selected The selected value
	 * @param buttonType The type of the buttons to use to represent each value
	 * @param buttonCreator Creates a button for initial or new values
	 * @param withButtons Accepts the observable collection of buttons created by this method
	 * @param render Allows initialization and modification of the buttons with their values
	 * @param descrip Provides a tooltip for each value button
	 * @return A subscription that stops all the listening that this method initializes
	 */
	public static <T, TB extends JToggleButton> Subscription togglesFor(ObservableCollection<? extends T> availableValues,
		SettableValue<T> selected, //
		TypeToken<TB> buttonType, Function<? super T, ? extends TB> buttonCreator, Consumer<ObservableCollection<TB>> withButtons,
		BiConsumer<? super TB, ? super T> render, Function<? super T, String> descrip) {
		List<Subscription> subs = new LinkedList<>();
		ObservableCollection<? extends T> safeValues;
		SimpleObservable<Void> safeUntil = SimpleObservable.build().build();
		subs.add(() -> safeUntil.onNext(null));
		SettableValue<T> safeSelected = selected.safe(ThreadConstraint.EDT, safeUntil);
		safeValues = availableValues.safe(ThreadConstraint.EDT, safeUntil);
		ObservableCollection<TB> buttons = safeValues.flow().transform(buttonType, tx -> tx.map((value, button) -> {
			if (button == null)
				button = buttonCreator.apply(value);
			render.accept(button, value);
			String enabled = safeSelected.isEnabled().get();
			if (enabled != null)
				button.setToolTipText(enabled);
			else if (descrip != null)
				button.setToolTipText(descrip.apply(value));
			else
				button.setToolTipText(null);
			return button;
		})).collectActive(safeUntil);
		withButtons.accept(buttons);

		int[] currentSelection = new int[] { -1 };
		Consumer<String> checkEnabled = enabled -> {
			for (int i = 0; i < buttons.size(); i++) {
				String bEnabled = enabled;
				if (bEnabled == null)
					bEnabled = selected.isAcceptable(availableValues.get(i));
				TB button = buttons.get(i);
				button.setEnabled(bEnabled == null);
				if (bEnabled != null)
					button.setToolTipText(bEnabled);
				else if (descrip != null)
					button.setToolTipText(descrip.apply(safeValues.get(i)));
				else
					button.setToolTipText(null);
			}
		};
		TriFunction<T, Integer, Object, Boolean>[] listener = new TriFunction[1];
		subs.add(ObservableComboBoxModel.<T> hookUpComboData(safeValues, safeSelected, index -> {
			if (index >= 0)
				buttons.get(index).setSelected(true);
			else if (currentSelection[0] >= 0)
				buttons.get(currentSelection[0]).setSelected(false);
			currentSelection[0] = index;
		}, lstnr -> {
			listener[0] = lstnr;
			return () -> listener[0] = null;
		}, checkEnabled));
		ButtonGroup group = new ButtonGroup();
		ActionListener selectListener = evt -> {
			Object button = evt.getSource();
			if (!TypeTokens.get().isInstance(buttonType, button))
				return;
			int index = buttons.indexOf(button);
			if (index >= 0 && listener[0] != null)
				listener[0].apply(safeValues.get(index), index, evt);
		};
		for (TB button : buttons) {
			group.add(button);
			button.addActionListener(selectListener);
		}
		subs.add(buttons.changes().act(evt -> {
			if (listener[0] == null)
				return;
			switch (evt.type) {
			case add:
				for (TB button : evt.getValues()) {
					group.add(button);
					button.addActionListener(selectListener);
				}
				break;
			case remove:
				for (TB button : evt.getValues()) {
					group.remove(button);
					button.removeActionListener(selectListener);
				}
				break;
			case set:
				break;
			}
		}));
		return Subscription.forAll(subs);
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
		SimpleObservable<Void> until = SimpleObservable.build().build();
		SettableValue<T> safeValue = value.safe(ThreadConstraint.EDT, until);
		boolean[] callbackLock = new boolean[1];
		ChangeListener changeListener = evt -> {
			if (!callbackLock[0]) {
				callbackLock[0] = true;
				try {
					T newValue = purify.apply((T) spinner.getValue());
					String accept = safeValue.isAcceptable(newValue);
					if (accept != null) {
						JOptionPane.showMessageDialog(spinner.getParent(), accept, "Unacceptable Value", JOptionPane.ERROR_MESSAGE);
						spinner.setValue(safeValue.get());
					} else {
						safeValue.set(newValue, evt);
					}
				} finally {
					callbackLock[0] = false;
				}
			}
		};
		spinner.addChangeListener(changeListener);

		Subscription valueSub = safeValue.changes().act(evt -> {
			if (!callbackLock[0]) {
				callbackLock[0] = true;
				try {
					T newValue = purify == null ? evt.getNewValue() : purify.apply(evt.getNewValue());
					spinner.setValue(newValue);
				} finally {
					callbackLock[0] = false;
				}
			}
		});
		Subscription enabledSub = safeValue.isEnabled().changes().act(evt -> {
			String enabled = evt.getNewValue();
			spinner.setEnabled(enabled == null);
			spinner.setToolTipText(enabled != null ? enabled : descrip);
		});
		return () -> {
			valueSub.unsubscribe();
			enabledSub.unsubscribe();
			spinner.removeChangeListener(changeListener);
			until.onNext(null);
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
		SimpleObservable<Void> until = SimpleObservable.build().build();
		SettableValue<Integer> safeValue = value.safe(ThreadConstraint.EDT, until);
		boolean[] callbackLock = new boolean[1];
		ChangeListener changeListener = evt -> {
			if (!callbackLock[0] && !slider.getValueIsAdjusting()) {
				callbackLock[0] = true;
				try {
					int newValue = slider.getValue();
					String accept = safeValue.isAcceptable(newValue);
					if (accept != null) {
						JOptionPane.showMessageDialog(slider.getParent(), accept, "Unacceptable Value", JOptionPane.ERROR_MESSAGE);
						slider.setValue(safeValue.get());
					} else {
						safeValue.set(newValue, evt);
					}
				} finally {
					callbackLock[0] = false;
				}
			}
		};
		slider.addChangeListener(changeListener);
		Subscription valueSub = safeValue.changes().act(evt -> {
			if (!callbackLock[0]) {
				callbackLock[0] = true;
				try {
					slider.setValue(evt.getNewValue());
				} finally {
					callbackLock[0] = false;
				}
			}
		});
		Subscription enabledSub = safeValue.isEnabled().changes().act(evt -> {
			String enabled = evt.getNewValue();
			slider.setEnabled(enabled == null);
			slider.setToolTipText(enabled != null ? enabled : descrip);
		});
		return () -> {
			valueSub.unsubscribe();
			enabledSub.unsubscribe();
			slider.removeChangeListener(changeListener);
			until.onNext(null);
		};
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
			if (selectionModel.isSelectedIndex(i) && i < model.getSize())
				selValues.add(map == null ? (V) model.getElementAt(i) : map.apply(model.getElementAt(i)));
		}
		return selValues;
	}

	/** Updates the list model at a particular index */
	public interface ModelUpdater {
		/**
		 * @param index The index in the model to update
		 * @param cause The cause to use
		 */
		void update(int index, Object cause);
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
		Equivalence<? super E> equivalence, SettableValue<E> selection, Observable<?> until, ModelUpdater update,
		boolean enforceSingleSelection) {
		SettableValue<E> safeSelection = selection.safe(ThreadConstraint.EDT, until);
		if (enforceSingleSelection)
			selectionModel.get().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		boolean[] callbackLock = new boolean[1];
		ListSelectionListener selListener = e -> {
			ListSelectionModel selModel = selectionModel.get();
			if (selModel.getValueIsAdjusting() || callbackLock[0])
				return;
			flushEQCache();
			callbackLock[0] = true;
			try {
				if (selModel.getMinSelectionIndex() >= 0 && selModel.getMinSelectionIndex() == selModel.getMaxSelectionIndex()
					&& selModel.getMinSelectionIndex() < model.getSize()) {
					E selectedValue = model.getElementAt(selModel.getMinSelectionIndex());
					if (safeSelection.isAcceptable(selectedValue) == null)
						safeSelection.set(selectedValue, e);
				} else if (safeSelection.get() != null)
					safeSelection.set(null, e);
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
				flushEQCache();
				callbackLock[0] = true;
				try {
					if (e.getIndex0() <= selModel.getMinSelectionIndex() && e.getIndex1() >= selModel.getMinSelectionIndex()) {
						Transaction t = selection.tryLock(true, e);
						if (t != null) {
							try {
								E newSelection = model.getElementAt(selModel.getMinSelectionIndex());
								if (selection.isAcceptable(newSelection) == null)
									selection.set(newSelection, e);
							} finally {
								t.close();
							}
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
		safeSelection.changes().takeUntil(until).act(evt -> {
			if (callbackLock[0] || (model instanceof ObservableListModel && ((ObservableListModel<?>) model).getWrapped().isEventing()))
				return;
			flushEQCache();
			callbackLock[0] = true;
			try {
				ListSelectionModel selModel = selectionModel.get();
				if (evt.getNewValue() == null) {
					selModel.clearSelection();
					return;
				} else if (evt.getOldValue() == evt.getNewValue()//
					&& !selModel.isSelectionEmpty() && selModel.getMinSelectionIndex() == selModel.getMaxSelectionIndex()//
					&& equivalence.elementEquals(model.getElementAt(selModel.getMinSelectionIndex()), evt.getNewValue())) {
					if (update != null) {
						Causable cause = Causable.simpleCause(Causable.broken(evt));
						Transaction t2 = null;
						try (Transaction t = cause.use()) {
							t2 = model instanceof ObservableListModel ? ((ObservableListModel<?>) model).getWrapped().tryLock(true, cause)
								: Transaction.NONE;
							if (t2 != null)
								update.update(selModel.getMaxSelectionIndex(), cause);
						} finally {
							if (t2 != null)
								t2.close();
						}
					}
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
		});

		until.take(1).act(__ -> {
			if (component != null)
				component.removePropertyChangeListener("selectionModel", selModelListener);
			selectionModel.get().removeListSelectionListener(selListener);
			model.removeListDataListener(modelListener);
		});

		return selection;
	}

	private static class IconKey {
		final Class<?> clazz;
		final String location;
		// final int width;
		// final int height;

		public IconKey(Class<?> clazz, String location, int width, int height) {
			this.clazz = clazz;
			this.location = location;
			// this.width = width;
			// this.height = height;
		}

		@Override
		public int hashCode() {
			return Objects.hash(clazz, location);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof IconKey))
				return false;
			IconKey other = (IconKey) obj;
			return Objects.equals(clazz, other.clazz) && location.equals(other.location);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			if (clazz != null)
				str.append(clazz.getName()).append(':');
			str.append(location);
			return str.toString();
		}
	}
	private static final Map<IconKey, WeakReference<ImageIcon>> CACHED_ICONS = new ConcurrentHashMap<>();

	/**
	 * @param clazz The class with which to get the resource, or null to use this class
	 * @param location The resource location of the image file
	 * @return The icon, or null if the resource could not be found
	 */
	public static ImageIcon getIcon(Class<?> clazz, String location) {
		IconKey key = new IconKey(clazz, location, 0, 0);
		WeakReference<ImageIcon> iconRef = CACHED_ICONS.get(key);
		ImageIcon icon = iconRef == null ? null : iconRef.get();
		if (icon == null) {
			URL searchUrl = (clazz != null ? clazz : ObservableSwingUtils.class).getResource(location);
			if (searchUrl == null) {
				String otherLocation;
				if (location.startsWith("/"))
					otherLocation = location.substring(1);
				else
					otherLocation = "/" + location;
				searchUrl = (clazz != null ? clazz : ObservableSwingUtils.class).getResource(otherLocation);
			}
			if (searchUrl == null && clazz != null) {
				searchUrl = ObservableSwingUtils.class.getResource(location);
				if (searchUrl == null) {
					String otherLocation;
					if (location.startsWith("/"))
						otherLocation = location.substring(1);
					else
						otherLocation = "/" + location;
					searchUrl = ObservableSwingUtils.class.getResource(otherLocation);
				}
			}
			if (searchUrl == null) {
				try {
					if (location.startsWith("file://"))
						searchUrl = new File(location.substring("file://".length())).toURI().toURL();
					else
						searchUrl = new URL(location);
				} catch (MalformedURLException e) {}
			}
			icon = searchUrl != null ? new ImageIcon(searchUrl) : null;
			iconRef = new WeakReference<>(icon);
			CACHED_ICONS.put(key, iconRef);
		}
		return icon;
	}

	/**
	 * @param clazz The class with which to get the resource, or null to use this class
	 * @param location The resource location of the image file
	 * @param width The width for the icon
	 * @param height The height for the icon
	 * @return The icon, or null if the resource could not be found
	 */
	public static ImageIcon getFixedIcon(Class<?> clazz, String location, int width, int height) {
		IconKey key = new IconKey(clazz, location, width, height);
		WeakReference<ImageIcon> iconRef = CACHED_ICONS.get(key);
		ImageIcon icon = iconRef == null ? null : iconRef.get();
		if (icon == null) {
			icon = getIcon(clazz, location);
			if (icon != null && (icon.getIconWidth() != width || icon.getIconHeight() != height)) {
				icon = new ImageIcon(icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
				CACHED_ICONS.put(key, new WeakReference<>(icon));
			}
		}
		return icon;
	}

	/**
	 * @param component The component
	 * @param visible Whether the currently set tooltip should be shown to the user
	 */
	public static void setTooltipVisible(Component component, boolean visible) {
		// Super hacky, but not sure how else to do this. Swing's tooltip system doesn't have many hooks into it.
		// Overall, this approach may be somewhat flawed, but it's about the best I can do,
		// the potential negative consequences are small, and I think it's a very good feature
		Point mousePos = component.getMousePosition();
		if (visible) {
			MouseEvent me = new MouseEvent(component, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, //
				mousePos == null ? 0 : mousePos.x, mousePos == null ? 0 : mousePos.y, 0, false);
			component.dispatchEvent(me);
		} else if (mousePos == null) { // If the mouse isn't over the component, it can't be displaying a tooltip, right?
			int prevDelay = ToolTipManager.sharedInstance().getDismissDelay();
			ToolTipManager.sharedInstance().setDismissDelay(1);
			ToolTipManager.sharedInstance().setDismissDelay(prevDelay);
		}
	}

	/** @return A builder for an observable-backed application */
	public static AppPopulation.ObservableUiBuilder buildUI() {
		return new AppPopulation.ObservableUiBuilder();
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
	 * Starts an application configured by an ObservableConfig
	 *
	 * @param configName The name of the application
	 * @param defaultConfigLocation The default location of the config file
	 * @param app Builds a component from the ObservableConfig
	 * @return The application frame
	 */
	public static JFrame startApplication(String configName, String defaultConfigLocation, Function<ObservableConfig, Component> app) {
		String configFileLoc = System.getProperty(configName + ".config");
		if (configFileLoc == null) {
			configFileLoc = defaultConfigLocation;
		}
		ObservableConfig config = ObservableConfig.createRoot("configName", ThreadConstraint.EDT);
		ObservableConfig.XmlEncoding encoding = ObservableConfig.XmlEncoding.DEFAULT;
		File configFile = new File(configFileLoc);
		if (configFile.exists()) {
			try {
				try (InputStream configStream = new BufferedInputStream(new FileInputStream(configFile))) {
					ObservableConfig.readXml(config, configStream, encoding);
				}
			} catch (IOException | TextParseException e) {
				System.err.println("Could not read config file " + configFileLoc);
				e.printStackTrace();
			}
		}
		config.persistOnShutdown(ObservableConfig.toFile(configFile, encoding), ex -> {
			System.err.println("Could not persist UI config");
			ex.printStackTrace();
		});
		ObservableSwingUtils.systemLandF();
		Component ui = app.apply(config);
		JFrame frame = new JFrame();
		// frame.setContentPane(ui);
		frame.getContentPane().add(ui);
		frame.setVisible(true);
		frame.pack();
		ObservableSwingUtils.configureWindowBounds(frame, config);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		return frame;
	}

	/**
	 * Initializes a window's location and size from configuration (if present) and persists the window's location to the config when it is
	 * changed
	 *
	 * @param window The window whose location to configure
	 * @param config The configuration to persist to
	 * @return A subscription that will stop persisting the window's location and size to the config
	 */
	public static Subscription configureWindowBounds(Window window, ObservableConfig config) {
		if (config.get("x") != null) {
			try {
				int x = config.getChild("x").asValue(int.class).parse(null);
				int y = config.getChild("y").asValue(int.class).parse(null);
				int w = config.getChild("width").asValue(int.class).parse(null);
				int h = config.getChild("height").asValue(int.class).parse(null);
				window.setBounds(fitBoundsToGraphicsEnv(x, y, w, h, getGraphicsBounds()));
			} catch (ParseException e) {
				window.pack();
				window.setLocationRelativeTo(null);
			}
		} else
			window.setLocationRelativeTo(null);
		ComponentListener listener = new ComponentListener() {
			@Override
			public void componentResized(ComponentEvent e) {
				config.set("width", String.valueOf(window.getWidth()));
				config.set("height", String.valueOf(window.getHeight()));
			}

			@Override
			public void componentMoved(ComponentEvent e) {
				config.set("x", String.valueOf(window.getX()));
				config.set("y", String.valueOf(window.getY()));
			}

			@Override
			public void componentShown(ComponentEvent e) {}

			@Override
			public void componentHidden(ComponentEvent e) {}
		};
		window.addComponentListener(listener);
		return () -> window.removeComponentListener(listener);
	}

	/**
	 * Fits a configured bounds window to a graphics environment. If the bounds are offscreen in the graphics environment, the window will
	 * be moved and/or resized a position that best accommodates its dimensions while retaining its location as closely as possible.
	 *
	 * @param x The leftmost X-component for the window's bounds
	 * @param y The topmost Y-component for the window's bounds
	 * @param width The width for the window
	 * @param height The height for the window
	 * @param graphicsBounds The bounds of the displays in the graphics environment
	 * @return The fitted bounds
	 */
	public static Rectangle fitBoundsToGraphicsEnv(int x, int y, int width, int height, List<Rectangle> graphicsBounds) {
		Point bestTopLeft = null;
		Point bestBottomRight = null;
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		// If the system has any duplicate configurations (e.g. broadcasting a screen to a projector or something),
		// we need to get exclusive bounds so we don't count any overlap between the environment and the configuration twice.
		List<Rectangle> exclBounds = getExclusiveBounds(graphicsBounds);
		for (Rectangle devB : exclBounds) {
			if (devB.x < minX)
				minX = devB.x;
			if (devB.x + devB.width > maxX)
				maxX = devB.x + devB.width;
			if (devB.y < minY)
				minY = devB.y;
			if (devB.y + devB.height > maxY)
				maxY = devB.y + devB.height;

			Point closest;
			closest = getClosest(devB, x, y);
			if (bestTopLeft == null
				|| (Math.abs(x - closest.x) + Math.abs(y - closest.y)) < (Math.abs(x - bestTopLeft.x) + Math.abs(y - bestTopLeft.y)))
				bestTopLeft = closest;

			closest = getClosest(devB, x + width, y + height);
			if (bestBottomRight == null || (Math.abs(x + width - closest.x)
				+ Math.abs(y + height - closest.y)) < (Math.abs(x + width - bestBottomRight.x) + Math.abs(y + height - bestBottomRight.y)))
				bestBottomRight = closest;
		}
		if (bestTopLeft.x == x && bestTopLeft.y == y//
			&& bestBottomRight.x - bestTopLeft.x == width && bestBottomRight.y - bestTopLeft.y == height)
			return new Rectangle(x, y, width, height); // The graphics environment accommodates the bounds as-is
		// We'll try fitting each corner in turn and see which corner is best
		Point bestOtherCorner;
		Point adjustedBTL, adjustedBBR;
		long bestArea;
		int bestDist;
		{ // Top-left
			if (bestTopLeft.x == x && bestTopLeft.y == y)
				bestOtherCorner = bestBottomRight;
			else
				bestOtherCorner = getClosest(exclBounds, bestTopLeft.x + width, bestTopLeft.y + height);
			long area = getIntersectArea(exclBounds, bestTopLeft.x, bestTopLeft.y, bestOtherCorner.x, bestOtherCorner.y);
			bestArea = area;
			bestDist = Math.abs(x - bestTopLeft.x) + Math.abs(y - bestTopLeft.y);
			adjustedBTL = bestTopLeft;
			adjustedBBR = bestOtherCorner;
		}
		{ // Top-right
			if (bestBottomRight.x == x + width && bestTopLeft.y == y)
				bestOtherCorner = new Point(bestTopLeft.x, bestBottomRight.y);
			else
				bestOtherCorner = getClosest(exclBounds, bestBottomRight.x - width, bestTopLeft.y + height);
			long area = getIntersectArea(exclBounds, bestOtherCorner.x, bestTopLeft.y, bestBottomRight.x, bestOtherCorner.y);
			int dist = Math.abs(x + width - bestBottomRight.x) + Math.abs(y - bestTopLeft.y);
			if (area > bestArea || (area == bestArea && dist < bestDist)) {
				bestArea = area;
				bestDist = dist;
				adjustedBTL = new Point(bestOtherCorner.x, bestTopLeft.y);
				adjustedBBR = new Point(bestBottomRight.x, bestOtherCorner.y);
			}
		}
		{ // Bottom-left
			if (bestTopLeft.x == x && bestBottomRight.y == y + height)
				bestOtherCorner = new Point(bestBottomRight.x, bestTopLeft.y);
			else
				bestOtherCorner = getClosest(exclBounds, bestTopLeft.x + width, bestBottomRight.y - height);
			long area = getIntersectArea(exclBounds, bestTopLeft.x, bestOtherCorner.y, bestOtherCorner.x, bestBottomRight.y);
			int dist = Math.abs(x - bestTopLeft.x) + Math.abs(y + height - bestBottomRight.y);
			if (area > bestArea || (area == bestArea && dist < bestDist)) {
				bestArea = area;
				bestDist = dist;
				adjustedBTL = new Point(bestTopLeft.x, bestOtherCorner.y);
				adjustedBBR = new Point(bestOtherCorner.x, bestBottomRight.y);
			}
		}
		{ // Bottom-right
			if (bestBottomRight.x == x + width && bestBottomRight.y == y + height)
				bestOtherCorner = bestTopLeft;
			else
				bestOtherCorner = getClosest(exclBounds, bestBottomRight.x - width, bestBottomRight.y - height);
			long area = getIntersectArea(exclBounds, bestOtherCorner.x, bestOtherCorner.y, bestBottomRight.x, bestBottomRight.y);
			int dist = Math.abs(x + width - bestBottomRight.x) + Math.abs(y + height - bestBottomRight.y);
			if (area > bestArea || (area == bestArea && dist < bestDist)) {
				bestArea = area;
				bestDist = dist;
				adjustedBTL = bestOtherCorner;
				adjustedBBR = bestBottomRight;
			}
		}
		return new Rectangle(adjustedBTL.x, adjustedBTL.y, adjustedBBR.x - adjustedBTL.x, adjustedBBR.y - adjustedBTL.y);
	}

	/** @return The bounds for all graphics devices on the system */
	public static List<Rectangle> getGraphicsBounds() {
		List<Rectangle> bounds = new ArrayList<>();
		for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
			for (GraphicsConfiguration config : device.getConfigurations()) {
				bounds.add(config.getBounds());
			}
		}
		return bounds;
	}

	/**
	 * @param bounds The bounds for all graphics devices on the system
	 * @return The same bounds, but with no overlaps
	 */
	public static List<Rectangle> getExclusiveBounds(List<Rectangle> bounds) {
		List<Rectangle> bounds2 = new ArrayList<>();
		List<Rectangle> bounds3 = new ArrayList<>(bounds);
		boolean modified;
		do {
			modified = false;
			bounds2.clear();
			List<Rectangle> temp = bounds3;
			bounds3 = bounds2;
			bounds2 = temp;
			for (Rectangle r : bounds2) {
				int right = r.x + r.width;
				int bottom = r.y + r.height;
				if (bounds3.isEmpty()) {
					bounds3.add(r);
					continue;
				}
				for (Rectangle b : bounds3) {
					Rectangle iSect = r.intersection(b);
					if (iSect.isEmpty()) {
						// r does not overlap b
						// See if the rectangles are adjacent along a common dimension so we can combine them
						if (r.x == b.x && r.width == b.width) {
							if (bottom == b.y) {// Rectangles are adjacent, r on top of b
								modified = true;
								bounds3.remove(b);
								bounds3.add(new Rectangle(r.x, r.y, r.width, r.height + b.height));
							} else if (r.y == b.y + b.height) {// Rectangles are adjacent, b on top of r
								modified = true;
								bounds3.remove(b);
								bounds3.add(new Rectangle(b.x, b.y, r.width, b.height + r.height));
							} else// Rectangles are disjoint
								bounds3.add(r);
						} else if (r.y == b.y && r.height == b.height) {
							if (right == b.x) { // Rectangles are adjacent, r to the left of b
								modified = true;
								bounds3.remove(b);
								bounds3.add(new Rectangle(r.x, r.y, r.width + b.width, r.height));
							} else if (r.x == b.x + b.width) {// Rectangles are adjacent, b to the left of r
								modified = true;
								bounds3.remove(b);
								bounds3.add(new Rectangle(b.x, b.y, b.width + r.width, r.height));
							} else// Rectangles are disjoint
								bounds3.add(r);
						} else
							bounds3.add(r); // Rectangles are disjoint and can't be combined
						break;
					}
					modified = true;
					int iRight = iSect.x + iSect.width;
					int iBottom = iSect.y + iSect.height;
					if (iSect.x > r.x) {
						if (iSect.y > r.y) {
							if (iRight < right) {
								if (iBottom < bottom) {
									// b is equal to or contained by r
									bounds3.remove(b);
								} else {
									// Need to cut a rectangle out of the middle of the bottom
									bounds3.add(new Rectangle(r.x, r.y, iSect.x - r.x, r.height)); // Left side
									bounds3.add(new Rectangle(iSect.x, r.y, iSect.width, iSect.y - r.y)); // Middle of the top
									bounds3.add(new Rectangle(iRight, r.y, right - iRight, r.height));// Right side
								}
							} else if (iSect.y - r.y + iSect.height < r.height) {
								// Need to cut a rectangle out of the middle of the right side
								bounds3.add(new Rectangle(r.x, r.y, r.width, iSect.y - r.y));// Top
								bounds3.add(new Rectangle(r.x, iSect.y, iSect.x - r.x, iSect.height)); // Middle of the left side
								bounds3.add(new Rectangle(r.x, iBottom, r.width, bottom - iBottom)); // Bottom
							} else {
								// Need to cut a rectangle out of the bottom-right corner
								bounds3.add(new Rectangle(r.x, r.y, iSect.x - r.x, r.height)); // Left side
								bounds3.add(new Rectangle(iSect.x, r.y, iSect.width, r.height - iSect.height));// Top-right corner
							}
						} else if (iSect.x - r.x + iSect.width < r.width) {
							if (iSect.height < r.height) {
								// Need to cut a rectangle out of the middle of the top side
								bounds3.add(new Rectangle(r.x, r.y, iSect.x - r.x, r.height)); // Left side
								bounds3.add(new Rectangle(iSect.x, iBottom, iSect.width, r.height - iSect.height));// Middle of the bottom
								bounds3.add(new Rectangle(iRight, r.y, right - iRight, r.height));// Right side
							} else {
								// Need to cut the horizontal middle out
								bounds3.add(new Rectangle(r.x, r.y, iSect.x - r.x, r.height)); // Left side
								bounds3.add(new Rectangle(iSect.x + iSect.width, r.y, right - iRight, r.height));// Right side
							}
						} else if (iSect.height < r.height) {
							// Need to cut a rectangle out of the top-right corner
							bounds3.add(new Rectangle(r.x, r.y, r.width - iSect.width, r.height)); // Left side
							bounds3.add(new Rectangle(iSect.x, iRight, iSect.width, r.height - iSect.height));// Bottom-right corner
						} else {
							// Need to cut the right side off
							bounds3.add(new Rectangle(r.x, r.y, r.width - iSect.width, r.height)); // Left side
						}
					} else if (iSect.y > r.y) {
						if (iSect.width < r.width) {
							if (iSect.height < r.height) {
								// Need to cut a rectangle out of the middle of the left side
								bounds3.add(new Rectangle(r.x, r.y, r.width, iSect.y - r.y)); // Top
								bounds3.add(new Rectangle(iRight, iSect.y, right - iRight, iSect.height));// Middle of the right side
								bounds3.add(new Rectangle(r.x, iBottom, r.width, bottom - iBottom));// Bottom
							} else {
								// Need to cut a rectangle out of the bottom-left corner
								bounds3.add(new Rectangle(r.x, r.y, iSect.width, r.height - iSect.height)); // Top left corner
								bounds3.add(new Rectangle(iRight, r.y, right - iRight, r.height)); // Right side
							}
						} else if (iSect.height < r.height) {
							// Need to cut the vertical middle out
							bounds3.add(new Rectangle(r.x, r.y, r.width, iSect.y - r.y)); // Top
							bounds3.add(new Rectangle(r.x, iBottom, r.width, bottom - iBottom)); // Bottom
						} else {
							// Need to cut the bottom off
							bounds3.add(new Rectangle(r.x, r.y, r.width, iSect.y - r.y)); // Top
						}
					} else if (iSect.width < r.width) {
						if (iSect.height < r.height) {
							// Need to cut a rectangle out of the top-left corner
							bounds3.add(new Rectangle(iRight, r.y, right - iRight, iSect.height)); // Top right corner
							bounds3.add(new Rectangle(r.x, iBottom, r.width, bottom - iBottom)); // Bottom
						} else {
							// Need to cut the left side off
							bounds3.add(new Rectangle(iRight, r.y, right - iRight, r.height)); // Right side
						}
					} else if (iSect.height < r.height) {
						// Need to cut the top off
						bounds3.add(new Rectangle(r.x, iBottom, r.width, bottom - iBottom)); // Bottom
					} else {// r is equal to or contained by b, ignore it
					}
					break;
				}
			}
		} while (modified);
		return bounds3;
	}

	private static Point getClosest(List<Rectangle> bounds, int x, int y) {
		Point best = null;
		for (Rectangle devB : bounds) {
			Point closest;
			closest = getClosest(devB, x, y);
			if (best == null || (Math.abs(x - closest.x) + Math.abs(y - closest.y)) < (Math.abs(x - best.x) + Math.abs(y - best.y)))
				best = closest;
		}
		return best;
	}

	private static Point getClosest(Rectangle bounds, int x, int y) {
		int devX, devY;
		if (x < bounds.x)
			devX = bounds.x;
		else if (x >= bounds.x + bounds.width)
			devX = bounds.x + bounds.width;
		else
			devX = x;
		if (y < bounds.y)
			devY = bounds.y;
		else if (y >= bounds.y + bounds.height)
			devY = bounds.y + bounds.height;
		else
			devY = y;
		return new Point(devX, devY);
	}

	private static long getIntersectArea(List<Rectangle> bounds, int left, int top, int right, int bottom) {
		long area = 0;
		for (Rectangle b : bounds) {
			int maxL = Math.max(left, b.x);
			int minR = Math.min(right, b.x + b.width);
			if (maxL >= minR)
				continue;
			int maxT = Math.max(top, b.y);
			int minB = Math.min(bottom, b.y + b.height);
			if (maxT >= minB)
				continue;
			area += (minR - maxL) * 1L * (minB - maxT);
		}
		return area;
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

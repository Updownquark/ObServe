package org.observe.util.swing;

import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.event.ChangeListener;

import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeEvent;

/** Utilities for the org.observe.util.swing package */
public class ObservableSwingUtils {
	private static final ConcurrentLinkedQueue<Runnable> EDT_EVENTS = new ConcurrentLinkedQueue<>();

	/**
	 * Executes a task on the AWT/Swing event thread. If this thread *is* the event thread, the task is executed inline
	 *
	 * @param task The task to execute on the AWT {@link EventQueue}
	 */
	public static void onEQ(Runnable task) {
		if (EventQueue.isDispatchThread())
			task.run();
		else {
			boolean tellEdt = EDT_EVENTS.isEmpty();
			EDT_EVENTS.add(task);
			if (tellEdt)
				EventQueue.invokeLater(ObservableSwingUtils::emptyEdtEvents);
		}
	}

	private static void emptyEdtEvents() {
		Runnable task = EDT_EVENTS.poll();
		while (task != null) {
			task.run();
			task = EDT_EVENTS.poll();
		}
	}

	/**
	 * @param text The text to create the label with
	 * @return The LabelHolder to use to configure the label
	 */
	public static LabelHolder label(String text) {
		return new LabelHolder(text);
	}

	/**
	 * @param label The label to create the holder with
	 * @return The LabelHolder to use to configure the label
	 */
	public static LabelHolder label(JLabel label) {
		return new LabelHolder(label);
	}

	/** A holder of a JLabel that contains many chain-compatible methods for configuration */
	public static class LabelHolder implements Supplier<JLabel> {
		/** The label */
		public final JLabel label;

		/** Creates a holder for an empty label */
		public LabelHolder() {
			this(new JLabel());
		}

		/** @param text The text to create the label with */
		public LabelHolder(String text) {
			this(new JLabel(text));
		}

		/** @param label The label to create the holder with */
		public LabelHolder(JLabel label) {
			this.label = label;
		}

		/**
		 * Performs a generic operation on the label
		 *
		 * @param adjustment The operation
		 * @return This holder
		 */
		public LabelHolder adjust(Consumer<JLabel> adjustment) {
			adjustment.accept(label);
			return this;
		}

		/**
		 * Makes the label's font {@link Font#BOLD bold}
		 *
		 * @return This holder
		 */
		public LabelHolder bold() {
			return withStyle(Font.BOLD);
		}

		/**
		 * Makes the label's font {@link Font#PLAIN plain}
		 *
		 * @return This holder
		 */
		public LabelHolder plain() {
			return withStyle(Font.PLAIN);
		}

		/**
		 * @param fontSize The point size for the label's font
		 * @return This holder
		 */
		public LabelHolder withFontSize(float fontSize) {
			label.setFont(label.getFont().deriveFont(fontSize));
			return this;
		}

		/**
		 * @param style The font {@link Font#getStyle() style} for the label
		 * @return This holder
		 */
		public LabelHolder withStyle(int style) {
			label.setFont(label.getFont().deriveFont(style));
			return this;
		}

		/**
		 * @param style The font {@link Font#getStyle() style} for the label
		 * @param fontSize The point size for the label's font
		 * @return This holder
		 */
		public LabelHolder withSizeAndStyle(int style, float fontSize) {
			label.setFont(label.getFont().deriveFont(style, fontSize));
			return this;
		}

		@Override
		public JLabel get() {
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
			checkBox.setToolTipText(enabled == null ? descrip : enabled);
		};
		Subscription valueSub = selected.changes().act(evt -> {
			if (!callbackLock[0])
				checkBox.setSelected(evt.getNewValue());
			checkEnabled.accept(selected.isEnabled().get());
		});
		Subscription enabledSub = selected.isEnabled().changes().act(evt -> checkEnabled.accept(evt.getNewValue()));
		return () -> {
			valueSub.unsubscribe();
			enabledSub.unsubscribe();
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
}

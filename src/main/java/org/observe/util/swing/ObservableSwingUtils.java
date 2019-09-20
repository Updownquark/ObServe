package org.observe.util.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.LayoutManager;
import java.awt.LayoutManager2;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeListener;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleSettableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.qommons.collect.ListenerList;
import org.qommons.io.Format;

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
		public final C label;

		/** @param label The component to create the holder with */
		public FontAdjuster(C label) {
			this.label = label;
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
		return checkFor(checkBox, ObservableValue.of(descrip), selected);
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
				checkBox.setSelected(evt.getNewValue());
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
	public static <C extends Container> MigFieldPanelPopulator<C> populateFields(C container, Observable<?> until) {
		if (container == null)
			container = (C) new JPanel();
		return new MigFieldPanelPopulator<>(container, until);
	}

	public static interface FieldPanelPopulator<C extends Container> {
		C getContainer();

		<F> FieldPanelPopulator<C> addTextField(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<PanelPopulatorField<F, ObservableTextField<F>>> modify);

		<F> FieldPanelPopulator<C> addLabel(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<PanelPopulatorField<F, JLabel>> modify);

		FieldPanelPopulator<C> addCheckField(String fieldName, SettableValue<Boolean> field,
			Consumer<PanelPopulatorField<Boolean, JCheckBox>> modify);

		/* TODO
		 * toggle/radio buttons
		 * slider
		 * table
		 * tree
		 * better button controls (visibility, variable text, etc.)
		 * form controls (e.g. press enter in a text field and a submit action (also tied to a button) fires
		 */

		default FieldPanelPopulator<C> addIntSpinnerField(String fieldName, SettableValue<Integer> value,
			Consumer<PanelPopulatorField<Integer, JSpinner>> modify) {
			return addSpinnerField(fieldName, new JSpinner(new SpinnerNumberModel(0, Integer.MIN_VALUE, Integer.MAX_VALUE, 1)), value,
				Number::intValue, modify);
		}

		default FieldPanelPopulator<C> addDoubleSpinnerField(String fieldName, SettableValue<Double> value,
			Consumer<PanelPopulatorField<Double, JSpinner>> modify) {
			return addSpinnerField(fieldName,
				new JSpinner(new SpinnerNumberModel(0.0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1.0)), value,
				Number::doubleValue, modify);
		}

		<F> FieldPanelPopulator<C> addSpinnerField(String fieldName, JSpinner spinner, SettableValue<F> value,
			Function<? super F, ? extends F> purifier, Consumer<PanelPopulatorField<F, JSpinner>> modify);

		default <F> FieldPanelPopulator<C> addComboField(String fieldName, SettableValue<F> value,
			Consumer<PanelComboField<F, JComboBox<F>>> modify, F... availableValues) {
			return addComboField(fieldName, value, Arrays.asList(availableValues), modify);
		}

		<F> FieldPanelPopulator<C> addComboField(String fieldName, SettableValue<F> value, List<? extends F> availableValues,
			Consumer<PanelComboField<F, JComboBox<F>>> modify);

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
		// public <F> MigPanelPopulatorField<F, ObservableTableModel<F>> addTable(ObservableCollection<F> rows,
		// ObservableCollection<CategoryRenderStrategy<? super F, ?>> columns) {}
		//
		// public <F> MigPanelPopulatorField<F, ObservableTreeModel> addTree(Object root,
		// Function<Object, ? extends ObservableCollection<?>> branching) {}

		default FieldPanelPopulator<C> addButton(String text, ObservableAction<?> action,
			Consumer<PanelPopulatorField<Object, JButton>> modify) {
			return addButton(ObservableValue.of(text), action, modify);
		}

		FieldPanelPopulator<C> addButton(ObservableValue<String> text, ObservableAction<?> action,
			Consumer<PanelPopulatorField<Object, JButton>> modify);

		default FieldPanelPopulator<C> addHPanel(String fieldName, String layoutType, Consumer<HorizPanel> modify) {
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
			return addHPanel(fieldName, layout, modify);
		}

		FieldPanelPopulator<C> addHPanel(String fieldName, LayoutManager layout, Consumer<HorizPanel> modify);

		default FieldPanelPopulator<C> spacer(int size){
			return addComponent(null, Box.createRigidArea(new Dimension(size, size)), null);
		}

		<S> FieldPanelPopulator<C> addComponent(String fieldName, S component, Consumer<PanelPopulatorField<Object, S>> modify);
	}

	private interface FieldPanelPopulatorImpl<C extends Container> extends FieldPanelPopulator<C> {
		Observable<?> _getUntil();

		abstract void doAdd(PanelPopulatorField<?, ?> field);

		@Override
		default <F> FieldPanelPopulator<C> addTextField(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<PanelPopulatorField<F, ObservableTextField<F>>> modify) {
			PanelPopulatorField<F, ObservableTextField<F>> fieldPanel = new PanelPopulatorField<>(fieldName,
				new ObservableTextField<>(field, format, _getUntil()));
			fieldPanel.getTooltip().changes().takeUntil(_getUntil()).act(evt -> fieldPanel.getEditor().setToolTipText(evt.getNewValue()));
			if (modify != null)
				modify.accept(fieldPanel);
			doAdd(fieldPanel);
			return this;
		}

		@Override
		default <F> FieldPanelPopulator<C> addLabel(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<PanelPopulatorField<F, JLabel>> modify) {
			JLabel label = new JLabel();
			PanelPopulatorField<F, JLabel> fieldPanel = new PanelPopulatorField<>(fieldName, label);
			fieldPanel.getTooltip().changes().takeUntil(_getUntil()).act(evt -> fieldPanel.getEditor().setToolTipText(evt.getNewValue()));
			field.isEnabled().combine((enabled, tt) -> enabled == null ? tt : enabled, fieldPanel.getTooltip()).changes()
			.takeUntil(_getUntil()).act(evt -> label.setToolTipText(evt.getNewValue()));
			if (modify != null)
				modify.accept(fieldPanel);
			doAdd(fieldPanel);
			return this;
		}

		@Override
		default FieldPanelPopulator<C> addCheckField(String fieldName, SettableValue<Boolean> field,
			Consumer<PanelPopulatorField<Boolean, JCheckBox>> modify) {
			PanelPopulatorField<Boolean, JCheckBox> fieldPanel = new PanelPopulatorField<>(fieldName, new JCheckBox());
			Subscription sub = checkFor(fieldPanel.getEditor(), fieldPanel.getTooltip(), field);
			_getUntil().take(1).act(__ -> sub.unsubscribe());
			if (modify != null)
				modify.accept(fieldPanel);
			doAdd(fieldPanel);
			return this;
		}

		@Override
		default <F> FieldPanelPopulator<C> addSpinnerField(String fieldName, JSpinner spinner, SettableValue<F> value,
			Function<? super F, ? extends F> purifier, Consumer<PanelPopulatorField<F, JSpinner>> modify) {
			PanelPopulatorField<F, JSpinner> fieldPanel = new PanelPopulatorField<>(fieldName, spinner);
			ObservableSwingUtils.spinnerFor(spinner, fieldPanel.getTooltip().get(), value, purifier);
			if (modify != null)
				modify.accept(fieldPanel);
			doAdd(fieldPanel);
			return this;
		}

		@Override
		default <F> FieldPanelPopulator<C> addComboField(String fieldName, SettableValue<F> value,
			Consumer<PanelComboField<F, JComboBox<F>>> modify, F... availableValues) {
			return addComboField(fieldName, value, Arrays.asList(availableValues), modify);
		}

		@Override
		default <F> FieldPanelPopulator<C> addComboField(String fieldName, SettableValue<F> value, List<? extends F> availableValues,
			Consumer<PanelComboField<F, JComboBox<F>>> modify) {
			ObservableCollection<? extends F> observableValues;
			if (availableValues instanceof ObservableCollection)
				observableValues = (ObservableCollection<F>) availableValues;
			else
				observableValues = ObservableCollection.of(value.getType(), availableValues);
			PanelComboField<F, JComboBox<F>> fieldPanel = new PanelComboField<>(fieldName, new JComboBox<>());
			Subscription sub = ObservableComboBoxModel.comboFor(fieldPanel.getEditor(), fieldPanel.getTooltip(),
				fieldPanel::getValueTooltip, observableValues, value);
			_getUntil().take(1).act(__ -> sub.unsubscribe());
			if (modify != null)
				modify.accept(fieldPanel);
			doAdd(fieldPanel);
			return this;
		}

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
		// public <F> MigPanelPopulatorField<F, ObservableTableModel<F>> addTable(ObservableCollection<F> rows,
		// ObservableCollection<CategoryRenderStrategy<? super F, ?>> columns) {}
		//
		// public <F> MigPanelPopulatorField<F, ObservableTreeModel> addTree(Object root,
		// Function<Object, ? extends ObservableCollection<?>> branching) {}

		@Override
		default FieldPanelPopulator<C> addHPanel(String fieldName, LayoutManager layout, Consumer<HorizPanel> modify) {
			HorizPanel subPanel = new HorizPanel(fieldName, layout, _getUntil());
			if(modify!=null)
				modify.accept(subPanel);
			doAdd(subPanel);
			return this;
		}

		@Override
		default FieldPanelPopulator<C> addButton(ObservableValue<String> text, ObservableAction<?> action,
			Consumer<PanelPopulatorField<Object, JButton>> modify) {
			JButton button = new JButton();
			text.changes().takeUntil(_getUntil()).act(evt -> button.setText(evt.getNewValue()));
			button.addActionListener(evt -> action.act(evt));
			PanelPopulatorField<Object, JButton> field = new PanelPopulatorField<>(null, button);
			action.isEnabled().combine((enabled, tt) -> enabled == null ? tt : enabled, field.getTooltip()).changes().takeUntil(_getUntil())
			.act(evt -> button.setToolTipText(evt.getNewValue()));
			action.isEnabled().takeUntil(_getUntil()).changes().act(evt -> button.setEnabled(evt.getNewValue() == null));
			if (modify != null)
				modify.accept(field);
			doAdd(field);
			return this;
		}

		@Override
		default <S> FieldPanelPopulator<C> addComponent(String fieldName, S component, Consumer<PanelPopulatorField<Object, S>> modify) {
			PanelPopulatorField<Object, S> subPanel = new PanelPopulatorField<>(fieldName, component);
			if (modify != null)
				modify.accept(subPanel);
			doAdd(subPanel);
			return this;
		}
	}

	public static class PanelPopulatorField<F, E> {
		private ObservableValue<String> theFieldName;
		private Consumer<FontAdjuster<JLabel>> theFieldLabelModifier;
		private final E theEditor;
		private ObservableValue<String> theTooltip;
		private SettableValue<ObservableValue<String>> theSettableTooltip;
		private ObservableValue<String> thePostLabel;
		private boolean isGrow;
		private ObservableValue<Boolean> isVisible;
		private Consumer<FontAdjuster<?>> theFont;

		PanelPopulatorField(String fieldName, E editor) {
			theFieldName = fieldName == null ? null : ObservableValue.of(fieldName);
			theEditor = editor;
			theSettableTooltip = new SimpleSettableValue<>(ObservableValue.TYPE_KEY.getCompoundType(String.class), true);
			theTooltip = ObservableValue.flatten(theSettableTooltip);
		}

		public E getEditor() {
			return theEditor;
		}

		public PanelPopulatorField<F, E> withFieldName(String fieldName) {
			theFieldName = fieldName == null ? null : ObservableValue.of(fieldName);
			return this;
		}

		public PanelPopulatorField<F, E> withVariableFieldName(ObservableValue<String> fieldName) {
			theFieldName = fieldName;
			return this;
		}

		public PanelPopulatorField<F, E> modifyLabel(Consumer<FontAdjuster<JLabel>> labelModifier) {
			if (theFieldLabelModifier == null)
				theFieldLabelModifier = labelModifier;
			else {
				Consumer<FontAdjuster<JLabel>> prev = theFieldLabelModifier;
				theFieldLabelModifier = f -> {
					prev.accept(f);
					labelModifier.accept(f);
				};
			}
			return this;
		}

		public PanelPopulatorField<F, E> withFont(Consumer<FontAdjuster<?>> font) {
			if (theFont == null)
				theFont = font;
			else {
				Consumer<FontAdjuster<?>> prev = theFont;
				theFont = f -> {
					prev.accept(f);
					font.accept(f);
				};
			}
			return this;
		}

		public PanelPopulatorField<F, E> visibleWhen(ObservableValue<Boolean> visible) {
			isVisible = visible;
			return this;
		}

		public PanelPopulatorField<F, E> modifyEditor(Consumer<E> modify) {
			modify.accept(theEditor);
			return this;
		}

		public ObservableValue<String> getTooltip() {
			return theTooltip;
		}

		protected Component getComponent() {
			if (!(theEditor instanceof Component))
				throw new IllegalStateException("Editor is not a component");
			if (theFont != null)
				theFont.accept(new FontAdjuster<>((Component) theEditor));
			return (Component) theEditor;
		}

		public PanelPopulatorField<F, E> withTooltip(String tooltip) {
			return withTooltip(ObservableValue.of(TypeTokens.get().STRING, tooltip));
		}

		public PanelPopulatorField<F, E> withTooltip(ObservableValue<String> tooltip) {
			theSettableTooltip.set(tooltip, null);
			return this;
		}

		public PanelPopulatorField<F, E> withPostLabel(String descrip) {
			thePostLabel = ObservableValue.of(descrip);
			return this;
		}

		public PanelPopulatorField<F, E> withPostLabel(ObservableValue<String> descrip) {
			thePostLabel = descrip;
			return this;
		}

		public PanelPopulatorField<F, E> fill() {
			isGrow = true;
			return this;
		}

		protected ObservableValue<String> getFieldName() {
			return theFieldName;
		}

		protected JLabel createFieldNameLabel(Observable<?> until) {
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

		protected JLabel createPostLabel(Observable<?> until) {
			if (thePostLabel == null)
				return null;
			JLabel postLabel = new JLabel(thePostLabel.get());
			thePostLabel.changes().takeUntil(until).act(evt -> postLabel.setText(evt.getNewValue()));
			if (theFieldLabelModifier != null)
				theFieldLabelModifier.accept(new FontAdjuster<>(postLabel));
			if (theFont != null)
				theFont.accept(new FontAdjuster<>(postLabel));
			return postLabel;
		}

		protected ObservableValue<String> getPostLabel() {
			return thePostLabel;
		}

		protected boolean isGrow() {
			return isGrow;
		}

		protected ObservableValue<Boolean> isVisible() {
			return isVisible;
		}
	}

	public static class PanelComboField<F, E> extends PanelPopulatorField<F, E> {
		protected Function<? super F, String> theValueTooltip;

		public PanelComboField(String fieldName, E editor) {
			super(fieldName, editor);
		}

		public PanelComboField<F, E> withValueTooltip(Function<? super F, String> tooltip) {
			theValueTooltip = tooltip;
			return this;
		}

		public String getValueTooltip(F value) {
			return theValueTooltip == null ? null : theValueTooltip.apply(value);
		}

		@Override
		public PanelComboField<F, E> withVariableFieldName(ObservableValue<String> fieldName) {
			return (PanelComboField<F, E>) super.withVariableFieldName(fieldName);
		}

		@Override
		public PanelComboField<F, E> modifyEditor(Consumer<E> modify) {
			return (PanelComboField<F, E>) super.modifyEditor(modify);
		}

		@Override
		public PanelComboField<F, E> withTooltip(String tooltip) {
			return (PanelComboField<F, E>) super.withTooltip(tooltip);
		}

		@Override
		public PanelComboField<F, E> withTooltip(ObservableValue<String> tooltip) {
			return (PanelComboField<F, E>) super.withTooltip(tooltip);
		}

		@Override
		public PanelComboField<F, E> withPostLabel(String descrip) {
			return (PanelComboField<F, E>) super.withPostLabel(descrip);
		}

		@Override
		public PanelComboField<F, E> withPostLabel(ObservableValue<String> descrip) {
			return (PanelComboField<F, E>) super.withPostLabel(descrip);
		}

		@Override
		public PanelComboField<F, E> fill() {
			return (PanelComboField<F, E>) super.fill();
		}
	}

	public static class PanelSpinnerField<F> extends PanelPopulatorField<F, JSpinner> {
		public PanelSpinnerField(String fieldName, JSpinner editor) {
			super(fieldName, editor);
		}

		@Override
		public PanelSpinnerField<F> withVariableFieldName(ObservableValue<String> fieldName) {
			super.withVariableFieldName(fieldName);
			return this;
		}

		@Override
		public PanelSpinnerField<F> visibleWhen(ObservableValue<Boolean> visible) {
			super.visibleWhen(visible);
			return this;
		}

		@Override
		public PanelSpinnerField<F> modifyEditor(Consumer<JSpinner> modify) {
			super.modifyEditor(modify);
			return this;
		}

		@Override
		public PanelSpinnerField<F> withTooltip(String tooltip) {
			super.withTooltip(tooltip);
			return this;
		}

		@Override
		public PanelSpinnerField<F> withTooltip(ObservableValue<String> tooltip) {
			super.withTooltip(tooltip);
			return this;
		}

		@Override
		public PanelSpinnerField<F> withPostLabel(String descrip) {
			super.withPostLabel(descrip);
			return this;
		}

		@Override
		public PanelSpinnerField<F> withPostLabel(ObservableValue<String> descrip) {
			super.withPostLabel(descrip);
			return this;
		}

		@Override
		public PanelSpinnerField<F> fill() {
			super.fill();
			return this;
		}

		public PanelSpinnerField<F> withStepSize(F stepSize) {
			((SpinnerNumberModel) getEditor().getModel()).setStepSize((Number) stepSize);
			return this;
		}
	}

	public static class HorizPanel extends PanelPopulatorField<Object, JPanel> implements FieldPanelPopulatorImpl<JPanel> {
		private final Observable<?> theUntil;

		public HorizPanel(String fieldName, LayoutManager layout, Observable<?> until) {
			super(fieldName, new JPanel(layout));
			theUntil = until;
		}

		@Override
		public JPanel getContainer() {
			return getEditor();
		}

		@Override
		public Observable<?> _getUntil() {
			return theUntil;
		}

		@Override
		public void doAdd(PanelPopulatorField<?, ?> field) {
			JLabel fieldNameLabel = field.createFieldNameLabel(_getUntil());
			if (fieldNameLabel != null)
				getContainer().add(fieldNameLabel);
			JLabel postLabel = field.createPostLabel(_getUntil());
			Component component = field.getComponent();
			String constraints = null;
			if (field.isGrow() && getContainer().getLayout().getClass().getName().startsWith("net.mig"))
				constraints = "growx, pushx";
			getContainer().add(component, constraints);
			if (postLabel != null)
				getContainer().add(postLabel);
			if (field.isVisible() != null) {
				field.isVisible().changes().takeUntil(_getUntil()).act(evt -> {
					if (fieldNameLabel != null)
						fieldNameLabel.setVisible(evt.getNewValue());
					component.setVisible(evt.getNewValue());
					if (postLabel != null)
						postLabel.setVisible(evt.getNewValue());
				});
			}
		}

		/* Need to override all these because otherwise, the return type would be either FieldPanelPopulator or PanelPopulatorField.
		 * So after calling a method of one, it would not be possible to chain a call to the other.
		 * The only thing these overrides do is make the cross-chaining possible. */

		@Override
		public HorizPanel withFieldName(String fieldName) {
			super.withFieldName(fieldName);
			return this;
		}

		@Override
		public HorizPanel withVariableFieldName(ObservableValue<String> fieldName) {
			super.withVariableFieldName(fieldName);
			return this;
		}

		@Override
		public HorizPanel modifyLabel(Consumer<FontAdjuster<JLabel>> labelModifier) {
			super.modifyLabel(labelModifier);
			return this;
		}

		@Override
		public HorizPanel withFont(Consumer<FontAdjuster<?>> labelModifier) {
			super.withFont(labelModifier);
			return this;
		}

		@Override
		public HorizPanel visibleWhen(ObservableValue<Boolean> visible) {
			super.visibleWhen(visible);
			return this;
		}

		@Override
		public HorizPanel modifyEditor(Consumer<JPanel> modify) {
			super.modifyEditor(modify);
			return this;
		}

		@Override
		public HorizPanel withTooltip(String tooltip) {
			super.withTooltip(tooltip);
			return this;
		}

		@Override
		public HorizPanel withTooltip(ObservableValue<String> tooltip) {
			super.withTooltip(tooltip);
			return this;
		}

		@Override
		public HorizPanel withPostLabel(String descrip) {
			super.withPostLabel(descrip);
			return this;
		}

		@Override
		public HorizPanel withPostLabel(ObservableValue<String> descrip) {
			super.withPostLabel(descrip);
			return this;
		}

		@Override
		public HorizPanel fill() {
			super.fill();
			return this;
		}

		@Override
		public HorizPanel addButton(String text, ObservableAction<?> action, Consumer<PanelPopulatorField<Object, JButton>> modify) {
			FieldPanelPopulatorImpl.super.addButton(text, action, modify);
			return this;
		}

		@Override
		public HorizPanel addButton(ObservableValue<String> text, ObservableAction<?> action,
			Consumer<PanelPopulatorField<Object, JButton>> modify) {
			FieldPanelPopulatorImpl.super.addButton(text, action, modify);
			return this;
		}

		@Override
		public <F> HorizPanel addTextField(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<PanelPopulatorField<F, ObservableTextField<F>>> modify) {
			FieldPanelPopulatorImpl.super.addTextField(fieldName, field, format, modify);
			return this;
		}

		@Override
		public <F> HorizPanel addLabel(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<PanelPopulatorField<F, JLabel>> modify) {
			FieldPanelPopulatorImpl.super.addLabel(fieldName, field, format, modify);
			return this;
		}

		@Override
		public HorizPanel addCheckField(String fieldName, SettableValue<Boolean> field,
			Consumer<PanelPopulatorField<Boolean, JCheckBox>> modify) {
			FieldPanelPopulatorImpl.super.addCheckField(fieldName, field, modify);
			return this;
		}

		@Override
		public HorizPanel addIntSpinnerField(String fieldName, SettableValue<Integer> value,
			Consumer<PanelPopulatorField<Integer, JSpinner>> modify) {
			FieldPanelPopulatorImpl.super.addIntSpinnerField(fieldName, value, modify);
			return this;
		}

		@Override
		public HorizPanel addDoubleSpinnerField(String fieldName, SettableValue<Double> value,
			Consumer<PanelPopulatorField<Double, JSpinner>> modify) {
			FieldPanelPopulatorImpl.super.addDoubleSpinnerField(fieldName, value, modify);
			return this;
		}

		@Override
		public <F> HorizPanel addSpinnerField(String fieldName, JSpinner spinner, SettableValue<F> value,
			Function<? super F, ? extends F> purifier, Consumer<PanelPopulatorField<F, JSpinner>> modify) {
			FieldPanelPopulatorImpl.super.addSpinnerField(fieldName, spinner, value, purifier, modify);
			return this;
		}

		@Override
		public <F> HorizPanel addComboField(String fieldName, SettableValue<F> value, Consumer<PanelComboField<F, JComboBox<F>>> modify,
			F... availableValues) {
			FieldPanelPopulatorImpl.super.addComboField(fieldName, value, modify, availableValues);
			return this;
		}

		@Override
		public <F> HorizPanel addComboField(String fieldName, SettableValue<F> value, List<? extends F> availableValues,
			Consumer<PanelComboField<F, JComboBox<F>>> modify) {
			FieldPanelPopulatorImpl.super.addComboField(fieldName, value, availableValues, modify);
			return this;
		}

		@Override
		public HorizPanel spacer(int size) {
			FieldPanelPopulatorImpl.super.spacer(size);
			return this;
		}

		@Override
		public <S> HorizPanel addComponent(String fieldName, S component, Consumer<PanelPopulatorField<Object, S>> modify) {
			FieldPanelPopulatorImpl.super.addComponent(fieldName, component, modify);
			return this;
		}
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

	/**
	 * <p>
	 * Adds fields to a panel whose layout is a net.miginfocom.swing.MigLayout.
	 * </p>
	 * <p>
	 * This layout class is not included in this library, but must be included separately.
	 * </p>
	 * <p>
	 * If the container's layout is not a MigLayout when the field populator is created for it, an attempt will be made to create and set
	 * the layout, looking up the class by name. This will throw an {@link IllegalStateException} if the class cannot be found or created.
	 * </p>
	 *
	 * @param <C> The type of the container
	 */
	public static class MigFieldPanelPopulator<C extends Container> implements FieldPanelPopulatorImpl<C> {
		private final C theContainer;
		private final Observable<?> theUntil;

		public MigFieldPanelPopulator(C container, Observable<?> until) {
			theContainer = container;
			theUntil = until == null ? Observable.empty() : until;
			if (container.getLayout() == null || !MIG_LAYOUT_CLASS_NAME.equals(container.getLayout().getClass().getName())) {
				LayoutManager2 migLayout = createMigLayout(true, () -> "install the layout before using this class");
				container.setLayout(migLayout);
			}
		}

		@Override
		public C getContainer() {
			return theContainer;
		}

		@Override
		public Observable<?> _getUntil() {
			return theUntil;
		}

		@Override
		public void doAdd(PanelPopulatorField<?, ?> field) {
			JLabel fieldNameLabel = field.createFieldNameLabel(_getUntil());
			if (fieldNameLabel != null)
				getContainer().add(fieldNameLabel, "align right");
			StringBuilder constraints = new StringBuilder();
			if (field.isGrow())
				constraints.append("growx, pushx");
			JLabel postLabel = field.createPostLabel(_getUntil());
			if (postLabel != null) {
				if (fieldNameLabel == null) {
					if (constraints.length() > 0)
						constraints.append(", ");
					constraints.append("span 2");
				}
			} else {
				if (constraints.length() > 0)
					constraints.append(", ");
				constraints.append("span, wrap");
			}
			Component component = field.getComponent();
			getContainer().add(component, constraints.toString());
			if (postLabel != null)
				getContainer().add(postLabel, "wrap");
			if (field.isVisible() != null) {
				field.isVisible().changes().takeUntil(_getUntil()).act(evt -> {
					if (fieldNameLabel != null)
						fieldNameLabel.setVisible(evt.getNewValue());
					component.setVisible(evt.getNewValue());
					if (postLabel != null)
						postLabel.setVisible(evt.getNewValue());
				});
			}
		}
	}
}

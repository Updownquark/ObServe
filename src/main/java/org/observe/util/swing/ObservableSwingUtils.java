package org.observe.util.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.Font;
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
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
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
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.JTextComponent;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.observe.util.SafeObservableCollection;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Causable.CausableKey;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.QommonsUtils.TimePrecision;
import org.qommons.Transaction;
import org.qommons.TriFunction;
import org.qommons.collect.CircularArrayList;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.ListenerList;
import org.qommons.io.Format;
import org.qommons.threading.QommonsTimer;
import org.xml.sax.SAXException;

import com.google.common.reflect.TypeToken;

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
			Font newFont = label.getFont().deriveFont(fontSize);
			if (!Objects.equals(newFont, label.getFont()))
				label.setFont(newFont);
			return this;
		}

		/**
		 * @param style The font {@link Font#getStyle() style} for the label
		 * @return This holder
		 */
		public FontAdjuster<C> withStyle(int style) {
			Font newFont = label.getFont().deriveFont(style);
			if (!Objects.equals(newFont, label.getFont()))
				label.setFont(newFont);
			return this;
		}

		/**
		 * @param style The font {@link Font#getStyle() style} for the label
		 * @param fontSize The point size for the label's font
		 * @return This holder
		 */
		public FontAdjuster<C> withSizeAndStyle(int style, float fontSize) {
			Font newFont = label.getFont().deriveFont(style, fontSize);
			if (!Objects.equals(newFont, label.getFont()))
				label.setFont(newFont);
			return this;
		}

		/**
		 * @param color The font color for the label
		 * @return This holder
		 */
		public FontAdjuster<C> withColor(Color color) {
			if (!label.isForegroundSet() || Objects.equals(label.getForeground(), color))
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
		SafeObservableCollection<? extends T> safeValues;
		SimpleObservable<Void> safeUntil = SimpleObservable.build().safe(false).build();
		subs.add(() -> safeUntil.onNext(null));
		if (availableValues instanceof SafeObservableCollection)
			safeValues = (SafeObservableCollection<? extends T>) availableValues;
		else
			safeValues = new SafeObservableCollection<>(availableValues, EventQueue::isDispatchThread, EventQueue::invokeLater, safeUntil);
		ObservableCollection<TB> buttons = safeValues.flow().transform(buttonType, tx -> tx.map((value, button) -> {
			if (button == null)
				button = buttonCreator.apply(value);
			render.accept(button, value);
			String enabled = selected.isEnabled().get();
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
				TB button = buttons.get(i);
				button.setEnabled(enabled == null);
				if (enabled != null)
					button.setToolTipText(enabled);
				else if (descrip != null)
					button.setToolTipText(descrip.apply(safeValues.get(i)));
				else
					button.setToolTipText(null);
			}
		};
		TriFunction<T, Integer, Object, Boolean>[] listener = new TriFunction[1];
		subs.add(ObservableComboBoxModel.<T> hookUpComboData(safeValues, selected, index -> {
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
				CollectionUtils.synchronize(selection, selValues, (v1, v2) -> equivalence.elementEquals(v1, v2))//
				.simple(LambdaUtils.identity())//
				.adjust();
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
			if (selectionModel.isSelectedIndex(i) && i < model.getSize())
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
					E selectedValue = model.getElementAt(selModel.getMinSelectionIndex());
					selection.set(selectedValue, e);
				} else if (selection.get() != null)
					selection.set(null, e);
			} finally {
				callbackLock[0] = false;
			}
		};
		/* There is the potential for a cycle here, in the case where 2 similar tables or lists are created
		 * with the same (or same-sourced) model and selection.
		 * E.g. if a model update on one collection causes a selection update which causes a model update on a second collection,
		 * an IllegalStateException will be thrown due to the attempted recursive modification.
		 * I can't think of a great way to prevent the attempt from happening.
		 * The ObservableCollection API provides the ability to determine if an element from one collection
		 * is derived from another collection, but not if both collections are derived from a common source.
		 * There are security issues even enabling such functionality, as it would require the ability to unroll all the sources of a
		 * derived collection, which should not be possible.
		 *
		 * Since the attempt cannot be prevented and the functionality (having multiple tables with shared data and selection sources)
		 * should not be disallowed, all I can think to do is catch and swallow the exception. */
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
				} catch (ListenerList.ReentrantNotificationException ex) {
					// See the cycle comment above
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
					if (update != null) {
						try {
							update.accept(selModel.getMaxSelectionIndex());
						} catch (ListenerList.ReentrantNotificationException ex) {
							// See the cycle comment above
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
		}));

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
		final int width;
		final int height;

		public IconKey(Class<?> clazz, String location, int width, int height) {
			this.clazz = clazz;
			this.location = location;
			this.width = width;
			this.height = height;
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

	public static ObservableUiBuilder buildUI() {
		return new ObservableUiBuilder();
	}

	public static class ObservableUiBuilder extends WindowPopulation.DefaultWindowBuilder<JFrame, ObservableUiBuilder> {
		private File theDefaultConfigLocation;
		private String theConfigName;
		private List<File> theOldConfigLocations;
		private List<String> theOldConfigNames;
		private URL theErrorReportLink;
		private BiConsumer<StringBuilder, Boolean> theErrorReportInstructions;
		private Consumer<ObservableConfig> theConfigInit;
		private boolean isCloseWithoutSaveEnabled;
		private volatile boolean isClosingWithoutSave;

		public ObservableUiBuilder() {
			super(new JFrame(), Observable.empty(), true);
		}

		public ObservableUiBuilder withConfigAt(String configLocation) {
			return withConfigAt(new File(configLocation));
		}

		public ObservableUiBuilder withConfigAt(File configLocation) {
			theDefaultConfigLocation = configLocation;
			return this;
		}

		public ObservableUiBuilder withConfig(String configName) {
			theConfigName = configName;
			return this;
		}

		public ObservableUiBuilder withOldConfigAt(String configLocation) {
			return withOldConfigAt(new File(configLocation));
		}

		public ObservableUiBuilder withOldConfigAt(File configLocation) {
			if (theOldConfigLocations == null)
				theOldConfigLocations = new LinkedList<>();
			theOldConfigLocations.add(configLocation);
			return this;
		}

		public ObservableUiBuilder withOldConfig(String configName) {
			if (theOldConfigNames == null)
				theOldConfigNames = new LinkedList<>();
			theOldConfigNames.add(configName);
			return this;
		}

		public ObservableUiBuilder withConfigInit(Consumer<ObservableConfig> configInit) {
			theConfigInit = configInit;
			return this;
		}

		public ObservableUiBuilder withErrorReporting(String link, BiConsumer<StringBuilder, Boolean> instructions) {
			try {
				return withErrorReporting(new URL(link), instructions);
			} catch (MalformedURLException e) {
				e.printStackTrace();
				throw new IllegalArgumentException("Bad URL: " + link);
			}
		}

		public ObservableUiBuilder withErrorReporting(URL link, BiConsumer<StringBuilder, Boolean> instructions) {
			theErrorReportLink = link;
			theErrorReportInstructions = instructions;
			withMenuBar(bar -> bar.withMenu("File", fileMenu -> {
				fileMenu.withAction("Report Error or Request Feature", __ -> {
					if (theErrorReportInstructions != null) {
						WindowPopulation.populateDialog(null, null, true)//
						.withTitle("Report Error/Request Feature")//
						.withVContent(content -> {
							String gotoText = new StringBuilder().append("<html>Go to <a href=\"").append(theErrorReportLink)
								.append("\">").append(theErrorReportLink).append("</a><br>").toString();
							content.addLabel(null, ObservableValue.of(gotoText), Format.TEXT, label -> {
								label.onClick(evt -> {
									try {
										Desktop.getDesktop().browse(theErrorReportLink.toURI());
									} catch (IOException | URISyntaxException | RuntimeException e) {
										JOptionPane.showMessageDialog(getWindow(),
											"Unable to open browser. Go to " + theErrorReportLink, "Unable To Open Browser",
											JOptionPane.ERROR_MESSAGE);
										e.printStackTrace();
									}
								});
							});
							if (theErrorReportInstructions != null) {
								StringBuilder msg = new StringBuilder("<html>");
								theErrorReportInstructions.accept(msg, false);
								content.addLabel(null, ObservableValue.of(msg.toString()), Format.TEXT, null);
							}
						}).run(getWindow());
					} else {
						try {
							Desktop.getDesktop().browse(theErrorReportLink.toURI());
						} catch (IOException | URISyntaxException | RuntimeException e) {
							JOptionPane.showMessageDialog(getWindow(), "Unable to open browser. Go to " + theErrorReportLink,
								"Unable To Open Browser", JOptionPane.ERROR_MESSAGE);
							e.printStackTrace();
						}
					}
				}, null);
			}));
			return this;
		}

		public ObservableUiBuilder enableCloseWithoutSave() {
			isCloseWithoutSaveEnabled = true;
			withMenuBar(bar -> bar.withMenu("File", fileMenu -> {
				fileMenu.withAction("Close Without Save", __ -> {
					if (JOptionPane.showConfirmDialog(getWindow(),
						"<html>This will cause all changes since the app was opened to be discarded.<br>Close the app?",
						"Exit Without Saving?", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
						return;
					isClosingWithoutSave = true;
					System.exit(0);
				}, null);
			}));
			return this;
		}

		public ObservableUiBuilder systemLandF() {
			ObservableSwingUtils.systemLandF();
			return this;
		}

		public JFrame build(Function<ObservableConfig, Component> app) {
			String configName = theConfigName;
			if (configName == null) {
				if (theDefaultConfigLocation == null)
					throw new IllegalStateException("No configuration set to initialize configuration");
				else if (!theDefaultConfigLocation.exists())
					throw new IllegalStateException("Config file does not exist");
				else if (theDefaultConfigLocation.isDirectory())
					throw new IllegalStateException("Config file is not a file");
			}
			String configFileLoc = null;
			if (configName != null)
				configFileLoc = System.getProperty(configName + ".config");
			if (configFileLoc == null && theDefaultConfigLocation != null) {
				configFileLoc = theDefaultConfigLocation.getPath();
			}
			if (configFileLoc == null)
				configFileLoc = "./" + configName + ".config";
			if (!new File(configFileLoc).exists() && (theOldConfigNames != null || theOldConfigLocations != null)) {
				if (theOldConfigNames != null) {
					configFileLoc = System.getProperty(configName + ".config");
					if ((configFileLoc == null || !new File(configFileLoc).canWrite()) && theDefaultConfigLocation != null)
						configFileLoc = theDefaultConfigLocation.getPath();
					if (configFileLoc == null)
						configFileLoc = "./" + configName + ".config";
					boolean found = false;
					for (String oldConfigName : theOldConfigNames) {
						String oldConfigLoc = System.getProperty(oldConfigName + ".config");
						if (oldConfigLoc != null && new File(oldConfigLoc).exists()) {
							new File(oldConfigLoc).renameTo(new File(configFileLoc));
							found = true;
							break;
						}
					}
					if (!found) {
						for (File oldConfigLoc : theOldConfigLocations) {
							if (oldConfigLoc.exists()) {
								oldConfigLoc.renameTo(new File(configFileLoc));
								break;
							}
						}
					}
				}
			}
			ObservableConfig config = ObservableConfig.createRoot("config");
			ObservableConfig.XmlEncoding encoding = ObservableConfig.XmlEncoding.DEFAULT;
			File configFile = new File(configFileLoc);
			if (theErrorReportLink != null || theErrorReportInstructions != null) {
				String errorFileName;
				int lastDot = configFile.getName().lastIndexOf('.');
				if (lastDot >= 0)
					errorFileName = configFile.getName().substring(0, lastDot) + ".errors.txt";
				else
					errorFileName = configFile.getName() + ".errors.txt";
				File errorFile = new File(configFile.getParentFile(), errorFileName);
				new SystemOutputHandler(configFile, errorFile);
			}
			if (configFile.exists()) {
				try {
					try (InputStream configStream = new BufferedInputStream(new FileInputStream(configFile))) {
						ObservableConfig.readXml(config, configStream, encoding);
					}
				} catch (IOException | SAXException e) {
					System.err.println("Could not read config file " + configFileLoc);
					e.printStackTrace();
				}
				if (configName != null)
					config.setName(configName);
			} else {
				if (configName != null)
					config.setName(configName);
				if (theConfigInit != null)
					theConfigInit.accept(config);
			}
			config.persistOnShutdown(ObservableConfig.persistIf(ObservableConfig.toFile(configFile, encoding), () -> !isClosingWithoutSave),
				ex -> {
					System.err.println("Could not persist UI config");
					ex.printStackTrace();
				});
			if (EventQueue.isDispatchThread())
				return _build(config, app);
			else {
				JFrame[] frame = new JFrame[1];
				try {
					EventQueue.invokeAndWait(() -> {
						frame[0] = _build(config, app);
					});
				} catch (InvocationTargetException | InterruptedException e) {
					throw new IllegalStateException(e);
				}
				return frame[0];
			}
		}

		static class SystemOutput {
			final byte[] content;
			final Instant time;
			final boolean error;

			SystemOutput(byte[] content, Instant time, boolean error) {
				this.content = content;
				this.time = time;
				this.error = error;
			}
		}

		class SystemOutputHandler {
			private final File theConfigFile;
			private final File theErrorFile;
			private final ByteArrayOutputStream theBytes;
			private final Duration theAccumulationTime;
			private final Duration theErrorCascadeTolerance;
			private final CircularArrayList<SystemOutput> theOutput;
			private volatile Instant theLastWriteTime;
			private volatile boolean theLastWriteError;
			private volatile boolean isWritingError;

			SystemOutputHandler(File configFile, File errorFile) {
				theConfigFile = configFile;
				theErrorFile = errorFile;
				theBytes = new ByteArrayOutputStream(64 * 1028);
				theAccumulationTime = Duration.ofSeconds(2);
				theErrorCascadeTolerance = Duration.ofMillis(100);
				theOutput = CircularArrayList.build().build();

				System.setOut(new HandlerPrintStream(System.out, false));
				System.setErr(new HandlerPrintStream(System.err, true));
			}

			void start() {
				/// TODO
			}

			synchronized void report(int b, boolean error) throws IOException {
				startReport(error);
				theBytes.write(b);
			}

			synchronized void report(byte[] b, int off, int len, boolean error) throws IOException {
				startReport(error);
				theBytes.write(b, off, len);
			}

			private void startReport(boolean error) {
				Instant now = Instant.now();
				boolean timeDiff = !now.equals(theLastWriteTime);
				if (timeDiff) {
					if (!isWritingError) {
						SystemOutput output = theOutput.peekFirst();
						while (output != null && output.time.plus(theAccumulationTime).compareTo(now) < 0) {
							theOutput.removeFirst();
							output = theOutput.peekFirst();
						}
					}
					theLastWriteTime = now;
				}
				if (theBytes.size() == 0) {//
				} else if (error != theLastWriteError || timeDiff) {
					byte[] output = theBytes.toByteArray();
					theBytes.reset();
					theOutput.add(new SystemOutput(output, now, error));
				}
				if (error) {
					isWritingError = true;
					QommonsTimer.getCommonInstance().doAfterInactivity(this, this::alertUser, theErrorCascadeTolerance);
				}
			}

			private void alertUser() {
				isWritingError = false;
				try (OutputStream out = new BufferedOutputStream(new FileOutputStream(theErrorFile));
					Writer writer = new OutputStreamWriter(out)) {
					boolean lastLineEnd = true;
					boolean lastError = false;
					Instant lastTime = Instant.ofEpochMilli(0);
					SystemOutput output = theOutput.pollFirst();
					while (output != null) {
						boolean timeDiff = !lastTime.equals(output.time);
						if (lastError != output.error || (lastLineEnd && timeDiff)) {
							writer.append('[').append(output.error ? "ERR" : "OUT").append(' ');
							writer.append(QommonsUtils.printRelativeTime(output.time.toEpochMilli(), lastTime.toEpochMilli(),
								TimePrecision.MILLIS, TimeZone.getDefault(), 0, null));
							writer.append("] ");
							writer.flush();
						}
						lastError = output.error;
						lastTime = output.time;
						out.write(output.content);
						byte lastChar = output.content[output.content.length - 1];
						lastLineEnd = lastChar == '\n' || lastChar == '\r';
						output = theOutput.pollFirst();
					}
					WindowPopulation.populateDialog(null, null, true)//
					.withTitle("Unhandled Application Error")//
					.withVContent(content -> {
						String title = getTitle();
						if (title != null)
							content.addLabel(null, ObservableValue.of(title + " has encountered an error."), Format.TEXT, null);
						else
							content.addLabel(null, ObservableValue.of("An error has been encountered."), Format.TEXT, null);
						if (theErrorReportLink != null) {
							String gotoText = new StringBuilder().append("<html>Go to <a href=\"").append(theErrorReportLink)
								.append("\">").append(theErrorReportLink).append("</a><br>").toString();
							content.addLabel(null, ObservableValue.of(gotoText), Format.TEXT, label -> {
								label.onClick(evt -> {
									try {
										Desktop.getDesktop().browse(theErrorReportLink.toURI());
									} catch (IOException | URISyntaxException | RuntimeException e) {
										JOptionPane.showMessageDialog(getWindow(),
											"Unable to open browser. Go to " + theErrorReportLink, "Unable To Open Browser",
											JOptionPane.ERROR_MESSAGE);
										e.printStackTrace();
									}
								});
							});
						} else
							content.addLabel(null, ObservableValue.of("Please report it."), Format.TEXT, null);
						content.addLabel(null,
							ObservableValue.of("<html>Please attach the captured error output file: <a href=\"thisDoesntMatter.html\">"
								+ theErrorFile.getPath() + "</a>"),
							Format.TEXT, label -> {
								label.onClick(evt -> {
									try {// We don't have Java 9, but this hack seems to work
										Desktop.getDesktop().browse(theErrorFile.getParentFile().toURI());
									} catch (IOException | RuntimeException e) {
										e.printStackTrace();
									}
								});
							});
						if (theErrorReportInstructions != null) {
							StringBuilder msg = new StringBuilder("<html>");
							theErrorReportInstructions.accept(msg, true);
							content.addLabel(null, ObservableValue.of(msg.toString()), Format.TEXT, null);
						}
						String msg = "<html>You may want to consider backing up your data file (" + theConfigFile.getPath() + ")";
						if (isCloseWithoutSaveEnabled)
							msg += "<br>" + " or closing the app without saving your changes (File->Close Without Save)";
						content.addLabel(null, ObservableValue.of(msg), Format.TEXT, null);
					}).run(getWindow());
				} catch (IOException e) {
					System.err.println("Could not write error output");
					e.printStackTrace();
				}
			}

			class HandlerPrintStream extends PrintStream {
				private final PrintStream theSystemStream;

				HandlerPrintStream(PrintStream systemStream, boolean error) {
					super(new OutputStream() {
						@Override
						public void write(int b) throws IOException {
							report(b, error);
						}

						@Override
						public void write(byte[] b) throws IOException {
							report(b, 0, b.length, error);
						}

						@Override
						public void write(byte[] b, int off, int len) throws IOException {
							report(b, off, len, error);
						}
					});
					theSystemStream = systemStream;
				}

				@Override
				public void flush() {
					theSystemStream.flush();
					super.flush();
				}

				@Override
				public void close() {
					theSystemStream.close();
					super.close();
				}

				@Override
				public boolean checkError() {
					theSystemStream.checkError();
					return super.checkError();
				}

				@Override
				public void write(int b) {
					theSystemStream.write(b);
					super.write(b);
				}

				@Override
				public void write(byte[] buf, int off, int len) {
					theSystemStream.write(buf, off, len);
					super.write(buf, off, len);
				}

				@Override
				public void print(boolean b) {
					theSystemStream.print(b);
					super.print(b);
				}

				@Override
				public void print(char c) {
					theSystemStream.print(c);
					super.print(c);
				}

				@Override
				public void print(int i) {
					theSystemStream.print(i);
					super.print(i);
				}

				@Override
				public void print(long l) {
					theSystemStream.print(l);
					super.print(l);
				}

				@Override
				public void print(float f) {
					theSystemStream.print(f);
					super.print(f);
				}

				@Override
				public void print(double d) {
					theSystemStream.print(d);
					super.print(d);
				}

				@Override
				public void print(char[] s) {
					theSystemStream.print(s);
					super.print(s);
				}

				@Override
				public void print(String s) {
					theSystemStream.print(s);
					super.print(s);
				}

				@Override
				public void print(Object obj) {
					theSystemStream.print(obj);
					super.print(obj);
				}

				@Override
				public void println() {
					theSystemStream.println();
					super.println();
				}

				@Override
				public void println(boolean x) {
					theSystemStream.println(x);
					super.println(x);
				}

				@Override
				public void println(char x) {
					theSystemStream.println(x);
					super.println(x);
				}

				@Override
				public void println(int x) {
					theSystemStream.println(x);
					super.println(x);
				}

				@Override
				public void println(long x) {
					theSystemStream.println(x);
					super.println(x);
				}

				@Override
				public void println(float x) {
					theSystemStream.println(x);
					super.println(x);
				}

				@Override
				public void println(double x) {
					theSystemStream.println(x);
					super.println(x);
				}

				@Override
				public void println(char[] x) {
					theSystemStream.println(x);
					super.println(x);
				}

				@Override
				public void println(String x) {
					theSystemStream.println(x);
					super.println(x);
				}

				@Override
				public void println(Object x) {
					theSystemStream.println(x);
					super.println(x);
				}

				@Override
				public PrintStream printf(String format, Object... args) {
					theSystemStream.printf(format, args);
					return super.printf(format, args);
				}

				@Override
				public PrintStream printf(Locale l, String format, Object... args) {
					theSystemStream.printf(l, format, args);
					return super.printf(l, format, args);
				}

				@Override
				public PrintStream format(String format, Object... args) {
					theSystemStream.format(format, args);
					return super.format(format, args);
				}

				@Override
				public PrintStream format(Locale l, String format, Object... args) {
					theSystemStream.format(l, format, args);
					return super.format(l, format, args);
				}

				@Override
				public PrintStream append(CharSequence csq) {
					theSystemStream.append(csq);
					return super.append(csq);
				}

				@Override
				public PrintStream append(CharSequence csq, int start, int end) {
					theSystemStream.append(csq, start, end);
					return super.append(csq, start, end);
				}

				@Override
				public PrintStream append(char c) {
					theSystemStream.append(c);
					return super.append(c);
				}

				@Override
				public void write(byte[] b) throws IOException {
					theSystemStream.write(b);
					super.write(b);
				}
			}
		}

		private JFrame _build(ObservableConfig config, Function<ObservableConfig, Component> app) {
			Component ui = app.apply(config);
			return withBounds(config).withContent(ui).run(null).getWindow();
		}
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
		ObservableConfig config = ObservableConfig.createRoot("configName");
		ObservableConfig.XmlEncoding encoding = ObservableConfig.XmlEncoding.DEFAULT;
		File configFile = new File(configFileLoc);
		if (configFile.exists()) {
			try {
				try (InputStream configStream = new BufferedInputStream(new FileInputStream(configFile))) {
					ObservableConfig.readXml(config, configStream, encoding);
				}
			} catch (IOException | SAXException e) {
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

	public static List<Rectangle> getGraphicsBounds() {
		List<Rectangle> bounds = new ArrayList<>();
		for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
			for (GraphicsConfiguration config : device.getConfigurations()) {
				bounds.add(config.getBounds());
			}
		}
		return bounds;
	}

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

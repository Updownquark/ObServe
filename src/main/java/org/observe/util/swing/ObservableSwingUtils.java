package org.observe.util.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.LayoutManager2;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
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
import org.qommons.BiTuple;
import org.qommons.io.Format;

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
	 */
	public static class MigFieldPanelPopulator<C extends Container> {
		private static final String MIG_LAYOUT_CLASS_NAME = "net.miginfocom.swing.MigLayout";

		private final C theContainer;
		private final Observable<?> theUntil;

		public MigFieldPanelPopulator(C container, Observable<?> until) {
			theContainer = container;
			theUntil = until == null ? Observable.empty() : until;
			if (container.getLayout() == null || !MIG_LAYOUT_CLASS_NAME.equals(container.getLayout().getClass().getName())) {
				LayoutManager2 migLayout;
				try {
					migLayout = (LayoutManager2) Class.forName(MIG_LAYOUT_CLASS_NAME).getConstructor(String.class)
						.newInstance("fillx, hidemode 3");
				} catch (InstantiationException | IllegalAccessException | ClassNotFoundException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException e) {
					throw new IllegalStateException(getClass().getName() + " could not instantiate " + MIG_LAYOUT_CLASS_NAME
						+ ": install the layout before using this class");
				}
				container.setLayout(migLayout);
			}
		}

		public MigButtonPanel addButtons() {
			return new MigButtonPanel();
		}

		public <F> MigFieldPanelPopulator addTextField(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<MigPanelPopulatorField<F, ObservableTextField<F>>> modify) {
			MigPanelPopulatorField<F, ObservableTextField<F>> fieldPanel = new MigPanelPopulatorField<>(fieldName, field,
				new ObservableTextField<>(field, format, theUntil));
			fieldPanel.getTooltip().changes().takeUntil(theUntil).act(evt -> fieldPanel.getEditor().setToolTipText(evt.getNewValue()));
			if (modify != null)
				modify.accept(fieldPanel);
			fieldPanel.done();
			return this;
		}

		public MigFieldPanelPopulator addCheckField(String fieldName, SettableValue<Boolean> field,
			Consumer<MigPanelPopulatorField<Boolean, JCheckBox>> modify) {
			MigPanelPopulatorField<Boolean, JCheckBox> fieldPanel = new MigPanelPopulatorField<Boolean, JCheckBox>(fieldName, field,
				new JCheckBox());
			Subscription sub = checkFor(fieldPanel.getEditor(), fieldPanel.getTooltip(), field);
			theUntil.take(1).act(__ -> sub.unsubscribe());
			if (modify != null)
				modify.accept(fieldPanel);
			fieldPanel.done();
			return this;
		}

		/* TODO
		 * spinners
		 * toggle/radio buttons
		 * slider
		 * table
		 * tree
		 * better button controls (visibility, variable text, etc.)
		 * form controls (e.g. press enter in a text field and a submit action (also tied to a button) fires
		 */

		// public MigPanelPopulatorField<Integer, JSpinner> addIntSpinnerField(String fieldName, SettableValue<Integer> value) {
		// return addSpinnerField(fieldName, value, Number::intValue);
		// }
		//
		// public MigPanelPopulatorField<Double, JSpinner> addDoubleSpinnerField(String fieldName, SettableValue<Double> value) {
		// return addSpinnerField(fieldName, value, Number::doubleValue);
		// }
		//
		// public <F extends Number> MigPanelPopulatorField<F, JSpinner> addSpinnerField(String fieldName, SettableValue<F> value,
		// Function<? super F, ? extends F> purifier) {
		// MigPanelPopulatorField<F, JSpinner> fieldPanel=new MigPanelPopulatorField<F, JSpinner>(fieldName, value, new JSpinner(new SNM));
		// }

		public <F> MigFieldPanelPopulator addComboField(String fieldName, SettableValue<F> value,
			Consumer<MigPanelComboField<F, JComboBox<F>>> modify, F... availableValues) {
			return addComboField(fieldName, value, Arrays.asList(availableValues), modify);
		}

		public <F> MigFieldPanelPopulator addComboField(String fieldName, SettableValue<F> value, List<? extends F> availableValues,
			Consumer<MigPanelComboField<F, JComboBox<F>>> modify) {
			ObservableCollection<? extends F> observableValues;
			if (availableValues instanceof ObservableCollection)
				observableValues = (ObservableCollection<F>) availableValues;
			else
				observableValues = ObservableCollection.of(value.getType(), availableValues);
			MigPanelComboField<F, JComboBox<F>> fieldPanel = new MigPanelComboField<>(fieldName, value, new JComboBox<>());
			Subscription sub = ObservableComboBoxModel.comboFor(fieldPanel.getEditor(), fieldPanel.getTooltip(),
				fieldPanel::getValueTooltip, observableValues, value);
			theUntil.take(1).act(__ -> sub.unsubscribe());
			if (modify != null)
				modify.accept(fieldPanel);
			fieldPanel.done();
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

		public C getContainer() {
			return theContainer;
		}

		public class MigPanelPopulatorField<F, E> {
			private ObservableValue<String> theFieldName;
			private final SettableValue<F> theValue;
			private final E theEditor;
			private ObservableValue<String> theTooltip;
			private SettableValue<ObservableValue<String>> theSettableTooltip;
			private JLabel thePostLabel;
			private boolean isGrow;
			private ObservableValue<Boolean> isVisible;

			MigPanelPopulatorField(String fieldName, SettableValue<F> value, E editor) {
				theFieldName = ObservableValue.of(TypeTokens.get().STRING, fieldName);
				theValue = value;
				theEditor = editor;
				theSettableTooltip = new SimpleSettableValue<>(ObservableValue.TYPE_KEY.getCompoundType(String.class), true);
				theTooltip = ObservableValue.flatten(theSettableTooltip);
			}

			public E getEditor() {
				return theEditor;
			}

			public MigPanelPopulatorField<F, E> withVariableFieldName(ObservableValue<String> fieldName) {
				theFieldName = fieldName;
				return this;
			}

			public MigPanelPopulatorField<F, E> visibleWhen(ObservableValue<Boolean> visible) {
				isVisible = visible;
				return this;
			}

			public MigPanelPopulatorField<F, E> modifyEditor(Consumer<E> modify) {
				modify.accept(theEditor);
				return this;
			}

			public ObservableValue<String> getTooltip() {
				return theTooltip;
			}

			protected Component getComponent() {
				if (!(theEditor instanceof Component))
					throw new IllegalStateException("Editor is not a component");
				return (Component) theEditor;
			}

			public MigPanelPopulatorField<F, E> withTooltip(String tooltip) {
				return withTooltip(ObservableValue.of(TypeTokens.get().STRING, tooltip));
			}

			public MigPanelPopulatorField<F, E> withTooltip(ObservableValue<String> tooltip) {
				theSettableTooltip.set(tooltip, null);
				return this;
			}

			public MigPanelPopulatorField<F, E> withPostLabel(String descrip) {
				thePostLabel = new JLabel(descrip);
				return this;
			}

			public MigPanelPopulatorField<F, E> withPostLabel(ObservableValue<String> descrip) {
				thePostLabel = new JLabel();
				descrip.changes().takeUntil(theUntil).act(evt -> thePostLabel.setText(evt.getNewValue()));
				return this;
			}

			// public MigPanelPopulatorField<F, E> andThen() { // TODO signature?
			// }

			public MigPanelPopulatorField<F, E> grow() {
				isGrow = true;
				return this;
			}

			protected MigFieldPanelPopulator done() {
				JLabel fieldNameLabel = new JLabel(theFieldName.get());
				theFieldName.changes().takeUntil(theUntil).act(evt -> fieldNameLabel.setText(evt.getNewValue()));
				theContainer.add(fieldNameLabel, "align right");
				StringBuilder constraints = new StringBuilder();
				if (isGrow)
					constraints.append("growx, pushx");
				if (thePostLabel == null) {
					if (constraints.length() > 0)
						constraints.append(", ");
					constraints.append("span, wrap");
				}
				Component component = getComponent();
				theContainer.add(component, constraints.toString());
				if (thePostLabel != null)
					theContainer.add(thePostLabel, "wrap");
				if (isVisible != null) {
					isVisible.changes().takeUntil(theUntil).act(evt -> {
						fieldNameLabel.setVisible(evt.getNewValue());
						component.setVisible(evt.getNewValue());
						if (thePostLabel != null)
							thePostLabel.setVisible(evt.getNewValue());
					});
				}
				return MigFieldPanelPopulator.this;
			}
		}

		public class MigPanelComboField<F, E> extends MigPanelPopulatorField<F, E> {
			protected Function<? super F, String> theValueTooltip;

			public MigPanelComboField(String fieldName, SettableValue<F> value, E editor) {
				super(fieldName, value, editor);
			}

			public MigPanelComboField<F, E> withValueTooltip(Function<? super F, String> tooltip) {
				theValueTooltip = tooltip;
				return this;
			}

			public String getValueTooltip(F value) {
				return theValueTooltip == null ? null : theValueTooltip.apply(value);
			}

			@Override
			public MigPanelComboField<F, E> withVariableFieldName(ObservableValue<String> fieldName) {
				return (MigPanelComboField<F, E>) super.withVariableFieldName(fieldName);
			}

			@Override
			public MigPanelComboField<F, E> modifyEditor(Consumer<E> modify) {
				return (MigPanelComboField<F, E>) super.modifyEditor(modify);
			}

			@Override
			public MigPanelComboField<F, E> withTooltip(String tooltip) {
				return (MigPanelComboField<F, E>) super.withTooltip(tooltip);
			}

			@Override
			public MigPanelComboField<F, E> withTooltip(ObservableValue<String> tooltip) {
				return (MigPanelComboField<F, E>) super.withTooltip(tooltip);
			}

			@Override
			public MigPanelComboField<F, E> withPostLabel(String descrip) {
				return (MigPanelComboField<F, E>) super.withPostLabel(descrip);
			}

			@Override
			public MigPanelComboField<F, E> withPostLabel(ObservableValue<String> descrip) {
				return (MigPanelComboField<F, E>) super.withPostLabel(descrip);
			}

			@Override
			public MigPanelComboField<F, E> grow() {
				return (MigPanelComboField<F, E>) super.grow();
			}
		}

		public class MigButtonPanel {
			private final List<BiTuple<ObservableValue<String>, ObservableAction<?>>> theActions = new LinkedList<>();

			public MigButtonPanel addButton(String text, ObservableAction<?> action) {
				return addButton(ObservableValue.of(text), action);
			}

			public MigButtonPanel addButton(ObservableValue<String> text, ObservableAction<?> action) {
				theActions.add(new BiTuple<>(text, action));
				return this;
			}

			protected MigFieldPanelPopulator done() {
				JPanel buttonPanel = new JPanel(new JustifiedBoxLayout(false).setMainAlignment(JustifiedBoxLayout.Alignment.CENTER));
				for (BiTuple<ObservableValue<String>, ObservableAction<?>> action : theActions) {
					JButton button = new JButton();
					action.getValue1().changes().takeUntil(theUntil).act(evt -> button.setText(evt.getNewValue()));
					ObservableAction<?> a = action.getValue2();
					button.addActionListener(evt -> a.act(evt));
					buttonPanel.add(button);
				}
				theContainer.add(buttonPanel, "span, growx, wrap");
				return MigFieldPanelPopulator.this;
			}
		}
	}
}

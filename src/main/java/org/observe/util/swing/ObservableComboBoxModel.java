package org.observe.util.swing;

import java.awt.EventQueue;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.plaf.basic.ComboPopup;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.util.SafeObservableCollection;
import org.observe.util.TypeTokens;
import org.qommons.Transaction;

/**
 * A combo box model backed by an {@link ObservableCollection}
 *
 * @param <E> The type of elements in the model
 */
public class ObservableComboBoxModel<E> extends ObservableListModel<E> implements ComboBoxModel<E> {
	private E theSelectedValue;

	/**
	 * @param values The observable collection to back this model
	 */
	public ObservableComboBoxModel(ObservableCollection<E> values) {
		super(values);
	}

	@Override
	public void setSelectedItem(Object anItem) {
		theSelectedValue = TypeTokens.get().cast(getWrapped().getType(), anItem);
	}

	@Override
	public E getSelectedItem() {
		return theSelectedValue;
	}

	/**
	 * Creates and installs a combo box model whose data is backed by an {@link ObservableCollection} and whose selection is governed by a
	 * {@link SettableValue}
	 *
	 * @param comboBox The combo box to install the model into
	 * @param descrip The tooltip description for the combo box (when the selected value is enabled)
	 * @param availableValues The values available for (potential) selection in the combo box
	 * @param selected The selected value that will control the combo box's selection and report it
	 * @return The subscription to {@link Subscription#unsubscribe() unsubscribe} to to cease listening
	 */
	public static <T> Subscription comboFor(JComboBox<T> comboBox, String descrip, ObservableCollection<? extends T> availableValues,
		SettableValue<? super T> selected) {
		return comboFor(comboBox, descrip, null, availableValues, selected);
	}

	/**
	 * Creates and installs a combo box model whose data is backed by an {@link ObservableCollection} and whose selection is governed by a
	 * {@link SettableValue}
	 *
	 * @param comboBox The combo box to install the model into
	 * @param descrip The tooltip description for the combo box (when the selected value is enabled)
	 * @param valueTooltip A function to generate a tooltip for each value in the combo box
	 * @param availableValues The values available for (potential) selection in the combo box
	 * @param selected The selected value that will control the combo box's selection and report it
	 * @return The subscription to {@link Subscription#unsubscribe() unsubscribe} to to cease listening
	 */
	public static <T> Subscription comboFor(JComboBox<T> comboBox, String descrip, Function<? super T, String> valueTooltip,
		ObservableCollection<? extends T> availableValues, SettableValue<? super T> selected) {
		return comboFor(comboBox, ObservableValue.of(TypeTokens.get().STRING, descrip), valueTooltip, availableValues, selected);
	}

	/**
	 * Creates and installs a combo box model whose data is backed by an {@link ObservableCollection} and whose selection is governed by a
	 * {@link SettableValue}
	 *
	 * @param comboBox The combo box to install the model into
	 * @param descrip The tooltip description for the combo box (when the selected value is enabled)
	 * @param valueTooltip A function to generate a tooltip for each value in the combo box
	 * @param availableValues The values available for (potential) selection in the combo box
	 * @param selected The selected value that will control the combo box's selection and report it
	 * @return The subscription to {@link Subscription#unsubscribe() unsubscribe} to to cease listening
	 */
	public static <T> Subscription comboFor(JComboBox<T> comboBox, ObservableValue<String> descrip,
		Function<? super T, String> valueTooltip, ObservableCollection<? extends T> availableValues, SettableValue<? super T> selected) {
		List<Subscription> subs = new LinkedList<>();
		SafeObservableCollection<? extends T> safeValues;
		if (availableValues instanceof SafeObservableCollection)
			safeValues = (SafeObservableCollection<? extends T>) availableValues;
		else {
			SimpleObservable<Void> safeUntil = SimpleObservable.build().safe(false).build();
			safeValues = new SafeObservableCollection<>(availableValues, EventQueue::isDispatchThread, EventQueue::invokeLater, safeUntil);
			subs.add(() -> safeUntil.onNext(null));
		}
		ObservableComboBoxModel<? extends T> comboModel = new ObservableComboBoxModel<>(safeValues);
		comboBox.setModel((ComboBoxModel<T>) comboModel);
		boolean[] callbackLock = new boolean[1];
		Consumer<String> checkEnabled = enabled -> {
			comboBox.setEnabled(enabled == null);
			comboBox.setToolTipText(enabled == null ? descrip.get() : enabled);
		};
		subs.add(descrip.changes().act(evt -> {
			if (selected.isEnabled().get() == null)
				comboBox.setToolTipText(evt.getNewValue());
		}));
		// Pretty hacky here, but it's the only way I've found to display tooltips over expanded combo box items
		AccessibleContext accessible = comboBox.getAccessibleContext();
		ComboPopup popup;
		{
			ComboPopup tempPopup = null;
			for (int i = 0; i < accessible.getAccessibleChildrenCount(); i++) {
				Accessible child = accessible.getAccessibleChild(i);
				if (child instanceof ComboPopup) {
					tempPopup = (ComboPopup) child;
					break;
				}
			}
			popup = tempPopup;
		}
		subs.add(ObservableComboBoxModel.<T> hookUpComboData(safeValues, selected, index -> {
			// Ignore update events when the popup is expanded
			if (index != comboBox.getSelectedIndex() || (index >= 0 && comboBox.getSelectedItem() != safeValues.get(index))
				|| !popup.isVisible())
				comboBox.setSelectedIndex(index);
		}, listener -> {
			ItemListener itemListener = evt -> {
				if (evt.getStateChange() != ItemEvent.SELECTED)
					return;
				if (!callbackLock[0]) {
					callbackLock[0] = true;
					try {
						listener.accept((T) evt.getItem(), evt);
					} finally {
						callbackLock[0] = false;
					}
				} else
					checkEnabled.accept(selected.isEnabled().get());
			};
			comboBox.addItemListener(itemListener);
			return () -> comboBox.removeItemListener(itemListener);
		}, checkEnabled));
		subs.add(selected.isEnabled().changes().act(evt -> ObservableSwingUtils.onEQ(() -> checkEnabled.accept(evt.getNewValue()))));

		if (popup != null) {
			JList<T> popupList = popup.getList();
			MouseMotionListener popupMouseListener = new MouseMotionAdapter() {
				@Override
				public void mouseMoved(MouseEvent e) {
					int index = popupList.locationToIndex(e.getPoint());
					if (index < 0) {
						popupList.setToolTipText(null);
						return;
					}
					T item = comboModel.getElementAt(index);
					String tooltip = selected.isAcceptable(item);
					if (tooltip == null && valueTooltip != null)
						tooltip = valueTooltip.apply(item);
					popupList.setToolTipText(tooltip);
				}
			};
			popupList.addMouseMotionListener(popupMouseListener);
			subs.add(() -> ObservableSwingUtils.onEQ(() -> popupList.removeMouseMotionListener(popupMouseListener)));
		}
		return Subscription.forAll(subs);
	}

	/**
	 * Connects UI models and observable structures for the use case of selecting a value from a list of possible values
	 *
	 * @param availableValues The values to select from
	 * @param selected The selected value
	 * @param setSelected Accepts an index in the available values of the new selected value
	 * @param acceptSelected Allows this method to listen to the UI selection
	 * @param checkEnabled Called to update the enablement of the widget(s)
	 * @return A subscription to stop all listening
	 */
	public static <T> Subscription hookUpComboData(SafeObservableCollection<? extends T> availableValues, SettableValue<? super T> selected,
		IntConsumer setSelected, Function<BiConsumer<T, Object>, Subscription> acceptSelected, Consumer<String> checkEnabled) {
		List<Subscription> subs = new LinkedList<>();
		boolean[] callbackLock = new boolean[1];
		Object[] currentSelected = new Object[1];
		subs.add(acceptSelected.apply((item, cause) -> {
			currentSelected[0] = item;
			if (!callbackLock[0]) {
				callbackLock[0] = true;
				try {
					selected.set(item, cause);
				} finally {
					callbackLock[0] = false;
				}
			} else
				checkEnabled.accept(selected.isEnabled().get());
		}));
		subs.add(selected.changes().act(evt -> {
			if (callbackLock[0])
				return;
			ObservableSwingUtils.onEQ(() -> {
				if (callbackLock[0])
					return;
				String enabled = selected.isEnabled().get();
				callbackLock[0] = true;
				try (Transaction avT = availableValues.lock(false, null)) {
					int index = availableValues.indexOf(evt.getNewValue());
					if (index >= 0 && index < availableValues.size()) {
						currentSelected[0] = availableValues.get(index);
						setSelected.accept(index);
					} else {
						currentSelected[0] = null;
						setSelected.accept(-1);
					}
				} finally {
					callbackLock[0] = false;
				}
				checkEnabled.accept(enabled);
			});
		}));
		// It is possible for a value to change before the availableValues collection changes to include it.
		// In this case, the above code will find an index of zero
		// and we need to watch the values to set the selected value when it becomes available
		subs.add(availableValues.changes().act(evt -> {
			if (evt.type == CollectionChangeType.remove)
				return;
			Object selectedV = selected.get();
			if (selectedV != null && currentSelected[0] != selectedV) {
				for (CollectionChangeEvent.ElementChange<? extends T> change : evt.getElements()) {
					if (((Equivalence<T>) availableValues.equivalence()).elementEquals(change.newValue, selectedV)) {
						callbackLock[0] = true;
						try {
							setSelected.accept(change.index);
							if (selected.isAcceptable(change.newValue) == null)
								selected.set(change.newValue, evt);
						} finally {
							callbackLock[0] = false;
						}
						break;
					}
				}
			}
		}));
		return Subscription.forAll(subs);
	}
}

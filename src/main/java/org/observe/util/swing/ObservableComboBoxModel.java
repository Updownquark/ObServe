package org.observe.util.swing;

import java.awt.EventQueue;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.function.Consumer;

import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;

import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;

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
		ObservableComboBoxModel<? extends T> comboModel = new ObservableComboBoxModel<>(availableValues);
		comboBox.setModel((ComboBoxModel<T>) comboModel);
		boolean[] callbackLock = new boolean[1];
		Consumer<String> checkEnabled = enabled -> {
			if (enabled == null) {
				enabled = selected.isAcceptable((T) comboBox.getSelectedItem());
			}
			comboBox.setEnabled(enabled == null);
			comboBox.setToolTipText(enabled == null ? descrip : enabled);
		};
		ItemListener itemListener = evt -> {
			if (evt.getStateChange() != ItemEvent.SELECTED)
				return;
			if (!callbackLock[0]) {
				callbackLock[0] = true;
				try {
					selected.set((T) evt.getItem(), evt);
				} finally {
					callbackLock[0] = false;
				}
			} else
				checkEnabled.accept(selected.isEnabled().get());
		};
		comboBox.addItemListener(itemListener);
		Subscription valueSub = selected.changes().act(evt -> {
			if (!callbackLock[0]) {
				String enabled = selected.isEnabled().get();
				EventQueue.invokeLater(() -> {
					callbackLock[0] = true;
					try {
						comboBox.setSelectedItem(evt.getNewValue());
					} finally {
						callbackLock[0] = false;
					}
					checkEnabled.accept(enabled);
				});
			}
		});
		Subscription enabledSub = selected.isEnabled().changes()
			.act(evt -> ObservableSwingUtils.onEQ(() -> checkEnabled.accept(evt.getNewValue())));
		return () -> {
			valueSub.unsubscribe();
			enabledSub.unsubscribe();
			ObservableSwingUtils.onEQ(() -> comboBox.removeItemListener(itemListener));
		};
	}
}

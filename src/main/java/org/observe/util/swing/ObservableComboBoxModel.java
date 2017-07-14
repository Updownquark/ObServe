package org.observe.util.swing;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.function.Consumer;

import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;

import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableList;

/**
 * A combo box model backed by an {@link ObservableList}
 *
 * @param <E>
 *            The type of elements in the model
 */
public class ObservableComboBoxModel<E> extends ObservableListModel<E> implements ComboBoxModel<E> {
	private E theSelectedValue;

	/**
	 * @param values
	 *            The observable list to back this model
	 */
	public ObservableComboBoxModel(ObservableCollection<E> values) {
		super(values);
	}

	@Override
	public void setSelectedItem(Object anItem) {
		theSelectedValue = (E) getWrapped().getType().getRawType().cast(anItem);
	}

	@Override
	public E getSelectedItem() {
		return theSelectedValue;
	}

	public static <T> Subscription comboFor(JComboBox<T> comboBox, String descrip, ObservableCollection<T> availableValues,
		SettableValue<? super T> selected) {
		ObservableComboBoxModel<T> comboModel = new ObservableComboBoxModel<>(availableValues);
		comboBox.setModel(comboModel);
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
		Subscription valueSub = selected.act(evt -> {
			if (!callbackLock[0]) {
				callbackLock[0] = true;
				try {
					comboBox.setSelectedItem(evt.getValue());
				} finally {
					callbackLock[0] = false;
				}
			}
			checkEnabled.accept(selected.isEnabled().get());
		});
		Subscription enabledSub = selected.isEnabled().act(evt -> checkEnabled.accept(evt.getValue()));
		return () -> {
			valueSub.unsubscribe();
			enabledSub.unsubscribe();
			comboBox.removeItemListener(itemListener);
		};
	}
}

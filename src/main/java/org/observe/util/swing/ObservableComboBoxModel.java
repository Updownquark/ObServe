package org.observe.util.swing;

import java.awt.EventQueue;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.plaf.basic.BasicComboPopup;

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
		ObservableComboBoxModel<? extends T> comboModel = new ObservableComboBoxModel<>(availableValues);
		List<Subscription> subs = new LinkedList<>();
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
		subs.add(() -> ObservableSwingUtils.onEQ(() -> comboBox.removeItemListener(itemListener)));
		subs.add(selected.changes().act(evt -> {
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
		}));
		subs.add(selected.isEnabled().changes().act(evt -> ObservableSwingUtils.onEQ(() -> checkEnabled.accept(evt.getNewValue()))));
		if (valueTooltip != null) {
			// Oh so hacky, but it's the only way I've found to do this
			JList<T> popupList = ((BasicComboPopup) comboBox.getUI().getAccessibleChild(comboBox, 0)).getList();
			MouseMotionListener popupMouseListener = new MouseMotionAdapter() {
				@Override
				public void mouseMoved(MouseEvent e) {
					int index = popupList.locationToIndex(e.getPoint());
					if (index < 0) {
						popupList.setToolTipText(null);
						return;
					}
					T item = comboModel.getElementAt(index);
					String tooltip = valueTooltip.apply(item);
					popupList.setToolTipText(tooltip);
				}
			};
			popupList.addMouseMotionListener(popupMouseListener);
			subs.add(() -> ObservableSwingUtils.onEQ(() -> popupList.removeMouseMotionListener(popupMouseListener)));
		}
		return Subscription.forAll(subs);
	}
}

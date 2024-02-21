package org.observe.util.swing;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.plaf.basic.ComboPopup;

import org.observe.Equivalence;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.TriFunction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ReentrantNotificationException;

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
		theSelectedValue = (E) anItem;
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
		SettableValue<T> selected) {
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
		ObservableCollection<? extends T> availableValues, SettableValue<T> selected) {
		return comboFor(comboBox, ObservableValue.of(descrip), valueTooltip, availableValues, selected);
	}

	/** A subscription to a combo box hookup situation which also provides the item which the mouse is currently hovering over */
	public interface ComboHookup extends Subscription {
		/** @return The index of the item the mouse is currently hovering over */
		int getHoveredItem();

		/**
		 * @param sub The subscription for the hookup
		 * @param hoveredItem The hovered item index retriever for the hookup
		 * @return The hookup subscription
		 */
		static ComboHookup of(Subscription sub, IntSupplier hoveredItem) {
			return new ComboHookup() {
				@Override
				public void unsubscribe() {
					sub.unsubscribe();
				}

				@Override
				public int getHoveredItem() {
					return hoveredItem.getAsInt();
				}
			};
		}
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
	public static <T> ComboHookup comboFor(JComboBox<T> comboBox, ObservableValue<String> descrip, Function<? super T, String> valueTooltip,
		ObservableCollection<? extends T> availableValues, SettableValue<T> selected) {
		// Setting this obscure property has the effect of preventing change events when the user is using the up or down arrow keys
		// to navigate items while the combo popup is open.
		// The default behavior of combo box is to fire events each time the user moves to a different item in the list.
		// Setting this property means the event will only be fired when the user presses enter on the new selection.
		// This property does not effect mouse behavior.
		comboBox.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);
		SimpleObservable<Void> safeUntil = SimpleObservable.build().build();
		List<Subscription> subs = new LinkedList<>();
		subs.add(() -> safeUntil.onNext(null));
		ObservableCollection<? extends T> safeValues = availableValues.safe(ThreadConstraint.EDT, safeUntil);
		SettableValue<T> safeSelected = selected.safe(ThreadConstraint.EDT, safeUntil);
		ObservableValue<String> safeDescrip = descrip.safe(ThreadConstraint.EDT, safeUntil);
		ObservableComboBoxModel<? extends T> comboModel = new ObservableComboBoxModel<>(safeValues);
		comboBox.setModel((ComboBoxModel<T>) comboModel);
		boolean[] callbackLock = new boolean[1];
		Consumer<String> checkEnabled = enabled -> {
			comboBox.setEnabled(enabled == null);
			comboBox.setToolTipText(enabled == null ? safeDescrip.get() : enabled);
		};
		subs.add(safeDescrip.safe(ThreadConstraint.EDT, safeUntil).changes().act(evt -> {
			if (safeSelected.isEnabled().get() == null)
				comboBox.setToolTipText(evt.getNewValue());
		}));
		// Pretty hacky here, but it's the only way I've found to display tooltips over expanded combo box items
		ComboPopup popup = getComboPopup(comboBox);
		subs.add(ObservableComboBoxModel.<T> hookUpComboData(safeValues, safeSelected, index -> {
			if (index < 0)
				comboBox.setSelectedIndex(-1);
			else if (index >= comboBox.getItemCount())
				return;
			else if (index == comboBox.getSelectedIndex())
				return;
			else if (comboBox.getSelectedItem() == safeValues.get(index))
				return;
			// Ignore update events when the popup is expanded
			else if (popup != null && popup.isVisible())
				return;
			comboBox.setSelectedIndex(index);
		}, listener -> {
			ItemListener itemListener = evt -> {
				if (evt.getStateChange() != ItemEvent.SELECTED)
					return;
				if (!callbackLock[0]) {
					callbackLock[0] = true;
					try {
						if (!listener.apply((T) evt.getItem(), comboBox.getSelectedIndex(), evt)) {
							EventQueue.invokeLater(popup::show);
						}
					} finally {
						callbackLock[0] = false;
					}
				} else
					checkEnabled.accept(safeSelected.isEnabled().get());
			};
			comboBox.addItemListener(itemListener);
			return () -> comboBox.removeItemListener(itemListener);
		}, checkEnabled));
		subs.add(safeSelected.isEnabled().changes().act(evt -> ObservableSwingUtils.onEQ(() -> checkEnabled.accept(evt.getNewValue()))));

		ListCellRenderer<? super T> oldRenderer = comboBox.getRenderer();
		comboBox.setRenderer(new ListCellRenderer<T>() {
			@Override
			public Component getListCellRendererComponent(JList<? extends T> list, T value, int index, boolean isSelected,
				boolean cellHasFocus) {
				Component rendered = oldRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (index >= 0) {
					String enabled = safeSelected.isAcceptable(value);
					rendered.setEnabled(enabled == null);
				}
				return rendered;
			}
		});

		int[] hoveredItem = new int[] { -1 };
		if (popup != null) {
			JList<T> popupList = getPopupList(comboBox, popup);
			class PopupMouseListener extends MouseAdapter {
				private Point lastHover;

				@Override
				public void mouseMoved(MouseEvent e) {
					lastHover = e.getPoint();
					int index = popupList.locationToIndex(lastHover);
					if (index != hoveredItem[0]) {
						hoveredItem[0] = index;
						popupList.repaint();
					}
					_showToolTip();
				}

				@Override
				public void mouseExited(MouseEvent e) {
					lastHover = null;
					if (hoveredItem[0] >= 0) {
						hoveredItem[0] = -1;
						popupList.repaint();
					}
				}

				void showToolTip() {
					if (lastHover == null || !popupList.isShowing())
						return;
					_showToolTip();
				}

				private void _showToolTip() {
					int index = popupList.locationToIndex(lastHover);
					hoveredItem[0] = index;
					if (index < 0) {
						popupList.setToolTipText(null);
						return;
					}
					T item = comboModel.getElementAt(index);
					String tooltip = safeSelected.isAcceptable(item);
					if (tooltip == null && valueTooltip != null)
						tooltip = valueTooltip.apply(item);
					String oldToolTip = popupList.getToolTipText();
					popupList.setToolTipText(tooltip);
					if (tooltip != null && !Objects.equals(oldToolTip, tooltip))
						ObservableSwingUtils.setTooltipVisible(popupList, true);
				}
			}
			PopupMouseListener popupMouseListener = new PopupMouseListener();
			subs.add(availableValues.simpleChanges().act(__ -> popupMouseListener.showToolTip()));
			popupList.addMouseListener(popupMouseListener);
			popupList.addMouseMotionListener(popupMouseListener);
			subs.add(() -> ObservableSwingUtils.onEQ(() -> popupList.removeMouseMotionListener(popupMouseListener)));
		}
		return ComboHookup.of(Subscription.forAll(subs), () -> hoveredItem[0]);
	}

	/**
	 * @param comboBox The combo box to get the popup for
	 * @return The popup for the combo
	 */
	public static ComboPopup getComboPopup(JComboBox<?> comboBox) {
		// Pretty hacky here, but it's the only way I've found to display tooltips over expanded combo box items
		AccessibleContext accessible = comboBox.getAccessibleContext();
		for (int i = 0; i < accessible.getAccessibleChildrenCount(); i++) {
			Accessible child = accessible.getAccessibleChild(i);
			if (child instanceof ComboPopup)
				return (ComboPopup) child;
		}
		return null;
	}

	/**
	 * @param <T> The type of values in the combo box
	 * @param comboBox The combo box to get the list for
	 * @param popup The popup for the combo
	 * @return The list for the combo
	 */
	public static <T> JList<T> getPopupList(JComboBox<T> comboBox, ComboPopup popup){
		if(popup==null)
			popup=getComboPopup(comboBox);
		if(popup==null)
			return null;
		// I'm doing it this way because in some JDKs, the return value is not generic.
		@SuppressWarnings("rawtypes")
		JList list=popup.getList();
		return list;
	}

	/**
	 * <p>
	 * Connects UI models and observable structures for the use case of selecting a value from a list of possible values
	 * </p>
	 * <p>
	 * This method is package-private because it assumes the given values are {@link ThreadConstraint#EDT EDT}-safe.
	 * </p>
	 *
	 * @param availableValues The values to select from
	 * @param selected The selected value
	 * @param setSelected Accepts an index in the available values of the new selected value
	 * @param acceptSelected Allows this method to listen to the UI selection
	 * @param checkEnabled Called to update the enablement of the widget(s)
	 * @return A subscription to stop all listening
	 */
	static <T> Subscription hookUpComboData(ObservableCollection<? extends T> availableValues, SettableValue<T> selected,
		IntConsumer setSelected, Function<TriFunction<T, Integer, Object, Boolean>, Subscription> acceptSelected,
		Consumer<String> checkEnabled) {
		List<Subscription> subs = new LinkedList<>();
		boolean[] callbackLock = new boolean[1];
		ElementId[] currentSelectedElement = new ElementId[1];
		Object[] currentSelected = new Object[1];
		subs.add(acceptSelected.apply((item, idx, cause) -> {
			if (!callbackLock[0]) {
				callbackLock[0] = true;
				try {
					if (selected.isAcceptable(item) != null) {
						if (currentSelectedElement[0] == null)
							EventQueue.invokeLater(() -> setSelected.accept(-1));
						else if (currentSelectedElement[0].isPresent())
							EventQueue.invokeLater(() -> setSelected.accept(availableValues.getElementsBefore(currentSelectedElement[0])));
						return false;
					}
					currentSelectedElement[0] = availableValues.getElement(idx).getElementId();
					currentSelected[0] = item;
					selected.set(item, cause);
				} finally {
					callbackLock[0] = false;
				}
			} else {
				currentSelected[0] = item;
				checkEnabled.accept(selected.isEnabled().get());
			}
			return true;
		}));
		subs.add(selected.changes().act(evt -> {
			if (callbackLock[0])
				return;
			if (evt.getNewValue() == null) {
				currentSelectedElement[0] = null;
				currentSelected[0] = null;
				setSelected.accept(-1);
				return;
			}
			String enabled = selected.isEnabled().get();
			callbackLock[0] = true;
			try (Transaction avT = availableValues.lock(false, null)) {
				CollectionElement<? extends T> found = ((ObservableCollection<T>) availableValues).getElement(evt.getNewValue(), true);
				if (found != null) {
					currentSelectedElement[0] = found.getElementId();
					currentSelected[0] = found.get();
					setSelected.accept(availableValues.getElementsBefore(found.getElementId()));
				} else {
					currentSelectedElement[0] = null;
					currentSelected[0] = null;
					setSelected.accept(-1);
				}
			} finally {
				callbackLock[0] = false;
			}
			checkEnabled.accept(enabled);
		}));
		subs.add(selected.isEnabled().noInitChanges().act(evt -> {
			if (callbackLock[0])
				return;
			String enabled = selected.isEnabled().get();
			checkEnabled.accept(enabled);
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
							if (selected.isAcceptable(change.newValue) == null) {
								currentSelected[0] = change.newValue;
								try {
									selected.set(change.newValue, evt);
								} catch (ReentrantNotificationException e) {
									// Have issues where the architecture doesn't have the capability to detect this reentrant situation
								}
							}
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

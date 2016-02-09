package org.observe.util.swing;

import javax.swing.ComboBoxModel;

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
    public ObservableComboBoxModel(ObservableList<E> values) {
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
}

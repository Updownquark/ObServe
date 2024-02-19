package org.observe.util.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import javax.swing.JComponent;

/**
 * This class performs an advanced adjustment of some property of a component. It provides several helpful features:
 * <ul>
 * <li>It listens to the property on the component, ensuring the value is maintained as set in this class.</li>
 * <li>It allows properties to be modified as a function of a source value. E.g. a font can be bold-ed while retaining other characteristics
 * of the source font.</li>
 * <li>It handles changes to properties by outside code (e.g. the UI) and re-sets the property using the updated value as the source for the
 * adjustment.</li>
 * </ul>
 *
 * @param <C> The type of component to manage
 */
public class ComponentPropertyManager<C extends Component> {
	private class ComponentProperty<T> implements PropertyChangeListener {
		private final Function<? super C, T> theGetter;
		private final BiConsumer<? super C, T> theSetter;
		private T theSourceValue;
		private UnaryOperator<T> thePropertyAdjuster;
		private boolean theCallbackLock;
		private int theStamp;

		ComponentProperty(Function<? super C, T> getter, BiConsumer<? super C, T> setter, UnaryOperator<T> adjuster) {
			theGetter = getter;
			theSetter = setter;
			theSourceValue = getter.apply(theComponent);
			adjust(adjuster);
		}

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if (theCallbackLock)
				return;
			theStamp++;
			int newStamp = theStamp;
			theSourceValue = theGetter.apply(theComponent);
			T adjusted = thePropertyAdjuster.apply(theSourceValue);
			if (theSourceValue != adjusted) {
				if (isImmediate)
					setValue(newStamp, adjusted);
				else
					EventQueue.invokeLater(() -> setValue(newStamp, adjusted));
			}
		}

		private void setValue(long newStamp, T adjusted) {
			if (theStamp == newStamp) {
				theCallbackLock = true;
				try {
					theSetter.accept(theComponent, adjusted);
				} finally {
					theCallbackLock = false;
				}
			}
		}

		void adjust(UnaryOperator<T> adjuster) {
			thePropertyAdjuster = adjuster;
			theStamp++;
			T adjusted = thePropertyAdjuster.apply(theSourceValue);
			if (adjusted != theGetter.apply(theComponent)) {
				theCallbackLock = true;
				try {
					theSetter.accept(theComponent, adjusted);
				} finally {
					theCallbackLock = false;
				}
			}
		}

		void remove(String propertyName) {
			theComponent.removePropertyChangeListener(propertyName, this);
			theSetter.accept(theComponent, theSourceValue);
		}
	}

	private final C theComponent;
	private boolean isImmediate;
	private final Map<String, ComponentProperty<?>> theProperties;

	/** @param component The component whose properties to manage */
	public ComponentPropertyManager(C component) {
		theComponent = component;
		theProperties = new LinkedHashMap<>();
	}

	/** @return The component being managed */
	public C getComponent() {
		return theComponent;
	}

	/**
	 * @param immediate Whether, when a managed property is changed outside of this manager's control, to re-set the property to the managed
	 *        value immediately or in an {@link EventQueue#invokeLater(Runnable) invokeLater}.
	 * @return This manager
	 */
	public ComponentPropertyManager<C> setImmediate(boolean immediate) {
		isImmediate = immediate;
		return this;
	}

	/**
	 * @param <T> The type of the property
	 * @param propertyName The beans name of the property
	 * @param getter The getter for the property
	 * @param setter The setter for the property
	 * @param adjuster The adjuster to modify the externally-set value of the property
	 * @return This manager
	 */
	public <T> ComponentPropertyManager<C> setProperty(String propertyName, Function<? super C, T> getter, BiConsumer<? super C, T> setter,
		UnaryOperator<T> adjuster) {
		if (!EventQueue.isDispatchThread()) {
			EventQueue.invokeLater(() -> setProperty(propertyName, getter, setter, adjuster));
			return this;
		}
		ComponentProperty<T> property = (ComponentProperty<T>) theProperties.get(propertyName);
		if (property == null) {
			if (adjuster == null)
				return this;
			property = new ComponentProperty<T>(getter, setter, adjuster);
			theProperties.put(propertyName, property);
			theComponent.addPropertyChangeListener(propertyName, property);
		} else {
			if (adjuster == null) {
				property.remove(propertyName);
				theProperties.remove(propertyName);
			} else
				property.adjust(adjuster);
		}
		return this;
	}

	/**
	 * @param adjuster The adjuster to modify the externally-set font
	 * @return This manager
	 */
	public ComponentPropertyManager<C> setFont(UnaryOperator<Font> adjuster) {
		return setProperty("font", Component::getFont, Component::setFont, adjuster);
	}

	/**
	 * @param fg The foreground for the component, or null to stop managing foreground
	 * @return This manager
	 */
	public ComponentPropertyManager<C> setForeground(Color fg) {
		return setProperty("foreground", Component::getForeground, Component::setForeground, fg == null ? null : __ -> fg);
	}

	/**
	 * @param bg The background for the component, or null to stop managing background
	 * @return This manager
	 */
	public ComponentPropertyManager<C> setBackground(Color bg) {
		return setProperty("background", Component::getBackground, Component::setBackground, bg == null ? null : __ -> bg);
	}

	/**
	 * @param opaque The opacity for the component, or null to stop managing opacity
	 * @return This manager
	 */
	public ComponentPropertyManager<C> setOpaque(Boolean opaque) {
		if (theComponent instanceof JComponent)
			((ComponentPropertyManager<JComponent>) this).setProperty("opaque", JComponent::isOpaque, JComponent::setOpaque,
				opaque == null ? null : __ -> opaque);
		return this;
	}

	void dispose() {
		if (!EventQueue.isDispatchThread()) {
			dispose();
			return;
		}
		for (Map.Entry<String, ComponentProperty<?>> property : theProperties.entrySet())
			property.getValue().remove(property.getKey());
		theProperties.clear();
	}
}

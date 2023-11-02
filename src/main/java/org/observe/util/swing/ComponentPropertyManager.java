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

	public ComponentPropertyManager(C component) {
		theComponent = component;
		theProperties = new LinkedHashMap<>();
	}

	public C getComponent() {
		return theComponent;
	}

	public ComponentPropertyManager<C> setImmediate(boolean immediate) {
		isImmediate = immediate;
		return this;
	}

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

	public ComponentPropertyManager<C> setFont(UnaryOperator<Font> adjuster) {
		return setProperty("font", Component::getFont, Component::setFont, adjuster);
	}

	public ComponentPropertyManager<C> setForeground(Color fg) {
		return setProperty("foreground", Component::getForeground, Component::setForeground, fg == null ? null : __ -> fg);
	}

	public ComponentPropertyManager<C> setBackground(Color bg) {
		return setProperty("background", Component::getBackground, Component::setBackground, bg == null ? null : __ -> bg);
	}

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

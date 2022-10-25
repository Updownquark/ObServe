package org.observe.quick;

import java.awt.Image;

import javax.swing.WindowConstants;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoBaseV0_1;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ExternalModelSet;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.qommons.config.QonfigElement;

public interface QuickDocument extends ExpressoBaseV0_1.AppEnvironment {
	class QuickDocumentImpl implements QuickDocument {
		private final QonfigElement theElement;
		private final QuickHeadSection theHead;
		private final QuickComponentDef theComponent;
		private ValueContainer<SettableValue<?>, SettableValue<String>> theTitle;
		private ValueContainer<SettableValue<?>, SettableValue<Image>> theIcon;
		private ValueContainer<SettableValue<?>, SettableValue<Integer>> theX;
		private ValueContainer<SettableValue<?>, SettableValue<Integer>> theY;
		private ValueContainer<SettableValue<?>, SettableValue<Integer>> theWidth;
		private ValueContainer<SettableValue<?>, SettableValue<Integer>> theHeight;
		private ValueContainer<SettableValue<?>, SettableValue<Boolean>> isVisible;
		private int theCloseAction = WindowConstants.HIDE_ON_CLOSE;

		public QuickDocumentImpl(QonfigElement element, QuickHeadSection head, QuickComponentDef component) {
			theElement = element;
			theHead = head;
			theComponent = component;
		}

		@Override
		public QonfigElement getElement() {
			return theElement;
		}

		@Override
		public QuickHeadSection getHead() {
			return theHead;
		}

		@Override
		public QuickComponentDef getComponent() {
			return theComponent;
		}

		@Override
		public ValueContainer<SettableValue<?>, SettableValue<String>> getTitle() {
			return theTitle;
		}

		@Override
		public void setTitle(ValueContainer<SettableValue<?>, SettableValue<String>> title) {
			theTitle = title;
		}

		@Override
		public ValueContainer<SettableValue<?>, SettableValue<Image>> getIcon() {
			return theIcon;
		}

		@Override
		public void setIcon(ValueContainer<SettableValue<?>, SettableValue<Image>> icon) {
			theIcon = icon;
		}

		@Override
		public ValueContainer<SettableValue<?>, SettableValue<Integer>> getX() {
			return theX;
		}

		@Override
		public ValueContainer<SettableValue<?>, SettableValue<Integer>> getY() {
			return theY;
		}

		@Override
		public ValueContainer<SettableValue<?>, SettableValue<Integer>> getWidth() {
			return theWidth;
		}

		@Override
		public ValueContainer<SettableValue<?>, SettableValue<Integer>> getHeight() {
			return theHeight;
		}

		@Override
		public ValueContainer<SettableValue<?>, SettableValue<Boolean>> getVisible() {
			return isVisible;
		}

		@Override
		public int getCloseAction() {
			return theCloseAction;
		}

		@Override
		public QuickDocument withBounds(//
			ValueContainer<SettableValue<?>, SettableValue<Integer>> x, ValueContainer<SettableValue<?>, SettableValue<Integer>> y, //
			ValueContainer<SettableValue<?>, SettableValue<Integer>> width,
			ValueContainer<SettableValue<?>, SettableValue<Integer>> height) {
			theX = x;
			theY = y;
			theWidth = width;
			theHeight = height;
			return this;
		}

		@Override
		public QuickDocument setVisible(ValueContainer<SettableValue<?>, SettableValue<Boolean>> visible) {
			isVisible = visible;
			return this;
		}

		@Override
		public void setCloseAction(int closeAction) {
			theCloseAction = closeAction;
		}

		@Override
		public QuickUiDef createUI(ExternalModelSet extModels) {
			if (!(extModels.getNameChecker() instanceof ObservableModelSet.JavaNameChecker))
				throw new IllegalArgumentException("Cannot use Quick with models that use anything but a "
					+ ObservableModelSet.JavaNameChecker.class.getName() + " name checker");
			return new QuickUiDef(this, extModels);
		}
	}

	public QonfigElement getElement();

	public QuickHeadSection getHead();

	public QuickComponentDef getComponent();

	@Override
	public ValueContainer<SettableValue<?>, SettableValue<String>> getTitle();

	public void setTitle(ValueContainer<SettableValue<?>, SettableValue<String>> title);

	@Override
	public ValueContainer<SettableValue<?>, SettableValue<Image>> getIcon();

	public void setIcon(ValueContainer<SettableValue<?>, SettableValue<Image>> icon);

	public ValueContainer<SettableValue<?>, SettableValue<Integer>> getX();

	public ValueContainer<SettableValue<?>, SettableValue<Integer>> getY();

	public ValueContainer<SettableValue<?>, SettableValue<Integer>> getWidth();

	public ValueContainer<SettableValue<?>, SettableValue<Integer>> getHeight();

	public QuickDocument withBounds(//
		ValueContainer<SettableValue<?>, SettableValue<Integer>> x, ValueContainer<SettableValue<?>, SettableValue<Integer>> y, //
		ValueContainer<SettableValue<?>, SettableValue<Integer>> width, ValueContainer<SettableValue<?>, SettableValue<Integer>> height);

	public ValueContainer<SettableValue<?>, SettableValue<Boolean>> getVisible();

	public int getCloseAction();

	public QuickDocument setVisible(ValueContainer<SettableValue<?>, SettableValue<Boolean>> visible);

	public void setCloseAction(int closeAction);

	public QuickUiDef createUI(ExternalModelSet extModels);
}
package org.observe.quick;

import java.awt.Image;
import java.util.function.Function;

import javax.swing.WindowConstants;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoBaseV0_1;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ExternalModelSet;
import org.observe.expresso.ObservableModelSet.JavaNameChecker;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigElement;

public interface QuickDocument extends ExpressoBaseV0_1.AppEnvironment {
	class QuickDocumentImpl implements QuickDocument {
		private final QonfigElement theElement;
		private final QuickHeadSection theHead;
		private final QuickComponentDef theComponent;
		private Function<ModelSetInstance, SettableValue<String>> theTitle;
		private Function<ModelSetInstance, SettableValue<Image>> theIcon;
		private Function<ModelSetInstance, SettableValue<Integer>> theX;
		private Function<ModelSetInstance, SettableValue<Integer>> theY;
		private Function<ModelSetInstance, SettableValue<Integer>> theWidth;
		private Function<ModelSetInstance, SettableValue<Integer>> theHeight;
		private Function<ModelSetInstance, SettableValue<Boolean>> isVisible;
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
		public Function<ModelSetInstance, SettableValue<String>> getTitle() {
			return theTitle;
		}
	
		@Override
		public void setTitle(Function<ModelSetInstance, SettableValue<String>> title) {
			theTitle = title;
		}
	
		@Override
		public Function<ModelSetInstance, SettableValue<Image>> getIcon() {
			return theIcon;
		}
	
		@Override
		public void setIcon(Function<ModelSetInstance, SettableValue<Image>> icon) {
			theIcon = icon;
		}
	
		@Override
		public Function<ModelSetInstance, SettableValue<Integer>> getX() {
			return theX;
		}
	
		@Override
		public Function<ModelSetInstance, SettableValue<Integer>> getY() {
			return theY;
		}
	
		@Override
		public Function<ModelSetInstance, SettableValue<Integer>> getWidth() {
			return theWidth;
		}
	
		@Override
		public Function<ModelSetInstance, SettableValue<Integer>> getHeight() {
			return theHeight;
		}
	
		@Override
		public Function<ModelSetInstance, SettableValue<Boolean>> getVisible() {
			return isVisible;
		}
	
		@Override
		public int getCloseAction() {
			return theCloseAction;
		}
	
		@Override
		public QuickDocument withBounds(//
			Function<ModelSetInstance, SettableValue<Integer>> x, Function<ModelSetInstance, SettableValue<Integer>> y, //
			Function<ModelSetInstance, SettableValue<Integer>> width, Function<ModelSetInstance, SettableValue<Integer>> height) {
			theX = x;
			theY = y;
			theWidth = width;
			theHeight = height;
			return this;
		}
	
		@Override
		public QuickDocument setVisible(Function<ModelSetInstance, SettableValue<Boolean>> visible) {
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
	public Function<ModelSetInstance, SettableValue<String>> getTitle();

	public void setTitle(Function<ModelSetInstance, SettableValue<String>> title);

	@Override
	public Function<ModelSetInstance, SettableValue<Image>> getIcon();

	public void setIcon(Function<ModelSetInstance, SettableValue<Image>> icon);

	public Function<ModelSetInstance, SettableValue<Integer>> getX();

	public Function<ModelSetInstance, SettableValue<Integer>> getY();

	public Function<ModelSetInstance, SettableValue<Integer>> getWidth();

	public Function<ModelSetInstance, SettableValue<Integer>> getHeight();

	public QuickDocument withBounds(//
		Function<ModelSetInstance, SettableValue<Integer>> x, Function<ModelSetInstance, SettableValue<Integer>> y, //
		Function<ModelSetInstance, SettableValue<Integer>> width, Function<ModelSetInstance, SettableValue<Integer>> height);

	public Function<ModelSetInstance, SettableValue<Boolean>> getVisible();

	public int getCloseAction();

	public QuickDocument setVisible(Function<ModelSetInstance, SettableValue<Boolean>> visible);

	public void setCloseAction(int closeAction);

	public QuickUiDef createUI(ExternalModelSet extModels);
}
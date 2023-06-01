package org.observe.quick;

import java.awt.Image;

import javax.swing.WindowConstants;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoBaseV0_1;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ExternalModelSet;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.qommons.config.QonfigElement;

public interface QuickDocument extends ExpressoBaseV0_1.AppEnvironment {
	class QuickDocumentImpl implements QuickDocument {
		private final QonfigElement theElement;
		private final QuickHeadSection theHead;
		private final QuickComponentDef theComponent;
		private ModelValueSynth<SettableValue<?>, SettableValue<String>> theTitle;
		private ModelValueSynth<SettableValue<?>, SettableValue<Image>> theIcon;
		private ModelValueSynth<SettableValue<?>, SettableValue<Integer>> theX;
		private ModelValueSynth<SettableValue<?>, SettableValue<Integer>> theY;
		private ModelValueSynth<SettableValue<?>, SettableValue<Integer>> theWidth;
		private ModelValueSynth<SettableValue<?>, SettableValue<Integer>> theHeight;
		private ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible;
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
		public ModelValueSynth<SettableValue<?>, SettableValue<String>> getTitle() {
			return theTitle;
		}

		@Override
		public void setTitle(ModelValueSynth<SettableValue<?>, SettableValue<String>> title) {
			theTitle = title;
		}

		@Override
		public ModelValueSynth<SettableValue<?>, SettableValue<Image>> getIcon() {
			return theIcon;
		}

		@Override
		public void setIcon(ModelValueSynth<SettableValue<?>, SettableValue<Image>> icon) {
			theIcon = icon;
		}

		@Override
		public ModelValueSynth<SettableValue<?>, SettableValue<Integer>> getX() {
			return theX;
		}

		@Override
		public ModelValueSynth<SettableValue<?>, SettableValue<Integer>> getY() {
			return theY;
		}

		@Override
		public ModelValueSynth<SettableValue<?>, SettableValue<Integer>> getWidth() {
			return theWidth;
		}

		@Override
		public ModelValueSynth<SettableValue<?>, SettableValue<Integer>> getHeight() {
			return theHeight;
		}

		@Override
		public ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> getVisible() {
			return isVisible;
		}

		@Override
		public int getCloseAction() {
			return theCloseAction;
		}

		@Override
		public QuickDocument withBounds(//
			ModelValueSynth<SettableValue<?>, SettableValue<Integer>> x, ModelValueSynth<SettableValue<?>, SettableValue<Integer>> y, //
			ModelValueSynth<SettableValue<?>, SettableValue<Integer>> width,
			ModelValueSynth<SettableValue<?>, SettableValue<Integer>> height) {
			theX = x;
			theY = y;
			theWidth = width;
			theHeight = height;
			return this;
		}

		@Override
		public QuickDocument setVisible(ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> visible) {
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
	public ModelValueSynth<SettableValue<?>, SettableValue<String>> getTitle();

	public void setTitle(ModelValueSynth<SettableValue<?>, SettableValue<String>> title);

	@Override
	public ModelValueSynth<SettableValue<?>, SettableValue<Image>> getIcon();

	public void setIcon(ModelValueSynth<SettableValue<?>, SettableValue<Image>> icon);

	public ModelValueSynth<SettableValue<?>, SettableValue<Integer>> getX();

	public ModelValueSynth<SettableValue<?>, SettableValue<Integer>> getY();

	public ModelValueSynth<SettableValue<?>, SettableValue<Integer>> getWidth();

	public ModelValueSynth<SettableValue<?>, SettableValue<Integer>> getHeight();

	public QuickDocument withBounds(//
		ModelValueSynth<SettableValue<?>, SettableValue<Integer>> x, ModelValueSynth<SettableValue<?>, SettableValue<Integer>> y, //
		ModelValueSynth<SettableValue<?>, SettableValue<Integer>> width, ModelValueSynth<SettableValue<?>, SettableValue<Integer>> height);

	public ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> getVisible();

	public int getCloseAction();

	public QuickDocument setVisible(ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> visible);

	public void setCloseAction(int closeAction);

	public QuickUiDef createUI(ExternalModelSet extModels);
}
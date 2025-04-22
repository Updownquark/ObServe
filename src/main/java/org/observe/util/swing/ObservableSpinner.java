package org.observe.util.swing;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.SpinnerUI;
import javax.swing.plaf.basic.BasicSpinnerUI;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.util.swing.ObservableTextEditor.ObservableTextEditorWidget;
import org.qommons.Stamped;
import org.qommons.ThreadConstraint;
import org.qommons.io.Format;

/**
 * A {@link JSpinner} backed by a {@link SettableValue} and functions to produce previous and next values from the current value
 *
 * @param <T> The type of the spinner's value
 */
public class ObservableSpinner<T> extends JSpinner implements ObservableTextEditorWidget<T, ObservableSpinner<T>> {
	private final ObservableTextField<T> theTextField;
	private ObservableTextEditor<T> theEditor;

	/**
	 * @param value The value for the model
	 * @param format The format for representing the value as text
	 * @param previousMaker Function to produce a previous value from the current value
	 * @param nextMaker Function to produce a next value from the current value
	 * @param until Observable to stop all listening
	 */
	public ObservableSpinner(SettableValue<T> value, Format<T> format, Function<? super T, ? extends T> previousMaker,
		Function<? super T, ? extends T> nextMaker, Observable<?> until) {
		super(new ObservableSpinnerModel<>(value, previousMaker, nextMaker, until));
		ObservableSpinnerModel<T> model = (ObservableSpinnerModel<T>) getModel();
		theTextField = new ObservableTextField<>(value, format, until);
		theTextField.getEditor().setAdjuster((v0, up) -> up ? model.getNextValue() : model.getPreviousValue());
		setEditor(theTextField);
		SpinnerUI spinnerUI = getUI();
		setUI(new ObservableSpinnerUI<>(model, spinnerUI instanceof BasicSpinnerUI ? (BasicSpinnerUI) spinnerUI : null, until));
	}

	@Override
	public Format<T> getFormat() {
		if (theEditor == null)
			return null;
		return theEditor.getFormat();
	}

	/** @return The text field for this spinner */
	public ObservableTextField<T> getTextField() {
		return theTextField;
	}

	/**
	 * @param editable Whether this spinner's text field should be editable
	 * @return This spinner
	 */
	public ObservableSpinner<T> setTextEditable(boolean editable) {
		theTextField.setEditable(editable);
		return this;
	}

	@Override
	public ObservableSpinner<T> withWarning(Function<? super T, String> warning) {
		if (theEditor != null)
			theEditor.withWarning(warning);
		return this;
	}

	@Override
	public ObservableSpinner<T> clearWarning() {
		if (theEditor != null)
			theEditor.clearWarning();
		return this;
	}

	@Override
	public boolean isSelectAllOnFocus() {
		if (theEditor == null)
			return false;
		return theEditor.isSelectAllOnFocus();
	}

	@Override
	public ObservableSpinner<T> setSelectAllOnFocus(boolean selectAll) {
		if (theEditor != null)
			theEditor.setSelectAllOnFocus(selectAll);
		return this;
	}

	@Override
	public ObservableSpinner<T> setReformatOnCommit(boolean format) {
		if (theEditor != null)
			theEditor.setReformatOnCommit(format);
		return this;
	}

	@Override
	public ObservableSpinner<T> setRevertOnFocusLoss(boolean revert) {
		if (theEditor != null)
			theEditor.setRevertOnFocusLoss(revert);
		return this;
	}

	@Override
	public ObservableSpinner<T> setCommitOnType(boolean commit) {
		if (theEditor != null)
			theEditor.setCommitOnType(commit);
		return this;
	}

	@Override
	public ObservableSpinner<T> setCommitAdjustmentImmediately(boolean commitImmediately) {
		if (theEditor != null)
			theEditor.setCommitAdjustmentImmediately(commitImmediately);
		return this;
	}

	@Override
	public ObservableSpinner<T> onEnter(BiConsumer<? super T, ? super KeyEvent> action) {
		if (theEditor != null)
			theEditor.onEnter(action);
		return this;
	}

	@Override
	public ObservableSpinner<T> withToolTip(String tooltip) {
		if (theEditor != null)
			theEditor.withToolTip(tooltip);
		else
			super.setToolTipText(tooltip);
		return this;
	}

	/**
	 * @param cols The minimum number of columns of text to display
	 * @return This text field
	 */
	public ObservableSpinner<T> withColumns(int cols) {
		theTextField.setColumns(cols);
		return this;
	}

	@Override
	public String getEmptyText() {
		return null;
	}

	@Override
	public ObservableSpinner<T> setEmptyText(String emptyText) {
		System.err.println("Spinner empty text unsupported");
		return this;
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (theEditor != null)
			theEditor.setEnabled(enabled);
		else
			super.setEnabled(enabled);
	}

	@Override
	public void setToolTipText(String text) {

		if (theEditor != null)
			theEditor.setToolTipText(text);
		else
			super.setToolTipText(text);
	}

	/** @return Whether the user has entered text to change this field's value */
	@Override
	public boolean isDirty() {
		if (theEditor == null)
			return false;
		return theEditor.isDirty();
	}

	/** Undoes any edits in this field's text, reverting to the formatted current value */
	@Override
	public void revertEdits() {
		if (theEditor != null)
			theEditor.revertEdits();
	}

	/**
	 * Causes any edits in this field's text to take effect, parsing it and setting it in the value
	 *
	 * @param cause The cause of the action (e.g. a swing event)
	 * @return Whether the edits (if any) were successfully committed to the value or were rejected. If there were no edits, this returns
	 *         true.
	 */
	@Override
	public boolean flushEdits(Object cause) {
		if (theEditor == null)
			return false;
		return theEditor.flushEdits(cause);
	}

	/**
	 * @return The error for the current text of this field. Specifically, either:
	 *         <ol>
	 *         <li>The message in the {@link ParseException} thrown by this field's {@link #getFormat() format} when the text was parsed (or
	 *         "Invalid text" if the exception message was null) or</li>
	 *         <li>The message reported by the value ({@link SettableValue#isAcceptable(Object)}) for the parsed value</li>
	 *         <ol>
	 */
	@Override
	public String getEditError() {
		if (theEditor == null)
			return null;
		return theEditor.getEditError();
	}

	@Override
	public String getEditWarning() {
		if (theEditor == null)
			return null;
		return theEditor.getEditWarning();
	}

	@Override
	public ObservableValue<String> getErrorState() {
		return theTextField.getErrorState();
	}

	@Override
	public ObservableValue<String> getWarningState() {
		return theTextField.getWarningState();
	}

	/** Re-displays the parsing error message from this text field as a tooltip */
	public void redisplayErrorTooltip() {
		if (theEditor != null)
			theEditor.redisplayErrorTooltip();
	}

	/** Re-displays the warning message from this text field as a tooltip */
	public void redisplayWarningTooltip() {
		if (theEditor != null)
			theEditor.redisplayWarningTooltip();
	}

	/**
	 * Sets the step size for a number-typed spinner model
	 *
	 * @param <N> The type of the spinner model
	 * @param model The spinner model to set the step size for
	 * @param stepSize The step size for the spinner model
	 */
	public static <N extends Number> void setStepSize(ObservableSpinnerModel<N> model, Number stepSize) {
		model.setPreviousMaker(new StepSizeAdjustment<>(stepSize, false));
		model.setNextMaker(new StepSizeAdjustment<>(stepSize, true));
	}

	private static class StepSizeAdjustment<N extends Number> implements Function<N, N> {
		private final Number theStepSize;
		private final boolean isAdd;

		StepSizeAdjustment(Number stepSize, boolean add) {
			theStepSize = stepSize;
			isAdd = add;
		}

		@Override
		public N apply(N value) {
			if (value instanceof Double) {
				if (isAdd)
					return (N) Double.valueOf(value.doubleValue() + theStepSize.doubleValue());
				else
					return (N) Double.valueOf(value.doubleValue() - theStepSize.doubleValue());
			} else if (value instanceof Float) {
				if (isAdd)
					return (N) Float.valueOf(value.floatValue() + theStepSize.floatValue());
				else
					return (N) Float.valueOf(value.floatValue() - theStepSize.floatValue());
			} else if (value instanceof Long) {
				if (isAdd)
					return (N) Long.valueOf(value.longValue() + theStepSize.longValue());
				else
					return (N) Long.valueOf(value.longValue() - theStepSize.longValue());
			} else if (value instanceof Integer) {
				if (isAdd)
					return (N) Integer.valueOf(value.intValue() + theStepSize.intValue());
				else
					return (N) Integer.valueOf(value.intValue() - theStepSize.intValue());
			} else
				return value;
		}
	}

	/**
	 * Simple spinner model backed by a {@link SettableValue} and functions to produce next and previous values based on the current value
	 *
	 * @param <T> The type of the value
	 */
	public static class ObservableSpinnerModel<T> implements SpinnerModel {
		private final SettableValue<T> theValue;
		private Function<? super T, ? extends T> thePreviousMaker;
		private Function<? super T, ? extends T> theNextMaker;

		private final List<ChangeListener> theChangeListeners;
		private Subscription theValueChangeSub;

		private long theCachedValueStamp;
		private T theCachedPrevious;
		private boolean isPreviousValid;
		private T theCachedNext;
		private boolean isNextValid;

		/**
		 * @param value The value for the model
		 * @param previousMaker Function to produce a previous value from the current value
		 * @param nextMaker Function to produce a next value from the current value
		 * @param until Observable to stop all listening
		 */
		public ObservableSpinnerModel(SettableValue<T> value, Function<? super T, ? extends T> previousMaker,
			Function<? super T, ? extends T> nextMaker, Observable<?> until) {
			theValue = value;
			thePreviousMaker = previousMaker;
			theNextMaker = nextMaker;
			theChangeListeners = new ArrayList<>();
			markCacheDirty();
			until.take(1).act(__ -> {
				if (theValueChangeSub != null) {
					theValueChangeSub.unsubscribe();
					theValueChangeSub = null;
				}
			});
		}

		/** @return The value backing this model */
		public SettableValue<T> getObservableValue() {
			return theValue;
		}

		/** @param previousMaker The function to control what the down button does on the spinner */
		public void setPreviousMaker(Function<? super T, ? extends T> previousMaker) {
			thePreviousMaker = previousMaker;
			markCacheDirty();
		}

		/** @param nextMaker The function to control what the up button does on the spinner */
		public void setNextMaker(Function<? super T, ? extends T> nextMaker) {
			theNextMaker = nextMaker;
			markCacheDirty();
		}

		@Override
		public T getValue() {
			return theValue.get();
		}

		@Override
		public void setValue(Object value) {
			markCacheDirty();
			if (!Objects.equals(theValue.get(), value))
				theValue.set((T) value, null);
		}

		private void markCacheDirty() {
			theCachedValueStamp = -1776;// Just some random value so we have to update the cache
			theCachedPrevious = null;
			theCachedNext = null;
		}

		@Override
		public T getPreviousValue() {
			updateCache();
			return isPreviousValid ? theCachedPrevious : null;
		}

		/** @return The previous value for the spinner, regardless of whether it is acceptable */
		public T getWouldBePrevious() {
			updateCache();
			return theCachedPrevious;
		}

		@Override
		public T getNextValue() {
			updateCache();
			return isNextValid ? theCachedNext : null;
		}

		/** @return The next value for the spinner, regardless of whether it is acceptable */
		public T getWouldBeNext() {
			updateCache();
			return theCachedNext;
		}

		private void updateCache() {
			long stamp = Stamped.compositeOf2Stamps(theValue.getStamp(), theValue.isEnabled().getStamp());
			if (stamp == theCachedValueStamp)
				return;

			theCachedValueStamp = stamp;
			T value = theValue.get();

			theCachedPrevious = thePreviousMaker.apply(value);
			isPreviousValid = theCachedPrevious != null && theValue.isAcceptable(theCachedPrevious) == null;

			theCachedNext = theNextMaker.apply(value);
			isNextValid = theCachedNext != null && theValue.isAcceptable(theCachedNext) == null;
		}

		@Override
		public void addChangeListener(ChangeListener l) {
			if (theChangeListeners.isEmpty())
				startListening();
			theChangeListeners.add(l);
		}

		@Override
		public void removeChangeListener(ChangeListener l) {
			if (theChangeListeners.remove(l) && theChangeListeners.isEmpty() && theValueChangeSub != null) {
				theValueChangeSub.unsubscribe();
				theValueChangeSub = null;
			}
		}

		private void startListening() {
			// Combine with enabled so when enablement changes, the buttons become enabled/disabled
			theValueChangeSub = theValue.transform(tx -> tx//
				.combineWith(theValue.isEnabled()).combine((v, e) -> v))//
				.safe(ThreadConstraint.EDT)//
				.noInitChanges().act(evt -> {
					ChangeEvent swingEvt = new ChangeEvent(this);
					for (ChangeListener listener : theChangeListeners)
						listener.stateChanged(swingEvt);
				});
		}
	}

	static class ObservableSpinnerUI<T> extends BasicSpinnerUI {
		private final ObservableSpinnerModel<T> theModel;
		private final BasicSpinnerUI theBacking;
		private final Observable<?> theUntil;

		ObservableSpinnerUI(ObservableSpinnerModel<T> model, BasicSpinnerUI backing, Observable<?> until) {
			theModel = model;
			theBacking = backing;
			theUntil = until;
		}

		@Override
		protected Component createPreviousButton() {
			Component button = super.createPreviousButton();
			if (button instanceof JComponent) {
				JComponent jButton = (JComponent) button;
				theModel.getObservableValue().transform(tx -> tx//
					.combineWith(theModel.getObservableValue().isEnabled()).combine((v, e) -> v))//
					.safe(ThreadConstraint.EDT)//
				.changes().takeUntil(theUntil).act(evt -> {
					if (theModel.getPreviousValue() != null) {
						jButton.setEnabled(true);
						jButton.setToolTipText(null);
					} else {
						jButton.setEnabled(false);
						T previousValue = theModel.getWouldBePrevious();
						if (previousValue == null)
							jButton.setToolTipText(null);
						else
							jButton.setToolTipText(theModel.getObservableValue().isAcceptable(previousValue));
					}
				});
			}
			return button;
		}

		@Override
		protected Component createNextButton() {
			Component button = super.createNextButton();
			if (button instanceof JComponent) {
				JComponent jButton = (JComponent) button;
				theModel.getObservableValue().transform(tx -> tx//
					.combineWith(theModel.getObservableValue().isEnabled()).combine((v, e) -> v))//
					.safe(ThreadConstraint.EDT)//
				.changes().takeUntil(theUntil).act(evt -> {
					if (theModel.getNextValue() != null) {
						jButton.setEnabled(true);
						jButton.setToolTipText(null);
					} else {
						jButton.setEnabled(false);
						T nextValue = theModel.getWouldBeNext();
						if (nextValue == null)
							jButton.setToolTipText(null);
						else
							jButton.setToolTipText(theModel.getObservableValue().isAcceptable(nextValue));
					}
				});
			}
			return button;
		}

		@Override
		public int hashCode() {
			return theBacking.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof ObservableSpinnerUI)
				return theBacking.equals(((ObservableSpinnerUI<?>) obj).theBacking);
			return theBacking.equals(obj);
		}

		@Override
		public String toString() {
			return theBacking.toString();
		}
	}
}

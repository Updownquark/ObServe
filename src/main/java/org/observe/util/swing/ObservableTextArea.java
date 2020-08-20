package org.observe.util.swing;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.text.ParseException;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.swing.JEditorPane;
import javax.swing.UIManager;

import org.observe.Observable;
import org.observe.SettableValue;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;

public class ObservableTextArea<E> extends JEditorPane {
	private final ObservableTextEditor<E> theEditor;

	private String theEmptyText;

	/**
	 * @param value The value for the text field to interact with
	 * @param format The format to convert the value to text and back
	 * @param until An observable that, when fired will release this text field's resources
	 */
	public ObservableTextArea(SettableValue<E> value, Format<E> format, Observable<?> until) {
		theEditor = new ObservableTextEditor<E>(this, value, format, until, //
			e -> super.setEnabled(e), //
			tt -> super.setToolTipText(tt)) {
			@Override
			protected ObservableTextEditor<E> setText(String text) {
				ObservableTextArea.this.setText(text);
				return this;
			}
		};
		addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				if (theEmptyText != null)
					repaint();
			}

			@Override
			public void focusLost(FocusEvent e) {
				if (theEmptyText != null)
					repaint();
			}
		});
	}

	/** @return The value controlled by this text field */
	public SettableValue<E> getValue() {
		return theEditor.getValue();
	}

	/** @return The format converting between value and text */
	public Format<E> getFormat() {
		return theEditor.getFormat();
	}

	/**
	 * Configures a warning message as a function of this text field's value. The user will be allowed to enter a value with a warning but a
	 * UI hit will be provided that there may be some problem with the value.
	 *
	 * @param warning The function to provide a warning message or null for no warning
	 * @return This text field
	 */
	public ObservableTextArea<E> withWarning(Function<? super E, String> warning) {
		theEditor.withWarning(warning);
		return this;
	}

	/**
	 * Clears this text field's {@link #withWarning(Function) warning}
	 *
	 * @return This text field
	 */
	public ObservableTextArea<E> clearWarning() {
		theEditor.clearWarning();
		return this;
	}

	/** @return Whether this text field selects all its text when it gains focus */
	public boolean isSelectAllOnFocus() {
		return theEditor.isSelectAllOnFocus();
	}

	/**
	 * @param selectAll Whether this text field should select all its text when it gains focus
	 * @return This text field
	 */
	public ObservableTextArea<E> setSelectAllOnFocus(boolean selectAll) {
		theEditor.setSelectAllOnFocus(selectAll);
		return this;
	}

	/**
	 * @param format Whether this text field should, after successful editing, replace the user-entered text with the formatted value
	 * @return This text field
	 */
	public ObservableTextArea<E> setReformatOnCommit(boolean format) {
		theEditor.setReformatOnCommit(format);
		return this;
	}

	/**
	 * @param revert Whether this text field should, when it loses focus while its text is either not {@link Format#parse(CharSequence)
	 *        parseable} or {@link SettableValue#isAcceptable(Object) acceptable}, revert its text to the formatted current model value. If
	 *        false, the text field will remain in an error state on focus lost.
	 * @return This text field
	 */
	public ObservableTextArea<E> setRevertOnFocusLoss(boolean revert) {
		theEditor.setRevertOnFocusLoss(revert);
		return this;
	}

	/**
	 * @param commit Whether this text field should update the model value with the parsed content of the text field each time the user
	 *        types a key (assuming the text parses correctly and the value is {@link SettableValue#isAcceptable(Object) acceptable}.
	 * @return This text field
	 */
	public ObservableTextArea<E> setCommitOnType(boolean commit) {
		theEditor.setCommitOnType(commit);
		return this;
	}

	/**
	 * If the {@link #getFormat() format} given to this text field is an instance of {@link SpinnerFormat}, this user can adjust the text
	 * field's value incrementally using the up or down arrow keys. The nature and magnitude of the adjustment may depend on the particular
	 * {@link SpinnerFormat} implementation as well as the position of the cursor.
	 *
	 * @param commitImmediately Whether {@link SpinnerFormat#adjust(Object, String, int, boolean) adjustments} to the value should be
	 *        committed to the model value immediately. If false, the adjustments will only be made to the text and the value will be
	 *        committed when the user presses enter or when focus is lost.
	 * @return This text field
	 */
	public ObservableTextArea<E> setCommitAdjustmentImmediately(boolean commitImmediately) {
		theEditor.setCommitAdjustmentImmediately(commitImmediately);
		return this;
	}

	/**
	 * <p>
	 * Sets an action to be performed when the user presses the ENTER key while focused on this text field (after a successful value
	 * commit).
	 * </p>
	 *
	 * <p>
	 * The text field only contains one such listener, so this call replaces any that may have been previously set.
	 * </p>
	 *
	 * @param action The action to perform when the user presses the ENTER key
	 * @return This text field
	 */
	public ObservableTextArea<E> onEnter(BiConsumer<? super E, ? super KeyEvent> action) {
		theEditor.onEnter(action);
		return this;
	}

	/**
	 * @param tooltip The tooltip for this text field (when enabled)
	 * @return This text field
	 */
	public ObservableTextArea<E> withToolTip(String tooltip) {
		theEditor.withToolTip(tooltip);
		return this;
	}

	public ObservableTextArea<E> asHtml() {
		setContentType("text/html");
		return this;
	}

	/** @return The text to display (grayed) when the text field's text is empty */
	public String getEmptyText() {
		return theEmptyText;
	}

	/**
	 * @param emptyText The text to display (grayed) when the text field's text is empty
	 * @return This text field
	 */
	public ObservableTextArea<E> setEmptyText(String emptyText) {
		theEmptyText = emptyText;
		return this;
	}

	@Override
	public void setEnabled(boolean enabled) {
		theEditor.setEnabled(enabled);
	}

	@Override
	public void setToolTipText(String text) {
		theEditor.setToolTipText(text);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		if (!this.hasFocus() && getText().length() == 0 && theEmptyText != null) {
			int height = getHeight();
			Font prev = g.getFont();
			Font italic = prev.deriveFont(Font.ITALIC);
			Color prevColor = g.getColor();
			g.setFont(italic);
			g.setColor(UIManager.getColor("textInactiveText"));
			int h = g.getFontMetrics().getHeight();
			int textBottom = (height - h) / 2 + h - 4;
			int x = this.getInsets().left;
			Graphics2D g2d = (Graphics2D) g;
			RenderingHints hints = g2d.getRenderingHints();
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2d.drawString(theEmptyText, x, textBottom);
			g2d.setRenderingHints(hints);
			g.setFont(prev);
			g.setColor(prevColor);
		}

	}

	/** @return Whether the user has entered text to change this field's value */
	public boolean isDirty() {
		return theEditor.isDirty();
	}

	/** Undoes any edits in this field's text, reverting to the formatted current value */
	public void revertEdits() {
		theEditor.revertEdits();
	}

	/**
	 * Causes any edits in this field's text to take effect, parsing it and setting it in the value
	 *
	 * @param cause The cause of the action (e.g. a swing event)
	 * @return Whether the edits (if any) were successfully committed to the value or were rejected. If there were no edits, this returns
	 *         true.
	 */
	public boolean flushEdits(Object cause) {
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
	public String getEditError() {
		return theEditor.getEditError();
	}

	/** Re-displays the parsing error message from this text field as a tooltip */
	public void redisplayErrorTooltip() {
		theEditor.redisplayErrorTooltip();
	}

	/** Re-displays the warning message from this text field as a tooltip */
	public void redisplayWarningTooltip() {
		theEditor.redisplayWarningTooltip();
	}
}

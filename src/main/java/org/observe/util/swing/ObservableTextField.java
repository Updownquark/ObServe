package org.observe.util.swing;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.ParseException;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.swing.Icon;
import javax.swing.JPasswordField;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.observe.Observable;
import org.observe.SettableValue;
import org.qommons.io.Format;

/**
 * A text field that interacts with a {@link SettableValue}
 *
 * @param <E> The type of the value
 */
public class ObservableTextField<E> extends JPasswordField {
	private final SettableValue<E> theValue;
	private final Format<E> theFormat;
	private Function<? super E, String> theWarning;
	private boolean reformatOnCommit;
	private boolean isInternallyChanging;
	private boolean isDirty;

	private final Color normal_bg = getBackground();
	private final Color disabled_bg;
	private final Color error_bg = new Color(255, 200, 200);
	private final Color error_disabled_bg = new Color(200, 150, 150);
	private final Color warning_bg = new Color(250, 200, 90);
	private final Color warning_disabled_bg = new Color(235, 220, 150);

	private boolean revertOnFocusLoss;
	private boolean commitOnType;
	private String theError;
	private String theWarningMsg;
	private boolean isExternallyEnabled;
	private String theToolTip;
	// Some of this is lifted and modified from https://gmigdos.wordpress.com/2010/03/30/java-a-custom-jtextfield-for-searching/
	private Icon theIcon;
	private Insets dummyInsets;
	private String theEmptyText;

	private BiConsumer<? super E, ? super KeyEvent> theEnterAction;

	/**
	 * @param value The value for the text field to interact with
	 * @param format The format to convert the value to text and back
	 * @param until An observable that, when fired will release this text field's resources
	 */
	public ObservableTextField(SettableValue<E> value, Format<E> format, Observable<?> until) {
		Border border = UIManager.getBorder("TextField.border");
		dummyInsets = border.getBorderInsets(this);
		asPassword((char) 0);

		theValue = value;
		theFormat = format;
		if (until == null)
			until = Observable.empty;
		reformatOnCommit = true;

		isExternallyEnabled = true;
		super.setEnabled(false);
		disabled_bg = getBackground();
		super.setEnabled(true);

		revertOnFocusLoss = true;
		theValue.changes().takeUntil(until).act(evt -> {
			if (!isInternallyChanging && (!hasFocus() || evt.getOldValue() != evt.getNewValue()))
				setValue(evt.getNewValue());
		});
		theValue.isEnabled().changes().takeUntil(until).act(evt -> {
			if (evt.getOldValue() == evt.getNewValue())
				return;
			checkEnabled();
			setErrorState(theError, theWarningMsg);
		});
		getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void removeUpdate(DocumentEvent e) {
				if (!isInternallyChanging) {
					checkText(e);
				}
			}

			@Override
			public void insertUpdate(DocumentEvent e) {
				if (!isInternallyChanging) {
					checkText(e);
				}
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				if (!isInternallyChanging) {
					checkText(e);
				}
			}

			private void checkText(Object cause) {
				isDirty = true;
				try {
					E parsed = theFormat.parse(new String(getPassword()));
					String err = theValue.isAcceptable(parsed);
					setErrorState(err, parsed);
					if (err == null && commitOnType)
						doCommit(parsed, cause, false);
				} catch (ParseException e) {
					setErrorState(e.getMessage() == null ? "Invalid text" : e.getMessage(), (String) null);
				}
			}
		});
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
					revertEdits();
				else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					if (flushEdits(e) && theEnterAction != null)
						theEnterAction.accept(theValue.get(), e);
				}
			}
		});
		addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				selectAll();
				if (theEmptyText != null)
					repaint();
			}

			@Override
			public void focusLost(FocusEvent e) {
				if (theEmptyText != null)
					repaint();
				if (isDirty) {
					isInternallyChanging = true;
					try {
						if (!flushEdits(e) && revertOnFocusLoss) {
							isInternallyChanging = false;
							revertEdits();
						}
					} finally {
						isInternallyChanging = false;
					}
				}
			}
		});
	}

	/** @return The value controlled by this text field */
	public SettableValue<E> getValue() {
		return theValue;
	}

	/** @return The format converting between value and text */
	public Format<E> getFormat() {
		return theFormat;
	}

	/**
	 * Configures a warning message as a function of this text field's value. The user will be allowed to enter a value with a warning but a
	 * UI hit will be provided that there may be some problem with the value.
	 *
	 * @param warning The function to provide a warning message or null for no warning
	 * @return This text field
	 */
	public ObservableTextField<E> withWarning(Function<? super E, String> warning) {
		if (warning == null)
			return clearWarning();
		if (theWarning == null)
			theWarning = warning;
		else {
			Function<? super E, String> oldWarning = theWarning;
			theWarning = v -> {
				String msg = oldWarning.apply(v);
				if (msg == null)
					msg = warning.apply(v);
				return msg;
			};
		}
		return this;
	}

	/**
	 * Clears this text field's {@link #withWarning(Function) warning}
	 *
	 * @return This text field
	 */
	public ObservableTextField<E> clearWarning() {
		theWarning = null;
		return this;
	}

	/**
	 * @param format Whether this text field should, after successful editing, replace the user-entered text with the formatted value
	 * @return This text field
	 */
	public ObservableTextField<E> setReformatOnCommit(boolean format) {
		reformatOnCommit = format;
		return this;
	}

	/**
	 * @param revert Whether this text field should, when it loses focus while its text is either not {@link Format#parse(CharSequence)
	 *        parseable} or {@link SettableValue#isAcceptable(Object) acceptable}, revert its text to the formatted current model value. If
	 *        false, the text field will remain in an error state on focus lost.
	 * @return This text field
	 */
	public ObservableTextField<E> setRevertOnFocusLoss(boolean revert) {
		revertOnFocusLoss = revert;
		return this;
	}

	/**
	 * @param commit Whether this text field should update the model value with the parsed content of the text field each time the user
	 *        types a key (assuming the text parses correctly and the value is {@link SettableValue#isAcceptable(Object) acceptable}.
	 * @return This text field
	 */
	public ObservableTextField<E> setCommitOnType(boolean commit) {
		commitOnType = commit;
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
	public ObservableTextField<E> onEnter(BiConsumer<? super E, ? super KeyEvent> action) {
		theEnterAction = action;
		return this;
	}

	/**
	 * @param tooltip The tooltip for this text field (when enabled)
	 * @return This text field
	 */
	public ObservableTextField<E> withToolTip(String tooltip) {
		setToolTipText(tooltip);
		return this;
	}

	/**
	 * @param cols The minimum number of columns of text to display
	 * @return This text field
	 */
	public ObservableTextField<E> withColumns(int cols) {
		setColumns(cols);
		return this;
	}

	/**
	 * @param echoChar The echo character to show for each typed character of the password, or <code>(char) 0</code> to show the text
	 * @return This text field
	 */
	public ObservableTextField<E> asPassword(char echoChar) {
		setEchoChar(echoChar);
		putClientProperty("JPasswordField.cutCopyAllowed", echoChar == 0); // Allow cut/copy for non-password field
		return this;
	}

	/** @return This text field's icon */
	public Icon getIcon() {
		return theIcon;
	}

	/**
	 * @param icon The icon to display on the left side of the text field
	 * @return This text field
	 */
	public ObservableTextField<E> setIcon(Icon icon) {
		theIcon = icon;
		if (theIcon != null) {
			int textX = 2;

			int iconWidth = theIcon.getIconWidth();
			int x = dummyInsets.left + 5;// this is our icon's x
			textX = x + iconWidth + 2; // this is the x where text should start

			setMargin(new Insets(2, textX, 2, 2));
		} else
			setMargin(dummyInsets);
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
	public ObservableTextField<E> setEmptyText(String emptyText) {
		theEmptyText = emptyText;
		return this;
	}

	@Override
	public void setEnabled(boolean enabled) {
		isExternallyEnabled = enabled;
		checkEnabled();
	}

	@Override
	public void setToolTipText(String text) {
		theToolTip = text;
		setErrorState(theError, theWarningMsg);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		if (theIcon != null) {
			int iconHeight = theIcon.getIconHeight();
			int x = dummyInsets.left + 5;// this is our icon's x
			int y = (this.getHeight() - iconHeight) / 2;
			theIcon.paintIcon(this, g, x, y);
		}

		if (!this.hasFocus() && this.getPassword().length == 0 && theEmptyText != null) {
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
		return isDirty;
	}

	/** Undoes any edits in this field's text, reverting to the formatted current value */
	public void revertEdits() {
		setValue(theValue.get());
	}

	/**
	 * Causes any edits in this field's text to take effect, parsing it and setting it in the value
	 *
	 * @param cause The cause of the action (e.g. a swing event)
	 * @return Whether the edits (if any) were successfully committed to the value or were rejected. If there were no edits, this returns
	 *         true.
	 */
	public boolean flushEdits(Object cause) {
		if (!isDirty)
			return true;
		isDirty = false;
		String text = new String(getPassword());
		E parsed;
		try {
			parsed = theFormat.parse(text);
			setErrorState(null, parsed);
		} catch (ParseException e) {
			setErrorState(e.getMessage() == null ? "Parsing failed" : e.getMessage(), (String) null);
			return false;
		}
		return doCommit(parsed, cause, true);
	}

	private boolean doCommit(E parsed, Object cause, boolean maybeReformat) {
		isInternallyChanging = true;
		try {
			theValue.set(parsed, cause);
			if (maybeReformat && reformatOnCommit)
				setText(theFormat.format(parsed));
			return true;
		} catch (IllegalArgumentException | UnsupportedOperationException e) {
			setErrorState(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), (String) null);
			return false;
		} finally {
			isInternallyChanging = false;
		}
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
		return theError;
	}

	/** Re-displays the parsing error message from this text field as a tooltip */
	public void redisplayErrorTooltip() {
		if (theError != null) {
			super.setToolTipText(theError);
			setTooltipVisible(true);
		}
	}

	/** Re-displays the warning message from this text field as a tooltip */
	public void redisplayWarningTooltip() {
		if (theWarningMsg != null) {
			super.setToolTipText(theWarningMsg);
			setTooltipVisible(true);
		}
	}

	private void setValue(E value) {
		String formatted;
		try {
			formatted = theFormat.format(value);
			setErrorState(null, value);
		} catch (RuntimeException e) {
			System.err.println(ObservableTextField.class.getName() + " could not format value " + value);
			e.printStackTrace();
			setErrorState("Could not format value " + value, value);
			return;
		}
		isInternallyChanging = true;
		try {
			setText(formatted);
		} finally {
			isInternallyChanging = false;
		}
	}

	private void checkEnabled() {
		boolean enabled = isExternallyEnabled && theValue.isEnabled().get() == null;
		if (isEnabled() != enabled)
			super.setEnabled(enabled);
	}

	private void setErrorState(String error, E parsedValue) {
		String warningMsg;
		if (error != null)
			warningMsg = null;
		else if (theWarning == null)
			warningMsg = null;
		else
			warningMsg = theWarning.apply(parsedValue);
		setErrorState(error, warningMsg);
	}

	private void setErrorState(String error, String warningMsg) {
		boolean prevError = theError != null;
		theError = error;
		theWarningMsg = warningMsg;
		String disabled = theValue.isEnabled().get();
		if (theError != null) {
			if (disabled != null)
				setBackground(error_disabled_bg);
			else
				setBackground(error_bg);
		} else if (warningMsg != null) {
			if (disabled != null)
				setBackground(warning_disabled_bg);
			else
				setBackground(warning_bg);
		} else {
			if (disabled != null)
				setBackground(disabled_bg);
			else
				setBackground(normal_bg);
		}

		if (theError != null)
			redisplayErrorTooltip();
		else if (theWarningMsg != null)
			redisplayWarningTooltip();
		else {
			if (prevError)
				setTooltipVisible(false);
			if (disabled != null)
				super.setToolTipText(disabled);
			else
				super.setToolTipText(theToolTip);
		}
	}

	private long toolTipLastDisplayed = 0;

	private void setTooltipVisible(boolean visible) {
		// Super hacky, but not sure how else to do this. Swing's tooltip system doesn't have many hooks into it.
		// This won't work right if the tooltip is already displayed or dismissed due to user interaction within the dismiss delay,
		// but will start working correctly again after the dismiss delay elapses
		// Overall, this approach may be somewhat flawed, but it's about the best I can do,
		// the potential negative consequences are small, and I think it's a very good feature
		long now = System.currentTimeMillis();
		boolean displayed = (now - toolTipLastDisplayed) < ToolTipManager.sharedInstance().getDismissDelay();
		if (visible == displayed) {
			// Tooltip is visible or invisible according to the parameter already
		} else {
			if (visible)
				toolTipLastDisplayed = now;
			else
				toolTipLastDisplayed = 0;
			KeyEvent ke = new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), InputEvent.CTRL_MASK, KeyEvent.VK_F1,
				KeyEvent.CHAR_UNDEFINED);
			dispatchEvent(ke);
		}
	}
}

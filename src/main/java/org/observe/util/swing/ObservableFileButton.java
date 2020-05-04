package org.observe.util.swing;

import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.util.TypeTokens;
import org.qommons.io.FileUtils;
import org.qommons.io.FileUtils.FileDataSource;
import org.qommons.io.FileUtils.MutableFileDataSource;
import org.qommons.threading.QommonsTimer;

import com.google.common.reflect.TypeToken;

/**
 * A file button that, when clicked, displays a file chooser to allow the user to select a file for a {@link SettableValue}
 *
 * @param <F> The {@link FileDataSource} sub-type of the value
 */
public class ObservableFileButton<F extends FileDataSource> extends JButton {
	/**
	 * Decorates an {@link ObservableFileButton} by its value
	 *
	 * @param <F> The {@link ObservableFileButton} sub-type that this decorator understands
	 */
	public interface FileDecorator<F extends FileDataSource> {
		/**
		 * @param file The value of the {@link ObservableFileButton}--may be null
		 * @return The icon to use to decorate the button--may be null
		 */
		Image getIcon(F file);
	}

	/** Default file decoration */
	public static FileDecorator<FileDataSource> DEFAULT_DECORATION = new FileDecorator<FileDataSource>() {
		private final ImageIcon theUnselectedIcon;
		private final ImageIcon theAbsentIcon;
		private final ImageIcon thePresentIcon;

		{
			theUnselectedIcon = ObservableSwingUtils.getFixedIcon(ObservableFileButton.class, "/icons/blueO.png", 6, 6);
			theAbsentIcon = ObservableSwingUtils.getFixedIcon(ObservableFileButton.class, "/icons/redX.png", 6, 6);
			thePresentIcon = ObservableSwingUtils.getFixedIcon(ObservableFileButton.class, "/icons/greenCheck.png", 6, 6);
		}

		@Override
		public Image getIcon(FileDataSource file) {
			ImageIcon icon;
			if (file == null)
				icon = theUnselectedIcon;
			else if (file.exists())
				icon = thePresentIcon;
			else
				icon = theAbsentIcon;
			return icon == null ? null : icon.getImage();
		}
	};

	private final SettableValue<F> theValue;
	private final Observable<?> theUntil;
	private final QommonsTimer.TaskHandle theFileWatchHandle;
	private final JFileChooser theFileChooser;
	private final JPopupMenu thePopup;
	private final Map<String, FileAction> theActions;

	private FileDecorator<? super F> theDecorator;
	private Image theDecoration;
	private long theLastMod;
	private boolean isClearable;

	private String theTooltip;
	private boolean isExternallyEnabled;
	private String theApproveText;
	private F theInitDirectory;
	private String theFileFilterDescrip;

	/**
	 * @param value The value to display and control
	 * @param open Whether the chooser should display an "Open" or "Save" choice
	 * @param until An observable that, when fired will release this button's resources
	 */
	public ObservableFileButton(SettableValue<F> value, boolean open, Observable<?> until) {
		theValue = value;
		theUntil = until;
		theFileWatchHandle = QommonsTimer.getCommonInstance().build(() -> {
			FileDataSource selected = theValue.get();
			if (selected != null && checkFile(selected))
				repaint();
		}, Duration.ofSeconds(1), false).onEDT();
		setIcon(ObservableSwingUtils.getFixedIcon(ObservableFileButton.class, "/icons/disk.png", 16, 16));
		setMargin(new Insets(2, 2, 2, 2));
		theFileChooser = new JFileChooser();
		thePopup = new JPopupMenu();
		theActions = new LinkedHashMap<>();

		theDecorator = DEFAULT_DECORATION;
		openOrSave(open);
		isExternallyEnabled = true;

		theValue.changes().takeUntil(theUntil).act(evt -> {
			theFileWatchHandle.setActive(evt.getNewValue() != null);
			EventQueue.invokeLater(() -> {
				if (evt.getNewValue() != null)
					fileSet(evt.getNewValue());
				if (theDecorator != null) {
					Image old = theDecoration;
					theDecoration = theDecorator.getIcon(evt.getNewValue());
					if (old != theDecoration)
						repaint();
				}
			});
		});
		theValue.isEnabled().changes().takeUntil(theUntil).act(evt -> {
			if (evt.getOldValue() == evt.getNewValue())
				return;
			EventQueue.invokeLater(() -> {
				checkEnabled();
				updateTooltip();
			});
		});
		theFileFilterDescrip = "Files";

		addActionListener(evt -> {
			configureFileChooser(theFileChooser);
			while (true) {
				if (theFileChooser.showDialog(this, theApproveText) == JFileChooser.APPROVE_OPTION) {
					MutableFileDataSource newFile = FileUtils.dataSource(theFileChooser.getSelectedFile());
					if (!TypeTokens.get().isInstance(theValue.getType(), newFile)) {
						// This probably shouldn't happen--the selected file or initial directory shouldn't have any ancestors
						// that aren't an instance of the correct type
						JOptionPane.showMessageDialog(ObservableFileButton.this, newFile + " is not a valid selection", "Invalid selection",
							JOptionPane.ERROR_MESSAGE);
					}
					try {
						theValue.set((F) newFile, evt);
						break;
					} catch (UnsupportedOperationException | IllegalArgumentException e) {
						JOptionPane.showMessageDialog(ObservableFileButton.this, e.getMessage(), "Invalid selection",
							JOptionPane.ERROR_MESSAGE);
					}
				} else
					break;
			}
		});
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (!SwingUtilities.isRightMouseButton(e) || !isEnabled())
					return;
				boolean hasEnabledAction = false;
				for (FileAction action : theActions.values()) {
					if (action.theAction.isEnabled().get() == null) {
						hasEnabledAction = true;
						break;
					}
				}
				if (!hasEnabledAction)
					return;

				thePopup.show(ObservableFileButton.this, e.getX(), e.getY());
			}
		});
		thePopup.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentShown(ComponentEvent e) {
				for (FileAction action : theActions.values())
					action.listenEnabled(true);
			}

			@Override
			public void componentHidden(ComponentEvent e) {
				for (FileAction action : theActions.values())
					action.listenEnabled(false);
			}
		});
	}

	/** @return This button's value */
	public SettableValue<F> getValue() {
		return theValue;
	}

	/**
	 * @param approveText The text for the button in the file chooser to select the file
	 * @return This button
	 */
	public ObservableFileButton<F> withApproveText(String approveText) {
		theApproveText = approveText;
		return this;
	}

	/**
	 * @param openOrSave Whether the file chooser should be an "Open" or "Save" dialog
	 * @return This button
	 */
	public ObservableFileButton<F> openOrSave(boolean openOrSave) {
		return withApproveText(openOrSave ? "Open" : "Save");
	}

	/**
	 * @param fileFilterDescrip The description for the file filter in the file chooser
	 * @return This button
	 */
	public ObservableFileButton<F> withFileFilterDescrip(String fileFilterDescrip) {
		theFileFilterDescrip = fileFilterDescrip;
		return this;
	}

	/**
	 * @param initDirectory The initial directory for the file chooser, if the value is absent
	 * @return This button
	 */
	public ObservableFileButton<F> startAt(F initDirectory) {
		theInitDirectory = initDirectory;
		return this;
	}

	/**
	 * @param decorator The decorator for this button's icon
	 * @return This button
	 */
	public ObservableFileButton<F> withDecorator(FileDecorator<? super F> decorator) {
		theDecorator = decorator;
		theDecoration = decorator == null ? null : decorator.getIcon(theValue.get());
		repaint();
		return this;
	}

	/**
	 * @param clearable Whether the right-click "Clear" action setting the value to null should be available
	 * @return The FileAction for the clear action
	 */
	public FileAction clearable(boolean clearable) {
		if (isClearable == clearable)
			return theActions.get("Clear");
		isClearable = clearable;
		if (clearable) {
			return withAction("Clear", new ObservableAction<Void>() {
				@Override
				public TypeToken<Void> getType() {
					return TypeTokens.get().VOID;
				}

				@Override
				public Void act(Object cause) throws IllegalStateException {
					theValue.set(null, cause);
					return null;
				}

				@Override
				public ObservableValue<String> isEnabled() {
					return theValue.map(v -> v == null ? "No file selected" : null);
				}
			}).withToolTip("Clear the file selection");
		} else
			return null;
	}

	/**
	 * @param actionText The text for the action
	 * @param action The action to perform when the user clicks it, or null to get the current action with the given name
	 * @return The FileAction for the action
	 */
	public FileAction withAction(String actionText, ObservableAction<?> action) {
		FileAction fileAction = theActions.get(actionText);
		if (fileAction != null) {
			if (action != null && fileAction.theAction != action)
				throw new IllegalArgumentException("Action " + actionText + " is already declared");
		} else if (action != null) {
			fileAction = new FileAction(actionText, action, new JMenuItem(actionText));
			thePopup.add(fileAction.theMenuItem);
			theActions.put(actionText, fileAction);
		}
		return fileAction;
	}

	@Override
	public void setToolTipText(String tooltip) {
		theTooltip = tooltip;
	}

	/**
	 * @param tooltip The tooltip description to set for this button
	 * @return This button
	 */
	public ObservableFileButton<F> withToolTip(String tooltip) {
		theTooltip = tooltip;
		return this;
	}

	@Override
	public void setEnabled(boolean enabled) {
		isExternallyEnabled = enabled;
		checkEnabled();
	}

	/**
	 * Called when the file value changes to initialize any variables that will need to be checked by {@link #checkFile(FileDataSource)} to
	 * determine if it has changed significantly
	 *
	 * @param file The new file value
	 */
	protected void fileSet(FileDataSource file) {
		theLastMod = file == null ? -1 : file.getLastModified();
	}

	/**
	 * Called periodically on the current file value to determine if any re-rendering may need to be done
	 *
	 * @param file The current file value
	 * @return Whether the file has changed in a way that affects this view
	 */
	protected boolean checkFile(FileDataSource file) {
		long lastMod = file.getLastModified();
		boolean update = false;
		if (theLastMod != lastMod) {
			update = true;
			theLastMod = lastMod;
		}
		return update;
	}

	/** @param chooser The file chooser to configure before displaying in response to the user's click on this button */
	protected void configureFileChooser(JFileChooser chooser) {
		F value = getValue().get();
		if (value != null) {
			File f = FileUtils.asFile(value);
			chooser.setCurrentDirectory(f.getParentFile());
			chooser.setSelectedFile(f);
		} else if (theInitDirectory != null)
			chooser.setCurrentDirectory(FileUtils.asFile(theInitDirectory));
		chooser.setMultiSelectionEnabled(false);
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		chooser.setApproveButtonToolTipText(theTooltip);
		chooser.setFileFilter(new ValueAcceptableFileFilter<>(theValue, theFileFilterDescrip));
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Image deco = theDecoration;
		if (deco != null) {
			g.drawImage(deco, getWidth() - deco.getWidth(null), 0, null);
			// This code draws the image on the upper-right corner of the icon instead of the corner of the whole button
			// Insets ins = getInsets();
			// int x = getWidth() - ins.right - deco.getWidth(null);
			// g.drawImage(deco, x, ins.top, null);
		}
	}

	private void checkEnabled() {
		boolean enabled = isExternallyEnabled && theValue.isEnabled().get() == null;
		if (isEnabled() != enabled)
			super.setEnabled(enabled);
	}

	private void updateTooltip() {
		String disabled = theValue.isEnabled().get();

		String tooltip;
		F value = theValue.get();
		if (disabled != null)
			tooltip = disabled;
		else if (theTooltip != null) {
			if (value != null)
				tooltip = prepend(theTooltip, value.toString());
			else
				tooltip = theTooltip;
		} else if (value != null)
			tooltip = value.toString();
		else
			tooltip = null;
		super.setToolTipText(tooltip);
	}

	private static String prepend(String tooltip, String filePath) {
		StringBuilder tt = new StringBuilder();
		if (tooltip.length() >= "<html>".length() && tooltip.charAt(0) == '<') {
			String ttPrefix = tooltip.substring(0, "<html>".length());
			if (ttPrefix.toLowerCase().equals("<html>")) {
				tt.append(ttPrefix).append(filePath).append("<br>").append(tooltip, ttPrefix.length(), tooltip.length());
			}
		}
		if (tt.length() == 0)
			tt.append("<html>").append(filePath).append("<br>").append(tooltip);
		return tt.toString();
	}

	/** Represents a right-click menu action on an {@link ObservableFileButton} */
	public class FileAction {
		String theName;
		final ObservableAction<?> theAction;
		final JMenuItem theMenuItem;
		private String theActionTooltip;

		private Subscription theEnablementSub;

		FileAction(String name, ObservableAction<?> action, JMenuItem menuItem) {
			theName = name;
			theAction = action;
			theMenuItem = menuItem;
			theMenuItem.addActionListener(evt -> {
				theAction.act(evt);
			});
		}

		/**
		 * @param name The new name for this action
		 * @return This action
		 */
		public FileAction setName(String name) {
			if (theActions.remove(theName) != null)
				theActions.put(name, this);
			theName = name;
			theMenuItem.setText(name);
			return this;
		}

		/**
		 * @param tooltip The tooltip description for this action
		 * @return This action
		 */
		public FileAction withToolTip(String tooltip) {
			theActionTooltip = tooltip;
			return this;
		}

		/**
		 * Removes this action
		 *
		 * @return This action
		 */
		public FileAction remove() {
			listenEnabled(false);
			thePopup.remove(theMenuItem);
			theActions.remove(theName);
			return this;
		}

		void listenEnabled(boolean listen) {
			if (!listen) {
				if (theEnablementSub != null) {
					theEnablementSub.unsubscribe();
					theEnablementSub = null;
				}
			} else if (theEnablementSub == null) {
				theEnablementSub = theAction.isEnabled().changes().takeUntil(theUntil).act(evt -> {
					theMenuItem.setEnabled(evt.getNewValue() == null);
					if (evt.getNewValue() != null)
						theMenuItem.setToolTipText(evt.getNewValue());
					else
						theMenuItem.setToolTipText(theActionTooltip);
				});
			}
		}
	}

	/**
	 * A file filter that rejects files (not directories, so the user can navigate) that are not acceptable by a value
	 *
	 * @param <F> The {@link FileDataSource} sub-type of the value
	 */
	public static class ValueAcceptableFileFilter<F extends FileDataSource> extends FileFilter {
		private final SettableValue<F> theValue;
		private String theDescription;

		/**
		 * @param value The value
		 * @param descrip The description for this file filter
		 */
		public ValueAcceptableFileFilter(SettableValue<F> value, String descrip) {
			theValue = value;
			theDescription = descrip;
		}

		@Override
		public boolean accept(File f) {
			if (f.isDirectory())
				return true;
			MutableFileDataSource ds = FileUtils.dataSource(f);
			if (!TypeTokens.get().isInstance(theValue.getType(), ds))
				return false;
			return theValue.isAcceptable((F) ds) == null;
		}

		@Override
		public String getDescription() {
			return theDescription;
		}
	}
}

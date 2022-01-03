package org.observe.test;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog.ModalityType;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.TimeZone;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.config.OperationResult;
import org.observe.config.ValueOperationException;
import org.observe.util.TypeTokens;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.PanelPopulation;
import org.qommons.BiTuple;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterList;
import org.qommons.io.Format;

/** A Swing panel to interface with an {@link InteractiveTestingService} to view tests, execute them, and view test results */
public class DefaultInteractiveTestingPanel extends JPanel {
	private final DecimalFormat PERCENT = new DecimalFormat("0.0");

	private final InteractiveTestingService theService;

	private final SettableValue<BetterList<InteractiveTestOrSuite>> theSelectedItem;
	private final SettableValue<TestingState> theCurrentState;
	private final SettableValue<String> theUserWait;
	private SwingUI theUI;

	/** @param service The testing service to execute tests for */
	public DefaultInteractiveTestingPanel(InteractiveTestingService service) {
		theService = service;

		theSelectedItem = SettableValue
			.build(
				TypeTokens.get().keyFor(BetterList.class).<BetterList<InteractiveTestOrSuite>> parameterized(InteractiveTestOrSuite.class))
			.safe(false).build();
		theCurrentState = SettableValue.build(TestingState.class).safe(false).build();
		theUserWait = SettableValue.build(String.class).safe(false).build();

		initComponents();
	}

	private void initComponents() {
		ObservableCollection<TestResult> failures = ObservableCollection.flattenValue(theSelectedItem.map(path -> {
			if (path == null || path.isEmpty())
				return null;
			else if (path.peekLast() instanceof InteractiveTest)
				return ((InteractiveTestSuite) path.get(path.size() - 2)).getTestResults(path.getLast().getName());
			else
				return ((InteractiveTestSuite) path.getLast()).getAllTestResults();
		})).flow().refresh(theCurrentState.noInitChanges()).collect();
		ObservableValue<InteractiveTest> currentTest = theCurrentState.map(state -> state == null ? null : state.getCurrentTest());
		PanelPopulation.populateVPanel(this, null)//
		.addSplit(true, mainSplit -> mainSplit.fill().fillV()
			.firstV(top -> top.addTree(ObservableValue.of(InteractiveTestOrSuite.class, theService), tos -> {
				if (tos instanceof InteractiveTestSuite)
					return ((InteractiveTestSuite) tos).getContent();
				else
					return ObservableCollection.of(InteractiveTestOrSuite.class);
			}, tree -> {
				tree.fill().fillV().withItemName("Test").withLeafTest(tos -> tos instanceof InteractiveTest)
				.renderWith(tos -> tos.getName());
				tree.withSelection(theSelectedItem, false);
			})).lastV(bottom -> bottom//
				.addLabel("Current Test:", currentTest.map(test -> test == null ? "" : test.getName()), Format.TEXT,
					lbl -> lbl.visibleWhen(currentTest.map(test -> test != null)))//
				.addLabel("Status:", currentTest.map(test -> test == null ? "" : test.getStatusMessage()), Format.TEXT,
					lbl -> lbl.visibleWhen(currentTest.map(state -> state != null)))//
				.addLabel("Completion:", currentTest.map(test -> {
					if (test == null)
						return "";
					double length = test.getEstimatedLength();
					double progress = test.getEstimatedProgress();
					if (length <= 0)
						return "";
					else if (progress < 0 || progress > length)
						return "";
					return PERCENT.format(progress / length * 100) + "%";
				}), Format.TEXT, lbl -> lbl.visibleWhen(currentTest.map(test -> {
					if (test == null)
						return false;
					double length = test.getEstimatedLength();
					double progress = test.getEstimatedProgress();
					if (length <= 0)
						return false;
					else if (progress < 0 || progress > length)
						return false;
					return true;
				})))//
				.addLabel(null, theUserWait, Format.TEXT, lbl -> {
					lbl.visibleWhen(theUserWait.map(w -> w != null));
					lbl.decorate(deco -> deco.underline().withForeground(Color.blue).withCursor(Cursor.HAND_CURSOR));
					lbl.onClick(evt -> {
						if (SwingUtilities.isLeftMouseButton(evt))
							theUI.focusDialog();
					});
				})//
				.addTable(failures, fails -> {
					fails.fill().fillV().decorate(deco -> deco.withTitledBorder("Results", Color.black)).withItemName("Test Result");
					fails//
					.withColumn("Date", Instant.class, f -> f.getTestTime(),
						col -> col.formatText(t -> QommonsUtils.printRelativeTime(t.toEpochMilli(), System.currentTimeMillis(),
							QommonsUtils.TimePrecision.MINUTES, TimeZone.getDefault(), 60000, "Just now")))//
					.withColumn("Test Name", String.class, f -> f.getTest().getName(), col -> col.withWidths(100, 200, 900))//
					.withColumn("Completion", String.class, f -> {
						if (f.getFailMessage() == null)
							return "100%";
						if (f.getLength() <= 0 || f.getProgress() > f.getLength())
							return "";
						return PERCENT.format(f.getProgress() / f.getLength() * 100) + "%";
					}, null)//
					.withColumn("Stage", TestStage.class, f -> f.getStage(), null)//
					.withColumn("Message", String.class, fail -> fail.getFailMessage(),
						col -> col.withValueTooltip((f, m) -> m).withWidths(100, 400, 2000))//
					;
				}).addHPanel(null, new JustifiedBoxLayout(false).mainCenter(), buttons -> {
					buttons.fill().addButton(null, __ -> {
						if (theService.getCurrentTest().get() != null)
							theService.cancelTest();
						else
							execute(theSelectedItem.get());
					}, btn -> {
						ObservableValue<BiTuple<InteractiveTestOrSuite, TestingState>> selectedAndCurrent = theSelectedItem.transform(
							(Class<BiTuple<InteractiveTestOrSuite, TestingState>>) (Class<?>) BiTuple.class,
							tx -> tx.combineWith(theService.getCurrentTest()).combine((test, current) -> {
								return new BiTuple<>(test == null ? null : test.peekLast(), current);
							}));
						btn.withText(selectedAndCurrent.map(sac -> {
							if (sac.getValue2() != null)
								return "Stop Test";
							else if (sac.getValue2() instanceof InteractiveTestSuite) {
								if (((InteractiveTestSuite) sac.getValue2()).getParent() == null)
									return "Execute All";
								else
									return "Execute Suite";
							} else
								return "Execute";
						}))//
						.disableWith(selectedAndCurrent.map(sac -> {
							if (sac.getValue2() != null)
								return null; // Can always cancel
							else if (sac.getValue1() == null)
								return "No test or suite selected";
							else
								return null;
						}));
					});
				}))//
			);
	}

	private void execute(BetterList<InteractiveTestOrSuite> path) {
		InteractiveTestSuite sequential = null;
		for (InteractiveTestOrSuite tos : path) {
			if (tos instanceof InteractiveTestSuite && ((InteractiveTestSuite) tos).isSequential()) {
				sequential = (InteractiveTestSuite) tos;
				break;
			}
		}
		if (sequential != null && sequential != path.getLast()) {
			String message;
			if (path.getLast() instanceof InteractiveTest)
				message = "Test ";
			else
				message = "Suite ";
			message += " " + path.getLast().getName() + " is part of sequential suite " + sequential.getName() + ".\n"//
				+ "To execute " + path.getLast().getName() + " all tests in suite " + sequential.getName() + " occurring before "
				+ path.getLast().getName() + " must also be executed.\n"//
				+ "Continue?";
			if (JOptionPane.OK_OPTION != JOptionPane.showConfirmDialog(this, message, "Execute parent suite", JOptionPane.OK_CANCEL_OPTION))
				return;
		}
		InteractiveTestSuite suite;
		InteractiveTest test;
		if (path.getLast() instanceof InteractiveTest) {
			test = (InteractiveTest) path.getLast();
			suite = sequential != null ? sequential : (InteractiveTestSuite) path.get(path.size() - 2);
		} else {
			test = null;
			suite = sequential != null ? sequential : (InteractiveTestSuite) path.getLast();
		}
		if (theUI == null)
			theUI = new SwingUI();
		new Thread(() -> {
			suite.execute(test, theUI, //
				state -> EventQueue.invokeLater(() -> theCurrentState.set(state, null)));
		}, theService.getName() + " Executor").start();
	}

	private final WeakHashMap<Window, JDialog> theWindowDialogs = new WeakHashMap<>();

	private class SwingUI implements UserInteraction {
		private final JDialog theDefaultDialog;
		private final SettableValue<String> theMessage;
		private final SettableValue<String> theYesLabel;
		private final SettableValue<String> theNoLabel;
		private final SettableValue<Boolean> theYesVisible;
		private final SettableValue<Boolean> theNoVisible;
		private volatile boolean isCanceled;

		private JDialog theCurrentDialog;
		private volatile UIResult<?> theCurrentResult;
		private AtomicInteger isClosing;

		SwingUI() {
			isClosing = new AtomicInteger();
			theMessage = SettableValue.build(String.class).safe(false).withValue("").build();
			theYesLabel = SettableValue.build(String.class).safe(false).withValue("Yes").build();
			theNoLabel = SettableValue.build(String.class).safe(false).withValue("No").build();
			theYesVisible = SettableValue.build(boolean.class).safe(false).withValue(false).build();
			theNoVisible = SettableValue.build(boolean.class).safe(false).withValue(false).build();

			theDefaultDialog = createDialog(SwingUtilities.getWindowAncestor(DefaultInteractiveTestingPanel.this));
		}

		private JDialog createDialog(Window owner) {
			JDialog dialog = new JDialog(owner, theService.getName(), ModalityType.MODELESS);
			dialog.addComponentListener(new ComponentAdapter() {
				@Override
				public void componentHidden(ComponentEvent e) {
					if (isClosing.get() == 0) {
						JOptionPane.showMessageDialog(DefaultInteractiveTestingPanel.this, "Please choose an option", "Choose An Option",
							JOptionPane.ERROR_MESSAGE);
						dialog.setVisible(true);
					} else {
						isClosing.getAndDecrement();
						theCurrentDialog = null;
					}
				}
			});

			JPanel imagePanel = new JPanel(new JustifiedBoxLayout(true).mainCenter());
			imagePanel.setName("Images");
			PanelPopulation.populateVPanel(dialog.getContentPane(), Observable.empty())//
			.addLabel(null, theMessage, Format.TEXT, lbl -> lbl.fill())//
			.addComponent(null, imagePanel, lbl -> lbl.fill())//
			.addHPanel(null, new JustifiedBoxLayout(false).mainCenter(), buttons -> {
				buttons.fill()//
				.addButton(null, __ -> {
							if (theCurrentResult == null)
								return;
					((UIResult<Object>) theCurrentResult).fulfilled(true);
					theCurrentResult = null;
					isClosing.getAndIncrement();
					dialog.setVisible(false);
				}, btn -> btn.withText(theYesLabel).visibleWhen(theYesVisible))//
				.addButton(null, __ -> {
							if (theCurrentResult == null)
								return;
					((UIResult<Object>) theCurrentResult).fulfilled(false);
					theCurrentResult = null;
					isClosing.getAndIncrement();
					dialog.setVisible(false);
				}, btn -> btn.withText(theNoLabel).visibleWhen(theNoVisible))//
				.addButton("Cancel Testing", __ -> {
							if (theCurrentResult == null)
								return;
					isCanceled = true;
					((UIResult<Object>) theCurrentResult).failed(new TestCanceledException());
					theCurrentResult = null;
					isClosing.getAndIncrement();
					dialog.setVisible(false);
				}, null)//
				;
			})//
			;

			dialog.pack();
			dialog.setLocationRelativeTo(owner);
			return dialog;
		}

		private JDialog getDialog(Component parent) {
			if (parent == null)
				return theDefaultDialog;
			else
				return theWindowDialogs.computeIfAbsent(
					parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent), w -> createDialog(w));
		}

		private JPanel getImagePanel(JDialog dialog) {
			for (Component c : dialog.getContentPane().getComponents()) {
				if (c instanceof JPanel && c.getName().equals("Images"))
					return (JPanel) c;
			}
			throw new IllegalStateException("No images panel!");
		}

		void focusDialog() {
			if (theCurrentDialog != null && theCurrentDialog.isVisible()) {
				theCurrentDialog.requestFocus();
				theCurrentDialog.toFront();
			}
		}

		@Override
		public OperationResult<Void> userWait(String title, String message, Component parent, Image... images)
			throws TestCanceledException {
			if (EventQueue.isDispatchThread())
				throw new IllegalStateException("This UI cannot be called from the EDT--it should be called from the testing thread");
			if (isCanceled)
				throw new TestCanceledException();
			UIResult<Void> result = new UIResult<>();
			theCurrentResult = result;
			EventQueue.invokeLater(() -> {
				JDialog dialog = getDialog(parent);
				dialog.setTitle(title);
				theMessage.set("<html>" + message.replaceAll("\n", "<br>"), null);
				JPanel imagePanel = getImagePanel(dialog);
				imagePanel.removeAll();
				if (images != null) {
					for (Image image : images) {
						if (image != null)
							imagePanel.add(new JLabel(new ImageIcon(image)));
					}
				}
				theYesVisible.set(false, null);
				theNoVisible.set(false, null);
				dialog.pack();
				dialog.setVisible(true);
			});
			return result;
		}

		@Override
		public OperationResult<Void> instructUser(String title, String message, Component parent, Image... images)
			throws TestCanceledException {
			if (EventQueue.isDispatchThread())
				throw new IllegalStateException("This UI cannot be called from the EDT--it should be called from the testing thread");
			if (isCanceled)
				throw new TestCanceledException();
			UIResult<Void> result = new UIResult<>();
			theCurrentResult = result;
			EventQueue.invokeLater(() -> {
				JDialog dialog = getDialog(parent);
				dialog.setTitle(title);
				theMessage.set("<html>" + message.replaceAll("\n", "<br>"), null);
				JPanel imagePanel = getImagePanel(dialog);
				imagePanel.removeAll();
				if (images != null) {
					for (Image image : images) {
						if (image != null)
							imagePanel.add(new JLabel(new ImageIcon(image)));
					}
				}
				theYesLabel.set("OK", null);
				theYesVisible.set(true, null);
				theNoVisible.set(false, null);
				dialog.pack();
				dialog.setVisible(true);
			});
			return result;
		}

		@Override
		public OperationResult<Boolean> confirm(String title, String question, Component parent, Image... images)
			throws TestCanceledException {
			if (EventQueue.isDispatchThread())
				throw new IllegalStateException("This UI cannot be called from the EDT--it should be called from the testing thread");
			if (isCanceled)
				throw new TestCanceledException();
			UIResult<Boolean> result = new UIResult<>();
			theCurrentResult = result;
			EventQueue.invokeLater(() -> {
				JDialog dialog = getDialog(parent);
				dialog.setTitle(title);
				theMessage.set("<html>" + question.replaceAll("\n", "<br>"), null);
				JPanel imagePanel = getImagePanel(dialog);
				imagePanel.removeAll();
				if (images != null) {
					for (Image image : images) {
						if (image != null)
							imagePanel.add(new JLabel(new ImageIcon(image)));
					}
				}
				theYesLabel.set("Yes", null);
				theNoLabel.set("No", null);
				theYesVisible.set(true, null);
				theNoVisible.set(true, null);
				dialog.pack();
				dialog.setVisible(true);
			});
			return result;
		}

		@Override
		public void cancel() {
			isCanceled = true;
			EventQueue.invokeLater(() -> {
				if (theCurrentDialog != null && theCurrentDialog.isVisible()) {
					isClosing.getAndIncrement();
					theCurrentDialog.setVisible(false);
				}
				if (theCurrentResult != null)
					theCurrentResult.failed(new TestCanceledException());
			});
		}

		@Override
		public void reset() {
			isCanceled = false;
			EventQueue.invokeLater(() -> {
				if (theCurrentDialog != null && theCurrentDialog.isVisible()) {
					isClosing.getAndIncrement();
					theCurrentDialog.setVisible(false);
				}
			});
		}

		class UIResult<T> extends OperationResult.AsyncResult<T> {
			@Override
			protected synchronized boolean begin() {
				return super.begin();
			}

			@Override
			protected synchronized void fulfilled(T value) {
				super.fulfilled(value);
			}

			@Override
			protected synchronized void failed(ValueOperationException failure) {
				super.failed(failure);
			}

			@Override
			public synchronized AsyncResult<T> cancel(boolean mayInterruptIfRunning) {
				super.cancel(mayInterruptIfRunning);
				EventQueue.invokeLater(() -> {
					if (theDefaultDialog.isVisible()) {
						isClosing.getAndIncrement();
						theDefaultDialog.setVisible(false);
					}
				});
				return this;
			}

			@Override
			public OperationResult<T> waitFor() throws InterruptedException {
				EventQueue.invokeLater(() -> theUserWait.set("Waiting for user", null));
				try {
					return super.waitFor();
				} finally {
					EventQueue.invokeLater(() -> theUserWait.set(null, null));
				}
			}

			@Override
			public OperationResult<T> waitFor(long timeout, int nanos) throws InterruptedException {
				EventQueue.invokeLater(() -> theUserWait.set("Waiting for user", null));
				try {
					return super.waitFor(timeout, nanos);
				} finally {
					EventQueue.invokeLater(() -> theUserWait.set(null, null));
				}
			}
		}
	}
}

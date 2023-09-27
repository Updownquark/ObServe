package org.observe.quick.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.EventQueue;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.quick.QuickAbstractWindow;
import org.observe.quick.QuickInterpretation;
import org.observe.quick.QuickTextWidget;
import org.observe.quick.QuickWidget;
import org.observe.quick.QuickWindow;
import org.observe.quick.base.*;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingContainerPopulator;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingDialog;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingDocument;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingLayout;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingTableAction;
import org.observe.quick.swing.QuickSwingTablePopulation.InterpretedSwingTableColumn;
import org.observe.util.TypeTokens;
import org.observe.util.swing.BgFontAdjuster;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.ObservableStyledDocument;
import org.observe.util.swing.ObservableTextArea;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.ContainerPopulator;
import org.observe.util.swing.PanelPopulation.FieldEditor;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.TableBuilder;
import org.observe.util.swing.PanelPopulation.WindowBuilder;
import org.observe.util.swing.WindowPopulation;
import org.qommons.Causable;
import org.qommons.LambdaUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.Transformer;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

/** Quick interpretation of the base toolkit for Swing */
public class QuickBaseSwing implements QuickInterpretation {
	@Override
	public void configure(Transformer.Builder<ExpressoInterpretationException> tx) {
		// Simple widgets
		tx.with((Class<QuickLabel.Interpreted<?, ?>>) (Class<?>) QuickLabel.Interpreted.class, QuickSwingPopulator.class,
			(i, tx2) -> new SwingLabel<>());
		tx.with(QuickSpacer.Interpreted.class, QuickSwingPopulator.class, (i, tx2) -> new SwingSpacer());
		QuickSwingPopulator.<QuickProgressBar, QuickProgressBar.Interpreted> interpretWidget(tx,
			QuickBaseSwing.gen(QuickProgressBar.Interpreted.class), QuickBaseSwing::interpretProgressBar);
		QuickSwingPopulator.<QuickTextField<?>, QuickTextField.Interpreted<?>> interpretWidget(tx,
			QuickBaseSwing.gen(QuickTextField.Interpreted.class), QuickBaseSwing::interpretTextField);
		QuickSwingPopulator.<QuickCheckBox, QuickCheckBox.Interpreted> interpretWidget(tx,
			QuickBaseSwing.gen(QuickCheckBox.Interpreted.class), QuickBaseSwing::interpretCheckBox);
		QuickSwingPopulator.<QuickButton, QuickButton.Interpreted<QuickButton>> interpretWidget(tx, gen(QuickButton.Interpreted.class),
			QuickBaseSwing::interpretButton);
		QuickSwingPopulator.<QuickFileButton, QuickFileButton.Interpreted> interpretWidget(tx, gen(QuickFileButton.Interpreted.class),
			QuickBaseSwing::interpretFileButton);
		QuickSwingPopulator.<QuickComboBox<?>, QuickComboBox.Interpreted<?>> interpretWidget(tx,
			QuickBaseSwing.gen(QuickComboBox.Interpreted.class), QuickBaseSwing::interpretComboBox);
		QuickSwingPopulator.<QuickRadioButtons<?>, QuickRadioButtons.Interpreted<?>> interpretWidget(tx,
			QuickBaseSwing.gen(QuickRadioButtons.Interpreted.class), QuickBaseSwing::interpretRadioButtons);
		tx.with(QuickTextArea.Interpreted.class, QuickSwingPopulator.class, SwingTextArea::new);
		QuickSwingPopulator.<QuickTextArea<?>, QuickTextArea.Interpreted<?>> interpretWidget(tx,
			QuickBaseSwing.gen(QuickTextArea.Interpreted.class), QuickBaseSwing::interpretTextArea);
		tx.with(DynamicStyledDocument.Interpreted.class, QuickSwingDocument.class,
			(qd, tx2) -> QuickBaseSwing.interpretDynamicStyledDoc(qd, tx2));

		// Containers
		tx.with(QuickBox.Interpreted.class, QuickSwingContainerPopulator.class, SwingBox::new);
		QuickSwingPopulator.<QuickFieldPanel, QuickFieldPanel.Interpreted> interpretContainer(tx, gen(QuickFieldPanel.Interpreted.class),
			QuickBaseSwing::interpretFieldPanel);
		QuickSwingPopulator.<QuickWidget, QuickField, QuickField.Interpreted> modifyForAddOn(tx, QuickField.Interpreted.class,
			(Class<QuickWidget.Interpreted<QuickWidget>>) (Class<?>) QuickWidget.Interpreted.class, (ao, qsp, tx2) -> {
				qsp.addModifier((comp, w) -> {
					if (w.getAddOn(QuickField.class).getFieldLabel() != null)
						comp.withFieldName(w.getAddOn(QuickField.class).getFieldLabel());
					if (ao.getDefinition().isFill())
						comp.fill();
				});
			});
		QuickSwingPopulator.<QuickSplit, QuickSplit.Interpreted<?>> interpretContainer(tx, gen(QuickSplit.Interpreted.class),
			QuickBaseSwing::interpretSplit);
		QuickSwingPopulator.<QuickScrollPane, QuickScrollPane.Interpreted> interpretContainer(tx, gen(QuickScrollPane.Interpreted.class),
			QuickBaseSwing::interpretScroll);

		// Box layouts
		tx.with(QuickInlineLayout.Interpreted.class, QuickSwingLayout.class, QuickBaseSwing::interpretInlineLayout);
		tx.with(QuickSimpleLayout.Interpreted.class, QuickSwingLayout.class, QuickBaseSwing::interpretSimpleLayout);
		tx.with(QuickBorderLayout.Interpreted.class, QuickSwingLayout.class, QuickBaseSwing::interpretBorderLayout);

		// Table
		QuickSwingPopulator.<QuickTable<?>, QuickTable.Interpreted<?>> interpretWidget(tx, gen(QuickTable.Interpreted.class),
			QuickBaseSwing::interpretTable);
		tx.with(ValueAction.Single.Interpreted.class, QuickSwingTableAction.class, QuickSwingTablePopulation::interpretValueAction);
		tx.with(ValueAction.Multi.Interpreted.class, QuickSwingTableAction.class, QuickSwingTablePopulation::interpretMultiValueAction);

		// Tabs
		QuickSwingPopulator.<QuickTabs<?>, QuickTabs.Interpreted<?>> interpretWidget(tx, gen(QuickTabs.Interpreted.class),
			QuickBaseSwing::interpretTabs);
		// Tree
		QuickSwingPopulator.<QuickTree<?>, QuickTree.Interpreted<?, ?>> interpretWidget(tx, gen(QuickTree.Interpreted.class),
			QuickBaseSwing::interpretTree);

		// Dialogs
		tx.with(QuickInfoDialog.Interpreted.class, QuickSwingDialog.class, QuickBaseSwing::interpretInfoDialog);
		tx.with(QuickConfirm.Interpreted.class, QuickSwingDialog.class, QuickBaseSwing::interpretConfirm);
		tx.with(QuickFileChooser.Interpreted.class, QuickSwingDialog.class, QuickBaseSwing::interpretFileChooser);
		tx.with(GeneralDialog.Interpreted.class, QuickSwingDialog.class, QuickBaseSwing::interpretGeneralDialog);
	}

	static <T> Class<T> gen(Class<? super T> rawClass) {
		return (Class<T>) rawClass;
	}

	static class SwingBox extends QuickSwingPopulator.QuickSwingContainerPopulator.Abstract<QuickBox> {
		private final QuickSwingLayout<QuickLayout> theLayout;
		private final List<QuickSwingPopulator<QuickWidget>> theContents;

		SwingBox(QuickBox.Interpreted<?> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theLayout = tx.transform(interpreted.getLayout(), QuickSwingLayout.class);
			theContents = BetterList.<QuickWidget.Interpreted<?>, QuickSwingPopulator<QuickWidget>, ExpressoInterpretationException> of2(
				interpreted.getContents().stream(), content -> tx.transform(content, QuickSwingPopulator.class));
			for (QuickSwingPopulator<QuickWidget> content : theContents)
				theLayout.modifyChild(content);
		}

		@Override
		protected void doPopulateContainer(ContainerPopulator<?, ?> panel, QuickBox quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			LayoutManager layoutInst = theLayout.create(quick.getLayout());
			panel.addHPanel(null, layoutInst, p -> {
				component.accept(p);
				int c = 0;
				for (QuickWidget content : quick.getContents()) {
					try {
						theContents.get(c).populate(p, content);
					} catch (ModelInstantiationException e) {
						content.reporting().error(e.getMessage(), e);
					}
					c++;
				}
			});
		}
	}

	static QuickSwingLayout<QuickInlineLayout> interpretInlineLayout(QuickInlineLayout.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) {
		return new QuickSwingLayout<QuickInlineLayout>() {
			@Override
			public LayoutManager create(QuickInlineLayout quick) throws ModelInstantiationException {
				return new JustifiedBoxLayout(quick.isVertical())//
					.setMainAlignment(quick.getMainAlign())//
					.setCrossAlignment(quick.getCrossAlign())//
					.setPadding(quick.getPadding());
			}

			@Override
			public void modifyChild(QuickSwingPopulator<?> child) throws ExpressoInterpretationException {
			}
		};
	}

	static QuickSwingLayout<QuickSimpleLayout> interpretSimpleLayout(QuickSimpleLayout.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) {
		return new QuickSwingLayout<QuickSimpleLayout>() {
			@Override
			public LayoutManager create(QuickSimpleLayout quick) throws ModelInstantiationException {
				return new SimpleLayout();
			}

			@Override
			public void modifyChild(QuickSwingPopulator<?> child) throws ExpressoInterpretationException {
				child.addModifier((comp, w) -> {
					Component[] component = new Component[1];
					comp.modifyComponent(c -> component[0] = c);
					Positionable h = w.getAddOn(Positionable.Horizontal.class);
					Positionable v = w.getAddOn(Positionable.Vertical.class);
					Sizeable width = w.getAddOn(Sizeable.Horizontal.class);
					Sizeable height = w.getAddOn(Sizeable.Vertical.class);
					SimpleLayout.SimpleConstraints childConstraint = simpleConstraints(h, v, width, height);
					comp.withLayoutConstraints(childConstraint);
					Observable.or(h.changes(), v.changes(), width.changes(), height.changes()).act(evt -> {
						if (component[0].getParent() != null)
							component[0].getParent().invalidate();
					});
				});
			}
		};
	}

	static SimpleLayout.SimpleConstraints simpleConstraints(Positionable h, Positionable v, Sizeable width, Sizeable height) {
		return new SimpleLayout.SimpleConstraints(//
			h.getLeading(), h.getCenter(), h.getTrailing(), //
			v.getLeading(), v.getCenter(), v.getTrailing(), //
			width.getSize(), enforceAbsolute(width.getMinimum()), enforceAbsolute(width.getPreferred()),
			enforceAbsolute(width.getMaximum()), //
			height.getSize(), enforceAbsolute(height.getMinimum()), enforceAbsolute(height.getPreferred()),
			enforceAbsolute(height.getMaximum())//
			);
	}

	static Supplier<Integer> enforceAbsolute(Supplier<QuickSize> size) {
		if (size == null)
			return LambdaUtils.constantSupplier(null, "null", null);
		return LambdaUtils.printableSupplier(() -> {
			QuickSize sz = size.get();
			if (sz == null)
				return null;
			else if (sz.percent == 0.0f)
				return sz.pixels;
			else {
				System.err.println("min/pref/max size constraints must be absolute: " + sz);
				return null;
			}
		}, size::toString, null);
	}

	static QuickSwingLayout<QuickBorderLayout> interpretBorderLayout(QuickBorderLayout.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) {
		return new QuickSwingLayout<QuickBorderLayout>() {
			@Override
			public LayoutManager create(QuickBorderLayout quick) throws ModelInstantiationException {
				return new BorderLayout();
			}

			@Override
			public void modifyChild(QuickSwingPopulator<?> child) throws ExpressoInterpretationException {
				child.addModifier((comp, w) -> {
					QuickBorderLayout.Region region = w.getAddOn(QuickBorderLayout.Child.class).getRegion();
					Component[] component = new Component[1];
					comp.modifyComponent(c -> component[0] = c);
					Sizeable size = w.getAddOn(Sizeable.class);
					BorderLayout.Constraints childConstraint = borderConstraints(region, size);
					comp.withLayoutConstraints(childConstraint);
					if (size != null) {
						size.changes().act(evt -> {
							if (component[0].getParent() != null)
								component[0].getParent().invalidate();
						});
					}
				});
			}
		};
	}

	static BorderLayout.Constraints borderConstraints(QuickBorderLayout.Region region, Sizeable size) {
		if (size == null)
			return new BorderLayout.Constraints(region, null, null, null, null);
		return new BorderLayout.Constraints(region, //
			size.getSize(), enforceAbsolute(size.getMinimum()), enforceAbsolute(size.getPreferred()), enforceAbsolute(size.getMaximum()));
	}

	static class SwingSpacer extends QuickSwingPopulator.Abstract<QuickSpacer> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickSpacer quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.spacer(quick.getLength());
			component.accept(null);
		}
	}

	static class SwingLabel<T> extends QuickSwingPopulator.Abstract<QuickLabel<T>> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickLabel<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			Format<T> format = quick.getFormat().get();
			if (format == null)
				format = (Format<T>) QuickTextWidget.TO_STRING_FORMAT;
			panel.addLabel(null, quick.getValue(), format, lbl -> {
				component.accept(lbl);
				if (quick.getIcon() != null)
					lbl.withIcon(quick.getIcon());
			});
		}
	}

	static class SwingProgressBar extends QuickSwingPopulator.Abstract<QuickProgressBar> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickProgressBar quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addProgressBar(null, progress -> {
				component.accept(progress);
				progress.withTaskLength(quick.getMaximum());
				progress.withProgress(quick.getValue());
				progress.withProgressText(quick.getText());
			});
		}
	}

	static class SwingTextField<T> extends QuickSwingPopulator.Abstract<QuickTextField<T>> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickTextField<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			Format<T> format = quick.getFormat().get();
			boolean commitOnType = quick.isCommitOnType();
			Integer columns = quick.getColumns();
			panel.addTextField(null, quick.getValue(), format, tf -> {
				component.accept(tf);
				tf.modifyEditor(tf2 -> {
					try {
						quick.setContext(new QuickEditableTextWidget.EditableTextWidgetContext.Default(//
							tf2.getErrorState(), tf2.getWarningState()));
					} catch (ModelInstantiationException e) {
						quick.reporting().error(e.getMessage(), e);
						return;
					}
					if (commitOnType)
						tf2.setCommitOnType(commitOnType);
					if (columns != null)
						tf2.withColumns(columns);
					quick.isPassword().changes().takeUntil(tf.getUntil()).act(evt -> tf2.asPassword(evt.getNewValue() ? '*' : (char) 0));
					quick.getEmptyText().changes().takeUntil(tf.getUntil()).act(evt -> tf2.setEmptyText(evt.getNewValue()));
					quick.isEditable().changes().takeUntil(tf.getUntil())
					.act(evt -> tf2.setEditable(!Boolean.FALSE.equals(evt.getNewValue())));
				});
			});
		}
	}

	static class SwingTextArea<T> extends QuickSwingPopulator.Abstract<QuickTextArea<T>> {
		private final QuickSwingDocument<T> theDocument;

		SwingTextArea(QuickTextArea.Interpreted<T> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theDocument = tx.transform(interpreted.getDocument(), QuickSwingDocument.class);
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickTextArea<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			Format<T> format = quick.getFormat().get();
			boolean commitOnType = quick.isCommitOnType();
			SettableValue<Integer> rows = quick.getRows();
			Consumer<FieldEditor<ObservableTextArea<T>, ?>> modifier = tf -> {
				component.accept(tf);
				tf.modifyEditor(tf2 -> {
					if (tf2.getErrorState() != null) {
						try {
							quick.setContext(new QuickEditableTextWidget.EditableTextWidgetContext.Default(//
								tf2.getErrorState(), tf2.getWarningState()));
						} catch (ModelInstantiationException e) {
							quick.reporting().error(e.getMessage(), e);
							return;
						}
					}
					if (commitOnType)
						tf2.setCommitOnType(commitOnType);
					rows.changes().takeUntil(tf.getUntil()).act(evt -> tf2.withRows(evt.getNewValue()));
					QuickTextArea.QuickTextAreaContext ctx = new QuickTextArea.QuickTextAreaContext.Default();
					tf2.addMouseListener(pos -> ctx.getMousePosition().set(pos, null));
					quick.setTextAreaContext(ctx);
					quick.isEditable().changes().takeUntil(tf.getUntil())
					.act(evt -> tf2.setEditable(!Boolean.FALSE.equals(evt.getNewValue())));
				});
			};
			if (theDocument != null) {
				ObservableStyledDocument<T> docInst = theDocument.interpret(quick.getDocument(), panel.getUntil());
				panel.addStyledTextArea(null, docInst, tf -> {
					modifier.accept(tf);
					tf.modifyEditor(tf2 -> {
						tf2.addMouseMotionListener(theDocument.mouseListener(quick.getDocument(), docInst, tf2, tf.getUntil()));
						tf2.addCaretListener(theDocument.caretListener(quick.getDocument(), docInst, tf2, tf.getUntil()));
					});
				});
			} else {
				panel.addTextArea(null, quick.getValue(), format, tf -> {
					modifier.accept(tf);
					tf.modifyEditor(tf2 -> {
						try {
							quick.setContext(new QuickEditableTextWidget.EditableTextWidgetContext.Default(//
								tf2.getErrorState(), tf2.getWarningState()));
						} catch (ModelInstantiationException e) {
							quick.reporting().error(e.getMessage(), e);
							return;
						}
						if (commitOnType)
							tf2.setCommitOnType(commitOnType);
						rows.changes().takeUntil(tf.getUntil()).act(evt -> tf2.withRows(evt.getNewValue()));
						QuickTextArea.QuickTextAreaContext ctx = new QuickTextArea.QuickTextAreaContext.Default();
						tf2.addMouseListener(pos -> ctx.getMousePosition().set(pos, null));
						quick.setTextAreaContext(ctx);
						quick.isEditable().changes().takeUntil(tf.getUntil())
						.act(evt -> tf2.setEditable(!Boolean.FALSE.equals(evt.getNewValue())));
					});
				});
			}
		}
	}

	static <T> QuickSwingDocument<T> interpretDynamicStyledDoc(DynamicStyledDocument.Interpreted<T, ?> interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		TypeToken<T> valueType = interpreted.getValueType();
		return new QuickSwingDocument<T>() {
			@Override
			public ObservableStyledDocument<T> interpret(StyledDocument<T> quickDoc, Observable<?> until)
				throws ModelInstantiationException {
				DynamicStyledDocument<T> doc = (DynamicStyledDocument<T>) quickDoc;
				Format<T> format = doc.getFormat();
				ObservableStyledDocument<T> swingDoc = new ObservableStyledDocument<T>(doc.getRoot(), format, ThreadConstraint.EDT, until) {
					@Override
					protected ObservableCollection<? extends T> getChildren(T value) {
						try {
							return doc.getChildren(staCtx(valueType, value));
						} catch (ModelInstantiationException e) {
							doc.reporting().error(e.getMessage(), e);
							return ObservableCollection.of(valueType);
						}
					}

					@Override
					protected void adjustStyle(T value, BgFontAdjuster style) {
						StyledDocument.TextStyle textStyle;
						try {
							textStyle = doc.getStyle(staCtx(valueType, value));
						} catch (ModelInstantiationException e) {
							doc.reporting().error(e.getMessage(), e);
							return;
						}
						if (textStyle != null) {
							QuickCoreSwing.adjustFont(style, textStyle);
							Color bg = textStyle.getBackground().get();
							if (bg != null)
								style.withBackground(bg);
						}
					}
				};
				if (doc.hasPostText()) {
					swingDoc.withPostNodeText(node -> {
						try {
							return doc.getPostText(staCtx(valueType, node)).get();
						} catch (ModelInstantiationException e) {
							doc.reporting().error(e.getMessage(), e);
							return null;
						}
					});
				}
				return swingDoc;
			}

			@Override
			public MouseMotionListener mouseListener(StyledDocument<T> quickDoc, ObservableStyledDocument<T> doc, JTextComponent widget,
				Observable<?> until) {
				return new MouseAdapter() {
					@Override
					public void mouseMoved(MouseEvent e) {
						int docPos = widget.viewToModel(e.getPoint());
						ObservableStyledDocument<T>.DocumentNode node = doc.getNodeAt(docPos);
						((DynamicStyledDocument<T>) quickDoc).getNodeValue().set(node == null ? null : node.getValue(), e);
					}
				};
			}

			@Override
			public CaretListener caretListener(StyledDocument<T> quickDoc, ObservableStyledDocument<T> doc, JTextComponent widget,
				Observable<?> until) {
				boolean[] selectionCallbackLock = new boolean[1];
				SettableValue<T> selectionStartValue = quickDoc.getSelectionStartValue();
				SettableValue<Integer> selectionStartOffset = quickDoc.getSelectionStartOffset();
				SettableValue<T> selectionEndValue = quickDoc.getSelectionEndValue();
				SettableValue<Integer> selectionEndOffset = quickDoc.getSelectionEndOffset();
				Observable.onRootFinish(Observable.or(selectionStartValue.noInitChanges(), selectionStartOffset.noInitChanges(),
					selectionEndValue.noInitChanges(), selectionEndOffset.noInitChanges())).act(__ -> {
						if (selectionCallbackLock[0])
							return;
						selectionCallbackLock[0] = true;
						try {
							T sv = selectionStartValue.get();
							T ev = selectionEndValue.get();
							ObservableStyledDocument<T>.DocumentNode startNode = sv == null ? null : doc.getNodeFor(sv);
							if (sv == null) {
								widget.setCaretPosition(0);
								return;
							}
							ObservableStyledDocument<T>.DocumentNode endNode = sv == null ? null : doc.getNodeFor(ev);
							int startIndex = startNode.getStart() + selectionStartOffset.get();
							if (startIndex < 0)
								startIndex = 0;
							else if (startIndex > widget.getDocument().getLength())
								startIndex = widget.getDocument().getLength();
							widget.setCaretPosition(startIndex);
							Rectangle selectionBounds;
							try {
								selectionBounds = widget.modelToView(startIndex);
							} catch (BadLocationException e) {
								quickDoc.reporting().error(e.getMessage(), e);
								selectionBounds = null;
							}
							if (endNode != null) {
								int endIndex = endNode.getStart() + selectionEndOffset.get();
								if (endIndex >= 0 && endIndex <= widget.getDocument().getLength())
									widget.select(Math.min(startIndex, endIndex), Math.max(startIndex, endIndex));
								Rectangle end;
								try {
									end = widget.modelToView(endIndex);
									if (selectionBounds == null)
										selectionBounds = end;
									else
										selectionBounds = selectionBounds.union(end);
								} catch (BadLocationException e) {
									quickDoc.reporting().error(e.getMessage(), e);
								}
							}
							if (selectionBounds != null) {
								QuickCoreSwing.scrollTo(widget, selectionBounds);
							}
						} finally {
							selectionCallbackLock[0] = false;
						}
					});
				return e -> {
					if (selectionCallbackLock[0])
						return;
					int selStart = Math.min(e.getDot(), e.getMark());
					int selEnd = Math.max(e.getDot(), e.getMark());
					ObservableStyledDocument<T>.DocumentNode startNode = doc.getNodeAt(selStart);
					ObservableStyledDocument<T>.DocumentNode endNode = doc.getNodeAt(selEnd);
					if (selectionStartValue.isAcceptable(startNode == null ? null : startNode.getValue()) == null) {
						int startOffset = startNode == null ? 0 : selStart - startNode.getStart();
						int endOffset = endNode == null ? 0 : selEnd - endNode.getStart();
						selectionCallbackLock[0] = true;
						try (Causable.CausableInUse cause = Causable.cause(e);
							Transaction svt = selectionStartValue.lock(true, cause);
							Transaction sot = selectionStartOffset.lock(true, cause);
							Transaction evt = selectionEndValue.lock(true, cause);
							Transaction eot = selectionEndOffset.lock(true, cause)) {
							selectionStartValue.set(startNode == null ? null : startNode.getValue(), cause);
							if (selectionStartOffset.isAcceptable(startOffset) == null)
								selectionStartOffset.set(startOffset, cause);
							if (selectionEndValue.isAcceptable(endNode == null ? null : endNode.getValue()) == null) {
								selectionEndValue.set(endNode == null ? null : endNode.getValue(), cause);
								if (selectionEndOffset.isAcceptable(endOffset) == null)
									selectionEndOffset.set(endOffset, cause);
							}
						} finally {
							selectionCallbackLock[0] = false;
						}
					}
				};
			}
		};
	}

	static <T> DynamicStyledDocument.StyledTextAreaContext<T> staCtx(TypeToken<T> type, T value) {
		return new DynamicStyledDocument.StyledTextAreaContext.Default<>(SettableValue.of(type, value, "Unmodifiable"));
	}

	static class SwingCheckBox extends QuickSwingPopulator.Abstract<QuickCheckBox> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickCheckBox quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addCheckField(null, quick.getValue(), cb -> component.accept(cb));
		}
	}

	static class SwingFieldPanel extends QuickSwingContainerPopulator.Abstract<QuickFieldPanel> {
		private BetterList<QuickSwingPopulator<QuickWidget>> theContents;

		SwingFieldPanel(QuickFieldPanel.Interpreted interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theContents = BetterList.<QuickWidget.Interpreted<?>, QuickSwingPopulator<QuickWidget>, ExpressoInterpretationException> of2(
				interpreted.getContents().stream(), content -> tx.transform(content, QuickSwingPopulator.class));
		}

		@Override
		protected void doPopulateContainer(ContainerPopulator<?, ?> panel, QuickFieldPanel quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addVPanel(p -> {
				component.accept(p);
				int c = 0;
				for (QuickWidget content : quick.getContents()) {
					try {
						theContents.get(c).populate(p, content);
					} catch (ModelInstantiationException e) {
						content.reporting().error(e.getMessage(), e);
					}
					c++;
				}
			});
		}
	}

	static class SwingButton extends QuickSwingPopulator.Abstract<QuickButton> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickButton quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addButton(null, quick.getAction(), btn -> {
				component.accept(btn);
				if (quick.getText() != null)
					btn.withText(quick.getText());
				if (quick.getIcon() != null)
					btn.withIcon(quick.getIcon());
			});
		}
	}

	static class SwingFileButton extends QuickSwingPopulator.Abst

	static QuickSwingPopulator<QuickFileButton> interpretFileButton(QuickFileButton.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) {
		return QuickSwingPopulator.createWidget((panel, quick) -> {
			panel.addFileField(null, quick.getValue(), quick.isOpen(), null);
		});
	}

	static <T> QuickSwingPopulator<QuickComboBox<T>> interpretComboBox(QuickComboBox.Interpreted<T> interpreted,
		Transformer<ExpressoInterpretationException> tx) {
		return QuickSwingPopulator.createWidget((panel, quick) -> {
			panel.addComboField(null, quick.getValue(), quick.getValues(), null);
		});
	}

	static <T> QuickSwingPopulator<QuickRadioButtons<T>> interpretRadioButtons(QuickRadioButtons.Interpreted<T> interpreted,
		Transformer<ExpressoInterpretationException> tx) {
		return QuickSwingPopulator.createWidget((panel, quick) -> {
			panel.addRadioField(null, quick.getValue(), quick.getValues(), null);
		});
	}

	static <R> QuickSwingPopulator<QuickTable<R>> interpretTable(QuickTable.Interpreted<R> interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		TypeToken<R> rowType = interpreted.getValueType();
		Map<Object, QuickSwingPopulator<QuickWidget>> renderers = new HashMap<>();
		Map<Object, QuickSwingPopulator<QuickWidget>> editors = new HashMap<>();
		boolean[] renderersInitialized = new boolean[1];
		Subscription sub;
		try {
			sub = interpreted.getColumns().subscribe(evt -> {
				boolean renderer = false;
				try {
					switch (evt.getType()) {
					case add:
						renderer = true;
						if (evt.getNewValue().getRenderer() != null)
							renderers.put(evt.getNewValue().getIdentity(),
								tx.transform(evt.getNewValue().getRenderer(), QuickSwingPopulator.class));
						renderer = false;
						if (evt.getNewValue().getEditing() != null && evt.getNewValue().getEditing().getEditor() != null)
							editors.put(evt.getNewValue().getIdentity(),
								tx.transform(evt.getNewValue().getEditing().getEditor(), QuickSwingPopulator.class));
						break;
					case remove:
						renderers.remove(evt.getOldValue().getIdentity());
						editors.remove(evt.getOldValue().getIdentity());
						break;
					case set:
						if (evt.getOldValue().getIdentity() != evt.getNewValue().getIdentity()) {
							renderers.remove(evt.getOldValue().getIdentity());
							editors.remove(evt.getOldValue().getIdentity());
						}
						renderer = true;
						if (evt.getNewValue().getRenderer() != null)
							renderers.put(evt.getNewValue().getIdentity(),
								tx.transform(evt.getNewValue().getRenderer(), QuickSwingPopulator.class));
						renderer = false;
						if (evt.getNewValue().getEditing() != null && evt.getNewValue().getEditing().getEditor() != null)
							editors.put(evt.getNewValue().getIdentity(),
								tx.transform(evt.getNewValue().getEditing().getEditor(), QuickSwingPopulator.class));
						break;
					}
				} catch (ExpressoInterpretationException e) {
					if (renderersInitialized[0])
						(renderer ? evt.getNewValue().getRenderer() : evt.getNewValue().getEditing().getEditor()).reporting()
						.at(e.getErrorOffset()).error(e.getMessage(), e);
					else
						throw new CheckedExceptionWrapper(e);
				}
			}, true);
		} catch (CheckedExceptionWrapper e) {
			if (e.getCause() instanceof ExpressoInterpretationException)
				throw (ExpressoInterpretationException) e.getCause();
			else
				throw new ExpressoInterpretationException(e.getMessage(), interpreted.reporting().getPosition(), 0, e.getCause());
		}
		renderersInitialized[0] = true;
		interpreted.destroyed().act(__ -> sub.unsubscribe());
		boolean[] tableInitialized = new boolean[1];
		// TODO Changes to actions collection?
		List<QuickSwingTableAction<R, ?>> interpretedActions = BetterList.<ValueAction.Interpreted<R, ?>, QuickSwingTableAction<R, ?>, ExpressoInterpretationException> of2(
			interpreted.getActions().stream(), a -> (QuickSwingTableAction<R, ?>) tx.transform(a, QuickSwingTableAction.class));
		QuickSwingPopulator<QuickTable<R>> swingTable = QuickSwingPopulator.createWidget((panel, quick) -> {
			TabularWidget.TabularContext<R> ctx = new TabularWidget.TabularContext.Default<>(rowType,
				quick.reporting().getPosition().toShortString());
			quick.setContext(ctx);
			TableBuilder<R, ?>[] parent = new TableBuilder[1];
			ObservableCollection<InterpretedSwingTableColumn<R, ?>> columns = quick.getAllColumns().flow()//
				.map((Class<InterpretedSwingTableColumn<R, ?>>) (Class<?>) InterpretedSwingTableColumn.class, column -> {
					try {
						return new InterpretedSwingTableColumn<>(quick, column, ctx, tx, panel.getUntil(), () -> parent[0],
							renderers.get(column.getColumnSet().getIdentity()), editors.get(column.getColumnSet().getIdentity()));
					} catch (ModelInstantiationException e) {
						if (tableInitialized[0]) {
							column.getColumnSet().reporting().error(e.getMessage(), e);
							return null;
						} else
							throw new CheckedExceptionWrapper(e);
					}
				})//
				.filter(column -> column == null ? "Column failed to create" : null)//
				.catchUpdates(ThreadConstraint.ANY)//
				// TODO collectActive(onWhat?)
				.collect();
			Subscription columnsSub = columns.subscribe(evt -> {
				if (evt.getNewValue() != null)
					evt.getNewValue().init(columns, evt.getElementId());
			}, true);
			panel.getUntil().take(1).act(__ -> columnsSub.unsubscribe());
			ObservableCollection<CategoryRenderStrategy<R, ?>> crss = columns.flow()//
				.map((Class<CategoryRenderStrategy<R, ?>>) (Class<?>) CategoryRenderStrategy.class, //
					column -> column.getCRS())//
				.collect();
			panel.addTable(quick.getRows(), table -> {
				parent[0] = table;
				table.withColumns(crss);
				if (quick.getSelection() != null)
					table.withSelection(quick.getSelection(), false);
				if (quick.getMultiSelection() != null)
					table.withSelection(quick.getMultiSelection());
				try {
					for (int a = 0; a < interpretedActions.size(); a++)
						((QuickSwingTableAction<R, ValueAction<R>>) interpretedActions.get(a)).addAction(table, quick.getActions().get(a));
				} catch (ModelInstantiationException e) {
					throw new CheckedExceptionWrapper(e);
				}
			});
		});
		tableInitialized[0] = true;
		return swingTable;
	}

	static <T> QuickSwingPopulator<QuickTree<T>> interpretTree(QuickTree.Interpreted<T, ?> interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		QuickSwingPopulator<QuickWidget> renderer, editor;
		if (interpreted.getTreeColumn() == null) {
			renderer = null;
			editor = null;
		} else {
			renderer = interpreted.getTreeColumn().getRenderer() == null ? null
				: tx.transform(interpreted.getTreeColumn().getRenderer(), QuickSwingPopulator.class);
			if (interpreted.getTreeColumn().getEditing() == null || interpreted.getTreeColumn().getEditing().getEditor() == null)
				editor = null;
			else
				editor = tx.transform(interpreted.getTreeColumn().getEditing().getEditor(), QuickSwingPopulator.class);
		}
		return QuickSwingPopulator.createWidget((panel, quick) -> {
			MultiValueRenderable.MultiValueRenderContext<BetterList<T>> ctx = new MultiValueRenderable.MultiValueRenderContext.Default<>(
				TypeTokens.get().keyFor(BetterList.class).<BetterList<T>> parameterized(quick.getNodeType()));
			quick.setContext(ctx);
			InterpretedSwingTableColumn<BetterList<T>, T> treeColumn;
			ValueHolder<PanelPopulation.TreeEditor<T, ?>> treeHolder = new ValueHolder<>();
			if (quick.getTreeColumn() == null)
				treeColumn = null;
			else {
				TabularWidget.TabularContext<BetterList<T>> tableCtx = new TabularWidget.TabularContext<BetterList<T>>() {
					private final SettableValue<Integer> theRow = SettableValue.build(int.class).withDescription("row").withValue(0)
						.build();
					private final SettableValue<Integer> theColumn = SettableValue.build(int.class).withDescription("column").withValue(0)
						.build();

					@Override
					public SettableValue<BetterList<T>> getActiveValue() {
						return ctx.getActiveValue();
					}

					@Override
					public SettableValue<Boolean> isSelected() {
						return ctx.isSelected();
					}

					@Override
					public SettableValue<Integer> getRowIndex() {
						return theRow;
					}

					@Override
					public SettableValue<Integer> getColumnIndex() {
						return theColumn;
					}
				};
				treeColumn = new InterpretedSwingTableColumn<>(quick,
					(QuickTableColumn<BetterList<T>, T>) quick.getTreeColumn().getColumns().getFirst(), tableCtx, tx, panel.getUntil(),
					treeHolder, renderer, editor);
			}
			TypeToken<BetterList<T>> pathType = TypeTokens.get().keyFor(BetterList.class)
				.<BetterList<T>> parameterized(quick.getNodeType());
			panel.addTree3(quick.getModel().getValue(), (parentPath, nodeUntil) -> {
				try {
					return quick.getModel().getChildren(ObservableValue.of(pathType, parentPath), nodeUntil);
				} catch (ModelInstantiationException e) {
					quick.reporting().error("Could not create children for " + parentPath, e);
					return null;
				}
			}, tree -> {
				treeHolder.accept(tree);
				if (quick.getSelection() != null)
					tree.withSelection(quick.getSelection(), false);
				if (quick.getMultiSelection() != null)
					tree.withSelection(quick.getMultiSelection());
				if (treeColumn != null)
					tree.withRender(treeColumn.getCRS());
				tree.withLeafTest2(path -> {
					ctx.getActiveValue().set(path, null);
					return quick.getModel().isLeaf(path);
				});
				tree.withRootVisible(quick.isRootVisible());
			});
		});
	}

	static QuickSwingContainerPopulator<QuickSplit> interpretSplit(QuickSplit.Interpreted<?> interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		BetterList<QuickSwingPopulator<QuickWidget>> contents = BetterList.<QuickWidget.Interpreted<?>, QuickSwingPopulator<QuickWidget>, ExpressoInterpretationException> of2(
			interpreted.getContents().stream(), content -> tx.transform(content, QuickSwingPopulator.class));
		return QuickSwingPopulator.createContainer((panel, quick) -> {
			panel.addSplit(quick.isVertical(), s -> {
				AbstractQuickContainerPopulator populator = new AbstractQuickContainerPopulator() {
					private boolean isFirst = true;

					@Override
					public Observable<?> getUntil() {
						return s.getUntil();
					}

					@Override
					public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
						Consumer<PanelPopulator<JPanel, ?>> hPanel) {
						if (isFirst) {
							isFirst = false;
							s.firstH(layout, p -> hPanel.accept((PanelPopulator<JPanel, ?>) modify(p)));
						} else
							s.lastH(layout, p -> hPanel.accept((PanelPopulator<JPanel, ?>) modify(p)));
						return this;
					}

					@Override
					public AbstractQuickContainerPopulator addVPanel(Consumer<PanelPopulator<JPanel, ?>> vPanel) {
						if (isFirst) {
							isFirst = false;
							s.firstV(p -> vPanel.accept((PanelPopulator<JPanel, ?>) modify(p)));
							s.firstV((Consumer<PanelPopulator<?, ?>>) (Consumer<?>) vPanel);
						} else
							s.lastV(p -> vPanel.accept((PanelPopulator<JPanel, ?>) modify(p)));
						return this;
					}
				};
				try {
					switch (contents.size()) {
					case 0:
						return;
					case 1:
						contents.getFirst().populate(populator, quick.getContents().getFirst());
						break;
					default:
						contents.getFirst().populate(populator, quick.getContents().getFirst());
						contents.get(1).populate(populator, quick.getContents().get(1));
					}
				} catch (ModelInstantiationException e) {
					throw new CheckedExceptionWrapper(e);
				}
			});
		});
	}

	static QuickSwingContainerPopulator<QuickScrollPane> interpretScroll(QuickScrollPane.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		QuickSwingPopulator<QuickWidget> content = tx.transform(interpreted.getContents().getFirst(), QuickSwingPopulator.class);
		QuickSwingPopulator<QuickWidget> rowHeader = interpreted.getRowHeader() == null ? null
			: tx.transform(interpreted.getRowHeader(), QuickSwingPopulator.class);
		QuickSwingPopulator<QuickWidget> columnHeader = interpreted.getColumnHeader() == null ? null
			: tx.transform(interpreted.getColumnHeader(), QuickSwingPopulator.class);
		return QuickSwingPopulator.createContainer((panel, quick) -> {
			panel.addScroll(null, s -> {
				try {
					content.populate(new ScrollPopulator(s), quick.getContents().getFirst());
					if (rowHeader != null)
						rowHeader.populate(new ScrollRowHeaderPopulator(s), quick.getRowHeader());
					if (columnHeader != null)
						columnHeader.populate(new ScrollColumnHeaderPopulator(s), quick.getColumnHeader());
				} catch (ModelInstantiationException e) {
					throw new CheckedExceptionWrapper(e);
				}
			});
		});
	}

	private static class ScrollPopulator extends AbstractQuickContainerPopulator {
		private final PanelPopulation.ScrollPane<?> theScroll;

		ScrollPopulator(PanelPopulation.ScrollPane<?> scroll) {
			theScroll = scroll;
		}

		@Override
		public Observable<?> getUntil() {
			return theScroll.getUntil();
		}

		@Override
		public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
			Consumer<PanelPopulator<JPanel, ?>> hPanel) {
			theScroll.withHContent(layout, p -> hPanel.accept((PanelPopulator<JPanel, ?>) modify(p)));
			return this;
		}

		@Override
		public AbstractQuickContainerPopulator addVPanel(Consumer<PanelPopulator<JPanel, ?>> vPanel) {
			theScroll.withVContent(p -> vPanel.accept((PanelPopulator<JPanel, ?>) modify(p)));
			return this;
		}
	}

	private static class ScrollRowHeaderPopulator extends AbstractQuickContainerPopulator {
		private final PanelPopulation.ScrollPane<?> theScroll;

		ScrollRowHeaderPopulator(PanelPopulation.ScrollPane<?> scroll) {
			theScroll = scroll;
		}

		@Override
		public Observable<?> getUntil() {
			return theScroll.getUntil();
		}

		@Override
		public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
			Consumer<PanelPopulator<JPanel, ?>> hPanel) {
			theScroll.withHRowHeader(layout, p -> hPanel.accept((PanelPopulator<JPanel, ?>) modify(p)));
			return this;
		}

		@Override
		public AbstractQuickContainerPopulator addVPanel(Consumer<PanelPopulator<JPanel, ?>> vPanel) {
			theScroll.withVRowHeader(p -> vPanel.accept((PanelPopulator<JPanel, ?>) modify(p)));
			return this;
		}
	}

	private static class ScrollColumnHeaderPopulator extends AbstractQuickContainerPopulator {
		private final PanelPopulation.ScrollPane<?> theScroll;

		ScrollColumnHeaderPopulator(PanelPopulation.ScrollPane<?> scroll) {
			theScroll = scroll;
		}

		@Override
		public Observable<?> getUntil() {
			return theScroll.getUntil();
		}

		@Override
		public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
			Consumer<PanelPopulator<JPanel, ?>> hPanel) {
			theScroll.withHColumnHeader(layout, p -> hPanel.accept((PanelPopulator<JPanel, ?>) modify(p)));
			return this;
		}

		@Override
		public AbstractQuickContainerPopulator addVPanel(Consumer<PanelPopulator<JPanel, ?>> vPanel) {
			throw new IllegalArgumentException("Vertical panel makes no sense for a column header");
		}
	}

	static QuickSwingDialog<QuickInfoDialog> interpretInfoDialog(QuickInfoDialog.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		QuickSwingPopulator<QuickWidget> content = tx.transform(interpreted.getContent(), QuickSwingPopulator.class);
		return new QuickSwingDialog<QuickInfoDialog>() {
			private SettableValue<String> theType;
			private SettableValue<String> theTitle;
			private SettableValue<Icon> theIcon;
			private Component theContent;
			private ObservableAction theOnClose;

			@Override
			public void initialize(QuickInfoDialog dialog, Component parent, Observable<?> until) throws ModelInstantiationException {
				QuickAbstractWindow window = dialog.getAddOn(QuickAbstractWindow.class);
				theType = dialog.getType();
				theTitle = window.getTitle();
				theIcon = dialog.getIcon();
				theOnClose = dialog.getOnClose();
				QuickBaseSwing.ComponentExtractor ce = new ComponentExtractor(until);
				content.populate(ce, dialog.getContent());
				theContent = ce.getExtractedComponent();

				window.isVisible().value().takeUntil(until).filter(v -> v).act(__ -> {
					Icon icon = theIcon == null ? null : theIcon.get();
					int swingType;
					String type = theType.get();
					if (type == null)
						swingType = JOptionPane.INFORMATION_MESSAGE;
					else {
						switch (type.toLowerCase()) {
						case "info":
						case "information":
							swingType = JOptionPane.INFORMATION_MESSAGE;
							break;
						case "err":
						case "error":
							swingType = JOptionPane.ERROR_MESSAGE;
							break;
						case "warn":
						case "warning":
							swingType = JOptionPane.WARNING_MESSAGE;
							break;
						case "plain":
							swingType = JOptionPane.PLAIN_MESSAGE;
							break;
						case "q":
						case "question":
						case "?":
							swingType = JOptionPane.QUESTION_MESSAGE;
							break;
						default:
							dialog.reporting().warn("Unrecognized message type: " + type);
							swingType = JOptionPane.INFORMATION_MESSAGE;
							break;
						}
					}
					if (icon != null)
						JOptionPane.showMessageDialog(parent, theContent, theTitle.get(), swingType, icon);
					else
						JOptionPane.showMessageDialog(parent, theContent, theTitle.get(), swingType);
					EventQueue.invokeLater(() -> {
						if (window.isVisible().isAcceptable(false) == null)
							window.isVisible().set(false, null);
					});
					theOnClose.act(null);
				});
			}
		};
	}

	static QuickSwingDialog<QuickConfirm> interpretConfirm(QuickConfirm.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		QuickSwingPopulator<QuickWidget> content = tx.transform(interpreted.getContent(), QuickSwingPopulator.class);
		return new QuickSwingDialog<QuickConfirm>() {
			private SettableValue<String> theTitle;
			private SettableValue<Icon> theIcon;
			private Component theContent;
			private ObservableAction theOnConfirm;
			private ObservableAction theOnCancel;

			@Override
			public void initialize(QuickConfirm dialog, Component parent, Observable<?> until) throws ModelInstantiationException {
				QuickAbstractWindow window = dialog.getAddOn(QuickAbstractWindow.class);
				theTitle = window.getTitle();
				theIcon = dialog.getIcon();
				theOnConfirm = dialog.getOnConfirm();
				theOnCancel = dialog.getOnCancel();
				QuickBaseSwing.ComponentExtractor ce = new ComponentExtractor(until);
				content.populate(ce, dialog.getContent());
				theContent = ce.getExtractedComponent();

				window.isVisible().value().takeUntil(until).filter(v -> v).act(__ -> {
					int result;
					Icon icon = theIcon == null ? null : theIcon.get();
					if (icon != null)
						result = JOptionPane.showConfirmDialog(parent, theContent, theTitle.get(), JOptionPane.OK_CANCEL_OPTION,
							JOptionPane.QUESTION_MESSAGE, icon);
					else
						result = JOptionPane.showConfirmDialog(parent, theContent, theTitle.get(), JOptionPane.OK_CANCEL_OPTION);
					EventQueue.invokeLater(() -> {
						if (window.isVisible().isAcceptable(false) == null)
							window.isVisible().set(false, null);
					});
					if (result == JOptionPane.OK_OPTION)
						theOnConfirm.act(null);
					else if (theOnCancel.isEnabled() == null)
						theOnCancel.act(null);
				});
			}
		};
	}

	static QuickSwingDialog<QuickFileChooser> interpretFileChooser(QuickFileChooser.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		return new QuickSwingDialog<QuickFileChooser>() {
			private QuickFileChooser theQuickChooser;
			private SettableValue<String> theTitle;
			private JFileChooser theSwingChooser = new JFileChooser();

			@Override
			public void initialize(QuickFileChooser dialog, Component parent, Observable<?> until) throws ModelInstantiationException {
				QuickAbstractWindow window = dialog.getAddOn(QuickAbstractWindow.class);
				theTitle = window.getTitle();
				theQuickChooser = dialog;
				int mode;
				if (theQuickChooser.isFilesSelectable()) {
					if (theQuickChooser.isDirectoriesSelectable())
						mode = JFileChooser.FILES_AND_DIRECTORIES;
					else
						mode = JFileChooser.FILES_ONLY;
				} else
					mode = JFileChooser.DIRECTORIES_ONLY;
				theSwingChooser.setFileSelectionMode(mode);
				theSwingChooser.setMultiSelectionEnabled(theQuickChooser.isMultiSelectable());

				window.isVisible().value().takeUntil(until).filter(v -> v).act(__ -> display(window, parent));
			}

			void display(QuickAbstractWindow window, Component parent) {
				boolean satisfied = false;
				while (!satisfied) {
					File dir = theQuickChooser.getDirectory().get();
					if (dir != null)
						theSwingChooser.setCurrentDirectory(dir);
					theSwingChooser.setDialogTitle(theTitle.get());

					int result;
					if (theQuickChooser.isOpen())
						result = theSwingChooser.showOpenDialog(parent);
					else
						result = theSwingChooser.showSaveDialog(parent);

					String enabled, title = null;
					if (result == JFileChooser.APPROVE_OPTION) {
						List<File> files;
						if (theSwingChooser.isMultiSelectionEnabled())
							files = Arrays.asList(theSwingChooser.getSelectedFiles());
						else
							files = Arrays.asList(theSwingChooser.getSelectedFile());
						enabled = theQuickChooser.filesChosen(files);
						if (enabled == null) {
							if (theQuickChooser.getDirectory().isAcceptable(theSwingChooser.getCurrentDirectory()) == null) {
								satisfied = true;
								theQuickChooser.getDirectory().set(theSwingChooser.getCurrentDirectory(), null);
							}
						} else
							title = "Selected file" + (files.size() == 1 ? "" : "s") + " not allowed";
					} else {
						enabled = theQuickChooser.getOnCancel().isEnabled().get();
						if (enabled == null) {
							satisfied = true;
							theQuickChooser.getOnCancel().act(null);
						} else
							title = theQuickChooser.isOpen() ? "A file must be chosen" : "The file must be saved";
					}
					if (!satisfied)
						JOptionPane.showMessageDialog(parent, enabled, title, JOptionPane.ERROR_MESSAGE);
				}
				EventQueue.invokeLater(() -> {
					if (window.isVisible().isAcceptable(false) == null)
						window.isVisible().set(false, null);
				});
			}
		};
	}

	static QuickSwingDialog<GeneralDialog> interpretGeneralDialog(GeneralDialog.Interpreted<?> interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		QuickSwingPopulator<QuickWidget> content = tx.transform(interpreted.getContent(), QuickSwingPopulator.class);
		return new QuickSwingDialog<GeneralDialog>() {
			private SettableValue<String> theTitle;
			private PanelPopulation.WindowBuilder<JDialog, ?> theDialog;

			@Override
			public void initialize(GeneralDialog dialog, Component parent, Observable<?> until) throws ModelInstantiationException {
				QuickWindow window = dialog.getAddOn(QuickWindow.class);
				theTitle = window.getTitle();
				JDialog jDialog = new JDialog(SwingUtilities.getWindowAncestor(parent), //
					dialog.isModal() ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS);
				theDialog = WindowPopulation.populateDialog(jDialog, until, false);
				content.populate(new WindowContentPopulator(theDialog, until), dialog.getContent());
				theDialog.withTitle(theTitle);
				if (window.getX() != null)
					theDialog.withX(window.getX());
				if (window.getY() != null)
					theDialog.withY(window.getY());
				if (window.getWidth() != null)
					theDialog.withWidth(window.getWidth());
				if (window.getHeight() != null)
					theDialog.withHeight(window.getHeight());
				theDialog.withVisible(window.isVisible());
				theDialog.disposeOnClose(false);
				EventQueue.invokeLater(() -> { // Do in an invoke later to allow the UI to come up before opening the dialog
					theDialog.run(parent);
				});
			}
		};
	}

	static class WindowContentPopulator extends AbstractQuickContainerPopulator {
		private final PanelPopulation.WindowBuilder<?, ?> theWindow;
		private final Observable<?> theUntil;

		WindowContentPopulator(WindowBuilder<?, ?> window, Observable<?> until) {
			theWindow = window;
			theUntil = until;
		}

		@Override
		public Observable<?> getUntil() {
			return theUntil;
		}

		@Override
		public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
			Consumer<PanelPopulator<JPanel, ?>> panel) {
			theWindow.withHContent(layout, p -> panel.accept((PanelPopulator<JPanel, ?>) modify(p)));
			return this;
		}

		@Override
		public AbstractQuickContainerPopulator addVPanel(Consumer<PanelPopulator<JPanel, ?>> panel) {
			theWindow.withVContent(p -> panel.accept((PanelPopulator<JPanel, ?>) modify(p)));
			return this;
		}
	}

	static <T> QuickSwingContainerPopulator<QuickTabs<T>> interpretTabs(QuickTabs.Interpreted<T> interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		Map<Object, QuickSwingPopulator<QuickWidget>> renderers = new HashMap<>();
		for (QuickWidget.Interpreted<?> content : interpreted.getContents())
			renderers.put(content.getIdentity(), tx.transform(content, QuickSwingPopulator.class));
		for (QuickTabs.TabSet.Interpreted<? extends T> tabSet : interpreted.getTabSets())
			renderers.put(tabSet.getRenderer().getIdentity(), tx.transform(tabSet.getRenderer(), QuickSwingPopulator.class));
		return QuickSwingPopulator.createContainer((panel, quick) -> {
			PanelPopulation.TabPaneEditor<?, ?>[] tabs = new PanelPopulation.TabPaneEditor[1];
			panel.addTabs(t -> tabs[0] = t);
			quick.getTabs().subscribe(evt -> {
				switch (evt.getType()) {
				case add:
					QuickSwingPopulator<QuickWidget> renderer = renderers.get(evt.getNewValue().getRenderer().getIdentity());
					try {
						renderer.populate(new QuickBaseSwing.TabsPopulator<>(tabs[0], quick, evt.getNewValue(), evt.getIndex()),
							evt.getNewValue().getRenderer());
					} catch (ModelInstantiationException e) {
						evt.getNewValue().getRenderer().reporting().error("Failed to populate tab", e);
					}
					break;
				case remove:
				case set:
				}
			}, true);
			tabs[0].withSelectedTab(quick.getSelectedTab());
		});
	}

	private static class TabsPopulator<T> extends AbstractQuickContainerPopulator {
		private final PanelPopulation.TabPaneEditor<?, ?> theTabEditor;
		private final QuickTabs<T> theTabs;
		private final QuickTabs.TabInstance<? extends T> theTab;
		private final int theTabIndex;

		TabsPopulator(PanelPopulation.TabPaneEditor<?, ?> tabEditor, QuickTabs<T> tabs, QuickTabs.TabInstance<? extends T> tab, int index) {
			theTabEditor = tabEditor;
			theTabs = tabs;
			theTab = tab;
			theTabIndex = index;
		}

		@Override
		public Observable<?> getUntil() {
			return theTabEditor.getUntil();
		}

		@Override
		public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
			Consumer<PanelPopulator<JPanel, ?>> panel) {
			theTabEditor.withHTab(theTab.getTabValue(), theTabIndex, layout, tab -> panel.accept((PanelPopulator<JPanel, ?>) modify(tab)),
				this::configureTab);
			return this;
		}

		@Override
		public AbstractQuickContainerPopulator addVPanel(Consumer<PanelPopulator<JPanel, ?>> panel) {
			theTabEditor.withVTab(theTab.getTabValue(), theTabIndex, tab -> panel.accept((PanelPopulator<JPanel, ?>) modify(tab)),
				this::configureTab);
			return this;
		}

		void configureTab(PanelPopulation.TabEditor<?> tab) {
			tab.setName(theTab.getTabName());
			tab.setIcon(theTab.getTabIcon());
			tab.setRemovable(theTab.isRemovable().get());
			tab.onRemove(__ -> theTab.onRemove());
			tab.onSelect(onSelect -> {
				onSelect.changes().takeUntil(theTab.getRenderer().isDestroyed().noInitChanges().take(1)).act(evt -> {
					if (evt.getNewValue())
						theTab.onSelect();
				});
			});
		}
	}

	static class ComponentExtractor extends AbstractQuickContainerPopulator {
		private Observable<?> theUntil;
		private Component theExtractedComponent;

		public ComponentExtractor(Observable<?> until) {
			theUntil = until;
		}

		@Override
		public Observable<?> getUntil() {
			return theUntil;
		}

		@Override
		public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
			Consumer<PanelPopulator<JPanel, ?>> panel) {
			PanelPopulation.PanelPopulator<JPanel, ?> populator = PanelPopulation.populateHPanel(null, layout, theUntil);
			modify(populator);
			panel.accept(populator);
			theExtractedComponent = populator.getContainer();
			return this;
		}

		@Override
		public AbstractQuickContainerPopulator addVPanel(Consumer<PanelPopulator<JPanel, ?>> panel) {
			PanelPopulation.PanelPopulator<JPanel, ?> populator = PanelPopulation.populateVPanel(null, theUntil);
			modify(populator);
			panel.accept(populator);
			theExtractedComponent = populator.getContainer();
			return this;
		}

		public Component getExtractedComponent() {
			return theExtractedComponent;
		}
	}
}
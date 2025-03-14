package org.observe.quick.swing;

import java.awt.datatransfer.DataFlavor;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.quick.Iconized;
import org.observe.quick.base.QuickTransfer;
import org.observe.quick.base.QuickTransfer.QuickDataFlavor;
import org.observe.quick.swing.QuickBaseSwing.QuickSwingDataFlavor;
import org.observe.util.swing.Dragging;
import org.observe.util.swing.ModelCell;
import org.qommons.Transformer;
import org.qommons.collect.BetterList;
import org.qommons.ex.CheckedExceptionWrapper;

/** Helper for configuring data transfer on Swing widgets from Quick configuration */
public class QuickSwingTransfer {
	private final Map<Object, QuickSwingDataFlavor<?, ?>> theDataFlavors;

	/** Creates the transfer helper */
	public QuickSwingTransfer() {
		theDataFlavors = new HashMap<>();
	}

	/**
	 * @param sources The interpreted Quick transfer sources to configure transfer for
	 * @param accepters The interpreted Quick transfer accepters to configure transfer for
	 * @param tx The Quick-swing transformer
	 * @throws ExpressoInterpretationException If the transfers could not be interpreted for Swing
	 */
	public QuickSwingTransfer(List<? extends QuickTransfer.TransferSource.Interpreted<?, ?>> sources,
		List<? extends QuickTransfer.TransferAccept.Interpreted<?, ?>> accepters, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
		this();
		withSources(sources, tx).withAccepters(accepters, tx);
	}

	/**
	 * @param sources The interpreted Quick transfer sources to configure transfer for
	 * @param tx The Quick-swing transformer
	 * @return This transfer helper
	 * @throws ExpressoInterpretationException If the transfer sources could not be interpreted for Swing
	 */
	public QuickSwingTransfer withSources(List<? extends QuickTransfer.TransferSource.Interpreted<?, ?>> sources,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		for (QuickTransfer.TransferSource.Interpreted<?, ?> ts : sources) {
			for (QuickTransfer.QuickDataFlavor.Interpreted<?, ?> flavor : ts.getFlavors())
				theDataFlavors.put(flavor.getIdentity(), tx.transform(flavor, QuickSwingDataFlavor.class));
		}
		return this;
	}

	/**
	 * @param accepters The interpreted Quick transfer accepters to configure transfer for
	 * @param tx The Quick-swing transformer
	 * @return This transfer helper
	 * @throws ExpressoInterpretationException If the transfer accepters could not be interpreted for Swing
	 */
	public QuickSwingTransfer withAccepters(List<? extends QuickTransfer.TransferAccept.Interpreted<?, ?>> accepters,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		for (QuickTransfer.TransferAccept.Interpreted<?, ?> ts : accepters) {
			for (QuickTransfer.QuickDataFlavor.Interpreted<?, ?> flavor : ts.getFlavors())
				theDataFlavors.put(flavor.getIdentity(), tx.transform(flavor, QuickSwingDataFlavor.class));
		}
		return this;
	}

	/**
	 * @param sources The transfer sources that have been removed from the Quick configuration
	 * @return This transfer helper
	 */
	public QuickSwingTransfer removeSources(List<? extends QuickTransfer.TransferSource.Interpreted<?, ?>> sources) {
		for (QuickTransfer.TransferSource.Interpreted<?, ?> ts : sources) {
			for (QuickTransfer.QuickDataFlavor.Interpreted<?, ?> flavor : ts.getFlavors())
				theDataFlavors.remove(flavor.getIdentity());
		}
		return this;
	}

	/**
	 * @param accepters The transfer accepters that have been removed from the Quick configuration
	 * @return This transfer helper
	 */
	public QuickSwingTransfer removeAccepters(List<? extends QuickTransfer.TransferAccept.Interpreted<?, ?>> accepters) {
		for (QuickTransfer.TransferAccept.Interpreted<?, ?> ts : accepters) {
			for (QuickTransfer.QuickDataFlavor.Interpreted<?, ?> flavor : ts.getFlavors())
				theDataFlavors.remove(flavor.getIdentity());
		}
		return this;
	}

	/**
	 * @param <R> The model value type of the widget to configure transfer for
	 * @param <C> The cell value type of the widget to configure transfer for
	 * @param activeCell Accepts model cells for transfer to configure the transfer operation for the Quick widget
	 * @param transferSources The transfer sources to configure transfer for
	 * @param dragSource The PanelPopulation drag source to configure transfer in
	 */
	public <R, C> void configureTransferSources(Consumer<ModelCell<? extends R, ? extends C>> activeCell,
		List<? extends QuickTransfer.TransferSource<?, ?>> transferSources,
			Consumer<Consumer<? super Dragging.TransferSource<? extends R, ? extends C>>> dragSource) {
		if (transferSources.isEmpty())
			return;
		dragSource.accept(dragSrc -> {
			for (QuickTransfer.TransferSource<?, ?> ts : transferSources) {
				try {
					configureTransferSource(activeCell, dragSrc, ts);
				} catch (ModelInstantiationException e) {
					throw new CheckedExceptionWrapper(e);
				}
			}
		});
	}

	/**
	 * @param <R> The model value type of the widget to configure transfer for
	 * @param <C> The cell value type of the widget to configure transfer for
	 * @param activeCell Accepts model cells for transfer to configure the transfer operation for the Quick widget
	 * @param transferAccepters The transfer accepters to configure transfer for
	 * @param dragAccept The PanelPopulation drag accepter to configure transfer in
	 */
	public <R, C> void configureTransferAccepters(Consumer<ModelCell<? extends R, ? extends C>> activeCell,
		List<? extends QuickTransfer.TransferAccept<?, ?>> transferAccepters,
			Consumer<? super Consumer<? super Dragging.TransferAccepter<R, ? extends C>>> dragAccept) {
		if (transferAccepters.isEmpty())
			return;
		Consumer<Dragging.TransferAccepter<R, ? extends C>> onDA = dragAcc -> {
			for (QuickTransfer.TransferAccept<?, ?> ta : transferAccepters) {
				try {
					configureTransferAccept(activeCell, dragAcc, ta);
				} catch (ModelInstantiationException e) {
					throw new CheckedExceptionWrapper(e);
				}
			}
		};
		dragAccept.accept(onDA);
	}

	private <R, C, T> void configureTransferSource(Consumer<ModelCell<? extends R, ? extends C>> activeCell,
		Dragging.TransferSource<? extends R, ? extends C> dragSrc, QuickTransfer.TransferSource<?, T> ts)
			throws ModelInstantiationException {
		Set<DataFlavor> flavors = new LinkedHashSet<>();
		for (QuickTransfer.QuickDataFlavor<? extends T> flavor : ts.getFlavors()) {
			QuickSwingDataFlavor<T, QuickDataFlavor<T>> swingFlavor = (QuickSwingDataFlavor<T, QuickDataFlavor<T>>) theDataFlavors
				.get(flavor.getIdentity());
			if (swingFlavor != null)
				flavors.add(swingFlavor.getFlavor((QuickDataFlavor<T>) flavor));
		}
		dragSrc.draggable(ts.isDraggable());
		dragSrc.copyable(ts.isCopyable());
		dragSrc.movable(ts.isMovable());
		SettableValue<Boolean> canTransform = ts.canTransform();
		SettableValue<T> transform = ts.getTransform();
		dragSrc.toFlavors(flavors, new Dragging.DataSourceTransform<R, C, T>() {
			@Override
			public boolean canTransform(ModelCell<? extends R, ? extends C> cell, DataFlavor flavor) {
				activeCell.accept(cell);
				return canTransform.get();
			}

			@Override
			public T transform(ModelCell<? extends R, ? extends C> cell, DataFlavor flavor) throws IOException {
				activeCell.accept(cell);
				return transform.get();
			}
		});
	}

	private <R, C, T> void configureTransferAccept(Consumer<ModelCell<? extends R, ? extends C>> cell,
		Dragging.TransferAccepter<R, ? extends C> dragSrc, QuickTransfer.TransferAccept<?, T> ts) throws ModelInstantiationException {
		Set<DataFlavor> flavors = new LinkedHashSet<>();
		for (QuickTransfer.QuickDataFlavor<? extends T> flavor : ts.getFlavors()) {
			QuickSwingDataFlavor<T, QuickDataFlavor<T>> swingFlavor = (QuickSwingDataFlavor<T, QuickDataFlavor<T>>) theDataFlavors
				.get(flavor.getIdentity());
			if (swingFlavor != null)
				flavors.add(swingFlavor.getFlavor((QuickDataFlavor<T>) flavor));
		}
		dragSrc.draggable(ts.isDraggable());
		dragSrc.pastable(ts.isPasteable());
		SettableValue<T> transferValue = ts.getTransferValue();
		ObservableCollection<T> transferValues = ts.getTransferValues();
		SettableValue<Boolean> canAccept = ts.canAccept();
		boolean canAcceptSingle = ts.isCanAcceptOpOnSingle();
		ObservableAction accept = ts.getAccept();
		boolean acceptSingle = ts.isAcceptOpOnSingle();
		dragSrc.fromFlavors(flavors, new Dragging.DataAccepterTransform<R, C, T>() {
			@Override
			public boolean canAccept(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
				List<? extends T> values, DataFlavor flavor) {
				cell.accept(targetCell);
				if (canAcceptSingle) {
					for (Object value : values) {
						transferValue.set((T) value);

						if (!canAccept.get())
							return false;
					}
					return true;
				} else {
					transferValues.clear();
					transferValues.addAll(values);
					return canAccept.get();
				}
			}

			@Override
			public List<? extends R> transform(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
				List<? extends T> values, DataFlavor flavor, boolean testOnly) throws IOException {
				cell.accept(targetCell);
				if (acceptSingle) {
					for (Object value : values) {
						transferValue.set((T) value);

						if (testOnly) {
							if (accept.isEnabled().get() != null)
								return null;
						} else
							accept.act(null);
					}
				} else {
					transferValues.clear();
					transferValues.addAll(values);
					if (testOnly) {
						if (accept.isEnabled().get() != null)
							return null;
					} else
						accept.act(null);
				}
				return BetterList.empty();
			}
		});
		// Here we're relying on the fact that the context has been pre-set by the canAccept method above
		// TODO Icon offset
		ObservableValue<Icon> icon = ts.getAddOn(Iconized.class).getIcon().map(img -> img == null ? null : new ImageIcon(img));
		dragSrc.appearance(icon);
	}
}

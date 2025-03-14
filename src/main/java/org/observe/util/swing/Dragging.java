package org.observe.util.swing;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.Icon;
import javax.swing.TransferHandler;
import javax.swing.TransferHandler.DropLocation;
import javax.swing.TransferHandler.TransferSupport;

import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.BiTuple;
import org.qommons.collect.BetterList;

public class Dragging {
	public interface TransferWrapper {
		public boolean isDrop();

		public void setDropAction(int action);

		public DropLocation getDropLocation();

		public DataFlavor[] getDataFlavors();

		public boolean isDataFlavorSupported(DataFlavor flavor);

		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException;
	}

	public static DefaultTransferWrapper wrap(TransferSupport transfer) {
		return new DefaultTransferWrapper(transfer);
	}

	public static class DefaultTransferWrapper implements TransferWrapper {
		private final TransferSupport theTransfer;

		public DefaultTransferWrapper(TransferSupport transfer) {
			theTransfer = transfer;
		}

		@Override
		public boolean isDrop() {
			return theTransfer.isDrop();
		}

		@Override
		public void setDropAction(int action) {
			theTransfer.setDropAction(action);
		}

		@Override
		public DropLocation getDropLocation() {
			return theTransfer.getDropLocation();
		}

		@Override
		public DataFlavor[] getDataFlavors() {
			return theTransfer.getDataFlavors();
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			return theTransfer.isDataFlavorSupported(flavor);
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
			return theTransfer.getTransferable().getTransferData(flavor);
		}
	}

	public static class MultiFlavor extends DataFlavor {
		public final DataFlavor single;

		public MultiFlavor(DataFlavor single) {
			super(List.class, "List<" + single.getHumanPresentableName() + ">");
			this.single = single;
		}
	}

	public static class GridFlavor extends DataFlavor {
		public final DataFlavor component;
		public final int rows;
		public final int columns;

		public GridFlavor(DataFlavor component, int rows, int columns) {
			super(Array.newInstance(Array.newInstance(component.getRepresentationClass(), 0).getClass(), 0).getClass(),
				"Grid<" + component.getHumanPresentableName() + ">");
			this.component = component;
			this.rows = rows;
			this.columns = columns;
		}
	}

	public static class GridElementFlavor extends DataFlavor {
		public final DataFlavor component;
		public final int row;
		public final int column;

		public GridElementFlavor(DataFlavor component, int row, int column) {
			super(component.getRepresentationClass(), "Grid<" + component.getHumanPresentableName() + ">[" + row + "][" + column + "]");
			this.component = component;
			this.row = row;
			this.column = column;
		}
	}

	public static class GridElementTransferWrapper implements TransferWrapper {
		private final TransferWrapper theWrapped;
		private final int theRow;
		private final int theColumn;

		public GridElementTransferWrapper(TransferWrapper wrapped, int row, int column) {
			theWrapped = wrapped;
			theRow = row;
			theColumn = column;
		}

		@Override
		public boolean isDrop() {
			return theWrapped.isDrop();
		}

		@Override
		public void setDropAction(int action) {
			theWrapped.setDropAction(action);
		}

		@Override
		public DropLocation getDropLocation() {
			return theWrapped.getDropLocation();
		}

		@Override
		public DataFlavor[] getDataFlavors() {
			DataFlavor[] flavors = theWrapped.getDataFlavors().clone();
			for (int f = 0; f < flavors.length; f++)
				flavors[f] = new GridElementFlavor(flavors[f], theRow, theColumn);
			return flavors;
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			return theWrapped.isDataFlavorSupported(new GridElementFlavor(flavor, theRow, theColumn));
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
			return theWrapped.getTransferData(new GridElementFlavor(flavor, theRow, theColumn));
		}
	}

	public static class GridTransferable implements Transferable {
		private final Transferable[][] theComponents;
		private final Set<DataFlavor> theComponentFlavors;
		private final GridFlavor[] theGridFlavors;

		public GridTransferable(Transferable[][] components) {
			theComponents = components;
			Set<DataFlavor> componentFlavors = new LinkedHashSet<>(Arrays.asList(theComponents[0][0].getTransferDataFlavors()));
			boolean first = true;
			for (Transferable[] row : theComponents) {
				for (Transferable t : row) {
					if (first) {
						first = false;
						continue;
					}
					componentFlavors.retainAll(Arrays.asList(t.getTransferDataFlavors()));
					if (componentFlavors.isEmpty())
						break;
				}
				if (componentFlavors.isEmpty())
					break;
			}
			theComponentFlavors = componentFlavors;
			theGridFlavors = new GridFlavor[componentFlavors.size()];
			int f = 0;
			for (DataFlavor componentFlavor : componentFlavors)
				theGridFlavors[f++] = new GridFlavor(componentFlavor, theComponents.length, theComponents[0].length);
		}

		@Override
		public DataFlavor[] getTransferDataFlavors() {
			return theGridFlavors;
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			if (flavor instanceof GridElementFlavor) {
				GridElementFlavor gef = (GridElementFlavor) flavor;
				if (gef.row >= theComponents.length || gef.column >= theComponents[0].length)
					return false;
				flavor = ((GridElementFlavor) flavor).component;
				return theComponentFlavors.contains(flavor);
			} else
				return ArrayUtils.contains(theGridFlavors, flavor);
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
			if (flavor instanceof GridElementFlavor) {
				GridElementFlavor gef = (GridElementFlavor) flavor;
				Transferable component = theComponents[gef.row][gef.column];
				return component.getTransferData(gef.component);
			} else {
				GridFlavor gridFlavor = (GridFlavor) flavor;
				Object[][] grid = (Object[][]) Array.newInstance(TypeTokens.get().wrap(gridFlavor.component.getRepresentationClass()),
					gridFlavor.rows, gridFlavor.columns);
				for (int r = 0; r < theComponents.length; r++) {
					for (int c = 0; c < theComponents[r].length; c++)
						grid[r][c] = theComponents[r][c].getTransferData(gridFlavor.component);
				}
				return grid;
			}
		}
	}

	public interface TransferSource<R, C> {
		TransferSource<R, C> draggable(boolean draggable);

		TransferSource<R, C> copyable(boolean copyable);

		TransferSource<R, C> movable(boolean movable);

		boolean isDraggable();

		default TransferSource<R, C> toFlavor(DataFlavor flavor, DataSourceTransform<? super R, ? super C, ?> transform) {
			advertiseFlavor(flavor);
			return toFlavors(Arrays.asList(flavor), transform);
		}

		default TransferSource<R, C> toFlavors(Collection<? extends DataFlavor> flavors,
			DataSourceTransform<? super R, ? super C, ?> transform) {
			for (DataFlavor f : flavors)
				advertiseFlavor(f);
			Set<DataFlavor> flavorSet = flavors instanceof Set ? (Set<DataFlavor>) flavors : new LinkedHashSet<>(flavors);
			return toFlavorLike(flavorSet::contains, transform);
		}

		TransferSource<R, C> advertiseFlavor(DataFlavor flavor);

		TransferSource<R, C> toFlavorLike(Predicate<? super DataFlavor> flavors, DataSourceTransform<? super R, ? super C, ?> transform);

		default TransferSource<R, C> columnToObject(Class<? super C> type) {
			return toFlavor(new DataFlavor(type, type.getName()), new DataSourceTransform<R, C, C>() {
				@Override
				public boolean canTransform(ModelCell<? extends R, ? extends C> cell, DataFlavor flavor) {
					return true;
				}

				@Override
				public C transform(ModelCell<? extends R, ? extends C> cell, DataFlavor flavor) throws IOException {
					return cell.getCellValue();
				}
			});
		}

		default TransferSource<R, C> rowToObject(Class<? super R> type) {
			return toFlavor(new DataFlavor(type, type.getName()), new DataSourceTransform<R, C, R>() {
				@Override
				public boolean canTransform(ModelCell<? extends R, ? extends C> cell, DataFlavor flavor) {
					return true;
				}

				@Override
				public R transform(ModelCell<? extends R, ? extends C> cell, DataFlavor flavor) throws IOException {
					return cell.getModelValue();
				}
			});
		}

		default TransferSource<R, C> toText(Function<? super C, ? extends CharSequence> toString) {
			// TODO Support multiple text-based flavors
			return toFlavor(DataFlavor.getTextPlainUnicodeFlavor(), new DataSourceTransform<R, C, InputStream>() {
				@Override
				public boolean canTransform(ModelCell<? extends R, ? extends C> cell, DataFlavor flavor) {
					return true;
				}

				@Override
				public InputStream transform(ModelCell<? extends R, ? extends C> cell, DataFlavor flavor) {
					return new ByteArrayInputStream(toString.apply(cell.getCellValue()).toString().getBytes(Charset.forName("UTF-8")));
				}
			});
		}

		int getSourceActions();

		Transferable createTransferable(ModelCell<? extends R, ? extends C> value);
	}

	public interface DataSourceTransform<R, C, E> {
		boolean canTransform(ModelCell<? extends R, ? extends C> cell, DataFlavor flavor);

		E transform(ModelCell<? extends R, ? extends C> cell, DataFlavor flavor) throws IOException;
	}

	public interface TransferAccepter<R, C> {
		TransferAccepter<R, C> draggable(boolean draggable);

		TransferAccepter<R, C> pastable(boolean pastable);

		boolean isDraggable();

		TransferAccepter<R, C> appearance(Supplier<Icon> appearance);

		default TransferAccepter<R, C> fromFlavor(DataFlavor flavor, DataAccepterTransform<R, ? super C, ?> data) {
			return fromFlavors(Arrays.asList(flavor), data);
		}

		TransferAccepter<R, C> fromFlavors(Collection<? extends DataFlavor> flavors, DataAccepterTransform<R, ? super C, ?> data);

		default TransferAccepter<R, C> fromObject(Class<? super R> type) {
			Class<? super R> wrapped = TypeTokens.get().wrap(type);
			return fromObject(type, new DataAccepterTransform<R, C, R>() {
				@Override
				public boolean canAccept(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
					List<? extends R> values, DataFlavor flavor) {
					for (Object value : values) {
						if (type.isPrimitive() && value == null)
							return false;
						if (value != null && !wrapped.isInstance(value))
							return false;
					}
					return true;
				}

				@Override
				public List<? extends R> transform(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter,
					boolean aboveCenter, List<? extends R> values, DataFlavor flavor, boolean testOnly) throws IOException {
					return values;
				}
			});
		}

		default <E> TransferAccepter<R, C> fromObject(Class<E> type, DataAccepterTransform<R, ? super C, ? extends E> action) {
			return fromFlavor(new DataFlavor(type, type.getName()), action);
		}

		default TransferAccepter<R, C> fromText(Function<? super CharSequence, ? extends R> fromString) {
			return fromFlavor(DataFlavor.getTextPlainUnicodeFlavor(), new DataAccepterTransform<R, C, InputStream>() {
				@Override
				public boolean canAccept(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
					List<? extends InputStream> values, DataFlavor flavor) {
					return true;
				}

				@Override
				public List<? extends R> transform(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter,
					boolean aboveCenter, List<? extends InputStream> values, DataFlavor flavor, boolean testOnly) throws IOException {
					List<R> transformed = new ArrayList<>(values.size());
					char[] buffer = new char[1028];
					for (InputStream value : values) {
						StringWriter writer = new StringWriter();
						Reader reader = new InputStreamReader(value, Charset.forName("UTF-8"));
						int read = reader.read(buffer);
						while (read >= 0) {
							writer.write(buffer, 0, read);
							read = reader.read(buffer);
						}
						transformed.add(fromString.apply(writer.toString()));
					}
					return transformed;
				}
			});
		}

		boolean canAccept(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
			TransferWrapper transfer, boolean withMulti);

		Icon getDragAppearance();

		BetterList<? extends R> accept(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
			TransferWrapper transfer, boolean withMulti, boolean testOnly) throws IOException;
	}

	public interface DataAccepterTransform<R, C, E> {
		boolean canAccept(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
			List<? extends E> value,
			DataFlavor flavor);

		List<? extends R> transform(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
			List<? extends E> values,
			DataFlavor flavor, boolean testOnly) throws IOException;
	}

	public interface DataConsumer<R, C, E> {
		boolean consume(ModelCell<? extends R, ? extends C> target, E incoming, boolean justTest);
	}

	static class SimpleTransferSource<R, C> implements TransferSource<R, C> {
		private final Set<DataFlavor> theFlavors;
		private final List<BiTuple<Predicate<? super DataFlavor>, DataSourceTransform<? super R, ? super C, ?>>> theTransforms;
		private boolean isDraggable;
		private boolean isCopyable;
		private boolean isMovable;

		SimpleTransferSource() {
			theFlavors = new LinkedHashSet<>();
			theTransforms = new ArrayList<>(2);
			isDraggable = true;
			isCopyable = isMovable = true;
		}

		@Override
		public TransferSource<R, C> draggable(boolean draggable) {
			isDraggable = draggable;
			return this;
		}

		@Override
		public TransferSource<R, C> copyable(boolean copyable) {
			isCopyable = copyable;
			return this;
		}

		@Override
		public TransferSource<R, C> movable(boolean movable) {
			isMovable = movable;
			return this;
		}

		@Override
		public boolean isDraggable() {
			return isDraggable;
		}

		@Override
		public TransferSource<R, C> advertiseFlavor(DataFlavor flavor) {
			theFlavors.add(flavor);
			return this;
		}

		@Override
		public TransferSource<R, C> toFlavorLike(Predicate<? super DataFlavor> flavors,
			DataSourceTransform<? super R, ? super C, ?> transform) {
			theTransforms.add(new BiTuple<>(flavors, transform));
			return this;
		}

		@Override
		public int getSourceActions() {
			int actions = 0;
			if (isCopyable)
				actions |= TransferHandler.COPY;
			if (isMovable)
				actions |= TransferHandler.MOVE;
			return actions;
		}

		@Override
		public Transferable createTransferable(ModelCell<? extends R, ? extends C> cell) {
			SimpleTransferable<? extends R, ? extends C> t = null;
			if (!theTransforms.isEmpty()) {
				if (t == null)
					t = new SimpleTransferable<>(cell);
				t.advertiseFlavors(theFlavors);
				t.acceptFlavors(theTransforms);
			}
			return t;
		}
	}

	static class SimpleTransferable<R, C> implements Transferable {
		private final ModelCell<R, C> theCell;
		private final Set<DataFlavor> theFlavors;
		private final List<BiTuple<Predicate<? super DataFlavor>, ? extends DataSourceTransform<? super R, ? super C, ?>>> theTransforms;

		SimpleTransferable(ModelCell<R, C> cell) {
			theCell = cell;
			theFlavors = new LinkedHashSet<>();
			theTransforms = new ArrayList<>();
		}

		void advertiseFlavors(Collection<? extends DataFlavor> flavors) {
			theFlavors.addAll(flavors);
		}

		void acceptFlavors(
			List<? extends BiTuple<Predicate<? super DataFlavor>, ? extends DataSourceTransform<? super R, ? super C, ?>>> transforms) {
			theTransforms.addAll(transforms);
		}

		@Override
		public DataFlavor[] getTransferDataFlavors() {
			return theFlavors.toArray(new DataFlavor[theFlavors.size()]);
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			for (BiTuple<Predicate<? super DataFlavor>, ? extends DataSourceTransform<? super R, ? super C, ?>> transform : theTransforms) {
				if ((transform.getValue1() == null || transform.getValue1().test(flavor))//
					&& transform.getValue2().canTransform(theCell, flavor))
					return true;
			}
			return false;
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
			for (BiTuple<Predicate<? super DataFlavor>, ? extends DataSourceTransform<? super R, ? super C, ?>> transform : theTransforms) {
				if ((transform.getValue1() == null || transform.getValue1().test(flavor))//
					&& transform.getValue2().canTransform(theCell, flavor)) {
					return transform.getValue2().transform(theCell, flavor);
				}
			}
			throw new UnsupportedFlavorException(flavor);
		}

		@Override
		public String toString() {
			return String.valueOf(theCell);
		}
	}

	static class SimpleTransferAccepter<R, C> implements TransferAccepter<R, C> {
		private List<BiTuple<Set<DataFlavor>, DataAccepterTransform<R, ? super C, ?>>> theFlavors;
		private Supplier<Icon> theAppearance;
		private boolean isDraggable;
		private boolean isPastable;

		SimpleTransferAccepter() {
			theFlavors = new ArrayList<>(3);
			isDraggable = true;
		}

		@Override
		public TransferAccepter<R, C> draggable(boolean draggable) {
			isDraggable = draggable;
			return this;
		}

		@Override
		public TransferAccepter<R, C> pastable(boolean pastable) {
			isPastable = pastable;
			return this;
		}

		@Override
		public boolean isDraggable() {
			return isDraggable;
		}

		@Override
		public TransferAccepter<R, C> appearance(Supplier<Icon> appearance) {
			theAppearance = appearance;
			return this;
		}

		@Override
		public TransferAccepter<R, C> fromFlavors(Collection<? extends DataFlavor> flavors, DataAccepterTransform<R, ? super C, ?> data) {
			Set<DataFlavor> flavorSet = new LinkedHashSet<>(flavors);
			theFlavors.add(new BiTuple<>(flavorSet, data));
			return this;
		}

		@Override
		public boolean canAccept(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
			TransferWrapper transfer, boolean withMulti) {
			if (transfer.isDrop()) {
				if (!isDraggable)
					return false;
			} else if (!isPastable)
				return false;
			for (BiTuple<Set<DataFlavor>, DataAccepterTransform<R, ? super C, ?>> flavor : theFlavors) {
				for (DataFlavor f : flavor.getValue1()) {
					DataFlavor f2 = null;
					List<?> transferData = null;
					try {
						if (transfer.isDataFlavorSupported(f)) {
							f2 = f;
							transferData = Collections.singletonList(transfer.getTransferData(f));
						} else if (f instanceof MultiFlavor) {
							f2 = f;
							if (transfer.isDataFlavorSupported(((MultiFlavor) f).single)) {
								transferData = (List<?>) transfer.getTransferData(f);
							}
						} else if (withMulti) {
							f2 = new MultiFlavor(f);
							if (transfer.isDataFlavorSupported(f2))
								transferData = (List<?>) transfer.getTransferData(f2);
						}
					} catch (UnsupportedFlavorException e) {
						// Blows my mind, but it seems that TransferSupport.isDataFlavorSupported(f) doesn't always call the method
						// in the transferable, so my code doesn't get to call some of the filter methods until we actually try to get
						// the transfer data, whereupon we have no choice but to throw an exception.
						// So don't do anything here except try other flavors.
					} catch (IOException e) {
						e.printStackTrace();
					}
					if (transferData != null && ((DataAccepterTransform<R, ? super C, Object>) flavor.getValue2()).canAccept(targetCell,
						leftOfCenter, aboveCenter, transferData, f2))
						return true;
				}
			}
			return false;
		}

		@Override
		public Icon getDragAppearance() {
			if(theAppearance==null)
				return null;
			return theAppearance.get();
		}

		@Override
		public BetterList<? extends R> accept(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
			TransferWrapper transfer, boolean withMulti, boolean testOnly) throws IOException {
			for (BiTuple<Set<DataFlavor>, DataAccepterTransform<R, ? super C, ?>> flavor : theFlavors) {
				for (DataFlavor f : flavor.getValue1()) {
					DataFlavor f2 = null;
					List<?> transferData = null;
					try {
						if (transfer.isDataFlavorSupported(f)) {
							f2 = f;
							transferData = Collections.singletonList(transfer.getTransferData(f));
						} else if (f instanceof MultiFlavor) {
							f2 = f;
							if (transfer.isDataFlavorSupported(((MultiFlavor) f).single)) {
								transferData = (List<?>) transfer.getTransferData(f);
							}
						} else if (withMulti) {
							f2 = new MultiFlavor(f);
							if (transfer.isDataFlavorSupported(f2))
								transferData = (List<?>) transfer.getTransferData(f2);
						}
					} catch (UnsupportedFlavorException e) {
						// Blows my mind, but it seems that TransferSupport.isDataFlavorSupported(f) doesn't always call the method
						// in the transferable, so my code doesn't get to call some of the filter methods until we actually try to get
						// the transfer data, whereupon we have no choice but to throw an exception.
						// So don't do anything here except try other flavors.
					} catch (IOException e) {
						e.printStackTrace();
					}
					if (transferData != null) {
						DataAccepterTransform<R, ? super C, Object> transform = (DataAccepterTransform<R, ? super C, Object>) flavor
							.getValue2();
						if (transform.canAccept(targetCell, leftOfCenter, aboveCenter, transferData, f2)) {
							List<? extends R> data = transform.transform(targetCell, leftOfCenter, aboveCenter, transferData, f2, testOnly);
							return BetterList.of(data);
						}
					}
				}
			}
			return null;
		}
	}

	public static abstract class CompositeTransferable implements Transferable {
		protected final Transferable[] theComponents;

		public CompositeTransferable(Transferable[] components) {
			theComponents = components;
		}

		public Transferable[] getComponents() {
			return theComponents.clone();
		}
	}

	public static class AndTransferable extends CompositeTransferable {
		private final List<DataFlavor> theFlavors;

		public AndTransferable(Transferable... components) {
			super(components);
			if (theComponents.length == 0)
				theFlavors = Collections.emptyList();
			else {
				Set<DataFlavor> flavors = new LinkedHashSet<>(Arrays.asList(theComponents[0].getTransferDataFlavors()));
				for (int c = 1; c < theComponents.length; c++) {
					flavors.retainAll(Arrays.asList(theComponents[c].getTransferDataFlavors()));
				}
				if (theComponents.length == 1)
					theFlavors = flavors.stream().flatMap(f -> Stream.of(f, new MultiFlavor(f)))
					.collect(Collectors.toCollection(() -> new ArrayList<>(flavors.size())));
				else
					theFlavors = flavors.stream().map(MultiFlavor::new)
					.collect(Collectors.toCollection(() -> new ArrayList<>(flavors.size())));
			}
		}

		@Override
		public DataFlavor[] getTransferDataFlavors() {
			return theFlavors.toArray(new DataFlavor[theFlavors.size()]);
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			if (theComponents.length == 0)
				return false;
			if (!(flavor instanceof MultiFlavor)) {
				if (theComponents.length == 1)
					return theComponents[0].isDataFlavorSupported(flavor);
				return false;
			}
			DataFlavor single = ((MultiFlavor) flavor).single;
			for (Transferable c : theComponents) {
				if (!(c.isDataFlavorSupported(single)))
					return false;
			}
			return true;
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
			if (theComponents.length == 0)
				return null;
			if (!(flavor instanceof MultiFlavor)) {
				if (theComponents.length == 1)
					return theComponents[0].getTransferData(flavor);
				throw new UnsupportedFlavorException(flavor);
			}
			DataFlavor single = ((MultiFlavor) flavor).single;
			for (Transferable c : theComponents) {
				if (!(c.isDataFlavorSupported(single)))
					throw new UnsupportedFlavorException(flavor);
			}
			List<Object> transferData = new ArrayList<>(theComponents.length);
			for (Transferable c : theComponents)
				transferData.add(c.getTransferData(single));
			return Collections.unmodifiableList(transferData);
		}
	}

	public static class OrTransferable extends CompositeTransferable {
		private List<DataFlavor> theFlavors;

		public OrTransferable(Transferable... components) {
			super(components);
			ArrayList<DataFlavor> flavors = Arrays.stream(theComponents).flatMap(c -> Arrays.stream(c.getTransferDataFlavors())).distinct()
				.collect(Collectors.toCollection(() -> new ArrayList<>()));
			flavors.trimToSize();
			theFlavors = Collections.unmodifiableList(flavors);
		}

		@Override
		public DataFlavor[] getTransferDataFlavors() {
			return theFlavors.toArray(new DataFlavor[theFlavors.size()]);
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			for (Transferable c : theComponents) {
				if (c.isDataFlavorSupported(flavor))
					return true;
			}
			return false;
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
			for (Transferable c : theComponents) {
				if (c.isDataFlavorSupported(flavor))
					return c.getTransferData(flavor);
			}
			throw new UnsupportedFlavorException(flavor);
		}
	}
}

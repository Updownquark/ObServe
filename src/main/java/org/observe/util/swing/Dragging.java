package org.observe.util.swing;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.Icon;
import javax.swing.TransferHandler;
import javax.swing.TransferHandler.TransferSupport;

import org.observe.util.TypeTokens;
import org.qommons.BiTuple;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterList;

public class Dragging {
	public static class MultiFlavor extends DataFlavor {
		public final DataFlavor single;

		public MultiFlavor(DataFlavor single) {
			super(List.class, "List<" + single.getHumanPresentableName() + ">");
			this.single = single;
		}
	}

	public interface TransferSource<E> {
		<E2> TransferSource<E> forType(Class<E2> type, Predicate<? super E> filter, Function<? super E, ? extends E2> map,
			Consumer<? super TransferSource<E2>> source);

		TransferSource<E> draggable(boolean draggable);

		TransferSource<E> copyable(boolean copyable);

		TransferSource<E> movable(boolean movable);

		default TransferSource<E> toFlavor(DataFlavor flavor, DataSourceTransform<? super E> transform) {
			advertiseFlavor(flavor);
			return toFlavors(Arrays.asList(flavor), transform);
		}

		default TransferSource<E> toFlavors(Collection<? extends DataFlavor> flavors, DataSourceTransform<? super E> transform) {
			for (DataFlavor f : flavors)
				advertiseFlavor(f);
			Set<DataFlavor> flavorSet = flavors instanceof Set ? (Set<DataFlavor>) flavors : new LinkedHashSet<>(flavors);
			return toFlavorLike(flavorSet::contains, transform);
		}

		TransferSource<E> advertiseFlavor(DataFlavor flavor);

		TransferSource<E> toFlavorLike(Predicate<? super DataFlavor> flavors, DataSourceTransform<? super E> transform);

		TransferSource<E> toObject(Class<E> type);

		// TODO default this, supporting multiple text-based flavors
		TransferSource<E> toText(Function<? super E, ? extends CharSequence> toString);

		int getSourceActions();

		Transferable createTransferable(E value);
	}

	public interface DataSourceTransform<E> {
		boolean canTransform(E value, DataFlavor flavor);

		Object transform(E value, DataFlavor flavor) throws IOException;
	}

	public interface TransferAccepter<R, C, E> {
		<E2> TransferAccepter<R, C, E> forType(Class<E2> type, Predicate<? super E2> filter, Function<? super E2, ? extends E> map,
			Consumer<? super TransferAccepter<R, C, E2>> accept);

		TransferAccepter<R, C, E> draggable(boolean draggable);

		TransferAccepter<R, C, E> pastable(boolean pastable);

		TransferAccepter<R, C, E> appearance(Supplier<Icon> appearance);

		default TransferAccepter<R, C, E> fromFlavor(DataFlavor flavor, DataAccepterTransform<R, C, ? extends E> data) {
			return fromFlavors(Arrays.asList(flavor), data);
		}

		TransferAccepter<R, C, E> fromFlavors(Collection<? extends DataFlavor> flavors, DataAccepterTransform<R, C, ? extends E> data);

		TransferAccepter<R, C, E> fromObject(Class<E> type);

		TransferAccepter<R, C, E> fromObject(Class<E> type, DataAccepterTransform<R, C, ? extends E> action);

		TransferAccepter<R, C, E> fromText(Function<? super CharSequence, ? extends E> fromString);

		boolean canAccept(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
			TransferSupport transfer, boolean withMulti);

		Icon getDragAppearance();

		BetterList<E> accept(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
			Transferable transferable, boolean withMulti, boolean testOnly) throws IOException;
	}

	public interface DataAccepterTransform<R, C, E> {
		boolean canAccept(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter, List<?> value,
			DataFlavor flavor);

		List<E> transform(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter, List<?> values,
			DataFlavor flavor, boolean testOnly) throws IOException;
	}

	public interface DataConsumer<R, C, E> {
		boolean consume(ModelCell<? extends R, ? extends C> target, E incoming, boolean justTest);
	}

	static class SimpleTransferSource<E> implements TransferSource<E> {
		private final Set<DataFlavor> theFlavors;
		private final List<BiTuple<Predicate<? super DataFlavor>, DataSourceTransform<? super E>>> theTransforms;
		private List<Filtered<E, ?>> theFiltered;
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
		public <E2> TransferSource<E> forType(Class<E2> type, Predicate<? super E> filter, Function<? super E, ? extends E2> map,
			Consumer<? super TransferSource<E2>> source) {
			if (theFiltered == null)
				theFiltered = new ArrayList<>(3);
			theFiltered.add(new Filtered<>(filter, map, source));
			return this;
		}

		@Override
		public TransferSource<E> draggable(boolean draggable) {
			isDraggable = draggable;
			return this;
		}

		@Override
		public TransferSource<E> copyable(boolean copyable) {
			isCopyable = copyable;
			return this;
		}

		@Override
		public TransferSource<E> movable(boolean movable) {
			isMovable = movable;
			return this;
		}

		@Override
		public TransferSource<E> advertiseFlavor(DataFlavor flavor) {
			theFlavors.add(flavor);
			return this;
		}

		@Override
		public TransferSource<E> toFlavorLike(Predicate<? super DataFlavor> flavors, DataSourceTransform<? super E> transform) {
			theTransforms.add(new BiTuple<>(flavors, transform));
			return this;
		}

		@Override
		public TransferSource<E> toObject(Class<E> type) {
			advertiseFlavor(new DataFlavor(type, type.getName()));
			return toFlavorLike(
				f -> f.getRepresentationClass() != null && type.isAssignableFrom(f.getRepresentationClass()),
				new DataSourceTransform<E>() {
					@Override
					public boolean canTransform(Object value, DataFlavor flavor) {
						if (!type.isInstance(value))
							return false;
						return true;
					}

					@Override
					public Object transform(E value, DataFlavor flavor) {
						return value;
					}
				});
		}

		@Override
		public TransferSource<E> toText(Function<? super E, ? extends CharSequence> toString) {
			return toFlavor(DataFlavor.getTextPlainUnicodeFlavor(), new DataSourceTransform<E>() {
				@Override
				public boolean canTransform(Object value, DataFlavor flavor) {
					return true;
				}

				@Override
				public Object transform(E value, DataFlavor flavor) {
					return new StringReader(toString.apply(value).toString());
				}
			});
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
		public Transferable createTransferable(E value) {
			return getFlavors(null, value);
		}

		SimpleTransferable<E> getFlavors(SimpleTransferable<E> t, E value) {
			if (theFiltered != null) {
				for (Filtered<E, ?> filtered : theFiltered) {
					t = (SimpleTransferable<E>) filtered.getFlavors(t, value);
				}
			}
			if (!theFlavors.isEmpty() || !theTransforms.isEmpty()) {
				if (t == null)
					t = new SimpleTransferable<>(value);
				t.advertiseFlavors(theFlavors);
				t.acceptFlavors(theTransforms);
			}
			return t;
		}

		static class Filtered<E, E2> {
			private final Predicate<? super E> theFilter;
			private final Function<? super E, ? extends E2> theMap;
			private final Consumer<? super TransferSource<E2>> theValue;

			Filtered(Predicate<? super E> filter, Function<? super E, ? extends E2> map,
				Consumer<? super TransferSource<E2>> value) {
				theFilter = filter;
				theMap = map;
				theValue = value;
			}

			SimpleTransferable<?> getFlavors(SimpleTransferable<?> t, E value) {
				if (theFilter != null && !theFilter.test(value))
					return t;
				SimpleTransferSource<E2> src = new SimpleTransferSource<>();
				theValue.accept(src);
				return src.getFlavors((SimpleTransferable<E2>) t, theMap.apply(value));
			}
		}
	}

	static class SimpleTransferable<E> implements Transferable {
		private final E theValue;
		private final Set<DataFlavor> theFlavors;
		private final List<BiTuple<Predicate<? super DataFlavor>, ? extends DataSourceTransform<? super E>>> theTransforms;

		SimpleTransferable(E value) {
			theValue = value;
			theFlavors = new LinkedHashSet<>();
			theTransforms = new ArrayList<>();
		}

		void advertiseFlavors(Collection<? extends DataFlavor> flavors) {
			theFlavors.addAll(flavors);
		}

		void acceptFlavors(List<? extends BiTuple<Predicate<? super DataFlavor>, ? extends DataSourceTransform<? super E>>> transforms) {
			theTransforms.addAll(transforms);
		}

		@Override
		public DataFlavor[] getTransferDataFlavors() {
			return theFlavors.toArray(new DataFlavor[theFlavors.size()]);
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			for (BiTuple<Predicate<? super DataFlavor>, ? extends DataSourceTransform<? super E>> transform : theTransforms) {
				if ((transform.getValue1() == null || transform.getValue1().test(flavor))//
					&& transform.getValue2().canTransform(theValue, flavor))
					return true;
			}
			return false;
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
			for (BiTuple<Predicate<? super DataFlavor>, ? extends DataSourceTransform<? super E>> transform : theTransforms) {
				if ((transform.getValue1() == null || transform.getValue1().test(flavor))//
					&& transform.getValue2().canTransform(theValue, flavor))
					return transform.getValue2().transform(theValue, flavor);
			}
			throw new UnsupportedFlavorException(flavor);
		}

		@Override
		public String toString() {
			return String.valueOf(theValue);
		}
	}

	static class SimpleTransferAccepter<R, C, E> implements TransferAccepter<R, C, E> {
		private List<BiTuple<Set<DataFlavor>, DataAccepterTransform<R, C, ? extends E>>> theFlavors;
		private List<Filtered<R, C, E, ?>> theFiltered;
		private Supplier<Icon> theAppearance;
		private boolean isDraggable;
		private boolean isPastable;

		SimpleTransferAccepter() {
			theFlavors = new ArrayList<>(3);
			isDraggable = true;
		}

		@Override
		public <E2> TransferAccepter<R, C, E> forType(Class<E2> type, Predicate<? super E2> filter,
			Function<? super E2, ? extends E> map, Consumer<? super TransferAccepter<R, C, E2>> accept) {
			if (theFiltered == null)
				theFiltered = new ArrayList<>(3);
			Predicate<E2> fFilter = filter == null ? type::isInstance : v -> {
				if (!type.isInstance(v))
					return false;
				return filter.test(v);
			};
			SimpleTransferAccepter<R, C, E2> accepter = new SimpleTransferAccepter<>();
			accept.accept(accepter);
			theFiltered.add(new Filtered<>(fFilter, map, accepter));
			return this;
		}

		@Override
		public TransferAccepter<R, C, E> draggable(boolean draggable) {
			isDraggable = draggable;
			return this;
		}

		@Override
		public TransferAccepter<R, C, E> pastable(boolean pastable) {
			isPastable = pastable;
			return this;
		}

		@Override
		public TransferAccepter<R, C, E> appearance(Supplier<Icon> appearance) {
			theAppearance = appearance;
			return this;
		}

		@Override
		public TransferAccepter<R, C, E> fromFlavors(Collection<? extends DataFlavor> flavors,
			DataAccepterTransform<R, C, ? extends E> data) {
			Set<DataFlavor> flavorSet = new LinkedHashSet<>(flavors);
			theFlavors.add(new BiTuple<>(flavorSet, data));
			return this;
		}

		@Override
		public TransferAccepter<R, C, E> fromObject(Class<E> type) {
			Class<E> wrapped = TypeTokens.get().wrap(type);
			return fromFlavor(new DataFlavor(type, type.getName()), new DataAccepterTransform<R, C, E>() {
				@Override
				public boolean canAccept(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
					List<?> values, DataFlavor flavor) {
					for (Object value : values) {
						if (type.isPrimitive() && value == null)
							return false;
						if (value != null && !wrapped.isInstance(value))
							return false;
					}
					return true;
				}

				@Override
				public List<E> transform(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
					List<?> values,
					DataFlavor flavor, boolean testOnly) throws IOException {
					return (List<E>) values;
				}
			});
		}

		@Override
		public TransferAccepter<R, C, E> fromObject(Class<E> type, DataAccepterTransform<R, C, ? extends E> data) {
			return fromFlavor(new DataFlavor(type, type.getName()), data);
		}

		@Override
		public TransferAccepter<R, C, E> fromText(Function<? super CharSequence, ? extends E> fromString) {
			return fromFlavor(DataFlavor.getTextPlainUnicodeFlavor(), new DataAccepterTransform<R, C, E>() {
				@Override
				public boolean canAccept(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
					List<?> values, DataFlavor flavor) {
					return true;
				}

				@Override
				public List<E> transform(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
					List<?> values,
					DataFlavor flavor, boolean testOnly) throws IOException {
					List<E> transformed = new ArrayList<>(values.size());
					char[] buffer = new char[1028];
					for (Object value : values) {
						StringWriter writer = new StringWriter();
						int read = ((Reader) value).read(buffer);
						while (read >= 0) {
							writer.write(buffer, 0, read);
							read = ((Reader) value).read(buffer);
						}
						transformed.add(fromString.apply(writer.toString()));
					}
					return transformed;
				}
			});
		}

		@Override
		public boolean canAccept(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
			TransferSupport transfer, boolean withMulti) {
			if (transfer.isDrop()) {
				if (!isDraggable)
					return false;
			} else if (!isPastable)
				return false;
			if (theFiltered != null) {
				for (Filtered<R, C, E, ?> filtered : theFiltered) {
					if (filtered.canAccept(targetCell, leftOfCenter, aboveCenter, transfer, withMulti))
						return true;
				}
			}
			for (BiTuple<Set<DataFlavor>, DataAccepterTransform<R, C, ? extends E>> flavor : theFlavors) {
				for (DataFlavor f : flavor.getValue1()) {
					DataFlavor f2 = null;
					List<E> transferData = null;
					try {
						if (transfer.isDataFlavorSupported(f)) {
							f2 = f;
							transferData = Collections.singletonList((E) transfer.getTransferable().getTransferData(f));
						} else if (f instanceof MultiFlavor) {
							f2 = f;
							if (transfer.isDataFlavorSupported(((MultiFlavor) f).single)) {
								transferData = (List<E>) transfer.getTransferable().getTransferData(f);
							}
						} else if (withMulti) {
							f2 = new MultiFlavor(f);
							if (transfer.isDataFlavorSupported(f2))
								transferData = (List<E>) transfer.getTransferable().getTransferData(f2);
						}
					} catch (IOException | UnsupportedFlavorException e) {
						e.printStackTrace();
						continue;
					}
					if (flavor.getValue2().canAccept(targetCell, leftOfCenter, aboveCenter, transferData, f2))
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
		public BetterList<E> accept(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
			Transferable transfer, boolean withMulti, boolean testOnly) throws IOException {
			if (theFiltered != null) {
				for (Filtered<R, C, E, ?> filtered : theFiltered) {
					BetterList<E> data = filtered.accept(targetCell, leftOfCenter, aboveCenter, transfer, withMulti, testOnly);
					if (data != null)
						return data;
				}
			}
			for (BiTuple<Set<DataFlavor>, DataAccepterTransform<R, C, ? extends E>> flavor : theFlavors) {
				for (DataFlavor f : flavor.getValue1()) {
					DataFlavor f2 = null;
					List<E> transferData = null;
					try {
						if (transfer.isDataFlavorSupported(f)) {
							f2 = f;
							transferData = Collections.singletonList((E) transfer.getTransferData(f));
						} else if (f instanceof MultiFlavor) {
							f2 = f;
							if (transfer.isDataFlavorSupported(((MultiFlavor) f).single)) {
								transferData = (List<E>) transfer.getTransferData(f);
							}
						} else if (withMulti) {
							f2 = new MultiFlavor(f);
							if (transfer.isDataFlavorSupported(f2))
								transferData = (List<E>) transfer.getTransferData(f2);
						}
					} catch (IOException | UnsupportedFlavorException e) {
						e.printStackTrace();
						continue;
					}
					if (transferData != null) {
						if (flavor.getValue2().canAccept(targetCell, leftOfCenter, aboveCenter, transferData, f2)) {
							List<? extends E> data = flavor.getValue2().transform(targetCell, leftOfCenter, aboveCenter, transferData, f2,
								false);
							return BetterList.of(data);
						}
					}
				}
			}
			return null;
		}

		static class Filtered<R, C, E, E2> {
			private final Predicate<? super E2> theFilter;
			private final Function<? super E2, ? extends E> theMap;
			private final SimpleTransferAccepter<R, C, E2> theValue;

			Filtered(Predicate<? super E2> filter, Function<? super E2, ? extends E> map, SimpleTransferAccepter<R, C, E2> value) {
				theFilter = filter;
				theMap = map;
				theValue = value;
			}

			boolean canAccept(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
				TransferSupport transfer, boolean withMulti) {
				if (!theValue.canAccept(targetCell, leftOfCenter, aboveCenter, transfer, withMulti))
					return false;
				List<E2> data;
				try {
					data = theValue.accept(targetCell, leftOfCenter, aboveCenter, transfer.getTransferable(), withMulti, true);
				} catch (IOException e) {
					throw new IllegalStateException("Badly advertised support", e);
				}
				if (data == null)
					throw new IllegalStateException("Badly advertised support");
				if (theFilter != null) {
					boolean anyPass = false;
					for (E2 d : data) {
						if (theFilter.test(d)) {
							anyPass = true;
							break;
						}
					}
					if (!anyPass)
						return false;
				}
				return true;
			}

			BetterList<E> accept(ModelCell<? extends R, ? extends C> targetCell, boolean leftOfCenter, boolean aboveCenter,
				Transferable transferable, boolean withMulti, boolean testOnly) throws IOException {
				BetterList<E2> data = theValue.accept(targetCell, leftOfCenter, aboveCenter, transferable, withMulti, testOnly);
				if (data == null)
					return null;
				return QommonsUtils.filterMap(data, theFilter, theMap);
			}
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

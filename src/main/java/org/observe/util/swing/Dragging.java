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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.TransferHandler;
import javax.swing.TransferHandler.TransferSupport;

import org.observe.util.TypeTokens;
import org.observe.util.swing.PanelPopulation.ImageControl;
import org.qommons.BiTuple;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterList;

import com.google.common.reflect.TypeToken;

public class Dragging {
	public static class MultiFlavor extends DataFlavor {
		public final DataFlavor single;

		public MultiFlavor(DataFlavor single) {
			super(List.class, "List<" + single.getHumanPresentableName() + ">");
			this.single = single;
		}
	}

	public interface TransferSource<E> {
		<E2> TransferSource<E> forType(TypeToken<E2> type, Predicate<? super E> filter, Function<? super E, ? extends E2> map,
			Consumer<? super TransferSource<E2>> source);

		default <E2 extends E> TransferSource<E> forSubType(Class<E2> type, Consumer<? super TransferSource<E2>> source) {
			return forType(TypeTokens.get().of(type), value -> type.isInstance(value), value -> (E2) value, source);
		}

		TransferSource<E> draggable(boolean draggable);

		TransferSource<E> copyable(boolean copyable);

		TransferSource<E> movable(boolean movable);

		TransferSource<E> appearance(Consumer<TransferAppearance<E>> appearance);

		default TransferSource<E> toFlavor(DataFlavor flavor, DataSourceTransform<? super E> transform) {
			advertiseFlavor(flavor);
			return toFlavors(Arrays.asList(flavor), transform);
		}

		default TransferSource<E> toFlavors(Collection<? extends DataFlavor> flavors, DataSourceTransform<? super E> transform) {
			for (DataFlavor f : flavors)
				advertiseFlavor(f);
			Set<DataFlavor> flavorSet = flavors instanceof Set ? (Set<DataFlavor>) flavors : new LinkedHashSet<>(flavors);
			return toFlavorLike(f -> flavorSet.contains(f), transform);
		}

		TransferSource<E> advertiseFlavor(DataFlavor flavor);

		TransferSource<E> toFlavorLike(Predicate<? super DataFlavor> flavors, DataSourceTransform<? super E> transform);

		TransferSource<E> toObject();

		// TODO default this, supporting multiple text-based flavors
		TransferSource<E> toText(Function<? super E, ? extends CharSequence> toString);

		int getSourceActions();

		Transferable createTransferable(E value);
	}

	public interface TransferAppearance<E> {
		E getValue();

		TransferAppearance<E> withDragIcon(String imageLocation, Consumer<PanelPopulation.ImageControl> imgConfig);

		TransferAppearance<E> withDragOffset(int x, int y);

		<E2> TransferAppearance<E> inCase(Predicate<? super E> filter, Function<? super E, ? extends E2> map,
			Consumer<? super TransferAppearance<E2>> appearance);
	}

	public interface DataSourceTransform<E> {
		boolean canTransform(Object value, DataFlavor flavor);

		Object transform(E value, DataFlavor flavor) throws IOException;
	}

	public interface TransferAccepter<R, C, E> {
		<E2> TransferAccepter<R, C, E> forType(TypeToken<E2> type, Predicate<? super E2> filter, Function<? super E2, ? extends E> map,
			Consumer<? super TransferAccepter<R, C, E2>> accept);

		default <E2> TransferAccepter<R, C, E> forType(Class<E2> type, Predicate<? super E2> filter, Function<? super E2, ? extends E> map,
			Consumer<? super TransferAccepter<R, C, E2>> accept) {
			return forType(TypeTokens.get().of(type), filter, map, accept);
		}

		TransferAccepter<R, C, E> draggable(boolean draggable);

		TransferAccepter<R, C, E> pastable(boolean pastable);

		TransferAccepter<R, C, E> appearance(Consumer<TransferAppearance<E>> appearance);

		default TransferAccepter<R, C, E> fromFlavor(DataFlavor flavor, DataAccepterTransform<R, C, ? extends E> data) {
			return fromFlavors(Arrays.asList(flavor), data);
		}

		TransferAccepter<R, C, E> fromFlavors(Collection<? extends DataFlavor> flavors, DataAccepterTransform<R, C, ? extends E> data);

		TransferAccepter<R, C, E> fromObject();

		TransferAccepter<R, C, E> fromText(Function<? super CharSequence, ? extends E> fromString);

		boolean canAccept(ModelCell<? extends R, ? extends C> targetCell, TransferSupport transfer, boolean withMulti);

		BetterList<E> accept(ModelCell<? extends R, ? extends C> targetCell, Transferable transferable, boolean withMulti, boolean testOnly)
			throws IOException;
	}

	public interface DataAccepterTransform<R, C, E> {
		boolean canAccept(ModelCell<? extends R, ? extends C> targetCell, Object value, DataFlavor flavor);

		E transform(ModelCell<? extends R, ? extends C> targetCell, Object value, DataFlavor flavor, boolean testOnly) throws IOException;
	}

	public interface DataConsumer<R, C, E> {
		boolean consume(ModelCell<? extends R, ? extends C> target, E incoming, boolean justTest);
	}

	static class SimpleTransferSource<E> implements TransferSource<E> {
		private final TypeToken<E> theType;
		private final Set<DataFlavor> theFlavors;
		private final List<BiTuple<Predicate<? super DataFlavor>, DataSourceTransform<? super E>>> theTransforms;
		private List<Filtered<E, ?>> theFiltered;
		private Consumer<TransferAppearance<E>> theAppearance;
		private boolean isDraggable;
		private boolean isCopyable;
		private boolean isMovable;

		SimpleTransferSource(TypeToken<E> type) {
			theType = type;
			theFlavors = new LinkedHashSet<>();
			theTransforms = new ArrayList<>(2);
			isDraggable = true;
			isCopyable = isMovable = true;
		}

		@Override
		public <E2> TransferSource<E> forType(TypeToken<E2> type, Predicate<? super E> filter, Function<? super E, ? extends E2> map,
			Consumer<? super TransferSource<E2>> source) {
			if (theFiltered == null)
				theFiltered = new ArrayList<>(3);
			theFiltered.add(new Filtered<>(type, filter, map, source));
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
		public TransferSource<E> appearance(Consumer<TransferAppearance<E>> appearance) {
			if (appearance == null)
				theAppearance = null;
			else if (theAppearance == null)
				theAppearance = appearance;
			else {
				Consumer<TransferAppearance<E>> oldApp = theAppearance;
				theAppearance = app -> {
					oldApp.accept(app);
					appearance.accept(app);
				};
			}
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
		public TransferSource<E> toObject() {
			advertiseFlavor(new DataFlavor(TypeTokens.getRawType(theType), theType.toString()));
			return toFlavorLike(
				f -> f.getRepresentationClass() != null && TypeTokens.getRawType(theType).isAssignableFrom(f.getRepresentationClass()),
				new DataSourceTransform<E>() {
					@Override
					public boolean canTransform(Object value, DataFlavor flavor) {
						if (!(TypeTokens.get().isInstance(theType, value)))
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
			private final TypeToken<E2> theType;
			private final Predicate<? super E> theFilter;
			private final Function<? super E, ? extends E2> theMap;
			private final Consumer<? super TransferSource<E2>> theValue;

			Filtered(TypeToken<E2> type, Predicate<? super E> filter, Function<? super E, ? extends E2> map,
				Consumer<? super TransferSource<E2>> value) {
				theType = type;
				theFilter = filter;
				theMap = map;
				theValue = value;
			}

			SimpleTransferable<?> getFlavors(SimpleTransferable<?> t, E value) {
				if (theFilter != null && !theFilter.test(value))
					return t;
				SimpleTransferSource<E2> src = new SimpleTransferSource<>(theType);
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
			for (BiTuple<Predicate<? super DataFlavor>, ? extends DataSourceTransform<?>> transform : theTransforms) {
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

	static class SimpleTransferAppearance<E> implements TransferAppearance<E> {
		private E theValue;
		private String theImageLocation;
		private ImageControl theImageControl;
		private int theOffsetX;
		private int theOffsetY;

		SimpleTransferAppearance(E value) {
			theValue = value;
		}

		@Override
		public E getValue() {
			return theValue;
		}

		@Override
		public TransferAppearance<E> withDragIcon(String imageLocation, Consumer<ImageControl> imgConfig) {
			theImageLocation = imageLocation;
			theImageControl = new PanelPopulation.SimpleImageControl(imageLocation);
			if (imgConfig != null)
				imgConfig.accept(theImageControl);
			return this;
		}

		@Override
		public TransferAppearance<E> withDragOffset(int x, int y) {
			theOffsetX = x;
			theOffsetY = y;
			return this;
		}

		@Override
		public <E2> TransferAppearance<E> inCase(Predicate<? super E> filter, Function<? super E, ? extends E2> map,
			Consumer<? super TransferAppearance<E2>> appearance) {
			if (filter != null && !filter.test(theValue))
				return this;
			E oldValue = theValue;
			theValue = (E) map.apply(oldValue);
			appearance.accept((TransferAppearance<E2>) this);
			theValue = oldValue;
			return this;
		}
	}

	static class SimpleTransferAccepter<R, C, E> implements TransferAccepter<R, C, E> {
		private final TypeToken<E> theType;
		private List<BiTuple<Set<DataFlavor>, DataAccepterTransform<R, C, ? extends E>>> theFlavors;
		private List<Filtered<R, C, E, ?>> theFiltered;
		private Consumer<TransferAppearance<E>> theAppearance;
		private boolean isDraggable;
		private boolean isPastable;

		SimpleTransferAccepter(TypeToken<E> type) {
			theType = type;
			theFlavors = new ArrayList<>(3);
			isDraggable = true;
		}

		@Override
		public <E2> TransferAccepter<R, C, E> forType(TypeToken<E2> type, Predicate<? super E2> filter,
			Function<? super E2, ? extends E> map, Consumer<? super TransferAccepter<R, C, E2>> accept) {
			if (theFiltered == null)
				theFiltered = new ArrayList<>(3);
			SimpleTransferAccepter<R, C, E2> accepter = new SimpleTransferAccepter<>(type);
			accept.accept(accepter);
			theFiltered.add(new Filtered<>(filter, map, accepter));
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
		public TransferAccepter<R, C, E> appearance(Consumer<TransferAppearance<E>> appearance) {
			if (appearance == null)
				theAppearance = null;
			else if (theAppearance == null)
				theAppearance = appearance;
			else {
				Consumer<TransferAppearance<E>> oldApp = theAppearance;
				theAppearance = app -> {
					oldApp.accept(app);
					appearance.accept(app);
				};
			}
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
		public TransferAccepter<R, C, E> fromObject() {
			return fromFlavor(new DataFlavor(TypeTokens.getRawType(theType), theType.toString()), new DataAccepterTransform<R, C, E>() {
				@Override
				public boolean canAccept(ModelCell<? extends R, ? extends C> targetCell, Object value, DataFlavor flavor) {
					if (value == null)
						return !theType.isPrimitive();
					return TypeTokens.get().isInstance(theType, value);
				}

				@Override
				public E transform(ModelCell<? extends R, ? extends C> targetCell, Object value, DataFlavor flavor, boolean testOnly)
					throws IOException {
					return (E) value;
				}
			});
		}

		@Override
		public TransferAccepter<R, C, E> fromText(Function<? super CharSequence, ? extends E> fromString) {
			return fromFlavor(DataFlavor.getTextPlainUnicodeFlavor(), new DataAccepterTransform<R, C, E>() {
				@Override
				public boolean canAccept(ModelCell<? extends R, ? extends C> targetCell, Object value, DataFlavor flavor) {
					return true;
				}

				@Override
				public E transform(ModelCell<? extends R, ? extends C> targetCell, Object value, DataFlavor flavor, boolean testOnly)
					throws IOException {
					StringWriter writer = new StringWriter();
					char[] buffer = new char[1028];
					int read = ((Reader) value).read(buffer);
					while (read >= 0) {
						writer.write(buffer, 0, read);
						read = ((Reader) value).read(buffer);
					}
					return fromString.apply(writer.toString());
				}
			});
		}

		@Override
		public boolean canAccept(ModelCell<? extends R, ? extends C> targetCell, TransferSupport transfer, boolean withMulti) {
			if (transfer.isDrop()) {
				if (!isDraggable)
					return false;
			} else if (!isPastable)
				return false;
			if (theFiltered != null) {
				for (Filtered<R, C, E, ?> filtered : theFiltered) {
					if (filtered.canAccept(targetCell, transfer, withMulti))
						return true;
				}
			}
			for (BiTuple<Set<DataFlavor>, DataAccepterTransform<R, C, ? extends E>> flavor : theFlavors) {
				for (DataFlavor f : flavor.getValue1()) {
					if (transfer.isDataFlavorSupported(f))
						return true;
					else if (f instanceof MultiFlavor) {
						if (transfer.isDataFlavorSupported(((MultiFlavor) f).single))
							return true;
					} else if (withMulti) {
						if (transfer.isDataFlavorSupported(new MultiFlavor(f)))
							return true;
					}
				}
			}
			return false;
		}

		@Override
		public BetterList<E> accept(ModelCell<? extends R, ? extends C> targetCell, Transferable transferable, boolean withMulti,
			boolean testOnly) throws IOException {
			if (theFiltered != null) {
				for (Filtered<R, C, E, ?> filtered : theFiltered) {
					BetterList<E> data = filtered.accept(targetCell, transferable, withMulti, testOnly);
					if (data != null)
						return data;
				}
			}
			for (BiTuple<Set<DataFlavor>, DataAccepterTransform<R, C, ? extends E>> flavor : theFlavors) {
				for (DataFlavor f : flavor.getValue1()) {
					DataFlavor f2;
					boolean multi = false;
					if (!transferable.isDataFlavorSupported(f)) {
						if (f instanceof MultiFlavor && transferable.isDataFlavorSupported(((MultiFlavor) f).single))
							f2 = ((MultiFlavor) f).single;
						else if (withMulti && transferable.isDataFlavorSupported(new MultiFlavor(f))) {
							multi = true;
							f2 = new MultiFlavor(f);
						} else
							continue;
					} else
						f2 = f;
					Object data;
					try {
						data = transferable.getTransferData(f2);
					} catch (UnsupportedFlavorException e) {
						throw new IllegalStateException("But you said you supported " + f2, e);
					}
					if (multi) {
						BetterList<E> list = QommonsUtils.filterMapE((Collection<E>) data, //
							d -> flavor.getValue2().canAccept(targetCell, d, ((MultiFlavor) f2).single), //
							d -> flavor.getValue2().transform(targetCell, d, ((MultiFlavor) f2).single, testOnly));
						if (!list.isEmpty())
							return list;
					} else if (flavor.getValue2().canAccept(targetCell, data, f2)) {
						return BetterList.of(flavor.getValue2().transform(targetCell, data, f2, testOnly));
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

			boolean canAccept(ModelCell<? extends R, ? extends C> targetCell, TransferSupport transfer, boolean withMulti) {
				if (!theValue.canAccept(targetCell, transfer, withMulti))
					return false;
				List<E2> data;
				try {
					data = theValue.accept(targetCell, transfer.getTransferable(), withMulti, true);
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

			BetterList<E> accept(ModelCell<? extends R, ? extends C> targetCell, Transferable transferable, boolean withMulti,
				boolean testOnly) throws IOException {
				BetterList<E2> data = theValue.accept(targetCell, transferable, withMulti, testOnly);
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

package org.observe.collect;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.observe.DefaultObservable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.util.ObservableUtils;

import prisms.lang.Type;

class ObservableSubList<E> implements ObservableList.PartialListImpl<E> {
	private final ObservableList<E> theList;
	private final int theOffset;
	private int theSize;

	ObservableSubList(ObservableList<E> list, int fromIndex, int toIndex) {
		if (fromIndex < 0)
			throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
		if (fromIndex > toIndex)
			throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
		theList = list;
		theOffset = fromIndex;
		theSize = toIndex - fromIndex;
	}

	@Override
	public Runnable onOrderedElement(Consumer<? super OrderedObservableElement<E>> onElement) {
		List<OrderedObservableElement<E>> elements = new ArrayList<>();
		List<Element> wrappers = new ArrayList<>();
		return theList.onOrderedElement(element -> {
			int index = element.getIndex();
			Element wrapper = new Element(element);
			elements.add(index, element);
			wrappers.add(index, wrapper);
			int removeIdx = theOffset + theSize;
			if(index < removeIdx && removeIdx < wrappers.size())
				wrappers.get(removeIdx).remove();
			if(index < theOffset && theOffset < wrappers.size())
				onElement.accept(wrappers.get(theOffset));
		});
	}

	@Override
	public Type getType() {
		return theList.getType();
	}

	@Override
	public ObservableValue<CollectionSession> getSession() {
		return theList.getSession();
	}

	@Override
	public E set(int index, E element) {
		rangeCheck(index);
		return theList.set(index+theOffset, element);
	}

	@Override
	public E get(int index) {
		rangeCheck(index);
		return theList.get(index+theOffset);
	}

	@Override
	public int size() {
		int size = theList.size() - theOffset;
		if(theSize < size)
			size = theSize;
		return size;
	}

	@Override
	public void removeRange(int fromIndex, int toIndex) {
		for(int i = fromIndex; i < toIndex; i++)
			theList.remove(theOffset + i);
		theSize -= (toIndex-fromIndex);
	}

	@Override
	public List<E> subList(int fromIndex, int toIndex) {
		return new ObservableSubList<>(this, fromIndex, toIndex);
	}

	private void rangeCheck(int index) {
		if (index < 0 || index >= theSize)
			throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
	}

	private String outOfBoundsMsg(int index) {
		return "Index: "+index+", Size: "+theSize;
	}

	class Element implements OrderedObservableElement<E> {
		private final OrderedObservableElement<E> theWrapped;

		private final DefaultObservable<Void> theRemovedObservable;

		private final Observer<Void> theRemovedController;

		Element(OrderedObservableElement<E> wrap) {
			theWrapped = wrap;
			theRemovedObservable = new DefaultObservable<>();
			theRemovedController = theRemovedObservable.control(null);
		}

		@Override
		public ObservableValue<E> persistent() {
			return theWrapped.persistent();
		}

		@Override
		public Type getType() {
			return theWrapped.getType();
		}

		@Override
		public E get() {
			return theWrapped.get();
		}

		@Override
		public Runnable observe(Observer<? super ObservableValueEvent<E>> observer) {
			return theWrapped.takeUntil(theRemovedObservable).observe(new Observer<ObservableValueEvent<E>>() {
				@Override
				public <V extends ObservableValueEvent<E>> void onNext(V value) {
					observer.onNext(ObservableUtils.wrap(value, Element.this));
				}

				@Override
				public <V extends ObservableValueEvent<E>> void onCompleted(V value) {
					observer.onCompleted(ObservableUtils.wrap(value, Element.this));
				}
			});
		}

		@Override
		public int getIndex() {
			return theWrapped.getIndex() - theOffset;
		}

		void remove() {
			theRemovedController.onNext(null);
		}
	}
}

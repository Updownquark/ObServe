package org.observe.expresso.qonfig;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.qommons.BiTuple;
import org.qommons.ex.ExBiFunction;
import org.qommons.ex.ExConsumer;
import org.qommons.ex.ExFunction;

/**
 * A high-performance, tailored map of values stored by document paths
 *
 * @param <T> The type of values in the map
 */
public class DocumentMap<T> implements Map<String, T> {
	static final Comparator<String> DOCUMENT_COMPARE = (doc1, doc2) -> {
		if (doc1 == doc2)
			return 0;
		else if (doc1 == null)
			return -1;
		else if (doc2 == null)
			return 1;
		int comp = Integer.compare(doc1.length(), doc2.length());
		if (comp != 0)
			return comp;
		comp = Integer.compare(doc1.hashCode(), doc2.hashCode());
		if (comp != 0)
			return comp;
		for (int i = doc1.length(); i >= 0; i--) {
			comp = Character.compare(doc1.charAt(i), doc2.charAt(i));
			if (comp != 0)
				return comp;
		}
		return 0;
	};

	private DocumentMap<T> theInherited;
	private List<String> theDocuments;
	private List<T> theValues;
	private List<T> theExposedValues;
	private BiTuple<String, T> theQueryCache;

	/** @param inherited The document map to inherit from (may be null for a root document map) */
	public DocumentMap(DocumentMap<T> inherited) {
		theInherited = inherited;
		if (theInherited == null)
			init();
	}

	private void init() {
		if (theDocuments != null)
			return; // Already initialized
		if (theInherited != null) {
			theInherited.init();
			theDocuments = new ArrayList<>(theInherited.theDocuments.size() + 1);
			theValues = new ArrayList<>(theInherited.theDocuments.size() + 1);
			theDocuments.addAll(theInherited.theDocuments);
			theValues.addAll(theInherited.theValues);
			theQueryCache = theInherited.theQueryCache;
		} else {
			theDocuments = new ArrayList<>(2);
			theValues = new ArrayList<>(2);
		}
		theExposedValues = Collections.unmodifiableList(theValues);
		theInherited = null;
	}

	/** @return A new document map that extends this one */
	public DocumentMap<T> extend() {
		return new DocumentMap<>(this);
	}

	@Override
	public int size() {
		if (theInherited != null)
			return theInherited.size();
		else
			return theValues.size();
	}

	@Override
	public boolean isEmpty() {
		if (theInherited != null)
			return theInherited.isEmpty();
		else
			return theValues.isEmpty();
	}

	private int docIdx(String document) {
		return Collections.binarySearch(theDocuments, document, DOCUMENT_COMPARE);
	}

	private T getCache(Object key) {
		if (!(key instanceof String))
			return null;
		BiTuple<String, T> cache = theQueryCache;
		if (cache == null || DOCUMENT_COMPARE.compare(cache.getValue1(), (String) key) != 0) {
			int docIdx = docIdx((String) key);
			if (docIdx >= 0)
				theQueryCache = cache = new BiTuple<>((String) key, theValues.get(docIdx));
			else
				return null;
		}
		return cache.getValue2();
	}

	@Override
	public boolean containsKey(Object key) {
		if (!(key instanceof String))
			return false;
		if (theInherited != null)
			return theInherited.containsKey(key);
		BiTuple<String, T> cache = theQueryCache;
		if (cache != null && DOCUMENT_COMPARE.compare(cache.getValue1(), (String) key) == 0)
			return true;
		return docIdx((String) key) >= 0;
	}

	@Override
	public boolean containsValue(Object value) {
		if (theInherited != null)
			return theInherited.containsValue(value);
		else
			return theValues.contains(value);
	}

	@Override
	public Set<String> keySet() {
		if (theInherited != null)
			return theInherited.keySet();
		else
			return new KeySet();
	}

	@Override
	public List<T> values() {
		if (theInherited != null)
			return theInherited.values();
		else
			return theExposedValues;
	}

	@Override
	public Set<Entry<String, T>> entrySet() {
		if (theInherited != null)
			return theInherited.entrySet();
		else
			return new EntrySet();
	}

	@Override
	public T get(Object key) {
		if (theInherited != null)
			return theInherited.get(key);
		return getCache(key);
	}

	@Override
	public T put(String key, T value) {
		if (theInherited != null) {
			if (theInherited.get(key) == value)
				return value;
			init();
		}
		BiTuple<String, T> cache = theQueryCache;
		if (cache != null && DOCUMENT_COMPARE.compare(cache.getValue1(), key) == 0) {
			if (cache.getValue2() == value)
				return value;
			theQueryCache = new BiTuple<>(cache.getValue1(), value);
		}
		int docIdx = docIdx(key);
		if (docIdx >= 0) {
			return theValues.set(docIdx, value);
		} else {
			docIdx = -docIdx - 1;
			theDocuments.add(docIdx, key);
			theValues.add(docIdx, value);
			return null;
		}
	}

	@Override
	public T remove(Object key) {
		if (theInherited != null) {
			if (theInherited.get(key) == null)
				return null;
			init();
		}
		if (!(key instanceof String))
			return null;
		int docIdx = docIdx((String) key);
		if (docIdx < 0)
			return null;

		BiTuple<String, T> cache = theQueryCache;
		if (cache != null && DOCUMENT_COMPARE.compare(cache.getValue1(), (String) key) == 0)
			theQueryCache = null;

		theDocuments.remove(docIdx);
		return theValues.remove(docIdx);
	}

	@Override
	public void putAll(Map<? extends String, ? extends T> m) {
		for (Map.Entry<? extends String, ? extends T> entry : m.entrySet())
			put(entry.getKey(), entry.getValue());
	}

	@Override
	public void clear() {
		if (theInherited != null) {
			if (theInherited.isEmpty())
				return;
			theInherited = null;
			init();
		} else {
			theQueryCache = null;
			theDocuments.clear();
			theValues.clear();
		}
	}

	@Override
	public int hashCode() {
		return entrySet().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof Map))
			return false;
		Map<?, ?> other = (Map<?, ?>) obj;
		if (size() != other.size())
			return false;
		for (Map.Entry<String, T> entry : entrySet()) {
			if (!Objects.equals(entry.getValue(), other.get(entry.getKey())))
				return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return entrySet().toString();
	}

	/**
	 * @param <X> The type of exception that may be thrown by the action
	 * @param action The action to perform on each value in this map
	 * @return This map
	 * @throws X The thrown exception, if any
	 */
	public <X extends Throwable> DocumentMap<T> forEach(ExConsumer<? super T, X> action) throws X {
		for (T value : theValues)
			action.accept(value);
		return this;
	}

	/**
	 * Performs a successive operation on a value for each item in this map
	 *
	 * @param <X> The type of exception that may be thrown by the action
	 * @param init The initial value to operate on
	 * @param op The action to perform with each value in this map
	 * @return The result of the operation
	 * @throws X The thrown exception, if any
	 */
	public <T2, X extends Throwable> T2 operate(T2 init, ExBiFunction<? super T2, ? super T, ? extends T2, X> op) throws X {
		for (T value : theValues)
			init = op.apply(init, value);
		return init;
	}

	/**
	 * @param <T2> The type of the mapped values
	 * @param <X> The type of exeption that mapping function may throw
	 * @param map A copy of this map whose values are mapped (now, not lazily) with the given function
	 * @return The mapped map
	 * @throws X If the mapping function throws an exception
	 */
	public <T2, X extends Throwable> DocumentMap<T2> map(ExFunction<? super T, ? extends T2, X> map) throws X {
		DocumentMap<T2> mapped = new DocumentMap<>(null);
		for (Map.Entry<String, T> entry : entrySet())
			mapped.put(entry.getKey(), map.apply(entry.getValue()));
		return mapped;
	}

	class KeySet extends AbstractSet<String> {
		@Override
		public Iterator<String> iterator() {
			return new Iterator<String>() {
				private int theIndex;
				private boolean isRemoved;

				@Override
				public boolean hasNext() {
					return theIndex < size();
				}

				@Override
				public String next() {
					isRemoved = false;
					String doc = theDocuments.get(theIndex);
					theIndex++;
					return doc;
				}

				@Override
				public void remove() {
					if (theIndex == 0 || isRemoved)
						throw new IllegalStateException("remove() must be called after next()");
					theIndex--;
					isRemoved = true;
					theDocuments.remove(theIndex);
					theValues.remove(theIndex);
				}
			};
		}

		@Override
		public int size() {
			return theDocuments.size();
		}

		@Override
		public boolean contains(Object o) {
			return o instanceof String && docIdx((String) o) >= 0;
		}

		@Override
		public boolean remove(Object o) {
			if (!(o instanceof String))
				return false;
			int docIdx = docIdx((String) o);
			if (docIdx < 0)
				return false;
			theDocuments.remove(docIdx);
			theValues.remove(docIdx);
			return true;
		}

		@Override
		public void clear() {
			DocumentMap.this.clear();
		}
	}

	class EntrySet extends AbstractSet<Map.Entry<String, T>> {
		@Override
		public Iterator<Map.Entry<String, T>> iterator() {
			return new Iterator<Map.Entry<String, T>>() {
				private int theIndex;
				private boolean isRemoved;

				@Override
				public boolean hasNext() {
					return theIndex < size();
				}

				@Override
				public Map.Entry<String, T> next() {
					isRemoved = false;
					int index = theIndex;
					String key = theDocuments.get(index);
					Map.Entry<String, T> entry = new Map.Entry<String, T>() {
						@Override
						public String getKey() {
							return key;
						}

						@Override
						public T getValue() {
							check();
							return theValues.get(index);
						}

						@Override
						public T setValue(T value) {
							check();
							return theValues.set(index, value);
						}

						@Override
						public int hashCode() {
							return Objects.hashCode(key) ^ Objects.hashCode(getValue());
						}

						@Override
						public boolean equals(Object obj) {
							if (this == obj)
								return true;
							else if (!(obj instanceof Map.Entry))
								return false;
							Map.Entry<?, ?> other = (Map.Entry<?, ?>) obj;
							return key.equals(other.getKey()) && Objects.equals(getValue(), other.getValue());
						}

						@Override
						public String toString() {
							return key + "=" + getValue();
						}

						void check() throws IllegalStateException {
							if (index >= theDocuments.size() || theDocuments.get(index) != key)
								throw new IllegalStateException("This map has changed and this entry is no longer valid");
						}
					};
					theIndex++;
					return entry;
				}

				@Override
				public void remove() {
					if (theIndex == 0 || isRemoved)
						throw new IllegalStateException("remove() must be called after next()");
					theIndex--;
					isRemoved = true;
					theDocuments.remove(theIndex);
					theValues.remove(theIndex);
				}
			};
		}

		@Override
		public int size() {
			return theDocuments.size();
		}

		@Override
		public void clear() {
			DocumentMap.this.clear();
		}
	}
}

package org.observe.supertest.collect;

import org.qommons.TestHelper;
import org.qommons.collect.CollectionElement;

/**
 * Just implements {@link #validate(CollectionLinkElement, boolean)}
 *
 * @param <S> The type of the source link
 * @param <T> The type of this link
 */
public abstract class AbstractFlatMappedCollectionLink<S, T> extends ObservableCollectionLink<S, T> {
	/**
	 * @param path The path for this link
	 * @param sourceLink The source for this link
	 * @param def The collection definition for this link
	 * @param helper The randomness for this link
	 */
	public AbstractFlatMappedCollectionLink(String path, ObservableCollectionLink<?, S> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper) {
		super(path, sourceLink, def, helper);
	}

	@Override
	protected void validate(CollectionLinkElement<S, T> element, boolean transactionEnd) {
		if (!element.isPresent())
			return;
		CollectionLinkElement<?, S> source = element.getFirstSource();
		CollectionLinkElement<S, T> adj = CollectionElement
			.get(getElements().getAdjacentElement(element.getElementAddress(), false));
		while (adj != null && !adj.isPresent())
			adj = CollectionElement.get(getElements().getAdjacentElement(adj.getElementAddress(), false));
		if (adj != null) {
			CollectionLinkElement<?, S> adjSource = adj.getFirstSource();
			int comp = adjSource.getCollectionAddress().compareTo(source.getCollectionAddress());
			if (comp > 0)
				element.error("Bad ordering");
			else if (comp == 0 && adj.getCollectionAddress().compareTo(element.getCollectionAddress()) >= 0)
				element.error("Bad ordering");
		}

		adj = CollectionElement.get(getElements().getAdjacentElement(element.getElementAddress(), true));
		while (adj != null && !adj.isPresent())
			adj = CollectionElement.get(getElements().getAdjacentElement(adj.getElementAddress(), true));
		if (adj != null) {
			CollectionLinkElement<?, S> adjSource = adj.getFirstSource();
			int comp = adjSource.getCollectionAddress().compareTo(source.getCollectionAddress());
			if (comp < 0)
				element.error("Bad ordering");
			else if (comp == 0 && adj.getCollectionAddress().compareTo(element.getCollectionAddress()) <= 0)
				element.error("Bad ordering");
		}
	}
}

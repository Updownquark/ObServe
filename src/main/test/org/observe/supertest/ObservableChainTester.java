package org.observe.supertest;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Test;
import org.observe.supertest.links.BaseCollectionLink;
import org.observe.supertest.links.CollectionDerivedValues;
import org.observe.supertest.links.CombinedCollectionLink;
import org.observe.supertest.links.CombinedValueLink;
import org.observe.supertest.links.DistinctCollectionLink;
import org.observe.supertest.links.FactoringFlatMapCollectionLink;
import org.observe.supertest.links.FilteredCollectionLink;
import org.observe.supertest.links.FlattenedCollectionValuesLink;
import org.observe.supertest.links.FlattenedValueBaseCollectionLink;
import org.observe.supertest.links.MappedCollectionLink;
import org.observe.supertest.links.MappedValueLink;
import org.observe.supertest.links.ModFilteredCollectionLink;
import org.observe.supertest.links.ReversedCollectionLink;
import org.observe.supertest.links.SortedBaseCollectionLink;
import org.observe.supertest.links.SortedCollectionLink;
import org.observe.supertest.links.SubSetLink;
import org.qommons.LockDebug;
import org.qommons.QommonsUtils;
import org.qommons.TestHelper;
import org.qommons.TestHelper.Testable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollections;
import org.qommons.debug.Debug;

/** Tests many of the classes in ObServe using randomly generated observable structure chains and randomly generated-data. */
public class ObservableChainTester implements Testable {
	private static final int MAX_VALUE = 1000;
	/** Providers of random values for each type */
	public static final Map<TestValueType, Function<TestHelper, ?>> SUPPLIERS;
	static {
		SUPPLIERS = new HashMap<>();
		for (TestValueType type : TestValueType.values()) {
			switch (type) {
			case INT:
				SUPPLIERS.put(type, helper -> helper.getInt(0, MAX_VALUE));
				break;
			case DOUBLE:
				SUPPLIERS.put(type, helper -> helper.getDouble(0, MAX_VALUE));
				break;
			case STRING:
				SUPPLIERS.put(type, helper -> String.valueOf(helper.getInt(0, MAX_VALUE)));
				break;
			case BOOLEAN:
				SUPPLIERS.put(type, helper -> helper.getBoolean());
				break;
			}
		}
	}

	private static final int MAX_LINK_COUNT = 20;

	private static final List<ChainLinkGenerator> LINK_GENERATORS;

	static {
		List<ChainLinkGenerator> generators = new ArrayList<>();
		// Initial link generators
		generators.add(BaseCollectionLink.GENERATE);
		generators.add(SortedBaseCollectionLink.GENERATE_SORTED);
		generators.add(FlattenedValueBaseCollectionLink.GENERATE_FLATTENED);

		// Derived collection generators
		generators.add(MappedCollectionLink.GENERATE);
		generators.add(CombinedCollectionLink.GENERATE);
		generators.add(ReversedCollectionLink.GENERATE);
		generators.add(ModFilteredCollectionLink.GENERATE);
		generators.add(FilteredCollectionLink.GENERATE);
		generators.add(SortedCollectionLink.GENERATE);
		generators.add(DistinctCollectionLink.GENERATE);
		generators.add(DistinctCollectionLink.GENERATE_SORTED);
		generators.add(SubSetLink.GENERATE);
		generators.add(FlattenedCollectionValuesLink.GENERATE);
		generators.add(FactoringFlatMapCollectionLink.GENERATE);

		// Derived collection value generators
		generators.addAll(CollectionDerivedValues.GENERATORS);

		// Derived value generators
		generators.add(MappedValueLink.GENERATE);
		generators.add(CombinedValueLink.GENERATE);

		LINK_GENERATORS = Collections.unmodifiableList(generators);
	}

	private ObservableChainLink<?, ?> theRoot;
	private List<LinkStruct> theLinks;

	@Override
	public void accept(TestHelper helper) {
		BetterCollections.setSimplifyDuplicateOperations(false);
		BetterCollections.setTesting(true);
		boolean debugging = helper.isReproducing();
		if (debugging)
			Debug.d().start();// .watchFor(new Debugging());
		boolean tempLockDebug = debugging && !LockDebug.isDebugging();
		if (tempLockDebug)
			LockDebug.setDebugging(true);
		try {
			assemble(helper);
			test(helper);
		} finally {
			if (debugging)
				Debug.d().end();
			if (tempLockDebug)
				LockDebug.setDebugging(false);
		}
	}

	/**
	 * Generates a set of random test cases to test ObServe functionality. If there are previous known failures, this method will execute
	 * them first. The first failure will be persisted and the test will end.
	 */
	@Test
	public void superTest() {
		Duration testDuration = Duration.ofMinutes(20);
		int maxFailures = 1;
		System.out.println(
			"Executing up to " + QommonsUtils.printTimeLength(testDuration.toMillis()) + " of tests with max " + maxFailures + " failures");
		TestHelper.TestSummary summary = TestHelper.createTester(getClass())//
			.withRandomCases(-1)//
			.withMaxCaseDuration(Duration.ofMinutes(3)) // Since we're using progress interval checking, this can be pretty long
			.withMaxTotalDuration(testDuration)//
			.withMaxProgressInterval(Duration.ofSeconds(10))//
			.withMaxFailures(maxFailures)//
			.withConcurrency(max -> max - 1)//
			.withPersistenceDir(new File("src/main/test/org/observe/supertest"), false)//
			.withPlacemarks("Transaction", "Modification").withDebug(true)//
			.execute();
		System.out.println("Summary: " + summary);
		summary.throwErrorIfFailed();
	}

	private <E> void assemble(TestHelper helper) {
		//Tend toward smaller chain lengths, but allow longer ones occasionally
		int linkCount = helper.getInt(2, helper.getInt(2, MAX_LINK_COUNT));

		// Create the root link
		TestHelper.RandomSupplier<ChainLinkGenerator> firstLink = helper.createSupplier();
		for (ChainLinkGenerator gen : LINK_GENERATORS) {
			double weight = gen.getAffinity(null);
			if (weight > 0)
				firstLink.or(weight, () -> gen);
		}
		theRoot = firstLink.get(null).deriveLink("root", null, helper);

		class LinkDerivation<T> {
			final ObservableChainLink<?, T> link;
			final Supplier<ObservableChainLink<T, ?>> linkDeriver;
			final double derivationWeight;

			LinkDerivation(ObservableChainLink<?, T> link) {
				this.link = link;
				TestHelper.RandomSupplier<ObservableChainLink<T, ?>> deriver = helper.createSupplier();
				double totalWeight = 0;
				for (ChainLinkGenerator gen : LINK_GENERATORS) {
					double weight = gen.getAffinity(link);
					if (weight > 0) {
						totalWeight += weight;
						ChainLinkGenerator fGen = gen;
						deriver.or(weight, () -> fGen.deriveLink(link.getPath() + "[" + link.getDerivedLinks().size() + "]", link, helper));
					}
				}
				this.linkDeriver = () -> deriver.get(null);
				this.derivationWeight = totalWeight;
			}

			<X> LinkDerivation<X> deriveNext() {
				ObservableChainLink<T, X> next = (ObservableChainLink<T, X>) linkDeriver.get();
				((List<ObservableChainLink<T, X>>) link.getDerivedLinks()).add(next);
				return new LinkDerivation<>(next);
			}

			void init() {
				link.initialize(helper);
			}
		}
		List<LinkDerivation<?>> links = new ArrayList<>(linkCount);
		LinkDerivation<?> initLink = new LinkDerivation<>(theRoot);
		links.add(initLink);
		TestHelper.RandomSupplier<LinkDerivation<?>> nextLink = helper.createSupplier();
		nextLink.or(initLink.derivationWeight, () -> initLink);

		for (int i = 1; i < linkCount; i++) {
			LinkDerivation<?> next = nextLink.get(null);
			LinkDerivation<?> derived = next.deriveNext();
			if (derived.derivationWeight > 0)
				nextLink.or(derived.derivationWeight, () -> derived);
		}

		for (LinkDerivation<?> link : links)
			link.init();

		theLinks = new ArrayList<>(linkCount);
		fillLinks(theLinks, theRoot, 0, "root");
	}

	private static void fillLinks(List<LinkStruct> links, ObservableChainLink<?, ?> link, int depth, String path) {
		links.add(new LinkStruct(link, depth, path));
		int d = 0;
		for (ObservableChainLink<?, ?> derived : link.getDerivedLinks()) {
			fillLinks(links, derived, depth + 1, path + "[" + d + "]");
			d++;
		}
	}

	private void test(TestHelper helper) {
		if (helper.isReproducing()) {
			System.out.println("Initial Values:" + print(true, null));
		} else
			System.out.println("Assembled:" + toString());
		try{
			validate(theRoot, true, "root");
		} catch (RuntimeException | Error e) {
			System.err.println("Integrity check failure on initial values");
			throw e;
		}

		TestHelper.RandomSupplier<LinkStruct> randomModLink = helper.createSupplier();
		for (LinkStruct link : theLinks) {
			double modify = link.link.getModificationAffinity();
			if (modify > 0)
				randomModLink.or(modify, () -> link);
		}

		int tries = 50;
		long modifications = 0;
		for (int tri = 0; tri < tries; tri++) {
			LinkStruct targetLink = randomModLink.get(null);
			boolean finished = false;
			boolean useTransaction = helper.getBoolean(.75);
			try (Transaction t = useTransaction ? targetLink.link.lock(true, null) : Transaction.NONE) {
				int transactionMods = (int) helper.getDouble(1, 10, 26);
				if (transactionMods == 25)
					transactionMods = 0; // Want the probability of no-op transactions to be small but present
				if (helper.isReproducing())
					System.out
					.println("Modification set " + (tri + 1) + ": " + transactionMods + " modifications on link " + targetLink.path);
				helper.placemark("Transaction");
				for (int transactionTri = 0; transactionTri < transactionMods; transactionTri++) {
					theRoot.setModification(tri, transactionTri, (int) modifications);
					String preValue = printValues(targetLink.path);
					if (helper.isReproducing())
						System.out.print("\tMod " + (transactionTri + 1) + ": ");
					TestHelper.RandomAction action = helper.createAction();
					try {
						targetLink.link.tryModify(action, helper);
						action.execute("Modification");
						modifications++;
					} catch (RuntimeException | Error e) {
						System.err.println("Modifying link " + targetLink.path);
						System.err.println("Error on transaction " + (tri + 1) + ", mod " + (transactionTri + 1) + " after "
							+ (modifications + transactionTri) + " successful modifications");
						System.err.println("Pre-failure values:" + preValue);
						System.err.println("Post-failure values:" + printValues(targetLink.path));
						throw e;
					}
					try {
						validate(theRoot, !useTransaction, "root");
					} catch (RuntimeException | Error e) {
						System.err.println("Modifying link " + targetLink.path);
						System.err.println("Integrity check failure after "
							+ (modifications + transactionTri + 1) + " modifications in " + (tri + 1) + " transactions");
						System.err.println("Pre-failure values:" + preValue);
						System.err.println("Post-failure values:" + printValues(targetLink.path));
						throw e;
					}
				}
				if (!helper.isReproducing()) {
					if (tri % 10 == 9)
						System.out.print('|');
					else
						System.out.print('.');
					System.out.flush();
				}
				finished = true;
			} catch (RuntimeException | Error e) {
				if (finished) {
					System.out.println("Values:" + print(true, targetLink.path));
					System.err.println("Modifying link " + targetLink.path);
					System.err.println("Error closing transaction " + tri + " after " + modifications + " successful modifications");
				}
				throw e;
			}
			try {
				validate(theRoot, true, "root");
			} catch (RuntimeException | Error e) {
				System.out.println("Values:" + print(true, targetLink.path));
				System.err.println("Modifying link " + targetLink.path);
				System.err.println("Integrity check failure after transaction " + (tri + 1) + " close after "
					+ modifications + " modifications");
				throw e;
			}
			if (helper.isReproducing())
				System.out.println("Values:" + print(true, null));
		}
	}

	private void validate(ObservableChainLink<?, ?> link, boolean transactionEnd, String path) {
		try {
			link.validate(transactionEnd);
		} catch (RuntimeException | Error e) {
			System.err.println("Integrity check failure on link " + path);
			throw e;
		}
		int i = 0;
		for (ObservableChainLink<?, ?> derived : link.getDerivedLinks()) {
			validate(derived, transactionEnd, //
				path + "[" + i + "]");
			i++;
		}
	}

	private static int TAB_LENGTH = 3;

	String print(boolean withValues, String highlightPath) {
		String[] names = new String[theLinks.size()];
		int[] nameLengths = new int[names.length];
		int maxNameLength = 0;
		for (int i = 0; i < names.length; i++) {
			LinkStruct link = theLinks.get(i);
			names[i] = link.link.toString();
			int nameLength = link.depth * TAB_LENGTH + link.path.length() + 2 + names[i].length();
			if (link.path.equals(highlightPath))
				nameLength += 2;
			else if (highlightPath != null && highlightPath.startsWith(link.path))
				nameLength++;
			nameLengths[i] = nameLength;
			if (nameLength > maxNameLength)
				maxNameLength = nameLength;
		}
		StringBuilder str = new StringBuilder();
		for (int i = 0; i < names.length; i++) {
			LinkStruct link = theLinks.get(i);
			str.append('\n');
			for (int d = 0; d < link.depth; d++)
				str.append('\t');
			if (link.path.equals(highlightPath))
				str.append("**");
			else if (highlightPath != null && highlightPath.startsWith(link.path))
				str.append('*');
			str.append(link.path).append(": ").append(names[i]);
			if (withValues) {
				int j = nameLengths[i];
				while (j < maxNameLength) {
					str.append(' ');
					j++;
				}
				str.append(": ").append(link.link.printValue());
			}
		}
		return str.toString();
	}

	String printValues(String highlightPath) {
		return print(true, highlightPath);
	}

	@Override
	public String toString() {
		return print(false, null);
	}

	static class LinkStruct {
		final ObservableChainLink<?, ?> link;
		final int depth;
		final String path;

		LinkStruct(ObservableChainLink<?, ?> link, int depth, String path) {
			this.link = link;
			this.depth = depth;
			this.path = path;
		}
	}
}

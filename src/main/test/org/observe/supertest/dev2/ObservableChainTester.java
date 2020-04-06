package org.observe.supertest.dev2;

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
import org.observe.supertest.dev2.links.BaseCollectionLink;
import org.observe.supertest.dev2.links.CollectionDerivedValues;
import org.observe.supertest.dev2.links.MappedCollectionLink;
import org.observe.supertest.dev2.links.ModFilteredCollectionLink;
import org.observe.supertest.dev2.links.ReversedCollectionLink;
import org.qommons.QommonsUtils;
import org.qommons.TestHelper;
import org.qommons.TestHelper.Testable;
import org.qommons.Transaction;
import org.qommons.debug.Debug;

public class ObservableChainTester implements Testable {
	private static final int MAX_VALUE = 1000;
	static final Map<TestValueType, Function<TestHelper, ?>> SUPPLIERS;
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
		generators.add(BaseCollectionLink.SIMPLE_GENERATOR);

		// Derived collection generators
		generators.add(MappedCollectionLink.GENERATE);
		generators.add(ReversedCollectionLink.GENERATE);
		generators.add(ModFilteredCollectionLink.GENERATE);

		// Derived collection value generators
		generators.addAll(CollectionDerivedValues.GENERATORS);

		LINK_GENERATORS = Collections.unmodifiableList(generators);
	}

	private ObservableChainLink<?, ?> theRoot;
	private List<LinkStruct> theLinks;

	@Override
	public void accept(TestHelper helper) {
		boolean debugging = helper.isReproducing();
		if (debugging)
			Debug.d().start();// .watchFor(new Debugging());
		assemble(helper);
		test(helper);
		if (debugging)
			Debug.d().end();
	}

	/**
	 * Generates a set of random test cases to test ObServe functionality. If there are previous known failures, this method will execute
	 * them first. The first failure will be persisted and the test will end.
	 */
	@Test
	public void superTest() {
		Duration testDuration = Duration.ofMinutes(5);
		int maxFailures = 1;
		System.out.println(
			"Executing up to " + QommonsUtils.printTimeLength(testDuration.toMillis()) + " of tests with max " + maxFailures + " failures");
		TestHelper.TestSummary summary = TestHelper.createTester(getClass())//
			/**/.withRandomCases(-1).withMaxTotalDuration(testDuration)//
			/**/.withMaxFailures(maxFailures)//
			/**/.withPersistenceDir(new File("src/main/test/org/observe/supertest/dev2"), false)//
			/**/.withPlacemarks("Transaction", "Modification")
			/**/.withDebug(true)//
			/**/.execute();
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
		theRoot = firstLink.get(null).deriveLink(null, helper);

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
						deriver.or(weight, () -> fGen.deriveLink(link, helper));
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
			System.out.println("Initial Values:" + print(true));
		} else
			System.out.println("Assembled:" + toString());
		try{
			validate(theRoot, true, "root");
		} catch(Error e){
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
					String preValue = printValues();
					if (helper.isReproducing())
						System.out.print("\tMod " + (transactionTri + 1) + ": ");
					TestHelper.RandomAction action = helper.createAction();
					try {
						targetLink.link.tryModify(action, helper);
						action.execute("Modification");
					} catch (RuntimeException | Error e) {
						System.err.println("Link " + targetLink.path);
						System.err.println("Error on transaction " + (tri + 1) + ", mod " + (transactionTri + 1) + " after "
							+ (modifications + transactionTri) + " successful modifications");
						System.err.println("Pre-failure values:\n" + preValue);
						System.err.println("Post-failure values:\n" + printValues());
						throw e;
					}
					try {
						validate(theRoot, !useTransaction, "root");
					} catch (Error e) {
						System.err.println("Link " + targetLink.path);
						System.err.println("Integrity check failure after "
							+ (modifications + transactionTri + 1) + " modifications in " + (tri + 1) + " transactions");
						System.err.println("Pre-failure values:\n" + preValue);
						System.err.println("Post-failure values:\n" + printValues());
						throw e;
					}
				}
				modifications += transactionMods;
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
					System.out.println("Values:" + print(true));
					System.err.println("Link " + targetLink.path);
					System.err.println("Error closing transaction " + tri + " after " + modifications + " successful modifications");
				}
				throw e;
			}
			try {
				validate(theRoot, true, "root");
			} catch (Error e) {
				System.out.println("Values:" + print(true));
				System.err.println("Link " + targetLink.path);
				System.err.println("Integrity check failure after transaction " + (tri + 1) + " close after "
					+ modifications + " modifications");
				throw e;
			}
			if (helper.isReproducing())
				System.out.println("Values:" + print(true));
		}
	}

	private void validate(ObservableChainLink<?, ?> link, boolean transactionEnd, String path) {
		try {
			link.validate(transactionEnd);
		} catch (Error e) {
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

	String print(boolean withValues) {
		String[] names = new String[theLinks.size()];
		int[] nameLengths = new int[names.length];
		int maxNameLength = 0;
		for (int i = 0; i < names.length; i++) {
			LinkStruct link = theLinks.get(i);
			names[i] = link.link.toString();
			int nameLength = link.depth * TAB_LENGTH + link.path.length() + 2 + names[i].length();
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

	String printValues() {
		StringBuilder s = new StringBuilder();
		print(theRoot, s, "root", 0, true);
		return s.toString();
	}

	@Override
	public String toString() {
		return print(false);
	}

	private void print(ObservableChainLink<?, ?> link, StringBuilder str, String path, int depth, boolean withValues) {
		str.append(path).append(": ").append(link);
		if (withValues)
			str.append('=').append(link.printValue());
		depth++;
		for (int i = 0; i < link.getDerivedLinks().size(); i++) {
			str.append('\n');
			for (int d = 0; d < depth; d++)
				str.append('\t');
			print(link.getDerivedLinks().get(i), str, path + "[" + i + "]", depth, withValues);
		}
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

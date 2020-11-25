package org.observe.util;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.regex.Matcher;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.observe.util.EntityArguments.Bound;
import org.observe.util.EntityArguments.CheckValue;
import org.observe.util.EntityArguments.Default;
import org.observe.util.EntityArguments.Pattern;
import org.observe.util.EntityArguments.Required;
import org.qommons.collect.BetterList;

/** Tests {@link EntityArguments} */
public class EntityArgumentsTest {
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("ddMMMyyyy HH:mm:ss");

	@SuppressWarnings("javadoc")
	public enum TestEnum {
		One, Two, Three, File
	}

	@SuppressWarnings("javadoc")
	public interface TestArgs {
		Boolean isBoolArg();

		@Default("20")
		int getIntArg();

		BetterList<Long> getLongArg();

		@Bound(min = "0.0", max = "100.0")
		BetterList<Double> getDoubleArg();

		@Required
		TestEnum getEnumArg();

		@Default("12/25/2020 12:30am")
		Instant getTimeArg();

		@Default("1m")
		Duration getDurationArg();

		@CheckValue("lengthLessEqual5")
		String getStringArg();

		@Pattern("(\\d+)\\-(\\d+)")
		Matcher getPatternArg();

		File getFileArg();

		static boolean lengthLessEqual5(String s) {
			return s.length() <= 5;
		}
	}

	private EntityArguments<TestArgs> theParser;

	/** Builds the parser */
	@Before
	public void setup() {
		theParser = new EntityArguments<>(TestArgs.class);
		theParser.getParser().printHelpOnEmpty(false).printHelpOnError(false);
	}

	/** Tests basic parsing of valid argument sets */
	@Test
	public void testArgumentParsing() {
		// Round 1
		TestArgs args = theParser.parse("--bool-arg=false", "--long-arg=5", "--long-arg=6", "--enum-arg=One", "--string-arg=str",
			"--pattern-arg=5-10");
		Assert.assertEquals(Arrays.asList(5L, 6L), args.getLongArg());
		Assert.assertEquals(TestEnum.One, args.getEnumArg());
		Assert.assertEquals("str", args.getStringArg());
		Assert.assertEquals("5", args.getPatternArg().group(1));
		Assert.assertEquals("10", args.getPatternArg().group(2));
		Assert.assertFalse(args.isBoolArg());
		Assert.assertEquals(0, args.getDoubleArg().size());
		Assert.assertNull(args.getFileArg());
		Assert.assertEquals(20, args.getIntArg());
		try {
			Assert.assertEquals(DATE_FORMAT.parse("25Dec2020 00:30:00").toInstant(), args.getTimeArg());
		} catch (ParseException e) {
			throw new IllegalStateException(e);
		}
		Assert.assertEquals(Duration.ofSeconds(60), args.getDurationArg());

		// Round 2
		args = theParser.parse("--double-arg=50.5", "--double-arg=60.5", "--double-arg=70.5", "--enum-arg=File", "--bool-arg=true",
			"--file-arg=/home/user/something");
		Assert.assertEquals(0, args.getLongArg().size());
		Assert.assertEquals(Arrays.asList(50.5, 60.5, 70.5), args.getDoubleArg());
		Assert.assertEquals(TestEnum.File, args.getEnumArg());
		Assert.assertTrue(args.isBoolArg());
		Assert.assertEquals(new File("/home/user/something"), args.getFileArg());

		// Round 3
		args = theParser.parse("--enum-arg=Three", "--duration-arg=30s");
		Assert.assertEquals(TestEnum.Three, args.getEnumArg());
		Assert.assertEquals(Duration.ofSeconds(30), args.getDurationArg());
	}

	/** Tests parsing of bad arguments or argument sets that violate configured requirements */
	@Test
	public void testErrorCases() {
		String message = null;
		try {
			message = "Missing enum-arg";
			theParser.parse("--bool-arg=false");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}

		try {
			message = "int-arg specified twice";
			theParser.parse("--enum-arg=One", "--int-arg=0", "--int-arg=1");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}

		try {
			message = "Bad time arg";
			theParser.parse("--enum-arg=One", "--time-arg=0");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}

		try {
			message = "Bad duration arg";
			theParser.parse("--enum-arg=One", "--duration-arg=x");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}

		try {
			message = "Bad pattern arg";
			theParser.parse("--enum-arg=One", "--pattern-arg=0");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}
	}

	/** Tests parsing of arguments that violate value constraints */
	@Test
	public void testConstraints() {
		String message = null;
		try {
			message = "string-arg too long";
			theParser.parse("--enum-arg=One", "--string-arg=something");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}
		try {
			message = "double-arg too small";
			theParser.parse("--enum-arg=One", "--double-arg=-1.0");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}
		try {
			message = "double-arg too large";
			theParser.parse("--enum-arg=One", "--double-arg=101");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}
	}
}

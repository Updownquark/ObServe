package org.observe.util;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.regex.Matcher;

import org.junit.Assert;
import org.junit.Test;
import org.observe.util.EntityArguments.Argument;
import org.observe.util.EntityArguments.Arguments;
import org.observe.util.EntityArguments.FileField;
import org.observe.util.EntityArguments.Flag;
import org.observe.util.EntityArguments.Pattern;
import org.qommons.ArgumentParsing2;
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
		@Flag
		boolean isFlag();

		Boolean isBoolArg();

		@Argument(defaultValue = "20")
		int getIntArg();

		BetterList<Long> getLongArg();

		@Argument(minValue = "0.0", maxValue = "100.0")
		BetterList<Double> getDoubleArg();

		@Argument(required = true)
		TestEnum getEnumArg();

		@Argument(defaultValue = "12/25/2020 12:30am")
		Instant getTimeArg();

		@Argument(defaultValue = "1m")
		Duration getDurationArg();

		@Argument(validate = "lengthLessEqual5")
		String getStringArg();

		@Pattern("(\\d+)\\-(\\d+)")
		Matcher getPatternArg();

		File getFileArg1();

		@FileField(relativeToField="fileArg1")
		File getFileArg2();

		BetterList<Integer> getMultiIntArg();

		@Argument(parseWith = "parseURL")
		URL getUrlArg();

		static boolean lengthLessEqual5(String s) {
			return s.length() <= 5;
		}

		static URL parseURL(String text) throws MalformedURLException {
			return new URL(text);
		}
	}

	@SuppressWarnings("javadoc")
	@Arguments(singleValuePattern = "singleValuePattern", multiValuePattern = "multiValuePattern")
	public interface SplitTestArgs {
		ArgumentParsing2.ArgumentPattern singleValuePattern = ArgumentParsing2.SPLIT_VALUE_PATTERN;
		ArgumentParsing2.ArgumentPattern notSplitValuePattern = ArgumentParsing2.DEFAULT_VALUE_PATTERN;
		ArgumentParsing2.ArgumentPattern multiValuePattern = ArgumentParsing2.SPLIT_MULTI_VALUE_PATTERN;

		@Flag
		boolean isFlag();

		@Argument(argPattern = "notSplitValuePattern")
		Boolean isBoolArg();

		@Argument(defaultValue = "20")
		int getIntArg();

		BetterList<Long> getLongArg();

		@Argument(minValue = "0.0", maxValue = "100.0")
		BetterList<Double> getDoubleArg();

		@Argument(required = true)
		TestEnum getEnumArg();

		@Argument(defaultValue = "12/25/2020 12:30am")
		Instant getTimeArg();

		@Argument(defaultValue = "1m")
		Duration getDurationArg();

		@Argument(validate = "lengthLessEqual5")
		String getStringArg();

		@Pattern("(\\d+)\\-(\\d+)")
		Matcher getPatternArg();

		File getFileArg1();

		@FileField(relativeToField = "fileArg1")
		File getFileArg2();

		BetterList<Integer> getMultiIntArg();

		static boolean lengthLessEqual5(String s) {
			return s.length() <= 5;
		}
	}


	/** Tests basic parsing of valid argument sets */
	@Test
	public void testArgumentParsing() {
		EntityArguments<TestArgs> parser = new EntityArguments<>(TestArgs.class).initParser();
		parser.getParser().printHelpOnEmpty(false).printHelpOnError(false);

		// Round 1
		TestArgs args = parser.parse("--flag", "--long-arg=5", "--long-arg=6", "--enum-arg=One", "--string-arg=str",
			"--pattern-arg=5-10");
		Assert.assertTrue(args.isFlag());
		Assert.assertEquals(Arrays.asList(5L, 6L), args.getLongArg());
		Assert.assertEquals(TestEnum.One, args.getEnumArg());
		Assert.assertEquals("str", args.getStringArg());
		Matcher match = args.getPatternArg();
		Assert.assertEquals("5", match.group(1));
		Assert.assertEquals("10", match.group(2));
		Assert.assertNull(args.isBoolArg());
		Assert.assertEquals(0, args.getDoubleArg().size());
		Assert.assertNull(args.getFileArg1());
		Assert.assertEquals(20, args.getIntArg());
		try {
			Assert.assertEquals(DATE_FORMAT.parse("25Dec2020 00:30:00").toInstant(), args.getTimeArg());
		} catch (ParseException e) {
			throw new IllegalStateException(e);
		}
		Assert.assertEquals(Duration.ofSeconds(60), args.getDurationArg());

		// Round 2
		args = parser.parse("--double-arg=50.5", "--double-arg=60.5", "--double-arg=70.5", "--enum-arg=File", "--bool-arg=true",
			"--file-arg1=/home/user/something", "--file-arg2=1/2/3", "--multi-int-arg=10,20,30");
		Assert.assertFalse(args.isFlag());
		Assert.assertEquals(0, args.getLongArg().size());
		Assert.assertEquals(Arrays.asList(50.5, 60.5, 70.5), args.getDoubleArg());
		Assert.assertEquals(TestEnum.File, args.getEnumArg());
		Assert.assertTrue(args.isBoolArg());
		Assert.assertEquals(new File("/home/user/something"), args.getFileArg1());
		Assert.assertEquals(new File("/home/user/something/1/2/3"), args.getFileArg2());
		Assert.assertEquals(Arrays.asList(10, 20, 30), args.getMultiIntArg());

		// Round 3
		URL url;
		try {
			url = new URL("https://www.someserver.com/somepage");
		} catch (MalformedURLException e) {
			throw new IllegalStateException(e);
		}
		args = parser.parse("--enum-arg=Three", "--duration-arg=30s", "--url-arg=" + url);
		Assert.assertEquals(TestEnum.Three, args.getEnumArg());
		Assert.assertEquals(Duration.ofSeconds(30), args.getDurationArg());
		Assert.assertEquals(url, args.getUrlArg());
	}

	/** Tests basic parsing of valid argument sets */
	@Test
	public void testSplitArgumentParsing() {
		EntityArguments<SplitTestArgs> parser = new EntityArguments<>(SplitTestArgs.class).initParser();
		parser.getParser().printHelpOnEmpty(false).printHelpOnError(false);

		// Round 1
		SplitTestArgs args = parser.parse("--flag", "--long-arg", "5", "--long-arg", "6", "--enum-arg", "One", "--string-arg", "str",
			"--pattern-arg", "5-10");
		Assert.assertTrue(args.isFlag());
		Assert.assertEquals(Arrays.asList(5L, 6L), args.getLongArg());
		Assert.assertEquals(TestEnum.One, args.getEnumArg());
		Assert.assertEquals("str", args.getStringArg());
		Matcher match = args.getPatternArg();
		Assert.assertEquals("5", match.group(1));
		Assert.assertEquals("10", match.group(2));
		Assert.assertNull(args.isBoolArg());
		Assert.assertEquals(0, args.getDoubleArg().size());
		Assert.assertNull(args.getFileArg1());
		Assert.assertEquals(20, args.getIntArg());
		try {
			Assert.assertEquals(DATE_FORMAT.parse("25Dec2020 00:30:00").toInstant(), args.getTimeArg());
		} catch (ParseException e) {
			throw new IllegalStateException(e);
		}
		Assert.assertEquals(Duration.ofSeconds(60), args.getDurationArg());

		// Round 2
		args = parser.parse("--double-arg", "50.5", "--double-arg", "60.5", "--double-arg", "70.5", "--enum-arg", "File", "--bool-arg=true",
			"--file-arg1", "/home/user/something", "--file-arg2", "1/2/3", "--multi-int-arg", "10,20,30");
		Assert.assertFalse(args.isFlag());
		Assert.assertEquals(0, args.getLongArg().size());
		Assert.assertEquals(Arrays.asList(50.5, 60.5, 70.5), args.getDoubleArg());
		Assert.assertEquals(TestEnum.File, args.getEnumArg());
		Assert.assertTrue(args.isBoolArg());
		Assert.assertEquals(new File("/home/user/something"), args.getFileArg1());
		Assert.assertEquals(new File("/home/user/something/1/2/3"), args.getFileArg2());
		Assert.assertEquals(Arrays.asList(10, 20, 30), args.getMultiIntArg());

		// Round 3
		args = parser.parse("--enum-arg", "Three", "--duration-arg", "30s");
		Assert.assertEquals(TestEnum.Three, args.getEnumArg());
		Assert.assertEquals(Duration.ofSeconds(30), args.getDurationArg());
	}

	/** Tests parsing of bad arguments or argument sets that violate configured requirements */
	@Test
	public void testErrorCases() {
		EntityArguments<TestArgs> parser = new EntityArguments<>(TestArgs.class).initParser();
		parser.getParser().printHelpOnEmpty(false).printHelpOnError(false);

		String message = null;
		try {
			message = "Missing enum-arg";
			parser.parse("--bool-arg=false");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}

		try {
			message = "int-arg specified twice";
			parser.parse("--enum-arg=One", "--int-arg=0", "--int-arg=1");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}

		try {
			message = "Bad time arg";
			parser.parse("--enum-arg=One", "--time-arg=0");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}

		try {
			message = "Bad duration arg";
			parser.parse("--enum-arg=One", "--duration-arg=x");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}

		try {
			message = "Bad pattern arg";
			parser.parse("--enum-arg=One", "--pattern-arg=0");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}
	}

	/** Tests parsing of arguments that violate value constraints */
	@Test
	public void testConstraints() {
		EntityArguments<TestArgs> parser = new EntityArguments<>(TestArgs.class).initParser();
		parser.getParser().printHelpOnEmpty(false).printHelpOnError(false);

		String message = null;
		try {
			message = "string-arg too long";
			parser.parse("--enum-arg=One", "--string-arg=something");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}
		try {
			message = "double-arg too small";
			parser.parse("--enum-arg=One", "--double-arg=-1.0");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}
		try {
			message = "double-arg too large";
			parser.parse("--enum-arg=One", "--double-arg=101");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}
	}
}

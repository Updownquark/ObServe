<?xml version="1.0" encoding="UTF-8"?>

<testing xmlns:test="Expresso-Test 0.1" xmlns:debug="Expresso-Debug v0.1">
	<!-- TODO
		Test AND DOCUMENT!!

		Simple Lists, Sorted Lists, Sets, Sorted Sets with configured elements
		Derived Lists, Sorted Lists, Sets, Sorted Sets (+ test set)
		Simple Maps, Sorted Maps, Multi-Maps, Sorted Multi-Maps with configured entries
		Derived Maps, Sorted Maps, Multi-Maps, Sorted Multi-Maps (+ test set)
		Event
			Any type can be cast to an Event

		Transformation (values, collections, (multi-)maps, actions
			disable
			map-to
				reversal
				combination
				with and without type spec
			filter(-by-type)
			reverse
			refresh(-each)
			distinct
			sort
			unmodifiable
			filter-mod
			map-equivalent
			flatten (to value and to collection)
				with and without type spec
			cross
			where-contained
			group-by

			no-init
			skip
			take
			take-until
	
			first-value
		
		Test expressions
			Array access
			External Literals (all types)
			Parenthetic

		Test external models
		In new file, test Expresso-Config
	-->
	<expresso>
		<imports>
			<import>org.observe.expresso.ExpressoTestEntity</import>
			<import>org.observe.expresso.ExpressoReflectTester</import>
			<import>org.junit.Assert.*</import>
			<import>org.observe.expresso.ExpressoTests.*</import>
		</imports>
		<models>
			<model name="models">
				<constant name="constant">5</constant>
				<value name="alsoConstant">5</value>
				<value name="emptyValue" type="Integer" />
	
				<value name="test" init="new ExpressoTestEntity()" />
				<value name="expected" init="new ExpressoTestEntity()" />
	
				<value name="testInt">test.getInt()</value>
				<value name="testDbl">test.getDouble()</value>
				<value name="testBool">test.getBoolean()</value>
				<value name="testStr">test.getString()</value>
				<value name="entityInst">test.getInstant()</value>
	
				<value name="anyInt" init="10" />
				<value name="anyDbl" init="1000.0" />
				<value name="anyBool" init="true" />
				<value name="anyStr" init="&quot;Something&quot;" />
				<value type="java.time.Instant" name="anyInst" init="`05May2022 10:15:43am`" />
				
				<constant name="constant2">anyInt</constant>
				<value name="derivedInt">anyInt*10</value>
				<transform name="derivedIntModifiable" source="test">
					<map-to source-as="entity">
						<map-with>entity.getInt()</map-with>
						<map-reverse type="modify-source" target-as="intValue">entity.setInt(intValue)</map-reverse>
					</map-to>
				</transform>
				<transform name="combinedInt" source="test">
					<map-to source-as="entity">
						<combine-with name="other">anyInt</combine-with>
						<map-with>entity.getInt()+other</map-with>
						<map-reverse type="modify-source" target-as="intValue">entity.setInt(intValue-other)</map-reverse>
					</map-to>
				</transform>

				<action name="assignInt">test.setInt(anyInt)</action>
				<action name="assignDbl">test.setDouble(anyDbl)</action>
				<action name="assignBool">test.setBoolean(anyBool)</action>
				<action name="assignStr">test.setString(anyStr)</action>
				<action name="assignInst">test.setInstant(anyInst)</action>

				<value name="error">test.assertEquals(expected)</value>
	
				<list name="list" type="int">
					<element>5</element>
					<element>4</element>
					<element>3</element>
					<element>2</element>
					<element>1</element>
				</list>
				<list name="entityList" type="ExpressoTestEntity">
					<element>new ExpressoTestEntity()</element>
					<element>new ExpressoTestEntity()</element>
					<element>new ExpressoTestEntity()</element>
					<element>new ExpressoTestEntity()</element>
				</list>
				<transform name="sortedEntityList" source="entityList">
					<refresh-each source-as="entity" on="entity.changes()" />
					<sort sort-value-as="entity">
						<sort-by>entity.getInt()</sort-by>
						<sort-by>entity.getDouble()</sort-by>
						<sort-by>entity.getBoolean()</sort-by>
						<sort-by>entity.getInstant()</sort-by>
						<sort-by>entity.getString()</sort-by>
					</sort>
				</transform>
			</model>
		</models>
	</expresso>
	<test name="constant">
		<model>
			<value name="initInt" init="models.anyInt" />
		</model>
		<action>assertEquals(5, models.constant)</action>
		<action expect-throw="UnsupportedOperationException">models.constant=6</action>
		<action>assertEquals(initInt, models.constant2)</action>
		<action>models.anyInt+=50</action>
		<action>assertEquals(initInt, models.constant2)</action>
	</test>
	<test name="simpleValue">
		<action>assertEquals(5, models.alsoConstant)</action>
		<action expect-throw="UnsupportedOperationException">models.alsoConstant=6</action>
		<action>assertEquals(null, models.emptyValue)</action>
		<action>models.emptyValue=5</action>
		<action>assertEquals(5, models.emptyValue)</action>
		<action>assertEquals(10, models.anyInt)</action>
		<action>models.anyInt=5</action>
		<action>assertEquals(5, models.anyInt)</action>
	</test>
	<test name="derivedValue">
		<action>models.test.setInt(30)</action>
		<action>assertEquals(30, models.testInt)</action>
		<action expect-throw="UnsupportedOperationException">models.testInt=20</action>

		<action>assertEquals(100, models.derivedInt)</action>
		<action>models.derivedInt=150</action>
		<action>assertEquals(150, models.derivedInt)</action>
		<action>assertEquals(15, models.anyInt)</action>
	</test>
	<test name="list">
		<model>
			<value name="size" type="int">models.list.observeSize()</value>
		</model>
		<action>assertEquals(5, size)</action>
		<action>models.list.add(17)</action>
		<action>assertEquals(6, size)</action>
		<!-- TODO Need more -->
	</test>
	<test name="unaryOperators">
		<model>
			<value name="a" type="boolean" />
			<value name="b" type="int" />
	
			<value name="notA">!a</value>
			<value name="negB">-b</value>
		</model>

		<action>assertTrue(notA)</action>
		<action>assertEquals(0, negB)</action>

		<action>a=true</action>
		<action>assertFalse(notA)</action>

		<action>b=25</action>
		<action>assertEquals(-25, negB)</action>

		<action>b++</action>
		<action>assertEquals(26, b)</action>

		<action>++b</action>
		<action>assertEquals(27, b)</action>

		<action>b--</action>
		<action>assertEquals(26, b)</action>

		<action>--b</action>
		<action>assertEquals(25, b)</action>
	</test>
	<test name="booleanOperators">
		<model>
			<value name="a" init="false" />
			<value name="b" init="false" />
	
			<value name="or">a || b</value>
			<value name="and">a &amp;&amp; b</value>
			<value name="xor">a ^ b</value>

			<watch name="watchOr">or</watch>
			<watch name="watchAnd">and</watch>
			<watch name="watchXor">xor</watch>
		</model>

		<!-- Boolean AND/OR tests -->
		<action>assertFalse(or)</action>
		<action>assertFalse(watchOr)</action>
		<action>assertFalse(and)</action>
		<action>assertFalse(watchAnd)</action>
		<action>assertFalse(xor)</action>
		<action>assertFalse(watchXor)</action>
		<action>a=true</action>
		<action>assertTrue(or)</action>
		<action>assertTrue(watchOr)</action>
		<action>assertFalse(and)</action>
		<action>assertFalse(watchAnd)</action>
		<action>assertTrue(xor)</action>
		<action>assertTrue(watchXor)</action>
		<action>b=true</action>
		<action>assertTrue(or)</action>
		<action>assertTrue(watchOr)</action>
		<action>assertTrue(and)</action>
		<action>assertTrue(watchAnd)</action>
		<action>assertFalse(xor)</action>
		<action>assertFalse(watchXor)</action>
		<action>a=false</action>
		<action>assertTrue(or)</action>
		<action>assertTrue(watchOr)</action>
		<action>assertFalse(and)</action>
		<action>assertFalse(watchAnd)</action>
		<action>assertTrue(xor)</action>
		<action>assertTrue(watchXor)</action>
		<action>b=false</action>
		<action>assertFalse(or)</action>
		<action>assertFalse(watchOr)</action>
		<action>assertFalse(and)</action>
		<action>assertFalse(watchAnd)</action>
		<action>assertFalse(xor)</action>
		<action>assertFalse(watchXor)</action>
	</test>
	<test name="comparisonOperators">
		<model>
			<!-- Create two of each comparable type -->
			<value name="intA" type="int"/>
			<value name="intB" type="int" />

			<value name="dblA" type="double" />
			<value name="dblB" type="double" />

			<value name="longA" type="long" />
			<value name="longB" type="long" />

			<value name="fltA" type="float" />
			<value name="fltB" type="float" />

			<value name="shortA" type="short" />
			<value name="shortB" type="short" />

			<value name="byteA" type="byte" />
			<value name="byteB" type="byte" />

			<value name="charA" type="char" />
			<value name="charB" type="char" />

			<value name="stringA" init="&quot;&quot;" />
			<value name="stringB" init="&quot;&quot;" />

			<value name="intAltB">intA&lt;intB</value>
			<value name="intAlteB">intA&lt;=intB</value>
			<value name="intAgtB">intA>intB</value>
			<value name="intAgteB">intA>=intB</value>
			<value name="intAeqB">intA==intB</value>
			<value name="intAneqB">intA!=intB</value>

			<value name="dblAltB">dblA&lt;dblB</value>
			<value name="dblAlteB">dblA&lt;=dblB</value>
			<value name="dblAgtB">dblA>dblB</value>
			<value name="dblAgteB">dblA>=dblB</value>
			<value name="dblAeqB">dblA==dblB</value>
			<value name="dblAneqB">dblA!=dblB</value>

			<value name="longAltB">longA&lt;longB</value>
			<value name="longAlteB">longA&lt;=longB</value>
			<value name="longAgtB">longA>longB</value>
			<value name="longAgteB">longA>=longB</value>
			<value name="longAeqB">longA==longB</value>
			<value name="longAneqB">longA!=longB</value>

			<value name="fltAltB">fltA&lt;fltB</value>
			<value name="fltAlteB">fltA&lt;=fltB</value>
			<value name="fltAgtB">fltA>fltB</value>
			<value name="fltAgteB">fltA>=fltB</value>
			<value name="fltAeqB">fltA==fltB</value>
			<value name="fltAneqB">fltA!=fltB</value>

			<value name="shortAltB">shortA&lt;shortB</value>
			<value name="shortAlteB">shortA&lt;=shortB</value>
			<value name="shortAgtB">shortA>shortB</value>
			<value name="shortAgteB">shortA>=shortB</value>
			<value name="shortAeqB">shortA==shortB</value>
			<value name="shortAneqB">shortA!=shortB</value>

			<value name="byteAltB">byteA&lt;byteB</value>
			<value name="byteAlteB">byteA&lt;=byteB</value>
			<value name="byteAgtB">byteA>byteB</value>
			<value name="byteAgteB">byteA>=byteB</value>
			<value name="byteAeqB">byteA==byteB</value>
			<value name="byteAneqB">byteA!=byteB</value>

			<value name="charAltB">charA&lt;charB</value>
			<value name="charAlteB">charA&lt;=charB</value>
			<value name="charAgtB">charA>charB</value>
			<value name="charAgteB">charA>=charB</value>
			<value name="charAeqB">charA==charB</value>
			<value name="charAneqB">charA!=charB</value>

			<value name="stringAltB">stringA&lt;stringB</value>
			<value name="stringAlteB">stringA&lt;=stringB</value>
			<value name="stringAgtB">stringA>stringB</value>
			<value name="stringAgteB">stringA>=stringB</value>
			<!-- Equality and inequality operators are different for objects -->
		</model>

		<action>testComparison(intA, intB, intAltB, intAlteB, intAgtB, intAgteB, intAeqB, intAneqB)</action>

		<action>testComparison(dblA, dblB, dblAltB, dblAlteB, dblAgtB, dblAgteB, dblAeqB, dblAneqB)</action>

		<action>testComparison(longA, longB, longAltB, longAlteB, longAgtB, longAgteB, longAeqB, longAneqB)</action>

		<action>testComparison(fltA, fltB, fltAltB, fltAlteB, fltAgtB, fltAgteB, fltAeqB, fltAneqB)</action>

		<action>testComparison(shortA, shortB, shortAltB, shortAlteB, shortAgtB, shortAgteB, shortAeqB, shortAneqB)</action>

		<action>testComparison(byteA, byteB, byteAltB, byteAlteB, byteAgtB, byteAgteB, byteAeqB, byteAneqB)</action>

		<action>testComparison(charA, charB, charAltB, charAlteB, charAgtB, charAgteB, charAeqB, charAneqB)</action>

		<action>testComparison(stringA, stringB, stringAltB, stringAlteB, stringAgtB, stringAgteB, null, null)</action>
	</test>
	<test name="mathOperators">
		<!--
			This test is obnoxiously long, but it pretty well needs to be.
			Because java (and Expresso) treats primitive types all differently, the binary operations for the different types are also
			completely different, independent bits of code that need to be tested separately.
			So please forgive all the copy/paste here, it's unavoidable.
		-->
		<model>
			<value name="intA" type="int"/>
			<value name="intB" type="int" />

			<value name="dblA" type="double" />
			<value name="dblB" type="double" />

			<value name="longA" type="long" />
			<value name="longB" type="long" />

			<value name="fltA" type="float" />
			<value name="fltB" type="float" />

			<value name="shortA" type="short" />
			<value name="shortB" type="short" />

			<value name="byteA" type="byte" />
			<value name="byteB" type="byte" />

			<value name="charA" type="char" />
			<value name="charB" type="char" />

			<value name="intAplusB">intA+intB</value>
			<value name="intAminusB">intA-intB</value>
			<value name="intAtimesB">intA*intB</value>
			<value name="intAdivB">intA/intB</value>
			<value name="intAmodB">intA%intB</value>

			<value name="dblAplusB">dblA+dblB</value>
			<value name="dblAminusB">dblA-dblB</value>
			<value name="dblAtimesB">dblA*dblB</value>
			<value name="dblAdivB">dblA/dblB</value>
			<value name="dblAmodB">dblA%dblB</value>
			<value name="dblTol">1e-14</value>

			<value name="longAplusB">longA+longB</value>
			<value name="longAminusB">longA-longB</value>
			<value name="longAtimesB">longA*longB</value>
			<value name="longAdivB">longA/longB</value>
			<value name="longAmodB">longA%longB</value>

			<value name="fltAplusB">fltA+fltB</value>
			<value name="fltAminusB">fltA-fltB</value>
			<value name="fltAtimesB">fltA*fltB</value>
			<value name="fltAdivB">fltA/fltB</value>
			<value name="fltAmodB">fltA%fltB</value>
			<value name="fltTol">1e-5f</value>

			<value name="shortAplusB">shortA+shortB</value>
			<value name="shortAminusB">shortA-shortB</value>
			<value name="shortAtimesB">shortA*shortB</value>
			<value name="shortAdivB">shortA/shortB</value>
			<value name="shortAmodB">shortA%shortB</value>

			<value name="byteAplusB">byteA+byteB</value>
			<value name="byteAminusB">byteA-byteB</value>
			<value name="byteAtimesB">byteA*byteB</value>
			<value name="byteAdivB">byteA/byteB</value>
			<value name="byteAmodB">byteA%byteB</value>

			<value name="charAplusB">charA+charB</value>
			<value name="charAminusB">charA-charB</value>
			<value name="charAtimesB">charA*charB</value>
			<value name="charAdivB">charA/charB</value>
			<value name="charAmodB">charA%charB</value>

			<!--
				We're not going to worry about performing the test with watches.
				The individual operators don't care whether the value is watched,
				and we test watching of binary operator expressions elsewhere (see the comparison operator test).
			-->
		</model>

		<action>assertEquals(0, intAplusB)</action>
		<action>assertEquals(0, intAminusB)</action>
		<action>assertEquals(0, intAtimesB)</action>
		<!-- The operators are set up to treat division and modulus by zero as the same as division by 1, or the unity operation -->
		<action>assertEquals(0, intAdivB)</action>
		<action>assertEquals(0, intAmodB)</action>

		<!-- Ensure changes to either value are effective in the result -->
		<action>intA=5</action>
		<action>assertEquals(5, intAplusB)</action>
		<action>assertEquals(5, intAminusB)</action>
		<action>assertEquals(0, intAtimesB)</action>
		<action>assertEquals(5, intAdivB)</action>
		<action>assertEquals(5, intAmodB)</action>

		<action>intB=3</action>
		<action>assertEquals(8, intAplusB)</action>
		<action>assertEquals(2, intAminusB)</action>
		<action>assertEquals(15, intAtimesB)</action>
		<action>assertEquals(1, intAdivB)</action>
		<action>assertEquals(2, intAmodB)</action>

		<action>intA=-7</action>
		<action>assertEquals(-4, intAplusB)</action>
		<action>assertEquals(-10, intAminusB)</action>
		<action>assertEquals(-21, intAtimesB)</action>
		<action>assertEquals(-2, intAdivB)</action>
		<action>assertEquals(-1, intAmodB)</action>

		<!-- Test assignments to all kinds of results -->
		<action>intAplusB=12</action>
		<action>assertEquals(9, intA)</action>
		<action>assertEquals(12, intAplusB)</action>
		<action>assertEquals(6, intAminusB)</action>
		<action>assertEquals(27, intAtimesB)</action>
		<action>assertEquals(3, intAdivB)</action>
		<action>assertEquals(0, intAmodB)</action>

		<action>intAminusB=13</action>
		<action>assertEquals(16, intA)</action>
		<action>assertEquals(19, intAplusB)</action>
		<action>assertEquals(13, intAminusB)</action>
		<action>assertEquals(48, intAtimesB)</action>
		<action>assertEquals(5, intAdivB)</action>
		<action>assertEquals(1, intAmodB)</action>

		<action>intAtimesB=21</action>
		<action>assertEquals(7, intA)</action>
		<action>assertEquals(10, intAplusB)</action>
		<action>assertEquals(4, intAminusB)</action>
		<action>assertEquals(21, intAtimesB)</action>
		<action>assertEquals(2, intAdivB)</action>
		<action>assertEquals(1, intAmodB)</action>

		<!-- The operator throws an IllegalArgumentException, but this is converted to an IllegalStateException by the assignment action -->
		<action expect-throw="IllegalStateException">intAtimesB=4</action>

		<action>intAdivB=10</action>
		<action>assertEquals(30, intA)</action>
		<action>assertEquals(33, intAplusB)</action>
		<action>assertEquals(27, intAminusB)</action>
		<action>assertEquals(90, intAtimesB)</action>
		<action>assertEquals(10, intAdivB)</action>
		<action>assertEquals(0, intAmodB)</action>

		<!-- This is a little squirrely, but it's supported so we'll test it -->
		<action>intAmodB=2</action>
		<action>assertEquals(32, intA)</action>
		<action>assertEquals(35, intAplusB)</action>
		<action>assertEquals(29, intAminusB)</action>
		<action>assertEquals(96, intAtimesB)</action>
		<action>assertEquals(10, intAdivB)</action>
		<action>assertEquals(2, intAmodB)</action>
		
		<action expect-throw="IllegalStateException">intAmodB=4</action>

		<!-- Now, same thing for other types, starting with double -->
		<action>assertEquals(0, dblAplusB, 0.0)</action>
		<action>assertEquals(0, dblAminusB, 0.0)</action>
		<action>assertEquals(0, dblAtimesB, 0.0)</action>
		<action>assertEquals(Double.NaN, dblAdivB, 0.0)</action>
		<action>assertEquals(Double.NaN, dblAmodB, 0.0)</action>

		<action>dblA=5</action>
		<action>assertEquals(5, dblAplusB, 0.0)</action>
		<action>assertEquals(5, dblAminusB, 0.0)</action>
		<action>assertEquals(0, dblAtimesB, 0.0)</action>
		<action>assertEquals(Double.POSITIVE_INFINITY, dblAdivB, 0.0)</action>
		<action>assertEquals(Double.NaN, dblAmodB, 0.0)</action>

		<action>dblB=3.125</action>
		<action>assertEquals(8.125, dblAplusB, dblTol)</action>
		<action>assertEquals(1.875, dblAminusB, dblTol)</action>
		<action>assertEquals(15.625, dblAtimesB, dblTol)</action>
		<action>assertEquals(1.6, dblAdivB, dblTol)</action>
		<action>assertEquals(1.875, dblAmodB, dblTol)</action>

		<action>dblA=-7</action>
		<action>assertEquals(-3.875, dblAplusB, dblTol)</action>
		<action>assertEquals(-10.125, dblAminusB, dblTol)</action>
		<action>assertEquals(-21.875, dblAtimesB, dblTol)</action>
		<action>assertEquals(-2.24, dblAdivB, dblTol)</action>
		<action>assertEquals(-0.75, dblAmodB, dblTol)</action>

		<action>dblAplusB=12</action>
		<action>assertEquals(8.875, dblA, dblTol)</action>
		<action>assertEquals(12, dblAplusB, dblTol)</action>
		<action>assertEquals(5.75, dblAminusB, dblTol)</action>
		<action>assertEquals(27.734375, dblAtimesB, dblTol)</action>
		<action>assertEquals(2.84, dblAdivB, dblTol)</action>
		<action>assertEquals(2.625, dblAmodB, dblTol)</action>

		<action>dblAminusB=13</action>
		<action>assertEquals(16.125, dblA, dblTol)</action>
		<action>assertEquals(19.25, dblAplusB, dblTol)</action>
		<action>assertEquals(13, dblAminusB, dblTol)</action>
		<action>assertEquals(50.390625, dblAtimesB, dblTol)</action>
		<action>assertEquals(5.16, dblAdivB, dblTol)</action>
		<action>assertEquals(0.5, dblAmodB, dblTol)</action>

		<action>dblAtimesB=21</action>
		<action>assertEquals(6.72, dblA, dblTol)</action>
		<action>assertEquals(9.845, dblAplusB, dblTol)</action>
		<action>assertEquals(3.595, dblAminusB, dblTol)</action>
		<action>assertEquals(21, dblAtimesB, dblTol)</action>
		<action>assertEquals(2.1504, dblAdivB, dblTol)</action>
		<action>assertEquals(0.47, dblAmodB, dblTol)</action>

		<!-- The double operation doesn't throw an exception -->
		<action>dblAtimesB=4</action>
		<action>assertEquals(1.28, dblA, dblTol)</action>
		<action>assertEquals(4.405, dblAplusB, dblTol)</action>
		<action>assertEquals(-1.845, dblAminusB, dblTol)</action>
		<action>assertEquals(4, dblAtimesB, dblTol)</action>
		<action>assertEquals(0.4096, dblAdivB, dblTol)</action>
		<action>assertEquals(1.28, dblAmodB, dblTol)</action>

		<action>dblAdivB=10</action>
		<action>assertEquals(31.25, dblA, dblTol)</action>
		<action>assertEquals(34.375, dblAplusB, dblTol)</action>
		<action>assertEquals(28.125, dblAminusB, dblTol)</action>
		<action>assertEquals(97.65625, dblAtimesB, dblTol)</action>
		<action>assertEquals(10, dblAdivB, dblTol)</action>
		<action>assertEquals(0, dblAmodB, dblTol)</action>

		<action>dblAmodB=2</action>
		<action>assertEquals(33.25, dblA, dblTol)</action>
		<action>assertEquals(36.375, dblAplusB, dblTol)</action>
		<action>assertEquals(30.125, dblAminusB, dblTol)</action>
		<action>assertEquals(103.90625, dblAtimesB, dblTol)</action>
		<action>assertEquals(10.64, dblAdivB, dblTol)</action>
		<action>assertEquals(2, dblAmodB, dblTol)</action>
		
		<action expect-throw="IllegalStateException">dblAmodB=4</action>

		<!-- long -->
		<action>assertEquals(0, longAplusB)</action>
		<action>assertEquals(0, longAminusB)</action>
		<action>assertEquals(0, longAtimesB)</action>
		<action>assertEquals(0, longAdivB)</action>
		<action>assertEquals(0, longAmodB)</action>

		<action>longA=5</action>
		<action>assertEquals(5, longAplusB)</action>
		<action>assertEquals(5, longAminusB)</action>
		<action>assertEquals(0, longAtimesB)</action>
		<action>assertEquals(5, longAdivB)</action>
		<action>assertEquals(5, longAmodB)</action>

		<action>longB=3</action>
		<action>assertEquals(8, longAplusB)</action>
		<action>assertEquals(2, longAminusB)</action>
		<action>assertEquals(15, longAtimesB)</action>
		<action>assertEquals(1, longAdivB)</action>
		<action>assertEquals(2, longAmodB)</action>

		<action>longA=-7</action>
		<action>assertEquals(-4, longAplusB)</action>
		<action>assertEquals(-10, longAminusB)</action>
		<action>assertEquals(-21, longAtimesB)</action>
		<action>assertEquals(-2, longAdivB)</action>
		<action>assertEquals(-1, longAmodB)</action>

		<action>longAplusB=12</action>
		<action>assertEquals(9, longA)</action>
		<action>assertEquals(12, longAplusB)</action>
		<action>assertEquals(6, longAminusB)</action>
		<action>assertEquals(27, longAtimesB)</action>
		<action>assertEquals(3, longAdivB)</action>
		<action>assertEquals(0, longAmodB)</action>

		<action>longAminusB=13</action>
		<action>assertEquals(16, longA)</action>
		<action>assertEquals(19, longAplusB)</action>
		<action>assertEquals(13, longAminusB)</action>
		<action>assertEquals(48, longAtimesB)</action>
		<action>assertEquals(5, longAdivB)</action>
		<action>assertEquals(1, longAmodB)</action>

		<action>longAtimesB=21</action>
		<action>assertEquals(7, longA)</action>
		<action>assertEquals(10, longAplusB)</action>
		<action>assertEquals(4, longAminusB)</action>
		<action>assertEquals(21, longAtimesB)</action>
		<action>assertEquals(2, longAdivB)</action>
		<action>assertEquals(1, longAmodB)</action>

		<action expect-throw="IllegalStateException">longAtimesB=4</action>

		<action>longAdivB=10</action>
		<action>assertEquals(30, longA)</action>
		<action>assertEquals(33, longAplusB)</action>
		<action>assertEquals(27, longAminusB)</action>
		<action>assertEquals(90, longAtimesB)</action>
		<action>assertEquals(10, longAdivB)</action>
		<action>assertEquals(0, longAmodB)</action>

		<action>longAmodB=2</action>
		<action>assertEquals(32, longA)</action>
		<action>assertEquals(35, longAplusB)</action>
		<action>assertEquals(29, longAminusB)</action>
		<action>assertEquals(96, longAtimesB)</action>
		<action>assertEquals(10, longAdivB)</action>
		<action>assertEquals(2, longAmodB)</action>
		
		<action expect-throw="IllegalStateException">longAmodB=4</action>

		<!-- float -->
		<action>assertEquals(0, fltAplusB, 0.0)</action>
		<action>assertEquals(0, fltAminusB, 0.0)</action>
		<action>assertEquals(0, fltAtimesB, 0.0)</action>
		<action>assertEquals(Double.NaN, fltAdivB, 0.0)</action>
		<action>assertEquals(Double.NaN, fltAmodB, 0.0)</action>

		<action>fltA=5</action>
		<action>assertEquals(5, fltAplusB, 0.0)</action>
		<action>assertEquals(5, fltAminusB, 0.0)</action>
		<action>assertEquals(0, fltAtimesB, 0.0)</action>
		<action>assertEquals(Double.POSITIVE_INFINITY, fltAdivB, 0.0)</action>
		<action>assertEquals(Double.NaN, fltAmodB, 0.0)</action>

		<action>fltB=3.125f</action>
		<action>assertEquals(8.125f, fltAplusB, fltTol)</action>
		<action>assertEquals(1.875f, fltAminusB, fltTol)</action>
		<action>assertEquals(15.625f, fltAtimesB, fltTol)</action>
		<action>assertEquals(1.6f, fltAdivB, fltTol)</action>
		<action>assertEquals(1.875f, fltAmodB, fltTol)</action>

		<action>fltA=-7</action>
		<action>assertEquals(-3.875f, fltAplusB, fltTol)</action>
		<action>assertEquals(-10.125f, fltAminusB, fltTol)</action>
		<action>assertEquals(-21.875f, fltAtimesB, fltTol)</action>
		<action>assertEquals(-2.24f, fltAdivB, fltTol)</action>
		<action>assertEquals(-0.75f, fltAmodB, fltTol)</action>

		<action>fltAplusB=12</action>
		<action>assertEquals(8.875f, fltA, fltTol)</action>
		<action>assertEquals(12f, fltAplusB, fltTol)</action>
		<action>assertEquals(5.75f, fltAminusB, fltTol)</action>
		<action>assertEquals(27.734375f, fltAtimesB, fltTol)</action>
		<action>assertEquals(2.84f, fltAdivB, fltTol)</action>
		<action>assertEquals(2.625f, fltAmodB, fltTol)</action>

		<action>fltAminusB=13</action>
		<action>assertEquals(16.125f, fltA, fltTol)</action>
		<action>assertEquals(19.25f, fltAplusB, fltTol)</action>
		<action>assertEquals(13f, fltAminusB, fltTol)</action>
		<action>assertEquals(50.390625f, fltAtimesB, fltTol)</action>
		<action>assertEquals(5.16f, fltAdivB, fltTol)</action>
		<action>assertEquals(0.5f, fltAmodB, fltTol)</action>

		<action>fltAtimesB=21</action>
		<action>assertEquals(6.72f, fltA, fltTol)</action>
		<action>assertEquals(9.845f, fltAplusB, fltTol)</action>
		<action>assertEquals(3.595f, fltAminusB, fltTol)</action>
		<action>assertEquals(21f, fltAtimesB, fltTol)</action>
		<action>assertEquals(2.1504f, fltAdivB, fltTol)</action>
		<action>assertEquals(0.47f, fltAmodB, fltTol)</action>

		<action>fltAtimesB=4</action>
		<action>assertEquals(1.28f, fltA, fltTol)</action>
		<action>assertEquals(4.405f, fltAplusB, fltTol)</action>
		<action>assertEquals(-1.845f, fltAminusB, fltTol)</action>
		<action>assertEquals(4f, fltAtimesB, fltTol)</action>
		<action>assertEquals(0.4096f, fltAdivB, fltTol)</action>
		<action>assertEquals(1.28f, fltAmodB, fltTol)</action>

		<action>fltAdivB=10</action>
		<action>assertEquals(31.25f, fltA, fltTol)</action>
		<action>assertEquals(34.375f, fltAplusB, fltTol)</action>
		<action>assertEquals(28.125f, fltAminusB, fltTol)</action>
		<action>assertEquals(97.65625f, fltAtimesB, fltTol)</action>
		<action>assertEquals(10f, fltAdivB, fltTol)</action>
		<action>assertEquals(0f, fltAmodB, fltTol)</action>

		<action>fltAmodB=2</action>
		<action>assertEquals(33.25f, fltA, fltTol)</action>
		<action>assertEquals(36.375f, fltAplusB, fltTol)</action>
		<action>assertEquals(30.125f, fltAminusB, fltTol)</action>
		<action>assertEquals(103.90625f, fltAtimesB, fltTol)</action>
		<action>assertEquals(10.64f, fltAdivB, fltTol)</action>
		<action>assertEquals(2f, fltAmodB, fltTol)</action>
		
		<action expect-throw="IllegalStateException">fltAmodB=4</action>

		<!-- short -->
		<action>assertEquals(0, shortAplusB)</action>
		<action>assertEquals(0, shortAminusB)</action>
		<action>assertEquals(0, shortAtimesB)</action>
		<action>assertEquals(0, shortAdivB)</action>
		<action>assertEquals(0, shortAmodB)</action>

		<action>shortA=(short) 5</action>
		<action>assertEquals(5, shortAplusB)</action>
		<action>assertEquals(5, shortAminusB)</action>
		<action>assertEquals(0, shortAtimesB)</action>
		<action>assertEquals(5, shortAdivB)</action>
		<action>assertEquals(5, shortAmodB)</action>

		<action>shortB=(short) 3</action>
		<action>assertEquals(8, shortAplusB)</action>
		<action>assertEquals(2, shortAminusB)</action>
		<action>assertEquals(15, shortAtimesB)</action>
		<action>assertEquals(1, shortAdivB)</action>
		<action>assertEquals(2, shortAmodB)</action>

		<action>shortA=(short) -7</action>
		<action>assertEquals(-4, shortAplusB)</action>
		<action>assertEquals(-10, shortAminusB)</action>
		<action>assertEquals(-21, shortAtimesB)</action>
		<action>assertEquals(-2, shortAdivB)</action>
		<action>assertEquals(-1, shortAmodB)</action>

		<action>shortAplusB=(short) 12</action>
		<action>assertEquals(9, shortA)</action>
		<action>assertEquals(12, shortAplusB)</action>
		<action>assertEquals(6, shortAminusB)</action>
		<action>assertEquals(27, shortAtimesB)</action>
		<action>assertEquals(3, shortAdivB)</action>
		<action>assertEquals(0, shortAmodB)</action>

		<action>shortAminusB=(short) 13</action>
		<action>assertEquals(16, shortA)</action>
		<action>assertEquals(19, shortAplusB)</action>
		<action>assertEquals(13, shortAminusB)</action>
		<action>assertEquals(48, shortAtimesB)</action>
		<action>assertEquals(5, shortAdivB)</action>
		<action>assertEquals(1, shortAmodB)</action>

		<action>shortAtimesB=(short) 21</action>
		<action>assertEquals(7, shortA)</action>
		<action>assertEquals(10, shortAplusB)</action>
		<action>assertEquals(4, shortAminusB)</action>
		<action>assertEquals(21, shortAtimesB)</action>
		<action>assertEquals(2, shortAdivB)</action>
		<action>assertEquals(1, shortAmodB)</action>

		<action expect-throw="IllegalStateException">shortAtimesB=(short) 4</action>

		<action>shortAdivB=(short) 10</action>
		<action>assertEquals(30, shortA)</action>
		<action>assertEquals(33, shortAplusB)</action>
		<action>assertEquals(27, shortAminusB)</action>
		<action>assertEquals(90, shortAtimesB)</action>
		<action>assertEquals(10, shortAdivB)</action>
		<action>assertEquals(0, shortAmodB)</action>

		<action>shortAmodB=(short) 2</action>
		<action>assertEquals(32, shortA)</action>
		<action>assertEquals(35, shortAplusB)</action>
		<action>assertEquals(29, shortAminusB)</action>
		<action>assertEquals(96, shortAtimesB)</action>
		<action>assertEquals(10, shortAdivB)</action>
		<action>assertEquals(2, shortAmodB)</action>
		
		<action expect-throw="IllegalStateException">shortAmodB=(short) 4</action>

		<!-- byte -->
		<action>assertEquals(0, byteAplusB)</action>
		<action>assertEquals(0, byteAminusB)</action>
		<action>assertEquals(0, byteAtimesB)</action>
		<action>assertEquals(0, byteAdivB)</action>
		<action>assertEquals(0, byteAmodB)</action>

		<action>byteA=(byte) 5</action>
		<action>assertEquals(5, byteAplusB)</action>
		<action>assertEquals(5, byteAminusB)</action>
		<action>assertEquals(0, byteAtimesB)</action>
		<action>assertEquals(5, byteAdivB)</action>
		<action>assertEquals(5, byteAmodB)</action>

		<action>byteB=(byte) 3</action>
		<action>assertEquals(8, byteAplusB)</action>
		<action>assertEquals(2, byteAminusB)</action>
		<action>assertEquals(15, byteAtimesB)</action>
		<action>assertEquals(1, byteAdivB)</action>
		<action>assertEquals(2, byteAmodB)</action>

		<action>byteA=(byte) -7</action>
		<action>assertEquals(-4, byteAplusB)</action>
		<action>assertEquals(-10, byteAminusB)</action>
		<action>assertEquals(-21, byteAtimesB)</action>
		<action>assertEquals(-2, byteAdivB)</action>
		<action>assertEquals(-1, byteAmodB)</action>

		<action>byteAplusB=(byte) 12</action>
		<action>assertEquals(9, byteA)</action>
		<action>assertEquals(12, byteAplusB)</action>
		<action>assertEquals(6, byteAminusB)</action>
		<action>assertEquals(27, byteAtimesB)</action>
		<action>assertEquals(3, byteAdivB)</action>
		<action>assertEquals(0, byteAmodB)</action>

		<action>byteAminusB=(byte) 13</action>
		<action>assertEquals(16, byteA)</action>
		<action>assertEquals(19, byteAplusB)</action>
		<action>assertEquals(13, byteAminusB)</action>
		<action>assertEquals(48, byteAtimesB)</action>
		<action>assertEquals(5, byteAdivB)</action>
		<action>assertEquals(1, byteAmodB)</action>

		<action>byteAtimesB=(byte) 21</action>
		<action>assertEquals(7, byteA)</action>
		<action>assertEquals(10, byteAplusB)</action>
		<action>assertEquals(4, byteAminusB)</action>
		<action>assertEquals(21, byteAtimesB)</action>
		<action>assertEquals(2, byteAdivB)</action>
		<action>assertEquals(1, byteAmodB)</action>

		<action expect-throw="IllegalStateException">byteAtimesB=(byte) 4</action>

		<action>byteAdivB=(byte) 10</action>
		<action>assertEquals(30, byteA)</action>
		<action>assertEquals(33, byteAplusB)</action>
		<action>assertEquals(27, byteAminusB)</action>
		<action>assertEquals(90, byteAtimesB)</action>
		<action>assertEquals(10, byteAdivB)</action>
		<action>assertEquals(0, byteAmodB)</action>

		<action>byteAmodB=(byte) 2</action>
		<action>assertEquals(32, byteA)</action>
		<action>assertEquals(35, byteAplusB)</action>
		<action>assertEquals(29, byteAminusB)</action>
		<action>assertEquals(96, byteAtimesB)</action>
		<action>assertEquals(10, byteAdivB)</action>
		<action>assertEquals(2, byteAmodB)</action>
		
		<action expect-throw="IllegalStateException">byteAmodB=(byte) 4</action>

		<!-- char -->
		<action>assertEquals(0, charAplusB)</action>
		<action>assertEquals(0, charAminusB)</action>
		<action>assertEquals(0, charAtimesB)</action>
		<action>assertEquals(0, charAdivB)</action>
		<action>assertEquals(0, charAmodB)</action>

		<action>charA=(char) 5</action>
		<action>assertEquals(5, charAplusB)</action>
		<action>assertEquals(5, charAminusB)</action>
		<action>assertEquals(0, charAtimesB)</action>
		<action>assertEquals(5, charAdivB)</action>
		<action>assertEquals(5, charAmodB)</action>

		<action>charB=(char) 3</action>
		<action>assertEquals(8, charAplusB)</action>
		<action>assertEquals(2, charAminusB)</action>
		<action>assertEquals(15, charAtimesB)</action>
		<action>assertEquals(1, charAdivB)</action>
		<action>assertEquals(2, charAmodB)</action>

		<!-- char is java's only unsigned type, so the negative test doesn't make much sense -->
		
		<action>charAplusB=(char) 12</action>
		<action>assertEquals(9, charA)</action>
		<action>assertEquals(12, charAplusB)</action>
		<action>assertEquals(6, charAminusB)</action>
		<action>assertEquals(27, charAtimesB)</action>
		<action>assertEquals(3, charAdivB)</action>
		<action>assertEquals(0, charAmodB)</action>

		<action>charAminusB=(char) 13</action>
		<action>assertEquals(16, charA)</action>
		<action>assertEquals(19, charAplusB)</action>
		<action>assertEquals(13, charAminusB)</action>
		<action>assertEquals(48, charAtimesB)</action>
		<action>assertEquals(5, charAdivB)</action>
		<action>assertEquals(1, charAmodB)</action>

		<action>charAtimesB=(char) 21</action>
		<action>assertEquals(7, charA)</action>
		<action>assertEquals(10, charAplusB)</action>
		<action>assertEquals(4, charAminusB)</action>
		<action>assertEquals(21, charAtimesB)</action>
		<action>assertEquals(2, charAdivB)</action>
		<action>assertEquals(1, charAmodB)</action>

		<action expect-throw="IllegalStateException">charAtimesB=(char) 4</action>

		<action>charAdivB=(char) 10</action>
		<action>assertEquals(30, charA)</action>
		<action>assertEquals(33, charAplusB)</action>
		<action>assertEquals(27, charAminusB)</action>
		<action>assertEquals(90, charAtimesB)</action>
		<action>assertEquals(10, charAdivB)</action>
		<action>assertEquals(0, charAmodB)</action>

		<action>charAmodB=(char) 2</action>
		<action>assertEquals(32, charA)</action>
		<action>assertEquals(35, charAplusB)</action>
		<action>assertEquals(29, charAminusB)</action>
		<action>assertEquals(96, charAtimesB)</action>
		<action>assertEquals(10, charAdivB)</action>
		<action>assertEquals(2, charAmodB)</action>
		
		<action expect-throw="IllegalStateException">charAmodB=(char) 4</action>

		<!-- Test all math operations with different type operands -->
		<action>intA=5</action>
		<action>intB=5</action>
		<action>longA=5</action>
		<action>longB=5</action>
		<action>fltA=5</action>
		<action>fltB=5</action>
		<action>dblA=5</action>
		<action>dblB=5</action>
		<action>shortA=(short)5</action>
		<action>shortB=(short)5</action>
		<action>byteA=(byte)5</action>
		<action>byteB=(byte)5</action>
		<action>charA=(char)5</action>
		<action>charB=(char)5</action>

		<action>assertEquals(10, byteA+charB)</action>
		<action>assertEquals(10, byteA+shortB)</action>
		<action>assertEquals(10, byteA+intB)</action>
		<action>assertEquals(10, byteA+longB)</action>
		<action>assertEquals(10, byteA+fltB, fltTol)</action>
		<action>assertEquals(10, byteA+dblB, dblTol)</action>

		<action>assertEquals(10, shortA+byteB)</action>
		<action>assertEquals(10, shortA+charB)</action>
		<action>assertEquals(10, shortA+intB)</action>
		<action>assertEquals(10, shortA+longB)</action>
		<action>assertEquals(10, shortA+fltB, fltTol)</action>
		<action>assertEquals(10, shortA+dblB, dblTol)</action>

		<action>assertEquals(10, charA+byteB)</action>
		<action>assertEquals(10, charA+shortB)</action>
		<action>assertEquals(10, charA+intB)</action>
		<action>assertEquals(10, charA+longB)</action>
		<action>assertEquals(10, charA+fltB, fltTol)</action>
		<action>assertEquals(10, charA+dblB, dblTol)</action>

		<action>assertEquals(10, intA+byteB)</action>
		<action>assertEquals(10, intA+shortB)</action>
		<action>assertEquals(10, intA+charB)</action>
		<action>assertEquals(10, intA+longB)</action>
		<action>assertEquals(10, intA+fltB, fltTol)</action>
		<action>assertEquals(10, intA+dblB, dblTol)</action>

		<action>assertEquals(10, longA+byteB)</action>
		<action>assertEquals(10, longA+shortB)</action>
		<action>assertEquals(10, longA+charB)</action>
		<action>assertEquals(10, longA+intB)</action>
		<action>assertEquals(10, longA+fltB, fltTol)</action>
		<action>assertEquals(10, longA+dblB, dblTol)</action>

		<action>assertEquals(10, fltA+byteB, fltTol)</action>
		<action>assertEquals(10, fltA+shortB, fltTol)</action>
		<action>assertEquals(10, fltA+charB, fltTol)</action>
		<action>assertEquals(10, fltA+intB, fltTol)</action>
		<action>assertEquals(10, fltA+longB, fltTol)</action>
		<action>assertEquals(10, fltA+dblB, dblTol)</action>

		<action>assertEquals(10, dblA+byteB, dblTol)</action>
		<action>assertEquals(10, dblA+shortB, dblTol)</action>
		<action>assertEquals(10, dblA+charB, dblTol)</action>
		<action>assertEquals(10, dblA+intB, dblTol)</action>
		<action>assertEquals(10, dblA+longB, dblTol)</action>
		<action>assertEquals(10, dblA+fltB, dblTol)</action>
	</test>
	<test name="casts">
		<model>
			<value name="byteV" type="byte" />
			<value name="shortV" type="short" />
			<value name="charV" type="char" />
			<value name="intV" type="int" />
			<value name="longV" type="long" />
			<value name="floatV" type="float" />
			<value name="doubleV" type="double" />

			<value name="byteToDouble" type="double">byteV</value>
			<value name="byteToFloat" type="float">byteV</value>
			<value name="byteToLong" type="long">byteV</value>
			<value name="byteToInt" type="int">byteV</value>
			<value name="byteToShort" type="short">byteV</value>
			<value name="byteToChar" type="char">byteV</value>

			<value name="shortToDouble" type="double">shortV</value>
			<value name="shortToFloat" type="float">shortV</value>
			<value name="shortToLong" type="long">shortV</value>
			<value name="shortToInt" type="int">shortV</value>
			<value name="shortToChar" type="char">(char) shortV</value>
			<value name="shortToByte" type="byte">(byte) shortV</value>

			<value name="charToDouble" type="double">charV</value>
			<value name="charToFloat" type="float">charV</value>
			<value name="charToLong" type="long">charV</value>
			<value name="charToInt" type="int">charV</value>
			<value name="charToShort" type="short">(short) charV</value>
			<value name="charToByte" type="byte">(byte)charV</value>

			<value name="intToDouble" type="double">intV</value>
			<value name="intToFloat" type="float">intV</value>
			<value name="intToLong" type="long">intV</value>
			<value name="intToChar" type="char">(char) intV</value>
			<value name="intToShort" type="short">(short)intV</value>
			<value name="intToByte" type="byte">(byte) intV</value>

			<value name="longToDouble" type="double">longV</value>
			<value name="longToFloat" type="float">longV</value>
			<value name="longToInt" type="int">(int) longV</value>
			<value name="longToChar" type="char">(char) longV</value>
			<value name="longToShort" type="short">(short)longV</value>
			<value name="longToByte" type="byte">(byte) longV</value>

			<value name="floatToDouble" type="double">floatV</value>
			<value name="floatToLong" type="long">(long) floatV</value>
			<value name="floatToInt" type="int">(int) floatV</value>
			<value name="floatToChar" type="char">(char) floatV</value>
			<value name="floatToShort" type="short">(short)floatV</value>
			<value name="floatToByte" type="byte">(byte) floatV</value>

			<value name="doubleToFloat" type="float">(float) doubleV</value>
			<value name="doubleToLong" type="long">(long) doubleV</value>
			<value name="doubleToInt" type="int">(int) doubleV</value>
			<value name="doubleToChar" type="char">(char) doubleV</value>
			<value name="doubleToShort" type="short">(short)doubleV</value>
			<value name="doubleToByte" type="byte">(byte) doubleV</value>
		</model>

		<!--All values need to be between 0 and 127 so none of the types wrap and testing is easy -->
		<action>byteV=(byte) 108</action>
		<action>charV='\n'</action> <!-- ASCII 10 -->
		<action>shortV=(short) 96</action>
		<action>intV=76</action>
		<action>longV=12L</action>
		<action>floatV=1E2f</action>
		<action>doubleV=1.1E2</action>

		<action>assertEquals(108, byteToDouble, 0.0)</action>
		<action>assertEquals(108, byteToFloat, 0.0f)</action>
		<action>assertEquals(108, byteToLong)</action>
		<action>assertEquals(108, byteToInt)</action>
		<action>assertEquals((short) 108, byteToShort)</action>
		<action>assertEquals((char) 108, byteToChar)</action>

		<action>assertEquals(10, charToDouble, 0.0)</action>
		<action>assertEquals(10, charToFloat, 0.0f)</action>
		<action>assertEquals(10, charToLong)</action>
		<action>assertEquals(10, charToInt)</action>
		<action>assertEquals((short) 10, charToShort)</action>
		<action>assertEquals((byte) 10, charToByte)</action>

		<action>assertEquals(96, shortToDouble, 0.0)</action>
		<action>assertEquals(96, shortToFloat, 0.0f)</action>
		<action>assertEquals(96, shortToLong)</action>
		<action>assertEquals(96, shortToInt)</action>
		<action>assertEquals((char) 96, shortToChar)</action>
		<action>assertEquals((byte) 96, shortToByte)</action>

		<action>assertEquals(76, intToDouble, 0.0)</action>
		<action>assertEquals(76, intToFloat, 0.0f)</action>
		<action>assertEquals(76, intToLong)</action>
		<action>assertEquals((char) 76, intToChar)</action>
		<action>assertEquals((short) 76, intToShort)</action>
		<action>assertEquals((byte) 76, intToByte)</action>

		<action>assertEquals(12, longToDouble, 0.0)</action>
		<action>assertEquals(12, longToFloat, 0.0f)</action>
		<action>assertEquals(12, longToInt)</action>
		<action>assertEquals((char) 12, longToChar)</action>
		<action>assertEquals((short) 12, longToShort)</action>
		<action>assertEquals((byte) 12, longToByte)</action>

		<action>assertEquals(100, floatToDouble, 0.0)</action>
		<action>assertEquals(100, floatToLong)</action>
		<action>assertEquals(100, floatToInt, 0.0f)</action>
		<action>assertEquals((char) 100, floatToChar)</action>
		<action>assertEquals((short) 100, floatToShort)</action>
		<action>assertEquals((byte) 100, floatToByte)</action>

		<action>assertEquals(110, doubleToFloat, 0.0f)</action>
		<action>assertEquals(110, doubleToLong)</action>
		<action>assertEquals(110, doubleToInt, 0.0f)</action>
		<action>assertEquals((char) 110, doubleToChar)</action>
		<action>assertEquals((short) 110, doubleToShort)</action>
		<action>assertEquals((byte) 110, doubleToByte)</action>

		<!-- Now test assignments -->
		<action>byteToDouble=10</action>
		<action>assertEquals((byte) 10, byteV)</action>
		<action>shortToDouble=11</action>
		<action>assertEquals((short) 11, shortV)</action>
		<action>charToDouble=12</action>
		<action>assertEquals((char) 12, charV)</action>
		<action>intToDouble=13</action>
		<action>assertEquals(13, intV)</action>
		<action>longToDouble=14</action>
		<action>assertEquals(14, longV)</action>
		<action>floatToDouble=15</action>
		<action>assertEquals(15, floatV, 1E-5f)</action>

		<action>byteToFloat=100</action>
		<action>assertEquals((byte) 100, byteV)</action>
		<action>shortToFloat=101</action>
		<action>assertEquals((short) 101, shortV)</action>
		<action>charToFloat=102</action>
		<action>assertEquals((char) 102, charV)</action>
		<action>intToFloat=103</action>
		<action>assertEquals(103, intV)</action>
		<action>longToFloat=104</action>
		<action>assertEquals(104, longV)</action>
		<action>doubleToFloat=105</action>
		<action>assertEquals(105, doubleV, 1E-14)</action>

		<action>byteToLong=10</action>
		<action>assertEquals((byte) 10, byteV)</action>
		<action>shortToLong=11</action>
		<action>assertEquals((short) 11, shortV)</action>
		<action>charToLong=12</action>
		<action>assertEquals((char) 12, charV)</action>
		<action>intToLong=13</action>
		<action>assertEquals(13, intV)</action>
		<action>floatToLong=14</action>
		<action>assertEquals(14, floatV, 1e-5f)</action>
		<action>doubleToFloat=15</action>
		<action>assertEquals(15, doubleV, 1e-14)</action>

		<action>byteToInt=100</action>
		<action>assertEquals((byte) 100, byteV)</action>
		<action>shortToInt=101</action>
		<action>assertEquals((short) 101, shortV)</action>
		<action>charToInt=102</action>
		<action>assertEquals((char) 102, charV)</action>
		<action>longToInt=103</action>
		<action>assertEquals(103, longV)</action>
		<action>floatToInt=104</action>
		<action>assertEquals(104, floatV, 1e-5f)</action>
		<action>doubleToInt=105</action>
		<action>assertEquals(105, doubleV, 1e-14)</action>

		<action>byteToShort=(short)10</action>
		<action>assertEquals((byte) 10, byteV)</action>
		<action>charToShort=(short)11</action>
		<action>assertEquals((char) 11, charV)</action>
		<action>intToShort=(short)12</action>
		<action>assertEquals(12, intV)</action>
		<action>longToShort=(short)13</action>
		<action>assertEquals(13, longV)</action>
		<action>floatToInt=(short)14</action>
		<action>assertEquals(14, floatV, 1e-5f)</action>
		<action>doubleToInt=(short)15</action>
		<action>assertEquals(15, doubleV, 1e-14)</action>

		<action>byteToChar=(char)100</action>
		<action>assertEquals((byte) 100, byteV)</action>
		<action>shortToChar=(char) 101</action>
		<action>assertEquals((short) 101, shortV)</action>
		<action>intToChar=(char) 102</action>
		<action>assertEquals(102, intV)</action>
		<action>longToChar=(char) 103</action>
		<action>assertEquals(103, longV)</action>
		<action>floatToChar=(char) 104</action>
		<action>assertEquals(104, floatV, 1e-5f)</action>
		<action>doubleToChar=(char) 105</action>
		<action>assertEquals(105, doubleV, 1e-14)</action>

		<action>charToByte=(byte)10</action>
		<action>assertEquals((char) 10, charV)</action>
		<action>shortToByte=(byte) 11</action>
		<action>assertEquals((short) 11, shortV)</action>
		<action>intToByte=(byte) 12</action>
		<action>assertEquals(12, intV)</action>
		<action>longToByte=(byte) 13</action>
		<action>assertEquals(13, longV)</action>
		<action>floatToByte=(byte) 14</action>
		<action>assertEquals(14, floatV, 1e-5f)</action>
		<action>doubleToByte=(byte) 15</action>
		<action>assertEquals(15, doubleV, 1e-14)</action>
	</test>
	<test name="stringConcat">
		<model>
			<value name="str1" type="String" />
			<value name="str2" type="String" />
			<value name="str3" type="String" />
			
			<value name="b" type="byte" />
			<value name="s" type="short" />
			<value name="i" type="int" />
			<value name="l" type="long" />
			<value name="c" type="char" init="'a'"/>
			<value name="f" type="float" />
			<value name="d" type="double" />
			<value name="inst" type="java.time.Instant" init="`12am 01Jan2020`" />
			<value name="instS">inst.toString()</value>

			<value name="str1plus1">str1+str1</value>
			<value name="str1plus2">str1+str2</value>
			<value name="str1plus2plus3">str1+str2+str3</value>

			<value name="strPlusB">str1+b</value>
			<value name="bPlusStr">b+str1</value>
			<value name="strPlusS">str1+s</value>
			<value name="sPlusStr">s+str1</value>
			<value name="strPlusI">str1+i</value>
			<value name="iPlusStr">i+str1</value>
			<value name="strPlusL">str1+l</value>
			<value name="lPlusStr">l+str1</value>
			<value name="strPlusC">str1+c</value>
			<value name="cPlusStr">c+str1</value>
			<value name="strPlusF">str1+f</value>
			<value name="fPlusStr">f+str1</value>
			<value name="strPlusD">str1+d</value>
			<value name="dPlusStr">d+str1</value>
			<value name="strPlusInst">str1+inst</value>
		</model>

		<action>assertEquals("nullnull", str1plus1)</action>
		<action>assertEquals("nullnull", str1plus2)</action>
		<action>assertEquals("nullnullnull", str1plus2plus3)</action>
		<action>assertEquals("null0", strPlusB)</action>
		<action>assertEquals("0null", bPlusStr)</action>
		<action>assertEquals("null0", strPlusS)</action>
		<action>assertEquals("0null", sPlusStr)</action>
		<action>assertEquals("null0", strPlusI)</action>
		<action>assertEquals("0null", iPlusStr)</action>
		<action>assertEquals("null0", strPlusL)</action>
		<action>assertEquals("0null", lPlusStr)</action>
		<action>assertEquals("nulla", strPlusC)</action>
		<action>assertEquals("anull", cPlusStr)</action>
		<action>assertEquals("null0.0", strPlusF)</action>
		<action>assertEquals("0.0null", fPlusStr)</action>
		<action>assertEquals("null0.0", strPlusD)</action>
		<action>assertEquals("0.0null", dPlusStr)</action>
		<action>assertEquals("null"+instS, strPlusInst)</action>

		<action>str1="First"</action>
		<action>str2="Second"</action>
		<action>str3="Third"</action>
		<action>assertEquals("FirstFirst", str1plus1)</action>
		<action>assertEquals("FirstSecond", str1plus2)</action>
		<action>assertEquals("FirstSecondThird", str1plus2plus3)</action>
		<action>assertEquals("First0", strPlusB)</action>
		<action>assertEquals("0First", bPlusStr)</action>
		<action>assertEquals("First0", strPlusS)</action>
		<action>assertEquals("0First", sPlusStr)</action>
		<action>assertEquals("First0", strPlusI)</action>
		<action>assertEquals("0First", iPlusStr)</action>
		<action>assertEquals("First0", strPlusL)</action>
		<action>assertEquals("0First", lPlusStr)</action>
		<action>assertEquals("Firsta", strPlusC)</action>
		<action>assertEquals("aFirst", cPlusStr)</action>
		<action>assertEquals("First0.0", strPlusF)</action>
		<action>assertEquals("0.0First", fPlusStr)</action>
		<action>assertEquals("First0.0", strPlusD)</action>
		<action>assertEquals("0.0First", dPlusStr)</action>
		<action>assertEquals("First"+instS, strPlusInst)</action>

		<!-- These operators throws IllegalArgumentExceptions, but this is converted to an IllegalStateException by the assignment actions -->
		<!-- If an assignment results in a value that is not what was assigned, it is not allowed -->
		<action expect-throw="IllegalStateException">str1plus1="BlahFirst"</action>
		<!-- The assigned value must end with the concatenated strings, because only the first string may be modified by the assignment -->
		<action expect-throw="IllegalStateException">str1plus2="BlahBlah"</action>

		<!-- Assignment of string concatenated values -->
		<action>str1plus2="NewFirstSecond"</action>
		<action>assertEquals("NewFirst", str1)</action>
		<action>assertEquals("NewFirstNewFirst", str1plus1)</action>
		<action>assertEquals("NewFirstSecond", str1plus2)</action>
		<action>assertEquals("NewFirstSecondThird", str1plus2plus3)</action>
		<action>assertEquals("NewFirst0", strPlusB)</action>
		<action>assertEquals("0NewFirst", bPlusStr)</action>
		<action>assertEquals("NewFirst0", strPlusS)</action>
		<action>assertEquals("0NewFirst", sPlusStr)</action>
		<action>assertEquals("NewFirst0", strPlusI)</action>
		<action>assertEquals("0NewFirst", iPlusStr)</action>
		<action>assertEquals("NewFirst0", strPlusL)</action>
		<action>assertEquals("0NewFirst", lPlusStr)</action>
		<action>assertEquals("NewFirsta", strPlusC)</action>
		<action>assertEquals("aNewFirst", cPlusStr)</action>
		<action>assertEquals("NewFirst0.0", strPlusF)</action>
		<action>assertEquals("0.0NewFirst", fPlusStr)</action>
		<action>assertEquals("NewFirst0.0", strPlusD)</action>
		<action>assertEquals("0.0NewFirst", dPlusStr)</action>
		<action>assertEquals("NewFirst"+instS, strPlusInst)</action>

		<action>str1plus2plus3="FirstAgainSecondThird"</action>
		<action>assertEquals("FirstAgain", str1)</action>
		<action>assertEquals("FirstAgainFirstAgain", str1plus1)</action>
		<action>assertEquals("FirstAgainSecond", str1plus2)</action>
		<action>assertEquals("FirstAgainSecondThird", str1plus2plus3)</action>
		<action>assertEquals("FirstAgain0", strPlusB)</action>
		<action>assertEquals("0FirstAgain", bPlusStr)</action>
		<action>assertEquals("FirstAgain0", strPlusS)</action>
		<action>assertEquals("0FirstAgain", sPlusStr)</action>
		<action>assertEquals("FirstAgain0", strPlusI)</action>
		<action>assertEquals("0FirstAgain", iPlusStr)</action>
		<action>assertEquals("FirstAgain0", strPlusL)</action>
		<action>assertEquals("0FirstAgain", lPlusStr)</action>
		<action>assertEquals("FirstAgaina", strPlusC)</action>
		<action>assertEquals("aFirstAgain", cPlusStr)</action>
		<action>assertEquals("FirstAgain0.0", strPlusF)</action>
		<action>assertEquals("0.0FirstAgain", fPlusStr)</action>
		<action>assertEquals("FirstAgain0.0", strPlusD)</action>
		<action>assertEquals("0.0FirstAgain", dPlusStr)</action>
		<action>assertEquals("FirstAgain"+instS, strPlusInst)</action>

		<!-- Assignment of non-String concatenated values -->
		<action expect-throw="IllegalStateException">strPlusB="Test1"</action>
		<action>strPlusB="TestB0"</action>
		<action>assertEquals("TestB", str1)</action>
		<!-- We'll lay off testing the other values, we've ensured the change will propagate by now -->

		<action expect-throw="IllegalStateException">strPlusS="Test1"</action>
		<action>strPlusS="TestS0"</action>
		<action>assertEquals("TestS", str1)</action>

		<action expect-throw="IllegalStateException">strPlusI="Test1"</action>
		<action>strPlusI="TestI0"</action>
		<action>assertEquals("TestI", str1)</action>

		<action expect-throw="IllegalStateException">strPlusL="Test1"</action>
		<action>strPlusL="TestL0"</action>
		<action>assertEquals("TestL", str1)</action>

		<action expect-throw="IllegalStateException">strPlusC="Test1"</action>
		<action>strPlusC="TestCa"</action>
		<action>assertEquals("TestC", str1)</action>

		<action expect-throw="IllegalStateException">strPlusF="Test1"</action>
		<action>strPlusF="TestF0.0"</action>
		<action>assertEquals("TestF", str1)</action>

		<action expect-throw="IllegalStateException">strPlusD="Test1"</action>
		<action>strPlusD="TestD0.0"</action>
		<action>assertEquals("TestD", str1)</action>

		<action expect-throw="IllegalStateException">strPlusInst="Test1/1/1"</action>
		<action>strPlusInst="TestInst"+instS</action>
		<action>assertEquals("TestInst", str1)</action>
	</test>
	<test name="bitwiseOperators">
		<model>
			<value name="intA" type="int" />
			<value name="intB" type="int" />
			<value name="shift" type="int" />
			<value name="tempI" type="int" />

			<value name="intAComp">~intA</value>
			<value name="intAOrB">intA | intB</value>
			<value name="intAAndB">intA &amp; intB</value>
			<value name="intAXorB">intA ^ intB</value>
			<value name="intALeft">intA&lt;&lt;shift</value>
			<value name="intARight">intA>>shift</value>
			<value name="intAUnsRight">intA>>>shift</value>

			<value name="longA" type="long" />
			<value name="longB" type="long" />
			<value name="tempL" type="long" />

			<value name="longAComp">~longA</value>
			<value name="longAOrB">longA | longB</value>
			<value name="longAAndB">longA &amp; longB</value>
			<value name="longAXorB">longA ^ longB</value>
			<value name="longALeft">longA&lt;&lt;shift</value>
			<value name="longARight">longA>>shift</value>
			<value name="longAUnsRight">longA>>>shift</value>
		</model>

		<!-- int -->
		<action>intA=0b11110101</action>
		<action>assertEquals(0b11111111111111111111111100001010, intAComp)</action>
		<action>assertEquals(intA, intAOrB)</action>
		<action>assertEquals(0, intAAndB)</action>
		<action>assertEquals(intA, intAXorB)</action>
		<action>assertEquals(intA, intALeft)</action>
		<action>assertEquals(intA, intARight)</action>
		<action>assertEquals(intA, intAUnsRight)</action>

		<action>intB=0b10101010</action>
		<action>assertEquals(0b11111111, intAOrB)</action>
		<action>assertEquals(0b10100000, intAAndB)</action>
		<action>assertEquals(0b01011111, intAXorB)</action>

		<action>shift=4</action>
		<action>assertEquals(0b111101010000, intALeft)</action>
		<action>assertEquals(0b1111, intARight)</action>
		<action>assertEquals(0b1111, intAUnsRight)</action>

		<action>intA=0x80000084</action>
		<action>assertEquals(0x840, intALeft)</action>
		<action>assertEquals(0xf8000008, intARight)</action>
		<action>assertEquals(0x8000008, intAUnsRight)</action>

		<!-- Assignments -->
		<action>tempI=0b10111110</action>
		<action>intAOrB=tempI</action>
		<action>assertEquals(tempI, intA)</action>
		<action>assertEquals(tempI, intAOrB)</action>
		<!-- Assignment can't modify b, so the assigned value must have all 1 bits that b has -->
		<action expect-throw="IllegalStateException">intAOrB=0b10110110</action>

		<action>tempI=0b10000010</action>
		<action>intAAndB=tempI</action>
		<action>assertEquals(tempI, intA)</action>
		<action>assertEquals(tempI, intAAndB)</action>
		<!-- Assignment can't modify b, so the assigned value must have all 0 bits that b has -->
		<action expect-throw="IllegalStateException">intAAndB=0b10110110</action>

		<action>tempI=0b11001100</action>
		<action>intAXorB=tempI</action>
		<action>assertEquals(0b01100110, intA)</action>
		<action>assertEquals(tempI, intAXorB)</action>

		<!-- long -->
		<action>shift=0</action>
		<action>longA=0b11110101L</action>
		<action>assertEquals(0b1111111111111111111111111111111111111111111111111111111100001010, longAComp)</action>
		<action>assertEquals(longA, longAOrB)</action>
		<action>assertEquals(0, longAAndB)</action>
		<action>assertEquals(longA, longAXorB)</action>
		<action>assertEquals(longA, longALeft)</action>
		<action>assertEquals(longA, longARight)</action>
		<action>assertEquals(longA, longAUnsRight)</action>

		<action>longB=0b10101010L</action>
		<action>assertEquals(0b11111111L, longAOrB)</action>
		<action>assertEquals(0b10100000L, longAAndB)</action>
		<action>assertEquals(0b01011111L, longAXorB)</action>

		<action>shift=4</action>
		<action>assertEquals(0b111101010000L, longALeft)</action>
		<action>assertEquals(0b1111L, longARight)</action>
		<action>assertEquals(0b1111L, longAUnsRight)</action>

		<action>longA=0x8000000000000084L</action>
		<action>assertEquals(0x840L, longALeft)</action>
		<action>assertEquals(0xf800000000000008L, longARight)</action>
		<action>assertEquals(0x800000000000008L, longAUnsRight)</action>

		<!-- Assignments -->
		<action>tempL=0b10111110L</action>
		<action>longAOrB=tempL</action>
		<action>assertEquals(tempL, longA)</action>
		<action>assertEquals(tempL, longAOrB)</action>
		<!-- Assignment can't modify b, so the assigned value must have all 1 bits that b has -->
		<action expect-throw="IllegalStateException">longAOrB=0b10110110L</action>

		<action>tempL=0b10000010L</action>
		<action>longAAndB=tempL</action>
		<action>assertEquals(tempL, longA)</action>
		<action>assertEquals(tempL, longAAndB)</action>
		<!-- Assignment can't modify b, so the assigned value must have all 0 bits that b has -->
		<action expect-throw="IllegalStateException">longAAndB=0b10110110L</action>

		<action>tempL=0b11001100L</action>
		<action>longAXorB=tempL</action>
		<action>assertEquals(0b01100110L, longA)</action>
		<action>assertEquals(tempL, longAXorB)</action>
	</test>
	<test name="objectOr">
		<model>
			<value name="a" type="String" />
			<value name="b" type="String" />
			<value name="c" type="String" />

			<value name="or">a || b || c</value>
		</model>

		<action>assertEquals(null, or)</action>

		<action>c="Something"</action>
		<action>assertEquals("Something", or)</action>

		<action>b="Something else"</action>
		<action>assertEquals("Something else", or)</action>

		<action>c="Blah"</action>
		<action>assertEquals("Something else", or)</action>

		<action>b="Something"</action>
		<action>assertEquals("Something", or)</action>

		<action>a="String"</action>
		<action>assertEquals("String", or)</action>

		<action>b=null</action>
		<action>assertEquals("String", or)</action>

		<action>a=null</action>
		<action>assertEquals("Blah", or)</action>
	</test>
	<test name="conditionalOperator">
		<model>
			<value name="b" init="false" />
			<value name="p" type="String" />
			<value name="s" type="String" />

			<value name="c">b ? p : s</value>

			<value name="b2" init="false" />
			<value name="obj">new ExpressoReflectTester(&quot;String&quot;)</value>
			<value name="objLen">b2 ? obj.getLength() : 1999</value>

			<value name="b3" init="false" />
			<value name="obj2">new ExpressoReflectTester(&quot;String&quot;)</value>
			<value name="objLen2">b3 ? obj2.getLength() : 1999</value>
			<watch name="watchLen">objLen2</watch>
		</model>

		<action>assertEquals(null, c)</action>

		<action>b=true</action>
		<action>assertEquals(null, c)</action>

		<action>s="Something"</action>
		<action>assertEquals(null, c)</action>

		<action>p="Something else"</action>
		<action>assertEquals("Something else", c)</action>

		<action>b=false</action>
		<action>assertEquals("Something", c)</action>

		<action>p="Something 2"</action>
		<action>assertEquals("Something", c)</action>

		<action>s="Blah"</action>
		<action>assertEquals("Blah", c)</action>

		<!-- Assignment -->
		<action>c="Test"</action>
		<action>assertEquals("Test", s)</action>
		<action>assertEquals("Something 2", p)</action>

		<action>b=true</action>
		<action>c="Test2"</action>
		<action>assertEquals("Test2", p)</action>
		<action>assertEquals("Test", s)</action>

		<!-- Ensure that the inactive target of the conditional is not evalauted -->
		<action>assertEquals(1999, objLen)</action>
		<action>assertEquals(0, obj.lengthCalled)</action>
		<action>b2=true</action>
		<action>assertEquals(6, objLen)</action>
		<action>assertEquals(1, obj.lengthCalled)</action>

		<action>assertEquals(1999, watchLen)</action>
		<action>assertEquals(0, obj2.lengthCalled)</action>
		<action>b3=true</action>
		<action>assertEquals(6, watchLen)</action>
		<action>assertEquals(1, obj2.lengthCalled)</action>
	</test>
	<test name="mapTo">
		<model>
			<transform name="mapped" source="models.anyInt">
				<map-to source-as="source">
					<map-with>source+10</map-with>
					<map-reverse type="replace-source" target-as="target">target-10</map-reverse>
				</map-to>
			</transform>
			<!-- Test the default reverse functionality of map-to -->
			<transform name="mappedValueDR" source="models.anyInt">
				<map-to source-as="source">
					<map-with>source+25</map-with>
				</map-to>
			</transform>
			<transform name="mappedListDR" source="models.list">
				<map-to source-as="source">
					<map-with>source+100</map-with>
				</map-to>
			</transform>
			<value name="initSource" init="models.anyInt" />
		</model>

		<action>assertEquals(models.anyInt+10, mapped)</action>
		<action>mapped+=25</action>
		<action>assertEquals(initSource+25, models.anyInt)</action>
		<action>assertEquals(models.anyInt+10, mapped)</action>

		<action>initSource=models.test.getInt()</action>
		<action>assertEquals(initSource, models.derivedIntModifiable)</action>
		<action>models.derivedIntModifiable+=500</action>
		<action>assertNotEquals(initSource, models.derivedIntModifiable)</action>
		<action>assertEquals(models.derivedIntModifiable, models.test.getInt())</action>

		<action>assertEquals(models.test.getInt()+models.anyInt, models.combinedInt)</action>
		<action>models.test.setInt(22)</action>
		<action>assertEquals(models.test.getInt()+models.anyInt, models.combinedInt)</action>
		<action>models.anyInt=42</action>
		<action>assertEquals(models.test.getInt()+models.anyInt, models.combinedInt)</action>
		<action>models.combinedInt=-37</action>
		<action>assertEquals(models.test.getInt()+models.anyInt, models.combinedInt)</action>
		<!-- TODO Test enabled, accept, add, add-accept attributes for map-to -->

		<!-- Value map-to default reverse -->
		<action>assertEquals(models.anyInt+25, mappedValueDR)</action>
		<action>mappedValueDR=0</action>
		<action>assertEquals(-25, models.anyInt)</action>

		<!-- Collection map-to default reverse -->
		<action>assertEquals(models.list.get(0)+100, mappedListDR.get(0))</action>
		<action>mappedListDR.set(0, 500)</action>
		<action>assertEquals(400, models.list.get(0))</action>
		<action>mappedListDR.add(25)</action>
		<action>assertEquals(-75, models.list.get(5))</action>
	</test>
	<test name="ifElseSwitchCase">
		<model>
			<value name="int1" type="int" />
			<value name="int2" type="int" />
			<value name="int3" type="int" />
			<value name="int4" type="int" />

			<transform name="ifElse" source="int1">
				<if source-as="input">"odd"
					<if if="input % 2 == 0">"even"</if>
				</if>
			</transform>
			<transform name="switchV" source="int1">
				<switch default="-1">
					<return case="int2">int3</return>
					<return case="5">int4</return>
				</switch>
			</transform>
		</model>

		<action>assertEquals("even", ifElse)</action>
		<action>int1=5</action>
		<action>assertEquals("odd", ifElse)</action>

		<action>assertEquals(0, switchV)</action>
		<action>int4=7</action>
		<action>assertEquals(7, switchV)</action>
		<action>int1=0</action>
		<action>assertEquals(0, switchV)</action>
		<action>int2=21</action>
		<action>assertEquals(21, switchV)</action>
		<action>int1=17</action>
		<action>assertEquals(-1, switchV)</action>
	</test>
	<test name="sort">
		<model>
			<value name="entityCopy" init="new java.util.ArrayList&lt;&gt;(models.sortedEntityList)" />
			<value name="random">new org.qommons.testing.TestUtil()</value>
			<value name="maxInstant" type="java.time.Instant">`Dec 31, 2100`</value>
			<value name="index" type="int" />
			<action-group name="randomlyModifyEntity">
				<action>index=random.getInt(0, entityCopy.size())</action>
				<action>entityCopy.get(index)
					.setInt(random.getInt(-10, 11))
					.setDouble(random.getDouble(-10, 10))
					.setBoolean(random.getBoolean())
					.setInstant(java.time.Instant.ofEpochMilli(random.getLong(0, maxInstant.toEpochMilli())))
					.setString(random.getAlphaNumericString(0, 10))
				</action>
				<!-- This println is both for debugging as well as to give evidence that the tests are indeed executing -->
				<action>System.out.println("index="+index+", list="+models.sortedEntityList)</action>
			</action-group>
			<loop name="modifyList" init="i=0" while="i&lt;25" after-body="i++">
				<model>
					<value name="i" type="int" />
				</model>
				<action>randomlyModifyEntity</action>
				<action>checkEntityListOrder(models.sortedEntityList)</action>
			</loop>
		</model>

		<action>modifyList</action>
	</test>
	<test name="assignInt">
		<action>models.expected.setInt(models.anyInt)</action>
		<action>assertNotEquals(models.expected, models.test)</action>
		<action>assertNotNull(models.error)</action>
		<action>models.assignInt</action>
		<action>assertEquals(models.expected, models.test)</action>
		<action>assertNull(models.error)</action>
	</test>
	<test name="assignInstant">
		<action>models.expected.setInstant(models.anyInst)</action>
		<action>assertNotEquals(models.expected, models.test)</action>
		<action>assertNotNull(models.error)</action>
		<action>models.assignInst</action>
		<action>assertEquals(models.expected, models.test)</action>
		<action>assertNull(models.error)</action>
	</test>
	<?DOC Test constructor and method invocation, field get and set.?>
	<test name="reflection">
		<model>
			<value name="str" type="String" />
			<value name="tester">new ExpressoReflectTester(str)</value>
			<value name="methodLen">tester.getLength()</value>
			<value name="i" type="int" />
			<value name="lenPlusI">tester.getLengthPlus(i)</value>
			<value name="lenField">tester.length</value>

			<value name="b" init="false" />
			<value name="tester2" type="ExpressoReflectTester" />
			<value name="len2">tester2.getLength()</value>
		</model>

		<!-- Constructed variable should not be re-evaluated on repeated access -->
		<action>assertEquals(-1, tester.length)</action>
		<action>assertEquals(-1, methodLen)</action>
		<action>assertEquals(1, tester.lengthCalled)</action>
		<action>assertEquals(-1, methodLen)</action>
		<!-- The length value is cached and won't be re-invoked upon repeated access to this variable -->
		<action>assertEquals(1, tester.lengthCalled)</action>
		<action>assertEquals(1, ExpressoReflectTester.CREATED)</action>
		<action>assertEquals(-1, lenPlusI)</action>

		<action>tester.length=0</action>
		<action>assertEquals(-1, methodLen)</action>
		<action>assertEquals(-1, lenPlusI)</action>

		<action>i=21</action>
		<action>assertEquals(21, lenPlusI)</action>

		<action>str="New String"</action>
		<action>assertEquals(10, tester.length)</action>
		<action>assertEquals(10, methodLen)</action>
		<action>assertEquals(1, tester.lengthCalled)</action>
		<action>assertEquals(2, ExpressoReflectTester.CREATED)</action>
		<action>assertEquals(31, lenPlusI)</action>

		<action>i=15</action>
		<action>assertEquals(25, lenPlusI)</action>
		<action>assertEquals(2, ExpressoReflectTester.CREATED)</action>

		<action>assertEquals(15, tester.varArgsCall(1, 2, 3, 4, 5))</action>
		<action>assertEquals(6, tester.varArgsCall(1, 2, 3))</action>

		<!-- Ensure that access to a non-final field always gets the current value -->
		<action>tester.length=0</action>
		<action>assertEquals(0, lenField)</action>
		<action>tester.length=21</action>
		<action>assertEquals(21, lenField)</action>

		<!-- Testing for method access to null context -->
		<action>assertEquals(0, len2)</action>
		<action>tester2=new ExpressoReflectTester("Test")</action>
		<action>assertEquals(4, len2)</action>
		<!-- TODO Type parameterization -->
	</test>
	<test name="classes">
		<model>
			<value name="object" type="Object" />

			<value name="io">object instanceof Number</value>
		</model>

		<!-- Test Class instance -->
		<action>assertEquals(3, int.class.getName().length())</action>
		<action>assertEquals(16, Object.class.getName().length())</action>

		<!-- Test instanceof -->
		<action>assertFalse(io)</action>

		<action>object="String"</action>
		<action>assertFalse(io)</action>

		<action>object=27</action>
		<action>assertTrue(io)</action>

		<action>object=81.1</action>
		<action>assertTrue(io)</action>

		<action>object=new Object()</action>
		<action>assertFalse(io)</action>
	</test>
	<test name="hook">
		<model>
			<value name="changeCount" init="0" />
			<hook name="hook" on="models.anyInt">changeCount++</hook>
		</model>

		<action>assertEquals(0, changeCount)</action>
		<action>models.anyInt++</action>
		<action>assertEquals(1, changeCount)</action>
		<action>models.anyInt--</action>
		<action>assertEquals(2, changeCount)</action>
		<action>models.anyInt=1_571_823</action>
		<action>assertEquals(3, changeCount)</action>
	</test>
	<test name="staticInternalState">
		<model>
			<stateful-struct name="struct1" derived-state="internalState + 10" />
			<stateful-struct name="struct2" derived-state="internalState - 10" />
			<stateful-struct name="struct3" derived-state="internalState * 10" />
			<stateful-struct name="struct4" derived-state="internalState % 10" />

			<!-- Types are declared here so Expresso will flatten the SettableValues -->
			<value name="struct1iv" type="int">struct1.getInternalState()</value>
			<value name="struct2iv" type="int">struct2.getInternalState()</value>
			<value name="struct3iv" type="int">struct3.getInternalState()</value>
			<value name="struct4iv" type="int">struct4.getInternalState()</value>
			<value name="struct1dv" type="int">struct1.getDerivedState()</value>
			<value name="struct2dv" type="int">struct2.getDerivedState()</value>
			<value name="struct3dv" type="int">struct3.getDerivedState()</value>
			<value name="struct4dv" type="int">struct4.getDerivedState()</value>
		</model>

		<!-- In code, the internalState is always initialized to 15 -->
		<action>assertEquals(25, struct1dv)</action>
		<action>assertEquals(5, struct2dv)</action>
		<action>assertEquals(150, struct3dv)</action>
		<action>assertEquals(5, struct4dv)</action>

		<action>struct1iv=1</action>
		<action>struct2iv=2</action>
		<action>struct3iv=3</action>
		<action>struct4iv=4</action>

		<action>assertEquals(11, struct1dv)</action>
		<action>assertEquals(-8, struct2dv)</action>
		<action>assertEquals(30, struct3dv)</action>
		<action>assertEquals(4, struct4dv)</action>
	</test>
	<test name="dynamicTypeInternalState">
		<!-- This test checks the functionality of dynamically-typed internal values specified by element-models in toolkit metadata -->
		<!-- By not specifying a type on the 'internalState' value in the model, the API is advertising a value but leaving the type
			as well as the value up to satisfaction by the implementation.
			As documented in the toolkit, the internalState value is set to the value of the 'internal-state' attribute with its type.
			This allows the derived-state expression to use the internalState value as a value its dynamically-determined type.
		-->
		<model>
			<dynamic-type-stateful-struct name="struct1" internal-state="models.anyInt" derived-state="internalState + 10" />
			<dynamic-type-stateful-struct name="struct2" internal-state="models.anyDbl" derived-state="internalState / 10" />
			<dynamic-type-stateful-struct name="struct3" internal-state="models.anyBool" derived-state="!internalState" />
			<dynamic-type-stateful-struct name="struct4" internal-state="models.anyStr" derived-state="internalState + &quot;-derived&quot;" />

			<!-- Types are declared here so Expresso will flatten the SettableValues -->
			<value name="struct1iv" type="int">(int) struct1.getInternalState()</value>
			<value name="struct2iv" type="double">(double) struct2.getInternalState()</value>
			<value name="struct3iv" type="boolean">(boolean) struct3.getInternalState()</value>
			<value name="struct4iv" type="String">(String) struct4.getInternalState()</value>
			<value name="struct1dv" type="int">(int) struct1.getDerivedState()</value>
			<value name="struct2dv" type="double">(double) struct2.getDerivedState()</value>
			<value name="struct3dv" type="boolean">(Boolean) struct3.getDerivedState()</value>
			<value name="struct4dv" type="String">(String) struct4.getDerivedState()</value>
		</model>

		<action>models.anyInt=10</action>
		<action>models.anyDbl=15</action>
		<action>models.anyStr="initStr"</action>

		<action>assertEquals(20, struct1dv)</action>
		<action>assertEquals(1.5, struct2dv, 1E-10)</action>
		<action>assertEquals(false, struct3dv)</action>
		<action>assertEquals("initStr-derived", struct4dv)</action>

		<action>struct1iv=25</action>
		<action>struct2iv=-9.75</action>
		<action>struct3iv=false</action>
		<action>struct4iv="changedStr"</action>

		<action>assertEquals(35, struct1dv)</action>
		<action>assertEquals(-0.975, struct2dv, 1E-10)</action>
		<action>assertEquals(true, struct3dv)</action>
		<action>assertEquals("changedStr-derived", struct4dv)</action>
	</test>
	<test name="dynamicTypeInternalState2">
		<!-- This test checks the functionality of dynamically-typed internal values specified by element-models in toolkit metadata -->
		<!-- By not specifying a type on the 'internalState' value in the model, the API is advertising a value but leaving the type
			as well as the value up to satisfaction by the implementation.
			As documented in the toolkit, the internalState value is set to the value of the 'internal-state' attribute with its type.
			This allows the derived-state expression to use the internalState value as a value its dynamically-determined type.
		-->
		<model>
			<dynamic-type-stateful-struct2 name="struct1" internal-state="models.anyInt" derived-state="internalState + 10" />
			<dynamic-type-stateful-struct2 name="struct2" internal-state="models.anyDbl" derived-state="internalState / 10" />
			<dynamic-type-stateful-struct2 name="struct3" internal-state="models.anyBool" derived-state="!internalState" />
			<dynamic-type-stateful-struct2 name="struct4" internal-state="models.anyStr" derived-state="internalState + &quot;-derived&quot;" />

			<!-- Types are declared here so Expresso will flatten the SettableValues -->
			<value name="struct1iv" type="int">(int) struct1.getInternalState()</value>
			<value name="struct2iv" type="double">(double) struct2.getInternalState()</value>
			<value name="struct3iv" type="boolean">(boolean) struct3.getInternalState()</value>
			<value name="struct4iv" type="String">(String) struct4.getInternalState()</value>
			<value name="struct1dv" type="int">(int) struct1.getDerivedState()</value>
			<value name="struct2dv" type="double">(double) struct2.getDerivedState()</value>
			<value name="struct3dv" type="boolean">(Boolean) struct3.getDerivedState()</value>
			<value name="struct4dv" type="String">(String) struct4.getDerivedState()</value>
		</model>

		<action>models.anyInt=10</action>
		<action>models.anyDbl=15</action>
		<action>models.anyStr="initStr"</action>

		<action>assertEquals(20, struct1dv)</action>
		<action>assertEquals(1.5, struct2dv, 1E-10)</action>
		<action>assertEquals(false, struct3dv)</action>
		<action>assertEquals("initStr-derived", struct4dv)</action>

		<action>struct1iv=25</action>
		<action>struct2iv=-9.75</action>
		<action>struct3iv=false</action>
		<action>struct4iv="changedStr"</action>

		<action>assertEquals(35, struct1dv)</action>
		<action>assertEquals(-0.975, struct2dv, 1E-10)</action>
		<action>assertEquals(true, struct3dv)</action>
		<action>assertEquals("changedStr-derived", struct4dv)</action>
	</test>
</testing>

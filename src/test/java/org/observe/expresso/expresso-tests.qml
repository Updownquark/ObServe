<?xml version="1.0" encoding="UTF-8"?>

<testing uses:test="Expresso-Test 0.1" uses:debug="Expresso-Debug v0.1">
	<!-- TODO
		Test AND DOCUMENT!!

		Simple Lists, Sorted Lists, Sets, Sorted Sets with configured elements
		Derived Lists, Sorted Lists, Sets, Sorted Sets (+ test set)
		Simple Maps, Sorted Maps, Multi-Maps, Sorted Multi-Maps with configured entries
		Derived Maps, Sorted Maps, Multi-Maps, Sorted Multi-Maps (+ test set)
		Event

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
			with-equivalance
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
			Assignment
			Unary operators
				!, ~, -
				++, - - (pre and post)
			Binary operators
				+, -, *, /, %
				==, !=, <, <=, >, >=
				&&, ||
			Cast
			Class instance (e.g. 'int.class')
			Conditional (ternary)
			Constructor
				Value should not change upon repeated access (at least for simple values)
				Test with arguments that change (should be re-invoked when args change)
			External Literals (all types)
			instanceof
			Lambda (-> and ::)
			Method
				Value should not change upon repeated access (at least for simple values)
				Test with arguments that change (should be re-invoked when args change)
				Function
				Static
				Non-static
				var-args
				type-parameterized
			Field access/set
			Model value (e.g. 'model.value')
			Parenthetic

		Test external models
		In new file, test Expresso-Config
		In new file, test styles (make a test toolkit and impl?)
	-->
	<expresso>
		<imports>
			<import>org.observe.expresso.ExpressoTestEntity</import>
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
	<test name="binaryOperators">
		<model>
			<value name="a" init="false" />
			<value name="b" init="false" />
	
			<value name="or">a || b</value>
			<value name="and">a &amp;&amp; b</value>

			<watch name="watchOr">or</watch>
			<watch name="watchAnd">and</watch>
		</model>

		<action>assertFalse(or)</action>
		<action>assertFalse(watchOr)</action>
		<action>assertFalse(and)</action>
		<action>assertFalse(watchAnd)</action>
		<action>a=true</action>
		<action>assertTrue(or)</action>
		<action>assertTrue(watchOr)</action>
		<action>assertFalse(and)</action>
		<action>assertFalse(watchAnd)</action>
		<action>b=true</action>
		<action>assertTrue(or)</action>
		<action>assertTrue(watchOr)</action>
		<action>assertTrue(and)</action>
		<action>assertTrue(watchAnd)</action>
		<action>a=false</action>
		<action>assertTrue(or)</action>
		<action>assertTrue(watchOr)</action>
		<action>assertFalse(and)</action>
		<action>assertFalse(watchAnd)</action>
		<action>b=false</action>
		<action>assertFalse(or)</action>
		<action>assertFalse(watchOr)</action>
		<action>assertFalse(and)</action>
		<action>assertFalse(watchAnd)</action>

		<!-- TODO
			+, -, *, /, %
			==, !=, <, <=, >, >=
			^, | &, Object ||
		 -->
	</test>
	<test name="mapTo">
		<model>
			<transform name="mapped" source="models.anyInt">
				<map-to source-as="source">
					<map-with>source+10</map-with>
					<map-reverse type="replace-source" target-as="target">target-10</map-reverse>
				</map-to>
			</transform>
			<value name="initSource" init="models.anyInt" />
		</model>

		<action>assertEquals(models.anyInt+10, mapped)</action>
		<action>mapped+=25</action>
		<action>assertEquals(models.anyInt, initSource+25)</action>
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

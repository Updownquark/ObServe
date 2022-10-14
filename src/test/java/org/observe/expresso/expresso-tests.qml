<?xml version="1.0" encoding="UTF-8"?>

<expresso uses:expresso="Expresso-Base 0.1">
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
	<imports>
		<import>org.observe.expresso.ExpressoTestEntity</import>
		<import>org.junit.Assert.*</import>
	</imports>
	<models>
		<ext-model name="ext">
			<value name="actionName" type="String" />
		</ext-model>
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
			
			<value name="derivedInt">anyInt*10</value>

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
		</model>
		<model name="tests">
			<model name="constant">
				<action name="testConstant">assertEquals(ext.actionName, 5, models.constant)</action>
				<action name="testConstantAssignmentXUnsupportedOperationException">models.constant=6</action>
			</model>
			<model name="simpleValue">
				<action name="testConstant">assertEquals(ext.actionName, 5, models.alsoConstant)</action>
				<action name="testConstantAssignmentXUnsupportedOperationException">models.alsoConstant=6</action>
				<action name="testEmptyValue">assertEquals(ext.actionName, null, models.emptyValue)</action>
				<action name="testAssignEmptyVal">models.emptyValue=5</action>
				<action name="testEmptyValue2">assertEquals(ext.actionName, 5, models.emptyValue)</action>
				<action name="testSimpleValue">assertEquals(ext.actionName, 10, models.anyInt)</action>
				<action name="testAssignSimpleValue">models.anyInt=5</action>
				<action name="testSimpleValue2">assertEquals(ext.actionName, 5, models.anyInt)</action>
			</model>
			<model name="derivedValue">
				<action name="assignTestField">models.test.setInt(30)</action>
				<action name="checkTestValue">assertEquals(ext.actionName, 30, models.testInt)</action>
				<action name="assignTestValueXUnsupportedOperationException">models.testInt=20</action>

				<action name="checkDerivedValue">assertEquals(ext.actionName, 100, models.derivedInt)</action>
				<action name="assignDerivedValue">models.derivedInt=150</action>
				<action name="checkDerivedValue2">assertEquals(ext.actionName, 150, models.derivedInt)</action>
				<action name="checkSourceValue">assertEquals(ext.actionName, 15, models.anyInt)</action>
			</model>
			<model name="list">
				<value name="size">models.list.observeSize()</value>
				<action name="checkSize">assertEquals(ext.actionName, 5, size)</action>
				<action name="addValue">models.list.add(17)</action>
				<action name="checkSize2">assertEquals(ext.actionName, 6, size)</action>
				<!-- TODO Need more -->
			</model>
			<model name="assignInt">
				<action name="setExpectInt">models.expected.setInt(models.anyInt)</action>
				<action name="checkNotEqual">assertNotEquals(ext.actionName, models.expected, models.test)</action>
				<action name="checkError">assertNotNull(ext.actionName, models.error)</action>
				<action name="assignInt">models.assignInt</action>
				<action name="checkEqual">assertEquals(ext.actionName, models.expected, models.test)</action>
				<action name="checkError2">assertNull(ext.actionName, models.error)</action>
			</model>
			<model name="assignInstant">
				<action name="setExpectInst">models.expected.setInstant(models.anyInst)</action>
				<action name="checkNotEqual">assertNotEquals(ext.actionName, models.expected, models.test)</action>
				<action name="checkError">assertNotNull(ext.actionName, models.error)</action>
				<action name="assignInst">models.assignInst</action>
				<action name="checkEqual">assertEquals(ext.actionName, models.expected, models.test)</action>
				<action name="checkError2">assertNull(ext.actionName, models.error)</action>
			</model>
		</model>
	</models>
</expresso>

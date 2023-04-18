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
	<head>
		<imports>
			<import>org.observe.expresso.ExpressoTestEntity</import>
			<import>org.junit.Assert.*</import>
			<import>org.observe.expresso.ExpressoTests.*</import>
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
	</head>
	<test name="constant">
		<model>
			<value name="initInt" init="models.anyInt" />
		</model>
		<action name="testConstant">assertEquals(ext.actionName, 5, models.constant)</action>
		<action name="testConstantAssignment" expect-throw="UnsupportedOperationException">models.constant=6</action>
		<action name="testConstant2">assertEquals(ext.actionName, initInt, models.constant2)</action>
		<action name="modifyConstantSource">models.anyInt+=50</action>
		<action name="testConstant3">assertEquals(ext.actionName, initInt, models.constant2)</action>
	</test>
	<test name="simpleValue">
		<action name="testConstant">assertEquals(ext.actionName, 5, models.alsoConstant)</action>
		<action name="testConstantAssignment" expect-throw="UnsupportedOperationException">models.alsoConstant=6</action>
		<action name="testEmptyValue">assertEquals(ext.actionName, null, models.emptyValue)</action>
		<action name="testAssignEmptyVal">models.emptyValue=5</action>
		<action name="testEmptyValue2">assertEquals(ext.actionName, 5, models.emptyValue)</action>
		<action name="testSimpleValue">assertEquals(ext.actionName, 10, models.anyInt)</action>
		<action name="testAssignSimpleValue">models.anyInt=5</action>
		<action name="testSimpleValue2">assertEquals(ext.actionName, 5, models.anyInt)</action>
	</test>
	<test name="derivedValue">
		<action name="assignTestField">models.test.setInt(30)</action>
		<action name="checkTestValue">assertEquals(ext.actionName, 30, models.testInt)</action>
		<action name="assignTestValue" expect-throw="UnsupportedOperationException">models.testInt=20</action>

		<action name="checkDerivedValue">assertEquals(ext.actionName, 100, models.derivedInt)</action>
		<action name="assignDerivedValue">models.derivedInt=150</action>
		<action name="checkDerivedValue2">assertEquals(ext.actionName, 150, models.derivedInt)</action>
		<action name="checkSourceValue">assertEquals(ext.actionName, 15, models.anyInt)</action>
	</test>
	<test name="list">
		<model>
			<value name="size" type="int">models.list.observeSize()</value>
		</model>
		<action name="checkSize">assertEquals(ext.actionName, 5, size)</action>
		<action name="addValue">models.list.add(17)</action>
		<action name="checkSize2">assertEquals(ext.actionName, 6, size)</action>
		<!-- TODO Need more -->
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

		<action name="checkMapped">assertEquals(ext.actionName, models.anyInt+10, mapped)</action>
		<action name="modifyMapped">mapped+=25</action>
		<action name="checkSource">assertEquals(ext.actionName, models.anyInt, initSource+25)</action>
		<action name="checkMapped2">assertEquals(ext.actionName, models.anyInt+10, mapped)</action>

		<action name="assignInitSource">initSource=models.test.getInt()</action>
		<action name="checkDerived">assertEquals(ext.actionName, initSource, models.derivedIntModifiable)</action>
		<action name="assignDerived">models.derivedIntModifiable+=500</action>
		<action name="checkDerivedChanged">assertNotEquals(ext.actionName, initSource, models.derivedIntModifiable)</action>
		<action name="checkDerived2">assertEquals(ext.actionName, models.derivedIntModifiable, models.test.getInt())</action>

		<action name="checkCombined">assertEquals(ext.actionName, models.test.getInt()+models.anyInt, models.combinedInt)</action>
		<action name="modifyCombinedSource">models.test.setInt(22)</action>
		<action name="checkCombinedChanged">assertEquals(ext.actionName, models.test.getInt()+models.anyInt, models.combinedInt)</action>
		<action name="modifyCombinedOther">models.anyInt=42</action>
		<action name="checkCombinedChanged2">assertEquals(ext.actionName, models.test.getInt()+models.anyInt, models.combinedInt)</action>
		<action name="modifyCombined">models.combinedInt=-37</action>
		<action name="checkCombinedChanged3">assertEquals(ext.actionName, models.test.getInt()+models.anyInt, models.combinedInt)</action>
		<!-- TODO Test enabled, accept, add, add-accept attributes for map-to -->
	</test>
	<test name="sort">
		<model>
			<value name="entityCopy" init="new java.util.ArrayList&lt;&gt;(models.sortedEntityList)" />
			<value name="random">new org.qommons.TestUtil()</value>
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

		<action name="test">modifyList</action>
	</test>
	<test name="assignInt">
		<action name="setExpectInt">models.expected.setInt(models.anyInt)</action>
		<action name="checkNotEqual">assertNotEquals(ext.actionName, models.expected, models.test)</action>
		<action name="checkError">assertNotNull(ext.actionName, models.error)</action>
		<action name="assignInt">models.assignInt</action>
		<action name="checkEqual">assertEquals(ext.actionName, models.expected, models.test)</action>
		<action name="checkError2">assertNull(ext.actionName, models.error)</action>
	</test>
	<test name="assignInstant">
		<action name="setExpectInst">models.expected.setInstant(models.anyInst)</action>
		<action name="checkNotEqual">assertNotEquals(ext.actionName, models.expected, models.test)</action>
		<action name="checkError">assertNotNull(ext.actionName, models.error)</action>
		<action name="assignInst">models.assignInst</action>
		<action name="checkEqual">assertEquals(ext.actionName, models.expected, models.test)</action>
		<action name="checkError2">assertNull(ext.actionName, models.error)</action>
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

		<action name="checkState1">assertEquals(ext.actionName, 25, struct1dv)</action>
		<action name="checkState2">assertEquals(ext.actionName, 5, struct2dv)</action>
		<action name="checkState3">assertEquals(ext.actionName, 150, struct3dv)</action>
		<action name="checkState4">assertEquals(ext.actionName, 5, struct4dv)</action>

		<action name="modState1">struct1iv=1</action>
		<action name="modState2">struct2iv=2</action>
		<action name="modState3">struct3iv=3</action>
		<action name="modState4">struct4iv=4</action>

		<action name="checkState1_2">assertEquals(ext.actionName, 11, struct1dv)</action>
		<action name="checkState2_2">assertEquals(ext.actionName, -8, struct2dv)</action>
		<action name="checkState3_2">assertEquals(ext.actionName, 30, struct3dv)</action>
		<action name="checkState4_2">assertEquals(ext.actionName, 4, struct4dv)</action>
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

		<action name="setInitInt">models.anyInt=10</action>
		<action name="setInitDbl">models.anyDbl=15</action>
		<action name="setInitStr">models.anyStr="initStr"</action>

		<action name="checkState1">assertEquals(ext.actionName, 20, struct1dv)</action>
		<action name="checkState2">assertEquals(ext.actionName, 1.5, struct2dv, 1E-10)</action>
		<action name="checkState3">assertEquals(ext.actionName, false, struct3dv)</action>
		<action name="checkState4">assertEquals(ext.actionName, "initStr-derived", struct4dv)</action>

		<action name="modState1">struct1iv=25</action>
		<action name="modState2">struct2iv=-9.75</action>
		<action name="modState3">struct3iv=false</action>
		<action name="modState4">struct4iv="changedStr"</action>

		<action name="checkState1_2">assertEquals(ext.actionName, 35, struct1dv)</action>
		<action name="checkState2_2">assertEquals(ext.actionName, -0.975, struct2dv, 1E-10)</action>
		<action name="checkState3_2">assertEquals(ext.actionName, true, struct3dv)</action>
		<action name="checkState4_2">assertEquals(ext.actionName, "changedStr-derived", struct4dv)</action>
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

		<action name="setInitInt">models.anyInt=10</action>
		<action name="setInitDbl">models.anyDbl=15</action>
		<action name="setInitStr">models.anyStr="initStr"</action>

		<action name="checkState1">assertEquals(ext.actionName, 20, struct1dv)</action>
		<action name="checkState2">assertEquals(ext.actionName, 1.5, struct2dv, 1E-10)</action>
		<action name="checkState3">assertEquals(ext.actionName, false, struct3dv)</action>
		<action name="checkState4">assertEquals(ext.actionName, "initStr-derived", struct4dv)</action>

		<action name="modState1">struct1iv=25</action>
		<action name="modState2">struct2iv=-9.75</action>
		<action name="modState3">struct3iv=false</action>
		<action name="modState4">struct4iv="changedStr"</action>

		<action name="checkState1_2">assertEquals(ext.actionName, 35, struct1dv)</action>
		<action name="checkState2_2">assertEquals(ext.actionName, -0.975, struct2dv, 1E-10)</action>
		<action name="checkState3_2">assertEquals(ext.actionName, true, struct3dv)</action>
		<action name="checkState4_2">assertEquals(ext.actionName, "changedStr-derived", struct4dv)</action>
	</test>
</testing>

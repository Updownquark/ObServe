<?xml version="1.0" encoding="UTF-8"?>

<testing uses:style="Quick-Style-Test 0.1">
	<!--Test: (all with changes, observability)
		Simple style as fn of model value
		Simple style as fn of element value
		Simple style as fn of local value
		Prioritization of element values in a style with more than one possible values for an attribute
			Test observability as values of both elements change
		Inherited style from parent
		Style sheet styles
		Local styles
	-->
	<head>
		<imports>
			<import>org.junit.Assert.*</import>
			<import>org.observe.quick.style.QuickStyleTests.*</import>
		</imports>
		<models>
			<ext-model name="ext">
				<value name="actionName" type="String" />
			</ext-model>
			<model name="models">
				<value name="m0" type="boolean" />
				<value name="m1" type="int" />
				<value name="m2" type="boolean" />
				<value name="m3" type="int" />
			</model>
		</models>
	</head>
	<test name="test0">
		<model>
			<a name="a0" a="models.m0" b="models.m2" c="models.m1">
				<style attr="s0">a</style>
				<style attr="s1" condition="b">models.m3</style>
				<style attr="s1">c*10</style>
			</a>
			<watch name="w_a0_s0">a0.s0</watch>
			<watch name="w_a0_s1">a0.s1</watch>
		</model>
		<action name="assignM0_1">models.m0=true</action>
		<action name="checkS0_1">assertEquals(ext.actionName, models.m0, a0.s0)</action>
		<action name="checkWS0_1">assertEquals(ext.actionName, models.m0, w_a0_s0)</action>

		<action name="assignM1_1">models.m1=25</action>
		<action name="checkS1_1">assertEquals(ext.actionName, 250, a0.s1)</action>
		<action name="checkWS0_1">assertEquals(ext.actionName, 250, w_a0_s1)</action>

		<action name="assignM3">models.m3=50</action>
		<action name="checkS1_2">assertEquals(ext.actionName, 250, a0.s1)</action>
		<action name="checkWS0_2">assertEquals(ext.actionName, 250, w_a0_s1)</action>

		<action name="assignM2_1" breakpoint="true">models.m2=true</action>
		<action name="checkS1_3" breakpoint="true">assertEquals(ext.actionName, 50, a0.s1)</action>
		<action name="checkWS0_3">assertEquals(ext.actionName, 50, w_a0_s1)</action>

		<action name="assignM1_2" breakpoint="true">models.m1=300</action>
		<action name="checkS1_4" breakpoint="true">assertEquals(ext.actionName, 50, a0.s1)</action>
		<action name="checkWS0_4">assertEquals(ext.actionName, 50, w_a0_s1)</action>

		<action name="assignM2_2" breakpoint="true">models.m2=false</action>
		<action name="checkS1_5" breakpoint="true">assertEquals(ext.actionName, 3000, a0.s1)</action>
		<action name="checkWS0_5">assertEquals(ext.actionName, 3000, w_a0_s1)</action>
	</test>
</testing>

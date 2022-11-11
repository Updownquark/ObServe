<?xml version="1.0" encoding="UTF-8"?>

<testing uses:style="Quick-Style-Test 0.1">
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
				<value name="m4" type="boolean "/>
			</model>
		</models>
		<style-sheet>
			<style element="b">
				<style attr="s3" condition="d">models.m3</style>
			</style>
		</style-sheet>
	</head>
	<test name="basicStyle">
		<!-- Basic styles that depend on model values with various conditions with varying priority and complexity -->
		<model>
			<a name="a0" a="models.m0" b="models.m2" c="models.m1" d="models.m4">
				<style attr="s0">a</style>
				<style attr="s1">c*10</style>
				<style attr="s1" condition="a">models.m1</style>
				<style attr="s1" condition="b">models.m3</style>
				<style attr="s1" condition="a &amp;&amp; d">models.m1 + models.m3</style>
			</a>
			<watch name="w_a0_s0">a0.s0</watch>
			<watch name="w_a0_s1">a0.s1</watch>
		</model>
		<action name="assignA_1">models.m0=true</action>
		<action name="checkS0_1">assertEquals(ext.actionName, models.m0, a0.s0)</action>
		<action name="checkWS0_1">assertEquals(ext.actionName, models.m0, w_a0_s0)</action>

		<!-- Default s1 condition, c=models.m1=0, so s1 should be 0 -->
		<action name="checkS1_1">assertEquals(ext.actionName, 0, a0.s1)</action>
		<action name="checkWS0_1">assertEquals(ext.actionName, 0, w_a0_s1)</action>

		<!-- Change the value of the active -->
		<action name="assignM1_1">models.m1=25</action>
		<action name="checkS1_1">assertEquals(ext.actionName, 25, a0.s1)</action>
		<action name="checkWS0_1">assertEquals(ext.actionName, 25, w_a0_s1)</action>

		<!-- Revert to default condition -->
		<action name="assignA_2">models.m0=false</action>
		<action name="checkS1_1">assertEquals(ext.actionName, 250, a0.s1)</action>
		<action name="checkWS0_1">assertEquals(ext.actionName, 250, w_a0_s1)</action>

		<!-- Verify no change for value with unmet condition -->
		<action name="assignM3">models.m3=50</action>
		<action name="checkS1_2">assertEquals(ext.actionName, 250, a0.s1)</action>
		<action name="checkWS0_2">assertEquals(ext.actionName, 250, w_a0_s1)</action>

		<!-- Change to b condition -->
		<action name="assignB_1">models.m2=true</action>
		<action name="checkS1_3">assertEquals(ext.actionName, 50, a0.s1)</action>
		<action name="checkWS0_3">assertEquals(ext.actionName, 50, w_a0_s1)</action>

		<!-- b=models.m2 has higher priority than a=models.m0, so making 'a' true won't affect s1 -->
		<action name="assignA_3">models.m0=true</action>
		<action name="checkS1_3">assertEquals(ext.actionName, 50, a0.s1)</action>
		<action name="checkWS0_3">assertEquals(ext.actionName, 50, w_a0_s1)</action>

		<!-- a && d has higher priority than b though -->
		<action name="assignD_1">models.m4=true</action>
		<action name="checkS1_3">assertEquals(ext.actionName, 75, a0.s1)</action>
		<action name="checkWS0_3">assertEquals(ext.actionName, 75, w_a0_s1)</action>
		<!-- Back to b condition -->
		<action name="assignD_2">models.m4=false</action>
		<action name="checkS1_3">assertEquals(ext.actionName, 50, a0.s1)</action>
		<action name="checkWS0_3">assertEquals(ext.actionName, 50, w_a0_s1)</action>

		<!-- Verify no change for value with unmet condition -->
		<action name="assignM1_2">models.m1=300</action>
		<action name="checkS1_4">assertEquals(ext.actionName, 50, a0.s1)</action>
		<action name="checkWS0_4">assertEquals(ext.actionName, 50, w_a0_s1)</action>

		<!-- Back to a condition -->
		<action name="assignB_2">models.m2=false</action>
		<action name="checkS1_4">assertEquals(ext.actionName, 300, a0.s1)</action>
		<action name="checkWS0_4">assertEquals(ext.actionName, 300, w_a0_s1)</action>

		<!-- Revert to default condition -->
		<action name="assignA_4">models.m0=false</action>
		<action name="checkS1_5">assertEquals(ext.actionName, 3000, a0.s1)</action>
		<action name="checkWS0_5">assertEquals(ext.actionName, 3000, w_a0_s1)</action>

		<!-- No change, needs both a and d -->
		<action name="assignD_3">models.m4=true</action>
		<action name="checkS1_5">assertEquals(ext.actionName, 3000, a0.s1)</action>
		<action name="checkWS0_5">assertEquals(ext.actionName, 3000, w_a0_s1)</action>
	</test>
	<test name="localStyleSheet">
		<!-- Test styles that are prescribed by a style sheet defined in this document -->
		<model>
			<a name="a0" a="localA" b="localB" c="localC" d="localD">
				<model>
					<value name="localA" type="boolean" init="false" />
					<value name="localB" type="boolean" init="false" />
					<value name="localC" type="int" init="0" />
					<value name="localD" type="boolean" init="false" />
				</model>
			</a>
			<b name="b0" e="localE" f="localF">
				<model>
					<value name="localA" type="boolean" init="false" />
					<value name="localB" type="boolean" init="false" />
					<value name="localC" type="int" init="0" />
					<value name="localD" type="boolean" init="false" />
					<value name="localE" type="boolean" init="false" />
					<value name="localF" type="int" init="0" />
				</model>
				<a a="localA" b="localB" c="localC" d="localD" />
			</b>
			<c name="c0" e="localE" f="localF" g="localG">
				<model>
					<value name="localA" type="boolean" init="false" />
					<value name="localB" type="boolean" init="false" />
					<value name="localC" type="int" init="0" />
					<value name="localD" type="boolean" init="false" />
					<value name="localE" type="boolean" init="false" />
					<value name="localF" type="int" init="0" />
					<value name="localG" type="boolean" init="false" />
				</model>
				<a a="localA" b="localB" c="localC" d="localD" />
			</c>
			<d name="d0" e="localE" f="localF" h="localH">
				<model>
					<value name="localA" type="boolean" init="false" />
					<value name="localB" type="boolean" init="false" />
					<value name="localC" type="int" init="0" />
					<value name="localD" type="boolean" init="false" />
					<value name="localE" type="boolean" init="false" />
					<value name="localF" type="int" init="0" />
					<value name="localH" type="int" init="0" />
				</model>
				<a a="localA" b="localB" c="localC" d="localD" />
			</d>
		</model>
		<!--
			Include local styles also
		-->
		<action name="blank">models.m0=true</action>
	</test>
	<test name="importedStyleSheet">
		<!-- Test styles that are prescribed by a style sheet imported from outside this document -->
		<model>
		</model>
		<action name="blank">models.m0=true</action>
	</test>
	<test name="inheritedStyle">
		<!-- Test inheritance of styles from parent elements -->
		<model>
		</model>
		<action name="blank">models.m0=true</action>
	</test>
</testing>

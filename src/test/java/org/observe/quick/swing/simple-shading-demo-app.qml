<?xml version="1.0" encoding="UTF-8"?>

<quick-app xmlns:app="Quick-App v0.1" app-file="simple-shading-demo.qml">
	<toolkit def="/org/qommons/config/simple-qonfig-reference.qtd" />
	<toolkit def="/org/observe/expresso/qonfig/expresso-core.qtd">
		<value-type>org.observe.expresso.qonfig.ExpressionValueType</value-type>
	</toolkit>
	<toolkit def="/org/observe/expresso/qonfig/expresso-base.qtd" />
	<toolkit def="/org/observe/quick/style/quick-style.qtd" />
	<toolkit def="/org/observe/quick/quick-core.qtd" />
	<toolkit def="/org/observe/quick/base/quick-base.qtd" />
	<toolkit def="/org/observe/quick/ext/quick-ext.qtd" />
	<special-session>org.observe.expresso.qonfig.ExpressoSessionImplV0_1</special-session>
	<interpretation>org.observe.expresso.qonfig.ExpressoBaseV0_1</interpretation>
	<interpretation>org.observe.quick.style.QuickStyleInterpretation</interpretation>
	<interpretation>org.observe.quick.QuickCoreInterpretation</interpretation>
	<interpretation>org.observe.quick.base.QuickBaseInterpretation</interpretation>
	<interpretation>org.observe.quick.ext.QuickXInterpretation</interpretation>
	<quick-interpretation>org.observe.quick.swing.QuickCoreSwing</quick-interpretation>
	<quick-interpretation>org.observe.quick.swing.QuickBaseSwing</quick-interpretation>
	<quick-interpretation>org.observe.quick.swing.QuickXSwing</quick-interpretation>
</quick-app>

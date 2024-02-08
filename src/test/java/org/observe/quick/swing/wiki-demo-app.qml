<?xml version="1.0" encoding="UTF-8"?>

<!--
  Reference to the Quick-App Qonfig toolkit and the Quick file defining the application to run.
  The Quick file is in the same directory as this file, so no path prefix is needed.
-->
<quick-app xmlns:app="Quick-App v0.1" app-file="wiki-demo.qml">
  <!-- References Qonfig toolkits used by the application -->
  <toolkit def="/org/qommons/config/simple-qonfig-reference.qtd" />
  <toolkit def="/org/observe/expresso/qonfig/expresso-core.qtd">
    <!--
      The Expresso-Core toolkit defines the "expression" external type,
      so we need to specify its implementation
    -->
    <value-type>org.observe.expresso.qonfig.ExpressionValueType</value-type>
  </toolkit>
  <toolkit def="/org/observe/expresso/qonfig/expresso-base.qtd" />
  <toolkit def="/org/observe/quick/style/quick-style.qtd" />
  <toolkit def="/org/observe/quick/quick-core.qtd" />
  <toolkit def="/org/observe/quick/base/quick-base.qtd" />
  <!-- Default Quick classes all use the same session implementation designed for Expresso -->
  <special-session>org.observe.expresso.qonfig.ExpressoSessionImplV0_1</special-session>
  <!-- Generally, one Qonfig interpretation per toolkit is needed -->
  <interpretation>org.observe.expresso.qonfig.ExpressoBaseV0_1</interpretation>
  <interpretation>org.observe.quick.style.QuickStyleInterpretation</interpretation>
  <interpretation>org.observe.quick.QuickCoreInterpretation</interpretation>
  <interpretation>org.observe.quick.base.QuickBaseInterpretation</interpretation>
  <!--
    These classes interpret the Quick data structures as application behaviors:
    in this case, widgets in a Swing UI
  -->
  <quick-interpretation>org.observe.quick.swing.QuickCoreSwing</quick-interpretation>
  <quick-interpretation>org.observe.quick.swing.QuickBaseSwing</quick-interpretation>
</quick-app>

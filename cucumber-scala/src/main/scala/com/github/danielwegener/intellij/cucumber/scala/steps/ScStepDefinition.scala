package com.github.danielwegener.intellij.cucumber.scala.steps

import java.util

import com.github.danielwegener.intellij.cucumber.scala.ScCucumberUtil
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.pom.PomNamedTarget
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScFunctionExpr, ScMethodCall}

import scala.collection.JavaConverters._

class ScStepDefinition(scMethod: ScMethodCall) extends AbstractStepDefinition(scMethod) with PomNamedTarget {

  override def getVariableNames: util.List[String] = {
    // WHEN("""regexp""") { (arg0:Int, arg1:String) <-- we want to match these

    val params = for {
      block <- scMethod.args.exprs.collectFirst({ case b: ScBlockExpr => b })
      function <- block.getChildren.collectFirst({ case f: ScFunctionExpr => f })

      parameters = function.parameters
    } yield parameters.map(_.getName())

    seqAsJavaList(params.getOrElse(Seq.empty))
  }

  @Nullable
  override def getCucumberRegexFromElement(element: PsiElement): String = {
    element match {
      case mc: ScMethodCall => ScCucumberUtil.getStepName(mc).orNull
      case _ => null
    }
  }

  override def getName: String = getCucumberRegex

  override def isValid: Boolean = {
    Option(getElement).exists(_.isValid)
  }

  override def navigate(requestFocus: Boolean): Unit = {
    Option(EditSourceUtil.getDescriptor(getElement)).foreach(_.navigate(requestFocus))
  }

  override def canNavigate: Boolean = EditSourceUtil.canNavigate(getElement)

  override def canNavigateToSource: Boolean = canNavigate()
}

object ScStepDefinition {
  val LOG: Logger = Logger.getInstance(classOf[ScStepDefinition])

  def apply(scMethodCall: ScMethodCall): ScStepDefinition = new ScStepDefinition(scMethodCall)
}
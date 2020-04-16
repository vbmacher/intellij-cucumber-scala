package com.github.danielwegener.intellij.cucumber.scala.steps

import java.util
import java.util.Collections

import com.github.danielwegener.intellij.cucumber.scala.ScalaUtils
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall

object ScalaStepDefinition {
  val LOG: Logger = Logger.getInstance(classOf[ScalaStepDefinition])

  def apply(scMethodCall: ScMethodCall): ScalaStepDefinition = new ScalaStepDefinition(scMethodCall)
}

class ScalaStepDefinition(scMethod: ScMethodCall) extends AbstractStepDefinition(scMethod) {
  import ScalaStepDefinition._

  override def getVariableNames: util.List[String] = {
    val r = for {
      // WHEN("""regexp""") { (arg0:Int, arg1:String) <-- we want to match these
      arg <- scMethod.args.exprs
      parentheses <- arg.getChildren
    } yield (arg, parentheses)

    LOG.info(r.toString)
    Collections.emptyList()
  }

  @Nullable
  override def getCucumberRegexFromElement(element: PsiElement): String = {
    element match {
      case mc: ScMethodCall => ScalaUtils.getStepName(mc).orNull
      case _ => null
    }
  }
}

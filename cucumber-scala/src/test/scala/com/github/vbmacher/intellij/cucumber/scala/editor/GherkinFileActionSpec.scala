package com.github.vbmacher.intellij.cucumber.scala.editor

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberSpecBase
import com.github.vbmacher.intellij.cucumber.scala.creation.BatchStepCreationAction
import com.github.vbmacher.intellij.cucumber.scala.publish.ExportFeaturesAction
import com.intellij.openapi.actionSystem.{ActionPlaces, ActionUpdateThread, AnActionEvent, CommonDataKeys, DataContext}
import com.intellij.openapi.application.{ApplicationManager, ReadAction}
import com.intellij.testFramework.PlatformTestUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.util.concurrent.Callable

@RunWith(classOf[JUnit4])
class GherkinFileActionSpec extends ScCucumberSpecBase {
  @Test
  def testFeatureActionsUpdateOnBackgroundThreadAndOnlyAppearForFeatures(): Unit = {
    val feature = myFixture.configureByText("actions.feature", "Feature: Actions\n  Scenario: Visible\n    Given setup\n")
    val plain = myFixture.addFileToProject("plain.txt", "Plain text")
    val actions = Seq(new BatchStepCreationAction, new NumberScenariosAction, new GoToHooksAction, new ExportFeaturesAction)
    actions.foreach { action =>
      action.getActionUpdateThread shouldBe ActionUpdateThread.BGT
      Seq(feature -> true, plain -> false, (null, false)).foreach { case (file, expected) =>
        val update = ApplicationManager.getApplication.executeOnPooledThread(new Callable[Boolean] {
          override def call(): Boolean = ReadAction.compute[Boolean, RuntimeException](() => {
            val context: DataContext = key => if (CommonDataKeys.PSI_FILE.is(key)) {
              ApplicationManager.getApplication.isDispatchThread shouldBe false
              file
            } else null
            val event = AnActionEvent.createFromDataContext(ActionPlaces.EDITOR_POPUP, null, context)
            action.update(event)
            event.getPresentation.isEnabledAndVisible
          })
        })
        PlatformTestUtil.waitWithEventsDispatching("Feature action update", () => update.isDone, 10)
        update.get() shouldBe expected
      }
    }
  }
}

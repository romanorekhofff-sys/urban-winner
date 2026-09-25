package dev.attention.app
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import org.junit.Before
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=AttentionApp::class,qualifiers="w400dp-h850dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InterfaceTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Before fun resetState(){val app=ApplicationProvider.getApplicationContext<AttentionApp>();runBlocking(Dispatchers.IO){app.db.clearAllTables();app.settings.save(Preferences())}}
 private fun screenshot(name:String){compose.runOnIdle{val view=compose.activity.window.decorView;val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(image));val file=File(System.getProperty("focus.screenshotDir") ?: "build/screenshots","$name.png");file.parentFile!!.mkdirs();file.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}}}
 private fun waitText(text:String){compose.waitUntil(10000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
 @Test fun onboardingCreateCompleteReopenAndScreens(){
  waitText("Далее");compose.onNodeWithText("Далее").performClick();compose.onNodeWithText("Начать").performClick()
  compose.waitUntil(10000){compose.onAllNodesWithContentDescription("Добавить задачу").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithContentDescription("Добавить задачу").performClick()
  compose.onNodeWithText("Что нужно сделать?").performTextInput("Проверка интерфейса")
  compose.onNodeWithText("Добавить").performScrollTo().performClick();waitText("Проверка интерфейса");screenshot("tasks")
  compose.onNodeWithText("0%").performClick();compose.onNodeWithText("100").performClick();compose.onNodeWithText("Завершить задачу").performClick()
  compose.onNodeWithText("История").performClick();waitText("Проверка интерфейса");compose.onNodeWithText("Проверка интерфейса").performClick()
  compose.onNodeWithText("75").performScrollTo().performClick();compose.onNodeWithText("Сохранить").performScrollTo().performClick()
  compose.onNodeWithContentDescription("Задачи").performClick();waitText("Проверка интерфейса")
  compose.onNodeWithText("Календарь").performClick();compose.onNodeWithText("Месяц").assertExists();screenshot("calendar")
  compose.onNodeWithText("Аналитика").performClick();waitText("закрытый оценённый объём");compose.onNodeWithText("30 дней").performClick();screenshot("analytics")
  compose.onNodeWithContentDescription("Задачи").performClick();compose.onNodeWithContentDescription("Карта").performClick();compose.onNodeWithText("Карта внимания").assertExists();screenshot("map")
 }
 @Test fun weightedSubtasksAndAvailableTime(){
  val app=ApplicationProvider.getApplicationContext<AttentionApp>()
  val task=runBlocking{val t=Task(title="Комплексная проверка");app.repository.save(t.copy(subtasks=listOf(Subtask(parentTaskId=t.id,title="Исследования",estimatedEffortMinutes=15),Subtask(parentTaskId=t.id,title="Слайды",estimatedEffortMinutes=45))))}
  waitText("Далее");compose.onNodeWithText("Далее").performClick();compose.onNodeWithText("Начать").performClick();waitText("Комплексная проверка")
  compose.onNodeWithText("Комплексная проверка").performClick()
  compose.onNodeWithContentDescription("Подзадача Исследования").performScrollTo().performClick();waitText("25%");screenshot("subtasks")
  compose.onNodeWithContentDescription("Подзадача Слайды").performScrollTo().performClick()
  compose.waitUntil(10000){runBlocking{app.db.work().row(task.id)!!.task.done}}
  compose.onNodeWithContentDescription("История").performClick();waitText("Комплексная проверка");compose.onNodeWithText("Комплексная проверка").performClick()
  compose.onNodeWithContentDescription("Подзадача Исследования").performScrollTo().performClick();waitText("75%")
  compose.onNodeWithContentDescription("Закрыть").performScrollTo().performClick();compose.onNodeWithContentDescription("Задачи").performClick()
  compose.onNodeWithContentDescription("Есть время").performClick();compose.onNodeWithText("15 мин").performClick();compose.onNodeWithText("Показать подходящие").performClick();waitText("Комплексная проверка")
  assertEquals(15,runBlocking{EffortLogic.remaining(app.db.work().row(task.id)!!.full()).minutes})
  compose.onNodeWithContentDescription("Аналитика").performClick();waitText("45 мин");screenshot("analytics-work")
 }

}

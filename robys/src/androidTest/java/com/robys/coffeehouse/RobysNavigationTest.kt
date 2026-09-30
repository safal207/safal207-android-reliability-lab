package com.robys.coffeehouse

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native UI contracts; browser Intents are intercepted, so no external network is required. */
@RunWith(AndroidJUnit4::class)
class RobysNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun tabsAndHomeActionNavigateBetweenNativeScreens() {
        tab("Ana Sayfa").assertIsSelected()
        compose.onNodeWithText("İyi kahve.").assertIsDisplayed()
        saveScreenshot("home.png")

        compose.onNodeWithTag("homeScroll")
            .performScrollToNode(hasText("Tatları keşfet"))
        compose.onNode(hasText("Tatları keşfet") and hasClickAction()).performClick()
        tab("Keşfet").assertIsSelected()
        compose.onNodeWithText("Kendi tadını keşfet.").assertIsDisplayed()
        saveScreenshot("discover.png")

        tab("Ziyaret").performClick().assertIsSelected()
        compose.onNodeWithText("BİZE UĞRAYIN").assertIsDisplayed()
        saveScreenshot("visit.png")

        tab("Ana Sayfa").performClick().assertIsSelected()
        compose.onNodeWithText("İyi kahve.").assertIsDisplayed()
    }

    @Test
    fun categorySelectionFiltersSamples() {
        tab("Keşfet").performClick()
        selectDessert()

        compose.onNodeWithText("Tatlı").assertIsSelected()
        compose.onNodeWithText("Tümü").assertIsNotSelected()
        compose.onNodeWithTag("discoverScroll")
            .performScrollToNode(hasText("San Sebastian"))
        compose.onNodeWithText("San Sebastian").assertIsDisplayed()
        compose.onNodeWithText("Latte").assertDoesNotExist()
        compose.onNodeWithText("Iced Latte").assertDoesNotExist()
        compose.onNodeWithText("Kruvasan").assertDoesNotExist()
    }

    @Test
    fun selectedTabAndCategorySurviveActivityRecreation() {
        tab("Keşfet").performClick()
        selectDessert()

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        tab("Keşfet").assertIsSelected()
        compose.onNodeWithTag("discoverScroll")
            .performScrollToNode(hasText("Tatlı"))
        compose.onNodeWithText("Tatlı").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("discoverScroll")
            .performScrollToNode(hasText("San Sebastian"))
        compose.onNodeWithText("San Sebastian").assertIsDisplayed()
        compose.onNodeWithText("Latte").assertDoesNotExist()
    }

    @Test
    fun visitLastActionRemainsReachableInLandscape() {
        val initialOrientation = compose.activity.requestedOrientation
        try {
            tab("Ziyaret").performClick()
            compose.activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            compose.waitForIdle()

            tab("Ziyaret").assertIsSelected()
            compose.onNodeWithText("Instagram topluluğuna katıl")
                .performScrollTo().assertIsDisplayed()
            tab("Ana Sayfa").assertIsDisplayed()
            tab("Keşfet").assertIsDisplayed()
            saveScreenshot("visit-landscape-last-action.png")
        } finally {
            compose.activityRule.scenario.onActivity {
                it.requestedOrientation = initialOrientation
            }
        }
    }

    @Test
    fun discoverActionOpensExactExternalUrl() {
        Intents.init()
        try {
            // Stub every browser launch; a wrong URL still fails the exact assertion below.
            Intents.intending(hasAction(Intent.ACTION_VIEW))
                .respondWith(ActivityResult(Activity.RESULT_OK, null))

            tab("Keşfet").performClick()
            compose.onNodeWithTag("discoverScroll")
                .performScrollToNode(hasText("Tatları keşfet"))
            compose.onNode(hasText("Tatları keşfet") and hasClickAction()).performClick()

            Intents.intended(
                allOf(
                    hasAction(Intent.ACTION_VIEW),
                    hasData("https://safal207.github.io/robys-coffee-house-demo/discover.html")
                )
            )
            tab("Keşfet").assertIsSelected()
        } finally {
            Intents.release()
        }
    }

    private fun tab(label: String) = compose.onNodeWithContentDescription(label)

    private fun selectDessert() {
        compose.onNodeWithTag("discoverScroll").performScrollToNode(hasText("Tatlı"))
        compose.onNodeWithText("Tatlı").performScrollTo().performClick()
    }

    private fun saveScreenshot(name: String) {
        val externalFiles = requireNotNull(compose.activity.getExternalFilesDir(null)) {
            "App external files directory is unavailable"
        }
        val directory = File(externalFiles, "runtime-evidence")
        assertTrue("Unable to create screenshot directory", directory.isDirectory || directory.mkdirs())
        File(directory, name).outputStream().use { output ->
            assertTrue(
                "Unable to encode screenshot $name",
                compose.onRoot().captureToImage().asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, output)
            )
        }
    }
}

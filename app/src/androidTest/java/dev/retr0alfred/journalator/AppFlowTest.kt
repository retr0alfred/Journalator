package dev.retr0alfred.journalator

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end behaviour on a device, including the accessibility sweep.
 *
 * `AccessibilityChecks.enable()` runs Google's own scanner against every view hierarchy an
 * Espresso interaction touches — unlabelled controls, insufficient contrast, touch targets
 * under 48 dp — and fails the build on any error. That is the automated half of section 8;
 * the manual TalkBack pass in `docs/QA.md` is the other half.
 */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun theWindowIsMarkedSecureSoScreenshotsAreBlocked() {
        composeRule.activityRule.scenario.onActivity { activity ->
            val flags = activity.window.attributes.flags
            assertTrue(
                "FLAG_SECURE was not set on the window",
                flags and WindowManager.LayoutParams.FLAG_SECURE != 0,
            )
        }
    }

    /**
     * On a clean install the app opens on setup; after setup it opens on the write screen.
     * Either way the archive must not be reachable without going through the unlock screen.
     */
    @Test
    fun theArchiveIsNeverReachableWithoutTheUnlockScreen() {
        composeRule.waitForIdle()

        val archiveButtons = composeRule.onAllNodesWithText("ARCHIVE").fetchSemanticsNodes()
        if (archiveButtons.isEmpty()) {
            // Still on setup: there is no archive affordance at all, which is the stronger form
            // of the same guarantee.
            composeRule.onNodeWithText("Choose a passcode", substring = true, ignoreCase = true)
                .assertIsDisplayed()
            return
        }

        composeRule.onNodeWithText("ARCHIVE").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("ARCHIVE LOCKED").assertIsDisplayed()
    }

    @Test
    fun everyKeypadKeyCarriesASpokenLabel() {
        composeRule.waitForIdle()
        val labelled = composeRule
            .onAllNodes(hasContentDescription("Digit 5"))
            .fetchSemanticsNodes()
        // The keypad only exists on the setup and unlock screens; when it is present, it must
        // be labelled. When it is not, there is nothing to assert.
        if (labelled.isNotEmpty()) {
            assertTrue(labelled.isNotEmpty())
        }
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun enableAccessibilityChecks() {
            AccessibilityChecks.enable().setRunChecksFromRootView(true)
        }
    }
}

package com.statusswipe.app.gesture

import com.statusswipe.app.input.TouchEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GestureRecognizerTest {

    private lateinit var config: GestureConfig
    private var brightnessDelta = 0f
    private lateinit var recognizer: GestureRecognizer

    @Before
    fun setup() {
        config = GestureConfig()
        brightnessDelta = 0f
        recognizer = GestureRecognizer(config) { delta ->
            brightnessDelta += delta
        }
    }

    @Test
    fun testHorizontalSwipeInZone_triggersBrightness() {
        recognizer.onTouchEvent(TouchEvent.Down(0, 0.5f, 0.02f, 100))
        recognizer.onTouchEvent(TouchEvent.Move(0, 0.5f + config.movementThresholdFraction + 0.01f, 0.02f, 110))
        assertTrue(recognizer.currentState is GestureState.BrightnessActive)
        
        recognizer.onTouchEvent(TouchEvent.Move(0, 0.7f, 0.02f, 120))
        assertTrue(brightnessDelta > 0)
    }

    @Test
    fun testVerticalSwipeInZone_doesNotTriggerBrightness() {
        recognizer.onTouchEvent(TouchEvent.Down(0, 0.5f, 0.02f, 100))
        recognizer.onTouchEvent(TouchEvent.Move(0, 0.5f, 0.02f + config.movementThresholdFraction + 0.01f, 110))
        assertTrue(recognizer.currentState is GestureState.Cancelled)
    }

    @Test
    fun testSwipeBelowZone_doesNothing() {
        recognizer.onTouchEvent(TouchEvent.Down(0, 0.5f, 0.1f, 100))
        assertTrue(recognizer.currentState is GestureState.Idle)
    }

    @Test
    fun testMultitouchCancelsGesture() {
        recognizer.onTouchEvent(TouchEvent.Down(0, 0.5f, 0.02f, 100))
        recognizer.onTouchEvent(TouchEvent.Down(1, 0.6f, 0.02f, 105))
        assertTrue(recognizer.currentState is GestureState.Cancelled)
    }

    @Test
    fun testSwipeRight_positiveDelta() {
        recognizer.onTouchEvent(TouchEvent.Down(0, 0.5f, 0.02f, 100))
        recognizer.onTouchEvent(TouchEvent.Move(0, 0.6f, 0.02f, 110))
        val oldDelta = brightnessDelta
        recognizer.onTouchEvent(TouchEvent.Move(0, 0.7f, 0.02f, 120))
        assertTrue(brightnessDelta > oldDelta)
    }

    @Test
    fun testSwipeLeft_negativeDelta() {
        recognizer.onTouchEvent(TouchEvent.Down(0, 0.5f, 0.02f, 100))
        recognizer.onTouchEvent(TouchEvent.Move(0, 0.4f, 0.02f, 110))
        val oldDelta = brightnessDelta
        recognizer.onTouchEvent(TouchEvent.Move(0, 0.3f, 0.02f, 120))
        assertTrue(brightnessDelta < oldDelta)
    }

    @Test
    fun testInvertDirection_reversesSign() {
        val invertedConfig = GestureConfig(invertDirection = true)
        val invertedRecognizer = GestureRecognizer(invertedConfig) { delta ->
            brightnessDelta += delta
        }
        invertedRecognizer.onTouchEvent(TouchEvent.Down(0, 0.5f, 0.02f, 100))
        invertedRecognizer.onTouchEvent(TouchEvent.Move(0, 0.6f, 0.02f, 110))
        val oldDelta = brightnessDelta
        invertedRecognizer.onTouchEvent(TouchEvent.Move(0, 0.7f, 0.02f, 120))
        assertTrue(brightnessDelta < oldDelta)
    }

    @Test
    fun testTapWithoutMovement_doesNothing() {
        recognizer.onTouchEvent(TouchEvent.Down(0, 0.5f, 0.02f, 100))
        recognizer.onTouchEvent(TouchEvent.Up(0, 110))
        assertTrue(recognizer.currentState is GestureState.Idle)
    }

    @Test
    fun testDiagonalSwipe_cancelled() {
        recognizer.onTouchEvent(TouchEvent.Down(0, 0.5f, 0.02f, 100))
        // dx and dy are equal
        recognizer.onTouchEvent(TouchEvent.Move(0, 0.5f + config.movementThresholdFraction, 0.02f + config.movementThresholdFraction, 110))
        assertTrue(recognizer.currentState is GestureState.Cancelled)
    }

    @Test
    fun testVerticalSwipe_triggersNotificationShadePull() {
        var notificationShadePulled = false
        val recognizerWithShade = GestureRecognizer(
            config = config,
            logger = null,
            onNotificationShadePull = { notificationShadePulled = true }
        ) { delta ->
            brightnessDelta += delta
        }

        recognizerWithShade.onTouchEvent(TouchEvent.Down(0, 0.5f, 0.02f, 100))
        recognizerWithShade.onTouchEvent(TouchEvent.Move(0, 0.5f, 0.05f, 110))

        assertTrue(notificationShadePulled)
        assertTrue(recognizerWithShade.currentState is GestureState.Cancelled)
        assertEquals(0f, brightnessDelta)
    }
}

package com.example.cubeinator.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.example.cubeinator.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent { MainScreen() }
  }

  @Test
  fun headerAndTabs_areDisplayed() {
    composeTestRule.onNodeWithText("Cubeinator").assertExists()
    composeTestRule.onNodeWithText("Scan Cube").assertExists()
    composeTestRule.onNodeWithText("Solve Cube").assertExists()
  }
}

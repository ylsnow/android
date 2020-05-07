/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.emulator

import org.junit.rules.ExternalResource

/**
 * Test rule that overrides the default configuration for the duration of every test.
 */
class OverriddenConfigurationRule(private val configuration: RuntimeConfiguration) : ExternalResource() {

  override fun before() {
    RuntimeConfigurationOverrider.overrideConfiguration(configuration)
  }

  override fun after() {
    RuntimeConfigurationOverrider.clearOverride()
  }
}
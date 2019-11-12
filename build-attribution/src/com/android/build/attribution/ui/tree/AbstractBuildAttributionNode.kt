/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.ui.tree

import com.android.build.attribution.ui.panels.AbstractBuildAttributionInfoPanel
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.CachingSimpleNode
import com.intellij.ui.treeStructure.SimpleNode
import javax.swing.Icon
import javax.swing.JComponent

abstract class AbstractBuildAttributionNode protected constructor(
  aParent: SimpleNode,
  val nodeName: String
) : CachingSimpleNode(aParent), ComponentContainer {

  val nodeId: String = if (aParent is AbstractBuildAttributionNode) "${aParent.nodeId} > $nodeName" else nodeName

  abstract val presentationIcon: Icon?
  abstract val issuesCountsSuffix: String?
  abstract val timeSuffix: String?

  override fun dispose() = Unit

  override fun getPreferredFocusableComponent(): JComponent {
    return component
  }

  override fun createPresentation(): PresentationData {
    val presentation = super.createPresentation()

    if (presentationIcon != null) presentation.setIcon(presentationIcon)

    presentation.addText(" $nodeName", SimpleTextAttributes.REGULAR_ATTRIBUTES)

    if (issuesCountsSuffix != null) presentation.addText(" ${issuesCountsSuffix}", SimpleTextAttributes.GRAYED_ATTRIBUTES)

    return presentation
  }

  private var cachedComponent: JComponent? = null
    get() {
      if (field == null) {
        field = createComponent().init()
      }
      return field
    }

  final override fun getComponent(): JComponent {
    return cachedComponent!!
  }

  abstract fun createComponent(): AbstractBuildAttributionInfoPanel
}
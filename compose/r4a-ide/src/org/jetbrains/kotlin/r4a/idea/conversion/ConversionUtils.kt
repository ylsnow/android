/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.r4a.idea.conversion

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.r4a.R4aUtils

fun createFunctionalComponent(name: String, composeBody: String, imports: MutableSet<FqName>): String {
    // TODO(jdemeulenaere): Allow to specify package.
    // We don't use org.jetbrains.kotlin.j2k.ast.Function because it requires a j2k.Converter instance to create a DeferredElement
    // (the type of the Function body).
    // TODO(jdemeulenaere): Also import composer?

    imports.add(R4aUtils.r4aFqName("Composable"))
    return """
            |@Composable
            |fun $name() {
            |    $composeBody
            |}""".trimMargin()
}
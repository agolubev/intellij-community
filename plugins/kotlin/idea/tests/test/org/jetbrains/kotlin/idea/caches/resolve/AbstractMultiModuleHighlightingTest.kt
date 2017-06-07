/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.codegen.forTestCompile.ForTestCompileRuntime
import org.jetbrains.kotlin.config.TargetPlatformKind
import org.jetbrains.kotlin.idea.stubs.AbstractMultiHighlightingTest

abstract class AbstractMultiModuleHighlightingTest : AbstractMultiHighlightingTest() {

    protected fun checkHighlightingInAllFiles(
            shouldCheckFile: () -> Boolean = { !file.text.contains("// !CHECK_HIGHLIGHTING") }
    ) {
        checkFiles(shouldCheckFile) {
            checkHighlighting(myEditor, true, false)
        }
    }

    protected fun doMultiPlatformTest(
            vararg platforms: TargetPlatformKind<*>,
            withStdlibCommon: Boolean = false,
            configureModule: (Module, TargetPlatformKind<*>) -> Unit = { _, _ -> },
            useFullJdk: Boolean = false
    ) {
        val commonModule = module("common", useFullJdk = useFullJdk)
        commonModule.createFacet(TargetPlatformKind.Common)
        if (withStdlibCommon) {
            commonModule.addLibrary(ForTestCompileRuntime.stdlibCommonForTests())
        }

        for (platform in platforms) {
            val path = when (platform) {
                is TargetPlatformKind.Jvm -> "jvm"
                is TargetPlatformKind.JavaScript -> "js"
                else -> error("Unsupported platform: $platform")
            }
            val platformModule = module(path, useFullJdk = useFullJdk)
            platformModule.createFacet(platform)
            platformModule.enableMultiPlatform()
            platformModule.addDependency(commonModule)
            configureModule(platformModule, platform)
        }

        checkHighlightingInAllFiles()
    }
}

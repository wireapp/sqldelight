/*
 * Modifications Copyright (C) 2026 Wire GmbH
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

package app.cash.sqldelight.tests

import app.cash.sqldelight.gradle.SqlDelightCompilationUnitImpl
import app.cash.sqldelight.gradle.SqlDelightDatabasePropertiesImpl
import app.cash.sqldelight.gradle.SqlDelightSourceFolderImpl
import app.cash.sqldelight.withTemporaryFixture
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class CompilationUnitTests {
  @Test
  fun `JVM kotlin`() {
    withTemporaryFixture {
      gradleFile(
        """
        |plugins {
        |  alias(libs.plugins.kotlin.jvm)
        |  alias(libs.plugins.sqldelight)
        |}
        |
        |sqldelight {
        |  databases {
        |    CommonDb {
        |      packageName = "com.sample"
        |    }
        |  }
        |}
        """.trimMargin(),
      )

      properties().let { properties ->
        assertThat(properties.databases).hasSize(1)

        val database = properties.databases[0]
        assertThat(database.className).isEqualTo("CommonDb")
        assertThat(database.packageName).isEqualTo("com.sample")
        assertThat(database.compilationUnits).containsExactly(
          SqlDelightCompilationUnitImpl(
            name = "main",
            sourceFolders = setOf(SqlDelightSourceFolderImpl(File(fixtureRoot, "src/main/sqldelight"), false)),
            outputDirectoryFile = File(fixtureRoot, "build/generated/sqldelight/code/CommonDb/main"),
          ),
        )
      }
    }
  }

  @Test
  fun `JVM kotlin with multiple databases`() {
    withTemporaryFixture {
      gradleFile(
        """
        |plugins {
        |  alias(libs.plugins.kotlin.jvm)
        |  alias(libs.plugins.sqldelight)
        |}
        |
        |sqldelight {
        |  databases {
        |    CommonDb {
        |      packageName = "com.sample"
        |    }
        |
        |    OtherDb {
        |      packageName = "com.sample.otherdb"
        |      srcDirs('src/main/sqldelight', 'src/main/otherdb')
        |      treatNullAsUnknownForEquality = true
        |    }
        |  }
        |}
        """.trimMargin(),
      )

      properties().let { properties ->
        assertThat(properties.databases).containsExactly(
          SqlDelightDatabasePropertiesImpl(
            className = "CommonDb",
            packageName = "com.sample",
            compilationUnits = listOf(
              SqlDelightCompilationUnitImpl(
                name = "main",
                sourceFolders = setOf(SqlDelightSourceFolderImpl(File(fixtureRoot, "src/main/sqldelight"), false)),
                outputDirectoryFile = File(fixtureRoot, "build/generated/sqldelight/code/CommonDb/main"),
              ),
            ),
            dependencies = emptyList(),
            rootDirectory = fixtureRoot,
          ),
          SqlDelightDatabasePropertiesImpl(
            className = "OtherDb",
            packageName = "com.sample.otherdb",
            compilationUnits = listOf(
              SqlDelightCompilationUnitImpl(
                name = "main",
                sourceFolders = setOf(
                  SqlDelightSourceFolderImpl(File(fixtureRoot, "src/main/otherdb"), false),
                  SqlDelightSourceFolderImpl(File(fixtureRoot, "src/main/sqldelight"), false),
                ),
                outputDirectoryFile = File(fixtureRoot, "build/generated/sqldelight/code/OtherDb/main"),
              ),
            ),
            dependencies = emptyList(),
            rootDirectory = fixtureRoot,
            treatNullAsUnknownForEquality = true,
          ),
        )
      }
    }
  }

  @Test
  fun `Multiplatform project with multiple targets`() {
    withTemporaryFixture {
      gradleFile(
        """
        |plugins {
        |  alias(libs.plugins.kotlin.multiplatform)
        |  alias(libs.plugins.sqldelight)
        |}
        |
        |sqldelight {
        |  databases {
        |    CommonDb {
        |      packageName = "com.sample"
        |    }
        |  }
        |}
        |
        |kotlin {
        |  iosX64()
        |  iosArm64()
        |  macosArm64()
        |  macosX64()
        |  js().nodejs()
        |  jvm()
        |}
        """.trimMargin(),
      )

      properties().let { properties ->
        assertThat(properties.databases).hasSize(1)

        val database = properties.databases[0]
        assertThat(database.className).isEqualTo("CommonDb")
        assertThat(database.packageName).isEqualTo("com.sample")
        assertThat(database.compilationUnits).containsExactly(
          SqlDelightCompilationUnitImpl(
            name = "commonMain",
            sourceFolders = setOf(SqlDelightSourceFolderImpl(File(fixtureRoot, "src/commonMain/sqldelight"), false)),
            outputDirectoryFile = File(fixtureRoot, "build/generated/sqldelight/code/CommonDb/commonMain"),
          ),
        )
      }
    }
  }

  @Test
  fun `Multiplatform project with android and ios targets AGP 8`() {
    withTemporaryFixture {
      gradleFile(
        """
        |plugins {
        |  alias(libs.plugins.kotlin.multiplatform)
        |  id("com.android.application").version("8.13.0")
        |  alias(libs.plugins.sqldelight)
        |}
        |
        |sqldelight {
        |  databases {
        |    CommonDb {
        |      packageName = "com.sample"
        |    }
        |  }
        |}
        |
        |android {
        |  namespace = 'com.example.namespace'
        |  compileSdk = libs.versions.compileSdk.get() as int
        |
        |  buildTypes {
        |    release {}
        |    sqldelight {}
        |  }
        |
        |  flavorDimensions "api", "mode"
        |
        |  productFlavors {
        |    demo {
        |      applicationIdSuffix ".demo"
        |      dimension "mode"
        |    }
        |    full {
        |      applicationIdSuffix ".full"
        |      dimension "mode"
        |    }
        |    minApi21 {
        |      dimension "api"
        |    }
        |    minApi23 {
        |      dimension "api"
        |    }
        |  }
        |}
        |
        |kotlin {
        |  androidTarget("androidLib")
        |  iosX64()
        |}
        """.trimMargin(),
      )

      properties().let { properties ->
        assertThat(properties.databases).hasSize(1)

        val database = properties.databases[0]
        assertThat(database.className).isEqualTo("CommonDb")
        assertThat(database.packageName).isEqualTo("com.sample")
        assertThat(database.compilationUnits).containsExactly(
          SqlDelightCompilationUnitImpl(
            name = "commonMain",
            sourceFolders = setOf(SqlDelightSourceFolderImpl(File(fixtureRoot, "src/commonMain/sqldelight"), false)),
            outputDirectoryFile = File(fixtureRoot, "build/generated/sqldelight/code/CommonDb/commonMain"),
          ),
        )
      }
    }
  }

  @Test
  fun `android project with multiple flavors`() {
    withTemporaryFixture {
      gradleFile(
        """
        |plugins {
        |  alias(libs.plugins.android.application)
        |  alias(libs.plugins.sqldelight)
        |}
        |
        |sqldelight {
        |  databases {
        |    CommonDb {
        |      packageName = "com.sample"
        |    }
        |  }
        |}
        |
        |android {
        |  namespace = 'com.example.namespace'
        |  compileSdk = libs.versions.compileSdk.get() as int
        |
        |  buildTypes {
        |    release {}
        |    sqldelight {}
        |  }
        |
        |  flavorDimensions "api", "mode"
        |
        |  productFlavors {
        |    demo {
        |      applicationIdSuffix ".demo"
        |      dimension "mode"
        |    }
        |    full {
        |      applicationIdSuffix ".full"
        |      dimension "mode"
        |    }
        |    minApi21 {
        |      dimension "api"
        |    }
        |    minApi23 {
        |      dimension "api"
        |    }
        |  }
        |}
        """.trimMargin(),
      )

      properties().let { properties ->
        assertThat(properties.databases).hasSize(1)

        val database = properties.databases[0]
        assertThat(database.className).isEqualTo("CommonDb")
        assertThat(database.packageName).isEqualTo("com.sample")
        assertThat(database.compilationUnits).containsExactly(
          SqlDelightCompilationUnitImpl(
            name = "minApi23DemoDebug",
            sourceFolders = setOf(
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/debug/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/demo/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/main/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi23/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi23Demo/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi23DemoDebug/sqldelight"), false),
            ),
            outputDirectoryFile = File(fixtureRoot, "build/generated/sqldelight/code/CommonDb/minApi23DemoDebug"),
          ),
          SqlDelightCompilationUnitImpl(
            name = "minApi23DemoRelease",
            sourceFolders = setOf(
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/demo/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/main/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi23/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi23Demo/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi23DemoRelease/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/release/sqldelight"), false),
            ),
            outputDirectoryFile = File(fixtureRoot, "build/generated/sqldelight/code/CommonDb/minApi23DemoRelease"),
          ),
          SqlDelightCompilationUnitImpl(
            name = "minApi23DemoSqldelight",
            sourceFolders = setOf(
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/demo/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/main/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi23/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi23Demo/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi23DemoSqldelight/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/sqldelight/sqldelight"), false),
            ),
            outputDirectoryFile = File(fixtureRoot, "build/generated/sqldelight/code/CommonDb/minApi23DemoSqldelight"),
          ),
          SqlDelightCompilationUnitImpl(
            name = "minApi23FullDebug",
            sourceFolders = setOf(
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/debug/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/full/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/main/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi23/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi23Full/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi23FullDebug/sqldelight"), false),
            ),
            outputDirectoryFile = File(fixtureRoot, "build/generated/sqldelight/code/CommonDb/minApi23FullDebug"),
          ),
          SqlDelightCompilationUnitImpl(
            name = "minApi23FullRelease",
            sourceFolders = setOf(
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/full/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/main/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi23/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi23Full/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi23FullRelease/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/release/sqldelight"), false),
            ),
            outputDirectoryFile = File(fixtureRoot, "build/generated/sqldelight/code/CommonDb/minApi23FullRelease"),
          ),
          SqlDelightCompilationUnitImpl(
            name = "minApi23FullSqldelight",
            sourceFolders = setOf(
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/full/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/main/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi23/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi23Full/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi23FullSqldelight/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/sqldelight/sqldelight"), false),
            ),
            outputDirectoryFile = File(fixtureRoot, "build/generated/sqldelight/code/CommonDb/minApi23FullSqldelight"),
          ),
          SqlDelightCompilationUnitImpl(
            name = "minApi21DemoDebug",
            sourceFolders = setOf(
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/debug/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/demo/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/main/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi21/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi21Demo/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi21DemoDebug/sqldelight"), false),
            ),
            outputDirectoryFile = File(fixtureRoot, "build/generated/sqldelight/code/CommonDb/minApi21DemoDebug"),
          ),
          SqlDelightCompilationUnitImpl(
            name = "minApi21DemoRelease",
            sourceFolders = setOf(
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/demo/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/main/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi21/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi21Demo/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi21DemoRelease/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/release/sqldelight"), false),
            ),
            outputDirectoryFile = File(fixtureRoot, "build/generated/sqldelight/code/CommonDb/minApi21DemoRelease"),
          ),
          SqlDelightCompilationUnitImpl(
            name = "minApi21DemoSqldelight",
            sourceFolders = setOf(
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/demo/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/main/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi21/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi21Demo/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi21DemoSqldelight/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/sqldelight/sqldelight"), false),
            ),
            outputDirectoryFile = File(fixtureRoot, "build/generated/sqldelight/code/CommonDb/minApi21DemoSqldelight"),
          ),
          SqlDelightCompilationUnitImpl(
            name = "minApi21FullDebug",
            sourceFolders = setOf(
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/debug/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/full/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/main/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi21/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi21Full/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi21FullDebug/sqldelight"), false),
            ),
            outputDirectoryFile = File(fixtureRoot, "build/generated/sqldelight/code/CommonDb/minApi21FullDebug"),
          ),
          SqlDelightCompilationUnitImpl(
            name = "minApi21FullRelease",
            sourceFolders = setOf(
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/full/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/main/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi21/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi21Full/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi21FullRelease/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/release/sqldelight"), false),
            ),
            outputDirectoryFile = File(fixtureRoot, "build/generated/sqldelight/code/CommonDb/minApi21FullRelease"),
          ),
          SqlDelightCompilationUnitImpl(
            name = "minApi21FullSqldelight",
            sourceFolders = setOf(
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/full/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/main/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi21/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi21Full/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/minApi21FullSqldelight/sqldelight"), false),
              SqlDelightSourceFolderImpl(File(fixtureRoot, "src/sqldelight/sqldelight"), false),
            ),
            outputDirectoryFile = File(fixtureRoot, "build/generated/sqldelight/code/CommonDb/minApi21FullSqldelight"),
          ),
        )
      }
    }
  }

  @Test
  fun `custom query keys flag propagation`() {
    withTemporaryFixture {
      gradleFile(
        """
        |plugins {
        |  alias(libs.plugins.kotlin.jvm)
        |  alias(libs.plugins.sqldelight)
        |}
        |
        |sqldelight {
        |  databases {
        |    DefaultDb {
        |      packageName = "com.sample"
        |    }
        |
        |    CustomKeyDb {
        |      packageName = "com.sample.customkeys"
        |      enableCustomQueryKeys = true
        |    }
        |  }
        |}
        """.trimMargin(),
      )

      properties().let { properties ->
        assertThat(properties.databases).hasSize(2)

        val defaultDb = properties.databases.first { it.className == "DefaultDb" }
        val customKeyDb = properties.databases.first { it.className == "CustomKeyDb" }

        // Verify DefaultDb has custom keys disabled (default)
        assertThat(defaultDb.packageName).isEqualTo("com.sample")
        assertThat(defaultDb.enableCustomQueryKeys).isFalse()

        // Verify CustomKeyDb has custom keys enabled
        assertThat(customKeyDb.packageName).isEqualTo("com.sample.customkeys")
        assertThat(customKeyDb.enableCustomQueryKeys).isTrue()
      }
    }
  }
}

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

package app.cash.sqldelight.test.util

import app.cash.sqldelight.core.SqlDelightCompilationUnit
import app.cash.sqldelight.core.SqlDelightDatabaseName
import app.cash.sqldelight.core.SqlDelightDatabaseProperties
import app.cash.sqldelight.core.SqlDelightEnvironment
import app.cash.sqldelight.core.SqlDelightSourceFolder
import app.cash.sqldelight.core.annotators.OptimisticLockCompilerAnnotator
import app.cash.sqldelight.core.lang.MigrationLanguage
import app.cash.sqldelight.core.lang.SqlDelightLanguage
import app.cash.sqldelight.dialect.api.SqlDelightDialect
import app.cash.sqldelight.dialects.sqlite_3_18.SqliteDialect
import com.alecstrong.sql.psi.core.SqlAnnotationHolder
import com.intellij.lang.LanguageParserDefinitions
import java.io.File

internal class TestEnvironment(
  private val outputDirectory: File = File("output"),
  private val deriveSchemaFromMigrations: Boolean = false,
  private val treatNullAsUnknownForEquality: Boolean = false,
  private val dialect: SqlDelightDialect = SqliteDialect(),
  private val generateAsync: Boolean = false,
  private val expandSelectStar: Boolean = true,
  private val enableCustomQueryKeys: Boolean = false,
) {
  fun build(
    root: String,
    annotationHolder: SqlAnnotationHolder,
  ): SqlDelightEnvironment {
    val compilationUnit = object : SqlDelightCompilationUnit {
      override val name = "test"
      override val outputDirectoryFile = outputDirectory
      override val sourceFolders = emptySet<SqlDelightSourceFolder>()
    }
    val environment = SqlDelightEnvironment(
      sourceFolders = listOf(File(root)),
      dependencyFolders = emptyList(),
      properties = object : SqlDelightDatabaseProperties {
        override val packageName = "com.example"
        override val className = "TestDatabase"
        override val dependencies = emptyList<SqlDelightDatabaseName>()
        override val compilationUnits = listOf(compilationUnit)
        override val deriveSchemaFromMigrations = this@TestEnvironment.deriveSchemaFromMigrations
        override val treatNullAsUnknownForEquality = this@TestEnvironment.treatNullAsUnknownForEquality
        override val rootDirectory = File(root)
        override val generateAsync: Boolean = this@TestEnvironment.generateAsync
        override val expandSelectStar: Boolean = this@TestEnvironment.expandSelectStar
        override val enableCustomQueryKeys: Boolean = this@TestEnvironment.enableCustomQueryKeys
      },
      dialect = dialect,
      verifyMigrations = true,
      // hyphen in the name tests that our module name sanitizing works correctly
      moduleName = "test-module",
      compilationUnit = compilationUnit,
    )
    LanguageParserDefinitions.INSTANCE.forLanguage(SqlDelightLanguage).createParser(environment.project)
    LanguageParserDefinitions.INSTANCE.forLanguage(MigrationLanguage).createParser(environment.project)
    environment.annotate(listOf(OptimisticLockCompilerAnnotator()), annotationHolder)
    return environment
  }
}

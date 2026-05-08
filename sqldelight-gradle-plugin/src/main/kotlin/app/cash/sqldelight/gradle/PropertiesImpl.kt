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

package app.cash.sqldelight.gradle

import app.cash.sqldelight.core.SqlDelightCompilationUnit
import app.cash.sqldelight.core.SqlDelightDatabaseName
import app.cash.sqldelight.core.SqlDelightDatabaseProperties
import app.cash.sqldelight.core.SqlDelightPropertiesFile
import app.cash.sqldelight.core.SqlDelightSourceFolder
import java.io.File
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested

data class SqlDelightPropertiesFileImpl(
  override val databases: List<SqlDelightDatabasePropertiesImpl>,
  override val dialectJars: Collection<File>,
  override val minimumSupportedVersion: String,
  override val currentVersion: String,
) : SqlDelightPropertiesFile

data class SqlDelightDatabasePropertiesImpl(
  @Input override val packageName: String,
  @Nested override val compilationUnits: List<SqlDelightCompilationUnitImpl>,
  @Input override val className: String,
  @Nested override val dependencies: List<SqlDelightDatabaseNameImpl>,
  @Input override val deriveSchemaFromMigrations: Boolean = false,
  @Input override val treatNullAsUnknownForEquality: Boolean = false,
  @Input override val generateAsync: Boolean = false,
  @Input override val expandSelectStar: Boolean = true,
  @Input override val enableCustomQueryKeys: Boolean = false,
  // Only used by intellij plugin to help with resolution.
  @Internal override val rootDirectory: File,
) : SqlDelightDatabaseProperties

data class SqlDelightDatabaseNameImpl(
  @Input override val packageName: String,
  @Input override val className: String,
) : SqlDelightDatabaseName

data class SqlDelightCompilationUnitImpl(
  @Input override val name: String,
  @Nested override val sourceFolders: Set<SqlDelightSourceFolderImpl>,
  // Output directory is already cached [SqlDelightTask.outputDirectory].
  @Internal override val outputDirectoryFile: File,
) : SqlDelightCompilationUnit

data class SqlDelightSourceFolderImpl(
  // Sources are already cached [SqlDelightTask.getSources]
  @Internal override val folder: File,
  @Input override val dependency: Boolean = false,
) : SqlDelightSourceFolder

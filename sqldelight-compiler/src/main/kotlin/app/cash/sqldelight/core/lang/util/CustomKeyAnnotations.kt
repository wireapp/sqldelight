/*
 * Copyright (C) 2026 Wire GmbH
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
package app.cash.sqldelight.core.lang.util

import app.cash.sqldelight.core.compiler.model.CustomKeyExpression
import app.cash.sqldelight.core.lang.psi.StmtIdentifierMixin
import com.alecstrong.sql.psi.core.AnnotationException
import com.alecstrong.sql.psi.core.psi.SqlAnnotatedElement
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement

/**
 * Represents a custom key annotation parsed from a SQL comment.
 *
 * Example:
 * ```sql
 * selectConversation:
 * -- @CustomKey conversation_:conversation_id
 * SELECT * FROM Message WHERE conversation_id = :conversation_id;
 * ```
 */
data class CustomKeyAnnotation(
  val annotationType: AnnotationType,
  val expression: CustomKeyExpression,
  val element: PsiElement,
) {
  enum class AnnotationType {
    CUSTOM_KEY, // @CustomKey for queries
    NOTIFY_KEY, // @NotifyCustomKey for mutations
  }
}

/**
 * Extracts custom key annotations from comments preceding a SQL statement.
 *
 * This function looks for comments containing @CustomKey or @NotifyCustomKey annotations
 * and parses them into CustomKeyAnnotation objects.
 * For grouped transaction statements, @NotifyCustomKey may be placed either between the
 * group name and the opening `{`, or at the start of the grouped body before the first SQL
 * statement. In both cases the annotations apply to the whole grouped transaction.
 *
 * @return List of CustomKeyAnnotation objects, in the order they appear in the comments
 */
fun SqlAnnotatedElement.customKeyAnnotations(): List<CustomKeyAnnotation> {
  val annotations = mutableListOf<CustomKeyAnnotation>()

  // Comments before regular statements are siblings of the SqlStmt wrapper.
  parent?.let { annotations += precedingSiblingAnnotations(it) }

  // Comments inside grouped bodies are siblings of the first SqlStmt.
  annotations += precedingSiblingAnnotations(this)

  // Comments before grouped bodies can live inside the grouped identifier node:
  // groupedName
  // -- @NotifyCustomKey key
  // {
  if (this is StmtIdentifierMixin) {
    annotations += annotationsInSubtree(this)
  }

  // Comments immediately after the opening `{` are leading children of the grouped body:
  // groupedName {
  // -- @NotifyCustomKey key
  // UPDATE ...
  annotations += leadingChildAnnotations(this)

  return annotations
    .distinctBy { it.element }
    .sortedBy { it.element.textRange?.startOffset ?: Int.MAX_VALUE }
}

private fun precedingSiblingAnnotations(element: PsiElement): List<CustomKeyAnnotation> {
  val annotations = mutableListOf<CustomKeyAnnotation>()
  var sibling: PsiElement? = element.prevSibling

  while (sibling != null) {
    val isComment = sibling.isSqlComment()

    if (isComment) {
      parseCustomKeyAnnotation(sibling)?.let { annotations.add(it) }
    }

    if (sibling !is PsiWhiteSpace && !isComment) {
      break
    }
    sibling = sibling.prevSibling
  }

  return annotations
}

private fun leadingChildAnnotations(element: PsiElement): List<CustomKeyAnnotation> {
  val annotations = mutableListOf<CustomKeyAnnotation>()
  var child: PsiElement? = element.firstChild

  while (child != null) {
    val isComment = child.isSqlComment()

    if (isComment) {
      parseCustomKeyAnnotation(child)?.let { annotations.add(it) }
    }

    if (child !is PsiWhiteSpace && !isComment) {
      break
    }
    child = child.nextSibling
  }

  return annotations
}

private fun annotationsInSubtree(element: PsiElement): List<CustomKeyAnnotation> {
  val annotations = mutableListOf<CustomKeyAnnotation>()

  fun visit(current: PsiElement) {
    if (current.isSqlComment()) {
      parseCustomKeyAnnotation(current)?.let { annotations.add(it) }
      return
    }

    var child = current.firstChild
    while (child != null) {
      visit(child)
      child = child.nextSibling
    }
  }

  visit(element)
  return annotations
}

private fun PsiElement.isSqlComment(): Boolean {
  return when (this) {
    is PsiComment -> true
    is LeafPsiElement -> text.trimStart().startsWith("--")
    else -> false
  }
}

/**
 * Parses a comment string to extract a CustomKeyAnnotation if present.
 *
 * Expected format:
 * - `-- @CustomKey expression`
 * - `-- @NotifyCustomKey expression`
 *
 * @param commentText The full text of the comment
 * @return CustomKeyAnnotation if a valid annotation is found, null otherwise
 */
private fun parseCustomKeyAnnotation(commentElement: PsiElement): CustomKeyAnnotation? {
  val commentText = commentElement.text
  // Remove comment prefix (-- or /* */)
  val cleaned = commentText
    .replace(Regex("^--\\s*"), "") // Remove leading --
    .replace(Regex("^/\\*\\s*"), "") // Remove leading /*
    .replace(Regex("\\s*\\*/$"), "") // Remove trailing */
    .trim()

  return when {
    cleaned.startsWith("@CustomKey ") -> {
      val expression = cleaned.substringAfter("@CustomKey ").trim()
      if (expression.isNotEmpty()) {
        CustomKeyAnnotation(
          annotationType = CustomKeyAnnotation.AnnotationType.CUSTOM_KEY,
          expression = parseExpression(expression, commentElement),
          element = commentElement,
        )
      } else {
        null
      }
    }
    cleaned.startsWith("@NotifyCustomKey ") -> {
      val expression = cleaned.substringAfter("@NotifyCustomKey ").trim()
      if (expression.isNotEmpty()) {
        CustomKeyAnnotation(
          annotationType = CustomKeyAnnotation.AnnotationType.NOTIFY_KEY,
          expression = parseExpression(expression, commentElement),
          element = commentElement,
        )
      } else {
        null
      }
    }
    else -> null
  }
}

private fun parseExpression(
  expression: String,
  element: PsiElement,
): CustomKeyExpression {
  return try {
    CustomKeyExpression.parse(expression)
  } catch (e: IllegalArgumentException) {
    throw AnnotationException(
      msg = e.message ?: "Invalid custom key expression: $expression",
      element = element,
    )
  }
}

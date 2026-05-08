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
 *
 * @return List of CustomKeyAnnotation objects, in the order they appear in the comments
 */
fun SqlAnnotatedElement.customKeyAnnotations(): List<CustomKeyAnnotation> {
  val annotations = mutableListOf<CustomKeyAnnotation>()

  // Look for annotations in comments preceding the statement
  // Comments are typically siblings of the parent (StatementValidatorMixin)
  // and are represented as LeafPsiElement with text starting with "--"

  // Check parent's siblings first (most common case)
  if (parent != null) {
    var sibling: PsiElement? = parent.prevSibling

    while (sibling != null) {
      // Check if this is a comment (LeafPsiElement or PsiComment with text starting with "--")
      val isComment = when (sibling) {
        is PsiComment -> true
        is LeafPsiElement -> sibling.text.trimStart().startsWith("--")
        else -> false
      }

      if (isComment) {
        parseCustomKeyAnnotation(sibling)?.let { annotations.add(it) }
      }

      // Stop when we hit something that's not a comment or whitespace
      if (sibling !is PsiWhiteSpace && !isComment) {
        break
      }
      sibling = sibling.prevSibling
    }
  }

  // Return annotations in the order they appeared (reverse the list since we walked backwards)
  return annotations.reversed()
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

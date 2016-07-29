/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.api.tools.framework.aspects.documentation.model;

import com.google.api.Page;
import com.google.api.tools.framework.aspects.http.model.RestMethod;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.Location;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.api.tools.framework.model.SymbolTable;
import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringEscapeUtils;

/**
 * Static utilities for dealing with documentation.
 */
public class DocumentationUtil {

  private DocumentationUtil() {}

  /**
   * Matches a word that is not preceeded by a period (so that it does not match a qualified
   * reference to a proto element).
   */
  private static final Pattern WORD = Pattern.compile("(?<!\\.)\\b\\w+\\b");

  /**
   * Get the documentation pages from given model.
   */
  public static List<Page> getToplevelPages(Model model) {
    return model.hasAttribute(DocumentationPagesAttribute.KEY)
        ? model.getAttribute(DocumentationPagesAttribute.KEY).toplevelPages()
        : ImmutableList.<Page>of();
  }

  /**
   * Get the documentation pages scoped to the visibility as currently set in the model.
   */
  public static List<Page> getScopedToplevelPages(Model model) {
    ImmutableList.Builder<Page> scopedPages = ImmutableList.builder();
    for (Page page : getToplevelPages(model)) {
      scopedPages.add(doPageScoping(model, page));
    }
    return scopedPages.build();
  }

  private static Page doPageScoping(Model model, Page page) {
    Page.Builder scopedPage = page.toBuilder().clearSubpages();
    scopedPage.setContent(
        new CommentFilter(model.getDiagCollector(), model.getLocationInConfig(page, "content"),
            model.getVisibilityLabels()).process(page.getContent()));
    for (Page subpage : page.getSubpagesList()) {
      scopedPage.addSubpages(doPageScoping(model, subpage));
    }
    return scopedPage.build();
  }

  /**
   * Given an ProtoElement, returns its associated deprecation description.  Returns the empty
   * string if not available.
   */
  public static String getDeprecationDescription(ProtoElement element) {
    return element.hasAttribute(ElementDocumentationAttribute.KEY)
        ? element.getAttribute(ElementDocumentationAttribute.KEY).deprecationDescription()
        : "";
  }

  /**
   * Given an ProtoElement, returns its associated description.
   * Returns the empty string if no description is available.
   */
  public static String getDescription(ProtoElement element) {
    return getDescription(element, "");
  }

  /**
   * Get the description of the element scoped to the visibility as currently set in the model.
   */
  public static String getScopedDescription(ProtoElement element) {
    return getScopedDescription(element, false);
  }

  public static String getScopedDescription(ProtoElement element, boolean reportWarning) {
    Model model = element.getModel();
    Location location = element.getLocation();
    String internalCommentFilteredString =
        new CommentFilter(model.getDiagCollector(), location, model.getVisibilityLabels())
            .process(getDescription(element));

    return sanitizeTodos(
        model.getDiagCollector(), location, internalCommentFilteredString, reportWarning);
  }

  /**
   * Given a proto element, returns its associated description. Returns {@code defaultText}
   * if no description is available.
   */
  public static String getDescription(ProtoElement element, String defaultText) {
    return element.hasAttribute(ElementDocumentationAttribute.KEY)
        ? element.getAttribute(ElementDocumentationAttribute.KEY).documentation()
        : defaultText;
  }

  /**
   * Given a string, searches for unqualified references to message fields and to method names and
   * converts them from RPC-style to REST-style. Field names are converted from lower_underscore to
   * lowerCamel. Method names are converted from VerbCollection-style to collection.verb style.
   * No work is done for qualified references; in such a case, an explicit markdown link with the
   * proper display text should be used in the proto comment (e.g.,
   * [foo_bar][Path.To.Message.foo_bar], which will be converted to
   * [fooBar][Path.To.Message.foo_bar] by rpcToRest).
   */
  public static String rpcToRest(SymbolTable symbolTable, String description) {
    StringBuffer sb = new StringBuffer();
    Matcher m = WORD.matcher(description);
    while (m.find()) {

      // Convert field names
      if (symbolTable.containsFieldName(m.group(0))) {
        m.appendReplacement(sb, CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, m.group(0)));

      // Convert method names
      } else if (symbolTable.lookupMethodSimpleName(m.group(0)) != null) {
        RestMethod restMethod =
            RestMethod.getPrimaryRestMethod(symbolTable.lookupMethodSimpleName(m.group(0)).get(0));
        if (restMethod == null) {
          m.appendReplacement(sb, m.group(0));
        } else {
          m.appendReplacement(sb, restMethod.getSimpleRestCollectionName() + "."
              + restMethod.getRestMethodName());
        }

      } else {
        m.appendReplacement(sb, m.group(0));
      }
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /**
   * Given a model, returns its associated documentation root url based on documentation and
   * legacy configuration. Returns empty string if not available.
   */
  public static String getDocumentationRootUrl(Model model) {
    if (model.getServiceConfig() == null) {
      return "";
    }
    if (model.getServiceConfig().hasDocumentation() && !Strings.isNullOrEmpty(
        model.getServiceConfig().getDocumentation().getDocumentationRootUrl())) {
      return model.getServiceConfig().getDocumentation().getDocumentationRootUrl();
    }
    return "";
  }

  /**
   * Given a documentation string, escape it such that it can be represented as a JSON string.
   */
  public static String asJsonString(String text) {
    if (text == null) {
      return "";
    }
    return StringEscapeUtils.ESCAPE_JSON.translate(text);
  }

  /**
   * Given a documentation string, replace the cross reference links with reference text.
   */
  public static String removeCrossReference(String text) {
    if (Strings.isNullOrEmpty(text)) {
      return "";
    }
    Pattern pattern = Pattern.compile("\\[(?<name>[^\\]]+?)\\]( |\\n)*"
        + "\\[(?<link>[^\\]]*?)\\]");
    Matcher matcher = pattern.matcher(text);
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      String replacementText = matcher.group("name");
      replacementText = Matcher.quoteReplacement(replacementText);
      matcher.appendReplacement(result, replacementText);
    }
    matcher.appendTail(result);
    return result.toString();
  }

  /**
   * Performs comment filtering. Parse the text to remove non visible comments enclosed by
   * "(-- --)" tags. It works as follows:
   * <ul>
   * <li>All leading and trailing white spaces surrounding tags will be replaced with one space.
   * For example:
   * <pre>
   *   "Foo (-- internal --) foo"
   *   will be returned as
   *   "Foo foo"
   * </pre>
   * Except, if internal comment is at the beginning of the line, no space will be
   * inserted. For example:
   * <pre>
   *   "(-- internal --) foo"
   *   will be returned as
   *   "foo"
   * </pre>
   *
   * <li>Lines with only internal comments (all text enclosed by tags) will be removed.
   * For example:
   * <pre>
   *   "Foo (-- internal --)
   *   (-- internal --) (-- internal2 --)
   *   Foo"
   *   will be returned as
   *   "Foo
   *   Foo"
   * <pre>
   *
   * <li> If visibility label "(--LABEL: ... --) is specified, the comments will be kept if the
   * label is found in the provided visibility labels. If no label was specified, the comments will
   * be filtered.
   * <li> Nested tags are valid, but should appear in pair.
   * <li> It only reports the first encountered error if multiple ones exist.
   * <li> Tags may be escaped with a backslash, for example, "\(-- ... \--)".
   * </ul>
   */
  public static String filter(DiagCollector collector, @Nullable Set<String> visibilityLabels,
      Location location, @Nullable String source) {
    return new CommentFilter(collector, location, visibilityLabels).process(source);
  }

  /**
   * Given a comment string, remove the TODO and all characters found after the TODO. If the input
   * string has no TODO, then the original string is returned. If a TODO was found, then a warning
   * will be triggered telling the user that the TODO comment has been removed and to use internal
   * documentation comment tags to avoid non internal documentation from getting removed from the
   * generated documentation.
   */
  private static String sanitizeTodos(
      DiagCollector diagCollector,
      Location location,
      @Nullable String source,
      boolean reportWarning) {
    if (Strings.isNullOrEmpty(source)) {
      return source;
    }

    String[] sourceSplitByTodo = Pattern.compile("\\bTODO(\\(.*?\\))?:").split(source);

    if (sourceSplitByTodo.length > 1 && reportWarning) {
      diagCollector.addDiag(
          Diag.warning(
              location,
              "A TODO comment was found. All comments from this TODO to the end of the comment "
                  + "block will be removed from the generated documentation. This TODO Comment "
                  + "should be wrapped in internal comment tags, \"(--\" and \"--)\", to prevent "
                  + "non-internal documentation after the TODO from being removed from the "
                  + "generated documentation."));
    }

    String result = sourceSplitByTodo[0];
    // Remove last newline.
    return result.endsWith("\n") ? result.substring(0, result.length() - 1) : result;
  }

  /**
   * Helper class to filter comments.
   */
  private static class CommentFilter {

    private static final String NEW_LINE = "\n";

    private final DiagCollector diagCollector;
    @Nullable private final Set<String> labels;
    private final Location location;

    /**
     * Creates an instance of {@link CommentFilter}
     */
    private CommentFilter(DiagCollector diagCollector, Location location,
        @Nullable Set<String> labels) {
      this.diagCollector = Preconditions.checkNotNull(diagCollector,
          "diagCollector should not be null.");
      this.labels = labels;
      this.location = Preconditions.checkNotNull(location, "location should not be null.");
    }

    public String process(@Nullable String source) {
      if (Strings.isNullOrEmpty(source)) {
        return source;
      }

      CommentTokenizer tokenizer = new CommentTokenizer(source);
      StringBuilder builder = new StringBuilder();
      while (tokenizer.hasNext()) {
        Token token = tokenizer.peekNext();
        switch (token.kind) {
          case BEGIN_INTERNAL_COMMENT:
            if (!handleInternalComment(tokenizer, builder, location)) {
              return source;
            }
            break;
          case TEXT:
            appendText(tokenizer, builder);
            break;
          default:
            collectError(location, token.lineNum,
                "Unexpected end tag '--)' with missing begin tag.");
            return source;
        }
      }
      String result = unescape(builder.toString());
      // Remove last newline.
      return result.endsWith(NEW_LINE) ? result.substring(0, result.length() - 1) : result;
    }

    private String unescape(String source) {
      return source.replace("\\(--", "(--").replace("\\--)", "--)");
    }

    /**
     * Handles internal comments based on visibility label.
     *
     * @param tokenizer tokens of original comment source
     * @param builder the builder that builds processed comment strings to the result
     * @param location the location of the comment source
     * @return true if no error found. Otherwise returns false
     */
    private boolean handleInternalComment(CommentTokenizer tokenizer, StringBuilder builder,
        Location location) {
      Token beginTag = tokenizer.pollNext();
      boolean shouldFilter = Strings.isNullOrEmpty(beginTag.label)
          || labels != null && !labels.contains(beginTag.label);
      while (tokenizer.hasNext()) {
        switch (tokenizer.peekNext().kind) {
          case BEGIN_INTERNAL_COMMENT:
            if (!handleInternalComment(tokenizer, builder, location)) {
              return false;
            }
            break;
          case TEXT:
            if (shouldFilter) {
              skipText(tokenizer);
            } else {
              appendText(tokenizer, builder);
            }
            break;
          default:
            // Found closing tag.
            Token endTag = tokenizer.pollNext();
            // If the internal text should be preserved, and the end tag ends with new line,
            // the new line should be kept.
            if (!shouldFilter && endTag.text.endsWith(NEW_LINE)) {
              builder.append(NEW_LINE);
            }
            return true;
        }
      }

      collectError(location, beginTag.lineNum,
          "Did not find associated end tag for the begin tag '(--'");
      return false;
    }

    /**
     * Consumes consecutive text tokens and append them to builder.
     */
    private void appendText(CommentTokenizer tokenizer, StringBuilder builder) {
      while (tokenizer.hasNext() && tokenizer.peekNext().kind == TokenKind.TEXT) {
        String text = tokenizer.pollNext().text;
        // Append a whitespace, if the position the text to be appended is not the
        // beginning of the line and text to be appended is not newline.
        if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n'
            && !text.equals(NEW_LINE)) {
          builder.append(' ');
        }
        builder.append(text);
      }
    }

    /**
     * Consumes consecutive text tokens.
     */
    private void skipText(CommentTokenizer tokenizer) {
      while (tokenizer.hasNext() && tokenizer.peekNext().kind == TokenKind.TEXT) {
        tokenizer.pollNext();
      }
    }

    /**
     * Collects error messages.
     *
     * @param location the location of the original comment source
     * @param lineNum the line number where the error is detected
     * @param message the message describes the error
     */
    private void collectError(Location location, int lineNum, String message) {
      diagCollector.addDiag(Diag.error(new SimpleLocation(
          String.format("%s (at document line %d)", location.getDisplayString(), lineNum)),
          message));
    }

    /**
     * Tokenizes given comment source by internal comment tags "(--LABEL" and "--)".
     */
    private static class CommentTokenizer {
      private static final Pattern ACL_LABEL = Pattern.compile("[A-Z_]+:");
      private static final Pattern BEGIN_TAG = Pattern.compile(String.format(
          " *(?<!\\\\)\\(--(?<ACL>%s)? *", ACL_LABEL));
      private static final Pattern END_TAG = Pattern.compile(" *(?<!\\\\)--\\) *\\n?");

      private static final Pattern TOKEN = Pattern.compile(String.format(
          "(?<BeginTag>%s)|(?<EndTag>%s)|(\n)", BEGIN_TAG, END_TAG));

      private static final String BEGIN_TAG_GROUP = "BeginTag";
      private static final String ACL_LABEL_GROUP = "ACL";
      private static final String END_TAG_GROUP = "EndTag";

      private final Matcher matcher;
      private final String source;
      private Token currentToken;

      /**
       * The line number of the current token inside the source.
       */
      private int lineNum = 1;

      /**
       * The index of source string.
       */
      private int index = 0;

      /**
       * Creates an instance of {@link CommentTokenizer} for given comment source.
       */
      private CommentTokenizer(String source) {
        this.source = Preconditions.checkNotNull(source, "source should not be null.");
        this.matcher = TOKEN.matcher(source);
        pollNext();
      }

      /**
       * Returns next token without consuming it.
       */
      private Token peekNext() {
        return currentToken;
      }

      /**
       * Determines if it has more tokens.
       */
      private boolean hasNext() {
        return currentToken != null;
      }

      /**
       * Returns next token. The matcher will move forward.
       */
      private Token pollNext() {
        Token result = currentToken;
        if (matcher.find()) {
          if (index < matcher.start()) {
            // There is text between current position and next matching.
            currentToken = new Token(TokenKind.TEXT, source.substring(index, matcher.start()),
                lineNum);
            index = matcher.start();
            matcher.region(index, matcher.regionEnd());
          } else {
            currentToken = createTokenFromMatcher();
            index = matcher.end();
          }
        } else {
          if (index < source.length()) {
            // Add trailing text.
            currentToken = new Token(TokenKind.TEXT, source.substring(index), lineNum);
            index = source.length();
          } else {
            // Reaches the end of source.
            currentToken = null;
          }
        }
        return result;
      }

      private Token createTokenFromMatcher() {
        Token result;
        if (matcher.group(BEGIN_TAG_GROUP) != null) {
          result = new Token(TokenKind.BEGIN_INTERNAL_COMMENT, matcher.group(BEGIN_TAG_GROUP),
              matcher.group(ACL_LABEL_GROUP), lineNum);
        } else if (matcher.group(END_TAG_GROUP) != null) {
          String endTag = matcher.group(END_TAG_GROUP);
          if (endTag.endsWith(NEW_LINE)) {
            lineNum++;
          }
          result = new Token(TokenKind.END_INTERNAL_COMMENT, endTag, lineNum);
        } else {
          // Matches newline.
          result = new Token(TokenKind.TEXT, NEW_LINE, lineNum);
          lineNum++;
        }
        return result;
      }
    }

    /**
     * Represents matched token.
     */
    private static class Token {
      private final TokenKind kind;
      private final String text;
      private final int lineNum;

      /**
       * ACL label name. Could be set for BEGIN_INTERNAL_COMMENT token.
       */
      @Nullable private final String label;

      private Token(TokenKind kind, String text, int lineNum) {
        this(kind, text, null, lineNum);
      }

      private Token(TokenKind kind, String text, @Nullable String label, int lineNum) {
        this.kind = Preconditions.checkNotNull(kind, "kind should not be null.");
        this.text = Preconditions.checkNotNull(text, "text should not be null.");
        this.label = label == null ? null : label.substring(0, label.length() - 1);
        this.lineNum = lineNum;
      }
    }

    /**
     * Represent token kind.
     */
    private static enum TokenKind {
      BEGIN_INTERNAL_COMMENT,
      END_INTERNAL_COMMENT,
      TEXT;
    }
  }
}

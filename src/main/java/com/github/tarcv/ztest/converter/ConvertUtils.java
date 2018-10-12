package com.github.tarcv.ztest.converter;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ConvertUtils {
    private ConvertUtils() {
    }

    static StringBuffer tryParseAndRemove(StringBuffer body, Pattern pattern, Consumer<MatchResult> replacer) {
        return tryReplace(body, pattern, matcher -> {
            replacer.accept(matcher);
            return "";
        });
    }

    static DataPair tryParseAndRemove(DataPair body, Pattern pattern, Consumer<MatchResult> replacer) {
        return tryReplace(body, pattern, matcher -> {
            replacer.accept(matcher);
            return "";
        });
    }

    static StringBuffer tryReplace(StringBuffer body, Pattern pattern, Function<MatchResult, String> replacer) {
        return tryReplace(new DataPair(body), pattern, replacer).getData();
    }

    static DataPair tryReplace(DataPair body, Pattern pattern, Function<MatchResult, String> replacer) {
        StringBuffer leftoutBody = new StringBuffer();
        Matcher bodyMatcher = pattern.matcher(body.dataForRegexes);
        int cursor = 0;
        while (bodyMatcher.find()) {
            MatchResult regexFriendlyResult = bodyMatcher.toMatchResult();
            MatchResult unmaskedResult = new UnmaskedResult(regexFriendlyResult, body.data);

            String replacement = replacer.apply(unmaskedResult);

            leftoutBody
                    .append(body.data.substring(cursor, regexFriendlyResult.start()))
                    .append(replacement);
            cursor = regexFriendlyResult.end();
        }
        leftoutBody
                .append(body.data.substring(cursor));

        return new DataPair(leftoutBody);
    }

    static StringBuffer removeByPattern(StringBuffer data, Pattern lineCommentPattern) {
        StringBuffer out = new StringBuffer();
        Matcher replacer = lineCommentPattern.matcher(data);
        while (replacer.find()) {
            replacer.appendReplacement(out, "");
        }
        replacer.appendTail(out);
        return out;
    }

    private static StringBuffer prepareForRegexes(StringBuffer in) {
        int state = 0;
        int lastRangeStartedAt = 0;
        int bracketLevel = 0;
        StringBuffer out = new StringBuffer(in.length());
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            int newState = state;
            if (state == 0) { // normal
                if (c == '"') {
                    newState = 1;
                } else if (c == '/' && (i + 1 < in.length()) && in.charAt(i + 1) == '*') {
                    ++i; // include comment start characters
                    newState = 2;
                } else if (c == '/' && (i + 1 < in.length()) && in.charAt(i + 1) == '/') {
                    ++i; // include comment start characters
                    newState = 3;
                }
            } else if (state == 1) { // in a string
                if (c == '\\') {
                    ++i; // skip checking next character
                } if (c == '"') {
                    newState = 0;
                }
            } else if (state == 2) { // in a block comment
                if (c == '*' && (i + 1 < in.length()) && in.charAt(i + 1) == '/') {
                    out.append(c); // include comment end characters
                    ++i;
                    c = in.charAt(i + 1);

                    newState = 0;
                }
            } else if (state == 3) { // in a line comment
                if (c == '\r' || c == '\n') {
                    newState = 0;
                }
            }

            if (newState != state) {
                if (state == 0) { // normal tokens ended with the previous char
                    appendRange(in, out, lastRangeStartedAt, i);
                    out.append(c);
                    lastRangeStartedAt = i + 1;
                } else if (newState == 0) {
                    int len = i - lastRangeStartedAt;
                    for (int j = 0; j < len; j++) {
                        out.append('_');
                    }
                    out.append(c);
                    lastRangeStartedAt = i + 1;
                }
            } else if (state == 0) {
                if (c == '{') {
                    if (bracketLevel > 0) {
                        appendRange(in, out, lastRangeStartedAt, i);
                        out.append("_");
                        lastRangeStartedAt = i + 1;
                    }
                    ++bracketLevel;
                } else if (c == '}') {
                    --bracketLevel;
                    if (bracketLevel > 0) {
                        appendRange(in, out, lastRangeStartedAt, i);
                        out.append("_");
                        lastRangeStartedAt = i + 1;
                    }
                }
            }

            state = newState;
        }
        appendRange(in, out, lastRangeStartedAt, in.length());

        return out;
    }

    private static void appendRange(StringBuffer in, StringBuffer out, int inBegin, int inEnd) {
        int len = inEnd - inBegin;
        char[] buffer = new char[len];
        in.getChars(inBegin, inEnd, buffer, 0);
        out.append(buffer);
    }

    static public class DataPair {
        final StringBuffer data;
        final StringBuffer dataForRegexes;

        public DataPair(StringBuffer data) {
            this.data = data;
            this.dataForRegexes = prepareForRegexes(data);
        }

        public DataPair(CharSequence data) {
            this(new StringBuffer(data));
        }

        public StringBuffer getData() {
            return data;
        }
    }

    private static class UnmaskedResult implements MatchResult {

        private final MatchResult regexFriendlyResult;
        private final String origGroup0;

        private UnmaskedResult(MatchResult regexFriendlyResult, StringBuffer origFullData) {
            this.regexFriendlyResult = regexFriendlyResult;
            this.origGroup0 = origFullData.substring(regexFriendlyResult.start(), regexFriendlyResult.end());
        }

        @Override
        public String group(int group) {
            int groupStart = start(group);
            if (groupStart == -1) {
                return null;
            }
            return origGroup0.substring(groupStart - start(), end(group) - start());
        }

        @Override
        public int groupCount() {
            return regexFriendlyResult.groupCount();
        }

        @Override
        public int start(int group) {
            return regexFriendlyResult.start(group);
        }

        @Override
        public int end(int group) {
            return regexFriendlyResult.end(group);
        }

        @Override
        public int start() {
            return start(0);
        }

        @Override
        public int end() {
            return end(0);
        }

        @Override
        public String group() {
            return group(0);
        }
    }
}

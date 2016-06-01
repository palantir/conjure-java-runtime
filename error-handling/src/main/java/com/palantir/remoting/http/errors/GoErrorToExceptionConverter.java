/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.remoting.http.errors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GoErrorToExceptionConverter extends ExceptionConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoErrorToExceptionConverter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern CAUSED_BY_PATTERN = Pattern.compile("Caused by: (.*)");
    private static final Pattern ERROR_LINE_PATTERN = Pattern.compile("--- at ([^:]*):(\\d+) \\(([^\\)]*)\\) ---");

    public static Exception getException(Collection<String> contentTypes, int status, String reason,
            @CheckForNull InputStream body) {
        return getExceptionHelper(contentTypes, status, reason, body, LOGGER, new JsonExceptionCreator() {
            @Override
            public Exception getException(String bodyAsJsonString, int status, String reason) {
                String bodyString;
                try {
                    // Go exception is returned as a JSON String object, so decode it
                    bodyString = MAPPER.readValue(bodyAsJsonString, String.class);
                } catch (Exception e) {
                    String message = String.format(
                            "Error %s. Reason: %s. Failed to parse error body and instantiate exception: %s. Body:%n%s",
                            status, reason, e.getMessage(), bodyAsJsonString);
                    LOGGER.error(message, e);
                    return new RuntimeException(message);
                }

                return getGoStacktraceException(bodyString, status, reason);
            }
        });
    }

    private static RuntimeException getGoStacktraceException(String goStackTrace, int status, String reason) {
        List<RuntimeException> goExceptions = new ArrayList<>();

        List<List<String>> linesByException = groupLinesByException(goStackTrace);
        for (int i = 0; i < linesByException.size(); i++) {
            List<String> currExceptionLines = linesByException.get(i);

            List<StackTraceElement> stackTraceElements = new ArrayList<>();
            boolean firstLineIsCauseLine = !ERROR_LINE_PATTERN.matcher(currExceptionLines.get(0)).find();
            for (int currLineIndex = firstLineIsCauseLine ? 1 : 0; currLineIndex < currExceptionLines.size();
                    currLineIndex++) {
                stackTraceElements.add(getStackTraceElementFromLine(currExceptionLines.get(currLineIndex)));
            }

            String cause = getCauseString(currExceptionLines);
            String message = i == 0 ? getMessageForTopLevelException(cause, status, reason) : cause;

            GoException currGoException = new GoException(message);
            currGoException.setStackTrace(stackTraceElements.toArray(new StackTraceElement[stackTraceElements.size()]));
            goExceptions.add(currGoException);
        }

        chainExceptions(goExceptions);

        return goExceptions.get(0);
    }

    /**
     * Returns the message for the top-level exception. The top-level exception message includes the error code, reason
     * and, if provided, the cause as part of the message.
     */
    private static String getMessageForTopLevelException(String cause, int status, String reason) {
        String message = String.format("Error %s. Reason: %s.", status, reason);
        if (!cause.isEmpty()) {
            message += String.format(" Cause: %s", cause);
        }
        return message;
    }

    /**
     * Returns the cause String for the exception represented by the provided lines. The first line in the exception can
     * be one of 3 possible things:
     *   1. the reason ("Failed to register")
     *   2. the cause ("Caused by: entry not found")
     *   3. an error line ("--- at github.com/... ---")
     *
     * In the first 2 cases, this method will return the String representing the cause (for case 1, the full String;
     * for case 2, the String after "Caused by: "). In the third case, this method returns an empty String.
     */
    private static String getCauseString(List<String> exceptionLines) {
        String causeString = "";
        String firstLine = exceptionLines.get(0);
        boolean firstLineIsCauseLine = !ERROR_LINE_PATTERN.matcher(firstLine).find();
        if (firstLineIsCauseLine) {
            Matcher causedByMatcher = CAUSED_BY_PATTERN.matcher(firstLine);
            if (causedByMatcher.find()) {
                causeString = causedByMatcher.group(1);
            } else {
                causeString = firstLine;
            }
        }
        return causeString;
    }

    /**
     * Chains the exceptions in the given list. Does so by iterating through the list in order and setting the cause of
     * each element to be the exception that follows it. The cause of the final exception in the list is set to be a
     * newly created RuntimeException with the current stack.
     */
    private static void chainExceptions(List<RuntimeException> goExceptions) {
        // chain exceptions to each other
        for (int i = 0; i < goExceptions.size() - 1; i++) {
            goExceptions.get(i).initCause(goExceptions.get(i + 1));
        }

        // chain final exception to a newly created Java exception
        if (!goExceptions.isEmpty()) {
            RuntimeException javaException = new RuntimeException();
            javaException.fillInStackTrace();
            goExceptions.get(goExceptions.size() - 1).initCause(javaException);
        }
    }

    /**
     * Parses the provided String representing a stack trace outputted by Palantir Go stacktrace and returns a list of
     * list of Strings where each inner list represents the set of strings that comprise a single exception. Assumes
     * that the input is delimited by '\n' and is of the following format:
     *
     * {Exception}+
     *
     * where:
     *
     * {Exception} = {Cause}?{SourceLine}*
     * {SourceLine} = "--- at .* ---"
     */
    private static List<List<String>> groupLinesByException(String goStackTrace) {
        List<List<String>> groupedLines = new ArrayList<>();

        List<String> lines = Lists.newArrayList(goStackTrace.split("\\n"));

        int currLineIndex = 0;
        while (currLineIndex < lines.size()) {
            List<String> currExceptionLines = new ArrayList<>();

            // consume first line, which is either an error line or an explanation line
            String currLine = lines.get(currLineIndex);
            currExceptionLines.add(currLine);
            currLineIndex++;

            // add the rest of the error lines for this exception
            while (currLineIndex < lines.size()) {
                currLine = lines.get(currLineIndex);

                if (ERROR_LINE_PATTERN.matcher(currLine).find()) {
                    // if this is an error line, add to current list and increment index
                    currExceptionLines.add(currLine);
                    currLineIndex++;
                } else {
                    // otherwise, done consuming current error -- break out of loop without advancing
                    break;
                }
            }

            groupedLines.add(currExceptionLines);
        }

        return groupedLines;
    }

    /**
     * Parses the provided line and coverts it to a StackTraceElement. The line is assumed to be an error line formatted
     * using the default format of the Palantir Go stacktrace library (matched using ERROR_LINE_PATTERN).
     */
    private static StackTraceElement getStackTraceElementFromLine(String line) {
        Matcher errorLineMatcher = ERROR_LINE_PATTERN.matcher(line);
        if (errorLineMatcher.find()) {
            String fileName = errorLineMatcher.group(1);
            String lineNumber = errorLineMatcher.group(2);
            String receiverAndMethod = errorLineMatcher.group(3);

            String receiver;
            String method;
            if (receiverAndMethod.indexOf('.') != -1) {
                String[] split = receiverAndMethod.split("\\.");
                receiver = split[0];
                method = split[1];
            } else {
                receiver = "<no receiver>";
                method = receiverAndMethod;
            }

            return new StackTraceElement(receiver, method, fileName, Integer.parseInt(lineNumber));
        }

        throw new IllegalArgumentException(
                String.format("provided line %s does not match pattern %s", line, ERROR_LINE_PATTERN));
    }

    private GoErrorToExceptionConverter() {
    }

}

/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.commons.tests.utils;

import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** */
public class FileUtils {

  public static List<String> readLines(Path... files) {
    return readLinesAsStream(files).collect(Collectors.toList());
  }

  private static Stream<String> readLinesAsStream(Path... files) {
    return Arrays.stream(files)
        .flatMap(
            path -> {
              try {
                return Files.lines(path);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  public static String readFile(Path file) throws IOException {
    return readFile(file, StandardCharsets.UTF_8);
  }

  public static String readFile(Path file, Charset charset) throws IOException {
    return Files.readAllLines(file, charset)
        .stream()
        .collect(Collectors.joining(System.lineSeparator()));
  }

  public static Stream<String> readAllLinesInDirectoryAsStream(Path dir, Charset charset)
      throws IOException {
    return Files.walk(dir)
        .filter(Files::isRegularFile)
        .flatMap(
            path -> {
              try {
                return Files.readAllLines(path, charset).stream();
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  public static List<String> readAllLinesInDirectory(Path dir, Charset charset) throws IOException {
    try (Stream<String> lines = readAllLinesInDirectoryAsStream(dir, charset)) {
      return lines.collect(Collectors.toList());
    }
  }

  public static void deleteDirectory(Path dir) {
    try {
      deleteRecursively(dir, ALLOW_INSECURE);
    } catch (IOException ignored) {
    }
  }
}

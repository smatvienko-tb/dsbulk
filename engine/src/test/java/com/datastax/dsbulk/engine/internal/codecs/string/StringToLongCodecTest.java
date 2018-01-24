/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.engine.internal.codecs.string;

import static com.datastax.dsbulk.engine.internal.settings.CodecSettings.CQL_DATE_TIME_FORMAT;
import static com.datastax.dsbulk.engine.tests.EngineAssertions.assertThat;
import static com.google.common.collect.Lists.newArrayList;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.time.Instant.EPOCH;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.collect.ImmutableMap;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class StringToLongCodecTest {

  private final StringToLongCodec codec =
      new StringToLongCodec(
          ThreadLocal.withInitial(
              () -> new DecimalFormat("#,###.##", DecimalFormatSymbols.getInstance(Locale.US))),
          CQL_DATE_TIME_FORMAT,
          MILLISECONDS,
          EPOCH,
          ImmutableMap.of("true", true, "false", false),
          newArrayList(ONE, ZERO));

  @Test
  void should_convert_from_valid_input() throws Exception {
    assertThat(codec)
        .convertsFrom("0")
        .to(0L)
        .convertsFrom("9223372036854775807")
        .to(Long.MAX_VALUE)
        .convertsFrom("-9223372036854775808")
        .to(Long.MIN_VALUE)
        .convertsFrom("9,223,372,036,854,775,807")
        .to(Long.MAX_VALUE)
        .convertsFrom("-9,223,372,036,854,775,808")
        .to(Long.MIN_VALUE)
        .convertsFrom("1970-01-01T00:00:00Z")
        .to(0L)
        .convertsFrom("2000-01-01T00:00:00Z")
        .to(946684800000L)
        .convertsFrom("TRUE")
        .to(1L)
        .convertsFrom("FALSE")
        .to(0L)
        .convertsFrom(null)
        .to(null)
        .convertsFrom("")
        .to(null);
  }

  @Test
  void should_convert_to_valid_input() throws Exception {
    assertThat(codec)
        .convertsTo(0L)
        .from("0")
        .convertsTo(Long.MAX_VALUE)
        .from("9,223,372,036,854,775,807")
        .convertsTo(Long.MIN_VALUE)
        .from("-9,223,372,036,854,775,808")
        .convertsTo(null)
        .from(null);
  }

  @Test
  void should_not_convert_from_invalid_input() throws Exception {
    assertThat(codec)
        .cannotConvertFrom("not a valid long")
        .cannotConvertFrom("1.2")
        .cannotConvertFrom("9223372036854775808")
        .cannotConvertFrom("-9223372036854775809");
  }
}

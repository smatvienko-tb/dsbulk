/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.dsbulk.engine.internal.codecs.json;

import com.datastax.driver.core.TypeCodec;
import com.datastax.dsbulk.engine.internal.codecs.ConvertingCodec;
import com.datastax.dsbulk.engine.internal.codecs.util.CodecUtils;
import com.datastax.dsbulk.engine.internal.codecs.util.TimeUUIDGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.UUID;

public class JsonNodeToUUIDCodec extends ConvertingCodec<JsonNode, UUID> {

  private final ConvertingCodec<String, Instant> instantCodec;
  private final TimeUUIDGenerator generator;

  public JsonNodeToUUIDCodec(
      TypeCodec<UUID> targetCodec,
      ConvertingCodec<String, Instant> instantCodec,
      TimeUUIDGenerator generator) {
    super(targetCodec, JsonNode.class);
    this.instantCodec = instantCodec;
    this.generator = generator;
  }

  @Override
  public UUID convertFrom(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    return CodecUtils.parseUUID(node.asText(), instantCodec, generator);
  }

  @Override
  public JsonNode convertTo(UUID value) {
    if (value == null) {
      return JsonNodeFactory.instance.nullNode();
    }
    return JsonNodeFactory.instance.textNode(value.toString());
  }
}

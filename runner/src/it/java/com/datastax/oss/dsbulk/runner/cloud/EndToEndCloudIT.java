/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.oss.dsbulk.runner.cloud;

import static com.datastax.oss.dsbulk.tests.assertions.TestAssertions.assertThat;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.nio.file.Files.createTempDirectory;
import static org.slf4j.event.Level.INFO;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.shaded.guava.common.collect.Lists;
import com.datastax.oss.dsbulk.runner.DataStaxBulkLoader;
import com.datastax.oss.dsbulk.runner.tests.CsvUtils;
import com.datastax.oss.dsbulk.runner.tests.EndToEndUtils;
import com.datastax.oss.dsbulk.tests.cloud.SNIProxyServer;
import com.datastax.oss.dsbulk.tests.cloud.SNIProxyServerExtension;
import com.datastax.oss.dsbulk.tests.logging.LogCapture;
import com.datastax.oss.dsbulk.tests.logging.LogInterceptingExtension;
import com.datastax.oss.dsbulk.tests.logging.LogInterceptor;
import com.datastax.oss.dsbulk.tests.logging.LogResource;
import com.datastax.oss.dsbulk.tests.utils.FileUtils;
import com.datastax.oss.dsbulk.tests.utils.StringUtils;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;
import ru.lanwen.wiremock.ext.WiremockResolver.Wiremock;

@ExtendWith(LogInterceptingExtension.class)
@ExtendWith(SNIProxyServerExtension.class)
@ExtendWith(WiremockResolver.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("long")
@LogResource("logback.xml")
class EndToEndCloudIT {

  private final SNIProxyServer proxy;
  private final CqlSession session;
  private final LogInterceptor logs;

  private Path logDir;
  private Path unloadDir;

  EndToEndCloudIT(
      SNIProxyServer proxy, CqlSession session, @LogCapture(level = INFO) LogInterceptor logs) {
    this.proxy = proxy;
    this.session = session;
    this.logs = logs;
  }

  @BeforeAll
  void createTables() {
    EndToEndUtils.createIpByCountryTable(session);
  }

  @BeforeEach
  void setUpDirs() throws IOException {
    logDir = createTempDirectory("logs");
    unloadDir = createTempDirectory("unload");
  }

  @AfterEach
  void deleteDirs() {
    FileUtils.deleteDirectory(logDir);
    FileUtils.deleteDirectory(unloadDir);
  }

  @Test
  void full_load_unload_default_CL() throws Exception {
    String bundlePath = proxy.getSecureBundlePath().toString();
    performLoad("-b", bundlePath);
    assertThat(logs).hasMessageContaining("changing default consistency level to LOCAL_QUORUM");
    performUnload("-b", bundlePath);
  }

  @Test
  void full_load_unload_http_bundle(@Wiremock WireMockServer server) throws Exception {
    server.givenThat(
        any(urlPathEqualTo("/creds.zip"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/octet-stream")
                    .withBody(Files.readAllBytes(proxy.getSecureBundlePath()))));
    String bundleUrl = server.baseUrl() + "/creds.zip";
    performLoad("-b", StringUtils.quoteJson(bundleUrl));
    assertThat(logs).hasMessageContaining("changing default consistency level to LOCAL_QUORUM");
    performUnload("-b", StringUtils.quoteJson(bundleUrl));
  }

  @Test
  void full_load_unload_forced_CL() throws Exception {
    String bundlePath = proxy.getSecureBundlePath().toString();
    performLoad("-b", bundlePath, "-cl", "LOCAL_QUORUM");
    assertThat(logs).hasMessageContaining("ignoring all explicit contact points");
    performUnload("-b", bundlePath);
  }

  @Test
  void full_load_unload_forced_wrong_CL() throws Exception {
    String bundlePath = proxy.getSecureBundlePath().toString();
    performLoad("-b", bundlePath, "-cl", "LOCAL_ONE");
    assertThat(logs).hasMessageContaining("forcing default consistency level to LOCAL_QUORUM");
    performUnload("-b", bundlePath);
  }

  private void performLoad(String... specificArgs) throws IOException, URISyntaxException {
    List<String> loadArgs =
        Lists.newArrayList(
            "load",
            "--connector.csv.url",
            StringUtils.quoteJson(CsvUtils.CSV_RECORDS_UNIQUE),
            "--connector.csv.header",
            "false",
            "--schema.keyspace",
            session.getKeyspace().get().asInternal(),
            "--schema.table",
            "ip_by_country",
            "--schema.mapping",
            EndToEndUtils.IP_BY_COUNTRY_MAPPING_INDEXED);
    loadArgs.addAll(Arrays.asList(specificArgs));
    loadArgs.addAll(commonArgs());
    int status = new DataStaxBulkLoader(loadArgs.toArray(new String[0])).run();
    EndToEndUtils.assertStatus(status, DataStaxBulkLoader.STATUS_OK);
    ResultSet set = session.execute("SELECT * FROM ip_by_country");
    List<Row> results = set.all();
    assertThat(results.size()).isEqualTo(24);
    EndToEndUtils.validatePositionsFile(CsvUtils.CSV_RECORDS_UNIQUE, 24);
    FileUtils.deleteDirectory(logDir);
  }

  private void performUnload(String... specificArgs) throws IOException {
    List<String> unloadArgs =
        Lists.newArrayList(
            "unload",
            "--connector.csv.url",
            StringUtils.quoteJson(unloadDir),
            "--connector.csv.header",
            "false",
            "--connector.csv.maxConcurrentFiles",
            "1",
            "--schema.keyspace",
            session.getKeyspace().get().asInternal(),
            "--schema.table",
            "ip_by_country",
            "--schema.mapping",
            EndToEndUtils.IP_BY_COUNTRY_MAPPING_INDEXED);
    unloadArgs.addAll(Arrays.asList(specificArgs));
    unloadArgs.addAll(commonArgs());
    int status = new DataStaxBulkLoader(unloadArgs.toArray(new String[0])).run();
    EndToEndUtils.assertStatus(status, DataStaxBulkLoader.STATUS_OK);
    EndToEndUtils.validateOutputFiles(24, unloadDir);
  }

  private List<String> commonArgs() {
    return Lists.newArrayList(
        "--log.directory",
        StringUtils.quoteJson(logDir),
        "--datastax-java-driver.advanced.connection.pool.local.size",
        "1",
        "--datastax-java-driver.advanced.auth-provider.class",
        "PlainTextAuthProvider",
        "--datastax-java-driver.advanced.auth-provider.username",
        "cassandra",
        "--datastax-java-driver.advanced.auth-provider.password",
        "cassandra");
  }
}
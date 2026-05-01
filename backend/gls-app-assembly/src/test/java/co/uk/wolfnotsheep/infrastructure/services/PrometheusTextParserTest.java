package co.uk.wolfnotsheep.infrastructure.services;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusTextParserTest {

    @Test
    void parsesLabelLessMetric() {
        String body = """
                # HELP llm_circuit_breaker_consecutive_failures Number of consecutive failures
                # TYPE llm_circuit_breaker_consecutive_failures gauge
                llm_circuit_breaker_consecutive_failures 3.0
                """;
        List<PrometheusTextParser.Sample> samples =
                PrometheusTextParser.parse(body, Set.of("llm_circuit_breaker_consecutive_failures"));
        assertThat(samples).hasSize(1);
        assertThat(samples.get(0).metricName()).isEqualTo("llm_circuit_breaker_consecutive_failures");
        assertThat(samples.get(0).labels()).isEmpty();
        assertThat(samples.get(0).value()).isEqualTo(3.0);
    }

    @Test
    void parsesLabelledMetricWithMultipleSamples() {
        String body = """
                # HELP gls_router_classify_result_total Router classification outcomes
                # TYPE gls_router_classify_result_total counter
                gls_router_classify_result_total{outcome="success",tier="BERT"} 142.0
                gls_router_classify_result_total{outcome="success",tier="LLM"} 38.0
                gls_router_classify_result_total{outcome="failure",tier="BERT"} 2.0
                """;
        List<PrometheusTextParser.Sample> samples =
                PrometheusTextParser.parse(body, Set.of("gls_router_classify_result_total"));
        assertThat(samples).hasSize(3);
        assertThat(samples.get(0).labels()).containsEntry("outcome", "success").containsEntry("tier", "BERT");
        assertThat(samples.get(0).value()).isEqualTo(142.0);
        assertThat(samples.get(1).labels()).containsEntry("tier", "LLM");
        assertThat(samples.get(2).labels()).containsEntry("outcome", "failure");
    }

    @Test
    void filtersOutMetricsNotInAllowlist() {
        String body = """
                # HELP jvm_memory_used_bytes JVM memory
                # TYPE jvm_memory_used_bytes gauge
                jvm_memory_used_bytes{area="heap",id="G1 Eden"} 1024.0
                llm_circuit_breaker_state{backend="anthropic"} 0.0
                jvm_threads_live_threads 42.0
                """;
        List<PrometheusTextParser.Sample> samples =
                PrometheusTextParser.parse(body, Set.of("llm_circuit_breaker_state"));
        assertThat(samples).hasSize(1);
        assertThat(samples.get(0).metricName()).isEqualTo("llm_circuit_breaker_state");
    }

    @Test
    void emptyAndNullInputReturnEmptyList() {
        assertThat(PrometheusTextParser.parse(null, Set.of("x"))).isEmpty();
        assertThat(PrometheusTextParser.parse("", Set.of("x"))).isEmpty();
    }

    @Test
    void skipsCommentLines() {
        String body = """
                # this is a comment
                # HELP foo bar
                # TYPE foo gauge
                foo 1.0
                """;
        List<PrometheusTextParser.Sample> samples =
                PrometheusTextParser.parse(body, Set.of("foo"));
        assertThat(samples).hasSize(1);
        assertThat(samples.get(0).value()).isEqualTo(1.0);
    }

    @Test
    void parseLabels_handlesOrderedKeys() {
        var labels = PrometheusTextParser.parseLabels("a=\"1\",b=\"2\",c=\"3\"");
        assertThat(labels).hasSize(3);
        assertThat(labels).containsEntry("a", "1");
        assertThat(labels).containsEntry("b", "2");
        assertThat(labels).containsEntry("c", "3");
    }

    @Test
    void parseLabels_handlesEmptyString() {
        assertThat(PrometheusTextParser.parseLabels("")).isEmpty();
    }

    @Test
    void parseLabels_skipsMalformedTrailing() {
        // Real-world prometheus output is well-formed; these are defensive.
        var labels = PrometheusTextParser.parseLabels("a=\"1\",b=");
        assertThat(labels).hasSize(1);
        assertThat(labels).containsEntry("a", "1");
    }

    @Test
    void invalidValueLineIsSkipped() {
        String body = "foo not_a_number\n";
        List<PrometheusTextParser.Sample> samples =
                PrometheusTextParser.parse(body, Set.of("foo"));
        assertThat(samples).isEmpty();
    }

    @Test
    void handlesValueWithTrailingTimestamp() {
        // Some exposition formats include "<value> <timestamp_ms>".
        String body = "foo 42.5 1714568400000\n";
        List<PrometheusTextParser.Sample> samples =
                PrometheusTextParser.parse(body, Set.of("foo"));
        assertThat(samples).hasSize(1);
        assertThat(samples.get(0).value()).isEqualTo(42.5);
    }
}

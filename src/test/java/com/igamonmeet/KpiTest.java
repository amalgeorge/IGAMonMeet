package com.igamonmeet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igamonmeet.template.Direction;
import com.igamonmeet.template.KpiStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class KpiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void statusRules() {
        assertThat(KpiStatus.of(Direction.HIGHER, d("95"), d("90"), d("96.4"))).isEqualTo(KpiStatus.ON_TARGET);
        assertThat(KpiStatus.of(Direction.HIGHER, d("95"), d("90"), d("95"))).isEqualTo(KpiStatus.ON_TARGET);
        assertThat(KpiStatus.of(Direction.HIGHER, d("95"), d("90"), d("92"))).isEqualTo(KpiStatus.BELOW_TARGET);
        assertThat(KpiStatus.of(Direction.HIGHER, d("95"), d("90"), d("90"))).isEqualTo(KpiStatus.BELOW_TARGET);
        assertThat(KpiStatus.of(Direction.HIGHER, d("95"), d("90"), d("85"))).isEqualTo(KpiStatus.OFF_TARGET);
        assertThat(KpiStatus.of(Direction.LOWER, d("4"), d("6"), d("3.2"))).isEqualTo(KpiStatus.ON_TARGET);
        assertThat(KpiStatus.of(Direction.LOWER, d("4"), d("6"), d("5"))).isEqualTo(KpiStatus.BELOW_TARGET);
        assertThat(KpiStatus.of(Direction.LOWER, d("4"), d("6"), d("7"))).isEqualTo(KpiStatus.OFF_TARGET);
        assertThat(KpiStatus.of(Direction.LOWER, d("4"), d("6"), null)).isNull();
    }

    private long meeting(String month, String date) throws Exception {
        String body = mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("kpi-admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"" + month + "\",\"meetingDate\":\"" + date + "\"}")).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private JsonNode kpiSection(long m) throws Exception {
        JsonNode d = json.readTree(mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER"))).andReturn().getResponse().getContentAsString());
        for (JsonNode s : d.get("sections")) {
            if (s.get("kind").asText().equals("KPI")) return s;
        }
        throw new AssertionError("no KPI section");
    }

    private ResultActions save(long m, JsonNode s, long version, String values) throws Exception {
        return mvc.perform(put("/api/meetings/" + m + "/sections/" + s.get("id").asLong()).with(csrf()).with(user("kpi-editor").roles("END_USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version + ",\"content\":{\"values\":" + values + ",\"commentary\":\"\"}}"));
    }

    @Test
    void valuesProduceStatusesOverridesAndPreviousMonth() throws Exception {
        long oct = meeting("2036-10", "2036-10-29");
        save(oct, kpiSection(oct), 0, "{\"sla\":{\"value\":94.1}}").andExpect(status().isOk());

        long nov = meeting("2036-11", "2036-11-26");
        JsonNode s = kpiSection(nov);
        save(nov, s, 0, "{\"sla\":{\"value\":96.4},\"provisioning\":{\"value\":7},"
                + "\"certification\":{\"value\":70,\"override\":\"BELOW_TARGET\",\"reason\":\"Two BUs escalated\"}}").andExpect(status().isOk());

        mvc.perform(get("/api/meetings/" + nov).with(user("r").roles("END_USER")))
                .andExpect(jsonPath("$.sections[?(@.kind=='KPI')].kpis[?(@.key=='sla')].status").value("ON_TARGET"))
                .andExpect(jsonPath("$.sections[?(@.kind=='KPI')].kpis[?(@.key=='sla')].previousValue").value(94.1))
                .andExpect(jsonPath("$.sections[?(@.kind=='KPI')].kpis[?(@.key=='provisioning')].status").value("OFF_TARGET"))
                .andExpect(jsonPath("$.sections[?(@.kind=='KPI')].kpis[?(@.key=='certification')].computedStatus").value("OFF_TARGET"))
                .andExpect(jsonPath("$.sections[?(@.kind=='KPI')].kpis[?(@.key=='certification')].status").value("BELOW_TARGET"))
                .andExpect(jsonPath("$.sections[?(@.kind=='KPI')].filled").value(false));
    }

    @Test
    void invalidKpiContentIsRejected() throws Exception {
        long m = meeting("2036-12", "2036-12-17");
        JsonNode s = kpiSection(m);
        save(m, s, 0, "{\"no-such-kpi\":{\"value\":1}}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_CONTENT"));
        save(m, s, 0, "{\"sla\":{\"value\":\"high\"}}").andExpect(status().isBadRequest());
        save(m, s, 0, "{\"sla\":{\"value\":80,\"override\":\"ON_TARGET\"}}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("reason")));
    }
}

package com.igamonmeet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igamonmeet.slides.RichText;
import com.igamonmeet.slides.SlidePlanner;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** Review fixes: text cannot pose as formatting marks, oversized pictures are refused, security headers are sent. */
@SpringBootTest
@AutoConfigureMockMvc
class HardeningTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void plainTextCannotCarryFormattingMarks() throws Exception {
        assertThat(RichText.clean("a\u0012javascript:x\u0013b\u0014\u0002c\tok\nline")).isEqualTo("ajavascript:xbc\tok\nline");
        assertThat(RichText.safeHref("javascript:alert(1)")).isNull();
        assertThat(RichText.safeHref("https://example.com/a")).isEqualTo("https://example.com/a");
        // Content stored before the check: a paragraph with stray marks still plans as plain text.
        JsonNode old = json.readTree("{\"blocks\":[{\"type\":\"paragraph\",\"text\":\"\\u0002no number end\"},{\"type\":\"bullets\",\"items\":[\"\\u0012x\"]}]}");
        assertThat(SlidePlanner.textLines(old)).containsExactly("no number end", "\u0001x");
    }

    @Test
    void savingTextWithControlCharactersIsRefused() throws Exception {
        long m = json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("hd-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"2062-03\",\"meetingDate\":\"2062-03-16\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        JsonNode detail = json.readTree(mvc.perform(get("/api/meetings/" + m).with(user("r").roles("END_USER"))).andReturn().getResponse().getContentAsString());
        JsonNode summary = null;
        for (JsonNode s : detail.get("sections")) if (s.get("key").asText().equals("summary")) summary = s;
        mvc.perform(put("/api/meetings/" + m + "/sections/" + summary.get("id").asLong()).with(csrf()).with(user("e").roles("END_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + summary.get("version").asLong() + ",\"content\":{\"points\":[\"ok \\u0012javascript:x\\u0013click\\u0014\"]}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("control characters")));
        mvc.perform(put("/api/meetings/" + m + "/sections/" + summary.get("id").asLong()).with(csrf()).with(user("e").roles("END_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + summary.get("version").asLong() + ",\"content\":{\"points\":[\"line one\\nline two\"]}}"))
                .andExpect(status().isOk());
    }

    @Test
    void aPictureThatUnpacksHugeIsRefusedBeforeDecoding() throws Exception {
        mvc.perform(multipart("/api/admin/theme-images").file(new MockMultipartFile("file", "bomb.png", "image/png", pngHeader(30000, 30000)))
                        .param("kind", "logo").with(csrf()).with(user("hd-admin").roles("ADMIN")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("pixels")));
    }

    @Test
    void securityHeadersAreSent() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    /** A PNG that claims w x h pixels: signature, IHDR and IEND only. */
    private static byte[] pngHeader(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        byte[] ihdr = ByteBuffer.allocate(13).putInt(w).putInt(h).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0).array();
        chunk(out, "IHDR", ihdr);
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws Exception {
        out.write(ByteBuffer.allocate(4).putInt(data.length).array());
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        out.write(t);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        out.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}

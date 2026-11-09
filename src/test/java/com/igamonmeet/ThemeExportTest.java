package com.igamonmeet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** The Meeting's Theme reaches the PPTX: theme colours and fonts, slide backgrounds and the logo (spec-v2 §4.4). */
@SpringBootTest
@AutoConfigureMockMvc
class ThemeExportTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private long image(String kind, int w, int h) throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", png);
        return json.readTree(mvc.perform(multipart("/api/admin/theme-images")
                        .file(new MockMultipartFile("file", kind + ".png", "image/png", png.toByteArray())).param("kind", kind)
                        .with(csrf()).with(user("tx-admin").roles("ADMIN")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void exportCarriesTheMeetingsTheme() throws Exception {
        long logo = image("logo", 300, 100);
        long bg = image("background", 1600, 900);
        ObjectNode s = json.createObjectNode();
        s.putObject("colors").put("primary", "004B87").put("accent", "00A3E0").put("text", "222222").put("onTarget", "0B6E4F");
        s.putObject("fonts").put("heading", "Georgia").put("body", "Calibri");
        ObjectNode bgs = s.putObject("backgrounds");
        bgs.putObject("title").put("color", "004B87");
        bgs.putObject("content").put("color", "F4F8FB").put("imageId", bg);
        s.putObject("logo").put("show", true).put("imageId", logo);
        ObjectNode body = json.createObjectNode();
        body.put("name", "Export blue");
        body.set("settings", s);
        long theme = json.readTree(mvc.perform(post("/api/admin/themes").with(csrf()).with(user("tx-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();

        long m = json.readTree(mvc.perform(post("/api/admin/meetings").with(csrf()).with(user("tx-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"2033-07\",\"meetingDate\":\"2033-07-14\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(put("/api/meetings/" + m + "/theme-choice").with(csrf()).with(user("tx-admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"themeId\":" + theme + "}")).andExpect(status().isOk());

        byte[] bytes = mvc.perform(post("/api/meetings/" + m + "/export/pptx").with(csrf()).with(user("r").roles("END_USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sections\":null,\"notes\":false,\"comments\":false,\"versionStamp\":false}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            var el = ppt.getSlideMasters().get(0).getTheme().getXmlObject().getThemeElements();
            assertThat(el.getClrScheme().getAccent1().getSrgbClr().getVal()).containsExactly(0x00, 0x4B, (byte) 0x87);
            assertThat(el.getClrScheme().getDk1().getSrgbClr().getVal()).containsExactly(0x22, 0x22, 0x22);
            assertThat(el.getFontScheme().getMajorFont().getLatin().getTypeface()).isEqualTo("Georgia");
            assertThat(el.getFontScheme().getMinorFont().getLatin().getTypeface()).isEqualTo("Calibri");

            XSLFSlide title = ppt.getSlides().get(0);
            assertThat(title.getSlideLayout().getName()).isEqualTo("IAM_TITLE");
            assertThat(title.getXmlObject().getCSld().getBg().getBgPr().getSolidFill().getSrgbClr().getVal())
                    .containsExactly(0x00, 0x4B, (byte) 0x87);
            assertThat(title.getShapes().stream().filter(x -> x instanceof XSLFPictureShape).count()).isEqualTo(1); // logo only

            XSLFSlide content = ppt.getSlides().stream().filter(x -> !"IAM_TITLE".equals(x.getSlideLayout().getName())
                    && !"IAM_SECTION".equals(x.getSlideLayout().getName()) && !"IAM_CLOSING".equals(x.getSlideLayout().getName())).findFirst().orElseThrow();
            assertThat(content.getShapes().get(0)).as("background picture sits behind everything").isInstanceOf(XSLFPictureShape.class);
            assertThat(content.getShapes().stream().filter(x -> x instanceof XSLFPictureShape).count()).isEqualTo(2);
        }
    }
}

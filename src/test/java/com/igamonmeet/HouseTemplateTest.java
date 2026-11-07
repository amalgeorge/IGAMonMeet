package com.igamonmeet;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.igamonmeet.house.HouseTemplateService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.function.Consumer;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class HouseTemplateTest {

    @Autowired MockMvc mvc;

    private static byte[] bundled() throws Exception {
        try (InputStream in = new ClassPathResource(HouseTemplateService.BUNDLED).getInputStream()) {
            return in.readAllBytes();
        }
    }

    /** The bundled template with one change applied to it. */
    private static byte[] modified(Consumer<XMLSlideShow> change) throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(bundled())); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            change.accept(ppt);
            ppt.write(out);
            return out.toByteArray();
        }
    }

    private static XSLFSlideLayout layout(XMLSlideShow ppt, String name) {
        return ppt.getSlideMasters().get(0).getLayout(name);
    }

    private ResultActions upload(String name, byte[] content) throws Exception {
        return mvc.perform(multipart("/api/admin/house-templates").file(new MockMultipartFile("file", name, "application/octet-stream", content))
                .with(csrf()).with(user("ht-admin").roles("ADMIN")));
    }

    @Test
    void bundledThemeIsServed() throws Exception {
        mvc.perform(get("/api/house-template/theme").with(user("u").roles("END_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colors.accent1").value("C8102E"))
                .andExpect(jsonPath("$.headFont").value("Arial"))
                .andExpect(jsonPath("$.layouts.IAM_KPI.placeholders.title.x").value(4.5))
                .andExpect(jsonPath("$.layouts.IAM_TITLE.background").value("C8102E"));
    }

    @Test
    void validUploadBecomesCurrent() throws Exception {
        upload("Our template.pptx", bundled()).andExpect(status().isCreated()).andExpect(jsonPath("$.versionNo").isNumber());
        mvc.perform(get("/api/admin/house-templates").with(user("ht-admin").roles("ADMIN")))
                .andExpect(jsonPath("$.current").isNumber())
                .andExpect(jsonPath("$.versions[0].fileName").value("Our template.pptx"));
    }

    @Test
    void missingLayoutOrPlaceholderIsRejected() throws Exception {
        byte[] noKpi = modified(ppt -> layout(ppt, "IAM_KPI").getXmlObject().getCSld().setName("SOMETHING_ELSE"));
        upload("t.pptx", noKpi).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Layout IAM_KPI is missing")));

        byte[] noLeft = modified(ppt -> layout(ppt, "IAM_TWO_COLUMN").getShapes().stream()
                .filter(s -> s.getShapeName().equals("left")).findFirst()
                .ifPresent(s -> ((CTShape) s.getXmlObject()).getNvSpPr().getCNvPr().setName("renamed")));
        upload("t.pptx", noLeft).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no placeholder named 'left'")));
    }

    @Test
    void wrongFilesAreRejected() throws Exception {
        upload("notes.txt", bundled()).andExpect(status().isBadRequest());
        upload("broken.pptx", "not a zip".getBytes()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This is not a readable PowerPoint file."));
    }

    @Test
    void endUserCannotUpload() throws Exception {
        mvc.perform(multipart("/api/admin/house-templates").file(new MockMultipartFile("file", "t.pptx", "application/octet-stream", bundled()))
                .with(csrf()).with(user("u").roles("END_USER"))).andExpect(status().isForbidden());
    }
}

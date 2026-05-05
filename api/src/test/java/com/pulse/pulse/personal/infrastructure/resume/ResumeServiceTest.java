package com.pulse.pulse.personal.infrastructure.resume;

import com.pulse.pulse.platform.observability.PulseObservability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResumeServiceTest {

    private SimpleMeterRegistry meterRegistry;
    private TestObservationRegistry observationRegistry;
    private ResumeService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        observationRegistry = TestObservationRegistry.create();
        service = new ResumeService(new PulseObservability(observationRegistry, meterRegistry));
    }

    @Test
    void extractTextRecordsSuccessForValidPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                createPdf("Hello Pulse")
        );

        String text = service.extractText(file);

        assertEquals("Hello Pulse", text);
        assertEquals(1.0, meterRegistry.get("pulse.resume.operations")
                .tag("operation", "extract")
                .tag("result", "success")
                .counter()
                .count());
        assertEquals(1L, meterRegistry.get("pulse.resume.extract.duration").timer().count());
        assertThat(observationRegistry).hasObservationWithNameEqualTo("pulse.resume.extract");
    }

    @Test
    void extractTextRecordsFailureForInvalidFileType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.txt",
                "text/plain",
                "hello".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> service.extractText(file));

        assertEquals(1.0, meterRegistry.get("pulse.resume.operations")
                .tag("operation", "extract")
                .tag("result", "error")
                .counter()
                .count());
    }

    private byte[] createPdf(String content) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(100, 700);
                stream.showText(content);
                stream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}

package com.pulse.pulse.personal.infrastructure.resume;

import com.pulse.pulse.platform.observability.PulseObservability;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@Slf4j
public class ResumeService {

    private final PulseObservability observability;

    public ResumeService(PulseObservability observability) {
        this.observability = observability;
    }

    public String extractText(MultipartFile file) {
        Observation observation = observability.start("pulse.resume.extract");
        Timer.Sample sample = observability.timerSample();
        String result = "error";

        observability.low(observation, "operation", "extract");
        observability.high(observation, "file.size_bytes", file.getSize());

        String filename = file.getOriginalFilename();
        try {
            if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
                throw new IllegalArgumentException("Only PDF files are supported");
            }

            try (PDDocument document = Loader.loadPDF(file.getBytes())) {
                observability.high(observation, "resume.page_count", document.getNumberOfPages());

                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                if (text == null || text.isBlank()) {
                    throw new RuntimeException("Could not extract text from PDF â€” the file may be image-based");
                }

                String trimmed = text.trim();
                observability.high(observation, "response.char_count", trimmed.length());
                result = "success";
                return trimmed;
            }
        } catch (IOException e) {
            observation.error(e);
            log.error("Failed to parse PDF: {}", filename, e);
            throw new RuntimeException("Failed to parse PDF file", e);
        } catch (RuntimeException e) {
            observation.error(e);
            throw e;
        } finally {
            observability.low(observation, "result", result);
            observability.counter("pulse.resume.operations",
                            "operation", "extract",
                            "result", result)
                    .increment();
            observability.stopTimer(sample, "pulse.resume.extract.duration",
                    "result", result);
            observation.stop();
        }
    }
}

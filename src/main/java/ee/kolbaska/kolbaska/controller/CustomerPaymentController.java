package ee.kolbaska.kolbaska.controller;

import com.google.zxing.WriterException;
import ee.kolbaska.kolbaska.exception.PaymentException;
import ee.kolbaska.kolbaska.exception.PaymentNotFoundException;
import ee.kolbaska.kolbaska.request.CertificateCreationRequest;
import ee.kolbaska.kolbaska.request.CertificateVerificationRequest;
import ee.kolbaska.kolbaska.response.CertificateCreationResponse;
import ee.kolbaska.kolbaska.response.CertificateVerificationResponse;
import ee.kolbaska.kolbaska.service.CustomerPaymentService;
import freemarker.template.TemplateException;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("${api.basepath}/payment")
@RequiredArgsConstructor
public class CustomerPaymentController {

    private final CustomerPaymentService service;

    @PostMapping("/initiateCreation")
    public ResponseEntity<CertificateCreationResponse> initiateCreation(
            @Valid @RequestBody CertificateCreationRequest request
    ) {
        return ResponseEntity.ok(service.initiateCreation(request));
    }

    @PostMapping("/verificationCreation")
    public ResponseEntity<CertificateVerificationResponse> verificationCreation(
            @Valid @RequestBody CertificateVerificationRequest request
    ) throws PaymentNotFoundException, PaymentException, MessagingException, IOException, WriterException, TemplateException {
        return ResponseEntity.ok(service.verificationCreation(request));
    }
}

package ee.maitsetuur.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.WriterException;
import ee.maitsetuur.exception.PaymentException;
import ee.maitsetuur.exception.PaymentNotFoundException;
import ee.maitsetuur.jsonModel.paymentData.PaymentData;
import ee.maitsetuur.jsonModel.paymentMethods.Country;
import ee.maitsetuur.jsonModel.paymentMethods.MainPaymentResponse;
import ee.maitsetuur.jsonModel.paymentMethods.PaymentInitiation;
import ee.maitsetuur.jsonModel.paymentMethods.PaymentMethod;
import ee.maitsetuur.model.business.Business;
import ee.maitsetuur.model.business.PaymentCustomer;
import ee.maitsetuur.model.certificate.Certificate;
import ee.maitsetuur.model.payment.Payment;
import ee.maitsetuur.model.payment.Status;
import ee.maitsetuur.model.user.Role;
import ee.maitsetuur.model.user.User;
import ee.maitsetuur.repository.*;
import ee.maitsetuur.request.CertificateVerificationRequest;
import ee.maitsetuur.request.PaymentValidationRequest;
import ee.maitsetuur.request.payment.CertificateInformation;
import ee.maitsetuur.request.payment.PaymentRequest;
import ee.maitsetuur.response.CertificateCreationResponse;
import ee.maitsetuur.response.CertificateVerificationResponse;
import ee.maitsetuur.response.PaymentMethodResponse;
import ee.maitsetuur.response.PaymentValidationResponse;
import ee.maitsetuur.service.miscellaneous.EmailService;
import ee.maitsetuur.service.miscellaneous.QrCodeService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Example;
import org.springframework.http.*;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CustomerPaymentService {

    private final CertificateRepository certificateRepository;

    private final UserRepository userRepository;

    private final PaymentRepository paymentRepository;

    private final QrCodeService qrCodeService;

    private final EmailService emailService;

    private final RoleRepository roleRepository;

    private final PaymentCustomerRepository paymentCustomerRepository;

    private final BusinessRepository businessRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${montonio.api.url}")
    private String MONTONIO_API_URL;

    @Value("${montonio.accessKey}")
    private String MONTONIO_ACCESS_KEY;

    @Value("${montonio.secretKey}")
    private String MONTONIO_SECRET_KEY;

    @Value("${front.baseurl}")
    private String WEBSITE_BASE_URL;

    @Value("${api.baseurl}")
    private String API_BASEURL;

    @Value("${api.basepath}")
    private String API_BASEPATH;

    @Transactional
    public CertificateCreationResponse initiateCreation(PaymentRequest request) throws JsonProcessingException {

        Map<String, Object> payload = new HashMap<>();

        UUID uuid = UUID.randomUUID();

        payload.put("accessKey", MONTONIO_ACCESS_KEY);
        payload.put("merchantReference", uuid.toString());
        payload.put("returnUrl", "%s/personal-coupon-order/order-details".formatted(WEBSITE_BASE_URL));
        payload.put("notificationUrl", "%s%s/payment/verificationCreation".formatted(API_BASEURL, API_BASEPATH));

        Double total = request.getCertificates().stream().mapToDouble(CertificateInformation::getNominalValue).sum();

        payload.put("grandTotal", total);
        payload.put("currency", "EUR");

        Map<String, Object> paymentMethod = new HashMap<>();
        paymentMethod.put("method", "paymentInitiation"); //TODO in the future change this so it choose method automatically
        paymentMethod.put("currency", "EUR");
        paymentMethod.put("amount", total);

        payload.put("payment", paymentMethod);

        Map<String, String> billingAddress = new HashMap<>();
        billingAddress.put("firstName", request.getBuyer().getFromFullName());
        Optional.ofNullable(request.getBusinessInformation()).ifPresent(businessInformation -> billingAddress.put("lastName", businessInformation.getBusinessName()));
        billingAddress.put("email", request.getBuyer().getFromEmail());
        billingAddress.put("phoneNumber", request.getBuyer().getFromPhone());
        billingAddress.put("addressLine1", "%s - %s".formatted(request.getAddress().getStreet(), request.getAddress().getApartmentNumber()));
        billingAddress.put("locality", request.getAddress().getCity());
        billingAddress.put("region", request.getAddress().getState());
        billingAddress.put("postalCode", request.getAddress().getZipCode());
        billingAddress.put("country", "EE"); //TODO fix this somehow

        payload.put("billingAddress", billingAddress);
        payload.put("locale", "et"); //TODO fix this somehow

        List<Map<String, Object>> items = new ArrayList<>();

        List<CertificateInformation> certificatesInformation = request.getCertificates();

        for (CertificateInformation ci : certificatesInformation) {
            Map<String, Object> item = new HashMap<>();

            item.put("name", "Certificate - %s".formatted(ci.getGreeting()));
            item.put("quantity", 1);
            item.put("finalPrice", ci.getNominalValue());

            items.add(item);
        }

        payload.put("lineItems", items);

        String jwtToken = JWT
                .create()
                .withPayload(payload)
                .withExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .withIssuedAt(Instant.now())
                .sign(Algorithm.HMAC256(MONTONIO_SECRET_KEY));

        String requestUrl = MONTONIO_API_URL + "/orders";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        String requestBody = "{\"data\": \""+ jwtToken +"\"}";

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(requestUrl, HttpMethod.POST, entity, String.class);

        String jsonResponse = response.getBody();

        ObjectMapper objectMapper = new ObjectMapper();
        PaymentData payments = objectMapper.readValue(jsonResponse, PaymentData.class);

        Optional<User> ifSender = userRepository.findByEmail(request.getBuyer().getFromEmail());

        Role customerRole = roleRepository.findRoleByRoleName("ROLE_CUSTOMER").get();

        User sender = ifSender.orElseGet(() -> userRepository.save(User.builder()
                .fullName(request.getBuyer().getFromFullName())
                .email(request.getBuyer().getFromEmail())
                .activated(false)
                .roles(List.of(customerRole))
                .build()));

        Optional.ofNullable(request.getBusinessInformation()).ifPresent(businessInfo -> {
            Business business = Business.builder()
                    .businessName(businessInfo.getBusinessName())
                    .registerCode(businessInfo.getRegisterCode())
                    .businessKMKR(businessInfo.getBusinessKMKR())
                    .representative(sender)
                    .build();

            Optional<Business> ifBusiness = businessRepository.findOne(Example.of(business));

            if (ifBusiness.isEmpty()) businessRepository.save(business);
        });

        Payment newPayment = Payment.builder()
                .fromFullName(request.getBuyer().getFromFullName())
                .fromEmail(request.getBuyer().getFromEmail())
                .status(Status.PENDING)
                .merchantReference(uuid.toString())
                .build();

        newPayment = paymentRepository.save(newPayment);

        for (CertificateInformation ci : certificatesInformation) {
            PaymentCustomer customer = PaymentCustomer.builder()
                    .greeting(ci.getGreeting())
                    .greetingText(ci.getGreetingsText())
                    .email(ci.getEmail())
                    .value(ci.getNominalValue())
                    .payment(newPayment)
                    .build();

            paymentCustomerRepository.save(customer);
        }


        return CertificateCreationResponse.builder()
                .redirectUrl(payments.getPaymentUrl())
                .build();
    }

    @Transactional
    public CertificateVerificationResponse verificationCreation(CertificateVerificationRequest request) throws PaymentNotFoundException, PaymentException, IOException, WriterException, MessagingException {

        DecodedJWT decodedJWT;
        try {
            decodedJWT = JWT.require(Algorithm.HMAC256(MONTONIO_SECRET_KEY)).build().verify(request.getOrderToken());
        } catch (JWTVerificationException e) {
            throw new JWTVerificationException("Secret token is not valid");
        }

        Map<String, Claim> claims = decodedJWT.getClaims();

        Optional<Payment> ifPayment = paymentRepository.findByMerchantReference(claims.get("merchantReference").asString());

        if(ifPayment.isEmpty()) throw new PaymentNotFoundException("Payment wasn't found");
        Payment payment = ifPayment.get();

        if (
                claims.get("paymentStatus").asString().equals("PAID") &&
                claims.get("accessKey").asString().equals(MONTONIO_ACCESS_KEY)
        ) {
            Role customerRole = roleRepository.findRoleByRoleName("ROLE_CUSTOMER").get();

            Set<PaymentCustomer> paymentCustomers = payment.getPaymentCustomers();

            LocalDate validUntilDate = LocalDate.now().plusYears(1);

            Optional<User> ifSender = userRepository.findByEmail(payment.getFromEmail());

            if (ifSender.isEmpty()) throw new UsernameNotFoundException("User not found, it must be created on previous step");

            User sender = ifSender.get();

            for (PaymentCustomer pc : paymentCustomers) {
                Optional<User> ifHolder = userRepository.findByEmail(pc.getEmail());

                User holder = ifHolder.orElseGet(() -> userRepository.save(User.builder()
                        .email(pc.getEmail())
                        .activated(false)
                        .roles(List.of(customerRole))
                        .build()));

                Certificate newCertificate = Certificate.builder()
                        .holder(holder)
                        .sender(sender)
                        .greeting(pc.getGreeting())
                        .greetingText(pc.getGreetingText())
                        .value(pc.getValue())
                        .validUntil(validUntilDate)
                        .active(true)
                        .createdByAdmin(false)
                        .payment(payment)
                        .build();

                newCertificate = certificateRepository.save(newCertificate);

                Map<String, String> payload = new HashMap<>();

                payload.put("certificate_id", newCertificate.getId().toString());
                payload.put("name", pc.getGreeting());
                payload.put("remainingValue", "%.2f".formatted(pc.getValue()));

                byte[] qrCodeImage = qrCodeService.createQrCode(payload.toString());

                Map<String, Object> content = new HashMap<>();

                DateTimeFormatter dtf = new DateTimeFormatterBuilder().appendPattern("dd/MM/yyyy").toFormatter();

                content.put("qrCode", qrCodeImage);
                content.put("value", "%.2f€".formatted(pc.getValue()));
                content.put("valid_until", validUntilDate.format(dtf));
                content.put("from", payment.getFromFullName());
                content.put("to", pc.getGreeting());
                content.put("description", pc.getGreetingText());

                emailService.sendHTMLEmail(
                        pc.getEmail(),
                        "Congratulations you received restaurant certificate",
                        "email/successfulCertificatePayment",
                        content
                );
            }

            payment.setStatus(Status.PAID);
            paymentRepository.save(payment);

            return CertificateVerificationResponse.builder()
                    .message("Certificate was successful created and sent to the receiver")
                    .build();
        }
        throw new PaymentException("Something with your access key or payment status");
    }

    @Deprecated
    public Map<String, List<PaymentMethodResponse>> methods() throws JsonProcessingException {

        Map<String, Object> payload = new HashMap<>();
        payload.put("accessKey", MONTONIO_ACCESS_KEY);

        String jwtToken = JWT
                .create()
                .withPayload(payload)
                .withIssuedAt(Instant.now())
                .withExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .sign(Algorithm.HMAC256(MONTONIO_SECRET_KEY));

        String requestUrl = MONTONIO_API_URL + "/stores/payment-methods";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        headers.setBearerAuth(jwtToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(requestUrl, HttpMethod.GET, entity, String.class);
        String jsonResponse = response.getBody();
        ObjectMapper objectMapper = new ObjectMapper();
        MainPaymentResponse payments = objectMapper.readValue(jsonResponse, MainPaymentResponse.class);

        PaymentInitiation paymentInitiation = payments.getPaymentMethods().getPaymentInitiation();

        Map<String, Country> setup = paymentInitiation.getSetup();

        Map<String, List<PaymentMethodResponse>> finalResponse = new HashMap<>();

        for (Map.Entry<String, Country> country : setup.entrySet()) {
            List<PaymentMethodResponse> countryMethods = new ArrayList<>();
            for (PaymentMethod method : country.getValue().getPaymentMethods()) {
                PaymentMethodResponse newMethod = PaymentMethodResponse.builder()
                        .paymentName(method.getName())
                        .paymentCode(method.getCode())
                        .logoUrl(method.getLogoUrl())
                        .build();

                countryMethods.add(newMethod);
            }
            finalResponse.put(country.getKey(), countryMethods);
        }

        return finalResponse;
    }

    public PaymentValidationResponse validatePayment(PaymentValidationRequest request) {
        try {
           JWT.require(Algorithm.HMAC256(MONTONIO_SECRET_KEY)).build().verify(request.getOrderToken());
        } catch (JWTVerificationException e) {
            return PaymentValidationResponse.builder().success(false).build();
        }
        return PaymentValidationResponse.builder().success(true).build();
    }
}

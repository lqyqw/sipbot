package com.example.sipbot.sip;

import com.example.sipbot.config.SipProperties;
import com.example.sipbot.media.AudioFileLoader;
import com.example.sipbot.media.PcmTtsGenerator;
import com.example.sipbot.media.RtpAudioStreamer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class SipAgent implements SipListener {

    private static final Logger log = LoggerFactory.getLogger(SipAgent.class);

    private final SipProperties properties;
    private final AudioFileLoader audioFileLoader;
    private final PcmTtsGenerator ttsGenerator;
    private final RtpAudioStreamer rtpAudioStreamer;

    private SipFactory sipFactory;
    private SipStack sipStack;
    private SipProvider sipProvider;
    private AddressFactory addressFactory;
    private HeaderFactory headerFactory;
    private MessageFactory messageFactory;

    private ClientTransaction lastRegister;
    private long cseq = 1;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<String, CallSession> callSessions = new ConcurrentHashMap<>();

    public SipAgent(SipProperties properties,
                    AudioFileLoader audioFileLoader,
                    PcmTtsGenerator ttsGenerator,
                    RtpAudioStreamer rtpAudioStreamer) {
        this.properties = properties;
        this.audioFileLoader = audioFileLoader;
        this.ttsGenerator = ttsGenerator;
        this.rtpAudioStreamer = rtpAudioStreamer;
    }

    @PostConstruct
    public void start() throws Exception {
        sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");

        Properties stackProps = new Properties();
        stackProps.setProperty("javax.sip.STACK_NAME", "sipbot-stack");
        stackProps.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "0");
        stackProps.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY", "gov.nist.javax.sip.stack.NioMessageProcessorFactory");

        sipStack = sipFactory.createSipStack(stackProps);
        addressFactory = sipFactory.createAddressFactory();
        headerFactory = sipFactory.createHeaderFactory();
        messageFactory = sipFactory.createMessageFactory();

        ListeningPoint listeningPoint = sipStack.createListeningPoint(properties.getLocalAddress(), properties.getPort(), properties.getTransport());
        sipProvider = sipStack.createSipProvider(listeningPoint);
        sipProvider.addSipListener(this);

        log.info("SIP stack started on {}:{} ({})", properties.getLocalAddress(), properties.getPort(), properties.getTransport());
        sendRegister(null);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        if (sipStack != null) {
            try {
                sipStack.deleteSipProvider(sipProvider);
                sipStack.stop();
            } catch (Exception e) {
                log.warn("Error stopping SIP stack", e);
            }
        }
    }

    private void sendRegister(AuthorizationHeader authHeader) throws ParseException, InvalidArgumentException, TransactionUnavailableException {
        SipURI requestUri = addressFactory.createSipURI(properties.getUsername(), properties.getDomain());
        Address fromAddress = addressFactory.createAddress(requestUri);
        FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, Long.toHexString(System.nanoTime()));
        ToHeader toHeader = headerFactory.createToHeader(fromAddress, null);

        ViaHeader viaHeader = headerFactory.createViaHeader(properties.getLocalAddress(), properties.getPort(), properties.getTransport(), null);
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);
        CallIdHeader callId = sipProvider.getNewCallId();
        CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(cseq++, Request.REGISTER);

        Request register = messageFactory.createRequest(requestUri, Request.REGISTER, callId, cSeqHeader, fromHeader, toHeader,
                java.util.Collections.singletonList(viaHeader), maxForwards);

        SipURI contactUri = addressFactory.createSipURI(properties.getUsername(), properties.getLocalAddress());
        contactUri.setPort(properties.getPort());
        contactUri.setTransportParam(properties.getTransport());
        Address contactAddress = addressFactory.createAddress(contactUri);
        ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
        register.addHeader(contactHeader);

        ExpiresHeader expires = headerFactory.createExpiresHeader(properties.getRegisterTtlSeconds());
        register.addHeader(expires);

        if (authHeader != null) {
            register.addHeader(authHeader);
        }

        lastRegister = sipProvider.getNewClientTransaction(register);
        lastRegister.sendRequest();
        log.info("Sent REGISTER to {} as {}", properties.getDomain(), properties.getUsername());
    }

    @Override
    public void processRequest(RequestEvent event) {
        Request request = event.getRequest();
        String method = request.getMethod();
        switch (method) {
            case Request.INVITE:
                handleInvite(event);
                break;
            case Request.ACK:
                handleAck(event);
                break;
            case Request.BYE:
                handleBye(event);
                break;
            case Request.CANCEL:
                handleCancel(event);
                break;
            default:
                log.info("Received unsupported request: {}", method);
        }
    }

    private void handleInvite(RequestEvent event) {
        try {
            Request request = event.getRequest();
            ServerTransaction serverTransaction = event.getServerTransaction();
            if (serverTransaction == null) {
                serverTransaction = sipProvider.getNewServerTransaction(request);
            }
            String callId = ((CallIdHeader) request.getHeader(CallIdHeader.NAME)).getCallId();
            byte[] rawContent = request.getRawContent();
            if (rawContent == null) {
                Response response = messageFactory.createResponse(Response.NOT_ACCEPTABLE_HERE, request);
                serverTransaction.sendResponse(response);
                log.warn("INVITE without SDP; rejecting call {}", callId);
                return;
            }
            String sdp = new String(rawContent, StandardCharsets.UTF_8);
            Response ringing = messageFactory.createResponse(Response.RINGING, request);
            serverTransaction.sendResponse(ringing);

            SdpDetails details = SdpParser.parse(sdp).orElse(null);
            if (details == null) {
                Response response = messageFactory.createResponse(Response.NOT_ACCEPTABLE_HERE, request);
                serverTransaction.sendResponse(response);
                log.warn("Unable to parse SDP; rejecting call {}", callId);
                return;
            }

            String sdpAnswer = SdpParser.buildAnswer(properties.getLocalAddress(), properties.getRtpPort());
            Response ok = messageFactory.createResponse(Response.OK, request);
            ok.addHeader(headerFactory.createContactHeader(addressFactory.createAddress("sip:" + properties.getUsername() + "@" + properties.getLocalAddress() + ":" + properties.getPort())));
            ok.setContent(sdpAnswer, headerFactory.createContentTypeHeader("application", "sdp"));
            serverTransaction.sendResponse(ok);

            Dialog dialog = serverTransaction.getDialog();
            CallSession session = new CallSession(callId, dialog, details.getRemoteHost(), details.getRemotePort(), serverTransaction);
            callSessions.put(dialog.getDialogId(), session);
            log.info("Accepted INVITE for call {} from {}:{}", callId, details.getRemoteHost(), details.getRemotePort());
        } catch (Exception e) {
            log.error("Error handling INVITE", e);
        }
    }

    private void handleAck(RequestEvent event) {
        Dialog dialog = event.getDialog();
        if (dialog == null) {
            return;
        }
        CallSession session = callSessions.get(dialog.getDialogId());
        if (session == null) {
            return;
        }
        log.info("ACK received for call {}. Starting media.", session.getCallId());
        byte[] audio = audioFileLoader.loadMuLawSamples(new java.io.File(properties.getAudioFile()).toPath());
        if (audio.length == 0) {
            audio = ttsGenerator.synthesizeMuLaw(properties.getTtsText());
        }
        Runnable byeTask = () -> sendBye(session);
        rtpAudioStreamer.stream(session.getRemoteHost(), session.getRemoteRtpPort(), properties.getRtpPort(), audio,
                properties.isHangupAfterPlayback() ? byeTask : null);
    }

    private void handleBye(RequestEvent event) {
        try {
            Response ok = messageFactory.createResponse(Response.OK, event.getRequest());
            ServerTransaction st = event.getServerTransaction();
            if (st != null) {
                st.sendResponse(ok);
            }
        } catch (Exception e) {
            log.warn("Failed to respond to BYE", e);
        } finally {
            Dialog dialog = event.getDialog();
            if (dialog != null) {
                callSessions.remove(dialog.getDialogId());
            }
        }
    }

    private void handleCancel(RequestEvent event) {
        try {
            Response ok = messageFactory.createResponse(Response.OK, event.getRequest());
            event.getServerTransaction().sendResponse(ok);
            Dialog dialog = event.getDialog();
            if (dialog != null) {
                Request original = dialog.createRequest(Request.BYE);
                dialog.sendRequest(sipProvider.getNewClientTransaction(original));
            }
        } catch (Exception e) {
            log.warn("Failed to handle CANCEL", e);
        }
    }

    private void sendBye(CallSession session) {
        try {
            if (session.getDialog() != null && session.getDialog().getState() != DialogState.TERMINATED) {
                Request bye = session.getDialog().createRequest(Request.BYE);
                ClientTransaction transaction = sipProvider.getNewClientTransaction(bye);
                session.getDialog().sendRequest(transaction);
                log.info("Sent BYE for call {}", session.getCallId());
            }
            callSessions.remove(session.getDialog().getDialogId());
        } catch (Exception e) {
            log.warn("Failed to send BYE for call {}", session.getCallId(), e);
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        int status = response.getStatusCode();
        CSeqHeader cSeqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        if (cSeqHeader == null || !Request.REGISTER.equalsIgnoreCase(cSeqHeader.getMethod())) {
            return;
        }

        if (status == Response.UNAUTHORIZED || status == Response.PROXY_AUTHENTICATION_REQUIRED) {
            log.info("Received {} for REGISTER, generating credentials", status);
            try {
                AuthorizationHeader authHeader = buildAuthHeader(response, (Request) lastRegister.getRequest().clone());
                sendRegister(authHeader);
            } catch (Exception e) {
                log.error("Failed to respond to authentication challenge", e);
            }
        } else if (status >= 200 && status < 300) {
            log.info("Registration successful ({}). Refreshing in {} seconds", status, properties.getRegisterTtlSeconds());
            long delay = Math.max(5, properties.getRegisterTtlSeconds() - 10);
            scheduler.schedule(() -> {
                try {
                    sendRegister(null);
                } catch (Exception e) {
                    log.error("Failed to refresh registration", e);
                }
            }, delay, TimeUnit.SECONDS);
        } else {
            log.warn("Unhandled REGISTER response: {}", status);
        }
    }

    private AuthorizationHeader buildAuthHeader(Response challenge, Request originalRequest) throws ParseException, NoSuchAlgorithmException, InvalidArgumentException {
        WWWAuthenticateHeader www = (WWWAuthenticateHeader) challenge.getHeader(WWWAuthenticateHeader.NAME);
        if (www == null) {
            return null;
        }
        String realm = www.getRealm();
        String nonce = www.getNonce();
        String uri = originalRequest.getRequestURI().toString();
        String username = properties.getUsername();
        String password = properties.getPassword();

        String ha1 = md5(username + ":" + realm + ":" + password);
        String ha2 = md5(originalRequest.getMethod() + ":" + uri);
        String response = md5(ha1 + ":" + nonce + ":" + ha2);

        AuthorizationHeader header = headerFactory.createAuthorizationHeader(www.getScheme());
        header.setUsername(username);
        header.setRealm(realm);
        header.setNonce(nonce);
        header.setURI((SipURI) originalRequest.getRequestURI());
        header.setResponse(response);
        header.setAlgorithm("MD5");
        return header;
    }

    private String md5(String value) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        log.warn("SIP transaction timed out: {}", timeoutEvent.getTimeout());
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        log.error("SIP IO exception", exceptionEvent);
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        log.debug("Transaction terminated: {}", transactionTerminatedEvent.getClientTransaction());
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        Dialog dialog = dialogTerminatedEvent.getDialog();
        if (dialog != null) {
            callSessions.remove(dialog.getDialogId());
        }
    }
}

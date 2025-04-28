/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.carbon.identity.authenticator.smsotp.test;

import org.apache.commons.lang.StringUtils;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.testng.PowerMockObjectFactory;
import org.powermock.reflect.Whitebox;
import org.testng.Assert;
import org.testng.IObjectFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.ObjectFactory;
import org.testng.annotations.Test;
import org.wso2.carbon.extension.identity.helper.FederatedAuthenticatorUtil;
import org.wso2.carbon.extension.identity.helper.IdentityHelperConstants;
import org.wso2.carbon.identity.application.authentication.framework.AuthenticatorFlowStatus;
import org.wso2.carbon.identity.application.authentication.framework.config.ConfigurationFacade;
import org.wso2.carbon.identity.application.authentication.framework.config.builder.FileBasedConfigurationBuilder;
import org.wso2.carbon.identity.application.authentication.framework.config.model.ApplicationConfig;
import org.wso2.carbon.identity.application.authentication.framework.config.model.AuthenticatorConfig;
import org.wso2.carbon.identity.application.authentication.framework.config.model.SequenceConfig;
import org.wso2.carbon.identity.application.authentication.framework.config.model.StepConfig;
import org.wso2.carbon.identity.application.authentication.framework.context.AuthenticationContext;
import org.wso2.carbon.identity.application.authentication.framework.exception.AuthenticationFailedException;
import org.wso2.carbon.identity.application.authentication.framework.exception.InvalidCredentialsException;
import org.wso2.carbon.identity.application.authentication.framework.exception.LogoutFailedException;
import org.wso2.carbon.identity.application.authentication.framework.internal.FrameworkServiceDataHolder;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatedUser;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatorData;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatorParamMetadata;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkConstants;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.application.common.model.ServiceProvider;
import org.wso2.carbon.identity.authenticator.smsotp.SMSOTPAuthenticator;
import org.wso2.carbon.identity.authenticator.smsotp.SMSOTPConstants;
import org.wso2.carbon.identity.authenticator.smsotp.SMSOTPUtils;
import org.wso2.carbon.identity.authenticator.smsotp.exception.SMSOTPException;
import org.wso2.carbon.identity.authenticator.smsotp.internal.SMSOTPServiceDataHolder;
import org.wso2.carbon.identity.central.log.mgt.utils.LoggerUtils;
import org.wso2.carbon.identity.core.ServiceURL;
import org.wso2.carbon.identity.core.ServiceURLBuilder;
import org.wso2.carbon.identity.core.URLBuilderException;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.event.services.IdentityEventService;
import org.wso2.carbon.user.api.Claim;
import org.wso2.carbon.user.api.ClaimManager;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.wso2.carbon.identity.authenticator.smsotp.SMSOTPConstants.REQUESTED_USER_MOBILE;
import static org.wso2.carbon.utils.multitenancy.MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ConfigurationFacade.class, SMSOTPUtils.class, FederatedAuthenticatorUtil.class, FrameworkUtils.class,
        IdentityTenantUtil.class, SMSOTPServiceDataHolder.class, ServiceURLBuilder.class, LoggerUtils.class,
        FileBasedConfigurationBuilder.class, FrameworkServiceDataHolder.class, IdentityUtil.class})
@PowerMockIgnore({"org.wso2.carbon.identity.application.common.model.User", "org.mockito.*", "javax.servlet.*"})
public class SMSOTPAuthenticatorTest {

    private static final long otpTime = 1608101321322l;
    public static String TENANT_DOMAIN = "wso2.com";
    public static String SUPER_TENANT = "carbon.super";

    private SMSOTPAuthenticator smsotpAuthenticator;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private HttpServletResponse httpServletResponse;

    @Spy
    private AuthenticationContext context;

    @Spy
    private SMSOTPAuthenticator spy;

    @Spy
    private AuthenticatorConfig spyAuthenticatorConfig;

    @Mock
    SMSOTPUtils smsotpUtils;

    @Mock
    private ConfigurationFacade configurationFacade;

    @Mock
    private UserStoreManager userStoreManager;

    @Mock
    private UserRealm userRealm;

    @Mock
    private RealmService realmService;

    @Mock
    private FileBasedConfigurationBuilder fileBasedConfigurationBuilder;

    @Mock
    private StepConfig stepConfig;

    @Mock
    private FrameworkServiceDataHolder frameworkServiceDataHolder;

    @Mock private ClaimManager claimManager;
    @Mock private Claim claim;
    @Mock private SMSOTPServiceDataHolder sMSOTPServiceDataHolder;
    @Mock private IdentityEventService identityEventService;
    @Mock private Enumeration<String> requestHeaders;
    @Mock private AuthenticatedUser authenticatedUser;

    @BeforeMethod
    public void setUp() throws Exception {
        smsotpAuthenticator = new SMSOTPAuthenticator();
        mockStatic(FileBasedConfigurationBuilder.class);
        mockStatic(FrameworkServiceDataHolder.class);
        mockStatic(SMSOTPServiceDataHolder.class);
        when(SMSOTPServiceDataHolder.getInstance()).thenReturn(sMSOTPServiceDataHolder);
        when(sMSOTPServiceDataHolder.getIdentityEventService()).thenReturn(identityEventService);
        when(httpServletRequest.getHeaderNames()).thenReturn(requestHeaders);
        initMocks(this);
        mockServiceURLBuilder();
        mockStatic(LoggerUtils.class);
        when(LoggerUtils.isDiagnosticLogsEnabled()).thenReturn(true);
    }

    @AfterMethod
    public void tearDown() {
        context.setRetrying(false);
        context.getProperties().clear();
    }


    @Test
    public void testGetFriendlyName() {
        Assert.assertEquals(smsotpAuthenticator.getFriendlyName(), SMSOTPConstants.AUTHENTICATOR_FRIENDLY_NAME);
    }

    @Test
    public void testGetName() {
        Assert.assertEquals(smsotpAuthenticator.getName(), SMSOTPConstants.AUTHENTICATOR_NAME);
    }

    @Test
    public void testRetryAuthenticationEnabled() throws Exception {
        SMSOTPAuthenticator smsotp = PowerMockito.spy(smsotpAuthenticator);
        Assert.assertTrue((Boolean) Whitebox.invokeMethod(smsotp, "retryAuthenticationEnabled"));
    }

    @Test
    public void testGetContextIdentifierPassed() {
        when(httpServletRequest.getParameter(FrameworkConstants.SESSION_DATA_KEY)).thenReturn
                ("0246893");
        Assert.assertEquals(smsotpAuthenticator.getContextIdentifier(httpServletRequest), "0246893");
    }

    @Test
    public void testCanHandleTrue() {
        when(httpServletRequest.getParameter(SMSOTPConstants.CODE)).thenReturn(null);
        when(httpServletRequest.getParameter(SMSOTPConstants.RESEND)).thenReturn("resendCode");
        Assert.assertEquals(smsotpAuthenticator.canHandle(httpServletRequest), true);
    }

    @Test
    public void testCanHandleFalse() {
        when(httpServletRequest.getParameter(SMSOTPConstants.CODE)).thenReturn(null);
        when(httpServletRequest.getParameter(SMSOTPConstants.RESEND)).thenReturn(null);
        when(httpServletRequest.getParameter(SMSOTPConstants.MOBILE_NUMBER)).thenReturn(null);
        Assert.assertEquals(smsotpAuthenticator.canHandle(httpServletRequest), false);
    }

    @Test
    public void testGetURL() throws Exception {
        SMSOTPAuthenticator smsotp = PowerMockito.spy(smsotpAuthenticator);
        Assert.assertEquals(Whitebox.invokeMethod(smsotp, "getURL",
                        SMSOTPConstants.LOGIN_PAGE, null),
                "authenticationendpoint/login.do?authenticators=SMSOTP");
    }

    @Test
    public void testGetURLwithQueryParams() throws Exception {
        SMSOTPAuthenticator smsotp = PowerMockito.spy(smsotpAuthenticator);
        Assert.assertEquals(Whitebox.invokeMethod(smsotp, "getURL",
                        SMSOTPConstants.LOGIN_PAGE, "n=John&n=Susan"),
                "authenticationendpoint/login.do?n=John&n=Susan&authenticators=SMSOTP");
    }


    @Test
    public void testGetMobileNumber() throws Exception {
        mockStatic(SMSOTPUtils.class);
        when(SMSOTPUtils.getMobileNumberForUsername(anyString())).thenReturn("0775968325");
        Assert.assertEquals(Whitebox.invokeMethod(smsotpAuthenticator, "getMobileNumber",
                httpServletRequest, httpServletResponse, any(AuthenticationContext.class),
                "Kanapriya", "queryParams"), "0775968325");
    }

    @Test
    public void testRedirectToErrorPage() throws Exception {
        mockStatic(SMSOTPUtils.class);
        AuthenticationContext authenticationContext = new AuthenticationContext();
        when(SMSOTPUtils.getErrorPageFromXMLFile(authenticationContext))
                .thenReturn("/authenticationendpoint/smsOtpError.jsp");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Whitebox.invokeMethod(smsotpAuthenticator, "redirectToErrorPage",
                httpServletResponse, authenticationContext, null, null);
        verify(httpServletResponse).sendRedirect(captor.capture());
        Assert.assertTrue(captor.getValue().contains(SMSOTPConstants.AUTHENTICATOR_NAME));
    }

    @Test
    public void testRedirectToMobileNumberReqPage() throws Exception {
        mockStatic(SMSOTPUtils.class);
        AuthenticationContext authenticationContext = new AuthenticationContext();
        when(SMSOTPUtils.isEnableMobileNoUpdate(authenticationContext)).thenReturn(true);
        when(SMSOTPUtils.getMobileNumberRequestPage(authenticationContext))
                .thenReturn("/authenticationendpoint/mobile.jsp");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Whitebox.invokeMethod(smsotpAuthenticator, "redirectToMobileNoReqPage",
                httpServletResponse, authenticationContext, null);
        verify(httpServletResponse).sendRedirect(captor.capture());
        Assert.assertTrue(captor.getValue().contains(SMSOTPConstants.AUTHENTICATOR_NAME));
    }

    @Test
    public void testCheckStatusCode() throws Exception {
        mockStatic(SMSOTPUtils.class);
        context.setProperty(SMSOTPConstants.STATUS_CODE, "");
        when(SMSOTPUtils.isRetryEnabled(context)).thenReturn(true);
        when(SMSOTPUtils.getLoginPageFromXMLFile(any(AuthenticationContext.class))).
                thenReturn("/authenticationendpoint/smsOtpError.jsp");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Whitebox.invokeMethod(smsotpAuthenticator, "checkStatusCode",
                httpServletResponse, context, null, SMSOTPConstants.ERROR_PAGE);
        verify(httpServletResponse).sendRedirect(captor.capture());
        Assert.assertTrue(captor.getValue().contains(SMSOTPConstants.AUTHENTICATOR_NAME));
    }

    @Test
    public void testCheckStatusCodeWithNullValue() throws Exception {
        mockStatic(SMSOTPUtils.class);
        context.setProperty(SMSOTPConstants.STATUS_CODE, null);
        when(SMSOTPUtils.isRetryEnabled(context)).thenReturn(true);
        when(SMSOTPUtils.getLoginPageFromXMLFile(any(AuthenticationContext.class))).
                thenReturn("/authenticationendpoint/smsOtp.jsp");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Whitebox.invokeMethod(smsotpAuthenticator, "checkStatusCode",
                httpServletResponse, context, null, SMSOTPConstants.ERROR_PAGE);
        verify(httpServletResponse).sendRedirect(captor.capture());
        Assert.assertTrue(captor.getValue().contains(SMSOTPConstants.AUTHENTICATOR_NAME));
    }

    @Test
    public void testCheckStatusCodeWithMismatch() throws Exception {
        mockStatic(SMSOTPUtils.class);
        context.setProperty(SMSOTPConstants.CODE_MISMATCH, "true");
        when(SMSOTPUtils.isRetryEnabled(context)).thenReturn(false);
        when(SMSOTPUtils.isEnableResendCode(context)).thenReturn(true);
        when(SMSOTPUtils.getLoginPageFromXMLFile(any(AuthenticationContext.class))).
                thenReturn("/authenticationendpoint/smsOtpError.jsp");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Whitebox.invokeMethod(smsotpAuthenticator, "checkStatusCode",
                httpServletResponse, context, null, SMSOTPConstants.ERROR_PAGE);
        verify(httpServletResponse).sendRedirect(captor.capture());
        Assert.assertTrue(captor.getValue().contains(SMSOTPConstants.ERROR_CODE_MISMATCH));
    }

    @Test
    public void testCheckStatusCodeWithTokenExpired() throws Exception {
        mockStatic(SMSOTPUtils.class);
        context.setProperty(SMSOTPConstants.TOKEN_EXPIRED, "token.expired");
        when(SMSOTPUtils.isEnableResendCode(context)).thenReturn(true);
        when(SMSOTPUtils.isRetryEnabled(context)).thenReturn(true);
        when(SMSOTPUtils.getLoginPageFromXMLFile(any(AuthenticationContext.class))).
                thenReturn("/authenticationendpoint/smsOtp.jsp");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Whitebox.invokeMethod(smsotpAuthenticator, "checkStatusCode",
                httpServletResponse, context, null, SMSOTPConstants.SMS_LOGIN_PAGE);
        verify(httpServletResponse).sendRedirect(captor.capture());
        Assert.assertTrue(captor.getValue().contains(SMSOTPConstants.TOKEN_EXPIRED_VALUE));
    }

    @Test
    public void testProcessSMSOTPFlow() throws Exception {
        mockStatic(SMSOTPUtils.class);
        mockStatic(ConfigurationFacade.class);
        when(SMSOTPUtils.isSMSOTPDisableForLocalUser("John", context)).thenReturn(true);
        when(SMSOTPUtils.getErrorPageFromXMLFile(any(AuthenticationContext.class))).
                thenReturn(SMSOTPConstants.ERROR_PAGE);
        when(SMSOTPUtils.isEnableMobileNoUpdate(any(AuthenticationContext.class))).thenReturn(true);
        when(ConfigurationFacade.getInstance()).thenReturn(configurationFacade);
        when(configurationFacade.getAuthenticationEndpointURL()).thenReturn("/authenticationendpoint/login.do");
        context.setProperty(SMSOTPConstants.MOBILE_NUMBER_UPDATE_FAILURE, "true");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Whitebox.invokeMethod(smsotpAuthenticator, "processSMSOTPFlow", context,
                httpServletRequest, httpServletResponse, true, "John@carbon.super", "", "carbon.super", SMSOTPConstants
                        .ERROR_PAGE);
        verify(httpServletResponse).sendRedirect(captor.capture());
        Assert.assertTrue(captor.getValue().contains(SMSOTPConstants.AUTHENTICATOR_NAME));
    }

    @Test
    public void testSendOTPDirectlyToMobile() throws Exception {
        mockStatic(SMSOTPUtils.class);
        when(SMSOTPUtils.isSendOTPDirectlyToMobile(context)).thenReturn(true);
        when(SMSOTPUtils.getMobileNumberRequestPage(any(AuthenticationContext.class))).
                thenReturn("/authenticationendpoint/mobile.jsp");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Whitebox.invokeMethod(smsotpAuthenticator, "processSMSOTPFlow", context,
                httpServletRequest, httpServletResponse, false, "John@carbon.super", "", "carbon.super", SMSOTPConstants
                        .ERROR_PAGE);
        verify(httpServletResponse).sendRedirect(captor.capture());
        Assert.assertTrue(captor.getValue().contains(SMSOTPConstants.AUTHENTICATOR_NAME));
    }

    @Test
    public void testProcessSMSOTPDisableFlow() throws Exception {
        mockStatic(SMSOTPUtils.class);
        when(SMSOTPUtils.isSendOTPDirectlyToMobile(context)).thenReturn(false);
        when(SMSOTPUtils.getErrorPageFromXMLFile(any(AuthenticationContext.class))).
                thenReturn(SMSOTPConstants.ERROR_PAGE);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Whitebox.invokeMethod(smsotpAuthenticator, "processSMSOTPFlow", context,
                httpServletRequest, httpServletResponse, false, "John@carbon.super", "", "carbon.super", SMSOTPConstants
                        .ERROR_PAGE);
        verify(httpServletResponse).sendRedirect(captor.capture());
        Assert.assertTrue(captor.getValue().contains(SMSOTPConstants.SEND_OTP_DIRECTLY_DISABLE));
    }

    @Test
    public void testProcessWithLogoutTrue() throws AuthenticationFailedException, LogoutFailedException {
        when(context.isLogoutRequest()).thenReturn(true);
        AuthenticatorFlowStatus status = smsotpAuthenticator.process(httpServletRequest, httpServletResponse, context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.SUCCESS_COMPLETED);
    }

    @Test
    public void testProcessWithLogoutFalse() throws Exception {
        mockStatic(FederatedAuthenticatorUtil.class);
        mockStatic(SMSOTPUtils.class);
        mockStatic(FrameworkUtils.class);
        when(context.isLogoutRequest()).thenReturn(false);
        when(httpServletRequest.getParameter(SMSOTPConstants.MOBILE_NUMBER)).thenReturn("true");
        context.setTenantDomain("carbon.super");
        Map<String, String> parameters = new HashMap<>();
        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        parameters.put(SMSOTPConstants.IS_SMSOTP_MANDATORY, "true");
        authenticatorConfig.setParameterMap(parameters);
        when(stepConfig.getAuthenticatedAutenticator()).thenReturn(authenticatorConfig);
        when(FileBasedConfigurationBuilder.getInstance()).thenReturn(fileBasedConfigurationBuilder);
        when(fileBasedConfigurationBuilder.getAuthenticatorBean(anyString())).thenReturn(authenticatorConfig);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier("admin");
        when(context.getProperty(SMSOTPConstants.OTP_GENERATED_TIME)).thenReturn(otpTime);
        authenticatedUser.setUserName("admin");
        authenticatedUser.setTenantDomain("carbon.super");
        setStepConfigWithSmsOTPAuthenticator(authenticatorConfig, authenticatedUser, context);
        when(stepConfig.getAuthenticatedUser()).thenReturn(authenticatedUser);
        when((AuthenticatedUser) context.getProperty(SMSOTPConstants.AUTHENTICATED_USER)).thenReturn(authenticatedUser);
        FederatedAuthenticatorUtil.setUsernameFromFirstStep(context);
        when(SMSOTPUtils.isSMSOTPMandatory(context)).thenReturn(true);
        when(SMSOTPUtils.getErrorPageFromXMLFile(context)).thenReturn(SMSOTPConstants.ERROR_PAGE);
        when(SMSOTPUtils.isSendOTPDirectlyToMobile(context)).thenReturn(false);
        when(FrameworkUtils.getQueryStringWithFrameworkContextId(context.getQueryParams(),
                context.getCallerSessionKey(), context.getContextIdentifier())).thenReturn(null);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Whitebox.invokeMethod(smsotpAuthenticator, "processSMSOTPFlow", context,
                httpServletRequest, httpServletResponse, false, "John@carbon.super", "", "carbon.super", SMSOTPConstants
                        .ERROR_PAGE);
        verify(httpServletResponse).sendRedirect(captor.capture());
        AuthenticatorFlowStatus status = spy.process(httpServletRequest, httpServletResponse, context);
        Assert.assertTrue(captor.getValue().contains(SMSOTPConstants.SEND_OTP_DIRECTLY_DISABLE));
        Assert.assertEquals(status, AuthenticatorFlowStatus.INCOMPLETE);
    }

    @Test
    public void testProcessWithLogout() throws AuthenticationFailedException, LogoutFailedException {
        mockStatic(FederatedAuthenticatorUtil.class);
        mockStatic(SMSOTPUtils.class);
        mockStatic(FrameworkUtils.class);
        mockStatic(ConfigurationFacade.class);
        when(ConfigurationFacade.getInstance()).thenReturn(configurationFacade);
        when(context.isLogoutRequest()).thenReturn(false);
        when(httpServletRequest.getParameter(SMSOTPConstants.CODE)).thenReturn("");
        context.setTenantDomain("carbon.super");
        Map<String, String> parameters = new HashMap<>();
        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        authenticatorConfig.setParameterMap(parameters);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier("admin");
        authenticatedUser.setUserName("testUser");
        authenticatedUser.setTenantDomain("carbon.super");
        setStepConfigWithSmsOTPAuthenticator(authenticatorConfig, authenticatedUser, context);
        when(stepConfig.getAuthenticatedUser()).thenReturn(authenticatedUser);
        context.setProperty(SMSOTPConstants.SENT_OTP_TOKEN_TIME, otpTime);
        when((AuthenticatedUser) context.getProperty(SMSOTPConstants.AUTHENTICATED_USER)).thenReturn(authenticatedUser);
        FederatedAuthenticatorUtil.setUsernameFromFirstStep(context);
        when(SMSOTPUtils.isSMSOTPMandatory(context)).thenReturn(true);
        when(SMSOTPUtils.getErrorPageFromXMLFile(context)).thenReturn(SMSOTPConstants.ERROR_PAGE);
        when(SMSOTPUtils.isSendOTPDirectlyToMobile(context)).thenReturn(false);
        when(FrameworkUtils.getQueryStringWithFrameworkContextId(context.getQueryParams(),
                context.getCallerSessionKey(), context.getContextIdentifier())).thenReturn(null);
        when(SMSOTPUtils.getBackupCode(context)).thenReturn("false");
        when(configurationFacade.getAuthenticationEndpointURL()).thenReturn("dummyLoginPageURL");
        when(FileBasedConfigurationBuilder.getInstance()).thenReturn(fileBasedConfigurationBuilder);
        when(fileBasedConfigurationBuilder.getAuthenticatorBean(anyString())).thenReturn(authenticatorConfig);
        AuthenticatorFlowStatus status = spy.process(httpServletRequest, httpServletResponse, context);
        Assert.assertEquals(status, AuthenticatorFlowStatus.INCOMPLETE);
    }

    @Test
    public void testInitiateAuthenticationRequestWithSMSOTPMandatory() throws Exception {

        setupInitiateAuthenticationRequestInitialMocks();
        when(FederatedAuthenticatorUtil.isUserExistInUserStore(anyString())).thenReturn(false);
        when(SMSOTPUtils.isSMSOTPMandatory(context)).thenReturn(true);
        when(SMSOTPUtils.isSendOTPDirectlyToMobile(context)).thenReturn(false);
        when(SMSOTPUtils.getBackupCode(context)).thenReturn("false");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Whitebox.invokeMethod(smsotpAuthenticator, "initiateAuthenticationRequest",
                httpServletRequest, httpServletResponse, context);
        verify(httpServletResponse).sendRedirect(captor.capture());
        Assert.assertTrue(captor.getValue().contains(SMSOTPConstants.SEND_OTP_DIRECTLY_DISABLE));
    }

    @Test
    public void testInitiateAuthenticationRequestWithSMSOTPMandatoryAndResendCode() throws Exception {

        setupInitiateAuthenticationRequestInitialMocks();
        String initialOtp = "00000";
        context.setProperty(SMSOTPConstants.OTP_TOKEN, initialOtp);
        context.setRetrying(true);
        when(SMSOTPUtils.getMaxResendAttempts(context)).thenReturn(Optional.of(2));
        when(SMSOTPUtils.isSMSOTPMandatory(context)).thenReturn(true);
        when(httpServletRequest.getParameter(SMSOTPConstants.RESEND)).thenReturn("true");

        Whitebox.invokeMethod(smsotpAuthenticator, "initiateAuthenticationRequest",
                httpServletRequest, httpServletResponse, context);
        Assert.assertNotEquals(context.getProperty(SMSOTPConstants.OTP_TOKEN), initialOtp);
        Assert.assertEquals(context.getProperty(SMSOTPConstants.OTP_RESEND_ATTEMPTS), 1);
    }

    @Test
    public void testInitiateAuthenticationRequestWithSMSOTPMandatoryAndExceededMaxResendCode() throws Exception {

        setupInitiateAuthenticationRequestInitialMocks();
        String prevOtp = "00000";
        context.setProperty(SMSOTPConstants.OTP_TOKEN, prevOtp);
        context.setProperty(SMSOTPConstants.OTP_RESEND_ATTEMPTS, 2);
        context.setRetrying(true);
        when(SMSOTPUtils.isSMSOTPMandatory(context)).thenReturn(true);
        when(SMSOTPUtils.getMaxResendAttempts(context)).thenReturn(Optional.of(2));
        when(httpServletRequest.getParameter(SMSOTPConstants.RESEND)).thenReturn("true");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Whitebox.invokeMethod(smsotpAuthenticator, "initiateAuthenticationRequest",
                httpServletRequest, httpServletResponse, context);
        verify(httpServletResponse).sendRedirect(captor.capture());
        Assert.assertTrue(captor.getValue().contains(SMSOTPConstants.ERROR_USER_RESEND_COUNT_EXCEEDED));
    }

    @Test
    public void testInitiateAuthenticationRequestWithSMSOTPOptional() throws Exception {

        setupInitiateAuthenticationRequestInitialMocks();
        context.setProperty(SMSOTPConstants.TOKEN_EXPIRED, "token.expired");
        context.setRetrying(true);
        when(SMSOTPUtils.isRetryEnabled(context)).thenReturn(true);
        when(httpServletRequest.getParameter(SMSOTPConstants.RESEND)).thenReturn("false");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Whitebox.invokeMethod(smsotpAuthenticator, "initiateAuthenticationRequest",
                httpServletRequest, httpServletResponse, context);
        verify(httpServletResponse).sendRedirect(captor.capture());
        Assert.assertTrue(captor.getValue().contains(SMSOTPConstants.TOKEN_EXPIRED_VALUE));
    }

    @Test
    public void testInitiateAuthenticationRequestWithSMSOTP() throws Exception {

        setupInitiateAuthenticationRequestInitialMocks();

        Whitebox.invokeMethod(smsotpAuthenticator, "initiateAuthenticationRequest",
                httpServletRequest, httpServletResponse, context);
        Assert.assertNotNull(context.getProperty(SMSOTPConstants.OTP_TOKEN));
        Assert.assertNotNull(context.getProperty(SMSOTPConstants.SENT_OTP_TOKEN_TIME));
        Assert.assertNotNull(context.getProperty(SMSOTPConstants.TOKEN_VALIDITY_TIME));
        Assert.assertNull(context.getProperty(SMSOTPConstants.OTP_RESEND_ATTEMPTS));
    }

    @Test
    public void testInitiateAuthenticationRequestWithSMSOTPResend() throws Exception {

        setupInitiateAuthenticationRequestInitialMocks();
        String prevOtp = "00000";
        int prevResendAttempts = 1;
        context.setProperty(SMSOTPConstants.OTP_TOKEN, prevOtp);
        context.setProperty(SMSOTPConstants.OTP_RESEND_ATTEMPTS, prevResendAttempts);
        context.setRetrying(true);
        when(SMSOTPUtils.getMaxResendAttempts(context)).thenReturn(Optional.of(2));
        when(httpServletRequest.getParameter(SMSOTPConstants.RESEND)).thenReturn("true");

        Whitebox.invokeMethod(smsotpAuthenticator, "initiateAuthenticationRequest",
                httpServletRequest, httpServletResponse, context);
        Assert.assertNotEquals(context.getProperty(SMSOTPConstants.OTP_TOKEN), prevOtp);
        Assert.assertEquals(context.getProperty(SMSOTPConstants.OTP_RESEND_ATTEMPTS), prevResendAttempts + 1);
    }

    @Test
    public void testInitiateAuthenticationRequestWithSMSOTPExceededMaxResend() throws Exception {

        setupInitiateAuthenticationRequestInitialMocks();
        String prevOtp = "00000";
        int prevResendAttempts = 2;
        context.setProperty(SMSOTPConstants.OTP_TOKEN, prevOtp);
        context.setProperty(SMSOTPConstants.OTP_RESEND_ATTEMPTS, prevResendAttempts);
        context.setRetrying(true);
        when(SMSOTPUtils.getMaxResendAttempts(context)).thenReturn(Optional.of(2));
        when(httpServletRequest.getParameter(SMSOTPConstants.RESEND)).thenReturn("true");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Whitebox.invokeMethod(smsotpAuthenticator, "initiateAuthenticationRequest",
                httpServletRequest, httpServletResponse, context);
        verify(httpServletResponse).sendRedirect(captor.capture());
        Assert.assertTrue(captor.getValue().contains(SMSOTPConstants.ERROR_USER_RESEND_COUNT_EXCEEDED));
    }

    private void setupInitiateAuthenticationRequestInitialMocks() throws AuthenticationFailedException,
            UserStoreException, SMSOTPException {

        mockStatic(FederatedAuthenticatorUtil.class);
        FederatedAuthenticatorUtil.setUsernameFromFirstStep(context);
        when(FederatedAuthenticatorUtil.isUserExistInUserStore(anyString())).thenReturn(true);

        mockStatic(SMSOTPUtils.class);
        when(SMSOTPUtils.isSMSOTPMandatory(context)).thenReturn(false);
        when(SMSOTPUtils.getErrorPageFromXMLFile(context)).thenReturn(SMSOTPConstants.ERROR_PAGE);
        when(SMSOTPUtils.getLoginPageFromXMLFile(context)).thenReturn(SMSOTPConstants.LOGIN_PAGE);
        when(SMSOTPUtils.getMobileNumberForUsername(anyString())).thenReturn("0778965320");

        mockStatic(FrameworkUtils.class);
        when(FrameworkUtils.getQueryStringWithFrameworkContextId(context.getQueryParams(),
                context.getCallerSessionKey(), context.getContextIdentifier())).thenReturn(null);

        context.setTenantDomain("carbon.super");

        Map<String, String> parameters = new HashMap<>();
        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        authenticatorConfig.setParameterMap(parameters);
        when(FileBasedConfigurationBuilder.getInstance()).thenReturn(fileBasedConfigurationBuilder);
        when(fileBasedConfigurationBuilder.getAuthenticatorBean(anyString())).thenReturn(authenticatorConfig);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier("admin");
        authenticatedUser.setUserName("admin");
        authenticatedUser.setTenantDomain("carbon.super");
        setStepConfigWithSmsOTPAuthenticator(authenticatorConfig, authenticatedUser, context);
        when(stepConfig.getAuthenticatedUser()).thenReturn(authenticatedUser);
        when((AuthenticatedUser) context.getProperty(SMSOTPConstants.AUTHENTICATED_USER)).thenReturn(authenticatedUser);

        when(httpServletRequest.getParameter(SMSOTPConstants.RESEND)).thenReturn("false");
    }

    @Test(expectedExceptions = {AuthenticationFailedException.class})
    public void testInitiateAuthenticationRequestWithoutAuthenticatedUser() throws Exception {
        mockStatic(FederatedAuthenticatorUtil.class);
        mockStatic(SMSOTPUtils.class);
        mockStatic(FrameworkUtils.class);
        context.setTenantDomain("carbon.super");
        FederatedAuthenticatorUtil.setUsernameFromFirstStep(context);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("usecase", "test");
        spyAuthenticatorConfig.setParameterMap(parameters);
        when(stepConfig.getAuthenticatedAutenticator()).thenReturn(spyAuthenticatorConfig);
        setStepConfigWithSmsOTPAuthenticator(spyAuthenticatorConfig, null, context);
        when((AuthenticatedUser) context.getProperty(SMSOTPConstants.AUTHENTICATED_USER)).thenReturn(null);
        when(FileBasedConfigurationBuilder.getInstance()).thenReturn(fileBasedConfigurationBuilder);
        when(fileBasedConfigurationBuilder.getAuthenticatorBean(anyString())).thenReturn(spyAuthenticatorConfig);
        when(FrameworkServiceDataHolder.getInstance()).thenReturn(frameworkServiceDataHolder);
        when(frameworkServiceDataHolder.getRealmService()).thenReturn(realmService);
        when(context.getProperty(IdentityHelperConstants.GET_PROPERTY_FROM_REGISTRY)).thenReturn(null);
        when(context.getProperty(SMSOTPConstants.USE_CASE)).thenReturn("test");
        Whitebox.invokeMethod(smsotpAuthenticator, "initiateAuthenticationRequest",
                httpServletRequest, httpServletResponse, context);
    }

    @Test(expectedExceptions = {InvalidCredentialsException.class})
    public void testProcessAuthenticationResponseWithoutOTPCode() throws Exception {

        mockStatic(SMSOTPUtils.class);
        when(httpServletRequest.getParameter(SMSOTPConstants.CODE)).thenReturn("");
        when(SMSOTPUtils.isLocalUser(context)).thenReturn(true);
        Whitebox.invokeMethod(smsotpAuthenticator, "processAuthenticationResponse",
                httpServletRequest, httpServletResponse, context);
    }

    @Test(expectedExceptions = {InvalidCredentialsException.class})
    public void testProcessAuthenticationResponseWithResend() throws Exception {

        mockStatic(SMSOTPUtils.class);
        when(httpServletRequest.getParameter(SMSOTPConstants.CODE)).thenReturn("123456");
        when(httpServletRequest.getParameter(SMSOTPConstants.RESEND)).thenReturn("true");
        when(SMSOTPUtils.isLocalUser(context)).thenReturn(true);
        Whitebox.invokeMethod(smsotpAuthenticator, "processAuthenticationResponse",
                httpServletRequest, httpServletResponse, context);
    }

    @Test
    public void testProcessAuthenticationResponse() throws Exception {

        mockStatic(SMSOTPUtils.class);
        mockStatic(IdentityTenantUtil.class);
        when(httpServletRequest.getParameter(SMSOTPConstants.CODE)).thenReturn("123456");
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier("admin");
        authenticatedUser.setUserId("4b4414e1-916b-4475-aaee-6b0751c29ff6");
        authenticatedUser.setUserName("admin");
        authenticatedUser.setTenantDomain("carbon.super");
        StepConfig stepConfig = new StepConfig();
        stepConfig.setSubjectAttributeStep(true);
        stepConfig.setAuthenticatedUser(authenticatedUser);
        context.setProperty(SMSOTPConstants.CODE_MISMATCH, false);
        context.setProperty(SMSOTPConstants.OTP_TOKEN,"123456");
        context.setProperty(SMSOTPConstants.TOKEN_VALIDITY_TIME,"");
        Map<String, String> parameters = new HashMap<>();
        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        authenticatorConfig.setParameterMap(parameters);
        setStepConfigWithSmsOTPAuthenticator(authenticatorConfig, authenticatedUser, context);
        context.setSequenceConfig(new SequenceConfig());
        context.getSequenceConfig().getStepMap().put(1, stepConfig);
        Whitebox.invokeMethod(smsotpAuthenticator, "getAuthenticatedUser",
                context);
        Property property = new Property();
        property.setName(SMSOTPConstants.PROPERTY_ACCOUNT_LOCK_ON_FAILURE);
        property.setValue("true");
        when(SMSOTPUtils.getAccountLockConnectorConfigs(authenticatedUser.getTenantDomain()))
                .thenReturn(new Property[]{property});
        when(IdentityTenantUtil.getTenantId("carbon.super")).thenReturn(-1234);
        when(IdentityTenantUtil.getRealmService()).thenReturn(realmService);
        when(realmService.getTenantUserRealm(-1234)).thenReturn(userRealm);
        when(userRealm.getUserStoreManager()).thenReturn(userStoreManager);

        Whitebox.invokeMethod(smsotpAuthenticator, "processAuthenticationResponse",
                httpServletRequest, httpServletResponse, context);
    }

    @Test
    public void testProcessAuthenticationResponseWithValidBackupCode() throws Exception {
        mockStatic(IdentityTenantUtil.class);
        mockStatic(SMSOTPUtils.class);
        when(httpServletRequest.getParameter(SMSOTPConstants.CODE)).thenReturn("123456");
        context.setProperty(SMSOTPConstants.OTP_TOKEN, "123456");
        context.setProperty(SMSOTPConstants.USER_NAME, "admin");
        Map<String, String> parameters = new HashMap<>();
        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        authenticatorConfig.setParameterMap(parameters);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier("admin");
        authenticatedUser.setUserName("admin");
        authenticatedUser.setTenantDomain("carbon.super");
        setStepConfigWithSmsOTPAuthenticator(authenticatorConfig, authenticatedUser, context);
        when((AuthenticatedUser) context.getProperty(SMSOTPConstants.AUTHENTICATED_USER)).thenReturn(authenticatedUser);
        when(SMSOTPUtils.getBackupCode(context)).thenReturn("true");

        when(IdentityTenantUtil.getTenantId("carbon.super")).thenReturn(-1234);
        when(IdentityTenantUtil.getRealmService()).thenReturn(realmService);
        when(realmService.getTenantUserRealm(-1234)).thenReturn(userRealm);
        when(userRealm.getUserStoreManager()).thenReturn(userStoreManager);
        when(userStoreManager.getUserClaimValues(anyString(), eq(new String[]{SMSOTPConstants.SAVED_OTP_LIST}),
                anyString())).thenReturn(Collections.singletonMap(SMSOTPConstants.SAVED_OTP_LIST, "123456,789123"));
        mockStatic(FrameworkUtils.class);
        when(FrameworkUtils.getMultiAttributeSeparator()).thenReturn(",");

        Property property = new Property();
        property.setName(SMSOTPConstants.PROPERTY_ACCOUNT_LOCK_ON_FAILURE);
        property.setValue("true");
        when(SMSOTPUtils.getAccountLockConnectorConfigs(authenticatedUser.getTenantDomain()))
                .thenReturn(new Property[]{property});
        when(SMSOTPUtils.isLocalUser(context)).thenReturn(true);
        when(userStoreManager.getClaimManager()).thenReturn(claimManager);
        when(userStoreManager.getClaimManager().getClaim(SMSOTPConstants.SAVED_OTP_LIST)).thenReturn(claim);
        when(context.getProperty(SMSOTPConstants.CODE_MISMATCH)).thenReturn(false);

        Whitebox.invokeMethod(smsotpAuthenticator, "processAuthenticationResponse",
                httpServletRequest, httpServletResponse, context);
    }

    @Test(expectedExceptions = {AuthenticationFailedException.class})
    public void testProcessAuthenticationResponseWithCodeMismatch() throws Exception {
        mockStatic(SMSOTPUtils.class);
        mockStatic(IdentityTenantUtil.class);
        when(httpServletRequest.getParameter(SMSOTPConstants.CODE)).thenReturn("123456");
        context.setProperty(SMSOTPConstants.OTP_TOKEN,"123");
        context.setProperty(SMSOTPConstants.USER_NAME,"admin");
        Map<String, String> parameters = new HashMap<>();
        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        authenticatorConfig.setParameterMap(parameters);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier("admin");
        authenticatedUser.setTenantDomain("carbon.super");
        setStepConfigWithSmsOTPAuthenticator(authenticatorConfig, authenticatedUser, context);
        when((AuthenticatedUser) context.getProperty(SMSOTPConstants.AUTHENTICATED_USER)).thenReturn(authenticatedUser);
        when(SMSOTPUtils.getBackupCode(context)).thenReturn("false");

        Property property = new Property();
        property.setName(SMSOTPConstants.PROPERTY_ACCOUNT_LOCK_ON_FAILURE);
        property.setValue("true");
        when(SMSOTPUtils.getAccountLockConnectorConfigs(authenticatedUser.getTenantDomain()))
                .thenReturn(new Property[]{property});

        when(IdentityTenantUtil.getTenantId("carbon.super")).thenReturn(-1234);
        when(IdentityTenantUtil.getRealmService()).thenReturn(realmService);
        when(realmService.getTenantUserRealm(-1234)).thenReturn(userRealm);
        when(userRealm.getUserStoreManager()).thenReturn(userStoreManager);

        Whitebox.invokeMethod(smsotpAuthenticator, "processAuthenticationResponse",
                httpServletRequest, httpServletResponse, context);
    }

    @Test
    public void testCheckWithBackUpCodes() throws Exception {
        mockStatic(IdentityTenantUtil.class);
        context.setProperty(SMSOTPConstants.USER_NAME,"admin");
        when(IdentityTenantUtil.getTenantId("carbon.super")).thenReturn(-1234);
        when(IdentityTenantUtil.getRealmService()).thenReturn(realmService);
        when(realmService.getTenantUserRealm(-1234)).thenReturn(userRealm);
        when(userRealm.getUserStoreManager()).thenReturn(userStoreManager);
        Map<String, String> parameters = new HashMap<>();
        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        authenticatorConfig.setParameterMap(parameters);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier("admin");
        authenticatedUser.setTenantDomain("carbon.super");
        setStepConfigWithSmsOTPAuthenticator(authenticatorConfig, authenticatedUser, context);
        when((AuthenticatedUser) context.getProperty(SMSOTPConstants.AUTHENTICATED_USER)).thenReturn(authenticatedUser);
        when(userRealm.getUserStoreManager()
                .getUserClaimValues(MultitenantUtils.getTenantAwareUsername("admin"),
                        new String[]{SMSOTPConstants.SAVED_OTP_LIST}, null))
                .thenReturn(Collections.singletonMap(SMSOTPConstants.SAVED_OTP_LIST, "12345,4568,1234,7896"));
        AuthenticatedUser user = (AuthenticatedUser) context.getProperty(SMSOTPConstants.AUTHENTICATED_USER);
        mockStatic(FrameworkUtils.class);
        when (FrameworkUtils.getMultiAttributeSeparator()).thenReturn(",");
        Whitebox.invokeMethod(smsotpAuthenticator, "checkWithBackUpCodes",
                context,"1234",user);
    }

    public void testCheckWithInvalidBackUpCodes() throws Exception {

        mockStatic(IdentityTenantUtil.class);
        mockStatic(SMSOTPUtils.class);
        context.setProperty(SMSOTPConstants.USER_NAME,"admin");
        when(IdentityTenantUtil.getTenantId("carbon.super")).thenReturn(-1234);
        when(IdentityTenantUtil.getRealmService()).thenReturn(realmService);
        when(realmService.getTenantUserRealm(-1234)).thenReturn(userRealm);
        when(userRealm.getUserStoreManager()).thenReturn(userStoreManager);
        Map<String, String> parameters = new HashMap<>();
        AuthenticatorConfig authenticatorConfig = new AuthenticatorConfig();
        authenticatorConfig.setParameterMap(parameters);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setAuthenticatedSubjectIdentifier("admin");
        authenticatedUser.setUserName("admin");
        authenticatedUser.setTenantDomain("carbon.super");
        setStepConfigWithSmsOTPAuthenticator(authenticatorConfig, authenticatedUser, context);
        when((AuthenticatedUser) context.getProperty(SMSOTPConstants.AUTHENTICATED_USER)).thenReturn(authenticatedUser);
        mockStatic(FrameworkUtils.class);
        when (FrameworkUtils.getMultiAttributeSeparator()).thenReturn(",");
        when(userRealm.getUserStoreManager()
                .getUserClaimValues(MultitenantUtils.getTenantAwareUsername("admin"),
                        new String[]{SMSOTPConstants.SAVED_OTP_LIST}, null))
                .thenReturn(Collections.singletonMap(SMSOTPConstants.SAVED_OTP_LIST, "12345,4568,1234,7896"));
        Whitebox.invokeMethod(smsotpAuthenticator, "checkWithBackUpCodes",
                context, "45698789", authenticatedUser);
    }

    @Test
    public void testGetScreenAttribute() throws UserStoreException, AuthenticationFailedException {
        mockStatic(IdentityTenantUtil.class);
        mockStatic(SMSOTPUtils.class);
        when(SMSOTPUtils.getScreenUserAttribute(context)).thenReturn
                ("http://wso2.org/claims/mobile");
        when(IdentityTenantUtil.getTenantId("carbon.super")).thenReturn(-1234);
        when(IdentityTenantUtil.getRealmService()).thenReturn(realmService);
        when(realmService.getTenantUserRealm(-1234)).thenReturn(userRealm);
        when(userRealm.getUserStoreManager()).thenReturn(userStoreManager);
        when(userRealm.getUserStoreManager()
                .getUserClaimValue("admin", "http://wso2.org/claims/mobile", null)).thenReturn("0778965231");
        when(SMSOTPUtils.getNoOfDigits(context)).thenReturn("4");

        // with forward order
        Assert.assertEquals(smsotpAuthenticator.getScreenAttribute(context,userRealm,"admin"),"0778******");

        // with backward order
        when(SMSOTPUtils.getDigitsOrder(context)).thenReturn("backward");
        Assert.assertEquals(smsotpAuthenticator.getScreenAttribute(context,userRealm,"admin"),"******5231");
    }

    @Test
    public void testGetScreenAttributeWhenMobileRequest() throws UserStoreException {

        mockStatic(IdentityTenantUtil.class);
        mockStatic(SMSOTPUtils.class);
        when(SMSOTPUtils.getScreenUserAttribute(context)).thenReturn
                ("http://wso2.org/claims/mobile");
        when(context.getProperty(REQUESTED_USER_MOBILE)).thenReturn("0778899889");
        when(IdentityTenantUtil.getTenantId("carbon.super")).thenReturn(-1234);
        when(IdentityTenantUtil.getRealmService()).thenReturn(realmService);
        when(realmService.getTenantUserRealm(-1234)).thenReturn(userRealm);
        when(userRealm.getUserStoreManager()).thenReturn(userStoreManager);
        when(userRealm.getUserStoreManager()
                .getUserClaimValue("admin", "http://wso2.org/claims/mobile", null)).thenReturn(null);
        when(SMSOTPUtils.getNoOfDigits(context)).thenReturn("4");

        // with forward order
        Assert.assertEquals(smsotpAuthenticator.getScreenAttribute(context, userRealm, "admin"), "0778******");

        // with backward order
        when(SMSOTPUtils.getDigitsOrder(context)).thenReturn("backward");
        Assert.assertEquals(smsotpAuthenticator.getScreenAttribute(context, userRealm, "admin"), "******9889");
    }

    @Test(expectedExceptions = {SMSOTPException.class})
    public void testUpdateMobileNumberForUsername() throws Exception {
        mockStatic(IdentityTenantUtil.class);
        when(IdentityTenantUtil.getTenantId("carbon.super")).thenReturn(-1234);
        when(IdentityTenantUtil.getRealmService()).thenReturn(realmService);
        when(realmService.getTenantUserRealm(-1234)).thenReturn(null);
        Whitebox.invokeMethod(smsotpAuthenticator, "updateMobileNumberForUsername",
                context,httpServletRequest,"admin","carbon.super");
    }

    @Test
    public void testGetConfigurationProperties() {
        List<Property> configProperties = new ArrayList<Property>();
        Property smsUrl = new Property();
        configProperties.add(smsUrl);
        Property httpMethod = new Property();
        configProperties.add(httpMethod);
        Property headers = new Property();
        configProperties.add(headers);
        Property payload = new Property();
        configProperties.add(payload);
        Property httpResponse = new Property();
        configProperties.add(httpResponse);
        Property showErrorInfo = new Property();
        configProperties.add(showErrorInfo);
        Property maskValues = new Property();
        configProperties.add(maskValues);
        Property mobileNumberRegexPattern = new Property();
        configProperties.add(mobileNumberRegexPattern);
        Property mobileNumberPatternFailureErrorMessage = new Property();
        configProperties.add(mobileNumberPatternFailureErrorMessage);
        Property lengthOTP = new Property();
        configProperties.add(lengthOTP);
        Property expiryTimeOTP = new Property();
        configProperties.add(expiryTimeOTP);
        Property numericOTP = new Property();
        configProperties.add(numericOTP);
        Assert.assertEquals(configProperties.size(), smsotpAuthenticator.getConfigurationProperties().size());
    }

    @ObjectFactory
    public IObjectFactory getObjectFactory() {
        return new PowerMockObjectFactory();
    }

    private void mockServiceURLBuilder() throws URLBuilderException {

        ServiceURLBuilder builder = new ServiceURLBuilder() {

            String path = "";

            @Override
            public ServiceURLBuilder addPath(String... strings) {

                Arrays.stream(strings).forEach(x -> {
                    if (x.startsWith("/")) {
                        path += x;
                    } else {
                        path += "/" + x;
                    }
                });
                return this;
            }

            @Override
            public ServiceURLBuilder addParameter(String s, String s1) {

                return this;
            }

            @Override
            public ServiceURLBuilder setFragment(String s) {

                return this;
            }

            @Override
            public ServiceURLBuilder addFragmentParameter(String s, String s1) {

                return this;
            }

            @Override
            public ServiceURL build() {

                ServiceURL serviceURL = mock(ServiceURL.class);
                PowerMockito.when(serviceURL.getRelativePublicURL()).thenReturn(path);
                PowerMockito.when(serviceURL.getRelativeInternalURL()).thenReturn(path);

                String tenantDomain = IdentityTenantUtil.getTenantDomainFromContext();
                if (IdentityTenantUtil.isTenantQualifiedUrlsEnabled()
                        && !StringUtils.equals(tenantDomain, SUPER_TENANT_DOMAIN_NAME)) {
                    PowerMockito.when(serviceURL.getAbsolutePublicURL())
                            .thenReturn("https://localhost:9443/t/" + tenantDomain + path);
                } else {
                    PowerMockito.when(serviceURL.getAbsolutePublicURL()).thenReturn("https://localhost:9443" + path);
                }
                return serviceURL;
            }
        };

        mockStatic(ServiceURLBuilder.class);
        PowerMockito.when(ServiceURLBuilder.create()).thenReturn(builder);
    }

    @DataProvider(name = "mobileNumberRequestDataProvider")
    public static Object[][] getMobileNumberRequestPageData() {

        return new Object[][]{

                // Super tenant
                {false, SUPER_TENANT, null, "https://localhost:9443/authenticationendpoint/mobile.jsp"},
                {true, SUPER_TENANT, null, "https://localhost:9443/authenticationendpoint/mobile.jsp"},

                // Tenant
                {false, TENANT_DOMAIN, null, "https://localhost:9443/authenticationendpoint/mobile.jsp"},
                {true, TENANT_DOMAIN, null, "https://localhost:9443/t/wso2.com/authenticationendpoint/mobile.jsp"},

                // Super tenant with Externalized relative URLs
                {false, SUPER_TENANT, "mysmsotp/mobile.jsp", "https://localhost:9443/mysmsotp/mobile.jsp"},
                {true, SUPER_TENANT, "mysmsotp/mobile.jsp", "https://localhost:9443/mysmsotp/mobile.jsp"},

                // Tenant with Externalized relative URLs
                {false, TENANT_DOMAIN, "mysmsotp/mobile.jsp", "https://localhost:9443/mysmsotp/mobile.jsp"},
                {true, TENANT_DOMAIN, "mysmsotp/mobile.jsp", "https://localhost:9443/t/wso2.com/mysmsotp/mobile.jsp"},

                // Super tenant with Externalized absolute URLs
                {false, SUPER_TENANT, "https://mydomain/mobile.jsp", "https://mydomain/mobile.jsp"},
                {true, SUPER_TENANT, "https://mydomain/mobile.jsp", "https://mydomain/mobile.jsp"},

                // Tenant with Externalized absolute URLs
                {false, TENANT_DOMAIN, "https://mydomain/mobile.jsp", "https://mydomain/mobile.jsp"},
                {true, TENANT_DOMAIN, "https://mydomain/mobile.jsp", "https://mydomain/mobile.jsp"},
        };
    }

    @Test(dataProvider = "mobileNumberRequestDataProvider")
    public void testGetMobileNumberRequestPage(boolean isTenantQualifiedURLEnabled,
                                               String tenantDomain, String urlFromConfig,
                                               String expectedURL) throws Exception {

        mockStatic(IdentityTenantUtil.class);
        mockStatic(SMSOTPUtils.class);
        mockStatic(ConfigurationFacade.class);

        Map<String, String> parameters = new HashMap<>();
        if (urlFromConfig != null) {
            parameters.put(SMSOTPConstants.MOBILE_NUMBER_REQ_PAGE, urlFromConfig);
        }
        context.setProperty(IdentityHelperConstants.GET_PROPERTY_FROM_REGISTRY, new Object());
        context.setTenantDomain(tenantDomain);

        when(ConfigurationFacade.getInstance()).thenReturn(configurationFacade);
        when(configurationFacade.getAuthenticationEndpointURL()).thenReturn("/authenticationendpoint/login.do");
        when(SMSOTPUtils.getMobileNumberRequestPage(any(AuthenticationContext.class))).thenCallRealMethod();
        when(SMSOTPUtils.getConfiguration(any(AuthenticationContext.class),
                eq(SMSOTPConstants.MOBILE_NUMBER_REQ_PAGE))).thenCallRealMethod();
        when(SMSOTPUtils.getSMSParameters()).thenReturn(parameters);

        PowerMockito.when(IdentityTenantUtil.isTenantQualifiedUrlsEnabled()).thenReturn(isTenantQualifiedURLEnabled);
        PowerMockito.when(IdentityTenantUtil.getTenantDomainFromContext()).thenReturn(tenantDomain);

        Assert.assertEquals(Whitebox.invokeMethod(smsotpAuthenticator, "getMobileNumberRequestPage",
                context), expectedURL);
    }

    @DataProvider(name = "loginPageDataProvider")
    public static Object[][] getLoginPageData() {

        return new Object[][]{

                // Super tenant
                {false, SUPER_TENANT, null, "https://localhost:9443/authenticationendpoint/smsOtp.jsp"},
                {true, SUPER_TENANT, null, "https://localhost:9443/authenticationendpoint/smsOtp.jsp"},

                // Tenant
                {false, TENANT_DOMAIN, null, "https://localhost:9443/authenticationendpoint/smsOtp.jsp"},
                {true, TENANT_DOMAIN, null, "https://localhost:9443/t/wso2.com/authenticationendpoint/smsOtp.jsp"},

                // Super tenant with Externalized relative URLs
                {false, SUPER_TENANT, "mysmsotp/smsOtp.jsp", "https://localhost:9443/mysmsotp/smsOtp.jsp"},
                {true, SUPER_TENANT, "mysmsotp/smsOtp.jsp", "https://localhost:9443/mysmsotp/smsOtp.jsp"},

                // Tenant with Externalized relative URLs
                {false, TENANT_DOMAIN, "mysmsotp/smsOtp.jsp", "https://localhost:9443/mysmsotp/smsOtp.jsp"},
                {true, TENANT_DOMAIN, "mysmsotp/smsOtp.jsp", "https://localhost:9443/t/wso2.com/mysmsotp/smsOtp.jsp"},

                // Super tenant with Externalized absolute URLs
                {false, SUPER_TENANT, "https://mydomain/smsOtp.jsp", "https://mydomain/smsOtp.jsp"},
                {true, SUPER_TENANT, "https://mydomain/smsOtp.jsp", "https://mydomain/smsOtp.jsp"},

                // Tenant with Externalized absolute URLs
                {false, TENANT_DOMAIN, "https://mydomain/smsOtp.jsp", "https://mydomain/smsOtp.jsp"},
                {true, TENANT_DOMAIN, "https://mydomain/smsOtp.jsp", "https://mydomain/smsOtp.jsp"},
        };
    }

    @Test(dataProvider = "loginPageDataProvider")
    public void testGetLoginPage(boolean isTenantQualifiedURLEnabled,
                                 String tenantDomain, String urlFromConfig,
                                 String expectedURL) throws Exception {

        mockStatic(IdentityTenantUtil.class);
        mockStatic(SMSOTPUtils.class);
        mockStatic(ConfigurationFacade.class);

        Map<String, String> parameters = new HashMap<>();
        if (urlFromConfig != null) {
            parameters.put(SMSOTPConstants.SMSOTP_AUTHENTICATION_ENDPOINT_URL, urlFromConfig);
        }
        context.setProperty(IdentityHelperConstants.GET_PROPERTY_FROM_REGISTRY, new Object());
        context.setTenantDomain(tenantDomain);

        when(ConfigurationFacade.getInstance()).thenReturn(configurationFacade);
        when(configurationFacade.getAuthenticationEndpointURL()).thenReturn("/authenticationendpoint/login.do");
        when(SMSOTPUtils.getLoginPageFromXMLFile(any(AuthenticationContext.class))).thenCallRealMethod();
        when(SMSOTPUtils.getConfiguration(any(AuthenticationContext.class), eq(SMSOTPConstants.SMSOTP_AUTHENTICATION_ENDPOINT_URL))).thenCallRealMethod();
        when(SMSOTPUtils.getSMSParameters()).thenReturn(parameters);

        PowerMockito.when(IdentityTenantUtil.isTenantQualifiedUrlsEnabled()).thenReturn(isTenantQualifiedURLEnabled);
        PowerMockito.when(IdentityTenantUtil.getTenantDomainFromContext()).thenReturn(tenantDomain);

        Assert.assertEquals(Whitebox.invokeMethod(smsotpAuthenticator, "getLoginPage",
                context), expectedURL);
    }

    @DataProvider(name = "errorPageDataProvider")
    public static Object[][] getErrorPageData() {

        return new Object[][]{

                // Super tenant
                {false, SUPER_TENANT, null, "https://localhost:9443/authenticationendpoint/smsOtpError.jsp"},
                {true, SUPER_TENANT, null, "https://localhost:9443/authenticationendpoint/smsOtpError.jsp"},

                // Tenant
                {false, TENANT_DOMAIN, null, "https://localhost:9443/authenticationendpoint/smsOtpError.jsp"},
                {true, TENANT_DOMAIN, null, "https://localhost:9443/t/wso2.com/authenticationendpoint/smsOtpError.jsp"},

                // Super tenant with Externalized relative URLs
                {false, SUPER_TENANT, "mysmsotp/smsOtpError.jsp", "https://localhost:9443/mysmsotp/smsOtpError.jsp"},
                {true, SUPER_TENANT, "mysmsotp/smsOtpError.jsp", "https://localhost:9443/mysmsotp/smsOtpError.jsp"},

                // Tenant with Externalized relative URLs
                {false, TENANT_DOMAIN, "mysmsotp/smsOtpError.jsp", "https://localhost:9443/mysmsotp/smsOtpError.jsp"},
                {true, TENANT_DOMAIN, "mysmsotp/smsOtpError.jsp", "https://localhost:9443/t/wso2.com/mysmsotp/smsOtpError.jsp"},

                // Super tenant with Externalized absolute URLs
                {false, SUPER_TENANT, "https://mydomain/smsOtpError.jsp", "https://mydomain/smsOtpError.jsp"},
                {true, SUPER_TENANT, "https://mydomain/smsOtpError.jsp", "https://mydomain/smsOtpError.jsp"},

                // Tenant with Externalized absolute URLs
                {false, TENANT_DOMAIN, "https://mydomain/smsOtpError.jsp", "https://mydomain/smsOtpError.jsp"},
                {true, TENANT_DOMAIN, "https://mydomain/smsOtpError.jsp", "https://mydomain/smsOtpError.jsp"},
        };
    }

    @Test(dataProvider = "errorPageDataProvider")
    public void testGetErrorPage(boolean isTenantQualifiedURLEnabled,
                                 String tenantDomain, String urlFromConfig,
                                 String expectedURL) throws Exception {

        mockStatic(IdentityTenantUtil.class);
        mockStatic(SMSOTPUtils.class);
        mockStatic(ConfigurationFacade.class);

        Map<String, String> parameters = new HashMap<>();
        if (urlFromConfig != null) {
            parameters.put(SMSOTPConstants.SMSOTP_AUTHENTICATION_ERROR_PAGE_URL, urlFromConfig);
        }
        context.setProperty(IdentityHelperConstants.GET_PROPERTY_FROM_REGISTRY, new Object());
        context.setTenantDomain(tenantDomain);

        when(ConfigurationFacade.getInstance()).thenReturn(configurationFacade);
        when(configurationFacade.getAuthenticationEndpointURL()).thenReturn("/authenticationendpoint/login.do");
        when(SMSOTPUtils.getErrorPageFromXMLFile(any(AuthenticationContext.class))).thenCallRealMethod();
        when(SMSOTPUtils.getConfiguration(any(AuthenticationContext.class), eq(SMSOTPConstants.SMSOTP_AUTHENTICATION_ERROR_PAGE_URL))).thenCallRealMethod();
        when(SMSOTPUtils.getSMSParameters()).thenReturn(parameters);

        PowerMockito.when(IdentityTenantUtil.isTenantQualifiedUrlsEnabled()).thenReturn(isTenantQualifiedURLEnabled);
        PowerMockito.when(IdentityTenantUtil.getTenantDomainFromContext()).thenReturn(tenantDomain);

        Assert.assertEquals(Whitebox.invokeMethod(smsotpAuthenticator, "getErrorPage",
                context), expectedURL);
    }

    /**
     * Set a step configuration to the context with SMSOTP authenticator.
     *
     * @param authenticatorConfig Authenticator config.
     * @param authenticatedUser   Authenticated user.
     * @param context             Authentication context.
     */
    private void setStepConfigWithSmsOTPAuthenticator(AuthenticatorConfig authenticatorConfig,
                                                      AuthenticatedUser authenticatedUser,
                                                      AuthenticationContext context) {

        Map<Integer, StepConfig> stepConfigMap = new HashMap<>();
        // SMS OTP authenticator step.
        StepConfig smsOTPStep = new StepConfig();
        authenticatorConfig.setName(SMSOTPConstants.AUTHENTICATOR_NAME);
        List<AuthenticatorConfig> authenticatorList = new ArrayList<>();
        authenticatorList.add(authenticatorConfig);
        smsOTPStep.setAuthenticatorList(authenticatorList);
        smsOTPStep.setAuthenticatedUser(authenticatedUser);
        smsOTPStep.setSubjectAttributeStep(true);
        stepConfigMap.put(1, smsOTPStep);

        ServiceProvider serviceProvider = new ServiceProvider();
        serviceProvider.setSaasApp(false);
        ApplicationConfig applicationConfig = new ApplicationConfig(serviceProvider, context.getTenantDomain());

        SequenceConfig sequenceConfig = new SequenceConfig();
        sequenceConfig.setStepMap(stepConfigMap);
        sequenceConfig.setApplicationConfig(applicationConfig);
        context.setSequenceConfig(sequenceConfig);
        context.setCurrentStep(1);
    }

    @Test
    public void testIsAPIBasedAuthenticationSupported() {

        boolean isAPIBasedAuthenticationSupported = smsotpAuthenticator.isAPIBasedAuthenticationSupported();
        Assert.assertTrue(isAPIBasedAuthenticationSupported);
    }

    @Test
    public void testGetAuthInitiationData() throws AuthenticationFailedException {

        Optional<AuthenticatorData> authenticatorData = smsotpAuthenticator.getAuthInitiationData(context);
        Assert.assertTrue(authenticatorData.isPresent());
        AuthenticatorData authenticatorDataObj = authenticatorData.get();

        List<AuthenticatorParamMetadata> authenticatorParamMetadataList = new ArrayList<>();
        AuthenticatorParamMetadata usernameMetadata = new AuthenticatorParamMetadata(
                SMSOTPConstants.USER_NAME, FrameworkConstants.AuthenticatorParamType.STRING,
                0, Boolean.FALSE, SMSOTPConstants.USERNAME_PARAM);
        authenticatorParamMetadataList.add(usernameMetadata);

        Assert.assertEquals(authenticatorDataObj.getName(), SMSOTPConstants.AUTHENTICATOR_NAME);
        Assert.assertEquals(authenticatorDataObj.getDisplayName(), SMSOTPConstants.AUTHENTICATOR_FRIENDLY_NAME,
                "Authenticator display name should match.");
        Assert.assertEquals(authenticatorDataObj.getAuthParams().size(), authenticatorParamMetadataList.size(),
                "Size of lists should be equal.");
        Assert.assertEquals(authenticatorDataObj.getPromptType(),
                FrameworkConstants.AuthenticatorPromptType.USER_PROMPT);
        Assert.assertEquals(authenticatorDataObj.getRequiredParams().size(),
                1);
        for (int i = 0; i < authenticatorParamMetadataList.size(); i++) {
            AuthenticatorParamMetadata expectedParam = authenticatorParamMetadataList.get(i);
            AuthenticatorParamMetadata actualParam = authenticatorDataObj.getAuthParams().get(i);

            Assert.assertEquals(actualParam.getName(), expectedParam.getName(), "Parameter name should match.");
            Assert.assertEquals(actualParam.getType(), expectedParam.getType(), "Parameter type should match.");
            Assert.assertEquals(actualParam.getParamOrder(), expectedParam.getParamOrder(),
                    "Parameter order should match.");
            Assert.assertEquals(actualParam.isConfidential(), expectedParam.isConfidential(),
                    "Parameter mandatory status should match.");
        }
    }
}

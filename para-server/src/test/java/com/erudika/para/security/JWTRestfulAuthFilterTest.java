/*
 * Copyright 2013-2015 Erudika. http://erudika.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For issues and patches go to: https://github.com/erudika
 */
package com.erudika.para.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.util.Date;
import javax.servlet.ServletException;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class JWTRestfulAuthFilterTest {

    @Autowired
    private JWTRestfulAuthFilter filter;

    public MockHttpServletResponse doFilter(MockHttpServletRequest request) throws IOException, ServletException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    public void jwtFilterValidTest() throws IOException, ServletException, KeyLengthException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "http://paraio.org");

        Date now = new Date();
        JWTClaimsSet.Builder claimsSet = new JWTClaimsSet.Builder();
        claimsSet.subject("alice");
        claimsSet.issueTime(now);
        claimsSet.issuer("my.site.com");
        claimsSet.expirationTime(new DateTime().plusHours(1).toDate());
        claimsSet.notBeforeTime(now);

        String token = "Bearer " + this.signAndSerializeJWT(claimsSet.build(), "superSecretKey");
        request.addHeader("Authorization", token);
        MockHttpServletResponse response = doFilter(request);
        Assert.assertEquals(200, response.getStatus());
    }

    private String signAndSerializeJWT(JWTClaimsSet claimsSet, String secret) throws KeyLengthException {
        JWSSigner signer = new MACSigner(secret);
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claimsSet);
        try {
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch(JOSEException e) {
            e.printStackTrace();
            return null;
        }
    }
}

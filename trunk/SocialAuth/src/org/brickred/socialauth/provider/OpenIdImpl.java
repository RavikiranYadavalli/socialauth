/*
 ===========================================================================
 Copyright (c) 2010 BrickRed Technologies Limited

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sub-license, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 ===========================================================================

 */

package org.brickred.socialauth.provider;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.brickred.socialauth.AbstractProvider;
import org.brickred.socialauth.AuthProvider;
import org.brickred.socialauth.Contact;
import org.brickred.socialauth.Profile;
import org.brickred.socialauth.exception.ProviderStateException;
import org.openid4java.OpenIDException;
import org.openid4java.consumer.ConsumerException;
import org.openid4java.consumer.ConsumerManager;
import org.openid4java.consumer.VerificationResult;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.discovery.Identifier;
import org.openid4java.message.AuthRequest;
import org.openid4java.message.AuthSuccess;
import org.openid4java.message.ParameterList;
import org.openid4java.message.ax.AxMessage;
import org.openid4java.message.ax.FetchRequest;
import org.openid4java.message.ax.FetchResponse;

/**
 * Implementation of Open ID provider. Currently only name and
 * email has been implemented as part of profile. Other functionality
 * like updating status and importing contacts is not available
 * for generic Open ID providers
 */
public class OpenIdImpl extends AbstractProvider implements AuthProvider {

	private ConsumerManager manager;
	private DiscoveryInformation discovered;
	private Properties props;

	public OpenIdImpl(final Properties props) throws ConsumerException {
		manager = new ConsumerManager();
		this.props = props;
		discovered = null;
	}

	/**
	 * This is the most important action. It redirects the browser to an
	 * appropriate URL which will be used for authentication with the provider
	 * that has been set using setId()
	 * 
	 * @throws Exception
	 */
	public String getLoginRedirectURL(final String redirectUri) throws IOException {
		setProviderState(true);
		return authRequest(props.getProperty("id"), redirectUri);
	}

	private String authRequest(final String userSuppliedString, final String returnToUrl)
	throws IOException
	{
		try {
			// perform discovery on the user-supplied identifier
			List discoveries = manager.discover(userSuppliedString);

			// attempt to associate with the OpenID provider
			// and retrieve one service endpoint for authentication
			discovered = manager.associate(discoveries);

			//// store the discovery information in the user's session
			// httpReq.getSession().setAttribute("openid-disc", discovered);

			// obtain a AuthRequest message to be sent to the OpenID provider
			AuthRequest authReq = manager.authenticate(discovered, returnToUrl);

			// Attribute Exchange example: fetching the 'email' attribute
			FetchRequest fetch = FetchRequest.createFetchRequest();

			// Using axschema
			fetch.addAttribute("emailax",
					"http://axschema.org/contact/email",
					true);

			fetch.addAttribute("firstnameax",
					"http://axschema.org/namePerson/first",
					true);

			fetch.addAttribute("lastnameax",
					"http://axschema.org/namePerson/last",
					true);

			fetch.addAttribute("fullnameax",
					"http://axschema.org/namePerson",
					true);

			fetch.addAttribute("email",
					"http://schema.openid.net/contact/email",
					true);

			// Using schema.openid.net (for compatibility)
			fetch.addAttribute("firstname",
					"http://schema.openid.net/namePerson/first",
					true);

			fetch.addAttribute("lastname",
					"http://schema.openid.net/namePerson/last",
					true);

			fetch.addAttribute("fullname",
					"http://schema.openid.net/namePerson",
					true);

			// attach the extension to the authentication request
			authReq.addExtension(fetch);

			return authReq.getDestinationUrl(true);
		} catch (OpenIDException e)  {
		}

		return null;
	}

	/**
	 * Verifies the user when the external provider redirects back to our
	 * application.
	 * 
	 * @return Profile object containing the profile information
	 * @param request Request object the request is received from the provider
	 * @throws Exception
	 */

	public Profile verifyResponse(final HttpServletRequest httpReq)
	throws Exception {
		if (!isProviderState()) {
			throw new ProviderStateException();
		}
		try {
			// extract the parameters from the authentication response
			// (which comes in as a HTTP request from the OpenID provider)
			ParameterList response =
				new ParameterList(httpReq.getParameterMap());

			// extract the receiving URL from the HTTP request
			StringBuffer receivingURL = httpReq.getRequestURL();
			String queryString = httpReq.getQueryString();
			if (queryString != null && queryString.length() > 0) {
				receivingURL.append("?").append(httpReq.getQueryString());
			}


			// verify the response; ConsumerManager needs to be the same
			// (static) instance used to place the authentication request
			VerificationResult verification = manager.verify(receivingURL.toString(),
					response, discovered);

			// examine the verification result and extract the verified identifier
			Identifier verified = verification.getVerifiedId();
			if (verified != null) {
				Profile p = new Profile();
				p.setValidatedId(verified.getIdentifier());
				AuthSuccess authSuccess =
					(AuthSuccess) verification.getAuthResponse();

				if (authSuccess.hasExtension(AxMessage.OPENID_NS_AX)) {
					FetchResponse fetchResp = (FetchResponse) authSuccess
					.getExtension(AxMessage.OPENID_NS_AX);


					p.setEmail(fetchResp.getAttributeValue("email"));
					p.setFirstName(fetchResp.getAttributeValue("firstname"));
					p.setLastName(fetchResp.getAttributeValue("lastname"));
					p.setFullName(fetchResp.getAttributeValue("fullname"));

					// also use the ax namespace for compatibility
					if (p.getEmail() == null) {
						p.setEmail(fetchResp.getAttributeValue("emailax"));
					}
					if (p.getFirstName() == null) {
						p.setFirstName(fetchResp.getAttributeValue("firstnameax"));
					}
					if (p.getLastName() == null) {
						p.setLastName(fetchResp.getAttributeValue("lastnameax"));
					}
					if (p.getFullName() == null) {
						p.setFullName(fetchResp.getAttributeValue("fullnameax"));
					}

				}

				return p;
			}
		} catch (OpenIDException e) {
			throw e;
		}

		return null;
	}

	/**
	 * Updating status is not available for generic Open ID providers.
	 */
	public void updateStatus(final String msg) {
		System.out.println("WARNING: Not implemented");
	}

	/**
	 * Contact list is not available for generic Open ID providers.
	 * @return null
	 */
	public List<Contact> getContactList() {
		return null;
	}

	/**
	 * Logout
	 */
	public void logout() {
		discovered = null;
	}
}
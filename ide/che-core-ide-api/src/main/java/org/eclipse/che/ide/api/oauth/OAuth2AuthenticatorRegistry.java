/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.ide.api.oauth;

/**
 * Authenticators registry.
 *
 * @author Vitalii Parfonov
 */
public interface OAuth2AuthenticatorRegistry {

  void registerAuthenticator(String providerName, OAuth2Authenticator oAuth2Authenticator);

  OAuth2Authenticator getAuthenticator(String providerName);
}

/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.testsuite.broker.provider;

import org.keycloak.broker.provider.AbstractIdentityProviderFactory;
import org.keycloak.models.IdentityProviderModel;

/**
 * @author pedroigor
 */
public class CustomIdentityProviderFactory extends AbstractIdentityProviderFactory<CustomIdentityProvider> {

    public static final String PROVIDER_ID = "testsuite-custom-identity-provider";

    @Override
    public String getName() {
        return "Testsuite Custom Identity Provider";
    }

    @Override
    public CustomIdentityProvider create(IdentityProviderModel model) {
        return new CustomIdentityProvider(model);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}

/*
 *
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2018 David Luna.
 *
 */

package com.luna.authentication;

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.authentication.callbacks.ScriptTextOutputCallback;
import com.sun.identity.shared.validation.PositiveIntegerValidator;
import com.sun.identity.sm.RequiredValueValidator;

import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.*;
import org.forgerock.openam.authentication.callbacks.PollingWaitCallback;

import javax.inject.Inject;
import javax.security.auth.callback.Callback;

import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

/**
 * A node that waits for a defined period of time, before progressing.
 */
@Node.Metadata(outcomeProvider  = SingleOutcomeNode.OutcomeProvider.class,
               configClass      = PollingWaitNode.Config.class)
public class PollingWaitNode extends SingleOutcomeNode {

    private static final String BUNDLE = PollingWaitNode.class.getName().replace(".", "/");
    private final Config config;

    /**
     * Configuration for the node.
     */
    public interface Config {
        @Attribute(order = 100, validators = PositiveIntegerValidator.class)
        default int secondsToWait() {
            return 8;
        }
        @Attribute(order = 200, validators = RequiredValueValidator.class)
        default MessageType messageType() { return MessageType.ResourceBundle; }
        @Attribute(order = 300)
        default String definedMessage() { return ""; }
    }

    /**
     * Create the node.
     *
     * @param config The service config.
     * @throws NodeProcessException If the configuration was not valid.
     */
    @Inject
    public PollingWaitNode(@Assisted Config config) throws NodeProcessException {
        this.config = config;
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {
        return context.getCallback(PollingWaitCallback.class)
                .map(outputVal -> goToNext().build())
                .orElseGet(() -> sendCallbacks(context));
    }

    private Action sendCallbacks(TreeContext context) {
        return Action.send(createCallbacks(context)).build();
    }

    private List<Callback> createCallbacks(TreeContext context) {
        Callback waitCallback = pollingWaitCallback();
        Callback scriptCallback = scriptingCallback(context);

        return Arrays.asList(waitCallback, scriptCallback);
    }

    private Callback pollingWaitCallback() {
        return new PollingWaitCallback.PollingWaitCallbackBuilder()
                .withWaitTime(String.valueOf(config.secondsToWait() * 1000)).build();
    }

    private Callback scriptingCallback(TreeContext context) {
        String waitingText = getTextOutput(context);
        String spinner = getSpinnerHtml(waitingText);
        String output = insertSpinnerScript(spinner);

        return new ScriptTextOutputCallback(output);
    }

    private String insertSpinnerScript(String spinner) {
        return "var newLocation = document.getElementById(\"content\"); \n" +
        "newLocation.getElementsByTagName(\"fieldset\")[0].innerHTML += \"" + spinner +"\"; \n" +
        "document.body.appendChild(newLocation); \n";
    }

    private String getSpinnerHtml(String waitingText) {
        return "<div class=\\\"form-group\\\"><span></span></div>" +
                        "<div class=\\\"panel panel-default\\\">" +
                        "<div class=\\\"panel-body text-center\\\">" +
                        "<h4 class=\\\"awaiting-response\\\">" +
                        "<i class=\\\"fa fa-circle-o-notch fa-spin text-primary\\\"></i> " + waitingText + "</h4>" +
                        "</div>" +
                        "</div>";
    }

    private String getTextOutput(TreeContext context) {
        String value;

        switch (config.messageType()) {
            case AdminDefined:
                value = config.definedMessage();
                break;
            case ResourceBundle:
            default:
                ResourceBundle bundle = context.request.locales
                        .getBundleInPreferredLocale(BUNDLE, OutcomeProvider.class.getClassLoader());
                value = bundle.getString("waiting");
        }

        return value.replaceAll("\\{\\{time\\}\\}", String.valueOf(config.secondsToWait()));
    }

    /**
     * Enum representing various message type approaches.
     */
    public enum MessageType {
        ResourceBundle,
        AdminDefined
    }
}
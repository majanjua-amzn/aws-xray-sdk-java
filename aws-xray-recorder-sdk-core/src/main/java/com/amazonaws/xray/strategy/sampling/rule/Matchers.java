package com.amazonaws.xray.strategy.sampling.rule;

import com.amazonaws.services.xray.model.SamplingRule;
import com.amazonaws.xray.strategy.sampling.SamplingRequest;
import com.amazonaws.xray.entities.SearchPattern;

import java.util.Collections;
import java.util.Map;

public class Matchers {

    private Map<String, String> attributes;

    private String service;

    private String method;

    private String host;

    private String url;

    public Matchers(SamplingRule r) {
        this.host = r.getHost();
        this.service = r.getServiceName();
        this.method = r.getHTTPMethod();
        this.url = r.getURLPath();

        this.attributes = r.getAttributes() == null ? Collections.emptyMap() : r.getAttributes();
    }

    boolean match(SamplingRequest req) {
        // Comparing against the full list of matchers can be expensive. We try to short-circuit the req as quickly
        // as possible by comparing against matchers with high variance and moving down to matchers that are almost
        // always "*".
        Map<String, String> requestAttributes = req.getAttributes();

        // Ensure that each defined attribute in the sampling rule is satisfied by the request. It is okay for the
        // request to have attributes with no corresponding match in the sampling rule.
        for (Map.Entry<String, String> a : attributes.entrySet()) {
            if (!requestAttributes.containsKey(a.getKey())) {
                return false;
            }

            if (!SearchPattern.wildcardMatch(a.getValue(), requestAttributes.get(a.getKey()))) {
                return false;
            }

            continue;
        }

        // Missing string parameters from the sampling request are replaced with ""s to ensure they match against *
        // matchers.
        return SearchPattern.wildcardMatch(url, req.getUrl().orElse(""))
                && SearchPattern.wildcardMatch(service, req.getService().orElse(""))
                && SearchPattern.wildcardMatch(method, req.getMethod().orElse(""))
                && SearchPattern.wildcardMatch(host, req.getHost().orElse(""));
    }

}

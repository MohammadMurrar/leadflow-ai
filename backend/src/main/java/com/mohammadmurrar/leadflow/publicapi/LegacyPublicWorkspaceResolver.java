package com.mohammadmurrar.leadflow.publicapi;

import com.mohammadmurrar.leadflow.workspace.Workspace;
import org.springframework.stereotype.Service;

@Service
public class LegacyPublicWorkspaceResolver {
    static final String LEGACY_SLUG = "leadflow-ai";
    private final PublicWorkspaceResolver resolver;

    public LegacyPublicWorkspaceResolver(PublicWorkspaceResolver resolver) {
        this.resolver = resolver;
    }

    public Workspace resolve() {
        return resolver.resolve(LEGACY_SLUG);
    }
}

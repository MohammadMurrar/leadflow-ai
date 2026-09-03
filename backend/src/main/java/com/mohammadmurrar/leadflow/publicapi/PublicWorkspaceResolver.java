package com.mohammadmurrar.leadflow.publicapi;

import com.mohammadmurrar.leadflow.common.NotFoundException;
import com.mohammadmurrar.leadflow.workspace.Workspace;
import com.mohammadmurrar.leadflow.workspace.WorkspaceRepository;
import com.mohammadmurrar.leadflow.workspace.WorkspaceStatus;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
public class PublicWorkspaceResolver {
    static final int MAXIMUM_SLUG_LENGTH = 63;
    private static final Pattern CANONICAL_SLUG = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private static final String UNAVAILABLE_MESSAGE = "Public inquiry is unavailable";

    private final WorkspaceRepository workspaces;

    public PublicWorkspaceResolver(WorkspaceRepository workspaces) {
        this.workspaces = workspaces;
    }

    @Transactional(readOnly = true)
    public Workspace resolve(String publicSlug) {
        if (publicSlug == null || publicSlug.length() > MAXIMUM_SLUG_LENGTH
                || !CANONICAL_SLUG.matcher(publicSlug).matches()) {
            throw unavailable();
        }
        try {
            return workspaces.findByPublicSlugAndStatus(publicSlug, WorkspaceStatus.ACTIVE)
                    .orElseThrow(PublicWorkspaceResolver::unavailable);
        } catch (IncorrectResultSizeDataAccessException ignored) {
            throw unavailable();
        }
    }

    private static NotFoundException unavailable() {
        return new NotFoundException(UNAVAILABLE_MESSAGE);
    }
}

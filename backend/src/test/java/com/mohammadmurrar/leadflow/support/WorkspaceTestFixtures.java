package com.mohammadmurrar.leadflow.support;

import com.mohammadmurrar.leadflow.workspace.Workspace;
import com.mohammadmurrar.leadflow.workspace.WorkspaceStatus;
import java.util.UUID;

public final class WorkspaceTestFixtures {
    private static final UUID WORKSPACE_A_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID WORKSPACE_B_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID WORKSPACE_C_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");

    private WorkspaceTestFixtures() {}

    public static Workspace activeWorkspaceA() {
        return Workspace.create(WORKSPACE_A_ID, "workspace-a", "Workspace A", WorkspaceStatus.ACTIVE);
    }

    public static Workspace activeWorkspaceB() {
        return Workspace.create(WORKSPACE_B_ID, "workspace-b", "Workspace B", WorkspaceStatus.ACTIVE);
    }

    public static Workspace suspendedWorkspaceC() {
        return Workspace.create(WORKSPACE_C_ID, "workspace-c", "Workspace C", WorkspaceStatus.SUSPENDED);
    }
}

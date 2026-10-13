package com.baseflow.permissionhandler;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.pm.PackageManager;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class PermissionInterruptionTest {
    private PermissionManager manager;
    private List<Map<Integer, Integer>> replies;

    @Before
    public void setUp() throws Exception {
        manager = new PermissionManager(null);
        replies = new ArrayList<>();
        // Start at the Android callback boundary, after a camera dialog has opened.
        set(manager, "pendingRequestCount", 1);
        set(manager, "requestResults", new HashMap<Integer, Integer>());
        set(manager, "successCallback", (PermissionManager.RequestPermissionsSuccessCallback)
                result -> replies.add(new HashMap<>(result)));
    }

    @Test
    public void grantedResultCompletesNormally() throws Exception {
        grantCamera();
        assertEquals(0, pendingCount());
        assertEquals(1, replies.size());
        assertEquals(Integer.valueOf(PermissionConstants.PERMISSION_STATUS_GRANTED),
                replies.get(0).get(PermissionConstants.PERMISSION_GROUP_CAMERA));
    }

    @Test
    public void emptyResultCompletesAndReleasesThePendingRequest() throws Exception {
        assertTrue(interrupt());
        assertEquals(0, pendingCount());
        assertEquals(1, replies.size());
        assertTrue(replies.get(0).isEmpty());
        assertNull(get(manager, "successCallback"));
        assertNull(get(manager, "requestResults"));
    }

    @Test
    public void interruptionPreservesAlreadyResolvedPermissions() throws Exception {
        Map<Integer, Integer> partial = new HashMap<>();
        partial.put(PermissionConstants.PERMISSION_GROUP_NOTIFICATION,
                PermissionConstants.PERMISSION_STATUS_GRANTED);
        set(manager, "requestResults", partial);
        interrupt();
        assertEquals(List.of(partial), replies);
    }

    @Test
    public void duplicateOrLateResultsCannotReplyTwiceAfterInterruption() {
        interrupt();
        interrupt();
        grantCamera();
        assertEquals(1, replies.size());
    }

    @Test
    public void anotherPluginsCallbackDoesNotReleaseOurRequest() throws Exception {
        assertFalse(manager.onRequestPermissionsResult(9001, new String[0], new int[0]));
        assertEquals(1, pendingCount());
        assertTrue(replies.isEmpty());
        grantCamera();
        assertEquals(0, pendingCount());
        assertEquals(1, replies.size());
    }

    @Test
    public void permanentActivityDetachCompletesOnce() throws Exception {
        PermissionHandlerPlugin plugin = plugin();
        plugin.onDetachedFromActivity();
        plugin.onDetachedFromActivity();
        assertEquals(0, pendingCount());
        assertEquals(1, replies.size());
        assertTrue(replies.get(0).isEmpty());
    }

    @Test
    public void configurationDetachPreservesThePendingRequest() throws Exception {
        plugin().onDetachedFromActivityForConfigChanges();
        assertEquals(1, pendingCount());
        assertTrue(replies.isEmpty());
        grantCamera();
        assertEquals(0, pendingCount());
        assertEquals(1, replies.size());
    }

    @Test
    public void detachAfterNormalCompletionDoesNotReplyAgain() throws Exception {
        grantCamera();
        plugin().onDetachedFromActivity();
        assertEquals(1, replies.size());
    }

    private PermissionHandlerPlugin plugin() throws Exception {
        PermissionHandlerPlugin plugin = new PermissionHandlerPlugin();
        set(plugin, "permissionManager", manager);
        return plugin;
    }

    private boolean interrupt() {
        return manager.onRequestPermissionsResult(
                PermissionConstants.PERMISSION_CODE, new String[0], new int[0]);
    }

    private void grantCamera() {
        manager.onRequestPermissionsResult(PermissionConstants.PERMISSION_CODE,
                new String[]{Manifest.permission.CAMERA},
                new int[]{PackageManager.PERMISSION_GRANTED});
    }

    private int pendingCount() throws Exception {
        return (int) get(manager, "pendingRequestCount");
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

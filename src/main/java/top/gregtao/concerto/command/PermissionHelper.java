package top.gregtao.concerto.command;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.command.permission.PermissionPredicate;
import net.minecraft.command.permission.PermissionSource;
import net.minecraft.entity.player.PlayerEntity;

/**
 * 由于 Mojang 重写了权限系统，暂且用这个顶用吧
 * TODO 重构这坨
 * @author fireboy637
 */
public class PermissionHelper {
    private static final Permission[] PERMISSIONS = new Permission[]{
            new Permission.Level(PermissionLevel.ALL),
            DefaultPermissions.MODERATORS,
            DefaultPermissions.GAMEMASTERS,
            DefaultPermissions.ADMINS,
            DefaultPermissions.OWNERS
    };

    public static Permission getPermission(int level) {
        return PERMISSIONS[level];
    }

    public static boolean hasPermission(PermissionSource source, int level) {
        return hasPermission(source.getPermissions(), level);
    }

    public static boolean hasPermission(PlayerEntity player, int level) {
        return hasPermission(player.getPermissions(), level);
    }

    public static boolean hasPermission(PermissionPredicate predicate, int level) {
        return predicate.hasPermission(getPermission(level));
    }
}

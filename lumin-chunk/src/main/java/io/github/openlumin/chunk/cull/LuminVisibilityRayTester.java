package io.github.openlumin.chunk.cull;

/**
 * LOCAL 档的射线可见性测试（原理参照：分段射线 + 门户段要求）。
 *
 * <p>做法：从待测 section 中心向相机做<b>分段射线</b>步进（步长与段数有上限），
 * 沿路径要求至少存在一个"门户 section"（已确认可见的 section）——若路径上没有任何
 * 已可见门户，则判定被遮挡。近距段（小于 {@link #MIN_RAY_DISTANCE_SQUARED}）直接放行，
 * 避免自身/邻格抖动导致误剔除。</p>
 *
 * <p>与纯图连通判定的差别：图判定是"拓扑可达"，射线判定是"几何上是否真的能看见"，
 * 因此能剔除被墙角挡住的 section。代价是逐 section 的射线步进，故仅用于近距。</p>
 */
public final class LuminVisibilityRayTester {

    public static final int MAX_RAY_STEPS = 12;
    public static final long MIN_RAY_DISTANCE_SQUARED = (16L * 3) * (16L * 3);

    /** 门户查询：该 section 是否已被判定可见（作为射线可见性的中继）。 */
    @FunctionalInterface
    public interface PortalQuery {
        boolean isVisible(int x, int y, int z);
    }

    /**
     * 射线是否被遮挡。
     *
     * @param sectionX/Y/Z section 中心（section 坐标）
     * @param cameraX/Y/Z  相机（section 坐标，可含小数以与 {@code *SectionsScale} 一致时用整值即可）
     * @param portals      门户查询
     * @return true = 被遮挡（调用方应把该 section 从 LOCAL 档降级/剔除）
     */
    public static boolean isRayBlocked(int sectionX, int sectionY, int sectionZ,
                                       int cameraX, int cameraY, int cameraZ,
                                       PortalQuery portals) {
        if (portals == null) {
            throw new NullPointerException("portals");
        }
        long dx = (long) cameraX - sectionX;
        long dy = (long) cameraY - sectionY;
        long dz = (long) cameraZ - sectionZ;
        long distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared <= MIN_RAY_DISTANCE_SQUARED) {
            return false;
        }
        double distance = Math.sqrt((double) distanceSquared);
        // 步长：以 section 半对角长为基准，限制在 MAX_RAY_STEPS 步内
        double step = 1.0 / (2.0 * distance);
        int steps = (int) Math.ceil(1.0 / step);
        if (steps > MAX_RAY_STEPS) {
            step = 1.0 / MAX_RAY_STEPS;
            steps = MAX_RAY_STEPS;
        }
        for (int i = 1; i < steps; i++) {
            double t = i * step;
            int px = (int) Math.round(sectionX + dx * t);
            int py = (int) Math.round(sectionY + dy * t);
            int pz = (int) Math.round(sectionZ + dz * t);
            if (px == sectionX && py == sectionY && pz == sectionZ) {
                continue;
            }
            if (px == cameraX && py == cameraY && pz == cameraZ) {
                continue;
            }
            if (portals.isVisible(px, py, pz)) {
                return false;
            }
        }
        return true;
    }

    private LuminVisibilityRayTester() {
    }
}
